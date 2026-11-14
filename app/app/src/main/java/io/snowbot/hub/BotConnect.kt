package io.snowbot.hub

import org.json.JSONArray
import org.json.JSONObject

/**
 * 봇 커넥트 규약(BCP) v0.3 의 허브 쪽 — docs/pleos/03-module-protocol.md
 *   봇 설명(hello) 읽기 · 허락(welcome) · 차가 주는 정보(context) 주제 만들기 · 봇 몸에 맞는 표정(express).
 * 화면·네트워크와 상관없는 순수 함수만 둔다(JVM 단위 시험).
 */
object BotConnect {

    const val VERSION = "0.3"

    /** 허브가 기본으로 허락하는 차 정보. driver.state(운전자 상태)는 운전자가 따로 허락해야 한다(규약 7절). */
    val DEFAULT_GRANT = setOf("car.motion", "car.outside", "car.cabin", "car.energy", "car.warning", "trip")
    val ALL_TOPICS = DEFAULT_GRANT + "driver.state"

    /** 봇에 없는 감정이 오면 가까운 것으로 (규약 6-1). */
    private val FALLBACK = mapOf("greet" to "happy", "sleepy" to "neutral", "guard" to "neutral")
    private val V01_HEADS = mapOf("turn_left" to "look_left", "turn_right" to "look_right")
    private val TO_V01_HEAD = V01_HEADS.entries.associate { it.value to it.key }

    data class Bot(
        val id: String,
        val name: String,
        val model: String,
        val online: Boolean,
        val emotions: List<String>,
        val gestures: List<String>,
        val parts: List<String>,        // 몸 부위 종류: eyelid brow neck light speaker microphone …
        val sensors: List<String>,      // 측정값 이름: co2 temp humidity …
        val wants: List<String>,
        val voice: String?,             // hub_stream · bot_tts · chime · null(스피커 없음)
        val version: String,            // "0.3" · "0.1" · "?"(아직 hello 없음)
        val look: LookShape.Look? = null,   // 모습(규약 3-5) — 화면 입자가 모일 외곽선. 없으면 빛 방울 그대로
    ) {
        val granted: Set<String> get() = wants.filter { it in DEFAULT_GRANT }.toSet()
        val denied: Set<String> get() = wants.filter { it !in DEFAULT_GRANT }.toSet()
    }

    /** hello(v0.3 · v0.1) 또는 아직 모르는 봇. */
    fun parse(id: String, hello: JSONObject?, online: Boolean): Bot {
        if (hello == null) return Bot(id, id, "", online, listOf("neutral"), emptyList(), emptyList(), emptyList(), emptyList(), null, "?")
        if (hello.has("expressions") || hello.has("parts")) {
            val bot = hello.optJSONObject("bot") ?: JSONObject()
            val ex = hello.optJSONObject("expressions") ?: JSONObject()
            val parts = objects(hello.optJSONArray("parts"))
            val voice = parts.firstOrNull { it.optString("kind") == "speaker" }?.optString("voice")?.ifEmpty { null }
            val sensors = objects(hello.optJSONArray("sensors"))
                .flatMap { s -> objects(s.optJSONArray("measures")).map { it.optString("name") } + strings(s.optJSONArray("results")) }
            return Bot(
                id, bot.optString("name").ifEmpty { bot.optString("model", id) }, bot.optString("model"), online,
                strings(ex.optJSONArray("emotions")).ifEmpty { listOf("neutral") }, strings(ex.optJSONArray("gestures")),
                parts.map { it.optString("kind") }.filter { it.isNotEmpty() }, sensors,
                strings(hello.optJSONArray("wants")), voice, hello.optString("bcp", VERSION),
                LookShape.parse(hello.optJSONObject("look")),
            )
        }
        // v0.1: capabilities [{id:express, expr, head}, {id:speaker}, {id:env, sensors}]
        val caps = objects(hello.optJSONArray("capabilities"))
        val express = caps.firstOrNull { it.optString("id") == "express" }
        val parts = buildList {
            if (express?.optBoolean("eyelid") == true) add("eyelid")
            if (express?.optBoolean("brow") == true) add("brow")
            if (express?.optJSONArray("head") != null) add("neck")
            if (caps.any { it.optString("id") == "speaker" }) add("speaker")
            if (caps.any { it.optString("id") == "mic" }) add("microphone")
        }
        val model = hello.optString("model", id)
        return Bot(
            id, if (model.startsWith("SnowBot")) "스노봇" else model, model, online,
            strings(express?.optJSONArray("expr")).ifEmpty { listOf("neutral") },
            strings(express?.optJSONArray("head")).map { V01_HEADS[it] ?: it },
            parts, caps.filter { it.optString("id") == "env" }.flatMap { strings(it.optJSONArray("sensors")) },
            // v0.1 봇은 원하는 정보를 말하지 않는다 — 판단에 쓰는 기본 주제를 준다
            listOf("car.motion", "car.outside", "trip"), if ("speaker" in parts) "hub_stream" else null, "0.1",
        )
    }

