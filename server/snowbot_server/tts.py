"""말하기(tts-ai) — ElevenLabs 스트리밍. 글자를 받아 PCM 16kHz 소리 조각을 받는 대로 내준다.

목소리·모델은 docs/10-ai-services.md 에서 정했다: Suzu + eleven_v4_turbo (첫 소리 약 0.24초).
"""
import os
from collections.abc import AsyncIterator

import httpx

VOICE_ID = os.environ.get("SNOWBOT_TTS_VOICE", "6awt6FKyZGV0HyQEwisX")   # Suzu
MODEL_ID = os.environ.get("SNOWBOT_TTS_MODEL", "eleven_v4_turbo")
SAMPLE_RATE = 16000
AUDIO_FORMAT = f"pcm_{SAMPLE_RATE}"   # 16비트 모노 PCM — 스노봇(ESP32·I2S 앰프)과 앱(AudioTrack)이 바로 재생


class TtsError(Exception):
    pass


# 연결을 열어 두고 다시 쓴다 — 말할 때마다 TLS 연결을 새로 맺으면 첫 소리가 0.3초쯤 늦어진다
_client: httpx.AsyncClient | None = None


def _http() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=10.0),
                                    limits=httpx.Limits(keepalive_expiry=300))
    return _client


async def elevenlabs_stream(text: str) -> AsyncIterator[bytes]:
    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key:
        raise TtsError("ELEVENLABS_API_KEY 가 없다")
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{VOICE_ID}/stream?output_format={AUDIO_FORMAT}"
    body = {"text": text, "model_id": MODEL_ID, "language_code": "ko"}
    async with _http().stream("POST", url, json=body, headers={"xi-api-key": key}) as r:
        if r.status_code != 200:
            raise TtsError(f"ElevenLabs {r.status_code}: {(await r.aread())[:200]!r}")
        async for chunk in r.aiter_bytes(4096):
            yield chunk
