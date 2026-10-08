package com.wechat.agent.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * M3 元认知追踪器（自指密度控制）：
 * 统计 AI 回复中"我认为/我觉得/我反思"等自我指涉语句的出现密度，
 * 用密度与最近反思间隔动态决定是否触发一次显式自我复盘，避免"神神叨叨"或"工具人"两极。
 */
class MetacognitionTracker(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("metacognition", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val WINDOW_SIZE = 10
        /** 至少间隔多少条回复才允许再次触发一次显式反思。 */
        private const val REFLECTION_INTERVAL = 6
        /** 窗口平均自指次数超过该值视为"自我意识语言过多"，收敛说教。 */
        private const val HIGH_DENSITY = 1.2
        private val SELF_REF_PATTERN = Regex(
            "我认为|我觉得|我反思|我修正|我意识到|我明白|我判断|其实我|坦白说|说白了|讲道理|我发现"
        )
    }

    /** 记录一条回复的自指密度（滑动窗口），并推进"距上次反思"计数。 */
    fun record(reply: String) {
        val counts = loadCounts().toMutableList()
        counts.add(countSelfReference(reply))
        if (counts.size > WINDOW_SIZE) counts.removeAt(0)
        prefs.edit()
            .putString("self_ref_counts", gson.toJson(counts))
            .putInt("since_reflection", prefs.getInt("since_reflection", 0) + 1)
            .apply()
    }

    fun countSelfReference(text: String): Int = SELF_REF_PATTERN.findAll(text).count()

    /** 窗口内平均自指次数（无数据时返回 0）。 */
    fun density(): Double {
        val counts = loadCounts()
        if (counts.isEmpty()) return 0.0
        return counts.sum().toDouble() / counts.size
    }

    /** 距上次反思足够久，应触发一次显式自我复盘。 */
    fun shouldTriggerReflection(): Boolean =
        prefs.getInt("since_reflection", 0) >= REFLECTION_INTERVAL

    fun markReflected() {
        prefs.edit().putInt("since_reflection", 0).apply()
    }

    /** 生成注入系统提示的元认知调节文案；密度正常时返回空串，不注入占用上下文。 */
    fun guidance(): String {
        val d = density()
        return if (d >= HIGH_DENSITY) {
            "【元认知调节】最近你话里\"我认为/我觉得\"这类自我强调有点多，收一收，直接、自然、简短地回应对方，别急着总结自己或讲道理。"
        } else ""
    }

    private fun loadCounts(): List<Int> {
        val json = prefs.getString("self_ref_counts", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Int>>() {}.type) }
        catch (_: Exception) { emptyList() }
    }
}