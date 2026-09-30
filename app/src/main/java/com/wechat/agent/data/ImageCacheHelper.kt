package com.wechat.agent.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 图片缓存工具：将任意可读 Uri（content:// 等）复制到应用内部存储，
 * 返回本地绝对路径。退出应用后仍可读取，解决聊天图片丢失问题。
 */
object ImageCacheHelper {

    fun cacheToInternal(context: Context, uriString: String): String? {
        return try {
            // 已是本地路径/file 协议，直接校验可用
            if (uriString.startsWith("file://")) {
                val path = Uri.parse(uriString).path
                if (path != null && File(path).exists()) return path
            } else if (uriString.startsWith("/")) {
                if (File(uriString).exists()) return uriString
            }

            val uri = Uri.parse(uriString)
            val resolver = context.contentResolver
            val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
            val dest = File(dir, UUID.randomUUID().toString().substring(0, 8) + ".jpg")
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.exists() && dest.length() > 0) dest.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }
}
