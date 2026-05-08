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

        const val DEFAULT_API_URL = "https://api.deepseek.com/"
        const val DEFAULT_MODEL = "deepseek-chat"

        const val DEFAULT_AGENT_AVATAR = "🤖"
        const val DEFAULT_USER_AVATAR = "👤"
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
}
