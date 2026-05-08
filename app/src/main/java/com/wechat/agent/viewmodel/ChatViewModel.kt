package com.wechat.agent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wechat.agent.data.EmotionEngine
import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.model.Chat
import com.wechat.agent.data.model.EmotionState
import com.wechat.agent.data.model.MemoryEntry
import com.wechat.agent.data.model.MemoryType
import com.wechat.agent.data.model.Message
import com.wechat.agent.data.model.MessageStatus
import com.wechat.agent.data.model.Mood
import com.wechat.agent.data.model.Role
import com.wechat.agent.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val memoryManager = MemoryManager(application)
    private val emotionEngine = EmotionEngine(memoryManager)
    private val repository = ChatRepository(memoryManager)

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

    private var streamingJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            _emotionState.value = memoryManager.loadEmotion()
            _moodText.value = emotionEngine.getMoodDescription(
                _emotionState.value.mood, _emotionState.value.affinity
            )
        }
    }

    val currentChat: Chat?
        get() {
            val id = _currentChatId.value ?: return null
            return _chats.value.find { it.id == id }
        }

    fun createNewChat(): String {
        val chat = Chat()
        _chats.value = listOf(chat) + _chats.value
        _currentChatId.value = chat.id
        _currentMessages.value = emptyList()
        return chat.id
    }

    fun selectChat(chatId: String) {
        _currentChatId.value = chatId
        val chat = _chats.value.find { it.id == chatId }
        _currentMessages.value = chat?.messages ?: emptyList()
    }

    fun sendMessage(content: String) {
        val chatId = _currentChatId.value ?: createNewChat()
        val userMessage = Message(content = content, role = Role.USER)

        val updatedMessages = _currentMessages.value + userMessage
        _currentMessages.value = updatedMessages

        val chatIndex = _chats.value.indexOfFirst { it.id == chatId }
        if (chatIndex >= 0) {
            val chat = _chats.value[chatIndex]
            val title = if (chat.messages.isEmpty()) {
                if (content.length > 20) content.take(20) + "..." else content
            } else chat.title
            _chats.value = _chats.value.toMutableList().apply {
                set(chatIndex, chat.copy(
                    title = title,
                    messages = updatedMessages,
                    lastMessage = content,
                    lastTime = System.currentTimeMillis()
                ))
            }
        }

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
                    mood = newMood,
                    lastInteraction = System.currentTimeMillis(),
                    todayTopicCount = state.todayTopicCount + 1
                )
                _emotionState.value = finalState
                _moodText.value = emotionEngine.getMoodDescription(finalState.mood, finalState.affinity)
                memoryManager.saveEmotion(finalState)

                val emotionDesc = "好感度${finalState.affinity}/100·${finalState.mood.label}"
                val moodDesc = emotionEngine.getMoodDescription(finalState.mood, finalState.affinity)

                val chatMessages = repository.buildChatMessages(
                    model, _currentMessages.value, emotionDesc, moodDesc
                )

                var fullReply = ""
                repository.sendMessageStream(model, apiKey, chatMessages)
                    .collect { chunk ->
                        fullReply += chunk
                        _streamingContent.value = fullReply
                    }

                if (fullReply.isNotEmpty()) {
                    finishStreaming(fullReply, chatId, MessageStatus.SENT)

                    memoryManager.addMemory(MemoryEntry(
                        id = UUID.randomUUID().toString(),
                        type = MemoryType.L0_INSTANT,
                        content = "对方说: $content → 你回复: ${fullReply.take(80)}",
                        emotion = userEmotion,
                        importance = 2
                    ))

                    if (userEmotion in listOf("very_warm", "warm") || finalState.affinity > 60) {
                        memoryManager.addGrowthMemory(
                            "对方说过温暖的话: ${content.take(80)}",
                            userEmotion,
                            importance = 4
                        )
                    }

                    viewModelScope.launch {
                        try {
                            val reflection = repository.backgroundReflection(
                                model, apiKey, content, fullReply
                            )
                            if (!reflection.isNullOrBlank()) {
                                memoryManager.addMemory(MemoryEntry(
                                    id = UUID.randomUUID().toString(),
                                    type = MemoryType.L1_DAILY,
                                    content = "自我复盘: ${reflection.take(120)}",
                                    emotion = _emotionState.value.mood.label,
                                    importance = 3
                                ))
                            }
                        } catch (_: Exception) {}
                    }
                } else {
                    _isLoading.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isLoading.value = false
                throw e
            } catch (e: Exception) {
                val errorMsg = Message(
                    content = "唔...网络好像不太对劲，等会儿再试试？(${e.message?.take(40)})",
                    role = Role.AGENT,
                    status = MessageStatus.ERROR
                )
                finishStreaming(errorMsg.content, chatId, errorMsg.status)
            }
        }
    }

    private fun finishStreaming(content: String, chatId: String, status: MessageStatus) {
        val agentMessage = Message(content = content, role = Role.AGENT, status = status)
        val finalMessages = _currentMessages.value + agentMessage
        _currentMessages.value = finalMessages
        _streamingContent.value = ""

        val chatIndex = _chats.value.indexOfFirst { it.id == chatId }
        if (chatIndex >= 0) {
            val chat = _chats.value[chatIndex]
            _chats.value = _chats.value.toMutableList().apply {
                set(chatIndex, chat.copy(
                    messages = finalMessages,
                    lastMessage = content.take(50),
                    lastTime = System.currentTimeMillis()
                ))
            }
        }
        _isLoading.value = false
    }

    private suspend fun getApiKey(): String = settingsManager.apiKey.first()

    private suspend fun getModelName(): String = settingsManager.modelName.first()

    fun deleteChat(chatId: String) {
        _chats.value = _chats.value.filter { it.id != chatId }
        if (_currentChatId.value == chatId) {
            _currentChatId.value = null
            _currentMessages.value = emptyList()
        }
    }
}
