"""봇 시험기(tools/bcp_check.py) 시험 — 규약을 지키는 가짜 봇과 어기는 가짜 봇으로 판정을 확인한다."""
import asyncio
import copy
import json

import pytest

from tools.bcp_check import (ALL_EMOTIONS, FAIL, PASS, SKIP, BotEnd, Dropped, MemoryHub, Options, render_table,
                             report_json, run_checks)

GOOD_HELLO = {
    "bcp": "0.3", "type": "hello",
    "bot": {"id": "FAKE-01", "model": "Fake Bot", "maker": "test", "fw": "0.0", "name": "가짜봇"},
    "place": {"where": "dashboard_center", "facing": "cabin", "power": "usb"},
    "parts": [
        {"id": "eyelid", "kind": "eyelid", "count": 2, "axes": {"open": [0, 1]}, "fastest_ms": 120},
        {"id": "brow", "kind": "brow", "count": 2, "axes": {"lift": [-1, 1], "tilt": [-1, 1]}},
        {"id": "head", "kind": "neck", "axes": {"pan": [-40, 40], "tilt": [-15, 20]}, "unit": "deg", "max_dps": 120},
        {"id": "spk", "kind": "speaker", "voice": "hub_stream"},
    ],
    "sensors": [{"id": "env", "measures": [{"name": "co2", "unit": "ppm"}, {"name": "temp", "unit": "C"}],
                 "period_s": 0.2}],
    "expressions": {"emotions": list(ALL_EMOTIONS), "gestures": ["nod", "shake"]},
    "actions": [],
    "wants": ["car.motion"],
}

CUBE_HELLO = {   # LED 큐브처럼 몸짓도 축도 없는 봇
    "bcp": "0.3", "type": "hello",
    "bot": {"id": "CUBE-01", "model": "LED Cube", "maker": "demo", "fw": "sim", "name": "큐브"},
    "place": {"where": "dashboard_right", "facing": "cabin", "power": "usb"},
    "parts": [{"id": "ring", "kind": "light", "leds": 12, "color": "rgb", "patterns": ["solid", "breathe"]},
              {"id": "buzz", "kind": "speaker", "voice": "chime"}],
    "sensors": [{"id": "env", "measures": [{"name": "humidity", "unit": "%"}], "period_s": 0.2}],
    "expressions": {"emotions": ["neutral", "happy", "worried", "alert", "sleepy"], "gestures": []},
    "actions": [], "wants": ["car.cabin"],
}


def ranges(hello):
    """hello 의 부위 축 범위 → {"head.pan": (-40, 40), …}"""
    out = {}
    for part in hello["parts"]:
        for axis, rng in (part.get("axes") or {}).items():
            out[f"{part['id']}.{axis}"] = (rng[0], rng[1])
    return out


async def fake_bot(hub, hello=GOOD_HELLO, *, ack=True, pong=True, clip=True, report_applied=True,
                   reconnect=True, sensor=None, data=None, period=0.2, connections=2):
    """가짜 봇. 기본은 규약을 지킨다. 인자를 끄면 그 항목을 어긴다."""
    limits = ranges(hello)
    sensor = sensor or (hello["sensors"][0]["id"] if hello.get("sensors") else "env")
    if data is None:
        data = {m["name"]: 1.0 for s in hello.get("sensors") or [] for m in s.get("measures") or []}

    async def observe(end):
        while period:                      # period 가 None 이면 관찰을 아예 보내지 않는다
            await end.send({"bcp": "0.3", "type": "observe", "sensor": sensor, "data": dict(data)})
            await asyncio.sleep(period)

    for _ in range(connections if reconnect else 1):
        end = hub.connect()
        await end.send(copy.deepcopy(hello))
        task = asyncio.create_task(observe(end))
        try:
            while True:
                msg = await end.recv()
                kind, mid = msg.get("type"), msg.get("id")
                if kind == "ping":
                    if pong:
                        await end.send({"bcp": "0.3", "type": "pong", "id": mid})
                elif kind == "pose" and ack:
                    applied = {}
                    for key, value in (msg.get("targets") or {}).items():
                        lo, hi = limits.get(key, (value, value))
                        applied[key] = max(lo, min(hi, value)) if clip else value
                    reply = {"bcp": "0.3", "type": "ack", "id": mid}
                    if report_applied:
                        reply["targets"] = applied
                    await end.send(reply)
                elif mid is not None and ack:
                    await end.send({"bcp": "0.3", "type": "ack", "id": mid})
        except Dropped:
            pass
        finally:
            task.cancel()


