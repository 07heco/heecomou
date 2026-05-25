package com.heecomou.desktop.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class LocalAsrRequest(
    @SerializedName("audio_b64") val audioB64: String,
    @SerializedName("language") val language: String = "zh"
)

data class LocalAsrResponse(
    @SerializedName("text") val text: String,
    @SerializedName("duration_ms") val durationMs: Float
)

data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("model_loaded") val modelLoaded: Boolean
)

class LocalAsrApiService(private val baseUrl: String) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun healthCheck(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return false
            val health = gson.fromJson(body, HealthResponse::class.java)
            health.status == "UP" && health.modelLoaded
        } catch (_: Exception) {
            false
        }
    }

    fun recognize(audioB64: String, language: String): LocalAsrResponse? {
        return try {
            val req = LocalAsrRequest(audioB64 = audioB64, language = language)
            val jsonBody = gson.toJson(req)
            val request = Request.Builder()
                .url("$baseUrl/api/v1/asr/local/recognize")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (response.isSuccessful) {
                gson.fromJson(body, LocalAsrResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            System.err.println("[LocalAsrApi] recognize failed: ${e.message}")
            null
        }
    }
}
