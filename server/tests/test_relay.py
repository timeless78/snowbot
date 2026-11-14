import json
from contextlib import contextmanager
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

import asyncio

from snowbot_server import chat
from snowbot_server.chat import (DRIVING_SENTENCES, MAX_SENTENCES, PARKED_SENTENCES, Face, Reply, claude_reply,
                                 parse_reply, split_reply)
from snowbot_server.main import CLOSE_DENIED, CLOSE_REPLACED, create_app
from snowbot_server.store import Store


async def fake_tts(text):
    """진짜 ElevenLabs 대신: 글자 수만큼의 가짜 소리 조각 2개."""
    yield b"\x01\x00" * len(text)
    yield b"\x02\x00" * len(text)


class FakeStt:
    """진짜 Scribe 대신: 받은 소리 조각 수를 부분 글자로 알리고, 끝나면 조각들을 글자로 이어 붙인다."""

    def __init__(self, on_partial):
        self.on_partial = on_partial
        self.chunks = []

    async def start(self):
        pass

    async def send(self, pcm):
        self.chunks.append(pcm.decode())
        await self.on_partial(" ".join(self.chunks))

    async def finish(self):
        return " ".join(self.chunks)

    async def close(self):
        pass


class FakeBrain:
    """진짜 Claude 대신: 받은 말을 기록하고 정해진 대답(표정 + 두 문장)을 한다. fail=True 면 실패."""

    def __init__(self):
        self.asked = []
        self.limits = []
        self.fail = False

    async def __call__(self, history, user, limit):
        if self.fail:
            raise RuntimeError("claude down")
        self.asked.append((list(history), user))
        self.limits.append(limit)
        yield Face("happy", "nod")
        yield "좋아요!"
        yield "같이 가요."


@pytest.fixture
def env(tmp_path):
    db = str(tmp_path / "relay.db")
    store = Store(db)
    a = dict(zip(("id", "key", "claim"), store.add_device()))
    b = dict(zip(("id", "key", "claim"), store.add_device()))
    # with 블록 안에서 써야 모든 연결이 한 이벤트 루프를 공유한다(실제 서버와 같게)
    brain = FakeBrain()
    with TestClient(create_app(db, tts=fake_tts, stt=FakeStt, brain=brain)) as client:
        yield SimpleNamespace(client=client, a=a, b=b, brain=brain)


