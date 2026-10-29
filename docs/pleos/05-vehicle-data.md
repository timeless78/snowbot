# 앱이 읽을 수 있는 차량 데이터 목록 (2026-10-28)

스노봇 차량 앱(Pleos Connect 앱)이 **읽을 수 있는** 차량·내비·위치 데이터를 모두 모은 목록이다. 기능을 정하는 문서가 아니다 — 무엇을 쓸지는 사용자가 정한다(맨 끝 "활용 아이디어"는 참고용).

## 어떻게 확인했나

- **Pleos 개발 문서** https://document.pleos.ai (2026-10-28 기준). Ego Lite 로 사이트를 열고, 문서 전체 목록(sitemap, 4,317쪽) 중 `pleos-only`(Pleos Connect 앱) 1,961쪽을 받아 읽었다.
  - 문서 기준 버전: **Vehicle SDK 2.3.2**, NaviHelper 3.0.5, FusedLocation 1.1.3 (2026-10-19 릴리즈). 04 문서의 2.3.1 보다 새 버전이다.
  - 주의: API 상세 쪽(메서드 하나짜리 쪽)은 브라우저에서도 **본문이 비어 있다.** 내용은 인터페이스 쪽(`.../door/entry` 같은 쪽)에만 있다.
- **SDK 파일 직접 확인**: Maven 저장소의 `ai.pleos.playground:Vehicle:2.3.2` aar 를 내려받아 클래스 목록을 열어 봤다(지금은 앱도 이 2.3.2 를 쓴다 — `app/app/build.gradle.kts`). **문서에서 숨겨졌지만 SDK 에 들어 있는 함수 12개**를 찾았다(아래 ★ 표시).
- **공식 예제 코드**: GitHub `Pleos-Playground/pleos-sample-vehicle-app`, `-navi-app`, `-fusedlocation-app` (읽기만 함).
- 표준 안드로이드 차량 API(AAOS)는 Pleos 문서가 "속도·기어·외기 온도·연료량은 표준 API 로 읽으라"고만 하고 자세한 내용은 Google 문서로 넘긴다. 이 문서의 AAOS 항목 권한·단위는 **Google 문서 기준(내 지식)이며 Pleos 차량·에뮬레이터에서 실제로 되는지는 확인 못 했다.**

## 먼저 알아 둘 규칙

**데이터를 받는 방법 (Vehicle SDK)**
- `vehicle.getXxx()` 로 도메인 객체(Door, HVAC …)를 얻고 그 안의 함수를 부른다.
- **G = get(조회)**: `getCurrentXxx(onComplete, onFailed)` — 한 번 물어보면 콜백으로 답이 온다(바로 반환 안 됨, 차와 통신하므로 비동기).
- **C = callback(구독)**: `registerXxx(listener)` — 값이 **바뀔 때마다** 알려 준다. 정해진 주기(몇 초마다)는 문서에 없다. 다 쓰면 `unregisterXxx` 로 해제.
- **Capability**: `checkXxxCapabilities` 로 그 차가 이 기능을 지원하는지·값 범위를 먼저 확인하라고 한다. **차 모델마다 지원 여부가 다르다.**
- 값이 `null` 로 올 수 있다(값 없음/미지원).

**권한과 신청**

| 권한 | 뜻 | 신청 |
|---|---|---|
| `pleos.car.permission.CAR_*` (CAR_DRIVE, CAR_DOORS, CAR_CLIMATE …) | 조회용. 보호 수준 normal — **매니페스트에 적기만 하면 됨**, 사용자 허용 창 없음 | **신청 불필요** ("Vehicle State" = 사용 가능 상태) |
| `pleos.car.permission.CONTROL_CAR_*` | 제어(set)용 | Vehicle Control = **"준비 중"(신청 불가)** |
| `pleos.car.permission.PRIVATE_CAR_*` | 내부용. 공식 예제에서도 뺐다 | **일반 앱은 못 씀**으로 본다 |
| `pleos.car.permission.NAVI_ROUTE` / `NAVI_ROUTE_SEARCH` / `NAVI_CUSTOM_MAP` | 내비 정보 조회 / 경로·경유지 요청 / 내비 화면 열기 | **신청 불필요** |
| `pleos.car.permission.FUSED_LOCATION` | 위치. 보호 수준 **dangerous**(사용자 허용 필요) | FusedLocation SDK 자체가 **"준비 중"** |

**주행 중**: 문서에 **데이터 조회를 주행 중 막는다는 말은 없다.** 막히는 것은 화면(UX)이다 — DO(주의 분산 최적화) 선언을 안 한 앱은 주행 중 아예 못 쓰고, 선언한 앱도 `CarUxRestrictions` 에 따라 키보드·영상·설정 등을 줄여야 한다. 아래 표의 "주행 중" 칸은 이 기준이다.

**에뮬레이터 시험(VC)**: Pleos Connect Emulator 2.1.0 의 **Virtual Console** 카드로 값을 바꿀 수 있는지.
✅ = 카드 있음 · ✗ = 카드 없음(실차나 다른 방법 필요) · ? = 확인 못 함

출처(공통)
- Vehicle SDK 소개: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- 권한: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- 신청 상태: https://document.pleos.ai/docs/pleos-only/create-my-project
- Virtual Console: https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- 주행 규제: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- 릴리즈 노트: https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015

---

## 1. 주행 상태 (속도·기어·시동 등)

