package com.heecomou.desktop.network

import com.google.gson.Gson
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VocabModelsTest {

    private val gson = Gson()

    @Test
    @DisplayName("ApiResponse serialization should work")
    fun `ApiResponse serialization`() {
        val response = ApiResponse(200, "success", "test data")
        val json = gson.toJson(response)

        assertTrue(json.contains("200"))
        assertTrue(json.contains("success"))
        assertTrue(json.contains("test data"))

        val deserialized = gson.fromJson(json, ApiResponse::class.java)
        assertEquals(200, deserialized.code)
        assertEquals("success", deserialized.message)
    }

    @Test
    @DisplayName("VocabVO serialization should handle snake_case fields")
    fun `VocabVO serialization`() {
        val vocab = VocabVO(
            id = 1L,
            userId = 2L,
            word = "微服务",
            pinyin = "wei fu wu",
            category = "术语",
            frequency = 10,
            version = 100L,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-02"
        )
        val json = gson.toJson(vocab)
        assertTrue(json.contains("微服务"))
        assertTrue(json.contains("createdAt"))
        assertTrue(json.contains("updatedAt"))
    }

    @Test
    @DisplayName("VocabAddRequest serialization should work")
    fun `VocabAddRequest serialization`() {
        val request = VocabAddRequest("测试词", "ce shi ci", "通用")
        val json = gson.toJson(request)

        assertTrue(json.contains("测试词"))
        assertTrue(json.contains("ce shi ci"))
        assertTrue(json.contains("通用"))
    }

    @Test
    @DisplayName("VocabListResponse deserialization should work")
    fun `VocabListResponse deserialization`() {
        val json = """
            {
                "items": [
                    {"id":1,"userId":1,"word":"test","pinyin":"test","category":"test","frequency":1,"version":1,"createdAt":null,"updatedAt":null}
                ],
                "total": 1,
                "page": 1,
                "size": 20
            }
        """.trimIndent()

        val response = gson.fromJson(json, VocabListResponse::class.java)
        assertEquals(1, response.items.size)
        assertEquals("test", response.items[0].word)
        assertEquals(1L, response.total)
        assertEquals(1, response.page)
    }

    @Test
    @DisplayName("VocabSyncResponse should have maxVersion field")
    fun `VocabSyncResponse serialization`() {
        val sync = VocabSyncResponse(
            items = emptyList(),
            maxVersion = 999L,
            hasMore = false
        )
        val json = gson.toJson(sync)
        assertTrue(json.contains("maxVersion"))
        assertTrue(json.contains("999"))

        val deserialized = gson.fromJson(json, VocabSyncResponse::class.java)
        assertEquals(999L, deserialized.maxVersion)
        assertTrue(deserialized.items.isEmpty())
    }

    @Test
    @DisplayName("VocabApiService can be constructed with token provider")
    fun `VocabApiService with token provider`() {
        var tokenValue = "test-token"
        val service = VocabApiService(
            baseUrl = "http://localhost:8081",
            tokenProvider = { tokenValue }
        )
        assertNotNull(service)
    }

    @Test
    @DisplayName("VocabApiService with null token provider")
    fun `VocabApiService with null token`() {
        val service = VocabApiService(tokenProvider = { null })
        assertNotNull(service)
    }
}
