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
    val pinyin: String?,
    val category: String?,
    val frequency: Int,
    val version: Long,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
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
