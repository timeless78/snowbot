# 스노봇 차량 앱 (Pleos Connect, 방법 1 프로토타입)

차량 앱이 중계 서버([../server/](../server/README.md))에 붙어 짝 맺은 스노봇에 표정 명령을 보내고, 스노봇 센서 값(CO₂)을 받는다.
**스노봇이 없어도 화면 속 가상 스노봇이 같은 표정을 짓는다**(모듈 없이도 동작 — 공모전 요구).

지금 들어 있는 것 (2026-10-27~28):
- 짝 맺기: QR 스티커의 기기 번호·등록 코드 입력 → 토큰 저장 → 자동 접속(끊기면 1~30초 간격 재접속)
- 스노봇 켜짐/꺼짐 표시 (서버의 presence)
- 버튼 3개: 인사하기(happy·nod), 쉬어가기 제안(sleepy·nod), 걱정하기(worried·shake) → 화면 속 스노봇 + 실물 스노봇
- 공기질 스킬: CO₂ 1,500ppm 을 넘는 순간 걱정 표정 + "창문을 조금 열어 볼까요?" (1,200 아래로 내려가면 다시 알릴 수 있음)

- 목소리 (2026-10-27): 버튼·환기 제안의 말을 서버가 Suzu 목소리로 만들어 보낸다. "목소리" 버튼으로 **스노봇 스피커 / 차 스피커** 선택(스노봇이 꺼져 있으면 차 스피커). 차 스피커는 `VoicePlayer`(AudioTrack, PCM 16kHz) — 말하는 동안만 오디오 포커스(GAIN_TRANSIENT_MAY_DUCK, USAGE_ASSISTANT)를 빌리고 끝나면 돌려준다. 에뮬레이터 로그로 포커스 허가·반납 확인

- 말하기 (2026-10-28): **"스노봇과 말하기"** 버튼을 누르면 듣기 시작(표정 alert) → 한 번 더 누르거나 12초가 지나면 끝. 마이크 소리(`MicRecorder`, PCM 16kHz)를 서버로 보내고, 받아 적은 글자를 "나: …"로 보여 준다. 대답이 오면 표정 → 말풍선 → 목소리. 마이크 권한(RECORD_AUDIO)은 처음 누를 때 묻는다.
  - 마이크 없는 개발 PC(맥 스튜디오)용 시험: 디버그 빌드에서 `adb shell "am start -n io.snowbot.hub/.MainActivity --es chat '나 좀 졸려'"` → 글자로 말 건 것과 같다. 바깥 큰따옴표가 없으면 adb 가 띄어쓰기에서 잘라 첫 낱말만 들어간다.
  - 에뮬레이터가 맥 마이크를 쓰려면 `adb emu avd hostmicon` + 맥에 마이크가 있어야 한다(서버 로그 `peak=0` 이면 소리 없음).

- Pleos 음성 (2026-11-02, `PleosVoice`): 듣기·말하기를 차의 **Pleos SpeechToText·TextToSpeech 3.3.1 차 안 엔진(ON_DEVICE)** 으로 한다. 권한 `STT_SERVICE`·`TTS_SERVICE`(보호 수준 normal, 설치하면 허용), 앱 등록·인터넷 불필요.
  - 말하기 버튼 → 차 엔진이 듣고 → 다 들은 글자를 서버에 `chat` 으로 보낸다(소리는 서버로 가지 않는다). `listen_start` 에 `stt:"app"` 을 붙여 하던 말만 멈춘다.
  - 엔진이 이름을 "스노프"로 알아들어서(11/2 시험 6번 중 6번) `PleosVoice.fixName` 이 "스노봇"으로 고친다. 시험용 `--es chat` 글자도 같은 길을 지난다.
  - 메시지에 `tts:"app"` 을 붙이면 차 스피커로 갈 말을 서버가 글자(`say`)로 보내고 차 엔진이 문장 차례로 읽는다(기본 화자 seoyoon). 스노봇 몸체 스피커로 가는 말은 몸체가 Pleos 밖이라 계속 서버 목소리.
  - 엔진 초기화가 실패하면(Pleos 가 아닌 기기) 위의 서버 듣기(`MicRecorder`)·서버 목소리(`VoicePlayer`)로 돌아간다.
  - 확인 (11/2 에뮬레이터): 글자 "스노프, 오늘 기분 어때?" → 화면 "나: 스노봇, …" → 대답 두 문장을 Pleos 목소리로 차례로 읽음(onDone). 버튼으로 듣기 → 차 마이크 열림 → 말 없이 끝내자 "잘 못 들었어요"를 Pleos 목소리로. 사람 목소리 듣기는 아직 사용자 확인 필요.