출처: FAQ(클러스터 속도) https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq · SDK API 정책 https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api · Brake/SteeringWheel/TurnSignal/DrivingMode 인터페이스 `.../Vehicle/vehicle/ai.pleos.playground.vehicle.api/{brake,steering-wheel,turn-signal,driving-mode}/entry`

| 항목 | API | 값·단위 | 권한 | 받는 법 | 주행 중 | VC |
|---|---|---|---|---|---|---|
| 차 속도 | **AAOS** `CarPropertyManager` + `PERF_VEHICLE_SPEED` (클러스터와 같은 값) | m/s (Google 기준) | `android.car.permission.CAR_SPEED` (Google 기준 dangerous — 사용자 허용 필요할 수 있음) | G·C | O | **✗ 속도 카드 없음** |
| 기어 ★ | `Gear.getCurrentGearSelectionState` / `registerGearSelection` (문서 숨김, SDK 에 있음). 또는 AAOS `GEAR_SELECTION` | `VehicleGear`: GEAR_PARK, GEAR_REVERSE, GEAR_NEUTRAL, (주행) DRIVE, GEAR_1~9, GEAR_UNKNOWN | CAR_DRIVE (AAOS 는 `CAR_POWERTRAIN`) | G·C | O | ✅ P/R/N/D |
| 시동 상태 | Vehicle SDK 에 **조회 함수 없음**(`IgnitionState` 값 목록만 있음: LOCK/OFF/ACC/ON/START). AAOS `IGNITION_STATE` | — | AAOS `CAR_POWERTRAIN` | G·C | — | ✗ |
| 브레이크 페달 | `Brake.getCurrentBrakePedalStatus` | Boolean (true=밟음) | CAR_DRIVE | G·C | O | ✅ 0~100% |
| 주차 브레이크 | 경고등 `PARKING_BRAKE`/`ELECTRONIC_PARKING_BRAKE` (6절 경고등) 또는 AAOS `PARKING_BRAKE_ON` | ON/OFF/BLINK | CAR_SAFETY | G | O | ✅ (경고등 카드) |
| 조향각 | `SteeringWheel.getCurrentSteeringWheelAngle` | 도(°), 앞바퀴 조향각 | CAR_DRIVE | G·C | O | ✅ -540~540° |
| 방향지시등 | `TurnSignal.getCurrentTurnSignalState` | OFF / LEFT / RIGHT | CAR_LIGHTS | G·C | O | ✅ |
| 드라이브 모드 | `DrivingMode.getCurrentDrivingModeState` | NORMAL / ECO / SPORT | CAR_DRIVE | G·C | O | ✗ |
| 크루즈 상태 | `DrivingMode.getCruiseControlState` | OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED, FORCED_DEACTIVATION_WARNING | CAR_DRIVE | G·C | O | ✅ |
| 크루즈 목표 속도 | `DrivingMode.getCruiseControlTargetSpeed` | m/s | CAR_DRIVE | G·C | O | ✅ 5.6~27.8 m/s |
| 주행 규제 상태 | **AAOS** `CarUxRestrictionsManager.registerListener` → `onUxRestrictionsChanged` | `isRequiresDistractionOptimization()`, `activeRestrictions` 비트(NO_VIDEO, NO_KEYBOARD …) | 별도 권한 없음 | C | O | ? (기어·브레이크로 바뀌는지 미확인) |

- Vehicle SDK 2.3.2 에서 **속도 리스너(`VehicleSpeedListener`)가 삭제**됐다. 속도는 AAOS 로만 읽는다.
- 주행 규제는 차 신호(속도·기어·브레이크)로 켜지고, **5 km/h 이하 + 조건**에서 풀린다(지역별 정책 문서).

## 2. 시간·주행 기록 (Odometer)

출처: `.../Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/entry`

| 항목 | API | 값·단위 | 권한 | 받는 법 | 주행 중 | VC |
|---|---|---|---|---|---|---|
| 총 주행거리 | `getCurrentDistanceDriven` | km | CAR_DRIVE | G·C | O | ✅ 0~1,000,000 km |
| 트립 거리 4종 | `getTripCurrentDrivingDistance` / `…Cumulative…` / `…SinceRefill…` / `…Instantaneous…` | m | CAR_DRIVE | G·C | O | ✗ |
| **트립 운전 시간** 3종 | `getTripCurrentDrivingTime` / `getTripCumulativeDrivingTime` / `getTripSinceRefillDrivingTime` | Int, **단위 문서에 없음**(초인지 분인지 실측 필요) | CAR_DRIVE | G·C | O | ✗ |
| 시각·날짜 | 안드로이드 표준 시계 | — | 없음 | — | O | — |

- "Current/Cumulative/SinceRefill/Instantaneous" 는 차 트립 컴퓨터 구분(이번 운행 / 누적 / 주유·충전 후 / 순간)으로 보인다. 정확한 기준(시동 켠 뒤부터인지)은 **문서에 없다.**

## 3. 차체 (문·창문·트렁크·좌석·안전벨트)

출처: `.../vehicle.api/{door,window,seat,side-mirror,safety}/entry`

