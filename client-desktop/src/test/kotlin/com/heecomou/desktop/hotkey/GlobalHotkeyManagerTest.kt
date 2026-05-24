package com.heecomou.desktop.hotkey

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GlobalHotkeyManagerTest {

    private lateinit var manager: GlobalHotkeyManager

    @BeforeEach
    fun setUp() {
        manager = GlobalHotkeyManager()
    }

    @AfterEach
    fun tearDown() {
        runCatching { manager.unregister() }
    }

    @Test
    @DisplayName("should not be registered initially")
    fun `not registered initially`() {
        assertFalse(manager.isRegistered())
    }

    @Test
    @DisplayName("should register successfully when native hook available")
    fun `register returns true`() {
        val result = manager.register()
        assertTrue(result, "Register should return true")
        assertTrue(manager.isRegistered(), "Should be marked as registered")
    }

    @Test
    @DisplayName("should unregister successfully")
    fun `unregister clears registration state`() {
        manager.register()
        assertTrue(manager.isRegistered())

        manager.unregister()
        assertFalse(manager.isRegistered(), "Should not be registered after unregister")
    }

    @Test
    @DisplayName("double register should be safe")
    fun `double register is safe`() {
        val first = manager.register()
        val second = manager.register()

        assertTrue(first, "First register should succeed")
        assertTrue(second, "Second register should also return true")
        assertTrue(manager.isRegistered())
    }

    @Test
    @DisplayName("double unregister should be safe")
    fun `double unregister is safe`() {
        manager.register()
        manager.unregister()

        // Second unregister should not throw
        try {
            manager.unregister()
        } catch (e: Exception) {
            fail("Second unregister should not throw: ${e.message}")
        }

        assertFalse(manager.isRegistered())
    }

    @Test
    @DisplayName("callbacks should be assignable")
    fun `callbacks are assignable`() {
        var hotkeyCalled = false
        var cancelCalled = false

        manager.onHotkeyTriggered = { hotkeyCalled = true }
        manager.onCancelTriggered = { cancelCalled = true }

        manager.onHotkeyTriggered?.invoke()
        manager.onCancelTriggered?.invoke()

        assertTrue(hotkeyCalled, "onHotkeyTriggered should be called")
        assertTrue(cancelCalled, "onCancelTriggered should be called")
    }

    @Test
    @DisplayName("unregister followed by register should work")
    fun `unregister and re-register`() {
        manager.register()
        manager.unregister()
        assertFalse(manager.isRegistered())

        val result = manager.register()
        assertTrue(result, "Re-register should succeed")
        assertTrue(manager.isRegistered())
    }

    @Test
    @DisplayName("callbacks can be set to null")
    fun `callbacks can be set to null`() {
        manager.onHotkeyTriggered = { }
        manager.onCancelTriggered = { }

        manager.onHotkeyTriggered = null
        manager.onCancelTriggered = null

        assertNull(manager.onHotkeyTriggered)
        assertNull(manager.onCancelTriggered)
    }
}