- 차량 데이터 (2026-10-28, `CarState`·`CarCoach`): Pleos **Vehicle SDK 2.3.2** 조회 권한 `CAR_DRIVE`·`CAR_SAFETY`(신청 불필요).
  - 기어(`Gear.registerGearSelection`) → 주행/주차. **연속 운전 시간**은 P 에서 출발한 순간부터 앱이 잰다(차의 트립 운전 시간 `registerTripCurrentDrivingTime` 은 단위가 문서에 없어 값만 받아 둔다).
  - 운전자 주의 경고등(`Safety.getTelltalesInfo` 의 `DRIVER_ATTENTION`) — 구독은 내부용 권한이라 5초마다 다시 묻는다.
  - 스노봇이 먼저 하는 말(앱 안 규칙, 인터넷 없어도 됨): 출발 인사 · 도착 인사(1시간 넘으면 "푹 쉬어요") · 주의 경고 → 쉼터 권유(10분에 한 번) · 120분째부터 60분마다 휴식 권유.
  - 서버에 `car` 메시지로 알려 대화가 차 상태를 안다: 주행 중엔 짧게(두 문장 30자 안팎), 경고·2시간 넘으면 쉬자는 말 먼저, 주차 중엔 세 문장까지.
  - 주행 중에는 시험용 버튼(인사·쉬어가기·걱정)을 숨긴다.

- 포그라운드 서비스 (2026-11-04, `SnowBotService`). 함께 만든 바쁜 정도는 2026-11-09 에 뺐다(아래 "바쁜 정도" 절):
  - 판단·서버 연결·음성을 **포그라운드 서비스**(`SnowBotService`, 유형 specialUse)로 옮겼다. 앱 화면(`MainActivity`)은 서비스 상태를 그리기만 한다. 화면을 닫아도 출발·도착 인사와 안전 알림을 한다(11/4 에뮬레이터: 화면을 닫고 VC 로 기어 D·P → 출발·도착 인사를 Pleos 목소리로 읽음. 첫 시도에서 출발 인사가 한 번 빠졌고 원인은 못 찾음).
  - 서버에 연결되지 않았으면 먼저 하는 말을 차 목소리로 바로 읽는다(`PleosVoice.sayLocal`).
  - 아직 안 쓰는 신호: 속도(Vehicle SDK 에 없음), 내비 회전 안내, 브레이크.
  - VC 를 스크립트로 조작: `adb forward tcp:8088 tcp:8088` 후 `POST /browser/session?client=이름`(3초 안에 다시 보내야 살아 있음) → `PUT /vehicle/gear?gear=Drive|Park`, `PUT /vehicle/turn-signal?mode=BlinkLeft|Off`, `PUT /vehicle/controls?steering=180`, `PUT /vehicle/body?front_wiper=Low|Off`, `PUT /vehicle/lights?feature=headlamp_mode&value=LowBeam|Off`. 브라우저 신호가 없으면 `503 emulator_inactive`.
  - ✅ **"getTelltalesInfo need Permission" 은 2026-11-08 풀렸다.** 원인은 권한 이름이었다 — Vehicle 2.3.2 aar 의 `getTelltalesInfo`·`registerTelltales`·`checkTelltalesCapabilities` 는 `@RequiresCustomPermission`으로 **`pleos.car.permission.PRIVATE_CAR_SAFETY`** 를 요구한다(Pleos 문서는 조회가 CAR_SAFETY 라고 적어 서로 다르다). 권한 검사는 차량 서비스가 아니라 SDK 안에서 `checkSelfPermission` 으로 한다. 에뮬레이터에서 이 권한의 보호 수준은 **normal** 이라 매니페스트에 적기만 하면 허용된다. 실차 보호 수준은 확인 못 했다 — 막히면 경고등 기능만 쉰다([../docs/pleos/05-vehicle-data.md](../docs/pleos/05-vehicle-data.md) 8절).
  - 에뮬레이터 시험: `adb forward tcp:8088 tcp:8088` → 브라우저 http://127.0.0.1:8088/ (Pleos Virtual Console) → Emulation Controls → Motion → **Gear Selection** P/D.
    **Virtual Console 경고등 목록에 DRIVER_ATTENTION 이 없다**(37종 중 없음). 그래서 주의 경고만 디버그 명령으로 대신한다:
    `adb shell "am start -n io.snowbot.hub/.MainActivity --es car 'gear=D add=120 attention=1'"` (gear=P|R|N|D, add=운전 분 더하기, attention=1|0. 시험 경고는 주차하면 풀린다)
  - `adb shell cmd car_service inject-vhal-event` 는 이 에뮬레이터(사용자 빌드)에서 막혀 있다.

