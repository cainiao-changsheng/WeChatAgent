package com.wechat.agent.data.speech

import android.content.Context
import android.os.Environment
import com.wechat.agent.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * 语音模型下载管理器。
 * - 断点续传：Range 请求 + .part 临时文件，覆盖安装/中断后可续传；
 * - SHA256 校验：sha256 为空时跳过（占位阶段），非空时严格校验；
 * - 解压：支持 zip 与 tar.bz2（Kokoro/KittenTTS/VITS 均为 tar.bz2，Vosk 为 zip）；
 * - 存储预检：可用空间不足时直接失败，避免下到一半写满。
 * 模型统一存放在 filesDir/models 下，覆盖安装不会丢失（按需下载缓存）。
 */
class ModelDownloadManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: ModelDownloadManager? = null
        fun get(context: Context): ModelDownloadManager =
            instance ?: synchronized(this) {
                instance ?: ModelDownloadManager(context.applicationContext).also { instance = it }
            }

        /** 模型根目录：filesDir/models，覆盖安装保留。 */
        fun modelsRoot(context: Context): File = File(context.filesDir, "models").apply { mkdirs() }

        private fun sha256Of(file: File): String? = try {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = fis.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { null }
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val modelId: String, val progress: Float) : DownloadState() // 0..1
        data class Extracting(val modelId: String) : DownloadState()
        data class Verifying(val modelId: String) : DownloadState()
        data class Done(val modelId: String) : DownloadState()
        data class Error(val modelId: String, val message: String) : DownloadState()
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** 下载单个模型（串行：一次只下载一个，避免并发争抢磁盘与带宽）。 */
    fun download(model: SpeechModel, onFinished: (Boolean, String) -> Unit = { _, _ -> }) {
        if (job?.isActive == true) {
            onFinished(false, "已有下载任务进行中")
            return
        }
        job = scope.launch {
            try {
                downloadInternal(model)
                _state.value = DownloadState.Done(model.id)
                onFinished(true, "")
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                _state.value = DownloadState.Error(model.id, msg)
                onFinished(false, msg)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = DownloadState.Idle
    }

    private suspend fun downloadInternal(model: SpeechModel) {
        val root = modelsRoot(context)
        val target = File(root, model.targetDir)
        if (ModelCatalog.isReady(model, root)) return // 已就绪

        // 立即进入下载态，确保点击“下载基础语音包”后 UI 立刻切换到进度展示，
        // 而不是在建立连接（最长 connectTimeout 30s）期间一直停留在 Idle。
        _state.value = DownloadState.Downloading(model.id, 0f)

        val tmp = File(context.cacheDir, model.id + ".part")
        val tmpBak = File(context.cacheDir, model.id + ".part.old")
        if (tmpBak.exists()) tmpBak.delete()

        // 存储预检
        val free = File(Environment.getDataDirectory().absolutePath).usableSpace
        val need = model.sizeBytes
        if (need > 0 && free < need) {
            throw IllegalStateException("存储空间不足：需约 ${need / 1024 / 1024}MB，可用 ${free / 1024 / 1024}MB")
        }

        // 断点续传下载
        val existed = tmp.length()
        val req = Request.Builder()
            .url(model.url)
            .header("Range", "bytes=$existed-")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("下载失败 HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IllegalStateException("空响应体")
            val total = if (resp.code == 206) {
                // 服务器可能不支持 Range；Content-Range: bytes start-end/total
                val cr = resp.header("Content-Range")
                cr?.substringAfter("/")?.toLongOrNull() ?: body.contentLength()
            } else {
                existed + body.contentLength()
            }
            RandomAccessFile(tmp, "rw").use { raf ->
                raf.seek(existed)
                val buf = ByteArray(64 * 1024)
                var written = existed
                val input = body.byteStream()
                while (true) {
                    if (!coroutineContext.isActive) throw java.util.concurrent.CancellationException()
                    val n = input.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    written += n
                    if (total > 0) {
                        _state.value = DownloadState.Downloading(model.id, (written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }

        // 校验 SHA256（空则跳过占位）
        if (model.sha256.isNotBlank()) {
            _state.value = DownloadState.Verifying(model.id)
            val real = sha256Of(tmp) ?: throw IllegalStateException("计算 SHA256 失败")
            if (!real.equals(model.sha256, ignoreCase = true)) {
                throw IllegalStateException("SHA256 校验失败：期望 ${model.sha256.take(12)}…，实际 $real")
            }
        }

        // 解压
        _state.value = DownloadState.Extracting(model.id)
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        when (model.archive) {
            "zip" -> extractZip(tmp, target)
            "tar.bz2" -> extractTarBz2(tmp, target)
            else -> throw IllegalStateException("不支持的压缩格式 ${model.archive}")
        }
        tmp.delete()
    }

    private fun extractZip(src: File, dst: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(src))).use { zis ->
            val names = mutableListOf<String>()
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) names.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
            val top = commonTopLevel(names)
            ZipInputStream(BufferedInputStream(FileInputStream(src))).use { zis2 ->
                var e = zis2.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val outFile = sanitize(dst, stripTop(e.name, top))
                        outFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(outFile)).use { out -> zis2.copyTo(out, 64 * 1024) }
                    }
                    zis2.closeEntry()
                    e = zis2.nextEntry
                }
            }
        }
    }

    private fun extractTarBz2(src: File, dst: File) {
        TarArchiveInputStream(
            BufferedInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(src))))
        ).use { tis ->
            val names = mutableListOf<String>()
            var entry = tis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) names.add(entry.name)
                entry = tis.nextEntry
            }
            val top = commonTopLevel(names)
            TarArchiveInputStream(
                BufferedInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(src))))
            ).use { tis2 ->
                var e = tis2.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val outFile = sanitize(dst, stripTop(e.name, top))
                        outFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(outFile)).use { out -> tis2.copyTo(out, 64 * 1024) }
                    }
                    e = tis2.nextEntry
                }
            }
        }
    }

    /** 若所有条目共享同一顶层目录，返回该前缀（如 "vosk-model-small-cn-0.22/"），否则返回 ""。 */
    private fun commonTopLevel(names: List<String>): String {
        if (names.isEmpty()) return ""
        var top: String? = null
        for (name in names) {
            val first = name.replace("\\", "/").substringBefore('/')
            if (first.isBlank()) continue
            if (top == null) top = first
            else if (top != first) return ""
        }
        return (top ?: "") + "/"
    }

    private fun stripTop(name: String, top: String): String =
        if (top.isNotEmpty() && name.startsWith(top)) name.removePrefix(top) else name

    /** 防路径穿越：只保留相对路径，剥离 ../。 */
    private fun sanitize(dst: File, name: String): File {
        val clean = name.replace("\\", "/")
            .split("/")
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
        return File(dst, clean)
    }

    suspend fun downloadSequential(models: List<SpeechModel>) {
        for (m in models) {
            if (ModelCatalog.isReady(m, modelsRoot(context))) continue
            try {
                downloadInternal(m)
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                _state.value = DownloadState.Error(m.id, msg)
                throw e
            }
        }
        // 全部完成后复位为 Idle，配合 SpeechManager.refresh() 让 UI 显示“已就绪”。
        _state.value = DownloadState.Idle
    }
}
