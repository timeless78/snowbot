package io.snowbot.hub

/**
 * 차 상태가 바뀌는 순간 스노봇이 먼저 하는 한마디를 **판단한다**. 문장·표정은 [Expression] 이 정한다.
 * 서버·AI 없이 앱 안에서 정하므로 인터넷이 끊겨도 동작한다. 순수하다(JVM 단위 시험).
 *
 *   경고등 켜짐      : 쉬운 말 통역 — [CarWarnings]. 같은 경고는 꺼졌다 다시 켜질 때만
 *   경고등 꺼짐      : 회복 보고 ("…경고등 꺼졌어요"). 우리가 알린 경고만
 *   주의 경고 켜짐   : 쉼터 권유. 같은 경고는 [ATTENTION_GAP_MIN] 분에 한 번
 *   오래 운전        : [REST_AFTER_MIN] 분째에, 그 뒤 [REST_EVERY_MIN] 분마다 쉬자고
 *   출발(P → 주행)  : 인사 + 출발 점검(벨트 · 주행 가능 거리·연료) — [Checkup.depart]
 *   도착(주행 → P)  : 수고 인사(1시간 넘게 달렸으면 푹 쉬라고) + 하차 점검 — [Checkup.park]
 *
 * **겹치면 안전 → 권유 → 인사** 차례로 하나만 고른다([Judgement.kind]). 출발·도착을 세는 일은
 * 고르는 것과 따로 해 두므로, 출발 순간에 경고등을 말해도 "이번 운행" 기록은 제대로 바뀐다.
 *
 * 첫 상태(앱을 켠 순간)에는 말하지 않는다 — 이미 켜져 있던 경고등도 알린 것으로 둔다.
 * 말하는 중(듣기 중)이면 출발·도착 인사는 건너뛴다. 안전 알림은 그래도 한다.
 *
 * 출발·하차 점검은 인사와 **한 호흡으로 붙여** 말한다(차 스피커가 한 번만 말하게). 그래서 바쁜 순간에
 * 출발 인사를 거르면 벨트 알림도 같이 걸러진다 — 벨트는 차가 `SEAT_BELT` 경고등을 켜면 경고등 통역이 다시 알린다.
 */
class CarCoach {

    private var last: CarState.Snapshot? = null
    private var attentionAt = -ATTENTION_GAP_MIN.toLong()   // 분 단위 시계(elapsedRealtime / 60000)
    private var restAtMin = 0                  // 이번 운전에서 마지막으로 쉬자고 한 운전 분
    private val warnings = CarWarnings.Tracker()
    private val offQueue = ArrayDeque<CarWarnings.Info>()   // 꺼짐 보고 차례 — 안전을 먼저 말해도 잃지 않는다
    private var seeded = false
    private var pending: List<CarWarnings.Info> = emptyList()   // 아직 말하지 않은 경고 (마지막으로 읽은 목록)

    /**
     * @param talking 운전자와 말하는 중(듣기 중) — 인사만 건너뛴다
     * @param clockMin 분 단위 시계 (`elapsedRealtime / 60_000`)
     * @return 이번에 할 말 하나. 없으면 null
     */
    fun update(now: CarState.Snapshot, talking: Boolean, clockMin: Long): Judgement? {
        val before = last
        last = now
        // 앱을 켠 순간 이미 켜져 있던 경고등은 말하지 않는다. 경고등 목록은 기어보다 늦게 오므로
        // "처음 읽은 순간"을 기준으로 삼는다(기어 첫 값 때 하면 아직 빈 목록이라 소용이 없다).
        if (!seeded && now.warningsRead) {
            warnings.seed(now.warnings)
            seeded = true
        }
        if (before == null || before.gear == "?") return null

        // 이번 운행 기록은 고르기와 따로 — 출발 순간에 다른 말을 골라도 쉬는 시각은 다시 센다
        val departed = !before.driving && now.driving
        val arrived = before.driving && !now.driving && now.gear == "P"
        if (departed) restAtMin = 0

        if (seeded) {
            val (fresh, gone) = warnings.refresh(now.warnings)
            offQueue += gone
            pending = fresh
        }
        val fresh = pending

        // ---- 안전 ----
        fresh.firstOrNull()?.let { w ->       // 한 번에 여러 개면 가장 급한 것만 말한다
            warnings.mark(w)
            return Judgement(Situation.WARNING_ON, warning = w)
        }
        if (now.driving && now.attention && !before.attention && clockMin - attentionAt >= ATTENTION_GAP_MIN) {
            attentionAt = clockMin
            return Judgement(Situation.ATTENTION)
        }

        // ---- 권유 ----
        offQueue.removeFirstOrNull()?.let { return Judgement(Situation.WARNING_OFF, warning = it) }
        if (now.driving && now.drivingMin >= REST_AFTER_MIN && now.drivingMin - restAtMin >= REST_EVERY_MIN) {
            restAtMin = now.drivingMin
            return Judgement(Situation.LONG_DRIVE,
                facts = mapOf("hours" to now.drivingMin / 60, "mins" to now.drivingMin % 60))
        }

        // ---- 인사 ----
        if (talking) return null
        if (departed) return Judgement(Situation.DEPART, items = Checkup.depart(now.cabin))
        if (arrived) return Judgement(Situation.ARRIVE, items = Checkup.park(now.cabin),
            facts = mapOf("long" to (now.drivingMin >= LONG_TRIP_MIN)))
        return null
    }

    companion object {
        const val REST_AFTER_MIN = 120
        const val REST_EVERY_MIN = 60
        const val ATTENTION_GAP_MIN = 10

        /** 이만큼 달렸으면 도착 인사를 "푹 쉬어요"로 바꾼다. */
        const val LONG_TRIP_MIN = 60
    }
}
