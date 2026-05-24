package com.heecomou.ime.asr

import org.junit.Assert.*
import org.junit.Test

class LocalAsrEngineStateTest {

    @Test
    fun `state enum has correct values`() {
        assertEquals(4, LocalAsrEngine.State.entries.size)
        assertTrue(LocalAsrEngine.State.entries.contains(LocalAsrEngine.State.UNINITIALIZED))
        assertTrue(LocalAsrEngine.State.entries.contains(LocalAsrEngine.State.LOADING))
        assertTrue(LocalAsrEngine.State.entries.contains(LocalAsrEngine.State.READY))
        assertTrue(LocalAsrEngine.State.entries.contains(LocalAsrEngine.State.ERROR))
    }

    @Test
    fun `state values are distinct`() {
        val states = LocalAsrEngine.State.entries.toSet()
        assertEquals(4, states.size)
    }
}

class LocalAsrResultTest {

    @Test
    fun `result stores text and duration`() {
        val result = LocalAsrEngine.LocalAsrResult("测试文本", 1500f)
        assertEquals("测试文本", result.text)
        assertEquals(1500f, result.durationMs)
    }

    @Test
    fun `result copy creates new instance`() {
        val result = LocalAsrEngine.LocalAsrResult("hello", 1000f)
        val copied = result.copy(text = "world")
        assertEquals("world", copied.text)
        assertEquals(1000f, copied.durationMs)
        assertEquals("hello", result.text)
    }

    @Test
    fun `result component access works`() {
        val result = LocalAsrEngine.LocalAsrResult("text", 500f)
        val (text, duration) = result
        assertEquals("text", text)
        assertEquals(500f, duration)
    }

    @Test
    fun `result equals works`() {
        val r1 = LocalAsrEngine.LocalAsrResult("a", 1f)
        val r2 = LocalAsrEngine.LocalAsrResult("a", 1f)
        assertEquals(r1, r2)
    }

    @Test
    fun `result hashCode consistent`() {
        val r1 = LocalAsrEngine.LocalAsrResult("a", 1f)
        val r2 = LocalAsrEngine.LocalAsrResult("a", 1f)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun `result toString is meaningful`() {
        val result = LocalAsrEngine.LocalAsrResult("hello", 1000f)
        val str = result.toString()
        assertTrue(str.contains("hello"))
        assertTrue(str.contains("1000"))
    }
}

class MelConversionTest {

    @Test
    fun `hz to mel is monotonic increasing`() {
        val mel0 = hzToMel(0f)
        val mel100 = hzToMel(100f)
        val mel1000 = hzToMel(1000f)
        val mel8000 = hzToMel(8000f)
        assertTrue(mel0 < mel100)
        assertTrue(mel100 < mel1000)
        assertTrue(mel1000 < mel8000)
    }

    @Test
    fun `mel to hz is inverse of hz to mel`() {
        val testFreqs = floatArrayOf(100f, 500f, 1000f, 2000f, 4000f, 8000f)
        for (freq in testFreqs) {
            val mel = hzToMel(freq)
            val recoveredFreq = melToHz(mel)
            assertEquals(freq, recoveredFreq, freq * 0.03f)
        }
    }

    @Test
    fun `hz to mel returns zero for zero frequency`() {
        assertEquals(0f, hzToMel(0f), 0.01f)
    }

    @Test
    fun `mel to hz returns zero for zero mel`() {
        assertEquals(0f, melToHz(0f), 0.01f)
    }

    companion object {
        fun hzToMel(hz: Float): Float {
            return (2595.0 * Math.log10(1.0 + hz / 700.0)).toFloat()
        }

        fun melToHz(mel: Float): Float {
            return (700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)).toFloat()
        }
    }
}

class MelFilterbankTest {

    @Test
    fun `filterbank has correct dimensions`() {
        val filters = createMelFilterbank(80, 512, 16000)
        assertEquals(80, filters.size)
        assertEquals(257, filters[0].size)
    }