| 항목 | API | 값·단위 | 권한 | 받는 법 | 주행 중 | VC |
|---|---|---|---|---|---|---|
| 문 열림 | `Door.getCurrentDoorPosition(DoorArea)` | Int, 0=닫힘, 최대값은 차마다(예제는 0~1). 영역: ROW_1~3_LEFT/RIGHT, HOOD(보닛), REAR(트렁크) | CAR_DOORS | G·C | O | ✅ 앞뒤 좌우 문, 트렁크/프렁크 |
| 문 잠금 | `Door.getDoorLockState(DoorArea)` | Boolean | CAR_DOORS | G·C | O | ✅ (전체 잠금/해제) |
| 글로브박스 잠금 | `Door.getCurrentGloveBoxLockState` | Boolean | CAR_DOORS | G·C | O | ✅ (열림/닫힘 카드) |
| 창문 위치 | `Window.getCurrentWindowPosition(WindowArea)` | Int (범위는 capability 로 확인, VC 는 0~100%) | CAR_WINDOWS | G·C | O | ✅ |
| 창문 잠금 | `Window.getCurrentWindowLockState` | Boolean | CAR_WINDOWS | G·C | O | ✅ |
| 창문 블라인드 | `Window.getCurrentWindowBlindPosition` | Int | CAR_WINDOWS | G·C | O | ✗ |
| 스마트 글라스 | `getCurrentSmartGlassState` / `…Level` / `getSmartGlassConfig` | Boolean / Int | CAR_WINDOWS | G·C | O | ✗ |
| 사이드미러 열선 | `SideMirror.getHvacSideMirrorHeatState` | Int(단계) | CAR_MIRRORS | G·C | O | ✗ |
| 좌석 위치·각도 10종 | `Seat.getCurrentSeatForeAftPosition`, `…HeightPosition`, `…BackrestAnglePosition`, `…TiltPosition`, `…DepthPosition`, 헤드레스트 3종, 요추 2종 | Int (범위는 차마다) | CAR_SEATS | G·C | O | ✗ |
| 안전벨트 착용 | `Seat.getCurrentSeatBeltBuckledState(SeatArea)` | Boolean | CAR_SEATS | G·C | O | ✗ (경고등 SEAT_BELT 로만) |
| 안전벨트 미착용 경고 | `Safety.hasSeatBeltNotBuckledWarning(SeatArea)` | Boolean (true=안 맴) | CAR_SAFETY | G·C | O | ? |
| 안전벨트 높이 | `Seat.getCurrentSeatBeltHeightPosition` | Int | CAR_SEATS | G·C | O | ✗ |

## 4. 탑승자·실내

출처: `.../vehicle.api/{seat,light,steering-wheel,safety}/entry`

| 항목 | API | 값·단위 | 권한 | 받는 법 | 주행 중 | VC |
|---|---|---|---|---|---|---|
| **좌석별 탑승 여부** | `Seat.getCurrentSeatOccupancy(SeatArea)` | Boolean. 좌석 ROW_1~3 × LEFT/CENTER/RIGHT | CAR_SEATS | G·C | O | ✅ 운전석·동승석·뒷좌석 좌우 |
| 시트 열선·통풍 | `Seat.getHvacSeatTemperature`, `getHvacSeatVentilationState` | Int(단계) | CAR_SEATS | G·C | O | ✅ 통풍 3단·끔·열선 3단 |
| 핸들 열선 | `SteeringWheel.getHvacSteeringWheelHeatState` | Int(단계) | CAR_CLIMATE | G·C | O | ✅ Off/Low/Med/High |
| 실내등 | `Light.getCurrentCabinLightState` / `getCabinLightSwitch` | OFF/ON/(DAYTIME_RUNNING) | CAR_LIGHTS | G·C | O | ✅ |
| 독서등(좌석별) | `Light.getCurrentReadingLightState(SeatArea)` | 위와 같음 | CAR_LIGHTS | G·C | O | ✅ |
| 앰비언트 라이트 | `getAmbientLightState` / `…Brightness` / `…BrightnessColor`(32bit ARGB) / `getAmbientColorAutoByDriveModeEnabled` | 상태·밝기·색 | CAR_LIGHTS | G·C | O | ✅ 밝기 0~100%, 색 4가지 |
| 에어백 활성·전개 | `Safety.hasSeatAirbagEnabled`, `hasSeatAirbagBeenDeployed` | Boolean | CAR_SAFETY | G·C | O | ✗ |

- 아이 감지: 값은 없고 경고등 `CHILD_PRESENCE_DETECTION_OFF`(아이 감지 꺼짐) 만 있다.

## 5. 공조 (HVAC)

출처: `.../vehicle.api/h-v-a-c/entry`, `.../vehicle.api/display/entry`. 권한 전부 CAR_CLIMATE(normal). 대부분 좌석 영역(SeatArea)별.

