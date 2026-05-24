package com.heecomou.desktop.asr

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.*

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
    @DisplayName("initialize should transition to READY")
    fun `initialize transitions to READY`() {
        val result = client.initialize()
        assertTrue(result, "Initialize should return true")
        assertEquals(LocalAsrState.READY, client.currentState)
    }

    @Test
    @DisplayName("startListening should transition to RECOGNIZING")
    fun `startListening transitions to RECOGNIZING`() {
        client.initialize()
        val result = client.startListening()
        assertTrue(result, "startListening should return true")
        assertEquals(LocalAsrState.RECOGNIZING, client.currentState)
    }

    @Test
    @DisplayName("stopListening should transition back to READY")
    fun `stopListening returns to READY`() {
        client.initialize()
        client.startListening()
        client.stopListening()
        assertEquals(LocalAsrState.READY, client.currentState)
    }

    @Test
    @DisplayName("release should return to IDLE")
    fun `release returns to IDLE`() {
        client.initialize()
        client.release()
        assertEquals(LocalAsrState.IDLE, client.currentState)
    }

    @Test
    @DisplayName("startListening should succeed from IDLE state")
    fun `startListening succeeds from IDLE`() {
        val result = client.startListening()
        assertTrue(result, "Should succeed when in IDLE state")
        assertEquals(LocalAsrState.RECOGNIZING, client.currentState)
    }

    @Test
    @DisplayName("feedPcmData should trigger onSpeechDetected")
    fun `feedPcmData triggers speech detected`() {
        client.initialize()
        client.startListening()

        var speechDetected = false
        client.onSpeechDetected = { speechDetected = true }

        val pcmData = ByteArray(1280)
        client.feedPcmData(pcmData, isSpeech = true, timestampMs = 1000L)

        assertTrue(speechDetected, "Speech should be detected")
    }

    @Test
    @DisplayName("feedPcmData should trigger onPartialResult")
    fun `feedPcmData triggers partial result`() {
        client.initialize()
        client.startListening()

        var partialText = ""
        client.onPartialResult = { partialText = it }

        val pcmData = ByteArray(1280)
        client.feedPcmData(pcmData, isSpeech = true, timestampMs = 1000L)

        assertTrue(partialText.contains("识别中"), "Partial result should contain status")
    }

    @Test
    @DisplayName("silence detection should trigger final result")
    fun `silence triggers final result`() {
        client.initialize()
        client.startListening()

        var finalText = ""
        client.onFinalResult = { finalText = it }

        // Feed speech data
        val pcmData = ByteArray(1280)
        client.feedPcmData(pcmData, isSpeech = true, timestampMs = 0L)

        // Feed silence for >800ms
        client.feedPcmData(ByteArray(1280), isSpeech = false, timestampMs = 900L)

        assertTrue(finalText.isNotEmpty(), "Final result should be produced")
    }

    @Test
    @DisplayName("state changes should invoke callback")
    fun `state changes invoke callback`() {
        val states = mutableListOf<LocalAsrState>()
        client.onStateChanged = { states.add(it) }

        client.initialize()
        client.startListening()
        client.stopListening()

        assertEquals(LocalAsrState.INITIALIZING, states[0])
        assertEquals(LocalAsrState.READY, states[1])
        assertEquals(LocalAsrState.RECOGNIZING, states[2])
        assertEquals(LocalAsrState.READY, states[3])
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

        client.initialize()
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