def pair(client, device, claim=None, token=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    body = {"device_id": device["id"], "claim_code": claim or device["claim"], "kind": "car"}
    return client.post("/api/pair", json=body, headers=headers)


def test_pair_needs_right_claim_code(env):
    assert pair(env.client, env.a, claim="WRONG").status_code == 403
    r = pair(env.client, env.a, claim=env.a["claim"].lower())
    assert r.status_code == 200 and r.json()["token"]


def test_pair_with_bad_token_is_rejected(env):
    assert pair(env.client, env.a, token="nope").status_code == 401


def test_second_pair_adds_device_to_same_app(env):
    token = pair(env.client, env.a).json()["token"]
    r = pair(env.client, env.b, token=token)
    assert r.status_code == 200 and r.json()["token"] is None
    with env.client.websocket_connect("/ws") as app:
        app.send_json({"type": "auth", "token": token})
        assert sorted(d["device_id"] for d in app.receive_json()["devices"]) == sorted([env.a["id"], env.b["id"]])


def test_device_with_wrong_key_is_closed(env):
    with env.client.websocket_connect("/ws") as ws:
        ws.send_json({"type": "auth", "device_id": env.a["id"], "key": "wrong"})
        with pytest.raises(WebSocketDisconnect) as e:
            ws.receive_json()
        assert e.value.code == CLOSE_DENIED


def test_messages_flow_both_ways(env):
    a = env.a
    token = pair(env.client, a).json()["token"]
    with env.client.websocket_connect("/ws") as app:
        app.send_json({"type": "auth", "token": token})
        ok = app.receive_json()
        assert ok["type"] == "auth_ok" and ok["devices"][0]["online"] is False

        with env.client.websocket_connect("/ws") as dev:
            dev.send_json({"type": "auth", "device_id": a["id"], "key": a["key"]})
            assert dev.receive_json()["type"] == "auth_ok"
            assert app.receive_json() == {"type": "presence", "device_id": a["id"], "online": True, "hello": None}

            dev.send_json({"type": "hello", "model": "SnowBot SNW-01"})
            assert app.receive_json() == {"type": "hello", "model": "SnowBot SNW-01", "device_id": a["id"]}

            app.send_json({"type": "express", "device_id": a["id"], "expr": "happy", "head": "nod"})
            assert dev.receive_json() == {"type": "express", "expr": "happy", "head": "nod"}

            dev.send_json({"type": "event", "cap": "env", "data": {"co2": 1450}})
            assert app.receive_json()["data"] == {"co2": 1450}

        assert app.receive_json() == {"type": "presence", "device_id": a["id"], "online": False}


def test_app_cannot_reach_unpaired_or_offline_device(env):
    token = pair(env.client, env.a).json()["token"]
    with env.client.websocket_connect("/ws") as app:
        app.send_json({"type": "auth", "token": token})
        app.receive_json()
        app.send_json({"type": "express", "device_id": env.b["id"], "expr": "happy"})
        assert app.receive_json()["code"] == "not_paired"
        app.send_json({"type": "express", "device_id": env.a["id"], "expr": "happy"})
        assert app.receive_json()["code"] == "device_offline"
        app.send_text("not json")
        assert app.receive_json()["code"] == "bad_message"


def test_second_device_connection_replaces_first(env):
    a = env.a
    with env.client.websocket_connect("/ws") as first:
        first.send_json({"type": "auth", "device_id": a["id"], "key": a["key"]})
        first.receive_json()
        with env.client.websocket_connect("/ws") as second:
            second.send_json({"type": "auth", "device_id": a["id"], "key": a["key"]})
            assert second.receive_json()["type"] == "auth_ok"
            with pytest.raises(WebSocketDisconnect) as e:
                first.receive_json()
            assert e.value.code == CLOSE_REPLACED
            assert env.client.get("/health").json()["devices_online"] == 1


@contextmanager
def connect_pair(env):
    """짝 맺은 앱과 켜진 기기를 연결해 (app, dev) 를 내준다. presence 까지 읽어 둔다."""
    a = env.a
    token = pair(env.client, a).json()["token"]
    with env.client.websocket_connect("/ws") as app, env.client.websocket_connect("/ws") as dev:
        app.send_json({"type": "auth", "token": token})
        app.receive_json()
        dev.send_json({"type": "auth", "device_id": a["id"], "key": a["key"]})
        dev.receive_json()
        app.receive_json()  # presence online
        yield app, dev


def face_msg(expr, head=None):
    """서버가 대답 표정을 봇에 보내는 모양 (규약 v0.3 + v0.1 필드)."""
    msg = {"bcp": "0.3", "type": "express", "emotion": expr, "intensity": 0.6, "expr": expr}
    if head:
        msg.update(gesture=head, head=head)
    return msg


def read_speech(ws):
    """audio_start → 소리 조각들 → audio_end 를 읽어 (글자, 소리) 를 돌려준다."""
    start = ws.receive_json()
    assert start["type"] == "audio_start" and start["format"] == "pcm_16000"
    audio = b""
    while True:
        msg = ws.receive()
        if msg.get("bytes") is not None:
            audio += msg["bytes"]
            continue
        assert json.loads(msg["text"]) == {"type": "audio_end", "id": start["id"]}
        return start["text"], audio


def spoken(audio):
    """fake_tts 가 만든 소리에서 원래 글자 수들을 거꾸로 읽는다 — 문장마다 (글자 수 × 조각 2개)."""
    return len(audio) // 4


def test_say_is_spoken_on_the_device(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "express", "device_id": env.a["id"], "expr": "happy", "say": "안녕"})
        assert dev.receive_json() == {"type": "express", "expr": "happy", "say": "안녕"}
        text, audio = read_speech(dev)
        assert text == "안녕" and audio == b"\x01\x00\x01\x00\x02\x00\x02\x00"


