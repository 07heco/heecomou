package com.heecomou.ime.network

import com.heecomou.ime.model.ApiResponse
import com.heecomou.ime.model.UserVO
import retrofit2.http.GET

interface UserApi {

    @GET("api/v1/user/me")
    suspend fun getMe(): ApiResponse<UserVO>
}
