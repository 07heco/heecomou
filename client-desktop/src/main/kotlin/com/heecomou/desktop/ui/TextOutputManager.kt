package com.heecomou.desktop.ui

import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.util.logging.Level
import java.util.logging.Logger

class TextOutputManager {

    companion object {
        private val LOGGER = Logger.getLogger(TextOutputManager::class.java.name)
        private const val PASTE_DELAY_MS = 50
        private const val TYPE_CHAR_DELAY_MS = 5
    }

    private var robot: Robot? = null
    private var clipboard: Clipboard? = null

    init {
        try {
            robot = Robot()
            clipboard = Toolkit.getDefaultToolkit().systemClipboard
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE, "Failed to initialize TextOutputManager: ${e.message}", e)
        }
    }

    fun isAvailable(): Boolean = robot != null

    fun outputViaClipboard(text: String): Boolean {
        if (text.isEmpty()) return false

        val r = robot ?: run {
            LOGGER.warning("Robot not available for clipboard paste")
            return false
        }

        return try {
            val selection = StringSelection(text)
            clipboard?.setContents(selection, null)

            Thread.sleep(PASTE_DELAY_MS.toLong())

            r.keyPress(KeyEvent.VK_CONTROL)
            r.keyPress(KeyEvent.VK_V)
            Thread.sleep(20)
            r.keyRelease(KeyEvent.VK_V)
            r.keyRelease(KeyEvent.VK_CONTROL)

            LOGGER.info("Text output via clipboard: ${text.length} chars")
            true
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Clipboard paste failed: ${e.message}", e)
            false
        }
    }

    fun outputViaTyping(text: String, delayMs: Int = TYPE_CHAR_DELAY_MS): Boolean {
        if (text.isEmpty()) return false

        val r = robot ?: run {
            LOGGER.warning("Robot not available for typing")
            return false
        }

        return try {
            for (char in text) {
                typeCharacter(r, char)
                Thread.sleep(delayMs.toLong())
            }
            LOGGER.info("Text output via typing: ${text.length} chars")
            true
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Typing output failed: ${e.message}", e)
            false
        }
    }

    fun output(text: String): Boolean {
        if (text.isEmpty()) return false

        if (outputViaClipboard(text)) return true

        LOGGER.info("Clipboard paste failed, falling back to per-character typing")
        return outputViaTyping(text)
    }

    fun writeToClipboard(text: String): Boolean {
        if (text.isEmpty()) return false

        return try {
            val selection = StringSelection(text)
            clipboard?.setContents(selection, null)
            true
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Failed to write to clipboard: ${e.message}", e)
            false
        }
    }

    fun release() {
        robot = null
        clipboard = null
    }

    private fun typeCharacter(robot: Robot, char: Char) {
        when {
            char == '\n' -> {
                robot.keyPress(KeyEvent.VK_ENTER)
                robot.keyRelease(KeyEvent.VK_ENTER)
            }
            char == '\t' -> {
                robot.keyPress(KeyEvent.VK_TAB)
                robot.keyRelease(KeyEvent.VK_TAB)
            }
            char.isUpperCase() || !char.isLetter() && needsShift(char) -> {
                robot.keyPress(KeyEvent.VK_SHIFT)
                typeAsciiKey(robot, char)
                robot.keyRelease(KeyEvent.VK_SHIFT)
            }
            else -> {
                typeAsciiKey(robot, char)
            }
        }
    }

    private fun needsShift(char: Char): Boolean {
        return when (char) {
            '!' -> true; '@' -> true; '#' -> true; '$' -> true; '%' -> true
            '^' -> true; '&' -> true; '*' -> true; '(' -> true; ')' -> true
            '_' -> true; '+' -> true; '{' -> true; '}' -> true; '|' -> true
            ':' -> true; '"' -> true; '<' -> true; '>' -> true; '?' -> true
            '~' -> true
            else -> false
        }
    }

    private fun typeAsciiKey(robot: Robot, char: Char) {
        val keyCode = charToKeyCode(char)
        if (keyCode != KeyEvent.VK_UNDEFINED) {
            robot.keyPress(keyCode)
            robot.keyRelease(keyCode)
        }
    }

    private fun charToKeyCode(char: Char): Int {
        return when (char.uppercaseChar()) {
            'A' -> KeyEvent.VK_A; 'B' -> KeyEvent.VK_B; 'C' -> KeyEvent.VK_C
            'D' -> KeyEvent.VK_D; 'E' -> KeyEvent.VK_E; 'F' -> KeyEvent.VK_F
            'G' -> KeyEvent.VK_G; 'H' -> KeyEvent.VK_H; 'I' -> KeyEvent.VK_I
            'J' -> KeyEvent.VK_J; 'K' -> KeyEvent.VK_K; 'L' -> KeyEvent.VK_L
            'M' -> KeyEvent.VK_M; 'N' -> KeyEvent.VK_N; 'O' -> KeyEvent.VK_O
            'P' -> KeyEvent.VK_P; 'Q' -> KeyEvent.VK_Q; 'R' -> KeyEvent.VK_R
            'S' -> KeyEvent.VK_S; 'T' -> KeyEvent.VK_T; 'U' -> KeyEvent.VK_U
            'V' -> KeyEvent.VK_V; 'W' -> KeyEvent.VK_W; 'X' -> KeyEvent.VK_X
            'Y' -> KeyEvent.VK_Y; 'Z' -> KeyEvent.VK_Z
            '0' -> KeyEvent.VK_0; '1' -> KeyEvent.VK_1; '2' -> KeyEvent.VK_2
            '3' -> KeyEvent.VK_3; '4' -> KeyEvent.VK_4; '5' -> KeyEvent.VK_5
            '6' -> KeyEvent.VK_6; '7' -> KeyEvent.VK_7; '8' -> KeyEvent.VK_8
            '9' -> KeyEvent.VK_9
            ' ' -> KeyEvent.VK_SPACE
            '-' -> KeyEvent.VK_MINUS; '=' -> KeyEvent.VK_EQUALS
            '[' -> KeyEvent.VK_OPEN_BRACKET; ']' -> KeyEvent.VK_CLOSE_BRACKET
            '\\' -> KeyEvent.VK_BACK_SLASH; ';' -> KeyEvent.VK_SEMICOLON
            '\'' -> KeyEvent.VK_QUOTE; ',' -> KeyEvent.VK_COMMA
            '.' -> KeyEvent.VK_PERIOD; '/' -> KeyEvent.VK_SLASH
            '`' -> KeyEvent.VK_BACK_QUOTE
            else -> KeyEvent.VK_UNDEFINED
        }
    }
}
