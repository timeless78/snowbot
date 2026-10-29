# 리서치 자료 (2026-10-24 ~ 10-26 조사)

## A. 공모전 정보
- 주최: 현대차·기아 / 대상: 대학(원)생, 일반 개발자, 스타트업 / 개인 또는 최대 5인 팀
- 접수: 2026-10-21 ~ 11-15, 공식 홈페이지 https://appdevchallenge.auto-hmg.io
- 절차: 서비스 기획안 제출 → 11월 서류 심사 → 12월 앱 시연 평가
- 시상: 대상·최우수·우수·특별상 등 10팀, 총 5,100만 원
- 수상팀: 내년 초 개발자 행사에서 부스 운영·최종 시연·시상식, 우수 아이디어 실제 출시 검토
- 공모 분야: 인카 엔터테인먼트, 모빌리티 라이프 케어, 안전 운전, 패밀리·동승자 케어
- 개발: Pleos Playground SDK·API 사용 필수
- 배경: 2026년 8월 사내 해커톤 "24H 코드 레이스"(220여 건 접수, 25팀 본선)에 이은 외부 확대
- 출처: 오토뷰 https://www.autoview.co.kr/ko-kr/articles/102156 · 위키트리 https://www.wikitree.co.kr/articles/1167905 · 링커리어 https://linkareer.com/activity/358198 · 현대차그룹 해커톤 https://www.hyundaimotorgroup.com/ko/news/CONT0000000000221655

## B. Pleos Playground 개발 수단
### B1. Pleos Connect SDK (차량 내 앱, AAOS 기반, Kotlin/Java)
- 모듈: Vehicle SDK, NaviHelper SDK, ADAS SDK, Gleo AI SDK(STT·TTS·LLM), Fused Location SDK, Sync SDK
- 인앱 구매, 데이터 동기화, 사용자 로그 수집 지원
- Vehicle API 그룹: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, HVAC, Light, Odometer, Safety, Seat, SideMirror, Steeringwheel, Tire, TurnSignal, Window, Wiper
  - ⚠ 속도 API, 카메라 영상 API는 목록에서 확인되지 않음 → 상세 문서 확인 필요
- NaviHelper: 목적지·경유지 설정, 경로 조회, 충전 사업자 정보
- Fused Location: GPS·IMU·카메라 결합 위치
- 개발 환경: SDK v2.0.5, Android API 26+, Gradle 8+, Pleos Connect Emulator(Android Studio AVD, cold boot)
- 블루투스: 에뮬레이터는 BT 제한적 → USB BT 동글 passthrough 연동 (macOS는 Bumble HCI bridge)
- 주변기기: Pleos 사이트에 "호환 주변기기 개발도 지원 확대 예정" 언급
- Sync SDK: JSON 문서 단위 operation 동기화, 다중 클라이언트, 음영 지역 대응
- 출처:
  - https://pleos.ai/playground/resources/api-reference/connect-sdk
  - https://pleos.ai/playground/resources/en/api-reference/connect-sdk/Vehicle/intro
  - https://pleos.ai/playground/resources/docs/guide/getting-started/application-development/setup-application-development-environment
  - https://pleos.ai/playground/resources/en/api-reference/sync-sdk
  - https://pleos.ai/playground/develop

### B2. Vehicle Data API (서버 REST, OAuth 2.0)
- 차주 동의 받은 커넥티드카의 충전·위치·운행·센서·공조 상태 조회
- 엔드포인트 예: `/v1/vehicles/{vin}/driving`, `/speed`, `/status/data`
- 동의 철회 기능 구현 의무
- PHP 서버에서 연동 가능
- 출처: https://pleos.ai/playground/resources/api-reference/vehicle-data-api/intro

### B3. Fleet API — 법인 차량용, 계약 필요. 공모전과 무관

## C. 현대차가 이미 가진 것 (겹치지 말 것)
- **글레오 AI**: LLM 기반 에이전트. 차량 제어, 지식 검색, 여행 일정 추천, 대화, 발화자 위치 인식. 더 뉴 그랜저(2026.5)부터 적용, 투싼 등 확대
  - https://v.daum.net/v/20260531090156023 · https://www.dt.co.kr/article/12063683 · https://inews24.com/view/2011170
- **페르소나 AI**: 브랜드별 AI 정체성 구축 중 → "AI 캐릭터"만으로는 차별성 없음
  - https://www.mt.co.kr/article/2026102019575795544
- **플레오스 커넥트 전 차종 순차 적용** (현대·기아·제네시스) — 하위 트림에도 탑재
  - https://dealsite.co.kr/articles/161078
