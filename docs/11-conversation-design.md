# 대화 설계 — 스노봇은 어디까지 말해야 하나 (2026-10-28)

사용자 질문: "봇과 내가 대화하는 부분이 중요한데, 어느 수준까지 대화를 해야 하는지 가늠이 안 된다."

이 문서는 웹 조사(2026-10-28, 출처는 맨 아래)와 기존 문서([01 콘셉트](01-concept.md), [07 통신·AI](07-communication-ai.md), [Pleos 요강](pleos/00-contest-rules.md), [기획서](pleos/01-proposal.md), [리서치](pleos/02-research.md))를 바탕으로 썼다.
근거의 세기를 **[근거 강]**(여러 연구·공식 자료) · **[근거 중]**(연구 1~2건, 작은 표본, 시뮬레이터) · **[근거 약]**(회사 발표·후기 1건) · **(추측)**(근거 없이 판단한 것)으로 표시한다.

---

## 1. 요약

1. **대화의 "깊이"는 점수가 아니다.** 요강 심사 기준(차량특화성·창의성·기술 활용성·사업성)에 "대화를 잘하는가"는 없다. 심사위원이 볼 것은 **차의 상황을 알고 말하는가**, **글레오와 무엇이 다른가**, **SDK 가 대화에 실제로 쓰이는가**, **사람들이 계속 쓸 이유가 있는가**다.
2. **운전 중 음성 대화도 머리를 쓴다.** AAA(미국 자동차협회) 연구에서 차량 음성 시스템 사용은 5점 척도 평균 3.34 로, 라디오 듣기(1.2)보다 훨씬 높았다 [근거 강]. 그래서 주행 중에는 **짧게, 적게** 말해야 한다.
3. **옆자리 동승자와의 대화는 전화 통화보다 덜 위험하다.** 동승자는 도로를 보고 위험할 때 말을 멈추기 때문이다 [근거 중]. 스노봇이 가질 수 있는 가장 분명한 차별점은 여기다: **차량 데이터로 "도로를 보는 동승자"처럼 말을 멈추고, 줄이고, 때를 고른다.** 글레오는 묻는 말에 답하는 비서이고, 스노봇은 상황을 보고 말할지 말지를 정하는 동승자다.
4. **몸이 있는 로봇은 정이 붙지만, 몇 주 지나면 질린다.** 로봇이 있으면 스마트폰보다 부담이 줄고 친밀감이 늘었다는 연구(MIT AIDA)가 있다 [근거 중]. 반면 가정용 로봇 사용은 2~4주 뒤 크게 줄었다 [근거 중]. 오래 쓰게 하는 것은 긴 잡담이 아니라 **나를 기억하고 이어서 말하는 것, 매일 반복되는 계기(출퇴근·주차)** 다.
5. **추천: 4단계(L0~L3) 중 프로토타입·PT 는 L0·L1 을 단단하게, L2 를 짧게 보여 주고, L3 는 정차·집 장면 하나로만.**
   - L0 표정·고개만(말 없음), L1 차량·센서 사건에 맞춘 한마디, L2 짧은 자유 대화 + 기억, L3 정차·집에서의 긴 대화.
   - 주행 중 한 번에 말하는 길이: **한 문장, 25자 안팎(약 3~4초), 정보 2~3개.** 정차 중 2~3문장, 집에서는 제한을 푼다.

---

## 2. 레퍼런스

### 2-1. 차 안 AI 비서·동반자

| 제품 | 대화 방식 | 먼저 말 거나 | 성격·감정 | 기억 | 반응·평가 | 근거 |
|---|---|---|---|---|---|---|
| **NIO NOMI / NOMI Mate** (중국) | 대시보드 위 동그란 머리가 말하는 사람 쪽으로 돈다. 2024년 LLM(NOMI GPT) 탑재 | 회사 설명: 피곤해 보이면 음악을 틀고 가벼운 대화를 건다 (의도 설명, 실제 동작 기록은 못 찾음) | "도구가 아니라 동반자"가 설계 목표. 눈 모양 표정 | 사용자 취향 학습(회사 설명) | 레드닷 수상, 2024년 6월 상호작용 1,000만 회(회사 발표 — 만족도 아님). 독립 사용자 조사는 못 찾음 | [근거 약] |
| **Mercedes MBUX Virtual Assistant** (CLA, 2025) | LLM 기반 여러 턴 대화, 짧은 대화 기억 | 학습한 습관·상황으로 제안(아침 뉴스, 운전 후 마사지) | 성격 4가지: 자연스러움·예측·개인화·공감. 화면 속 별 모양 아바타가 기분 표현 | 습관 학습 | InsideEVs 기자 시승: "다른 차로 바꾸니 아쉬웠다". Auto Express 는 회의적(구형일 수 있음) | [근거 약] |
| **VW IDA + ChatGPT** (Cerence, 2024) | 일반 질문은 ChatGPT 로 넘김 | 거의 없음 | 없음 | **차량 데이터와 분리, 이전 대화 기억 없음** | CES 체험 기사: 질문을 잘 못 알아듣고 없는 회사 이름을 지어냄(T3) | [근거 약] |
| **BMW IPA + Alexa+** (iX3, 2026) | LLM 기반 자연 대화, 맥락·이어지는 질문 | 자료 부족 | 자료 부족 | 아마존 계정 연결 시 개인 기능 | 독일 2026년 4월 시작, 사용자 평가 자료 아직 없음 | [근거 약] |
| **Li Auto 리샹퉁쉐 (MindGPT)** (중국) | 차량 제어 + 생활 서비스(주문·결제)까지 | 자료 부족 | — | — | 제3자 보고서: 하루 1억 2천만 건 요청(2025-01, 회사 확인 아님). 음성 명령 72%가 운전석 | [근거 약] |
| **Toyota Yui (Concept-i·LQ, 2017~2020)** | 콘셉트. 표정·목소리로 감정 추정, 대화로 졸음 방지 | 식당 추천, 심부름 상기, 경로 변경 | 감정 교감이 목표 | 취향 학습 | 양산 안 됨. 얼굴 인식이 "편안한 얼굴"을 "졸린 얼굴"로 오인하지 않게 조정했다는 언급 | [근거 약] |
| **Toyota Kirobo Mini (2016~2017, 일본)** | 컵홀더 크기 대화 로봇, 몸짓·깜빡임, 폰 앱 블루투스 연결 | 차량·집 정보로 화제를 꺼냄 | 슬프거나 화나 보이면 즐거웠던 대화를 꺼냄 | 좋아하는 것·같이 간 곳 기억 | 기사 평가: "직접 묻는 말에 답하는 수준". **스노봇과 가장 비슷한 선례** | [근거 약] |
| **MIT AIDA (VW 협력, 연구)** | 대시보드 로봇 vs 스마트폰 vs 화면 속 캐릭터 비교 | 알림 전달 | 표정 | — | 시뮬레이터: **로봇이 부담을 줄이고 친밀감을 더 높였다**, 스트레스 감소·안전 조치 증가 | [근거 중] |
| **현대·기아 글레오 AI** (그랜저 2026-05~, 투싼·아반떼) | LLM 에이전트: 차량 제어, 지식 검색, 여행 추천, 발화자 위치 인식 | 자료 부족 | 브랜드별 "페르소나 AI" 구축 중(2026-10 보도) | — | 시승기 위주. 개발자: 환각(지어내기)이 가장 어려운 과제 | [근거 약] |

