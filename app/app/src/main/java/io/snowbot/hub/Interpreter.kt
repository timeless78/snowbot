package io.snowbot.hub

/**
 * 해석 (봇 커넥트 규약 5절): 차가 주는 정보 + 봇이 주는 정보 → 상황.
 * 안전과 바로 관련된 상황은 여기서 규칙으로, 인터넷 없이 정한다. 말투·대화는 서버의 AI 가 맡는다.
 *
 *   stuffy   답답함    봇 CO₂ ≥ 1,500ppm (1,200 아래로 내려가야 다시)             — 내기 순환이면 외기 전환 권유
 *   fog_risk 김서림 우려 봇 습도 ≥ 75% 그리고 (비: 와이퍼 · 밤) (습도 70 아래 또는 비·밤이 끝나야 다시)
 *
 * 같은 상황은 풀렸다가 다시 생겨야 또 알린다(update 가 새로 생긴 상황만 돌려준다).
 *
 * **언제 어떻게 말할지는 [Ladder] 가 정한다** (2026-11-08). 서비스는 이 두 상황에 사다리를 쓰고,
 * 여기서는 상황의 뜻(감정·몸짓)과 문턱값만 가져간다 — 문턱이 두 곳에서 갈리지 않게 [Ladder] 가 이 상수를 참조한다.
 * [update]·[isActive] 의 "새로 생긴 순간만 한 번" 방식은 사다리가 대신하므로 서비스에서는 더 쓰지 않는다.
 *
 * 2026-11-09: 감정·몸짓의 정본은 [Expression] 으로 옮겼다(판단과 표현을 나누면서). 여기 [Situation.emotion] 은
 * 규약 시험이 쓰는 참고 값으로만 남는다 — 새 상황을 더할 때는 [Expression] 에 더한다.
 */
class Interpreter {

    data class Inputs(
        val co2: Int? = null,         // 봇 센서 ppm
        val humidity: Int? = null,    // 봇 센서 %
        val rain: Boolean = false,    // 차: 와이퍼 작동
        val night: Boolean = false,   // 차: 밤 시간
        val recirc: Boolean? = null,  // 차: 내기 순환
    )

    enum class Situation(val emotion: String, val gesture: String?) {
        STUFFY("sleepy", null),
        FOG_RISK("worried", "nod"),
    }

    private val active = mutableSetOf<Situation>()

    fun update(i: Inputs): List<Situation> {
        val fresh = mutableListOf<Situation>()
        fun rule(s: Situation, on: Boolean, off: Boolean) {
            if (s !in active && on) { active += s; fresh += s }
            else if (s in active && off) active -= s
        }
        rule(Situation.STUFFY, on = (i.co2 ?: 0) >= CO2_ON, off = i.co2 != null && i.co2 < CO2_OFF)
        val wet = i.rain || i.night
        rule(Situation.FOG_RISK, on = (i.humidity ?: 0) >= HUMID_ON && wet,
            off = i.humidity == null || i.humidity < HUMID_OFF || !wet)
        return fresh
    }

    fun isActive(s: Situation) = s in active

    companion object {
        const val CO2_ON = 1500
        const val CO2_OFF = 1200
        const val HUMID_ON = 75
        const val HUMID_OFF = 70
    }
}
