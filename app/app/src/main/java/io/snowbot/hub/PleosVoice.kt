package io.snowbot.hub

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
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
 * 차의 Pleos 음성 엔진으로 듣고(SpeechToText) 말한다(TextToSpeech). 차 안 엔진(ON_DEVICE)이라 인터넷·앱 등록이 필요 없다.
 * 듣기: listen() → 차 마이크 → 중간 글자는 onHeard(글자, false), 마지막 글자는 한 번만 onHeard(글자, true).
 *   차 안 엔진은 이름을 "스노프"로 알아들어서(11/2 시험 6번 중 6번) fixName 으로 고쳐 넘긴다.
 * 말하기: 서버가 보낸 문장(say)을 차례로 읽는다. 다른 말(id)이 오면 하던 말은 끊는다.
 * 엔진을 못 쓰면(Pleos 가 아닌 기기 등) canListen·canSpeak 가 false 로 남고, 부르는 쪽이 서버 듣기·목소리를 쓴다.
 * 콜백은 모두 메인 스레드에서 부른다.
 */
class PleosVoice(context: Context, private val onHeard: (text: String, final: Boolean) -> Unit) {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null

    var canSpeak = false
        private set
    var canListen = false
        private set
    var isListening = false
        private set

    private var partial = ""
    private var speechId = -1
    private var localId = -2   // 서버 말 id(1부터)와 겹치지 않게 음수
    private val lines = ArrayDeque<String>()
    private var speaking = false
    private val finishLater = Runnable { finishListening() }

    fun start() {
        TextToSpeech(app, TtsMode.ON_DEVICE).also { engine ->
            tts = engine
            engine.initialize(object : TtsInit {
                override fun onSuccess() {
                    main.post {
                        engine.addEventListener(ttsEvents)
                        engine.setLocale(Locale.KOREA)
                        engine.setAudioAttributes(ASSISTANT)
                        canSpeak = true
                    }
                }
                override fun onFailure(errorType: TtsError) { Log.w(TAG, "TTS 초기화 실패 $errorType") }
            })
        }
        SpeechToText(app, SttMode.ON_DEVICE).also { engine ->
            stt = engine
            engine.initialize(object : SttInit {
                override fun onSuccess() {
                    main.post {
                        engine.addListener(sttResults)
                        engine.setLocale(Locale.KOREA)
                        canListen = true
                    }
                }
                override fun onFailure(errorType: SttError) { Log.w(TAG, "STT 초기화 실패 $errorType") }
            })
        }
    }

    fun release() {
        stt?.run { if (isListening) stop(); removeListener(sttResults); release() }
        tts?.run { removeEventListener(ttsEvents); release() }
        stt = null
        tts = null
        canSpeak = false
        canListen = false
    }

    // ---- 듣기 ----

    fun listen(): Boolean {
        val engine = stt ?: return false
        if (!canListen) return false
        if (isListening) return true
        partial = ""
        isListening = true
        engine.request()
        return true
    }

    /** 다 말했어요: 마이크를 닫는다. 엔진이 마지막 글자를 주면 그것을, 안 주면 지금까지 들은 글자를 넘긴다. */
    fun stopListening() {
        if (!isListening) return
        stt?.stop()
        main.postDelayed(finishLater, FINAL_WAIT_MS)
    }

    private fun finishListening() {
        if (!isListening) return
        isListening = false
        main.removeCallbacks(finishLater)
        onHeard(fixName(partial), true)
    }

    private val sttResults = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            main.post {
                if (!isListening) return@post
                partial = stt
                if (completed) finishListening() else onHeard(fixName(stt), false)
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() { main.post(::finishListening) }
        override fun onError(errorType: SttError) {
            Log.w(TAG, "STT 오류 $errorType")
            main.post(::finishListening)
        }
    }

    // ---- 말하기 ----

    /** 서버 없이 앱이 직접 하는 말(인터넷이 끊겼을 때 인사·안전 알림). 하던 말은 끊는다. */
    fun sayLocal(text: String) = say(localId--, text)

    /** 서버가 보낸 문장 하나. 새 id 면 하던 말을 끊고 이 말을 시작한다. */
    fun say(id: Int, text: String) {
        if (id != speechId) {
            stopSpeaking()
            speechId = id
        }
        lines.addLast(text)
        if (!speaking) next()
    }

    /** 서버가 말을 끊었을 때(say_end cancelled). 끝까지 보낸 말은 받은 문장을 다 읽는다. */
    fun sayEnd(id: Int, cancelled: Boolean) {
        if (cancelled && id == speechId) stopSpeaking()
    }

    fun stopSpeaking() {
        lines.clear()
        speechId = -1
        if (speaking) tts?.stop()   // onStop 이 오면 speaking 이 풀린다
    }

    private fun next() {
        val engine = tts ?: return
        val line = lines.removeFirstOrNull() ?: return
        speaking = true
        engine.speak(line, true)
    }

    private val ttsEvents = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = done()
        override fun onStop() = done()
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "TTS 오류 $errorType")
            done()
        }

        private fun done() {
            main.post {
                speaking = false
                next()
            }
        }
    }

    companion object {
        private const val TAG = "PleosVoice"
        private const val FINAL_WAIT_MS = 1_500L   // 마이크를 닫은 뒤 마지막 글자를 기다리는 시간
        private val ASSISTANT = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        private val MISHEARD_NAME = Regex("스노\\s?[프붓폿]|스노우\\s?봇|스노\\s봇")

        /** 차 안 엔진이 잘못 알아듣는 이름을 "스노봇"으로 고친다. "스노보드" 같은 말은 건드리지 않는다. */
        fun fixName(text: String) = MISHEARD_NAME.replace(text, "스노봇")
    }
}
