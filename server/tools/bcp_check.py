"""봇 커넥트 규약(BCP) v0.3 적합성 시험기 — 봇을 만드는 사람이 규약을 지키는지 스스로 확인한다.

규약: ../../docs/pleos/03-module-protocol.md (검사 항목은 9절 표).

시험기는 **허브(앱) 역할**을 흉내 낸다. 작은 로컬 허브를 띄우고 봇이 거기 붙으면
`hello` → `welcome` → `express`·`gesture`·`pose`·`ping` 을 보내며 답을 본다.
봇 쪽 접속 방식은 중계 서버(`snowbot_server/main.py`)와 같다: 첫 메시지 `auth` → `auth_ok` → `hello` …
그래서 봇은 서버 주소만 바꾸면 고칠 것이 없다. 기기 키는 검사하지 않는다(규약만 본다).

  python tools/bcp_check.py                       # ws://127.0.0.1:8787/ws 로 봇이 붙기를 기다린다
  python tools/bcp_check.py --json                # 결과를 JSON 으로 (표는 표준오류로)
  python tools/bcp_check.py --run "python tools/sim.py device --server {server} --device-id SIM --key x"

끝나면 하나라도 ❌ 면 1, 아니면 0 으로 끝난다.
"""
import argparse
import asyncio
import json
import os
import shlex
import statistics
import subprocess
import sys
import time
import unicodedata
from dataclasses import dataclass, field

BCP = "0.3"
REQUIRED_EMOTIONS = ("neutral", "happy", "worried", "alert")     # 6-1절: 봇이 최소로 표현해야 하는 감정
ALL_EMOTIONS = ("neutral", "happy", "greet", "worried", "sleepy", "alert", "guard")   # 공통 감정 어휘 7가지
ACK_TARGET_MS = 300                                              # 9절: express → ack 목표
AXES_BY_KIND = {"eyelid": ("open",), "brow": ("lift", "tilt"), "neck": ("pan", "tilt")}
KNOWN_KINDS = (*AXES_BY_KIND, "light", "screen", "speaker", "microphone")      # 3-2절 표
FIXED_RANGES = {("eyelid", "open"): (0.0, 1.0), ("brow", "lift"): (-1.0, 1.0), ("brow", "tilt"): (-1.0, 1.0)}
VOICES = ("hub_stream", "bot_tts", "chime")
LISTENS = ("hub_stream", "bot_stt")
PATTERNS = ("solid", "breathe", "blink", "spin")
PERIOD_RANGE = (0.5, 1.5)        # 실제 관찰 간격 / 선언 주기 가 이 안이면 통과
GRANTED = ["car.motion", "car.outside", "car.cabin", "trip"]

PASS, FAIL, SKIP = "pass", "fail", "skip"
MARK = {PASS: "✅", FAIL: "❌", SKIP: "⚠️"}
TITLES = {"hello": "봇 설명", "express": "의도", "gesture": "몸짓·부위", "observe": "관찰",
          "connect": "연결", "latency": "반응 시간"}
KEYS = tuple(TITLES)


@dataclass
class Options:
    ack_timeout: float = 2.0         # id 를 붙인 메시지에 ack 를 기다리는 시간(초)
    hello_wait: float = 60.0         # 봇이 붙어 hello 를 보내기를 기다리는 시간
    observe_wait: float = 25.0       # 관찰 주기를 재려고 더 기다리는 시간
    observe_periods: int = 2         # 센서마다 이만큼의 observe 를 모으면 그만 기다린다
    reconnect_wait: float = 15.0     # 끊은 뒤 다시 붙어 hello 를 보내기를 기다리는 시간
    gestures_max: int = 8            # 시험할 몸짓 개수 상한


@dataclass
class Check:
    key: str
    status: str
    reason: str
    notes: list[str] = field(default_factory=list)
    metrics: dict = field(default_factory=dict)

    @property
    def title(self) -> str:
        return TITLES[self.key]


@dataclass
class Report:
    checks: list[Check] = field(default_factory=list)
    hello: dict | None = None
    odd: list[str] = field(default_factory=list)      # 규약에 없거나 모양이 틀린 메시지

    @property
    def ok(self) -> bool:
        return all(c.status != FAIL for c in self.checks)

    def by_key(self, key: str) -> Check:
        return next(c for c in self.checks if c.key == key)


# ─────────────────────────────── 연결 (허브 쪽) ───────────────────────────────

class BotGone(Exception):
    """봇이 연결을 끊었다."""


class Dropped(Exception):
    """허브가 연결을 끊었다 (봇 쪽에서 보는 예외)."""


class HubLink:
    """허브 쪽에서 본 봇 하나와의 연결. 받은 시각은 `at` 에 남는다."""

    supports_drop = True

    def __init__(self) -> None:
        self.frames: asyncio.Queue = asyncio.Queue()
        self.at = 0.0

    async def accept(self, timeout: float) -> dict | None:
        """봇이 (다시) 붙기를 기다린다. auth 없이 바로 보낸 메시지가 있으면 돌려준다."""
        raise NotImplementedError

    async def send(self, msg: dict) -> None:
        raise NotImplementedError

    async def drop(self) -> None:
        raise NotImplementedError

    async def recv(self, timeout: float) -> dict:
        at, msg = await asyncio.wait_for(self.frames.get(), timeout)
        self.at = at
        if msg is None:
            raise BotGone
        return msg

    def _arrived(self, msg: dict | None) -> None:
        self.frames.put_nowait((time.monotonic(), msg))


