package com.wechat.agent.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {

    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun sendMessageStream(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): Response<okhttp3.ResponseBody>
}