    fun welcome(bot: Bot, driving: Boolean): JSONObject = JSONObject()
        .put("bcp", VERSION).put("type", "welcome").put("device_id", bot.id).put("hub", "SnowBot Connect")
        .put("granted", JSONArray(bot.granted.sorted())).put("denied", JSONArray(bot.denied.sorted()))
        .put("mode", if (driving) "drive" else "park")

    /** 봇 하나에 보낼 표정. 봇에 없는 감정은 가까운 것으로, 없는 몸짓은 뺀다. v0.1 봇이 읽는 expr·head 도 같이. */
    fun express(bot: Bot, emotion: String, gesture: String?, intensity: Double = 0.6, reason: String? = null): JSONObject {
        val e = when {
            emotion in bot.emotions -> emotion
            FALLBACK[emotion] in bot.emotions -> FALLBACK.getValue(emotion)
            else -> "neutral"
        }
        val g = gesture?.let { V01_HEADS[it] ?: it }?.takeIf { it in bot.gestures }
        val msg = JSONObject().put("bcp", VERSION).put("type", "express").put("device_id", bot.id)
            .put("emotion", e).put("intensity", intensity).put("expr", e)
        if (g != null) msg.put("gesture", g).put("head", TO_V01_HEAD[g] ?: g)
        if (reason != null) msg.put("reason", reason)
        return msg
    }

    /** 차가 주는 정보 주제 (규약 4-1). 값이 바뀐 주제만 보내도록 부르는 쪽이 지난 값과 비교한다. */
    fun topics(car: CarState.Snapshot): Map<String, Map<String, Any?>> {
        val s = car.signals
        val c = car.cabin
        val worst = CarWarnings.worst(car.warnings)
        return mapOf(
            "car.motion" to mapOf("gear" to car.gear, "driving_min" to car.drivingMin),
            "car.outside" to mapOf("rain" to s.wiper, "night" to s.night),
            "car.cabin" to mapOf("recirc" to s.recirc, "window_open" to c.windowsOpen, "door_open" to c.doorsOpen,
                "belt_off" to c.beltsOff),
            // 경고등: 켜진 것 전부(SDK 이름) + 가장 급한 것의 쉬운 말. 말로 바꿀 수 없는 표시등뿐이면 설명은 비운다
            "car.warning" to mapOf("lit" to car.warnings.sorted(), "situation" to worst?.situation,
                "label" to worst?.label, "say" to worst?.line, "urgent" to worst?.urgent),
            "car.energy" to mapOf("range_km" to c.rangeKm, "fuel" to c.fuelPct, "soc" to c.socPct),
            "driver.state" to mapOf("attention" to car.attention),
        )
    }

    fun context(botId: String, topic: String, data: Map<String, Any?>): JSONObject {
        val d = JSONObject()
        data.forEach { (k, v) -> d.put(k, if (v is List<*>) JSONArray(v) else v ?: JSONObject.NULL) }
        return JSONObject().put("bcp", VERSION).put("type", "context").put("device_id", botId).put("topic", topic).put("data", d)
    }

    /** 화면에 보일 한 줄: 이름 · 켜짐 · 몸 · 센서 · 받는 정보. */
    fun describe(bot: Bot, labels: Map<String, String>): String {
        val body = bot.parts.distinct().joinToString("·") { labels[it] ?: it }.ifEmpty { "—" }
        val sense = bot.sensors.distinct().joinToString("·") { labels[it] ?: it }.ifEmpty { "—" }
        val gets = bot.granted.sorted().joinToString("·") { labels[it] ?: it }.ifEmpty { "—" }
        return "${if (bot.online) "●" else "○"} ${bot.name}  몸 $body · 센서 $sense\n   받는 정보: $gets"
    }

    private fun strings(a: JSONArray?): List<String> = a?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList()
    private fun objects(a: JSONArray?): List<JSONObject> = a?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } } ?: emptyList()
}
