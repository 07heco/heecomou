package com.heecomou.desktop.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class VoiceInputStateTest {

    @Test
    @DisplayName("VoiceInputState should have 4 values")
    fun `voice input state has four values`() {
        val states = VoiceInputState.entries
        assertEquals(4, states.size)
        assertEquals(VoiceInputState.IDLE, states[0])
        assertEquals(VoiceInputState.LISTENING, states[1])
        assertEquals(VoiceInputState.RECOGNIZING, states[2])
        assertEquals(VoiceInputState.RESULT, states[3])
    }

    @Test
    @DisplayName("VoiceInputState valueOf should work")
    fun `valueOf returns correct state`() {
        assertEquals(VoiceInputState.IDLE, VoiceInputState.valueOf("IDLE"))
        assertEquals(VoiceInputState.LISTENING, VoiceInputState.valueOf("LISTENING"))
        assertEquals(VoiceInputState.RECOGNIZING, VoiceInputState.valueOf("RECOGNIZING"))
        assertEquals(VoiceInputState.RESULT, VoiceInputState.valueOf("RESULT"))
    }

    @Test
    @DisplayName("VoiceInputState name should match")
    fun `state names match`() {
        assertEquals("IDLE", VoiceInputState.IDLE.name)
        assertEquals("LISTENING", VoiceInputState.LISTENING.name)
        assertEquals("RECOGNIZING", VoiceInputState.RECOGNIZING.name)
        assertEquals("RESULT", VoiceInputState.RESULT.name)
    }

    @Test
    @DisplayName("VoiceInputState ordinal values should be sequential")
    fun `ordinal values are sequential`() {
        assertEquals(0, VoiceInputState.IDLE.ordinal)
        assertEquals(1, VoiceInputState.LISTENING.ordinal)
        assertEquals(2, VoiceInputState.RECOGNIZING.ordinal)
        assertEquals(3, VoiceInputState.RESULT.ordinal)
    }
}