| 항목 | API | 값·단위 | 받는 법 | VC |
|---|---|---|---|---|
| 공조 전원 | `getHvacPowerState` | Boolean | G·C | ✅ |
| 에어컨 | `getHvacAcState` | Boolean | G·C | ✅ |
| 최대 냉방 | `getHvacMaxAcState` | Boolean | G·C | ✗ |
| 오토 | `getHvacAutoState` | Boolean | G·C | ✅ |
| 좌우 동기화 | `getHvacDualState` | Boolean | G·C | ✅ |
| **내기 순환** | `getHvacRecirculationState` | Boolean (true=내기) | G·C | ✅ Fresh/Recirculate |
| 자동 내기 전환 | `getHvacAutoRecirculationState` | Boolean | G·C | ✗ |
| 풍량 | `getHvacFanSpeed` | Int(단계) | G·C | ✅ 0~8 |
| 바람 방향 | `getHvacFanDirection` | FACE, FLOOR, DEFROST, 조합 4종, UNKNOWN | G | ✅ |
| 설정 온도 | `getHvacTemperature(area, unit)` | °C / °F | G·C | ✅ 16~30 °C |
| **현재 실내 온도** | `getHvacTemperatureCurrent(area, unit)` | °C / °F | G·C | **✗ 카드 없음** |
| 앞·뒤 성에 제거 | `getHvacDefrosterState(WindowArea)`, `getHvacMaxDefrostState` | Boolean | G·C | ✅(성에 제거) / ✗(최대) |
| 공기 청정 | `getHvacAirCleanerState` | Boolean (켜짐 여부만) | G·C | ✅ |
| 온도 표시 단위 | `Display.getCurrentTemperatureDisplayUnit` (권한 CAR_INFO) | CELSIUS / FAHRENHEIT | G·C | ✗ |
| 외기 온도 | Vehicle SDK **없음**. AAOS `ENV_OUTSIDE_TEMPERATURE` | °C (Google 기준) | G·C | ✗ |

- **실내 공기질·CO₂·미세먼지·습도 값은 없다.** (SDK 소개 글에 "습도 등"이라고 적혀 있지만 실제 함수는 없다.) 공기 청정기는 켜짐/꺼짐만.
- 주행 중 조회 제한은 문서에 없다. 공조 **제어**(외기 전환 등)는 Vehicle Control "준비 중".

## 6. 에너지 (배터리·연료·주행 가능 거리)

출처: `.../vehicle.api/{ev-battery,fuel,odometer}/entry`, FAQ(배터리), aar 직접 확인(★)

| 항목 | API | 값·단위 | 권한 | 받는 법 | 주행 중 | VC |
|---|---|---|---|---|---|---|
| EV 배터리 잔량 | `EvBattery.getEvBatteryStateOfCharge` | % (Int) | CAR_ENERGY | G·C | O | ✅ Battery SOC |
| EV 배터리 에너지 ★ | `EvBattery.getEvBatteryLevel` | Float, 단위 문서 없음(VC 는 kWh) | CAR_ENERGY | G·C | O | ✅ |
| 배터리 용량 ★ | `EvBattery.getEvBatteryCapacity` | Float (VC 는 kWh) | CAR_INFO | G | O | ✅ |
| 충전 켜짐 | `EvBattery.getEvChargeSwitchState` | Boolean | CAR_ENERGY | G | O | ✅ |
| 충전구 열림 ★ | `EvBattery.isEvChargePortOpen` | Boolean | CAR_ENERGY | G·C | O | ✅ |
| 충전 전류 제한 | `EvBattery.getEvChargeCurrentDrawLimit` | A | CAR_ENERGY | G | O | ✅ 10~48 A |
| 목표 충전량 | **조회 함수 없음** (capability 확인·설정만) | % | — | — | — | ✅(값 설정만) |
| 전비 4종 | `getCurrentEvEfficiency` / `Instantaneous` / `Cumulative` / `SinceCharge` | Float, **단위 문서 없음** | CAR_ENERGY | G·C | O | ✗ |
| 연료 잔량 | `Fuel.getFuelPercent` (AAOS `FUEL_LEVEL` 도 있음) | % (Int) | CAR_ENERGY | G·C | O | ✗ 연료 카드 없음 |
| 연비 4종 | `getCurrentFuelEconomy` / `Instantaneous` / `Cumulative` / `SinceChargeFuelEconomy`(주유 후) | Float, **단위 문서 없음** | CAR_ENERGY | G·C | O | ✗ |
| 주행 가능 거리 ★ | `Odometer.getRangeRemainingDistance` / `registerRangeRemainingListener` (AAOS `RANGE_REMAINING` 도 있음) | m | CAR_DRIVE | G·C | O | ✅ 0~1,000,000 m |
| 연료·배터리 부족 경고 | 경고등 `LOW_FUEL`, `LOW_EV_BATTERY` | ON/OFF/BLINK | CAR_SAFETY | G | O | ✅(경고등) |

- ★ 표시는 릴리즈 노트에 "문서 숨김(리스너 타입과 콜백은 유지)"으로 적힌 것들이다. **쓸 수는 있지만 문서가 없어 바뀔 수 있다** — 쓰려면 사무국에 확인하는 편이 안전하다.

## 7. 조명·와이퍼·날씨 관련

출처: `.../vehicle.api/{light,wiper,safety}/entry`

| 항목 | API | 값·단위 | 권한 | 받는 법 | 주행 중 | VC |
|---|---|---|---|---|---|---|
| 하향등 | `Light.getCurrentLowBeamLightState` / `getLowBeamLightSwitch` | 상태 OFF/ON/DAYTIME_RUNNING, 스위치 +AUTOMATIC | CAR_LIGHTS | G·C | O | ✅ (외부 조명 모드) |
| 상향등 | `getCurrentHighBeamLightState` / `…Switch` | 위와 같음 | CAR_LIGHTS | G·C | O | ✅ |
| 미등 | `getCurrentTailLampLightState` / `…Switch` | 위와 같음 | CAR_LIGHTS | G·C | O | ✅ |
| 앞·뒤 안개등 | `getCurrentFrontFogLightState`, `getCurrentRearFogLightState` (+Switch) | 위와 같음 | CAR_LIGHTS | G·C | O | ✗ |
| 와이퍼 상태 | `Wiper.getCurrentWiperState(WindowArea)` | OFF / ON / SERVICE / OTHER | CAR_WINDOWS | G·C | O | ✅ |
| 와이퍼 스위치 | `Wiper.getCurrentWiperSwitch` | OFF, MIST, 간헐 1~5, 연속 1~5, AUTO | CAR_WINDOWS | G | O | ✅ Off/Auto/Low/High |
| 워셔액 부족 | `Safety.hasLowWasherFluidWarning` | Boolean | CAR_SAFETY | G·C | O | ✗ |
| 노면 결빙 경고 | 경고등 `ICY_ROAD` | ON/OFF/BLINK | CAR_SAFETY | G | O | ✅(경고등, 지원 시) |