class BotEnd:
    """가짜 봇 쪽 끝 (시험용). 허브가 끊으면 recv 가 Dropped 를 낸다."""

    def __init__(self) -> None:
        self.inbox: asyncio.Queue = asyncio.Queue()
        self.outbox: asyncio.Queue = asyncio.Queue()
        self.closed = asyncio.Event()

    async def send(self, msg: dict) -> None:
        self.outbox.put_nowait(msg)

    async def recv(self) -> dict:
        get = asyncio.ensure_future(self.inbox.get())
        closed = asyncio.ensure_future(self.closed.wait())
        done, _ = await asyncio.wait({get, closed}, return_when=asyncio.FIRST_COMPLETED)
        if get in done:
            closed.cancel()
            return get.result()
        get.cancel()
        raise Dropped


class MemoryHub(HubLink):
    """메모리 안에서 가짜 봇과 잇는 연결 — pytest 용."""

    def __init__(self) -> None:
        super().__init__()
        self.waiting: asyncio.Queue = asyncio.Queue()
        self.end: BotEnd | None = None
        self._pump: asyncio.Task | None = None

    def connect(self) -> BotEnd:
        """봇 쪽에서 부른다 — 새 연결을 만든다. 받아들이기 전에 보낸 말도 그대로 기다린다."""
        end = BotEnd()
        self.waiting.put_nowait(end)
        return end

    async def _drain(self, end: BotEnd) -> None:
        """봇이 보낸 메시지에 받은 시각을 붙인다 (실제 연결의 수신 루프와 같은 자리)."""
        while True:
            self._arrived(await end.outbox.get())

    async def accept(self, timeout: float) -> dict | None:
        end = await asyncio.wait_for(self.waiting.get(), timeout)
        self.frames = asyncio.Queue()
        self.end = end
        self._pump = asyncio.create_task(self._drain(end))
        return None

    async def send(self, msg: dict) -> None:
        if self.end is not None:
            self.end.inbox.put_nowait(msg)

    async def drop(self) -> None:
        if self._pump is not None:
            self._pump.cancel()
            self._pump = None
        if self.end is not None:
            self.end.closed.set()
            self.end = None


class LocalHub(HubLink):
    """봇이 직접 붙는 작은 허브 — 봇 쪽 접속 방식은 중계 서버와 같다."""

    def __init__(self, host: str = "127.0.0.1", port: int = 8787) -> None:
        super().__init__()
        self.host, self.port = host, port
        self.ws = None
        self._pending: asyncio.Queue = asyncio.Queue()
        self._server = None

    async def start(self) -> str:
        from websockets.asyncio.server import serve
        self._server = await serve(self._handle, self.host, self.port)
        self.port = next(iter(self._server.sockets)).getsockname()[1]
        return f"ws://{self.host}:{self.port}/ws"

    async def stop(self) -> None:
        if self._server is not None:
            self._server.close()

    async def _handle(self, ws) -> None:
        try:
            first = json.loads(await ws.recv())
            if not isinstance(first, dict):
                raise ValueError
        except Exception:
            await ws.close(code=4001)
            return
        pre = None
        if first.get("type") == "auth":          # 중계 서버와 같은 첫 메시지 — 키는 보지 않는다
            await ws.send(json.dumps({"type": "auth_ok", "role": "device",
                                      "device_id": first.get("device_id", "")}, ensure_ascii=False))
        else:
            pre = first                          # auth 없이 바로 말을 건 봇
        self._pending.put_nowait((ws, pre))
        while self.ws is not ws:                 # 시험기가 이 연결을 받아들이기를 기다린다
            await asyncio.sleep(0.01)
        try:
            async for raw in ws:
                if isinstance(raw, bytes):       # 소리 조각 — 규약 검사와 상관없다
                    continue
                try:
                    msg = json.loads(raw)
                except ValueError:
                    msg = {"type": "?", "_bad": raw[:120]}
                self._arrived(msg if isinstance(msg, dict) else {"type": "?", "_bad": str(msg)[:120]})
        except Exception:
            pass
        finally:
            if self.ws is ws:
                self._arrived(None)

    async def accept(self, timeout: float) -> dict | None:
        ws, pre = await asyncio.wait_for(self._pending.get(), timeout)
        self.frames = asyncio.Queue()
        self.ws = ws
        return pre

    async def send(self, msg: dict) -> None:
        if self.ws is not None:
            await self.ws.send(json.dumps(msg, ensure_ascii=False))

    async def drop(self) -> None:
        ws, self.ws = self.ws, None
        if ws is not None:
            await ws.close()


# ─────────────────────────────── 봇 설명 검사 (3절) ───────────────────────────────

def _is_num(v) -> bool:
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def _range_of(axis_range) -> tuple[float, float] | None:
    if (isinstance(axis_range, (list, tuple)) and len(axis_range) == 2
            and all(_is_num(v) for v in axis_range) and axis_range[0] < axis_range[1]):
        return float(axis_range[0]), float(axis_range[1])
    return None


