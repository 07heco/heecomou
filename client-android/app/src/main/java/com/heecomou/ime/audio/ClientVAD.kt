package com.heecomou.ime.audio

import kotlin.math.ln
import kotlin.math.sqrt

class ClientVAD(
    val frameSize: Int = 160,
    val sampleRate: Int = 16000
) {
    companion object {
        private const val REF_PCM = 32768.0
    }

    var speechDetected: Boolean = false
        private set

    private var energyThreshold = -38.0
    private var zcrThreshold = 0.15
    private var minSpeechFrames = 3
    private var hangoverFrames = 8
    private var hangoverCount = 0
    private var speechFrameCount = 0
    private var adaptiveEnabled = true
    private var noiseFloor = -50.0
    private val smoothAlpha = 0.2

    fun setEnergyThreshold(db: Double) { energyThreshold = db }
    fun setZCRThreshold(zcr: Double) { zcrThreshold = zcr }
    fun setAdaptive(enabled: Boolean) { adaptiveEnabled = enabled }

    fun detect(frame: ShortArray): Boolean {
        if (frame.isEmpty()) {
            speechDetected = false
            return false
        }

        val energyDb = computeEnergyDb(frame)
        val zcr = computeZCR(frame)
        val isSpeech = energyDb > energyThreshold || zcr > zcrThreshold

        if (adaptiveEnabled) {
            if (!isSpeech && energyDb < -30) {
                noiseFloor = smoothAlpha * energyDb + (1 - smoothAlpha) * noiseFloor
                val newThreshold = noiseFloor + 12.0
                energyThreshold = when {
                    newThreshold < -45 -> -45.0
                    newThreshold > -25 -> -25.0
                    else -> newThreshold
                }
            }
        }

        if (isSpeech) {
            speechFrameCount++
            if (speechFrameCount >= hangoverFrames) {
                hangoverCount = 15
            }
        } else {
            speechFrameCount = 0
        }

        speechDetected = speechFrameCount >= minSpeechFrames || hangoverCount > 0
        if (!isSpeech && hangoverCount > 0) {
            hangoverCount--
        }

        return speechDetected
    }

    fun detectBuffer(samples: ShortArray): List<Boolean> {
        val results = mutableListOf<Boolean>()
        var i = 0
        while (i + frameSize <= samples.size) {
            val frame = samples.copyOfRange(i, i + frameSize)
            results.add(detect(frame))
            i += frameSize
        }
        return results
    }

    fun reset() {
        speechDetected = false
        hangoverCount = 0
        speechFrameCount = 0
        noiseFloor = -50.0
        energyThreshold = -38.0
    }

    private fun computeEnergyDb(samples: ShortArray): Double {
        var sumSquares = 0.0
        for (s in samples) {
            val v = s.toDouble()
            sumSquares += v * v
        }
        val rms = sqrt(sumSquares / samples.size)
        val safeRms = if (rms < 1.0) 1.0 else rms
        return 20.0 * ln(safeRms / REF_PCM) / ln(10.0)
    }

    private fun computeZCR(samples: ShortArray): Double {
        if (samples.size < 2) return 0.0
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) ||
                (samples[i] < 0 && samples[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / (samples.size - 1)
    }
}
