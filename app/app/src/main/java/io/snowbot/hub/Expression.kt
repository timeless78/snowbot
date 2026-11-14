package io.snowbot.hub

/**
 * 표현 — 판단 결과([Judgement])를 **감정·몸짓·문장**으로 바꾼다. 판단 결과를 바꾸지 않는다.
 * 순수하다(JVM 단위 시험): 문장은 글자가 아니라 **조각 목록**([Piece])으로 돌려주고,
 * 글자로 바꾸는 것은 안드로이드 자원을 가진 쪽([SnowBotService.render])이 한다.
 *
 * 감정 표 (기술 명세서 4-1): 새 경고등·주의 경고 worried · 오래 운전 sleepy · 답답한 공기 sleepy ·
 * 김서림 worried(한마디일 때 nod) · 안도 happy · 평소로 neutral · 출발 greet+nod · 도착 happy+nod.
 * 세기(intensity)는 0.6 고정이다([BotConnect.express] 의 기본값).
 *
 * **문장에 근거 숫자를 넣는다** — 운전자가 왜 말했는지 바로 알게. 숫자는 판단이 넘긴 [Judgement.facts] 에서만 쓴다.
 * 판단이 **값을 넘기지 않았으면 지어내지 않는다**: 그 숫자가 없는 문장을 고르거나, 그런 문장이 없으면
 * 아예 말하지 않는다(빈 조각 목록 → 표정만). 0 으로 바꿔 말하면 운전자가 거짓을 듣는다.
 */
object Expression {

    /** 봇·화면에 보낼 표정. [gesture] 가 null 이면 고개를 움직이지 않는다. */
    data class Face(val emotion: String, val gesture: String? = null)

    /** 문장 한 조각. */
    sealed interface Piece
    /** 코드 안에 든 글 (경고등 표의 한마디, 차 요약 보고) */
    data class Raw(val text: String) : Piece
    /** 문자열 자원 + 넣을 값 */
    data class Text(val res: Int, val args: List<Any?> = emptyList()) : Piece
    /** 문자열 배열 자원에서 하나 고르기 (인사말 바꿔 가며) */
    data class OneOf(val arrayRes: Int) : Piece

    /** 말 없이 표정만 가는 전달 방식. */
    fun silent(d: Delivery) = d == Delivery.FACE_ONLY || d == Delivery.RELIEF || d == Delivery.CALM

    /** 이 판단을 어떤 얼굴로 보일지. */
    fun look(j: Judgement): Face = when (j.delivery) {
        Delivery.RELIEF -> Face(Ladder.RELIEF_EMOTION)
        Delivery.CALM -> Face("neutral")
        Delivery.FACE_ONLY -> Face(emotion(j.situation))
        else -> Face(emotion(j.situation), gesture(j.situation))
    }

    private fun emotion(s: Situation): String = when (s) {
        Situation.WARNING_ON, Situation.ATTENTION, Situation.DOOR_OPEN_DRIVING, Situation.NIGHT_NO_BEAM,
        Situation.FOG_RISK, Situation.RAIN_WINDOW_OPEN, Situation.RANGE_SHORT, Situation.RANGE_LOW,
        Situation.CANT_CONTROL, Situation.CANT_TALK, Situation.NAVI_MISSING -> "worried"
        Situation.LONG_DRIVE, Situation.STUFFY, Situation.DRIVE_TIME_OVER -> "sleepy"
        Situation.WARNING_OFF, Situation.INTACT, Situation.NAVI_OPENED, Situation.ARRIVE -> "happy"
        Situation.DEPART -> "greet"
        Situation.WORKING -> "alert"
        Situation.REPORT, Situation.RANGE_REPORT, Situation.ROUTE_CONGESTED, Situation.DROPPED -> "neutral"
    }

