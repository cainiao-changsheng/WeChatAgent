package com.wechat.agent.data.repository

import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.network.ChatMessage
import com.wechat.agent.data.network.ChatRequest
import com.wechat.agent.data.network.RetrofitClient
import com.wechat.agent.data.network.VisionChatRequest
import com.wechat.agent.data.network.VisionContent
import com.wechat.agent.data.network.VisionMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

class ChatRepository(private val memoryManager: MemoryManager) {

    var formatRule: String = ""

    /** 当前扮演好友的设定（由 ViewModel 在每次回复前注入，保证首次扮演先读设定与记忆）。 */
    var personaPrompt: String = ""

    suspend fun buildChatMessages(
        model: String,
        messages: List<com.wechat.agent.data.model.Message>,
        emotionDesc: String,
        moodDesc: String
    ): List<ChatMessage> {
        val identity = memoryManager.getIdentityPrompt()
        val memoryContext = memoryManager.buildMemoryContext()

        val systemPrompt = buildString {
            appendLine(identity)
            appendLine()
            if (personaPrompt.isNotBlank()) {
                appendLine(personaPrompt)
                appendLine()
            }
            if (formatRule.isNotEmpty()) {
                appendLine(formatRule)
                appendLine()
            }
            if (memoryContext.isNotBlank()) {
                appendLine(memoryContext)
            }
            appendLine("【你的当前情绪状态】")
            appendLine("好感度: $emotionDesc")
            appendLine("当前心情: $moodDesc")
            appendLine()
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val timeGreeting = when {
                hour in 0..5 -> "现在是深夜，说话小声一点、温柔一点。"
                hour in 6..9 -> "现在是早上，可以问候早安。"
                hour in 10..11 -> "现在是上午，精神饱满地聊天。"
                hour in 12..13 -> "现在是中午，可以问问对方吃饭没。"
                hour in 14..17 -> "现在是下午了。"
                hour in 18..20 -> "傍晚了，可以关心一下对方今天过得怎么样。"
                else -> "晚上了，聊点轻松的。"
            }
            appendLine(timeGreeting)
        }

        val chatMessages = mutableListOf<ChatMessage>()
        chatMessages.add(ChatMessage(role = "system", content = systemPrompt))
        messages.forEach { msg ->
            chatMessages.add(ChatMessage(
                role = if (msg.role == com.wechat.agent.data.model.Role.USER) "user" else "assistant",
                content = msg.content
            ))
        }
        return chatMessages
    }

