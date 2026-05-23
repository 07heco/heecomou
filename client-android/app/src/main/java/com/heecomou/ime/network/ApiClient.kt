package com.heecomou.ime.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:8081/"

    val apiService: AuthApi by lazy {
        createRetrofit().create(AuthApi::class.java)
    }

    val userApiService: UserApi by lazy {
        createRetrofit().create(UserApi::class.java)
    }

    private fun createRetrofit(): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            TokenManager.token?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            val response = chain.proceed(requestBuilder.build())
            if (response.code == 401) {
                TokenManager.clear()
            }
            response
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
