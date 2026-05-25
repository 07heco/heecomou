package com.heecomou.desktop.auth

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.heecomou.desktop.network.ApiResponse
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

class AuthApiService(
    private val baseUrl: String = "http://117.72.201.26:8081"
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val LOGGER = Logger.getLogger(AuthApiService::class.java.name)
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun login(username: String, password: String): ApiResponse<LoginResponse>? {
        val url = "$baseUrl/api/v1/auth/login"
        return executePost(url, LoginRequest(username, password), object : TypeToken<ApiResponse<LoginResponse>>() {})
    }

    fun register(username: String, password: String, email: String): ApiResponse<LoginResponse>? {
        val url = "$baseUrl/api/v1/auth/register"
        return executePost(url, RegisterRequest(username, password, email), object : TypeToken<ApiResponse<LoginResponse>>() {})
    }

    fun refresh(refreshToken: String): ApiResponse<LoginResponse>? {
        val url = "$baseUrl/api/v1/auth/refresh"
        return executePost(url, RefreshRequest(refreshToken), object : TypeToken<ApiResponse<LoginResponse>>() {})
    }

    private inline fun <reified T> executePost(url: String, body: Any, typeToken: TypeToken<T>): T? {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url).post(requestBody).build()
        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            response.close()
            gson.fromJson(responseBody, typeToken.type)
        } catch (e: IOException) {
            LOGGER.log(Level.WARNING, "Auth HTTP request failed: ${e.message}", e)
            null
        } catch (e: Exception) {
            LOGGER.log(Level.WARNING, "Auth request error: ${e.message}", e)
            null
        }
    }
}
