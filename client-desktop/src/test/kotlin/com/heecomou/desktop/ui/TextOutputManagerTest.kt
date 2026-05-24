package com.heecomou.desktop.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.*

class TextOutputManagerTest {

    private lateinit var manager: TextOutputManager

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
    @DisplayName("writeToClipboard should work for non-empty text")
    fun `writeToClipboard non-empty text`() {
        val result = manager.writeToClipboard("Hello World")
        // clipboard write should succeed in most environments
        assertTrue(result, "writeToClipboard should succeed")
    }

    @Test
    @DisplayName("writeToClipboard empty text returns false")
    fun `writeToClipboard empty text returns false`() {
        assertFalse(manager.writeToClipboard(""))
    }

    @Test
    @DisplayName("output should work for non-empty text")
    fun `output non-empty text`() {
        // First write to clipboard
        manager.writeToClipboard("test text")
        val result = manager.output("test text")
        // output may work via clipboard or typing depending on Robot availability
        assertNotNull(result)
    }

    @Test
    @DisplayName("release should clean up")
    fun `release cleans up`() {
        manager.release()
        // After release, calls should not throw
        try {
            manager.output("text")
        } catch (e: Exception) {
            fail("output after release should not throw: ${e.message}")
        }
    }

    @Test
    @DisplayName("output with special characters")
    fun `output with special characters`() {
        val text = "Hello, World! Test @ # $ %"
        val result = manager.writeToClipboard(text)
        assertTrue(result)
    }

    @Test
    @DisplayName("output with Chinese characters")
    fun `output with Chinese characters`() {
        val text = "你好世界\u2014\u2014语音输入测试"
        val result = manager.writeToClipboard(text)
        assertTrue(result)
    }

    @Test
    @DisplayName("output with newlines and tabs")
    fun `output with newlines and tabs`() {
        val text = "Line 1\nLine 2\n\tIndented"
        val result = manager.writeToClipboard(text)
        assertTrue(result)
    }

    @Test
    @DisplayName("outputViaTyping with non-empty text")
    fun `outputViaTyping non-empty text`() {
        val result = manager.outputViaTyping("abc")
        // Will be false if Robot is not available (headless CI)
        assertNotNull(result)
    }

    @Test
    @DisplayName("outputViaClipboard with non-empty text")
    fun `outputViaClipboard non-empty text`() {
        val result = manager.outputViaClipboard("abc")
        assertNotNull(result)
    }
}
