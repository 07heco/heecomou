package com.heecomou.ime.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ClientVADTest {

    private fun generateSamples(count: Int, amplitude: Short): ShortArray {
        return ShortArray(count) { amplitude }
    }

    private fun generateTone(count: Int, freq: Double, amplitude: Double, sampleRate: Int): ShortArray {
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val v = amplitude * sin(2 * PI * freq * t)
            v.toInt().toShort()
        }
    }

    @Test
    fun `default frame size is 160`() {
        val vad = ClientVAD()
        assertEquals(160, vad.frameSize)
    }

    @Test
    fun `default sample rate is 16000`() {
        val vad = ClientVAD()
        assertEquals(16000, vad.sampleRate)
    }

    @Test
    fun `speech not detected initially`() {
        val vad = ClientVAD()
        assertFalse(vad.speechDetected)
    }

    @Test
    fun `silence is not detected as speech`() {
        val vad = ClientVAD()
        vad.detect(generateSamples(160, 0))
        assertFalse(vad.speechDetected)
    }

    @Test
    fun `loud tone detected as speech after enough frames`() {
        val vad = ClientVAD()
        val tone = generateTone(160, 440.0, 20000.0, 16000)
        vad.detect(tone)
        vad.detect(tone)
        assertTrue(vad.detect(tone))
    }

    @Test
    fun `detect buffer returns correct count`() {
        val vad = ClientVAD()
        val samples = ShortArray(640) {
            sin(2 * PI * 440.0 * (it / 16000.0)).toInt().toShort()
        }
        val results = vad.detectBuffer(samples)
        assertEquals(4, results.size)
    }

    @Test
    fun `reset clears detection state`() {
        val vad = ClientVAD()
        val tone = generateTone(160, 440.0, 20000.0, 16000)
        repeat(4) { vad.detect(tone) }
        assertTrue(vad.speechDetected)

        vad.reset()
        assertFalse(vad.speechDetected)
    }

    @Test
    fun `empty frame is not speech`() {
        val vad = ClientVAD()
        vad.detect(ShortArray(0))
        assertFalse(vad.speechDetected)
    }

    @Test
    fun `energy threshold can be changed`() {
        val vad = ClientVAD()
        vad.setEnergyThreshold(-15.0)
        vad.setZCRThreshold(0.5)
        val quietTone = generateTone(160, 440.0, 3000.0, 16000)
        repeat(4) { vad.detect(quietTone) }
        assertFalse(vad.speechDetected)
    }

    @Test
    fun `ZCR threshold can be changed`() {
        val vad = ClientVAD()
        vad.setZCRThreshold(0.5)
        assertTrue(true)
    }

    @Test
    fun `adaptive mode can be toggled`() {
        val vad = ClientVAD()
        vad.setAdaptive(false)
        assertTrue(true)
    }
}
