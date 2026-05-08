package com.wechat.agent.data.model

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val role: Role,
    val timestamp: Long = System.currentTimeMillis(),
    var status: MessageStatus = MessageStatus.SENDING
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
