"""가상 스노봇·가상 앱 — 실물·에뮬레이터 없이 중계 서버를 시험한다.

  python tools/sim.py pair   --server http://HOST:3000 --device-id SNW-01-XXXX --claim CODE
  python tools/sim.py device --server http://HOST:3000 --device-id SNW-01-XXXX --key KEY
  python tools/sim.py app    --server http://HOST:3000 --token TOKEN [--expr happy --head nod]
  python tools/sim.py bench  --server http://HOST:3000 --token TOKEN [--count 50]
    (bench: 앱 → 서버 → 가상 스노봇 → 서버 → 앱 왕복 시간. 가상 스노봇이 echo 를 되돌려 준다)
  python tools/sim.py talk   --server http://HOST:3000 --token TOKEN (--wav 말.wav | --text "공기 어때?")
    (talk: 말하기 버튼처럼 WAV(16kHz 모노)를 실제 속도로 흘려보내고, 들은 글자·대답·첫 소리까지 시간을 잰다)
"""
import statistics
import argparse
import asyncio
import json
import os
import tempfile
import time
import urllib.request
import wave

from websockets.asyncio.client import connect
from websockets.exceptions import ConnectionClosed

HELLO = {   # 봇 커넥트 규약(BCP) v0.3 봇 설명 — docs/pleos/03-module-protocol.md 3-1절
    "bcp": "0.3", "type": "hello",
    "bot": {"id": "", "model": "SnowBot SNW-01 (sim)", "maker": "snowbot", "fw": "sim", "name": "스노봇"},
    "place": {"where": "dashboard_center", "facing": "cabin", "power": "usb"},
    # 모습(규약 3-5): 허브 화면의 입자 구름이 이 선 위로 모인다. 큰 머리 + 작은 몸통 + 스피커 그릴,
    # marks 로 눈·눈썹·코(주황)·목도리(주황) 자리. 원은 큐빅 네 조각(k = 0.5523 r)으로 그렸다.
    "look": {
        "viewbox": [120, 160],
        "color": "#7FC4FF",
        "outline": (
            "M102 50 C102 73.2 83.2 92 60 92 C36.8 92 18 73.2 18 50 C18 26.8 36.8 8 60 8 C83.2 8 102 26.8 102 50 Z "
            "M86 116 C86 130.4 74.4 142 60 142 C45.6 142 34 130.4 34 116 C34 101.6 45.6 90 60 90 C74.4 90 86 101.6 86 116 Z "
            "M66 120 C66 123.3 63.3 126 60 126 C56.7 126 54 123.3 54 120 C54 116.7 56.7 114 60 114 C63.3 114 66 116.7 66 120 Z"
        ),
        "marks": {
            "eyes": [[44, 46], [76, 46]],
            "brows": [[44, 30], [76, 30]],
            "nose": [[60, 58]],
            "scarf": [[24, 84], [42, 90], [60, 92], [78, 90], [96, 84]],
        },
    },
    "parts": [
        {"id": "eyelid", "kind": "eyelid", "count": 2, "axes": {"open": [0, 1]}, "fastest_ms": 120},
        {"id": "brow", "kind": "brow", "count": 2, "axes": {"lift": [-1, 1], "tilt": [-1, 1]}},
        {"id": "head", "kind": "neck", "axes": {"pan": [-40, 40], "tilt": [-15, 20]}, "unit": "deg", "max_dps": 120},
        {"id": "spk", "kind": "speaker", "voice": "hub_stream"},
        {"id": "mic", "kind": "microphone", "listen": "hub_stream"},
    ],
    "sensors": [{"id": "env", "measures": [{"name": "co2", "unit": "ppm"}, {"name": "temp", "unit": "C"}], "period_s": 10}],
    "expressions": {"emotions": ["neutral", "happy", "greet", "worried", "sleepy", "alert", "guard"],
                    "gestures": ["nod", "shake", "look_left", "look_right", "look_up", "look_down"]},
    "actions": [],
    "wants": ["car.motion", "car.outside", "car.cabin", "car.warning", "car.energy", "driver.state", "trip"],
}


def ws_url(server: str) -> str:
    return server.rstrip("/").replace("https://", "wss://").replace("http://", "ws://") + "/ws"


def show(prefix: str, msg) -> None:
    print(time.strftime("%H:%M:%S"), prefix, json.dumps(msg, ensure_ascii=False), flush=True)


def save_wav(folder: str, sid, pcm: bytes, rate: int) -> str:
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, f"speech-{sid}.wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(bytes(pcm))
    return path