def part_problems(parts: list) -> tuple[list[str], list[str]]:
    """부위 선언을 본다 → (어긴 것, 참고). 3-2절 표."""
    bad, notes = [], []
    for i, part in enumerate(parts):
        if not isinstance(part, dict):
            bad.append(f"`parts[{i}]` 가 객체가 아니다")
            continue
        pid, kind = str(part.get("id") or ""), str(part.get("kind") or "")
        if not pid:
            bad.append(f"`parts[{i}].id` 가 없다")
        if not kind:
            bad.append(f"부위 `{pid or i}` 에 `kind` 가 없다")
        name = pid or kind or str(i)
        for axis in AXES_BY_KIND.get(kind, ()):
            rng = _range_of((part.get("axes") or {}).get(axis))
            if rng is None:
                bad.append(f"부위 `{name}`({kind}) 의 축 `{axis}` 범위가 없거나 [작은값, 큰값] 이 아니다")
            elif (kind, axis) in FIXED_RANGES and rng != FIXED_RANGES[(kind, axis)]:
                lo, hi = FIXED_RANGES[(kind, axis)]
                bad.append(f"부위 `{name}`.{axis} 범위가 {list(rng)} — 규약은 [{lo:g}, {hi:g}]")
        for axis, rng in (part.get("axes") or {}).items():
            if _range_of(rng) is None:
                bad.append(f"부위 `{name}`.{axis} 범위가 [작은값, 큰값] 이 아니다: {rng!r}")
        if kind == "neck" and not _is_num(part.get("max_dps")):
            notes.append(f"부위 `{name}`(neck) 에 `max_dps`(최대 속도)가 없다(권장)")
        if kind == "light":
            if not _is_num(part.get("leds")):
                bad.append(f"부위 `{name}`(light) 에 `leds` 개수가 없다")
            unknown = [p for p in (part.get("patterns") or []) if p not in PATTERNS]
            if unknown:
                notes.append(f"부위 `{name}` 의 빛 모양 {', '.join(map(str, unknown))} 은 규약에 없다(제조사 고유면 `x-…`)")
        if kind == "speaker" and part.get("voice") not in VOICES:
            bad.append(f"부위 `{name}`(speaker) 의 `voice` 가 {'/'.join(VOICES)} 중 하나가 아니다: {part.get('voice')!r}")
        if kind == "microphone" and part.get("listen") not in LISTENS:
            bad.append(f"부위 `{name}`(microphone) 의 `listen` 이 {'/'.join(LISTENS)} 중 하나가 아니다: {part.get('listen')!r}")
        if kind and kind not in KNOWN_KINDS and not kind.startswith("x-"):
            notes.append(f"부위 `{name}` 의 `kind` `{kind}` 는 규약에 없다(제조사 고유면 `x-회사.이름`)")
    return bad, notes


def sensor_problems(sensors: list) -> tuple[list[str], list[str]]:
    bad, notes = [], []
    for i, sensor in enumerate(sensors):
        if not isinstance(sensor, dict):
            bad.append(f"`sensors[{i}]` 가 객체가 아니다")
            continue
        sid = str(sensor.get("id") or "")
        if not sid:
            bad.append(f"`sensors[{i}].id` 가 없다")
        measures = sensor.get("measures")
        if not isinstance(measures, list) or not measures:
            bad.append(f"센서 `{sid or i}` 에 `measures` 가 없다")
        else:
            for m in measures:
                if not isinstance(m, dict) or not m.get("name"):
                    bad.append(f"센서 `{sid or i}` 의 측정 항목에 `name` 이 없다: {m!r}")
                elif not m.get("unit"):
                    bad.append(f"센서 `{sid or i}`.{m['name']} 에 `unit`(단위)이 없다")
        if not _is_num(sensor.get("period_s")):
            notes.append(f"센서 `{sid or i}` 에 `period_s` 가 없다 — 주기를 검사할 수 없다(사건마다 보내는 센서면 맞다)")
    return bad, notes


def look_problems(look: dict) -> tuple[list[str], list[str]]:
    """3-5절 `look` (선택 항목) → (어긴 것, 참고). 모양이 틀린 것만 어긴 것으로 본다."""
    bad, notes = [], []
    if not isinstance(look.get("outline"), str) or not look["outline"].strip():
        bad.append("`look.outline`(SVG path) 이 글자가 아니다")
    box = look.get("viewbox")
    if not (isinstance(box, list) and len(box) == 2 and all(_is_num(v) and v > 0 for v in box)):
        bad.append("`look.viewbox` 가 [너비, 높이] 가 아니다")
    unknown = []
    for name, points in (look.get("marks") or {}).items():
        if not (isinstance(points, list) and all(isinstance(p, list) and len(p) == 2 and all(_is_num(v) for v in p)
                                                 for p in points)):
            bad.append(f"`look.marks.{name}` 가 [[x, y], …] 가 아니다")
        elif name not in MARKS and not name.startswith("x-"):
            unknown.append(name)
    if unknown:   # 모르는 자리는 허브가 지나치면 되므로 통과 기준(9절)에 넣지 않는다
        notes.append(f"`look.marks` 의 {', '.join(unknown)} 은 규약 표에 없다(eyes·brows·mouth·nose·scarf) — "
                     "허브가 모르면 지나친다. 제조사 고유면 `x-회사.이름`(8절)")
    return bad, notes


