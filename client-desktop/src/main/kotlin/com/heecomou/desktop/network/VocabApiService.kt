package com.heecomou.desktop.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

class VocabApiService(
    private val baseUrl: String = "http://117.72.201.26:8081",
    private val tokenProvider: () -> String? = { null }
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val LOGGER = Logger.getLogger(VocabApiService::class.java.name)
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun list(page: Int = 1, size: Int = 20): ApiResponse<VocabListResponse>? {
        val url = "$baseUrl/api/v1/vocabulary?page=$page&size=$size"
        return executeGet(url, object : TypeToken<ApiResponse<VocabListResponse>>() {})
    }

    fun search(keyword: String, page: Int = 1, size: Int = 20): ApiResponse<VocabListResponse>? {
        val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
        val url = "$baseUrl/api/v1/vocabulary/search?keyword=$encoded&page=$page&size=$size"
        return executeGet(url, object : TypeToken<ApiResponse<VocabListResponse>>() {})
    }

    fun add(request: VocabAddRequest): ApiResponse<VocabVO>? {
        val url = "$baseUrl/api/v1/vocabulary"
        return executePost(url, request, object : TypeToken<ApiResponse<VocabVO>>() {})
    }

    fun update(id: Long, request: VocabAddRequest): ApiResponse<VocabVO>? {
        val url = "$baseUrl/api/v1/vocabulary/$id"
        return executePut(url, request, object : TypeToken<ApiResponse<VocabVO>>() {})
    }

    fun delete(id: Long): ApiResponse<Void>? {
        val url = "$baseUrl/api/v1/vocabulary/$id"
        return executeDelete(url, object : TypeToken<ApiResponse<Void>>() {})
    }

    fun sync(sinceVersion: Long = 0): ApiResponse<VocabSyncResponse>? {
        val url = "$baseUrl/api/v1/vocabulary/sync?since_version=$sinceVersion"
        return executeGet(url, object : TypeToken<ApiResponse<VocabSyncResponse>>() {})
    }

    private inline fun <reified T> executeGet(url: String, typeToken: TypeToken<T>): T? {
        val requestBuilder = Request.Builder().url(url).get()
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return execute(requestBuilder.build(), typeToken)
    }

    private inline fun <reified T> executePost(url: String, body: Any, typeToken: TypeToken<T>): T? {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder().url(url).post(requestBody)
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return execute(requestBuilder.build(), typeToken)
    }

    private inline fun <reified T> executePut(url: String, body: Any, typeToken: TypeToken<T>): T? {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder().url(url).put(requestBody)
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return execute(requestBuilder.build(), typeToken)
    }

    private inline fun <reified T> executeDelete(url: String, typeToken: TypeToken<T>): T? {
        val requestBuilder = Request.Builder().url(url).delete()
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return execute(requestBuilder.build(), typeToken)
    }

    private inline fun <reified T> execute(request: Request, typeToken: TypeToken<T>): T? {
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            response.close()
            gson.fromJson(body, typeToken.type)
        } catch (e: IOException) {
            LOGGER.log(Level.WARNING, "HTTP request failed: ${e.message}", e)
            null
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Request error: ${e.message}", e)
            null
        }
    }
}