def test_say_goes_to_the_app_when_asked(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "express", "device_id": env.a["id"], "expr": "sleepy", "say": "쉬어요", "voice": "app"})
        assert dev.receive_json() == {"type": "express", "expr": "sleepy", "say": "쉬어요"}
        assert read_speech(app)[0] == "쉬어요"


def test_say_goes_to_the_app_when_device_is_off(env):
    token = pair(env.client, env.a).json()["token"]
    with env.client.websocket_connect("/ws") as app:
        app.send_json({"type": "auth", "token": token})
        app.receive_json()
        app.send_json({"type": "express", "device_id": env.a["id"], "expr": "worried", "say": "괜찮아요?"})
        assert app.receive_json()["code"] == "device_offline"
        assert read_speech(app)[0] == "괜찮아요?"


def read_said(ws, until="say_end"):
    """앱이 자기 목소리로 말할 때: say 글자들과 다른 메시지들을 say_end 까지 모은다."""
    said, others = [], []
    while True:
        msg = ws.receive_json()
        if msg["type"] == "say":
            said.append(msg["text"])
        elif msg["type"] == until:
            assert "cancelled" not in msg
            return said, others
        else:
            others.append(msg)


def test_app_with_its_own_voice_gets_text_not_sound(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "express", "device_id": env.a["id"], "expr": "sleepy", "say": "쉬어요",
                       "voice": "app", "tts": "app"})
        assert dev.receive_json() == {"type": "express", "expr": "sleepy", "say": "쉬어요"}   # tts 는 기기로 안 감
        assert read_said(app) == (["쉬어요"], [])


def test_device_still_speaks_with_server_voice_when_app_has_its_own(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "express", "device_id": env.a["id"], "expr": "happy", "say": "안녕", "tts": "app"})
        dev.receive_json()
        assert read_speech(dev)[0] == "안녕"


def test_app_that_listens_itself_sends_text_and_hears_text_back(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "listen_start", "device_id": env.a["id"], "stt": "app", "tts": "app", "voice": "app"})
        app.send_json({"type": "chat", "device_id": env.a["id"], "text": "공기 어때", "tts": "app", "voice": "app"})
        said, others = read_said(app)
        assert said == ["좋아요!", "같이 가요."]
        assert others[0] == {"type": "reply_start", "device_id": env.a["id"], "expr": "happy", "head": "nod"}
        assert all(m["type"] != "heard" for m in others)   # 서버는 듣지 않았다
        assert dev.receive_json() == face_msg("happy", "nod")
        assert env.brain.asked[0][1].endswith("[운전자] 공기 어때")


def test_parse_reply_reads_tag_and_survives_bad_ones():
    assert parse_reply("[happy nod] 좋아요!") == Reply("좋아요!", "happy", "nod", "[happy nod] 좋아요!")
    assert parse_reply("[sleepy none] 쉬어요") == Reply("쉬어요", "sleepy", None, "[sleepy none] 쉬어요")
    r = parse_reply("[dancing spin] 춤춰요")
    assert (r.text, r.expr, r.head) == ("춤춰요", "neutral", None)
    r = parse_reply("태그 없는 말")
    assert (r.text, r.expr, r.head) == ("태그 없는 말", "neutral", None)


def test_listen_then_reply_with_face_and_voice(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "listen_start", "device_id": env.a["id"]})
        app.send_bytes("공기".encode())
        assert app.receive_json() == {"type": "heard", "text": "공기", "final": False}
        app.send_bytes("어때".encode())
        assert app.receive_json() == {"type": "heard", "text": "공기 어때", "final": False}
        app.send_json({"type": "listen_end", "device_id": env.a["id"]})
        final = app.receive_json()
        assert (final["type"], final["text"], final["final"]) == ("heard", "공기 어때", True)
        assert final["audio_ms"] == len("공기어때".encode()) // 32 and final["peak"] > 0   # 마이크 점검 값

        assert app.receive_json() == {"type": "reply_start", "device_id": env.a["id"], "expr": "happy", "head": "nod"}
        reply = app.receive_json()
        assert reply["type"] == "reply" and (reply["text"], reply["expr"], reply["head"]) == ("좋아요! 같이 가요.", "happy", "nod")
        assert set(reply["timing"]) == {"stt_ms", "face_ms", "llm_ms"}
        assert dev.receive_json() == face_msg("happy", "nod")
        assert spoken(read_speech(dev)[1]) == len("좋아요!") + len("같이 가요.")   # 두 문장 모두 한 번의 말로
        assert env.brain.asked[0][1].endswith("[운전자] 공기 어때")


