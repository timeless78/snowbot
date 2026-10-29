# AI 서비스 비교와 추천 (2026-10-27)

중계 서버에 붙일 AI 4자리 — **listen-ai**(STT 음성 인식), **think-ai**(LLM 대화 AI), **tts-ai**(TTS 음성 합성), **vision-ai**(사진 이해).
값은 2026-10-27 에 공식 요금 페이지(일부는 제3자 정리)에서 확인했다. 요금은 자주 바뀌므로 가입 전에 다시 본다. **한국어 품질과 실제 응답 시간은 아직 재지 않았다 — 시험 필요.**

## 1. 한 번 대화 기준 비용 (추정)

가정: 사람 말 5초 → 스노봇 답 약 60자(2문장, 약 8초). LLM 입력 1,500토큰(캐릭터 설정 + 상황 + 최근 대화), 출력 150토큰(답 + 감정 태그).

### listen-ai (음성 → 글자, 실시간 스트리밍)

| 후보 | 요금 | 5초 비용 | 한국어 | 메모 |
|---|---|---|---|---|
| **ElevenLabs Scribe v2 Realtime** | $0.39/시간 | $0.0005 | 90+ 언어(한국어 포함으로 알려짐, 시험) | 응답 약 150ms(업체 표기) |
| Deepgram Nova-3 Multilingual | $0.0058/분 | $0.0005 | 한국어 지원 여부 문서 확인 필요 | 가장 쌈 |
| OpenAI gpt-live-transcribe | $0.017/분 | $0.0014 | 지원 | 파일 방식 mini-transcribe 는 $0.003/분이지만 실시간 아님 |
| Google Speech-to-Text | $0.016/분 | $0.0013 | 지원 | |
| **NAVER CLOVA Speech** | 요금표 확인 못 함 | — | **국내 최고 수준(업체 주장)** | 실시간 gRPC. 서버와 같은 네이버 클라우드, 국내 처리 |

### think-ai (대화 + 감정 태그, 사진 이해 겸용)

| 후보 | 입력 / 출력 (100만 토큰당) | 1회 비용 | 메모 |
|---|---|---|---|
| **Claude Haiku 5.5** | $0.10 / $0.50 | $0.0002 | 사진 입력 가능. 빠른 응답은 effort `low` |
| OpenAI gpt-6-luna | $0.10 / $0.50 | $0.0002 | 같은 가격대 |
| Gemini 3.6 Flash | $0.75 / $3.75 (2026-12-31까지, 이후 2배) | $0.0017 | |
| Claude Sonnet 5.5 | $2 / $10 | $0.0045 | 더 똑똑한 대화가 필요할 때 |

> 이 문서를 쓴 Claude 는 Anthropic 모델이다. Claude 를 1순위로 두었지만 Gemini·OpenAI 와 같은 문장으로 비교 시험한다.

### tts-ai (글자 → 스노봇 목소리)

| 후보 | 요금 | 60자 비용 | 첫 소리까지 | 메모 |
|---|---|---|---|---|
| **ElevenLabs Flash** | $0.04 / 1천 자 | $0.0024 | 약 75ms(업체 표기) | 32개 언어, 목소리 많음, 목소리 복제 |
| OpenAI gpt-4o-mini-tts | 오디오 출력 $12 / 100만 토큰 | 약 $0.002 | — | 말투 지시 가능 |
| Google Chirp 3: HD | 약 $30 / 100만 자(기억값, 확인 못 함) | 약 $0.002 | — | |
| **Typecast** | Lite $15/월(20만 자), 초과 $0.09/1천 자 | $0.005 | — | 한국 회사, 캐릭터 목소리 500개 이상 |
| **NAVER CLOVA Voice** | 요금표 확인 못 함 | — | — | 한국어 자연스러움, 아이 목소리 등 |

### 한꺼번에 하는 실시간 음성 AI (listen + think + tts)

