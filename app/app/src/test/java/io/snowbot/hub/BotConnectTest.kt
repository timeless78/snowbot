package io.snowbot.hub

import io.snowbot.hub.Interpreter.Situation.FOG_RISK
import io.snowbot.hub.Interpreter.Situation.STUFFY
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 봇 커넥트 규약 v0.3 허브 쪽 (docs/pleos/03-module-protocol.md 3·4·6·7절) + 해석 규칙(5절). */
class BotConnectTest {

    private val snow = JSONObject("""{
      "bcp":"0.3","type":"hello",
      "bot":{"id":"SNW-01-A1B2","model":"SnowBot SNW-01","name":"스노봇"},
      "parts":[{"id":"eyelid","kind":"eyelid"},{"id":"brow","kind":"brow"},{"id":"head","kind":"neck"},
               {"id":"spk","kind":"speaker","voice":"hub_stream"}],
      "sensors":[{"id":"env","measures":[{"name":"co2","unit":"ppm"},{"name":"temp","unit":"C"}]}],
      "expressions":{"emotions":["neutral","happy","greet","worried","sleepy","alert","guard"],"gestures":["nod","shake","look_left","look_right"]},
      "wants":["car.motion","car.busy","car.cabin","driver.state","trip"]}""")   // car.busy 는 없앤 주제 — 모르는 주제를 달라고 하면 거부한다

    private val cube = JSONObject("""{
      "bcp":"0.3","type":"hello","bot":{"id":"LED-01-0001","model":"LED Cube (sim)","name":"큐브"},
      "parts":[{"id":"ring","kind":"light"},{"id":"buzz","kind":"speaker","voice":"chime"}],
      "sensors":[{"id":"env","measures":[{"name":"humidity","unit":"%"},{"name":"temp","unit":"C"}]}],
      "expressions":{"emotions":["neutral","happy","worried","alert","sleepy"],"gestures":[]},
      "wants":["car.motion","car.cabin"]}""")

    private val v01 = JSONObject("""{"type":"hello","model":"SnowBot SNW-01 (sim)",
      "capabilities":[{"id":"express","expr":["neutral","happy","worried"],"head":["nod","turn_left"],"brow":true,"eyelid":true},
                      {"id":"speaker"},{"id":"env","sensors":["temp","co2"]}]}""")

    @Test fun readsBodyAndSensors() {
        val b = BotConnect.parse("SNW-01-A1B2", snow, true)
        assertEquals("스노봇", b.name)
        assertEquals(listOf("eyelid", "brow", "neck", "speaker"), b.parts)
        assertEquals(listOf("co2", "temp"), b.sensors)
        assertEquals("hub_stream", b.voice)
        assertEquals("0.3", b.version)
    }

    @Test fun driverStateNeedsTheDriversPermission() {
        val b = BotConnect.parse("x", snow, true)
        assertFalse("driver.state" in b.granted)
        assertTrue("driver.state" in b.denied)
        val w = BotConnect.welcome(b, driving = true)
        assertEquals("welcome", w.getString("type"))
        assertEquals("drive", w.getString("mode"))
        assertEquals(listOf("car.cabin", "car.motion", "trip"), (0 until w.getJSONArray("granted").length()).map { w.getJSONArray("granted").getString(it) })
    }

    @Test fun readsTheOldHelloToo() {
        val b = BotConnect.parse("SIM", v01, true)
        assertEquals(listOf("nod", "look_left"), b.gestures)
        assertEquals(listOf("eyelid", "brow", "neck", "speaker"), b.parts)
        assertEquals("0.1", b.version)
    }

    @Test fun sameFeelingDifferentBodies() {
        val s = BotConnect.express(BotConnect.parse("a", snow, true), "greet", "look_left")
        assertEquals("greet", s.getString("emotion"))
        assertEquals("look_left", s.getString("gesture"))
        assertEquals("turn_left", s.getString("head"))      // v0.1 봇이 읽는 이름
        val c = BotConnect.express(BotConnect.parse("b", cube, true), "greet", "look_left")
        assertEquals("happy", c.getString("emotion"))        // 큐브엔 greet 이 없다 → happy
        assertFalse(c.has("gesture"))                        // 고개가 없다
        val g = BotConnect.express(BotConnect.parse("b", cube, true), "guard", null)
        assertEquals("neutral", g.getString("emotion"))
    }