def test_situation_carries_sensor_values_and_history_grows(env):
    with connect_pair(env) as (app, dev):
        dev.send_json({"type": "event", "cap": "env", "data": {"co2": 1620, "temp": 27.4}})
        app.receive_json()
        app.send_json({"type": "chat", "device_id": env.a["id"], "text": "답답해"})
        assert app.receive_json()["type"] == "reply_start"
        assert app.receive_json()["type"] == "reply"
        dev.receive_json()
        read_speech(dev)
        app.send_json({"type": "chat", "device_id": env.a["id"], "text": "고마워"})
        app.receive_json()
        history, user = env.brain.asked[1]
        assert "CO₂ 1620ppm" in user and "27.4°C" in user and "스노봇 몸체 켜짐" in user
        assert [m["role"] for m in history] == ["user", "assistant"]
        assert history[1]["content"] == "[happy nod] 좋아요! 같이 가요."


def test_silence_gets_a_not_heard_reply_without_claude(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "listen_start", "device_id": env.a["id"], "voice": "app"})
        app.send_json({"type": "listen_end", "device_id": env.a["id"], "voice": "app"})
        assert app.receive_json() == {"type": "heard", "text": "", "final": True, "audio_ms": 0, "peak": 0}
        assert app.receive_json()["expr"] == "worried"           # reply_start
        assert "못 들었어요" in app.receive_json()["text"]          # reply
        assert spoken(read_speech(app)[1]) == len("미안해요, 잘 못 들었어요. 다시 말해 줄래요?")  # 목소리는 앱으로
        assert env.brain.asked == []
        assert dev.receive_json() == face_msg("worried")


def test_claude_failure_still_answers(env):
    env.brain.fail = True
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "chat", "device_id": env.a["id"], "text": "안녕"})
        assert app.receive_json()["type"] == "reply_start"
        reply = app.receive_json()
        assert reply["error"] == "claude down" and reply["expr"] == "worried" and reply["text"]


def collect(pieces):
    async def gen():
        for p in pieces:
            yield p

    async def run():
        return [x async for x in split_reply(gen())]

    return asyncio.run(run())


def test_split_reply_streams_face_then_sentences():
    assert collect(["[hap", "py no", "d] 좋아요! 같이", " 가요. 출발", "해요"]) == [
        Face("happy", "nod"), "좋아요!", "같이 가요.", "출발해요"]
    assert collect(["그냥 말이에요. 끝"]) == [Face(), "그냥 말이에요.", "끝"]
    assert collect(["[sleepy none]", " 졸려요..."]) == [Face("sleepy", None), "졸려요..."]
    assert collect([]) == [Face()]


def test_claude_reply_stops_after_two_sentences_not_counting_short_cries(monkeypatch):
    async def fake_text(history, user):
        yield "[happy nod] 좋아요! 제일 추운 바다는? 썰렁해요, 제 고향이에요! 하나 더 할까요?"

    monkeypatch.setattr(chat, "_claude_text", fake_text)

    async def run():
        return [x async for x in claude_reply([], "농담 해 줘")]

    assert asyncio.run(run()) == [Face("happy", "nod"), "좋아요!", "제일 추운 바다는?", "썰렁해요, 제 고향이에요!"]


def test_claude_reply_lets_a_question_at_the_limit_get_its_answer(monkeypatch):
    async def fake_text(history, user):
        yield "[happy nod] 오늘은 제가 개그 담당이에요. 제일 추운 바다는? 썰렁해요! 또 할까요? 네."

    monkeypatch.setattr(chat, "_claude_text", fake_text)

    async def run():
        return [x async for x in claude_reply([], "아재개그")]

    assert asyncio.run(run()) == [Face("happy", "nod"), "오늘은 제가 개그 담당이에요.", "제일 추운 바다는?", "썰렁해요!"]


