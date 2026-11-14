# SnowBot 중계 서버 (프로토타입, 방법 1)

스노봇(기기)과 차량·폰 앱이 각자 인터넷으로 이 서버에 **WebSocket** 으로 붙고, 서버가 메시지를 건네준다.
설계: [../docs/07-communication-ai.md](../docs/07-communication-ai.md) 8절, 서버 운영: [../docs/09-prototype-server.md](../docs/09-prototype-server.md).
메시지 본문(`hello`·`event`·`express`)은 [../docs/pleos/03-module-protocol.md](../docs/pleos/03-module-protocol.md) 를 그대로 쓴다.

지금 들어 있는 것: 기기 등록, 앱–기기 짝 맺기, 메시지 전달, 접속 상태 알림.
말하기(TTS) 들어 있음 (2026-10-27): ElevenLabs v4 Turbo + 목소리 Suzu, PCM 16kHz 스트리밍. 첫 소리 약 0.21초(연결 재사용, 서버 → 가상 스노봇 실측).
듣기(STT)·대화 들어 있음 (2026-10-28): Scribe v2 Realtime(한국어, 확정은 말하기 버튼을 뗄 때) → Claude Haiku 5.5(생각 끔·effort low, 대답을 받는 대로 문장 단위로 목소리로) → Suzu.
**말 끝 → 첫 소리 보통 1.5초**(중간값 1.46초, 17번 중 1.31~2.54초 — 맥 → 서버, 합성 음성, 기록: [../docs/12-latency-measurements.md](../docs/12-latency-measurements.md)). 대화 설정문은 `snowbot_server/chat.py` 의 SYSTEM, 근거는 [../docs/11-conversation-design.md](../docs/11-conversation-design.md).
대화 기억은 앱별 최근 8번 주고받기, 서버 메모리에만(재시작하면 잊음). 서버 로그에는 말 내용을 남기지 않는다(길이·소리 크기만).
차 상태(`car`: 기어·운전 분·주의 경고)에 따라 대답 문장 수와 내용을 바꾼다(주행 중 2문장).
봇 커넥트 규약(BCP) v0.3 (2026-11-04): 봇 `hello`(부위·센서)를 기억해 앱에 알리고, `observe` 를 앱에 건넨다. 대답 표정은 짝 맺은 켜진 봇 모두에 `express`(v0.3 `emotion`·`gesture` + v0.1 `expr`·`head`, 봇에 없는 몸짓은 뺌)로 보내고, Claude 에게는 연결된 봇들의 표정·몸짓 목록을 상황에 적어 준다. 앱 → 봇 `welcome`·`context` 는 그대로 건넨다.
아직 없는 것: 오래 가는 기억, 집에서 쓰는 폰 앱.

## 주소

바깥에서는 **암호화 연결만** 된다. Caddy(443)가 받아 서버 안의 중계 서버(127.0.0.1:3000)로 넘긴다.

| 용도 | 주소 (프로토타입 서버) |
|---|---|
| WebSocket | `wss://<서버-IP>.sslip.io/ws` |
| 짝 맺기 | `POST https://<서버-IP>.sslip.io/api/pair` |
| 상태 | `GET https://<서버-IP>.sslip.io/health` → `{"ok":true,"devices_online":N}` |

실제 주소는 저장소에 넣지 않는다. 앱은 `app/local.properties` 의 `snowbot.server` 에서 읽는다.

## 짝 맺기 (QR 스티커 → 앱)

1. 서버에서 기기를 등록하면 `device_id`, `device_key`(펌웨어에), `claim_code`(QR 스티커에)가 **한 번만** 나온다. 서버에는 해시만 남는다.
2. 앱이 QR 을 찍어 `POST /api/pair {"device_id","claim_code","kind":"car"|"phone","name"}` → `{"app_id","device_id","token"}`.
3. 이미 토큰이 있는 앱이 기기를 더 묶을 때는 `Authorization: Bearer <token>` 을 붙인다 → 같은 앱에 추가, `token` 은 `null`.

## WebSocket 메시지

접속 후 **첫 메시지는 auth** (10초 안에). 모든 메시지는 JSON 글자 프레임.

