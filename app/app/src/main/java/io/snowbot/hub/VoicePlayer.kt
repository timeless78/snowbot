package io.snowbot.hub

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors

/**
 * 서버가 흘려보내는 스노봇 목소리(PCM 16kHz 16비트 모노)를 받는 대로 차 스피커로 재생한다.
 * 말하는 동안만 오디오 포커스를 잠깐 빌리고(음악은 작아짐), 끝나면 돌려준다(Pleos 오디오 규칙).
 * 쓰기는 한 줄 작업 스레드에서 한다 — AudioTrack.write 가 화면을 멈추지 않게.
 */
class VoicePlayer(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val worker = Executors.newSingleThreadExecutor()
    private var track: AudioTrack? = null
    private var rate = 16000
    private var framesWritten = 0L

    fun start(sampleRate: Int) = worker.execute {
        finish(immediately = true)
        audio.requestAudioFocus(focus)
        rate = sampleRate
        framesWritten = 0
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, sampleRate))   // 약 0.5초
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }

    fun write(chunk: ByteArray) = worker.execute {
        val t = track ?: return@execute
        val n = t.write(chunk, 0, chunk.size)
        if (n > 0) framesWritten += n / 2
    }

    /** 받은 소리를 다 재생한 뒤 멈춘다. cancelled 면 바로 멈춘다. */
    fun end(cancelled: Boolean) = worker.execute { finish(immediately = cancelled) }

    fun release() {
        worker.execute { finish(immediately = true) }
        worker.shutdown()
    }

    private fun finish(immediately: Boolean) {
        val t = track ?: return
        track = null
        if (!immediately) {
            // 써 둔 소리가 다 나올 때까지 기다린다 (재생 위치가 쓴 양에 닿을 때까지, 넉넉히 1초 더해 끊는다)
            val deadline = System.currentTimeMillis() + framesWritten * 1000 / rate + 1000
            while ((t.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < framesWritten &&
                System.currentTimeMillis() < deadline) Thread.sleep(20)
        }
        t.pause()
        t.flush()
        t.release()
        audio.abandonAudioFocusRequest(focus)
    }
}
