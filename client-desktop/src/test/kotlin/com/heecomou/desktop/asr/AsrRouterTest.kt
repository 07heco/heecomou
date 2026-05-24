package com.heecomou.desktop.asr

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsrRouterTest {

    private val router = AsrRouter()

    @Test
    @DisplayName("should route to LOCAL when network is unavailable")
    fun `route local when network unavailable`() {
        val decision = router.decide(
            preferences = AsrPreferences(),
            isNetworkAvailable = false
        )
        assertEquals(AsrEngineMode.LOCAL, decision.mode)
        assertTrue(decision.reason.contains("网络不可用"))
    }

    @Test
    @DisplayName("should route to LOCAL when manually selected")
    fun `route local when manually selected`() {
        val decision = router.decide(
            preferences = AsrPreferences(engineMode = AsrEngineMode.LOCAL),
            isNetworkAvailable = true
        )
        assertEquals(AsrEngineMode.LOCAL, decision.mode)
        assertTrue(decision.reason.contains("手动选择"))
    }

    @Test
    @DisplayName("should route to CLOUD when manually selected")
    fun `route cloud when manually selected`() {
        val decision = router.decide(
            preferences = AsrPreferences(engineMode = AsrEngineMode.CLOUD),
            isNetworkAvailable = true
        )
        assertEquals(AsrEngineMode.CLOUD, decision.mode)
        assertTrue(decision.reason.contains("手动选择"))
    }

    @Test
    @DisplayName("should route to LOCAL when privacy first")
    fun `route local when privacy first`() {
        val decision = router.decide(
            preferences = AsrPreferences(privacyFirst = true),
            isNetworkAvailable = true
        )
        assertEquals(AsrEngineMode.LOCAL, decision.mode)
        assertTrue(decision.reason.contains("隐私优先"))
    }

    @Test
    @DisplayName("should route to LOCAL when network RTT is high")
    fun `route local when high latency`() {
        val decision = router.decide(
            preferences = AsrPreferences(),
            isNetworkAvailable = true,
            networkRttMs = 600
        )
        assertEquals(AsrEngineMode.LOCAL, decision.mode)
        assertTrue(decision.reason.contains("延迟过高"))
    }

    @Test
    @DisplayName("should route to CLOUD when noise level is high")
    fun `route cloud when high noise`() {
        val decision = router.decide(
            preferences = AsrPreferences(),
            isNetworkAvailable = true,
            noiseLevel = 3
        )
        assertEquals(AsrEngineMode.CLOUD, decision.mode)
        assertTrue(decision.reason.contains("高噪声"))
    }

    @Test
    @DisplayName("should default to CLOUD under normal conditions")
    fun `default route to cloud`() {
        val decision = router.decide(
            preferences = AsrPreferences(),
            isNetworkAvailable = true,
            networkRttMs = 50,
            noiseLevel = 0
        )
        assertEquals(AsrEngineMode.CLOUD, decision.mode)
        assertTrue(decision.reason.contains("默认"))
    }

    @Test
    @DisplayName("should not route to LOCAL for moderate latency with AUTO")
    fun `cloud for moderate latency`() {
        val decision = router.decide(
            preferences = AsrPreferences(),
            isNetworkAvailable = true,
            networkRttMs = 400
        )
        assertEquals(AsrEngineMode.CLOUD, decision.mode)
    }

    @Test
    @DisplayName("manual LOCAL overrides privacy first")
    fun `manual local overrides privacy`() {
        val decision = router.decide(
            preferences = AsrPreferences(
                engineMode = AsrEngineMode.LOCAL,
                privacyFirst = true
            ),
            isNetworkAvailable = true
        )
        assertEquals(AsrEngineMode.LOCAL, decision.mode)
        assertTrue(decision.reason.contains("手动选择"))
    }

    @Test
    @DisplayName("manual CLOUD overrides privacy first")
    fun `manual cloud overrides privacy`() {
        val decision = router.decide(
            preferences = AsrPreferences(
                engineMode = AsrEngineMode.CLOUD,
                privacyFirst = true
            ),
            isNetworkAvailable = true
        )
        assertEquals(AsrEngineMode.CLOUD, decision.mode)
        assertTrue(decision.reason.contains("手动选择"))
    }

    @Test
    @DisplayName("should have default preferences")
    fun `default preferences`() {
        val prefs = AsrPreferences()
        assertEquals(AsrEngineMode.AUTO, prefs.engineMode)
        assertEquals("zh", prefs.dialect)
        assertFalse(prefs.privacyFirst)
    }

    @Test
    @DisplayName("AsrEngineMode should have 3 values")
    fun `AsrEngineMode has three values`() {
        val modes = AsrEngineMode.entries
        assertEquals(3, modes.size)
        assertEquals(AsrEngineMode.AUTO, AsrEngineMode.valueOf("AUTO"))
        assertEquals(AsrEngineMode.CLOUD, AsrEngineMode.valueOf("CLOUD"))
        assertEquals(AsrEngineMode.LOCAL, AsrEngineMode.valueOf("LOCAL"))
    }

    @Test
    @DisplayName("should list supported dialects")
    fun `supported dialects`() {
        val dialects = router.getSupportedDialects()
        assertEquals(4, dialects.size)
        assertTrue(dialects.containsKey("zh"))
        assertTrue(dialects.containsKey("yue"))
        assertTrue(dialects.containsKey("wuu"))
        assertTrue(dialects.containsKey("en"))
        assertEquals("普通话", dialects["zh"])
        assertEquals("粤语", dialects["yue"])
    }
}
