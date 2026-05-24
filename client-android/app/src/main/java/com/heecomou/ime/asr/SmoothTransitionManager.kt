package com.heecomou.ime.asr

import java.util.concurrent.atomic.AtomicBoolean

class SmoothTransitionManager {

    enum class TransitionType {
        CLOUD_TO_LOCAL,
        LOCAL_TO_CLOUD
    }

    private var currentEngine: AsrEngineMode = AsrEngineMode.CLOUD
    private val transitioning = AtomicBoolean(false)
    private var pendingAudioBuffer = mutableListOf<FloatArray>()

    data class TransitionConfig(
        val crossfadeDurationMs: Long = 500L,
        val bufferAudioDuringTransition: Boolean = true
    )

    private val config = TransitionConfig()

    var onTransitionStart: ((TransitionType) -> Unit)? = null
    var onTransitionComplete: ((AsrEngineMode) -> Unit)? = null

    fun requestTransition(targetEngine: AsrEngineMode): Boolean {
        if (targetEngine == currentEngine) return true
        if (!transitioning.compareAndSet(false, true)) return false

        val transitionType = if (targetEngine == AsrEngineMode.LOCAL) {
            TransitionType.CLOUD_TO_LOCAL
        } else {
            TransitionType.LOCAL_TO_CLOUD
        }

        onTransitionStart?.invoke(transitionType)

        if (config.bufferAudioDuringTransition) {
            pendingAudioBuffer.clear()
        }

        currentEngine = targetEngine
        transitioning.set(false)
        onTransitionComplete?.invoke(targetEngine)

        return true
    }

    fun bufferAudioDuringTransition(audioData: FloatArray) {
        if (transitioning.get() && config.bufferAudioDuringTransition) {
            pendingAudioBuffer.add(audioData.copyOf())
        }
    }

    fun getAndClearBuffer(): List<FloatArray> {
        val buffer = pendingAudioBuffer.toList()
        pendingAudioBuffer.clear()
        return buffer
    }

    fun getCurrentEngine(): AsrEngineMode = currentEngine

    fun isTransitioning(): Boolean = transitioning.get()

    fun reset() {
        pendingAudioBuffer.clear()
        transitioning.set(false)
        currentEngine = AsrEngineMode.CLOUD
    }
}