    @Test
    fun `filterbank values are non-negative`() {
        val filters = createMelFilterbank(80, 512, 16000)
        for (filter in filters) {
            for (value in filter) {
                assertTrue("Filter value should be non-negative: $value", value >= 0f)
            }
        }
    }

    @Test
    fun `each filter has at least some non-zero values`() {
        val filters = createMelFilterbank(80, 512, 16000)
        for (filter in filters) {
            val hasNonZero = filter.any { it > 0f }
            assertTrue("Each mel filter should have non-zero values", hasNonZero)
        }
    }

    @Test
    fun `first filter has low frequency emphasis`() {
        val filters = createMelFilterbank(80, 512, 16000)
        val firstFilter = filters[0]
        val sorted = firstFilter.sortedArray()
        val maxVal = sorted.last()
        assertTrue(maxVal > 0f)
    }

    @Test
    fun `last filter has high frequency emphasis`() {
        val filters = createMelFilterbank(80, 512, 16000)
        val lastFilter = filters[79]
        val sorted = lastFilter.sortedArray()
        val maxVal = sorted.last()
        assertTrue(maxVal > 0f)
    }

    @Test
    fun `filterbank with different mel count`() {
        val filters20 = createMelFilterbank(20, 512, 16000)
        assertEquals(20, filters20.size)

        val filters40 = createMelFilterbank(40, 512, 16000)
        assertEquals(40, filters40.size)
    }

    companion object {
        fun hzToMel(hz: Float): Float {
            return (2595.0 * Math.log10(1.0 + hz / 700.0)).toFloat()
        }

        fun melToHz(mel: Float): Float {
            return (700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)).toFloat()
        }

        fun createMelFilterbank(
            nMels: Int, fftSize: Int, sampleRate: Int
        ): Array<FloatArray> {
            val nFreqs = fftSize / 2 + 1
            val lowMel = hzToMel(0f)
            val highMel = hzToMel(sampleRate / 2f)
            val melPoints = FloatArray(nMels + 2)

            for (i in melPoints.indices) {
                melPoints[i] = lowMel + (highMel - lowMel) * i / (nMels + 1)
            }

            val freqPoints = FloatArray(nMels + 2) { melToHz(melPoints[it]) }
            val binPoints = IntArray(nMels + 2) {
                Math.round(freqPoints[it] * (fftSize - 1) / sampleRate)
            }

            val filters = Array(nMels) { FloatArray(nFreqs) }

            for (m in 1..nMels) {
                val start = binPoints[m - 1]
                val center = binPoints[m]
                val end = binPoints[m + 1]

                for (k in start until center) {
                    if (k < nFreqs) {
                        filters[m - 1][k] = (k - start).toFloat() / (center - start).toFloat()
                    }
                }
                for (k in center until end) {
                    if (k < nFreqs) {
                        filters[m - 1][k] = (end - k).toFloat() / (end - center).toFloat()
                    }
                }
            }

            return filters
        }
    }
}

class FFTTest {

    fun computeFFT(real: FloatArray, imag: FloatArray, n: Int) {
        var i = 1
        var j = 0
        while (i < n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            i++
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wReal = Math.cos(angle).toFloat()
            val wImag = Math.sin(angle).toFloat()
            var k = 0
            while (k < n) {
                var wkr = 1f
                var wki = 0f
                for (h in 0 until len / 2) {
                    val idx1 = k + h
                    val idx2 = k + h + len / 2
                    val tr = wkr * real[idx2] - wki * imag[idx2]
                    val ti = wkr * imag[idx2] + wki * real[idx2]
                    real[idx2] = real[idx1] - tr
                    imag[idx2] = imag[idx1] - ti
                    real[idx1] = real[idx1] + tr
                    imag[idx1] = imag[idx1] + ti
                    val newWr = wkr * wReal - wki * wImag
                    val newWi = wkr * wImag + wki * wReal
                    wkr = newWr
                    wki = newWi
                }
                k += len
            }
            len = len shl 1
        }
    }

