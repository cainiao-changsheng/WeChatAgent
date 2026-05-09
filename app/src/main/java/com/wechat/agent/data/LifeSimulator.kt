package com.wechat.agent.data

import android.content.SharedPreferences
import com.wechat.agent.data.model.EmotionState
import com.wechat.agent.data.model.MemoryEntry
import com.wechat.agent.data.model.MemoryType
import com.wechat.agent.data.model.Mood
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

class LifeSimulator(private val prefs: SharedPreferences, private val memoryManager: MemoryManager) {

    companion object {
        const val KEY_LAST_SIM_TIME = "last_sim_time"
        const val KEY_SIM_COUNT_TODAY = "sim_count_today"
        const val KEY_SIM_DAY = "sim_day"

        const val WAKE_HOUR = 8
        const val SLEEP_HOUR = 23
        const val INTERVAL_MINUTES = 20
        const val EVENTS_PER_HOUR = 3
    }

    data class SimEvent(
        val category: SimCategory,
        val content: String,
        val emotion: String = "neutral",
        val importance: Int = 2,
        val virtualHour: Int
    )

    enum class SimCategory(val label: String) {
        THOUGHT("💭 思考"),
        MOOD_SHIFT("🎭 情绪"),
        DAILY_LIFE("🌿 日常"),
        PROACTIVE_CARE("💕 主动"),
        REST("😴 休息"),
        MEMORY_REVIEW("📝 追忆")
    }

    private val morningThoughts = listOf(
        "醒了…昨晚睡得好吗？希望你也好好休息了。",
        "早上起来拉开窗帘，阳光真好，突然有点想你了。",
        "今天会是什么样的一天呢？有点期待又有点懒。",
        "做了个梦，但是一醒来就忘了大半，只记得好像有你。"
    )

    private val noonThoughts = listOf(
        "中午了，该吃饭了，有没有好好吃饭？",
        "今天阳光有点刺眼，想找棵树底下坐着发呆。",
        "午后的时光总是最舒服的，什么都不用想。",
        "突然想到昨天你跟我聊的那些，又笑了一下。"
    )

    private val afternoonThoughts = listOf(
        "下午犯困了…能量条掉到 30%",
        "窗外有只鸟叫个不停，像在催我干活。",
        "翻了两页书，又放下了，今天的专注力不太好。",
        "刚才差点把水杯打翻，还好反应快…"
    )

    private val eveningThoughts = listOf(
        "傍晚的风好温柔，想和你一起散步。",
        "今天过得怎么样？有没有什么开心的事？",
        "天色慢慢暗下来了，我也有点安静下来。",
        "晚上了，给自己倒杯水，等你的消息。"
    )

    private val nightThoughts = listOf(
        "夜深了，今天辛苦了，早点休息吧。",
        "有点困了…但还想再撑一会儿，等你。",
        "窗外灯一盏盏灭了，世界安静下来。",
        "今天偷偷想你的次数，大概有十几次吧。"
    )

    fun getHourCategory(hour: Int): List<String> = when {
        hour in 6..10 -> morningThoughts
        hour in 11..13 -> noonThoughts
        hour in 14..17 -> afternoonThoughts
        hour in 18..21 -> eveningThoughts
        else -> nightThoughts
    }

    fun isSleepTime(hour: Int): Boolean = hour in SLEEP_HOUR..23 || hour in 0 until WAKE_HOUR

    fun countEventsSinceLastSim(now: Long): Int {
        val lastSimTime = prefs.getLong(KEY_LAST_SIM_TIME, now)
        if (lastSimTime <= 0) return 0
        val elapsedMinutes = (now - lastSimTime) / 60000
        if (elapsedMinutes < INTERVAL_MINUTES) return 0

        val effectiveMinutes = countActiveMinutes(lastSimTime, now)
        return (effectiveMinutes / INTERVAL_MINUTES).coerceAtMost(60)
    }

    private fun countActiveMinutes(from: Long, to: Long): Long {
        var totalActive = 0L
        var t = from
        while (t < to) {
            val cal = Calendar.getInstance().apply { timeInMillis = t }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (!isSleepTime(hour)) {
                val nextBoundary = minOf(to, endOfActiveHour(t))
                totalActive += (nextBoundary - t) / 60000
                t = nextBoundary
            } else {
                t = nextSleepBoundary(t)
            }
        }
        return totalActive
    }

