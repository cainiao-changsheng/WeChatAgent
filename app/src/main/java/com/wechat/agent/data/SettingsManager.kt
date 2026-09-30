package com.wechat.agent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** AI 角色档案：每个角色拥有独立的 id、形象与记忆库。 */
data class AgentProfile(
    val id: String,
    val name: String,
    val gender: String = "女",
    val age: String = "18",
    val persona: String = "",
    val globalSettings: String = "",
    val avatar: String = "🤖",
    val avatarUri: String = ""
)

/** 记忆库自动备份配置。 */
data class AutoBackupConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val overwriteOld: Boolean = false,
    val backupOnExit: Boolean = false
)

/** 高级设置（我 → 高级 页），对应参考图"思考设置"各功能项。 */
data class AdvancedSettings(
    val streamEnabled: Boolean = true,
    val customParams: String = "",
    val timeoutDisabled: Boolean = false,
    val customIcon: Boolean = false,
    val darkMode: Boolean = false,
    val sendDelayMs: Int = 0,
    val splitMessages: Boolean = false
)

class SettingsManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }

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

        const val DEFAULT_AGENT_ID = "default"
    }

    private val gson = Gson()
    private val profilePrefs: SharedPreferences =
        context.getSharedPreferences("agent_profiles", Context.MODE_PRIVATE)

    // ========== 多角色档案（SharedPreferences 同步存储） ==========

    private val _agentProfiles = MutableStateFlow(loadProfiles())
    val agentProfiles: StateFlow<List<AgentProfile>> = _agentProfiles.asStateFlow()

    private val _currentAgentId = MutableStateFlow(
        profilePrefs.getString("current_agent_id", DEFAULT_AGENT_ID) ?: DEFAULT_AGENT_ID
    )
    val currentAgentId: StateFlow<String> = _currentAgentId.asStateFlow()

    val currentAgentProfile: Flow<AgentProfile?> =
        combine(agentProfiles, currentAgentId) { list, id ->
            list.find { it.id == id } ?: list.firstOrNull()
        }

    init {
        // 初始不再自动创建默认 AI 好友：初次打开 App 默认无好友，需用户手动添加。
        // 已存在档案的存量用户不受影响。
    }

    fun addAgentProfile(
        name: String, gender: String, age: String, persona: String,
        globalSettings: String, avatar: String = DEFAULT_AGENT_AVATAR, avatarUri: String = ""
    ): String {
        val id = UUID.randomUUID().toString()
        val profile = AgentProfile(
            id = id, name = name.ifBlank { DEFAULT_AGENT_NAME },
            gender = gender.ifBlank { DEFAULT_AGENT_GENDER },
            age = age.ifBlank { DEFAULT_AGENT_AGE },
            persona = persona, globalSettings = globalSettings,
            avatar = avatar.ifBlank { DEFAULT_AGENT_AVATAR }, avatarUri = avatarUri
        )
        val updated = (_agentProfiles.value + profile).distinctBy { it.id }
        saveProfileInternalList(updated)
        _agentProfiles.value = updated
        return id
    }

    fun updateAgentProfile(profile: AgentProfile) {
        val updated = _agentProfiles.value.map {
            if (it.id == profile.id) profile else it
        }
        saveProfileInternalList(updated)
        _agentProfiles.value = updated
    }

    fun deleteAgentProfile(id: String) {
        val updated = _agentProfiles.value.filter { it.id != id }
        saveProfileInternalList(updated)
        _agentProfiles.value = updated
        if (_currentAgentId.value == id) {
            val fallback = updated.firstOrNull()?.id ?: DEFAULT_AGENT_ID
            setCurrentAgentId(fallback)
        }
    }

    fun setCurrentAgentId(id: String) {
        profilePrefs.edit().putString("current_agent_id", id).apply()
        _currentAgentId.value = id
    }

    fun getAgentProfile(id: String): AgentProfile? =
        _agentProfiles.value.find { it.id == id }

    // ========== 记忆库自动备份配置（SharedPreferences 同步存储） ==========

    private val _backupConfig = MutableStateFlow(loadBackupConfig())
    val backupConfig: StateFlow<AutoBackupConfig> = _backupConfig.asStateFlow()

    fun saveBackupConfig(config: AutoBackupConfig) {
        profilePrefs.edit()
            .putBoolean("backup_enabled", config.enabled)
            .putInt("backup_interval_minutes", config.intervalMinutes.coerceIn(5, 1440))
            .putBoolean("backup_overwrite_old", config.overwriteOld)
            .putBoolean("backup_on_exit", config.backupOnExit)
            .apply()
        _backupConfig.value = config.copy(
            intervalMinutes = config.intervalMinutes.coerceIn(5, 1440)
        )
    }

    fun getBackupConfigSync(): AutoBackupConfig = _backupConfig.value

    suspend fun getBackupConfigOnce(): AutoBackupConfig = backupConfig.first()

    private fun loadBackupConfig(): AutoBackupConfig {
        return AutoBackupConfig(
            enabled = profilePrefs.getBoolean("backup_enabled", false),
            intervalMinutes = profilePrefs.getInt("backup_interval_minutes", 60).coerceIn(5, 1440),
            overwriteOld = profilePrefs.getBoolean("backup_overwrite_old", false),
            backupOnExit = profilePrefs.getBoolean("backup_on_exit", false)
        )
    }

    // ========== 高级设置（我 → 高级） ==========

    private val _advancedSettings = MutableStateFlow(loadAdvancedSettings())
    val advancedSettings: StateFlow<AdvancedSettings> = _advancedSettings.asStateFlow()

    fun saveAdvancedSettings(settings: AdvancedSettings) {
        profilePrefs.edit()
            .putBoolean("adv_stream_enabled", settings.streamEnabled)
            .putString("adv_custom_params", settings.customParams ?: "")
            .putBoolean("adv_timeout_disabled", settings.timeoutDisabled)
            .putBoolean("adv_custom_icon", settings.customIcon)
            .putBoolean("adv_dark_mode", settings.darkMode)
            .putInt("adv_send_delay_ms", settings.sendDelayMs.coerceIn(0, 60000))
            .putBoolean("adv_split_messages", settings.splitMessages)
            .apply()
        _advancedSettings.value = settings.copy(
            sendDelayMs = settings.sendDelayMs.coerceIn(0, 60000)
        )
    }

    fun getAdvancedSettingsSync(): AdvancedSettings = _advancedSettings.value

    private fun loadAdvancedSettings(): AdvancedSettings {
        return AdvancedSettings(
            streamEnabled = profilePrefs.getBoolean("adv_stream_enabled", true),
            customParams = profilePrefs.getString("adv_custom_params", "") ?: "",
            timeoutDisabled = profilePrefs.getBoolean("adv_timeout_disabled", false),
            customIcon = profilePrefs.getBoolean("adv_custom_icon", false),
            darkMode = profilePrefs.getBoolean("adv_dark_mode", false),
            sendDelayMs = profilePrefs.getInt("adv_send_delay_ms", 0).coerceIn(0, 60000),
            splitMessages = profilePrefs.getBoolean("adv_split_messages", false)
        )
    }

    private fun loadProfiles(): List<AgentProfile> {
        val json = profilePrefs.getString("profiles", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<AgentProfile>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }

    private fun saveProfileInternal(profile: AgentProfile) {
        saveProfileInternalList(listOf(profile))
    }

    private fun saveProfileInternalList(profiles: List<AgentProfile>) {
        profilePrefs.edit().putString("profiles", gson.toJson(profiles)).apply()
    }

    // ========== 兼容旧版 DataStore 流（模型配置 / 用户信息） ==========

    val apiUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_URL] ?: DEFAULT_API_URL
    }

    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    val modelName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MODEL_NAME] ?: DEFAULT_MODEL
    }

    val userAvatar: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_AVATAR] ?: DEFAULT_USER_AVATAR
    }

    val userAvatarUri: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_AVATAR_URI] ?: ""
    }

    val userNickname: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NICKNAME] ?: DEFAULT_USER_NICKNAME
    }

    /** 当前角色的名字，优先角色档案，回退旧字段。 */
    val agentName: Flow<String> = combine(agentProfiles, currentAgentId) { list, id ->
        list.find { it.id == id }?.name ?: DEFAULT_AGENT_NAME
    }

    val agentGender: Flow<String> = combine(agentProfiles, currentAgentId) { list, id ->
        list.find { it.id == id }?.gender ?: DEFAULT_AGENT_GENDER
    }

    val agentAge: Flow<String> = combine(agentProfiles, currentAgentId) { list, id ->
        list.find { it.id == id }?.age ?: DEFAULT_AGENT_AGE
    }

    val agentPersona: Flow<String> = combine(agentProfiles, currentAgentId) { list, id ->
        list.find { it.id == id }?.persona ?: DEFAULT_AGENT_PERSONA
    }

    val agentGlobalSettings: Flow<String> = combine(agentProfiles, currentAgentId) { list, id ->
        list.find { it.id == id }?.globalSettings ?: ""
    }

    val agentAvatar: Flow<String> = combine(agentProfiles, currentAgentId) { list, id ->
        list.find { it.id == id }?.avatar ?: DEFAULT_AGENT_AVATAR
    }

    val agentAvatarUri: Flow<String> = combine(agentProfiles, currentAgentId) { list, id ->
        list.find { it.id == id }?.avatarUri ?: ""
    }

    suspend fun saveApiSettings(url: String, key: String, model: String) {
        context.dataStore.edit { preferences ->
            preferences[API_URL] = url
            preferences[API_KEY] = key
            preferences[MODEL_NAME] = model
        }
    }

    suspend fun saveUserNickname(nickname: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NICKNAME] = nickname
        }
    }

    /** 保存用户头像（旧版 DataStore 字段）。 */
    suspend fun saveUserAvatar(avatar: String, avatarUri: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_AVATAR] = avatar
            preferences[USER_AVATAR_URI] = avatarUri
        }
    }

    /** 更新当前 AI 角色档案（编辑场景）。 */
    suspend fun saveAgentProfile(
        name: String, gender: String, age: String, persona: String, globalSettings: String
    ) {
        val current = currentAgentProfile.first() ?: return
        updateAgentProfile(
            current.copy(
                name = name.ifBlank { DEFAULT_AGENT_NAME },
                gender = gender.ifBlank { DEFAULT_AGENT_GENDER },
                age = age.ifBlank { DEFAULT_AGENT_AGE },
                persona = persona, globalSettings = globalSettings
            )
        )
    }
}
