package com.wechat.agent.data.model

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val role: Role,
    val timestamp: Long = System.currentTimeMillis(),
    var status: MessageStatus = MessageStatus.SENDING,
    val imageUri: String = "",
    val thinking: String = "",
    val audioUri: String = "",
    val audioDurationMs: Int = 0,
    /** Agent 回复默认展示为语音气泡；长按选择「转文字」后置 true，改为展示纯文本。 */
    val voiceToText: Boolean = false
)

enum class Role {
    USER,
    AGENT
}

enum class MessageStatus {
    SENDING,
    SENT,
    ERROR
}
