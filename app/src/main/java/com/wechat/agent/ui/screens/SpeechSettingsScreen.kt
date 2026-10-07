package com.wechat.agent.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wechat.agent.data.SettingsManager
import com.wechat.agent.data.speech.ModelDownloadManager
import com.wechat.agent.data.speech.ModelCatalog
import com.wechat.agent.data.speech.SpeechManager
import com.wechat.agent.data.speech.SpeechModel
import com.wechat.agent.ui.theme.WeChatGreen
import java.io.File

/**
 * 语音功能设置页（阶段1）：
 * - 语音输入 / 语音回复开关
 * - 基础语音包（Vosk ASR + VITS TTS）下载与进度
 * - 录音权限引导
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechSettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    val advanced by settings.advancedSettings.collectAsState()
    val speech = remember { SpeechManager.get(context) }
    val speechState by speech.state.collectAsState()
    val downloadState by speech.downloadManager.state.collectAsState()

    LaunchedEffect(Unit) { speech.refresh() }

    val allModels = remember { ModelCatalog.load(context) }
    val vosk = allModels.firstOrNull { it.id == "vosk-cn" }
    val vits = allModels.firstOrNull { it.id == "vits-zh" }
    val modelsRoot = remember { ModelDownloadManager.modelsRoot(context) }

    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("语音功能", fontWeight = FontWeight.Medium)
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 开关卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ToggleRow(
                        icon = { Icon(Icons.Default.Mic, contentDescription = null, tint = WeChatGreen) },
                        title = "语音输入",
                        subtitle = "聊天输入栏“+”面板中启用语音输入（按住说话）",
                        checked = advanced.voiceInput,
                        onCheckedChange = { settings.saveAdvancedSettings(advanced.copy(voiceInput = it)) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    ToggleRow(
                        icon = { Icon(Icons.Default.VolumeUp, contentDescription = null, tint = WeChatGreen) },
                        title = "语音回复",
                        subtitle = "AI 回复完成后自动朗读（需下载 TTS 模型）",
                        checked = advanced.voiceReply,
                        onCheckedChange = { settings.saveAdvancedSettings(advanced.copy(voiceReply = it)) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    ToggleRow(
                        icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = WeChatGreen) },
                        title = "录音权限",
                        subtitle = if (micGranted) "已授权" else "未授权，点击授权",
                        checked = micGranted,
                        onCheckedChange = { if (it && !micGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 模型下载卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("语音模型", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "首次使用需下载基础语音包（语音识别 + 语音合成）。模型缓存在应用内部目录，覆盖安装 APK 不会丢失，无需重复下载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ModelRow(vosk, modelsRoot)
                    Spacer(modifier = Modifier.height(8.dp))
                    ModelRow(vits, modelsRoot)

                    Spacer(modifier = Modifier.height(12.dp))
                    when (val ds = downloadState) {
                        is ModelDownloadManager.DownloadState.Idle -> {
                            if (speechState.modelsReady) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WeChatGreen)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("基础语音包已就绪", color = WeChatGreen)
                                }
                            } else {
                                OutlinedButton(onClick = { speech.downloadBaseModels() }) {
                                    Text("下载基础语音包")
                                }
                            }
                        }
                        is ModelDownloadManager.DownloadState.Downloading -> {
                            Column {
                                Text("下载中 ${(ds.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { ds.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        is ModelDownloadManager.DownloadState.Extracting -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("解压模型中…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ModelDownloadManager.DownloadState.Verifying -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("校验中…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ModelDownloadManager.DownloadState.Done -> {
                            LaunchedEffect(ds.modelId) { speech.refresh() }
                            Text("下载完成", color = WeChatGreen)
                        }
                        is ModelDownloadManager.DownloadState.Error -> {
                            Text("下载失败：${ds.message}", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(onClick = { speech.downloadBaseModels() }) {
                                Text("重试")
                            }
                        }
                    }
                    if (speechState.lastError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            speechState.lastError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: SpeechModel?, modelsRoot: File) {
    if (model == null) return
    val ready = ModelCatalog.isReady(model, modelsRoot)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(model.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (ready) "已就绪" else model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        if (ready) {
            Icon(Icons.Default.CheckCircle, contentDescription = "就绪", tint = WeChatGreen)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
