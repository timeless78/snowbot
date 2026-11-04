"""SnowBot 중계 서버 — 스노봇(기기)과 차량·폰 앱을 WebSocket 으로 잇는다.

연결 순서 (server/README.md 참고):
  1. 접속하면 첫 메시지로 auth 를 보낸다. 기기는 device_id + key, 앱은 token.
  2. 기기 → 짝 맺은 앱 모두: hello·event 등 모든 메시지에 device_id 를 붙여 전달.
  3. 앱 → 기기: device_id 로 대상을 고른 메시지(express 등)를 그 기기에 전달.
  4. 기기가 붙거나 끊기면 앱에 presence 를 보낸다.
  5. 앱의 express 에 say(할 말)가 있으면 서버가 목소리(TTS)를 만들어 소리 조각으로 흘려보낸다.
     받는 쪽: 기기(기본) — 기기가 꺼져 있거나 voice="app" 이면 보낸 앱(차 스피커·화면 속 스노봇).
     audio_start(JSON) → PCM 16kHz 바이너리 조각들 → audio_end(JSON). 새 말이 오면 하던 말은 끊는다.
     앱이 tts="app" 을 붙이면(차의 Pleos 목소리) 앱으로 갈 말은 소리 대신 문장 글자로 보낸다: say(문장마다) → say_end.
  6. 대화: 앱이 listen_start → 마이크 PCM 바이너리 조각들 → listen_end 를 보내면 서버가 글자로 바꾸고(STT),
     Claude 가 대답·표정을 정해(chat) 표정은 기기에, 말은 5번처럼 목소리로 보낸다. chat 메시지로 글자를 바로 보내도 된다.
     앱이 직접 듣는 경우(차의 Pleos 듣기)는 listen_start 에 stt="app" 을 붙여 하던 말만 멈추고, 들은 글자는 chat 으로 보낸다.
  7. 차량 앱은 car 메시지로 차 상태(기어·연속 운전 분·주의 경고·운전 부담 load 0~3)를 보낸다. 대답할 때 상황에 넣고,
     운전 부담으로 대답 길이를 정한다(LOAD_SENTENCES). load 가 3(바쁨)이 되는 순간 그 앱의 대답·목소리를 끊는다
     — 앱이 차 신호로 판단하고(DrivingLoad), 서버는 그 값만 따른다.

실행: uvicorn --factory snowbot_server.main:create_app --port 3000   (DB 경로는 SNOWBOT_DB)
"""
import asyncio
import itertools
import json
import os
import time
from collections.abc import AsyncIterator, Callable
from datetime import datetime
from typing import Literal
from zoneinfo import ZoneInfo

from fastapi import FastAPI, Header, HTTPException, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field

from .chat import (DRIVING_SENTENCES, MAX_SENTENCES, PARKED_SENTENCES, Conversation, Face, claude_reply,
                   parse_reply, user_turn)
from .store import Store
from .stt import OnPartial, ScribeSession
from .tts import AUDIO_FORMAT, SAMPLE_RATE, elevenlabs_stream

TtsFn = Callable[[str], AsyncIterator[bytes]]
SttFn = Callable[[OnPartial], ScribeSession]                 # 듣기 한 번을 여는 함수
BrainFn = Callable[[list[dict], str, int], AsyncIterator[Face | str]]   # (최근 대화, 이번 말, 문장 수) → 표정 하나, 문장들

NOT_HEARD = "[worried none] 미안해요, 잘 못 들었어요. 다시 말해 줄래요?"
BRAIN_FAILED = "[worried none] 지금은 생각이 잘 안 나요. 잠시 뒤에 다시 말해 줘요."
WEEKDAYS = "월화수목금토일"
GEARS = ("P", "R", "N", "D")
# 운전 부담(앱의 DrivingLoad) → 대답 문장 수. 0 여유 3 · 1 보통 2 · 2 주의 1 · 3 바쁨 1 (바쁠 때 물으면 한 문장)
# docs/pleos/07-dev-plan.md 2-2절 표의 "수다 단계 3(보통)" 줄. 수다 단계는 다음 단계에서 더한다.
LOAD_SENTENCES = {0: 3, 1: 2, 2: 1, 3: 1}
BUSY = 3
LOAD_WORDS = {2: "운전에 주의가 필요한 때(비·밤 등). 한 문장으로.",
              3: "운전이 바쁜 순간(차선 변경·회전·후진). 한 문장으로 아주 짧게."}
