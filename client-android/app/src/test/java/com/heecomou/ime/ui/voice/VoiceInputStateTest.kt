package com.heecomou.ime.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputStateTest {

    @Test
    fun `IDLE enum value exists`() {
        assertEquals("IDLE", VoiceInputState.IDLE.name)
    }

    @Test
    fun `LISTENING enum value exists`() {
        assertEquals("LISTENING", VoiceInputState.LISTENING.name)
    }

    @Test
    fun `RECOGNIZING enum value exists`() {
        assertEquals("RECOGNIZING", VoiceInputState.RECOGNIZING.name)
    }

    @Test
    fun `RESULT enum value exists`() {
        assertEquals("RESULT", VoiceInputState.RESULT.name)
    }

    @Test
    fun `ERROR enum value exists`() {
        assertEquals("ERROR", VoiceInputState.ERROR.name)
    }

    @Test
    fun `voice input state has five values`() {
        assertEquals(5, VoiceInputState.entries.size)
    }

    @Test
    fun `voice input state ordinal ordering`() {
        assertTrue(VoiceInputState.IDLE.ordinal < VoiceInputState.LISTENING.ordinal)
        assertTrue(VoiceInputState.LISTENING.ordinal < VoiceInputState.RECOGNIZING.ordinal)
        assertTrue(VoiceInputState.RECOGNIZING.ordinal < VoiceInputState.RESULT.ordinal)
        assertTrue(VoiceInputState.RESULT.ordinal < VoiceInputState.ERROR.ordinal)
    }
}