    /** 고개는 말에 곁들인다 — 표정만 보낼 때는 움직이지 않는다([look] 이 거른다). */
    private fun gesture(s: Situation): String? = when (s) {
        Situation.DEPART, Situation.ARRIVE, Situation.FOG_RISK, Situation.NAVI_OPENED -> "nod"
        else -> null
    }

    /** 이 판단을 무슨 말로 할지. 빈 목록이면 말하지 않는다. */
    fun pieces(j: Judgement): List<Piece> {
        if (silent(j.delivery)) return emptyList()
        val again = j.delivery == Delivery.SAY_AGAIN
        return when (j.situation) {
            // ---- 안전 ----
            Situation.WARNING_ON -> listOfNotNull(j.warning?.let { Raw(it.line) })
            Situation.ATTENTION -> listOf(OneOf(R.array.car_attention))
            Situation.DOOR_OPEN_DRIVING -> listOf(Text(R.string.watch_door_open))
            Situation.NIGHT_NO_BEAM -> listOf(Text(R.string.watch_no_beam))
            Situation.LONG_DRIVE -> {
                val hours = j.num("hours") ?: return emptyList()
                val mins = j.num("mins") ?: 0
                listOf(if (mins == 0) Text(R.string.car_rest_h, listOf(hours))
                       else Text(R.string.car_rest_hm, listOf(hours, mins)))
            }

            // ---- 권유 ----
            Situation.STUFFY -> {
                val recirc = j.facts["recirc"] == true
                if (again) {
                    val from = j.num("from") ?: return emptyList()
                    val to = j.num("to") ?: return emptyList()
                    listOf(Text(if (recirc) R.string.line_stuffy_recirc_again else R.string.line_air_again,
                        listOf(CarReport.fmt(from), CarReport.fmt(to))))
                } else {
                    val co2 = j.num("co2") ?: return emptyList()
                    listOf(Text(if (recirc) R.string.line_stuffy_recirc else R.string.line_air,
                        listOf(CarReport.fmt(co2))))
                }
            }
            Situation.FOG_RISK -> {
                if (again) {
                    val from = j.num("from") ?: return emptyList()
                    val to = j.num("to") ?: return emptyList()
                    listOf(Text(R.string.line_fog_again, listOf(from, to)))
                } else {
                    val humidity = j.num("humidity") ?: return emptyList()
                    listOf(Text(if (j.facts["ac"] == true) R.string.line_fog_ac_on else R.string.line_fog, listOf(humidity)))
                }
            }
            Situation.RANGE_SHORT -> {
                val range = j.num("range_km") ?: return emptyList()
                val dest = j.num("dest_km") ?: return emptyList()
                listOf(Text(R.string.watch_range_short, listOf(range, dest, place(j.offer))))
            }
            Situation.RANGE_LOW -> listOf(Text(R.string.watch_range_low, listOf(j.num("range_km") ?: return emptyList())))
            Situation.DRIVE_TIME_OVER -> {
                val hours = j.num("hours") ?: return emptyList()
                val left = j.num("dest_min") ?: return emptyList()
                listOf(Text(R.string.watch_time_over, listOf(hours, left)))
            }
            Situation.RAIN_WINDOW_OPEN -> listOf(Text(R.string.watch_rain_window))
            Situation.ROUTE_CONGESTED -> listOf(
                j.num("congestion_km")?.let { Text(R.string.watch_jam_km, listOf(it)) } ?: Text(R.string.watch_jam)
            )
            Situation.WARNING_OFF -> listOfNotNull(j.warning?.let { Text(R.string.warn_off, listOf(it.label)) })

            // ---- 운전자 말에 대한 답 ----
            Situation.REPORT -> listOf(Raw(CarReport.say(j.report)))
            Situation.RANGE_REPORT -> listOf(Raw(CarReport.howFar(j.report)))
            Situation.INTACT -> listOf(Raw(CarReport.intact(j.report)))
            // 어느 부분인지 모르면 부분 이름 없이 말한다 (빈 이름으로 "은 직접 해 주셔야" 하지 않게)
            Situation.CANT_CONTROL -> listOf(j.text("what")
                ?.let { Text(R.string.cant_control, listOf(it + CarReport.topic(it))) }
                ?: Text(R.string.cant_control_any))
            Situation.CANT_TALK -> listOf(Text(R.string.cant_talk))
            Situation.WORKING -> listOf(Text(R.string.working))
            Situation.NAVI_OPENED -> listOf(Text(R.string.navi_opened))
            Situation.NAVI_MISSING -> listOf(Text(R.string.navi_missing))
            Situation.DROPPED -> emptyList()

            // ---- 인사 (점검 한마디를 한 호흡으로 붙인다) ----
            Situation.DEPART -> listOf(OneOf(R.array.car_depart)) + j.items.map(::checkup)
            Situation.ARRIVE ->
                listOf(OneOf(if (j.facts["long"] == true) R.array.car_arrive_long else R.array.car_arrive)) +
                    j.items.map(::checkup)
        }
    }

