package io.snowbot.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 경고등 통역 (docs/pleos/03-module-protocol.md 4-1·5절, 기획서 3장 "차 살피기 · 경고등 통역"). */
class CarWarningsTest {

    /** 시험을 읽기 쉽게: 아직 말하지 않은 경고만. */
    private fun CarWarnings.Tracker.fresh(lit: Set<String>) = refresh(lit).first


    /** Pleos Virtual Console 의 Telltales 카드가 켤 수 있는 37종 — 시연에서 실제로 켜 볼 수 있는 것들. */
    private val vcTypes = listOf(
        "PARKING_BRAKE", "ELECTRONIC_PARKING_BRAKE", "SEAT_BELT", "DOOR_OPEN", "ABS", "MDPS", "TPMS",
        "LOW_VOLT_BATTERY", "ESC", "ESC_OFF", "AIRBAG", "AFS", "MASTER_WARNING", "AUTO_HOLD", "AUTO_HIGH_BEAM",
        "HEADLAMP_LOW", "HEADLAMP_HIGH", "POSITION_LAMP", "FRONT_FOG_LAMP", "REAR_FOG_LAMP",
        "ENGINE_OIL_PRESSURE", "ENGINE_TEMPERATURE", "EMERGENCY_CALL", "EV_SYSTEM", "POWER_DOWN", "RBS",
        "IMMOBILIZER", "ENGINE", "LED_HEADLAMP", "PREHEAT", "PARTICLE_FILTER", "H2_SENSOR", "H2_LEAK",
        "PASSENGER_AIRBAG_OFF", "PASSENGER_AIRBAG_ON", "TURN_SIGNAL_LEFT", "TURN_SIGNAL_RIGHT",
    )

    @Test fun tireWarningIsPlainKorean() {
        val info = CarWarnings.info("TPMS")
        assertNotNull(info)
        assertEquals("warning_tire", info!!.situation)
        assertTrue(info.line.contains("타이어"))
    }

    @Test fun everyVirtualConsoleTypeIsClassified() {
        val unknown = vcTypes.filter { CarWarnings.info(it) == null && it !in CarWarnings.SILENT }
        assertEquals(emptyList<String>(), unknown)
    }

    @Test fun statusLampsStaySilent() {
        // 전조등·깜빡이·오토홀드는 "켜졌다"가 경고가 아니다 — 말하지 않는다
        for (t in listOf("HEADLAMP_LOW", "TURN_SIGNAL_LEFT", "AUTO_HOLD", "POSITION_LAMP", "PREHEAT")) {
            assertNull(t, CarWarnings.info(t))
            assertTrue(t, t in CarWarnings.SILENT)
        }
    }

    @Test fun driverAttentionIsLeftToTheCoach() {
        // 운전자 주의 경고는 CarCoach 가 따로 쉼터 권유를 한다 — 경고등 통역이 겹쳐 말하지 않게
        assertNull(CarWarnings.info("DRIVER_ATTENTION"))
        assertTrue("DRIVER_ATTENTION" in CarWarnings.SILENT)
    }

    @Test fun linesOnlySayWhichLampIsOn() {
        // 11/8 규칙: 어디로 가라·무엇을 하라는 말은 하지 않는다 (고속도로인지·목적지가 어딘지 앱이 모른다).
        // 예외는 어디서든 조치가 같은 바로 위험한 넷 — 세우라는 말만 붙는다.
        val stopNow = setOf("ENGINE_OIL_PRESSURE", "ENGINE_TEMPERATURE", "POWER_DOWN", "H2_LEAK")
        for ((type, info) in CarWarnings.TABLE) {
            assertTrue("$type: ${info.line}", info.line.contains("켜졌어요."))
            for (place in listOf("휴게소", "정비소", "주유소", "충전소", "줄여", "가요")) {
                assertTrue("$type 이 조치를 말한다: ${info.line}", place !in info.line)
            }
            assertEquals("$type: ${info.line}", type in stopNow, info.line.contains("안전한 곳에"))
        }
        assertEquals("타이어 공기압 경고등이 켜졌어요.", CarWarnings.info("TPMS")!!.line)
    }

