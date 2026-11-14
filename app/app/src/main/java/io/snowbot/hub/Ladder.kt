package io.snowbot.hub

/**
 * 다가가기 사다리 — **편의 권유만** 얼마나 세게 전할지 고른다 (답답함·김서림). 판정·근거는 docs/pleos/13-ladder-review.md.
 *
 * 칸 네 개 = 전달 방식:
 *   0 그대로 · 1 표정만(말 없이) · 2 한마디 · 3 나빠졌으면 다른 말로 한 번 더(그 뒤 풀릴 때까지 조용)
 *
 * 경고등·운전자 주의 경고·휴식 권유·출발·도착 인사는 **사다리 밖**이다 — [CarCoach] 의 지금 경로 그대로 간다.
 * 무시하는 운전자에게 입을 닫으면 안 되기 때문이다(판정 4-1·4-2).
 *
 * - 내려오기(따랐는지 확인하는 고리): 한마디(2칸) 뒤에 운전자가 차를 만지고(외기 전환 · 창문 열림)
 *   **그 뒤 값이 실제로 내려가야** 안도([Cue.RELIEF])로 0칸이 된다. 행동만 있고 결과가 없으면 안도하지 않는다.
 *   상황이 저절로 풀리면 조용히([Cue.RELAX]) 0칸.
 * - 3칸: 2칸을 말한 지 [againAfterMs] 가 지났고, 심각도가 **말한 때보다** [worseMargin] 넘게 높을 때만.
 *   그대로이거나 나아졌으면 말하지 않는다. 한 번 하면 풀릴 때까지 조용하다.
 * - 반응 뒤 유예: 외기로 바꿔도 CO₂ 는 바로 안 떨어진다. [graceMs] 동안은 값이 그대로여도 다시 오르지 않는다.
 *
 * 시계는 밖에서 넣는다(시험에서 시간을 흉내 내려고). 신호가 바뀔 때와 센서 값이 올 때 부른다.
 * 부를 때마다 할 일을 하나 돌려준다(없으면 null).
 *
 * 숫자는 모두 **시작 값이고 실측으로 조정한다**. 판정 문서 3절.
 *
 * @param againAfterMs 2칸 뒤 3칸까지 기다리는 시간
 * @param graceMs 반응한 뒤 다시 오르지 않는 시간
 * @param resultDrop "값이 실제로 내려갔다"고 볼 낙폭. 센서 흔들림보다 커야 한다
 * @param worseMargin "말한 때보다 나빠졌다"고 볼 여유 폭. 센서 흔들림만으로 3칸에 가지 않게
 */
