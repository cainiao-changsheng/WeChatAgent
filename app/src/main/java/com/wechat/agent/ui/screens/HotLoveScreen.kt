package com.wechat.agent.ui.screens

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wechat.agent.data.HotLoveSettings
import com.wechat.agent.data.NotificationListener
import com.wechat.agent.ui.theme.WeChatGreen
import com.wechat.agent.viewmodel.SettingsViewModel

private data class MusicAppInfo(val packageName: String, val label: String)

/**
 * 实验室 → 热恋模式。
 * 开启后：展示手机屏幕使用时间数据、锁屏控制（锁屏自动暂停/解锁恢复）、
 * 音乐播放器控制（从已安装音乐 App 中选择并接管播放/暂停/切歌）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotLoveScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.hotLoveSettings.collectAsState()
    val context = LocalContext.current

    var usageGranted by remember { mutableStateOf(hasUsageAccess(context)) }
    var notifGranted by remember { mutableStateOf(hasNotificationAccess(context)) }
    var musicApps by remember { mutableStateOf(loadMusicApps(context)) }
    var usageList by remember { mutableStateOf(loadUsageList(context)) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // 每次开关/权限变化后刷新状态
    LaunchedEffect(settings.enabled, refreshTick) {
        usageGranted = hasUsageAccess(context)
        notifGranted = hasNotificationAccess(context)
        if (settings.enabled && usageGranted) {
            usageList = loadUsageList(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("热恋模式", fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 总开关
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = WeChatGreen)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("热恋模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "开启后获取屏幕使用时间、锁屏控制与音乐播放器控制权限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.saveHotLoveSettings(settings.copy(enabled = enabled))
                        }
                    )
                }
            }

            if (settings.enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "权限与数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 屏幕使用时间权限
                PermissionCard(
                    icon = { Icon(Icons.Default.Timer, contentDescription = null, tint = WeChatGreen) },
                    title = "屏幕使用时间",
                    subtitle = "读取手机各 App 屏幕使用时长数据",
                    granted = usageGranted,
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                // 通知使用权（音乐控制基础）
                PermissionCard(
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = WeChatGreen) },
                    title = "通知使用权",
                    subtitle = "用于读取并控制音乐 App 的播放状态（播放/暂停/切歌）",
                    granted = notifGranted,
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                // 锁屏控制
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.saveHotLoveSettings(settings.copy(lockScreenPause = !settings.lockScreenPause))
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = WeChatGreen)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("锁屏控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "锁屏自动暂停所选音乐，解锁自动恢复播放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = settings.lockScreenPause,
                        onCheckedChange = { value ->
                            viewModel.saveHotLoveSettings(settings.copy(lockScreenPause = value))
                        }
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                // AI 后台主动发消息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.saveHotLoveSettings(settings.copy(proactiveMessages = !settings.proactiveMessages))
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = WeChatGreen)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI 后台主动发消息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "热恋模式下 AI 会按设定间隔主动联系你，间隔由大模型按角色自定，可手动修改",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = settings.proactiveMessages,
                        onCheckedChange = { value ->
                            viewModel.saveHotLoveSettings(settings.copy(proactiveMessages = value))
                        }
                    )
                }
                if (settings.proactiveMessages) {
                    // 主动发消息间隔：大模型按角色自定一次，用户可手动修改（修改后 AI 不再覆盖）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = WeChatGreen)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "主动发消息间隔",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                when {
                                    settings.proactiveIntervalFromAi -> "由大模型按角色自定，可手动修改"
                                    settings.proactiveIntervalUserSet -> "手动设置（大模型不再自动覆盖）"
                                    else -> "大模型将在下次主动消息前自定间隔，可手动修改"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IntervalStepButton("-", enabled = settings.proactiveIntervalMinutes > 5) {
                                viewModel.saveHotLoveSettings(
                                    settings.copy(
                                        proactiveIntervalMinutes = (settings.proactiveIntervalMinutes - 5).coerceAtLeast(5),
                                        proactiveIntervalFromAi = false,
                                        proactiveIntervalUserSet = true
                                    )
                                )
                            }
                            Text(
                                "${settings.proactiveIntervalMinutes} 分钟",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            IntervalStepButton("+", enabled = settings.proactiveIntervalMinutes < 1440) {
                                viewModel.saveHotLoveSettings(
                                    settings.copy(
                                        proactiveIntervalMinutes = (settings.proactiveIntervalMinutes + 5).coerceAtMost(1440),
                                        proactiveIntervalFromAi = false,
                                        proactiveIntervalUserSet = true
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "音乐播放器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    if (musicApps.isEmpty()) {
                        Text(
                            "未检测到已安装的音乐 App，请安装后重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        musicApps.forEachIndexed { index, app ->
                            val selected = settings.selectedMusicPackage == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.saveHotLoveSettings(
                                            settings.copy(selectedMusicPackage = app.packageName)
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = WeChatGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = WeChatGreen
                                    )
                                }
                            }
                            if (index != musicApps.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                if (usageGranted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "屏幕使用时间（今日）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        if (usageList.isEmpty()) {
                            Text(
                                "暂无数据，使用手机一段时间后自动统计",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            usageList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Text(
                                        item.first,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        formatDuration(item.second),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                if (index != usageList.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "数据每 30 分钟刷新一次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { refreshTick++ }
                            .padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "实验功能：涉及隐私权限，请在知情后谨慎开启，随时可关闭。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/** 主动发消息间隔的步进按钮（- / +，步长 5 分钟）。 */
