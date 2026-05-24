package com.heecomou.ime.asr

class NoiseDetector {
    companion object {
        private const val QUIET_THRESHOLD_DB = 45f
        private const val MODERATE_THRESHOLD_DB = 65f
    }

    enum class Level(val label: String) {
        QUIET("安静"),
        MODERATE("中等"),
        LOUD("嘈杂")
    }

    fun classify(rmsDb: Float): Level {
        return when {
            rmsDb < QUIET_THRESHOLD_DB -> Level.QUIET
            rmsDb < MODERATE_THRESHOLD_DB -> Level.MODERATE
            else -> Level.LOUD
        }
    }

    fun estimateNoiseDb(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f

        var sumSq = 0.0
        for (s in samples) {
            sumSq += (s.toDouble() * s.toDouble())
        }
        val rms = Math.sqrt(sumSq / samples.size)
        if (rms < 1e-10) return -60f

        return (20.0 * Math.log10(rms)).toFloat()
    }
}