SAID_FIRST_FRESH = 600   # 스노봇이 먼저 한 말을 이 초 동안 상황에 넣는다

AUTH_TIMEOUT = 10
CLOSE_REPLACED = 4000   # 같은 기기가 다시 접속해 이전 연결을 닫음
CLOSE_BAD_AUTH = 4001   # 첫 메시지가 auth 가 아님 / 시간 초과
CLOSE_DENIED = 4003     # 키·토큰이 틀림


class Hub:
    """지금 연결된 기기·앱 목록과 메시지 전달."""

    def __init__(self, store: Store, tts: TtsFn):
        self.store = store
        self.tts = tts
        self.devices: dict[str, WebSocket] = {}
        self.hello: dict[str, dict] = {}
        self.env: dict[str, dict] = {}                 # 기기별 마지막 공기·온도 값
        self.talks: dict[str, Conversation] = {}       # 앱별 최근 대화
        self.cars: dict[str, dict] = {}                # 앱별 마지막 차 상태 (gear, driving_min, attention, load)
        self.said_first: dict[str, tuple[str, float]] = {}   # 앱별 스노봇이 먼저 한 마지막 말과 시각
        self.apps: dict[str, set[WebSocket]] = {}
        self.speaking: dict[WebSocket, asyncio.Task] = {}
        self._speech_ids = itertools.count(1)

    def start_speaking(self, target: WebSocket, text: str | asyncio.Queue, as_text: bool = False) -> None:
        """target 에게 text 를 목소리로 흘려보낸다. 하던 말이 있으면 끊는다.
        text 가 Queue 면 문장이 들어오는 대로 차례로 말한다(None 이 들어오면 끝).
        as_text 면 소리를 만들지 않고 문장 글자만 보낸다 — 받는 앱이 자기 목소리(Pleos TTS)로 읽는다."""
        self.stop_speaking(target)
        task = asyncio.create_task(self._say(target, text) if as_text else self._speak(target, text))
        self.speaking[target] = task
        task.add_done_callback(lambda t: self.speaking.pop(target, None) if self.speaking.get(target) is t else None)

    def stop_speaking(self, target: WebSocket) -> None:
        task = self.speaking.pop(target, None)
        if task is not None:
            task.cancel()

    async def _speak(self, target: WebSocket, text: str | asyncio.Queue) -> None:
        sid = next(self._speech_ids)
        await self.send(target, {"type": "audio_start", "id": sid, "format": AUDIO_FORMAT,
                                 "sample_rate": SAMPLE_RATE, "text": text if isinstance(text, str) else ""})
        try:
            async for sentence in _sentences(text):
                async for chunk in self.tts(sentence):
                    await target.send_bytes(chunk)
        except asyncio.CancelledError:
            await self.send(target, {"type": "audio_end", "id": sid, "cancelled": True})
            raise
        except Exception as e:  # TTS 실패나 끊긴 연결 — 받는 쪽에 알리고 끝낸다
            await self.send(target, {"type": "audio_end", "id": sid, "error": str(e)[:200]})
            return
        await self.send(target, {"type": "audio_end", "id": sid})

    async def _say(self, target: WebSocket, text: str | asyncio.Queue) -> None:
        sid = next(self._speech_ids)
        try:
            async for sentence in _sentences(text):
                await self.send(target, {"type": "say", "id": sid, "text": sentence})
        except asyncio.CancelledError:
            await self.send(target, {"type": "say_end", "id": sid, "cancelled": True})
            raise
        await self.send(target, {"type": "say_end", "id": sid})

    @staticmethod
    async def send(ws: WebSocket, msg: dict) -> None:
        try:
            await ws.send_text(json.dumps(msg, ensure_ascii=False))
        except Exception:
            pass  # 끊긴 연결은 그쪽 수신 루프가 정리한다

    async def to_app(self, app_id: str, msg: dict) -> None:
        for ws in list(self.apps.get(app_id, ())):
            await self.send(ws, msg)

    async def to_apps_of(self, device_id: str, msg: dict) -> None:
        for app_id in self.store.apps_of(device_id):
            await self.to_app(app_id, msg)

    def device_info(self, device_id: str) -> dict:
        return {"device_id": device_id, "online": device_id in self.devices, "hello": self.hello.get(device_id)}

    def driving(self, app_id: str) -> bool | None:
        """차가 주행 중인지. 차 상태를 받은 적이 없으면 None."""
        car = self.cars.get(app_id)
        return None if car is None or car["gear"] not in GEARS else car["gear"] != "P"

    def sentence_limit(self, app_id: str) -> int:
        """대답 문장 수: 운전 부담을 받았으면 그것으로, 없으면 주행/주차로, 차 상태를 모르면 기본."""
        car = self.cars.get(app_id)
        if car is not None and car.get("load") in LOAD_SENTENCES:
            return LOAD_SENTENCES[car["load"]]
        driving = self.driving(app_id)
        return MAX_SENTENCES if driving is None else DRIVING_SENTENCES if driving else PARKED_SENTENCES

    def situation(self, device_id: str, app_id: str | None = None) -> str:
        """Claude 에게 주는 지금 상황 한 줄 (시각·스노봇 상태·센서 값·차 상태)."""
        now = datetime.now(ZoneInfo("Asia/Seoul"))
        parts = [f"지금 {now:%Y-%m-%d}({WEEKDAYS[now.weekday()]}) {now:%H:%M}.",
                 "스노봇 몸체 켜짐." if device_id in self.devices else "스노봇 몸체 꺼짐(화면 속 스노봇만 있음)."]
        env = self.env.get(device_id) or {}
        if isinstance(env.get("co2"), (int, float)):
            parts.append(f"차 안 CO₂ {env['co2']:.0f}ppm.")
        if isinstance(env.get("temp"), (int, float)):
            parts.append(f"차 안 온도 {env['temp']:.1f}°C.")
        if len(parts) == 2:
            parts.append("센서 값 아직 없음.")
        car = self.cars.get(app_id) if app_id else None
        if car is not None and car["gear"] in GEARS:
            parts.append("차 주차 중(P)." if car["gear"] == "P"
                         else f"차 주행 중({car['gear']}). 출발한 지 {car['driving_min']}분.")
            if car["attention"]:
                parts.append("차가 운전자 주의 경고등을 켰음.")
            if car.get("load") in LOAD_WORDS:
                parts.append(LOAD_WORDS[car["load"]])
        said = self.said_first.get(app_id) if app_id else None
        if said is not None and time.monotonic() - said[1] < SAID_FIRST_FRESH:
            parts.append(f'스노봇이 먼저 한 말: "{said[0]}"')
        return " ".join(parts)


