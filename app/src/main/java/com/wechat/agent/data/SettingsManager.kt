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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    val customPrompt: String = "",
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

/** 实验功能：热恋模式。开启后可获取屏幕使用时间、锁屏控制与音乐播放器控制权限。 */
data class HotLoveSettings(
    val enabled: Boolean = false,
    val selectedMusicPackage: String = "",
    val lockScreenPause: Boolean = true,
    val proactiveMessages: Boolean = false,
    /** AI 后台主动发消息的时间间隔（分钟）。默认 30；开启后由大模型按角色设定自定一次，用户可在热恋模式页手动修改。 */
    val proactiveIntervalMinutes: Int = 30,
    /** 当前间隔是否由大模型决定（用户手动修改后置 false，避免 AI 覆盖用户自定义值）。 */
    val proactiveIntervalFromAi: Boolean = false,
    /** 用户是否手动设置过间隔（置 true 后 AI 不再自动覆盖，除非值仍为默认 30）。 */
    val proactiveIntervalUserSet: Boolean = false,
    /** 主动发消息时间窗下限 x（分钟）：收到用户上条消息后至少等 x 分钟，AI 才可能主动发消息。默认 5。 */
    val proactiveWindowMinMinutes: Int = 5,
    /** 主动发消息时间窗上限 y（分钟）：收到用户上条消息后 y 分钟内 AI 自行决定是否主动发；超过窗口不再打扰。默认 20。 */
    val proactiveWindowMaxMinutes: Int = 20,
    /** 时间窗当前是否由大模型决定（用户手动修改后置 false，避免 AI 覆盖用户自定义值）。 */
    val proactiveWindowFromAi: Boolean = false,
    /** 用户是否手动设置过时间窗（置 true 后 AI 不再自动覆盖；点击「恢复 AI 设定」后置 false）。 */
    val proactiveWindowUserSet: Boolean = false
)