차 음성 비서 전반: J.D. Power 품질 조사에서 음성 인식은 계속 불만 상위 항목이고, 중국 2025년 조사에서는 "쓰기 어려운 음성 인식"이 가장 나빠진 항목 중 하나였다 [근거 강].

**읽을 점**
- 큰 회사들은 모두 "LLM 으로 무엇이든 대답하기"로 가고 있다. 스노봇이 같은 방향으로 가면 글레오와 정면으로 겹친다.
- **차 안에 물리적 캐릭터를 둔 사례(NOMI, Kirobo Mini, AIDA)** 가 스노봇과 가장 가깝다. 공통점은 "말을 많이 하는 것"보다 **고개 돌리기·표정·짧은 말**로 존재감을 준다는 것이다.
- VW 사례처럼 **차량 데이터와 기억이 없는 대화는 평범한 챗봇**이 된다. 스노봇은 차량 데이터·기억을 대화에 넣는 쪽이어야 한다.

### 2-2. 책상·동반 로봇

| 제품 | 살아 있다고 느끼게 한 것 | 실망·실패 | 근거 |
|---|---|---|---|
| **Anki Cozmo / Vector** | 픽사 출신 애니메이터, "감정 엔진"(성공하면 자신감↑, 낭떠러지 감지하면 겁↑), 표정 1,500개, **예측 못 하는 작은 행동**(갑자기 소리, 잠들기), 일부러 완벽하지 않게 | 2019년 회사 문 닫음 | [근거 중] |
| **Living.ai EMO** | 표정 1,000개 이상(판매처 주장), 기분 표현(춤, 처진 눈) | "깊은 AI 도 스마트홈 비서도 아니다" | [근거 약] (판매처 글 다수) |
| **Loona** | ChatGPT-4o 대화, 몸짓 인식 | 대화 지연·품질의 독립 평가 못 찾음 | [근거 약] |
| **Ropet** (CES 2025) | 기능보다 **감정 반응과 작은 움직임**에 집중, 수줍음·졸림 같은 성격 | 장기 사용 평가 없음 | [근거 약] |
| **Jibo** (2017~2019) | 몸 움직임 | 비싼 값(약 900유로)에 비해 할 수 있는 일이 스마트 스피커보다 적었다. 서버 종료 | [근거 중] |
| **Embodied Moxie** (아이용, ~2024-12) | 아이와 대화하는 동반 로봇 | 투자 실패로 회사 종료 → **클라우드에만 의존해 로봇이 작동을 멈춤**, 약 800달러 환불 거의 없음, 아이들이 상실감 | [근거 강] (여러 언론) |
| **Samsung Ballie** | — | 2025 여름 출시 약속 → 연기 → 2026-01 무기한 보류. 이유: **"무엇에 쓰는가"가 불분명**, 이미 있는 기기와 겹침 | [근거 중] |
| **Friend 목걸이** (2025) | 항상 듣고 문자로 말 걸기 | 알림이 너무 많음, 시끄러운 곳에서 잘 못 알아들음, 항상 듣는다는 거부감 | [근거 중] |
| **Character.AI** | 캐릭터와 끝없는 대화 | 미성년자 피해 소송 → 2025-11 부터 18세 미만 자유 대화 금지 | [근거 강] |
| LG Q9, Eilik | — | 이번 조사에서 자료를 찾지 못함 | — |

연구 쪽:
- **신기함은 2~4주 뒤 떨어진다.** 8~9세 아이 321명에게 Cozmo 를 8주 준 연구에서 수용도가 2~4주 뒤 가장 크게 떨어졌고, 끝까지 꾸준히 쓴 아이는 약 30% [근거 중].
- Guy Hoffman(IEEE Spectrum)의 정리: 소셜 로봇이 오래 못 간 큰 이유는 **"한 번 묻고 한 번 답하는 구조"를 벗어나지 못한 것**, 즉 지난 대화를 이어 가지 못한 것. 그리고 **몸이 있는 물건은 목소리만 있는 비서가 못 주는 정서적 끌림을 준다** [근거 중, 전문가 의견].

