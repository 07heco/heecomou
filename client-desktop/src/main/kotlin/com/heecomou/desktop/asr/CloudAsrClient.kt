package com.heecomou.desktop.asr

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class DesktopAsrSessionStarted(
    val type: String,
    @SerializedName("session_id") val sessionId: String,
    val format: DesktopAsrAudioFormat? = null
)

data class DesktopAsrAudioFormat(
    @SerializedName("sample_rate") val sampleRate: Int,
    @SerializedName("num_channels") val numChannels: Int,
    @SerializedName("bit_depth") val bitDepth: Int
)

data class DesktopAsrSamplesReceived(
    val type: String,
    @SerializedName("sample_count") val sampleCount: Int,
    @SerializedName("total_samples") val totalSamples: Int
)

data class DesktopAsrResult(
    val type: String,
    val text: String,
    @SerializedName("is_final") val isFinal: Boolean,
    val confidence: Float = 0f
)

enum class DesktopAsrClientState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CLOSED
}

class CloudAsrClient(
    private val gatewayHost: String = "117.72.201.26",
    private val gatewayPort: Int = 8080,
    private val connectTimeoutMs: Long = 10_000L
) {
    companion object {
        private const val WS_PATH = "/ws/audio"
    }

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val state = AtomicBoolean(false)

    var onSessionStarted: ((DesktopAsrSessionStarted) -> Unit)? = null
    var onSamplesReceived: ((DesktopAsrSamplesReceived) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String, Float) -> Unit)? = null
    var onConnectionFailed: ((String) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val isConnected: Boolean get() = state.get()

    fun connect() {
        if (state.get()) return

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
                    val msg = gson.fromJson(text, Map::class.java) ?: return
                    when (msg["type"]) {
                        "session_started" -> {
                            val session = gson.fromJson(text, DesktopAsrSessionStarted::class.java)
                            onSessionStarted?.invoke(session)
                        }
                        "samples_received" -> {
                            val sr = gson.fromJson(text, DesktopAsrSamplesReceived::class.java)
                            onSamplesReceived?.invoke(sr)
                        }
                        "partial_result" -> {
                            val result = gson.fromJson(text, DesktopAsrResult::class.java)
                            onPartialResult?.invoke(result.text)
                        }
                        "final_result" -> {
                            val result = gson.fromJson(text, DesktopAsrResult::class.java)
                            onFinalResult?.invoke(result.text, result.confidence)
                        }
                        "error" -> {
                            val errorMsg = msg["message"] as? String ?: "Unknown error"
                            onError?.invoke(errorMsg)
                        }
                        else -> { /* unknown message type */ }
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
        val buffer = Buffer()
        buffer.write(pcmData)
        return try {
            webSocket?.send(buffer.readByteString()) ?: false
        } catch (e: Exception) {
            onError?.invoke("Failed to send audio: ${e.message}")
            false
        }
    }

    fun disconnect() {
        state.set(false)
        try {
            webSocket?.close(1000, "Client disconnect")
        } catch (_: Exception) {
        }
        webSocket = null
    }

    fun release() {
        disconnect()
        onSessionStarted = null
        onSamplesReceived = null
        onPartialResult = null
        onFinalResult = null
        onConnectionFailed = null
        onDisconnected = null
        onError = null
    }
}