| 방향 | 메시지 | 서버가 하는 일 |
|---|---|---|
| 기기 → 서버 | `{"type":"auth","device_id":"…","key":"…"}` | 맞으면 `auth_ok`, 틀리면 닫기(4003). 같은 기기가 다시 붙으면 이전 연결을 닫는다(4000) |
| 앱 → 서버 | `{"type":"auth","token":"…"}` | `auth_ok` + 짝 맺은 기기 목록 `devices:[{device_id, online, hello}]` |
| 기기 → 앱 | `hello`, `event` 등 아무 메시지 | 짝 맺은 앱 모두에 **`device_id` 를 붙여** 전달. `hello` 는 기억했다가 나중에 붙은 앱에 `auth_ok` 로 준다 |
| 앱 → 기기 | `{"type":"express","device_id":"…",…}` 등 | `device_id` 를 떼고 그 기기에 전달. 짝이 아니면 `error not_paired`, 꺼져 있으면 `error device_offline` |
| 서버 → 앱 | `{"type":"presence","device_id":"…","online":true/false}` | 기기가 붙거나 끊길 때 |
| 서버 → 누구나 | `{"type":"error","code":"bad_message"…}` | JSON 이 아닌 메시지 |
| 앱 → 기기 (목소리) | `express` 에 `"say":"할 말"`, 선택 `"voice":"device"|"app"` | 표정 명령을 먼저 전달하고, 서버가 목소리를 만들어 **기기**(기본)에 흘려보낸다. 기기가 꺼져 있거나 `voice:"app"` 이면 **보낸 앱**(차 스피커·화면 속 스노봇)으로 |
| 서버 → 재생할 쪽 | `{"type":"audio_start","id","format":"pcm_16000","sample_rate":16000,"text"}` → 바이너리 프레임들(16비트 모노 PCM) → `{"type":"audio_end","id"}` | 새 말이 오면 하던 말은 끊고 `audio_end` 에 `"cancelled":true`. 실패는 `"error"`. 대화 대답은 문장이 나오는 대로 이어 말하므로 `text` 가 빈 글자 |
| 앱 → 서버 (말하기) | `{"type":"listen_start","device_id","voice"}` → **바이너리 프레임들**(마이크 PCM 16kHz 모노 16비트, 0.1초씩) → `{"type":"listen_end","device_id","voice"}` | 하던 말·생각을 멈추고 듣는다. 끝나면 대답 |
| 서버 → 앱 | `{"type":"heard","text","final":false}` (듣는 중) → `{"type":"heard","text","final":true,"audio_ms","peak"}` | 받아 적은 글자. `peak` 가 0 이면 마이크가 소리를 못 받은 것 |
| 앱 → 서버 (글자로) | `{"type":"chat","device_id","text","voice"}` | 듣기 없이 바로 대답. 앱이 차 듣기(Pleos STT)로 받아 적은 말, 시험용 글자 |
| 앱이 차 음성을 쓸 때 (Pleos) | `listen_start` 에 `"stt":"app"` / 아무 메시지에 `"tts":"app"` | `stt:"app"`: 하던 말·생각만 멈추고 서버는 듣지 않는다(들은 글자는 `chat` 으로 온다). `tts:"app"`: **이 앱으로** 갈 말은 소리 대신 글자로 `{"type":"say","id","text"}`(문장마다) → `{"type":"say_end","id"}`(끊기면 `"cancelled":true`). 기기 스피커로 가는 말은 그대로 서버 목소리 |
| 앱 → 서버 (차 상태) | `{"type":"car","gear":"P\|R\|N\|D\|?","driving_min":정수,"attention":bool}` | 기기로 보내지 않는다. 대답할 때 상황에 넣고 문장 수를 정한다: 주행 중 2(30자 안팎) · 주차 중 3 · 모르면 2. 앱의 `express.say`(스노봇이 먼저 한 말)도 10분 동안 상황에 넣는다 |
| 서버 → 앱·기기 | 앱에 `{"type":"reply_start","expr","head"}`, 기기에 `{"type":"express","expr","head"}` → 목소리(위 audio_*) → 앱에 `{"type":"reply","text","expr","head","timing":{stt_ms,face_ms,llm_ms}}` | 표정 먼저, 말은 문장 단위로. 못 들었거나 Claude 가 실패하면 정해 둔 말(걱정 표정)로 대신하고 `reply.error` |

닫기 코드: 4000 다른 곳에서 다시 접속, 4001 첫 메시지가 auth 아님·시간 초과, 4003 키·토큰 틀림.

## 개발

```bash
cd server
uv venv --python 3.11 .venv && uv pip install --python .venv -r requirements.txt pytest pytest-timeout httpx
.venv/bin/python -m pytest -q tests                      # 시험 46개 (중계 27 + 봇 시험기 19)
SNOWBOT_DB=dev.db .venv/bin/uvicorn --factory snowbot_server.main:create_app --port 3000   # 내 맥에서 띄우기
```

의존성 고정: `uv pip compile requirements.in --python-version 3.11 --python-platform x86_64-unknown-linux-gnu -o requirements.txt`

## 서버에 올리기·관리

