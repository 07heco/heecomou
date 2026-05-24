package com.heecomou.ime.asr

import android.content.Context
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

enum class LocalAsrClientState {
    IDLE,
    INITIALIZING,
    READY,
    RECOGNIZING,
    ERROR
}

class LocalAsrClient(
    private val context: Context
) {
    companion object {
        private const val TAG = "LocalAsrClient"
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_TIMEOUT_MS = 1500L
    }

    private var engine: LocalAsrEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recording = AtomicBoolean(false)
    private var recognitionJob: Job? = null

    private val pcmBuffer = ByteArrayOutputStream()
    private var lastSpeechTimeMs = 0L
    private var totalSamples = 0L

    var onStateChanged: ((LocalAsrClientState) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var state: LocalAsrClientState = LocalAsrClientState.IDLE
        set(value) {
            field = value
            onStateChanged?.invoke(value)
        }

    val currentState: LocalAsrClientState get() = state

    fun initialize() {
        if (state == LocalAsrClientState.READY || state == LocalAsrClientState.INITIALIZING) return

        state = LocalAsrClientState.INITIALIZING

        scope.launch {
            try {
                val engine = LocalAsrEngine(context)
                if (!engine.isModelAvailable()) {
                    state = LocalAsrClientState.ERROR
                    onError?.invoke("端侧模型文件未找到，请先放置 ONNX 模型到 assets/models/")
                    return@launch
                }

                withContext(Dispatchers.Default) {
                    engine.init()
                }

                this@LocalAsrClient.engine = engine
                state = LocalAsrClientState.READY
            } catch (e: Exception) {
                state = LocalAsrClientState.ERROR
                onError?.invoke("端侧引擎初始化失败: ${e.message}")
            }
        }
    }

    fun startListening() {
        if (state != LocalAsrClientState.READY) {
            onError?.invoke("端侧引擎未就绪，请先调用 initialize()")
            return
        }

        recording.set(true)
        pcmBuffer.reset()
        totalSamples = 0L
        lastSpeechTimeMs = 0L
        state = LocalAsrClientState.RECOGNIZING
    }

    fun feedPcmData(pcmData: ByteArray, isSpeech: Boolean, timestampMs: Long) {
        if (!recording.get() || state != LocalAsrClientState.RECOGNIZING) return

        if (isSpeech) {
            pcmBuffer.write(pcmData)
            totalSamples += pcmData.size / 2
            lastSpeechTimeMs = timestampMs
        }
    }

    fun stopListening() {
        if (!recording.getAndSet(false)) return

        val audioBytes = pcmBuffer.toByteArray()
        if (audioBytes.isEmpty()) {
            state = LocalAsrClientState.READY
            return
        }

        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            try {
                val result = recognizeSync(audioBytes)
                onFinalResult?.invoke(result)
                state = LocalAsrClientState.READY
            } catch (e: Exception) {
                onError?.invoke("端侧识别失败: ${e.message}")
                state = LocalAsrClientState.READY
            }
        }
    }

    fun shouldAutoStop(currentTimeMs: Long): Boolean {
        if (!recording.get()) return false
        if (lastSpeechTimeMs == 0L) return false
        return (currentTimeMs - lastSpeechTimeMs) > SILENCE_TIMEOUT_MS
    }

    private suspend fun recognizeSync(audioBytes: ByteArray): String {
        val engine = this.engine ?: throw IllegalStateException("Engine not initialized")

        val shortSamples = ShortArray(audioBytes.size / 2)
        for (i in shortSamples.indices) {
            val low = audioBytes[i * 2].toInt() and 0xFF
            val high = audioBytes[i * 2 + 1].toInt() and 0xFF
            shortSamples[i] = ((high shl 8) or low).toShort()
        }

        val floatSamples = FloatArray(shortSamples.size) {
            shortSamples[it].toFloat() / Short.MAX_VALUE.toFloat()
        }

        return withContext(Dispatchers.Default) {
            val result = engine.recognize(floatSamples, "zh")
            result.text
        }
    }

    fun isInitialized(): Boolean {
        return state == LocalAsrClientState.READY
    }

    fun release() {
        recording.set(false)
        recognitionJob?.cancel()
        scope.cancel()
        engine?.release()
        engine = null
        state = LocalAsrClientState.IDLE
        onStateChanged = null
        onPartialResult = null
        onFinalResult = null
        onError = null
    }
}
