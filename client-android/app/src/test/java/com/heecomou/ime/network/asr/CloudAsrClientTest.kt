package com.heecomou.ime.network.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class CloudAsrClientTest {

    @Test
    fun `default gateway host is 10_0_2_2`() {
        val client = CloudAsrClient()
        assertNotNull(client)
    }

    @Test
    fun `constructor accepts custom host and port`() {
        val client = CloudAsrClient(
            gatewayHost = "192.168.1.100",
            gatewayPort = 9090
        )
        assertNotNull(client)
    }

    @Test
    fun `isConnected returns false initially`() {
        val client = CloudAsrClient()
        assertFalse(client.isConnected)
    }

    @Test
    fun `connect to invalid host invokes onConnectionFailed`() {
        val failed = AtomicBoolean(false)
        val client = CloudAsrClient(
            gatewayHost = "255.255.255.255",
            gatewayPort = 1,
            connectTimeoutMs = 1000L
        )
        client.onConnectionFailed = { failed.set(true) }

        client.connect()
        Thread.sleep(1500)

        assertTrue(failed.get())
        client.release()
    }

    @Test
    fun `sendAudio when disconnected returns false`() {
        val client = CloudAsrClient()
        val result = client.sendAudio(ByteArray(100))
        assertFalse(result)
    }

    @Test
    fun `disconnect from non-connected state is safe`() {
        val client = CloudAsrClient()
        client.disconnect()
        assertFalse(client.isConnected)
    }

    @Test
    fun `release clears all callbacks`() {
        val client = CloudAsrClient()
        client.onSessionStarted = {}
        client.onSamplesReceived = {}
        client.onConnectionFailed = {}
        client.onDisconnected = {}
        client.onError = {}

        client.release()

        assertNull(client.onSessionStarted)
        assertNull(client.onSamplesReceived)
        assertNull(client.onConnectionFailed)
        assertNull(client.onDisconnected)
        assertNull(client.onError)
    }

    @Test
    fun `session started callback captures session id`() {
        val sessionId = AtomicReference<String>()
        val client = CloudAsrClient()
        client.onSessionStarted = { sessionId.set(it.sessionId) }
        client.onSessionStarted?.invoke(
            AsrSessionStarted("session_started", "abc-123", null)
        )
        assertEquals("abc-123", sessionId.get())
    }

    @Test
    fun `samples received callback captures sample count`() {
        val count = AtomicReference<Int>()
        val client = CloudAsrClient()
        client.onSamplesReceived = { count.set(it.sampleCount) }
        client.onSamplesReceived?.invoke(
            AsrSamplesReceived("samples_received", 800, 8000)
        )
        assertEquals(800, count.get()?.toInt())
    }

    @Test
    fun `asr session started data class has all fields`() {
        val fmt = AsrAudioFormat(16000, 1, 16)
        val session = AsrSessionStarted("session_started", "sess-456", fmt)
        assertEquals("session_started", session.type)
        assertEquals("sess-456", session.sessionId)
        assertEquals(16000, session.format?.sampleRate)
        assertEquals(1, session.format?.numChannels)
        assertEquals(16, session.format?.bitDepth)
    }

    @Test
    fun `asr samples received data class has all fields`() {
        val sr = AsrSamplesReceived("samples_received", 160, 3200)
        assertEquals("samples_received", sr.type)
        assertEquals(160, sr.sampleCount)
        assertEquals(3200, sr.totalSamples)
    }

    @Test
    fun `asr client state enum values are correct`() {
        assertEquals("DISCONNECTED", AsrClientState.DISCONNECTED.name)
        assertEquals("CONNECTING", AsrClientState.CONNECTING.name)
        assertEquals("CONNECTED", AsrClientState.CONNECTED.name)
        assertEquals("CLOSED", AsrClientState.CLOSED.name)
    }
}