- **앱 마켓**: 네이버 오토, 네이버 지도, 유튜브, 스포티파이 등
- **현대모비스 인캐빈 모니터링(ICM)**: 졸음, 휴대전화 사용, 안전벨트, 후석 어린이 방치 감지 (카메라+SW)
  - https://www.hyundaimotorgroup.com/ko/news/CONT0000000000168389
- **레이더 후석 승객 알림(ROA)**: GV70 세계 최초 적용
  - https://www.tech42.co.kr/?p=32286
- 커넥티드 앱(마이현대/기아 커넥트) 평판 낮음: 위젯 편집 불가, 광고 과다 불만 (클리앙)

## D. 커뮤니티 불편 조사 (주로 클리앙 굴러간당·육아당·공원)
### D1. 지하주차장 층 기억
- 폰 기압계로 층 판별 실험 글, 댓글 47개. 언덕길이 지하 3층보다 기압 변화 큼 → 차체 기울기로 램프 세는 방식으로 변경
- 예외: 게이트별 B1/B2 직행, 반층(스타필드·목동), 지상 주차장, "1 L B2 B3" 표기
- "집에서 더 자주 잊는다", 백화점은 기둥번호 사진 찍음
- https://www.clien.net/service/board/cm_car/19263956

### D2. 아파트 충전 갈등
- 충전구역을 지정 주차석처럼 점유, 연락 시 핑계 → 늦게 귀가하면 충전 불가 (댓글 37)
  - https://www.clien.net/service/board/cm_car/19256196
- 충전기 1대·주차면 2개 → 충전 시작 안 된 선을 뽑았다가 분쟁·경찰 출동. 원인은 차주가 충전 실패를 몰랐던 것
  - https://www.clien.net/service/board/cm_car/19236697
- 설문: 충전이 최대 불편(29.0%), 시설 부족 38.6%, 충전질서 21.2%, 고장 14.3% — https://kbmaeil.com/article/202405260394867
- 완속 14시간 초과 과태료 10만 원 등

### D3. 전기차 차계부
- 마이클은 풀투풀 기준이라 구간 충전 기록 어려움, 충전 후 기록 잊음, 제조사 앱 자동 기록 희망
  - https://www.clien.net/service/board/cm_car/19128745
- 마이현대 앱 "누더기" (댓글 42) — https://www.clien.net/service/board/cm_car/19054518

### D4. 반려동물
- 마트 옆 차에 강아지 두고 감, "테슬라 애견 모드 부럽다" — https://www.clien.net/service/board/park/16445476
- 지하주차장 강아지 방치 목격 — https://www.clien.net/service/board/park/19102164
- 외기 29℃ → 차 안 10분 뒤 40℃, 1시간 뒤 53℃ / 차내 방치 처벌 동물보호법 개정안 발의 — https://www.dailyvet.co.kr/?p=135567

### D5. 차박
- 영하 14℃ 차박, 배터리 90%→30%, 1~2시간마다 직접 확인 — https://www.clien.net/service/board/cm_car/19119355
- 초보 가족 차박 질문, 공조 소음 민폐 우려 — https://www.clien.net/service/board/cm_car/19047120
- 차박·취사 금지구역 판별 어려움, 과태료 300만 원 이하 — https://www.ajunews.com/view/20210825151303125

### D6. 졸음·휴식
- 서울-춘천 37km 휴게소 없음, 외곽순환 최대 48.95km — https://www.ytn.co.kr/_ln/0103_201607160504139290
- 졸음·전방주시 태만이 고속도로 사망사고 절반 이상 — https://mediahub.seoul.go.kr/archives/2018723
- 도로공사 CO₂ 감지 졸음 경고장치 무상 보급 — https://www.imaeil.com/page/view/2022031010311082784

### D7. 아이 동승
- 카시트 거부·울음, 장거리에서 잘 안 잠, 영상 노출 꺼림, 휴게소 키즈시설 공유 (클리앙 육아당)
- 전기차 멀미 — 회생제동 미숙 지적 다수 — https://www.clien.net/service/board/cm_car/18849674

### D8. 고령 운전자
- 터치 공조·다이얼 기어가 오히려 사고 유발 우려 — https://www.clien.net/service/board/cm_car/19188402

### D9. 플랫폼 관련
- 플레오스 커넥트 12.9인치/14.9인치 화면 구성 차이 → 반응형 UI 필수 — https://www.clien.net/service/board/cm_car/19185018
- 순정 내비는 바퀴 센서 연동으로 지하·터널 위치 정확 — https://www.clien.net/service/board/cm_car/19253989

## E. 조사 한계
- 네이버 카페·검색은 브라우저 보안 제한으로 접근 불가, 보배드림은 페이지별 승인 필요로 제외 → 클리앙 중심
