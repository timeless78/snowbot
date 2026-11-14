package io.snowbot.hub

/**
 * 경고등 통역 (봇 커넥트 규약 4-1절 `car.warning`, 5절 `warning_*` 상황).
 * 계기판에 새로 켜진 경고등을 **앱 안 고정 문장**으로 쉬운 말로 바꾼다 — 서버·AI 를 거치지 않으니
 * 인터넷이 없어도 되고 AI 비용도 들지 않는다.
 *
 * 키는 Pleos Vehicle SDK 2.3.2 의 `TelltaleType` 이름 그대로다(SDK 에 98종이 있다). 아래 표는
 * 에뮬레이터 Virtual Console 이 켤 수 있는 37종 중 경고인 것 + 운전자에게 알릴 만한 것을 담았다.
 *
 * - 표에 없는 종류는 **말하지 않는다**. 전조등·깜빡이·드라이브 모드처럼 "켜졌다"가 경고가 아닌 표시등이
 *   많기 때문이다([SILENT] 은 그중 흔한 것을 적어 둔 목록 — 분류를 시험으로 지키려고 둔 것이고,
 *   표에 없으면 어차피 말하지 않는다).
 * - `DRIVER_ATTENTION`(운전자 주의 경고)은 [CarCoach] 가 따로 쉼터 권유를 하므로 여기서는 말하지 않는다.
 * - 같은 경고를 반복해서 말하지 않는다: [Tracker] 가 꺼졌다 다시 켜질 때만 새 경고로 본다.
 * - 한 번에 여러 개가 켜지면 **가장 급한 것 하나만** 말한다. 나머지는 `car.warning` 으로 봇·AI 에 간다.
 *
 * 문장 규칙 (11/8 바꿈): **어떤 경고등이 켜졌는지만** 알린다("타이어 공기압 경고등이 켜졌어요.").
 *   어디로 가라·무엇을 하라는 말은 하지 않는다 — 고속도로인지, 목적지가 어디인지, 깜빡이는지 계속 켜졌는지를
 *   앱이 모르므로 조치는 운전자가 정한다. "그럼 어떡해?"는 대화가 맡는다(켜진 경고등은 서버 AI 에 차 요약으로 간다).
 *   예외: 어디서든 조치가 같은 바로 위험한 것(엔진 오일 압력·엔진 과열·전원 저하·수소 누출)만 세우라는 말을 붙인다.
 */
object CarWarnings {

    /**
     * @param type Pleos `TelltaleType` 이름
     * @param situation 규약 5절 상황 이름 (`warning_tire` …)
     * @param label 화면·봇에 쓰는 짧은 이름
     * @param line 운전자에게 하는 한마디
     * @param urgent 지금 멈추거나 바로 확인해야 하는 것 — 여러 개가 켜졌을 때 먼저 말한다
     */
    data class Info(
        val type: String,
        val situation: String,
        val label: String,
        val line: String,
        val urgent: Boolean = false,
    )

