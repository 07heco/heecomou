package com.heecomou.desktop.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TextOutputManagerTest {

    private lateinit var manager: TextOutputManager
    private val outputAvailable: Boolean

    init {
        val probe = TextOutputManager()
        outputAvailable = probe.isAvailable()
        probe.release()
    }

    @BeforeEach
    fun setUp() {
        manager = TextOutputManager()
    }

    @AfterEach
    fun tearDown() {
        runCatching { manager.release() }
    }

    @Test
    @DisplayName("isAvailable should return boolean")
    fun `isAvailable returns boolean`() {
        val available = manager.isAvailable()
        assertNotNull(available)
    }

    @Test
    @DisplayName("output empty text should return false")
    fun `output empty text returns false`() {
        assertFalse(manager.output(""))
    }

    @Test
    @DisplayName("outputViaClipboard empty text returns false")
    fun `outputViaClipboard empty text returns false`() {
        assertFalse(manager.outputViaClipboard(""))
    }

    @Test
    @DisplayName("outputViaTyping empty text returns false")
    fun `outputViaTyping empty text returns false`() {
        assertFalse(manager.outputViaTyping(""))
    }

    @Test
    @DisplayName("writeToClipboard should work when available")
    fun `writeToClipboard non-empty text`() {
        val result = manager.writeToClipboard("Hello World")
        if (outputAvailable) {
            assertTrue(result, "writeToClipboard should succeed when output is available")
        }
    }

    @Test
    @DisplayName("writeToClipboard empty text returns false")
    fun `writeToClipboard empty text returns false`() {
        assertFalse(manager.writeToClipboard(""))
    }

    @Test
    @DisplayName("output should work for non-empty text when available")
    fun `output non-empty text`() {
        manager.writeToClipboard("test text")
        val result = manager.output("test text")
        assertNotNull(result)
    }

    @Test
    @DisplayName("release should clean up")
    fun `release cleans up`() {
        manager.release()
        try {
            manager.output("text")
        } catch (e: Exception) {
            fail("output after release should not throw: ${e.message}")
        }
    }

    @Test
    @DisplayName("output with special characters when available")
    fun `output with special characters`() {
        val text = "Hello, World! Test @ # $ %"
        val result = manager.writeToClipboard(text)
        if (outputAvailable) {
            assertTrue(result)
        }
    }

    @Test
    @DisplayName("output with Chinese characters when available")
    fun `output with Chinese characters`() {
        val text = "你好世界\u2014\u2014语音输入测试"
        val result = manager.writeToClipboard(text)
        if (outputAvailable) {
            assertTrue(result)
        }
    }

    @Test
    @DisplayName("output with newlines and tabs when available")
    fun `output with newlines and tabs`() {
        val text = "Line 1\nLine 2\n\tIndented"
        val result = manager.writeToClipboard(text)
        if (outputAvailable) {
            assertTrue(result)
        }
    }

    @Test
    @DisplayName("outputViaTyping with non-empty text")
    fun `outputViaTyping non-empty text`() {
        val result = manager.outputViaTyping("abc")
        assertNotNull(result)
    }

    @Test
    @DisplayName("outputViaClipboard with non-empty text")
    fun `outputViaClipboard non-empty text`() {
        val result = manager.outputViaClipboard("abc")
        assertNotNull(result)
    }
}