**읽을 점**
- 살아 있다는 느낌은 **말보다 비언어(표정·고개·타이밍·작은 우연)** 에서 나온다. 스노봇의 서보 4개(눈꺼풀·눈썹·고개 2축)는 이 점에서 이미 맞는 방향이다.
- 실패 원인은 "대화가 얕아서"보다 **쓸모 불분명(Ballie·Jibo), 클라우드 의존(Moxie·Jibo), 말이 너무 많음(Friend), 안전(Character.AI)** 이다.

---

## 3. 운전 중 대화 제약

### 3-1. 정해진 기준

| 기준 | 내용 | 스노봇에 주는 뜻 |
|---|---|---|
| **NHTSA 시각·수동 주의분산 지침 1단계(2013, 차량 내장)·2단계(휴대·애프터마켓)** | 화면을 보는 한 번의 시선이 2초를 넘지 않을 것, 한 작업의 화면 보기 합계 12초 이하 | 스노봇 **실물도 운전자가 쳐다보게 만드는 물건**이다. 정보는 반드시 소리로 전하고, 표정은 보조로만. 주행 중 큰 움직임은 시선을 끈다(추측 — 시험 필요) |
| NHTSA 음성(청각·발화) 지침 | 이번 조사에서 확정된 수치 기준을 찾지 못함 | 아래 연구 기준을 쓴다 |
| **Android for Cars 디자인 지침** | 주행 중 문구는 짧고 분명하게, 중요한 정보를 앞에, 비상시가 아니면 안심시키는 말투 | "중요한 말 먼저" — "다음 쉼터 12km예요"처럼 숫자를 앞쪽에 |
| **FHWA(미국 도로청) 운전자 정보 지침** | 정보 단위(핵심 명사·형용사) 7~9개가 기억 한계, 급하지 않은 메시지 7개 이하, 길 안내는 3~4개 | 주행 중 한마디는 **정보 2~3개** 로 (예: "쉼터·12km·커피") |
| Pleos 주행 규제(`CarUxRestrictions`) | 주행 중 키보드·설정·영상 제한 | 이미 기획서에 반영됨 |

### 3-2. 연구 결과

- **음성도 부담이 있다.** AAA 재단(Strayer 외 2015): 차량 음성 시스템 평균 3.34/5, 라디오·오디오북 1.2/5. 2 이상이면 위험할 수 있다고 본다 [근거 강]. 자동차 회사들은 "사고와의 연결은 입증 안 됨"이라고 반박했다.
- **긴 음성 메시지는 기억을 방해한다.** 짧은 신호음·단어 하나는 괜찮았고 긴 말은 단기 기억을 방해했다. 운전자는 메시지 길이와 상관없이 4개 안팎만 기억했다 [근거 중].
- **동승자 대화가 전화보다 안전한 이유:** 동승자는 교통 상황을 보고 말을 멈추거나 위험을 알려 준다. 영상통화로 상대가 도로를 볼 수 있게 하면 동승자와 비슷해졌다 [근거 중, 반대 결과도 1건 있음].
- **말 거는 때가 중요하다.** 운전자 63명에게 주행 중 "지금 말 걸어도 돼?"를 반복해 물은 연구(Semmens·Ju, CHI 2019): 차량 데이터만으로 좋은 때를 우연보다 잘 고를 수 있었지만 완벽하지 않았다 [근거 중]. KAIST 연구도 "말 걸어도 된다"는 잘못된 판단을 줄이는 쪽(보수적)으로 모델을 만들었다 — 잘못된 때 말하면 위험하고 사용자가 기능을 꺼 버리기 때문 [근거 중].
- **목소리의 기분을 운전자 기분에 맞추면 운전이 나아졌다.** Nass 연구진(2005, 시뮬레이터, 약 40명): 기분 좋은 운전자에게는 밝은 목소리, 기분 나쁜 운전자에게는 차분한 목소리일 때 사고가 적고 차와 더 많이 대화했다 [근거 중]. **스노봇의 귀여운 목소리(Suzu)는 피곤하거나 짜증 난 운전자에게 맞지 않을 수 있다** — 말투·속도를 상황별로 바꿔야 한다.
- 특허(근거 약)에서 제안된 방법: 먼저 말 거는 횟수를 트립·시간당 상한으로 묶고, 사용자가 양을 정하게 한다.

### 3-3. 그래서 정하는 운전 중 규칙

| 상태 | 판단 데이터 | 스노봇이 하는 것 |
|---|---|---|
| **주행 중 — 복잡한 순간** | 길 안내 회전 지점 접근(NaviHelper), 방향지시등 켜짐, 급제동·ADAS 경고(Safety), 고속 | **말하지 않는다.** 사용자가 말을 걸어도 "듣는 표정"만 하고, 순간이 지나면 답한다(최대 몇 초 보류). 안전 경고는 차가 하므로 스노봇은 끼어들지 않는다 |
| **주행 중 — 평소** | 속도 > 0, 주행 규제 켜짐 | 묻는 말에 **한 문장(25자 안팎, 약 3~4초), 정보 2~3개.** 대화는 2~3턴까지. 잡담·퀴즈·긴 이야기 금지 |
| **신호 대기·정체** | 속도 0, 기어 D | 주행 중과 같다(언제든 출발) |
| **정차** | 기어 P | 2~3문장(60자 안팎), 이어지는 대화 허용 |
| **충전 중·휴게소·집** | 기어 P + 충전, 하차 | 제한을 푼다(L3) |
| **다른 소리가 있을 때** | 통화 중, 글레오·내비 음성 출력 중, (가능하면) 동승자 대화 감지 | 말하지 않고 기다린다. 음악은 소리를 낮추고 말한다(오디오 포커스) |

---

## 4. 심사 관점

요강의 1차 심사 기준(세부 배점 비공개)과 대화 설계를 이어 보면 아래와 같다. 2차 PT 기준은 아직 공지되지 않았다.