MARKS = ("eyes", "brows", "mouth", "nose", "scarf")   # 규약 3-5절 look.marks


def emotions_of(hello: dict) -> list[str]:
    return [str(e) for e in ((hello.get("expressions") or {}).get("emotions") or [])]


def gestures_of(hello: dict) -> list[str]:
    return [str(g) for g in ((hello.get("expressions") or {}).get("gestures") or [])]


def check_hello(hello: dict) -> Check:
    bad, notes = [], []
    if hello.get("bcp") != BCP:
        notes.append(f"`bcp` 가 {hello.get('bcp')!r} — 이 시험기는 v{BCP} 기준으로 본다")
    bot = hello.get("bot")
    if not isinstance(bot, dict):
        bad.append("`bot`(봇 정보) 객체가 없다")
    else:
        for want in ("id", "model"):
            if not str(bot.get(want) or "").strip():
                bad.append(f"`bot.{want}` 가 없다")
        missing = [w for w in ("maker", "fw", "name") if not str(bot.get(w) or "").strip()]
        if missing:
            notes.append("`bot." + "`·`bot.".join(missing) + "` 가 없다(권장)")
    place = hello.get("place")
    if not isinstance(place, dict) or not place.get("where"):
        notes.append("`place.where`(놓인 자리)가 없다 — 없으면 허브가 `look at` 각도를 계산할 수 없다(2-1절)")
    parts = hello.get("parts")
    if not isinstance(parts, list) or not parts:
        bad.append("`parts`(몸 부위)가 비었다")
    else:
        p_bad, p_notes = part_problems(parts)
        bad += p_bad
        notes += p_notes
    sensors = hello.get("sensors")
    if not isinstance(sensors, list):
        notes.append("`sensors` 가 없다 — 센서가 없는 봇이면 `[]` 로 적는다")
    else:
        s_bad, s_notes = sensor_problems(sensors)
        bad += s_bad
        notes += s_notes
    emotions = emotions_of(hello)
    lacking = [e for e in REQUIRED_EMOTIONS if e not in emotions]
    if lacking:
        bad.append(f"최소 감정 4개 중 {', '.join(lacking)} 를 선언하지 않았다")
    unknown = [e for e in emotions if e not in ALL_EMOTIONS and not e.startswith("x-")]
    if unknown:
        notes.append(f"감정 {', '.join(unknown)} 은 공통 어휘 7가지에 없다(제조사 고유면 `x-회사.이름`)")
    if "look" in hello:
        if isinstance(hello["look"], dict):
            l_bad, l_notes = look_problems(hello["look"])
            bad += l_bad
            notes += l_notes
        else:
            bad.append("`look` 이 객체가 아니다")
    else:
        notes.append("`look`(모습)이 없다 — 선택 항목이다(3-5절). 허브는 빛 방울을 그대로 쓴다")
    if not isinstance(hello.get("wants"), list):
        notes.append("`wants`(받고 싶은 차 정보)가 없다 — 허브가 아무 주제도 보내지 않는다")

    good = (f"필수 필드 있음 · 부위 {len(parts) if isinstance(parts, list) else 0}개 범위 정상 · "
            f"감정 {len(emotions)}개(최소 4개 포함)")
    return Check("hello", FAIL if bad else PASS, " / ".join(bad) if bad else good, notes,
                 {"parts": len(parts) if isinstance(parts, list) else 0, "emotions": len(emotions),
                  "gestures": len(gestures_of(hello))})


# ─────────────────────────────── 주고받으며 하는 검사 ───────────────────────────────

class Session:
    """봇과 주고받으며 검사한다. 사이에 온 observe·state 는 시각과 함께 모아 둔다."""

    def __init__(self, hub: HubLink, opt: Options) -> None:
        self.hub, self.opt = hub, opt
        self.observes: list[tuple[float, dict]] = []
        self.states: list[dict] = []
        self.odd: list[str] = []
        self.pre: dict | None = None
        self._ids = iter(range(100, 10_000))

    def next_id(self) -> int:
        return next(self._ids)

    async def recv(self, timeout: float) -> dict:
        if self.pre is not None:
            msg, self.pre = self.pre, None
            self.hub.at = time.monotonic()
            return msg
        return await self.hub.recv(timeout)

    def _keep(self, msg: dict) -> None:
        kind = msg.get("type")
        if kind == "observe":
            self.observes.append((self.hub.at, msg))
        elif kind == "state":
            self.states.append(msg)
        elif kind == "?":
            self.odd.append(f"JSON 객체가 아닌 메시지: {msg.get('_bad')!r}")
        elif kind is None:
            self.odd.append(f"`type` 이 없는 메시지: {json.dumps(msg, ensure_ascii=False)[:120]}")

    async def wait_for(self, ok, timeout: float) -> dict:
        """ok(msg) 가 참인 첫 메시지. 없으면 asyncio.TimeoutError."""
        end = time.monotonic() + timeout
        while True:
            left = end - time.monotonic()
            if left <= 0:
                raise asyncio.TimeoutError
            msg = await self.recv(left)
            self._keep(msg)
            if ok(msg):
                return msg

    async def ask(self, msg: dict, want: str = "ack") -> tuple[dict | None, float]:
        """id 를 붙여 보내고 답을 기다린다 → (답, 걸린 ms). 답이 없으면 (None, -1)."""
        mid = self.next_id()
        t0 = time.perf_counter()
        await self.hub.send({"bcp": BCP, "id": mid, **msg})
        try:
            reply = await self.wait_for(lambda m: m.get("type") == want and m.get("id") == mid, self.opt.ack_timeout)
        except asyncio.TimeoutError:
            return None, -1.0
        return reply, (time.perf_counter() - t0) * 1000

    async def drain(self, seconds: float) -> None:
        """seconds 동안 오는 메시지를 받아 둔다(observe 모으기)."""
        end = time.monotonic() + seconds
        while (left := end - time.monotonic()) > 0:
            try:
                self._keep(await self.recv(left))
            except asyncio.TimeoutError:
                return


