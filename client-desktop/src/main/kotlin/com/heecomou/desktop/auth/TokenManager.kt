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
    private val storageFile: File by lazy {
        val home = System.getProperty("user.home")
        val dir = File(home, ".heecomou")
        dir.mkdirs()
        File(dir, "auth.json")
    }

    fun save(token: StoredToken) {
        storageFile.writeText(gson.toJson(token))
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
        storageFile.delete()
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