| 심사 항목 | 심사위원이 대화에서 볼 것 (추측 포함) | 스노봇이 보여 줄 것 |
|---|---|---|
| **차량특화성** | 폰 챗봇을 차에 옮긴 것인가, 차 안이라서 가능한 것인가 | 같은 질문에도 **주행·정차·충전 상태에 따라 길이와 때가 달라지는 대화.** 회전 구간에서 말을 멈추는 장면 |
| **창의성** | 글레오가 이미 하는 것과 겹치지 않는가, 불편이 분명한가 | "답하는 비서(글레오)" vs "상황을 보는 동승자(스노봇)". 졸음·주차·공기 같은 구체적 순간 |
| **기술 활용성** | SDK 가 장식이 아니라 핵심에 쓰였는가 | 대화 한 줄마다 **어느 SDK 데이터에서 나왔는지** 보이기(Odometer·NaviHelper·FusedLocation·Gleo Interface·STT/TTS 차 안 엔진) |
| **사업성** | 계속 쓸 이유, Retention 근거 | 2~4주 뒤 신기함이 떨어진다는 연구를 먼저 인정하고, **출퇴근·주차라는 매일 계기 + 기억** 으로 버틴다고 설명 |
| 시연(PT, 동작 필수) | 실제로 도는가, 실패해도 무너지지 않는가 | 정해진 흐름은 확실히 돌고, 자유 대화는 짧게. 네트워크가 끊겨도 L0·L1 은 동작 |

외부 해커톤 사례(2015~2016 커넥티드카 해커톤)의 심사 기준은 "앱이 하는 일을 분명히 설명", "독창성", "주최사 SW·하드웨어 활용", "발표 품질"이 반복된다 [근거 약, 오래된 자료]. GM 캐나다 차량 AI 해커톤 1등은 "프로필을 만들고 차량 설정(지도·시트)을 바꿔 주는 비서"였다 [근거 약]. 공통점: **대화 자체보다 차에서 실제로 무엇이 바뀌는지**를 본다.

**(추측) 심사위원이 던질 질문 예상**
1. "글레오가 있는데 왜 스노봇이 필요한가?" → 3-3 규칙과 L0·L1 로 답한다.
2. "운전 중에 로봇이 말 걸면 오히려 위험하지 않나?" → 운전 중 규칙(말 멈춤·횟수 상한)을 데이터와 함께.
3. "몇 주 쓰다 질리지 않나?" → 기억·루틴, Retention 근거.
4. "인터넷이 끊기면?" → 차 안 엔진 + 규칙 판단(07 문서 8-2).
5. "대화 내용·위치 정보는 어디로 가나?" → 기억 목록 화면에서 보고 지우기.

---

## 5. 대화 단계 제안

| 단계 | 이름 | 무엇을 하나 | 만드는 방법 | 인터넷 | 언제 쓰나 |
|---|---|---|---|---|---|
| **L0** | 비언어 반응 | 말 없이 표정·고개·짧은 효과음. 듣는 중(눈 크게), 생각 중(눈 위로), 졸림 공감(눈꺼풀 반), 끄덕/도리도리, 잠(눈 감기) | 사건 → 표정 표(고정) | 필요 없음 | **항상.** 주행 중에도. 모든 단계의 바탕 |
| **L1** | 상황 한마디 | 차량·센서 사건에 맞춘 **정해진 한 문장.** 시동 인사, 졸음·CO₂ 제안, 주차 저장 알림, 충전 완료, 하차 인사 | 사건 + 문장 틀 + 실제 값 채우기("다음 쉼터 {거리}예요"). 문장마다 2~3개 변형을 두고 돌려 쓴다. 자주 쓰는 문장은 음성을 미리 만들어 둔다 | 필요 없음(미리 만든 음성 또는 차 안 TTS) | 주행 중 먼저 말 거는 것은 **이 단계만** |
| **L2** | 짧은 자유 대화 | 누르고 말하기(push-to-talk) → LLM 이 캐릭터로 1~2문장 + 표정 태그. **차량 상태·기억을 대화에 넣는다** ("어제 늦게 들어왔죠? 오늘은 일찍 쉬어요") | LLM + 상황 묶음(주행 상태, 시간, 트립, 최근 기억 3~5개). 숫자·위치는 **도구 결과만** 쓰게 한다 | 필요 | 주행 중(짧게) · 정차 중 |
| **L3** | 긴 대화·관계 | 여러 턴 대화, 지난 대화 이어 가기, 하루·주간 돌아보기, 아이 동승 놀이, 집에서 이어 말하기 | L2 + 긴 기억 + 대화 요약 저장 | 필요 | **정차·충전·집에서만** |

**추천**

| 시기 | 목표 단계 | 이유 |
|---|---|---|
| 접수(11/15)까지 | L0 + L1 2~3개(졸음·주차·시동 인사), L2 는 영상에서 한 장면 | 기획서·시연 영상에 "차량 데이터 → 표정·한마디"가 보이면 차량특화성·기술 활용성이 바로 설명된다 |
| **PT(12/3)까지** | **L0·L1 완성, L2 를 기억과 묶어 2~3턴, L3 는 정차(또는 집) 장면 1개** | 심사 점수는 L0·L1·운전 중 규칙에서 나온다. L2 는 "평범한 챗봇이 아니라 나를 아는 동반자"를 보여 주는 데 필요한 만큼만. L3 를 길게 하면 글레오·ChatGPT 와 비교당하고, 라이브 시연 위험(지연·엉뚱한 답)이 커진다 |
| 공모전 뒤 | L3 확장(집 스노봇, 주간 돌아보기, 아이 모드) | 장기 사용(Retention)은 여기서 결정된다. 실제 사용자 시험으로 정한다 |

**글레오와 나누는 일 (제안)**