    @Test
    fun `fft of pure sine wave has correct peak`() {
        val n = 256
        val freq = 5
        val real = FloatArray(n) { Math.sin(2.0 * Math.PI * freq * it / n).toFloat() }
        val imag = FloatArray(n)

        computeFFT(real, imag, n)

        val magnitude = FloatArray(n) { Math.sqrt((real[it] * real[it] + imag[it] * imag[it]).toDouble()).toFloat() }
        val maxIdx = magnitude.indices.maxByOrNull { magnitude[it] } ?: 0
        assertEquals(freq.toFloat(), maxIdx.toFloat(), 1f)
    }

    @Test
    fun `fft followed by ifft recovers original signal`() {
        val n = 256
        val original = FloatArray(n) { Math.sin(2.0 * Math.PI * 3 * it / n).toFloat() }
        val real = original.copyOf()
        val imag = FloatArray(n)

        computeFFT(real, imag, n)

        for (i in 0 until n) {
            imag[i] = -imag[i]
        }
        computeFFT(real, imag, n)
        for (i in 0 until n) {
            real[i] /= n.toFloat()
        }

        for (i in 0 until n) {
            assertEquals(original[i], real[i], 0.01f)
        }
    }

    @Test
    fun `fft of zero signal is zero`() {
        val n = 128
        val real = FloatArray(n)
        val imag = FloatArray(n)

        computeFFT(real, imag, n)

        for (i in 0 until n) {
            assertEquals(0f, real[i], 0.001f)
            assertEquals(0f, imag[i], 0.001f)
        }
    }

    @Test
    fun `fft length power of 2`() {
        val lengths = intArrayOf(64, 128, 256, 512)
        for (n in lengths) {
            val real = FloatArray(n) { Math.sin(2.0 * Math.PI * 2 * it / n).toFloat() }
            val imag = FloatArray(n)
            try {
                computeFFT(real, imag, n)
            } catch (e: Exception) {
                fail("FFT should not throw for n=$n: ${e.message}")
            }
        }
    }
}

class LogMelExtractionTest {