async def check_express(s: Session, hello: dict) -> tuple[Check, Check]:
    """의도(7가지 감정 ack · 없는 감정 대체)와 반응 시간을 같이 본다 — 같은 왕복으로 잰다."""
    declared = emotions_of(hello)
    times: dict[str, float] = {}
    missed: list[str] = []
    notes: list[str] = []
    for emotion in ALL_EMOTIONS:
        reply, ms = await s.ask({"type": "express", "emotion": emotion, "intensity": 0.6,
                                 "reason": "bcp_check", "hold_ms": 600})
        if reply is None:
            missed.append(emotion)
            continue
        times[emotion] = ms
        shown = reply.get("emotion")
        if isinstance(shown, str) and shown not in declared:
            missed.append(emotion)
            notes.append(f"`{emotion}` 의 ack 가 선언하지 않은 감정 `{shown}` 을 돌려줬다")
    undeclared = [e for e in ALL_EMOTIONS if e not in declared]
    if undeclared:
        acked = [e for e in undeclared if e in times]
        if not acked:
            notes.append(f"선언하지 않은 감정 {', '.join(undeclared)}: ack 가 없었다")
        elif len(acked) == len(undeclared):
            notes.append(f"선언하지 않은 감정 {', '.join(undeclared)} 에도 ack — 가까운 감정으로 바꿔 표현한 것으로 본다")
        else:
            notes.append(f"선언하지 않은 감정 {', '.join(undeclared)} 중 {', '.join(acked)} 에만 ack")
        notes.append("ack 가 실제로 어떤 감정을 보였는지는 밖에서 볼 수 없다 — 봇이 ack 에 `emotion` 을 넣으면 검사한다")
    else:
        notes.append("공통 감정 7가지를 모두 선언해 대체 규칙은 해당 없음")

    express = Check("express", FAIL if missed else PASS,
                    f"{', '.join(missed)} 에 ack 가 없다(그 외 {len(times)}개는 응답)" if missed
                    else f"감정 7가지 모두 ack (없는 감정 {len(undeclared)}개 포함)", notes)
    if not times:
        latency = Check("latency", FAIL, f"`express` 에 ack 가 없어 반응 시간을 재지 못했다(목표 {ACK_TARGET_MS}ms)")
    else:
        values = sorted(times.values())
        metrics = {"count": len(values), "min_ms": round(values[0], 1),
                   "median_ms": round(statistics.median(values), 1), "max_ms": round(values[-1], 1),
                   "target_ms": ACK_TARGET_MS}
        over = [e for e, ms in times.items() if ms > ACK_TARGET_MS]
        word = (f"가장 느린 {metrics['max_ms']}ms · 중간 {metrics['median_ms']}ms · 가장 빠른 {metrics['min_ms']}ms "
                f"({len(values)}번)")
        latency = Check("latency", FAIL if over else PASS,
                        f"{word} — {', '.join(over)} 가 목표 {ACK_TARGET_MS}ms 를 넘었다" if over
                        else f"{word}, 목표 {ACK_TARGET_MS}ms 안", [], metrics)
    return express, latency


def pose_probe(parts: list) -> tuple[str, tuple[float, float]] | None:
    """범위 밖 값을 보내 볼 축 하나를 고른다 → ("head.pan", (-40, 40))."""
    for part in parts if isinstance(parts, list) else []:
        if not isinstance(part, dict):
            continue
        for axis, raw in (part.get("axes") or {}).items():
            rng = _range_of(raw)
            if rng is not None and part.get("id"):
                return f"{part['id']}.{axis}", rng
    return None


