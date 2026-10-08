package com.wechat.agent.data.model

data class MemoryEntry(
    val id: String,
    val type: MemoryType,
    val content: String,
    val emotion: String = "neutral",
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Int = 1,
    /** 最近一次被召回/访问的时间戳，用于记忆可朽衰减（旧数据缺失时为 0，视为从未访问）。 */
    val lastAccessAt: Long = System.currentTimeMillis()
)

enum class MemoryType {
    L0_INSTANT,
    L1_DAILY,
    L2_GROWTH,
    L3_IDENTITY
}
