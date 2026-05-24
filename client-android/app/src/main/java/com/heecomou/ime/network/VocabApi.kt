package com.heecomou.ime.network

import com.heecomou.ime.model.ApiResponse
import com.heecomou.ime.model.VocabAddRequest
import com.heecomou.ime.model.VocabListResponse
import com.heecomou.ime.model.VocabVO
import retrofit2.http.*

interface VocabApi {

    @POST("api/v1/vocabulary")
    suspend fun add(@Body request: VocabAddRequest): ApiResponse<VocabVO>

    @PUT("api/v1/vocabulary/{id}")
    suspend fun update(@Path("id") id: Long, @Body request: VocabAddRequest): ApiResponse<VocabVO>

    @DELETE("api/v1/vocabulary/{id}")
    suspend fun delete(@Path("id") id: Long): ApiResponse<Void>

    @GET("api/v1/vocabulary")
    suspend fun list(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<VocabListResponse>

    @GET("api/v1/vocabulary/search")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<VocabListResponse>

    @GET("api/v1/vocabulary/{id}")
    suspend fun getById(@Path("id") id: Long): ApiResponse<VocabVO>
}
