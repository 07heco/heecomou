package com.heecomou.desktop.network

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class VocabVO(
    val id: Long,
    val userId: Long,
    val word: String,
    val pinyin: String? = null,
    val category: String? = null,
    val frequency: Int = 0,
    val version: Long = 0,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class VocabAddRequest(
    val word: String,
    val pinyin: String?,
    val category: String?
)

data class VocabListResponse(
    val items: List<VocabVO>,
    val total: Long,
    val page: Int,
    val size: Int
)

data class VocabSyncResponse(
    val items: List<VocabVO>,
    @SerializedName("max_version") val maxVersion: Long
)
