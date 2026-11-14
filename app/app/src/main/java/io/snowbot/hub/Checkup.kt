package io.snowbot.hub

/**
 * 출발·하차 점검 (기획서 3장 "차 살피기 · 출발·하차 점검").
 * 출발(기어 P → 주행)과 하차(주행 → P) 순간에 **차가 알려 준 값으로만** 한마디 거리를 고른다.
 *
 *   출발: 사람이 앉았는데 벨트를 안 맨 자리 · 주행 가능 거리(없으면 연료·배터리 잔량) 부족
 *   하차: 창문 열림 · 문 열림 · 뒷좌석을 썼으면 가벼운 확인("뒤에 두고 내린 것 없죠?")
 *
 * 규칙
 * - **못 읽는 신호는 만들지 않는다.** 값이 null 이면 그 항목은 아예 나오지 않는다
 *   (차 모델마다 지원이 다르고, 에뮬레이터에 카드가 없는 값도 있다).
 * - 같은 뜻을 두 번 말하지 않는다: 주행 가능 거리를 아는 차에서는 연료·배터리 잔량을 따로 말하지 않는다.
 * - **출발은 한 가지만**([MAX_DEPART]) — 막 움직이기 시작한 운전자에게 길게 말하지 않는다
 *   (판단기 기준으로 주행 중은 두 문장, 인사가 한 문장을 쓴다). 하차는 멈춰 있으니 [MAX_PARK] 가지까지.
 * - 자리 이름은 [BELT_NAMES_MAX] 개까지만 읽는다. 더 많으면 자리를 세지 않고 두루뭉술하게 말한다.
 * - 시동 상태는 Vehicle SDK 에 조회 함수가 없다 → 하차 시점은 **기어 P** 로 본다.
 *
 * 영역 이름은 Pleos `SeatValue`·`WindowValue`·`DoorValue` 이름 그대로 담고(봇·AI 에 주는 `car.cabin` 용),
 * 말할 때만 한국어로 바꾼다.
 */
object Checkup {

    const val RANGE_LOW_KM = 50

    /**
     * 주행 가능 거리로 **쓸 수 있는** 가장 작은 값(km). 차가 1km 미만을 주면 값이 아직 안 올라왔거나
     * 의미가 없는 때라, 그 값으로 말하면 "남은 거리 0킬로미터" 같은 거짓이 된다. 그래서 **모름**으로 본다.
     * 연료가 정말 바닥인 차는 연료 부족 경고등이 켜지고 [CarWarnings] 가 알린다.
     */
    const val MIN_RANGE_KM = 1
    const val ENERGY_LOW_PCT = 15
    const val MAX_DEPART = 1
    const val MAX_PARK = 2
    const val BELT_NAMES_MAX = 2

    /**
     * 차가 알려 준 실내 상태. 모르는 값은 null·빈 목록으로 둔다.
     * @param beltsOff 사람이 앉았는데 벨트를 안 맨 자리 (`ROW_1_RIGHT` …)
     * @param rangeKm 주행 가능 거리(km)
     * @param fuelPct 연료 잔량(%) · @param socPct EV 배터리 잔량(%)
     * @param rearUsed 이번 운행에서 뒷좌석을 썼다 (뒷좌석 탑승 또는 뒷문 열림을 본 적 있음)
     */
    data class Cabin(
        val beltsOff: List<String> = emptyList(),
        val rangeKm: Int? = null,
        val fuelPct: Int? = null,
        val socPct: Int? = null,
        val windowsOpen: List<String> = emptyList(),
        val doorsOpen: List<String> = emptyList(),
        val rearUsed: Boolean = false,
    )

    sealed interface Item
    data class Belt(val seats: List<String>) : Item
    data class Range(val km: Int) : Item
    data class Energy(val pct: Int, val ev: Boolean) : Item
    data class Window(val areas: List<String>) : Item
    data class Door(val areas: List<String>) : Item
    data object Rear : Item

