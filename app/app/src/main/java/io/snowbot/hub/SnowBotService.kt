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
import org.json.JSONArray
import org.json.JSONObject

/**
 * 스노봇의 몸통: 앱 화면을 닫아도 계속 도는 포그라운드 서비스. **판단과 표현을 잇는 곳**이다.
 * 화면(MainActivity)은 이 서비스에 붙어 [Ui] 상태를 그리기만 한다.
 *
 * 입력 → 판단 → 표현
 *   입력: 차 신호([CarState]) · 내비([NaviState]) · 봇 센서(observe) · 운전자 말([DriverWords]) · 이전 기록
 *   판단: [CarCoach](경고등·주의 경고·휴식·출발·도착) · [CarWatch](대조·거리 이정표) ·
 *         [Ladder](편의 권유의 전달 방식) → [Judgement]
 *   표현: [Expression] 이 감정·몸짓·문장 조각을 정하고, 여기서 글자로 바꿔 봇·차 스피커로 보낸다
 *
 * 겹치면 **안전 → 권유 → 인사**. 권유끼리는 [TIP_GAP_MS] 를 두고 하나씩 한다.
 * 운전자가 그만두라고 하면([DriverWords.Decline]) 그 상황의 권유는 이번 운행에서 하지 않는다 —
 * **안전은 끌 수 없다**.
 *
 * 서버에 연결되지 않았으면 먼저 하는 말(인사·안전 알림)은 차 목소리로 바로 읽는다 — 인터넷 없이도 동작.
 * 운전자가 차 상태를 물으면 앱이 차 요약([CarReport])으로 바로 답한다 — 이것도 인터넷이 필요 없다.
 *
 * 봇 커넥트 규약(BCP) v0.3 의 허브 (docs/pleos/03-module-protocol.md, 순수 부분은 BotConnect·Interpreter):
 *   - 짝 맺은 봇들의 설명(hello)을 읽어 목록을 만들고, 봇마다 허락(welcome)을 보낸다.
 *   - 차가 주는 정보를 주제(context)로 바꿔, 허락한 주제가 바뀔 때만 보낸다. 출발·도착은 trip 으로.
 *   - 봇이 주는 정보(observe·event)를 모아 차 정보와 함께 해석하고(Interpreter), 상황이 생기면 피드백한다.
 *   - 표정은 짝 맺은 켜진 봇 모두에 각자 몸에 맞게(BotConnect.express), 말은 첫 번째 봇(또는 차 스피커)으로.
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
        val bots: String = "",
        val speaking: Boolean = false,      // 말하는 중 — 입자 구름 가장자리가 출렁인다
        val look: LookShape.Look? = null,   // 연결된 봇이 알려 준 모습. null 이면 화면은 빛 방울
        val panel: List<CarReport.Item> = emptyList(),   // 차 상태 패널 (항목별 값 · 정상 여부)
        val flash: String? = null,          // 방금 말한 근거 숫자 — 패널 위에 잠깐 크게 보인다
        val flashSeq: Int = 0,
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
    private val coach = CarCoach()
    private val watch = CarWatch()
    private val carState by lazy { CarState(this, ::onCar) }
    private val navi by lazy { NaviState(this, ::onNav) }
    private val listeners = mutableSetOf<(Ui) -> Unit>()
    private val talkTimeout = Runnable { endTalk() }
    private val flashOff = Runnable { change { it.copy(flash = null) } }
    private val tipLater = Runnable { flushTip() }
    private val offerTimeout = Runnable { pendingOffer = null }
    private val replyTimeout = Runnable {
        Log.i(TAG, "백엔드 대답이 ${REPLY_WAIT_MS}ms 안에 오지 않음 — 불가 보고")
        show(Judgement(Situation.CANT_TALK))
    }

    var ui = Ui()
        private set
    private var relay: RelayClient? = null
    private var connected = false
    private var deviceId: String? = null
    private var deviceOnline = false
    private var carNow: CarState.Snapshot? = null
    private var navNow = NaviState.Nav()
    private var carSent: List<Any>? = null
    private val bots = linkedMapOf<String, BotConnect.Bot>()
    private val sentTopics = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>()   // 봇별 마지막으로 보낸 주제 값
    private val env = mutableMapOf<String, Number>()     // 봇들이 알려 준 차 안 공기 (어느 봇이든 같은 차 안)
    // 편의 권유는 상황마다 사다리 하나씩. 경고등·주의 경고·휴식·인사는 CarCoach 쪽이라 사다리를 쓰지 않는다.
    private val airLadder = Ladder.air()
    private val fogLadder = Ladder.fog()
    private var lastTipAt = -TIP_GAP_MS          // 마지막 권유 한마디 시각 — 권유끼리 겹쳐 말하지 않게
    private var pendingTip: Judgement? = null    // 간격이 벌어지면 할 권유 (늦게 온 것이 이긴다)
    private var pendingOffer: Judgement? = null  // 수락("응")을 기다리는 제안
    private var safetyAt = -SAFETY_HUSH_MS       // 마지막 안전 알림 시각 — 그 직후 인사는 건너뛴다
    private var lastTip: Situation? = null       // 마지막으로 한 권유 — 운전자가 "그냥 둬" 할 때 끌 대상
    private val dropped = mutableSetOf<Situation>()   // 이번 운행에서 하지 않을 권유 (안전은 못 끈다)
    private var started = false

    // ---- 서비스 수명 ----

    override fun onCreate() {
        super.onCreate()
        deviceId = prefs.getString(KEY_DEVICE, null)
        change { it.copy(voiceInCar = voiceInCar(), status = getString(R.string.status_connecting)) }
        pleos.onSpeaking = { on -> change { it.copy(speaking = on) } }
        renderDevice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!started) {
            started = true
            carState.start()
            navi.start()
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
        navi.stop()
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

    private fun setFace(expr: String, head: String?) =
        change { it.copy(expr = expr, head = SCREEN_HEADS[head] ?: head, faceSeq = it.faceSeq + 1) }

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
                RelayClient.pair(BuildConfig.SERVER_URL, id, claim, getString(R.string.app_name), prefs.getString(KEY_TOKEN, null))
            }
            result.onSuccess { token ->
                if (deviceId == null) deviceId = id      // 첫 봇이 말하는 봇(스피커)이 된다. 더한 봇은 목록에 붙는다
                prefs.edit().putString(KEY_TOKEN, token).putString(KEY_DEVICE, deviceId).apply()
                change { it.copy(pairing = false) }
                relay?.stop()
                connect()
            }.onFailure {
                change { s -> s.copy(status = getString(R.string.pair_failed, it.message ?: it.javaClass.simpleName)) }
            }
        }
    }

    /** 봇 추가: 짝 맺기 칸을 다시 보인다 (이미 짝 맺은 앱이면 같은 앱에 봇을 더한다). */
    fun showPairing(show: Boolean) = change { it.copy(pairing = show) }

    fun unpair() {
        relay?.stop()
        relay = null
        connected = false
        prefs.edit().clear().apply()
        deviceId = null
        deviceOnline = false
        bots.clear()
        sentTopics.clear()
        renderDevice()
        renderBots()
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
        // 백엔드가 살아 있다는 신호 — 기다리던 불가 보고를 거둔다
        if (msg.optString("type") in REPLY_KINDS) main.removeCallbacks(replyTimeout)
        when (msg.optString("type")) {
            "auth_ok" -> {
                val list = msg.optJSONArray("devices")
                val ids = (0 until (list?.length() ?: 0)).mapNotNull { list?.optJSONObject(it) }
                bots.clear()
                sentTopics.clear()
                for (d in ids) {
                    val bot = BotConnect.parse(d.optString("device_id"), d.optJSONObject("hello"), d.optBoolean("online"))
                    bots[bot.id] = bot
                }
                if (deviceId !in bots) deviceId = bots.keys.firstOrNull()
                deviceOnline = bots[deviceId]?.online == true
                prefs.edit().putString(KEY_DEVICE, deviceId).apply()
                renderDevice()
                carSent = null
                sendCar()   // 다시 붙으면 서버가 차 상태를 모르니 다시 알린다
                bots.values.filter { it.online && it.version != "?" }.forEach(::welcomeBot)
                renderBots()
            }
            "presence" -> {
                val id = msg.optString("device_id")
                val online = msg.optBoolean("online")
                bots[id]?.let { bots[id] = it.copy(online = online) }
                if (!online) sentTopics.remove(id)
                if (id == deviceId) {
                    deviceOnline = online
                    renderDevice()
                }
                renderBots()
            }
            "hello" -> {
                val id = msg.optString("device_id")
                val bot = BotConnect.parse(id, msg, true)
                bots[id] = bot
                if (deviceId == null) deviceId = id
                welcomeBot(bot)
                renderBots()
            }
            "observe" -> if (msg.optString("sensor") == "env") onObserve(msg.optJSONObject("data") ?: JSONObject())
            "event" -> if (msg.optString("cap") == "env") onObserve(msg.optJSONObject("data") ?: JSONObject())
            "heard" -> {
                val text = msg.optString("text")
                if (text.isNotEmpty()) change { it.copy(heard = getString(R.string.talk_heard, text)) }
            }
            "reply_start" -> setFace(msg.optString("expr", "neutral"), if (msg.isNull("head")) null else msg.optString("head"))
            "reply" -> change { it.copy(speech = msg.optString("text")) }
            "say" -> pleos.say(msg.optInt("id"), msg.optString("text"))
            "say_end" -> pleos.sayEnd(msg.optInt("id"), msg.optBoolean("cancelled"))
        }
    }

    override fun onAudioStart(sampleRate: Int) {
        voice.start(sampleRate)
        main.post { change { it.copy(speaking = true) } }
    }
    override fun onAudio(chunk: ByteArray) = voice.write(chunk)
    override fun onAudioEnd(cancelled: Boolean) {
        voice.end(cancelled)
        main.post { change { it.copy(speaking = false) } }
    }

    // ---- 말하기 ----

    /** 화면 속 스노봇은 항상, 실물 스노봇은 켜져 있을 때 같은 명령을 받는다. 서버가 없으면 차 목소리로 바로 읽는다. */
    fun express(expr: String, head: String?, say: String) {
        setFace(expr, head)
        change { it.copy(speech = say) }
        Log.i(TAG, "말함 [$expr${head?.let { " · $it" } ?: ""}] $say")
        if (!sendExpress(expr, head, say) && pleos.canSpeak) pleos.sayLocal(say)
    }

    /**
     * **말 없이 표정만** (사다리 1칸 · 안도 · 조용히 풀림). 말풍선은 건드리지 않고 차 스피커로도 아무 소리를 내지 않는다.
     * 화면 입자 구름은 [setFace] 로 같은 표정을 따라간다.
     */
    fun expressFace(expr: String, head: String?) {
        setFace(expr, head)
        Log.i(TAG, "표정만 [$expr${head?.let { " · $it" } ?: ""}]")
        sendExpress(expr, head, say = null)
    }

    /**
     * 표정을 짝 맺은 켜진 봇 모두에 각자 몸에 맞게 보낸다(규약 6절). 말은 첫 봇(말하는 봇)에만 붙인다.
     * [say] 가 null 이면 말이 붙지 않고, 서버도 `say` 가 없으면 목소리를 만들지 않는다
     * (`server/snowbot_server/main.py` 의 express 처리).
     *
     * @return 서버로 보냈는지. 못 보냈으면 부르는 쪽이 차 목소리로 대신한다.
     */
    private fun sendExpress(expr: String, head: String?, say: String?): Boolean {
        val id = deviceId ?: return false
        val client = relay ?: return false
        if (!connected) return false
        val primary = bots[id]
        val msg = if (primary != null && primary.version != "?") BotConnect.express(primary, expr, head)
            else JSONObject().put("type", "express").put("device_id", id).put("expr", expr).apply { if (head != null) put("head", head) }
        if (say != null) {
            val talk = talkMessage("express", id)
            talk.keys().forEach { k -> if (k != "type" && k != "device_id") msg.put(k, talk.get(k)) }
            msg.put("say", say)
        }
        client.send(msg)
        // 같은 감정을 다른 봇들도 자기 몸으로 (말은 첫 봇·차 스피커만)
        bots.values.filter { it.id != id && it.online && it.version != "?" }.forEach { client.send(BotConnect.express(it, expr, head)) }
        return true
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

    /**
     * 글자로 말 걸기 (차 엔진이 들은 말, 시험용 명령).
     * **백엔드로 보내기 전에** [DriverWords] 로 먼저 본다 — 차 상태 질문·수락·거절·차 조작·경로는
     * 앱이 바로 답하고(인터넷 불필요), 그 밖의 말만 AI 가 대답한다.
     */
    fun chat(text: String) {
        val said = DriverWords.parse(text, waiting = pendingOffer != null, tip = canDrop())
        Log.i(TAG, "운전자 말 판단: ${said::class.simpleName} (제안 대기=${pendingOffer != null} 끌 권유=${canDrop()})")
        when (said) {
            DriverWords.StatusAsk -> show(Judgement(Situation.REPORT, report = report()))
            DriverWords.IntactAsk -> show(Judgement(Situation.INTACT, report = report()))
            DriverWords.RangeAsk -> show(Judgement(Situation.RANGE_REPORT, report = report()))
            DriverWords.Accept -> acceptOffer()
            DriverWords.Decline -> decline()
            is DriverWords.Control -> show(Judgement(Situation.CANT_CONTROL, facts = mapOf("what" to said.what)))
            is DriverWords.Route -> openPlaces(said.offer)
            DriverWords.ToAi -> toAi(text)
        }
    }

    /** 운전자가 바로 길·장소를 찾아 달라고 했다: 기다릴 것 없이 내비 화면을 연다. */
    private fun openPlaces(offer: Offer) {
        val opened = navi.openPlaces(
            if (offer == Offer.REROUTE || offer == Offer.CHARGE) null else Expression.place(offer),
            routeOptions = offer == Offer.REROUTE,
        )
        show(Judgement(if (opened) Situation.NAVI_OPENED else Situation.NAVI_MISSING))
    }

    /**
     * 그 밖의 말은 백엔드가 대답한다. 못 붙었으면, 또는 보냈는데 [REPLY_WAIT_MS] 안에 대답이 오지 않으면
     * 불가 보고를 한다 — 인터넷이 끊겨도 운전자가 기다리지 않게. 끊긴 것을 소켓이 바로 알지 못하므로
     * "보냈다"만으로는 붙었다고 볼 수 없다.
     */
    private fun toAi(text: String) {
        val id = deviceId
        val sent = if (id == null || !connected) false else relay?.send(talkMessage("chat", id).put("text", text)) ?: false
        main.removeCallbacks(replyTimeout)
        if (sent) main.postDelayed(replyTimeout, REPLY_WAIT_MS) else show(Judgement(Situation.CANT_TALK))
    }

    /** tts="app": 이 앱으로 올 말은 서버가 소리 대신 글자(say)로 보내고, 차 엔진이 읽는다. */
    private fun talkMessage(type: String, id: String): JSONObject {
        val msg = JSONObject().put("type", type).put("device_id", id).put("voice", if (voiceInCar()) "app" else "device")
        if (pleos.canSpeak) msg.put("tts", "app")
        return msg
    }

    // ---- 차 상태와 판단기 ----

    /** 차 상태가 바뀔 때: 화면 갱신, 서버에 알림, 판단(대조·먼저 말하기). */
    private fun onCar(snap: CarState.Snapshot) {
        val before = carNow
        carNow = snap
        renderCar()
        sendCar()
        pushContext()
        val departed = before != null && before.gear != "?" && !before.driving && snap.driving
        val arrived = before != null && before.gear != "?" && before.driving && snap.gear == "P"
        if (departed || arrived) {
            sendTrip(if (departed) "depart" else "arrive")
            navi.ask()
        }
        if (departed) {     // 새 운행: 끄라고 했던 권유와 기다리던 제안을 잊는다
            dropped.clear()
            pendingOffer = null
            pendingTip = null
        }
        interpret()   // 사다리는 상황마다 칸을 들고 있으므로 차 상태가 바뀔 때마다 부른다 (내기·창문·비·밤)
        crossCheck()
        if (before == null || before.gear != snap.gear || before.drivingMin != snap.drivingMin ||
            before.attention != snap.attention || before.warnings != snap.warnings || before.cabin != snap.cabin) {
            coach.update(snap, talking = talking, clockMin = SystemClock.elapsedRealtime() / 60_000)?.let(::judged)
        }
    }

    /** 내비 값이 바뀔 때. 내비 없는 차에서는 한 번도 오지 않는다. */
    private fun onNav(nav: NaviState.Nav) {
        if (nav == navNow) return
        navNow = nav
        renderCar()
        sendCar()
        crossCheck()
    }

    /** 대조: 차 신호와 내비를 맞춰 본다 ([CarWatch]). 조건이 새로 성립한 것만 돌아온다. */
    private fun crossCheck() {
        val snap = carNow ?: return
        val c = snap.cabin
        watch.update(CarWatch.Inputs(
            driving = snap.driving,
            drivingMin = snap.drivingMin,
            rangeKm = c.rangeKm,
            ev = if (c.socPct != null) true else if (c.fuelPct != null) false else null,
            destKm = navNow.destKm,
            destMin = navNow.destMin,
            windowOpen = c.windowsOpen.isNotEmpty(),
            doorOpen = c.doorsOpen.isNotEmpty(),
            rain = snap.signals.wiper,
            night = snap.signals.night,
            lowBeam = snap.signals.lowBeam,
            congested = navNow.congested,
            congestionKm = navNow.congestionKm,
        )).forEach(::judged)
    }

    // ---- 판단 결과 → 표현 ----

    /**
     * 판단 결과를 표현으로 옮긴다. 겹치면 안전 → 권유 → 인사이고, 권유끼리는 [TIP_GAP_MS] 를 둔다.
     * 운전자가 끄라고 한 권유는 말하지 않는다(표정만 가는 것은 그대로 둔다 — 얼굴이 멈춰 있지 않게).
     */
    private fun judged(j: Judgement) {
        val silent = Expression.silent(j.delivery)
        if (muted(j, dropped)) {
            Log.i(TAG, "끄라고 한 권유 ${j.situation} — 말하지 않음")
            return
        }
        val now0 = SystemClock.elapsedRealtime()
        // 안전을 막 말했으면 인사는 건너뛴다 — 같은 순간에 두 마디가 잇따라 나오지 않게
        if (j.kind == Kind.GREETING && now0 - safetyAt < SAFETY_HUSH_MS) {
            Log.i(TAG, "안전을 말한 직후라 인사 건너뜀 ${j.situation}")
            return
        }
        if (j.kind == Kind.SAFETY && !silent) safetyAt = now0
        if (j.kind == Kind.TIP && !silent) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastTipAt < TIP_GAP_MS) {
                pendingTip = j
                main.removeCallbacks(tipLater)
                main.postDelayed(tipLater, TIP_GAP_MS - (now - lastTipAt) + 50)
                Log.i(TAG, "권유 미룸 ${j.situation} (앞 권유와 간격 ${now - lastTipAt}ms)")
                return
            }
            lastTipAt = now
            lastTip = j.situation
        }
        show(j)
    }

    /** 미뤄 둔 권유를 간격이 벌어진 뒤에 한다. */
    private fun flushTip() {
        val j = pendingTip ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTipAt < TIP_GAP_MS) {
            main.postDelayed(tipLater, TIP_GAP_MS - (now - lastTipAt) + 50)
            return
        }
        pendingTip = null
        lastTipAt = now
        lastTip = j.situation
        Log.i(TAG, "미뤘던 권유 ${j.situation}")
        show(j)
    }

    /** 표현: 감정·몸짓·문장을 정해 보낸다. 말이 없으면 표정만 간다. */
    private fun show(j: Judgement) {
        val face = Expression.look(j)
        val text = Expression.pieces(j).joinToString(" ", transform = ::resolve).trim()
        Expression.flash(j)?.let { flash(it) }
        if (text.isEmpty()) expressFace(face.emotion, face.gesture) else express(face.emotion, face.gesture, text)
        if (j.offer != null && j.situation != Situation.NAVI_OPENED) waitOffer(j)
    }

    /** 문장 조각 하나를 글자로. 자원을 아는 쪽이 여기다(표현 쪽은 안드로이드를 모른다). */
    private fun resolve(p: Expression.Piece): String = when (p) {
        is Expression.Raw -> p.text
        is Expression.Text -> getString(p.res, *p.args.toTypedArray())
        is Expression.OneOf -> resources.getStringArray(p.arrayRes).random()
    }

    /** 근거 숫자를 화면 패널 위에 잠깐 크게 보인다. */
    private fun flash(text: String) {
        change { it.copy(flash = text, flashSeq = it.flashSeq + 1) }
        main.removeCallbacks(flashOff)
        main.postDelayed(flashOff, FLASH_MS)
    }

    // ---- 제안 기다리기 ----

    private fun waitOffer(j: Judgement) {
        pendingOffer = j
        main.removeCallbacks(offerTimeout)
        main.postDelayed(offerTimeout, OFFER_WAIT_MS)
        Log.i(TAG, "제안 대기 ${j.situation} → ${j.offer}")
    }

    /** 운전자가 "응" 했다: 진행 표시를 보이고 내비 화면을 연다. */
    private fun acceptOffer() {
        val j = pendingOffer ?: return
        pendingOffer = null
        main.removeCallbacks(offerTimeout)
        setFace("alert", null)
        change { it.copy(speech = getString(R.string.working)) }   // 진행 보고 — 소리는 내지 않는다
        val opened = navi.openPlaces(when (j.offer) {
            Offer.REROUTE -> null
            Offer.CHARGE -> null
            else -> Expression.place(j.offer)
        }, routeOptions = j.offer == Offer.REROUTE)
        show(Judgement(if (opened) Situation.NAVI_OPENED else Situation.NAVI_MISSING))
    }

    /**
     * 지금 끌 수 있는 권유가 있는지 — 기다리는 제안이 있거나, 권유를 한 지 [DECLINE_WINDOW_MS] 안이다.
     * 없으면 "괜찮아" 같은 말은 끄라는 뜻이 아니므로 백엔드가 대답한다.
     */
    private fun canDrop(): Boolean {
        if (pendingOffer != null) return true
        val tip = lastTip ?: return false
        return tip !in dropped && SystemClock.elapsedRealtime() - lastTipAt < DECLINE_WINDOW_MS
    }

    /** 운전자가 "그냥 둬" 했다: 지금 권유를 평소 표정으로 끝내고 이번 운행에서 그 상황을 하지 않는다. */
    private fun decline() {
        // 안전은 끌 수 없다 — 지금 끌 대상이 안전이면 아무것도 끄지 않는다
        val target = (pendingOffer?.situation ?: lastTip)?.takeIf { it.kind != Kind.SAFETY }
        pendingOffer = null
        pendingTip = null
        main.removeCallbacks(offerTimeout)
        if (target != null) {
            dropped += target
            Log.i(TAG, "권유 끔 $target (이번 운행)")
        }
        show(Judgement(Situation.DROPPED, Delivery.CALM))
    }

    // ---- 차 요약 ----

    /** 지금 차 요약. 못 읽은 값은 만들지 않는다([CarReport]). */
    private fun report(): List<CarReport.Item> {
        val snap = carNow
        return CarReport.items(
            gear = snap?.gear ?: "?",
            drivingMin = snap?.drivingMin ?: 0,
            warnings = snap?.warnings ?: emptySet(),
            cabin = snap?.cabin ?: Checkup.Cabin(),
            signals = snap?.signals ?: CarState.Signals(),
            nav = navNow,
            co2 = env["co2"]?.toInt(),
            humidity = env["humidity"]?.toInt(),
            online = connected,
        )
    }

    /** 화면: 차 상태 한 줄 + 차 상태 패널. */
    private fun renderCar() {
        val snap = carNow
        val warn = getString(if (snap?.attention == true) R.string.car_attention_on else R.string.car_attention_off)
        val line = when {
            snap == null || snap.gear == "?" ->
                if (snap?.error != null) getString(R.string.car_failed, snap.error) else getString(R.string.car_unknown)
            snap.driving -> getString(R.string.car_driving, snap.gear, snap.drivingMin, warn)
            else -> getString(R.string.car_parked, warn)
        }
        change { it.copy(car = line, driving = snap?.driving == true, panel = report()) }
    }

    /** 서버가 아는 차 상태. 대화에 쓰는 값이 바뀔 때만 보낸다. */
    private fun sendCar() {
        val snap = carNow ?: return
        val warnings = CarWarnings.labels(snap.warnings)   // 켜진 경고등 이름만 (AI 가 "왜 켜졌어?"에 답하게)
        val summary = CarReport.forServer(report())
        val key = listOf(snap.gear, snap.drivingMin, snap.attention, warnings, summary)
        if (key == carSent) return
        val sent = relay?.send(JSONObject().put("type", "car").put("gear", snap.gear).put("driving_min", snap.drivingMin)
            .put("attention", snap.attention).put("warnings", JSONArray(warnings)).put("summary", summary)) ?: false
        if (sent) carSent = key
    }

    // ---- 시험용 (디버그 빌드에서 화면이 넘겨준다) ----

    fun pretendCar(spec: String) {
        val kv = spec.split(' ', ',').mapNotNull { it.split('=').takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        fun flag(k: String) = kv[k]?.let { it == "1" }
        // warn=TPMS 또는 warn=TPMS+LOW_FUEL (경고등 이름은 Pleos TelltaleType), warn=0 이면 끔
        val warn = kv["warn"]?.let { if (it == "0") emptySet() else it.split('+').map { n -> n.uppercase() }.toSet() }
        carState.pretend(
            gear = kv["gear"]?.uppercase(), addMin = kv["add"]?.toIntOrNull(), attention = flag("attention"),
            wiper = flag("wiper"), beam = flag("beam"),
            recirc = flag("recirc"), ac = flag("ac"), defrost = flag("defrost"),
            warn = warn, belt = flag("belt"), range = kv["range"]?.toIntOrNull(), fuel = kv["fuel"]?.toIntOrNull(),
            soc = kv["soc"]?.toIntOrNull(), window = flag("window"), door = flag("door"), rear = flag("rear"),
        )
    }

    /** 시험용: 에뮬레이터에 내비가 없을 때 내비 값을 넣는다. 예) "dest_km=52 eta_min=40" */
    fun pretendNav(spec: String) = navi.pretend(spec)

    /**
     * 시험용: 봇이 보낸 것처럼 봇 설명(hello)을 넣는다 — 서버 없이 화면 입자 구름이 그 봇 모습으로 모이는지 본다.
     * 서버가 붙여 주는 `device_id` 까지 담은 JSON 을 그대로 넘긴다.
     */
    fun pretendHello(json: String) {
        runCatching { onMessage(JSONObject(json)) }.onFailure { Log.w(TAG, "시험용 hello 읽기 실패", it) }
    }

    /** 시험용: 그 봇이 꺼진 것처럼 — 입자가 흩어져 빛 방울로 돌아가는지 본다. */
    fun pretendBye(id: String) =
        onMessage(JSONObject().put("type", "presence").put("device_id", id).put("online", false))

    /** 시험용: 봇이 보낸 것처럼 공기 값을 넣는다. 예) "humidity=80 co2=1600" */
    fun pretendObserve(spec: String) {
        val data = JSONObject()
        spec.split(' ', ',').mapNotNull { it.split('=').takeIf { p -> p.size == 2 } }.forEach { (k, v) -> v.toIntOrNull()?.let { data.put(k, it) } }
        onObserve(data)
    }

    // ---- 공기·표시 ----

    // ---- 봇 커넥트 규약: 허락 · 차 정보 · 봇 정보 · 해석 ----

    private fun welcomeBot(bot: BotConnect.Bot) {
        relay?.send(BotConnect.welcome(bot, carNow?.driving == true))
        sentTopics[bot.id] = mutableMapOf()
        pushContext()
    }

    /** 허락한 차 정보 주제 중 값이 바뀐 것만 켜진 봇들에 보낸다. */
    private fun pushContext() {
        val snap = carNow ?: return
        val client = relay ?: return
        if (!connected) return
        val topics = BotConnect.topics(snap)
        for (bot in bots.values) {
            if (!bot.online || bot.version == "?") continue
            val sent = sentTopics.getOrPut(bot.id) { mutableMapOf() }
            for ((topic, data) in topics) {
                if (topic !in bot.granted || sent[topic] == data) continue
                if (client.send(BotConnect.context(bot.id, topic, data))) sent[topic] = data
            }
        }
    }

    private fun sendTrip(event: String) {
        val client = relay ?: return
        bots.values.filter { it.online && "trip" in it.granted }
            .forEach { client.send(BotConnect.context(it.id, "trip", mapOf("event" to event))) }
    }

    /** 봇이 주는 차 안 공기 값. 어느 봇이 느꼈든 같은 차 안으로 모은다. */
    private fun onObserve(data: JSONObject) {
        for (k in listOf("co2", "humidity", "temp")) if (data.has(k)) env[k] = data.optDouble(k)
        val parts = listOfNotNull(
            env["co2"]?.let { getString(R.string.air_co2, it.toInt()) },
            env["humidity"]?.let { getString(R.string.air_humidity, it.toInt()) },
            env["temp"]?.let { getString(R.string.air_temp, it.toDouble()) },
        )
        change { it.copy(air = getString(R.string.air_line, parts.joinToString(" · "))) }
        interpret()
    }

    /**
     * 차 정보 + 봇 정보 → 편의 권유 사다리([Ladder]). 상황마다 사다리 하나가 전달 방식을 들고 있으므로,
     * 값이 바뀔 때마다(차 신호·봇 센서) 빠짐없이 불러야 한다.
     * 시간이 지나야 되는 "다시 한마디"는 새 센서 값이 올 때 판단한다(봇이 주기로 보낸다).
     *
     * 김서림(앞이 안 보이게 될 수 있음)을 답답함보다 먼저 본다 — 둘이 같은 순간 말할 차례면 김서림이 먼저 말한다.
     */
    private fun interpret() {
        val snap = carNow
        val s = snap?.signals
        val car = snap?.let { Ladder.Car.of(it) } ?: Ladder.Car()
        val now = SystemClock.elapsedRealtime()
        val co2 = env["co2"]?.toInt()
        val humidity = env["humidity"]?.toInt()
        val wet = s?.wiper == true || s?.night == true

        fogLadder.updateFog(humidity, wet, car, now)?.let { cue ->
            Log.i(TAG, "사다리 김서림: $cue 칸=${fogLadder.rung} 습도=$humidity 비·밤=$wet 창문=${car.windowOpen} 에어컨=${car.ac} 성에=${car.defrost}")
            judged(Judgement(Situation.FOG_RISK, delivery(cue), facts = buildMap {
                put("ac", car.ac == true)
                humidity?.let { put("humidity", it) }
                put("from", fogLadder.from)
                put("to", humidity ?: 0)
            }))
            if (cue == Ladder.Cue.RELIEF || cue == Ladder.Cue.RELAX) forget(Situation.FOG_RISK)
        }
        airLadder.updateAir(co2, car, now)?.let { cue ->
            Log.i(TAG, "사다리 답답함: $cue 칸=${airLadder.rung} co2=$co2 내기=${s?.recirc} 창문=${car.windowOpen}")
            judged(Judgement(Situation.STUFFY, delivery(cue), facts = buildMap {
                put("recirc", s?.recirc == true)
                co2?.let { put("co2", it) }
                put("from", airLadder.from)
                put("to", co2 ?: 0)
            }))
            if (cue == Ladder.Cue.RELIEF || cue == Ladder.Cue.RELAX) forget(Situation.STUFFY)
        }
    }

    /** 사다리 칸 → 전달 방식. */
    private fun delivery(cue: Ladder.Cue): Delivery = when (cue) {
        Ladder.Cue.FACE -> Delivery.FACE_ONLY
        Ladder.Cue.SAY -> Delivery.SAY
        Ladder.Cue.SAY_AGAIN -> Delivery.SAY_AGAIN
        Ladder.Cue.RELIEF -> Delivery.RELIEF
        Ladder.Cue.RELAX -> Delivery.CALM
    }

    /** 풀린 상황의 미뤄 둔 권유·기다리던 제안은 버린다. */
    private fun forget(sit: Situation) {
        if (pendingTip?.situation == sit) pendingTip = null
        if (pendingOffer?.situation == sit) pendingOffer = null
    }

    private fun renderBots() {
        val text = bots.values.joinToString("\n") { BotConnect.describe(it, LABELS) }
        change { it.copy(bots = text, look = screenLook()) }
    }

    /**
     * 화면 입자 구름이 모일 모습: 짝 맺은 켜진 봇 중 모습을 알려 준 첫 봇(말하는 봇을 먼저 본다).
     * 아무도 없으면 null — 화면은 빛 방울로 돌아간다 (규약 3-5절).
     */
    private fun screenLook(): LookShape.Look? =
        (listOfNotNull(bots[deviceId]) + bots.values).firstOrNull { it.online && it.look != null }?.look

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
        private val SCREEN_HEADS = mapOf("look_left" to "turn_left", "look_right" to "turn_right")   // 화면 속 스노봇이 아는 이름
        private val LABELS = mapOf(
            "eyelid" to "눈꺼풀", "brow" to "눈썹", "neck" to "목", "light" to "LED", "screen" to "화면",
            "speaker" to "스피커", "microphone" to "마이크", "co2" to "CO₂", "temp" to "온도", "humidity" to "습도",
            "car.motion" to "주행", "car.outside" to "비·밤", "car.cabin" to "실내",
            "car.energy" to "연료", "car.warning" to "경고등", "trip" to "출발·도착", "driver.state" to "운전자 상태",
        )
        private const val CHANNEL = "snowbot"
        private const val NOTIFY_ID = 1
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_VOICE_CAR = "voice_in_car"
        private const val MAX_TALK_MS = 12_000L    // 버튼을 안 눌러도 12초면 듣기를 끝낸다
        /** 권유 한마디 사이 최소 간격. 시작 값, 시험 주행으로 조정. */
        const val TIP_GAP_MS = 20_000L
        /** 제안("찾아볼까요?")에 "응"을 기다리는 시간. 시작 값. */
        const val OFFER_WAIT_MS = 30_000L
        /** 근거 숫자를 패널 위에 크게 보이는 시간. */
        const val FLASH_MS = 5_000L
        /** 안전을 말한 뒤 이 시간 안의 인사는 건너뛴다. 시작 값. */
        const val SAFETY_HUSH_MS = 5_000L
        /** 권유를 한 뒤 이 시간 안의 "그냥 둬"만 그 권유를 끄는 말로 읽는다. 시작 값. */
        const val DECLINE_WINDOW_MS = 60_000L
        /** 백엔드에 말을 보낸 뒤 대답을 기다리는 시간. 넘으면 불가 보고. 시작 값. */
        const val REPLY_WAIT_MS = 8_000L
        /** 이 메시지가 오면 백엔드가 대답하는 중이다. */
        private val REPLY_KINDS = setOf("heard", "reply_start", "reply", "say")

        fun start(context: Context) {
            val i = Intent(context, SnowBotService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }
}
