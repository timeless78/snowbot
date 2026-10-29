package io.snowbot.hub

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * 말하기 버튼을 누른 동안 차 마이크 소리를 PCM 16kHz 모노 16비트로 0.1초씩 잘라 넘긴다.
 * 조각은 서버의 듣기(STT)로 바로 보낸다 — server/README.md 의 listen_start / 바이너리 / listen_end.
 * onChunk 는 녹음 스레드에서 부른다. RECORD_AUDIO 권한은 부르는 쪽이 먼저 받는다.
 */
class MicRecorder(private val onChunk: (ByteArray) -> Unit) {

    @Volatile private var running = false
    private var worker: Thread? = null

    val isRecording get() = running

    /** 마이크를 열지 못하면 false. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minSize <= 0) return false
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minSize, CHUNK_BYTES * 4))
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        running = true
        record.startRecording()
        worker = thread(name = "snowbot-mic") {
            val buf = ByteArray(CHUNK_BYTES)
            try {
                while (running) {
                    var filled = 0
                    while (running && filled < buf.size) {
                        val n = record.read(buf, filled, buf.size - filled)
                        if (n <= 0) break
                        filled += n
                    }
                    if (filled == 0) break
                    onChunk(buf.copyOf(filled))
                }
            } finally {
                record.stop()
                record.release()
                running = false
            }
        }
        return true
    }

    /** 녹음을 멈추고, 마지막 조각까지 넘긴 뒤 돌아온다. */
    fun stop() {
        running = false
        worker?.join(500)
        worker = null
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHUNK_BYTES = SAMPLE_RATE * 2 / 10   // 0.1초
    }
}
