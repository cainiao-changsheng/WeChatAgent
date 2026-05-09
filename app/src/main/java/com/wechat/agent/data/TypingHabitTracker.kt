package com.wechat.agent.data

import android.content.SharedPreferences
import kotlin.math.max
import kotlin.random.Random

class TypingHabitTracker(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_TOTAL_MSGS = "total_msgs"
        private const val KEY_AVG_LEN = "avg_len"
        private const val KEY_MIN_LEN = "min_len"
        private const val KEY_MAX_LEN = "max_len"
        private const val KEY_SHORT_RATE = "short_rate"
        private const val KEY_DELAY_AVG = "delay_avg"
        private const val KEY_PUNCT_STYLE = "punct_style"
        private const val KEY_HABIT_SEED = "habit_seed"
    }

    class TypingStyle(
        val avgLen: Int = 15,
        val minLen: Int = 2,
        val maxLen: Int = 35,
        val shortRate: Float = 0.4f,
        val delayMs: Long = 800,
        val punctStyle: String = "mixed"
    )

    fun recordUserMessage(text: String, delaySinceLast: Long) {
        val total = prefs.getInt(KEY_TOTAL_MSGS, 0) + 1
        val oldAvg = prefs.getFloat(KEY_AVG_LEN, 15f)
        val newAvg = oldAvg * 0.9f + text.length * 0.1f
        val min = max(2, minOf(prefs.getInt(KEY_MIN_LEN, 5), text.length))
        val max = max(15, maxOf(prefs.getInt(KEY_MAX_LEN, 25), text.length))
        val isShort = if (text.length < 8) 1f else 0f
        val oldShort = prefs.getFloat(KEY_SHORT_RATE, 0.35f)
        val newShort = oldShort * 0.9f + isShort * 0.1f
        val oldDelay = prefs.getFloat(KEY_DELAY_AVG, 2000f)
        val newDelay = if (delaySinceLast in 500..30000) oldDelay * 0.85f + delaySinceLast * 0.15f else oldDelay

        val punctStyle = when {
            text.contains("！") || text.contains("!") -> "excited"
            text.contains("～") || text.contains("~") -> "soft"
            text.contains("…") || text.contains("...") -> "hesitant"
            !text.any { c in "。！？.!?" } -> "loose"
            else -> prefs.getString(KEY_PUNCT_STYLE, "mixed") ?: "mixed"
        }

        prefs.edit()
            .putInt(KEY_TOTAL_MSGS, total)
            .putFloat(KEY_AVG_LEN, newAvg)
            .putInt(KEY_MIN_LEN, min)
            .putInt(KEY_MAX_LEN, max)
            .putFloat(KEY_SHORT_RATE, newShort)
            .putFloat(KEY_DELAY_AVG, newDelay)
            .putString(KEY_PUNCT_STYLE, punctStyle)
            .apply()
    }

    fun getMyTypingStyle(): TypingStyle {
        val userAvg = prefs.getFloat(KEY_AVG_LEN, 15f).toInt()
        val userMin = prefs.getInt(KEY_MIN_LEN, 3)
        val userMax = prefs.getInt(KEY_MAX_LEN, 30)
        val userShort = prefs.getFloat(KEY_SHORT_RATE, 0.35f)
        val userDelay = prefs.getFloat(KEY_DELAY_AVG, 2000f)
        val userPunct = prefs.getString(KEY_PUNCT_STYLE, "mixed") ?: "mixed"
        val total = prefs.getInt(KEY_TOTAL_MSGS, 0)

        val seed = if (total > 0) prefs.getString(KEY_HABIT_SEED, null)?.hashCode()
            ?: Random.nextInt().also { prefs.edit().putString(KEY_HABIT_SEED, it.toString()).apply() }
        else 0

        val rng = Random(seed xor 0x5AD0)

        val variance = when {
            total < 5 -> 0.25f
            total < 20 -> 0.20f
            total < 50 -> 0.15f
            else -> 0.12f
        }

        val myAvg = (userAvg * (0.6f + rng.nextFloat() * 0.6f)).toInt().coerceIn(5, 40)
        val myMin = max(1, (userMin * (0.7f + rng.nextFloat() * 0.5f)).toInt())
        val myMax = max(10, (userMax * (0.7f + rng.nextFloat() * 0.8f)).toInt())
        val myShort = (userShort * (0.5f + rng.nextFloat() * 0.8f)).coerceIn(0.15f, 0.7f)
        val myDelay = (userDelay * (0.6f + rng.nextFloat() * 0.8f)).coerceIn(400f, 5000f)

        val myPunct = when (userPunct) {
            "excited" -> if (rng.nextBoolean()) "soft" else "excited"
            "soft" -> if (rng.nextBoolean()) "hesitant" else "soft"
            "hesitant" -> if (rng.nextBoolean()) "loose" else "hesitant"
            "loose" -> if (rng.nextBoolean()) "mixed" else "loose"
            else -> listOf("soft", "mixed", "hesitant")[rng.nextInt(3)]
        }

        return TypingStyle(
            avgLen = myAvg,
            minLen = myMin,
            maxLen = myMax,
            shortRate = myShort,
            delayMs = myDelay.toLong(),
            punctStyle = myPunct
        )
    }

    fun splitIntoMessages(text: String): List<String> {
        if (text.length < 20) return listOf(text)

        val style = getMyTypingStyle()
        val messages = mutableListOf<String>()
        val rawSegments = text
            .split(Regex("(?<=[。！？!?\\n])|(?=\n)"))
            .filter { it.isNotBlank() }
            .flatMap { seg ->
                if (seg.length <= style.maxLen) listOf(seg.trim())
                else seg.trim().chunked(style.maxLen).map { it.trim() }
            }
            .filter { it.isNotEmpty() }

        for (seg in rawSegments) {
            if (seg.length <= style.maxLen) {
                messages.add(seg)
            } else {
                var remaining = seg
                while (remaining.length > style.maxLen) {
                    val cutPoint = remaining.indexOfAny("，, ", style.maxLen - 10)
                        .let { if (it in 1..style.maxLen) it else style.maxLen }
                    messages.add(remaining.substring(0, cutPoint).trim())
                    remaining = remaining.substring(cutPoint).trim()
                }
                if (remaining.isNotEmpty()) messages.add(remaining)
            }
        }
        return if (messages.isEmpty()) listOf(text.take(style.maxLen)) else messages
    }

    fun getMessageDelays(count: Int): List<Long> {
        val style = getMyTypingStyle()
        val rng = Random(System.currentTimeMillis())
        return (0 until count).map { i ->
            if (i == 0) 0L
            else {
                val base = style.delayMs
                val variation = (base * (0.4f + rng.nextFloat() * 0.5f))
                variation.toLong().coerceIn(300, 6000)
            }
        }
    }

    fun getFormatRule(): String {
        val style = getMyTypingStyle()
        val punctRule = when (style.punctStyle) {
            "soft" -> "多用～结尾，语气柔和"
            "hesitant" -> "偶尔用…体现犹豫，但不要每句都用"
            "excited" -> "偶尔可以用！来表达开心，但不要太频繁"
            "loose" -> "标点随意，想到什么打什么"
            else -> "自然使用标点，像普通人一样"
        }
        return buildString {
            appendLine("【输出格式硬规则 - 严禁违反】")
            appendLine("1. 每条消息不超过${style.maxLen}个字，像真人聊微信一样短")
            appendLine("2. 长回复必须拆成多条短消息发出，每条${style.avgLen}字左右")
            appendLine("3. 绝对禁止用 *动作描写* 【心理描写】 （括号描写） 任何非对话内容")
            appendLine("4. 禁止 "作为AI" "请注意" "根据对话" 等AI腔")
            appendLine("5. $punctRule")
            appendLine("6. 用 \\n\\n 分隔要发多条消息的内容（不能用\\n\\n就自然断开）")
            appendLine("7. 禁止写超过2行的长段落")
        }
    }
}