def pair(args) -> None:
    body = json.dumps({"device_id": args.device_id, "claim_code": args.claim, "kind": args.kind}).encode()
    req = urllib.request.Request(args.server.rstrip("/") + "/api/pair", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        print(r.read().decode())


def _ranges() -> dict[str, tuple[float, float]]:
    """HELLO 의 부위 범위: {"head.pan": (-40, 40), ...}"""
    out = {}
    for part in HELLO.get("parts", []):
        for axis, rng in (part.get("axes") or {}).items():
            out[f"{part['id']}.{axis}"] = (float(rng[0]), float(rng[1]))
    return out


def reply_to(msg: dict) -> dict | None:
    """규약 2-3: ping 에는 pong, id 가 붙은 지시에는 ack. pose 는 범위 안으로 잘라 실제 적용값을 돌려준다."""
    kind, mid = msg.get("type"), msg.get("id")
    if kind == "ping":
        return {"bcp": "0.3", "type": "pong", "id": mid}
    if mid is None or kind not in ("express", "gesture", "pose", "action", "say", "listen", "mode"):
        return None
    ack = {"bcp": "0.3", "type": "ack", "id": mid}
    if kind == "express":
        emotion = msg.get("emotion") or msg.get("expr") or "neutral"
        ack["emotion"] = emotion if emotion in HELLO["expressions"]["emotions"] else "neutral"
    if kind == "pose" and isinstance(msg.get("targets"), dict):
        ranges, applied = _ranges(), {}
        for key, value in msg["targets"].items():
            if key in ranges and isinstance(value, (int, float)):
                lo, hi = ranges[key]
                applied[key] = min(max(float(value), lo), hi)
        ack["targets"] = applied
    return ack


async def device(args) -> None:
    """끊기면 2초 뒤 다시 붙어 hello 부터 보낸다(규약 2-4). Ctrl+C 로 끝낸다."""
    while True:
        try:
            await device_once(args)
        except (OSError, ConnectionError, ConnectionClosed) as e:
            print(f"         (연결 끊김: {e.__class__.__name__}) 2초 뒤 다시 연결", flush=True)
        else:
            print("         (연결 끊김) 2초 뒤 다시 연결", flush=True)
        await asyncio.sleep(2)


async def device_once(args) -> None:
    async with connect(ws_url(args.server)) as ws:
        await ws.send(json.dumps({"type": "auth", "device_id": args.device_id, "key": args.key}))
        show("<-", json.loads(await ws.recv()))
        await ws.send(json.dumps({**HELLO, "bot": {**HELLO["bot"], "id": args.device_id}}))

        async def sensors():
            co2 = args.co2
            while True:
                await asyncio.sleep(args.every)
                co2 += 50
                event = {"bcp": "0.3", "type": "observe", "sensor": "env", "data": {"temp": 24.5, "co2": co2}, "sim": True}
                await ws.send(json.dumps(event))
                show("->", event)

        task = asyncio.create_task(sensors())
        speech, heard_at = None, None
        try:
            async for raw in ws:
                if isinstance(raw, bytes):  # 목소리 조각 (PCM 16kHz)
                    if speech is not None:
                        if not speech["pcm"]:
                            speech["first_ms"] = round((time.perf_counter() - (heard_at or speech["t0"])) * 1000)
                        speech["pcm"] += raw
                    continue
                msg = json.loads(raw)
                if msg.get("type") == "audio_start":
                    speech = {"t0": time.perf_counter(), "pcm": bytearray(), "rate": msg.get("sample_rate", 16000)}
                    show("<-", msg)
                    continue
                if msg.get("type") == "audio_end" and speech is not None:
                    path = save_wav(args.audio_dir, msg.get("id"), speech["pcm"], speech["rate"])
                    secs = len(speech["pcm"]) / 2 / speech["rate"]
                    print(f"         (목소리 {secs:.1f}초 받음, 표정 명령 뒤 첫 소리까지 {speech.get('first_ms')}ms → {path})"
                          + (f" 오류: {msg['error']}" if msg.get("error") else ""), flush=True)
                    speech, heard_at = None, None
                    continue
                if msg.get("type") == "echo":
                    await ws.send(raw)
                    continue
                reply = reply_to(msg)
                if reply is not None:
                    await ws.send(json.dumps(reply))
                    if reply["type"] == "pong":
                        continue
                show("<-", msg)
                if msg.get("type") == "express":
                    heard_at = time.perf_counter()
                    print(f"         (감정 {msg.get('emotion') or msg.get('expr')} / 몸짓 {msg.get('gesture') or msg.get('head')} / 말 {msg.get('say')!r})", flush=True)
        finally:
            task.cancel()


async def app(args) -> None:
    async with connect(ws_url(args.server)) as ws:
        await ws.send(json.dumps({"type": "auth", "token": args.token}))
        ok = json.loads(await ws.recv())
        show("<-", ok)
        if args.expr and ok.get("devices"):
            cmd = {"type": "express", "device_id": ok["devices"][0]["device_id"], "expr": args.expr}
            if args.head:
                cmd["head"] = args.head
            if args.say:
                cmd["say"] = args.say
                cmd["voice"] = args.voice
            await ws.send(json.dumps(cmd, ensure_ascii=False))
            show("->", cmd)
        audio_bytes = 0
        try:
            async with asyncio.timeout(args.listen):
                async for raw in ws:
                    if isinstance(raw, bytes):
                        audio_bytes += len(raw)
                        continue
                    show("<-", json.loads(raw))
        except TimeoutError:
            pass
        if audio_bytes:
            print(f"목소리 {audio_bytes / 2 / 16000:.1f}초 분량을 앱으로 받음")


async def talk(args) -> None:
    async with connect(ws_url(args.server)) as ws:
        await ws.send(json.dumps({"type": "auth", "token": args.token}))
        ok = json.loads(await ws.recv())
        device_id = ok["devices"][0]["device_id"]
        base = {"device_id": device_id, "voice": args.voice}
        if args.wav:
            with wave.open(args.wav) as w:
                assert (w.getframerate(), w.getnchannels(), w.getsampwidth()) == (16000, 1, 2), "16kHz 모노 16비트 WAV"
                pcm = w.readframes(w.getnframes())
            await ws.send(json.dumps({"type": "listen_start", **base}))
            step = 3200  # 0.1초
            for i in range(0, len(pcm), step):
                await ws.send(pcm[i:i + step])
                await asyncio.sleep(0.1)
            await ws.send(json.dumps({"type": "listen_end", **base}))
        else:
            await ws.send(json.dumps({"type": "chat", "text": args.text, **base}, ensure_ascii=False))
        t_end = time.perf_counter()
        ms = lambda: round((time.perf_counter() - t_end) * 1000)
        speech = bytearray()
        first = None
        try:
            async with asyncio.timeout(args.listen):
                async for raw in ws:
                    if isinstance(raw, bytes):
                        first = first or ms()
                        speech += raw
                        continue
                    msg = json.loads(raw)
                    if msg.get("type") == "heard" and not msg.get("final"):
                        print(f"   … {msg['text']}", flush=True)
                        continue
                    show(f"<- +{ms()}ms", msg)
                    if msg.get("type") == "audio_end":
                        break
        except TimeoutError:
            pass
        if speech:
            path = save_wav(args.audio_dir, "talk", speech, 16000)
            print(f"말 끝 → 첫 소리 {first}ms, 목소리 {len(speech) / 32000:.1f}초 → {path}")


async def bench(args) -> None:
    async with connect(ws_url(args.server)) as ws:
        await ws.send(json.dumps({"type": "auth", "token": args.token}))
        ok = json.loads(await ws.recv())
        online = [d["device_id"] for d in ok.get("devices", []) if d["online"]]
        if not online:
            raise SystemExit("켜져 있는 기기가 없다 — 먼저 sim.py device 를 띄운다")
        rtts = []
        for i in range(args.count):
            t0 = time.perf_counter()
            await ws.send(json.dumps({"type": "echo", "device_id": online[0], "seq": i}))
            while True:
                msg = json.loads(await ws.recv())
                if msg.get("type") == "echo" and msg.get("seq") == i:
                    break
            rtts.append((time.perf_counter() - t0) * 1000)
            await asyncio.sleep(0.1)
        rtts.sort()
        print(f"왕복 {len(rtts)}번 (앱→서버→기기→서버→앱), ms: "
              f"최소 {rtts[0]:.0f} · 중간 {statistics.median(rtts):.0f} · "
              f"90% {rtts[int(len(rtts) * 0.9) - 1]:.0f} · 최대 {rtts[-1]:.0f}")


def main() -> None:
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("pair", "device", "app", "bench", "talk"):
        s = sub.add_parser(name)
        s.add_argument("--server", required=True)
    sub.choices["pair"].add_argument("--device-id", required=True)
    sub.choices["pair"].add_argument("--claim", required=True)
    sub.choices["pair"].add_argument("--kind", default="car", choices=["car", "phone"])
    sub.choices["device"].add_argument("--device-id", required=True)
    sub.choices["device"].add_argument("--key", required=True)
    sub.choices["device"].add_argument("--every", type=float, default=10, help="센서 값 보내는 간격(초)")
    sub.choices["device"].add_argument("--co2", type=int, default=800, help="CO₂ 시작 값(ppm), 보낼 때마다 50씩 오른다")
    sub.choices["device"].add_argument("--audio-dir", default=os.path.join(tempfile.gettempdir(), "snowbot-sim"),
                                       help="받은 목소리를 WAV 로 저장할 폴더")
    sub.choices["app"].add_argument("--token", required=True)
    sub.choices["app"].add_argument("--expr")
    sub.choices["app"].add_argument("--head")
    sub.choices["app"].add_argument("--say")
    sub.choices["app"].add_argument("--voice", choices=["device", "app"], default="device", help="목소리를 재생할 쪽")
    sub.choices["app"].add_argument("--listen", type=float, default=15, help="받은 메시지를 보여 줄 시간(초)")
    t = sub.choices["talk"]
    t.add_argument("--token", required=True)
    t.add_argument("--wav")
    t.add_argument("--text")
    t.add_argument("--voice", choices=["device", "app"], default="app")
    t.add_argument("--listen", type=float, default=20)
    t.add_argument("--audio-dir", default=os.path.join(tempfile.gettempdir(), "snowbot-sim"))
    sub.choices["bench"].add_argument("--token", required=True)
    sub.choices["bench"].add_argument("--count", type=int, default=50)
    args = p.parse_args()
    if args.cmd == "pair":
        pair(args)
    else:
        asyncio.run({"device": device, "app": app, "bench": bench, "talk": talk}[args.cmd](args))


if __name__ == "__main__":
    main()