    /** 주행 가능 거리로 쓸 값. [MIN_RANGE_KM] 미만은 모름(null)으로 본다. */
    fun usableRange(km: Int?): Int? = km?.takeIf { it >= MIN_RANGE_KM }

    /** 출발 한마디에 붙일 것 (급한 것 먼저). */
    fun depart(c: Cabin): List<Item> = buildList {
        if (c.beltsOff.isNotEmpty()) add(Belt(c.beltsOff))
        when {
            c.rangeKm != null -> if (c.rangeKm <= RANGE_LOW_KM) add(Range(c.rangeKm))
            c.fuelPct != null && c.fuelPct <= ENERGY_LOW_PCT -> add(Energy(c.fuelPct, ev = false))
            c.socPct != null && c.socPct <= ENERGY_LOW_PCT -> add(Energy(c.socPct, ev = true))
        }
    }.take(MAX_DEPART)

    /** 하차 한마디에 붙일 것 (급한 것 먼저). */
    fun park(c: Cabin): List<Item> = buildList {
        if (c.windowsOpen.isNotEmpty()) add(Window(c.windowsOpen))
        if (c.doorsOpen.isNotEmpty()) add(Door(c.doorsOpen))
        if (c.rearUsed) add(Rear)
    }.take(MAX_PARK)

    private val SEAT_NAMES = mapOf(
        "ROW_1_LEFT" to "운전석", "ROW_1_CENTER" to "앞 가운데", "ROW_1_RIGHT" to "동승석",
        "ROW_2_LEFT" to "뒷좌석 왼쪽", "ROW_2_CENTER" to "뒷좌석 가운데", "ROW_2_RIGHT" to "뒷좌석 오른쪽",
        "ROW_3_LEFT" to "셋째 줄 왼쪽", "ROW_3_CENTER" to "셋째 줄 가운데", "ROW_3_RIGHT" to "셋째 줄 오른쪽",
    )
    private val WINDOW_NAMES = mapOf(
        "ROW_1_LEFT" to "운전석 창문", "ROW_1_RIGHT" to "동승석 창문",
        "ROW_2_LEFT" to "뒷좌석 왼쪽 창문", "ROW_2_RIGHT" to "뒷좌석 오른쪽 창문",
        "ROOF_TOP_1" to "선루프", "ROOF_TOP_2" to "뒤 선루프",
        "FRONT_WINDSHILED" to "앞유리", "FRONT_WINDSHIELD" to "앞유리", "REAR_WINDSHIELD" to "뒷유리",
    )
    private val DOOR_NAMES = mapOf(
        "ROW_1_LEFT" to "운전석 문", "ROW_1_RIGHT" to "동승석 문",
        "ROW_2_LEFT" to "뒷좌석 왼쪽 문", "ROW_2_RIGHT" to "뒷좌석 오른쪽 문",
        "ROW_3_LEFT" to "셋째 줄 왼쪽 문", "ROW_3_RIGHT" to "셋째 줄 오른쪽 문",
        "HOOD" to "보닛", "REAR" to "트렁크",
    )

    /** 뒷좌석으로 보는 영역 이름 (하차 때 "뒤에 두고 내린 것 없죠?" 판단에 쓴다). */
    fun isRear(area: String) = area.startsWith("ROW_2") || area.startsWith("ROW_3") || area == "REAR"

    fun seatName(area: String) = name(area, SEAT_NAMES, "자리")
    fun windowName(area: String) = name(area, WINDOW_NAMES, "창문")
    fun doorName(area: String) = name(area, DOOR_NAMES, "문")

    /**
     * 차가 묶음 구역(`ROW_2_LEFT+ROW_2_RIGHT`)을 주면 첫 칸의 이름으로 말한다.
     * 아는 이름이 없으면 자리 이름을 읽지 않고 두루뭉술하게 말한다(엉뚱한 이름을 만들지 않는다).
     */
    private fun name(area: String, names: Map<String, String>, fallback: String): String =
        area.split('+').firstNotNullOfOrNull { names[it] } ?: fallback
}
