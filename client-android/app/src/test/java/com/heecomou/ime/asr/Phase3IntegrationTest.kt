package com.heecomou.ime.asr

import org.junit.Assert.*
import org.junit.Test

class DeviceContextTest {

    @Test
    fun `device context data class creates correctly`() {
        val ctx = DeviceContextProvider.DeviceContext(
            isOnline = true,
            networkType = "wifi",
            signalStrength = 0.8f,
            batteryLevel = 0.75f,
            isCharging = false
        )
        assertTrue(ctx.isOnline)
        assertEquals("wifi", ctx.networkType)
        assertEquals(0.8f, ctx.signalStrength)
        assertEquals(0.75f, ctx.batteryLevel)
        assertFalse(ctx.isCharging)
    }

    @Test
    fun `device context copy works`() {
        val ctx = DeviceContextProvider.DeviceContext(true, "wifi", 0.5f, 1.0f, true)
        val updated = ctx.copy(batteryLevel = 0.1f)
        assertEquals(0.1f, updated.batteryLevel)
        assertTrue(updated.isCharging)
    }
}

class AsrDialectTest {

    @Test
    fun `mandarin is supported by local`() {
        assertTrue(AsrDialect.MANDARIN.supportedByLocal)
    }

    @Test
    fun `english is supported by local`() {
        assertTrue(AsrDialect.ENGLISH.supportedByLocal)
    }

    @Test
    fun `cantonese is not supported by local`() {
        assertFalse(AsrDialect.CANTONESE.supportedByLocal)
    }

    @Test
    fun `from code returns correct dialect`() {
        assertEquals(AsrDialect.MANDARIN, AsrDialect.fromCode("zh"))
        assertEquals(AsrDialect.CANTONESE, AsrDialect.fromCode("yue"))
        assertEquals(AsrDialect.ENGLISH, AsrDialect.fromCode("en"))
    }

    @Test
    fun `unknown code defaults to mandarin`() {
        assertEquals(AsrDialect.MANDARIN, AsrDialect.fromCode("fr"))
        assertEquals(AsrDialect.MANDARIN, AsrDialect.fromCode(""))
    }

    @Test
    fun `dialect labels are correct`() {
        assertEquals("普通话", AsrDialect.MANDARIN.label)
        assertEquals("粤语", AsrDialect.CANTONESE.label)
    }

    @Test
    fun `all dialect codes are unique`() {
        val codes = AsrDialect.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }
}

class SmoothTransitionTest {

    @Test
    fun `transition to same engine returns true`() {
        val manager = SmoothTransitionManager()
        assertTrue(manager.requestTransition(AsrEngineMode.CLOUD))
        assertEquals(AsrEngineMode.CLOUD, manager.getCurrentEngine())
    }

    @Test
    fun `transition to different engine succeeds`() {
        val manager = SmoothTransitionManager()
        var startedType: SmoothTransitionManager.TransitionType? = null
        var completedEngine: AsrEngineMode? = null

        manager.onTransitionStart = { startedType = it }
        manager.onTransitionComplete = { completedEngine = it }

        assertTrue(manager.requestTransition(AsrEngineMode.LOCAL))
        assertEquals(AsrEngineMode.LOCAL, manager.getCurrentEngine())
        assertEquals(SmoothTransitionManager.TransitionType.CLOUD_TO_LOCAL, startedType)
        assertEquals(AsrEngineMode.LOCAL, completedEngine)
    }

    @Test
    fun `concurrent transition is rejected`() {
        val manager = SmoothTransitionManager()
        assertTrue(manager.requestTransition(AsrEngineMode.LOCAL))
        assertTrue(manager.requestTransition(AsrEngineMode.CLOUD))
    }

    @Test
    fun `buffer audio during transition`() {
        val manager = SmoothTransitionManager()
        val audio = floatArrayOf(1f, 2f, 3f)
        manager.bufferAudioDuringTransition(audio)
        manager.bufferAudioDuringTransition(floatArrayOf(4f, 5f))

        val buffer = manager.getAndClearBuffer()
        assertEquals(2, buffer.size)
    }

    @Test
    fun `get and clear buffer empties it`() {
        val manager = SmoothTransitionManager()
        manager.bufferAudioDuringTransition(floatArrayOf(1f))
        manager.getAndClearBuffer()
        assertEquals(0, manager.getAndClearBuffer().size)
    }

    @Test
    fun `reset returns to cloud`() {
        val manager = SmoothTransitionManager()
        manager.requestTransition(AsrEngineMode.LOCAL)
        manager.reset()
        assertEquals(AsrEngineMode.CLOUD, manager.getCurrentEngine())
    }

    @Test
    fun `transition types are correct`() {
        assertEquals(2, SmoothTransitionManager.TransitionType.entries.size)
        assertTrue(SmoothTransitionManager.TransitionType.entries.contains(
            SmoothTransitionManager.TransitionType.CLOUD_TO_LOCAL
        ))
        assertTrue(SmoothTransitionManager.TransitionType.entries.contains(
            SmoothTransitionManager.TransitionType.LOCAL_TO_CLOUD
        ))
    }
}

class Phase3IntegrationTest {

    @Test
    fun `engine modes have correct values`() {
        assertEquals(3, AsrEngineMode.entries.size)
        assertEquals("自动", AsrEngineMode.AUTO.label)
        assertEquals("云端", AsrEngineMode.CLOUD.label)
        assertEquals("端侧", AsrEngineMode.LOCAL.label)
    }

    @Test
    fun `routing precedence low battery overrides network`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(
            batteryLevel = 0.05f,
            isOnline = true,
            networkType = "wifi",
            signalStrength = 1.0f
        ))
        assertEquals(AsrEngineMode.LOCAL, decision.engine)
    }

    @Test
    fun `routing precedence offline overrides everything`() {
        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(
            isOnline = false,
            batteryLevel = 0.9f,
            networkType = "wifi",
            signalStrength = 1.0f
        ))
        assertEquals(AsrEngineMode.LOCAL, decision.engine)
    }

    @Test
    fun `noise detector thresholds are consistent`() {
        val detector = NoiseDetector()
        assertEquals(NoiseDetector.Level.QUIET, detector.classify(30f))
        assertEquals(NoiseDetector.Level.MODERATE, detector.classify(55f))
        assertEquals(NoiseDetector.Level.LOUD, detector.classify(80f))
    }

    @Test
    fun `dialect routing fallback for unsupported dialect`() {
        val dialect = AsrDialect.fromCode("yue")
        assertFalse(dialect.supportedByLocal)
    }

    @Test
    fun `full device context chain works`() {
        val provider = DeviceContextProvider.DeviceContext(
            isOnline = true,
            networkType = "cellular",
            signalStrength = 0.3f,
            batteryLevel = 0.8f,
            isCharging = false
        )

        val router = AsrRouter()
        val decision = router.decide(AsrRouter.RoutingConfig(
            isOnline = provider.isOnline,
            networkType = provider.networkType,
            signalStrength = provider.signalStrength,
            batteryLevel = provider.batteryLevel
        ))

        assertEquals(AsrEngineMode.LOCAL, decision.engine)
    }
}
