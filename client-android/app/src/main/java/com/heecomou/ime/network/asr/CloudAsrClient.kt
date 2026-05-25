package com.heecomou.ime.network.asr

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AsrSessionStarted(
    val type: String,
    @SerializedName("session_id") val sessionId: String,
    val format: AsrAudioFormat? = null
)

data class AsrAudioFormat(
    @SerializedName("sample_rate") val sampleRate: Int,
    @SerializedName("num_channels") val numChannels: Int,
    @SerializedName("bit_depth") val bitDepth: Int
)

data class AsrSamplesReceived(
    val type: String,
    @SerializedName("sample_count") val sampleCount: Int,
    @SerializedName("total_samples") val totalSamples: Int
)

data class AsrFinalResult(
    val type: String,
    val text: String,
    val confidence: Double
)

data class AsrPartialResult(
    val type: String,
    val text: String
)

enum class AsrClientState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CLOSED
}

class CloudAsrClient(
    private val gatewayHost: String = "10.0.2.2",
    private val gatewayPort: Int = 8080,
    private val connectTimeoutMs: Long = 10_000L
) {
    companion object {
        private const val WS_PATH = "/ws/audio"
    }

    private var webSocket: WebSocket? = null
    private val state = AtomicBoolean(false)

    var onSessionStarted: ((AsrSessionStarted) -> Unit)? = null
    var onSamplesReceived: ((AsrSamplesReceived) -> Unit)? = null
    var onFinalResult: ((String, Double) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onConnectionFailed: ((String) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val isConnected: Boolean get() = state.get()

    fun connect() {
        if (state.get()) return

        state.set(true)
        val client = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val wsUrl = "ws://$gatewayHost:$gatewayPort$WS_PATH"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                state.set(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    com.google.gson.Gson().fromJson(text, Map::class.java)?.let { msg ->
                        when (msg["type"]) {
                            "session_started" -> {
                                val session = com.google.gson.Gson().fromJson(
                                    text, AsrSessionStarted::class.java
                                )
                                onSessionStarted?.invoke(session)
                            }
                            "samples_received" -> {
                                val sr = com.google.gson.Gson().fromJson(
                                    text, AsrSamplesReceived::class.java
                                )
                                onSamplesReceived?.invoke(sr)
                            }
                            "final_result" -> {
                                val fr = com.google.gson.Gson().fromJson(
                                    text, AsrFinalResult::class.java
                                )
                                onFinalResult?.invoke(fr.text, fr.confidence)
                            }
                            "partial_result" -> {
                                val pr = com.google.gson.Gson().fromJson(
                                    text, AsrPartialResult::class.java
                                )
                                onPartialResult?.invoke(pr.text)
                            }
                            else -> { /* unknown message type */ }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                state.set(false)
                val msg = response?.message ?: t.message ?: "Unknown error"
                onConnectionFailed?.invoke(msg)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                state.set(false)
                onDisconnected?.invoke()
            }
        })
    }

    fun sendAudio(pcmData: ByteArray): Boolean {
        if (!state.get()) return false
        val buffer = okio.Buffer()
        buffer.write(pcmData)
        return webSocket?.send(buffer.readByteString()) ?: false
    }

    fun disconnect() {
        state.set(false)
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    fun release() {
        disconnect()
        onSessionStarted = null
        onSamplesReceived = null
        onFinalResult = null
        onPartialResult = null
        onConnectionFailed = null
        onDisconnected = null
        onError = null
    }
}
