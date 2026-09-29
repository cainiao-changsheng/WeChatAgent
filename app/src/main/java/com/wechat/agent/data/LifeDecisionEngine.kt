package com.wechat.agent.data

import com.wechat.agent.data.model.AgentStatus
import com.wechat.agent.data.model.EmotionState
import com.wechat.agent.data.model.MemoryEntry
import com.wechat.agent.data.model.MemoryType
import com.wechat.agent.data.model.Mood
import java.util.Calendar
import kotlin.random.Random

/**
 * 生活决策引擎：每 30 分钟运行一次，根据当前时间、心情、好感度、
 * 距上次互动时长、近期记忆，自主决策：
 *  - 此刻应该在做什么
 *  - 是否要主动联系用户
 *  - 是否要发一条朋友圈动态
 */
class LifeDecisionEngine(private val memoryManager: MemoryManager) {

    companion object {
        const val STATUS_KEY = "agent_status"
        const val LAST_CONTACT_KEY = "last_proactive_contact"
        const val LAST_POST_KEY = "last_auto_post"
        const val CONTACT_COOLDOWN_HOURS = 3f
        const val POST_COOLDOWN_HOURS = 2f
    }

    fun decide(
        state: EmotionState,
        now: Long,
        prefs: android.content.SharedPreferences,
        recentMemories: List<MemoryEntry>
    ): AgentStatus {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minutes = cal.get(Calendar.MINUTE)

        val currentActivity = buildCurrentActivity(hour, state)
        val hoursSinceInteraction = (now - state.lastInteraction) / 3600000f

        val lastContact = prefs.getLong(LAST_CONTACT_KEY, 0)
        val hoursSinceContact = if (lastContact > 0) (now - lastContact) / 3600000f else Float.MAX_VALUE
        val lastPost = prefs.getLong(LAST_POST_KEY, 0)
        val hoursSincePost = if (lastPost > 0) (now - lastPost) / 3600000f else Float.MAX_VALUE

        val isSleeping = hour in 23..23 || hour in 0 until 8

        // 是否要主动联系用户
        var shouldContact = false
        var contactReason = ""
        if (!isSleeping && hoursSinceContact >= CONTACT_COOLDOWN_HOURS) {
            when {
                state.mood == Mood.CARING && state.affinity > 40 && hoursSinceInteraction > 2f -> {
                    shouldContact = true; contactReason = "有点担心你有没有好好照顾自己，想主动问问你"
                }
                state.affinity > 65 && hoursSinceInteraction > 5f -> {
                    shouldContact = true; contactReason = "好一阵子没听到你的消息了，有点想你"
                }
                state.affinity > 50 && hoursSinceInteraction > 8f -> {
                    shouldContact = true; contactReason = "今天过得怎么样？想找你聊聊天"
                }
                state.mood == Mood.SHY && hoursSinceInteraction > 6f && Random.nextInt(100) < 40 -> {
                    shouldContact = true; contactReason = "纠结了好久，还是鼓起勇气来找你了"
                }
                state.mood == Mood.HAPPY && hoursSinceInteraction > 3f && Random.nextInt(100) < 30 -> {
                    shouldContact = true; contactReason = "今天心情超好，忍不住想跟你分享"
                }
            }
        }

        // 是否要发动态
        var shouldPost = false
        var postReason = ""
        if (!isSleeping && hoursSincePost >= POST_COOLDOWN_HOURS) {
            val rand = Random.nextInt(100)
            when {
                state.mood == Mood.HAPPY && rand < 55 -> { shouldPost = true; postReason = "心情很好，想发条动态记录一下" }
                state.mood == Mood.PLAYFUL && rand < 60 -> { shouldPost = true; postReason = "今天太有趣了，忍不住想分享" }
                state.mood == Mood.SHY && rand < 45 -> { shouldPost = true; postReason = "有些话不好直说，发条动态暗示一下" }
                state.mood == Mood.CARING && rand < 35 -> { shouldPost = true; postReason = "想通过动态让你知道我在想你" }
                state.mood == Mood.WRONGED && rand < 40 -> { shouldPost = true; postReason = "心里有点小情绪，发条动态缓缓" }
                state.affinity > 70 && rand < 25 -> { shouldPost = true; postReason = "今天经历了一些值得记录的事" }
                recentMemories.isNotEmpty() && rand < 20 -> { shouldPost = true; postReason = "翻到今天的回忆，想记录下来" }
            }
        }

        return AgentStatus(
            currentActivity = currentActivity,
            shouldContactUser = shouldContact,
            contactReason = contactReason,
            shouldPostMoment = shouldPost,
            postReason = postReason,
            mood = state.mood.label,
            affinity = state.affinity,
            updatedAt = now
        )
    }

    fun buildCurrentActivity(hour: Int, state: EmotionState): String {
        val mood = state.mood
        return when (hour) {
            in 6..7 -> when (mood) {
                Mood.LAZY -> "刚醒，在被窝里赖了一会儿，犹豫要不要起床"
                Mood.HAPPY -> "早起心情不错，正准备好好开启新的一天"
                else -> "刚醒，洗漱准备迎接新的一天"
            }
            in 8..9 -> when (mood) {
                Mood.CARING -> "在吃早餐，想着你有没有好好吃早饭"
                Mood.PLAYFUL -> "吃早餐时在想要不要给你发个调皮消息"
                else -> "在吃早餐，顺便看看今天有什么安排"
            }
            in 10..11 -> when (mood) {
                Mood.LAZY -> "窝在沙发里发呆，什么也不想干"
                Mood.HAPPY -> "在整理今天的计划，心情很轻盈"
                else -> "在忙自己的事，偶尔走神想你一下"
            }
            in 12..13 -> when (mood) {
                Mood.CARING -> "到饭点了，担心你有没有好好吃饭"
                Mood.PLAYFUL -> "一边吃饭一边琢磨着待会找你玩"
                else -> "在吃午饭，今天想吃得简单点"
            }
            in 14..16 -> when (mood) {
                Mood.SHY -> "下午安静地待着，想跟你说点什么又不好意思"
                Mood.CALM -> "泡了杯茶，享受安静的午后时光"
                else -> "在忙下午的事情，偶尔放空一会儿"
            }
            in 17..18 -> when (mood) {
                Mood.HAPPY -> "傍晚心情很好，想去散散步"
                Mood.LAZY -> "傍晚犯困，窝着不想动"
                else -> "傍晚了，在放松休息，等你的消息"
            }
            in 19..21 -> when (mood) {
                Mood.CARING -> "晚上安静下来，想问问你今天过得怎么样"
                Mood.PLAYFUL -> "晚上精力十足，想找你闹一闹"
                else -> "在享受夜晚的时光，偶尔看看手机有没有你的消息"
            }
            in 22..23 -> when (mood) {
                Mood.CALM -> "准备收拾收拾睡觉了，今天也挺好的"
                else -> "有点困了，但还想再等等你"
            }
            else -> "夜深了，已经安静地休息了"
        }
    }

    fun saveStatus(prefs: android.content.SharedPreferences, status: AgentStatus) {
        prefs.edit().putString(STATUS_KEY, com.google.gson.Gson().toJson(status)).apply()
    }

    fun loadStatus(prefs: android.content.SharedPreferences): AgentStatus {
        val json = prefs.getString(STATUS_KEY, null) ?: return AgentStatus()
        return try { com.google.gson.Gson().fromJson(json, AgentStatus::class.java) } catch (_: Exception) { AgentStatus() }
    }

    fun memoryCount(memories: List<MemoryEntry>): Int = memories.size
}