    @Test fun carTopicsFromTheSnapshot() {
        val snap = CarState.Snapshot("D", 95, false, null, null,
            signals = CarState.Signals(gear = "D", wiper = true, night = true, recirc = true))
        val t = BotConnect.topics(snap)
        assertEquals(mapOf("gear" to "D", "driving_min" to 95), t["car.motion"])
        assertEquals("바쁜 정도 주제는 없앴다", null, t["car.busy"])
        assertEquals(mapOf("rain" to true, "night" to true), t["car.outside"])
        assertEquals(mapOf("recirc" to true, "window_open" to emptyList<String>(), "door_open" to emptyList<String>(),
            "belt_off" to emptyList<String>()), t["car.cabin"])
        val msg = BotConnect.context("b", "car.cabin", t.getValue("car.cabin"))
        assertEquals("context", msg.getString("type"))
        assertTrue(msg.getJSONObject("data").getBoolean("recirc"))
    }

    @Test fun warningTopicCarriesTheLampsAndThePlainWords() {
        val snap = CarState.Snapshot("D", 5, false, null, null,
            warnings = setOf("TPMS", "HEADLAMP_LOW"), signals = CarState.Signals(gear = "D"))
        val data = BotConnect.topics(snap).getValue("car.warning")
        assertEquals(listOf("HEADLAMP_LOW", "TPMS"), data["lit"])
        assertEquals("warning_tire", data["situation"])
        assertEquals("타이어 공기압", data["label"])
        assertEquals(false, data["urgent"])
        assertTrue((data["say"] as String).contains("타이어"))
        // 말로 바꿀 수 없는 표시등만 켜져 있으면 설명은 비운다 (봇이 멋대로 말하지 않게)
        val lampOnly = BotConnect.topics(snap.copy(warnings = setOf("HEADLAMP_LOW"))).getValue("car.warning")
        assertEquals(null, lampOnly["say"])
        assertEquals(listOf("HEADLAMP_LOW"), lampOnly["lit"])
    }

    @Test fun energyTopicOnlyHasWhatTheCarKnows() {
        val snap = CarState.Snapshot("D", 5, false, null, null,
            cabin = Checkup.Cabin(rangeKm = 42, socPct = 11), signals = CarState.Signals(gear = "D"))
        assertEquals(mapOf("range_km" to 42, "fuel" to null, "soc" to 11), BotConnect.topics(snap)["car.energy"])
    }

    @Test fun listsBecomeJsonArrays() {
        val snap = CarState.Snapshot("P", 0, false, null, null,
            cabin = Checkup.Cabin(windowsOpen = listOf("ROW_1_LEFT"), doorsOpen = listOf("REAR")),
            warnings = setOf("TPMS"), signals = CarState.Signals(gear = "P"))
        val t = BotConnect.topics(snap)
        val cabin = BotConnect.context("b", "car.cabin", t.getValue("car.cabin")).getJSONObject("data")
        assertEquals("ROW_1_LEFT", cabin.getJSONArray("window_open").getString(0))
        assertEquals("REAR", cabin.getJSONArray("door_open").getString(0))
        val warn = BotConnect.context("b", "car.warning", t.getValue("car.warning")).getJSONObject("data")
        assertEquals("TPMS", warn.getJSONArray("lit").getString(0))
        val energy = BotConnect.context("b", "car.energy", t.getValue("car.energy")).getJSONObject("data")
        assertTrue(energy.isNull("range_km"))
    }

    // ---- 해석 ----

    @Test fun stuffyOnceUntilAirClearsUp() {
        val i = Interpreter()
        assertEquals(listOf(STUFFY), i.update(Interpreter.Inputs(co2 = 1600)))
        assertEquals(emptyList<Interpreter.Situation>(), i.update(Interpreter.Inputs(co2 = 1700)))   // 이미 알림
        i.update(Interpreter.Inputs(co2 = 1300))                                                 // 아직 1,200 위
        assertTrue(i.isActive(STUFFY))
        i.update(Interpreter.Inputs(co2 = 1100))
        assertEquals(listOf(STUFFY), i.update(Interpreter.Inputs(co2 = 1550)))                     // 풀렸다가 다시
    }

    @Test fun fogNeedsBotHumidityAndCarRainOrNight() {
        val i = Interpreter()
        assertEquals(emptyList<Interpreter.Situation>(), i.update(Interpreter.Inputs(humidity = 80)))           // 봇 정보만
        assertEquals(emptyList<Interpreter.Situation>(), i.update(Interpreter.Inputs(humidity = 60, rain = true))) // 차 정보만
        assertEquals(listOf(FOG_RISK), i.update(Interpreter.Inputs(humidity = 80, rain = true)))               // 둘 다
        i.update(Interpreter.Inputs(humidity = 72, rain = true))
        assertTrue(i.isActive(FOG_RISK))                                                                       // 70 위면 유지
        i.update(Interpreter.Inputs(humidity = 72, rain = false))
        assertFalse(i.isActive(FOG_RISK))                                                                      // 비 그침
        assertEquals(listOf(FOG_RISK), i.update(Interpreter.Inputs(humidity = 76, night = true)))
    }
}
