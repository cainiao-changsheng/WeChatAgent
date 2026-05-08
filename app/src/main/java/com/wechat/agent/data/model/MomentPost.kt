package com.wechat.agent.data.model

data class MomentPost(
    val id: String,
    val content: String,
    val mood: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val liked: Boolean = false,
    val timeCategory: TimeCategory = TimeCategory.MORNING
)

enum class TimeCategory(val label: String, val emoji: String) {
    MORNING("早安", "☀️"),
    NOON("午间", "🌤"),
    AFTERNOON("午后", "🌿"),
    EVENING("傍晚", "🌅"),
    NIGHT("晚安", "🌙"),
    LATE_NIGHT("深夜", "✨")
}
