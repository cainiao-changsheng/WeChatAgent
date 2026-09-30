package com.wechat.agent.data.network

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

/** 多模态消息内容块（OpenAI 兼容：text / image_url）。 */
data class VisionContent(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: Map<String, String>? = null
)

data class VisionMessage(
    val role: String,
    val content: List<VisionContent>
)

data class VisionChatRequest(
    val model: String,
    val messages: List<VisionMessage>,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>? = null,
    val error: ApiError? = null
)

data class Choice(
    val message: ChatMessage? = null,
    val delta: Delta? = null,
    val index: Int = 0
)

data class Delta(
    val role: String? = null,
    val content: String? = null,
    val reasoning_content: String? = null
)

data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

data class StreamChunk(
    val choices: List<Choice>? = null
)
