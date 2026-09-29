package com.wechat.agent.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wechat.agent.data.UpdateChecker
import com.wechat.agent.data.UpdateInfo
import com.wechat.agent.ui.theme.WeChatGreen
import kotlinx.coroutines.launch

/**
 * 检查更新页：查询 GitHub 最新 Release，下载 APK 后走系统安装器覆盖安装。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCheckScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var currentVersion by remember {
        mutableStateOf(
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            }.getOrNull() ?: "1.0"
        )
    }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checked by remember { mutableStateOf(false) }
    var checkFailed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("检查更新", fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = WeChatGreen,
                    modifier = Modifier.padding(0.dp)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前版本：v$currentVersion",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)

                    when {
                        checking || downloading -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (checking) "正在检查最新版本…" else "正在下载安装包…",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        checkFailed -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("检查失败：无法连接更新服务器，请稍后重试",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error)
                        }

                        updateInfo != null && updateInfo!!.hasUpdate -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("发现新版本：v${updateInfo!!.latestVersion}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = WeChatGreen, fontWeight = FontWeight.Bold)
                            if (updateInfo!!.releaseNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("更新说明：${updateInfo!!.releaseNotes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val url = updateInfo!!.downloadUrl
                                    if (url.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("未找到可下载的 APK 安装包") }
                                        return@Button
                                    }
                                    downloading = true
                                    scope.launch {
                                        val file = UpdateChecker.downloadApk(context, url)
                                        downloading = false
                                        if (file != null) {
                                            if (UpdateChecker.installApk(context, file)) {
                                                snackbarHostState.showSnackbar("已拉起安装器，确认后即可覆盖安装")
                                            } else {
                                                snackbarHostState.showSnackbar("拉起安装器失败，请检查安装权限")
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("下载失败，请稍后重试")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen),
                                enabled = !downloading
                            ) {
                                Text("下载并安装（无需卸载旧版）", fontWeight = FontWeight.Medium)
                            }
                        }

                        updateInfo != null -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("已是最新版本",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }

                        else -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("点击下方按钮检查 GitHub 上的最新版本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    checking = true
                    checkFailed = false
                    scope.launch {
                        val info = UpdateChecker.checkLatest(currentVersion)
                        checking = false
                        if (info == null) {
                            checkFailed = true
                            updateInfo = null
                        } else {
                            checkFailed = false
                            updateInfo = info
                        }
                        checked = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen.copy(alpha = 0.9f)),
                enabled = !checking && !downloading
            ) {
                Text(if (checked && updateInfo == null && !checkFailed) "重新检查" else "检查更新", fontWeight = FontWeight.Medium)
            }
        }
    }
}
