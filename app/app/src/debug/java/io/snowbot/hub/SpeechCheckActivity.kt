package io.snowbot.hub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/**
 * Pleos TextToSpeech·SpeechToText SDK 가 에뮬레이터에서 실제로 되는지 보는 시험 화면 (debug 빌드 전용).
 * 결과는 화면과 logcat(태그 SpeechCheck)에 같이 남긴다. adb 로 바로 시험할 수 있게 intent 값으로도 움직인다:
 *   --es action tts|stt_file|stt_mic   --es mode ON_DEVICE|HYBRID|SERVER   --es text "읽을 글"   --es file 파일이름(files/ 안)
 * 서버 모드(HYBRID·SERVER)는 Playground 앱 등록이 필요해 여기서는 등록하지 않는다 — 등록 없이 어떻게 되는지만 본다.
 */
class SpeechCheckActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var startedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this).apply { textSize = 16f; setPadding(24, 24, 24, 24) }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply { text = "TTS 말하기"; setOnClickListener { speak(TtsMode.ON_DEVICE, SAMPLE) } })
            addView(Button(context).apply { text = "파일 인식"; setOnClickListener { recognizeFile(SttMode.ON_DEVICE, SAMPLE_FILE) } })
            addView(Button(context).apply { text = "마이크 인식"; setOnClickListener { recognizeMic(SttMode.ON_DEVICE) } })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(buttons)
            addView(ScrollView(context).apply { addView(logView) })
        })
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onDestroy() {
        tts?.release()
        stt?.release()
        super.onDestroy()
    }

    private fun handle(intent: Intent) {
        val mode = intent.getStringExtra("mode") ?: "ON_DEVICE"
        when (intent.getStringExtra("action")) {
            "tts" -> speak(TtsMode.valueOf(mode), intent.getStringExtra("text") ?: SAMPLE)
            "stt_file" -> recognizeFile(SttMode.valueOf(mode), intent.getStringExtra("file") ?: SAMPLE_FILE)
            "stt_mic" -> recognizeMic(SttMode.valueOf(mode))
        }
    }

    // ---- TextToSpeech ----

    private fun speak(mode: TtsMode, text: String) {
        tts?.release()
        startedAt = SystemClock.elapsedRealtime()
        log("TTS[$mode] 초기화 시작")
        var peakRms = 0.0
        val engine = TextToSpeech(applicationContext, mode)
        tts = engine
        engine.initialize(object : TtsInit {
            override fun onSuccess() {
                log("TTS 초기화 성공")
                engine.addEventListener(object : EventListener {
                    override fun onReady() = log("TTS onReady")
                    override fun onStart() = log("TTS onStart (재생 시작)")
                    override fun onUpdatedRms(rms: Double) { if (rms > peakRms) peakRms = rms }
                    override fun onDone() = log("TTS onDone (재생 끝) 최대 RMS=%.1f".format(peakRms))
                    override fun onStop() = log("TTS onStop")
                    override fun onError(errorType: TtsError) = log("TTS onError $errorType")
                })
                engine.setLocale(Locale.KOREA)
                log("TTS speak: $text")
                engine.speak(text, true)
            }
            override fun onFailure(errorType: TtsError) = log("TTS 초기화 실패 $errorType")
        })
    }

    // ---- SpeechToText ----

    private fun newStt(mode: SttMode, onReady: (SpeechToText) -> Unit) {
        stt?.release()
        startedAt = SystemClock.elapsedRealtime()
        log("STT[$mode] 초기화 시작")
        val engine = SpeechToText(applicationContext, mode)
        stt = engine
        engine.initialize(object : SttInit {
            override fun onSuccess() {
                log("STT 초기화 성공")
                engine.addListener(object : ResultListener {
                    override fun onReady() = log("STT onReady")
                    override fun onStartedRecognition() = log("STT onStartedRecognition")
                    override fun onUpdated(stt: String, completed: Boolean) =
                        log(if (completed) "STT 최종: $stt" else "STT 중간: $stt")
                    override fun onUpdatedEpdData(on: Long, off: Long) = log("STT 음성 구간 on=$on off=$off")
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onEndedRecognition() = log("STT onEndedRecognition")
                    override fun onError(errorType: SttError) = log("STT onError $errorType")
                })
                engine.setLocale(Locale.KOREA)
                onReady(engine)
            }
            override fun onFailure(errorType: SttError) = log("STT 초기화 실패 $errorType")
        })
    }

    /** files/ 안의 16kHz·16비트·모노 PCM 을 실시간 속도(100ms 씩)로 넣는다. ON_DEVICE 에서만 된다(Pleos 문서). */
    private fun recognizeFile(mode: SttMode, name: String) {
        val file = File(filesDir, name)
        if (!file.exists()) { log("파일 없음: ${file.path}"); return }
        newStt(mode) { engine ->
            val pcm = file.readBytes()
            log("STT 파일 보내기 ${pcm.size} byte (${pcm.size / 32} ms)")
            Thread {
                pcm.asList().chunked(BYTES_PER_100_MS).forEach { chunk ->
                    engine.sendAudio(chunk.toByteArray())
                    Thread.sleep(100L)
                }
                engine.completeAudioSend()
                log("STT 파일 보내기 끝")
            }.start()
        }
    }

    private fun recognizeMic(mode: SttMode) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            log("마이크 권한을 허용한 뒤 다시 누르세요")
            return
        }
        newStt(mode) { engine ->
            log("STT 마이크 듣기 시작 — 말해 보세요")
            engine.request()
        }
    }

    private fun log(line: String) {
        val t = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
        val text = "[%5.2fs] %s".format(t, line)
        Log.i(TAG, text)
        runOnUiThread { logView.append(text + "\n") }
    }

    companion object {
        private const val TAG = "SpeechCheck"
        private const val BYTES_PER_100_MS = 3_200   // 16kHz × 2byte × 0.1초
        private const val SAMPLE = "안녕하세요. 저는 스노봇이에요. 오늘도 안전 운전하세요."
        private const val SAMPLE_FILE = "stt_sample_ko.pcm"
    }
}
