package com.wechat.agent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val API_URL = stringPreferencesKey("api_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val AGENT_AVATAR = stringPreferencesKey("agent_avatar")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
        val AGENT_AVATAR_URI = stringPreferencesKey("agent_avatar_uri")
        val USER_AVATAR_URI = stringPreferencesKey("user_avatar_uri")
        val AGENT_NAME = stringPreferencesKey("agent_name")
        val AGENT_GENDER = stringPreferencesKey("agent_gender")
        val AGENT_AGE = stringPreferencesKey("agent_age")
        val AGENT_PERSONA = stringPreferencesKey("agent_persona")
        val AGENT_GLOBAL_SETTINGS = stringPreferencesKey("agent_global_settings")
        val USER_NICKNAME = stringPreferencesKey("user_nickname")

        const val DEFAULT_API_URL = "https://api.deepseek.com/"
        const val DEFAULT_MODEL = "deepseek-v4-flash"

        const val DEFAULT_AGENT_AVATAR = "🤖"
        const val DEFAULT_USER_AVATAR = "👤"
        const val DEFAULT_AGENT_NAME = "AI伴侣"
        const val DEFAULT_AGENT_GENDER = "女"
        const val DEFAULT_AGENT_AGE = "18"
        const val DEFAULT_AGENT_PERSONA = "温柔、善解人意，像好朋友一样陪伴你"
        const val DEFAULT_USER_NICKNAME = "我"
    }

    val apiUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_URL] ?: DEFAULT_API_URL
    }

    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    val modelName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MODEL_NAME] ?: DEFAULT_MODEL
    }

    val agentAvatar: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AGENT_AVATAR] ?: DEFAULT_AGENT_AVATAR
    }

    val userAvatar: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_AVATAR] ?: DEFAULT_USER_AVATAR
    }

    val agentAvatarUri: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AGENT_AVATAR_URI] ?: ""
    }

    val userAvatarUri: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_AVATAR_URI] ?: ""
    }

    val agentName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AGENT_NAME] ?: DEFAULT_AGENT_NAME
    }

    val agentGender: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AGENT_GENDER] ?: DEFAULT_AGENT_GENDER
    }

    val agentAge: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AGENT_AGE] ?: DEFAULT_AGENT_AGE
    }

    val agentPersona: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AGENT_PERSONA] ?: DEFAULT_AGENT_PERSONA
    }

    val agentGlobalSettings: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AGENT_GLOBAL_SETTINGS] ?: ""
    }

    val userNickname: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NICKNAME] ?: DEFAULT_USER_NICKNAME
    }

    suspend fun saveApiSettings(url: String, key: String, model: String) {
        context.dataStore.edit { preferences ->
            preferences[API_URL] = url
            preferences[API_KEY] = key
            preferences[MODEL_NAME] = model
        }
    }

    suspend fun saveAvatar(agent: String, user: String) {
        context.dataStore.edit { preferences ->
            preferences[AGENT_AVATAR] = agent
            preferences[USER_AVATAR] = user
        }
    }

    suspend fun saveAvatarUri(agentUri: String, userUri: String) {
        context.dataStore.edit { preferences ->
            preferences[AGENT_AVATAR_URI] = agentUri
            preferences[USER_AVATAR_URI] = userUri
        }
    }

    suspend fun saveAgentProfile(
        name: String, gender: String, age: String, persona: String, globalSettings: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[AGENT_NAME] = name
            preferences[AGENT_GENDER] = gender
            preferences[AGENT_AGE] = age
            preferences[AGENT_PERSONA] = persona
            preferences[AGENT_GLOBAL_SETTINGS] = globalSettings
        }
    }

    suspend fun saveUserNickname(nickname: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NICKNAME] = nickname
        }
    }
}
