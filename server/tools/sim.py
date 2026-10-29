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

HELLO = {
    "type": "hello", "model": "SnowBot SNW-01 (sim)", "fw": "sim",
    "capabilities": [
        {"id": "express", "expr": ["neutral", "happy", "sleepy", "worried", "alert", "guard", "greet"],
         "head": ["nod", "shake", "turn_left", "turn_right"], "brow": True, "eyelid": True},
        {"id": "speaker"}, {"id": "mic"}, {"id": "env", "sensors": ["temp", "co2"]},
    ],
    "power": "usb",
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


async def device(args) -> None:
    async with connect(ws_url(args.server)) as ws:
        await ws.send(json.dumps({"type": "auth", "device_id": args.device_id, "key": args.key}))
        show("<-", json.loads(await ws.recv()))
        await ws.send(json.dumps(HELLO))

        async def sensors():
            co2 = 800
            while True:
                await asyncio.sleep(args.every)
                co2 += 50
                event = {"type": "event", "cap": "env", "data": {"temp": 24.5, "co2": co2}, "sim": True}
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
                show("<-", msg)
                if msg.get("type") == "express":
                    heard_at = time.perf_counter()
                    print(f"         (표정 {msg.get('expr')} / 고개 {msg.get('head')} / 말 {msg.get('say')!r})", flush=True)
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