| 사용자가 원하는 것 | 누가 | 스노봇의 행동 |
|---|---|---|
| 창문·공조·시트 같은 차량 조작 | 글레오 | 스노봇 앱은 차량 제어 SDK 가 "준비 중"이라 직접 못 한다. "그건 '글레오, 창문 닫아 줘' 하면 돼요"처럼 짧게 안내(추측 — 실제 문구는 시험) |
| 일반 지식·검색·맛집 | 글레오 (스노봇도 짧게 답할 수는 있음) | 제품 이야기의 중심에 두지 않는다 |
| 주차 위치, 졸음·공기, 오늘 운전 요약, "나" 에 대한 기억 | **스노봇** | 글레오가 Gleo Interface 로 스노봇 기능을 부를 수도 있다(`get_parking_spot` 등) |
| 기분·존재감·작은 반응 | **스노봇** | L0 |

---

## 6. 페르소나 규칙

### 6-1. 성격 (제안)

- **역할:** 조수석에 앉은, 말수 적고 눈치 빠른 작은 친구. 비서가 아니다. 운전자를 가르치거나 혼내지 않는다.
- **말투:** 존댓말(해요체), 짧고 부드럽게. 귀여운 목소리(Suzu)는 유지하되, **운전자가 피곤하거나 짜증 나 보이면 느리고 낮게**(TTS 속도·안정성 설정 — ElevenLabs 에서 시험 필요).
- **모를 때:** 지어내지 않는다. "그건 잘 모르겠어요" + 고개 갸웃. 차량 값(거리·배터리·층)은 **앱이 준 값만** 말한다.
- **반복 피하기:** 같은 사건의 문장은 변형 2~3개를 돌려 쓰고, 같은 날 같은 문장을 두 번 쓰지 않는다.
- **감정의 선:** 위로·공감은 하되 의존을 부추기지 않는다. 연애·독점("나만 있으면 돼")·자해 관련 대화는 정해진 안전 응답으로. 아이 동승(키즈 모드) 때는 자유 대화를 끄고 정해진 놀이만(Character.AI 사례).

### 6-2. 길이 (한국어 TTS 약 7~8자/초 가정 — [10 문서](10-ai-services.md)의 "60자 ≈ 8초"에서 계산, Suzu 로 실측 필요)

| 상태 | 한 번에 | 대략 시간 | 정보 개수 | 대화 턴 |
|---|---|---|---|---|
| 주행 중 먼저 말 걸기(L1) | 1문장, **25자 안팎** | 3~4초 | 2~3개 | 1 (예/아니오로 끝나게) |
| 주행 중 대답(L2) | 1~2문장, **40자 이하** | 5초 이하 | 3개 이하 | 2~3턴, 그 뒤 "도착해서 더 얘기해요" |
| 정차 중 | 2~3문장, 60자 안팎 | 약 8초 | — | 제한 느슨 |
| 충전·휴게소·집 | 제한 없음(한 번에 4문장 이하 권장) | — | — | 자유 |

### 6-3. 말하지 않을 때 (침묵 규칙)

1. 길 안내 회전 지점 근처, 방향지시등 켜짐, 급제동·차량 경고 직후 → 말하지 않음(L0 만, 움직임도 작게).
2. 통화 중, 글레오·내비가 말하는 중 → 기다림.
3. 운전자가 "조용히" 하면 → 그 트립 동안 L0 만(안전 관련 L1 은 예외로 둘지 시험).
4. **먼저 말 거는 횟수 상한(추측 — 시험으로 조정):** 주행 중 안전·안내가 아닌 말은 30분에 1번 이하. 같은 제안을 거절하면 같은 트립에서 다시 하지 않는다(상황이 더 나빠질 때만 한 번 더).
5. 시동 직후 1~2분(출발 준비 중)과 주차 중(후진) → 인사 외에는 말하지 않음.
6. 네트워크가 끊겨 LLM 이 없으면 → L2·L3 를 흉내 내지 않고 L0·L1 만.

### 6-4. 기억할 것 / 하지 않을 것

| 기억할 것 (제안) | 쓰는 곳 | 보관 |
|---|---|---|
| 부르는 이름·호칭 | 인사 | 계속 |
| 집·회사 같은 자주 가는 곳(사용자가 정한 것만) | 출퇴근 인사, 도착 알림 | 계속 |
| 최근 주차 위치(층·구역·시간) | "차 어디야?" | 다음 주차까지 |
| 졸음 이력(언제, 몇 시간째에 제안했는지, 받아들였는지) | 제안 시점 조정, "요즘 밤 운전이 잦네요" | 30일 |
| 휴식 취향(커피·스트레칭·환기) | 제안 문장 | 계속 |
| 사용자가 말한 일정·약속 한 줄("내일 부산 출장") | 다음 날 이어 말하기 | 7일 |
| 동승 가족·반려동물 이름 | 인사, 키즈·펫 모드 | 계속 |
| 차량 사건(타이어 경고, 충전 완료 시간) | 집에서 알림 | 7~30일 |
| 거절한 제안·"조용히" 설정 | 말 거는 빈도 조절 | 30일 |

**하지 않을 것:** 목소리 원본·카메라 사진 저장, 이동 경로 전체 기록(필요한 지점만), 건강·정치·종교 같은 민감한 내용, 동승자 대화 내용.
**보이기:** 폰·차량 앱에 **"스노봇이 기억하는 것"** 목록을 두고 하나씩 지울 수 있게 한다. 개인정보 질문에 대한 답이 되고, 시연 화면으로도 쓸 수 있다.

### 6-5. 감정 → 표정 매핑 (L0, 03 모듈 규격 `express` 로 보냄)