def chat_once(env, app, dev, text):
    app.send_json({"type": "chat", "device_id": env.a["id"], "text": text})
    assert app.receive_json()["type"] == "reply_start"
    assert app.receive_json()["type"] == "reply"
    dev.receive_json()
    read_speech(dev)


def test_car_state_shapes_the_situation_and_reply_length(env):
    with connect_pair(env) as (app, dev):
        chat_once(env, app, dev, "안녕")                          # 차 상태를 모르면 기본 두 문장
        app.send_json({"type": "car", "gear": "D", "driving_min": 135, "attention": True})
        chat_once(env, app, dev, "심심해")
        app.send_json({"type": "car", "gear": "P", "driving_min": "많이", "attention": "yes"})   # 이상한 값은 버린다
        chat_once(env, app, dev, "다 왔다")
        assert env.brain.limits == [2, 2, 3]
        driving, parked = env.brain.asked[1][1], env.brain.asked[2][1]
        assert "차 주행 중(D). 출발한 지 135분." in driving and "주의 경고등" in driving
        assert "차 주차 중(P)." in parked and "주의 경고등" not in parked and "출발한 지" not in parked
        assert "차 " not in env.brain.asked[0][1]


def test_lit_warnings_reach_the_situation(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "car", "gear": "D", "driving_min": 20, "attention": False,
                       "warnings": ["엔진 과열", "타이어 공기압"]})
        chat_once(env, app, dev, "왜 경고등 켜졌어?")
        app.send_json({"type": "car", "gear": "D", "driving_min": 21, "attention": False, "warnings": "TPMS"})  # 목록이 아니면 버린다
        chat_once(env, app, dev, "이제 괜찮아?")
        app.send_json({"type": "car", "gear": "D", "driving_min": 22, "attention": False, "warnings": [1, " ", "x" * 50]})
        chat_once(env, app, dev, "또?")
        assert "켜진 경고등: 엔진 과열, 타이어 공기압." in env.brain.asked[0][1]
        assert "켜진 경고등" not in env.brain.asked[1][1]
        assert "켜진 경고등: " + "x" * 30 + "." in env.brain.asked[2][1]


def test_a_line_said_first_is_remembered_in_the_situation(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "express", "device_id": env.a["id"], "expr": "worried", "say": "쉼터에서 쉬어 가요."})
        assert dev.receive_json()["type"] == "express"
        read_speech(dev)
        chat_once(env, app, dev, "그래 알았어")
        assert '스노봇이 먼저 한 말: "쉼터에서 쉬어 가요."' in env.brain.asked[0][1]


def test_reply_length_follows_driving_or_parked(env):
    """대답 길이는 주행/주차로 정한다. 2026-11-09 에 "바쁜 정도"(load)는 없앴다."""
    with connect_pair(env) as (app, dev):
        chat_once(env, app, dev, "안녕")                                   # 차 상태를 모를 때
        app.send_json({"type": "car", "gear": "D", "driving_min": 10, "attention": False})
        chat_once(env, app, dev, "안녕")
        app.send_json({"type": "car", "gear": "P", "driving_min": 10, "attention": False})
        chat_once(env, app, dev, "안녕")
        assert env.brain.limits == [MAX_SENTENCES, DRIVING_SENTENCES, PARKED_SENTENCES]


def test_an_old_app_sending_load_is_ignored(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "car", "gear": "D", "driving_min": 10, "attention": False, "load": 3})
        chat_once(env, app, dev, "안녕")
        assert env.brain.limits == [DRIVING_SENTENCES]
        assert "바쁜" not in env.brain.asked[0][1]


