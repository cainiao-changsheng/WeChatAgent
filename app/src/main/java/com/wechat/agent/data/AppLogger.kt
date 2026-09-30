package com.wechat.agent.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用运行时日志记录器：追加写入 filesDir/logs/app.log，
 * 同时保留内存最近 N 条供调试页直接展示。
 */
object AppLogger {
    private const val MAX_MEMORY_LINES = 800
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024 // 2MB 滚动截断

    private var logFile: File? = null
    private val memoryLines = ArrayDeque<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** 在 Application 启动时初始化一次。 */
    fun init(context: Context) {
        if (logFile != null) return
        val dir = File(context.filesDir, "logs")
        dir.mkdirs()
        logFile = File(dir, "app.log")
    }

    /** 记录一条日志（线程安全）。 */
    @Synchronized
    fun log(tag: String, message: String) {
        val line = "[${dateFormat.format(Date())}] [$tag] $message"
        synchronized(memoryLines) {
            memoryLines.addLast(line)
            while (memoryLines.size > MAX_MEMORY_LINES) memoryLines.removeFirst()
        }
        try {
            val f = logFile ?: return
            f.appendText(line + "\n")
            if (f.length() > MAX_FILE_BYTES) {
                // 简单滚动：截断为后半段，避免文件无限膨胀
                val content = f.readText()
                f.writeText(content.substring(content.length / 2))
            }
        } catch (_: Exception) {}
    }

    /** 读取内存中最近的日志行。 */
    @Synchronized
    fun recentLines(): List<String> = synchronized(memoryLines) { memoryLines.toList() }

    /** 读取完整文件内容（用于上传）。 */
    @Synchronized
    fun fileContent(): String {
        return try { logFile?.readText() ?: "" } catch (_: Exception) { "" }
    }

    /** 当前日志文件大小。 */
    @Synchronized
    fun fileSize(): Long = try { logFile?.length() ?: 0L } catch (_: Exception) { 0L }

    /** 清空日志。 */
    @Synchronized
    fun clear() {
        synchronized(memoryLines) { memoryLines.clear() }
        try { logFile?.writeText("") } catch (_: Exception) {}
    }
}