| 상황 | 눈꺼풀 | 눈썹 | 고개 | 소리·말투 | 예시 한마디(L1) |
|---|---|---|---|---|---|
| 듣는 중 | 크게 뜸 | 살짝 올림 | 말하는 사람 쪽으로 | — | — |
| 생각 중(LLM 대기) | 반쯤, 눈동자 위 | 한쪽 올림 | 살짝 갸웃 | 짧은 "음" 효과음(선택) | — |
| 인사·기쁨 | 크게 | 올림 | 끄덕 2번 | 밝게 | "다녀오셨어요? 오늘 금요일이에요." |
| 졸음 공감·휴식 제안 | 반쯤 내림 → 다시 뜸 | 처짐 | 천천히 갸웃 | 느리고 낮게 | "조금 답답하죠? 다음 쉼터 12km예요." |
| 걱정(공기·배터리) | 보통 | 안쪽 올림 | 작게 도리도리 | 차분하게 | "창문 잠깐 열까요?" |
| 동의·확인 | 보통 | 보통 | 끄덕 1번 | — | "쉼터 넣었어요." |
| 모름·실패 | 깜빡 | 한쪽 올림 | 갸웃 | — | "그건 잘 모르겠어요." |
| 조용히 모드·회전 구간 | 보통 | 보통 | 정면 고정, 움직임 최소 | 없음 | — |
| 잠(시동 OFF·하차 후) | 감음 | 내림 | 숙임 | 없음 | "잘 다녀오세요." (하차 때 1번) |

주행 중에는 고개를 크게 돌리거나 빠르게 움직이지 않는다(시선을 끌 수 있다 — 추측, 실차 시험 필요).

---

## 7. 시연 시나리오 (PT 3~5분용 후보 8개, 5개 고르기)

에뮬레이터 차량 앱(가상 차량 값 조작) → 서버 → 실물 스노봇 구성. 각 장면은 **정해진 흐름(L0·L1)이 먼저 돌고**, 자유 대화(L2)는 짧게 붙인다.

| # | 장면 | 단계 | 쓰는 데이터·SDK | 심사위원이 보는 것 | 시간 | 실패 대비 |
|---|---|---|---|---|---|---|
| 1 | **아침 시동 — 어제를 기억** "어제 부산 출장 잘 다녀왔어요? 오늘은 회사까지 25분이에요." | L1+L2 | 시동·시간, 기억, NaviHelper 경로 | 평범한 챗봇이 아니라 나를 아는 동반자 | 30초 | 기억은 미리 넣어 둔다 |
| 2 | **회전 구간에서 말 멈춤** — 운전자가 말을 거는 중 회전 안내 → 스노봇은 듣는 표정만 → 지나간 뒤 대답 | L0→L2 | NaviHelper 회전 지점, 속도, 방향지시등 | **"도로를 보는 동승자"** — 글레오와 다른 점을 가장 짧게 보여 줌 | 40초 | 회전 사건은 에뮬레이터에서 수동으로 발생 |
| 3 | **졸음 + 답답한 공기 → 쉼터 경유지** "조금 답답하죠? 다음 쉼터 12km예요." "응" → 경유지 추가, 끄덕 | L0+L1 | Odometer 트립 시간, 모듈 CO₂, NaviHelper 경유지 | 차량 데이터 → 표정·한마디 → 차 기능 실행까지 한 줄로 연결 | 50초 | CO₂ 값은 시연용 버튼으로 올림 |
| 4 | **글레오와 함께** "글레오, 스노봇한테 주차 어디 했는지 물어봐" → 글레오가 답하고 스노봇이 끄덕 | L0 | Gleo Interface(App Schema) | 경쟁이 아니라 **글레오가 부르는 기능** | 30초 | 에뮬레이터에서 안 되면 녹화 영상 |
| 5 | **주차 저장 → 하차 인사 → 집 책상에서 이어받기** "어제 차는 B3 C-12 에 있어요. 배터리 82%예요." | L1 | 기어 P·시동 OFF, FusedLocation, EvBattery | 차에서 끝나지 않는 경험 | 40초 | 같은 로봇 1대로 장소만 바꿔 연출 |
| 6 | **지하주차장(인터넷 끊김)** — 네트워크를 끄고 "차 어디야?" → 차 안 엔진으로 답 | L0+L1 | STT·TTS ON_DEVICE, 앱 규칙 판단 | Moxie 처럼 클라우드가 꺼져도 멈추지 않는다 | 30초 | 실제로 와이파이를 끄는 장면 |
| 7 | **충전 중 긴 대화** — 정차·충전이면 대화가 길어지고 "이번 주 운전 돌아보기" | L3 | 기어 P·충전 상태, 주행 기록 | 상태에 따라 대화 깊이가 바뀐다 | 40초 | 질문을 미리 정해 둔다 |
| 8 | **기억 목록 보고 지우기** — 앱 화면에서 "스노봇이 기억하는 것" | — | 앱 화면 | 개인정보 질문에 대한 답 | 20초 | 화면만 |

**추천 조합(약 4분):** 1 → 2 → 3 → 6 → 5 (+ 시간이 남으면 4 또는 8).
2·3·6 은 반드시 넣는다: 2 는 차별점, 3 은 SDK 연결, 6 은 실패에 강함을 보여 준다.

**라이브 시연 운영 (추측 — 리허설로 확인)**
- 장면마다 **정해진 첫 질문**을 쓴다. 자유 질문은 심사위원이 원할 때만.
- 응답 대기 중에는 반드시 "생각 중" 표정(L0)을 먼저 보낸다(첫 소리까지 3초 목표, 07 문서).
- 행사장 와이파이 대비: 폰 핫스팟 예비, 전체 흐름 녹화 영상 예비.

---

## 8. 피할 것

