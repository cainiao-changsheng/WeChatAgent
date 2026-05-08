package com.wechat.agent.data.model

data class EmotionState(
    val affinity: Int = 50,
    val mood: Mood = Mood.CALM,
    val intimacyThreshold: Int = 30,
    val lastInteraction: Long = System.currentTimeMillis(),
    val todayTopicCount: Int = 0,
    val topicsTalked: List<String> = emptyList()
)

enum class Mood(val label: String) {
    HAPPY("开心"),
    LAZY("慵懒"),
    WRONGED("委屈"),
    TSUNDERE("傲娇"),
    CALM("安静"),
    CARING("关心"),
    PLAYFUL("调皮"),
    SHY("害羞")
}