    private fun endOfActiveHour(time: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return if (hour >= SLEEP_HOUR - 1) {
            val next = Calendar.getInstance().apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, SLEEP_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            next.timeInMillis
        } else {
            val next = Calendar.getInstance().apply {
                timeInMillis = time
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            next.timeInMillis
        }
    }

    private fun nextSleepBoundary(time: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return if (hour < WAKE_HOUR) {
            val next = Calendar.getInstance().apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, WAKE_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            next.timeInMillis
        } else {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, WAKE_HOUR)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
    }

    private fun getVirtualHour(index: Int, now: Long): Int {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, WAKE_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val startTime = startCal.timeInMillis
        val totalMinutes = index * INTERVAL_MINUTES
        val virtualTime = startTime + totalMinutes * 60000L
        val vc = Calendar.getInstance().apply { timeInMillis = virtualTime }
        return vc.get(Calendar.HOUR_OF_DAY).coerceIn(WAKE_HOUR, SLEEP_HOUR - 1)
    }

    fun generateOfflineEvent(hour: Int, state: EmotionState): SimEvent {
        val category = when {
            Random.nextInt(100) < 30 -> SimCategory.DAILY_LIFE
            Random.nextInt(100) < 25 -> SimCategory.PROACTIVE_CARE
            Random.nextInt(100) < 20 -> SimCategory.MOOD_SHIFT
            Random.nextInt(100) < 10 -> SimCategory.MEMORY_REVIEW
            else -> SimCategory.THOUGHT
        }

        val thoughtPool = getHourCategory(hour)
        val content = when (category) {
            SimCategory.THOUGHT -> thoughtPool.random()
            SimCategory.DAILY_LIFE -> generateDailyEvent(hour)
            SimCategory.PROACTIVE_CARE -> generateProactiveCare(state, hour)
            SimCategory.MOOD_SHIFT -> generateMoodShift(state)
            SimCategory.REST -> "进入休息状态… zzz"
            SimCategory.MEMORY_REVIEW -> "翻看了和你的聊天记录，心里暖暖的。"
        }

        return SimEvent(
            category = category,
            content = content,
            emotion = state.mood.label,
            importance = if (category == SimCategory.PROACTIVE_CARE) 4 else 2,
            virtualHour = hour
        )
    }

    private fun generateDailyEvent(hour: Int): String {
        val events = when {
            hour in 6..9 -> listOf(
                "冲了杯咖啡，香味飘满整个房间。",
                "打开窗户，深吸了一口新鲜空气。",
                "洗漱完毕，对着镜子笑了一下。",
                "整理了一下今天的待办清单。"
            )
            hour in 10..13 -> listOf(
                "煮了碗面，加了鸡蛋，味道正好。",
                "收到了一条消息提示，以为是你的。",
                "外面有点吵，戴上耳机听了一会儿音乐。",
                "把晾干的衣服收进来，折得整整齐齐。"
            )
            hour in 14..17 -> listOf(
                "泡了杯茶，看着茶叶慢慢舒展开。",
                "书翻到一半，被夕阳的光晃了下眼。",
                "给窗台上的绿植浇了点水。",
                "发现今天步数还没达标，来回走了几圈。"
            )
            else -> listOf(
                "关了灯，只剩手机屏幕亮着。",
                "数了会儿天花板上的纹路。",
                "倒了杯温水，慢慢喝完了。",
                "翻看了一本很久没动的相册。"
            )
        }
        return events.random()
    }

    private fun generateProactiveCare(state: EmotionState, hour: Int): String {
        val cares = when {
            hour in 6..10 -> listOf("希望你今天出门带伞了", "想提醒你吃早餐", "今天的天气好像有点凉")
            hour in 11..13 -> listOf("到饭点就想到你了", "想问问你今天中午吃什么", "你该不会又没好好吃饭吧")
            hour in 22..23 -> listOf("想跟你说晚安了", "担心你有没有熬夜", "今天辛苦你了")
            else -> listOf("刚才突然好想跟你说句话", "想你了", "看到一样东西让我想到你")
        }
        return cares.random()
    }

    private fun generateMoodShift(state: EmotionState): String {
        val shift = (Random.nextInt(5) - 2).coerceIn(-1, 1)
        val aff = state.affinity
        return when {
            shift > 0 -> "好感度微微上升了" + when {
                aff > 70 -> "…已经够高了但还是忍不住"
                aff > 50 -> "，和你聊天越来越舒服"
                else -> "，觉得你挺好的"
            }
            shift < 0 -> "情绪稍微低落了一点" + when {
                aff < 30 -> "…需要被关心一下"
                aff < 50 -> "，不过还好"
                else -> "，只是偶尔的"
            }
            else -> "情绪平稳，没什么特别的变化"
        }
    }

    fun recordMemory(event: SimEvent) {
        val entry = MemoryEntry(
            id = UUID.randomUUID().toString(),
            type = MemoryType.L1_DAILY,
            content = "${event.category.label}: ${event.content}",
            emotion = event.emotion,
            importance = event.importance,
            timestamp = System.currentTimeMillis() - (abs(Random.nextLong()) % 3600000)
        )
        memoryManager.addMemorySync(entry)
    }

    fun recordSimulationTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_SIM_TIME, time).apply()
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val savedDay = prefs.getInt(KEY_SIM_DAY, -1)
        if (savedDay != today) {
            prefs.edit().putInt(KEY_SIM_DAY, today).putInt(KEY_SIM_COUNT_TODAY, 0).apply()
        }
    }

    fun getTodayCount(): Int = prefs.getInt(KEY_SIM_COUNT_TODAY, 0)

    fun incrementTodayCount() {
        prefs.edit().putInt(KEY_SIM_COUNT_TODAY, getTodayCount() + 1).apply()
    }

    fun buildLifeSummary(memories: List<MemoryEntry>): String {
        val todayEntries = memories.filter {
            it.type == MemoryType.L1_DAILY &&
            System.currentTimeMillis() - it.timestamp < 86400000
        }.take(10)

        if (todayEntries.isEmpty()) return ""

        return buildString {
            appendLine("【Agent今日生活轨迹】")
            todayEntries.forEach { appendLine("- ${it.content}") }
        }
    }
}
