package io.snowbot.hub

import android.content.Context

/**
 * 차 상태가 바뀌는 순간 스노봇이 먼저 하는 한마디. 서버·AI 없이 앱 안에서 정해 둔 말로 한다(인터넷이 끊겨도 동작).
 *   출발(P → 주행)  : 인사
 *   도착(주행 → P)  : 수고 인사 (1시간 넘게 달렸으면 푹 쉬라고)
 *   주의 경고 켜짐   : 걱정 + 쉼터 권유. 같은 경고는 ATTENTION_GAP_MIN 분에 한 번
 *   오래 운전        : REST_AFTER_MIN 분째에, 그 뒤 REST_EVERY_MIN 분마다 쉬자고
 * 첫 상태(앱을 켠 순간)에는 말하지 않는다. 말하는 중(듣기 중)이면 출발·도착 인사는 건너뛴다. 안전 알림은 그래도 한다.
 */
class CarCoach(private val context: Context, private val say: (expr: String, head: String?, line: String) -> Unit) {

    private var last: CarState.Snapshot? = null
    private var attentionAt = -ATTENTION_GAP_MIN.toLong()   // 분 단위 시계(elapsedRealtime / 60000)
    private var restAtMin = 0                  // 이번 운전에서 마지막으로 쉬자고 한 운전 분

    fun update(now: CarState.Snapshot, busy: Boolean, clockMin: Long) {
        val before = last
        last = now
        if (before == null || before.gear == "?") return
        when {
            !before.driving && now.driving -> {
                restAtMin = 0
                if (!busy) say("greet", "nod", pick(R.array.car_depart))
            }
            before.driving && !now.driving && now.gear == "P" -> {
                if (!busy) say("happy", "nod", pick(if (now.drivingMin >= 60) R.array.car_arrive_long else R.array.car_arrive))
            }
            now.driving && now.attention && !before.attention && clockMin - attentionAt >= ATTENTION_GAP_MIN -> {
                attentionAt = clockMin
                say("worried", null, pick(R.array.car_attention))
            }
            now.driving && now.drivingMin >= REST_AFTER_MIN && now.drivingMin - restAtMin >= REST_EVERY_MIN -> {
                restAtMin = now.drivingMin
                val hours = now.drivingMin / 60
                val mins = now.drivingMin % 60
                val time = if (mins == 0) context.getString(R.string.car_hours, hours)
                           else context.getString(R.string.car_hours_mins, hours, mins)
                say("sleepy", null, context.getString(R.string.car_rest, time))
            }
        }
    }

    private fun pick(array: Int) = context.resources.getStringArray(array).random()

    companion object {
        const val REST_AFTER_MIN = 120
        const val REST_EVERY_MIN = 60
        const val ATTENTION_GAP_MIN = 10
    }
}