async def check_gesture(s: Session, hello: dict) -> Check:
    """몸짓·부위: 선언한 몸짓을 실행하는지, 범위 밖 값을 잘라서 받는지."""
    gestures = gestures_of(hello)[:s.opt.gestures_max]
    probe = pose_probe(hello.get("parts"))
    if not gestures and probe is None:
        return Check("gesture", SKIP, "몸짓도 범위 있는 부위도 선언하지 않았다 — 해당 없음(1단계 `express` 만 처리하면 된다)",
                     ["`gesture`·`pose` 는 선언한 부위만 처리하면 된다(6절)"])
    bad, notes = [], []
    missed = [g for g in gestures if (await s.ask({"type": "gesture", "name": g}))[0] is None]
    if missed:
        bad.append(f"선언한 몸짓 {', '.join(missed)} 에 ack 가 없다")
    elif gestures:
        notes.append(f"선언한 몸짓 {len(gestures)}개 모두 ack — 실제로 움직였는지는 밖에서 볼 수 없다")
    else:
        notes.append("선언한 몸짓이 없다(`gestures: []`) — 몸짓 검사는 건너뛰었다")

    if probe is None:
        notes.append("범위를 가진 부위가 없어 `pose` 자르기는 검사하지 않았다")
    else:
        key, (lo, hi) = probe
        inside, _ = await s.ask({"type": "pose", "targets": {key: round((lo + hi) / 2, 3)}, "ms": 300})
        if inside is None:
            bad.append(f"범위 안 `pose` ({key})에 ack 가 없다")
        over = hi + (hi - lo) + 10
        reply, _ = await s.ask({"type": "pose", "targets": {key: over}, "ms": 300})
        if reply is None:
            bad.append(f"범위 밖 `pose` ({key}={over:g})에 ack 가 없다 — 잘랐는지 확인할 수 없다")
        else:
            applied = reply.get("targets") if isinstance(reply.get("targets"), dict) else reply.get("applied")
            if isinstance(applied, dict) and key in applied and _is_num(applied[key]):
                got = float(applied[key])
                if lo <= got <= hi:
                    notes.append(f"범위 밖 {key}={over:g} → 봇이 {got:g} 로 잘라 돌려줬다(범위 {lo:g}~{hi:g})")
                else:
                    bad.append(f"범위 밖 {key}={over:g} 를 자르지 않았다 — ack 의 적용값 {got:g} (범위 {lo:g}~{hi:g})")
            else:
                notes.append(f"범위 밖 {key}={over:g} 에 ack 는 왔지만 적용값이 없어 **잘랐는지 확인할 수 없다** — "
                             "봇이 ack 에 `targets`(실제 적용값)를 넣으면 검사한다")
                notes.append("규약은 ack 에 적용값을 요구하지 않는다 — 여기서 보는 것은 '범위 밖 값에도 끊기지 않고 답한다'까지다")
    return Check("gesture", FAIL if bad else PASS,
                 " / ".join(bad) if bad else "선언한 몸짓 실행, 범위 밖 값에도 ack", notes,
                 {"gestures": len(gestures), "pose_axis": probe[0] if probe else None})


async def check_observe(s: Session, hello: dict) -> Check:
    """관찰: 선언한 주기대로 observe 가 오는지, 센서·측정 이름이 선언과 맞는지."""
    sensors = {str(x.get("id")): x for x in (hello.get("sensors") or []) if isinstance(x, dict) and x.get("id")}
    periodic = {sid: float(x["period_s"]) for sid, x in sensors.items() if _is_num(x.get("period_s"))}
    if not sensors:
        return Check("observe", SKIP, "센서를 선언하지 않았다 — 해당 없음",
                     ["센서가 없는 봇도 규약을 지킨 봇이다(3절)"] if isinstance(hello.get("sensors"), list) else [])

    need = s.opt.observe_periods
    if periodic:
        budget = min(s.opt.observe_wait, max(periodic.values()) * need + 2)
        end = time.monotonic() + budget
        while time.monotonic() < end:
            counts = {sid: sum(1 for _, m in s.observes if m.get("sensor") == sid) for sid in periodic}
            if all(c >= need for c in counts.values()):
                break
            await s.drain(min(0.2, end - time.monotonic()))

    bad, notes = [], []
    seen: dict[str, list[float]] = {}
    for at, msg in s.observes:
        sid = str(msg.get("sensor") or "")
        if sid not in sensors:
            bad.append(f"선언하지 않은 센서 `{sid or '(없음)'}` 의 observe 가 왔다")
            continue
        data = msg.get("data")
        if not isinstance(data, dict) or not data:
            bad.append(f"센서 `{sid}` 의 observe 에 `data` 객체가 없다")
            continue
        declared = {str(m.get("name")) for m in (sensors[sid].get("measures") or []) if isinstance(m, dict)}
        if sid in periodic:   # 주기 센서는 선언한 측정 항목만 보내야 단위가 맞는지 알 수 있다
            extra = [k for k in data if k not in declared]
            if extra:
                bad.append(f"센서 `{sid}` 가 선언하지 않은 측정 항목 {', '.join(extra)} 을 보냈다(단위를 알 수 없다)")
        for k, v in data.items():
            if k in declared and not _is_num(v) and not isinstance(v, (str, bool)):
                bad.append(f"센서 `{sid}`.{k} 값이 숫자·글자가 아니다: {v!r}")
        seen.setdefault(sid, []).append(at)

    for sid, period in periodic.items():
        times = seen.get(sid, [])
        if not times:
            bad.append(f"센서 `{sid}` 가 주기 {period:g}초를 선언했는데 observe 가 한 번도 오지 않았다")
        elif len(times) < 2:
            notes.append(f"센서 `{sid}`: observe 를 한 번만 받아 간격을 재지 못했다(주기 {period:g}초, "
                         f"시험 시간 {s.opt.observe_wait:g}초)")
        else:
            gaps = [b - a for a, b in zip(times, times[1:])]
            mean = statistics.mean(gaps)
            ratio = mean / period if period else 0
            if PERIOD_RANGE[0] <= ratio <= PERIOD_RANGE[1]:
                notes.append(f"센서 `{sid}`: 실제 간격 {mean:.1f}초 (선언 {period:g}초, {len(times)}번 받아 "
                             f"{len(gaps)}번 쟀다)")
            else:
                bad.append(f"센서 `{sid}` 의 선언 주기는 {period:g}초인데 실제 간격이 {mean:.1f}초다"
                           f"(허용 {period * PERIOD_RANGE[0]:.1f}~{period * PERIOD_RANGE[1]:.1f}초)")
    for sid in sensors:
        if sid not in periodic and sid not in seen:
            notes.append(f"센서 `{sid}`: 주기를 선언하지 않았고 시험 중 observe 도 없었다(사건마다 보내는 센서면 맞다)")
    if s.states:
        notes.append(f"`state`(봇 자기 상태) {len(s.states)}번 받음")
    else:
        notes.append("`state`(봇 온도·넘어짐)를 받지 못했다 — 규약이 주기를 정하지는 않는다(4-2절)")
    return Check("observe", FAIL if bad else PASS,
                 " / ".join(dict.fromkeys(bad)) if bad else
                 f"센서 {len(seen)}개가 선언한 주기·단위대로 observe", notes,
                 {"observes": len(s.observes), "sensors": {k: len(v) for k, v in seen.items()}})


