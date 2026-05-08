package com.wechat.agent.viewmodel

import android.app.Application
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

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun saveSettings(url: String, key: String, model: String) {
        viewModelScope.launch {
            settingsManager.saveApiSettings(url, key, model)
            RetrofitClient.updateBaseUrl(url)
            _saveSuccess.value = true
        }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
}
