package io.snowbot.hub

/**
 * 차 요약 — 차 신호·봇 센서·내비를 **항목별 값 + 정상 여부**로 모은다. 순수 함수만 둔다(JVM 단위 시험).
 *
 * 쓰는 곳 세 군데:
 *   1. 운전자가 "지금 차 어때"라고 물으면 앱이 바로 답한다([say]) — 인터넷이 없어도 된다.
 *   2. "멀쩡한 게 뭐야"에는 정상인 것 하나를 골라 건조하게 답한다([intact]).
 *   3. 백엔드에 짧은 글([forServer])로 보내 AI 가 차 상태를 알고 대답한다.
 *   4. 앱 화면의 차 상태 패널이 같은 목록을 그린다.
 *
 * **못 읽은 값은 만들지 않는다.** 값이 null 인 항목은 목록에 넣되 [Item.value] 를 null 로 두고
 * 말에는 쓰지 않는다 — 화면에는 "—"로 보인다.
 */
object CarReport {

    /**
     * @param key 화면·시험에서 항목을 가리키는 이름
     * @param label 짧은 이름 ("경고등")
     * @param value 지금 값 ("타이어 공기압", "38km"). 못 읽었으면 null
     * @param ok 정상인지. false 면 주의 표시를 하고 말에 먼저 쓴다
     * @param phrase 말로 할 때 쓰는 한 조각. 비면 말하지 않는다
     */
    data class Item(
        val key: String,
        val label: String,
        val value: String?,
        val ok: Boolean = true,
        val phrase: String = "",
    )

    const val SAY_MAX = 2        // 보고 한 번에 담는 문제 수 (말이 길어지지 않게)

    /** 운전 시간이 이 분을 넘으면 "오래 달렸다"로 본다. [CarCoach.REST_AFTER_MIN] 과 같은 뜻. */
    const val LONG_MIN = CarCoach.REST_AFTER_MIN

    /**
     * 지금 차 상태를 항목으로. 순서가 보고·화면의 순서다.
     *
     * @param warnings 켜진 경고등 (Pleos TelltaleType 이름)
     * @param co2 봇이 준 CO₂ ppm · @param humidity 봇이 준 습도 %
     * @param online 백엔드에 붙어 있는지
     */
    fun items(
        gear: String,
        drivingMin: Int,
        warnings: Set<String>,
        cabin: Checkup.Cabin,
        signals: CarState.Signals,
        nav: NaviState.Nav,
        co2: Int?,
        humidity: Int?,
        online: Boolean,
    ): List<Item> = buildList {
        val lamps = CarWarnings.labels(warnings)
        add(Item("warnings", "경고등",
            if (lamps.isEmpty()) "없음" else lamps.joinToString("·"),
            ok = lamps.isEmpty(),
            phrase = if (lamps.isEmpty()) "" else "${lamps.first()} 경고등이 켜져 있어요."))

        val range = cabin.rangeKm
        if (range != null) {
            add(Item("range", "주행 가능 거리", "${range}km", ok = range > Checkup.RANGE_LOW_KM,
                phrase = "남은 거리 ${range}킬로미터예요."))
        } else {
            val pct = cabin.socPct ?: cabin.fuelPct
            val ev = cabin.socPct != null
            add(Item("range", if (ev) "배터리" else "연료", pct?.let { "$it%" },
                ok = pct == null || pct > Checkup.ENERGY_LOW_PCT,
                phrase = if (pct == null) "" else "${if (ev) "배터리" else "연료"}가 ${pct}퍼센트 남았어요."))
        }

        add(Item("dest", "목적지까지",
            nav.destKm?.let { km -> nav.destMin?.let { "${km}km · ${it}분" } ?: "${km}km" }
                ?: nav.destMin?.let { "${it}분" }))

        add(Item("driving", "운전 시간", if (gear == "?") null else "${drivingMin}분",
            ok = drivingMin < LONG_MIN,
            phrase = if (drivingMin < LONG_MIN) "" else "${drivingMin}분째 운전 중이에요."))

        val doors = cabin.doorsOpen.map(Checkup::doorName)
        val windows = cabin.windowsOpen.map(Checkup::windowName)
        add(Item("door", "문", if (doors.isEmpty()) "닫힘" else doors.joinToString("·"), ok = doors.isEmpty(),
            phrase = if (doors.isEmpty()) "" else "${doors.first()}이 열려 있어요."))
        add(Item("window", "창문", if (windows.isEmpty()) "닫힘" else windows.joinToString("·"), ok = windows.isEmpty(),
            phrase = if (windows.isEmpty()) "" else "${windows.first()}이 열려 있어요."))
        add(Item("belt", "안전벨트", if (cabin.beltsOff.isEmpty()) "맴" else cabin.beltsOff.joinToString("·", transform = Checkup::seatName),
            ok = cabin.beltsOff.isEmpty(),
            phrase = if (cabin.beltsOff.isEmpty()) "" else "안전벨트 안 맨 자리가 있어요."))

        add(Item("hvac", "공조", listOfNotNull(
            signals.recirc?.let { if (it) "내기" else "외기" },
            signals.ac?.let { if (it) "에어컨 켜짐" else "에어컨 꺼짐" },
        ).joinToString(" · ").ifEmpty { null }))

        add(Item("co2", "CO₂", co2?.let { "${fmt(it)}ppm" },
            ok = co2 == null || co2 < Interpreter.CO2_ON,
            phrase = if (co2 == null || co2 < Interpreter.CO2_ON) "" else "CO₂ ${fmt(co2)}ppm이에요."))
        add(Item("humidity", "습도", humidity?.let { "$it%" },
            ok = humidity == null || humidity < Interpreter.HUMID_ON,
            phrase = if (humidity == null || humidity < Interpreter.HUMID_ON) "" else "습도 ${humidity}퍼센트예요."))

        add(Item("online", "인터넷", if (online) "연결됨" else "끊김", ok = online,
            phrase = if (online) "" else "인터넷이 끊겨 있어요."))
    }

