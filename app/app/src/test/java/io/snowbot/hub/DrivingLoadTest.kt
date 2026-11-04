package io.snowbot.hub

import io.snowbot.hub.DrivingLoad.BUSY
import io.snowbot.hub.DrivingLoad.CAREFUL
import io.snowbot.hub.DrivingLoad.NORMAL
import io.snowbot.hub.DrivingLoad.RELAXED
import io.snowbot.hub.DrivingLoad.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 말할 때 판단기: docs/pleos/07-dev-plan.md 1-1절 표의 조건마다 하나 이상. */
class DrivingLoadTest {

    private val d = Signals(gear = "D")

    @Test fun parkedIsRelaxed() = assertEquals(RELAXED, DrivingLoad.level(Signals(gear = "P", turnSignal = true, wiper = true)))
    @Test fun plainDrivingIsNormal() = assertEquals(NORMAL, DrivingLoad.level(d))
    @Test fun unknownGearIsNormal() = assertEquals(NORMAL, DrivingLoad.level(Signals()))
    @Test fun neutralIsNormal() = assertEquals(NORMAL, DrivingLoad.level(Signals(gear = "N")))

    @Test fun turnSignalIsBusy() = assertEquals(BUSY, DrivingLoad.level(d.copy(turnSignal = true)))
    @Test fun reverseIsBusy() = assertEquals(BUSY, DrivingLoad.level(Signals(gear = "R")))
    @Test fun bigSteerIsBusy() {
        assertEquals(BUSY, DrivingLoad.level(d.copy(steerDeg = 90f)))
        assertEquals(BUSY, DrivingLoad.level(d.copy(steerDeg = -120f)))
        assertEquals(NORMAL, DrivingLoad.level(d.copy(steerDeg = 45f)))
    }

    @Test fun wiperIsCareful() = assertEquals(CAREFUL, DrivingLoad.level(d.copy(wiper = true)))
    @Test fun headlightsAtNightIsCareful() {
        assertEquals(CAREFUL, DrivingLoad.level(d.copy(lowBeam = true, night = true)))
        assertEquals(NORMAL, DrivingLoad.level(d.copy(lowBeam = true, night = false)))   // 낮 터널 등
    }

    @Test fun cruiseIsRelaxedUnlessBusy() {
        assertEquals(RELAXED, DrivingLoad.level(d.copy(cruise = true)))
        assertEquals(BUSY, DrivingLoad.level(d.copy(cruise = true, turnSignal = true)))
        assertEquals(CAREFUL, DrivingLoad.level(d.copy(cruise = true, wiper = true)))   // 비 오면 크루즈여도 주의
    }

    @Test fun highestWins() = assertEquals(BUSY, DrivingLoad.level(d.copy(wiper = true, turnSignal = true)))

    @Test fun nightHours() {
        assertEquals(true, DrivingLoad.isNight(19))
        assertEquals(true, DrivingLoad.isNight(5))
        assertEquals(false, DrivingLoad.isNight(6))
        assertEquals(false, DrivingLoad.isNight(18))
    }

    // ---- 바로 올리고 5초 뒤 내린다 ----

    @Test fun raisesAtOnce() {
        val t = DrivingLoad.Tracker(holdMs = 5_000)
        assertEquals(BUSY, t.update(BUSY, 0))
    }

    @Test fun lowersOnlyAfterHold() {
        val t = DrivingLoad.Tracker(holdMs = 5_000)
        t.update(BUSY, 0)
        assertEquals(BUSY, t.update(NORMAL, 1_000))        // 깜빡이를 끈 순간
        assertEquals(4_000L, t.waitMs(2_000))
        assertEquals(BUSY, t.update(NORMAL, 5_999))
        assertEquals(NORMAL, t.update(NORMAL, 6_000))      // 끈 지 5초
        assertNull(t.waitMs(6_000))
    }

    @Test fun busyAgainDuringHoldRestartsWait() {
        val t = DrivingLoad.Tracker(holdMs = 5_000)
        t.update(BUSY, 0)
        t.update(NORMAL, 1_000)
        t.update(BUSY, 3_000)                              // 다시 차선 변경
        assertEquals(BUSY, t.update(NORMAL, 4_000))
        assertEquals(BUSY, t.update(NORMAL, 8_000))        // 처음 끈 때부터는 7초지만, 마지막으로 끈 때부터는 4초
        assertEquals(NORMAL, t.update(NORMAL, 9_000))
    }

    @Test fun lowersToLatestLevel() {
        val t = DrivingLoad.Tracker(holdMs = 5_000)
        t.update(BUSY, 0)
        t.update(CAREFUL, 1_000)                           // 깜빡이 끔, 비는 옴
        t.update(RELAXED, 3_000)                           // 주차
        assertEquals(RELAXED, t.update(RELAXED, 6_000))
    }
}
