package io.snowbot.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 차 요약. **못 읽은 값은 만들지 않는다**는 것과, 보고가 짧게 급한 것부터 나오는지 고정한다.
 */
class CarReportTest {

    private fun items(
        gear: String = "D", drivingMin: Int = 10, warnings: Set<String> = emptySet(),
        cabin: Checkup.Cabin = Checkup.Cabin(), signals: CarState.Signals = CarState.Signals(gear = "D"),
        nav: NaviState.Nav = NaviState.Nav(), co2: Int? = null, humidity: Int? = null, online: Boolean = true,
    ) = CarReport.items(gear, drivingMin, warnings, cabin, signals, nav, co2, humidity, online)

    private fun List<CarReport.Item>.at(key: String) = single { it.key == key }

    // ---- 못 읽은 값은 만들지 않는다 ----

    @Test fun unknownValuesStayEmpty() {
        val list = items()
        assertNull("내비가 없으면 목적지는 빈칸", list.at("dest").value)
        assertNull("봇이 없으면 CO₂ 는 빈칸", list.at("co2").value)
        assertNull("공조를 못 읽으면 빈칸", list.at("hvac").value)
        assertEquals("빈칸은 말에 쓰지 않는다", "", list.at("co2").phrase)
    }

    @Test fun whatTheCarDoesTellUsShowsUp() {
        val list = items(
            warnings = setOf("TPMS"), cabin = Checkup.Cabin(rangeKm = 38, windowsOpen = listOf("ROW_1_LEFT")),
            signals = CarState.Signals(gear = "D", recirc = true, ac = false),
            nav = NaviState.Nav(destKm = 52, destMin = 40), co2 = 1500, humidity = 78,
        )
        assertEquals("타이어 공기압", list.at("warnings").value)
        assertEquals("38km", list.at("range").value)
        assertEquals("52km · 40분", list.at("dest").value)
        assertEquals("운전석 창문", list.at("window").value)
        assertEquals("내기 · 에어컨 꺼짐", list.at("hvac").value)
        assertEquals("1,500ppm", list.at("co2").value)
        assertEquals("78%", list.at("humidity").value)
    }

    @Test fun energyStandsInWhenTheCarHasNoRange() {
        assertEquals("12%", items(cabin = Checkup.Cabin(socPct = 12)).at("range").value)
        assertEquals("배터리", items(cabin = Checkup.Cabin(socPct = 12)).at("range").label)
        assertEquals("연료", items(cabin = Checkup.Cabin(fuelPct = 40)).at("range").label)
    }

    // ---- 정상 여부 ----

    @Test fun whatCountsAsAProblem() {
        assertFalse(items(warnings = setOf("TPMS")).at("warnings").ok)
        assertFalse(items(cabin = Checkup.Cabin(rangeKm = 30)).at("range").ok)
        assertTrue(items(cabin = Checkup.Cabin(rangeKm = 300)).at("range").ok)
        assertFalse(items(co2 = 1600).at("co2").ok)
        assertTrue(items(co2 = 800).at("co2").ok)
        assertFalse(items(drivingMin = 130).at("driving").ok)
        assertFalse(items(online = false).at("online").ok)
    }

    @Test fun unknownIsNotAProblem() {
        for (item in items()) assertTrue(item.key, item.ok)
    }

    // ---- 보고 ----

    @Test fun theReportLeadsWithTheProblems() {
        val said = CarReport.say(items(warnings = setOf("TPMS"), cabin = Checkup.Cabin(rangeKm = 30), co2 = 1600))
        assertTrue(said, said.startsWith("타이어 공기압 경고등이 켜져 있어요."))
        assertEquals("두 가지까지만", 2, said.split("요.").filter { it.isNotBlank() }.size)
    }

    @Test fun theReportSaysItIsFineWhenNothingIsWrong() {
        val said = CarReport.say(items(cabin = Checkup.Cabin(rangeKm = 300)))
        assertTrue(said, said.startsWith("지금은 다 괜찮아요."))
        assertTrue(said, said.contains("300km"))
    }

    @Test fun theReportNeverInventsAValue() {
        assertEquals("지금은 다 괜찮아요.", CarReport.say(items()))
    }

    // ---- "멀쩡한 게 뭐야" ----

    @Test fun intactPicksSomethingThatIsActuallyFine() {
        val list = items(warnings = setOf("TPMS", "ABS"), signals = CarState.Signals(gear = "D", ac = true))
        assertEquals("공조는 완전 멀쩡해요.", CarReport.intact(list))
    }

    @Test fun intactFallsBackToWhateverIsKnown() {
        val list = items(warnings = setOf("TPMS"), cabin = Checkup.Cabin(rangeKm = 300))
        assertEquals("창문은 완전 멀쩡해요.", CarReport.intact(list))
    }

    @Test fun intactSaysEverythingIsFineWhenItIs() =
        assertEquals("다 멀쩡해요.", CarReport.intact(items()))

    // ---- "얼마나 더 갈 수 있어" ----

    @Test fun howFarUsesWhatTheCarAndNaviKnow() {
        val said = CarReport.howFar(items(cabin = Checkup.Cabin(rangeKm = 38), nav = NaviState.Nav(destKm = 52, destMin = 40)))
        assertEquals("남은 거리 38킬로미터예요. 목적지까지 52km · 40분예요.", said)
    }

    @Test fun howFarAdmitsWhenItDoesNotKnow() =
        assertEquals("지금은 남은 거리를 못 봐요.", CarReport.howFar(items()))

    // ---- 백엔드에 보내는 요약 ----

    @Test fun theServerSummaryOnlyHasKnownValues() {
        val text = CarReport.forServer(items(warnings = setOf("TPMS"), cabin = Checkup.Cabin(rangeKm = 38)))
        assertTrue(text, text.contains("경고등 타이어 공기압"))
        assertTrue(text, text.contains("주행 가능 거리 38km"))
        assertFalse("모르는 값은 안 보낸다", text.contains("목적지까지"))
    }

    @Test fun particles() {
        assertEquals("은", CarReport.topic("창문"))
        assertEquals("는", CarReport.topic("공조"))
        assertEquals("는", CarReport.topic("CO₂"))
    }
}