- 경고등 통역 + 출발·하차 점검 (2026-11-08, `CarWarnings`·`Checkup`): 권한 `CAR_SEATS`·`CAR_DOORS`·`CAR_ENERGY`·`PRIVATE_CAR_SAFETY` 추가(모두 신청 불필요).
  - **경고등 통역**: 켜진 경고등(`Safety.getTelltalesInfo` + `registerTelltales`)을 **앱 안 고정 문장**으로 쉬운 말 한마디. 서버·AI 를 거치지 않아 인터넷이 없어도 되고 AI 비용이 들지 않는다. 표는 `CarWarnings.TABLE` — 키가 Pleos `TelltaleType` 이름이다(SDK 98종 중 44종에 말을 붙였고, 전조등·깜빡이·드라이브 모드 같은 **상태 표시등은 말하지 않는다**).
    - 같은 경고는 꺼졌다 다시 켜질 때만. 한 번에 여러 개면 **가장 급한 것 하나만** 말하고 나머지는 `car.warning` 으로 봇·AI 에 간다. 앱을 켠 순간 이미 켜져 있던 것은 말하지 않는다. `DRIVER_ATTENTION` 은 쉼터 권유가 따로 맡으므로 통역에서 뺀다.
  - **출발·하차 점검**(`Checkup`): 출발(P→주행) 때 벨트 안 맨 자리 또는 주행 가능 거리·연료 부족을 **한 가지만**, 하차(→P) 때 창문 열림·문 열림·뒷좌석 확인을 **두 가지까지**. 인사와 한 호흡으로 붙여 말한다(차 스피커가 한 번만 말하게).
    - 못 읽는 신호는 만들지 않는다. 구역은 `check…Capabilities` 가 주는 것만 구독한다. 시동 상태는 SDK 에 조회 함수가 없어 하차는 **기어 P** 로 본다.
    - 에뮬레이터는 안전벨트가 늘 "안 맴"이고 매는 카드가 없다 → 시연에서 조용한 출발이 필요하면 `--es car 'belt=0'`.
    - 연료 잔량(`Fuel.getFuelPercent`)은 이 에뮬레이터에 속성이 없어 실패한다(로그 한 줄). 주행 가능 거리·EV 잔량은 된다.
  - 봇에 가는 주제가 늘었다: `car.warning`(켜진 목록 + 가장 급한 것의 쉬운 말), `car.energy`(주행 가능 거리·연료·배터리), `car.cabin`(내기 순환 + 창문·문·벨트).
  - 확인 (11/8 에뮬레이터, VC 만 써서): 앱을 켠 직후 무언 → 기어 D 에 "출발! … 안전벨트 안 맨 자리가 있어요. 매고 가요." → VC Telltales 에서 TPMS On → 5.1초 뒤 "타이어 바람이 빠졌대요. 다음 휴게소에서 봐요." → 다시 켜도 무언 → 엔진 과열 On → "엔진이 많이 뜨거워요. 안전한 곳에 세우고 식혀 주세요." → TPMS Off→On 에 다시 알림 → 창문 열고 기어 P 에 "잘 도착했어요. … 뒷좌석 왼쪽 창문이 열려 있어요. 닫고 가요. 뒤에 두고 내린 것 없죠?". 전부 Pleos 차 안 TTS(화자 seoyoon)로 읽힘. 말한 문장은 logcat `SnowBot: 말함 …` 에 남는다. 단위 시험 64개. (경고등 문장은 이 확인 뒤 "켜졌다는 것만 알린다"로 바꿈: "타이어 공기압 경고등이 켜졌어요." — 단위 시험 66개)
  - VC 를 스크립트로: 경고등 `PUT /vehicle/telltales/simulator?type=TPMS&state=On&color=Yellow`(type 은 `GET /vehicle/telltales` 의 37종), 좌석 탑승 `PUT /vehicle/seat?kind=occupied&area=driver&value=true`, 창문 `PUT /vehicle/body?window_rl=60`, 문 `PUT /vehicle/body?door_rl=true`, 주행 가능 거리 `PUT /vehicle/odometer?remaining_range_m=40000`, 배터리 `PUT /vehicle/ev-battery?field=soc&value=12`. 쓸 수 있는 모든 길은 `GET /vehicle/catalog` 에 있다.
  - 시험 명령(경고등을 못 읽는 차를 흉내 내거나 VC 에 카드가 없는 값을 넣을 때):
    `--es car 'warn=TPMS+LOW_EV_BATTERY'`(경고등 켜기, `warn=0` 끄기) · `belt=1|0`(운전석 벨트 안 맴) · `range=30`(km) · `fuel=10` · `soc=12` · `window=1|0` · `door=1|0` · `rear=1|0`. 한 명령 안에서 `gear=` 보다 먼저 반영된다(`'gear=D belt=0'` 이면 벨트를 맨 채로 출발).

