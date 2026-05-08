package com.wechat.agent.data.model

data class MemoryEntry(
    val id: String,
    val type: MemoryType,
    val content: String,
    val emotion: String = "neutral",
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Int = 1
)

enum class MemoryType {
    L0_INSTANT,
    L1_DAILY,
    L2_GROWTH,
    L3_IDENTITY
}