class Ladder(
    private val againAfterMs: Long = AGAIN_AFTER_MS,
    private val graceMs: Long = GRACE_MS,
    private val resultDrop: Int = 0,
    private val worseMargin: Int = 0,
) {

    /**
     * 운전자 반응을 읽는 데 쓰는 차 신호. 모두 [CarState.Snapshot] 에 있다.
     * 답답함은 외기 전환·창문, 김서림은 여기에 에어컨·앞유리 성에 제거까지 — 봇이 "에어컨 켜 볼까요?"라고 권하므로
     * 켠 것을 읽어야 따른 운전자를 "무시했다"로 보지 않는다. 어느 신호든 바뀐 순간만 반응으로 센다([touched]).
     */
    data class Car(
        val recirc: Boolean? = null,
        val windowOpen: Boolean = false,
        val ac: Boolean? = null,
        val defrost: Boolean? = null,
    ) {
        companion object {
            fun of(s: CarState.Snapshot) =
                Car(s.signals.recirc, s.cabin.windowsOpen.isNotEmpty(), s.signals.ac, s.signals.defrost)
        }
    }

    /** 이번에 봇이 할 일. 표정·문장은 고르지 않는다 — 상황을 아는 쪽(서비스)의 몫. */
    enum class Cue { FACE, SAY, SAY_AGAIN, RELIEF, RELAX }

    var rung = NONE
        private set
    private var saidAt = 0L        // 실제로 말한 순간 (2칸·3칸 모두)
    private var saidSeverity = 0   // 그때의 심각도. 3칸은 이것보다 나빠야 가고, 안도는 이것보다 좋아져야 한다

    /** 지난번 말했을 때의 값. "1,500에서 1,700까지 올랐어요" 처럼 **변화**를 말할 때 쓴다. */
    var from = 0
        private set
    private var acted = false      // 말한 뒤 운전자가 차를 만졌다
    private var graceUntil = 0L
    private var car: Car? = null

    /**
     * @param wanted 상황이 원하는 칸([NONE]·[FACE]·[SAY]). [NONE] 이면 풀림,
     *   null 이면 판단 보류(센서 값 없음 · 켜고 끄는 값 사이) — 오르지도 풀리지도 않는다
     * @param severity 지금 상황의 심각도. 클수록 나쁘고, 한 사다리 안에서는 같은 단위로 넣는다(CO₂ ppm · 습도 %)
     * @return 이번에 할 일. 없으면 null
     */
    fun update(wanted: Int?, car: Car, nowMs: Long, severity: Int = wanted ?: NONE): Cue? {
        if (rung >= SAY && touched(this.car, car)) acted = true
        this.car = car
        if (wanted == NONE) {
            val cue = when {
                rung == NONE -> null
                acted -> Cue.RELIEF   // 운전자가 만지고 → 값이 풀렸다
                else -> Cue.RELAX     // 저절로 풀렸다
            }
            reset(0L)
            return cue
        }
        // 따랐는지 확인하는 고리: 만진 것만으로는 안 되고, 말한 때보다 값이 실제로 내려가야 안도한다.
        if (acted && severity <= saidSeverity - resultDrop) {
            reset(nowMs + graceMs)
            return Cue.RELIEF
        }
        if (wanted == null || nowMs < graceUntil) return null
        val next = reachable(wanted, severity, nowMs)
        if (next <= rung) return null
        rung = next
        if (next >= SAY) {   // 말한 때를 새로 적는다 — 안도·3칸은 "마지막으로 말한 때"와 견준다
            from = saidSeverity
            saidAt = nowMs
            saidSeverity = severity
        }
        return when (next) {
            FACE -> Cue.FACE
            SAY -> Cue.SAY
            else -> Cue.SAY_AGAIN
        }
    }

    /** CO₂ 값으로 부른다(답답함). 칸은 [airWanted] 로 고르고, 심각도는 ppm 그대로 비교한다. */
    fun updateAir(co2: Int?, car: Car, nowMs: Long): Cue? =
        update(airWanted(co2), car, nowMs, severity = co2 ?: NONE)

    /** 습도와 비·밤으로 부른다(김서림). 심각도는 % 그대로 비교한다. */
    fun updateFog(humidity: Int?, wet: Boolean, car: Car, nowMs: Long): Cue? =
        update(fogWanted(humidity, wet), car, nowMs, severity = humidity ?: NONE)

    private fun reset(graceTo: Long) {
        rung = NONE
        acted = false
        graceUntil = graceTo
    }

    /** 3칸은 2칸을 말한 뒤에, 시간이 지나고 그때보다 나빠져야만 갈 수 있다. 처음 읽은 값이 아무리 높아도 2칸부터. */
    private fun reachable(wanted: Int, severity: Int, nowMs: Long): Int = when {
        wanted < SAY -> wanted
        rung == SAY && nowMs - saidAt >= againAfterMs && severity > saidSeverity + worseMargin -> AGAIN
        else -> SAY
    }

    /**
     * 운전자가 차를 만졌다. 값이 **바뀐** 순간만 반응이다 — 처음 온 값이나 null → false 는 운전자가 한 일이 아니다.
     * 한마디(2칸) 뒤부터 센다. 말하지 않았으면 운전자가 봇 때문에 움직였다고 볼 근거가 없다(판정 4-3).
     *
     * 차는 누가 바꿨는지 말해 주지 않는다. 자동 내기 전환이 차 스스로 외기로 바꾸면 반응으로 읽는다 —
     * 다만 결과(값이 내려감)까지 봐야 안도하므로 헛짚어도 공기는 실제로 좋아진 상태다.
     */
    private fun touched(before: Car?, now: Car): Boolean {
        if (before == null) return false
        val toFresh = before.recirc == true && now.recirc == false
        val opened = !before.windowOpen && now.windowOpen
        val acOn = before.ac == false && now.ac == true
        val defrostOn = before.defrost == false && now.defrost == true
        return toFresh || opened || acOn || defrostOn
    }

    companion object {
        const val NONE = 0
        const val FACE = 1
        const val SAY = 2
        const val AGAIN = 3

        /** 시작 값, 실측으로 조정. 10분은 `CarCoach.ATTENTION_GAP_MIN` 에서 빌려 왔다 — 판정 3절("짧을 수 있다"). */
        const val AGAIN_AFTER_MS = 10 * 60_000L

        /** 시작 값, 실측으로 조정. 외기로 바꾼 뒤 CO₂ 가 내려오는 데 걸리는 시간을 재서 정한다. */
        const val GRACE_MS = 10 * 60_000L

        /** 규약의 감정 어휘에 "안도"가 없어 가장 가까운 것을 쓴다. 모든 봇이 표현해야 하는 감정이다(규약 6-1). */
        const val RELIEF_EMOTION = "happy"

        // ---- 답답함 (CO₂ ppm) ----
        /** 켜고 끄는 값을 떨어뜨려 둔다: 1,200 하나로 켜고 끄면 값이 흔들릴 때 표정도 같이 흔들린다. */
        const val AIR_CLEAR = Interpreter.CO2_OFF   // 1,200 — 이 아래로 내려가야 풀림. 뜻을 해석기와 하나로 맞춤
        const val AIR_FACE = 1350                   // 시작 값, 실측으로 조정. 1,200~1,349 는 판단 보류
        const val AIR_SAY = Interpreter.CO2_ON      // 1,500 — 유일하게 약한 근거가 있는 값(판정 3절)

        /** 시작 값, 실측으로 조정. SCD40 오차는 1,500ppm 에서 ±125 라 그보다 커야 흔들림에 속지 않는다. */
        const val AIR_MARGIN = 150

        // ---- 김서림 (습도 %) ----
        const val FOG_CLEAR = Interpreter.HUMID_OFF   // 70 — 해석기와 같은 값
        const val FOG_FACE = 73                       // 시작 값, 실측으로 조정. 70~72 는 판단 보류
        const val FOG_SAY = Interpreter.HUMID_ON      // 75 — 해석기와 같은 값

        /** 시작 값, 실측으로 조정. 습도 센서 오차를 재서 정한다. */
        const val FOG_MARGIN = 3

        fun air() = Ladder(resultDrop = AIR_MARGIN, worseMargin = AIR_MARGIN)

        fun fog() = Ladder(resultDrop = FOG_MARGIN, worseMargin = FOG_MARGIN)

        /** CO₂ → 답답함이 원하는 칸. 내기 순환 여부는 칸이 아니라 문장을 고를 때 쓴다(해석기와 같다). */
        fun airWanted(co2: Int?): Int? = when {
            co2 == null -> null
            co2 < AIR_CLEAR -> NONE
            co2 < AIR_FACE -> null
            co2 < AIR_SAY -> FACE
            else -> SAY
        }

        /** 습도 + 비·밤 → 김서림이 원하는 칸. 비도 밤도 아니면 상황 자체가 아니다(해석기와 같다). */
        fun fogWanted(humidity: Int?, wet: Boolean): Int? = when {
            !wet -> NONE
            humidity == null -> null
            humidity < FOG_CLEAR -> NONE
            humidity < FOG_FACE -> null
            humidity < FOG_SAY -> FACE
            else -> SAY
        }
    }
}
