package com.wechat.agent.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.wechat.agent.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var baseUrl: String = "https://api.deepseek.com/"
    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // 永不记录请求/响应 BODY，避免聊天内容、图片或其他敏感数据进入日志。
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    fun updateBaseUrl(url: String) {
        val trimmed = url.trim()
        val parsed = trimmed.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("API 地址无效")
        require(parsed.scheme == "https" || parsed.scheme == "http") { "API 地址必须使用 HTTP 或 HTTPS" }
        require(parsed.userInfo == null) { "API 地址不能包含用户名或密码" }
        require(parsed.query == null && parsed.fragment == null) { "API 地址不能包含查询参数或片段" }
        if (parsed.scheme == "http") {
            require(BuildConfig.DEBUG && isLocalHost(parsed.host)) {
                "生产环境只允许 HTTPS；HTTP 仅支持调试时的本机地址"
            }
        }
        val normalizedUrl = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        if (normalizedUrl != baseUrl) {
            baseUrl = normalizedUrl
            retrofit = null
            apiService = null
        }
    }

    private fun isLocalHost(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1"

    @Synchronized
    fun getApiService(): ApiService {
        apiService?.let { return it }
        val built = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit = built
        return built.create(ApiService::class.java).also { apiService = it }
    }
}
