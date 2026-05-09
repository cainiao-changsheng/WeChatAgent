package com.wechat.agent.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wechat.agent.data.EmotionEngine
import com.wechat.agent.data.LifeSimulator
import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.MomentsGenerator
import com.wechat.agent.data.MusicController
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.TypingHabitTracker
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
    private val gson = Gson()
    private val chatPrefs: SharedPreferences = application.getSharedPreferences("chat_sessions", 0)
    private val momentsPrefs: SharedPreferences = application.getSharedPreferences("moments", 0)

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

    private var streamingJob: Job? = null
    private var deliveryJob: Job? = null
    private var lastUserMessageTime: Long = 0
    private var messageDeliverySequence = 0

    init {
        loadChatsFromStorage()
        loadMomentsFromStorage()
        viewModelScope.launch {
            _emotionState.value = memoryManager.loadEmotion()
            _moodText.value = emotionEngine.getMoodDescription(
                _emotionState.value.mood, _emotionState.value.affinity
            )
            repository.formatRule = typingTracker.getFormatRule()
            runLifeSimulation()
            checkAutoMoments()
        }
        try { musicController.connect() } catch (_: Exception) {}
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

    override fun onCleared() {
        super.onCleared()
        saveChatsToStorage()
        saveMomentsToStorage()
        musicController.release()
    }
}
