"""듣기(listen-ai) — ElevenLabs Scribe v2 Realtime. 마이크 PCM 16kHz 조각을 받는 대로 넘기고, 끝나면 확정 글자를 받는다.

말하기 버튼 방식이라 글자 확정(commit)은 직접 한다: 마지막 조각 하나를 붙잡아 두었다가 finish() 때 commit=true 로 보낸다.
문서: https://elevenlabs.io/docs/api-reference/speech-to-text/v-1-speech-to-text-realtime
"""
import asyncio
import base64
import contextlib
import json
import os
from collections.abc import Awaitable, Callable
from urllib.parse import quote

from websockets.asyncio.client import connect

SAMPLE_RATE = 16000
MODEL_ID = os.environ.get("SNOWBOT_STT_MODEL", "scribe_v2_realtime")
KEYTERMS = ("스노봇",)   # 잘 틀리는 이름을 미리 알려 준다 ("스노브" 로 적던 것)
URL = (f"wss://api.elevenlabs.io/v1/speech-to-text/realtime?model_id={MODEL_ID}"
       f"&audio_format=pcm_{SAMPLE_RATE}&language_code=ko&commit_strategy=manual"
       + "".join(f"&keyterms={quote(k)}" for k in KEYTERMS))
FINISH_TIMEOUT = 5.0

OnPartial = Callable[[str], Awaitable[None]]


class SttError(Exception):
    pass


class ScribeSession:
    """듣기 한 번. start() → send() 여러 번 → finish() 가 확정 글자를 돌려준다. close() 는 언제 불러도 된다."""

    def __init__(self, on_partial: OnPartial):
        self.on_partial = on_partial
        self._ws = None
        self._held: bytes | None = None
        self._committed: asyncio.Future[str] | None = None
        self._reader: asyncio.Task | None = None

    async def start(self) -> None:
        key = os.environ.get("ELEVENLABS_API_KEY")
        if not key:
            raise SttError("ELEVENLABS_API_KEY 가 없다")
        self._ws = await connect(URL, additional_headers={"xi-api-key": key}, open_timeout=10)
        self._committed = asyncio.get_running_loop().create_future()
        self._reader = asyncio.create_task(self._read())

    async def send(self, pcm: bytes) -> None:
        if self._held is not None:
            await self._send_chunk(self._held, commit=False)
        self._held = pcm

    async def finish(self) -> str:
        """붙잡아 둔 마지막 조각을 commit 과 함께 보내고 확정 글자를 기다린다. 소리가 없었으면 빈 글자."""
        if self._held is None:
            return ""
        await self._send_chunk(self._held, commit=True)
        self._held = None
        try:
            return await asyncio.wait_for(asyncio.shield(self._committed), FINISH_TIMEOUT)
        except asyncio.TimeoutError:
            raise SttError("글자 확정이 늦다") from None

    async def close(self) -> None:
        if self._reader is not None:
            self._reader.cancel()
        if self._ws is not None:
            with contextlib.suppress(Exception):
                await self._ws.close()

    async def _send_chunk(self, pcm: bytes, commit: bool) -> None:
        await self._ws.send(json.dumps({"message_type": "input_audio_chunk", "sample_rate": SAMPLE_RATE,
                                        "audio_base_64": base64.b64encode(pcm).decode(), "commit": commit}))

    async def _read(self) -> None:
        try:
            async for raw in self._ws:
                msg = json.loads(raw)
                kind = msg.get("message_type")
                if kind == "partial_transcript" and msg.get("text"):
                    await self.on_partial(msg["text"])
                elif kind == "committed_transcript":
                    if not self._committed.done():
                        self._committed.set_result(msg.get("text", "").strip())
                elif kind == "insufficient_audio_activity":
                    if not self._committed.done():
                        self._committed.set_result("")
                elif "error" in msg:
                    raise SttError(f"{kind}: {msg['error']}"[:200])
        except asyncio.CancelledError:
            raise
        except Exception as e:
            if not self._committed.done():
                self._committed.set_exception(e if isinstance(e, SttError) else SttError(str(e)[:200]))
            return
        if not self._committed.done():
            self._committed.set_exception(SttError("연결이 끊겼다"))
