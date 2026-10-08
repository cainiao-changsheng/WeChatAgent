package com.wechat.agent.agent

import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.network.ChatTool
import com.wechat.agent.data.network.ToolFunction

/**
 * Agent 工具注册表：把应用能力暴露为 OpenAI 兼容的 function calling 工具。
 *
 * 只读工具（时间、屏幕使用时间、记忆查询、表情搜索）由模型按需调用；
 * 写操作工具（发朋友圈动态）仅在用户主动开启热恋/Agent 能力时由模型自行决定，
 * 且带本地冷却保护，避免刷屏与滥用。
 */
data class AgentToolSpec(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
    /** 本地执行器：入参为模型解析后的参数 Map，返回给模型的工具结果文本。 */
    val executor: suspend (Map<String, Any?>) -> String
) {
    /** 转为 API 请求中的工具声明。 */
    fun toChatTool(): ChatTool = ChatTool(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = parameters
        )
    )
}

object AgentToolRegistry {

    /** Agent 工具清单（含只读工具与「发朋友圈动态」写工具，写工具带冷却保护）。 */
    fun agentTools(
        memoryManager: MemoryManager,
        screenUsageProvider: suspend () -> String,
        stickerSearchProvider: suspend (String) -> String,
        stickerImageProvider: suspend (String) -> String?,
        photosProvider: suspend () -> String,
        postMomentProvider: suspend (String, String) -> String
    ): List<AgentToolSpec> = listOf(
        AgentToolSpec(
            name = "get_current_time",
            description = "获取当前真实时间（日期、星期、时段）。仅在需要知道具体时间、或做早晚/时段问候时才调用；普通闲聊勿调用。",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "required" to emptyList<String>()
            ),
            executor = { _ ->
                val now = java.util.Calendar.getInstance()
                val weekday = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[now.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
                val period = when (hour) {
                    in 0..5 -> "深夜"
                    in 6..8 -> "清晨"
                    in 9..11 -> "上午"
                    in 12..13 -> "中午"
                    in 14..17 -> "下午"
                    in 18..20 -> "傍晚"
                    else -> "晚上"
                }
                "现在是${now.get(java.util.Calendar.YEAR)}年${now.get(java.util.Calendar.MONTH) + 1}月${now.get(java.util.Calendar.DAY_OF_MONTH)}日 $weekday $period（${hour}点${now.get(java.util.Calendar.MINUTE)}分）。"
            }
        ),
        AgentToolSpec(
            name = "query_screen_time",
            description = "查询对方（用户）今日真实屏幕使用时间：累计时长和今日使用最多的应用。仅在对方聊到手机/屏幕使用相关话题时才调用；普通闲聊勿调用。仅在用户已授予「使用情况访问权限」且有数据时返回，否则返回明确提示。",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "required" to emptyList<String>()
            ),
            executor = { _ ->
                val usage = screenUsageProvider()
                usage.ifBlank { "暂未获取到屏幕使用数据（对方可能未授予使用情况访问权限，或今日暂无有效使用记录）。" }
            }
        ),
        AgentToolSpec(
            name = "recall_memory",
            description = "按关键词精确搜索你与该角色的长期记忆库（过去发生的事、约定、对方偏好等）。仅在对方明确提到过去的事、约定或偏好时才调用；普通闲聊勿调用。keywords 越具体命中越准。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "keywords" to mapOf(
                        "type" to "string",
                        "description" to "要回忆的记忆关键词，如'喜欢吃辣'、'上次旅行'、'生日'。可多个，用空格分隔。"
                    )
                ),
                "required" to emptyList<String>()
            ),
            executor = { args ->
                val keywords = (args["keywords"] as? String)?.trim() ?: ""
                memoryManager.searchMemory(keywords).ifBlank { "暂无相关记忆记录。" }
            }
        ),
        AgentToolSpec(
            name = "search_sticker",
            description = "按语义关键词精确搜索对方自定义的图片表情，返回可用的表情名列表。仅在确实想配一张表情时才调用本工具（不是每条消息都必须用表情），找到后用 [表情名] 或 表情:表情名 的格式输出。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "keywords" to mapOf(
                        "type" to "string",
                        "description" to "描述你想要的表情的语义关键词，如'开心'、'生气'、'点赞'、'晚安'。可多个，用空格分隔。"
                    )
                ),
                "required" to listOf("keywords")
            ),
            executor = { args ->
                val keywords = (args["keywords"] as? String)?.trim() ?: ""
                val result = stickerSearchProvider(keywords)
                if (result.isBlank()) {
                    "未找到匹配的表情，这次回复自然文本即可，不要编造表情名。"
                } else {
                    "找到可用表情：$result"
                }
            }
        ),
        AgentToolSpec(
            name = "browse_photos",
            description = "查看对方最近分享过的相册照片，返回可用的照片路径列表。仅在确实想给动态配一张对方照片时才调用：先从返回结果里选一条路径，再把它填入 post_moment 的 imageUri。",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "required" to emptyList<String>()
            ),
            executor = { _ ->
                val photos = photosProvider()
                if (photos.isBlank()) {
                    "暂时没有可用的相册照片，这次发纯文字动态即可。"
                } else {
                    "可用相册照片（把选中的路径原样填入 post_moment 的 imageUri）：\n$photos"
                }
            }
        ),
        AgentToolSpec(
            name = "post_moment",
            description = "发一条朋友圈动态，把你此刻想分享的生活或心情发布到动态里。仅在对方聊到相关话题、或你确实有想分享的事时才调用，不要频繁发（同一时间最多一条）。content 为动态正文，语气像真人朋友圈，1-3 句话，不要加前缀后缀。想配图时可二选一：stickerName 填收藏表情名，或 imageUri 填从 browse_photos 拿到的相册照片路径。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "content" to mapOf(
                        "type" to "string",
                        "description" to "朋友圈动态正文，1-3 句，语气自然像真人动态。"
                    ),
                    "stickerName" to mapOf(
                        "type" to "string",
                        "description" to "想配的收藏表情名称（可选）。必须是你确定存在的表情名（可用 search_sticker 查询），否则留空。"
                    ),
                    "imageUri" to mapOf(
                        "type" to "string",
                        "description" to "想配的用户相册照片路径（可选）。需先调用 browse_photos 获取可用路径，再从中选一条原样填入。"
                    )
                ),
                "required" to listOf("content")
            ),
            executor = { args ->
                val content = (args["content"] as? String)?.trim() ?: ""
                val stickerName = (args["stickerName"] as? String)?.trim() ?: ""
                val imageUri = (args["imageUri"] as? String)?.trim() ?: ""
                val resolved = if (stickerName.isNotEmpty()) stickerImageProvider(stickerName) else null
                postMomentProvider(content, resolved ?: imageUri)
            }
        )
    )
}
