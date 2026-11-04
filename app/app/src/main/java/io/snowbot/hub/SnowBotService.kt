package io.snowbot.hub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 스노봇의 몸통: 앱 화면을 닫아도 계속 도는 포그라운드 서비스.
 * 차 상태(CarState)를 보고 먼저 말을 걸고(CarCoach), 서버에 붙어 있고(RelayClient), 차 목소리로 말한다(PleosVoice).
 * 화면(MainActivity)은 이 서비스에 붙어 [Ui] 상태를 그리기만 한다.
 *
 * 말할 때 판단기(DrivingLoad): 운전 부담이 3(바쁨)이 되는 순간
 *   - 하던 말을 끊는다(차 목소리·서버 목소리 둘 다), 서버에도 알려 남은 문장을 버리게 한다(car 메시지의 load).
 *   - 표정은 정면(neutral)으로 둔다.
 *   - 3단계 동안 오는 출발·도착 인사는 버리고, 안전 알림(주의 경고·휴식)은 3단계가 끝나면 바로 한다.
 *     3단계가 SAFETY_WAIT_MS 넘게 이어지면 안전 알림은 기다리지 않고 한다.
 *   - 3단계가 끝나도 끊었던 말을 다시 하지 않는다. 운전자가 다시 물으면 그때 대답한다.
 *
 * 서버에 연결되지 않았으면 먼저 하는 말(인사·안전 알림)은 차 목소리로 바로 읽는다 — 인터넷 없이도 동작.
 */
class SnowBotService : Service(), RelayClient.Listener {

    /** 화면이 그릴 것 전부. faceSeq 는 표정 명령이 새로 왔을 때만 올라간다(고개 움직임을 다시 그리지 않게). */
    data class Ui(
        val status: String = "",
        val statusColor: Int = R.color.muted,
        val device: String = "",
        val air: String? = null,
        val car: String = "",
        val speech: String? = null,
        val heard: String? = null,          // null 이면 숨김
        val expr: String = "neutral",
        val head: String? = null,
        val faceSeq: Int = 0,
        val talking: Boolean = false,
        val pairing: Boolean = false,
        val driving: Boolean = false,
        val voiceInCar: Boolean = false,
    )

    inner class LocalBinder : Binder() {
        val service: SnowBotService get() = this@SnowBotService
    }

    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by lazy { getSharedPreferences("snowbot", Context.MODE_PRIVATE) }
    private val voice by lazy { VoicePlayer(this) }
    private val pleos by lazy { PleosVoice(this, ::onHeard) }
    private val mic = MicRecorder { chunk -> relay?.sendBytes(chunk) }
    private val coach by lazy { CarCoach(this, ::coachSays) }
    private val carState by lazy { CarState(this, ::onCar) }
    private val listeners = mutableSetOf<(Ui) -> Unit>()
    private val talkTimeout = Runnable { endTalk() }
    private val safetyTimeout = Runnable { flushSafety() }

    var ui = Ui()
        private set
    private var relay: RelayClient? = null
    private var connected = false
    private var deviceId: String? = null
    private var deviceOnline = false
    private var airAlerted = false
    private var carNow: CarState.Snapshot? = null
    private var carSent: List<Any>? = null
    private var pendingSafety: Triple<String, String?, String>? = null
    @Volatile private var busy = false   // 운전 부담 3 — 네트워크 스레드(목소리 조각)에서도 읽는다
    private var started = false

    // ---- 서비스 수명 ----

