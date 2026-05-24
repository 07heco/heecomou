package com.heecomou.desktop.vocab

import com.heecomou.desktop.network.VocabVO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.*

class LocalVocabStoreTest {

    private lateinit var store: LocalVocabStore
    private val testDbPath = "test-vocab-${System.currentTimeMillis()}.db"

    @BeforeEach
    fun setUp() {
        store = LocalVocabStore(testDbPath)
    }

    @AfterEach
    fun tearDown() {
        runCatching {
            store.close()
            File(testDbPath).delete()
        }
    }

    @Test
    @DisplayName("should have 0 words initially")
    fun `empty store has zero words`() {
        assertEquals(0, store.countWords())
        assertEquals(0L, store.getMaxVersion())
    }

    @Test
    @DisplayName("upsertBatch should insert vocab items")
    fun `upsertBatch inserts items`() {
        val items = listOf(
            VocabVO(1L, 1L, "微服务", "wei fu wu", "术语", 5, 100L, null, null),
            VocabVO(2L, 1L, "架构", "jia gou", "术语", 3, 101L, null, null)
        )
        store.upsertBatch(items)

        assertEquals(2, store.countWords())
        assertEquals(101L, store.getMaxVersion())
    }

    @Test
    @DisplayName("upsertBatch should update existing items")
    fun `upsertBatch updates existing items`() {
        val initial = listOf(
            VocabVO(1L, 1L, "测试", "ce shi", "通用", 1, 10L, null, null)
        )
        store.upsertBatch(initial)
        assertEquals(1, store.countWords())
        assertEquals(1, store.getWord("测试"))

        val updated = listOf(
            VocabVO(1L, 1L, "测试", "ce shi", "通用", 5, 20L, null, null)
        )
        store.upsertBatch(updated)
        assertEquals(1, store.countWords())
        assertEquals(5, store.getWord("测试"))
        assertEquals(20L, store.getMaxVersion())
    }

    @Test
    @DisplayName("search should find items by word")
    fun `search finds by word`() {
        val items = listOf(
            VocabVO(1L, 1L, "微服务架构", "wei fu wu jia gou", "术语", 5, 100L, null, null),
            VocabVO(2L, 1L, "分布式系统", "fen bu shi xi tong", "术语", 3, 101L, null, null),
            VocabVO(3L, 1L, "微前端", "wei qian duan", "术语", 2, 102L, null, null)
        )
        store.upsertBatch(items)

        val results = store.search("微")
        assertEquals(2, results.size)
        assertEquals("微服务架构", results[0].first)
        assertEquals(5, results[0].second)
    }

    @Test
    @DisplayName("search should find items by pinyin")
    fun `search finds by pinyin`() {
        val items = listOf(
            VocabVO(1L, 1L, "你好", "ni hao", "通用", 10, 100L, null, null)
        )
        store.upsertBatch(items)

        val results = store.search("ni")
        assertEquals(1, results.size)
        assertEquals("你好", results[0].first)
    }

    @Test
    @DisplayName("getWord should return frequency or 0")
    fun `getWord returns frequency`() {
        val items = listOf(
            VocabVO(1L, 1L, "架构", "jia gou", "术语", 5, 100L, null, null)
        )
        store.upsertBatch(items)

        assertEquals(5, store.getWord("架构"))
        assertEquals(0, store.getWord("不存在的词"))
    }

    @Test
    @DisplayName("bumpFrequency should increment and update version")
    fun `bumpFrequency increments count`() {
        val items = listOf(
            VocabVO(1L, 1L, "常用词", "chang yong ci", "通用", 3, 100L, null, null)
        )
        store.upsertBatch(items)

        store.bumpFrequency("常用词")
        assertEquals(4, store.getWord("常用词"))
        assertTrue(store.getMaxVersion() > 100L)
    }

    @Test
    @DisplayName("search should return results ordered by frequency desc")
    fun `search results ordered by frequency`() {
        val items = listOf(
            VocabVO(1L, 1L, "低频词A", "a", "通用", 1, 100L, null, null),
            VocabVO(2L, 1L, "高频词A", "a", "通用", 100, 101L, null, null),
            VocabVO(3L, 1L, "中频词A", "a", "通用", 50, 102L, null, null)
        )
        store.upsertBatch(items)

        val results = store.search("a")
        assertEquals(3, results.size)
        // Should be ordered by frequency descending
        assertTrue(results[0].second >= results[1].second)
        assertTrue(results[1].second >= results[2].second)
    }

    @Test
    @DisplayName("upsertBatch empty list should not fail")
    fun `upsertBatch empty list`() {
        store.upsertBatch(emptyList())
        assertEquals(0, store.countWords())
        assertEquals(0L, store.getMaxVersion())
    }
}