@Composable
private fun IntervalStepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .then(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun PermissionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onGrant)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp)
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Text(
            if (granted) "已授权" else "去授权",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) WeChatGreen else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

// ========== 辅助逻辑 ==========

private fun hasUsageAccess(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

private fun hasNotificationAccess(context: Context): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.isNotificationListenerAccessGranted(ComponentName(context, NotificationListener::class.java))
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

private fun loadMusicApps(context: Context): List<MusicAppInfo> {
    val pm = context.packageManager
    val result = LinkedHashMap<String, String>()
    try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        pm.queryIntentActivities(intent, 0).forEach { ri ->
            val pkg = ri.activityInfo.packageName
            result[pkg] = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }
    } catch (_: Exception) {}

    // 兜底常见音乐 App（CATEGORY_APP_MUSIC 缺失或厂商 ROM 未标记时仍可列出）
    val known = listOf(
        "com.tencent.qqmusic" to "QQ音乐",
        "com.netease.cloudmusic" to "网易云音乐",
        "com.kugou.android" to "酷狗音乐",
        "com.kuwo.player" to "酷我音乐",
        "com.spotify.music" to "Spotify",
        "com.apple.android.music" to "Apple Music",
        "com.bytedance.music" to "汽水音乐",
        "com.migu.music" to "咪咕音乐",
        "com.miui.player" to "小米音乐",
        "com.tencent.weme" to "全民K歌"
    )
    for ((pkg, fallback) in known) {
        if (result.containsKey(pkg)) continue
        try {
            val label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            result[pkg] = label
        } catch (_: Exception) {}
    }
    return result.map { (pkg, label) -> MusicAppInfo(pkg, label) }
}

private fun loadUsageList(context: Context): List<Pair<String, Long>> {
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24 * 3600 * 1000L, now)
        val pm = context.packageManager
        stats
            .filter { it.totalTimeInForeground > 60_000L && it.packageName != context.packageName }
            .map { stat ->
                val label = try {
                    pm.getApplicationInfo(stat.packageName, 0).loadLabel(pm).toString()
                } catch (_: Exception) {
                    stat.packageName
                }
                label to stat.totalTimeInForeground
            }
            .groupBy { it.first }
            .map { (label, list) -> label to list.sumOf { it.second } }
            .sortedByDescending { it.second }
            .take(10)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    if (minutes < 60) return "${minutes}分钟"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "${h}小时" else "${h}小时${m}分"
}
