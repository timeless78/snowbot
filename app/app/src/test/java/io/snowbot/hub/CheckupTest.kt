package io.snowbot.hub

import io.snowbot.hub.Checkup.Cabin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 출발·하차 점검 (기획서 3장 "차 살피기 · 출발·하차 점검"). 확인할 수 없는 신호는 말하지 않는다. */
class CheckupTest {

    @Test fun nothingToSayWhenTheCarIsQuiet() {
        assertEquals(emptyList<Checkup.Item>(), Checkup.depart(Cabin(rangeKm = 300)))
        assertEquals(emptyList<Checkup.Item>(), Checkup.park(Cabin()))
    }

    @Test fun unknownSignalsAreNotInvented() {
        // 벨트·주행 가능 거리·연료를 못 읽는 차(값 null)에서는 아무 말도 만들지 않는다
        assertEquals(emptyList<Checkup.Item>(), Checkup.depart(Cabin()))
    }

    // ---- 출발 ----

    @Test fun beltTellsWhichSeat() {
        val items = Checkup.depart(Cabin(beltsOff = listOf("ROW_1_RIGHT")))
        assertEquals(listOf(Checkup.Belt(listOf("ROW_1_RIGHT"))), items)
        assertEquals("동승석", Checkup.seatName("ROW_1_RIGHT"))
    }

    @Test fun lowRangeIsWorthAWord() {
        assertEquals(listOf(Checkup.Range(40)), Checkup.depart(Cabin(rangeKm = 40)))
        assertEquals(emptyList<Checkup.Item>(), Checkup.depart(Cabin(rangeKm = 51)))
    }

    @Test fun fuelOnlyWhenRangeIsUnknown() {
        assertEquals(listOf(Checkup.Energy(9, ev = false)), Checkup.depart(Cabin(fuelPct = 9)))
        // 주행 가능 거리를 아는 차에서는 같은 말을 두 번 하지 않는다
        assertEquals(listOf(Checkup.Range(30)), Checkup.depart(Cabin(rangeKm = 30, fuelPct = 9)))
    }

    @Test fun evBatteryUsesItsOwnWording() =
        assertEquals(listOf(Checkup.Energy(12, ev = true)), Checkup.depart(Cabin(socPct = 12)))

    @Test fun departSaysOneThingOnly() {
        // 막 움직이기 시작한 운전자에게 길게 말하지 않는다 — 벨트가 주행 가능 거리보다 먼저다
        val items = Checkup.depart(Cabin(beltsOff = listOf("ROW_1_LEFT"), rangeKm = 20))
        assertEquals(listOf(Checkup.Belt(listOf("ROW_1_LEFT"))), items)
    }

    // ---- 하차 ----

    @Test fun openWindowAtParking() {
        assertEquals(listOf(Checkup.Window(listOf("ROW_2_LEFT"))), Checkup.park(Cabin(windowsOpen = listOf("ROW_2_LEFT"))))
        assertEquals("뒷좌석 왼쪽 창문", Checkup.windowName("ROW_2_LEFT"))
    }

    @Test fun openDoorAtParking() {
        assertEquals(listOf(Checkup.Door(listOf("REAR"))), Checkup.park(Cabin(doorsOpen = listOf("REAR"))))
        assertEquals("트렁크", Checkup.doorName("REAR"))
        assertEquals("운전석 문", Checkup.doorName("ROW_1_LEFT"))
    }

    @Test fun rearSeatGetsALightReminder() =
        assertEquals(listOf(Checkup.Rear), Checkup.park(Cabin(rearUsed = true)))

    @Test fun atMostTwoThingsInOneBreath() {
        val items = Checkup.park(Cabin(windowsOpen = listOf("ROW_1_LEFT"), doorsOpen = listOf("REAR"), rearUsed = true))
        assertEquals(2, items.size)
        assertEquals(Checkup.Window(listOf("ROW_1_LEFT")), items[0])
        assertEquals(Checkup.Door(listOf("REAR")), items[1])
    }

    @Test fun groupedAreaUsesTheFirstNameItKnows() =
        assertEquals("뒷좌석 왼쪽", Checkup.seatName("ROW_2_LEFT+ROW_2_RIGHT"))

    @Test fun unknownAreaStaysVagueInsteadOfGuessing() {
        assertEquals("자리", Checkup.seatName("WHAT_IS_THIS"))
        assertEquals("창문", Checkup.windowName("WHAT_IS_THIS"))
        assertEquals("문", Checkup.doorName("WHAT_IS_THIS"))
        assertEquals("선루프", Checkup.windowName("ROOF_TOP_1"))
    }

    @Test fun rearAreasAreRecognised() {
        assertTrue(Checkup.isRear("ROW_2_RIGHT"))
        assertTrue(Checkup.isRear("REAR"))
        assertFalse(Checkup.isRear("ROW_1_LEFT"))
    }

    // ---- 주행 가능 거리 1km 미만은 모름 ----

    @Test fun rangeBelowOneKilometreIsUnknown() {
        assertNull("0 은 값이 아직 안 올라온 것과 구별할 수 없다", Checkup.usableRange(0))
        assertNull(Checkup.usableRange(null))
        assertEquals(1, Checkup.usableRange(1))
        assertEquals(38, Checkup.usableRange(38))
    }

    @Test fun anUnknownRangeMakesNoDepartWord() {
        assertEquals(emptyList<Checkup.Item>(), Checkup.depart(Checkup.Cabin(rangeKm = Checkup.usableRange(0))))
    }
}
