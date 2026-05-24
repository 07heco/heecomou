package com.heecomou.ime.network

import com.heecomou.ime.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:8081/"

    private val refreshLock = Any()

    val apiService: AuthApi by lazy {
        createRetrofit().create(AuthApi::class.java)
    }

    val userApiService: UserApi by lazy {
        createRetrofit().create(UserApi::class.java)
    }

    val vocabApiService: VocabApi by lazy {
        createRetrofit().create(VocabApi::class.java)
    }

    private fun createRetrofit(): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            TokenManager.accessToken?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        val tokenAuthenticator = Authenticator { _, response ->
            if (response.request.url.encodedPath.endsWith("/api/v1/auth/refresh")) {
                return@Authenticator null
            }

            synchronized(refreshLock) {
                val currentRefreshToken = TokenManager.refreshToken
                if (currentRefreshToken.isNullOrEmpty()) {
                    return@synchronized null
                }

                val refreshResponse = runBlocking {
                    try {
                        val tempClient = OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .build()
                        val tempRetrofit = Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(tempClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                        val tempApi = tempRetrofit.create(AuthApi::class.java)
                        tempApi.refresh(RefreshRequest(currentRefreshToken))
                    } catch (e: Exception) {
                        null
                    }
                }

                if (refreshResponse != null && refreshResponse.code == 200 && refreshResponse.data != null) {
                    val newTokens = refreshResponse.data!!
                    TokenManager.saveTokens(newTokens.accessToken, newTokens.refreshToken)
                    response.request.newBuilder()
                        .removeHeader("Authorization")
                        .addHeader("Authorization", "Bearer ${newTokens.accessToken}")
                        .build()
                } else {
                    TokenManager.clear()
                    null
                }
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
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
