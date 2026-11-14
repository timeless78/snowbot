package io.snowbot.hub

/**
 * 판단 결과 — 입력(차 신호, 봇 센서, 운전자 말, 이전 기록)에서 나온다.
 * **문장·표정·봇 몸을 모른다.** 그것은 [Expression] 이 정한다.
 *
 * 판단하는 쪽: [CarCoach](경고등·주의 경고·휴식·출발·도착) · [CarWatch](대조·변화·회복) ·
 * [Ladder](편의 권유의 전달 방식) · [DriverWords](운전자 말).
 *
 * @param situation 무슨 상황인지. 종류([Kind])를 함께 들고 있다
 * @param delivery 어떻게 전할지 (표정만 · 한마디 · 다시 한마디 · 안도 · 평소로)
 * @param facts 근거 값. 문장에 숫자로 들어간다 (예: `range_km` 38, `dest_km` 52)
 * @param items 출발·하차 점검 거리 ([Checkup])
 * @param warning 경고등 하나 (켜짐·꺼짐 보고)
 * @param report 차 요약 항목 ([CarReport]) — 상태 질문에 답할 때
 * @param offer 수락하면 할 일. 있으면 허브가 **제안 대기**로 두고 "응"을 기다린다
 */
data class Judgement(
    val situation: Situation,
    val delivery: Delivery = Delivery.SAY,
    val facts: Map<String, Any> = emptyMap(),
    val items: List<Checkup.Item> = emptyList(),
    val warning: CarWarnings.Info? = null,
    val report: List<CarReport.Item> = emptyList(),
    val offer: Offer? = null,
) {
    val kind get() = situation.kind

    /**
     * 근거 값. **없으면 null 이다** — 0 으로 바꾸지 않는다.
     * 모르는 값을 0 으로 말하면("남은 거리 0킬로미터") 운전자가 거짓을 듣는다. 표현 쪽이 null 을 받으면
     * 그 숫자가 없는 문장을 고르거나 아예 말하지 않는다([Expression.pieces]).
     */
    fun num(key: String): Int? = facts[key] as? Int

    /** 근거 글자. 비었으면 null. */
    fun text(key: String): String? = (facts[key] as? String)?.takeIf { it.isNotBlank() }
}

/**
 * 운전자가 "그냥 둬"로 끈 상황인지. **안전은 끌 수 없다** — 쉬자는 말을 끄면 가장 위험한 운전자에게
 * 경고가 사라진다(docs/pleos/13-ladder-review.md 4-2). 말 없이 표정만 가는 것은 막지 않는다
 * (얼굴이 그 상황 표정으로 멈춰 있지 않게).
 */
fun muted(j: Judgement, dropped: Set<Situation>): Boolean =
    j.kind != Kind.SAFETY && j.situation in dropped && !Expression.silent(j.delivery)

/** 종류. 한 순간에 여럿이 겹치면 이 차례로 하나만 고른다: 안전 → 권유 → 인사. */
enum class Kind { SAFETY, TIP, GREETING }

/** 전달 방식. [Ladder] 의 칸과 같은 뜻이고, 사다리를 쓰지 않는 상황은 [SAY] 로 온다. */
enum class Delivery {
    FACE_ONLY,   // 표정만 (말 없이)
    SAY,         // 한마디
    SAY_AGAIN,   // 나빠졌으니 다른 말로 한 번 더
    RELIEF,      // 안도 — 표정만
    CALM,        // 평소로 — 표정만
}

/** 수락하면 열 내비 화면. 앱이 장소를 **검색**할 수는 없어 운전자가 고르는 화면만 연다. */
enum class Offer { FUEL, CHARGE, REST, REROUTE }

/**
 * 상황. 운전자가 들으면 왜 말했는지 바로 알 수 있는 것만 둔다.
 * 감정·몸짓은 [Expression.look], 문장은 [Expression.pieces] 가 정한다(기술 명세서 4-1 표).
 */
enum class Situation(val kind: Kind) {
    // ---- 안전 ----
    WARNING_ON(Kind.SAFETY),          // 경고등이 새로 켜졌다
    ATTENTION(Kind.SAFETY),           // 차가 운전자 주의 경고등을 켰다
    DOOR_OPEN_DRIVING(Kind.SAFETY),   // 주행 중인데 문이 열려 있다
    NIGHT_NO_BEAM(Kind.SAFETY),       // 밤 · 주행 중인데 전조등이 꺼져 있다

    // 오래 운전했다. **안전이다** — 명세서 3-1 의 쉼터 권유이고, "그냥 둬"로 끌 수 없다.
    // (도착 전 운전 시간을 미리 알리는 [DRIVE_TIME_OVER] 는 제안이라 권유로 둔다.)
    LONG_DRIVE(Kind.SAFETY),

    // ---- 권유 ----
    STUFFY(Kind.TIP),                 // 답답한 공기 (CO₂)
    FOG_RISK(Kind.TIP),               // 김서림 우려 (습도 + 비·밤)
    RANGE_SHORT(Kind.TIP),            // 주행 가능 거리 < 목적지까지 남은 거리
    RANGE_LOW(Kind.TIP),              // 주행 가능 거리가 100km·50km 아래로 처음 내려갔다
    DRIVE_TIME_OVER(Kind.TIP),        // 도착 전에 연속 운전 시간이 넘는다
    RAIN_WINDOW_OPEN(Kind.TIP),       // 비 오는데 창문이 열려 있다
    ROUTE_CONGESTED(Kind.TIP),        // 앞 경로가 정체다
    WARNING_OFF(Kind.TIP),            // 경고등이 꺼졌다 (회복 보고)

    // ---- 운전자 말에 대한 답 ----
    REPORT(Kind.TIP),                 // "지금 차 어때" — 차 요약
    RANGE_REPORT(Kind.TIP),           // "얼마나 더 갈 수 있어" — 주행 가능 거리·목적지까지
    INTACT(Kind.TIP),                 // "멀쩡한 게 뭐야" — 정상인 것
    CANT_CONTROL(Kind.TIP),           // "창문 열어줘" — 아직 직접 못 움직인다
    CANT_TALK(Kind.TIP),              // 백엔드에 못 붙었다
    WORKING(Kind.TIP),                // 확인 중 (진행 보고)
    NAVI_OPENED(Kind.TIP),            // 내비 화면을 열었다
    NAVI_MISSING(Kind.TIP),           // 내비를 쓸 수 없다
    DROPPED(Kind.TIP),                // 운전자가 그만두라고 했다 — 말 없이 평소 표정으로

    // ---- 인사 ----
    DEPART(Kind.GREETING),
    ARRIVE(Kind.GREETING),
}