    @Test fun labelsForTheAiComeUrgentFirst() {
        assertEquals(listOf("엔진 과열", "타이어 공기압"),
            CarWarnings.labels(setOf("TPMS", "HEADLAMP_LOW", "ENGINE_TEMPERATURE")))
        assertEquals(emptyList<String>(), CarWarnings.labels(emptySet()))
    }

    @Test fun unknownTypeIsNotSpoken() = assertNull(CarWarnings.info("SOMETHING_NEW"))

    @Test fun everyLineEndsAsOneSpokenSentence() {
        for ((type, info) in CarWarnings.TABLE) {
            assertTrue("$type 설명이 비었다", info.line.length >= 8)
            assertTrue("$type 설명이 너무 길다(${info.line.length}자)", info.line.length <= 60)
            assertTrue("$type 상황 이름", info.situation.startsWith("warning_"))
        }
    }

    // ---- 같은 경고를 반복해서 말하지 않기 ----

    @Test fun saysOnceUntilTheLampGoesOff() {
        val t = CarWarnings.Tracker()
        val fresh = t.fresh(setOf("TPMS"))
        assertEquals(listOf("warning_tire"), fresh.map { it.situation })
        t.mark(fresh.first())
        assertEquals(emptyList<CarWarnings.Info>(), t.fresh(setOf("TPMS")))   // 아직 켜져 있음 — 다시 말하지 않는다
        t.fresh(emptySet())                                                   // 꺼짐
        assertEquals(1, t.fresh(setOf("TPMS")).size)                          // 다시 켜지면 또 알린다
    }

    @Test fun unspokenWarningStaysPending() {
        // 출발 인사와 겹쳐 말하지 못한 경고는 다음 차례에 다시 나온다 (mark 를 부르지 않았으므로)
        val t = CarWarnings.Tracker()
        assertEquals(1, t.fresh(setOf("TPMS")).size)
        assertEquals(1, t.fresh(setOf("TPMS")).size)
    }

    @Test fun urgentWarningComesFirst() {
        val t = CarWarnings.Tracker()
        val fresh = t.fresh(setOf("TPMS", "ENGINE_TEMPERATURE"))
        assertEquals("warning_engine_temp", fresh.first().situation)   // 엔진 과열이 타이어보다 급하다
        assertTrue(fresh.first().urgent)
        assertEquals(2, fresh.size)
    }

    @Test fun silentLampsNeverBecomePending() {
        val t = CarWarnings.Tracker()
        assertEquals(emptyList<CarWarnings.Info>(), t.fresh(setOf("HEADLAMP_LOW", "TURN_SIGNAL_LEFT")))
    }

    @Test fun seedingTheFirstSnapshotSaysNothing() {
        val t = CarWarnings.Tracker()
        t.seed(setOf("TPMS"))
        assertEquals(emptyList<CarWarnings.Info>(), t.fresh(setOf("TPMS")))
    }

    @Test fun reportsTheLampGoingOffOnlyIfWeSaidItWasOn() {
        val t = CarWarnings.Tracker()
        val fresh = t.fresh(setOf("TPMS"))
        assertEquals(emptyList<CarWarnings.Info>(), t.refresh(setOf("TPMS")).second)   // 말하기 전엔 꺼짐도 없다
        t.mark(fresh.first())
        val gone = t.refresh(emptySet()).second
        assertEquals(listOf("warning_tire"), gone.map { it.situation })
        assertEquals("두 번 알리지 않는다", emptyList<CarWarnings.Info>(), t.refresh(emptySet()).second)
    }

    @Test fun lampsOnAtStartupNeverReportGoingOff() {
        // 앱을 켤 때부터 켜져 있던 것은 켜졌다고 말하지 않았으니 꺼졌다고도 말하지 않는다
        val t = CarWarnings.Tracker()
        t.seed(setOf("TPMS"))
        assertEquals(emptyList<CarWarnings.Info>(), t.refresh(emptySet()).second)
    }

    @Test fun worstPicksTheMostUrgentLineForTheBots() {
        assertEquals("warning_engine_temp", CarWarnings.worst(setOf("TPMS", "ENGINE_TEMPERATURE"))?.situation)
        assertNull(CarWarnings.worst(setOf("HEADLAMP_LOW")))
    }
}