| 피할 것 | 이유 | 근거 |
|---|---|---|
| **"무엇이든 대답하는 두 번째 비서"** | 글레오와 정면으로 겹치고, 현대차도 브랜드별 페르소나 AI 를 만들고 있다. "AI 캐릭터"만으로는 차별점이 아니다 | [02 리서치 C], 머니투데이 2026-10 |
| 주행 중 긴 답·여러 턴 잡담 | 음성 대화도 인지 부담이 크다. 정보는 2~3개까지만 기억된다 | AAA, FHWA, 음성 메시지 연구 |
| 먼저 너무 자주 말 걸기 | 귀찮아서 기능을 끈다(Friend 알림 과다). 잘못된 때 말하면 위험 | Friend 후기, KAIST·CHI 2019 |
| 주행 중 크고 빠른 로봇 움직임 | 시선을 도로에서 뺏을 수 있다 | NHTSA 2초 기준에서 추론(추측) |
| 차량 값 지어내기 | "쉼터 12km"가 틀리면 신뢰가 바로 깨진다. LLM 은 환각이 있다 | 글레오 개발자 언급, VW T3 체험 |
| 늘 같은 문장 | 2~4주 뒤 질림이 빨라진다 | Cozmo 아이 연구, Hoffman |
| 기분과 맞지 않는 들뜬 목소리 | 피곤·짜증 상태에서 밝은 목소리는 운전에 불리할 수 있다 | Nass 2005 |
| 클라우드에만 의존 | 서버가 꺼지면 로봇이 멈춘다 | Moxie, Jibo |
| 쓸모가 모호한 "귀여움"만 | 귀여움은 첫 주를 만들지만 계속 쓰게 하지 못한다 | Ballie, Jibo |
| 의존·연애형 대화, 아이 자유 대화 | 안전·법적 문제 | Character.AI |
| 졸음 "감지" 정확도를 과장 | 안전 기능 주장은 근거를 요구받는다. "제안"으로 표현한다 | (추측) |
| PT 에서 긴 자유 대화 즉흥 시연 | 지연·엉뚱한 답이 그대로 보인다 | (추측) |

---

## 9. 남은 확인

- [ ] Suzu 로 한국어 25자·40자 문장 실제 길이(초) 측정 → 6-2 표 수정
- [ ] ElevenLabs 속도·안정성 설정으로 "차분한 Suzu" 가 되는지
- [ ] NaviHelper 에서 회전 지점까지 거리·안내 음성 시점을 받을 수 있는지 (장면 2의 전제)
- [ ] 방향지시등(TurnSignal)·Safety 경고 값을 에뮬레이터에서 바꿀 수 있는지
- [ ] 실차(또는 대시보드 모형)에서 스노봇 움직임이 시선을 끄는 정도 — 동승자 관찰로 간단히
- [ ] L1 문장 목록(사건별 2~3개 변형) 작성
- [ ] 10/28 설명회 다시보기에서 PT 심사 기준·Gleo Interface 시험 가능 여부 확인

---

## 10. 출처 (2026-10-28 검색)

**차 안 AI**
- NIO NOMI 역사·설계: https://www-global-eu.nio.com/blog/history-behind-nomi · https://www.nio.com/blog/house-of-nio-ep1
- NOMI GPT 1,000만 상호작용: https://cnevpost.com/2024/06/27/nio-nomi-gpt-hits-10-million-user-interactions/
- NOMI GPT 출시: https://autonews.gasgoo.com/china_news/70032446.html
- NOMI Mate 레드닷: https://www.red-dot.org/project/nomi-mate-15-46042
- Mercedes MBUX Virtual Assistant(CES 2024): https://media.mbusa.com/releases/release-ebe78e1e0abb0f8a2f173a4032054126-mercedes-benz-heralds-a-new-era-for-the-user-interface-with-human-like-virtual-assistant-powered-by-generative-ai
- Mercedes CLA 시승기: https://insideevs.com/news/782831/mercedes-cla-ai-assistant-review/ · https://www.autoexpress.co.uk/mercedes/cla/interior
- VW ChatGPT: https://www.volkswagen-group.com/en/articles/chatgpt-is-now-available-in-many-volkswagen-models-18464 · https://www.techradar.com/computing/artificial-intelligence/chatgpt-gives-volkswagen-cars-the-smarts-to-talk-to-you-all-day-long · https://t3.com/news/volkswagens-chatgpt-struggled-to-wow-me-at-ces-but-i-think-its-future-could-still-be-bright
- BMW IPA + Alexa+: https://www.press.bmwgroup.com/usa/article/detail/T0454794EN_US/the-bmw-ix3-ushers-in-next-generation-innovations-at-ces-2026 · https://www.bmwgroup.com/de/news/allgemein/2026/alexa.html
- Li Auto MindGPT: https://www.globenewswire.com/news-release/2025/04/07/3056503/28124/en/li-auto-layout-in-electrification-connectivity-intelligence-and-sharing-analysis-report-2024-2025-mind-gpt-cockpit-evolution-multimodal-end-to-end-architecture-complex-task-process.html · https://autonews.gasgoo.com/70018735.html
- Toyota Yui: https://global.toyota/en/newsroom/corporate/30063126.html · https://www.toyota.ca/en/connect/2018/your-car-can-now-tell-if-youre-happy/
- Toyota Kirobo Mini: https://www.pcworld.com/article/410559/toyotas-cute-kirobo-mini-robot-will-chat-with-you.html · https://www.autoguide.com/auto-news/2016/10/toyota-is-selling-a-robot-in-japan-that-wants-to-be-your-friend.html
- MIT AIDA: https://robots.media.mit.edu/portfolio/aida · https://www.media.mit.edu/publications/reducing-driver-task-load-and-promoting-sociability-through-an-affective-intelligent-driving-agent-aida · https://robots.media.mit.edu/wp-content/uploads/sites/7/2015/01/WilliamsPetersBreazeal-IVS-13.pdf
- 글레오 AI: https://www.newsis.com/view/NISX20260429_0003610848 · https://v.daum.net/v/20260531090156023 · https://economist.co.kr/article/view/ecn202605280065 · https://www.seoul.co.kr/news/economy/car/2026/06/01/20260601031007
- 현대차 페르소나 AI: https://www.mt.co.kr/article/2026102019575795544
- J.D. Power: https://www.businesswire.com/news/home/20250626729527/en/Fewer-Physical-Vehicle-Controls-and-More-Problematic-Touchscreen-Make-Infotainment-System-a-Modern-Catch-All-Drawer-J.D.-Power-Finds · https://www.jdpower.com/business/press-releases/2025-china-initial-quality-study-iqs

