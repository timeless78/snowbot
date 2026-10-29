# SnowBot (가칭)

책상 위에 두는 **눈사람 모양 AI 로봇**. 돌아다니지 않고, 말을 알아듣고, 카메라로 상황을 보고,
스피커로 대답한다. 얼굴 화면은 없다. **눈은 흰자 + 검은자인 2개 모양이고 그중 1개의 검은자 속에 카메라**가 있다. 다른 1개는 장식이다.
**흰 기계식 눈꺼풀**, **움직이는 나뭇가지 눈썹**, 고개 움직임(좌우·끄덕임), 목소리로 감정을 표현한다(서보 4개). 짧은 당근 코를 달고,
목도리는 탈착식 액세서리, 초기 버전에는 팔과 LED 가 없다.
로봇 안에는 ESP32-S3 제어 보드만 넣고, Mac 의 Python 서비스가 클라우드 AI API 와 로봇 사이를 중계한다
(나중에 Mac 로컬 모델로 바꿀 수 있게 만든다).

상태(2026-10-26): 기획·가능성 검토 단계. 부품 장바구니 준비 완료(결제 전), 외형은 C 공업 디자인으로 결정.

**현대차·기아 차량용 앱 공모전(Pleos)과 연계해 진행한다** — 기획안 마감 **2026-11-15**, 12월 앱 시연.
공모전에서 SnowBot 은 "Pleos 모듈 허브" 앱의 레퍼런스 디바이스가 된다. → [docs/pleos/](docs/pleos/README.md)

## 문서