    private val ALL = listOf(
        // ---- 바로 세우거나 확인해야 하는 것 ----
        Info("ENGINE_OIL_PRESSURE", "warning_oil_pressure", "엔진 오일 압력", "엔진 오일 압력 경고등이 켜졌어요. 안전한 곳에 세워 주세요.", true),
        Info("ENGINE_TEMPERATURE", "warning_engine_temp", "엔진 과열", "엔진 과열 경고등이 켜졌어요. 안전한 곳에 세워 주세요.", true),
        Info("POWER_DOWN", "warning_power_down", "전원 저하", "전원 저하 경고등이 켜졌어요. 안전한 곳에 세워 주세요.", true),
        Info("H2_LEAK", "warning_h2_leak", "수소 누출", "수소 누출 경고등이 켜졌어요. 안전한 곳에 세우고 차에서 내려 주세요.", true),
        Info("AIRBAG", "warning_airbag", "에어백", "에어백 경고등이 켜졌어요.", true),
        Info("EV_SYSTEM", "warning_ev_system", "전기 구동 장치", "전기 구동 장치 경고등이 켜졌어요.", true),
        Info("HEV_SYSTEM", "warning_hev_system", "하이브리드 장치", "하이브리드 장치 경고등이 켜졌어요.", true),
        Info("FCEV_SYSTEM", "warning_fcev_system", "수소 연료전지", "수소 연료전지 경고등이 켜졌어요.", true),
        Info("EREV_SYSTEM", "warning_erev_system", "주행거리 연장 장치", "주행거리 연장 장치 경고등이 켜졌어요.", true),
        Info("LOW_VOLT_BATTERY", "warning_low_volt", "배터리 충전", "배터리 충전 경고등이 켜졌어요.", true),
        Info("DOOR_OPEN", "warning_door", "문 열림", "문 열림 경고등이 켜졌어요.", true),

        // ---- 운전 중에 조심하면 되는 것 ----
        Info("TPMS", "warning_tire", "타이어 공기압", "타이어 공기압 경고등이 켜졌어요."),
        Info("SEAT_BELT", "warning_belt", "안전벨트", "안전벨트 경고등이 켜졌어요."),
        Info("TRUNK_OPEN", "warning_trunk", "트렁크 열림", "트렁크 열림 경고등이 켜졌어요."),
        Info("ABS", "warning_abs", "ABS", "ABS 경고등이 켜졌어요."),
        Info("RBS", "warning_rbs", "회생 제동", "회생 제동 경고등이 켜졌어요."),
        Info("MDPS", "warning_steering", "조향 보조", "조향 보조 경고등이 켜졌어요."),
        Info("ESC", "warning_esc", "자세 제어", "자세 제어 경고등이 켜졌어요."),
        Info("ESC_OFF", "warning_esc_off", "자세 제어 꺼짐", "자세 제어 꺼짐 표시등이 켜졌어요."),
        Info("ICY_ROAD", "warning_icy_road", "노면 결빙", "노면 결빙 경고등이 켜졌어요."),
        Info("OVER_SPEED", "warning_over_speed", "과속", "과속 경고등이 켜졌어요."),
        Info("PARKING_BRAKE", "warning_parking_brake", "주차 브레이크", "주차 브레이크 경고등이 켜졌어요."),
        Info("ELECTRONIC_PARKING_BRAKE", "warning_parking_brake_e", "전자 주차 브레이크", "전자 주차 브레이크 경고등이 켜졌어요."),

        // ---- 에너지·주유 ----
        Info("LOW_FUEL", "warning_low_fuel", "연료 부족", "연료 부족 경고등이 켜졌어요."),
        Info("LOW_FUEL_FCEV", "warning_low_h2", "수소 부족", "수소 부족 경고등이 켜졌어요."),
        Info("LOW_EV_BATTERY", "warning_low_battery", "배터리 부족", "배터리 부족 경고등이 켜졌어요."),
        Info("EV_CHARGING_CONNECTOR", "warning_charge_plug", "충전 커넥터", "충전 커넥터 경고등이 켜졌어요."),

        // ---- 정비하러 가면 되는 것 ----
        Info("ENGINE", "warning_engine_check", "엔진 점검", "엔진 점검 경고등이 켜졌어요."),
        Info("MASTER_WARNING", "warning_master", "통합 경고", "통합 경고등이 켜졌어요. 계기판에 메시지가 떴어요."),
        Info("PARTICLE_FILTER", "warning_dpf", "매연 필터", "매연 필터 경고등이 켜졌어요."),
        Info("WATER_SEPARATOR", "warning_water_sep", "연료 수분", "연료 수분 경고등이 켜졌어요."),
        Info("SCR", "warning_scr", "요소수", "요소수 경고등이 켜졌어요."),
        Info("H2_SENSOR", "warning_h2_sensor", "수소 감지 센서", "수소 감지 센서 경고등이 켜졌어요."),
        Info("IMMOBILIZER", "warning_key", "스마트키", "스마트키 경고등이 켜졌어요."),
        Info("EMERGENCY_CALL", "warning_ecall", "긴급 통화", "긴급 통화 장치 경고등이 켜졌어요."),
        Info("AFS", "warning_afs", "적응형 전조등", "적응형 전조등 경고등이 켜졌어요."),
        Info("LED_HEADLAMP", "warning_led_lamp", "LED 전조등", "LED 전조등 경고등이 켜졌어요."),
        Info("CHILD_PRESENCE_DETECTION_OFF", "warning_child_off", "뒷좌석 알림 꺼짐", "뒷좌석 승객 알림 꺼짐 표시등이 켜졌어요."),

        // ---- 운전 보조 장치 고장 (보조가 빠진 걸 알려 준다) ----
        Info("FCA", "warning_fca", "전방 충돌 방지", "전방 충돌 방지 보조 경고등이 켜졌어요."),
        Info("LKA", "warning_lka", "차선 유지 보조", "차선 유지 보조 경고등이 켜졌어요."),
        Info("LFA", "warning_lfa", "차선 따라가기", "차선 따라가기 경고등이 켜졌어요."),
        Info("ELK", "warning_elk", "차선 이탈 방지", "차선 이탈 방지 경고등이 켜졌어요."),
        Info("LCA_LEFT", "warning_lca_left", "왼쪽 후측방", "왼쪽 후측방 경고등이 켜졌어요."),
        Info("LCA_RIGHT", "warning_lca_right", "오른쪽 후측방", "오른쪽 후측방 경고등이 켜졌어요."),
    )

    val TABLE: Map<String, Info> = ALL.associateBy { it.type }