- 다가가기 사다리 = 편의 권유 (2026-11-08, `Ladder`, 판정·근거: [../docs/pleos/13-ladder-review.md](../docs/pleos/13-ladder-review.md)):
  - **답답함(CO₂)·김서림(습도)에만** 쓴다. 경고등·주의 경고·휴식 권유·출발·도착 인사는 `CarCoach` 의 지금 경로 그대로다.
  - 칸 네 개: `0 그대로` → `1 표정만(말 없이)` → `2 한마디` → `3 나빠졌으면 다른 말로 한 번 더`(그 뒤 풀릴 때까지 조용).
    고개는 칸이 아니라 말에 곁들인다(김서림의 `nod`).
  - 반응은 값이 올 때마다 적어 둔다.
  - **따랐는지 확인한다**: 한마디 뒤에 운전자가 외기로 바꾸거나 창문을 열고(`car.cabin` 신호), **그 뒤 값이 실제로 내려가야**
    안도 표정(`happy`, 말 없이)으로 0칸에 돌아온다. 행동만 있고 결과가 없으면 안도하지 않는다. 저절로 풀리면 조용히 `neutral`.
  - 말 없이 표정만 보내는 길: `SnowBotService.expressFace()`. `say` 를 안 붙이면 서버가 목소리를 만들지 않는다.
  - 숫자는 모두 **시작 값이고 실측으로 조정한다**: 해제 1,200ppm(해석기 `CO2_OFF` 와 같은 뜻) · 1칸 1,350 · 한마디 1,500 ·
    "나빠짐·좋아짐" 여유 폭 150ppm(습도는 3%) · 10분. 1,200~1,349 는 판단 보류라 문턱 근처에서 흔들려도 표정은 한 번.
  - 시험: 단위 시험 34개(`LadderTest`). 에뮬레이터 확인은 아래.
    ```bash
    adb forward tcp:8088 tcp:8088
    # VC 세션은 3초마다 다시 보내야 살아 있다 (배경에서 돌려 둔다)
    while true; do curl -sX POST "http://127.0.0.1:8088/browser/session?client=me" -o /dev/null; sleep 2; done &
    curl -sX PUT "http://127.0.0.1:8088/vehicle/gear?gear=Drive"
    curl -sX PUT "http://127.0.0.1:8088/vehicle/hvac?recirc=true"          # 내기 순환
    adb shell "am start -n io.snowbot.hub/.MainActivity --es observe 'co2=1400'"   # → 표정만 [sleepy]
    adb shell "am start -n io.snowbot.hub/.MainActivity --es observe 'co2=1500'"   # → 말함 "외기로 바꿔 볼까요?"
    curl -sX PUT "http://127.0.0.1:8088/vehicle/hvac?recirc=false"        # 운전자가 외기로 (아직 안도 안 함)
    adb shell "am start -n io.snowbot.hub/.MainActivity --es observe 'co2=1300'"   # → 표정만 [happy] 안도
    ```
    로그는 `adb logcat -s SnowBot:I` 의 `사다리 답답함: …` · `표정만 […]` · `말함 […]`.
    VC 세션이 끊기면 VC 의 차 상태가 되돌아가므로 한 흐름은 세션을 켜 둔 채 끝까지 간다.
  - ⚠ 내기 순환 구독의 **콜백 이름이 SDK 안에서 어긋나 있다**: 수동(`registerHvacRecirculationState`)과
    자동(`registerHvacAutoRecirculationState`)이 리스너 인터페이스를 같이 쓰는데 그 메서드 이름이
    `onHvacAutoRecirculationStateUpdated` 다. 어느 값이 오는지는 register 쪽이 정한다 —
    앱은 수동 쪽으로 등록하므로 **받는 값은 수동 내·외기가 맞다**(2.3.2 aar 실측, 에뮬레이터 VC 로도 확인).

