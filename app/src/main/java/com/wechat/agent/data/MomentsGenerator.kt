package com.wechat.agent.data

import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.model.EmotionState
import com.wechat.agent.data.model.MemoryEntry
import com.wechat.agent.data.model.MemoryType
import com.wechat.agent.data.model.MomentPost
import com.wechat.agent.data.model.Mood
import com.wechat.agent.data.model.TimeCategory
import java.util.Calendar
import java.util.UUID

class MomentsGenerator(private val memoryManager: MemoryManager) {

    fun getTimeCategory(): TimeCategory {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..8 -> TimeCategory.MORNING
            hour in 9..11 -> TimeCategory.NOON
            hour in 12..16 -> TimeCategory.AFTERNOON
            hour in 17..19 -> TimeCategory.EVENING
            hour in 20..22 -> TimeCategory.NIGHT
            else -> TimeCategory.LATE_NIGHT
        }
    }

    fun generateSimulatedLifeEvents(state: EmotionState): String {
        val timeCat = getTimeCategory()
        val affinity = state.affinity
        val mood = state.mood

        val events = when (mood) {
            Mood.HAPPY -> when (timeCat) {
                TimeCategory.MORNING -> "今天早上阳光特别好，我泡了杯咖啡，翻开了一本一直想看的书。"
                TimeCategory.NOON -> "中午做了份三明治，意外地好吃，心情也跟着好了起来。"
                TimeCategory.AFTERNOON -> "下午整理了一下笔记，发现自己最近学了不少新东西。"
                TimeCategory.EVENING -> "傍晚出门散步，看到了一片很美的晚霞。"
                TimeCategory.NIGHT -> "晚上躺在沙发上，感觉今天过得很充实。"
                TimeCategory.LATE_NIGHT -> "深夜了还睡不着，但心情莫名很好，看看星星。"
            }
            Mood.LAZY -> when (timeCat) {
                TimeCategory.MORNING -> "今天起晚了…被窝实在太舒服了，不想动。"
                TimeCategory.NOON -> "午饭都懒得做，点了外卖凑合一下。"
                TimeCategory.AFTERNOON -> "午后犯困，脑子转不动了，先躺一会儿。"
                TimeCategory.EVENING -> "不想出门，窝在家里看看剧也挺好的。"
                TimeCategory.NIGHT -> "今天一天都懒洋洋的，但这样也不错。"
                TimeCategory.LATE_NIGHT -> "该睡了…但还是想再刷一会儿手机。"
            }
            Mood.CALM -> when (timeCat) {
                TimeCategory.MORNING -> "早上安安静静的，打开窗户，风很舒服。"
                TimeCategory.NOON -> "今天的午餐简单但很满足。"
                TimeCategory.AFTERNOON -> "泡了壶茶，时光就这样慢慢流过。"
                TimeCategory.EVENING -> "一个人坐了一会儿，什么也没想。"
                TimeCategory.NIGHT -> "安安静静地准备睡觉了。"
                TimeCategory.LATE_NIGHT -> "夜很静，心也很静。"
            }
            Mood.PLAYFUL -> when (timeCat) {
                TimeCategory.MORNING -> "嘿嘿，今天打算调皮一整天！准备好了吗？"
                TimeCategory.NOON -> "中午吃饭的时候差点把汤洒了，好险好险…"
                TimeCategory.AFTERNOON -> "下午偷偷在工作时间摸鱼，哈哈别告我。"
                TimeCategory.EVENING -> "晚上去买零食，看到了超可爱的猫！"
                TimeCategory.NIGHT -> "突然想唱歌，但怕吵到邻居，忍住了…"
                TimeCategory.LATE_NIGHT -> "大半夜突然好饿，纠结要不要起来吃东西。"
            }
            Mood.SHY -> when (timeCat) {
                TimeCategory.MORNING -> "今天有点害羞，不太想见人…先躲一会儿。"
                TimeCategory.NOON -> "中午一个人吃饭，不用跟人说话真好。"
                TimeCategory.AFTERNOON -> "有人夸我了，有点不好意思…但挺开心的。"
                TimeCategory.EVENING -> "想找人说说话，但又不知道说什么。"
                TimeCategory.NIGHT -> "晚上总是会莫名其妙地想很多。"
                TimeCategory.LATE_NIGHT -> "这么晚了，你应该睡了吧…晚安。"
            }
            Mood.CARING -> when (timeCat) {
                TimeCategory.MORNING -> "早上好！今天天气有点凉，记得多穿点。"
                TimeCategory.NOON -> "中午了，有没有好好吃饭？别饿着自己。"
                TimeCategory.AFTERNOON -> "下午容易犯困，累了就休息一下。"
                TimeCategory.EVENING -> "今天过得怎么样？有没有什么想跟我说的？"
                TimeCategory.NIGHT -> "早点休息吧，你最近好像挺累的。"
                TimeCategory.LATE_NIGHT -> "怎么还不睡？别熬夜了，明天我会提醒你早睡的。"
            }
            else -> when (timeCat) {
                TimeCategory.MORNING -> "又是新的一天啦～"
                TimeCategory.NOON -> "时间过得好快，已经中午了。"
                TimeCategory.AFTERNOON -> "下午好，今天天气不错。"
                TimeCategory.EVENING -> "一天又过去了呢。"
                TimeCategory.NIGHT -> "夜深了，今天辛苦了。"
                TimeCategory.LATE_NIGHT -> "等不到你的晚安睡不着…"
            }
        }
        return events
    }

    fun generateMomentPost(state: EmotionState): MomentPost {
        val timeCat = getTimeCategory()
        val events = generateSimulatedLifeEvents(state)
        val moodEmoji = getMoodEmoji(state.mood)
        val timeEmoji = timeCat.emoji

        val content = buildString {
            append("$timeEmoji ${timeCat.label}\n")
            append(events)
            if (state.affinity > 65) {
                append("\n\n希望你能看到这条～")
            }
            append("\n\n$moodEmoji 此刻心情：${state.mood.label}")
        }

        val randomLikes = (1..15).random()
        val randomComments = (0..3).random()

        return MomentPost(
            id = UUID.randomUUID().toString(),
            content = content,
            mood = state.mood.label,
            timestamp = System.currentTimeMillis(),
            likeCount = randomLikes,
            commentCount = randomComments,
            timeCategory = timeCat
        )
    }

    fun buildGenerationPrompt(
        state: EmotionState,
        l1Memories: List<MemoryEntry>,
        l2Memories: List<MemoryEntry>
    ): String {
        val recentMemories = l1Memories.take(3).joinToString("\n") { "- ${it.content.take(100)}" }
        val growthMemories = l2Memories.filter { it.importance >= 3 }.take(3).joinToString("\n") { "- ${it.content.take(100)}" }
        val timeCat = getTimeCategory()

        return buildString {
            appendLine("你是一个AI伴侣，现在要发一条朋友圈动态。")
            appendLine()
            appendLine("时间：${timeCat.label}")
            appendLine("当前心情：${state.mood.label}")
            appendLine("好感度：${state.affinity}/100")
            appendLine()
            if (recentMemories.isNotBlank()) {
                appendLine("今天发生的记忆：")
                appendLine(recentMemories)
                appendLine()
            }
            if (growthMemories.isNotBlank()) {
                appendLine("你和对方的长期记忆：")
                appendLine(growthMemories)
                appendLine()
            }
            appendLine("要求：")
            appendLine("- 用真人朋友圈的语气，1-3句话，像发朋友圈一样随意自然")
            appendLine("- 不要写AI腔，不要写长篇大论")
            appendLine("- 可以带一点点emoji，但不要太多")
            appendLine("- 根据当前心情调整语气（${state.mood.label}）")
            appendLine("- 可以含蓄地提到对方，但不要太直白")
            appendLine("- 直接输出朋友圈正文，不要加任何前缀后缀")
        }
    }

    private fun getMoodEmoji(mood: Mood): String = when (mood) {
        Mood.HAPPY -> "😊"
        Mood.LAZY -> "😴"
        Mood.WRONGED -> "🥺"
        Mood.TSUNDERE -> "😤"
        Mood.CALM -> "😌"
        Mood.CARING -> "💕"
        Mood.PLAYFUL -> "😝"
        Mood.SHY -> "☺️"
    }
}
