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
import java.util.concurrent.TimeUnit


data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val hasUpdate: Boolean
)

/** 在线更新：仅接受 GitHub 官方来源的 APK，并在安装前做基本文件完整性检查。 */
object UpdateChecker {
    private const val REPO = "cainiao-changsheng/WeChatAgent"
    private const val BRANCH = "master"
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L
    private val allowedHosts = setOf(
        "github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatest(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            fetchReleaseFromApi(currentVersion) ?: fetchUpdateInfoFromRaw(currentVersion)
        }.getOrNull()
    }

    private suspend fun fetchReleaseFromApi(currentVersion: String): UpdateInfo? = runCatching {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "WeChatAgent")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").removePrefix("v")
            val notes = json.optString("body", "").take(300)
            var downloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val candidate = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && isAllowedDownloadUrl(candidate)) {
                        downloadUrl = candidate
                        break
                    }
                }
            }
            if (tag.isBlank()) return@runCatching null
            UpdateInfo(tag, downloadUrl, notes, versionCompare(tag, currentVersion) > 0)
        }
    }.getOrNull()

    private suspend fun fetchUpdateInfoFromRaw(currentVersion: String): UpdateInfo? = runCatching {
        val req = Request.Builder()
            .url("https://raw.githubusercontent.com/$REPO/$BRANCH/update_info.json")
            .header("User-Agent", "WeChatAgent")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val tag = json.optString("version", "").removePrefix("v")
            val downloadUrl = json.optString("download_url", "")
            val notes = json.optString("notes", "").take(300)
            if (tag.isBlank() || !isAllowedDownloadUrl(downloadUrl)) return@runCatching null
            UpdateInfo(tag, downloadUrl, notes, versionCompare(tag, currentVersion) > 0)
        }
    }.getOrNull()

    suspend fun downloadApk(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        if (!isAllowedDownloadUrl(url)) return@withContext null
        runCatching {
            val dir = File(context.cacheDir, "apk").apply { mkdirs() }
            val target = File(dir, "wechat_agent_update.apk")
            target.delete()
            val req = Request.Builder().url(url).header("User-Agent", "WeChatAgent").build()
            var downloaded = false
            client.newCall(req).execute().use { resp ->
                val body = resp.body
                val sourceAllowed = isAllowedDownloadUrl(resp.request.url.toString())
                if (resp.isSuccessful && sourceAllowed && body != null && body.contentLength() <= MAX_APK_BYTES) {
                    body.byteStream().use { input ->
                        target.outputStream().use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_APK_BYTES) break
                                out.write(buffer, 0, count)
                            }
                            downloaded = total in 1..MAX_APK_BYTES
                        }
                    }
                }
            }
            if (!downloaded || !isLikelyApk(target)) {
                target.delete()
                null
            } else {
                target
            }
        }.getOrNull()
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.isFile || apkFile.length() <= 0L || !isLikelyApk(apkFile)) return false
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

    private fun isAllowedDownloadUrl(value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        val host = uri.host?.lowercase() ?: return false
        uri.scheme == "https" && uri.userInfo == null && uri.query == null &&
            uri.fragment == null && host in allowedHosts
    }.getOrDefault(false)

    private fun isLikelyApk(file: File): Boolean = runCatching {
        if (file.length() < 4L || file.length() > MAX_APK_BYTES) return false
        file.inputStream().use { input ->
            input.read() == 'P'.code && input.read() == 'K'.code &&
                input.read() == 3 && input.read() == 4
        }
    }.getOrDefault(false)

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
