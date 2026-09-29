package com.wechat.agent.data.model

/**
 * Agent 实时状态：由生活决策引擎每 30 分钟刷新一次。
 * 描述当前时间 Agent 应该在做什么，以及是否主动联系用户 / 是否发动态。
 */
data class AgentStatus(
    val currentActivity: String = "",
    val shouldContactUser: Boolean = false,
    val contactReason: String = "",
    val shouldPostMoment: Boolean = false,
    val postReason: String = "",
    val mood: String = "",
    val affinity: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
