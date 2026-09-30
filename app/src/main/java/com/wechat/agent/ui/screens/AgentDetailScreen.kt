package com.wechat.agent.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wechat.agent.data.MemoryManager
import com.wechat.agent.ui.theme.WeChatGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    agentId: String,
    agentName: String,
    agentGender: String,
    agentAge: String,
    agentPersona: String,
    agentGlobalSettings: String,
    agentAvatar: String,
    agentAvatarUri: String,
    backupIntervalMinutes: Int,
    backupOverwrite: Boolean,
    backupOnExit: Boolean,
    onBackupConfigChange: (intervalMinutes: Int, overwrite: Boolean, onExit: Boolean) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSendMessage: () -> Unit,
    onClearMemory: () -> Unit,
    onDeleteAgent: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memoryManager = remember {
        MemoryManager(context.applicationContext).also { it.setActiveAgent(agentId) }
    }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    val dangerRed = Color(0xFFE64340)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(agentName, fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Settings, contentDescription = "设定", tint = WeChatGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onSendMessage,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
                ) { Text("发消息", fontWeight = FontWeight.Medium) }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(88.dp).clip(CircleShape)
                        .background(WeChatGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (agentAvatarUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(agentAvatarUri)).crossfade(true).build(),
                            contentDescription = "",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(agentAvatar.ifEmpty { "🤖" }, fontSize = MaterialTheme.typography.displayLarge.fontSize)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = agentName,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ProfileRow(label = "性别", value = agentGender)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ProfileRow(label = "年龄", value = agentAge)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ProfileRow(label = "人设描述", value = agentPersona.ifEmpty { "无" })
                    if (agentGlobalSettings.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ProfileRow(label = "性格特点", value = agentGlobalSettings)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 记忆库 =====
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🧠 记忆库", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("每个 AI 好友拥有独立的记忆库文件，支持导出/导入 .json 或 .md 格式", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val exportJsonLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/json")
                    ) { uri: Uri? ->
                        uri?.let {
                            scope.launch {
                                runCatching {
                                    context.contentResolver.openOutputStream(it)?.use { out ->
                                        out.write(memoryManager.exportJson().toByteArray())
                                    }
                                }
                                snackbarHostState.showSnackbar("记忆已导出为 JSON")
                            }
                        }
                    }

                    val exportMdLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("text/markdown")
                    ) { uri: Uri? ->
                        uri?.let {
                            scope.launch {
                                runCatching {
                                    context.contentResolver.openOutputStream(it)?.use { out ->
                                        out.write(memoryManager.exportMarkdown().toByteArray())
                                    }
                                }
                                snackbarHostState.showSnackbar("记忆已导出为 Markdown")
                            }
                        }
                    }

                    val importLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            scope.launch {
                                val raw = runCatching {
                                    context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
                                }.getOrNull() ?: ""
                                val content = raw.removePrefix("\uFEFF").trimStart()
                                val ok = when {
                                    content.isBlank() -> false
                                    content.startsWith("{") -> memoryManager.importJson(content)
                                    content.startsWith("[") -> memoryManager.importJson(content)
                                    else -> memoryManager.importMarkdown(content)
                                }
                                snackbarHostState.showSnackbar(
                                    if (ok) "记忆导入成功" else "导入失败：无法识别的格式"
                                )
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportJsonLauncher.launch("agent_memory.json") },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
                        ) { Text("导出 .json", fontWeight = FontWeight.Medium) }
                        Button(
                            onClick = { exportMdLauncher.launch("agent_memory.md") },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen.copy(alpha = 0.8f))
                        ) { Text("导出 .md", fontWeight = FontWeight.Medium) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { importLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) { Text("导入记忆（自动识别 json / md）", fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface) }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("导入会覆盖当前角色记忆，请谨慎操作", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 自动备份 =====
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🛡 自动备份", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("自动备份当前角色的记忆库文件，备份保存在应用内部", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    BackupToggleRow(
                        title = "自动备份",
                        subtitle = "按下方间隔自动备份记忆库",
                        checked = backupIntervalMinutes > 0,
                        onCheckedChange = { enabled ->
                            onBackupConfigChange(if (enabled) 60 else 0, backupOverwrite, backupOnExit)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("备份间隔", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                when (backupIntervalMinutes) {
                                    0 -> "未开启定时备份"
                                    15 -> "每 15 分钟"
                                    30 -> "每 30 分钟"
                                    60 -> "每小时"
                                    180 -> "每 3 小时"
                                    720 -> "每 12 小时"
                                    else -> "每 $backupIntervalMinutes 分钟"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        BackupIntervalSelector(
                            interval = backupIntervalMinutes,
                            onSelect = { minutes -> onBackupConfigChange(minutes, backupOverwrite, backupOnExit) }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    BackupToggleRow(
                        title = "覆盖旧备份",
                        subtitle = "开启后每次备份覆盖上一份，否则按时间生成多份",
                        checked = backupOverwrite,
                        onCheckedChange = { overwrite ->
                            onBackupConfigChange(backupIntervalMinutes, overwrite, backupOnExit)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    BackupToggleRow(
                        title = "退出聊天时备份",
                        subtitle = "离开聊天窗口时自动备份当前记忆",
                        checked = backupOnExit,
                        onCheckedChange = { onExit ->
                            onBackupConfigChange(backupIntervalMinutes, backupOverwrite, onExit)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 危险操作：清除记忆 / 删除好友 =====
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { confirmAction = "clear" },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
                ) { Text("清除记忆", color = dangerRed, fontWeight = FontWeight.Medium) }
                Button(
                    onClick = { confirmAction = "delete" },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
                ) { Text("删除好友", color = dangerRed, fontWeight = FontWeight.Medium) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "清除记忆将删除该好友的全部记忆数据；删除好友将移除好友及其全部聊天与记忆，操作均无法恢复",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = dangerRed.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "点击右上角设置可更改 AI 好友设定",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ===== 确认弹窗：清除记忆 / 删除好友 =====
    confirmAction?.let { action ->
        val isDelete = action == "delete"
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (isDelete) "删除好友" else "清除记忆", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isDelete) "确定要删除该好友吗？删除后将移除该好友及其全部聊天记录和记忆数据，该操作无法恢复。"
                    else "确定要清除该好友的全部记忆吗？清除后记忆数据无法恢复，角色将从零开始认识你。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAction = null
                        if (isDelete) {
                            onDeleteAgent()
                        } else {
                            scope.launch {
                                memoryManager.clearMemory()
                                snackbarHostState.showSnackbar("已清除该好友的记忆")
                            }
                        }
                    }
                ) { Text("确认", color = dangerRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BackupToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BackupIntervalSelector(
    interval: Int,
    onSelect: (Int) -> Unit
) {
    val options = listOf(15, 30, 60, 180, 720)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { minutes ->
            val selected = interval == minutes
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) WeChatGreen.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .then(
                        if (selected) Modifier.border(1.5.dp, WeChatGreen, RoundedCornerShape(14.dp))
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    )
                    .clickable { onSelect(minutes) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (minutes) {
                        15 -> "15分"
                        30 -> "30分"
                        60 -> "1时"
                        180 -> "3时"
                        else -> "12时"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) WeChatGreen else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
