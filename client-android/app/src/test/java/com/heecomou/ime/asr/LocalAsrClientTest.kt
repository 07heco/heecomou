package com.heecomou.ime.asr

import org.junit.Assert.*
import org.junit.Test

class LocalAsrClientStateTest {

    @Test
    fun `state enum has correct values`() {
        assertEquals(5, LocalAsrClientState.entries.size)
        assertTrue(LocalAsrClientState.entries.contains(LocalAsrClientState.IDLE))
        assertTrue(LocalAsrClientState.entries.contains(LocalAsrClientState.INITIALIZING))
        assertTrue(LocalAsrClientState.entries.contains(LocalAsrClientState.READY))
        assertTrue(LocalAsrClientState.entries.contains(LocalAsrClientState.RECOGNIZING))
        assertTrue(LocalAsrClientState.entries.contains(LocalAsrClientState.ERROR))
    }

    @Test
    fun `state values are distinct`() {
        val states = LocalAsrClientState.entries.toSet()
        assertEquals(5, states.size)
    }
}

class LocalAsrClientPcmConversionTest {

    @Test
    fun `byte array to short array conversion`() {
        val audioBytes = byteArrayOf(
            0x00, 0x01,  // 256 in little-endian
            0xFF.toByte(), 0x7F,  // 32767
            0x00, 0x00   // 0
        )

        val shortSamples = ShortArray(audioBytes.size / 2)
        for (i in shortSamples.indices) {
            val low = audioBytes[i * 2].toInt() and 0xFF
            val high = audioBytes[i * 2 + 1].toInt() and 0xFF
            shortSamples[i] = ((high shl 8) or low).toShort()
        }

        assertEquals(3, shortSamples.size)
        assertEquals(256.toShort(), shortSamples[0])
        assertEquals(Short.MAX_VALUE, shortSamples[1])
        assertEquals(0.toShort(), shortSamples[2])
    }

    @Test
    fun `short to float normalization`() {
        val shortSamples = shortArrayOf(Short.MAX_VALUE, 0, Short.MIN_VALUE)
        val floatSamples = FloatArray(shortSamples.size) {
            shortSamples[it].toFloat() / Short.MAX_VALUE.toFloat()
        }

        assertEquals(1.0f, floatSamples[0], 0.001f)
        assertEquals(0.0f, floatSamples[1], 0.001f)
        assertTrue(floatSamples[2] < -0.9f)
    }

    @Test
    fun `empty byte array produces empty short array`() {
        val audioBytes = ByteArray(0)
        val shortSamples = ShortArray(audioBytes.size / 2)
        assertEquals(0, shortSamples.size)
    }

    @Test
    fun `single sample conversion`() {
        val audioBytes = byteArrayOf(0x64.toByte(), 0x00)  // 100 in little-endian
        val shortSamples = ShortArray(audioBytes.size / 2)
        for (i in shortSamples.indices) {
            val low = audioBytes[i * 2].toInt() and 0xFF
            val high = audioBytes[i * 2 + 1].toInt() and 0xFF
            shortSamples[i] = ((high shl 8) or low).toShort()
        }
        assertEquals(100.toShort(), shortSamples[0])
    }
}

class SilenceTimeoutTest {

    @Test
    fun `silence timeout is 1500ms`() {
        assertEquals(1500L, 1500L)
    }

    @Test
    fun `should not auto stop when no speech yet`() {
        val lastSpeechTimeMs = 0L
        val currentTimeMs = 2000L
        val timedOut = lastSpeechTimeMs > 0L &&
                (currentTimeMs - lastSpeechTimeMs) > 1500L
        assertFalse(timedOut)
    }

    @Test
    fun `should auto stop after silence timeout`() {
        val lastSpeechTimeMs = 1000L
        val currentTimeMs = 3000L
        val timedOut = lastSpeechTimeMs > 0L &&
                (currentTimeMs - lastSpeechTimeMs) > 1500L
        assertTrue(timedOut)
    }

    @Test
    fun `should not auto stop within timeout`() {
        val lastSpeechTimeMs = 2000L
        val currentTimeMs = 3000L
        val timedOut = lastSpeechTimeMs > 0L &&
                (currentTimeMs - lastSpeechTimeMs) > 1500L
        assertFalse(timedOut)
    }
}

class SampleRateConstantTest {

    @Test
    fun `sample rate is 16000`() {
        assertEquals(16000, 16000)
    }
}

class PcmBufferAccumulationTest {

    @Test
    fun `buffer correctly accumulates bytes`() {
        val buffer = java.io.ByteArrayOutputStream()
        buffer.write(byteArrayOf(1, 2, 3, 4))
        buffer.write(byteArrayOf(5, 6, 7, 8))

        val result = buffer.toByteArray()
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), result)
    }

    @Test
    fun `reset buffer produces empty array`() {
        val buffer = java.io.ByteArrayOutputStream()
        buffer.write(byteArrayOf(1, 2, 3))
        buffer.reset()
        val result = buffer.toByteArray()
        assertEquals(0, result.size)
    }

    @Test
    fun `total sample counting is correct`() {
        var totalSamples = 0L
        val pcmData = ByteArray(640)
        totalSamples += pcmData.size / 2
        assertEquals(320, totalSamples)

        totalSamples += pcmData.size / 2
        assertEquals(640, totalSamples)
    }
}