```bash
server/deploy/deploy.sh                 # 코드 올리고 서비스 재시작 (처음이면 사용자·폴더·서비스 생성)
ssh snowbot-proto 'cd /opt/snowbot-relay && sudo -u snowbot SNOWBOT_DB=/var/lib/snowbot/relay.db .venv/bin/python -m snowbot_server.manage add-device'
ssh snowbot-proto 'journalctl -u snowbot-relay -f'   # 로그 보기
# 키: /var/lib/snowbot/secrets.env 에 ELEVENLABS_API_KEY(듣기·말하기), ANTHROPIC_API_KEY(대화)
```

서버 위치: 코드 `/opt/snowbot-relay`, DB `/var/lib/snowbot/relay.db`, 서비스 `snowbot-relay`(systemd, 재부팅 시 자동 시작, `snowbot` 사용자로 실행, 127.0.0.1:3000).
앞단 Caddy: 설정 `/etc/caddy/Caddyfile`(원본 [deploy/Caddyfile](deploy/Caddyfile) — 주소는 자리표시, 실제 주소가 든 사본은 `deploy/Caddyfile.local`), 바꾼 뒤 `scp deploy/Caddyfile.local snowbot-proto:/etc/caddy/Caddyfile && ssh snowbot-proto systemctl reload caddy`.

## 가상 스노봇·가상 앱으로 시험

```bash
.venv/bin/python tools/sim.py pair   --server https://<서버> --device-id <ID> --claim <등록코드>
.venv/bin/python tools/sim.py device --server https://<서버> --device-id <ID> --key <기기키> --every 3
.venv/bin/python tools/sim.py app    --server https://<서버> --token <토큰> --expr sleepy --head nod --say "조금 쉬어갈까요?"
.venv/bin/python tools/sim.py bench  --server https://<서버> --token <토큰> --count 50   # 왕복 시간 (device 가 켜져 있어야 함)
.venv/bin/python tools/sim.py talk   --server https://<서버> --token <토큰> --wav 말.wav      # 말하기 버튼 흉내 (16kHz 모노 WAV)
.venv/bin/python tools/sim.py talk   --server https://<서버> --token <토큰> --text "공기 어때?"
# 시험용 말 만들기 (맥): say -v Yuna -o q.aiff "나 좀 졸려" && afconvert -f WAVE -d LEI16@16000 -c 1 q.aiff q.wav
```

## 봇 시험기 (적합성 시험)

봇을 만드는 사람이 규약을 지키는지 스스로 확인하는 도구다([규약 9절](../docs/pleos/03-module-protocol.md#9-적합성-시험-봇-시험기)).
시험기가 **허브(앱) 역할**을 하는 작은 로컬 허브를 띄우고, 봇이 거기 붙으면 9절 표의 항목을 검사해
✅/❌/⚠️ 표로 보여 준다. 봇 쪽 접속 방식은 중계 서버와 같다(첫 메시지 `auth` → `auth_ok` → `hello`)
— 봇은 서버 주소만 바꾸면 고칠 것이 없다. 기기 키는 검사하지 않고, 하나라도 ❌ 면 끝 코드 1 이다.

```bash
.venv/bin/python tools/bcp_check.py --port 8787     # 봇이 ws://127.0.0.1:8787/ws 로 붙기를 기다린다
.venv/bin/python tools/bcp_check.py --json          # 결과를 JSON 으로 (표는 표준오류로)
# 봇을 직접 띄우면서: {server} 가 http://127.0.0.1:포트 로 바뀐다
.venv/bin/python tools/bcp_check.py --run ".venv/bin/python tools/sim.py device --server {server} --device-id X --key Y"
# 브라우저 봇(LED 큐브): 시험기를 먼저 띄우고 led-cube.html#server=ws://127.0.0.1:8787/ws&dev=<ID>&key=<키> 를 연다
```

검사 항목: 봇 설명(필수 필드·부위 범위·최소 감정 4개) · 의도(감정 7가지에 `ack`, 없는 감정 대체) ·
몸짓·부위(선언한 몸짓, 범위 밖 `pose`) · 관찰(선언 주기·단위) · 연결(`ping`→`pong`, 끊은 뒤 `hello` 부터) ·
반응 시간(`express`→`ack`, 목표 300ms).

한계: 봇이 **실제로 움직였는지**는 밖에서 볼 수 없다 — `ack` 가 온 것까지만 본다. 범위 자르기는
봇이 `ack` 에 `targets`(적용값)를 넣을 때만 검사하고, 아니면 "끊기지 않고 답했다"까지만 본다.
관찰 주기는 선언값의 0.5~1.5배를 통과로 보고, 시험 시간이 주기보다 짧으면 "간격을 재지 못했다"고 적는다.

2026-11-08 기준 가상 봇 두 대(`sim.py device`, `led-cube.html`)는 `ack`·`pong`·다시 붙기가 없어
의도 · 몸짓·부위 · 연결 · 반응 시간이 ❌ 로 나온다. 시험기를 느슨하게 하지 않고 그대로 보고한다.
