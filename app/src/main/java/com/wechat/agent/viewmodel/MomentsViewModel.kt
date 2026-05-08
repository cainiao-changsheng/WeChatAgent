package com.wechat.agent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wechat.agent.data.MemoryManager
import com.wechat.agent.data.MomentsGenerator
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.model.MomentPost
import com.wechat.agent.data.repository.ChatRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MomentsViewModel(application: Application) : AndroidViewModel(application) {

    private val memoryManager = MemoryManager(application)
    private val settingsManager = SettingsManager(application)
    private val repository = ChatRepository(memoryManager)
    private val generator = MomentsGenerator(memoryManager)
    private val gson = Gson()

    private val _posts = MutableStateFlow<List<MomentPost>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadSavedPosts()
    }

    private fun loadSavedPosts() {
        val prefs = getApplication<Application>().getSharedPreferences("moments", 0)
        val json = prefs.getString("posts", null) ?: return
        try {
            val list = gson.fromJson<List<MomentPost>>(
                json, object : TypeToken<List<MomentPost>>() {}.type
            )
            _posts.value = list ?: emptyList()
        } catch (_: Exception) {}
    }

    private fun savePosts() {
        val prefs = getApplication<Application>().getSharedPreferences("moments", 0)
        prefs.edit().putString("posts", gson.toJson(_posts.value)).apply()
    }

    fun generateMomentsPost() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apiKey = settingsManager.apiKey.first()
                val model = settingsManager.modelName.first()
                val state = memoryManager.loadEmotion()
                val l1Memories = memoryManager.getL1Memory()
                val l2Memories = memoryManager.getL2Memory()

                val prompt = generator.buildGenerationPrompt(state, l1Memories, l2Memories)

                val result = repository.sendMessage(
                    model, apiKey,
                    listOf(com.wechat.agent.data.network.ChatMessage(role = "user", content = prompt))
                )

                val content = result.getOrElse { generator.generateSimulatedLifeEvents(state) }

                val cleanedContent = content
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .trim()

                val post = generator.generateMomentPost(state).copy(content = cleanedContent)

                val currentPosts = _posts.value.toMutableList()
                currentPosts.add(post)
                if (currentPosts.size > 50) currentPosts.removeAt(0)
                _posts.value = currentPosts
                savePosts()
            } catch (_: Exception) {
                val state = memoryManager.loadEmotionSync()
                val post = generator.generateMomentPost(state)
                val currentPosts = _posts.value.toMutableList()
                currentPosts.add(post)
                if (currentPosts.size > 50) currentPosts.removeAt(0)
                _posts.value = currentPosts
                savePosts()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleLike(postId: String) {
        _posts.value = _posts.value.map { post ->
            if (post.id == postId) post.copy(liked = !post.liked) else post
        }
        savePosts()
    }
}
