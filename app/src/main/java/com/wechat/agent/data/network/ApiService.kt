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

    /** 多模态请求：content 支持 text + image_url 混合，用于朋友圈识图回复。 */
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun sendVisionMessage(
        @Header("Authorization") authorization: String,
        @Body request: VisionChatRequest
    ): Response<ChatResponse>
}