def drive(bot, opt=None):
    """가짜 봇과 시험기를 같이 돌려 보고서를 받는다."""
    opt = opt or Options(ack_timeout=0.3, hello_wait=1.0, observe_wait=0.8, reconnect_wait=0.4)

    async def main():
        hub = MemoryHub()
        task = asyncio.create_task(bot(hub))
        try:
            return await run_checks(hub, opt)
        finally:
            task.cancel()

    return asyncio.run(main())


def status(report):
    return {c.key: c.status for c in report.checks}


def reason(report, key):
    return next(c for c in report.checks if c.key == key).reason


def test_good_bot_passes_every_check():
    report = drive(fake_bot)
    assert status(report) == {k: PASS for k in ("hello", "express", "gesture", "observe", "connect", "latency")}
    assert report.ok
    latency = next(c for c in report.checks if c.key == "latency")
    assert 0 <= latency.metrics["max_ms"] < 300


def test_bot_without_ack_fails_intent_gesture_and_latency():
    report = drive(lambda hub: fake_bot(hub, ack=False))
    assert status(report)["hello"] == PASS
    assert status(report)["express"] == FAIL
    assert status(report)["gesture"] == FAIL
    assert status(report)["latency"] == FAIL
    assert "ack" in reason(report, "express")


def test_hello_problems_are_reported():
    hello = copy.deepcopy(GOOD_HELLO)
    del hello["bot"]["id"]
    hello["expressions"]["emotions"] = ["neutral", "happy"]
    hello["parts"][2].pop("axes")                      # 목인데 범위를 안 알림
    report = drive(lambda hub: fake_bot(hub, hello=hello))
    assert status(report)["hello"] == FAIL
    why = reason(report, "hello")
    assert "bot.id" in why and "worried" in why and "head" in why


def test_look_shape_must_be_right_but_extra_marks_are_only_noted():
    hello = copy.deepcopy(GOOD_HELLO)
    hello["look"] = {"viewbox": [120, 160], "color": "#fff", "outline": "M0 0 Z",
                     "marks": {"eyes": [[46, 44], [74, 44]], "nose": [[60, 56]], "ears": [[30, 10]]}}
    report = drive(lambda hub: fake_bot(hub, hello=hello))
    check = next(c for c in report.checks if c.key == "hello")
    notes = " ".join(check.notes)
    assert check.status == PASS and "`look.marks` 의 ears 은" in notes   # nose 는 규약 자리라 안 걸리고, ears 만 모르는 자리

    hello["look"]["viewbox"] = "120x160"
    hello["look"]["marks"]["eyes"] = [46, 44]
    report = drive(lambda hub: fake_bot(hub, hello=hello))
    why = reason(report, "hello")
    assert "viewbox" in why and "marks.eyes" in why


def test_bot_that_does_not_pong_or_reconnect_fails_connection():
    report = drive(lambda hub: fake_bot(hub, pong=False, reconnect=False))
    assert status(report)["connect"] == FAIL
    why = reason(report, "connect")
    assert "pong" in why and "hello" in why


def test_reconnect_alone_is_not_enough_without_pong():
    report = drive(lambda hub: fake_bot(hub, pong=False))
    assert status(report)["connect"] == FAIL
    assert "pong" in reason(report, "connect")


