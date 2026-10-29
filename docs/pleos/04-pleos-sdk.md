# Pleos 개발 문서 확인 (2026-10-26)

출처: Pleos Playground 개발 문서 https://document.pleos.ai (Claude 가 Ego Lite 로 22쪽을 읽고 정리). 문서는 개발 중이라 바뀔 수 있다.
인계 자료(02-research.md B 절)와 다른 점이 많다 — **이 문서가 최신이다.**

## SDK 구성 (지금)

| SDK | 하는 일 | 지금 쓸 수 있나 |
|---|---|---|
| **Vehicle** (v2.3.x) | 차량 상태 조회·제어. 도메인: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, **HVAC**, Light, Odometer, Safety, Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal, Window, Wiper | **조회(Vehicle State)는 신청 없이 사용.** 제어(Vehicle Control)는 "준비 중" |
| **NaviHelper** | 목적지·경로·**경유지 추가**, 길 안내 이벤트 | 신청 없이 사용 |
| **SpeechToText** (v3.3.1) | 말 → 글. 서버 / 차 안 엔진 / 둘 다(HYBRID). **명령 실행은 안 함** | 문서 있음. 프로젝트 화면 기준 "준비 중" (확인 필요) |
| **TextToSpeech** (v3.3.1) | 글 → 말, 차 스피커로 재생 | 위와 같음 |
| **FusedLocation** (v1.1.3) | 차 위치(위도·경도·고도·방향), **영역 진입/이탈 알림** | 위와 같음 |
| **Gleo Interface** | 앱 기능을 JSON(App Schema)으로 등록하면 **Gleo AI(LLM)가 음성 명령으로 앱 기능을 호출**(function calling). 앱 응답으로 Gleo 가 답을 만든다 | 가이드 "개선 중". 스키마 전달 방법 문서 있음 |

**인계 자료와 다른 점**
- **"Gleo AI SDK(STT·TTS·LLM)"는 없다.** 앱이 LLM 을 직접 부르는 SDK 는 없고, **Gleo 가 앱을 부르는 방식(Gleo Interface)** 이다.
- **Sync SDK, ADAS SDK 는 현재 문서에 없다.**
- **속도·기어는 Vehicle SDK 가 아니라 안드로이드 표준 차량 API** 로 읽는다: `CarPropertyManager` 의 `PERF_VEHICLE_SPEED`(클러스터와 같은 속도), 기어 상태 등.
- 주행 중인지는 표준 `CarUxRestrictions`(주행 규제 상태)로 판단한다.

## 개발 환경