- 비 센서·일사량·바깥 밝기 값은 **없다.** 와이퍼·전조등 상태로 짐작만 할 수 있다. 날씨는 외부 API 가 필요하다.
- 비상등·제동등은 Virtual Console 카드는 있지만 **SDK 조회 함수가 없다.**

## 8. 안전·경고등·ADAS·운전자 상태

출처: `.../vehicle.api/{safety,tire}/entry`, `.../vehicle.constant.telltale/telltale-type/entry`, `.../vehicle.api.model.telltale/telltale-data/entry`

| 항목 | API | 값·단위 | 권한 | 받는 법 | 주행 중 | VC |
|---|---|---|---|---|---|---|
| **경고등 목록(telltale)** | `Safety.getTelltalesInfo` → `Telltales.telltales: List<TelltaleData>` | 경고등마다 type, state(OFF/ON/BLINK), color, priority, warningCount | **조회 CAR_SAFETY** / 구독 `registerTelltales` 는 **PRIVATE_CAR_SAFETY(불가)** | **G만** → 바뀐 걸 알려면 주기적으로 다시 물어봐야 함 | O | ✅ (지원 경고등 Off/On/Blink) |
| 엔진오일·브레이크액·변속기오일 | `getEngineOilLevelState`, `getBrakeFluidLevelState`, `getTransmissionOilLevelState` | LOW / NORMAL / HIGH / CRITICALLY_LOW / ERROR | CAR_SAFETY | G·C | O | ✗ |
| 엔진 이상(파워다운) 경고 | `hasPowerDownWarningIndicator` | Boolean | CAR_SAFETY | G·C | O | ✗ |
| 타이어 저압 기준값 | `Tire.getLowTirePressure(WheelArea)` | kPa — **현재 공기압이 아니라 "이 아래면 위험" 기준값** | CAR_SAFETY | G | O | ✗ |
| 실제 타이어 공기압 ★ | `Tire.getTirePressure` | kPa | **PRIVATE_CAR_SAFETY(불가)** | — | — | ✗ |

**경고등 종류(80여 개) 중 스노봇에 쓸 만한 것**: `DRIVER_ATTENTION`(운전자 주의 경고), `OVER_SPEED`, `ICY_ROAD`, `SEAT_BELT`, `DOOR_OPEN`, `TRUNK_OPEN`, `TPMS`(타이어), `LOW_FUEL`, `LOW_EV_BATTERY`, `EV_CHARGING_CONNECTOR`, `PARKING_BRAKE`, `AUTO_HOLD`, `MASTER_WARNING`, `ENGINE_TEMPERATURE`, ADAS 계열 `FCA`(전방 충돌 방지), `LKA`/`LFA`(차선 유지·따라가기), `LCA_LEFT/RIGHT`, `ELK`, `ICC`(스마트 크루즈), 드라이브 모드 계열(ECO/SPORT …), `CHILD_PRESENCE_DETECTION_OFF`.
에뮬레이터 Virtual Console 의 Telltales 카드가 지원하는 경고등(2026-10-28 직접 확인, 37종): Parking Brake, Electronic Parking Brake, Seat Belt, Door Open, ABS, MDPS, TPMS, Low Volt Battery, ESC, ESC Off, Airbag, AFS, Master Warning, Auto Hold, Auto High Beam, Headlamp Low/High, Position Lamp, Front/Rear Fog Lamp, Engine Oil Pressure, Engine Temperature, Emergency Call, EV System, Power Down, RBS, Immobilizer, Engine, LED Headlamp, Preheat, Particle Filter, H2 Sensor, H2 Leak, Passenger Airbag Off/On, Turn Signal Left/Right. **DRIVER_ATTENTION 은 없다** → 앱 시험은 디버그 명령으로 대신한다([../../app/README.md](../../app/README.md)).
Virtual Console 여는 법: `adb forward tcp:8088 tcp:8088` → 브라우저 http://127.0.0.1:8088/ (기어 P/R/N/D, 가속·브레이크 페달로 속도도 바뀐다).

**운전자 상태(졸음·주의)**: Vehicle SDK 에 졸음·주의 **수준 값은 없다.** 차가 켜는 `DRIVER_ATTENTION` 경고등이 켜졌는지(get 으로 물어보기)만 알 수 있다. 표준 AAOS(Android 14)에 `DRIVER_DROWSINESS_ATTENTION_STATE` 같은 운전자 모니터링 값이 있지만 권한(`READ_DRIVER_MONITORING_STATES`)이 시스템 앱용으로 알려져 있다 — **Pleos 문서에 언급 없음, 쓸 수 없다고 보는 게 안전.**

