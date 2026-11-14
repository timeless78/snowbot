package io.snowbot.hub

/**
 * 운전자 말을 **입력으로** 읽는다 (순수 함수, JVM 단위 시험).
 * 앱은 차의 듣기가 준 글자를 백엔드(AI)로 보내기 **전에** 먼저 이것으로 본다.
 *
 *   상태 질문 · "멀쩡한 게 뭐야" · 남은 거리 질문  → 앱이 차 요약으로 바로 답한다 (인터넷 불필요)
 *   수락 · 거절·취소                              → 지금 권유를 끝내거나 제안을 실행한다
 *   차 조작 요청                                  → 아직 못 한다고 짧게 보고한다 (Vehicle Control SDK 는 "준비 중")
 *   경로 요청                                     → 내비 화면을 연다
 *   그 밖에                                       → [ToAi] — 지금처럼 백엔드가 대답한다
 *
 * **오인식 방지**: 차의 듣기는 글자를 틀리게 적고, 잡담에도 같은 낱말이 섞인다.
 * 그래서 종류마다 **짧은 문장에서만** 맞춰 보고(글자 수 제한), 길면 AI 에 넘긴다.
 * 수락·거절은 **문장 전체가 그 말이거나 그 말로 시작하고 짧은 꼬리말만 붙었을 때**만 맞는다([hits]) —
 * "반응이 왜 그래"의 "응", "콜라 좋아"의 "좋아"에 걸리지 않게.
 * 수락("응")은 **기다리는 제안이 있을 때만**, 거절("그냥 둬")은 **끌 권유나 기다리는 제안이 있을 때만**
 * 그렇게 읽는다. 끌 것이 없으면 그냥 하는 말이므로 AI 가 대답해야 한다("나 괜찮아").
 */
object DriverWords {

    sealed interface Said
    /** "지금 차 어때" — 차 요약을 말한다 */
    data object StatusAsk : Said
    /** "멀쩡한 게 뭐야" — 정상인 것을 말한다 */
    data object IntactAsk : Said
    /** "얼마나 더 갈 수 있어" — 주행 가능 거리·잔량 */
    data object RangeAsk : Said
    /** 기다리는 제안을 하겠다 */
    data object Accept : Said
    /** 그만두라고 했다 — 이번 운행에서 그 상황의 권유를 다시 하지 않는다 (안전은 못 끈다) */
    data object Decline : Said
    /** 차를 움직여 달라고 했다. [what] 은 그 부분 이름("창문") */
    data class Control(val what: String) : Said
    /** 길·장소를 찾아 달라고 했다 */
    data class Route(val offer: Offer) : Said
    /** 그 밖의 말 — 백엔드가 대답한다 */
    data object ToAi : Said

    // 종류마다 다른 글자 수 한도 (띄어쓰기·기호를 뺀 길이). 길면 잡담으로 보고 AI 에 넘긴다
    const val MAX_YESNO = 8
    const val MAX_CONTROL = 12
    const val MAX_ROUTE = 14
    const val MAX_ASK = 16

    private val ACCEPT = listOf("응", "어", "그래", "해줘", "해조", "넣어줘", "좋아", "부탁", "그러자", "알겠어", "오케이", "콜")
    private val DECLINE = listOf("그냥둬", "됐어", "됬어", "괜찮아", "하지마", "취소", "안해", "아니", "놔둬", "나중에", "싫어", "그만")

    /** 꼬리말에만 올 수 있는 글자 ("그래 해줘요" 의 "해줘요"). 뜻이 있는 낱말은 넣지 않는다. */
    private const val TAIL = "요줘해봐야아어용지음넹응다"
    private const val TAIL_MAX = 3

    private val STATUS = listOf("차어때", "차상태", "상태알려", "상태어때", "보고해", "차괜찮", "상태보고", "차는어때", "지금어때")
    private val INTACT = listOf("멀쩡")
    private val RANGE = listOf("얼마나더갈", "얼마나갈", "얼마나더가", "주행가능", "기름얼마", "연료얼마", "배터리얼마", "충전얼마", "몇킬로")

