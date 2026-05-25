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
    val storageFile: File
    val writable: Boolean
    val storagePath: String

    init {
        val appData = System.getenv("LOCALAPPDATA")
            ?: System.getenv("APPDATA")
            ?: System.getProperty("java.io.tmpdir")
            ?: System.getProperty("user.dir")
            ?: "."
        val dataDir = File(appData, "HeecoMou")
        val ok = dataDir.exists() || dataDir.mkdirs()
        if (ok) {
            storageFile = File(dataDir, "auth.json")
            writable = true
        } else {
            val tmp = File(System.getProperty("java.io.tmpdir") ?: ".", "heecomou_token.json")
            storageFile = tmp
            writable = tmp.parentFile?.canWrite() ?: false
        }
        storagePath = storageFile.absolutePath
    }

    fun save(token: StoredToken) {
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) {
            val created = parent.mkdirs()
            if (!created) {
                throw RuntimeException("无法创建目录: ${parent.absolutePath}")
            }
        }
        try {
            storageFile.writeText(gson.toJson(token))
        } catch (e: Exception) {
            throw RuntimeException("无法保存登录信息到 ${storageFile.absolutePath}: ${e.message}", e)
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
