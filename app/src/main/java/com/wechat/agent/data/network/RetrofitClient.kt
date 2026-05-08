package com.wechat.agent.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var baseUrl: String = "https://api.openai.com/"
    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun updateBaseUrl(url: String) {
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        if (normalizedUrl != baseUrl) {
            baseUrl = normalizedUrl
            retrofit = null
            apiService = null
        }
    }

    fun getApiService(): ApiService {
        if (apiService == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit!!.create(ApiService::class.java)
        }
        return apiService!!
    }

    fun getOkHttpClient(): OkHttpClient = okHttpClient
}
