package com.wechat.agent.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wechat.agent.data.EmotionEngine
import com.wechat.agent.data.LifeSimulator
import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.MomentsGenerator
import com.wechat.agent.data.LifeDecisionEngine
import com.wechat.agent.data.MusicController
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.util.Calendar
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
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
    private val gson = Gson()
    private val chatPrefs: SharedPreferences = application.getSharedPreferences("chat_sessions", 0)
    private val momentsPrefs: SharedPreferences = application.getSharedPreferences("moments", 0)
    private val statusPrefs: SharedPreferences = application.getSharedPreferences("agent_status", 0)

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

    private val _emotionState = MutableStateFlow(EmotionState())
    val emotionState = _emotionState.asStateFlow()

    private val _moodText = MutableStateFlow("")
    val moodText = _moodText.asStateFlow()

    private val _nowPlaying = MutableStateFlow(MusicController.NowPlaying())
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _momentsPosts = MutableStateFlow<List<MomentPost>>(emptyList())
    val momentsPosts = _momentsPosts.asStateFlow()

    private val _agentStatus = MutableStateFlow(AgentStatus())
    val agentStatus = _agentStatus.asStateFlow()

    private var streamingJob: Job? = null
    private var deliveryJob: Job? = null
    private var statusJob: Job? = null
    private var lastUserMessageTime: Long = 0
    private var messageDeliverySequence = 0

    init {
        loadChatsFromStorage()
        loadMomentsFromStorage()
        _agentStatus.value = decisionEngine.loadStatus(statusPrefs)
        viewModelScope.launch {
            _emotionState.value = memoryManager.loadEmotion()
            _moodText.value = emotionEngine.getMoodDescription(
                _emotionState.value.mood, _emotionState.value.affinity
            )
            repository.formatRule = typingTracker.getFormatRule()
            runLifeSimulation()
            refreshAgentStatus()
            startStatusLoop()
        }
        try { musicController.connect() } catch (_: Exception) {}
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
            val post = if (apiKey.isNotEmpty()) {
                val prompt = momentsGenerator.buildGenerationPrompt(
                    state, memoryManager.getL1Memory(), memoryManager.getL2Memory()
                )
                val result = repository.sendMessage(model, apiKey,
                    listOf(ChatMessage(role = "user", content = prompt)))
                val content = result.getOrElse { momentsGenerator.generateSimulatedLifeEvents(state) }
                    .removePrefix("\"").removeSuffix("\"").trim()
                momentsGenerator.generateMomentPost(state).copy(content = content)
            } else {
                momentsGenerator.generateMomentPost(state)
            }

            val currentPosts = _momentsPosts.value.toMutableList()
            currentPosts.add(post)
            if (currentPosts.size > 50) currentPosts.removeAt(0)
            _momentsPosts.value = currentPosts
            saveMomentsToStorage()
            momentsPrefs.edit().putLong("last_auto_moments", now).apply()
        } catch (_: Exception) {}
    }

    /** AI 主动联系用户：在聊天中插入一条由 AI 性格驱动的主动消息。 */
    private fun sendProactiveContact(reason: String) {
        viewModelScope.launch {
            try {
                val chatId = _currentChatId.value ?: createNewChat()
                val apiKey = settingsManager.apiKey.first()
                val model = settingsManager.modelName.first()
                val state = _emotionState.value
                val content = if (apiKey.isNotEmpty()) {
                    val prompt = "你现在想主动联系对方。原因: $reason。请像真人发微信一样，用一两句自然的话开启聊天，不要任何符号前缀，不要解释原因本身。直接输出这句话。"
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
            for (i in 0 until eventCount) {
                val virtualHour = Calendar.getInstance().apply {
                    timeInMillis = now - (eventCount - i) * LifeSimulator.INTERVAL_MINUTES * 60000L
                }.get(Calendar.HOUR_OF_DAY)
                if (lifeSimulator.isSleepTime(virtualHour)) continue
                val event = if (i % 8 == 0 && apiKey.isNotEmpty()) {
                    generateApiEvent(model, apiKey, state, virtualHour)
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
        model: String, apiKey: String, state: EmotionState, hour: Int
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
                appendLine("你是AI伴侣，正在进行后台低功耗自主思考。")
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
        val chat = Chat()
        _chats.value = listOf(chat) + _chats.value
        _currentChatId.value = chat.id
        _currentMessages.value = emptyList()
        saveChatsToStorage()
        return chat.id
    }

    fun selectChat(chatId: String) {
        _currentChatId.value = chatId
        val chat = _chats.value.find { it.id == chatId }
        _currentMessages.value = chat?.messages ?: emptyList()
    }

    fun sendMessage(content: String) {
        val now = System.currentTimeMillis()
        val delaySinceLast = if (lastUserMessageTime > 0) now - lastUserMessageTime else 2000L
        lastUserMessageTime = now
        typingTracker.recordUserMessage(content, delaySinceLast)
        repository.formatRule = typingTracker.getFormatRule()

        val chatId = _currentChatId.value ?: createNewChat()
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
                val chatMessages = repository.buildChatMessages(model, _currentMessages.value, emotionDesc, moodDesc)

                var fullReply = ""
                repository.sendMessageStream(model, apiKey, chatMessages)
                    .collect { chunk ->
                        fullReply += chunk
                        _streamingContent.value = fullReply
                    }

                if (fullReply.isNotEmpty()) {
                    val cleaned = fullReply
                        .replace(Regex("""\*[^*]+\*"""), "")
                        .replace(Regex("""【[^】]+】"""), "")
                        .replace(Regex("""（[^）]+）"""), "")
                        .replace(Regex("""\([^)]+\)"""), "")
                        .trim()

                    _streamingContent.value = ""
                    deliverMultiMessage(cleaned, chatId)

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

    private fun deliverMultiMessage(text: String, chatId: String) {
        val parts = typingTracker.splitIntoMessages(text)
        if (parts.size <= 1) {
            finishStreaming(text, chatId, MessageStatus.SENT)
            return
        }

        val delays = typingTracker.getMessageDelays(parts.size)
        val seq = ++messageDeliverySequence
        deliveryJob?.cancel()
        deliveryJob = viewModelScope.launch {
            for (i in parts.indices) {
                if (!isActive || seq != messageDeliverySequence) break
                if (i > 0) delay(delays[i])
                val msg = parts[i].trim()
                if (msg.isEmpty()) continue
                val agentMsg = Message(content = msg, role = Role.AGENT, status = MessageStatus.SENT)
                _currentMessages.value = _currentMessages.value + agentMsg
                syncChatInList(chatId, msg, _currentMessages.value)
            }
            _isLoading.value = false
        }
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

            val post = if (apiKey.isNotEmpty()) {
                val prompt = momentsGenerator.buildGenerationPrompt(
                    state,
                    memoryManager.getL1Memory(),
                    memoryManager.getL2Memory()
                )
                val result = repository.sendMessage(model, apiKey,
                    listOf(ChatMessage(role = "user", content = prompt)))
                val content = result.getOrElse { momentsGenerator.generateSimulatedLifeEvents(state) }
                    .removePrefix("\"").removeSuffix("\"").trim()
                momentsGenerator.generateMomentPost(state).copy(content = content)
            } else {
                momentsGenerator.generateMomentPost(state)
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
    fun searchAndPlaySong(query: String) { try { musicController.searchSong(query) } catch (_: Exception) {} }
    fun toggleLike(postId: String) {
        _momentsPosts.value = _momentsPosts.value.map {
            if (it.id == postId) it.copy(liked = !it.liked) else it
        }
        saveMomentsToStorage()
    }

    /** 用户发布朋友圈动态，并触发 AI 自主互动（根据条件判断是否点赞/评论）。 */
    fun postUserMoment(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
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
            aiReacted = false
        )
        _momentsPosts.value = _momentsPosts.value + post
        saveMomentsToStorage()
        reactToPost(post.id)
    }

    /** AI 对用户动态互动：按好感度 / 心情 / 概率决定是否点赞、是否评论。 */
    private fun reactToPost(postId: String) {
        viewModelScope.launch {
            try {
                val post = _momentsPosts.value.find { it.id == postId } ?: return@launch
                if (post.aiReacted) return@launch
                val state = _emotionState.value
                val rand = Random.nextInt(100)

                var aiLiked = false
                var comment: String? = null
                when {
                    state.affinity >= 70 -> {
                        aiLiked = true
                        if (rand < 75) comment = generateReactionComment(state, post)
                    }
                    state.affinity >= 45 -> {
                        if (rand < 55) aiLiked = true
                        if (rand < 40) comment = generateReactionComment(state, post)
                    }
                    state.affinity >= 25 -> {
                        if (rand < 30) aiLiked = true
                    }
                    else -> {
                        if (rand < 10) comment = "（已读）"
                    }
                }

                val finalComment = comment ?: ""
                val updated = post.copy(
                    likeCount = post.likeCount + (if (aiLiked) 1 else 0),
                    commentCount = post.commentCount + (if (finalComment.isNotEmpty()) 1 else 0),
                    comments = if (finalComment.isNotEmpty()) post.comments + finalComment else post.comments,
                    aiLiked = aiLiked,
                    aiReacted = true
                )
                _momentsPosts.value = _momentsPosts.value.map { if (it.id == postId) updated else it }
                saveMomentsToStorage()
            } catch (_: Exception) {}
        }
    }

    private suspend fun generateReactionComment(state: EmotionState, post: MomentPost): String {
        return try {
            val apiKey = settingsManager.apiKey.first()
            val model = settingsManager.modelName.first()
            if (apiKey.isNotEmpty()) {
                val prompt = "你正在看对方发的朋友圈。对方动态内容: \"${post.content.take(80)}\"。你当前心情: ${state.mood.label}，好感度: ${state.affinity}/100。" +
                    "请以AI伴侣的身份，回一条简短自然的评论（15字以内），像真人发朋友圈评论一样，不要引号和任何符号前缀。直接输出。"
                repository.sendMessage(model, apiKey,
                    listOf(ChatMessage(role = "user", content = prompt)))
                    .getOrElse { localReactionComment(state) }
                    .removePrefix("\"").removeSuffix("\"").trim().take(30)
            } else {
                localReactionComment(state)
            }
        } catch (_: Exception) {
            localReactionComment(state)
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
            try {
                val apiKey = settingsManager.apiKey.first()
                val model = settingsManager.modelName.first()
                val state = _emotionState.value
                val prompt = momentsGenerator.buildGenerationPrompt(
                    state, memoryManager.getL1Memory(), memoryManager.getL2Memory()
                )
                val result = repository.sendMessage(model, apiKey,
                    listOf(ChatMessage(role = "user", content = prompt)))
                val content = result.getOrElse { momentsGenerator.generateSimulatedLifeEvents(state) }
                    .removePrefix("\"").removeSuffix("\"").trim()
                val post = momentsGenerator.generateMomentPost(state).copy(content = content)
                val current = _momentsPosts.value.toMutableList()
                current.add(post)
                if (current.size > 50) current.removeAt(0)
                _momentsPosts.value = current
                saveMomentsToStorage()
            } catch (_: Exception) {
                val post = momentsGenerator.generateMomentPost(_emotionState.value)
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

    private fun finishStreaming(content: String, chatId: String, status: MessageStatus) {
        val agentMessage = Message(content = content, role = Role.AGENT, status = status)
        _currentMessages.value = _currentMessages.value + agentMessage
        _streamingContent.value = ""
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

    fun sendImageMessage(uri: String) {
        val now = System.currentTimeMillis()
        val chatId = _currentChatId.value ?: createNewChat()
        val userMessage = Message(content = "[图片]", role = Role.USER, imageUri = uri)
        val updatedMessages = _currentMessages.value + userMessage
        _currentMessages.value = updatedMessages
        syncChatInList(chatId, "[图片]", updatedMessages)

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _isLoading.value = true
            _streamingContent.value = ""
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
                val chatMessages = repository.buildChatMessages(model, updatedMessages, emotionDesc, moodDesc)

                var fullReply = ""
                repository.sendMessageStream(model, apiKey, chatMessages)
                    .collect { chunk ->
                        fullReply += chunk
                        _streamingContent.value = fullReply
                    }

                if (fullReply.isNotEmpty()) {
                    val cleaned = fullReply
                        .replace(Regex("""\*[^*]+\*"""), "")
                        .replace(Regex("""【[^】]+】"""), "")
                        .replace(Regex("""（[^）]+）"""), "")
                        .replace(Regex("""\([^)]+\)"""), "")
                        .trim()

                    _streamingContent.value = ""
                    deliverMultiMessage(cleaned, chatId)

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
        saveChatsToStorage()
        saveMomentsToStorage()
        musicController.release()
    }
}