    fun hzToMel(hz: Float): Float = (2595.0 * Math.log10(1.0 + hz / 700.0)).toFloat()
    fun melToHz(mel: Float): Float = (700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)).toFloat()

    fun createMelFilterbank(nMels: Int, fftSize: Int, sampleRate: Int): Array<FloatArray> {
        val nFreqs = fftSize / 2 + 1
        val lowMel = hzToMel(0f)
        val highMel = hzToMel(sampleRate / 2f)
        val melPoints = FloatArray(nMels + 2)
        for (i in melPoints.indices) {
            melPoints[i] = lowMel + (highMel - lowMel) * i / (nMels + 1)
        }
        val freqPoints = FloatArray(nMels + 2) { melToHz(melPoints[it]) }
        val binPoints = IntArray(nMels + 2) {
            Math.round(freqPoints[it] * (fftSize - 1) / sampleRate)
        }
        val filters = Array(nMels) { FloatArray(nFreqs) }
        for (m in 1..nMels) {
            val start = binPoints[m - 1]
            val center = binPoints[m]
            val end = binPoints[m + 1]
            val leftRange = center - start
            if (leftRange > 0) {
                for (k in start until center) {
                    if (k < nFreqs) filters[m - 1][k] = (k - start).toFloat() / leftRange.toFloat()
                }
            }
            val rightRange = end - center
            if (rightRange > 0) {
                for (k in center until end) {
                    if (k < nFreqs) filters[m - 1][k] = (end - k).toFloat() / rightRange.toFloat()
                }
            }
        }
        return filters
    }

    fun computeFFT(real: FloatArray, imag: FloatArray, n: Int) {
        var i = 1
        var j = 0
        while (i < n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            i++
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wReal = Math.cos(angle).toFloat()
            val wImag = Math.sin(angle).toFloat()
            var k = 0
            while (k < n) {
                var wkr = 1f; var wki = 0f
                for (h in 0 until len / 2) {
                    val idx1 = k + h; val idx2 = k + h + len / 2
                    val tr = wkr * real[idx2] - wki * imag[idx2]
                    val ti = wkr * imag[idx2] + wki * real[idx2]
                    real[idx2] = real[idx1] - tr; imag[idx2] = imag[idx1] - ti
                    real[idx1] = real[idx1] + tr; imag[idx1] = imag[idx1] + ti
                    val newWr = wkr * wReal - wki * wImag
                    val newWi = wkr * wImag + wki * wReal
                    wkr = newWr; wki = newWi
                }
                k += len
            }
            len = len shl 1
        }
    }

    fun extractLogMel(pcmSamples: FloatArray, nFft: Int = 512, hopLength: Int = 160, nMels: Int = 80): FloatArray {
        val numFrames = 1 + (pcmSamples.size - nFft) / hopLength
        if (numFrames <= 0) throw IllegalArgumentException("Audio too short")

        val hannWindow = FloatArray(nFft) { i ->
            (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (nFft - 1)))).toFloat()
        }

        val fftReal = FloatArray(nFft)
        val fftImag = FloatArray(nFft)
        val powerSpec = FloatArray(nFft / 2 + 1)
        val melEnergies = FloatArray(nMels)
        val allMelFrames = FloatArray(numFrames * nMels)

        val melFilterbank = createMelFilterbank(nMels, nFft, 16000)

        for (frameIdx in 0 until numFrames) {
            val start = frameIdx * hopLength
            for (i in 0 until nFft) {
                val sample = if (start + i < pcmSamples.size) pcmSamples[start + i] else 0f
                fftReal[i] = sample * hannWindow[i]
                fftImag[i] = 0f
            }
            computeFFT(fftReal, fftImag, nFft)
            for (k in 0 until nFft / 2 + 1) {
                powerSpec[k] = (fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k]) / nFft.toFloat()
            }
            for (m in 0 until nMels) {
                var energy = 0f
                val filter = melFilterbank[m]
                for (k in filter.indices) {
                    energy += filter[k] * powerSpec[k]
                }
                melEnergies[m] = Math.log10(Math.max(energy, 1e-10f).toDouble()).toFloat()
            }
            System.arraycopy(melEnergies, 0, allMelFrames, frameIdx * nMels, nMels)
        }

        return allMelFrames
    }

    @Test
    fun `extract log mel produces correct output dimensions`() {
        val pcmSamples = FloatArray(16000) { Math.sin(2.0 * Math.PI * 440 * it / 16000).toFloat() }
        val melFeatures = extractLogMel(pcmSamples)
        val numFrames = 1 + (16000 - 512) / 160
        assertEquals(numFrames * 80, melFeatures.size)
    }

    @Test
    fun `extract log mel with minimum viable audio`() {
        val minSamples = 512
        val pcmSamples = FloatArray(minSamples) { Math.sin(2.0 * Math.PI * 440 * it / 16000).toFloat() }
        val melFeatures = extractLogMel(pcmSamples)
        assertTrue(melFeatures.size > 0)
    }

    @Test
    fun `extract log mel with short audio throws`() {
        val shortAudio = FloatArray(100)
        try {
            extractLogMel(shortAudio)
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("too short"))
        }
    }

    @Test
    fun `log mel values are finite for normal audio`() {
        val pcmSamples = FloatArray(16000) { Math.sin(2.0 * Math.PI * 440 * it / 16000).toFloat() }
        val melFeatures = extractLogMel(pcmSamples)
        for (value in melFeatures) {
            assertFalse(value.isNaN())
            assertFalse(value.isInfinite())
        }
    }

    @Test
    fun `log mel values for silence are finite`() {
        val silence = FloatArray(16000) { 0.001f }
        val melFeatures = extractLogMel(silence)
        for (value in melFeatures) {
            assertFalse(value.isNaN())
            assertFalse(value.isInfinite())
        }
    }

    @Test
    fun `different audio produces different log mel features`() {
        val audio1 = FloatArray(16000) { Math.sin(2.0 * Math.PI * 100 * it / 16000).toFloat() }
        val audio2 = FloatArray(16000) { Math.sin(2.0 * Math.PI * 2000 * it / 16000).toFloat() }
        val mel1 = extractLogMel(audio1)
        val mel2 = extractLogMel(audio2)
        assertFalse(mel1.contentEquals(mel2))
    }
}
