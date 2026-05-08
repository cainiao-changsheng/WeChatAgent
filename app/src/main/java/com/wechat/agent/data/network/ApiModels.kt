package com.wechat.agent.data.network

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
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
    val content: String? = null
)

data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

data class StreamChunk(
    val choices: List<Choice>? = null
)