    override fun onCreate() {
        super.onCreate()
        deviceId = prefs.getString(KEY_DEVICE, null)
        change { it.copy(voiceInCar = voiceInCar(), status = getString(R.string.status_connecting)) }
        renderDevice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!started) {
            started = true
            carState.start()
            pleos.start()
            connect()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        relay?.stop()
        carState.stop()
        mic.stop()
        voice.release()
        pleos.release()
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notify_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notify_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFY_ID, n, type)
    }

    // ---- 화면과 주고받기 ----

    fun addListener(l: (Ui) -> Unit) {
        listeners += l
        l(ui)
    }

    fun removeListener(l: (Ui) -> Unit) {
        listeners -= l
    }

    private fun change(f: (Ui) -> Ui) {
        val next = f(ui)
        if (next == ui) return
        ui = next
        listeners.toList().forEach { it(next) }
    }

    private fun setFace(expr: String, head: String?) = change { it.copy(expr = expr, head = head, faceSeq = it.faceSeq + 1) }

    /** 목소리를 차 스피커로 낼지. 끄면 스노봇 스피커(스노봇이 꺼져 있으면 서버가 알아서 차 쪽으로 보낸다). */
    private fun voiceInCar() = prefs.getBoolean(KEY_VOICE_CAR, false)

    fun toggleVoice() {
        prefs.edit().putBoolean(KEY_VOICE_CAR, !voiceInCar()).apply()
        change { it.copy(voiceInCar = voiceInCar()) }
    }

    // ---- 서버 연결 ----

    private fun connect() {
        val server = BuildConfig.SERVER_URL
        if (server.isBlank()) {
            setStatus(R.string.status_no_server, R.color.warn)
            return
        }
        val token = prefs.getString(KEY_TOKEN, null)
        change { it.copy(pairing = token == null) }
        if (token == null) return
        relay = RelayClient(server, this).also { it.start(token) }
    }

    fun pair(id: String, claim: String) {
        if (id.isEmpty() || claim.isEmpty()) return
        change { it.copy(status = getString(R.string.pair_working)) }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RelayClient.pair(BuildConfig.SERVER_URL, id, claim, getString(R.string.app_name))
            }
            result.onSuccess { token ->
                prefs.edit().putString(KEY_TOKEN, token).putString(KEY_DEVICE, id).apply()
                deviceId = id
                relay?.stop()
                connect()
            }.onFailure {
                change { s -> s.copy(status = getString(R.string.pair_failed, it.message ?: it.javaClass.simpleName)) }
            }
        }
    }

    fun unpair() {
        relay?.stop()
        relay = null
        connected = false
        prefs.edit().clear().apply()
        deviceId = null
        deviceOnline = false
        renderDevice()
        connect()
    }

    override fun onState(state: RelayClient.State) {
        connected = state == RelayClient.State.CONNECTED
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
                change { it.copy(pairing = true) }
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
                carSent = null
                sendCar()   // 다시 붙으면 서버가 차 상태를 모르니 다시 알린다
            }
            "presence" -> if (msg.optString("device_id") == deviceId) {
                deviceOnline = msg.optBoolean("online")
                renderDevice()
            }
            "event" -> if (msg.optString("cap") == "env") onAir(msg.optJSONObject("data") ?: JSONObject())
            "heard" -> {
                val text = msg.optString("text")
                if (text.isNotEmpty()) change { it.copy(heard = getString(R.string.talk_heard, text)) }
            }
            "reply_start" -> if (!busy) setFace(msg.optString("expr", "neutral"), if (msg.isNull("head")) null else msg.optString("head"))
            "reply" -> change { it.copy(speech = msg.optString("text")) }
            "say" -> if (!busy) pleos.say(msg.optInt("id"), msg.optString("text"))
            "say_end" -> pleos.sayEnd(msg.optInt("id"), msg.optBoolean("cancelled"))
        }
    }

    // 서버 목소리 조각은 네트워크 스레드에서 온다. 바쁠 때는 틀지 않는다.
    override fun onAudioStart(sampleRate: Int) { if (!busy) voice.start(sampleRate) }
    override fun onAudio(chunk: ByteArray) { if (!busy) voice.write(chunk) }
    override fun onAudioEnd(cancelled: Boolean) = voice.end(cancelled || busy)

    // ---- 말하기 ----

    /** 화면 속 스노봇은 항상, 실물 스노봇은 켜져 있을 때 같은 명령을 받는다. 서버가 없으면 차 목소리로 바로 읽는다. */
    fun express(expr: String, head: String?, say: String) {
        setFace(expr, head)
        change { it.copy(speech = say) }
        val id = deviceId
        val client = relay
        if (id != null && client != null && connected) {
            val msg = talkMessage("express", id).put("expr", expr).put("say", say)
            if (head != null) msg.put("head", head)
            client.send(msg)
        } else if (pleos.canSpeak) {
            pleos.sayLocal(say)
        }
    }

    fun expressRes(expr: String, head: String?, line: Int) = express(expr, head, getString(line))

    val talking get() = mic.isRecording || pleos.isListening

    /** 말하기: 하던 말을 멈추고 듣기 시작한다. 마이크 권한은 화면이 미리 받아 둔다. */
    fun startTalk() {
        val id = deviceId ?: return
        val client = relay ?: return
        voice.end(cancelled = true)
        pleos.stopSpeaking()
        if (pleos.canListen) {
            client.send(talkMessage("listen_start", id).put("stt", "app"))
            pleos.listen()
        } else {
            client.send(talkMessage("listen_start", id))
            if (!mic.start()) {
                client.send(talkMessage("listen_end", id))
                change { it.copy(heard = getString(R.string.talk_no_mic)) }
                return
            }
        }
        setFace("alert", null)
        change { it.copy(heard = getString(R.string.talk_listening), talking = true) }
        main.postDelayed(talkTimeout, MAX_TALK_MS)
    }

    fun endTalk() {
        main.removeCallbacks(talkTimeout)
        if (pleos.isListening) {
            pleos.stopListening()   // 마지막 글자가 오면 onHeard 가 마무리한다
            return
        }
        if (!mic.isRecording) return
        mic.stop()
        deviceId?.let { relay?.send(talkMessage("listen_end", it)) }
        talkDone()
    }

    private fun talkDone() {
        setFace("neutral", null)
        change { it.copy(talking = false) }
    }

    /** 차 엔진이 들은 글자. 다 들으면(final) 서버에 글자로 말을 건다 — 빈 글자면 서버가 "잘 못 들었어요"로 답한다. */
    private fun onHeard(text: String, final: Boolean) {
        if (text.isNotEmpty()) change { it.copy(heard = getString(R.string.talk_heard, text)) }
        if (!final) return
        if (text.isEmpty()) change { it.copy(heard = null) }   // "듣고 있어요…"를 남기지 않는다
        main.removeCallbacks(talkTimeout)
        talkDone()
        chat(text)
    }

    /** 글자로 말 걸기 (차 엔진이 들은 말, 시험용 명령). */
    fun chat(text: String) {
        val id = deviceId ?: return
        relay?.send(talkMessage("chat", id).put("text", text))
    }

    /** tts="app": 이 앱으로 올 말은 서버가 소리 대신 글자(say)로 보내고, 차 엔진이 읽는다. */
    private fun talkMessage(type: String, id: String): JSONObject {
        val msg = JSONObject().put("type", type).put("device_id", id).put("voice", if (voiceInCar()) "app" else "device")
        if (pleos.canSpeak) msg.put("tts", "app")
        return msg
    }

    // ---- 차 상태와 판단기 ----

    /** 차 상태가 바뀔 때: 화면 줄 갱신, 판단기(말 멈춤), 서버에 알림, 먼저 말할지 판단. */
    private fun onCar(snap: CarState.Snapshot) {
        val before = carNow
        carNow = snap
        val warn = getString(if (snap.attention) R.string.car_attention_on else R.string.car_attention_off)
        val load = getString(R.string.car_load, snap.load, resources.getStringArray(R.array.load_names)[snap.load])
        val line = when {
            snap.error != null && snap.gear == "?" -> getString(R.string.car_failed, snap.error)
            snap.gear == "?" -> getString(R.string.car_unknown)
            snap.driving -> getString(R.string.car_driving, snap.gear, snap.drivingMin, warn)
            else -> getString(R.string.car_parked, warn)
        }
        change { it.copy(car = "$line\n$load", driving = snap.driving) }

        val wasBusy = busy
        busy = snap.load == DrivingLoad.BUSY
        if (busy && !wasBusy) quiet(snap)
        sendCar()
        if (!busy && wasBusy) flushSafety()
        if (before == null || before.gear != snap.gear || before.drivingMin != snap.drivingMin || before.attention != snap.attention) {
            coach.update(snap, busy = talking, clockMin = SystemClock.elapsedRealtime() / 60_000)
        }
    }

    /** 운전이 바빠진 순간: 하던 말을 끊고 정면을 본다. */
    private fun quiet(snap: CarState.Snapshot) {
        pleos.stopSpeaking()
        voice.end(cancelled = true)
        setFace("neutral", null)
        Log.i(TAG, "운전 부담 3 → 말 멈춤 ${SystemClock.elapsedRealtime() - snap.loadChangedAt}ms (${snap.signals})")
    }

    private fun coachSays(expr: String, head: String?, line: String, safety: Boolean) {
        if (!busy) {
            express(expr, head, line)
            return
        }
        if (!safety) return   // 바쁠 때 인사는 버린다
        pendingSafety = Triple(expr, head, line)
        main.removeCallbacks(safetyTimeout)
        main.postDelayed(safetyTimeout, SAFETY_WAIT_MS)
    }

    private fun flushSafety() {
        main.removeCallbacks(safetyTimeout)
        val (expr, head, line) = pendingSafety ?: return
        pendingSafety = null
        if (!busy) {
            express(expr, head, line)
            return
        }
        // 10초 넘게 바쁨이 이어짐: 기다리지 않고 차 목소리로 바로 한 문장 (서버 말은 바쁜 동안 막혀 있다)
        setFace(expr, head)
        change { it.copy(speech = line) }
        if (pleos.canSpeak) pleos.sayLocal(line)
    }

    /** 서버가 아는 차 상태. 핸들 각도처럼 자주 바뀌는 값은 보내지 않고, 대화에 쓰는 값이 바뀔 때만 보낸다. */
    private fun sendCar() {
        val snap = carNow ?: return
        val key = listOf(snap.gear, snap.drivingMin, snap.attention, snap.load)
        if (key == carSent) return
        val sent = relay?.send(JSONObject().put("type", "car").put("gear", snap.gear).put("driving_min", snap.drivingMin)
            .put("attention", snap.attention).put("load", snap.load)) ?: false
        if (sent) carSent = key
    }

    // ---- 시험용 (디버그 빌드에서 화면이 넘겨준다) ----

    fun pretendCar(spec: String) {
        val kv = spec.split(' ', ',').mapNotNull { it.split('=').takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        fun flag(k: String) = kv[k]?.let { it == "1" || it.equals("L", true) || it.equals("R", true) }
        carState.pretend(
            gear = kv["gear"]?.uppercase(), addMin = kv["add"]?.toIntOrNull(), attention = flag("attention"),
            turn = flag("turn"), steer = kv["steer"]?.toFloatOrNull(), wiper = flag("wiper"), beam = flag("beam"), cruise = flag("cruise"),
        )
    }

    // ---- 공기·표시 ----

    /** CO₂ 가 1,500ppm 을 넘는 순간 한 번: 걱정 표정 + 환기 제안. 1,200 아래로 내려가면 다시 알릴 수 있다. */
    private fun onAir(data: JSONObject) {
        val co2 = data.optInt("co2", -1)
        if (co2 < 0) return
        change { it.copy(air = getString(R.string.air_value, co2, data.optDouble("temp", Double.NaN))) }
        if (co2 >= CO2_ALERT && !airAlerted) {
            airAlerted = true
            coachSays("worried", "nod", getString(R.string.line_air), safety = false)
        } else if (co2 < CO2_RESET) {
            airAlerted = false
        }
    }

    private fun renderDevice() {
        val id = deviceId
        change {
            it.copy(device = when {
                id == null -> getString(R.string.device_none)
                deviceOnline -> getString(R.string.device_on, id)
                else -> getString(R.string.device_off, id)
            })
        }
    }

    private fun setStatus(text: Int, color: Int) = change { it.copy(status = getString(text), statusColor = color) }

    companion object {
        private const val TAG = "SnowBot"
        private const val CHANNEL = "snowbot"
        private const val NOTIFY_ID = 1
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_VOICE_CAR = "voice_in_car"
        private const val CO2_ALERT = 1500
        private const val CO2_RESET = 1200
        private const val MAX_TALK_MS = 12_000L    // 버튼을 안 눌러도 12초면 듣기를 끝낸다
        private const val SAFETY_WAIT_MS = 10_000L // 바쁜 동안 안전 알림을 미루는 최대 시간

        fun start(context: Context) {
            val i = Intent(context, SnowBotService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }
}