async def check_connect(s: Session, hello: dict) -> Check:
    """연결: ping 에 pong, 끊었다 다시 붙으면 hello 부터."""
    bad, notes = [], []
    reply, ms = await s.ask({"type": "ping"}, want="pong")
    if reply is None:
        bad.append(f"`ping` 에 {s.opt.ack_timeout:g}초 안에 `pong` 이 오지 않았다(2-3절: 5초마다 주고받는다)")
    else:
        notes.append(f"`ping` → `pong` {ms:.0f}ms")

    if not s.hub.supports_drop:
        notes.append("이 연결 방식으로는 끊기를 흉내 낼 수 없어 다시 붙기는 검사하지 않았다")
        return Check("connect", FAIL if bad else PASS, " / ".join(bad) if bad else "`ping` 에 `pong`", notes)

    await s.hub.drop()
    s.pre = None
    try:
        pre = await s.hub.accept(s.opt.reconnect_wait)
        s.pre = pre
        again = await s.wait_for(lambda m: m.get("type") == "hello", s.opt.reconnect_wait)
    except (asyncio.TimeoutError, BotGone):
        bad.append(f"끊은 뒤 {s.opt.reconnect_wait:g}초 안에 다시 붙어 `hello` 를 보내지 않았다(2-4절)")
    else:
        notes.append("끊은 뒤 다시 붙어 `hello` 부터 다시 보냈다")
        if emotions_of(again) != emotions_of(hello) or gestures_of(again) != gestures_of(hello):
            notes.append("다시 보낸 `hello` 의 감정·몸짓 목록이 처음과 다르다")
    return Check("connect", FAIL if bad else PASS,
                 " / ".join(bad) if bad else "`ping` 에 `pong`, 끊은 뒤 다시 붙어 `hello`", notes)


async def run_checks(hub: HubLink, opt: Options | None = None) -> Report:
    """9절 표의 항목을 차례로 검사한다."""
    opt = opt or Options()
    s = Session(hub, opt)
    report = Report()
    try:
        s.pre = await hub.accept(opt.hello_wait)
    except asyncio.TimeoutError:
        report.checks.append(Check("hello", FAIL, f"{opt.hello_wait:g}초 안에 봇이 붙지 않았다"))
        report.checks += [Check(k, SKIP, "봇이 붙지 않아 검사하지 못했다") for k in KEYS[1:]]
        return report
    try:
        hello = await s.wait_for(lambda m: m.get("type") == "hello", opt.hello_wait)
    except (asyncio.TimeoutError, BotGone) as e:
        why = ("붙은 뒤 연결이 끊겼다" if isinstance(e, BotGone)
               else f"붙은 뒤 {opt.hello_wait:g}초 안에 `hello` 를 보내지 않았다(2-4절)")
        report.checks.append(Check("hello", FAIL, why))
        report.checks += [Check(k, SKIP, "`hello` 가 없어 검사하지 못했다") for k in KEYS[1:]]
        report.odd = s.odd
        return report

    report.hello = hello
    report.checks.append(check_hello(hello))
    await hub.send({"bcp": BCP, "type": "welcome", "hub": "bcp_check", "granted": GRANTED, "denied": ["driver.state"],
                    "mode": "drive",
                    "observe_period_s": {str(x.get("id")): x.get("period_s")
                                         for x in (hello.get("sensors") or []) if isinstance(x, dict)}})
    try:
        express, latency = await check_express(s, hello)
        report.checks += [express]
        report.checks.append(await check_gesture(s, hello))
        report.checks.append(await check_observe(s, hello))
        report.checks.append(await check_connect(s, hello))
        report.checks.append(latency)
    except BotGone:
        done = {c.key for c in report.checks}
        report.checks += [Check(k, SKIP, "검사 중 봇이 연결을 끊었다") for k in KEYS if k not in done]
    report.checks.sort(key=lambda c: KEYS.index(c.key))
    report.odd = s.odd
    return report


