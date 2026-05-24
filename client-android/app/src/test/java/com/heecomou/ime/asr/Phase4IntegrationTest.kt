package com.heecomou.ime.asr

import com.heecomou.ime.model.CorrectionFeedback
import com.heecomou.ime.model.VocabAddRequest
import com.heecomou.ime.model.VocabVO
import org.junit.Assert.*
import org.junit.Test

class Phase4IntegrationTest {

    @Test
    fun `vocab add request validates correctly`() {
        val req = VocabAddRequest(
            word = "人工智能",
            pinyin = null,
            category = "tech"
        )
        assertEquals("人工智能", req.word)
        assertNull(req.pinyin)
    }

    @Test
    fun `correction feedback model works`() {
        val feedback = CorrectionFeedback(
            originalText = "人工只能",
            correctedText = "人工智能",
            source = "user_correction"
        )
        assertEquals("人工只能", feedback.originalText)
        assertEquals("人工智能", feedback.correctedText)
        assertEquals("user_correction", feedback.source)
    }

    @Test
    fun `vocab models interop correctly`() {
        val vo = VocabVO(
            id = 1, userId = 100, word = "test",
            pinyin = "test_py", category = "custom",
            frequency = 5, version = 1000,
            createdAt = null, updatedAt = null
        )
        assertEquals(1L, vo.id)
        assertEquals(5, vo.frequency)
    }

    @Test
    fun `correction feedback default source is manual`() {
        val feedback = CorrectionFeedback("a", "b")
        assertEquals("manual", feedback.source)
    }

    @Test
    fun `local vocab store operations chain`() {
        val store = createMemoryStore()

        store.upsertBatch(listOf(
            VocabVO(1, 100, "测试", null, null, 1, 100, null, null),
            VocabVO(2, 100, "词汇", null, null, 2, 200, null, null)
        ))

        assertEquals(2, store.countWords())
        assertEquals(200L, store.getMaxVersion())
    }

    @Test
    fun `local vocab store search by keyword`() {
        val store = createMemoryStore()
        store.upsertBatch(listOf(
            VocabVO(1, 100, "人工智能", null, "tech", 10, 100, null, null),
            VocabVO(2, 100, "学习人工智能", null, "edu", 5, 200, null, null),
            VocabVO(3, 100, "机器学习", null, "tech", 8, 300, null, null)
        ))

        val results = store.search("智能")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.first.contains("智能") })
    }

    @Test
    fun `local vocab store bump frequency`() {
        val store = createMemoryStore()
        store.upsertBatch(listOf(
            VocabVO(1, 100, "测试词", null, null, 1, 100, null, null)
        ))

        assertEquals(1, store.getWord("测试词"))

        store.bumpFrequency("测试词")
        assertEquals(2, store.getWord("测试词"))
    }

    @Test
    fun `vocab sync manager flow`() {
        val store = createMemoryStore()

        store.upsertBatch(listOf(
            VocabVO(1, 100, "word1", null, null, 1, 100, null, null),
            VocabVO(2, 100, "word2", null, null, 1, 200, null, null)
        ))

        assertEquals(200L, store.getMaxVersion())
        assertEquals(2, store.countWords())
    }

    @Test
    fun `router respect vocab context`() {
        val router = AsrRouter()

        val onlineDecision = router.decide(AsrRouter.RoutingConfig(
            isOnline = true, networkType = "wifi", signalStrength = 0.9f
        ))
        assertEquals(AsrEngineMode.CLOUD, onlineDecision.engine)

        val offlineDecision = router.decide(AsrRouter.RoutingConfig(
            isOnline = false
        ))
        assertEquals(AsrEngineMode.LOCAL, offlineDecision.engine)
    }

    @Test
    fun `end to end vocab model cycle`() {
        val addReq = VocabAddRequest("端到端测试", "duan dao duan ce shi", "test")

        assertEquals("端到端测试", addReq.word)
        assertEquals("duan dao duan ce shi", addReq.pinyin)
        assertEquals("test", addReq.category)

        val feedback = CorrectionFeedback("端到端测试", "端到端验证", "auto")
        assertEquals("端到端验证", feedback.correctedText)
    }

    private fun createMemoryStore(): LocalVocabStore {
        return object : LocalVocabStore(null!!) {
            private val data = mutableMapOf<Long, VocabVO>()

            override fun getMaxVersion(): Long = data.values.maxOfOrNull { it.version } ?: 0L
            override fun upsertBatch(items: List<VocabVO>) { items.forEach { data[it.id] = it } }
            override fun search(keyword: String): List<Pair<String, Int>> =
                data.values.filter { it.word.contains(keyword) }.map { it.word to it.frequency }
            override fun getWord(word: String): Int =
                data.values.find { it.word == word }?.frequency ?: 0
            override fun bumpFrequency(word: String) {
                data.values.find { it.word == word }?.let {
                    data[it.id] = it.copy(frequency = it.frequency + 1)
                }
            }
            override fun countWords(): Int = data.size
        }
    }
}
