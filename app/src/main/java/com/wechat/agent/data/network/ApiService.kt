package com.wechat.agent.data.network

import com.google.gson.JsonObject
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
        @Body request: JsonObject
    ): Response<ChatResponse>

    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun sendMessageStream(
        @Header("Authorization") authorization: String,
        @Body request: JsonObject
    ): Response<okhttp3.ResponseBody>

    /** 多模态请求：content 支持 text + image_url 混合，用于朋友圈识图回复。 */
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun sendVisionMessage(
        @Header("Authorization") authorization: String,
        @Body request: JsonObject
    ): Response<ChatResponse>

    /** 多模态流式请求：聊天中发送图片后，让支持识图的模型直接看图回复。 */
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun sendVisionMessageStream(
        @Header("Authorization") authorization: String,
        @Body request: JsonObject
    ): Response<okhttp3.ResponseBody>
}