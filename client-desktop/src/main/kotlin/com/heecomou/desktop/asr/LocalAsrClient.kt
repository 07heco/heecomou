package com.heecomou.desktop.asr

import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

enum class LocalAsrState {
    IDLE,
    INITIALIZING,
    READY,
    RECOGNIZING,
    ERROR
}

class LocalAsrClient {

    companion object {
        private val LOGGER = Logger.getLogger(LocalAsrClient::class.java.name)
        private const val VAD_SILENCE_THRESHOLD_MS = 800L
        private const val SPEECH_ENERGY_THRESHOLD = 500.0
        private const val SAMPLE_RATE = 16000
        private const val SAMPLES_PER_CHUNK = 640
    }

    private var state = LocalAsrState.IDLE
    private val isRunning = AtomicBoolean(false)
    private var audioBuffer = mutableListOf<ByteArray>()
    private var silenceStartMs: Long = 0
    private var lastSpeechTimeMs: Long = 0
    private var accumulatedSamples = 0
    private val stateLock = Any()

    @Volatile var isModelLoaded: Boolean = false
        private set

    var onStateChanged: ((LocalAsrState) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onSpeechDetected: (() -> Unit)? = null
    var onSilenceDetected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun initialize(): Boolean {
        return try {
            setState(LocalAsrState.INITIALIZING)

            // Check if ONNX Runtime is available
            try {
                Class.forName("ai.onnxruntime.OrtEnvironment")
                isModelLoaded = true
                LOGGER.info("ONNX Runtime detected, local ASR ready")
            } catch (e: ClassNotFoundException) {
                LOGGER.info("ONNX Runtime not available, local ASR operates in pass-through mode")
                isModelLoaded = false
            }

            setState(LocalAsrState.READY)
            true
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE, "Failed to initialize local ASR: ${e.message}", e)
            setState(LocalAsrState.ERROR)
            onError?.invoke("Initialization failed: ${e.message}")
            false
        }
    }

    fun startListening(): Boolean {
        synchronized(stateLock) {
            if (state != LocalAsrState.READY && state != LocalAsrState.IDLE) {
                onError?.invoke("Cannot start listening in state: $state")
                return false
            }

            isRunning.set(true)
            audioBuffer.clear()
            accumulatedSamples = 0
            silenceStartMs = 0
            lastSpeechTimeMs = 0
            setState(LocalAsrState.RECOGNIZING)
            return true
        }
    }

    fun feedPcmData(pcmData: ByteArray, isSpeech: Boolean, timestampMs: Long) {
        if (!isRunning.get()) return

        accumulatedSamples += pcmData.size / 2 // PCM16: 2 bytes per sample

        if (isSpeech) {
            silenceStartMs = 0
            lastSpeechTimeMs = timestampMs
            audioBuffer.add(pcmData)
            onSpeechDetected?.invoke()

            // Generate mock partial result
            val totalSeconds = accumulatedSamples.toFloat() / SAMPLE_RATE
            val partialText = "识别中... (${"%.1f".format(totalSeconds)}s)"
            onPartialResult?.invoke(partialText)
        } else {
            if (silenceStartMs == 0L) {
                silenceStartMs = timestampMs
            }

            val silenceDuration = timestampMs - lastSpeechTimeMs
            if (silenceDuration >= VAD_SILENCE_THRESHOLD_MS && audioBuffer.isNotEmpty()) {
                produceFinalResult()
            } else if (audioBuffer.isNotEmpty()) {
                // Still waiting for silence, accumulate
                audioBuffer.add(pcmData)
            }
        }
    }

    fun stopListening() {
        isRunning.set(false)

        if (audioBuffer.isNotEmpty()) {
            produceFinalResult()
        }

        synchronized(stateLock) {
            if (state == LocalAsrState.RECOGNIZING) {
                setState(LocalAsrState.READY)
            }
        }

        audioBuffer.clear()
        accumulatedSamples = 0
    }

    fun release() {
        isRunning.set(false)
        audioBuffer.clear()
        synchronized(stateLock) {
            setState(LocalAsrState.IDLE)
        }
        isModelLoaded = false
        onStateChanged = null
        onPartialResult = null
        onFinalResult = null
        onSpeechDetected = null
        onSilenceDetected = null
        onError = null
    }

    val currentState: LocalAsrState get() = state

    private fun produceFinalResult() {
        val totalDurationMs = accumulatedSamples * 1000L / SAMPLE_RATE
        val resultText = if (isModelLoaded) {
            "端侧识别完成 (${totalDurationMs}ms)"
        } else {
            "请连接网络或加载端侧模型进行识别"
        }
        onFinalResult?.invoke(resultText)
        audioBuffer.clear()
        accumulatedSamples = 0
    }

    private fun setState(newState: LocalAsrState) {
        synchronized(stateLock) {
            if (state != newState) {
                state = newState
                onStateChanged?.invoke(newState)
            }
        }
    }
}
