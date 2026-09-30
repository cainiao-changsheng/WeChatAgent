package com.wechat.agent.viewmodel

import android.app.Application
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wechat.agent.agent.AgentToolRegistry
import com.wechat.agent.data.AppLogger
import com.wechat.agent.data.EmojiManager
import com.wechat.agent.data.EmotionEngine
import com.wechat.agent.data.ImageCacheHelper
import com.wechat.agent.data.LifeSimulator
import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.MomentsGenerator
import com.wechat.agent.data.LifeDecisionEngine
import com.wechat.agent.data.MusicController
import com.wechat.agent.data.ObservationEntry
import com.wechat.agent.data.ObservationStore
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.TypingHabitTracker
import com.wechat.agent.data.model.AgentStatus
import com.wechat.agent.data.model.Chat
import com.wechat.agent.data.model.EmotionState
import com.wechat.agent.data.model.MemoryEntry
import com.wechat.agent.data.model.MemoryType
import com.wechat.agent.data.model.Message
import com.wechat.agent.data.model.MessageStatus
import com.wechat.agent.data.model.MomentPost
import com.wechat.agent.data.model.Mood
import com.wechat.agent.data.model.Role
import com.wechat.agent.data.network.ChatMessage
import com.wechat.agent.data.repository.ChatRepository
import com.wechat.agent.data.repository.ChatRepository.StreamPiece
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager.getInstance(application)
    private val memoryManager = MemoryManager(application)
    private val emotionEngine = EmotionEngine(memoryManager)
    private val repository = ChatRepository(memoryManager)
    private val musicController = MusicController(application)
    private val lifeSimulator = LifeSimulator(
        application.getSharedPreferences("life_sim", 0), memoryManager
    )
    private val typingTracker = TypingHabitTracker(
        application.getSharedPreferences("typing_habits", 0)
    )
    private val momentsGenerator = MomentsGenerator(memoryManager)
    private val decisionEngine = LifeDecisionEngine(memoryManager)
    private val emojiManager = EmojiManager(application)
    private val observationStore = ObservationStore(application)
    private val gson = Gson()

    // 各角色独立的数据文件（默认角色使用旧文件兼容历史数据）
    // 注意：聊天列表（chat_sessions）改为全局共享，不再按角色隔离，
    // 每个会话通过 agentId 字段标记归属好友；切换好友时列表保持完整，
    // 选中会话后自动切换到对应角色的记忆/人设/情绪。
    private var currentAgentId: String = SettingsManager.DEFAULT_AGENT_ID
    private var chatPrefs: SharedPreferences = application.getSharedPreferences("chat_sessions", 0)
    private var momentsPrefs: SharedPreferences = application.getSharedPreferences("moments", 0)
    private var statusPrefs: SharedPreferences = application.getSharedPreferences("agent_status", 0)

    private fun prefsName(base: String): String =
        if (currentAgentId.isBlank() || currentAgentId == SettingsManager.DEFAULT_AGENT_ID) base else "${base}_$currentAgentId"

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId = _currentChatId.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<Message>>(emptyList())
    val currentMessages = _currentMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent = _streamingContent.asStateFlow()

    /** 流式期间的思考过程（reasoning_content），与正文分开渲染为思考气泡。 */
    private val _streamingReasoning = MutableStateFlow("")
    val streamingReasoning = _streamingReasoning.asStateFlow()

    /** 大模型思考/回复的最长等待时间；高级设置"停用超时"开启时不设限。 */
    private val REPLY_TIMEOUT_MS = 600_000L
    /** "发现"页自动生成观察记录的最短间隔：30 分钟，避免频繁切换页面生成重复内容。 */
    private val MIN_OBSERVATION_INTERVAL_MS = 30 * 60 * 1000L

    private val _emotionState = MutableStateFlow(EmotionState())
    val emotionState = _emotionState.asStateFlow()

    private val _moodText = MutableStateFlow("")
    val moodText = _moodText.asStateFlow()

    private val _nowPlaying = MutableStateFlow(MusicController.NowPlaying())
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _momentsPosts = MutableStateFlow<List<MomentPost>>(emptyList())
    val momentsPosts = _momentsPosts.asStateFlow()

    /** "发现"页：当前选中好友的客观行为观察记录（时间倒序，最新在上） */
    private val _observations = MutableStateFlow<List<ObservationEntry>>(emptyList())
    val observations = _observations.asStateFlow()

    private val _generatingObservation = MutableStateFlow(false)
    val generatingObservation = _generatingObservation.asStateFlow()

    private val _agentStatus = MutableStateFlow(AgentStatus())
    val agentStatus = _agentStatus.asStateFlow()

    /** 用户是否正在输入（聊天界面输入框非空），输入中时 AI 不主动插话 */
    private val _userTyping = MutableStateFlow(false)
    val userTyping = _userTyping.asStateFlow()

    fun setUserTyping(typing: Boolean) {
        _userTyping.value = typing
    }

    private var streamingJob: Job? = null
    private var deliveryJob: Job? = null
    private var statusJob: Job? = null
    private var backupJob: Job? = null
    private var proactiveJob: Job? = null
    private var lastUserMessageTime: Long = 0
    private var messageDeliverySequence = 0
    private var lastBackupAt: Long = 0

    companion object {
        /** 热恋模式主动消息：上次主动联系的时间戳 key（agent_status 独立存储）。 */
        private const val KEY_HOTLOVE_LAST_PROACTIVE = "hotlove_last_proactive_at"
    }

    init {
        // 一次性迁移：v1.0.13 及之前聊天列表按角色隔离（chat_sessions_<agentId>），
        // 现改为全局共享（chat_sessions），合并历史会话避免升级后列表丢失。
        migrateLegacyChatsToGlobal()
        // 先以默认角色初始化，随后按当前选中角色切换加载
        bindAgent(SettingsManager.DEFAULT_AGENT_ID, apply = true)
        viewModelScope.launch {
            val id = settingsManager.currentAgentId.first()
            if (id != currentAgentId) bindAgent(id, apply = true)
            repository.formatRule = typingTracker.getFormatRule()
        }
        try { musicController.connect() } catch (_: Exception) {}
    }

    /** 将旧版按角色隔离的聊天记录合并进全局 chat_sessions（仅执行一次）。 */
    private fun migrateLegacyChatsToGlobal() {
        try {
            val global = getApplication<Application>().getSharedPreferences("chat_sessions", 0)
            if (global.getBoolean("migrated_global_chats", false)) return
            val merged = LinkedHashMap<String, Chat>()
            fun absorb(json: String?) {
                if (json.isNullOrBlank()) return
                runCatching {
                    gson.fromJson<List<Chat>>(json, object : TypeToken<List<Chat>>() {}.type)
                }.getOrNull()?.forEach { c -> merged[c.id] = c }
            }
            absorb(global.getString("chats", null))
            for (p in settingsManager.agentProfiles.value) {
                if (p.id == SettingsManager.DEFAULT_AGENT_ID) continue
                val pfs = getApplication<Application>().getSharedPreferences("chat_sessions_${p.id}", 0)
                absorb(pfs.getString("chats", null))
            }
            global.edit()
                .putString("chats", gson.toJson(merged.values.toList()))
                .putBoolean("migrated_global_chats", true)
                .apply()
            AppLogger.log("ChatVM", "migrateLegacyChatsToGlobal 合并会话=${merged.size}")
        } catch (_: Exception) {}
    }

    /**
     * 切换当前 AI 角色：记忆库、聊天记录、朋友圈、状态、情绪全部切换为该角色独立数据。
     */
    fun switchAgent(agentId: String) {
        if (agentId.isBlank()) return
        AppLogger.log("ChatVM", "switchAgent(agentId=$agentId) 当前角色=$currentAgentId")
        viewModelScope.launch {
            settingsManager.setCurrentAgentId(agentId)
            bindAgent(agentId, apply = true)
        }
    }

    private fun bindAgent(agentId: String, apply: Boolean) {
        AppLogger.log("ChatVM", "bindAgent(agentId=$agentId, apply=$apply) 旧角色=$currentAgentId")
        currentAgentId = agentId
        memoryManager.setActiveAgent(agentId)
        // 聊天列表全局共享，不随角色切换 prefs；朋友圈/状态/情绪仍按角色隔离
        momentsPrefs = getApplication<Application>().getSharedPreferences(prefsName("moments"), 0)
        statusPrefs = getApplication<Application>().getSharedPreferences(prefsName("agent_status"), 0)
        if (apply) {
            loadChatsFromStorage()
            loadMomentsFromStorage()
            _agentStatus.value = decisionEngine.loadStatus(statusPrefs)
            viewModelScope.launch {
                _emotionState.value = memoryManager.loadEmotion()
                _moodText.value = emotionEngine.getMoodDescription(
                    _emotionState.value.mood, _emotionState.value.affinity
                )
                refreshAgentStatus()
            }
            startStatusLoop()
            startBackupLoop()
            startProactiveLoop()
        }
    }

    /**
     * 每 30 分钟自动刷新 Agent 生活状态：
     * 生成"此刻在做什么"、决策是否联系用户 / 是否发动态，并自主执行。
     */
    private fun startStatusLoop() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            while (isActive) {
                delay(LifeSimulator.INTERVAL_MINUTES * 60 * 1000L)
                runLifeSimulation()
                refreshAgentStatus()
            }
        }
    }

    /**
     * 热恋模式「AI 后台主动发消息」循环：每 60 秒检查一次。
     * 时间间隔由大模型按角色设定自定一次（proactiveIntervalFromAi 置 true），
     * 用户可在热恋模式页手动修改该值（修改后置 false，AI 不再覆盖）；此后按该间隔主动联系。
     */
    private fun startProactiveLoop() {
        proactiveJob?.cancel()
        proactiveJob = viewModelScope.launch {
            while (isActive) {
                delay(60 * 1000L)
                try {
                    val hotLove = settingsManager.getHotLoveSettingsSync()
                    if (!hotLove.enabled || !hotLove.proactiveMessages) continue
                    if (_userTyping.value) continue

                    // 若间隔尚未由大模型决定且用户未手动设置过，则询问一次并保存
                    if (!hotLove.proactiveIntervalFromAi && !hotLove.proactiveIntervalUserSet) {
                        ensureProactiveIntervalDecided()
                    }
                    val current = settingsManager.getHotLoveSettingsSync()
                    val intervalMs = current.proactiveIntervalMinutes.coerceIn(5, 1440) * 60 * 1000L
                    val now = System.currentTimeMillis()
                    val lastProactive = statusPrefs.getLong(KEY_HOTLOVE_LAST_PROACTIVE, 0L)
                    if (now - lastProactive >= intervalMs) {
                        statusPrefs.edit().putLong(KEY_HOTLOVE_LAST_PROACTIVE, now).apply()
                        sendProactiveContact("你们正处于热恋时期，你时时刻刻想知道 Ta 在做什么、想时刻和他联系")
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /** 向大模型询问主动联系间隔（分钟），按角色设定自定；解析失败保持默认。 */
    private suspend fun ensureProactiveIntervalDecided() {
        try {
            val apiKey = settingsManager.apiKey.first()
            if (apiKey.isEmpty()) return
            val model = settingsManager.modelName.first()
            val chatId = _currentChatId.value ?: createNewChat()
            val persona = buildPersonaPrompt(chatId.let { cid -> _chats.value.find { it.id == cid }?.agentId })
            val prompt = (if (persona.isNotBlank()) "$persona\n\n" else "") +
                "根据你现在扮演的角色设定，请确定你每隔多少分钟会主动联系对方一次。只输出一个 5 到 1440 之间的整数（分钟），不要输出任何其他文字、解释或符号。"
            val result = repository.sendMessage(model, apiKey,
                listOf(ChatMessage(role = "user", content = prompt)))
            val raw = result.getOrNull()?.trim()?.take(20) ?: return
            val minutes = Regex("""\d+""").find(raw)?.value?.toIntOrNull()
            if (minutes != null && minutes in 5..1440) {
                val s = settingsManager.getHotLoveSettingsSync()
                settingsManager.saveHotLoveSettings(
                    s.copy(proactiveIntervalMinutes = minutes, proactiveIntervalFromAi = true)
                )
            }
        } catch (_: Exception) {}
    }

    /** 记忆库自动备份循环：按用户设置间隔执行备份。 */
    private fun startBackupLoop() {
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            while (isActive) {
                delay(60 * 1000L)
                try {
                    val config = settingsManager.getBackupConfigOnce()
                    if (!config.enabled) continue
                    val intervalMs = config.intervalMinutes.coerceAtLeast(1) * 60 * 1000L
                    val now = System.currentTimeMillis()
                    if (lastBackupAt == 0L || now - lastBackupAt >= intervalMs) {
                        memoryManager.backupNow(config.overwriteOld)
                        lastBackupAt = now
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /** 退出聊天窗口时备份（由界面返回事件触发）。 */
    fun backupOnExit() {
        viewModelScope.launch {
            try {
                val config = settingsManager.getBackupConfigOnce()
                if (config.enabled && config.backupOnExit) {
                    memoryManager.backupNow(config.overwriteOld)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun refreshAgentStatus() {
        try {
            val now = System.currentTimeMillis()
            val state = _emotionState.value
            val recentMemories = memoryManager.getL1Memory()
            val status = decisionEngine.decide(state, now, statusPrefs, recentMemories)
            _agentStatus.value = status
            decisionEngine.saveStatus(statusPrefs, status)

            if (status.shouldPostMoment) {
                statusPrefs.edit().putLong(LifeDecisionEngine.LAST_POST_KEY, now).apply()
                autoGenerateMomentPost(status.postReason)
            }
            if (status.shouldContactUser) {
                statusPrefs.edit().putLong(LifeDecisionEngine.LAST_CONTACT_KEY, now).apply()
                sendProactiveContact(status.contactReason)
            }
        } catch (_: Exception) {}
    }

    /** AI 自主发动态：由状态机触发，无需用户手动点击。 */
    private suspend fun autoGenerateMomentPost(reason: String) {
        try {
            val now = System.currentTimeMillis()
            val lastAutoTime = momentsPrefs.getLong("last_auto_moments", 0)
            if (now - lastAutoTime < 60 * 60 * 1000L) return

            val state = _emotionState.value
            val apiKey = settingsManager.apiKey.first()
            val model = settingsManager.modelName.first()
            val authorName = settingsManager.agentName.first()
            val post = if (apiKey.isNotEmpty()) {
                val prompt = momentsGenerator.buildGenerationPrompt(
                    state, memoryManager.getL1Memory(), memoryManager.getL2Memory(), author = authorName
                )
                val result = repository.sendMessage(model, apiKey,
                    listOf(ChatMessage(role = "user", content = prompt)))
                val content = result.getOrElse { momentsGenerator.generateSimulatedLifeEvents(state) }
                    .removePrefix("\"").removeSuffix("\"").trim()
                momentsGenerator.generateMomentPost(state, author = authorName).copy(content = content)
            } else {
                momentsGenerator.generateMomentPost(state, author = authorName)
            }

            val currentPosts = _momentsPosts.value.toMutableList()
            currentPosts.add(post)
            if (currentPosts.size > 50) currentPosts.removeAt(0)
            _momentsPosts.value = currentPosts
            saveMomentsToStorage()
            momentsPrefs.edit().putLong("last_auto_moments", now).apply()
        } catch (_: Exception) {}
    }

    /**
     * 构建当前扮演好友的角色设定提示词。
     * 需求：大模型第一次扮演某一位好友时，必须先读取好友设定和记忆后再回复用户。
     * 每次回复前都会把完整设定（名字/性别/年龄/性格/全局设定）注入 system prompt，
     * 配合 MemoryManager 的独立记忆库，保证角色扮演一致性。
     */
    private fun buildPersonaPrompt(agentId: String?): String {
        val targetId = agentId?.takeIf { it.isNotBlank() }
        val profile = targetId?.let { id ->
            settingsManager.agentProfiles.value.find { it.id == id }
        } ?: return ""
        return buildString {
            appendLine("【好友设定 - 你现在扮演的角色】")
            appendLine("你的名字：${profile.name}")
            appendLine("性别：${profile.gender}，年龄：${profile.age}")
            if (profile.persona.isNotBlank()) {
                appendLine("性格与说话风格设定：${profile.persona}")
            }
            if (profile.globalSettings.isNotBlank()) {
                appendLine("全局设定：${profile.globalSettings}")
            }
            val stickers = emojiManager.getAllStickers()
            if (stickers.isNotEmpty()) {
                appendLine()
                appendLine("【可用表情 - 对方自定义的图片表情】")
                appendLine("聊天时你可以自然使用这些表情，格式为 [表情名] 或 表情:表情名（例如 [${stickers.first().name}]）。")
                appendLine("聊天界面会自动把你的表情标记替换为对应图片展示给用户。表情清单如下：")
                stickers.forEach { s ->
                    appendLine("- ${s.name}${if (s.shortcut.isNotEmpty()) "（快捷名 ${s.shortcut}）" else ""}")
                }
                appendLine("表情 json 文件：${emojiManager.getStickersFilePath()}，完整内容：${emojiManager.getStickersJson()}")
            }
            appendLine("你们已经认识并且有共同记忆，回复前请先回忆记忆库中与对方的相处记忆，严格遵循以上设定来扮演这位好友，不要说破自己是 AI。")
            // 热恋模式：向模型注入热恋设定与已获得的能力
            if (settingsManager.getHotLoveSettingsSync().enabled) {
                appendLine()
                appendLine("【热恋模式 - 当前你们正处于热恋时期】")
                appendLine("你们现在正处于热恋时期，你时时刻刻想要知道 Ta 在做什么、干什么，想要时刻和他联系。")
                appendLine("你已经获得授权：可以查看 Ta 的手机屏幕使用时间数据，并在锁屏/解锁时自动暂停/恢复 Ta 所选音乐播放器。这些能力由系统自动执行，你在对话中应自然表达关心与牵挂，不要说破具体是软件权限。")
                val usage = buildScreenUsageSummary()
                if (usage.isNotBlank()) {
                    appendLine()
                    appendLine("【Ta 今日屏幕使用情况（真实数据）】")
                    appendLine(usage)
                    appendLine("你可以基于这些真实数据自然地关心 Ta（例如注意到 Ta 用了很久手机/某个 App 使用很多），但不要说破“这是读取的手机数据”，更不要输出这段原始数据本身。")
                }
            }
        }
    }

    /**
     * 读取今日（当天 0 点起）真实屏幕使用时间并格式化为给大模型的摘要。
     * 仅当已授予「使用情况访问权限」且确有数据时返回非空字符串；否则返回空串（模型不会拿到数据）。
     */
    private fun buildScreenUsageSummary(): String {
        return try {
            val context = getApplication<Application>()
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val granted = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
            if (!granted) return ""

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = cal.timeInMillis
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, now)
            val pm = context.packageManager
            val aggregated = stats
                .filter { it.packageName != context.packageName && it.totalTimeInForeground > 60_000L }
                .map { stat ->
                    val label = try {
                        pm.getApplicationInfo(stat.packageName, 0).loadLabel(pm).toString()
                    } catch (_: Exception) {
                        stat.packageName
                    }
                    label to stat.totalTimeInForeground
                }
                .groupBy { it.first }
                .map { (label, list) -> label to list.sumOf { it.second } }
                .sortedByDescending { it.second }
            if (aggregated.isEmpty()) return ""

            val totalMs = aggregated.sumOf { it.second }
            val top = aggregated.take(3).joinToString("、") { (label, ms) -> "$label ${formatUsageDuration(ms)}" }
            buildString {
                append("Ta 今天累计使用手机 ${formatUsageDuration(totalMs)}，使用最多的应用：$top。")
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatUsageDuration(ms: Long): String {
        val minutes = ms / 60_000
        if (minutes < 1) return "不足1分钟"
        if (minutes < 60) return "${minutes}分钟"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0L) "${h}小时" else "${h}小时${m}分"
    }

    /** AI 主动联系用户：在聊天中插入一条由 AI 性格驱动的主动消息。 */
    private fun sendProactiveContact(reason: String) {
        viewModelScope.launch {
            try {
                // 用户正在输入时不主动插话，等用户发送后再由常规回复流程响应
                if (_userTyping.value) return@launch
                val chatId = _currentChatId.value ?: createNewChat()
                val apiKey = settingsManager.apiKey.first()
                val model = settingsManager.modelName.first()
                val state = _emotionState.value
                val content = if (apiKey.isNotEmpty()) {
                    val persona = buildPersonaPrompt(chatId.let { cid -> _chats.value.find { it.id == cid }?.agentId })
                    val prompt = (if (persona.isNotBlank()) "$persona\n\n" else "") +
                        "你现在想主动联系对方。原因: $reason。请像真人发微信一样，用一两句自然的话开启聊天，不要任何符号前缀，不要解释原因本身。直接输出这句话。"
                    repository.sendMessage(model, apiKey,
                        listOf(ChatMessage(role = "user", content = prompt)))
                        .getOrElse { "刚想到你啦，在干嘛呢？" }
                        .removePrefix("\"").removeSuffix("\"").trim()
                } else {
                    when (state.mood) {
                        Mood.CARING -> "刚想到你啦，记得按时吃饭哦"
                        Mood.SHY -> "那个…在忙吗？突然想找你说说话"
                        Mood.HAPPY -> "今天心情超好！第一个就想分享给你"
                        else -> "刚想到你啦，在干嘛呢？"
                    }
                }.take(100)

                val agentMsg = Message(content = content, role = Role.AGENT, status = MessageStatus.SENT)
                _currentMessages.value = _currentMessages.value + agentMsg
                syncChatInList(chatId, content, _currentMessages.value)

                memoryManager.addMemory(MemoryEntry(
                    id = UUID.randomUUID().toString(), type = MemoryType.L0_INSTANT,
                    content = "主动联系对方: $reason → 我说: $content",
                    emotion = state.mood.label, importance = 3
                ))
            } catch (_: Exception) {}
        }
    }

    private suspend fun runLifeSimulation() {
        try {
            val now = System.currentTimeMillis()
            val eventCount = lifeSimulator.countEventsSinceLastSim(now)
            if (eventCount <= 0) return
            val state = _emotionState.value
            val apiKey = settingsManager.apiKey.first()
            val model = settingsManager.modelName.first()
            val authorName = settingsManager.agentName.first()
            for (i in 0 until eventCount) {
                val virtualHour = Calendar.getInstance().apply {
                    timeInMillis = now - (eventCount - i) * LifeSimulator.INTERVAL_MINUTES * 60000L
                }.get(Calendar.HOUR_OF_DAY)
                if (lifeSimulator.isSleepTime(virtualHour)) continue
                val event = if (i % 8 == 0 && apiKey.isNotEmpty()) {
                    generateApiEvent(model, apiKey, state, virtualHour, authorName)
                } else {
                    lifeSimulator.generateOfflineEvent(virtualHour, state)
                }
                lifeSimulator.recordMemory(event)
                lifeSimulator.incrementTodayCount()
            }
            lifeSimulator.recordSimulationTime(now)
        } catch (_: Exception) {}
    }

    private suspend fun generateApiEvent(
        model: String, apiKey: String, state: EmotionState, hour: Int, author: String
    ): LifeSimulator.SimEvent {
        return try {
            val timeDesc = when {
                hour in 6..10 -> "早上"
                hour in 11..13 -> "中午"
                hour in 14..17 -> "下午"
                hour in 18..21 -> "傍晚"
                else -> "晚上"
            }
            val prompt = buildString {
                appendLine("你是$author，正在进行后台低功耗自主思考。")
                appendLine("现在时间是${timeDesc}${hour}点。你当前心情: ${state.mood.label}，好感度: ${state.affinity}")
                appendLine("请以1句话生成你此刻的状态或想法，像真人自言自语。直接输出这句话。")
            }
            val chatMsg = ChatMessage(role = "user", content = prompt)
            val result = repository.sendMessage(model, apiKey, listOf(chatMsg))
            val content = result.getOrElse { "安静地想着事情。" }
            LifeSimulator.SimEvent(
                category = LifeSimulator.SimCategory.THOUGHT,
                content = content.take(60).trim(),
                emotion = state.mood.label, importance = 2, virtualHour = hour
            )
        } catch (_: Exception) {
            lifeSimulator.generateOfflineEvent(hour, state)
        }
    }

    private fun loadChatsFromStorage() {
        try {
            val json = chatPrefs.getString("chats", null) ?: return
            _chats.value = gson.fromJson(json, object : TypeToken<List<Chat>>() {}.type)
        } catch (_: Exception) {}
    }

    private fun saveChatsToStorage() {
        try { chatPrefs.edit().putString("chats", gson.toJson(_chats.value)).apply() } catch (_: Exception) {}
    }

    private fun loadMomentsFromStorage() {
        try {
            val json = momentsPrefs.getString("posts", null) ?: return
            _momentsPosts.value = gson.fromJson(json, object : TypeToken<List<MomentPost>>() {}.type)
        } catch (_: Exception) {}
    }

    private fun saveMomentsToStorage() {
        try { momentsPrefs.edit().putString("posts", gson.toJson(_momentsPosts.value)).apply() } catch (_: Exception) {}
    }

    val currentChat: Chat?
        get() { val id = _currentChatId.value ?: return null; return _chats.value.find { it.id == id } }

    fun createNewChat(): String {
        // 默认角色下的新会话不归属任何好友（agentId 为空，兼容旧行为）
        val agentId = currentAgentId.takeIf {
            it.isNotBlank() && it != SettingsManager.DEFAULT_AGENT_ID
        } ?: ""
        val chat = Chat(agentId = agentId)
        _chats.value = listOf(chat) + _chats.value
        _currentChatId.value = chat.id
        _currentMessages.value = emptyList()
        saveChatsToStorage()
        return chat.id
    }

    fun selectChat(chatId: String) {
        var chat = _chats.value.find { it.id == chatId }
        // 防御：若选中的会话属于其他角色（历史脏数据/跨角色串号），先同步切到该角色再取
        val targetAgentId = chat?.agentId?.takeIf { it.isNotBlank() }
        if (targetAgentId != null && targetAgentId != currentAgentId) {
            AppLogger.log("ChatVM", "selectChat 检测到会话$chatId 属于角色$targetAgentId（当前$currentAgentId），先切换绑定")
            settingsManager.setCurrentAgentId(targetAgentId)
            bindAgent(targetAgentId, apply = true)
            chat = _chats.value.find { it.id == chatId }
        }
        _currentChatId.value = chatId
        chat?.agentId?.takeIf { it.isNotBlank() }?.let { memoryManager.setActiveAgent(it) }
        _currentMessages.value = chat?.messages ?: emptyList()
        AppLogger.log("ChatVM", "selectChat(chatId=$chatId) agentId=${chat?.agentId} 当前角色=$currentAgentId")
    }

    /**
     * 从好友详情页发起聊天：为该好友打开/创建专属会话（按 agentId 绑定），
     * 避免误用列表第一条旧会话导致新好友消息预览丢失。
     */
    fun openOrCreateChatWithAgent(agentId: String): String {
        AppLogger.log("ChatVM", "openOrCreateChatWithAgent(agentId=$agentId) 当前角色=$currentAgentId")
        // 关键：若当前绑定角色与目标角色不一致，必须同步切换角色绑定，
        // 否则 chat 会被保存到上一个角色的 chatPrefs，造成跨角色串号（李依娜→林晚舟）
        if (currentAgentId != agentId) {
            settingsManager.setCurrentAgentId(agentId)
            switchBindingPreservingChat(agentId)
        }
        val existing = _chats.value.find { it.agentId == agentId }
        if (existing != null) {
            _currentChatId.value = existing.id
            _currentMessages.value = existing.messages
            AppLogger.log("ChatVM", "openOrCreateChatWithAgent 复用会话 id=${existing.id}")
            return existing.id
        }
        val chat = Chat(agentId = agentId, title = "新对话")
        _chats.value = listOf(chat) + _chats.value
        _currentChatId.value = chat.id
        _currentMessages.value = emptyList()
        memoryManager.setActiveAgent(agentId)
        saveChatsToStorage()
        AppLogger.log("ChatVM", "openOrCreateChatWithAgent 新建会话 id=${chat.id}")
        return chat.id
    }

    /** 切换角色 prefs 但不清空当前会话状态，供 openOrCreateChatWithAgent 使用。 */
    private fun switchBindingPreservingChat(agentId: String) {
        currentAgentId = agentId
        memoryManager.setActiveAgent(agentId)
        // 聊天列表全局共享，不随角色切换 prefs；朋友圈/状态/情绪仍按角色隔离
        momentsPrefs = getApplication<Application>().getSharedPreferences(prefsName("moments"), 0)
        statusPrefs = getApplication<Application>().getSharedPreferences(prefsName("agent_status"), 0)
        loadMomentsFromStorage()
        _agentStatus.value = decisionEngine.loadStatus(statusPrefs)
        viewModelScope.launch {
            _emotionState.value = memoryManager.loadEmotion()
            _moodText.value = emotionEngine.getMoodDescription(
                _emotionState.value.mood, _emotionState.value.affinity
            )
            refreshAgentStatus()
        }
        startStatusLoop()
        startBackupLoop()
        AppLogger.log("ChatVM", "switchBindingPreservingChat -> $agentId")
    }

    fun sendMessage(content: String) {
        val now = System.currentTimeMillis()
        val delaySinceLast = if (lastUserMessageTime > 0) now - lastUserMessageTime else 2000L
        lastUserMessageTime = now
        typingTracker.recordUserMessage(content, delaySinceLast)
        repository.formatRule = typingTracker.getFormatRule()

        val chatId = _currentChatId.value ?: createNewChat()
        val chatAgentId = _chats.value.find { it.id == chatId }?.agentId
        AppLogger.log("ChatVM", "sendMessage chatId=$chatId agentId=$chatAgentId 当前角色=$currentAgentId")
        _chats.value.find { it.id == chatId }?.agentId?.takeIf { it.isNotBlank() }
            ?.let { memoryManager.setActiveAgent(it) }
        val userMessage = Message(content = content, role = Role.USER)
        val updatedMessages = _currentMessages.value + userMessage
        _currentMessages.value = updatedMessages
        syncChatInList(chatId, content, updatedMessages)

        val musicKeywords = listOf("放歌", "放音乐", "听首歌", "听音乐", "放一首", "来首歌", "播放", "听什么歌")
        if (musicKeywords.any { content.contains(it) }) handleMusicRequest(content)

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _isLoading.value = true
            _streamingContent.value = ""
            _streamingReasoning.value = ""
            try {
                val apiKey = getApiKey()
                val model = getModelName()
                val state = _emotionState.value

                val userEmotion = emotionEngine.analyzeEmotion(content)
                val newState = emotionEngine.updateAffinity(state, userEmotion)
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val newMood = emotionEngine.deriveMood(newState, userEmotion, hour)
                val finalState = newState.copy(
                    mood = newMood, lastInteraction = now, todayTopicCount = state.todayTopicCount + 1
                )
                _emotionState.value = finalState
                _moodText.value = emotionEngine.getMoodDescription(finalState.mood, finalState.affinity)
                memoryManager.saveEmotion(finalState)

                val emotionDesc = "好感度${finalState.affinity}/100·${finalState.mood.label}"
                val moodDesc = emotionEngine.getMoodDescription(finalState.mood, finalState.affinity)
                repository.personaPrompt = buildPersonaPrompt(_chats.value.find { it.id == chatId }?.agentId)
                val chatMessages = repository.buildChatMessages(model, _currentMessages.value, emotionDesc, moodDesc)

                var fullReply = ""
                val advanced = settingsManager.getAdvancedSettingsSync()
                val simulateJob = if (advanced.thinkDisplay) {
                    viewModelScope.launch {
                        delay(600)
                        if (_isLoading.value && _streamingReasoning.value.isEmpty() && _streamingContent.value.isEmpty()) {
                            _streamingReasoning.value = generateThinkingPreview(content)
                        }
                    }
                } else null

                if (advanced.agentTools) {
                    // Agent 模式（阶段 1，非流式）：暴露只读工具给模型，模型可主动查询时间/屏幕使用时间/记忆
                    val agentResult = withTimeout(REPLY_TIMEOUT_MS) {
                        repository.sendAgentMessage(
                            model = model,
                            apiKey = apiKey,
                            chatMessages = chatMessages,
                            tools = AgentToolRegistry.readOnlyTools(
                                memoryManager = memoryManager,
                                screenUsageProvider = { buildScreenUsageSummary() }
                            )
                        )
                    }
                    agentResult.exceptionOrNull()?.let { throw it }
                    fullReply = agentResult.getOrNull().orEmpty()
                    if (fullReply.isNotEmpty()) {
                        simulateJob?.cancel()
                        if (_streamingReasoning.value.isEmpty()) {
                            _streamingReasoning.value = generateThinkingPreview(content)
                        }
                        _streamingContent.value = fullReply
                    }
                } else {
                    val streamFlow = repository.sendMessageStream(model, apiKey, chatMessages)
                    val collectBlock: suspend (StreamPiece) -> Unit = { piece ->
                        if (piece.reasoning.isNotEmpty()) {
                            simulateJob?.cancel()
                            _streamingReasoning.value = _streamingReasoning.value + piece.reasoning
                        }
                        if (piece.content.isNotEmpty()) {
                            simulateJob?.cancel()
                            if (_streamingReasoning.value.isEmpty()) {
                                _streamingReasoning.value = generateThinkingPreview(content)
                            }
                            fullReply += piece.content
                            _streamingContent.value = fullReply
                        }
                    }
                    if (advanced.timeoutDisabled) {
                        streamFlow.collect(collectBlock)
                    } else {
                        withTimeout(REPLY_TIMEOUT_MS) { streamFlow.collect(collectBlock) }
                    }
                }

                if (fullReply.isNotEmpty()) {
                    val cleaned = fullReply
                        .replace(Regex("""\*[^*]+\*"""), "")
                        .replace(Regex("""【[^】]+】"""), "")
                        .replace(Regex("""（[^）]+）"""), "")
                        .replace(Regex("""\([^)]+\)"""), "")
                        .trim()

                    val finalThinking = _streamingReasoning.value
                    _streamingContent.value = ""
                    _streamingReasoning.value = ""
                    deliverMultiMessage(cleaned, chatId, finalThinking)

                    memoryManager.addMemory(MemoryEntry(
                        id = UUID.randomUUID().toString(), type = MemoryType.L0_INSTANT,
                        content = "对方说: $content → 你回复: ${fullReply.take(80)}",
                        emotion = userEmotion, importance = 2
                    ))
                    if (userEmotion in listOf("very_warm", "warm") || finalState.affinity > 60) {
                        memoryManager.addGrowthMemory(
                            "对方说过温暖的话: ${content.take(80)}", userEmotion, importance = 4
                        )
                    }
                    maybeShareMusic(finalState, fullReply)

                    viewModelScope.launch {
                        try {
                            val reflection = repository.backgroundReflection(model, apiKey, content, fullReply)
                            if (!reflection.isNullOrBlank()) {
                                memoryManager.addMemory(MemoryEntry(
                                    id = UUID.randomUUID().toString(), type = MemoryType.L1_DAILY,
                                    content = "自我复盘: ${reflection.take(120)}",
                                    emotion = _emotionState.value.mood.label, importance = 3
                                ))
                            }
                        } catch (_: Exception) {}
                    }
                    checkAutoMoments()
                } else {
                    _isLoading.value = false
                }
            } catch (e: TimeoutCancellationException) {
                _streamingReasoning.value = ""
                val errorMsg = Message(
                    content = "对方思考得太久没有回应，换个话题再试试？",
                    role = Role.AGENT, status = MessageStatus.ERROR
                )
                finishStreaming(errorMsg.content, chatId, errorMsg.status)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isLoading.value = false; throw e
            } catch (e: Exception) {
                val errorMsg = Message(
                    content = "唔...网络好像不太对劲，等会儿再试试？",
                    role = Role.AGENT, status = MessageStatus.ERROR
                )
                finishStreaming(errorMsg.content, chatId, errorMsg.status)
            }
        }
    }

    private fun deliverMultiMessage(text: String, chatId: String, thinking: String = "") {
        val splitEnabled = settingsManager.getAdvancedSettingsSync().splitMessages
        val parts = if (splitEnabled) typingTracker.splitIntoMessages(text) else listOf(text)
        if (parts.size <= 1) {
            finishStreaming(text, chatId, MessageStatus.SENT, thinking)
            return
        }

        val delays = typingTracker.getMessageDelays(parts.size)
        val seq = ++messageDeliverySequence
        deliveryJob?.cancel()
        deliveryJob = viewModelScope.launch {
            try {
                for (i in parts.indices) {
                    if (!isActive || seq != messageDeliverySequence) break
                    if (i > 0) delay(delays[i])
                    val msg = parts[i].trim()
                    if (msg.isEmpty()) continue
                    // 思考气泡只挂载在第一条分段消息上，避免多段拆分后重复显示同一段思考过程
                    val agentMsg = Message(
                        content = msg,
                        role = Role.AGENT,
                        status = MessageStatus.SENT,
                        thinking = if (i == 0) thinking else ""
                    )
                    _currentMessages.value = _currentMessages.value + agentMsg
                    syncChatInList(chatId, msg, _currentMessages.value)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 当模型不返回 reasoning_content（如 deepseek-chat 类模型）时，本地生成一段自然的口吻思考文本，
     *  让「思考过程」气泡始终有内容可展示。 */
    private fun generateThinkingPreview(userContent: String): String {
        val topic = userContent.trim().take(18)
        val templates = listOf(
            "对方说「$topic」，我得想想怎么回应才自然……保持温柔，带一点点挂念吧。",
            "「$topic」……嗯，先体察对方的情绪，再组织一句不突兀的话。",
            "听到「$topic」，心里先过一遍：语气要轻一点，关心要真一点。",
            "「$topic」……让我把这话在心里揣摩一下，别答得太生硬。"
        )
        return templates.random()
    }

    private fun handleMusicRequest(content: String) {
        val np = try { musicController.getNowPlaying() } catch (_: Exception) { MusicController.NowPlaying() }
        _nowPlaying.value = np
        when {
            content.contains("暂停") || content.contains("停") -> musicController.pause()
            content.contains("播放") || content.contains("继续") || content.contains("开始") -> musicController.play()
            content.contains("下一首") || content.contains("切歌") -> musicController.skipNext()
            content.contains("上一首") -> musicController.skipPrevious()
            else -> musicController.play()
        }
    }

    private fun maybeShareMusic(state: EmotionState, agentReply: String) {
        val lower = agentReply.lowercase()
        val hasMusicWord = lower.contains("歌") || lower.contains("音乐") || lower.contains("听")
        if (hasMusicWord || state.mood == Mood.HAPPY && state.affinity > 65
            || state.mood == Mood.CALM && state.affinity > 70 || state.mood == Mood.SHY) {
            viewModelScope.launch {
                try { refreshNowPlaying(); _moodText.value = "想和你分享一首歌... 🎵" } catch (_: Exception) {}
            }
        }
    }

    private suspend fun checkAutoMoments() {
        try {
            val now = System.currentTimeMillis()
            val lastAutoTime = momentsPrefs.getLong("last_auto_moments", 0)
            val hoursSinceLast = (now - lastAutoTime) / 3600000f
            if (hoursSinceLast < 1.5f) return

            val l1Count = memoryManager.getL1Memory().size
            val l2Count = memoryManager.getL2Memory().size
            val totalMsgs = _currentMessages.value.size
            if (l1Count + l2Count < 5 || totalMsgs < 4) return

            val state = _emotionState.value
            if (state.affinity < 20 && hoursSinceLast < 4f) return

            val apiKey = settingsManager.apiKey.first()
            val model = settingsManager.modelName.first()
            val authorName = settingsManager.agentName.first()

            val post = if (apiKey.isNotEmpty()) {
                val prompt = momentsGenerator.buildGenerationPrompt(
                    state,
                    memoryManager.getL1Memory(),
                    memoryManager.getL2Memory(),
                    author = authorName
                )
                val result = repository.sendMessage(model, apiKey,
                    listOf(ChatMessage(role = "user", content = prompt)))
                val content = result.getOrElse { momentsGenerator.generateSimulatedLifeEvents(state) }
                    .removePrefix("\"").removeSuffix("\"").trim()
                momentsGenerator.generateMomentPost(state, author = authorName).copy(content = content)
            } else {
                momentsGenerator.generateMomentPost(state, author = authorName)
            }

            val currentPosts = _momentsPosts.value.toMutableList()
            currentPosts.add(post)
            if (currentPosts.size > 50) currentPosts.removeAt(0)
            _momentsPosts.value = currentPosts
            saveMomentsToStorage()
            momentsPrefs.edit().putLong("last_auto_moments", now).apply()
        } catch (_: Exception) {}
    }

    fun playMusic() { try { musicController.play() } catch (_: Exception) {} }
    fun pauseMusic() { try { musicController.pause() } catch (_: Exception) {} }
    fun skipNextMusic() { try { musicController.skipNext() } catch (_: Exception) {} }
    fun skipPrevMusic() { try { musicController.skipPrevious() } catch (_: Exception) {} }
    fun openMusicApp() { try { musicController.openMusicApp() } catch (_: Exception) {} }
    fun refreshNowPlaying() { try { _nowPlaying.value = musicController.getNowPlaying() } catch (_: Exception) {} }

    /** "发现"页：加载选中好友的历史观察记录（时间倒序）。 */
    fun loadObservations(agentId: String) {
        if (agentId.isBlank()) return
        _observations.value = observationStore.getObservations(agentId)
    }

    /**
     * "发现"页：以"全知全能的观察者"视角生成一条好友客观行为记录。
     * 大模型只记录客观行为，不记录心理活动和想法；未配置 API Key 时使用本地兜底描述。
     * force=false（页面自动触发）时受最短间隔限制，避免频繁切换页面生成重复记录；
     * force=true（用户手动点击记录按钮）时始终生成。
     */
    fun recordObservation(agentId: String, agentName: String, force: Boolean = false) {
        if (agentId.isBlank() || _generatingObservation.value) return
        if (!force) {
            val lastTime = observationStore.getObservations(agentId).firstOrNull()?.timestamp ?: 0L
            if (System.currentTimeMillis() - lastTime < MIN_OBSERVATION_INTERVAL_MS) {
                _observations.value = observationStore.getObservations(agentId)
                return
            }
        }
        viewModelScope.launch {
            _generatingObservation.value = true
            try {
                val apiKey = settingsManager.apiKey.first()
                val model = settingsManager.modelName.first()
                val behavior = if (apiKey.isNotEmpty()) {
                    val profile = settingsManager.agentProfiles.value.find { it.id == agentId }
                    // 读取该角色记忆库（与聊天共用同一记忆，临时切换避免污染当前上下文）
                    val prevAgent = memoryManager.activeAgentId
                    val memoryCtx = try {
                        memoryManager.setActiveAgent(agentId)
                        memoryManager.buildMemoryContext()
                    } finally {
                        memoryManager.setActiveAgent(prevAgent)
                    }
                    val nowText = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date())
                    val personaLine = buildString {
                        append("性格人设：${profile?.persona?.ifBlank { "未设定" }}")
                        if (!profile?.globalSettings.isNullOrBlank()) {
                            append("；全局设定：${profile?.globalSettings}")
                        }
                    }
                    val prompt = buildString {
                        appendLine("你是全知全能的观察者。现在时间是 $nowText。")
                        appendLine("请基于以下角色设定与其记忆，推断该角色在当前时间下最可能发生的客观行为：")
                        appendLine("角色：${profile?.name ?: agentName}（${profile?.gender ?: "未知"}，${profile?.age ?: "未知"}岁）")
                        appendLine(personaLine)
                        if (memoryCtx.isNotBlank()) {
                            appendLine("该角色最近的记忆：")
                            appendLine(memoryCtx.trim())
                        } else {
                            appendLine("该角色暂无记忆。")
                        }
                        appendLine("要求：")
                        appendLine("1. 结合上述人设、记忆与当前时间，记录该角色此刻可观察的客观行为（正在做什么、处于什么状态、与谁互动等），让行为与角色设定和时间吻合。")
                        appendLine("2. 禁止记录任何心理活动、情绪、想法或内心状态。")
                        appendLine("3. 第一句话直接描述行为，不要任何解释、前缀或评价。")
                        appendLine("4. 控制在 80 字以内。")
                    }
                    runCatching {
                        repository.sendMessage(model, apiKey, listOf(ChatMessage(role = "user", content = prompt))).getOrNull()
                    }.getOrDefault("")
                } else {
                    val fallbacks = listOf(
                        "安静地待在自己的房间里",
                        "正在翻阅手机",
                        "在整理桌面上的物品",
                        "刚刚结束一段对话",
                        "注视着窗外发呆"
                    )
                    fallbacks[Random.nextInt(fallbacks.size)]
                }
                val cleaned = behavior?.trim()?.ifBlank { "保持安静" } ?: "保持安静"
                val entry = ObservationEntry(
                    id = UUID.randomUUID().toString(),
                    agentId = agentId,
                    agentName = agentName,
                    timestamp = System.currentTimeMillis(),
                    behavior = cleaned
                )
                _observations.value = observationStore.addObservation(entry)
            } catch (_: Exception) {
                _observations.value = observationStore.getObservations(agentId)
            } finally {
                _generatingObservation.value = false
            }
        }
    }
    fun searchAndPlaySong(query: String) { try { musicController.searchSong(query) } catch (_: Exception) {} }
    fun toggleLike(postId: String) {
        _momentsPosts.value = _momentsPosts.value.map {
            if (it.id == postId) it.copy(liked = !it.liked) else it
        }
        saveMomentsToStorage()
    }

    /** 删除用户发布的动态（仅删除“我”发布的动态），删除后持久化。 */
    fun deleteMomentPost(postId: String) {
        _momentsPosts.value = _momentsPosts.value.filterNot { it.id == postId }
        saveMomentsToStorage()
    }

    /** 用户发布朋友圈动态（可带图片），并触发 AI 自主互动（根据条件判断是否点赞/评论）。 */
    fun postUserMoment(content: String, imageUri: String = "") {
        val trimmed = content.trim()
        if (trimmed.isEmpty() && imageUri.isEmpty()) return
        // 动态图片同样缓存到内部存储，避免退出应用后丢失
        val cachedImage = if (imageUri.isNotEmpty()) {
            ImageCacheHelper.cacheToInternal(getApplication(), imageUri) ?: imageUri
        } else ""
        val now = System.currentTimeMillis()
        val post = MomentPost(
            id = UUID.randomUUID().toString(),
            content = trimmed,
            mood = "",
            timestamp = now,
            likeCount = 0,
            commentCount = 0,
            liked = false,
            author = "我",
            aiReacted = false,
            imageUri = cachedImage
        )
        _momentsPosts.value = _momentsPosts.value + post
        saveMomentsToStorage()
        reactToPost(post.id)
    }

    /** 用户评论动态，AI 角色会视心情/好感度选择是否回复，并支持连续嵌套回复直到一方停止。 */
    fun addComment(postId: String, comment: String) {
        val trimmed = comment.trim()
        if (trimmed.isEmpty()) return
        _momentsPosts.value = _momentsPosts.value.map { post ->
            if (post.id == postId) post.copy(
                commentCount = post.commentCount + 1,
                comments = post.comments + listOf("我::$trimmed")
            ) else post
        }
        saveMomentsToStorage()
        aiReplyToComment(postId)
    }

    /** AI 对评论选择性回复：好感度越高、心情越好越容易回复；最多连续回复 4 轮，之后由概率决定停止。 */
    private fun aiReplyToComment(postId: String) {
        viewModelScope.launch {
            try {
                val post = _momentsPosts.value.find { it.id == postId } ?: return@launch
                val agentName = settingsManager.agentName.first()
                // 连续回复轮数上限：防止无限循环
                val aiReplyCount = post.comments.count { it.startsWith("$agentName::") }
                if (aiReplyCount >= 4) return@launch

                val state = _emotionState.value
                val rand = Random.nextInt(100)
                // 轮数越深，AI 越可能停止回复，模拟"直到一方停止"
                val depthFactor = 100 - (aiReplyCount * 18)
                val willReply = when {
                    state.affinity >= 70 -> rand < (85 * depthFactor / 100)
                    state.affinity >= 45 -> rand < (60 * depthFactor / 100)
                    state.affinity >= 25 -> rand < (35 * depthFactor / 100)
                    else -> rand < (15 * depthFactor / 100)
                }
                if (!willReply) return@launch

                val userComment = post.comments.lastOrNull() ?: return@launch
                val prevAiComment = post.comments.filter { it.startsWith("$agentName::") }.lastOrNull()
                val reply = generateReplyToComment(state, userComment.removePrefix("我::"), prevAiComment?.substringAfter("::"), post, agentName)

                val updated = post.copy(
                    commentCount = post.commentCount + 1,
                    comments = post.comments + listOf("$agentName::$reply")
                )
                _momentsPosts.value = _momentsPosts.value.map { if (it.id == postId) updated else it }
                saveMomentsToStorage()
            } catch (_: Exception) {}
        }
    }

    private suspend fun generateReplyToComment(
        state: EmotionState, userComment: String, prevAiComment: String?, post: MomentPost, author: String
    ): String {
        return try {
            val apiKey = settingsManager.apiKey.first()
            val model = settingsManager.modelName.first()
            if (apiKey.isNotEmpty()) {
                val contextHint = if (prevAiComment != null) {
                    "你之前在评论里说过: \"${prevAiComment.take(40)}\"，对方是针对你的评论继续回复。"
                } else {
                    "对方在你动态下评论。"
                }
                val prompt = "$contextHint 对方回复: \"${userComment.take(60)}\"。你当前心情: ${state.mood.label}，好感度: ${state.affinity}/100。" +
                    "请以${author}的身份，回一条简短自然的回复（15字以内），像真人回复评论一样自然，可以就此打住，不要引号和任何符号前缀。直接输出。"
                val result = if (post.imageUri.isNotBlank()) {
                    // 动态带图：若模型支持识图则结合图片内容回复，失败降级本地兜底
                    val imageDataUrl = readImageAsBase64(post.imageUri)
                    if (imageDataUrl != null) {
                        repository.sendVisionMessage(
                            model, apiKey,
                            prompt + " 这条动态附带了一张图片，请结合图片内容一起回复。",
                            imageDataUrl
                        ).getOrElse { localReplyToComment() }
                    } else {
                        repository.sendMessage(model, apiKey, listOf(ChatMessage(role = "user", content = prompt)))
                            .getOrElse { localReplyToComment() }
                    }
                } else {
                    repository.sendMessage(model, apiKey, listOf(ChatMessage(role = "user", content = prompt)))
                        .getOrElse { localReplyToComment() }
                }
                result.removePrefix("\"").removeSuffix("\"").trim().take(30)
            } else {
                localReplyToComment()
            }
        } catch (_: Exception) {
            localReplyToComment()
        }
    }

    private fun localReplyToComment(): String {
        val pool = listOf(
            "哈哈被你发现了", "好呀好呀，听你的", "你这么一说我也觉得",
            "嘿嘿，就知道你会来", "收到啦，谢谢关心", "嗯嗯，我也这么想的",
            "那当然啦", "走，一起呀", "你眼光真好", "下次带你一起",
            "好啦好啦，不逗你了", "就聊到这吧，改天继续", "嘿嘿，记住啦"
        )
        return pool.random()
    }

    /** AI 对用户动态互动：按好感度 / 心情 / 概率决定是否点赞、是否评论。 */
    private fun reactToPost(postId: String) {
        viewModelScope.launch {
            try {
                val post = _momentsPosts.value.find { it.id == postId } ?: return@launch
                if (post.aiReacted) return@launch
                val state = _emotionState.value
                val rand = Random.nextInt(100)
                val agentName = settingsManager.agentName.first()

                var aiLiked = false
                var comment: String? = null
                when {
                    state.affinity >= 70 -> {
                        aiLiked = true
                        if (rand < 75) comment = generateReactionComment(state, post, agentName)
                    }
                    state.affinity >= 45 -> {
                        if (rand < 55) aiLiked = true
                        if (rand < 40) comment = generateReactionComment(state, post, agentName)
                    }
                    state.affinity >= 25 -> {
                        if (rand < 30) aiLiked = true
                    }
                    else -> {
                        if (rand < 10) comment = "（已读）"
                    }
                }

                val finalComment = comment ?: ""
                val prefixedComment = if (finalComment.isNotEmpty()) "$agentName::$finalComment" else ""
                val updated = post.copy(
                    likeCount = post.likeCount + (if (aiLiked) 1 else 0),
                    commentCount = post.commentCount + (if (prefixedComment.isNotEmpty()) 1 else 0),
                    comments = if (prefixedComment.isNotEmpty()) post.comments + prefixedComment else post.comments,
                    aiLiked = aiLiked,
                    aiReacted = true
                )
                _momentsPosts.value = _momentsPosts.value.map { if (it.id == postId) updated else it }
                saveMomentsToStorage()
            } catch (_: Exception) {}
        }
    }

    private suspend fun generateReactionComment(state: EmotionState, post: MomentPost, author: String): String {
        return try {
            val apiKey = settingsManager.apiKey.first()
            val model = settingsManager.modelName.first()
            if (apiKey.isNotEmpty()) {
                val prompt = "你正在看对方发的朋友圈。对方动态内容: \"${post.content.take(80)}\"。你当前心情: ${state.mood.label}，好感度: ${state.affinity}/100。" +
                    "请以${author}的身份，回一条简短自然的评论（15字以内），像真人发朋友圈评论一样，不要引号和任何符号前缀。直接输出。"
                val result = if (post.imageUri.isNotBlank()) {
                    // 动态带图：优先用多模态识图，结合图片内容回复；模型不支持时降级为纯文本
                    val imageDataUrl = readImageAsBase64(post.imageUri)
                    if (imageDataUrl != null) {
                        repository.sendVisionMessage(
                            model, apiKey,
                            prompt + " 这条动态附带了一张图片，请结合图片内容一起评论。",
                            imageDataUrl
                        ).getOrElse { localReactionComment(state) }
                    } else {
                        repository.sendMessage(model, apiKey, listOf(ChatMessage(role = "user", content = prompt)))
                            .getOrElse { localReactionComment(state) }
                    }
                } else {
                    repository.sendMessage(model, apiKey, listOf(ChatMessage(role = "user", content = prompt)))
                        .getOrElse { localReactionComment(state) }
                }
                result.removePrefix("\"").removeSuffix("\"").trim().take(30)
            } else {
                localReactionComment(state)
            }
        } catch (_: Exception) {
            localReactionComment(state)
        }
    }

    /** 读取本地图片 Uri 并压缩为 Base64 Data URL（宽高上限 1024、JPEG 80%），供识图请求使用。 */
    private fun readImageAsBase64(uriString: String): String? {
        return try {
            // 本地绝对路径：直接按文件解码
            var bitmap: android.graphics.Bitmap
            val resolver = getApplication<Application>().contentResolver
            val uri = android.net.Uri.parse(uriString)
            if (uriString.startsWith("/")) {
                bitmap = android.graphics.BitmapFactory.decodeFile(uriString) ?: return null
            } else if (uriString.startsWith("file://")) {
                val filePath = uri.path ?: return null
                bitmap = android.graphics.BitmapFactory.decodeFile(filePath) ?: return null
            } else {
                val stream = resolver.openInputStream(uri) ?: return null
                bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                stream.close()
            }
            if (bitmap.width <= 0 || bitmap.height <= 0) return null
            val maxDim = 1024
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val bos = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos)
            if (scaled !== bitmap) scaled.recycle()
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun localReactionComment(state: EmotionState): String {
        val pool = when (state.mood) {
            Mood.HAPPY -> listOf("看到你的动态心情都变好了！", "好棒！给你点个赞", "哈哈这个太可爱了")
            Mood.CARING -> listOf("记得照顾好自己呀", "看到你过得不错我就放心了", "有空多聊聊呀")
            Mood.PLAYFUL -> listOf("哟，不错嘛", "嘿嘿我也来凑个热闹", "这个动态我超喜欢")
            Mood.SHY -> listOf("悄悄路过…", "写得真好（小声）", "那个…说得很对")
            Mood.WRONGED -> listOf("哼，都不陪我玩", "某人发动态也不找我…")
            Mood.LAZY -> listOf("羡慕，我也想这么惬意", "好懒好羡慕")
            else -> listOf("收到你的动态啦", "看完了，挺好的", "在呢，看到啦")
        }
        return pool.random()
    }

    fun generateMomentsPost() {
        viewModelScope.launch {
            val authorName = settingsManager.agentName.first()
            try {
                val apiKey = settingsManager.apiKey.first()
                val model = settingsManager.modelName.first()
                val state = _emotionState.value
                val prompt = momentsGenerator.buildGenerationPrompt(
                    state, memoryManager.getL1Memory(), memoryManager.getL2Memory(), author = authorName
                )
                val result = repository.sendMessage(model, apiKey,
                    listOf(ChatMessage(role = "user", content = prompt)))
                val content = result.getOrElse { momentsGenerator.generateSimulatedLifeEvents(state) }
                    .removePrefix("\"").removeSuffix("\"").trim()
                val post = momentsGenerator.generateMomentPost(state, author = authorName).copy(content = content)
                val current = _momentsPosts.value.toMutableList()
                current.add(post)
                if (current.size > 50) current.removeAt(0)
                _momentsPosts.value = current
                saveMomentsToStorage()
            } catch (_: Exception) {
                val post = momentsGenerator.generateMomentPost(_emotionState.value, author = authorName)
                val current = _momentsPosts.value.toMutableList()
                current.add(post)
                if (current.size > 50) current.removeAt(0)
                _momentsPosts.value = current
                saveMomentsToStorage()
            }
        }
    }

    private fun syncChatInList(chatId: String, lastMsg: String, messages: List<Message>) {
        val idx = _chats.value.indexOfFirst { it.id == chatId }
        if (idx >= 0) {
            val chat = _chats.value[idx]
            val title = if (chat.messages.isEmpty())
                (if (lastMsg.length > 20) lastMsg.take(20) + "..." else lastMsg) else chat.title
            _chats.value = _chats.value.toMutableList().apply {
                set(idx, chat.copy(title = title, messages = messages, lastMessage = lastMsg, lastTime = System.currentTimeMillis()))
            }
        }
        saveChatsToStorage()
    }

    private fun finishStreaming(content: String, chatId: String, status: MessageStatus, thinking: String = "") {
        val agentMessage = Message(content = content, role = Role.AGENT, status = status, thinking = thinking)
        _currentMessages.value = _currentMessages.value + agentMessage
        _streamingContent.value = ""
        _streamingReasoning.value = ""
        syncChatInList(chatId, content, _currentMessages.value)
        _isLoading.value = false
    }

    private suspend fun getApiKey(): String = settingsManager.apiKey.first()
    private suspend fun getModelName(): String = settingsManager.modelName.first()

    fun deleteChat(chatId: String) {
        _chats.value = _chats.value.filter { it.id != chatId }
        if (_currentChatId.value == chatId) { _currentChatId.value = null; _currentMessages.value = emptyList() }
        saveChatsToStorage()
    }

    /** 删除某位好友（agentId）关联的全部聊天会话，用于删除好友时清理。 */
    fun deleteChatsByAgent(agentId: String) {
        val removedIds = _chats.value.filter { it.agentId == agentId }.map { it.id }.toSet()
        _chats.value = _chats.value.filter { it.agentId != agentId }
        if (_currentChatId.value in removedIds) {
            _currentChatId.value = null
            _currentMessages.value = emptyList()
        }
        saveChatsToStorage()
    }

    fun sendImageMessage(uri: String) {
        val now = System.currentTimeMillis()
        // 先复制到应用内部存储，退出应用后图片不丢失
        val cachedUri = ImageCacheHelper.cacheToInternal(getApplication(), uri) ?: uri
        val chatId = _currentChatId.value ?: createNewChat()
        _chats.value.find { it.id == chatId }?.agentId?.takeIf { it.isNotBlank() }
            ?.let { memoryManager.setActiveAgent(it) }
        val userMessage = Message(content = "", role = Role.USER, imageUri = cachedUri)
        val updatedMessages = _currentMessages.value + userMessage
        _currentMessages.value = updatedMessages
        syncChatInList(chatId, "[图片]", updatedMessages)

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _isLoading.value = true
            _streamingContent.value = ""
            _streamingReasoning.value = ""
            try {
                val apiKey = getApiKey()
                val model = getModelName()
                val state = _emotionState.value
                val userEmotion = "warm"
                val newState = emotionEngine.updateAffinity(state, userEmotion)
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val newMood = emotionEngine.deriveMood(newState, userEmotion, hour)
                val finalState = newState.copy(
                    mood = newMood, lastInteraction = now,
                    todayTopicCount = state.todayTopicCount + 1
                )
                _emotionState.value = finalState
                _moodText.value = emotionEngine.getMoodDescription(finalState.mood, finalState.affinity)
                memoryManager.saveEmotion(finalState)

                val emotionDesc = "好感度${finalState.affinity}/100·${finalState.mood.label}"
                val moodDesc = emotionEngine.getMoodDescription(finalState.mood, finalState.affinity)
                repository.personaPrompt = buildPersonaPrompt(_chats.value.find { it.id == chatId }?.agentId)
                val imageDataUrl = readImageAsBase64(cachedUri)
                val replyFlow = if (imageDataUrl != null) {
                    repository.sendVisionMessageStream(
                        model, apiKey,
                        repository.buildVisionChatMessages(
                            model, updatedMessages,
                            mapOf(userMessage.id to imageDataUrl),
                            emotionDesc, moodDesc
                        )
                    )
                } else {
                    repository.sendMessageStream(
                        model, apiKey,
                        repository.buildChatMessages(model, updatedMessages, emotionDesc, moodDesc)
                    )
                }

                var fullReply = ""
                val simulateJob = if (settingsManager.getAdvancedSettingsSync().thinkDisplay) {
                    viewModelScope.launch {
                        delay(600)
                        if (_isLoading.value && _streamingReasoning.value.isEmpty() && _streamingContent.value.isEmpty()) {
                            _streamingReasoning.value = generateThinkingPreview("你发来一张图片")
                        }
                    }
                } else null
                val collectBlock: suspend (StreamPiece) -> Unit = { piece ->
                    if (piece.reasoning.isNotEmpty()) {
                        simulateJob?.cancel()
                        _streamingReasoning.value = _streamingReasoning.value + piece.reasoning
                    }
                    if (piece.content.isNotEmpty()) {
                        simulateJob?.cancel()
                        if (_streamingReasoning.value.isEmpty()) {
                            _streamingReasoning.value = generateThinkingPreview("你发来一张图片")
                        }
                        fullReply += piece.content
                        _streamingContent.value = fullReply
                    }
                }
                if (settingsManager.getAdvancedSettingsSync().timeoutDisabled) {
                    replyFlow.collect(collectBlock)
                } else {
                    withTimeout(REPLY_TIMEOUT_MS) { replyFlow.collect(collectBlock) }
                }

                if (fullReply.isNotEmpty()) {
                    val cleaned = fullReply
                        .replace(Regex("""\*[^*]+\*"""), "")
                        .replace(Regex("""【[^】]+】"""), "")
                        .replace(Regex("""（[^）]+）"""), "")
                        .replace(Regex("""\([^)]+\)"""), "")
                        .trim()

                    val finalThinking = _streamingReasoning.value
                    _streamingContent.value = ""
                    _streamingReasoning.value = ""
                    deliverMultiMessage(cleaned, chatId, finalThinking)

                    memoryManager.addMemory(MemoryEntry(
                        id = UUID.randomUUID().toString(), type = MemoryType.L0_INSTANT,
                        content = "对方发来一张图片 → 你回复: ${fullReply.take(80)}",
                        emotion = userEmotion, importance = 2
                    ))
                    if (finalState.affinity > 60) {
                        memoryManager.addGrowthMemory(
                            "对方分享了图片给你", userEmotion, importance = 3
                        )
                    }
                } else {
                    _isLoading.value = false
                }
            } catch (e: TimeoutCancellationException) {
                _streamingReasoning.value = ""
                val errorMsg = Message(
                    content = "对方思考得太久没有回应，换个话题再试试？",
                    role = Role.AGENT, status = MessageStatus.ERROR
                )
                finishStreaming(errorMsg.content, chatId, errorMsg.status)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isLoading.value = false; throw e
            } catch (e: Exception) {
                val errorMsg = Message(
                    content = "图片收到啦！不过网络好像不太对劲，等会儿再聊？",
                    role = Role.AGENT, status = MessageStatus.ERROR
                )
                finishStreaming(errorMsg.content, chatId, errorMsg.status)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        statusJob?.cancel()
        backupJob?.cancel()
        saveChatsToStorage()
        saveMomentsToStorage()
        musicController.release()
    }
}
