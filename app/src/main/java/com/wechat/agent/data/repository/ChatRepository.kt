package com.wechat.agent.data.repository

import com.wechat.agent.data.AppLogger
import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.agent.AgentToolSpec
import com.wechat.agent.data.network.ChatMessage
import com.wechat.agent.data.network.ChatRequest
import com.wechat.agent.data.network.DeltaToolCall
import com.wechat.agent.data.network.FunctionCall
import com.wechat.agent.data.network.RetrofitClient
import com.wechat.agent.data.network.StreamChunk
import com.wechat.agent.data.network.ToolCall
import com.wechat.agent.data.network.VisionChatRequest
import com.wechat.agent.data.network.VisionContent
import com.wechat.agent.data.network.VisionMessage
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader

class ChatRepository(
    private val memoryManager: MemoryManager,
    private val settingsManager: SettingsManager
) {

    private val gson = Gson()

    var formatRule: String = ""

    /** 当前扮演好友的设定（由 ViewModel 在每次回复前注入，保证首次扮演先读设定与记忆）。 */
    var personaPrompt: String = ""

    /** 灵魂文件 soul.md 内容（由 ViewModel 每轮实时读取注入，Agent 模式核心人设，优先级最高）。 */
    var soulPrompt: String = ""

    /** 记录不支持 function calling 的模型名，避免每次请求重复触发 400。 */
    private val toolsUnsupportedModels = java.util.Collections.synchronizedSet(java.util.HashSet<String>())
    /** Agent 执行最大轮数（含工具调用轮），防止死循环。 */
    companion object {
        const val MAX_AGENT_ROUNDS = 6
        /** 单轮请求（含读流）超时护栏：防止工具循环单轮无响应卡死 UI。 */
        const val ROUND_TIMEOUT_MS = 45_000L
    }

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
            if (soulPrompt.isNotBlank()) {
                appendLine()
                appendLine("【灵魂设定 soul.md - 最高优先级人设】")
                appendLine(soulPrompt)
            }
            appendLine()
            appendLine("【回复原则 - 快速直接】")
            appendLine("1. 看到对方消息后直接给出自然回复，不要长篇分析、不要内心独白、不要反复推敲。")
            appendLine("2. 除非确实需要（查时间、查记忆、找表情）才调用工具；普通闲聊一律直接回答，禁止每条消息都调用工具。")
            appendLine("3. 回复简短自然，像微信聊天，一两句说清即可，不要绕圈子。")
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

    /** 将请求数据类序列化为 JsonObject，并把「自定义请求参数」浅合并到顶层（用户参数覆盖默认字段）。 */
    private fun buildRequestBody(any: Any): JsonObject {
        val obj = gson.toJsonTree(any).asJsonObject
        val extraText = settingsManager.getAdvancedSettingsSync().customParams.trim()
        if (extraText.isNotEmpty()) {
            try {
                val extra = gson.fromJson(extraText, JsonObject::class.java)
                extra?.entrySet()?.forEach { (key, value) -> obj.add(key, value) }
            } catch (e: Exception) {
                AppLogger.log("ChatRepository", "自定义请求参数解析失败，已忽略: ${e.message}")
            }
        }
        return obj
    }

    suspend fun sendMessage(
        model: String,
        apiKey: String,
        chatMessages: List<ChatMessage>
    ): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val request = buildRequestBody(ChatRequest(model = model, messages = chatMessages, stream = false))
            val response = RetrofitClient.getApiService().sendMessage(
                authorization = "Bearer $apiKey",
                request = request
            )
            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.choices?.firstOrNull()?.message?.content ?: ""
                Result.success(content)
            } else {
                val message = apiErrorMessage(response.code())
                AppLogger.log("ChatRepository", "非成功 HTTP 响应: ${response.code()}")
                Result.failure(Exception(message))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.log("ChatRepository", "请求异常: ${e.javaClass.simpleName}")
            Result.failure(Exception(networkErrorMessage(e), e))
        }
    }

    /**
     * Agent 模式调用（阶段 1，非流式）：把只读工具暴露给模型，循环执行「请求 → 工具调用 → 本地执行 → 回填 → 再请求」，
     * 直到模型给出纯文本回复或达到最大轮数。模型不支持 tools 参数时自动降级为普通文本请求。
     *
     * @param tools 工具规格清单（含执行器）
     * @return 模型最终文本回复
     */
    suspend fun sendAgentMessage(
        model: String,
        apiKey: String,
        chatMessages: List<ChatMessage>,
        tools: List<AgentToolSpec>
    ): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            if (tools.isEmpty()) {
                return@withContext sendMessage(model, apiKey, chatMessages)
            }
            val messages = chatMessages.toMutableList()
            val supportsTools = !toolsUnsupportedModels.contains(model)
            var lastText = ""
            var lastError: Exception? = null

            for (round in 0 until MAX_AGENT_ROUNDS) {
                val request = buildRequestBody(
                    ChatRequest(
                        model = model,
                        messages = messages,
                        stream = false,
                        tools = if (supportsTools) tools.map { it.toChatTool() } else null
                    )
                )
                val response = RetrofitClient.getApiService().sendMessage(
                    authorization = "Bearer $apiKey",
                    request = request
                )
                if (!response.isSuccessful) {
                    // 若带 tools 请求被拒且该模型此前未被标记，则标记不支持并降级重试一次纯文本
                    if (supportsTools && response.code() == 400) {
                        toolsUnsupportedModels.add(model)
                        AppLogger.log("ChatRepository", "模型 $model 不支持 tools，降级纯文本模式")
                        val fallback = buildRequestBody(ChatRequest(model = model, messages = messages, stream = false))
                        val fbResponse = RetrofitClient.getApiService().sendMessage(
                            authorization = "Bearer $apiKey",
                            request = fallback
                        )
                        if (fbResponse.isSuccessful) {
                            val content = fbResponse.body()?.choices?.firstOrNull()?.message?.content ?: ""
                            if (content.isNotBlank()) {
                                return@withContext Result.success(content)
                            }
                        }
                    }
                    val message = apiErrorMessage(response.code())
                    lastError = Exception(message)
                    return@withContext Result.failure(lastError!!)
                }

                val choice = response.body()?.choices?.firstOrNull() ?: break
                val message = choice.message ?: break
                lastText = message.content ?: ""
                val toolCalls = message.toolCalls
                if (toolCalls.isNullOrEmpty()) {
                    // 模型已给出最终文本回复
                    return@withContext Result.success(lastText)
                }

                // 回填 assistant 消息（保留 tool_calls，供模型看到自己调用了什么）
                messages.add(ChatMessage(role = "assistant", content = message.content, toolCalls = toolCalls))

                // 逐个执行工具并回填 role=tool 结果
                for (toolCall in toolCalls) {
                    val name = toolCall.function?.name ?: continue
                    val argsJson = toolCall.function?.arguments ?: "{}"
                    val args: Map<String, Any?> = try {
                        com.google.gson.Gson().fromJson(argsJson, Map::class.java) as? Map<String, Any?>
                            ?: emptyMap()
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    val spec = tools.find { it.name == name }
                    val result = if (spec != null) {
                        try {
                            spec.executor(args)
                        } catch (e: Exception) {
                            "工具执行失败: ${e.message ?: e.javaClass.simpleName}"
                        }
                    } else {
                        "未找到工具 $name"
                    }
                    messages.add(ChatMessage(
                        role = "tool",
                        content = result,
                        toolCallId = toolCall.id
                    ))
                }
                if (round == MAX_AGENT_ROUNDS - 1) {
                    AppLogger.log("ChatRepository", "Agent 达到最大轮数 $MAX_AGENT_ROUNDS，以最后文本收尾")
                }
            }

            // 达到最大轮数仍未得到文本：返回最后一段文本，无则报错
            if (lastText.isNotBlank()) Result.success(lastText)
            else Result.failure(lastError ?: Exception("AI 未返回有效回复"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.log("ChatRepository", "Agent 请求异常: ${e.javaClass.simpleName}")
            Result.failure(Exception(networkErrorMessage(e), e))
        }
    }

    /** Agent 流式片段：思考（reasoning）、正文（content）、工具执行状态（toolStatus）。 */
    data class AgentStreamPiece(
        val reasoning: String = "",
        val content: String = "",
        val toolStatus: String = ""
    )

    /** Agent 工具执行状态文案。 */
    private fun agentToolLabel(name: String): String = when (name) {
        "get_current_time" -> "正在查看时间…"
        "query_screen_time" -> "正在查看屏幕使用时间…"
        "recall_memory" -> "正在回忆与你的记忆…"
        else -> "正在执行 $name…"
    }

    /**
     * Agent 模式流式发送（SSE）：实时输出思考/正文，模型发起工具调用时自动执行并进入下一轮；
     * 每轮有 ROUND_TIMEOUT_MS（45s）护栏，杜绝工具循环把 UI 锁死。
     */
    fun sendAgentMessageStream(
        model: String,
        apiKey: String,
        chatMessages: List<ChatMessage>,
        tools: List<AgentToolSpec>
    ): Flow<AgentStreamPiece> = flow {
        if (tools.isEmpty()) {
            sendMessageStream(model, apiKey, chatMessages).collect { piece ->
                if (piece.reasoning.isNotEmpty()) emit(AgentStreamPiece(reasoning = piece.reasoning))
                if (piece.content.isNotEmpty()) emit(AgentStreamPiece(content = piece.content))
            }
            return@flow
        }
        val messages = chatMessages.toMutableList()
        val supportsTools = !toolsUnsupportedModels.contains(model)
        val gson = com.google.gson.Gson()
        var lastText = ""
        try {
            for (round in 0 until MAX_AGENT_ROUNDS) {
                var textThisRound = ""
                val toolCalls = try {
                    withTimeout(ROUND_TIMEOUT_MS) {
                        val request = buildRequestBody(
                            ChatRequest(
                                model = model,
                                messages = messages,
                                stream = true,
                                tools = if (supportsTools) tools.map { it.toChatTool() } else null
                            )
                        )
                        val response = RetrofitClient.getApiService().sendMessageStream(
                            authorization = "Bearer $apiKey",
                            request = request
                        )
                        if (!response.isSuccessful) {
                            // 带 tools 被拒且此前未标记：标记并降级纯文本流式
                            if (supportsTools && response.code() == 400) {
                                toolsUnsupportedModels.add(model)
                                AppLogger.log("ChatRepository", "模型 $model 不支持 tools，降级纯文本流式")
                                sendMessageStream(model, apiKey, messages).collect { piece ->
                                    if (piece.reasoning.isNotEmpty()) emit(AgentStreamPiece(reasoning = piece.reasoning))
                                    if (piece.content.isNotEmpty()) emit(AgentStreamPiece(content = piece.content))
                                }
                                return@withTimeout emptyList<ToolCall>()
                            }
                            throw ApiRequestException(response.code(), apiErrorMessage(response.code()))
                        }

                        val responseBody = response.body()
                            ?: throw Exception("服务器未返回流式响应")
                        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                        val toolAccum = LinkedHashMap<Int, DeltaToolCall>()
                        val currentContent = StringBuilder()
                        val currentReasoning = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val currentLine = line ?: continue
                            if (!currentLine.startsWith("data: ")) continue
                            val data = currentLine.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            try {
                                val chunk = gson.fromJson(data, StreamChunk::class.java)
                                val delta = chunk.choices?.firstOrNull()?.delta ?: continue
                                delta.reasoning_content?.let { r ->
                                    if (r.isNotEmpty()) {
                                        currentReasoning.append(r)
                                        emit(AgentStreamPiece(reasoning = r))
                                    }
                                }
                                delta.content?.let { c ->
                                    if (c.isNotEmpty()) {
                                        currentContent.append(c)
                                        emit(AgentStreamPiece(content = c))
                                    }
                                }
                                delta.toolCalls?.forEach { dtc ->
                                    val prev = toolAccum[dtc.index] ?: DeltaToolCall(index = dtc.index)
                                    toolAccum[dtc.index] = prev.copy(
                                        id = dtc.id ?: prev.id,
                                        type = dtc.type ?: prev.type,
                                        function = FunctionCall(
                                            name = dtc.function?.name ?: prev.function?.name,
                                            arguments = (prev.function?.arguments ?: "") + (dtc.function?.arguments ?: "")
                                        )
                                    )
                                }
                            } catch (_: Exception) {}
                        }
                        reader.close()

                        textThisRound = currentContent.toString()
                        if (textThisRound.isNotBlank()) lastText = textThisRound
                        toolAccum.values
                            .filter { it.function?.name?.isNotBlank() == true }
                            .map { ToolCall(id = it.id, type = it.type, function = it.function) }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    AppLogger.log("ChatRepository", "Agent 单轮响应超时（45s 护栏触发）")
                    if (lastText.isBlank()) throw Exception("AI 响应超时，请稍后再试")
                    return@flow
                }

                if (toolCalls.isEmpty()) {
                    // 模型已给出最终文本（正文已实时 emit）
                    return@flow
                }

                // 回填 assistant 消息（保留 tool_calls，供模型看到自己调用了什么）
                messages.add(ChatMessage(
                    role = "assistant",
                    content = textThisRound.ifEmpty { null },
                    toolCalls = toolCalls
                ))

                // 逐个执行工具并回填 role=tool 结果
                for (toolCall in toolCalls) {
                    val name = toolCall.function?.name ?: continue
                    val argsJson = toolCall.function?.arguments ?: "{}"
                    emit(AgentStreamPiece(toolStatus = agentToolLabel(name)))
                    val args: Map<String, Any?> = try {
                        gson.fromJson(argsJson, Map::class.java) as? Map<String, Any?>
                            ?: emptyMap()
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    val spec = tools.find { it.name == name }
                    val result = if (spec != null) {
                        try {
                            spec.executor(args)
                        } catch (e: Exception) {
                            "工具执行失败: ${e.message ?: e.javaClass.simpleName}"
                        }
                    } else {
                        "未找到工具 $name"
                    }
                    messages.add(ChatMessage(
                        role = "tool",
                        content = result,
                        toolCallId = toolCall.id
                    ))
                }
                if (round == MAX_AGENT_ROUNDS - 1) {
                    AppLogger.log("ChatRepository", "Agent 达到最大轮数 $MAX_AGENT_ROUNDS，以最后文本收尾")
                }
            }

            // 达到最大轮数仍未完成（正文每轮已实时输出，无需重复 emit）
            if (lastText.isBlank()) throw Exception("AI 未返回有效回复")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.log("ChatRepository", "Agent 流式请求异常: ${e.javaClass.simpleName}")
            throw Exception(networkErrorMessage(e), e)
        }
    }.flowOn(Dispatchers.IO)

    /** 多模态消息发送：携带 text + 图片（data URL），用于朋友圈识图回复。 */
    suspend fun sendVisionMessage(
        model: String,
        apiKey: String,
        prompt: String,
        imageDataUrl: String
    ): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val request = buildRequestBody(
                VisionChatRequest(
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
                val message = apiErrorMessage(response.code())
                AppLogger.log("ChatRepository", "非成功 HTTP 响应: ${response.code()}")
                Result.failure(Exception(message))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.log("ChatRepository", "请求异常: ${e.javaClass.simpleName}")
            Result.failure(Exception(networkErrorMessage(e), e))
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
            val request = buildRequestBody(VisionChatRequest(model = model, messages = messages, stream = true))
            val response = RetrofitClient.getApiService().sendVisionMessageStream(
                authorization = "Bearer $apiKey",
                request = request
            )
            if (response.isSuccessful) {
                val responseBody = response.body()
                    ?: throw Exception("服务器未返回流式响应")
                val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
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
                AppLogger.log("ChatRepository", "流式请求 HTTP ${response.code()}")
                throw ApiRequestException(response.code(), apiErrorMessage(response.code()))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.startsWith("请求失败") != true) {
                AppLogger.log("ChatRepository", "流式请求异常: ${e.javaClass.simpleName}")
            }
            throw Exception(networkErrorMessage(e), e)
        }
    }.flowOn(Dispatchers.IO)

    fun sendMessageStream(
        model: String,
        apiKey: String,
        chatMessages: List<ChatMessage>
    ): Flow<StreamPiece> = flow {
        try {
            val request = buildRequestBody(ChatRequest(model = model, messages = chatMessages, stream = true))
            val response = RetrofitClient.getApiService().sendMessageStream(
                authorization = "Bearer $apiKey",
                request = request
            )
            if (response.isSuccessful) {
                val responseBody = response.body()
                    ?: throw Exception("服务器未返回流式响应")
                val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
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
                AppLogger.log("ChatRepository", "流式请求 HTTP ${response.code()}")
                throw ApiRequestException(response.code(), apiErrorMessage(response.code()))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.message?.startsWith("请求失败") != true) {
                AppLogger.log("ChatRepository", "流式请求异常: ${e.javaClass.simpleName}")
            }
            throw Exception(networkErrorMessage(e), e)
        }
    }.flowOn(Dispatchers.IO)

    private class ApiRequestException(
        val statusCode: Int,
        override val message: String
    ) : Exception(message)

    private fun apiErrorMessage(code: Int): String = when (code) {
        400 -> "请求参数有误，请检查模型配置。"
        401 -> "API Key 无效或已失效，请检查模型配置。"
        403 -> "API 请求被拒绝，请检查账号权限或接口地址。"
        404 -> "接口或模型不存在，请检查 API 地址和模型名称。"
        408 -> "请求超时，请稍后再试。"
        429 -> "请求过于频繁或额度不足，请稍后再试。"
        in 500..599 -> "AI 服务暂时不可用，请稍后再试。"
        else -> "请求失败（HTTP $code），请检查网络和模型配置。"
    }

    private fun networkErrorMessage(error: Throwable): String = when (error) {
        is ApiRequestException -> error.message
        is java.net.UnknownHostException -> "无法连接 AI 服务，请检查网络或 API 地址。"
        is java.net.ConnectException -> "无法连接 AI 服务，请检查网络或 API 地址。"
        is java.net.SocketTimeoutException -> "连接 AI 服务超时，请稍后再试。"
        else -> error.message?.takeIf { it.startsWith("请求失败") || it.startsWith("API Key") || it.startsWith("接口") }
            ?: "网络请求失败，请稍后再试。"
    }

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
            val request = buildRequestBody(ChatRequest(model = model, messages = messages, stream = false))
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
