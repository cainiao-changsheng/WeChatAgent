package com.wechat.agent.data.network

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    /** Agent 模式：向模型暴露可调用工具（OpenAI 兼容 function calling）。 */
    val tools: List<ChatTool>? = null,
    @SerializedName("tool_choice") val toolChoice: String? = null
)

/** 工具声明（OpenAI 兼容）。 */
data class ChatTool(
    val type: String = "function",
    val function: ToolFunction
)

data class ToolFunction(
    val name: String,
    val description: String? = null,
    /** JSON Schema 格式的参数定义（Map 序列化）。 */
    val parameters: Map<String, Any>? = null
)

/** 模型返回的工具调用（流式时 function.arguments 为增量片段，非流式为完整 JSON 字符串）。 */
data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
)

data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null
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
    val content: String? = null,
    /** Agent 模式：assistant 消息携带的工具调用列表。 */
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
    /** 工具执行结果消息回填时使用：对应 tool_calls 的 id。 */
    @SerializedName("tool_call_id") val toolCallId: String? = null
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
    val reasoning_content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<DeltaToolCall>? = null
)

/** 流式工具调用增量片段（SSE delta.tool_calls 元素，按 index 聚合出完整 ToolCall）。 */
data class DeltaToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
)

data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

data class StreamChunk(
    val choices: List<Choice>? = null
)
