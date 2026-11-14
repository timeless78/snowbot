package io.snowbot.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 대조 — 여러 출처를 맞춰 보는 판단. 조건이 **새로 성립한 순간만**, 풀렸다 다시 생겨야 또.
 * 모르는 값(null)이 든 조건은 아예 보지 않는다(내비 없는 차에서 조용해야 한다).
 */
class CarWatchTest {

    private val driving = CarWatch.Inputs(driving = true)

    private fun CarWatch.sits(i: CarWatch.Inputs) = update(i).map { it.situation }

    /** 멈춘 상태에서 한 번 불러 "출발"을 만든다 — 출발 때 기록이 비워지는 것까지 포함해 시험한다. */
    private fun started(): CarWatch = CarWatch().also { it.update(CarWatch.Inputs(driving = false)) }

    // ---- 주행 가능 거리 < 목적지 ----

    @Test fun rangeShorterThanWhatIsLeftToDrive() {
        val w = started()
        val i = driving.copy(rangeKm = 38, destKm = 52)
        val j = w.update(i).single { it.situation == Situation.RANGE_SHORT }
        assertEquals(38, j.num("range_km"))
        assertEquals(52, j.num("dest_km"))
        assertEquals("주유소를 권한다", Offer.FUEL, j.offer)
        assertEquals("한 번만", emptyList<Situation>(), w.sits(i))
    }

    @Test fun electricCarsAreOfferedChargers() {
        val j = started().update(driving.copy(rangeKm = 38, destKm = 52, ev = true))
            .single { it.situation == Situation.RANGE_SHORT }
        assertEquals(Offer.CHARGE, j.offer)
    }

    @Test fun aRangeBelowOneKilometreIsTreatedAsUnknown() {
        assertEquals("0 으로 \"목적지보다 짧다\"고 말하지 않는다", emptyList<Situation>(),
            started().sits(driving.copy(rangeKm = 0, destKm = 52)))
    }

    @Test fun anUnknownRangeNeverPassesAMilestone() {
        val w = started()
        w.update(driving.copy(rangeKm = 140))
        assertEquals(emptyList<Situation>(), w.sits(driving.copy(rangeKm = 0)))
    }

    @Test fun withoutTheNaviTheRangeConditionStaysQuiet() =
        assertEquals(emptyList<Situation>(), started().sits(driving.copy(rangeKm = 10)))

    @Test fun theConditionCanComeBackAfterItClears() {
        val w = started()
        assertTrue(Situation.RANGE_SHORT in w.sits(driving.copy(rangeKm = 38, destKm = 52)))
        assertEquals(emptyList<Situation>(), w.sits(driving.copy(rangeKm = 90, destKm = 52)))   // 주유했다
        assertTrue(Situation.RANGE_SHORT in w.sits(driving.copy(rangeKm = 30, destKm = 52)))
    }

    // ---- 도착 전 운전 시간 ----

    @Test fun drivingTimePlusWhatIsLeftPassesTheLimit() {
        val j = started().update(driving.copy(drivingMin = 80, destMin = 40))
            .single { it.situation == Situation.DRIVE_TIME_OVER }
        assertEquals(2, j.num("hours"))
        assertEquals(40, j.num("dest_min"))
        assertEquals(Offer.REST, j.offer)
    }

    @Test fun notYetTwoHoursTogether() =
        assertEquals(emptyList<Situation>(), started().sits(driving.copy(drivingMin = 60, destMin = 30)))

    // ---- 비·밤·문 ----

    @Test fun rainWithAnOpenWindow() =
        assertEquals(listOf(Situation.RAIN_WINDOW_OPEN), started().sits(driving.copy(rain = true, windowOpen = true)))

    @Test fun nightWithoutHeadlights() =
        assertEquals(listOf(Situation.NIGHT_NO_BEAM), started().sits(driving.copy(night = true, lowBeam = false)))

    @Test fun nightWithHeadlightsIsFine() =
        assertEquals(emptyList<Situation>(), started().sits(driving.copy(night = true, lowBeam = true)))

    @Test fun doorOpenWhileDriving() =
        assertEquals(listOf(Situation.DOOR_OPEN_DRIVING), started().sits(driving.copy(doorOpen = true)))

    @Test fun parkedCarsAreNotCrossChecked() =
        assertEquals("주차 중 창문·문은 하차 점검이 맡는다", emptyList<Situation>(),
            CarWatch().sits(CarWatch.Inputs(driving = false, rain = true, windowOpen = true, doorOpen = true)))

    // ---- 정체 ----

    @Test fun congestionAhead() {
        val j = started().update(driving.copy(congested = true, congestionKm = 3))
            .single { it.situation == Situation.ROUTE_CONGESTED }
        assertEquals(3, j.num("congestion_km"))
        assertEquals(Offer.REROUTE, j.offer)
    }

    // ---- 겹치면 안전 먼저 ----

    @Test fun safetyComesBeforeTips() {
        val out = started().update(driving.copy(doorOpen = true, rain = true, windowOpen = true,
            rangeKm = 10, destKm = 50))
        assertEquals(Kind.SAFETY, out.first().kind)
        assertEquals(Situation.DOOR_OPEN_DRIVING, out.first().situation)
    }

    // ---- 거리 이정표 ----

    @Test fun reportsCrossingOneHundredAndFiftyOncePerTrip() {
        val w = started()
        assertEquals("출발 때 첫 값은 알리지 않는다", emptyList<Situation>(), w.sits(driving.copy(rangeKm = 140)))
        val first = w.update(driving.copy(rangeKm = 95)).single { it.situation == Situation.RANGE_LOW }
        assertEquals(95, first.num("range_km"))
        assertEquals(100, first.num("mark_km"))
        assertEquals("같은 지점은 한 번만", emptyList<Situation>(), w.sits(driving.copy(rangeKm = 80)))
        assertEquals(50, w.update(driving.copy(rangeKm = 48)).single { it.situation == Situation.RANGE_LOW }.num("mark_km"))
    }

    @Test fun startingBelowAMilestoneSaysNothing() {
        val w = started()
        assertEquals("주행 가능 거리 40km 로 출발한 사람에게 100km 이야기는 안 한다",
            emptyList<Situation>(), w.sits(driving.copy(rangeKm = 40)))
        assertEquals(emptyList<Situation>(), w.sits(driving.copy(rangeKm = 35)))
    }

    @Test fun eachTripCountsItsOwnMilestones() {
        val w = started()
        w.update(driving.copy(rangeKm = 140))
        assertTrue(Situation.RANGE_LOW in w.sits(driving.copy(rangeKm = 95)))
        w.update(CarWatch.Inputs(driving = false, rangeKm = 95))         // 주차
        w.update(driving.copy(rangeKm = 140))                            // 다시 출발 (충전했다)
        assertTrue(Situation.RANGE_LOW in w.sits(driving.copy(rangeKm = 95)))
    }

    @Test fun crossingSeveralMarksAtOnceOnlyReportsTheLowest() {
        val w = started()
        w.update(driving.copy(rangeKm = 140))
        val out = w.update(driving.copy(rangeKm = 30))      // 100 과 50 을 한꺼번에 지났다
        assertEquals(1, out.size)
        assertEquals(50, out.single().num("mark_km"))
    }

    @Test fun milestonesOnlyCountWhileDriving() =
        assertEquals(emptyList<Situation>(), CarWatch().sits(CarWatch.Inputs(driving = false, rangeKm = 40)))
}
