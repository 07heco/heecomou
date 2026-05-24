package com.heecomou.desktop.audio

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AudioCaptureManagerTest {

    @Test
    @DisplayName("constructor should use default values (16000Hz, 16-bit, mono)")
    fun `constructor default values`() {
        val manager = AudioCaptureManager()

        assertEquals(16000f, manager.sampleRate)
        assertEquals(16, manager.sampleSizeInBits)
        assertEquals(1, manager.channels)
        assertFalse(manager.isRecording())
    }

    @Test
    @DisplayName("constructor should accept custom values")
    fun `constructor custom values`() {
        val manager = AudioCaptureManager(sampleRate = 44100f, sampleSizeInBits = 8, channels = 2)

        assertEquals(44100f, manager.sampleRate)
        assertEquals(8, manager.sampleSizeInBits)
        assertEquals(2, manager.channels)
    }

    @Test
    @DisplayName("bufferSize should be positive and based on sample rate")
    fun `bufferSize calculation`() {
        val manager = AudioCaptureManager(sampleRate = 16000f)
        val expectedMinFrames = (16000 * 0.04).toInt()
        val expected = expectedMinFrames * 2 * 4 // frames * frameSizeBytes * multiplier

        assertEquals(expected, manager.bufferSize)
        assertTrue(manager.bufferSize > 0)
    }

    @Test
    @DisplayName("bufferSize should scale with sample rate")
    fun `bufferSize scales with sample rate`() {
        val manager16k = AudioCaptureManager(sampleRate = 16000f)
        val manager44k = AudioCaptureManager(sampleRate = 44100f)

        assertTrue(manager44k.bufferSize > manager16k.bufferSize)
    }

    @Test
    @DisplayName("audioFormat should be PCM_SIGNED with correct parameters")
    fun `audioFormat properties`() {
        val manager = AudioCaptureManager(sampleRate = 16000f, sampleSizeInBits = 16, channels = 1)
        val format = manager.audioFormat

        assertEquals(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, format.encoding)
        assertEquals(16000f, format.sampleRate)
        assertEquals(16, format.sampleSizeInBits)
        assertEquals(1, format.channels)
        assertEquals(2, format.frameSize)
        assertEquals(16000f, format.frameRate)
        assertFalse(format.isBigEndian)
    }

    @Test
    @DisplayName("isSupported should not throw exception")
    fun `isSupported does not throw`() {
        val manager = AudioCaptureManager()

        try {
            val supported = manager.isSupported()
            // In CI or headless environment, this will likely be false
            // Just verify it returns a boolean without throwing
            assertNotNull(supported)
        } catch (e: Exception) {
            fail("isSupported should not throw: ${e.message}")
        }
    }

    @Test
    @DisplayName("startRecording should return false when no microphone available")
    fun `startRecording fails when no microphone`() {
        val manager = AudioCaptureManager()
        val errors = mutableListOf<String>()
        manager.onError = { errors.add(it) }

        val result = manager.startRecording()

        if (!manager.isSupported()) {
            assertFalse(result, "Should return false when audio not supported")
            assertTrue(errors.isNotEmpty(), "Should invoke onError callback")
            assertTrue(errors[0].contains("not supported"), "Error should mention not supported")
        }
    }

    @Test
    @DisplayName("startRecording should prevent double recording")
    fun `startRecording prevents double recording when hardware available`() {
        val manager = AudioCaptureManager()
        val errors = mutableListOf<String>()
        manager.onError = { errors.add(it) }

        if (manager.isSupported()) {
            try {
                val firstResult = manager.startRecording()
                if (firstResult) {
                    manager.onError = { errors.add(it) }
                    val secondResult = manager.startRecording()

                    assertFalse(secondResult, "Second startRecording should return false")
                    assertTrue(errors.any { it.contains("already in progress") },
                        "Error should mention already in progress")
                }
            } finally {
                manager.stopRecording()
            }
        }
    }

    @Test
    @DisplayName("stopRecording should be idempotent when not recording")
    fun `stopRecording is idempotent when not recording`() {
        val manager = AudioCaptureManager()

        manager.stopRecording()
        manager.stopRecording()

        assertFalse(manager.isRecording())
    }

    @Test
    @DisplayName("stopRecording should trigger onStateChanged callback")
    fun `stopRecording triggers onStateChanged`() {
        val manager = AudioCaptureManager()
        val stateChanges = mutableListOf<Boolean>()
        manager.onStateChanged = { stateChanges.add(it) }

        if (manager.isSupported()) {
            try {
                val started = manager.startRecording()
                if (started) {
                    manager.stopRecording()
                    assertEquals(listOf(true, false), stateChanges)
                }
            } finally {
                manager.release()
            }
        }
    }

    @Test
    @DisplayName("startRecording should trigger onStateChanged(true) callback")
    fun `startRecording triggers onStateChanged true`() {
        val manager = AudioCaptureManager()
        val stateChanges = mutableListOf<Boolean>()
        manager.onStateChanged = { stateChanges.add(it) }

        if (manager.isSupported()) {
            try {
                val started = manager.startRecording()
                if (started) {
                    assertTrue(stateChanges.isNotEmpty(), "Should have at least one state change")
                    assertEquals(true, stateChanges[0], "First state change should be true")
                }
            } finally {
                manager.stopRecording()
            }
        }
    }

    @Test
    @DisplayName("release should clear all callbacks")
    fun `release clears all callbacks`() {
        val manager = AudioCaptureManager()
        manager.onAudioData = { }
        manager.onError = { }
        manager.onStateChanged = { }

        if (manager.isSupported()) {
            val latch = CountDownLatch(1)
            manager.onAudioData = { latch.countDown() }
            try {
                manager.startRecording()
                latch.await(1, TimeUnit.SECONDS)
            } finally {
                manager.release()
            }
        } else {
            manager.release()
        }

        assertNull(manager.onAudioData)
        assertNull(manager.onError)
        assertNull(manager.onStateChanged)
        assertFalse(manager.isRecording())
    }

    @Test
    @DisplayName("startRecording should invoke onAudioData callback when recording")
    fun `startRecording invokes onAudioData callback`() {
        val manager = AudioCaptureManager()
        val audioData = mutableListOf<ByteArray>()
        manager.onAudioData = { audioData.add(it) }

        if (manager.isSupported()) {
            val latch = CountDownLatch(5)
            manager.onAudioData = {
                audioData.add(it)
                latch.countDown()
            }

            try {
                manager.startRecording()
                val received = latch.await(3, TimeUnit.SECONDS)

                if (received) {
                    assertTrue(audioData.isNotEmpty(), "Should receive audio data")
                    audioData.forEach { chunk ->
                        assertTrue(chunk.isNotEmpty(), "Each chunk should have data")
                        assertTrue(chunk.size <= manager.bufferSize / 2,
                            "Chunk size should be <= buffer/2")
                    }
                }
            } finally {
                manager.stopRecording()
            }
        }
    }

    @Test
    @DisplayName("startRecording handles error callback on unsupported format")
    fun `startRecording error on unsupported format`() {
        val manager = AudioCaptureManager(sampleRate = 1f)
        val errors = mutableListOf<String>()
        manager.onError = { errors.add(it) }

        val result = manager.startRecording()

        if (!manager.isSupported()) {
            assertFalse(result)
            assertTrue(errors.isNotEmpty())
        }
    }
}