/** 高级设置（我 → 高级 页），对应参考图"思考设置"各功能项。 */
data class AdvancedSettings(
    val streamEnabled: Boolean = true,
    val customParams: String = "",
    val timeoutDisabled: Boolean = false,
    val customIcon: Boolean = false,
    val darkMode: Boolean = false,
    val sendDelayMs: Int = 0,
    val splitMessages: Boolean = false,
    val thinkDisplay: Boolean = true,
    val autoCollapseThinking: Boolean = false,
    /** Agent 模式：聊天时向大模型暴露只读工具（时间/屏幕使用时间/记忆查询），模型可主动调用。 */
    val agentTools: Boolean = true
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
    private val secureApiKeyStore = SecureApiKeyStore(context)
    private val apiKeyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _apiKey = MutableStateFlow(secureApiKeyStore.read())
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
        migrateLegacyApiKey()
    }

    fun addAgentProfile(
        name: String, gender: String, age: String, persona: String,
        globalSettings: String, customPrompt: String = "",
        avatar: String = DEFAULT_AGENT_AVATAR, avatarUri: String = ""
    ): String {
        val id = UUID.randomUUID().toString()
        val profile = AgentProfile(
            id = id, name = name.ifBlank { DEFAULT_AGENT_NAME },
            gender = gender.ifBlank { DEFAULT_AGENT_GENDER },
            age = age.ifBlank { DEFAULT_AGENT_AGE },
            persona = persona, globalSettings = globalSettings, customPrompt = customPrompt,
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

    // ========== 实验功能：热恋模式 ==========

    private val _hotLoveSettings = MutableStateFlow(loadHotLoveSettings())
    val hotLoveSettings: StateFlow<HotLoveSettings> = _hotLoveSettings.asStateFlow()

    fun saveHotLoveSettings(settings: HotLoveSettings) {
        profilePrefs.edit()
            .putBoolean("hotlove_enabled", settings.enabled)
            .putString("hotlove_music_package", settings.selectedMusicPackage ?: "")
            .putBoolean("hotlove_lock_screen_pause", settings.lockScreenPause)
            .putBoolean("hotlove_proactive_messages", settings.proactiveMessages)
            .putInt("hotlove_proactive_interval_minutes", settings.proactiveIntervalMinutes.coerceIn(5, 1440))
            .putBoolean("hotlove_proactive_interval_from_ai", settings.proactiveIntervalFromAi)
            .putBoolean("hotlove_proactive_interval_user_set", settings.proactiveIntervalUserSet)
            .putInt("hotlove_proactive_window_min", settings.proactiveWindowMinMinutes.coerceIn(1, 1440))
            .putInt("hotlove_proactive_window_max", settings.proactiveWindowMaxMinutes.coerceIn(1, 1440))
            .putBoolean("hotlove_proactive_window_from_ai", settings.proactiveWindowFromAi)
            .putBoolean("hotlove_proactive_window_user_set", settings.proactiveWindowUserSet)
            .apply()
        val windowMin = settings.proactiveWindowMinMinutes.coerceIn(1, 1440)
        val windowMax = settings.proactiveWindowMaxMinutes.coerceIn(1, 1440)
        _hotLoveSettings.value = settings.copy(
            proactiveIntervalMinutes = settings.proactiveIntervalMinutes.coerceIn(5, 1440),
            proactiveWindowMinMinutes = minOf(windowMin, windowMax),
            proactiveWindowMaxMinutes = maxOf(windowMin, windowMax)
        )
    }

    fun getHotLoveSettingsSync(): HotLoveSettings = _hotLoveSettings.value

    /** 恢复 AI 设定的主动发消息时间窗：重置为内置 AI 默认值（5-20 分钟）并标记由 AI 设定。 */
    fun restoreProactiveWindowFromAi() {
        val s = getHotLoveSettingsSync()
        saveHotLoveSettings(
            s.copy(
                proactiveWindowMinMinutes = 5,
                proactiveWindowMaxMinutes = 20,
                proactiveWindowFromAi = true,
                proactiveWindowUserSet = false
            )
        )
    }

    private fun loadHotLoveSettings(): HotLoveSettings {
        return HotLoveSettings(
            enabled = profilePrefs.getBoolean("hotlove_enabled", false),
            selectedMusicPackage = profilePrefs.getString("hotlove_music_package", "") ?: "",
            lockScreenPause = profilePrefs.getBoolean("hotlove_lock_screen_pause", true),
            proactiveMessages = profilePrefs.getBoolean("hotlove_proactive_messages", false),
            proactiveIntervalMinutes = profilePrefs.getInt("hotlove_proactive_interval_minutes", 30).coerceIn(5, 1440),
            proactiveIntervalFromAi = profilePrefs.getBoolean("hotlove_proactive_interval_from_ai", false),
            proactiveIntervalUserSet = profilePrefs.getBoolean("hotlove_proactive_interval_user_set", false),
            proactiveWindowMinMinutes = profilePrefs.getInt("hotlove_proactive_window_min", 5).coerceIn(1, 1440),
            proactiveWindowMaxMinutes = profilePrefs.getInt("hotlove_proactive_window_max", 20).coerceIn(1, 1440),
            proactiveWindowFromAi = profilePrefs.getBoolean("hotlove_proactive_window_from_ai", false),
            proactiveWindowUserSet = profilePrefs.getBoolean("hotlove_proactive_window_user_set", false)
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
            .putBoolean("adv_think_display", settings.thinkDisplay)
            .putBoolean("adv_auto_collapse_think", settings.autoCollapseThinking)
            .putBoolean("adv_agent_tools", settings.agentTools)
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
            splitMessages = profilePrefs.getBoolean("adv_split_messages", false),
            thinkDisplay = profilePrefs.getBoolean("adv_think_display", true),
            autoCollapseThinking = profilePrefs.getBoolean("adv_auto_collapse_think", false),
            agentTools = profilePrefs.getBoolean("adv_agent_tools", true)
        )
    }

    private fun loadProfiles(): List<AgentProfile> {
        val json = profilePrefs.getString("profiles", null) ?: return emptyList()
        return try {
            val profiles: List<AgentProfile> = gson.fromJson(json, object : TypeToken<List<AgentProfile>>() {}.type)
            profiles.map { it.copy(customPrompt = it.customPrompt.orEmpty()) }
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

    /** API Key 只从加密存储读取，不再通过 DataStore 保存明文。 */
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

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
        require(secureApiKeyStore.write(key.trim())) { "API Key 安全存储失败" }
        _apiKey.value = key.trim()
        context.dataStore.edit { preferences ->
            preferences[API_URL] = url.trim()
            // 旧版本曾将 API Key 明文放在 DataStore；保存新配置时主动移除旧字段。
            preferences.remove(API_KEY)
            preferences[MODEL_NAME] = model.trim()
        }
    }

    private fun migrateLegacyApiKey() {
        apiKeyScope.launch {
            runCatching {
                val legacyKey = context.dataStore.data.first()[API_KEY].orEmpty()
                if (legacyKey.isNotBlank() && secureApiKeyStore.read().isBlank()) {
                    // 只有加密写入并回读成功后才删除旧明文字段，避免迁移失败导致凭据丢失。
                    if (secureApiKeyStore.write(legacyKey) && secureApiKeyStore.read() == legacyKey) {
                        _apiKey.value = legacyKey
                        context.dataStore.edit { preferences -> preferences.remove(API_KEY) }
                    }
                } else if (legacyKey.isBlank()) {
                    // 没有旧凭据时清理遗留字段。
                    context.dataStore.edit { preferences -> preferences.remove(API_KEY) }
                }
            }.onFailure {
                AppLogger.log("Settings", "API Key 迁移失败: ${it.javaClass.simpleName}")
            }
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
