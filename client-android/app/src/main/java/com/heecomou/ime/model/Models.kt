package com.heecomou.ime.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String
)

data class LoginResponseData(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("tokenType") val tokenType: String?,
    @SerializedName("expiresIn") val expiresIn: Long,
    @SerializedName("userId") val userId: Long,
    val username: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class UserVO(
    val id: Long,
    val username: String,
    val email: String?,
    val phone: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("createdAt") val createdAt: String?
)
