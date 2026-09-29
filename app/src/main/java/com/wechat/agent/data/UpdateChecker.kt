package com.wechat.agent.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val hasUpdate: Boolean
)

/**
 * 在线更新：检查 GitHub Release 最新版本，下载 APK 并走系统安装器覆盖安装。
 */
object UpdateChecker {

    private const val REPO = "cainiao-changsheng/WeChatAgent"
    private val client = OkHttpClient.Builder().build()

    /** 查询最新 Release 并和当前版本比较；网络/解析失败返回 null。 */
    suspend fun checkLatest(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "WeChatAgent")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").removePrefix("v")
                val notes = json.optString("body", "").take(300)
                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i)
                        val name = a?.optString("name", "") ?: ""
                        if (name.endsWith(".apk")) {
                            downloadUrl = a.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                UpdateInfo(
                    latestVersion = tag,
                    downloadUrl = downloadUrl,
                    releaseNotes = notes,
                    hasUpdate = tag.isNotBlank() && versionCompare(tag, currentVersion) > 0
                )
            }
        }.getOrNull()
    }

    /** 下载 APK 到 cacheDir/apk 目录；失败返回 null。 */
    suspend fun downloadApk(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "apk").apply { mkdirs() }
            val target = File(dir, "wechat_agent_update.apk")
            target.delete()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "WeChatAgent")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                body.byteStream().use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
            }
            target
        }.getOrNull()
    }

    /** 通过系统安装器安装 APK（版本号不低于当前即可覆盖安装，无需卸载旧版）。 */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun versionCompare(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
