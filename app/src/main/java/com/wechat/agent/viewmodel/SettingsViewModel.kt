package com.wechat.agent.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val agentAvatar: StateFlow<String> = settingsManager.agentAvatar
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_AVATAR)

    val userAvatar: StateFlow<String> = settingsManager.userAvatar
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_USER_AVATAR)

    val agentAvatarUri: StateFlow<String> = settingsManager.agentAvatarUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val userAvatarUri: StateFlow<String> = settingsManager.userAvatarUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val agentName: StateFlow<String> = settingsManager.agentName
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_NAME)

    val agentGender: StateFlow<String> = settingsManager.agentGender
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_GENDER)

    val agentAge: StateFlow<String> = settingsManager.agentAge
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_AGE)

    val agentPersona: StateFlow<String> = settingsManager.agentPersona
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AGENT_PERSONA)

    val agentGlobalSettings: StateFlow<String> = settingsManager.agentGlobalSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val userNickname: StateFlow<String> = settingsManager.userNickname
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_USER_NICKNAME)

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun saveSettings(url: String, key: String, model: String) {
        viewModelScope.launch {
            settingsManager.saveApiSettings(url, key, model)
            RetrofitClient.updateBaseUrl(url)
            _saveSuccess.value = true
        }
    }

    fun saveAvatar(agent: String, user: String) {
        viewModelScope.launch {
            settingsManager.saveAvatar(agent, user)
            _saveSuccess.value = true
        }
    }

    fun saveAvatarUri(agentUri: String, userUri: String) {
        viewModelScope.launch {
            settingsManager.saveAvatarUri(agentUri, userUri)
            _saveSuccess.value = true
        }
    }

    fun saveAgentProfile(
        name: String, gender: String, age: String, persona: String, globalSettings: String
    ) {
        viewModelScope.launch {
            settingsManager.saveAgentProfile(name, gender, age, persona, globalSettings)
            _saveSuccess.value = true
        }
    }

    fun saveUserNickname(nickname: String) {
        viewModelScope.launch {
            settingsManager.saveUserNickname(nickname)
            _saveSuccess.value = true
        }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
}