| 후보 | 오디오 입력 / 출력 (100만 토큰당) | 1회 비용(추정) |
|---|---|---|
| Gemini Live | $3 / $12 | 약 $0.002 |
| OpenAI gpt-realtime-2.1-mini | $10 / $20 | 약 $0.003~ |
| OpenAI gpt-realtime-2.1 | $32 / $64 | 약 $0.01~ |

### 조합별 합계

| 조합 | 1회 | 하루 30번 × 30일 (900회) |
|---|---|---|
| **Scribe Realtime + Haiku 5.5 + ElevenLabs Flash** (추천) | 약 $0.003 | 약 $2.8/월 (약 3,900원) |
| 같은 조합, TTS 만 Typecast | 약 $0.006 | 약 $5/월 |
| Gemini Live 하나로 | 약 $0.002 | 약 $2/월 |

**비용의 대부분은 TTS(목소리)** 다. LLM 은 거의 무시할 수준. 하루 10번 쓰는 사용자라면 월 1천~2천 원 수준.

## 2. 추천: 따로 쓰기 (자리별로 고르기)

| 자리 | 1순위 | 비교 시험할 대안 |
|---|---|---|
| listen-ai | ElevenLabs Scribe v2 Realtime | CLOVA Speech (한국어 정확도) |
| think-ai | Claude Haiku 5.5 (필요하면 Sonnet 5.5) | Gemini 3.6 Flash |
| tts-ai | **확정: ElevenLabs v4 Turbo + 목소리 Suzu** (2026-10-27 사용자 결정, 첫 소리 약 0.24초, 1글자 0.5크레딧) | — |
| vision-ai | think-ai 가 겸함 (Haiku 5.5 사진 입력) | — |

**한꺼번에(실시간 음성 AI) 대신 따로 쓰는 이유**

1. **차 안에서는 자리를 바꿔 끼운다**(07 문서 8-2절). 차에서는 Pleos 음성 엔진(차 마이크·스피커)으로 듣고 말하고 생각만 클라우드로 — 한꺼번에 하는 AI 는 이렇게 나눌 수 없다.
2. **스노봇 목소리를 고를 수 있다.** 캐릭터 제품이라 목소리가 정체성이다. 실시간 음성 AI 는 고를 수 있는 목소리가 적다.
3. **감정 태그(표정 명령)를 안정적으로 받는다.** 글자 단계가 있어 `{expr, head}` 를 확실히 뽑을 수 있다.
4. 비용은 비슷하다. 실시간 음성 AI 는 "빠른 수다 모드"로 나중에 시험해 볼 수 있다.

ElevenLabs 를 듣기·말하기 둘 다 1순위로 두면 계정 하나로 시작할 수 있다.

## 3. 주의할 점

- **개인정보 국외 이전:** ElevenLabs·Anthropic·Google·OpenAI 는 해외 서버에서 처리한다. 사용자 음성·사진을 보내므로 개인정보 처리방침에 국외 이전을 알리고 동의를 받아야 한다. CLOVA(국내 처리)는 이 부담이 적다 — 기획서 보안 항목과 함께 정리.
- **API 키는 서버에만**(`/var/lib/snowbot` 아래 권한 600 파일 또는 systemd 환경 파일). 채팅·문서·GitHub 에 넣지 않는다.
- **결제는 사용자가 한다.** 무료 사용량으로 시험부터 — ElevenLabs 무료 1만~2만 자, Typecast 무료 3만 자/월.

## 4. 가입·키 현황

| 서비스 | 상태 (2026-10-27) |
|---|---|
| ElevenLabs | 키를 서버 `/var/lib/snowbot/secrets.env`(`ELEVENLABS_API_KEY`, snowbot 소유, 권한 600)에 저장. **음성 합성 ✔, 음성 인식 ✔, 읽기 권한(User·Voices·Models Read) ✔.** 쓰기·관리 권한은 꺼 둠. 계정은 Creator 요금제 — 2026-10-27 기준 이번 달 207,027 크레딧 중 160,955 사용(남은 약 4.6만, **다른 작업과 같이 씀**), 2026-11-11 11:05 다시 채워짐. Flash 모델은 1글자에 0.5크레딧 |