| 문서 | 내용 |
|---|---|
| [docs/contest-plan.html](docs/contest-plan.html) | **공모전 계획 한 장 페이지** (아티팩트: https://claude.ai/artifact/NFp3Lqst5Vg3ajZ9arGWkW) — 기기 연결·대화 순서·기능·사업성·캐릭터 봇·일정 |
| [docs/pleos/README.md](docs/pleos/README.md) | **Pleos 공모전** — 일정, 확정된 방향, SnowBot 설계와 맞춰 볼 점, 남은 작업 |
| [docs/pleos/01-proposal.md](docs/pleos/01-proposal.md) | **공모전 기획서 초안** — 접수양식 8개 항목 순서, 사업성 숫자(가정 표시) |
| [docs/pleos/02-research.md](docs/pleos/02-research.md) | 공모전·Pleos SDK·사용자 불편 조사 자료와 출처 |
| [docs/pleos/03-module-protocol.md](docs/pleos/03-module-protocol.md) | 모듈 ↔ 앱 연동 규격 초안 v0.1 |
| [docs/08-brand-character-bots.md](docs/08-brand-character-bots.md) | **브랜드 캐릭터 봇 시안** — 같은 기구에 겉만 바꾼 현대 SD 휴머노이드(BOT-H1)·기아 SD 호랑이(BOT-K1), 브랜드 자산 주의 |
| [docs/10-ai-services.md](docs/10-ai-services.md) | **AI 서비스 비교·추천** — 듣기·생각·말하기·사진 4자리 후보, 1회 대화 비용, 추천 조합, 비교 시험 계획 |
| [app/](app/README.md) | **차량 앱 (Pleos 에뮬레이터)** — 짝 맺기, 표정 버튼, 화면 속 가상 스노봇, CO₂ 환기 제안. 화면 `docs/assets/app/` |
| [server/](server/README.md) | **중계 서버 (프로토타입)** — WebSocket 으로 스노봇·앱 연결, 짝 맺기, 가상 스노봇·가상 앱 시험 도구. 운영은 [docs/09-prototype-server.md](docs/09-prototype-server.md) |
| [docs/07-communication-ai.md](docs/07-communication-ai.md) | **기기 간 통신과 AI 처리 과정** — SnowBot·차량 앱이 각자 인터넷으로 서버에 연결, 집 Wi-Fi/폰 핫스팟, 음성 대화·차량 이벤트·글레오 호출 순서도 |
| [docs/01-concept.md](docs/01-concept.md) | 출발점(ChatGPT 대화 정리), 요구사항, 가능성 판단, 구조, **디자인 결정(5절)**, 비용, 제작 순서, 남은 결정 |
| [docs/design-gallery.html](docs/design-gallery.html) | **디자인 기록 웹 페이지** — 그림 20장을 만든 순서대로 (아티팩트: https://claude.ai/artifact/3p3FvAGrNpDrctKtrsg7o7). 압축 이미지는 docs/assets/web/ |
| [docs/parts-list.html](docs/parts-list.html) | 부품 목록 웹 페이지 (아티팩트: https://claude.ai/artifact/NYE35yKLxHQJ84YuXvVUU7). 디바이스마트 기준 내용이라 알리 장바구니는 03 문서를 본다 |
| [docs/05-cad-stages.md](docs/05-cad-stages.md) | **3D 설계 단계** — 기준 그림 비율(키 152mm)로 1~5단계 완료. **지금은 기능 확인용 시제품, 기능 완성 뒤 기준 그림과 똑같은 완성품** (맞출 디테일 목록 포함) |
| [docs/06-print-order.md](docs/06-print-order.md) | **출력 주문표** — 1차(시험)·2차(본) 묶음, 색·메모, 업체 질문, 공차 시험 판 쓰는 법, 추가로 살 재료. 파일은 `print/` |
| [cad/](cad/README.md) | **3D 설계 코드** (build123d) — 1차 배치 모델, 미리보기 `cad/out/preview.png` |
| [docs/04-body-build-plan.md](docs/04-body-build-plan.md) | **외형 제작 준비** — 공구, 설계 도구, 잴 치수, 출력 조각 목록, 시험 출력, 일정 |
| [docs/03-parts-list.md](docs/03-parts-list.md) | **부품 구매 목록** — 1단계 전자부품(약 7만 원)·2단계 기구·외형(약 6~10만 원), 배선·전원, 출력 대행 |
| [docs/02-design-options.md](docs/02-design-options.md) | 디자인 시안과 **선택안: C 공업 디자인** (이전 선택 D + 큰 머리는 기록으로 남김) |
| [docs/assets/design-tech/c-industrial.png](docs/assets/design-tech/c-industrial.png) | **선택한 외형: C 공업 디자인 (2026-10-26)** — 세부 사양·분해도는 02 문서 |
| [docs/assets/design-tech/](docs/assets/design-tech/) | 테크 눈사람 시안 4가지(A 프리미엄 가전 · B 얼음 투명 · C 공업 디자인 · D 로우폴리)와 C 분해도 |
| [docs/assets/design-options/e-snow-bighead.png](docs/assets/design-options/e-snow-bighead.png) | 이전 선택 (D + 큰 머리, 눈 뭉치 질감) — C 로 바뀌어 쓰지 않음 |
| [docs/assets/snowbot-eye-compare.png](docs/assets/snowbot-eye-compare.png) | 눈 모양 비교 — 석탄 눈 vs 흰자 + 검은자 (**흰자 + 검은자로 결정**) |
| [docs/assets/snowbot-expressions-v4.png](docs/assets/snowbot-expressions-v4.png) | **표정 그림 v4 (현재)** — 흰자·검은자 눈, 눈꺼풀·눈썹·고개로 만든 표정 10가지 |
| [docs/assets/snowbot-expressions-v3.png](docs/assets/snowbot-expressions-v3.png) | 표정 그림 v3 (석탄 눈, 이전 판) |
| [docs/assets/snowbot-concept-v2.png](docs/assets/snowbot-concept-v2.png) | 콘셉트 그림 v2 (이전 판: 매끈한 흰 몸체·석탄 눈·눈썹 없음) |
| [docs/assets/snowbot-concept-chatgpt.png](docs/assets/snowbot-concept-chatgpt.png) | ChatGPT 초기 콘셉트 이미지 (얼굴 화면·팔이 있는 옛 디자인, 참고용) |