def test_the_car_summary_reaches_the_situation(env):
    with connect_pair(env) as (app, dev):
        app.send_json({"type": "car", "gear": "D", "driving_min": 10, "attention": False,
                       "summary": "경고등 없음; 주행 가능 거리 38km; 목적지까지 52km · 40분"})
        chat_once(env, app, dev, "얼마나 더 갈 수 있어?")
        app.send_json({"type": "car", "gear": "D", "driving_min": 11, "attention": False, "summary": 5})
        chat_once(env, app, dev, "또?")
        assert "차 상태: 경고등 없음; 주행 가능 거리 38km; 목적지까지 52km · 40분." in env.brain.asked[0][1]
        assert "차 상태:" not in env.brain.asked[1][1]        # 글자가 아니면 버린다


SNOW_HELLO = {"bcp": "0.3", "type": "hello", "bot": {"id": "x", "model": "SnowBot", "name": "스노봇"},
              "parts": [{"id": "head", "kind": "neck"}],
              "expressions": {"emotions": ["neutral", "happy", "worried"], "gestures": ["nod", "look_left"]}}
CUBE_HELLO = {"bcp": "0.3", "type": "hello", "bot": {"id": "y", "model": "LED Cube", "name": "큐브"},
              "parts": [{"id": "ring", "kind": "light"}],
              "expressions": {"emotions": ["neutral", "happy", "worried", "alert"], "gestures": []}}


class LookLeftBrain(FakeBrain):
    async def __call__(self, history, user, limit):
        self.asked.append((list(history), user))
        self.limits.append(limit)
        yield Face("worried", "look_left")
        yield "뒷자리에 가방 있어요."


def test_two_bots_get_the_same_feeling_in_their_own_body(tmp_path):
    db = str(tmp_path / "relay.db")
    store = Store(db)
    a = dict(zip(("id", "key", "claim"), store.add_device()))
    b = dict(zip(("id", "key", "claim"), store.add_device()))
    brain = LookLeftBrain()
    with TestClient(create_app(db, tts=fake_tts, stt=FakeStt, brain=brain)) as client:
        token = pair(client, a).json()["token"]
        assert pair(client, b, token=token).json()["token"] is None      # 같은 앱에 두 번째 봇
        with client.websocket_connect("/ws") as app, client.websocket_connect("/ws") as snow, \
                client.websocket_connect("/ws") as cube:
            app.send_json({"type": "auth", "token": token})
            app.receive_json()
            for ws, dev, hello in ((snow, a, SNOW_HELLO), (cube, b, CUBE_HELLO)):
                ws.send_json({"type": "auth", "device_id": dev["id"], "key": dev["key"]})
                ws.receive_json()
                assert app.receive_json()["type"] == "presence"
                ws.send_json(hello)
                assert app.receive_json()["type"] == "hello"
            cube.send_json({"bcp": "0.3", "type": "observe", "sensor": "env", "data": {"humidity": 78}})
            seen = app.receive_json()
            assert seen["type"] == "observe" and seen["device_id"] == b["id"]   # 봇이 주는 정보는 앱(허브)으로
            app.send_json({"type": "chat", "device_id": a["id"], "text": "뒤에 뭐 있어?", "tts": "app"})
            assert snow.receive_json() == {"bcp": "0.3", "type": "express", "emotion": "worried", "intensity": 0.6,
                                           "expr": "worried", "gesture": "look_left", "head": "turn_left"}
            assert cube.receive_json() == {"bcp": "0.3", "type": "express", "emotion": "worried", "intensity": 0.6,
                                           "expr": "worried"}                    # 큐브는 고개가 없다
        situation = brain.asked[0][1]
        assert "차 안 습도 78%." in situation                                   # 큐브가 느낀 습도
        assert "연결된 봇: 스노봇(표정 neutral,happy,worried; 몸짓 nod,look_left)" in situation
        assert "큐브(표정 neutral,happy,worried,alert; 몸짓 없음)" in situation


def test_v01_hello_still_describes_the_body():
    from snowbot_server.main import bot_body
    hello = {"type": "hello", "model": "SnowBot SNW-01 (sim)",
             "capabilities": [{"id": "express", "expr": ["neutral", "happy"], "head": ["nod", "turn_left"]}]}
    assert bot_body(hello) == ("SnowBot SNW-01 (sim)", ["neutral", "happy"], ["nod", "look_left"])
