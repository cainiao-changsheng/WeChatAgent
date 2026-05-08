package com.wechat.agent.data.model

import java.util.UUID

data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val messages: List<Message> = emptyList(),
    val lastMessage: String = "",
    val lastTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0
)