    /**
     * 켜져도 말하지 않는 표시등 — "켜졌다"가 경고가 아니거나(상태 표시) 다른 기능이 맡는다.
     * 표에 없으면 어차피 말하지 않으므로, 이 목록은 분류를 적어 두고 시험으로 지키려는 것이다.
     */
    val SILENT: Set<String> = setOf(
        // 조명·방향지시등 상태
        "HEADLAMP_LOW", "HEADLAMP_HIGH", "AUTO_HIGH_BEAM", "POSITION_LAMP", "FRONT_FOG_LAMP", "REAR_FOG_LAMP",
        "TURN_SIGNAL_LEFT", "TURN_SIGNAL_RIGHT",
        // 지금 켜져 있음을 알리는 표시등
        "PREHEAT", "PASSENGER_AIRBAG_OFF", "PASSENGER_AIRBAG_ON", "AUTO_HOLD", "XEV_READY", "ISG", "I_PEDAL",
        "GREEN_ZONE", "EV_BATTERY_CONDITIONING", "LPI", "ICC", "DBC", "REV", "AWD_LOCK",
        "FORCED_ALL_WHEEL_DRIVE", "TOW_MODE",
        // 드라이브 모드
        "DRIVE_MODE", "DRIVE_MODE_SPORT", "DRIVE_MODE_ECO", "DRIVE_MODE_COMFORT", "DRIVE_MODE_TERRAIN",
        "FOUR_WHEEL_DRIVE_MODE_2H", "FOUR_WHEEL_DRIVE_MODE_4A", "FOUR_WHEEL_DRIVE_MODE_4H", "FOUR_WHEEL_DRIVE_MODE_4L",
        "ESC_SPORT", "CREEP_OFF", "DRIFT_MODE", "TRACK_MODE", "UTILITY_MODE", "LAUNCH_MODE",
        "HEV_MODE", "PHEV_MODE", "PHEV_MODE_HEV", "PHEV_MODE_AUTO", "PHEV_MODE_EV_PLUS", "EREV_MODE",
        "ACCEL_RESPONSE_DASH", "ACCEL_RESPONSE_ACCEL", "N_BATTERY_DRAG", "N_BATTERY_SPRINT", "N_BATTERY_ENDURANCE",
        // CarCoach 가 쉼터 권유로 따로 맡는다
        "DRIVER_ATTENTION",
    )

    private val ORDER = compareByDescending<Info> { it.urgent }.thenBy { it.situation }

    /** 이 경고등을 말로 바꿀 수 있으면 설명, 아니면 null(말하지 않음). */
    fun info(type: String): Info? = TABLE[type]

    /** 켜진 것들 중 말로 바꿀 수 있는 것의 짧은 이름, 급한 것 먼저 (서버 AI 에 주는 차 요약). */
    fun labels(lit: Set<String>): List<String> = lit.mapNotNull { info(it) }.sortedWith(ORDER).map { it.label }

    /** 켜진 것들 중 가장 급한 하나의 설명 (봇·AI 에 주는 `car.warning` 의 `say`). */
    fun worst(lit: Set<String>): Info? = lit.mapNotNull { info(it) }.minWithOrNull(ORDER)

    /**
     * 같은 경고를 반복해서 말하지 않게 지켜보는 쪽.
     * - [refresh] 는 지금 켜진 목록을 반영하고 (**아직 말하지 않은 경고**, **방금 꺼진 경고**)를
     *   급한 것 먼저 돌려준다. 꺼진 경고는 잊으므로 다시 켜지면 또 알린다.
     * - 말한 것만 [mark] 로 적는다 — 출발 인사와 겹쳐 말하지 못한 경고는 다음 차례에 다시 나온다.
     * - [seed] 는 앱을 켠 첫 순간 이미 켜져 있던 경고를 "알린 것"으로 둔다(첫 상태에는 말하지 않는다).
     * - **꺼짐은 우리가 실제로 말한 경고만** 알린다. 앱을 켤 때부터 켜져 있던 것은 켜졌다고 말하지 않았으니
     *   꺼졌다고도 말하지 않는다.
     */
    class Tracker {
        private val said = mutableSetOf<String>()      // 다시 말하지 않을 것 (앱 시작 때 켜져 있던 것 + 말한 것)
        private val spoken = mutableSetOf<String>()    // 우리가 실제로 말한 것 — 꺼짐 보고 대상

        /** @return (아직 말하지 않은 경고, 방금 꺼진 경고) */
        fun refresh(lit: Set<String>): Pair<List<Info>, List<Info>> {
            val gone = spoken.filter { it !in lit }.mapNotNull { info(it) }.sortedWith(ORDER)
            spoken.removeAll(gone.map { it.type }.toSet())
            said.retainAll(lit)
            val fresh = lit.filter { it !in said }.mapNotNull { info(it) }.sortedWith(ORDER)
            return fresh to gone
        }

        fun mark(info: Info) {
            said += info.type
            spoken += info.type
        }

        fun seed(lit: Set<String>) {
            said.clear()
            spoken.clear()
            said += lit
        }
    }
}
