package io.snowbot.hub

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 차량 앱 첫 화면 (방법 1 프로토타입): 중계 서버에 붙어 짝 맺은 스노봇에 표정 명령을 보내고,
 * 스노봇의 센서 값(CO₂)을 받아 보여 준다. 스노봇이 꺼져 있어도 화면 속 스노봇은 같은 표정을 짓는다.
 * 차량 데이터(CarState)로 출발·도착·주의 경고·오래 운전 때 먼저 말을 걸고(CarCoach), 차 상태를 서버에 알려
 * 대화가 주행 중에는 짧아지게 한다. 주행 중에는 시험용 버튼을 숨긴다.
 */
class MainActivity : AppCompatActivity(), RelayClient.Listener {

    private lateinit var snowbot: SnowBotView
    private lateinit var speech: TextView
    private lateinit var heard: TextView
    private lateinit var talkButton: Button
    private lateinit var status: TextView
    private lateinit var device: TextView
    private lateinit var air: TextView
    private lateinit var car: TextView
    private lateinit var testButtons: List<View>
    private lateinit var pairing: View
    private lateinit var actions: View

    private val prefs by lazy { getSharedPreferences("snowbot", Context.MODE_PRIVATE) }
    private val voice by lazy { VoicePlayer(this) }
    private val mic = MicRecorder { chunk -> relay?.sendBytes(chunk) }
    private val main = Handler(Looper.getMainLooper())
    private val talkTimeout = Runnable { endTalk() }
    private lateinit var voiceButton: Button
    private var relay: RelayClient? = null
    private var deviceId: String? = null
    private var deviceOnline = false
    private var airAlerted = false
    private val coach by lazy { CarCoach(this) { expr, head, line -> express(expr, head, line) } }
    private val carState by lazy { CarState(this, ::onCar) }
    private var carNow: CarState.Snapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        snowbot = findViewById(R.id.snowbot)
        speech = findViewById(R.id.speech)
        heard = findViewById(R.id.heard)
        talkButton = findViewById(R.id.btn_talk)
        talkButton.setOnClickListener { if (mic.isRecording) endTalk() else startTalk() }
        status = findViewById(R.id.status)
        device = findViewById(R.id.device)
        air = findViewById(R.id.air)
        car = findViewById(R.id.car)
        testButtons = listOf(R.id.btn_greet, R.id.btn_rest, R.id.btn_worry).map { findViewById(it) }
        pairing = findViewById(R.id.pairing)
        actions = findViewById(R.id.actions)

        findViewById<Button>(R.id.btn_greet).setOnClickListener { express("happy", "nod", R.string.line_greet) }
        findViewById<Button>(R.id.btn_rest).setOnClickListener { express("sleepy", "nod", R.string.line_rest) }
        findViewById<Button>(R.id.btn_worry).setOnClickListener { express("worried", "shake", R.string.line_worry) }
        findViewById<Button>(R.id.pair_button).setOnClickListener { pair() }
        findViewById<Button>(R.id.unpair_button).setOnClickListener { unpair() }
        voiceButton = findViewById(R.id.voice_button)
        voiceButton.setOnClickListener {
            prefs.edit().putBoolean(KEY_VOICE_CAR, !voiceInCar()).apply()
            renderVoice()
        }

