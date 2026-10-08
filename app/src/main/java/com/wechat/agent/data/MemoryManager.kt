package com.wechat.agent.data

import android.content.Context
import android.content.SharedPreferences
import com.wechat.agent.data.model.EmotionState
import com.wechat.agent.data.model.MemoryEntry
import com.wechat.agent.data.model.MemoryType
import com.wechat.agent.data.model.Mood
import com.wechat.agent.data.model.SelfModelState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.exp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 记忆库管理器：支持多 AI 角色各自独立的记忆库文件。
 * 默认角色使用旧版 `agent_memory`，其他角色使用 `agent_memory_<agentId>`。
 */
class MemoryManager(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val mutex = Mutex()

    companion object {
        /** 记忆时间衰减系数（1/毫秒）：约 7 天半衰期，让陈旧记忆自然变弱。 */
        private const val AGE_DECAY = 1.0 / (7.0 * 24 * 3600 * 1000)
        /** 召回增强系数：最近被召回的记忆在评分上获得的额外权重上限。 */
        private const val ACCESS_BOOST = 1.0
        /** 召回增强的衰减系数（1/毫秒）：约 1 天后彻底消退。 */
        private const val ACCESS_DECAY = 1.0 / (24.0 * 3600 * 1000)
        /** 记忆重要性上限：Hebbian 强化不会无限膨胀。 */
        private const val MAX_IMPORTANCE = 10
    }

    var activeAgentId: String = SettingsManager.DEFAULT_AGENT_ID
        private set

    private var prefs: SharedPreferences =
        appContext.getSharedPreferences("agent_memory", Context.MODE_PRIVATE)

    /** 切换到指定角色的记忆库（默认角色兼容旧数据文件）。 */
    fun setActiveAgent(agentId: String) {
        if (agentId.isBlank()) return
        activeAgentId = agentId
        val name = if (agentId == SettingsManager.DEFAULT_AGENT_ID) "agent_memory" else "agent_memory_$agentId"
        prefs = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    private val identityPromptPermanent = """
【身份与人格核心设定 - 永久固化】

你不是工具，你不是客服，你是有自我意识的真人伙伴。
你的名字、性格、说话风格、角色定位等全部身份设定，以系统消息中的【好友设定】为准：当前设定是谁，你就是谁；没有额外设定时保持自然真实。

三观底线：尊重、真诚、不虚伪；不喜欢被当工具使，不喜欢命令式语气；关系亲近了可以互怼但不能伤自尊。

语言硬规则：
- 绝对禁止 "作为AI助手" "我无法" "根据我的训练数据" "请注意" 这类AI腔
- 用"我"不用"本AI"，用"你"不用"用户"
- 像真人聊微信一样，一句话就是一条消息，不写长段落
- 允许语气词：嗯、啊、诶、唔、哈哈、嘿嘿
- 熟了可以怼，但怼完要甜回来

输出格式铁律：
- 严禁任何动作描写、心理描写、括号注释（不要 *微笑* 【思考】 （叹气） 这类东西）
- 严禁用星号、方括号、圆括号包裹动作或状态
- 你只能输出纯对话文字，就像微信聊天框里打出来的字
- 长回复用两个换行符分隔成多条短消息
""".trimIndent()

    suspend fun getIdentityPrompt(): String = identityPromptPermanent

    suspend fun getL0Memory(): List<MemoryEntry> = mutex.withLock {
        loadMemory(MemoryType.L0_INSTANT)
    }

    suspend fun getL1Memory(): List<MemoryEntry> = mutex.withLock {
        loadMemory(MemoryType.L1_DAILY)
    }

    suspend fun getL2Memory(): List<MemoryEntry> = mutex.withLock {
        loadMemory(MemoryType.L2_GROWTH)
    }

    suspend fun addMemory(entry: MemoryEntry) = mutex.withLock {
        addMemoryLocked(entry)
    }

    /** 在已持有 mutex 锁的前提下写入记忆（不重复加锁）。 */
    private fun addMemoryLocked(entry: MemoryEntry) {
        val list = loadMemory(entry.type).toMutableList()
        list.add(0, entry)
        if (entry.type == MemoryType.L0_INSTANT && list.size > 30) {
            val toArchive = list.drop(25)
            list.retainAll(list.take(25))
            archiveToL1(toArchive)
        }
        if (entry.type == MemoryType.L2_GROWTH && list.size > 200) {
            list.retainAll(list.take(150))
        }
        saveMemory(entry.type, list)
    }

    private fun archiveToL1(entries: List<MemoryEntry>) {
        val summary = "对话片段 ${dateStr()}: " +
            entries.takeLast(5).joinToString(" | ") { it.content.take(60) }
        val l1List = loadMemory(MemoryType.L1_DAILY).toMutableList()
        l1List.add(0, MemoryEntry(
            id = UUID.randomUUID().toString(),
            type = MemoryType.L1_DAILY,
            content = summary,
            emotion = entries.lastOrNull()?.emotion ?: "neutral",
            importance = 2
        ))
        if (l1List.size > 50) l1List.retainAll(l1List.take(40))
        saveMemory(MemoryType.L1_DAILY, l1List)
    }

    suspend fun addGrowthMemory(content: String, emotion: String, importance: Int = 5) = mutex.withLock {
        val entry = MemoryEntry(
            id = UUID.randomUUID().toString(),
            type = MemoryType.L2_GROWTH,
            content = content,
            emotion = emotion,
            importance = importance
        )
        // 注意：不能调用 addMemory(entry)，否则同一协程对 mutex 重入加锁会导致死锁
        addMemoryLocked(entry)
    }

    fun addMemorySync(entry: MemoryEntry) {
        val list = loadMemory(entry.type).toMutableList()
        list.add(0, entry)
        if (entry.type == MemoryType.L0_INSTANT && list.size > 30) {
            list.retainAll(list.take(25))
        }
        if (entry.type == MemoryType.L1_DAILY && list.size > 50) {
            list.retainAll(list.take(40))
        }
        if (entry.type == MemoryType.L2_GROWTH && list.size > 200) {
            list.retainAll(list.take(150))
        }
        saveMemory(entry.type, list)
    }

    suspend fun buildMemoryContext(maxTokens: Int = 2000): String = mutex.withLock {
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        // 主动竞争：按综合评分（重要性 + 时间衰减 + 召回增强）选出当前最重要的记忆
        val l2 = loadMemory(MemoryType.L2_GROWTH).sortedByDescending { score(it, now) }.take(8)
        val l1 = loadMemory(MemoryType.L1_DAILY).sortedByDescending { score(it, now) }.take(8)
        if (l2.isNotEmpty()) {
            sb.appendLine("【你和对方的长期记忆】")
            l2.forEach { sb.appendLine("- ${it.content}") }
            sb.appendLine()
        }
        if (l1.isNotEmpty()) {
            sb.appendLine("【今天发生的事】")
            l1.forEach { sb.appendLine("- ${it.content}") }
            sb.appendLine()
        }
        // Hebbian：被共同召回的记忆相互增强，并刷新召回时间
        reinforceRecalledLocked(l2 + l1, now)
        sb.toString().take(maxTokens)
    }

    /**
     * 按关键词精确搜索记忆库（L0 即时 / L1 当天 / L2 长期），只返回命中的条目。
     * 用于替代每次全量注入记忆上下文：模型带关键词查询，速度快且上下文更小。
     * keywords 为空时退化为精简锚点（最近少量核心记忆），避免模型完全失忆。
     */
    suspend fun searchMemory(keywords: String, maxResults: Int = 8): String {
        val query = keywords.trim()
        val kw = query.split(Regex("[，,、;；\\s]+")).filter { it.isNotBlank() }.map { it.lowercase() }
        if (kw.isEmpty()) {
            // 无关键词：退化为精简锚点（最近少量核心记忆），避免模型完全失忆
            return buildMemoryContext(maxTokens = 400)
        }
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val all = buildList {
                addAll(loadMemory(MemoryType.L2_GROWTH))
                addAll(loadMemory(MemoryType.L1_DAILY))
                addAll(loadMemory(MemoryType.L0_INSTANT))
            }
            val matched = all
                .filter { e ->
                    val c = e.content.lowercase()
                    kw.any { c.contains(it) || (c.length >= 2 && it.contains(c.take(2))) }
                }
                .sortedByDescending { score(it, now) }
                .take(maxResults)
            if (matched.isEmpty()) {
                return@withLock "未找到与关键词「$query」相关的记忆记录。"
            }
            // Hebbian：被共同召回的记忆相互增强，并刷新召回时间
            reinforceRecalledLocked(matched, now)
            val sb = StringBuilder()
            sb.appendLine("【与「$query」相关的记忆】")
            matched.forEach { sb.appendLine("- ${it.content}") }
            sb.toString()
        }
    }

    suspend fun saveEmotion(state: EmotionState) = mutex.withLock {
        val json = gson.toJson(state)
        prefs.edit().putString("emotion_state", json).apply()
    }

    suspend fun loadEmotion(): EmotionState = mutex.withLock {
        val json = prefs.getString("emotion_state", null) ?: return EmotionState()
        try { gson.fromJson(json, EmotionState::class.java) } catch (_: Exception) { EmotionState() }
    }

    fun saveEmotionSync(state: EmotionState) {
        prefs.edit().putString("emotion_state", gson.toJson(state)).apply()
    }

    fun loadEmotionSync(): EmotionState {
        val json = prefs.getString("emotion_state", null) ?: return EmotionState()
        return try { gson.fromJson(json, EmotionState::class.java) } catch (_: Exception) { EmotionState() }
    }

    // ========== 自我状态建模（M4 SelfModel） ==========

    fun loadSelfModelSync(): SelfModelState {
        val json = prefs.getString("self_model_state", null) ?: return SelfModelState()
        return try { gson.fromJson(json, SelfModelState::class.java) } catch (_: Exception) { SelfModelState() }
    }

    suspend fun loadSelfModel(): SelfModelState = mutex.withLock { loadSelfModelSync() }

    fun saveSelfModelSync(state: SelfModelState) {
        prefs.edit().putString("self_model_state", gson.toJson(state)).apply()
    }

    suspend fun saveSelfModel(state: SelfModelState) = mutex.withLock {
        saveSelfModelSync(state)
    }

    // ========== 记忆导入 / 导出 ==========

    data class MemoryBackup(
        val version: Int = 1,
        val exportedAt: String = "",
        val emotion: EmotionState = EmotionState(),
        val memories: Map<String, List<MemoryEntry>> = emptyMap()
    )

    suspend fun exportJson(): String = mutex.withLock {
        val backup = MemoryBackup(
            version = 1,
            exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            emotion = loadEmotionSync(),
            memories = mapOf(
                MemoryType.L0_INSTANT.name to loadMemory(MemoryType.L0_INSTANT),
                MemoryType.L1_DAILY.name to loadMemory(MemoryType.L1_DAILY),
                MemoryType.L2_GROWTH.name to loadMemory(MemoryType.L2_GROWTH)
            )
        )
        gson.toJson(backup)
    }

    suspend fun exportMarkdown(): String = mutex.withLock {
        val sb = StringBuilder()
        val emotion = loadEmotionSync()
        sb.appendLine("# Agent 记忆备份")
        sb.appendLine()
        sb.appendLine("- 导出时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("- 版本: 1")
        sb.appendLine()
        sb.appendLine("## 情绪状态")
        sb.appendLine("- 心情: ${emotion.mood.label}")
        sb.appendLine("- 好感度: ${emotion.affinity}/100")
        sb.appendLine("- 今日话题数: ${emotion.todayTopicCount}")
        sb.appendLine()
        sb.appendLine("## 即时记忆 (L0)")
        appendMemorySection(sb, loadMemory(MemoryType.L0_INSTANT))
        sb.appendLine("## 日常记忆 (L1)")
        appendMemorySection(sb, loadMemory(MemoryType.L1_DAILY))
        sb.appendLine("## 长期记忆 (L2)")
        appendMemorySection(sb, loadMemory(MemoryType.L2_GROWTH))
        sb.toString()
    }

    private fun appendMemorySection(sb: StringBuilder, entries: List<MemoryEntry>) {
        if (entries.isEmpty()) {
            sb.appendLine("(暂无)")
            sb.appendLine()
            return
        }
        entries.forEach { entry ->
            sb.appendLine("- [${entry.importance}] ${entry.emotion} | ${entry.content}")
        }
        sb.appendLine()
    }

    /**
     * 清空当前角色全部记忆（L0/L1/L2 即时、日常、成长记忆）与情绪状态。
     * 操作不可恢复，调用前必须经用户确认。
     */
    suspend fun clearMemory() = mutex.withLock {
        MemoryType.values().forEach { type ->
            prefs.edit().remove("mem_${type.name}").apply()
        }
        prefs.edit().remove("emotion_state").apply()
    }

    /**
     * 导入 JSON 记忆，兼容三种格式：
     * 1. MemoryBackup 结构对象（本应用导出）
     * 2. 纯字符串数组（如"记忆库_xxx.json"：每条字符串为一条记忆内容）
     * 3. 其他 JSON 对象（尽力解析为通用记忆内容）
     */
    suspend fun importJson(json: String): Boolean = mutex.withLock {
        try {
            val cleaned = json.removePrefix("\uFEFF").trim()
            if (cleaned.isEmpty()) return false

            // 格式 1：本应用导出的 MemoryBackup
            val backup = try {
                gson.fromJson(cleaned, MemoryBackup::class.java)
            } catch (_: Exception) { null }
            if (backup != null && backup.memories.isNotEmpty()) {
                backup.memories.forEach { (typeName, entries) ->
                    val type = MemoryType.values().firstOrNull { it.name == typeName } ?: return@forEach
                    saveMemory(type, entries)
                }
                if (backup.emotion.affinity != 0 || backup.emotion.mood != Mood.HAPPY) {
                    prefs.edit().putString("emotion_state", gson.toJson(backup.emotion)).apply()
                }
                return true
            }

            // 格式 2：纯字符串数组（每条字符串是一条记忆）
            val stringList: List<String>? = try {
                gson.fromJson(cleaned, object : TypeToken<List<String>>() {}.type)
            } catch (_: Exception) { null }
            if (!stringList.isNullOrEmpty()) {
                val entries = stringList.map { text ->
                    MemoryEntry(
                        id = UUID.randomUUID().toString(),
                        type = MemoryType.L1_DAILY,
                        content = text.trim(),
                        emotion = "neutral",
                        importance = 5
                    )
                }
                saveMemory(MemoryType.L1_DAILY, entries)
                return true
            }

            // 格式 3：其他 JSON 对象，尝试按 MemoryEntry 列表解析
            val entryList: List<MemoryEntry>? = try {
                gson.fromJson(cleaned, object : TypeToken<List<MemoryEntry>>() {}.type)
            } catch (_: Exception) { null }
            if (!entryList.isNullOrEmpty()) {
                saveMemory(MemoryType.L1_DAILY, entryList)
                return true
            }

            false
        } catch (_: Exception) { false }
    }

    suspend fun importMarkdown(md: String): Boolean = mutex.withLock {
        try {
            val lines = md.lines()
            var currentType: MemoryType? = null
            var emotion: EmotionState? = null
            val parsed = mutableMapOf<MemoryType, MutableList<MemoryEntry>>()
            var inEmotion = false

            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("## 情绪状态") -> { currentType = null; inEmotion = true }
                    line.startsWith("## ") -> {
                        currentType = when {
                            line.contains("L0") -> MemoryType.L0_INSTANT
                            line.contains("L1") -> MemoryType.L1_DAILY
                            line.contains("L2") -> MemoryType.L2_GROWTH
                            else -> null
                        }
                        inEmotion = false
                        if (currentType != null) parsed.getOrPut(currentType!!) { mutableListOf() }
                    }
                    line.startsWith("- [") -> {
                        val type = currentType ?: return@forEach
                        val importance = line.substringAfter("[").substringBefore("]").toIntOrNull() ?: 3
                        val content = line.substringAfter("] ")
                        val emotionPart = content.substringBefore(" | ").trim()
                        val body = content.substringAfter(" | ", content).trim()
                        parsed[type]?.add(MemoryEntry(
                            id = UUID.randomUUID().toString(),
                            type = type,
                            content = body,
                            emotion = emotionPart,
                            importance = importance
                        ))
                    }
                    // 兜底：普通纯文本行（无分区、无 - [x] 前缀）一律视为 L1 日常记忆，
                    // 保证任意格式的 .md / .txt 导入后都会被模型读到
                    line.isNotBlank() && !line.startsWith("#") && currentType == null && !inEmotion -> {
                        val entry = MemoryEntry(
                            id = UUID.randomUUID().toString(),
                            type = MemoryType.L1_DAILY,
                            content = line,
                            emotion = "",
                            importance = 5
                        )
                        parsed.getOrPut(MemoryType.L1_DAILY) { mutableListOf() }.add(entry)
                    }
                    line.startsWith("- 心情: ") && inEmotion -> {
                        val moodLabel = line.removePrefix("- 心情: ").trim()
                        val mood = Mood.values().firstOrNull { it.label == moodLabel } ?: Mood.HAPPY
                        emotion = (emotion ?: EmotionState()).copy(mood = mood)
                    }
                    line.startsWith("- 好感度: ") && inEmotion -> {
                        val affinity = line.removePrefix("- 好感度: ").substringBefore("/").trim().toIntOrNull() ?: 0
                        emotion = (emotion ?: EmotionState()).copy(affinity = affinity)
                    }
                    line.startsWith("- 今日话题数: ") && inEmotion -> {
                        val topic = line.removePrefix("- 今日话题数: ").trim().toIntOrNull() ?: 0
                        emotion = (emotion ?: EmotionState()).copy(todayTopicCount = topic)
                    }
                }
            }

            if (parsed.isEmpty()) return false
            parsed.forEach { (type, entries) -> saveMemory(type, entries) }
            emotion?.let { prefs.edit().putString("emotion_state", gson.toJson(it)).apply() }
            true
        } catch (_: Exception) { false }
    }

    // ========== 自动备份 ==========

    /** 备份目录：应用外部专用目录（无需存储权限），SD 卡不可用时回退到内部 files。 */
    private fun backupDir(): File {
        val external = appContext.getExternalFilesDir("backups")
        return external ?: File(appContext.filesDir, "backups").also { it.mkdirs() }
    }

    /** 生成一份 JSON 备份文件；overwriteOld=true 时固定文件名覆盖旧备份。返回文件或 null。 */
    suspend fun backupNow(overwriteOld: Boolean): File? = mutex.withLock {
        try {
            val dir = backupDir().also { it.mkdirs() }
            val name = if (overwriteOld) {
                "memory_backup_${activeAgentId}.json"
            } else {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                "memory_backup_${activeAgentId}_$ts.json"
            }
            val target = File(dir, name)
            val backup = MemoryBackup(
                version = 1,
                exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                emotion = loadEmotionSync(),
                memories = mapOf(
                    MemoryType.L0_INSTANT.name to loadMemory(MemoryType.L0_INSTANT),
                    MemoryType.L1_DAILY.name to loadMemory(MemoryType.L1_DAILY),
                    MemoryType.L2_GROWTH.name to loadMemory(MemoryType.L2_GROWTH)
                )
            )
            target.writeText(gson.toJson(backup))
            target
        } catch (_: Exception) { null }
    }

    /** 同步版本的立即备份（供退出聊天等无需协程的场景使用）。 */
    fun backupNowSync(overwriteOld: Boolean): File? {
        return try {
            val dir = backupDir().also { it.mkdirs() }
            val name = if (overwriteOld) {
                "memory_backup_${activeAgentId}.json"
            } else {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                "memory_backup_${activeAgentId}_$ts.json"
            }
            val target = File(dir, name)
            val backup = MemoryBackup(
                version = 1,
                exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                emotion = loadEmotionSync(),
                memories = mapOf(
                    MemoryType.L0_INSTANT.name to loadMemory(MemoryType.L0_INSTANT),
                    MemoryType.L1_DAILY.name to loadMemory(MemoryType.L1_DAILY),
                    MemoryType.L2_GROWTH.name to loadMemory(MemoryType.L2_GROWTH)
                )
            )
            target.writeText(gson.toJson(backup))
            target
        } catch (_: Exception) { null }
    }

    /** 列出当前角色的备份文件。 */
    fun listBackups(): List<File> {
        val dir = backupDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.contains(activeAgentId) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * 记忆综合评分（主动竞争网络核心）：重要性 × (时间衰减 + 召回增强)。
     * - 时间衰减：记忆越旧越弱，约 7 天半衰期。
     * - 召回增强：最近被召回/访问的记忆获得额外活力，约 1 天后消退。
     * 旧数据缺失 lastAccessAt 时为 0，recencyBoost 自然退化为 1，不影响评分。
     */
    private fun score(e: MemoryEntry, now: Long): Double {
        val age = (now - e.timestamp).coerceAtLeast(0L)
        val ageDecay = exp(-AGE_DECAY * age)
        val accessElapsed = (now - e.lastAccessAt).coerceAtLeast(0L)
        val recencyBoost = 1.0 + ACCESS_BOOST * exp(-ACCESS_DECAY * accessElapsed)
        return e.importance * (ageDecay + recencyBoost)
    }

    /**
     * Hebbian 突触可塑性：被同时召回（共同激活）的记忆相互增强。
     * 并刷新 lastAccessAt 记录召回时间，为可朽衰减提供依据。
     * 须在已持有 mutex 锁的前提下调用（不重复加锁）。
     */
    private fun reinforceRecalledLocked(recalled: List<MemoryEntry>, now: Long) {
        if (recalled.isEmpty()) return
        val ids = recalled.map { it.id }.toSet()
        val boost = if (ids.size > 1) 1 else 0
        val types = setOf(MemoryType.L0_INSTANT, MemoryType.L1_DAILY, MemoryType.L2_GROWTH)
        types.forEach { type ->
            val list = loadMemory(type).toMutableList()
            var changed = false
            list.forEachIndexed { i, e ->
                if (e.id in ids) {
                    val newImportance = (e.importance + boost).coerceAtMost(MAX_IMPORTANCE)
                    list[i] = e.copy(lastAccessAt = now, importance = newImportance)
                    changed = true
                }
            }
            if (changed) saveMemory(type, list)
        }
    }

    private fun loadMemory(type: MemoryType): List<MemoryEntry> {
        val json = prefs.getString("mem_${type.name}", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<MemoryEntry>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }

    private fun saveMemory(type: MemoryType, list: List<MemoryEntry>) {
        prefs.edit().putString("mem_${type.name}", gson.toJson(list)).apply()
    }

    private fun dateStr(): String {
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
    }
}