아직 없는 것: Gleo Interface, 쉼터 경유지 추가(NaviHelper), 회전 구간 침묵(NaviHelper 회전 안내), 주행 규제(CarUxRestrictions) 연동, 서버 AI 에 경고등 알리기(`car` 메시지에 아직 없다 — 봇에는 `car.warning` 으로 간다).

## 빌드·실행 (맥, Android Studio 없이)

준비물(설치됨): JDK 17(`/Library/Java/JavaVirtualMachines/temurin-17.jdk`), Android SDK(`/opt/homebrew/share/android-commandlinetools`),
에뮬레이터 + Pleos 이미지 `system-images;android-34;pleos_car_emulator;arm64-v8a`(42dot SDK 라이선스 동의, Pleos 사이트를 `~/.android/repositories.cfg` 에 등록), AVD `PleosConnect`(Pleos_Connect_v2_0, 2560×1440, RAM 4GB).

```bash
cd app
# local.properties (저장소에 넣지 않음)
#   sdk.dir=/opt/homebrew/share/android-commandlinetools
#   snowbot.server=https://<서버-IP>.sslip.io
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
./gradlew assembleDebug

SDK=/opt/homebrew/share/android-commandlinetools
$SDK/emulator/emulator -avd PleosConnect -no-snapshot-load &      # 처음은 cold boot (Pleos 안내)
$SDK/platform-tools/adb -e install -r app/build/outputs/apk/debug/app-debug.apk
$SDK/platform-tools/adb -e shell am start -n io.snowbot.hub/.MainActivity
```

시험용 가상 스노봇: `server/tools/sim.py device ...` (server/README.md). 실물 스노봇이 오면 같은 서버에 붙는다.

## 주의

- Pleos 키보드는 화면 전체를 덮는다. 입력 칸 사이는 키보드의 "다음"으로 옮긴다.
- 소리를 들으려면 에뮬레이터를 `-no-audio` 없이 띄운다(맥 스피커로 나온다).
- 서버 주소·토큰은 APK·저장소에 넣지 않는다(서버 주소는 local.properties → BuildConfig).