## 9. 내비게이션 (NaviHelper SDK 3.0.5)

출처: 소개 https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro · 권한 `.../NaviHelper/declaring-permissions` · 이벤트 `.../NaviHelper/event-listeners` · 목적지 `.../NaviHelper/destinations` · 경유지 `.../NaviHelper/route-with-waypoints` · API `.../NaviHelper/navi-helper/ai.pleos.playground.navi.helper.listener/navi-helper-event-listener/entry`, 데이터 `.../ai.pleos.playground.navi.data/{destination-info,trip-info,…}/entry`

- **받는 법이 Vehicle 과 다르다**: `getXxxInfo()` 를 부르면 값이 바로 오지 않고 등록해 둔 `NaviHelperEventListener` 의 `onXxxInfo` 로 온다(E). 내비 상태가 바뀌면 이벤트가 알아서 온다.
- 권한: 조회 `NAVI_ROUTE`, 경로·경유지 요청 `NAVI_ROUTE_SEARCH`, 내비 화면 열기 `NAVI_CUSTOM_MAP` — **신청 불필요.**
- **내비가 달린 차에서만** 된다. 차에 내비가 있는지 확인하는 방법은 비공개(문의 필요). 실제로 오는 이벤트는 내비 앱 구현·상태에 따라 다르다고 문서에 적혀 있다.
- 주행 중: 데이터 조회 제한은 문서에 없음(내비 검색 화면 UX 는 지역별 제한).
- VC: **내비 관련 카드 없음.** 에뮬레이터에 내비 앱이 돌아가는지는 확인 못 함(?). 내비 모드에 `SIMULATION_GUIDING`(모의 안내)이 있어 모의 주행으로 이벤트를 시험할 수 있을 가능성이 있다.

| 항목 | API (요청 → 이벤트) | 담긴 값·단위 |
|---|---|---|
| 목적지 | `getDestinationInfo()` → `onDestinationInfo` | 이름, 주소, 위도·경도, **남은 거리(m), 남은 시간(초)**, 통행료(원), POI ID |
| 경유지 | `getWaypointInfo()` → `onWayPointInfo` | 경유지별 이름·주소·좌표·남은 거리(m)·남은 시간(초) |
| 현재 위치(내비) | `getCurrentLocationInfo()` → `onCurrentLocationInfo` | 위도·경도, 진행 방향, **주소 문자열**, 경로 ID |
| 경로 상태 | `getRouteStateInfo()` → `onRouteStateInfo` | 안내 중 여부, 내비 모드(NOT_WORKING/SAFE_GUIDING/ROUTE_GUIDING/SIMULATION_GUIDING), 경로 옵션, 도착 시 예상 배터리·연료, **교통 상황**(혼잡 구간, 사고·공사·재난 이벤트) |
| 내비 상태 | `onNavigationStatus` (자동) | INACTIVE / INITIALIZING / FREE_DRIVE(목적지 없음) / ROUTE_DRIVE(안내 중) |
| 다음 회전 안내(TBT) | `getTBTInfo()` → `onTBTInfo` | 회전 종류(`TurnType` — **TURN_RESTAREA 휴게소, TURN_ENTER_SERVICEAREA**, 톨게이트, 터널, 좌·우회전 등), 남은 거리, 설명 |
| 경로 요약 | `requestTrip(TripRequest)` → `onTripInfo` | 총 거리(m), **ETA(초)**, 도착 시 예상 배터리(%), 예상 소비 에너지(Wh), 구간별 혼잡, 경로 위 지점 목록 |
| 주행 정보 | `onDrivingInfo` (자동) | 목적지·경유지별 남은 시간(초)·거리(m)·도착 시 예상 잔량, 충전소 여부·최대 출력(kW)·충전 계획 |
| 도로 제한 속도 | `onCurrentRoadSpeedLimit` (자동, **리스너 등록 후 1초마다**) | Int (km/h 로 보임, 문서엔 단위 없음) |
| 단속 카메라 | `onCameraAlert` (자동) | 카메라 종류(고정·이동·구간·스쿨존 등), 제한 속도(km/h), 남은 거리(m), 구간 평균 속도, **과속 여부(isOverSpeed)** |
| 주의 지점 | `onCautionPointInfo` (자동) | 종류(스쿨존, 사고 다발, 안개, 미끄러운 도로, 낙석, 야생동물, 과속방지턱, 역주행 경고 등 20여 종), 남은 거리(m) |
| 경로 시작·완료·취소·재탐색 | `onRouteStarted`(총 시간·거리·통행료) / `onRoutingCompleted` / `onRouteCancelled` / `onReRouteEnded` / `onRouteOptionChanged` | |
| 도착 | `onDestinationArrived`(걸린 시간·거리·이름·주소), `onWayPointArrived`, `onDestinationChanged`, `onWaypointChanged` | |
| 즐겨찾기 | `getBookmarkInfo()` → `onBookmarkInfo` | HOME / OFFICE / EV 충전소 / 기본 |
| 최근 목적지 | `getRecentDestinationInfo()` → `onRecentDestinationInfo` | 목록 |
| 충전 사업자 | `getChargerOperatorInfo()` → `onChargerOperatorInfo` | ID·이름 |

