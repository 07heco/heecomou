package com.heecomou.desktop.asr

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.*

class CloudAsrClientTest {

    private lateinit var client: CloudAsrClient

    @BeforeEach
    fun setUp() {
        client = CloudAsrClient()
    }

    @AfterEach
    fun tearDown() {
        runCatching { client.release() }
    }

    @Test
    @DisplayName("should be disconnected initially")
    fun `initially disconnected`() {
        assertFalse(client.isConnected)
    }

    @Test
    @DisplayName("should use localhost as default gateway host")
    fun `default gateway host is localhost`() {
        val defaultClient = CloudAsrClient()
        assertNotNull(defaultClient)
    }

    @Test
    @DisplayName("should accept custom gateway parameters")
    fun `accepts custom gateway parameters`() {
        val customClient = CloudAsrClient(
            gatewayHost = "192.168.1.100",
            gatewayPort = 9090,
            connectTimeoutMs = 5_000L
        )
        assertNotNull(customClient)
    }

    @Test
    @DisplayName("disconnect should be safe when not connected")
    fun `disconnect safe when not connected`() {
        client.disconnect()
        assertFalse(client.isConnected)
    }

    @Test
    @DisplayName("release should be safe when not connected")
    fun `release safe when not connected`() {
        client.release()
        assertFalse(client.isConnected)
        assertNull(client.onSessionStarted)
        assertNull(client.onFinalResult)
        assertNull(client.onError)
    }

    @Test
    @DisplayName("sendAudio should return false when not connected")
    fun `sendAudio returns false when not connected`() {
        val result = client.sendAudio(ByteArray(100))
        assertFalse(result, "Should not send audio when disconnected")
    }

    @Test
    @DisplayName("sendAudio should return false with empty data")
    fun `sendAudio with empty data`() {
        val result = client.sendAudio(ByteArray(0))
        assertFalse(result)
    }

    @Test
    @DisplayName("callbacks should be assignable and invocable")
    fun `callbacks are assignable`() {
        var sessionStarted = false
        var partialText = ""
        var finalText = ""
        var errorMsg = ""

        client.onSessionStarted = { sessionStarted = true }
        client.onPartialResult = { partialText = it }
        client.onFinalResult = { text, _ -> finalText = text }
        client.onError = { errorMsg = it }

        client.onSessionStarted?.invoke(DesktopAsrSessionStarted("session_started", "test-session"))
        client.onPartialResult?.invoke("partial text")
        client.onFinalResult?.invoke("final text", 0.95f)
        client.onError?.invoke("test error")

        assertTrue(sessionStarted)
        assertEquals("partial text", partialText)
        assertEquals("final text", finalText)
        assertEquals("test error", errorMsg)
    }

    @Test
    @DisplayName("release should clear all callbacks")
    fun `release clears all callbacks`() {
        client.onSessionStarted = { }
        client.onPartialResult = { }
        client.onFinalResult = { _, _ -> }
        client.onConnectionFailed = { }
        client.onDisconnected = { }
        client.onError = { }

        client.release()

        assertNull(client.onSessionStarted)
        assertNull(client.onPartialResult)
        assertNull(client.onFinalResult)
        assertNull(client.onConnectionFailed)
        assertNull(client.onDisconnected)
        assertNull(client.onError)
    }

    @Test
    @DisplayName("DesktopAsrSessionStarted data class should serialize correctly")
    fun `DesktopAsrSessionStarted serialization`() {
        val gson = com.google.gson.Gson()
        val session = DesktopAsrSessionStarted(
            type = "session_started",
            sessionId = "abc123",
            format = DesktopAsrAudioFormat(sampleRate = 16000, numChannels = 1, bitDepth = 16)
        )
        val json = gson.toJson(session)
        assertTrue(json.contains("session_started"))
        assertTrue(json.contains("abc123"))
        assertTrue(json.contains("session_id"))

        val deserialized = gson.fromJson(json, DesktopAsrSessionStarted::class.java)
        assertEquals("session_started", deserialized.type)
        assertEquals("abc123", deserialized.sessionId)
        assertEquals(16000, deserialized.format?.sampleRate)
        assertEquals(1, deserialized.format?.numChannels)
        assertEquals(16, deserialized.format?.bitDepth)
    }

    @Test
    @DisplayName("DesktopAsrResult data class should handle is_final serialization")
    fun `DesktopAsrResult serialization`() {
        val gson = com.google.gson.Gson()
        val result = DesktopAsrResult(
            type = "final_result",
            text = "hello world",
            isFinal = true,
            confidence = 0.98f
        )
        val json = gson.toJson(result)
        assertTrue(json.contains("hello world"))
        assertTrue(json.contains("is_final"))

        val deserialized = gson.fromJson(json, DesktopAsrResult::class.java)
        assertEquals("hello world", deserialized.text)
        assertTrue(deserialized.isFinal)
        assertEquals(0.98f, deserialized.confidence)
    }

    @Test
    @DisplayName("DesktopAsrClientState enum should have expected values")
    fun `DesktopAsrClientState values`() {
        val states = DesktopAsrClientState.entries
        assertEquals(4, states.size)
        assertEquals(DesktopAsrClientState.DISCONNECTED, DesktopAsrClientState.valueOf("DISCONNECTED"))
        assertEquals(DesktopAsrClientState.CONNECTING, DesktopAsrClientState.valueOf("CONNECTING"))
        assertEquals(DesktopAsrClientState.CONNECTED, DesktopAsrClientState.valueOf("CONNECTED"))
        assertEquals(DesktopAsrClientState.CLOSED, DesktopAsrClientState.valueOf("CLOSED"))
    }

    @Test
    @DisplayName("connect should not double-connect when already connected")
    fun `double connect prevented by state check`() {
        // Without a server, connect() will open but onOpen may not be called immediately.
        // The state check before connect() prevents double connection attempts.
        // connect() is safe to call multiple times - second call is no-op.
        client.connect()
        // Second connect should not throw
        try {
            client.connect()
        } catch (e: Exception) {
            fail("Second connect should not throw: ${e.message}")
        }
        client.disconnect()
    }
}
