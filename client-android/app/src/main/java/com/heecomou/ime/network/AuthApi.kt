package com.heecomou.ime.network

import com.heecomou.ime.model.ApiResponse
import com.heecomou.ime.model.LoginRequest
import com.heecomou.ime.model.LoginResponseData
import com.heecomou.ime.model.RegisterRequest
import com.heecomou.ime.model.UserVO
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponseData>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<UserVO>
}
