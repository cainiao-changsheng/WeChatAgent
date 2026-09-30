package com.wechat.agent.ui.screens

import android.content.Context
import android.util.Base64
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wechat.agent.data.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val GITHUB_OWNER = "cainiao-changsheng"
private const val GITHUB_REPO = "WeChatAgent"
private const val GITHUB_BRANCH = "master"
private const val PREFS_NAME = "debug_prefs"
private const val KEY_TOKEN = "github_token"

/** 读取已保存的 GitHub Token。 */
internal fun getSavedGitHubToken(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_TOKEN, "") ?: ""
}

/** 保存 GitHub Token。 */
internal fun saveGitHubToken(context: Context, token: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_TOKEN, token.trim()).apply()
}

/** 上传日志内容到 GitHub 仓库，返回成功时的上传路径。 */
private fun uploadToGithub(token: String, content: String, filename: String): String {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    val path = "logs/$filename"
    val body = JSONObject()
        .put("message", "upload app log $filename")
        .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        .put("branch", GITHUB_BRANCH)
        .toString()
        .toRequestBody("application/json; charset=utf-8".toMediaType())
    val req = Request.Builder()
        .url("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/contents/$path")
        .put(body)
        .header("Authorization", "token $token")
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "WeChatAgent-debug")
        .build()
    client.newCall(req).execute().use { resp ->
        val text = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            val reason = try {
                JSONObject(text).optString("message", "HTTP ${resp.code}")
            } catch (_: Exception) { "HTTP ${resp.code}" }
            throw IOException("上传失败: $reason")
        }
        val sha = try { JSONObject(text).optString("sha", "") } catch (_: Exception) { "" }
        return "$path (sha: ${sha.take(7)})"
    }
}

/**
 * 日志详情页：展示运行时日志，底部上传按钮可将日志上传到 GitHub 仓库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf(listOf<String>()) }
    var uploading by remember { mutableStateOf(false) }
    var uploadResult by remember { mutableStateOf<String?>(null) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf(getSavedGitHubToken(context)) }

    LaunchedEffect(Unit) {
        lines = AppLogger.recentLines().ifEmpty {
            AppLogger.fileContent().split("\n").filter { it.isNotBlank() }.takeLast(800)
        }
    }

    fun doUpload() {
        val token = getSavedGitHubToken(context)
        if (token.isBlank()) {
            showTokenDialog = true
            return
        }
        scope.launch {
            uploading = true
            uploadResult = null
            uploadError = null
            val result = withContext(Dispatchers.IO) {
                try {
                    val content = AppLogger.fileContent().ifBlank { lines.joinToString("\n") }
                    val filename = "app_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
                    uploadToGithub(token, content, filename)
                } catch (e: Exception) {
                    "ERR:" + (e.message ?: "未知错误")
                }
            }
            uploading = false
            if (result.startsWith("ERR:")) uploadError = result.removePrefix("ERR:")
            else uploadResult = result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("日志", fontWeight = FontWeight.Medium)
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
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(lines) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (line.contains("[ChatVM]")) Color(0xFF2E7D32)
                        else if (line.contains("ERR") || line.contains("异常") || line.contains("失败")) Color(0xFFC62828)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    )
                }
                if (lines.isEmpty()) {
                    item {
                        Text(
                            "暂无日志",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // 底部：上传按钮 + 状态
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (uploadResult != null) {
                        Text(
                            "已上传：$uploadResult",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    if (uploadError != null) {
                        Text(
                            uploadError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Button(
                        onClick = { doUpload() },
                        enabled = !uploading
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (uploading) "上传中..." else "上传到 GitHub")
                    }
                    Text(
                        "共 ${lines.size} 行 · ${AppLogger.fileSize()} 字节",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("GitHub Token") },
            text = {
                Column {
                    Text(
                        "首次上传需填写 GitHub Personal Access Token（仓库权限），将保存到本机调试配置。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("ghp_xxx") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveGitHubToken(context, tokenInput)
                        showTokenDialog = false
                        doUpload()
                    }
                ) { Text("保存并上传") }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) { Text("取消") }
            }
        )
    }
}
