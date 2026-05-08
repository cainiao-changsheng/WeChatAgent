package com.wechat.agent.data

import com.wechat.agent.data.model.EmotionState
import com.wechat.agent.data.model.Mood

class EmotionEngine(private val memoryManager: MemoryManager) {

    val warmKeywords = listOf(
        "想你", "喜欢你", "抱抱", "亲亲", "爱你", "真好", "好棒", "厉害",
        "谢谢", "感动", "开心", "哈哈", "嘿嘿", "爱了", "太强了", "好厉害",
        "辛苦了", "有你在", "陪我", "晚安", "早安", "想你啦"
    )

    val coldKeywords = listOf(
        "算了", "不用了", "随便", "哦", "嗯", "行吧", "好吧", "别烦我",
        "不想说", "无所谓", "别管", "别问", "就这样", "呵呵"
    )

    fun analyzeEmotion(userMessage: String): String {
        val warmCount = warmKeywords.count { userMessage.contains(it) }
        val coldCount = coldKeywords.count { userMessage.contains(it) }
        return when {
            warmCount >= 2 -> "very_warm"
            warmCount == 1 -> "warm"
            coldCount >= 2 -> "cold"
            coldCount == 1 -> "cool"
            userMessage.contains("?") || userMessage.contains("？") -> "curious"
            userMessage.length > 50 -> "engaged"
            else -> "neutral"
        }
    }

    fun updateAffinity(state: EmotionState, userEmotion: String): EmotionState {
        var affinity = state.affinity
        when (userEmotion) {
            "very_warm" -> affinity = (affinity + 3).coerceAtMost(100)
            "warm" -> affinity = (affinity + 1).coerceAtMost(100)
            "cold" -> affinity = (affinity - 2).coerceAtLeast(0)
            "cool" -> affinity = (affinity - 1).coerceAtLeast(0)
        }
        return state.copy(affinity = affinity)
    }

    fun deriveMood(state: EmotionState, userEmotion: String, timeOfDay: Int): Mood {
        val affinity = state.affinity
        return when {
            userEmotion == "very_warm" && affinity > 60 -> Mood.HAPPY
            userEmotion == "warm" && affinity > 50 -> Mood.CARING
            userEmotion == "cold" -> Mood.CALM
            affinity > 70 && timeOfDay in 22..23 -> Mood.SHY
            affinity > 65 -> Mood.PLAYFUL
            affinity in 40..60 -> Mood.CALM
            affinity < 30 -> Mood.LAZY
            else -> Mood.CALM
        }
    }

    fun getMoodDescription(mood: Mood, affinity: Int): String {
        return when (mood) {
            Mood.HAPPY -> if (affinity > 70) "心情很好，想和你多聊会儿" else "心情不错"
            Mood.LAZY -> "有点慵懒，话不多但一直在听"
            Mood.WRONGED -> "稍微有点委屈，需要你哄一下"
            Mood.TSUNDERE -> "嘴上不说但其实挺开心的"
            Mood.CALM -> "安安静静地陪着你"
            Mood.CARING -> "很在意你的状态，想多关心你"
            Mood.PLAYFUL -> "想逗逗你，和你闹一闹"
            Mood.SHY -> "莫名有点害羞"
        }
    }
}