첫 실측(프로토타입 서버 → ElevenLabs, 1회씩):
- 음성 합성 Flash v2.5, "안녕하세요": **첫 소리 데이터 0.11초**
- 음성 인식 Scribe v2(파일 방식), 합성한 문장 "안녕하세요, 저는 스노봇이에요. 조금 쉬어갈까요?": **글자 그대로 인식, 전체 0.58초** (기계 목소리라 쉬운 시험 — 사람 목소리로 다시)

### 스노봇 목소리 후보 (ElevenLabs, 한국어 원어 목소리, 2026-10-27)

계정 목소리 95개 + 공유 라이브러리 한국어 약 100개 중에서 "젊음·밝음·친근함·캐릭터"로 골랐다. 사용 수 = 다른 사용자가 가져다 쓴 횟수.

| 후보 | 느낌 | 출처 | voice_id |
|---|---|---|---|
| Aria - Bright & Youthful Korean | 밝고 귀여운 젊은 여성, 대화용 | 공유(사용 189) | `O2KReOFLpoR65nz6C9Se` |
| Sena - Friendly Cheerful | 밝고 귀여움, 애니·게임용 | 공유(509) | `vJ170fCwESgE1tjW39So` |
| Annie - Friendly, Soft and Clear | 부드럽고 사랑스러움 | 공유(17,255) | `Lb7qkOn5hF8p7qfCDH8q` |
| OnaDot | 귀여운 여성 | 공유(280) | `pB7oWYX9EdaK4jiH8N3J` |
| Sora K - Pure, Friendly Voice | 맑고 친근한 젊은 여성 | 내 계정 | `JpGoUJuLFthrFkcdBy4k` |
| Deoksu - Joyful, Clear and Rich | 통통하고 귀여운 남성 캐릭터 | 공유(8,054) | `IAETYMYM3nJvjnlkVTKI` |
| Klamon - Bright, Warm, Clear & Friendly | 밝고 따뜻한 젊은 남성 | 공유(871) | `dnZGOA54w68T7vLLPFTp` |
| Taemin | 따뜻하고 차분한 20대 남성 | 내 계정 | `Ir7oQcBXWiq4oFGROCfj` |

- **검수 페이지:** https://claude.ai/artifact/CYRHcgRLbBMgce1XFDm7n9 (파일 `docs/voice-review/`) — 후보 8명 × 대사 3개(Flash v2.5), 좋아요·보류·별로·메모는 페이지 저장소 `picks/<voice_id>` 에 남는다
- **결정 (2026-10-27, 사용자): 스노봇 목소리 = Suzu** (`6awt6FKyZGV0HyQEwisX`, "Cute, Clear & Calm", 원래 일본어 애니메이션풍 10대 여성 목소리). 공유 라이브러리 목소리, 추가 요금 없음(rate 1.0), 공개 중단 시 **내 목소리에 추가해 둔 사용자에게 730일 유예**. 한국어 억양·숫자 읽기는 대사를 늘려 다시 확인.
- **Suzu 모델 비교 (2026-10-27):** 6문장 × 5모델을 검수 페이지 맨 위 표에 올림. 서버 → ElevenLabs 첫 소리(스트리밍, 6문장 중간값): Flash v2.5 234ms · v4 Turbo 235ms · v3 Conversational 225ms (모두 1글자 0.5크레딧) / v4 902ms · Multilingual v2 1,172ms (1글자 1크레딧). 원시 값 `docs/voice-review/suzu-latency.json`. 대화 목표(말 끝 → 첫 소리 3초)에는 빠른 3개가 맞다.
- **사용자: 1차 후보에 "귀여운 목소리가 없다" (2026-10-27).** 공유 라이브러리 한국어 4,000개 전수(주 언어 한국어 439개) 확인 — 아이·캐릭터 목소리는 거의 없고 차분한 내레이션 위주. 2차 후보를 검수 페이지에 추가: ① 다른 언어 캐릭터 목소리 7개(Piku·Peanut·Gudiya·Kano·Suzu·Kei·Knurps)로 한국어 읽기 — 억양 확인 필요 ② 1차 한국어 목소리 4개를 4반음 높임(ffmpeg, 크레딧 0).
- 아이 목소리·중성 로봇 목소리는 한국어 원어로는 찾지 못했다. 필요하면 ElevenLabs **Voice Design**(설명으로 새 목소리 만들기)을 쓴다 — 키에 목소리 쓰기 권한이 필요하다.
- 내 계정의 "도윤 후보"·"하린 후보"(Saja·Sujin)는 다른 작업의 캐릭터 목소리라 스노봇에는 쓰지 않는다.