def test_observe_with_undeclared_sensor_fails():
    report = drive(lambda hub: fake_bot(hub, sensor="weather"))
    assert status(report)["observe"] == FAIL
    assert "weather" in reason(report, "observe")


def test_observe_with_undeclared_measure_fails():
    report = drive(lambda hub: fake_bot(hub, data={"co2": 800, "pm2_5": 12}))
    assert status(report)["observe"] == FAIL
    assert "pm2_5" in reason(report, "observe")


def test_observe_period_must_match_what_was_declared():
    report = drive(lambda hub: fake_bot(hub, period=0.02))
    assert status(report)["observe"] == FAIL
    assert "주기" in reason(report, "observe")


def test_no_observe_at_all_fails():
    report = drive(lambda hub: fake_bot(hub, period=None))
    assert status(report)["observe"] == FAIL
    assert "한 번도" in reason(report, "observe")


def test_one_observe_passes_but_says_the_interval_was_not_measured():
    """시험 시간이 선언 주기보다 짧을 때 — 한 주기만 보고 한계를 적는다."""
    report = drive(lambda hub: fake_bot(hub, period=60))
    check = next(c for c in report.checks if c.key == "observe")
    assert check.status == PASS
    assert "간격을 재지 못했다" in " ".join(check.notes)


def test_value_outside_the_range_must_be_clipped():
    report = drive(lambda hub: fake_bot(hub, clip=False))
    assert status(report)["gesture"] == FAIL
    assert "자르지" in reason(report, "gesture")


def test_clipping_is_unverifiable_when_ack_says_nothing():
    report = drive(lambda hub: fake_bot(hub, report_applied=False))
    assert status(report)["gesture"] == PASS
    notes = " ".join(next(c for c in report.checks if c.key == "gesture").notes)
    assert "적용값" in notes       # 한계를 적어 둔다


def test_bot_without_gestures_or_axes_skips_that_check():
    report = drive(lambda hub: fake_bot(hub, hello=CUBE_HELLO))
    assert status(report)["gesture"] == SKIP
    assert status(report)["express"] == PASS      # 선언 안 한 감정도 ack 하면 통과
    assert report.ok                              # 건너뜀은 실패가 아니다


def test_substitution_note_tells_which_emotions_were_not_declared():
    report = drive(lambda hub: fake_bot(hub, hello=CUBE_HELLO))
    notes = " ".join(next(c for c in report.checks if c.key == "express").notes)
    assert "greet" in notes and "guard" in notes


def test_bot_that_never_says_hello_fails_everything_else_as_skipped():
    async def mute_bot(hub):
        hub.connect()
        await asyncio.sleep(60)

    report = drive(mute_bot)
    assert status(report)["hello"] == FAIL
    assert [c.status for c in report.checks if c.key != "hello"] == [SKIP] * 5


def test_table_and_json_are_rendered():
    report = drive(fake_bot)
    table = render_table(report)
    assert "봇 설명" in table and "✅" in table and "반응 시간" in table
    data = json.loads(json.dumps(report_json(report), ensure_ascii=False))
    assert data["ok"] is True and data["bot"]["id"] == "FAKE-01"
    assert [c["key"] for c in data["checks"]] == ["hello", "express", "gesture", "observe", "connect", "latency"]
    assert data["summary"]["pass"] == 6


def test_memory_hub_raises_dropped_when_the_hub_closes(   # 시험 길잡이: 가짜 연결의 약속
):
    async def main():
        hub = MemoryHub()
        end = hub.connect()
        await hub.accept(0.5)
        await end.send({"type": "hello"})
        assert (await hub.recv(0.5))["type"] == "hello"
        await hub.drop()
        with pytest.raises(Dropped):
            await end.recv()

    asyncio.run(main())


def test_bot_end_is_the_only_way_the_fake_bot_talks():
    assert hasattr(BotEnd, "send") and hasattr(BotEnd, "recv")
