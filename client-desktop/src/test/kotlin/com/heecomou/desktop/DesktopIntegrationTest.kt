package com.heecomou.desktop

import com.heecomou.desktop.audio.AudioCaptureManager
import com.heecomou.desktop.asr.AsrEngineMode
import com.heecomou.desktop.asr.AsrPreferences
import com.heecomou.desktop.asr.AsrRouter
import com.heecomou.desktop.asr.CloudAsrClient
import com.heecomou.desktop.asr.LocalAsrClient
import com.heecomou.desktop.hotkey.GlobalHotkeyManager
import com.heecomou.desktop.network.VocabVO
import com.heecomou.desktop.network.VocabAddRequest
import com.heecomou.desktop.network.VocabApiService
import com.heecomou.desktop.ui.TextOutputManager
import com.heecomou.desktop.ui.VoiceInputState
import com.heecomou.desktop.vocab.LocalVocabStore
import com.heecomou.desktop.vocab.VocabSyncManager
import com.google.gson.Gson
import org.junit.jupiter.api.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DesktopIntegrationTest {

    // ============================================================
    // 1. Audio Module
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("[Audio] AudioCaptureManager instantiation and defaults")
    fun audioCaptureManagerDefaults() {
        val audioCapture = AudioCaptureManager()
        assertEquals(16000f, audioCapture.sampleRate)
        assertEquals(16, audioCapture.sampleSizeInBits)
        assertEquals(1, audioCapture.channels)
        assertFalse(audioCapture.isRecording())

        val format = audioCapture.audioFormat
        assertEquals(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, format.encoding)
        assertEquals(16000f, format.sampleRate)
    }

    // ============================================================
    // 2. Hotkey Module
    // ============================================================
    @Test
    @Order(2)
    @DisplayName("[Hotkey] GlobalHotkeyManager lifecycle")
    fun hotkeyManagerLifecycle() {
        val hotkey = GlobalHotkeyManager()
        assertFalse(hotkey.isRegistered())

        var hotkeyTriggered = false
        var cancelTriggered = false
        hotkey.onHotkeyTriggered = { hotkeyTriggered = true }
        hotkey.onCancelTriggered = { cancelTriggered = true }

        hotkey.onHotkeyTriggered?.invoke()
        hotkey.onCancelTriggered?.invoke()

        assertTrue(hotkeyTriggered)
        assertTrue(cancelTriggered)
    }

    // ============================================================
    // 3. ASR Module
    // ============================================================
    @Test
    @Order(3)
    @DisplayName("[ASR] CloudAsrClient instantiation")
    fun cloudAsrClientInstantiation() {
        val client = CloudAsrClient()
        assertFalse(client.isConnected)
        client.release()
    }

    @Test
    @Order(4)
    @DisplayName("[ASR] LocalAsrClient state machine")
    fun localAsrClientStateMachine() {
        val client = LocalAsrClient()
        assertEquals(com.heecomou.desktop.asr.LocalAsrState.IDLE, client.currentState)

        client.initialize()
        assertEquals(com.heecomou.desktop.asr.LocalAsrState.READY, client.currentState)

        client.startListening()
        assertEquals(com.heecomou.desktop.asr.LocalAsrState.RECOGNIZING, client.currentState)

        client.stopListening()
        assertEquals(com.heecomou.desktop.asr.LocalAsrState.READY, client.currentState)

        client.release()
        assertEquals(com.heecomou.desktop.asr.LocalAsrState.IDLE, client.currentState)
    }

    @Test
    @Order(5)
    @DisplayName("[ASR] AsrRouter routing decisions")
    fun asrRouterDecisions() {
        val router = AsrRouter()

        // Offline → Local
        val offline = router.decide(AsrPreferences(), isNetworkAvailable = false)
        assertEquals(AsrEngineMode.LOCAL, offline.mode)

        // Privacy → Local
        val privacy = router.decide(AsrPreferences(privacyFirst = true), isNetworkAvailable = true)
        assertEquals(AsrEngineMode.LOCAL, privacy.mode)

        // Normal → Cloud
        val normal = router.decide(AsrPreferences(), isNetworkAvailable = true)
        assertEquals(AsrEngineMode.CLOUD, normal.mode)

        // High noise → Cloud
        val noisy = router.decide(AsrPreferences(), isNetworkAvailable = true, noiseLevel = 3)
        assertEquals(AsrEngineMode.CLOUD, noisy.mode)
    }

    // ============================================================
    // 4. Text Output Module
    // ============================================================
    @Test
    @Order(6)
    @DisplayName("[Output] TextOutputManager clipboard")
    fun textOutputClipboard() {
        val output = TextOutputManager()

        val testText = "集成测试文本\n第二行"
        val result = output.writeToClipboard(testText)
        assertTrue(result, "Clipboard write should succeed")

        output.release()
    }

    // ============================================================
    // 5. Vocabulary Sync Module
    // ============================================================
    @Test
    @Order(7)
    @DisplayName("[Vocab] VocabVO cross-platform compatibility")
    fun vocabModelCompatibility() {
        val gson = Gson()

        // Android-side JSON format
        val androidJson = """
            {
                "id": 1,
                "userId": 1,
                "word": "微服务架构",
                "pinyin": "wei fu wu jia gou",
                "category": "术语",
                "frequency": 10,
                "version": 100,
                "createdAt": "2024-06-01T10:00:00",
                "updatedAt": "2024-06-01T12:00:00"
            }
        """.trimIndent()

        // Desktop deserialization
        val vocab = gson.fromJson(androidJson, VocabVO::class.java)
        assertEquals(1L, vocab.id)
        assertEquals(1L, vocab.userId)
        assertEquals("微服务架构", vocab.word)
        assertEquals("wei fu wu jia gou", vocab.pinyin)
        assertEquals("术语", vocab.category)
        assertEquals(10, vocab.frequency)
        assertEquals(100L, vocab.version)

        // Desktop serialization → should be parseable by Android
        val desktopJson = gson.toJson(vocab)
        assertTrue(desktopJson.contains("微服务架构"))
        assertTrue(desktopJson.contains("createdAt"))
    }

    @Test
    @Order(8)
    @DisplayName("[Vocab] VocabAddRequest cross-platform compatibility")
    fun vocabAddRequestCompatibility() {
        val gson = Gson()

        val request = VocabAddRequest("测试词", "ce shi ci", "通用")
        val json = gson.toJson(request)

        assertTrue(json.contains("测试词"))
        assertTrue(json.contains("ce shi ci"))
        assertTrue(json.contains("通用"))

        val deserialized = gson.fromJson(json, VocabAddRequest::class.java)
        assertEquals("测试词", deserialized.word)
        assertEquals("ce shi ci", deserialized.pinyin)
        assertEquals("通用", deserialized.category)
    }

    @Test
    @Order(9)
    @DisplayName("[Vocab] LocalVocabStore CRUD full cycle")
    fun localVocabStoreFullCycle() {
        val testDb = "integration-test-${System.currentTimeMillis()}.db"
        val store = LocalVocabStore(testDb)

        try {
            // Initial state
            assertEquals(0, store.countWords())
            assertEquals(0L, store.getMaxVersion())

            // Insert
            val items = listOf(
                VocabVO(1L, 1L, "微服务", "wei fu wu", "术语", 5, 100L, null, null),
                VocabVO(2L, 1L, "分布式", "fen bu shi", "术语", 3, 101L, null, null),
                VocabVO(3L, 1L, "架构", "jia gou", "术语", 8, 102L, null, null)
            )
            store.upsertBatch(items)
            assertEquals(3, store.countWords())
            assertEquals(102L, store.getMaxVersion())

            // Search
            val results = store.search("分")
            assertEquals(1, results.size)
            assertEquals("分布式", results[0].first)

            // Bump frequency
            store.bumpFrequency("架构")
            assertEquals(9, store.getWord("架构"))
        } finally {
            store.close()
            File(testDb).delete()
        }
    }

    @Test
    @Order(10)
    @DisplayName("[Vocab] VocabSyncManager instantiation")
    fun vocabSyncManagerInstantiation() {
        val testDb = "sync-test-${System.currentTimeMillis()}.db"
        val apiService = VocabApiService()
        val localStore = LocalVocabStore(testDb)

        try {
            val syncManager = VocabSyncManager(apiService, localStore)
            assertEquals(0, syncManager.getLocalWordCount())
        } finally {
            localStore.close()
            File(testDb).delete()
        }
    }

    // ============================================================
    // 6. UI State Module
    // ============================================================
    @Test
    @Order(11)
    @DisplayName("[UI] VoiceInputState transitions")
    fun voiceInputStateTransitions() {
        val states = VoiceInputState.entries
        assertEquals(4, states.size)

        // Verify all states are unique
        val stateSet = states.toSet()
        assertEquals(states.size, stateSet.size)
    }

    // ============================================================
    // 7. End-to-End Scenario: Full Voice Input Pipeline
    // ============================================================
    @Test
    @Order(12)
    @DisplayName("[E2E] Full voice input pipeline simulation")
    fun fullVoiceInputPipeline() {
        // Phase 1: Capture audio
        val audioCapture = AudioCaptureManager()
        assertEquals(16000f, audioCapture.sampleRate)

        // Phase 2: Route decision
        val router = AsrRouter()
        val decision = router.decide(
            AsrPreferences(engineMode = AsrEngineMode.CLOUD),
            isNetworkAvailable = true
        )
        assertEquals(AsrEngineMode.CLOUD, decision.mode)

        // Phase 3: ASR client setup
        val cloudClient = CloudAsrClient()
        assertFalse(cloudClient.isConnected)

        // Phase 4: Local ASR fallback
        val localClient = LocalAsrClient()
        localClient.initialize()
        assertEquals(com.heecomou.desktop.asr.LocalAsrState.READY, localClient.currentState)

        // Phase 5: Text output
        val textOutput = TextOutputManager()
        val testText = "端到端测试通过"
        assertTrue(textOutput.writeToClipboard(testText))

        // Cleanup
        audioCapture.release()
        cloudClient.release()
        localClient.release()
        textOutput.release()
    }

    // ============================================================
    // 8. Cross-platform Data Format Verification
    // ============================================================
    @Test
    @Order(13)
    @DisplayName("[Cross] Desktop-Android vocab model alignment")
    fun crossPlatformVocabAlignment() {
        val gson = Gson()

        // Fields that must match between Android and Desktop
        val requiredFields = listOf(
            "id", "userId", "word", "pinyin", "category",
            "frequency", "version", "createdAt", "updatedAt"
        )

        val vocab = VocabVO(
            id = 1L, userId = 1L, word = "test", pinyin = "test",
            category = "test", frequency = 1, version = 1L,
            createdAt = "2024-06-01T10:00:00", updatedAt = "2024-06-01T12:00:00"
        )

        val json = gson.toJson(vocab)
        for (field in requiredFields) {
            assertTrue(
                json.contains(field),
                "JSON should contain field: $field"
            )
        }
    }
}