    /** 차 조작 요청 → 그 부분 이름. 켜고 끄는 말을 함께 모은다 */
    private val CONTROL = listOf(
        "창문" to "창문", "윈도" to "창문", "선루프" to "선루프",
        "에어컨" to "에어컨", "히터" to "히터", "난방" to "히터", "냉방" to "에어컨",
        "외기" to "공조", "내기" to "공조", "환기" to "공조", "성에" to "성에 제거",
        "문열" to "문", "문닫" to "문", "트렁크" to "트렁크", "시트" to "시트", "열선" to "열선",
    )

    /** 조작을 **요청**하는 말. 이것이 없으면 그냥 이야기로 본다("에어컨 시원하네") */
    private val CONTROL_VERB = listOf("켜", "꺼", "열", "닫", "올려", "내려", "바꿔", "틀어", "줄여", "높여", "낮춰")

    private val ROUTE = listOf(
        "쉴곳" to Offer.REST, "휴게소" to Offer.REST, "쉼터" to Offer.REST, "쉬어갈" to Offer.REST,
        "충전소" to Offer.CHARGE, "충전할" to Offer.CHARGE,
        "주유소" to Offer.FUEL, "기름넣" to Offer.FUEL, "주유" to Offer.FUEL,
        "다른길" to Offer.REROUTE, "딴길" to Offer.REROUTE, "우회" to Offer.REROUTE, "다른경로" to Offer.REROUTE,
    )

    /**
     * @param text 차의 듣기가 적은 글자
     * @param waiting 지금 수락을 기다리는 제안이 있는지
     * @param tip 방금 한 권유가 있어 "그냥 둬"로 끌 것이 있는지
     */
    fun parse(text: String, waiting: Boolean, tip: Boolean = false): Said {
        val t = squeeze(text)
        if (t.isEmpty()) return ToAi
        // 질문을 먼저 본다 — "차 괜찮아?"가 "괜찮아"(거절)로 읽히지 않게
        if (t.length <= MAX_ASK) {
            if (INTACT.any { t.contains(it) }) return IntactAsk
            if (RANGE.any { t.contains(it) }) return RangeAsk
            if (STATUS.any { t.contains(it) }) return StatusAsk
        }
        if (t.length <= MAX_YESNO) {
            if (waiting && hits(t, ACCEPT)) return Accept
            if ((waiting || tip) && hits(t, DECLINE)) return Decline
        }
        if (t.length <= MAX_ROUTE) {
            ROUTE.firstOrNull { (k, _) -> t.contains(k) }?.let { return Route(it.second) }
        }
        if (t.length <= MAX_CONTROL && CONTROL_VERB.any { t.contains(it) }) {
            CONTROL.firstOrNull { (k, _) -> t.contains(k) }?.let { return Control(it.second) }
        }
        return ToAi
    }

    /**
     * 문장 전체가 그 말이거나, 그 말로 **시작**하고 뒤에 짧은 꼬리말만 붙었을 때만 맞는다.
     * 낱말이 문장 가운데 섞여 있는 것은 맞지 않는다 — "반응이 왜 그래"는 수락이 아니다.
     */
    private fun hits(t: String, words: List<String>): Boolean = words.any { k ->
        t == k || (t.startsWith(k) && t.length - k.length <= TAIL_MAX && t.drop(k.length).all { it in TAIL })
    }

    /** 띄어쓰기·문장부호를 뺀 글자. 듣기가 적은 글자에는 쉼표·물음표가 섞인다. */
    fun squeeze(text: String): String = text.filter { !it.isWhitespace() && it !in PUNCT }

    private const val PUNCT = ".,!?~…·\"'’”“()[]{}-—"
}