        deviceId = prefs.getString(KEY_DEVICE, null)
        renderDevice()
        renderVoice()
        carState.start()
    }

    override fun onDestroy() {
        carState.stop()
        mic.stop()
        voice.release()
        super.onDestroy()
    }

    /** 목소리를 차 스피커로 낼지. 끄면 스노봇 스피커(스노봇이 꺼져 있으면 서버가 알아서 차 쪽으로 보낸다). */
    private fun voiceInCar() = prefs.getBoolean(KEY_VOICE_CAR, false)

    private fun renderVoice() = voiceButton.setText(if (voiceInCar()) R.string.voice_app else R.string.voice_device)

    override fun onStart() {
        super.onStart()
        connect()
    }

    override fun onStop() {
        if (mic.isRecording) endTalk()
        relay?.stop()
        relay = null
        super.onStop()
    }

    private fun connect() {
        val server = BuildConfig.SERVER_URL
        if (server.isBlank()) {
            setStatus(R.string.status_no_server, R.color.warn)
            return
        }
        val token = prefs.getString(KEY_TOKEN, null)
        pairing.visibility = if (token == null) View.VISIBLE else View.GONE
        if (token == null) return
        relay = RelayClient(server, this).also { it.start(token) }
    }

    /** 화면 속 스노봇은 항상, 실물 스노봇은 켜져 있을 때 같은 명령을 받는다. */
    private fun express(expr: String, head: String?, line: Int) = express(expr, head, getString(line))

    private fun express(expr: String, head: String?, say: String) {
        snowbot.setExpression(expr, head)
        speech.text = say
        val id = deviceId ?: return
        val msg = JSONObject().put("type", "express").put("device_id", id).put("expr", expr).put("say", say)
            .put("voice", if (voiceInCar()) "app" else "device")
        if (head != null) msg.put("head", head)
        relay?.send(msg)
    }

    /** 말하기: 하던 말을 멈추고 녹음을 시작한다. 서버가 듣는 대로 글자(heard)를 보내 준다. */
    private fun startTalk() {
        val id = deviceId ?: return
        val client = relay ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        voice.end(cancelled = true)
        client.send(talkMessage("listen_start", id))
        if (!mic.start()) {
            client.send(talkMessage("listen_end", id))
            heard.visibility = View.VISIBLE
            heard.setText(R.string.talk_no_mic)
            return
        }
        snowbot.setExpression("alert", null)
        heard.visibility = View.VISIBLE
        heard.setText(R.string.talk_listening)
        talkButton.setText(R.string.talk_stop)
        main.postDelayed(talkTimeout, MAX_TALK_MS)
    }

    private fun endTalk() {
        main.removeCallbacks(talkTimeout)
        if (!mic.isRecording) return
        mic.stop()
        deviceId?.let { relay?.send(talkMessage("listen_end", it)) }
        snowbot.setExpression("neutral", null)
        talkButton.setText(R.string.talk_start)
    }

    /** 차 상태가 바뀔 때: 화면 줄 갱신, 서버에 알림, 먼저 말할지 판단, 주행 중이면 시험 버튼 숨기기. */
    private fun onCar(snap: CarState.Snapshot) {
        carNow = snap
        val warn = getString(if (snap.attention) R.string.car_attention_on else R.string.car_attention_off)
        car.text = when {
            snap.error != null && snap.gear == "?" -> getString(R.string.car_failed, snap.error)
            snap.gear == "?" -> getString(R.string.car_unknown)
            snap.driving -> getString(R.string.car_driving, snap.gear, snap.drivingMin, warn)
            else -> getString(R.string.car_parked, warn)
        }
        testButtons.forEach { it.visibility = if (snap.driving) View.GONE else View.VISIBLE }
        sendCar()
        coach.update(snap, busy = mic.isRecording, clockMin = SystemClock.elapsedRealtime() / 60_000)
    }

    private fun sendCar() {
        val snap = carNow ?: return
        relay?.send(JSONObject().put("type", "car").put("gear", snap.gear).put("driving_min", snap.drivingMin)
            .put("attention", snap.attention))
    }

    /**
     * 시험용(디버그 빌드만). 맥 스튜디오처럼 마이크가 없는 개발 PC, 에뮬레이터에서 못 바꾸는 차 값을 대신한다.
     *   adb shell "am start -n io.snowbot.hub/.MainActivity --es chat '나 좀 졸려'"     글자로 말 걸기
     *   adb shell "am start -n io.snowbot.hub/.MainActivity --es car 'gear=D add=118 attention=1'"
     *     gear=P|R|N|D 기어, add=분 연속 운전 시간 더하기, attention=1|0 주의 경고등
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("car")?.let { pretendCar(it) }
        val text = intent.getStringExtra("chat")
        val id = deviceId
        if (text.isNullOrBlank() || id == null) return
        heard.visibility = View.VISIBLE
        heard.text = getString(R.string.talk_heard, text)
        relay?.send(talkMessage("chat", id).put("text", text))
    }

    private fun pretendCar(spec: String) {
        val kv = spec.split(' ', ',').mapNotNull { it.split('=').takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        carState.pretend(kv["gear"]?.uppercase(), kv["add"]?.toIntOrNull(), kv["attention"]?.let { it == "1" })
    }

    private fun talkMessage(type: String, id: String) =
        JSONObject().put("type", type).put("device_id", id).put("voice", if (voiceInCar()) "app" else "device")

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startTalk()
    }

    override fun onAudioStart(sampleRate: Int) = voice.start(sampleRate)
    override fun onAudio(chunk: ByteArray) = voice.write(chunk)
    override fun onAudioEnd(cancelled: Boolean) = voice.end(cancelled)

    override fun onState(state: RelayClient.State) {
        when (state) {
            RelayClient.State.CONNECTING -> setStatus(R.string.status_connecting, R.color.muted)
            RelayClient.State.CONNECTED -> setStatus(R.string.status_online, R.color.ok)
            RelayClient.State.DISCONNECTED -> {
                setStatus(R.string.status_offline, R.color.warn)
                deviceOnline = false
                renderDevice()
            }
            RelayClient.State.UNAUTHORIZED -> {
                setStatus(R.string.status_unauthorized, R.color.warn)
                prefs.edit().remove(KEY_TOKEN).apply()
                pairing.visibility = View.VISIBLE
            }
        }
    }

    override fun onMessage(msg: JSONObject) {
        when (msg.optString("type")) {
            "auth_ok" -> {
                val first = msg.optJSONArray("devices")?.optJSONObject(0)
                if (first != null) {
                    deviceId = first.optString("device_id")
                    deviceOnline = first.optBoolean("online")
                    prefs.edit().putString(KEY_DEVICE, deviceId).apply()
                }
                renderDevice()
                sendCar()   // 다시 붙으면 서버가 차 상태를 모르니 다시 알린다
            }
            "presence" -> if (msg.optString("device_id") == deviceId) {
                deviceOnline = msg.optBoolean("online")
                renderDevice()
            }
            "event" -> if (msg.optString("cap") == "env") onAir(msg.optJSONObject("data") ?: JSONObject())
            "heard" -> {
                val text = msg.optString("text")
                if (text.isNotEmpty()) heard.text = getString(R.string.talk_heard, text)
            }
            "reply_start" -> snowbot.setExpression(msg.optString("expr", "neutral"), if (msg.isNull("head")) null else msg.optString("head"))
            "reply" -> speech.text = msg.optString("text")
        }
    }

    /** CO₂ 가 1,500ppm 을 넘는 순간 한 번: 걱정 표정 + 환기 제안. 1,200 아래로 내려가면 다시 알릴 수 있다. */
    private fun onAir(data: JSONObject) {
        val co2 = data.optInt("co2", -1)
        if (co2 < 0) return
        air.text = getString(R.string.air_value, co2, data.optDouble("temp", Double.NaN))
        if (co2 >= CO2_ALERT && !airAlerted) {
            airAlerted = true
            express("worried", "nod", R.string.line_air)
        } else if (co2 < CO2_RESET) {
            airAlerted = false
        }
    }

    private fun pair() {
        val id = findViewById<EditText>(R.id.device_id).text.toString().trim().uppercase()
        val claim = findViewById<EditText>(R.id.claim_code).text.toString().trim()
        if (id.isEmpty() || claim.isEmpty()) return
        status.setText(R.string.pair_working)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                RelayClient.pair(BuildConfig.SERVER_URL, id, claim, getString(R.string.app_name))
            }
            result.onSuccess { token ->
                prefs.edit().putString(KEY_TOKEN, token).putString(KEY_DEVICE, id).apply()
                deviceId = id
                relay?.stop()
                connect()
            }.onFailure {
                status.text = getString(R.string.pair_failed, it.message ?: it.javaClass.simpleName)
            }
        }
    }

    private fun unpair() {
        relay?.stop()
        relay = null
        prefs.edit().clear().apply()
        deviceId = null
        deviceOnline = false
        renderDevice()
        connect()
    }

    private fun renderDevice() {
        val id = deviceId
        device.text = when {
            id == null -> getString(R.string.device_none)
            deviceOnline -> getString(R.string.device_on, id)
            else -> getString(R.string.device_off, id)
        }
    }

    private fun setStatus(text: Int, color: Int) {
        status.setText(text)
        status.setTextColor(ContextCompat.getColor(this, color))
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_VOICE_CAR = "voice_in_car"
        private const val CO2_ALERT = 1500
        private const val CO2_RESET = 1200
        private const val REQ_MIC = 1
        private const val MAX_TALK_MS = 12_000L   // 버튼을 안 눌러도 12초면 듣기를 끝낸다
    }
}
