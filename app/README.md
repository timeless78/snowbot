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

- 차량 데이터 (2026-10-28, `CarState`·`CarCoach`): Pleos **Vehicle SDK 2.3.2** 조회 권한 `CAR_DRIVE`·`CAR_SAFETY`(신청 불필요).
  - 기어(`Gear.registerGearSelection`) → 주행/주차. **연속 운전 시간**은 P 에서 출발한 순간부터 앱이 잰다(차의 트립 운전 시간 `registerTripCurrentDrivingTime` 은 단위가 문서에 없어 값만 받아 둔다).
  - 운전자 주의 경고등(`Safety.getTelltalesInfo` 의 `DRIVER_ATTENTION`) — 구독은 내부용 권한이라 5초마다 다시 묻는다.
  - 스노봇이 먼저 하는 말(앱 안 규칙, 인터넷 없어도 됨): 출발 인사 · 도착 인사(1시간 넘으면 "푹 쉬어요") · 주의 경고 → 쉼터 권유(10분에 한 번) · 120분째부터 60분마다 휴식 권유.
  - 서버에 `car` 메시지로 알려 대화가 차 상태를 안다: 주행 중엔 짧게(두 문장 30자 안팎), 경고·2시간 넘으면 쉬자는 말 먼저, 주차 중엔 세 문장까지.
  - 주행 중에는 시험용 버튼(인사·쉬어가기·걱정)을 숨긴다.
  - 에뮬레이터 시험: `adb forward tcp:8088 tcp:8088` → 브라우저 http://127.0.0.1:8088/ (Pleos Virtual Console) → Emulation Controls → Motion → **Gear Selection** P/D.
    **Virtual Console 경고등 목록에 DRIVER_ATTENTION 이 없다**(37종 중 없음). 그래서 디버그 명령으로 대신한다:
    `adb shell "am start -n io.snowbot.hub/.MainActivity --es car 'gear=D add=120 attention=1'"` (gear=P|R|N|D, add=운전 분 더하기, attention=1|0. 시험 경고는 주차하면 풀린다)
  - `adb shell cmd car_service inject-vhal-event` 는 이 에뮬레이터(사용자 빌드)에서 막혀 있다.

아직 없는 것: Gleo Interface, 쉼터 경유지 추가(NaviHelper), 회전 구간 침묵(NaviHelper 회전 안내), 주행 규제(CarUxRestrictions) 연동.

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
