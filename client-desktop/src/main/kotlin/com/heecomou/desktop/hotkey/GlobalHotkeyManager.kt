package com.heecomou.desktop.hotkey

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

class GlobalHotkeyManager {

    companion object {
        private val LOGGER = Logger.getLogger(GlobalHotkeyManager::class.java.name)

        private const val HOTKEY_KEY = NativeKeyEvent.VC_V
        private const val CANCEL_KEY = NativeKeyEvent.VC_ESCAPE
    }

    private val registered = AtomicBoolean(false)
    private val ctrlPressed = AtomicBoolean(false)
    private val shiftPressed = AtomicBoolean(false)

    var onHotkeyTriggered: (() -> Unit)? = null
    var onCancelTriggered: (() -> Unit)? = null

    private val keyListener = object : NativeKeyListener {
        override fun nativeKeyPressed(e: NativeKeyEvent) {
            when (e.keyCode) {
                NativeKeyEvent.VC_CONTROL -> ctrlPressed.set(true)
                NativeKeyEvent.VC_SHIFT -> shiftPressed.set(true)
                CANCEL_KEY -> {
                    if (registered.get()) {
                        onCancelTriggered?.invoke()
                    }
                }
                HOTKEY_KEY -> {
                    if (ctrlPressed.get() && shiftPressed.get()) {
                        onHotkeyTriggered?.invoke()
                    }
                }
            }
        }

        override fun nativeKeyReleased(e: NativeKeyEvent) {
            when (e.keyCode) {
                NativeKeyEvent.VC_CONTROL -> ctrlPressed.set(false)
                NativeKeyEvent.VC_SHIFT -> shiftPressed.set(false)
            }
        }
    }

    fun register(): Boolean {
        if (registered.get()) {
            LOGGER.info("Global hotkey already registered")
            return true
        }

        return try {
            Logger.getLogger("com.github.kwhat.jnativehook").level = Level.OFF
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(keyListener)
            registered.set(true)
            LOGGER.info("Global hotkey registered: Ctrl+Shift+V (trigger), Esc (cancel)")
            true
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE, "Failed to register global hotkey: ${e.message}", e)
            false
        }
    }

    fun unregister() {
        if (!registered.get()) return

        try {
            GlobalScreen.removeNativeKeyListener(keyListener)
            GlobalScreen.unregisterNativeHook()
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Error unregistering global hotkey: ${e.message}", e)
        } finally {
            registered.set(false)
            ctrlPressed.set(false)
            shiftPressed.set(false)
        }
    }

    fun isRegistered(): Boolean = registered.get()
}
