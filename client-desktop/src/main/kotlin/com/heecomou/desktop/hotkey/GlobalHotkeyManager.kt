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

        private const val HOTKEY_KEY = 0x0056 // NativeKeyEvent.VC_V
        private const val CANCEL_KEY = 0x001B // NativeKeyEvent.VC_ESCAPE
        private const val VC_CONTROL = 0x0011 // NativeKeyEvent.VC_CONTROL
        private const val VC_SHIFT = 0x0010   // NativeKeyEvent.VC_SHIFT

        @Volatile
        private var nativeHookAvailable: Boolean? = null

        fun isNativeHookAvailable(): Boolean {
            if (nativeHookAvailable != null) return nativeHookAvailable!!
            nativeHookAvailable = try {
                Class.forName("com.github.kwhat.jnativehook.GlobalScreen")
                GlobalScreen.registerNativeHook()
                GlobalScreen.unregisterNativeHook()
                true
            } catch (_: Throwable) {
                LOGGER.info("JNativeHook not available, running in headless/CI mode")
                false
            }
            return nativeHookAvailable!!
        }
    }

    private val registered = AtomicBoolean(false)
    private val ctrlPressed = AtomicBoolean(false)
    private val shiftPressed = AtomicBoolean(false)

    var onHotkeyTriggered: (() -> Unit)? = null
    var onCancelTriggered: (() -> Unit)? = null

    private var keyListener: NativeKeyListener? = null

    private fun createKeyListener(): NativeKeyListener? {
        if (!isNativeHookAvailable()) return null
        return try {
            object : NativeKeyListener {
                override fun nativeKeyPressed(e: NativeKeyEvent) {
                    when (e.keyCode) {
                        VC_CONTROL -> ctrlPressed.set(true)
                        VC_SHIFT -> shiftPressed.set(true)
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
                        VC_CONTROL -> ctrlPressed.set(false)
                        VC_SHIFT -> shiftPressed.set(false)
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun register(): Boolean {
        if (registered.get()) {
            LOGGER.info("Global hotkey already registered")
            return true
        }

        if (!isNativeHookAvailable()) {
            LOGGER.info("Native hook not available, skip registration")
            return false
        }

        val listener = keyListener ?: createKeyListener()
        if (listener == null) {
            LOGGER.warning("Failed to create key listener")
            return false
        }
        keyListener = listener

        return try {
            Logger.getLogger("com.github.kwhat.jnativehook").level = Level.OFF
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(listener)
            registered.set(true)
            LOGGER.info("Global hotkey registered: Ctrl+Shift+V (trigger), Esc (cancel)")
            true
        } catch (e: Throwable) {
            LOGGER.log(Level.WARNING, "Failed to register global hotkey: ${e.message}")
            false
        }
    }

    fun unregister() {
        if (!registered.get()) return

        try {
            keyListener?.let { GlobalScreen.removeNativeKeyListener(it) }
            GlobalScreen.unregisterNativeHook()
        } catch (_: Throwable) {
        } finally {
            registered.set(false)
            ctrlPressed.set(false)
            shiftPressed.set(false)
        }
    }

    fun isRegistered(): Boolean = registered.get()
}
