package io.snowbot.hub

/**
 * 대조 — 여러 출처(차 신호 · 내비 · 봇 센서)를 **맞춰 보고** 혼자서는 안 보이는 것을 찾는다.
 * 순수하다(JVM 단위 시험). 문장·표정은 [Expression] 이 정한다.
 *
 *   주행 가능 거리 < 목적지까지 남은 거리   → 주유소·충전소를 찾아볼지 (제안 대기)
 *   운전 시간 + 남은 시간 ≥ [OVER_MIN]      → 쉬어 갈지 (제안 대기)
 *   비(와이퍼) + 창문 열림                  → 창문이 열려 있다고
 *   밤 + 주행 + 전조등 꺼짐                 → 안전
 *   주행 중 문 열림                         → 안전
 *   앞 경로 정체                            → 다른 길을 볼지 (제안 대기)
 *   주행 가능 거리가 [MILESTONES] 아래로 처음 내려감 → 한 번씩 보고
 *
 * 규칙
 * - **조건이 새로 성립한 순간만** 알린다. 풀렸다가 다시 생겨야 또 알린다(같은 말을 되풀이하지 않게).
 * - 모르는 값(null)이 들어간 조건은 아예 보지 않는다 — 내비 없는 차에서는 내비 조건이 조용하다.
 * - 멈춰 있을 때는 대조하지 않는다. 주차 중 창문·문은 하차 점검([Checkup.park])이 맡는다.
 * - 거리 이정표는 **운행마다** 새로 센다. 출발 때 이미 그 아래면 알리지 않는다("내려갈 때"만).
 *
 * 숫자는 모두 **시작 값이고 실측·시연으로 조정한다**(`Ladder` 주석과 같은 태도).
 */
class CarWatch {

    /**
     * 대조에 쓰는 지금 값. 못 읽은 값은 null·false 로 둔다.
     *
     * @param rangeKm 차가 말하는 주행 가능 거리 · @param ev 전기차인지(잔량으로 짐작) — 주유소/충전소를 가른다
     * @param destKm 내비 목적지까지 남은 거리 · @param destMin 남은 시간(분)
     * @param congestionKm 앞 정체 구간 길이(km)
     */
    data class Inputs(
        val driving: Boolean = false,
        val drivingMin: Int = 0,
        val rangeKm: Int? = null,
        val ev: Boolean? = null,
        val destKm: Int? = null,
        val destMin: Int? = null,
        val windowOpen: Boolean = false,
        val doorOpen: Boolean = false,
        val rain: Boolean = false,
        val night: Boolean = false,
        val lowBeam: Boolean = false,
        val congested: Boolean = false,
        val congestionKm: Int? = null,
    )

    private val on = mutableSetOf<Situation>()      // 지금 성립해 있는 조건
    private val passed = mutableSetOf<Int>()        // 이번 운행에서 지난 거리 이정표
    private var seeded = false                      // 이번 운행의 첫 거리 값을 받았다
    private var wasDriving = false

    /** 값이 바뀔 때마다 부른다. 이번에 **새로 성립한** 것만 급한 것 먼저 돌려준다. */
    fun update(i: Inputs): List<Judgement> {
        if (i.driving && !wasDriving) newTrip()
        wasDriving = i.driving
        val now = linkedMapOf<Situation, Judgement>()
        if (i.driving) {
            if (i.doorOpen) now[Situation.DOOR_OPEN_DRIVING] = Judgement(Situation.DOOR_OPEN_DRIVING)
            if (i.night && !i.lowBeam) now[Situation.NIGHT_NO_BEAM] = Judgement(Situation.NIGHT_NO_BEAM)
            if (i.rain && i.windowOpen) now[Situation.RAIN_WINDOW_OPEN] = Judgement(Situation.RAIN_WINDOW_OPEN)
            // 주행 가능 거리는 1km 미만이면 모름이다 — 0 으로 "목적지보다 짧다"고 말하지 않는다
            val range = Checkup.usableRange(i.rangeKm)
            if (range != null && i.destKm != null && i.destKm > range) {
                now[Situation.RANGE_SHORT] = Judgement(
                    Situation.RANGE_SHORT,
                    facts = mapOf("range_km" to range, "dest_km" to i.destKm),
                    offer = if (i.ev == true) Offer.CHARGE else Offer.FUEL,
                )
            }
            if (i.destMin != null && i.drivingMin + i.destMin >= OVER_MIN) {
                now[Situation.DRIVE_TIME_OVER] = Judgement(
                    Situation.DRIVE_TIME_OVER,
                    facts = mapOf("hours" to (i.drivingMin + i.destMin) / 60, "dest_min" to i.destMin),
                    offer = Offer.REST,
                )
            }
            if (i.congested) {
                now[Situation.ROUTE_CONGESTED] = Judgement(
                    Situation.ROUTE_CONGESTED,
                    facts = buildMap { i.congestionKm?.let { put("congestion_km", it) } },
                    offer = Offer.REROUTE,
                )
            }
        }
        val fresh = now.filterKeys { it !in on }.values.toList()
        on.clear()
        on += now.keys
        return (fresh + milestone(i)).sortedBy { it.kind.ordinal }
    }

    /** 운행이 시작됐다: 성립해 있던 조건과 거리 이정표를 모두 잊는다. */
    private fun newTrip() {
        on.clear()
        passed.clear()
        seeded = false
    }

    /**
     * 주행 가능 거리가 이정표 아래로 **내려가는** 순간 한 번. 출발 때 이미 아래면 알리지 않는다
     * (주행 가능 거리 40km 로 출발하는 사람에게 "100km 아래예요"는 말이 안 된다).
     */
    private fun milestone(i: Inputs): List<Judgement> {
        val km = Checkup.usableRange(i.rangeKm) ?: return emptyList()
        if (!i.driving) return emptyList()
        if (!seeded) {
            seeded = true
            passed += MILESTONES.filter { km < it }
            return emptyList()
        }
        val hit = MILESTONES.filter { km < it && it !in passed }
        passed += hit
        // 값이 한꺼번에 많이 떨어져 여러 지점을 지났으면 **가장 낮은 것만** 알린다 (같은 이야기를 두 번 하지 않게)
        val lowest = hit.minOrNull() ?: return emptyList()
        return listOf(Judgement(Situation.RANGE_LOW, facts = mapOf("range_km" to km, "mark_km" to lowest)))
    }

    companion object {
        /** 도착 전에 연속 운전이 이 분을 넘으면 쉬어 가자고 한다. [CarCoach.REST_AFTER_MIN] 과 같은 값. */
        const val OVER_MIN = CarCoach.REST_AFTER_MIN

        /** 주행 가능 거리 보고 지점(km). 시작 값. */
        val MILESTONES = listOf(100, 50)
    }
}