- 봇 커넥트 규약(BCP) v0.3 허브 (2026-11-04, `BotConnect`·`Interpreter`, 규약: [../docs/pleos/03-module-protocol.md](../docs/pleos/03-module-protocol.md)):
  - 짝 맺은 봇마다 설명(`hello` v0.3·v0.1)을 읽어 "연결된 봇" 목록에 보이고, 허락(`welcome`)을 보낸다. 기본 허락 6주제, `driver.state` 는 거부.
  - 차가 주는 정보를 주제로 바꿔(`car.motion` `car.outside` `car.cabin` `car.energy` `car.warning` `trip`) 값이 바뀔 때만 허락한 봇에 `context` 로 보낸다. 내기 순환은 HVAC 를 차가 알려 주는 구역(`checkHvacRecirculationCapabilities`)으로 구독해야 값이 온다(운전석 구역으로 등록하면 안 옴). 권한 `CAR_CLIMATE` 추가.
  - 봇이 주는 정보(`observe` env, v0.1 `event`)를 모아 해석: 답답함(CO₂ 1,500↑, 내기 순환이면 외기 권유) · 김서림 우려(습도 75%↑ + 비·밤). 피드백은 짝 맺은 켜진 봇 모두에 각자 몸에 맞게(`BotConnect.express`), 말은 첫 봇·차 스피커로.
  - "봇 추가" 버튼: 이미 짝 맺은 앱이면 토큰을 붙여 같은 앱에 봇을 더한다.
  - 시험 명령: `--es observe 'humidity=80 co2=1600'`(봇이 보낸 것처럼), `--es car 'recirc=1'`, `--es car 'ac=1 defrost=1'`(에어컨·앞유리 성에 제거, VC 로는 `PUT /vehicle/hvac?ac=true`). 권유끼리는 20초 간격 — 같은 순간이면 김서림 먼저, 답답함은 미뤘다가.

- 화면 속 대역 = 입자 구름 (2026-11-08, `ParticleView`·`LookShape`): 봇이 없으면 빛 방울, `look`(규약 3-5)을 보낸 봇이 켜지면 부풂 → 소용돌이 → 봇 모양, 꺼지면 빛 방울로. 감정 = 색·빠르기, `nod`/`shake` = 머리 입자, 말하는 중 = 외곽 출렁, 듣는 중 = 아래로 모임, 주행 중 = 입자 340개·움직임 줄임. 에뮬레이터 약 60fps.
  - 시험 명령: `--es hello '{"type":"hello","device_id":"SIM-01","look":{…},…}'`(봇이 켜진 것처럼, 가상 스노봇의 `HELLO` 를 JSON 으로), `--es bye 'SIM-01'`(꺼진 것처럼). 짝 맺지 않은 봇이라 몇 초 뒤 서버 기준 목록으로 돌아간다(시험 경로만의 일).
  - VC 내기 순환: `PUT /vehicle/hvac?recirc=true|false`.
  - 가상 봇: 스노봇 `server/tools/sim.py device --co2 1300`, LED 큐브 `server/tools/led-cube.html`(브라우저, `#server=…&dev=…&key=…` 로 자동 연결, 연결 뒤 입력 칸 숨김).
  - 확인 (11/4 에뮬레이터): 큐브 습도 80% → 김서림 해석 → 차 스피커 안내 + 스노봇 worried·nod, 큐브 worried. 스노봇 CO₂ 1,500 → 답답함. 단위 시험 23개.

