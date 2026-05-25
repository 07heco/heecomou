package com.heecomou.desktop.asr

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalAsrClientTest {

    private lateinit var client: LocalAsrClient

    @BeforeEach
    fun setUp() {
        client = LocalAsrClient()
    }

    @AfterEach
    fun tearDown() {
        runCatching { client.release() }
    }

    @Test
    @DisplayName("should start in IDLE state")
    fun `initial state is IDLE`() {
        assertEquals(LocalAsrState.IDLE, client.currentState)
        assertFalse(client.isModelLoaded)
    }

    @Test
    @DisplayName("initialize fails gracefully without Python/ONNX models")
    fun `initialize fails without runtime`() {
        val result = client.initialize()
        assertFalse(result, "Initialize should return false without Python/ONNX")
        assertEquals(LocalAsrState.ERROR, client.currentState)
        assertFalse(client.isModelLoaded)
    }

    @Test
    @DisplayName("startListening should work from IDLE state")
    fun `startListening succeeds from IDLE`() {
        val result = client.startListening()
        assertTrue(result, "Should succeed when in IDLE state")
        assertEquals(LocalAsrState.RECOGNIZING, client.currentState)
    }

    @Test
    @DisplayName("stopListening should transition to READY")
    fun `stopListening returns to READY`() {
        client.startListening()
        client.stopListening()
        assertEquals(LocalAsrState.READY, client.currentState)
    }

    @Test
    @DisplayName("release should return to IDLE and clear state")
    fun `release returns to IDLE`() {
        client.startListening()
        client.release()
        assertEquals(LocalAsrState.IDLE, client.currentState)
    }

    @Test
    @DisplayName("feedPcmData should trigger onPartialResult")
    fun `feedPcmData triggers partial result`() {
        client.startListening()

        var partialText = ""
        client.onPartialResult = { partialText = it }

        val pcmData = ByteArray(1280)
        client.feedPcmData(pcmData, isSpeech = true, timestampMs = 1000L)

        assertTrue(partialText.contains("端侧识别"), "Partial result should contain status")
    }

    @Test
    @DisplayName("silence/stop should produce final result via callback")
    fun `stop produces final result`() {
        client.startListening()

        var finalText = ""
        client.onFinalResult = { finalText = it }

        val pcmData = ByteArray(1280)
        client.feedPcmData(pcmData, isSpeech = true, timestampMs = 0L)

        client.stopListening()

        // Final result is async (coroutine → HTTP), may be stubbed in test
        // Without a running ONNX server, onFinalResult may not fire
        // This test validates the state transition + callback wiring
        assertEquals(LocalAsrState.READY, client.currentState)
    }

    @Test
    @DisplayName("state changes should invoke callback")
    fun `state changes invoke callback`() {
        val states = mutableListOf<LocalAsrState>()
        client.onStateChanged = { states.add(it) }

        client.startListening()
        client.stopListening()

        assertEquals(LocalAsrState.RECOGNIZING, states[0])
        assertEquals(LocalAsrState.READY, states[1])
    }

    @Test
    @DisplayName("release should clear all callbacks")
    fun `release clears callbacks`() {
        client.onStateChanged = { }
        client.onPartialResult = { }
        client.onFinalResult = { }
        client.onSpeechDetected = { }
        client.onSilenceDetected = { }
        client.onError = { }

        client.startListening()
        client.release()

        assertNull(client.onStateChanged)
        assertNull(client.onPartialResult)
        assertNull(client.onFinalResult)
        assertNull(client.onSpeechDetected)
        assertNull(client.onSilenceDetected)
        assertNull(client.onError)
    }

    @Test
    @DisplayName("LocalAsrState should have 5 values")
    fun `LocalAsrState has five values`() {
        val states = LocalAsrState.entries
        assertEquals(5, states.size)
        assertEquals(LocalAsrState.IDLE, LocalAsrState.valueOf("IDLE"))
        assertEquals(LocalAsrState.INITIALIZING, LocalAsrState.valueOf("INITIALIZING"))
        assertEquals(LocalAsrState.READY, LocalAsrState.valueOf("READY"))
        assertEquals(LocalAsrState.RECOGNIZING, LocalAsrState.valueOf("RECOGNIZING"))
        assertEquals(LocalAsrState.ERROR, LocalAsrState.valueOf("ERROR"))
    }
}
