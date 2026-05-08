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

        const val DEFAULT_API_URL = "https://api.openai.com/"
        const val DEFAULT_MODEL = "gpt-3.5-turbo"
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

    suspend fun saveApiSettings(url: String, key: String, model: String) {
        context.dataStore.edit { preferences ->
            preferences[API_URL] = url
            preferences[API_KEY] = key
            preferences[MODEL_NAME] = model
        }
    }
}
