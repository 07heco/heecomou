package com.heecomou.ime.asr

import org.junit.Assert.*
import org.junit.Test

class NoiseDetectorTest {

    @Test
    fun `classify quiet below 45dB`() {
        val detector = NoiseDetector()
        assertEquals(NoiseDetector.Level.QUIET, detector.classify(30f))
        assertEquals(NoiseDetector.Level.QUIET, detector.classify(44f))
    }

    @Test
    fun `classify moderate between 45 and 65 dB`() {
        val detector = NoiseDetector()
        assertEquals(NoiseDetector.Level.MODERATE, detector.classify(45f))
        assertEquals(NoiseDetector.Level.MODERATE, detector.classify(60f))
    }

    @Test
    fun `classify loud above 65 dB`() {
        val detector = NoiseDetector()
        assertEquals(NoiseDetector.Level.LOUD, detector.classify(65f))
        assertEquals(NoiseDetector.Level.LOUD, detector.classify(80f))
    }

    @Test
    fun `estimate noise for silence`() {
        val detector = NoiseDetector()
        val silence = FloatArray(160) { 0f }
        val db = detector.estimateNoiseDb(silence)
        assertTrue(db <= -40f)
    }

    @Test
    fun `estimate noise for normal speech`() {
        val detector = NoiseDetector()
        val speech = FloatArray(160) { Math.sin(2.0 * Math.PI * 440 * it / 16000).toFloat() * 0.5f }
        val db = detector.estimateNoiseDb(speech)
        assertTrue(db > -40f)
    }

    @Test
    fun `estimate noise empty array returns 0`() {
        val detector = NoiseDetector()
        val db = detector.estimateNoiseDb(FloatArray(0))
        assertEquals(0f, db)
    }

    @Test
    fun `level labels are correct`() {
        assertEquals("安静", NoiseDetector.Level.QUIET.label)
        assertEquals("中等", NoiseDetector.Level.MODERATE.label)
        assertEquals("嘈杂", NoiseDetector.Level.LOUD.label)
    }
}

class AsrRouterTest {

    @Test
    fun `offline forces local`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(isOnline = false))
        assertEquals(AsrEngineMode.LOCAL, decision.engine)
        assertTrue(decision.reason.contains("离线"))
    }

    @Test
    fun `low battery forces local`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(batteryLevel = 0.1f))
        assertEquals(AsrEngineMode.LOCAL, decision.engine)
    }

    @Test
    fun `battery just above threshold goes to cloud`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(batteryLevel = 0.16f))
        assertEquals(AsrEngineMode.CLOUD, decision.engine)
    }

    @Test
    fun `weak cellular goes local`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(
            networkType = "cellular",
            signalStrength = 0.3f
        ))
        assertEquals(AsrEngineMode.LOCAL, decision.engine)
    }

    @Test
    fun `good cellular goes cloud`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(
            networkType = "cellular",
            signalStrength = 0.6f
        ))
        assertEquals(AsrEngineMode.CLOUD, decision.engine)
    }

    @Test
    fun `weak WiFi goes local`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(
            networkType = "wifi",
            signalStrength = 0.2f
        ))
        assertEquals(AsrEngineMode.LOCAL, decision.engine)
    }

    @Test
    fun `sensitive context goes local`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(isSensitiveContext = true))
        assertEquals(AsrEngineMode.LOCAL, decision.engine)
    }

    @Test
    fun `default goes cloud`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig())
        assertEquals(AsrEngineMode.CLOUD, decision.engine)
    }

    @Test
    fun `resolve fallback when client available`() {
        val router = AsrRouter()
        assertEquals(AsrEngineMode.LOCAL, router.resolveLocalFallback(true))
    }

    @Test
    fun `resolve fallback when client unavailable`() {
        val router = AsrRouter()
        assertEquals(AsrEngineMode.CLOUD, router.resolveLocalFallback(false))
    }

    @Test
    fun `low battery overrides sensitive context`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(
            batteryLevel = 0.05f,
            isSensitiveContext = true
        ))
        assertEquals(AsrEngineMode.LOCAL, decision.engine)
        assertTrue(decision.reason.contains("低电量"))
    }
}
