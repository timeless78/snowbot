package io.snowbot.hub

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 차량 앱 첫 화면. 판단·서버 연결·목소리는 모두 SnowBotService(포그라운드 서비스)가 한다 — 이 화면을 닫아도
 * 스노봇은 출발·도착 인사와 안전 알림을 계속한다. 화면은 서비스의 Ui 상태를 그리고, 버튼을 서비스에 넘긴다.
 * 주행 중에는 시험용 버튼을 숨긴다. 마이크는 화면이 보일 때만 쓴다(화면을 떠나면 듣기를 끝낸다).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var snowbot: SnowBotView
    private lateinit var speech: TextView
    private lateinit var heard: TextView
    private lateinit var talkButton: Button
    private lateinit var status: TextView
    private lateinit var device: TextView
    private lateinit var air: TextView
    private lateinit var car: TextView
    private lateinit var voiceButton: Button
    private lateinit var testButtons: List<View>
    private lateinit var pairing: View

    private var service: SnowBotService? = null
    private var faceSeq = -1
    private var pendingIntent: Intent? = null
    private val render: (SnowBotService.Ui) -> Unit = ::render

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as SnowBotService.LocalBinder).service
            service = s
            faceSeq = -1
            s.addListener(render)
            pendingIntent?.let { debug(it) }
            pendingIntent = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        snowbot = findViewById(R.id.snowbot)
        speech = findViewById(R.id.speech)
        heard = findViewById(R.id.heard)
        talkButton = findViewById(R.id.btn_talk)
        status = findViewById(R.id.status)
        device = findViewById(R.id.device)
        air = findViewById(R.id.air)
        car = findViewById(R.id.car)
        voiceButton = findViewById(R.id.voice_button)
        testButtons = listOf(R.id.btn_greet, R.id.btn_rest, R.id.btn_worry).map { findViewById(it) }
        pairing = findViewById(R.id.pairing)

        talkButton.setOnClickListener { toggleTalk() }
        findViewById<Button>(R.id.btn_greet).setOnClickListener { service?.expressRes("happy", "nod", R.string.line_greet) }
        findViewById<Button>(R.id.btn_rest).setOnClickListener { service?.expressRes("sleepy", "nod", R.string.line_rest) }
        findViewById<Button>(R.id.btn_worry).setOnClickListener { service?.expressRes("worried", "shake", R.string.line_worry) }
        findViewById<Button>(R.id.pair_button).setOnClickListener {
            val id = findViewById<EditText>(R.id.device_id).text.toString().trim().uppercase()
            val claim = findViewById<EditText>(R.id.claim_code).text.toString().trim()
            service?.pair(id, claim)
        }
        findViewById<Button>(R.id.unpair_button).setOnClickListener { service?.unpair() }
        voiceButton.setOnClickListener { service?.toggleVoice() }

        SnowBotService.start(this)
        bindService(Intent(this, SnowBotService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (savedInstanceState == null) pendingIntent = intent
    }

    override fun onStop() {
        service?.let { if (it.talking) it.endTalk() }
        super.onStop()
    }

    override fun onDestroy() {
        service?.removeListener(render)
        unbindService(connection)
        super.onDestroy()
    }

    private fun render(ui: SnowBotService.Ui) {
        if (ui.faceSeq != faceSeq) {
            // 화면에 다시 붙었을 때는 고개 움직임을 되풀이하지 않는다
            snowbot.setExpression(ui.expr, if (faceSeq == -1) null else ui.head)
            faceSeq = ui.faceSeq
        }
        speech.text = ui.speech ?: getString(R.string.hello_line)
        heard.visibility = if (ui.heard == null) View.GONE else View.VISIBLE
        heard.text = ui.heard.orEmpty()
        talkButton.setText(if (ui.talking) R.string.talk_stop else R.string.talk_start)
        status.text = ui.status
        status.setTextColor(ContextCompat.getColor(this, ui.statusColor))
        device.text = ui.device
        air.text = ui.air ?: getString(R.string.air_unknown)
        car.text = ui.car
        voiceButton.setText(if (ui.voiceInCar) R.string.voice_app else R.string.voice_device)
        pairing.visibility = if (ui.pairing) View.VISIBLE else View.GONE
        testButtons.forEach { it.visibility = if (ui.driving) View.GONE else View.VISIBLE }
    }

    private fun toggleTalk() {
        val s = service ?: return
        if (s.talking) {
            s.endTalk()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        s.startTalk()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) service?.startTalk()
    }

    /**
     * 시험용(디버그 빌드만). 마이크가 없는 개발 PC, 에뮬레이터에서 못 바꾸는 차 값을 대신한다.
     *   adb shell "am start -n io.snowbot.hub/.MainActivity --es chat '나 좀 졸려'"     글자로 말 걸기
     *   adb shell "am start -n io.snowbot.hub/.MainActivity --es car 'gear=D add=118 attention=1'"
     *     gear=P|R|N|D 기어, add=분 연속 운전 시간 더하기, attention=1|0 주의 경고등
     *     turn=L|R|0 방향지시등, steer=도 핸들 각도, wiper=1|0, beam=1|0 전조등, cruise=1|0 크루즈 작동
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (service == null) pendingIntent = intent else debug(intent)
    }

    private fun debug(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val s = service ?: return
        intent.getStringExtra("car")?.let { s.pretendCar(it) }
        val text = intent.getStringExtra("chat")?.let(PleosVoice::fixName)
        if (text.isNullOrBlank()) return
        heard.visibility = View.VISIBLE
        heard.text = getString(R.string.talk_heard, text)
        s.chat(text)
    }

    companion object {
        private const val REQ_MIC = 1
    }
}