- Android Studio, Android SDK API 34, **minSdk 28 이상**, Gradle 8+, Kotlin/Java. Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`, 예: `ai.pleos.playground:Vehicle:2.3.1`.
- **Pleos Connect Emulator 2.1.0** (API 34 이미지). **Mac(arm64) 이미지가 따로 있다.** cold boot 필수, RAM 4GB. 그래픽 권장 사양은 RTX 4060급(최소 RTX 3060급) — Mac 에서 잘 도는지 실제로 돌려 봐야 한다.
- **에뮬레이터는 블루투스 동글 연동을 지원하지 않는다**(현재 버전). → 에뮬레이터에서 BLE 모듈 연결 시험 불가.
- Virtual Console 로 차량 상태(속도·기어 등)를 바꿔 가며 시험.
- 마이크는 에뮬레이터 Extended Controls 에서 켠다(STT 시험).
- Playground 가입 → 프로젝트 생성 → SDK 사용 신청. TTS·STT 서버 모드는 프로젝트 클라이언트 ID/시크릿으로 앱 등록 필요(차 안 엔진만 쓰면 불필요).

## 설계 규칙 (앱 심사 기준)

| 규칙 | 내용 |
|---|---|
| 하드웨어 접근 | 차량 물리 제어·센서에 **직접 접근 금지**, 플랫폼 API 로만. 모바일 API(전화·SMS·일부 센서 등)는 제한될 수 있다 |
| 주행 중 | `CarUxRestrictions` 를 따라 기능을 줄여야 한다. **DO(주의 분산 최적화) 선언 안 한 앱은 주행 중 사용 불가.** 한국은 주행 중 키보드·검색·설정·영상 제한 |
| 주의 분산 | 주행 중 동영상·애니메이션·움직이는 이미지 최소화, 음성·프리셋 우선, 큰 터치 영역 |
| 화면 | Driving view + 앱 창 1~2개. 세로(창 1개)·가로(창 2개) 둘 다 설계. 창에서 사라져도 백그라운드에 남는다 |
| 시동 OFF | **Suspend(저전력) 모드 — 화면·오디오·일반 서비스 중단.** 앱은 오디오 포커스를 반납해야 한다 |
| 네트워크 | 주행 중 끊김 대비(캐시, 재시도, 오프라인 안내) |
| 개인정보 | 위치·음성 등 왜 필요한지 설명, 최소 수집, 차량·사용자 ID 익명화, 운전 행동 데이터 마케팅 금지 |
| 카테고리 | Maps/Navigation, **Mobility Services**, **Lifestyle**, Entertainment, Business, Music/Audio, Kids, Social. 카테고리마다 주행 중 허용 범위가 다르다 |

## 우리 기획에 미치는 영향

1. **모듈 연결 방식.** BLE 는 ① 에뮬레이터에서 시험할 수 없고 ② 차량 앱에서 외부 블루투스 기기를 써도 되는지 문서에 없다.
   → **시연·1차 구현은 네트워크로 잇는다:** 차량 앱 ↔ 중계 서버(사용자 PHP 서버 또는 Mac) ↔ SnowBot(Wi-Fi).
   SnowBot 은 이미 Wi-Fi + Mac 중계 구조라 그대로 쓸 수 있다. 에뮬레이터는 Mac 과 같은 컴퓨터라 **에뮬레이터 앱이 실물 SnowBot 을 움직이는 시연**이 된다.
   BLE 는 사무국·Pleos 에 확인한 뒤 "향후"로 적는다.
2. **대화형 AI.** 앱 안에서 LLM 을 부를 SDK 는 없다. 두 갈래로 쓴다:
   - **Gleo Interface**: "글레오, 스노봇한테 주차 위치 물어봐" → Gleo 가 앱 기능 호출 → **기술 활용성 점수에 직결.**
   - 자체 대화(캐릭터 말투 등)는 중계 서버에서 외부 AI API 를 부른다(네트워크 끊김 대비 필요). 허용 여부는 문의 목록에 넣는다.
3. **주행 상태별 규칙**(기획안 4절)은 `CarUxRestrictions` + `PERF_VEHICLE_SPEED` + 기어로 구현한다. 앱은 주행 중에도 쓰이므로 **DO 선언 + 음성·표정 위주 화면**으로 설계.
4. **펫 지킴이(S2)는 차량 앱만으로는 안 된다** — 시동을 끄면 앱이 멈춘다(Suspend). 하차 후 감시는 **모듈(SnowBot/카메라 큐브)이 스스로** 하고 서버 → 폰 알림으로 보내야 한다(모듈 전원은 배터리 필요 — 차 USB 는 시동 OFF 시 끊길 수 있음). 시연 범위에서는 빼거나 "향후"로.
5. **SDK 활용이 강한 스킬을 앞에 둔다:**
   - **S3 졸음·공기질**: 모듈 CO₂ + 주행 시간(Odometer 트립) → Vehicle **HVAC**(외기 전환은 제어 SDK "준비 중"이라 안내만) + NaviHelper **경유지(쉼터) 추가** + TTS + Gleo.
   - **S1 주차 위치**: FusedLocation(위치) + 모듈 카메라 기둥 글자 인식 → 하차 시 저장 → 폰/집 SnowBot 이 알려 줌.
6. **카테고리**: Lifestyle 또는 Mobility Services(주행 중 제한 허용 범위가 더 넓음 — 차량 관리·위치 기반 정보). 기획서에 정해 적는다.
7. **TTS·STT·FusedLocation "준비 중" 표시**는 Playground 프로젝트를 만들어 직접 확인해야 한다(사용자 계정 필요). 안 되면 안드로이드 표준 TTS/STT 로 대체.

## 문의 목록에 더할 것 (00-contest-rules.md 초안에 추가)

- 차량 앱에서 안드로이드 표준 Bluetooth(BLE)로 외부 기기와 연결해도 되는지
- 공모전 기간에 TTS·STT·FusedLocation SDK 를 신청해 쓸 수 있는지("준비 중" 표시)
- Gleo Interface(App Schema 등록)를 공모전 앱에서 에뮬레이터로 시험할 수 있는지