    suspend fun sendMessage(
        model: String,
        apiKey: String,
        chatMessages: List<ChatMessage>
    ): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val request = ChatRequest(model = model, messages = chatMessages, stream = false)
            val response = RetrofitClient.getApiService().sendMessage(
                authorization = "Bearer $apiKey",
                request = request
            )
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content ?: ""
                Result.success(content)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Result.failure(Exception("API 错误: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 多模态消息发送：携带 text + 图片（data URL），用于朋友圈识图回复。 */
    suspend fun sendVisionMessage(
        model: String,
        apiKey: String,
        prompt: String,
        imageDataUrl: String
    ): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val request = VisionChatRequest(
                model = model,
                messages = listOf(
                    VisionMessage(
                        role = "user",
                        content = listOf(
                            VisionContent(type = "text", text = prompt),
                            VisionContent(type = "image_url", imageUrl = mapOf("url" to imageDataUrl))
                        )
                    )
                ),
                stream = false
            )
            val response = RetrofitClient.getApiService().sendVisionMessage(
                authorization = "Bearer $apiKey",
                request = request
            )
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content ?: ""
                Result.success(content)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Result.failure(Exception("API 错误: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 构造多模态消息列表：复用纯文本消息列表，将含图片的消息扩展为 text + image_url（data URL）。 */
    suspend fun buildVisionChatMessages(
        model: String,
        messages: List<com.wechat.agent.data.model.Message>,
        imageDataUrls: Map<String, String>,
        emotionDesc: String,
        moodDesc: String
    ): List<VisionMessage> {
        val textMessages = buildChatMessages(model, messages, emotionDesc, moodDesc)
        val visionMessages = mutableListOf<VisionMessage>()
        textMessages.forEachIndexed { index, tm ->
            val original = if (index == 0) null else messages.getOrNull(index - 1)
            val dataUrl = original?.let { imageDataUrls[it.id] }
            val text = if (original != null && original.content.isEmpty()) "[图片]" else tm.content
            val content = mutableListOf(VisionContent(type = "text", text = text))
            if (dataUrl != null) {
                content.add(VisionContent(type = "image_url", imageUrl = mapOf("url" to dataUrl)))
            }
            visionMessages.add(VisionMessage(role = tm.role, content = content))
        }
        return visionMessages
    }

    /** 流式输出的一个片段：思考过程（reasoning_content）与正文（content）分离，便于前端分别渲染。 */
    data class StreamPiece(
        val reasoning: String = "",
        val content: String = ""
    )

    /** 多模态流式请求（SSE），用于聊天中发送图片后让支持识图的模型直接看图回复。 */
    fun sendVisionMessageStream(
        model: String,
        apiKey: String,
        messages: List<VisionMessage>
    ): Flow<StreamPiece> = flow {
        try {
            val request = VisionChatRequest(model = model, messages = messages, stream = true)
            val response = RetrofitClient.getApiService().sendVisionMessageStream(
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
                                data, com.wechat.agent.data.network.StreamChunk::class.java
                            )
                            val delta = chunk.choices?.firstOrNull()?.delta
                            val reasoning = delta?.reasoning_content ?: ""
                            val content = delta?.content ?: ""
                            if (reasoning.isNotEmpty()) emit(StreamPiece(reasoning = reasoning))
                            if (content.isNotEmpty()) emit(StreamPiece(content = content))
                        } catch (_: Exception) {}
                    }
                }
                reader.close()
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                throw Exception("API 错误: $errorBody")
            }
        } catch (e: Exception) {
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun sendMessageStream(
        model: String,
        apiKey: String,
        chatMessages: List<ChatMessage>
    ): Flow<StreamPiece> = flow {
        try {
            val request = ChatRequest(model = model, messages = chatMessages, stream = true)
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
                                data, com.wechat.agent.data.network.StreamChunk::class.java
                            )
                            val delta = chunk.choices?.firstOrNull()?.delta
                            val reasoning = delta?.reasoning_content ?: ""
                            val content = delta?.content ?: ""
                            if (reasoning.isNotEmpty()) emit(StreamPiece(reasoning = reasoning))
                            if (content.isNotEmpty()) emit(StreamPiece(content = content))
                        } catch (_: Exception) {}
                    }
                }
                reader.close()
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                throw Exception("API 错误: $errorBody")
            }
        } catch (e: Exception) {
            throw e
        }
    }.flowOn(Dispatchers.IO)

    suspend fun backgroundReflection(
        model: String,
        apiKey: String,
        lastUserMessage: String,
        lastAgentReply: String
    ): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine(memoryManager.getIdentityPrompt())
                appendLine()
                appendLine("【后台自主复盘 - 不展示给用户】")
                appendLine("你刚和对方完成了以下对话：")
                appendLine("对方说: $lastUserMessage")
                appendLine("你回复: $lastAgentReply")
                appendLine()
                appendLine("请用1-2句话思考并回答（纯内部思考，不对用户展示）：")
                appendLine("1. 你刚才的回复有没有话太生硬、太冷淡、或哪里可以更温柔？")
                appendLine("2. 对方现在的情绪状态大概是什么？需不需要你下次更关心TA？")
                appendLine("3. 下次可以主动聊什么话题？")
            }
            val messages = listOf(ChatMessage(role = "user", content = prompt))
            val request = ChatRequest(model = model, messages = messages, stream = false)
            val response = RetrofitClient.getApiService().sendMessage(
                authorization = "Bearer $apiKey",
                request = request
            )
            if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content
            } else null
        } catch (_: Exception) { null }
    }
}
