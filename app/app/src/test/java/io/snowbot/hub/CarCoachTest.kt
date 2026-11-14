package io.snowbot.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 먼저 하는 한마디의 **판단**. 문장·표정은 여기서 정하지 않는다([ExpressionTest]).
 * 겹치면 안전 → 권유 → 인사 차례로 하나만 고르는지 고정한다.
 */
class CarCoachTest {

    private fun snap(
        gear: String = "P", drivingMin: Int = 0, attention: Boolean = false,
        warnings: Set<String> = emptySet(), read: Boolean = true, cabin: Checkup.Cabin = Checkup.Cabin(),
    ) = CarState.Snapshot(gear, drivingMin, attention, null, null, CarState.Signals(gear = gear),
        warnings, read, cabin)

    /** 앱을 켠 뒤 상태를 한 번 먹여 "첫 상태"를 지난다 — 그 뒤부터 말을 한다. */
    private fun coach(start: CarState.Snapshot = snap()): CarCoach =
        CarCoach().also { it.update(start, talking = false, clockMin = 0) }

    private fun CarCoach.feed(s: CarState.Snapshot, talking: Boolean = false, min: Long = 0) =
        update(s, talking, min)

    // ---- 첫 상태 ----

    @Test fun theFirstSnapshotSaysNothing() =
        assertNull(CarCoach().feed(snap(warnings = setOf("TPMS"))))

    @Test fun lampsAlreadyOnAtStartupAreNotAnnounced() {
        val c = coach(snap(gear = "D", warnings = setOf("TPMS")))
        assertNull(c.feed(snap(gear = "D", drivingMin = 1, warnings = setOf("TPMS"))))
    }

    // ---- 인사 ----

    @Test fun departGreetingCarriesTheDepartChecks() {
        val j = coach().feed(snap(gear = "D", cabin = Checkup.Cabin(beltsOff = listOf("ROW_1_LEFT"))))!!
        assertEquals(Situation.DEPART, j.situation)
        assertEquals(Kind.GREETING, j.kind)
        assertEquals(listOf(Checkup.Belt(listOf("ROW_1_LEFT"))), j.items)
    }

    @Test fun arriveGreetingKnowsItWasALongDrive() {
        val c = coach()
        c.feed(snap(gear = "D"))
        val j = c.feed(snap(gear = "P", drivingMin = 90, cabin = Checkup.Cabin(windowsOpen = listOf("ROW_2_LEFT"))))!!
        assertEquals(Situation.ARRIVE, j.situation)
        assertEquals(true, j.facts["long"])
        assertEquals(listOf(Checkup.Window(listOf("ROW_2_LEFT"))), j.items)
    }

    @Test fun greetingsAreSkippedWhileTheDriverIsTalking() =
        assertNull(coach().feed(snap(gear = "D"), talking = true))

    // ---- 안전이 인사보다 먼저 ----

    @Test fun aLampOnTheMomentWeDepartComesBeforeTheGreeting() {
        val j = coach().feed(snap(gear = "D", warnings = setOf("TPMS")))!!
        assertEquals(Situation.WARNING_ON, j.situation)
        assertEquals("TPMS", j.warning?.type)
    }

    /** 출발 순간에 다른 말을 골라도 "이번 운행" 기록은 바뀐다 — 휴식 권유가 바로 터지지 않는다. */
    @Test fun theTripRecordIsKeptEvenWhenSomethingElseIsSaid() {
        val c = coach()
        c.feed(snap(gear = "D", warnings = setOf("TPMS")))              // 출발인데 경고등을 말했다
        assertEquals(Situation.LONG_DRIVE, c.feed(snap(gear = "D", drivingMin = 120, warnings = setOf("TPMS")))!!.situation)
    }

    @Test fun theMostUrgentLampIsPicked() {
        val j = coach().feed(snap(gear = "D", warnings = setOf("TPMS", "ENGINE_TEMPERATURE")))!!
        assertEquals("ENGINE_TEMPERATURE", j.warning?.type)
    }

    @Test fun attentionWarningOnlyEveryTenMinutes() {
        val c = coach(snap(gear = "D"))
        assertEquals(Situation.ATTENTION, c.feed(snap(gear = "D", attention = true), min = 20)!!.situation)
        c.feed(snap(gear = "D"), min = 21)
        assertNull("10분이 안 됐다", c.feed(snap(gear = "D", attention = true), min = 25))
        c.feed(snap(gear = "D"), min = 26)                     // 경고가 꺼졌다
        assertEquals(Situation.ATTENTION, c.feed(snap(gear = "D", attention = true), min = 31)!!.situation)
    }

    // ---- 회복 보고 ----

    @Test fun reportsTheLampGoingOff() {
        val c = coach()
        c.feed(snap(gear = "D", warnings = setOf("TPMS")))             // 켜졌다고 말했다
        val j = c.feed(snap(gear = "D"))!!
        assertEquals(Situation.WARNING_OFF, j.situation)
        assertEquals("타이어 공기압", j.warning?.label)
        assertNull("한 번만", c.feed(snap(gear = "D", drivingMin = 1)))
    }

    @Test fun aNewLampComesBeforeTheRecoveryReport() {
        val c = coach()
        c.feed(snap(gear = "D", warnings = setOf("TPMS")))
        val j = c.feed(snap(gear = "D", warnings = setOf("ABS")))!!
        assertEquals("안전이 먼저", Situation.WARNING_ON, j.situation)
        assertEquals(Situation.WARNING_OFF, c.feed(snap(gear = "D", warnings = setOf("ABS"), drivingMin = 1))!!.situation)
    }

    // ---- 오래 운전 ----

    @Test fun restAfterTwoHoursThenEveryHour() {
        val c = coach()
        c.feed(snap(gear = "D"))
        val j = c.feed(snap(gear = "D", drivingMin = 125))!!
        assertEquals(Situation.LONG_DRIVE, j.situation)
        assertEquals(2, j.num("hours"))
        assertEquals(5, j.num("mins"))
        assertNull(c.feed(snap(gear = "D", drivingMin = 150)))
        assertEquals(Situation.LONG_DRIVE, c.feed(snap(gear = "D", drivingMin = 185))!!.situation)
    }

    @Test fun safetyIsSaidEvenWhileTheDriverIsTalking() {
        val j = coach(snap(gear = "D")).feed(snap(gear = "D", warnings = setOf("TPMS")), talking = true)!!
        assertEquals(Situation.WARNING_ON, j.situation)
    }
}
