package com.wechat.agent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wechat.agent.data.AgentProfile
import com.wechat.agent.data.AutoBackupConfig
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.network.ApiModels.ChatMessage
import com.wechat.agent.data.network.ApiModels.ChatRequest
import com.wechat.agent.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)

    val apiUrl: StateFlow<String> = settingsManager.apiUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_API_URL)

    val apiKey: StateFlow<String> = settingsManager.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val modelName: StateFlow<String> = settingsManager.modelName
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_MODEL)

    val userAvatar: StateFlow<String> = settingsManager.userAvatar
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_USER_AVATAR)

    val userAvatarUri: StateFlow<String> = settingsManager.userAvatarUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val userNickname: StateFlow<String> = settingsManager.userNickname
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_USER_NICKNAME)

    // ========== 多角色档案 ==========

    val agentProfiles: StateFlow<List<AgentProfile>> = settingsManager.agentProfiles

    val currentAgentId: StateFlow<String> = settingsManager.currentAgentId

    val currentAgentProfile: StateFlow<AgentProfile?> =
        combine(settingsManager.agentProfiles, settingsManager.currentAgentId) { list, id ->
            list.find { it.id == id } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val agentName: StateFlow<String> = currentAgentProfile
        .map { it?.name ?: SettingsManager.DEFAULT_AGENT_NAME }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_NAME)
    val agentGender: StateFlow<String> = currentAgentProfile
        .map { it?.gender ?: SettingsManager.DEFAULT_AGENT_GENDER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_GENDER)
    val agentAge: StateFlow<String> = currentAgentProfile
        .map { it?.age ?: SettingsManager.DEFAULT_AGENT_AGE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_AGE)
    val agentPersona: StateFlow<String> = currentAgentProfile
        .map { it?.persona ?: SettingsManager.DEFAULT_AGENT_PERSONA }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_PERSONA)
    val agentGlobalSettings: StateFlow<String> = currentAgentProfile
        .map { it?.globalSettings.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val agentAvatar: StateFlow<String> = currentAgentProfile
        .map { it?.avatar ?: SettingsManager.DEFAULT_AGENT_AVATAR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_AVATAR)
    val agentAvatarUri: StateFlow<String> = currentAgentProfile
        .map { it?.avatarUri.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val backupConfig: StateFlow<AutoBackupConfig> = settingsManager.backupConfig

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun saveSettings(url: String, key: String, model: String) {
        viewModelScope.launch {
            settingsManager.saveApiSettings(url, key, model)
            RetrofitClient.updateBaseUrl(url)
            _saveSuccess.value = true
        }
    }

    companion object {
        const val TEST_MESSAGE = "测试消息，请回复当前时间和你的模型信息，其余内容无需回复。"
    }

    /** 向配置的模型发送一条测试消息，返回模型回复文本；失败返回空串。 */
    fun testModelConnection(
        url: String,
        key: String,
        model: String,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val reply = runCatching {
                RetrofitClient.updateBaseUrl(url)
                val request = ChatRequest(
                    model = model,
                    messages = listOf(ChatMessage(role = "user", content = TEST_MESSAGE)),
                    stream = false
                )
                val resp = RetrofitClient.getApiService().sendMessage(
                    authorization = "Bearer $key",
                    request = request
                )
                if (resp.isSuccessful) {
                    val content = resp.body()?.choices?.firstOrNull()?.message?.content.orEmpty().trim()
                    content.ifBlank { "（模型返回了空内容）" }
                } else {
                    ""
                }
            }.getOrDefault("")
            onResult(reply)
        }
    }

    fun saveUserNickname(nickname: String) {
        viewModelScope.launch {
            settingsManager.saveUserNickname(nickname)
            _saveSuccess.value = true
        }
    }

    fun saveUserAvatar(avatar: String, avatarUri: String = "") {
        viewModelScope.launch {
            settingsManager.saveUserAvatar(avatar, avatarUri)
            _saveSuccess.value = true
        }
    }

    /** 新增 AI 角色：追加到角色列表（不覆盖默认角色），并自动切换为新角色。返回新角色 id。 */
    fun addAgentProfile(
        name: String, gender: String, age: String, persona: String,
        globalSettings: String, avatar: String = SettingsManager.DEFAULT_AGENT_AVATAR,
        avatarUri: String = ""
    ): String {
        val id = settingsManager.addAgentProfile(name, gender, age, persona, globalSettings, avatar, avatarUri)
        settingsManager.setCurrentAgentId(id)
        _saveSuccess.value = true
        return id
    }

    /** 编辑当前角色档案。 */
    fun updateCurrentAgentProfile(
        name: String, gender: String, age: String, persona: String, globalSettings: String,
        avatar: String, avatarUri: String
    ) {
        val current = currentAgentProfile.value ?: return
        settingsManager.updateAgentProfile(
            current.copy(
                name = name.ifBlank { SettingsManager.DEFAULT_AGENT_NAME },
                gender = gender.ifBlank { SettingsManager.DEFAULT_AGENT_GENDER },
                age = age.ifBlank { SettingsManager.DEFAULT_AGENT_AGE },
                persona = persona,
                globalSettings = globalSettings,
                avatar = avatar.ifBlank { SettingsManager.DEFAULT_AGENT_AVATAR },
                avatarUri = avatarUri
            )
        )
        _saveSuccess.value = true
    }

    fun switchAgent(id: String) {
        settingsManager.setCurrentAgentId(id)
    }

    fun deleteAgentProfile(id: String) {
        settingsManager.deleteAgentProfile(id)
    }

    fun saveBackupConfig(config: AutoBackupConfig) {
        settingsManager.saveBackupConfig(config)
    }

    fun getBackupConfigSync(): AutoBackupConfig = settingsManager.getBackupConfigSync()

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
}
