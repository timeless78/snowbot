package io.snowbot.hub

import kotlin.math.abs

/**
 * 말할 때 판단기 (docs/pleos/07-dev-plan.md 1절): 차 신호로 "지금 운전이 얼마나 바쁜가"를 0~3단계로 정한다.
 *   3 바쁨 — 후진(R) · 방향지시등 켜짐 · 핸들을 STEER_BUSY_DEG 이상 돌림  → 말하던 것도 멈춘다
 *   2 주의 — 와이퍼 작동(비) · 밤에 전조등 켜짐                          → 한 문장
 *   1 보통 — 그 밖의 주행                                                  → 두 문장
 *   0 여유 — 주차(P) · 크루즈 작동 중이고 3단계 조건 없음                   → 세 문장
 * 조건이 겹치면 가장 높은 단계. 기어를 모르면 1(보통)로 본다 — 모르는 상태에서 길게 말하지 않게.
 *
 * 아직 안 쓰는 신호: 속도(Vehicle SDK 에 없음 — AAOS 차속은 확인 뒤), 내비 회전 안내(NaviHelper, 에뮬레이터 확인 뒤).
 * 브레이크는 속도 없이 "급제동"을 가려낼 수 없어 쓰지 않는다(막히는 길에서 늘 3단계가 된다).
 *
 * 숫자(90°, 5초, 밤 19~6시)는 시작 값이다. 시뮬레이션 주행으로 조정한다.
 */
object DrivingLoad {

    const val RELAXED = 0
    const val NORMAL = 1
    const val CAREFUL = 2
    const val BUSY = 3

    /** 핸들 각도(도). SDK 문서는 "조향각(도)", Virtual Console 범위는 -540~540° 라 핸들 각도로 본다. */
    const val STEER_BUSY_DEG = 90f
    const val HOLD_MS = 5_000L

    data class Signals(
        val gear: String = "?",           // P R N D ?
        val turnSignal: Boolean = false,  // 왼쪽·오른쪽 깜빡이
        val steerDeg: Float? = null,
        val wiper: Boolean = false,
        val lowBeam: Boolean = false,
        val cruise: Boolean = false,      // 크루즈(ICC)가 차를 몰고 있음 (ACTIVATED)
        val night: Boolean = false,       // 19시~6시
    )

    /** 지금 신호만 보고 낸 단계 (유지 시간은 [Tracker]). */
    fun level(s: Signals): Int {
        if (s.gear == "P") return RELAXED
        val busy = s.gear == "R" || s.turnSignal || (s.steerDeg?.let { abs(it) >= STEER_BUSY_DEG } ?: false)
        if (busy) return BUSY
        if (s.wiper || (s.lowBeam && s.night)) return CAREFUL
        if (s.cruise && s.gear == "D") return RELAXED
        return NORMAL
    }

    fun isNight(hour: Int) = hour >= 19 || hour < 6

    /**
     * 바로 올리고, 천천히 내린다: 단계가 높아지면 즉시 바꾸고, 낮아질 때는 낮은 상태가 [holdMs] 동안 이어져야 내린다.
     * 회전을 끝내자마자 말을 다시 시작하지 않게 한다. 시계는 밖에서 넣는다(시험에서 시간을 흉내 내려고).
     */
    class Tracker(private val holdMs: Long = HOLD_MS) {
        var shown = NORMAL
            private set
        private var lowerSince: Long? = null   // 지금 신호가 shown 보다 낮아진 순간

        /** 신호가 바뀌었거나 시간이 지났을 때 부른다. 바뀐 단계를 돌려준다. */
        fun update(raw: Int, nowMs: Long): Int {
            when {
                raw >= shown -> { shown = raw; lowerSince = null }
                lowerSince == null -> lowerSince = nowMs
                nowMs - lowerSince!! >= holdMs -> { shown = raw; lowerSince = null }
            }
            return shown
        }

        /** 내려갈 차례를 기다리는 중이면 남은 시간(ms), 아니면 null. 부르는 쪽이 그때 다시 update 한다. */
        fun waitMs(nowMs: Long): Long? = lowerSince?.let { maxOf(0L, holdMs - (nowMs - it)) }
    }
}
