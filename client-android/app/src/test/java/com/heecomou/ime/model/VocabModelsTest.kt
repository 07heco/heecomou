package com.heecomou.ime.model

import org.junit.Assert.*
import org.junit.Test

class VocabModelsTest {

    @Test
    fun `vocabVO creates correctly`() {
        val vo = VocabVO(
            id = 1L,
            userId = 100L,
            word = "测试",
            pinyin = "ce shi",
            category = "custom",
            frequency = 5,
            version = 1000L,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-02"
        )
        assertEquals(1L, vo.id)
        assertEquals(100L, vo.userId)
        assertEquals("测试", vo.word)
        assertEquals("ce shi", vo.pinyin)
        assertEquals("custom", vo.category)
        assertEquals(5, vo.frequency)
        assertEquals(1000L, vo.version)
    }

    @Test
    fun `vocabAddRequest creates correctly`() {
        val req = VocabAddRequest(
            word = "人工智能",
            pinyin = null,
            category = "tech"
        )
        assertEquals("人工智能", req.word)
        assertNull(req.pinyin)
        assertEquals("tech", req.category)
    }

    @Test
    fun `vocabListResponse creates correctly`() {
        val items = listOf(
            VocabVO(1, 100, "a", "b", "c", 1, 1, null, null)
        )
        val resp = VocabListResponse(items, 100, 1, 20)
        assertEquals(1, resp.items.size)
        assertEquals(100, resp.total)
        assertEquals(1, resp.page)
        assertEquals(20, resp.size)
    }

    @Test
    fun `vocabListResponse empty items`() {
        val resp = VocabListResponse(emptyList(), 0, 1, 20)
        assertTrue(resp.items.isEmpty())
        assertEquals(0, resp.total)
    }

    @Test
    fun `vocabVO nullable fields`() {
        val vo = VocabVO(1, 100, "test", null, null, 1, 1, null, null)
        assertNull(vo.pinyin)
        assertNull(vo.category)
        assertNull(vo.createdAt)
        assertNull(vo.updatedAt)
    }

    @Test
    fun `vocabAddRequest all fields`() {
        val req = VocabAddRequest("word", "pin yin", "cat")
        assertEquals("word", req.word)
        assertEquals("pin yin", req.pinyin)
        assertEquals("cat", req.category)
    }

    @Test
    fun `vocabVO copy works`() {
        val vo = VocabVO(1, 100, "orig", "py", "cat", 1, 1, null, null)
        val updated = vo.copy(word = "new word", frequency = 10)
        assertEquals("new word", updated.word)
        assertEquals(10, updated.frequency)
        assertEquals("orig", vo.word)
    }
}
