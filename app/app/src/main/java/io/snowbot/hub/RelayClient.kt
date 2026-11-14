package io.snowbot.hub

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * SnowBot 중계 서버(server/README.md)에 앱으로 붙는다.
 * 첫 메시지로 auth(token) → auth_ok 를 받으면 연결됨. 끊기면 1초부터 최대 30초 간격으로 다시 붙는다.
 * 콜백은 모두 메인 스레드에서 부른다.
 */
class RelayClient(private val serverUrl: String, private val listener: Listener) {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, UNAUTHORIZED }

    interface Listener {
        fun onState(state: State)
        fun onMessage(msg: JSONObject)
        /** 목소리 시작·끝(audio_start / audio_end)과 그 사이 조각(PCM).
         *  순서가 섞이지 않게 셋 다 받은 스레드(네트워크)에서 바로 부른다. */
        fun onAudioStart(sampleRate: Int)
        fun onAudio(chunk: ByteArray)
        fun onAudioEnd(cancelled: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var token: String? = null
    private var running = false
    private var attempt = 0

    fun start(token: String) {
        this.token = token
        running = true
        attempt = 0
        connect()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        socket?.close(1000, null)
        socket = null
    }

    fun send(msg: JSONObject): Boolean = socket?.send(msg.toString()) ?: false

    /** 마이크 소리 조각(PCM). 어느 스레드에서 불러도 된다. */
    fun sendBytes(chunk: ByteArray): Boolean = socket?.send(chunk.toByteString()) ?: false

    private fun connect() {
        listener.onState(State.CONNECTING)
        val request = Request.Builder().url(wsUrl(serverUrl)).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "auth").put("token", token).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (webSocket === socket) when (msg.optString("type")) {
                    "audio_start" -> listener.onAudioStart(msg.optInt("sample_rate", 16000))
                    "audio_end" -> listener.onAudioEnd(msg.optBoolean("cancelled") || msg.has("error"))
                }
                main.post {
                    if (webSocket !== socket) return@post
                    if (msg.optString("type") == "auth_ok") {
                        attempt = 0
                        listener.onState(State.CONNECTED)
                    }
                    listener.onMessage(msg)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket === socket) listener.onAudio(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                main.post { closed(webSocket, code) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { closed(webSocket, null) }
            }
        })
    }

    private fun closed(webSocket: WebSocket, code: Int?) {
        if (webSocket !== socket) return
        socket = null
        if (code == CLOSE_DENIED) {
            running = false
            listener.onState(State.UNAUTHORIZED)
            return
        }
        listener.onState(State.DISCONNECTED)
        if (running) {
            val delay = min(30_000L, 1_000L shl min(attempt, 5))
            attempt++
            main.postDelayed({ if (running) connect() }, delay)
        }
    }

    companion object {
        private const val CLOSE_DENIED = 4003
        private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()

        private fun wsUrl(server: String) =
            server.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/ws"

        /** QR 스티커의 기기 번호·등록 코드로 짝을 맺고 토큰을 받는다. 네트워크를 쓰므로 메인 스레드에서 부르지 않는다. */
        /** token 이 있으면 같은 앱에 봇을 더한다(서버가 새 토큰을 주지 않음 → 기존 토큰을 돌려준다). */
        fun pair(server: String, deviceId: String, claimCode: String, name: String, token: String? = null): Result<String> = runCatching {
            val body = JSONObject()
                .put("device_id", deviceId)
                .put("claim_code", claimCode)
                .put("kind", "car")
                .put("name", name)
                .toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(server.trimEnd('/') + "/api/pair").post(body)
                .apply { if (token != null) header("Authorization", "Bearer $token") }.build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                check(response.isSuccessful) {
                    if (response.code == 403) "기기 번호나 등록 코드가 맞지 않습니다" else "서버 오류 (${response.code})"
                }
                val json = JSONObject(text)
                if (json.isNull("token")) token ?: error("서버가 토큰을 주지 않았습니다") else json.getString("token")
            }
        }
    }
}