데이터는 아니지만 관련된 요청: `requestRoute`(목적지 설정), `addWaypoint`/`requestRouteWithWaypoints`(경유지 — 순서 값이 FIRST/SECOND 뿐이라 2개까지로 보임), `changeRouteOption`, `requestNavigationScreen`(장소 검색·주변 충전소·경로 미리보기 화면 열기).
**휴게소·장소를 앱이 검색하는 API 는 없다** — 쉼터 경유지를 넣으려면 좌표를 앱이 따로 알아야 한다(내비 검색 화면을 열어 사용자에게 고르게 하는 것은 가능).

## 10. 위치 (FusedLocation SDK 1.1.3)

출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro · 권한 `.../FusedLocation/declaring-permissions` · 위치 데이터 `.../FusedLocation/location-data-access` · 모델 `.../fused-location/ai.pleos.playground.location.fused.model/location-data/entry`

| 항목 | API | 값·단위 | 권한 | 받는 법 | VC |
|---|---|---|---|---|---|
| 지원 여부 | `isSupported()` | Boolean — 차 모델마다 다름 | — | — | — |
| 마지막 위치 | `getLastKnownLocation()` | `LocationData?`: 위도·경도, 고도, 진행 방향, 신뢰도, 시각(ms). 고도·방향·신뢰도 **단위 문서 없음**. 없으면 null | `FUSED_LOCATION` (dangerous) | G | ? |
| 위치 업데이트 | `registerFusedLocationCallback` → `onChanged` | 위와 같음, 위치가 바뀔 때 | 위와 같음 | C | ? |
| 영역 진입·이탈 | `addProximityAlert(위도, 경도, 반경 m, ENTER/EXIT)` → `onTransition` | 알림 ID, ENTER/EXIT | 위와 같음 | C | ? |
| 위치 차단 안내 | `showLocationAccessBlockedPopup()` | 차의 개인정보 설정으로 위치가 막혔을 때 안내 창 | — | — | — |

- **SDK 신청 상태 "준비 중"** — 지금은 신청할 수 없다. 대신 **NaviHelper `getCurrentLocationInfo`(신청 불필요, 주소까지 줌)** 로 위치를 얻을 수 있다(내비 탑재 차만).
- 안드로이드 표준 위치 권한(ACCESS_FINE_LOCATION 등)의 동작은 "이 문서가 보장하지 않는다"고 적혀 있다.
- 에뮬레이터 위치를 Extended Controls 로 바꾸면 이 SDK 에 반영되는지는 확인 못 함.

## 11. 차량 정보·기타

출처: `.../vehicle.api/{car-info,toll,display}/entry`, FAQ

| 항목 | API | 값 | 권한 | 비고 |
|---|---|---|---|---|
| 차대번호(VIN) | `CarInfo.getVINInfo` | 문자열 | CAR_INFO | 개인정보 — 익명화 규칙 적용 대상. CRN 은 제공 종료 |
| 하이패스 음성 안내 볼륨 | `Toll.getCurrentEtcsVoiceGuidanceVolume` | Int | CAR_TOLL | 요금소 통과·카드 삽입 같은 이벤트 조회 함수는 없음(VC 시나리오만 있음) |
| 플랫폼 종류 | `android.os.Build.DEVICE` | CONNECT_S / CONNECT_L / CONNECT_W | 없음 | **실차에서만** 유효 |
| 지역 | 시스템 속성 `product.sw.target_region` | KOR / EUR / USA / CAN | 없음 | 지역별 주행 규제 분기용 |
| 차 제조사·모델·연식 | AAOS `INFO_MAKE` 등 (Google 기준 `CAR_INFO`) | — | — | Pleos 문서는 "모델명으로 분기하지 말고 capability 로 확인"하라고 함 |

---

## 없음 / 확인 못 함

**없음 (문서·SDK 어디에도 없음)**
- **졸음·주의 수준 값**(운전자 모니터링) — `DRIVER_ATTENTION` 경고등 켜짐 여부만 있다. AAOS 운전자 모니터링 값은 시스템 앱용 권한으로 보인다.
- 운전자 얼굴·시선·실내 카메라 — 없음(하드웨어 직접 접근 금지).
- **실내 CO₂·공기질·미세먼지·습도** — 없음(공기 청정기 켜짐 여부만).
- 비 센서·일사량·바깥 밝기·날씨 — 없음.
- 경고등 **실시간 구독**(`registerTelltales`) — PRIVATE 권한이라 불가. 조회(get)는 가능.
- 실제 타이어 공기압(`getTirePressure`) — PRIVATE 권한이라 불가. 기준값과 TPMS 경고등만.
- 목표 충전량 조회 — 함수 없음(설정만).
- 비상등·제동등 상태 조회 — 함수 없음.
- 앞차와의 거리 등 ADAS 세부 값 — 없음(ADAS 경고등만). 예제 앱 매니페스트에 `READ_ADAS_STATES` 권한이 있지만 문서화된 API 는 없다.
- 시동 상태 조회 함수 — Vehicle SDK 에 없음(값 목록만). AAOS 로는 가능할 수 있음(미확인).
- 장소·휴게소 검색 API — 없음.
- Gleo Interface — 앱이 Gleo 에 기능을 등록하는 방식이라 **차량 데이터를 주지 않는다.**
- LLM SDK — 문서 쪽은 남아 있지만 에뮬레이터 2.1.0 릴리즈 노트에 **"LLM SDK 제공 종료"**.
- 블루투스 동글 연동 — 에뮬레이터 2.1.0 에서 제공 종료.