- **영화형 판단·표현 (2026-11-09)** — 운전자와 대화하며 차 상태를 **보고·권고·실행**하는 쪽으로 바꿨다.

  **"바쁜 정도"(DrivingLoad)를 없앴다.** 방향지시등·핸들 각도·후진·크루즈 구독, 말 끊기, 안전 알림 미루기,
  서버의 `load`·문장 수 조절, 규약의 `car.busy`·`quiet` 가 모두 빠졌다. 안전 알림은 이제 바로 말한다.
  대답 길이는 주행 두 문장 · 주차 세 문장(서버). 와이퍼·전조등·밤은 남았다(김서림·대조에 쓴다).

  **판단과 표현을 나눴다.**
  - 판단(`Judgement` · `CarCoach` · `CarWatch` · `Ladder` · `DriverWords`): 입력에서 *상황 · 종류(안전/권유/인사) ·
    전달 방식 · 근거 값* 을 낸다. 문장·표정·봇 몸을 모른다. 안드로이드에 기대지 않아 JVM 단위 시험으로 다 덮는다.
  - 표현(`Expression`): 판단 결과로 감정·몸짓·**문장 조각**을 고른다. 글자로 바꾸는 것은 서비스가 한다.
    감정 표는 기술 명세서 4-1 그대로, 세기는 0.6 고정.
  - 겹치면 **안전 → 권유 → 인사**. 권유끼리는 20초 간격, 안전을 말한 직후 5초 안의 인사는 건너뛴다.
  - **안전은 끌 수 없다**: 경고등·주의 경고·주행 중 문 열림·밤 전조등·**쉼터 권유(오래 운전)** 는
    "그냥 둬"로 꺼지지 않는다(13-ladder-review 4-2). 도착 전에 미리 묻는 쪽(`DRIVE_TIME_OVER`)만 권유다.
  - **없는 값은 지어내지 않는다**: 판단이 근거 숫자를 넘기지 않으면 그 숫자가 없는 문장을 고르거나
    아예 말하지 않는다(`Judgement.num()` 이 null 을 준다 — 0 으로 바꾸지 않는다).

  **운전자 말을 입력으로** (`DriverWords`, 백엔드로 보내기 **전에** 본다 — 인터넷 없이 동작):
  - "지금 차 어때" · "멀쩡한 게 뭐야" · "얼마나 더 갈 수 있어" → 앱이 차 요약(`CarReport`)으로 바로 답한다.
  - "응"(기다리는 제안이 있을 때만) → 실행. "그냥 둬"(끌 권유나 기다리는 제안이 있을 때만) → 지금 권유를
    평소 표정으로 끝내고 **이번 운행에서 그 상황은 다시 말하지 않는다**(안전은 못 끈다).
    끌 것이 없으면 "괜찮아"는 그냥 하는 말이므로 AI 가 대답한다.
  - "창문 열어줘" → "아직 제가 직접 못 움직여요. 창문은 직접 해 주셔야 해요." (Vehicle Control SDK 는 "준비 중")
  - "주유소 찾아줘" 같은 말 → 내비 화면을 연다. 그 밖의 말만 백엔드 AI 로.
  - 오인식 방지: 종류마다 글자 수 한도를 두고 **짧은 문장에서만** 맞춘다. 긴 잡담은 AI 로.
    수락·거절은 **문장 전체가 그 말이거나 그 말로 시작 + 짧은 꼬리말**일 때만 — "반응이 왜 그래"의 "응",
    "콜라 좋아"의 "좋아", "그만 먹을래"의 "그만"에 걸리지 않는다.

  **차 요약 (`CarReport`)**: 경고등 · 주행 가능 거리(없으면 잔량) · 목적지까지 · 운전 시간 · 문 · 창문 · 벨트 ·
  공조 · CO₂ · 습도 · 인터넷을 *값 + 정상 여부* 로 모은다. 못 읽은 값은 만들지 않는다.
  **주행 가능 거리는 1km 미만을 모름으로 본다**(`Checkup.usableRange`) — 차가 막 깨어났을 때 0 이 오는데,
  그 값으로 말하면 "남은 거리 0킬로미터"가 된다. 연료가 정말 바닥이면 연료 부족 경고등이 알린다.
  백엔드에는 `car` 메시지의 `summary` 로 보내 AI 가 수치를 인용해 대답한다.

  **변화·회복 보고**: 권유 문장에 근거 숫자를 넣고("CO₂ 1,500피피엠이에요. 외기로 바꿔 볼까요?"),
  다시 한마디는 변화를 말한다("1,500에서 1,700까지 올랐어요."). 경고등이 꺼지면 "…경고등 꺼졌어요"(happy, 한 번).
  주행 가능 거리가 100km·50km 아래로 처음 내려갈 때 운행당 한 번씩(한꺼번에 여러 지점을 지나면 가장 낮은 것만).

  **대조 (`CarWatch`)** — 조건이 **새로 성립한 순간만**, 풀렸다 다시 생겨야 또:
  주행 가능 거리 < 목적지까지(→ 주유소·충전소 제안) · 운전 시간 + 남은 시간 ≥ 120분(→ 쉴 곳) ·
  비 + 창문 열림 · 밤 + 전조등 꺼짐(안전) · 주행 중 문 열림(안전) · 앞 경로 정체(→ 다른 길).
  모르는 값이 든 조건은 아예 보지 않는다 — 내비 없는 차에서는 내비 조건이 조용하다.

  **내비 (`NaviState`, NaviHelper 3.0.5)**: 목적지까지 남은 거리·시간, 앞 정체를 읽고 내비 화면을 연다.
  권한 `NAVI_ROUTE`·`NAVI_ROUTE_SEARCH`·`NAVI_CUSTOM_MAP`(신청 불필요). 앱이 장소를 **검색**하는 API 는 없어
  운전자가 고르는 화면만 연다. 에뮬레이터 확인 결과는 [../docs/pleos/05-vehicle-data.md](../docs/pleos/05-vehicle-data.md) 9절.
  시험 명령 `--es nav 'dest_km=52 eta_min=40 congested=1'`(`off=1` 로 되돌림).

  **화면: 차 상태 패널**: 항목별 값과 주의 표시(⚠), 그리고 스노봇이 수치를 말한 순간 그 숫자를 5초 크게 보인다.

  확인 (11/9 에뮬레이터, VC + 시험 명령):
  - "지금 차 어때" → 앱 안에서 "안전벨트 안 맨 자리가 있어요." · "멀쩡한 게 뭐야" → "공조는 완전 멀쩡해요."
  - "창문 열어줘" → 불가 보고 · "주유소 알려줘" → 내비 앱이 앞으로 나옴
  - 출발 → "밤인데 전조등이 꺼져 있어요."(안전) → 전조등 켜면 조용, 다시 끄면 또 알림
  - TPMS On → "타이어 공기압 경고등이 켜졌어요." → Off → "타이어 공기압 경고등 꺼졌어요."
  - 와이퍼 + 창문 → "비 오는데 창문이 열려 있어요."
  - 주행 가능 거리 38km + `--es nav 'dest_km=52'` → "남은 거리 38킬로미터, 목적지까지 52킬로미터예요. 충전소 찾아볼까요?"
    → "응 해줘" → 내비 화면 열림(`topResumedActivity` 가 내비 앱)
  - 권유 뒤 "그냥 둬" → 조건을 다시 만들어도 `끄라고 한 권유 … 말하지 않음`
  - 말하는 중 방향지시등·핸들 180° → 말이 끊기지 않는다(그 구독 자체가 없어졌다)
  - 비행기 모드 → "지금 인터넷이 끊겨서 대화는 어려워요. 차 상태는 볼 수 있어요." (보낸 뒤 8초 안에 대답이 없어도 같다)
  - 11/9 검토 수정 확인: 주행 가능 거리 0.5km + 목적지 52km → **아무 말 없음**, 38km 가 오자
    "남은 거리 38킬로미터, 목적지까지 52킬로미터예요"(큰 수치도 38km) · 2시간 운전 → 쉼터 권유,
    "그냥 둬"로도 꺼지지 않고 3시간째에 다시 말함 · 끌 권유가 없을 때 "괜찮아" → AI 로.
  - 단위 시험 175개(`./gradlew testDebugUnitTest`), 서버 48개(`pytest`).