    /** 출발·하차 점검 한 가지를 말로. */
    private fun checkup(item: Checkup.Item): Piece = when (item) {
        // 자리가 많으면 하나하나 읽지 않는다 ("뒷좌석 왼쪽·가운데·오른쪽·동승석 안전벨트가…"는 너무 길다)
        is Checkup.Belt ->
            if (item.seats.size > Checkup.BELT_NAMES_MAX) Text(R.string.check_belt_many)
            else Text(R.string.check_belt, listOf(item.seats.joinToString("·", transform = Checkup::seatName)))
        is Checkup.Range -> Text(R.string.check_range, listOf(item.km))
        is Checkup.Energy -> Text(if (item.ev) R.string.check_soc else R.string.check_fuel, listOf(item.pct))
        is Checkup.Window -> Text(R.string.check_window, listOf(item.areas.joinToString("·", transform = Checkup::windowName)))
        is Checkup.Door -> Text(R.string.check_door, listOf(item.areas.joinToString("·", transform = Checkup::doorName)))
        Checkup.Rear -> Text(R.string.check_rear)
    }

    /**
     * 화면 패널 위에 잠깐 크게 보일 근거 숫자. 말로 수치를 인용한 상황에만 있다(영화의 수치 표시 느낌).
     * 판단이 넘긴 값만 쓴다 — 없으면 null.
     */
    fun flash(j: Judgement): String? {
        if (silent(j.delivery)) return null
        return when (j.situation) {
            Situation.RANGE_SHORT -> both(j.num("range_km"), j.num("dest_km")) { r, d -> "${r}km / 목적지 ${d}km" }
            Situation.RANGE_LOW -> j.num("range_km")?.let { "${it}km" }
            Situation.STUFFY -> j.num("co2")?.let { "CO₂ ${CarReport.fmt(it)}ppm" }
            Situation.FOG_RISK -> j.num("humidity")?.let { "습도 $it%" }
            Situation.DRIVE_TIME_OVER -> both(j.num("hours"), j.num("dest_min")) { h, m -> "${h}시간 · 남은 ${m}분" }
            Situation.LONG_DRIVE -> both(j.num("hours"), j.num("mins")) { h, m -> "${h}시간 ${m}분" }
            Situation.ROUTE_CONGESTED -> j.num("congestion_km")?.let { "정체 ${it}km" }
            else -> null
        }
    }

    /** 둘 다 있을 때만. 하나라도 없으면 숫자를 띄우지 않는다. */
    private fun both(a: Int?, b: Int?, make: (Int, Int) -> String): String? =
        if (a == null || b == null) null else make(a, b)

    /** 제안이 열어 줄 곳의 이름. 문장에 넣는다. */
    fun place(offer: Offer?): String = when (offer) {
        Offer.CHARGE -> "충전소"
        Offer.REST -> "쉴 곳"
        Offer.REROUTE -> "다른 길"
        else -> "주유소"
    }
}