# ─────────────────────────────── 보고서 ───────────────────────────────

def _w(text: str) -> int:
    """터미널에서 차지하는 칸 수. 한글·✅·❌ 는 두 칸, `·` 같은 애매한 글자는 한 칸으로 센다."""
    return sum(2 if unicodedata.east_asian_width(c) in "WF" else 1 for c in text)


def _pad(text: str, width: int) -> str:
    return text + " " * max(0, width - _w(text))


def render_table(report: Report) -> str:
    rows = [(c.title, MARK[c.status], c.reason) for c in report.checks]
    w0 = max([_w("항목")] + [_w(r[0]) for r in rows])
    w1 = max([_w("결과")] + [_w(r[1]) for r in rows])
    lines = [f"{_pad('항목', w0)}  {_pad('결과', w1)}  이유", "─" * (w0 + w1 + 40)]
    for title, mark, reason in rows:
        lines.append(f"{_pad(title, w0)}  {_pad(mark, w1)}  {reason}")
        for note in next(c for c in report.checks if c.title == title).notes:
            lines.append(f"{' ' * (w0 + w1 + 4)}· {note}")
    if report.odd:
        lines.append("")
        lines.append("규약에 없는 메시지:")
        lines += [f"  · {o}" for o in dict.fromkeys(report.odd)]
    counts = {k: sum(1 for c in report.checks if c.status == k) for k in (PASS, FAIL, SKIP)}
    bot = (report.hello or {}).get("bot") or {}
    lines.append("")
    lines.append(f"봇: {bot.get('name') or '?'} ({bot.get('model') or '?'} · {bot.get('id') or '?'}"
                 f" · fw {bot.get('fw') or '?'})" if bot else "봇: 알 수 없음")
    lines.append(f"결과: ✅ {counts[PASS]} · ❌ {counts[FAIL]} · ⚠️ {counts[SKIP]}"
                 + ("  → 규약을 지켰다" if report.ok else "  → 고칠 것이 있다"))
    return "\n".join(lines)


def report_json(report: Report) -> dict:
    bot = (report.hello or {}).get("bot") or {}
    return {
        "bcp": BCP, "tool": "bcp_check", "at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "bot": {k: bot.get(k) for k in ("id", "model", "maker", "fw", "name")},
        "checks": [{"key": c.key, "title": c.title, "status": c.status, "reason": c.reason,
                    "notes": c.notes, "metrics": c.metrics} for c in report.checks],
        "odd_messages": list(dict.fromkeys(report.odd)),
        "summary": {k: sum(1 for c in report.checks if c.status == k) for k in (PASS, FAIL, SKIP)},
        "ok": report.ok,
    }


# ─────────────────────────────── CLI ───────────────────────────────

async def main_async(args) -> int:
    hub = LocalHub(args.host, args.port)
    url = await hub.start()
    out = sys.stderr if args.json else sys.stdout
    print(f"봇 시험기 (BCP v{BCP}) — 봇을 여기에 붙여라: {url}", file=out, flush=True)
    print(f"  첫 메시지는 중계 서버와 같게 {{\"type\":\"auth\",\"device_id\":…,\"key\":…}} (키는 검사하지 않는다)",
          file=out, flush=True)
    child = None
    if args.run:
        command = args.run.replace("{server}", f"http://{args.host}:{hub.port}").replace("{ws}", url)
        print(f"  봇 실행: {command}", file=out, flush=True)
        child = subprocess.Popen(shlex.split(command), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                 cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    opt = Options(ack_timeout=args.ack_timeout, hello_wait=args.hello_wait, observe_wait=args.observe_wait,
                  observe_periods=args.observe_periods, reconnect_wait=args.reconnect_wait)
    try:
        report = await run_checks(hub, opt)
    finally:
        await hub.stop()
        if child is not None:
            if child.poll() is not None:
                print(f"  (봇 프로세스가 먼저 끝났다 — 종료 코드 {child.returncode})", file=out, flush=True)
            child.terminate()
    print("", file=out)
    print(render_table(report), file=out, flush=True)
    if args.json:
        print(json.dumps(report_json(report), ensure_ascii=False, indent=2))
    return 0 if report.ok else 1


def main() -> None:
    p = argparse.ArgumentParser(description="봇 커넥트 규약(BCP) v0.3 적합성 시험기")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=8787, help="0 이면 빈 포트를 골라 쓴다")
    p.add_argument("--run", help='봇을 직접 띄운다. {server} 가 http://호스트:포트 로, {ws} 가 ws 주소로 바뀐다')
    p.add_argument("--json", action="store_true", help="결과를 JSON 으로 (표는 표준오류로)")
    p.add_argument("--ack-timeout", type=float, default=2.0)
    p.add_argument("--hello-wait", type=float, default=60.0)
    p.add_argument("--observe-wait", type=float, default=25.0, help="관찰 주기를 재려고 더 기다리는 시간(초)")
    p.add_argument("--observe-periods", type=int, default=2, help="센서마다 모을 observe 개수")
    p.add_argument("--reconnect-wait", type=float, default=15.0)
    raise SystemExit(asyncio.run(main_async(p.parse_args())))


if __name__ == "__main__":
    main()