    /**
     * 운전자에게 하는 보고. 문제가 있으면 급한 것부터 [SAY_MAX] 개까지, 없으면 괜찮다고 한 뒤 값 하나를 붙인다.
     * 지어내지 않는다 — 말할 조각([Item.phrase])이 있는 항목만 쓴다.
     */
    fun say(items: List<Item>): String {
        val bad = items.filter { !it.ok && it.phrase.isNotEmpty() }.take(SAY_MAX)
        if (bad.isNotEmpty()) return bad.joinToString(" ") { it.phrase }
        val known = items.firstOrNull { it.key == "range" && it.value != null }
            ?: items.firstOrNull { it.key == "dest" && it.value != null }
        return listOfNotNull("지금은 다 괜찮아요.", known?.let { "${it.label} ${it.value}예요." }).joinToString(" ")
    }

    /**
     * "멀쩡한 게 뭐야"에 대한 답. 정상이고 값을 아는 항목 중 [INTACT_ORDER] 가 앞에 둔 것 하나를 고른다.
     * 다 괜찮으면 그냥 다 괜찮다고 하고, 볼 수 있는 값이 없으면 모른다고 한다(지어내지 않는다).
     */
    fun intact(items: List<Item>): String {
        val bad = items.count { !it.ok }
        val fine = items.filter { it.ok && it.value != null && it.key != "online" }
        if (bad == 0) return if (fine.isEmpty()) "아직 볼 수 있는 값이 없어요." else "다 멀쩡해요."
        val pick = INTACT_ORDER.firstNotNullOfOrNull { key -> fine.firstOrNull { it.key == key } }
            ?: return "멀쩡하다고 할 만한 게 지금은 없어요."
        return "${pick.label}${topic(pick.label)} 완전 멀쩡해요."
    }

    /**
     * "얼마나 더 갈 수 있어"에 대한 답. 주행 가능 거리(없으면 잔량)와 목적지까지를 말한다.
     * 둘 다 모르면 모른다고 한다 — 지어내지 않는다.
     */
    fun howFar(items: List<Item>): String {
        val range = items.firstOrNull { it.key == "range" && it.value != null }
        val dest = items.firstOrNull { it.key == "dest" && it.value != null }
        if (range == null && dest == null) return "지금은 남은 거리를 못 봐요."
        return listOfNotNull(
            range?.phrase?.ifEmpty { "${range.label} ${range.value}예요." },
            dest?.let { "목적지까지 ${it.value}예요." },
        ).joinToString(" ")
    }

    /** 백엔드에 보내는 짧은 요약 글. 값을 아는 항목만, 세미콜론으로 잇는다. */
    fun forServer(items: List<Item>): String =
        items.filter { it.value != null }.joinToString("; ") { "${it.label} ${it.value}" }

    /** 1500 → "1,500". 말로 읽기 쉽게. */
    fun fmt(n: Int): String = "%,d".format(n)

    /** 은/는. 받침이 있으면 "은". 영어·기호로 끝나는 이름(CO₂)은 "는"으로 둔다. */
    fun topic(word: String): String {
        val last = word.lastOrNull() ?: return "는"
        if (last !in '가'..'힣') return "는"
        return if ((last - '가') % 28 == 0) "는" else "은"
    }

    /** 농담에 쓰기 좋은 차례 — 운전자가 "그게 멀쩡해서 뭐 해"라고 받을 만한 것부터. */
    private val INTACT_ORDER = listOf("hvac", "window", "door", "belt", "co2", "humidity", "range", "driving", "warnings")
}
