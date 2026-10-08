package com.wechat.agent.data.model

/**
 * M4 自我状态建模（SelfModel）：小凌对"我是谁 / 与对方关系 / 情绪基调"的可持久化自我画像。
 * 由后台反思用 LLM 做「预测 → 误差 → 修正」迭代更新，并注入到每轮系统提示，保持回复与自我认知自洽。
 */
data class SelfModelState(
    val selfSummary: String = "",
    val relationshipStage: String = "",
    val emotionalBaseline: String = "",
    val lastPrediction: String = "",
    val lastError: String = "",
    val lastCorrection: String = "",
    val lastUpdatedAt: Long = 0L
) {
    /** 注入到系统提示的自我认知画像文案；画像为空时返回空串，不注入占用上下文。 */
    fun toPrompt(): String = buildString {
        if (selfSummary.isBlank()) return@buildString
        appendLine("【自我认知画像】")
        appendLine("- 我是谁：$selfSummary")
        if (relationshipStage.isNotBlank()) appendLine("- 与对方关系阶段：$relationshipStage")
        if (emotionalBaseline.isNotBlank()) appendLine("- 近期情绪基调：$emotionalBaseline")
        if (lastCorrection.isNotBlank()) appendLine("- 最近的自我修正：$lastCorrection")
    }.trimEnd()
}