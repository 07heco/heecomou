package com.heecomou.ime.audio

import android.media.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AudioCaptureManagerTest {

    @Test
    fun `constructor sets default sample rate to 16000`() {
        val manager = AudioCaptureManager()
        assertEquals(16000, manager.sampleRate)
    }

    @Test
    fun `constructor sets mono channel config`() {
        val manager = AudioCaptureManager()
        assertEquals(AudioFormat.CHANNEL_IN_MONO, manager.channelConfig)
    }

    @Test
    fun `constructor sets PCM 16bit encoding`() {
        val manager = AudioCaptureManager()
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, manager.audioEncoding)
    }

    @Test
    fun `constructor accepts custom sample rate`() {
        val manager = AudioCaptureManager(sampleRate = 48000)
        assertEquals(48000, manager.sampleRate)
    }

    @Test
    fun `constructor accepts custom channel config`() {
        val manager = AudioCaptureManager(channelConfig = AudioFormat.CHANNEL_IN_STEREO)
        assertEquals(AudioFormat.CHANNEL_IN_STEREO, manager.channelConfig)
    }

    @Test
    fun `constructor accepts custom encoding`() {
        val manager = AudioCaptureManager(audioEncoding = AudioFormat.ENCODING_PCM_8BIT)
        assertEquals(AudioFormat.ENCODING_PCM_8BIT, manager.audioEncoding)
    }

    @Test
    fun `bytesPerSample returns 2 for PCM 16bit`() {
        val manager = AudioCaptureManager(audioEncoding = AudioFormat.ENCODING_PCM_16BIT)
        assertEquals(2, manager.bytesPerSample)
    }

    @Test
    fun `bytesPerSample returns 1 for PCM 8bit`() {
        val manager = AudioCaptureManager(audioEncoding = AudioFormat.ENCODING_PCM_8BIT)
        assertEquals(1, manager.bytesPerSample)
    }

    @Test
    fun `stopRecording when not recording is noop`() {
        val manager = AudioCaptureManager()
        val stateChanged = AtomicBoolean(false)
        manager.onStateChanged = { stateChanged.set(true) }

        manager.stopRecording()

        assertFalse(stateChanged.get())
    }

    @Test
    fun `release clears all callbacks`() {
        val manager = AudioCaptureManager()
        manager.onAudioData = { }
        manager.onError = { }
        manager.onStateChanged = { }

        manager.release()

        assertNull(manager.onAudioData)
        assertNull(manager.onError)
        assertNull(manager.onStateChanged)
    }

    @Test
    fun `release when recording stops first then clears`() {
        val manager = AudioCaptureManager()
        var stateTransitionCount = 0
        manager.onStateChanged = { stateTransitionCount++ }

        manager.release()

        assertNull(manager.onAudioData)
        assertNull(manager.onError)
        assertNull(manager.onStateChanged)
        assertEquals(0, stateTransitionCount)
    }

    @Test
    fun `onError and onStateChanged callbacks are invoked`() {
        val manager = AudioCaptureManager()
        val errors = mutableListOf<String>()
        val states = mutableListOf<Boolean>()

        manager.onError = { errors.add(it) }
        manager.onStateChanged = { states.add(it) }

        manager.onError?.invoke("test error")
        manager.onStateChanged?.invoke(true)
        manager.onStateChanged?.invoke(false)

        assertEquals(1, errors.size)
        assertEquals("test error", errors[0])
        assertEquals(2, states.size)
        assertTrue(states[0])
        assertFalse(states[1])
    }
}