**책상·동반 로봇**
- Cozmo/Vector: https://www.designnews.com/electronics-test/cozmo-vector-how-anki-designs-robots-emotional-intelligence · https://www.techhive.com/article/3329938/anki-vector-robot-review.html · https://www.therobotreport.com/cozmo-ai-robot-brings-wall-e-to-life/
- EMO: https://keyirobot.com/blogs/buying-guide/emo-cute-robot-pet-review-is-livingais-emotional-robot-worth-it (판매처 글)
- Loona: https://ca.robotshop.com/products/loona-smart-petbot (판매처)
- Ropet: https://technode.com/2025/01/09/ces-2025-ai-powered-pet-steals-the-spotlight/ · https://kr-asia.com/meet-ropet-the-ai-powered-pet-stealing-the-show-at-ces-2025
- Jibo: https://www.therobotreport.com/jibo-social-robot-analyzing-what-went-wrong/ · https://www.engadget.com/2019-03-04-social-robot-jibo-shutting-down-message.html
- 소셜 로봇 실패에서 배울 점(Hoffman): https://spectrum.ieee.org/anki-jibo-and-kuri-what-we-can-learn-from-social-robotics-failures
- Moxie 종료: https://www.axios.com/2024/12/10/moxie-kids-robot-shuts-down · https://oecd.ai/en/incidents/2024-12-09-ab89
- Ballie: https://www.androidauthority.com/samsung-ballie-robot-shelved-indefinitely-3630980 · https://www.heise.de/en/news/Samsung-indefinitely-shelves-robot-Ballie-11135811.html · https://androidcentral.com/accessories/smart-home/samsung-ballie-another-setback-concerns-home-use-report
- Friend: https://dc.fortune.com/2025/10/03/friend-ai-necklace-review-avi-schiffmann · https://spyglass.org/ai-friend-wearable/
- Character.AI: https://www.nbcnews.com/tech/tech-news/characterai-bans-minors-response-megan-garcia-parent-suing-company-rcna240985 · https://dc.fortune.com/2025/10/29/character-ai-ban-children-teens-chatbots-regulatory-pressure-age-verification-online-harms
- 신기함 감소(Cozmo 아이 321명): https://ascor.uva.nl/shared/faculteiten/en/faculteit-der-maatschappij-en-gedragswetenschappen/news/2022/07/social-robots-and-children.html · https://research-portal.uu.nl/en/publications/long-term-evaluation-of-a-social-robot-in-real-homes/

**운전 중 대화 제약**
- NHTSA 지침: https://www.transportation.gov/briefing-room/us-dot-releases-guidelines-minimize-vehicle-distractions · https://nap.nationalacademies.org/read/26550/chapter/4 · https://swov.nl/en/publicatie/visual-manual-nhtsa-driver-distraction-guidelines-portable-and-aftermarket-devices
- AAA 인지 부담: https://aaafoundation.org/mental-workload-common-voice-based-vehicle-interactions-across-six-different-vehicle-systems/ · https://www.psychologicalscience.org/?p=118232
- 동승자 vs 전화: https://sciencedaily.com/releases/2008/12/081201081917.htm · https://phys.org/news/2014-10-safest.html · https://scholarsmine.mst.edu/psysci_facwork/76
- 말 거는 때: https://tri.global/research/now-good-time-empirical-study-vehicle-driver-communication-timing · https://purl.stanford.edu/gj863qr6417 · https://ic.kaist.ac.kr/files/papers/kim2019predicting.pdf · https://dspace.kaist.ac.kr/handle/10203/283631
- 먼저 말 걸기 특허: https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/12606188
- 정보 단위: https://www.fhwa.dot.gov/publications/research/safety/98057/ch03b.cfm · https://core.ac.uk/works/7336694 · https://swov.nl/en/publicatie/complexity-car-visual-messages-and-drivers-performances
- 목소리 기분 맞추기(Nass): https://swov.nl/en/publicatie/matching-car-voice-driver-state-impact-attitude-and-driving-performance · https://arxiv.org/pdf/2003.13731
- Android for Cars 문구 지침: https://developer.android.com/design/ui/cars/guides/foundations/writing-guidelines · https://developers.google.com/cars/design/create-apps/apps-for-drivers/design-tasks

**심사**
- 커넥티드카 해커톤 심사 기준: https://www.hackathon.com/event/connected-car-expo-hackathon-powered-by-att-18940724216 · https://www.hackerearth.com/challenges/hackathon/mojio-connected-car-hack-16/description/
- GM 캐나다 차량 AI 해커톤: https://betakit.com/dmz-and-gm-canada-host-hackathon-focused-on-ai-solutions-for-cars/

**조사 한계:** 대부분 검색 요약과 초록으로 확인했고 논문 전문은 읽지 않았다. 표본 수·수치는 인용 전에 원문 확인이 필요하다. 2024~2026년 차 안 AI 의 **실제 사용자 만족도** 자료는 거의 없었다(대부분 회사 발표·시승기). LG Q9·Eilik 은 자료를 찾지 못했다. NIO NOMI Mate 인터뷰 기사는 접근이 막혀 읽지 못했다.