async def _sentences(text: str | asyncio.Queue) -> AsyncIterator[str]:
    if isinstance(text, str):
        yield text
        return
    while (sentence := await text.get()) is not None:
        yield sentence


async def recv_any(ws: WebSocket) -> dict | bytes:
    """프레임 하나를 받는다: 글자 프레임은 JSON 객체로, 바이너리 프레임은 bytes 로. JSON 형식이 틀리면 ValueError."""
    message = await ws.receive()
    if message["type"] == "websocket.disconnect":
        raise WebSocketDisconnect(message.get("code", 1000))
    if message.get("bytes") is not None:
        return message["bytes"]
    obj = json.loads(message.get("text") or "")
    if not isinstance(obj, dict):
        raise ValueError("JSON object expected")
    return obj


async def recv_json(ws: WebSocket) -> dict:
    """글자 프레임 하나를 JSON 객체로 받는다. 형식이 틀리면 ValueError."""
    obj = await recv_any(ws)
    if isinstance(obj, bytes):
        raise ValueError("text frames only")
    return obj


class PairIn(BaseModel):
    device_id: str = Field(max_length=32)
    claim_code: str = Field(max_length=32)
    kind: Literal["car", "phone"]
    name: str = Field(default="", max_length=64)


def create_app(db_path: str | None = None, tts: TtsFn | None = None,
               stt: SttFn | None = None, brain: BrainFn | None = None) -> FastAPI:
    store = Store(db_path or os.environ.get("SNOWBOT_DB", "relay.db"))
    hub = Hub(store, tts or elevenlabs_stream)
    stt = stt or ScribeSession
    brain = brain or claude_reply
    app = FastAPI(title="SnowBot relay", docs_url=None, redoc_url=None, openapi_url=None)

    @app.get("/health")
    def health():
        return {"ok": True, "devices_online": len(hub.devices)}

    @app.post("/api/pair")
    async def pair(body: PairIn, authorization: str | None = Header(default=None)):
        app_id = None
        if authorization:
            app_id = store.app_by_token(authorization.removeprefix("Bearer ").strip())
            if app_id is None:
                raise HTTPException(401, "invalid token")
        result = store.pair(body.device_id, body.claim_code, body.kind, body.name, app_id)
        if result is None:
            raise HTTPException(403, "invalid device_id or claim_code")
        app_id, token = result
        await hub.to_app(app_id, {"type": "presence", **hub.device_info(body.device_id)})
        return {"app_id": app_id, "device_id": body.device_id, "token": token}

    @app.websocket("/ws")
    async def ws_endpoint(ws: WebSocket):
        await ws.accept()
        try:
            first = await asyncio.wait_for(recv_json(ws), AUTH_TIMEOUT)
        except (asyncio.TimeoutError, ValueError, WebSocketDisconnect):
            await ws.close(code=CLOSE_BAD_AUTH)
            return
        if first.get("type") != "auth":
            await ws.close(code=CLOSE_BAD_AUTH)
        elif "device_id" in first:
            await device_session(ws, str(first["device_id"]), str(first.get("key", "")))
        else:
            await app_session(ws, str(first.get("token", "")))

    async def device_session(ws: WebSocket, device_id: str, key: str) -> None:
        if not store.check_device(device_id, key):
            await ws.close(code=CLOSE_DENIED)
            return
        old = hub.devices.get(device_id)
        hub.devices[device_id] = ws
        if old is not None:
            try:
                await old.close(code=CLOSE_REPLACED)
            except Exception:
                pass
        await hub.send(ws, {"type": "auth_ok", "role": "device", "device_id": device_id})
        await hub.to_apps_of(device_id, {"type": "presence", **hub.device_info(device_id)})
        try:
            while True:
                try:
                    msg = await recv_json(ws)
                except ValueError as e:
                    await hub.send(ws, {"type": "error", "code": "bad_message", "detail": str(e)})
                    continue
                if msg.get("type") == "hello":
                    hub.hello[device_id] = msg
                elif msg.get("type") == "event" and msg.get("cap") == "env" and isinstance(msg.get("data"), dict):
                    hub.env[device_id] = msg["data"]
                msg["device_id"] = device_id
                await hub.to_apps_of(device_id, msg)
        except WebSocketDisconnect:
            pass
        finally:
            hub.stop_speaking(ws)
            if hub.devices.get(device_id) is ws:
                del hub.devices[device_id]
                await hub.to_apps_of(device_id, {"type": "presence", "device_id": device_id, "online": False})

    async def app_session(ws: WebSocket, token: str) -> None:
        app_id = store.app_by_token(token)
        if app_id is None:
            await ws.close(code=CLOSE_DENIED)
            return
        hub.apps.setdefault(app_id, set()).add(ws)
        devices = [hub.device_info(d) for d in store.devices_of(app_id)]
        await hub.send(ws, {"type": "auth_ok", "role": "app", "app_id": app_id, "devices": devices})
        talk = hub.talks.setdefault(app_id, Conversation())
        listening: ScribeSession | None = None      # 지금 열린 듣기
        mic = {"bytes": 0, "peak": 0}               # 이번 듣기에 받은 소리 양·가장 큰 값 (마이크 점검용)
        opening: asyncio.Task | None = None         # 듣기 연결 여는 중
        turn: asyncio.Task | None = None            # 듣기 마무리 → 생각 → 대답

        def speaker(device_id: str, voice: str) -> WebSocket:
            device_ws = hub.devices.get(device_id)
            return ws if voice == "app" or device_ws is None else device_ws

        def speak(device_id: str, voice: str, own_voice: bool, text: str | asyncio.Queue) -> None:
            """기기 또는 이 앱으로 말한다. 이 앱이 자기 목소리를 쓰면(own_voice) 글자로 보낸다."""
            target = speaker(device_id, voice)
            hub.start_speaking(target, text, as_text=own_voice and target is ws)

        async def answer(device_id: str, voice: str, own_voice: bool, said: str, started: float) -> None:
            """said 에 대답한다: 표정은 기기·앱에 먼저, 말은 문장이 나오는 대로 목소리로."""
            heard_at = time.monotonic()
            timing = {"stt_ms": round((heard_at - started) * 1000)}
            lines: asyncio.Queue = asyncio.Queue()
            sentences: list[str] = []
            face = None
            error = None

            async def show(f: Face) -> None:
                timing["face_ms"] = round((time.monotonic() - heard_at) * 1000)
                await hub.send(ws, {"type": "reply_start", "device_id": device_id, "expr": f.expr, "head": f.head})
                device_ws = hub.devices.get(device_id)
                if device_ws is not None:
                    await hub.send(device_ws, {"type": "express", "expr": f.expr, **({"head": f.head} if f.head else {})})
                speak(device_id, voice, own_voice, lines)

            async def canned(raw: str) -> None:
                nonlocal face
                r = parse_reply(raw)
                face = Face(r.expr, r.head)
                await show(face)
                sentences.append(r.text)
                lines.put_nowait(r.text)

            try:
                if not said:
                    await canned(NOT_HEARD)
                else:
                    user = user_turn(said, hub.situation(device_id, app_id))
                    limit = hub.sentence_limit(app_id)
                    try:
                        async for item in brain(talk.history, user, limit):
                            if isinstance(item, Face):
                                face = item
                                await show(face)
                            else:
                                sentences.append(item)
                                lines.put_nowait(item)
                    except Exception as e:   # Claude 실패 — 정해 둔 말로 대신한다
                        error = str(e)[:200]
                    if face is None or not sentences:
                        if face is None:
                            await canned(BRAIN_FAILED)
                        else:
                            sentences.append(parse_reply(BRAIN_FAILED).text)
                            lines.put_nowait(sentences[-1])
                        error = error or "빈 대답"
                    else:
                        talk.remember(user, f"{face.tag()} {' '.join(sentences)}")
            finally:
                lines.put_nowait(None)
            timing["llm_ms"] = round((time.monotonic() - heard_at) * 1000)
            await hub.send(ws, {"type": "reply", "device_id": device_id, "text": " ".join(sentences), "expr": face.expr,
                                "head": face.head, "timing": timing, **({"error": error} if error else {})})

        async def finish_listening(session: ScribeSession, opened: asyncio.Task, device_id: str, voice: str,
                                   own_voice: bool, level: dict) -> None:
            started = time.monotonic()
            try:
                await opened
                said = await session.finish()
            except Exception as e:
                await hub.send(ws, {"type": "heard", "text": "", "final": True, "error": str(e)[:200], **level})
                said = ""
            else:
                await hub.send(ws, {"type": "heard", "text": said, "final": True, **level})
            finally:
                await session.close()
            # 운영 점검용 한 줄 — 말 내용은 남기지 않는다(개인정보), 길이·소리 크기만
            print(f"listen app={app_id} audio_ms={level['audio_ms']} peak={level['peak']} chars={len(said)}", flush=True)
            await answer(device_id, voice, own_voice, said, started)

        def run_turn(coro) -> asyncio.Task:
            nonlocal turn
            if turn is not None:
                turn.cancel()
            turn = asyncio.create_task(coro)
            return turn

        async def stop_listening() -> None:
            nonlocal listening, opening
            if opening is not None:
                opening.cancel()
            if listening is not None:
                await listening.close()
            listening = opening = None

        try:
            while True:
                try:
                    msg = await recv_any(ws)
                except ValueError as e:
                    await hub.send(ws, {"type": "error", "code": "bad_message", "detail": str(e)})
                    continue
                if isinstance(msg, bytes):        # 마이크 소리 조각
                    if listening is not None:
                        mic["bytes"] += len(msg)
                        if len(msg) % 2 == 0 and msg:
                            mic["peak"] = max(mic["peak"], max(map(abs, memoryview(msg).cast("h"))))
                        try:
                            await opening
                            await listening.send(msg)
                        except Exception as e:
                            await hub.send(ws, {"type": "heard", "text": "", "final": True, "error": str(e)[:200]})
                            await stop_listening()
                    continue
                kind = msg.get("type")
                if kind == "car":                 # 차 상태: 기기로 보내지 않고 대화 상황에만 쓴다
                    gear, minutes = msg.get("gear"), msg.get("driving_min")
                    load = msg.get("load")
                    load = load if isinstance(load, int) and not isinstance(load, bool) and load in LOAD_SENTENCES else None
                    was_busy = (hub.cars.get(app_id) or {}).get("load") == BUSY
                    hub.cars[app_id] = {"gear": gear if gear in GEARS else "?",
                                        "driving_min": max(0, min(minutes, 10_000)) if isinstance(minutes, int) else 0,
                                        "attention": msg.get("attention") is True, "load": load}
                    if load == BUSY and not was_busy:   # 운전이 바빠짐: 생각 중인 대답과 하던 말을 버린다
                        if turn is not None:
                            turn.cancel()
                        hub.stop_speaking(ws)
                        for device_id in store.devices_of(app_id):
                            device_ws = hub.devices.get(device_id)
                            if device_ws is not None:
                                hub.stop_speaking(device_ws)
                        print(f"busy app={app_id} → 말 멈춤", flush=True)
                    continue
                target = msg.pop("device_id", None)
                voice = msg.pop("voice", "device")
                own_voice = msg.pop("tts", None) == "app"     # 앱이 차 목소리(Pleos TTS)로 직접 말한다
                own_ears = msg.pop("stt", None) == "app"      # 앱이 차 듣기(Pleos STT)로 직접 듣는다
                if target not in store.devices_of(app_id):
                    await hub.send(ws, {"type": "error", "code": "not_paired", "ref": kind})
                    continue

                if kind == "listen_start":        # 말하기 버튼: 하던 말·생각을 멈추고 듣기 시작
                    await stop_listening()
                    if turn is not None:
                        turn.cancel()
                    hub.stop_speaking(speaker(target, voice))
                    hub.stop_speaking(ws)
                    if own_ears:                  # 들은 글자는 나중에 chat 으로 온다
                        continue

                    async def on_partial(text: str) -> None:
                        await hub.send(ws, {"type": "heard", "text": text, "final": False})

                    listening = stt(on_partial)
                    opening = asyncio.create_task(listening.start())
                    mic.update(bytes=0, peak=0)
                elif kind == "listen_end":
                    if listening is not None:
                        run_turn(finish_listening(listening, opening, target, voice, own_voice,
                                                  {"audio_ms": mic["bytes"] // 32, "peak": mic["peak"]}))
                        listening = opening = None
                elif kind == "chat":              # 글자로 바로 말 걸기 (앱이 직접 들은 말·시험)
                    said = str(msg.get("text", "")).strip()[:500]
                    print(f"chat app={app_id} chars={len(said)}", flush=True)   # 말 내용은 남기지 않는다
                    run_turn(answer(target, voice, own_voice, said, time.monotonic()))
                else:                             # express 등: 기기에 전달
                    device_ws = hub.devices.get(target)
                    if device_ws is None:
                        await hub.send(ws, {"type": "error", "code": "device_offline", "ref": kind})
                    else:
                        await hub.send(device_ws, msg)
                    say = msg.get("say")
                    if kind == "express" and isinstance(say, str) and say.strip():
                        speak(target, voice, own_voice, say)
                        hub.said_first[app_id] = (say.strip()[:200], time.monotonic())
        except WebSocketDisconnect:
            pass
        finally:
            await stop_listening()
            if turn is not None:
                turn.cancel()
            hub.stop_speaking(ws)
            hub.apps.get(app_id, set()).discard(ws)

    return app
