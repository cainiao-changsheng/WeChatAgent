package com.wechat.agent.data.repository

import com.wechat.agent.data.network.ChatMessage
import com.wechat.agent.data.network.ChatRequest
import com.wechat.agent.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ChatRepository {

    suspend fun sendMessage(
        model: String,
        apiKey: String,
        messages: List<com.wechat.agent.data.model.Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val chatMessages = messages.map { msg ->
                ChatMessage(
                    role = if (msg.role == com.wechat.agent.data.model.Role.USER) "user" else "assistant",
                    content = msg.content
                )
            }
            val request = ChatRequest(
                model = model,
                messages = chatMessages,
                stream = false
            )
            val response = RetrofitClient.getApiService().sendMessage(
                authorization = "Bearer $apiKey",
                request = request
            )
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content ?: ""
                Result.success(content)
            } else {
                val errorBody = response.errorBody()?.string() ?: "未知错误"
                Result.failure(Exception("API 错误: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendMessageStream(
        model: String,
        apiKey: String,
        messages: List<com.wechat.agent.data.model.Message>
    ): Flow<String> = flow {
        try {
            val chatMessages = messages.map { msg ->
                ChatMessage(
                    role = if (msg.role == com.wechat.agent.data.model.Role.USER) "user" else "assistant",
                    content = msg.content
                )
            }
            val request = ChatRequest(
                model = model,
                messages = chatMessages,
                stream = true
            )

            val response = RetrofitClient.getApiService().sendMessageStream(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val reader = BufferedReader(InputStreamReader(response.body()!!.byteStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val data = currentLine.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val chunk = com.google.gson.Gson().fromJson(
                                data,
                                com.wechat.agent.data.network.StreamChunk::class.java
                            )
                            val content = chunk.choices?.firstOrNull()?.delta?.content ?: ""
                            if (content.isNotEmpty()) {
                                emit(content)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                reader.close()
            } else {
                val errorBody = response.errorBody()?.string() ?: "未知错误"
                throw Exception("API 错误: $errorBody")
            }
        } catch (e: Exception) {
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