## 4-1. 대화 전체 실측 (2026-10-28, 서버에 연결 완료)

맥 `say -v Yuna`(한국어 합성 음성) 4문장을 말하기 버튼처럼 0.1초씩 실시간으로 흘려보내 잼(`server/tools/sim.py talk`). 맥 → 프로토타입 서버 → ElevenLabs·Anthropic.

| 구간 | 값 |
|---|---|
| 듣기 확정 (버튼 뗌 → 글자) | 0.22~0.34초 (앱에서 본 값) |
| Claude 표정 태그까지 | 0.46~0.79초 (설정문 캐시 후) |
| **말 끝 → 첫 소리** | **1.31~1.46초** (8번) — 처음 판(대답을 다 받고 말하기) 1.7~2.4초에서 줄임. 이름 인식을 넣은 뒤 다시 잰 것까지 17번: 중간값 1.46초, 1.31~2.54초 ([12-latency-measurements.md](12-latency-measurements.md)) |

- 인식: "안녕 스노봇" → "안녕, 스노브" 처럼 이름을 틀린다 → 설정문에 "음성 인식이라 조금 틀릴 수 있다"를 넣음. 다음: Scribe `keyterms=스노봇` 시험.
- 길이: 설정문에 25~40자를 적어도 Haiku 가 넘길 때가 있어 **두 문장에서 끊는다**.
- 아직 사람 목소리·차 소음으로는 안 쟀다(맥 스튜디오에 마이크 없음).

## 5. 다음: 비교 시험 (실측)

한국어 문장 10개로 같은 조건에서 잰다.
- listen-ai: Scribe Realtime vs CLOVA Speech — 정확도(틀린 글자 수), 말 끝나고 글자 확정까지 시간
- think-ai: Haiku 5.5 vs Gemini 3.6 Flash — 첫 글자까지 시간, 스노봇 말투·감정 태그 품질
- tts-ai: ElevenLabs Flash vs Typecast vs CLOVA Voice — 첫 소리까지 시간, **목소리 후보는 사용자가 들어 보고 고른다**
- 목표: 말 끝나고 첫 소리까지 3초 안 (07 문서 4절)

## 출처

- [ElevenLabs API 요금](https://elevenlabs.io/pricing/api) · [Deepgram 요금](https://deepgram.com/pricing) · [OpenAI API 요금](https://developers.openai.com/api/docs/pricing) · [Typecast API 요금](https://typecast.ai/pricing/api/)
- Claude 요금: Anthropic 공식 모델 표(2026-10-06 기준)
- [Gemini API 요금 정리(제3자)](https://www.morphllm.com/gemini-api-pricing) · [Gemini 3.6 Flash 정리(제3자)](https://www.chatbase.co/blog/gemini-3-6-flash)
- [실시간 음성 API 비교(제3자)](https://www.developersdigest.tech/models/speech/realtime) · [AssemblyAI·Google STT 요금 정리(제3자)](https://smallest.ai/blog/speech-to-text-api-pricing-models-explained-(2026)) · [Google STT 요금](https://cloud.google.com/speech/pricing?hl=ko)
- [CLOVA Speech 상품](https://www.ncloud.com/product/aiService/clovaSpeech) · [CLOVA Speech 실시간 스트리밍 문서](https://api.ncloud-docs.com/docs/ai-application-service-clovaspeech-example02) · [CLOVA Voice 상품](https://m.ncloud.com/product/aiService/clovaVoice)
