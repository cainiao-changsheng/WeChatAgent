package com.wechat.agent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.model.Chat
import com.wechat.agent.data.model.Message
import com.wechat.agent.data.model.MessageStatus
import com.wechat.agent.data.model.Role
import com.wechat.agent.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val repository = ChatRepository()

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

    private var streamingJob: Job? = null

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

        viewModelScope.launch {
            _isLoading.value = true
            _streamingContent.value = ""

            val apiKey = getApiKey()
            val model = getModelName()
            val messagesToSend = buildMessageHistory()

            streamingJob = viewModelScope.launch {
                try {
                    repository.sendMessageStream(model, apiKey, messagesToSend)
                        .collect { chunk ->
                            _streamingContent.value += chunk
                        }
                } catch (e: Exception) {
                    val errorMsg = Message(
                        content = "错误: ${e.message}",
                        role = Role.AGENT,
                        status = MessageStatus.ERROR
                    )
                    finishStreaming(errorMsg.content, chatId, errorMsg.status)
                    return@launch
                }
            }
            streamingJob?.join()

            val fullContent = _streamingContent.value
            if (fullContent.isNotEmpty()) {
                finishStreaming(fullContent, chatId, MessageStatus.SENT)
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

    private fun buildMessageHistory(): List<Message> {
        val history = mutableListOf<Message>()
        history.add(Message(
            content = "你是一个智能AI助手，请简洁、友好地回答用户的问题。",
            role = Role.USER
        ))
        history.add(Message(
            content = "好的，我会简洁友好地回答用户的问题。",
            role = Role.AGENT
        ))
        history.addAll(_currentMessages.value.takeLast(20))
        return history
    }

    private suspend fun getApiKey(): String {
        var key = ""
        settingsManager.apiKey.collect { key = it }
        return key
    }

    private suspend fun getModelName(): String {
        var model = SettingsManager.DEFAULT_MODEL
        settingsManager.modelName.collect { model = it }
        return model
    }

    fun deleteChat(chatId: String) {
        _chats.value = _chats.value.filter { it.id != chatId }
        if (_currentChatId.value == chatId) {
            _currentChatId.value = null
            _currentMessages.value = emptyList()
        }
    }
}
