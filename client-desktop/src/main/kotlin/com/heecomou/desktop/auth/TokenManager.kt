package com.heecomou.desktop.auth

import com.google.gson.Gson
import java.io.File

data class StoredToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: Long,
    val username: String
)

class TokenManager {
    private val gson = Gson()
    private val storageFile: File

    init {
        val home = System.getProperty("user.home") ?: System.getProperty("java.io.tmpdir") ?: "."
        val dotDir = File(home, ".heecomou")
        if (dotDir.exists() || dotDir.mkdirs()) {
            storageFile = File(dotDir, "auth.json")
        } else {
            val altDir = File(home, "heecomou_data")
            altDir.mkdirs()
            storageFile = File(altDir, "auth.json")
        }
    }

    fun save(token: StoredToken) {
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        try {
            storageFile.writeText(gson.toJson(token))
        } catch (e: Exception) {
            throw RuntimeException("无法保存登录信息: ${e.message}", e)
        }
    }

    fun load(): StoredToken? {
        if (!storageFile.exists()) return null
        return try {
            gson.fromJson(storageFile.readText(), StoredToken::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        if (storageFile.exists()) {
            storageFile.delete()
        }
    }

    fun getAccessToken(): String? {
        val token = load() ?: return null
        if (System.currentTimeMillis() >= token.expiresAt) {
            return null
        }
        return token.accessToken
    }

    fun getRefreshToken(): String? {
        return load()?.refreshToken
    }
}