**확인 못 함 (실측·문의 필요)**
- 에뮬레이터에서 **차 속도를 바꾸는 방법** — Virtual Console 카드 목록에 속도가 없다. (04 문서의 "속도를 바꿔 가며 시험"은 이번 문서 기준으로는 근거가 없다.) 주행 규제(`CarUxRestrictions`)를 에뮬레이터에서 켜는 방법도 미확인.
- AAOS 속성(`PERF_VEHICLE_SPEED`, `GEAR_SELECTION`, `IGNITION_STATE`, `ENV_OUTSIDE_TEMPERATURE`, `FUEL_LEVEL`)이 Pleos 에뮬레이터·차에서 실제로 읽히는지, 권한 허용 창이 뜨는지.
- 트립 운전 시간 단위(초/분), "Current" 트립의 시작 기준.
- 연비·전비 단위, EV 배터리 에너지 단위.
- ★ 문서 숨김 함수(기어, 주행 가능 거리, 배터리 에너지·용량, 충전구)를 공모전 앱에서 써도 되는지.
- 에뮬레이터에서 NaviHelper 이벤트·FusedLocation 위치가 나오는지.
- 콜백이 오는 주기(값이 바뀔 때만인지, 일정 주기인지) — 문서에 없음(내비 제한 속도만 "1초마다").

**권한·신청 정리**
- 신청 없이 지금 쓸 수 있음: **Vehicle 조회(CAR_* 권한, 매니페스트 선언만)**, **NaviHelper(NAVI_* 권한)**, AAOS `CarUxRestrictions`.
- 신청해야 하는데 지금은 불가("준비 중"): **FusedLocation**, TTS, STT, **Vehicle Control(모든 set)**.
- 일반 앱은 못 씀: PRIVATE_* (경고등 구독, 실제 타이어 공기압, capability 정보).
- AAOS 속도·에너지 권한은 Google 기준 사용자 허용이 필요할 수 있음(미확인).

---

## 스노봇 활용 아이디어 (결정은 사용자)

| 데이터 | 한 줄 아이디어 |
|---|---|
| 기어 P/D ★ | P 로 바뀌면 "도착했어요" 인사·주차 위치 저장 시작, D 면 주행용 조용한 화면으로 |
| `CarUxRestrictions` + 차 속도 | 주행 중엔 말·표정만, 정차 중엔 터치 메뉴를 여는 기준 |
| 트립 운전 시간 | 오래 연속 운전하면 쉬자고 권유(단위 실측 후 기준 정하기) |
| 내기 순환 + 운전 시간 + 실내 온도 | 내기 순환이 오래 켜져 있으면 환기 권유(CO₂ 자체는 모듈 센서로) |
| `DRIVER_ATTENTION` 경고등 | 차가 주의 경고를 띄우면 스노봇도 걱정 표정·휴식 제안(폴링 필요) |
| TBT `TURN_RESTAREA` / 경유지 추가 | 휴게소가 다가오면 "여기서 쉬어 갈까요?" → 경유지 추가 |
| 좌석 탑승 | 동승자 수에 따라 인사 바꾸기, 내릴 때 뒷좌석 확인 알림 |
| 안전벨트 착용 / 미착용 경고 | 출발 전 벨트 안 맨 자리를 귀엽게 알려 주기 |
| 문 열림·잠금 | 하차 후 문이 안 잠겼으면 알림, 문 열림 = 하차 시점 감지 |
| 창문 위치 + 와이퍼 | 비 오는데 창문이 열려 있으면 알림, 비 오는 날 표정 |
| 와이퍼·전조등·안개등 | 날씨·밤 분위기를 짐작해 표정·말투 바꾸기 |
| 실내 온도·설정 온도·열선 | "좀 덥죠?" 같은 대화(제어는 아직 불가 → 안내만) |
| EV 잔량·주행 가능 거리 ★ / 연료 % | 부족하면 충전·주유 권유, 내비 주변 충전소 화면 열기 |
| 도착 예상 배터리(TripInfo) | 목적지까지 배터리가 빠듯하면 미리 알려 주기 |
| 경고등 목록 | 켜진 경고등을 캐릭터가 쉬운 말로 설명 |
| 엔진오일·워셔액 | 정비가 필요할 때 부드럽게 알림 |
| 목적지 남은 시간·ETA | 도착 카운트다운, 집 SnowBot 에 "곧 도착" 전달 |
| 도착 이벤트 | 하차 인사 + 주차 위치 저장(S1) 트리거 |
| 단속 카메라 과속 여부 / 제한 속도 | 과속 중이면 스노봇 표정이 굳어지는 식의 은근한 피드백 |
| 주의 지점(스쿨존·안개·결빙·낙석) | 미리 한마디 경고 |
| 교통 상황(사고·정체) | 막히는 구간에서 기분 풀어 주는 말 |
| 위치·영역 진입(FusedLocation 준비 중 / 내비 현재 위치) | 집 근처에 오면 집 SnowBot 깨우기 |
| 총 주행거리 | 함께 달린 거리로 캐릭터 성장·기념일 만들기 |
| 드라이브 모드 ECO/SPORT | 모드에 맞춘 표정(신남/차분) |
| 앰비언트 라이트 색 | 스노봇 표정 색을 실내 조명 색과 맞추기 |
| 방향지시등·조향각 | 눈동자가 가는 쪽을 보는 작은 반응(주의 분산 안 되게 최소로) |
