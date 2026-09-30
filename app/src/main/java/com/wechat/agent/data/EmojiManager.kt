package com.wechat.agent.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 用户自定义图片表情：从相册选取图片 + 备注名称/快捷名称，保存为 json 文件
 * （filesDir/emoji_stickers.json），供聊天界面与大模型读取使用。
 * 支持按参考格式（zip 内含 custom_stickers.json + 图片）导入/导出：
 * custom_stickers.json 为数组，每项 { description: 表情描述, fileName: 图片文件名 }。
 */
data class EmojiSticker(
    val name: String,          // 备注名称（聊天框/大模型触发用）
    val shortcut: String = "", // 快捷名称（可选）
    val imagePath: String      // 图片绝对路径（内部存储，退应用不丢失）
)

/** 导出包内 json 字段（与参考 zip 的 custom_stickers.json 保持一致）。 */
private data class StickerExportItem(
    val description: String,
    val fileName: String
)

class EmojiManager(context: Context) {

    private val gson = Gson()
    private val stickersFile: File = File(context.filesDir, "emoji_stickers.json")
    private val stickersImageDir: File =
        File(context.filesDir, "emoji_stickers_images").apply { mkdirs() }

    fun getStickersFilePath(): String = stickersFile.absolutePath

    @Synchronized
    fun loadStickers(): List<EmojiSticker> {
        if (!stickersFile.exists()) return emptyList()
        return try {
            val json = stickersFile.readText()
            val list = gson.fromJson<List<EmojiSticker>>(
                json, object : TypeToken<List<EmojiSticker>>() {}.type
            ) ?: emptyList()
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveStickers(list: List<EmojiSticker>) {
        try {
            stickersFile.parentFile?.mkdirs()
            stickersFile.writeText(gson.toJson(list))
        } catch (_: Exception) {}
    }

    fun getAllStickers(): List<EmojiSticker> = loadStickers()

    fun addSticker(imagePath: String, name: String, shortcut: String = ""): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return false
        val current = loadStickers().toMutableList()
        if (current.any { it.name == trimmedName }) return false
        current.add(EmojiSticker(name = trimmedName, shortcut = shortcut.trim(), imagePath = imagePath))
        saveStickers(current)
        return true
    }

    fun removeSticker(name: String) {
        val current = loadStickers().toMutableList()
        if (current.removeAll { it.name == name }) saveStickers(current)
    }

    /** 供大模型读取的表情 json 内容（名称 + 快捷名 + 图片路径）。 */
    fun getStickersJson(): String = gson.toJson(loadStickers())

    /** 按名称或快捷名称精确匹配表情。 */
    fun findSticker(token: String): EmojiSticker? {
        val key = token.trim()
        if (key.isEmpty()) return null
        return loadStickers().firstOrNull {
            it.name == key || (it.shortcut.isNotEmpty() && it.shortcut == key)
        }
    }

    /**
     * 按语义关键词模糊搜索表情（名称/快捷名包含任一关键词即命中）。
     * 供大模型 search_sticker 工具调用：返回少量候选，避免全量注入表情清单。
     */
    fun searchStickers(keywords: String, maxResults: Int = 6): List<EmojiSticker> {
        val kw = keywords.trim().lowercase()
            .split(Regex("[，,、;；\\s]+"))
            .filter { it.isNotBlank() }
        if (kw.isEmpty()) return emptyList()
        return loadStickers().filter { s ->
            val name = s.name.lowercase()
            val shortcut = s.shortcut.lowercase()
            kw.any { name.contains(it) || (shortcut.isNotEmpty() && shortcut.contains(it)) }
        }.take(maxResults)
    }

    /**
     * 导出当前表情包为 zip（参考格式）：custom_stickers.json + 各表情图片。
     * json 每项为 { description, fileName }，fileName 与 zip 内图片文件名一致。
     */
    fun exportToZip(output: OutputStream): Boolean {
        return try {
            val stickers = loadStickers()
            if (stickers.isEmpty()) return false
            ZipOutputStream(output.buffered()).use { zos ->
                val items = stickers.map { s ->
                    val fileName = File(s.imagePath).name
                    StickerExportItem(description = s.name, fileName = fileName)
                }
                zos.putNextEntry(ZipEntry("custom_stickers.json"))
                zos.write(gson.toJson(items).toByteArray())
                zos.closeEntry()
                stickers.forEach { s ->
                    val file = File(s.imagePath)
                    if (file.exists()) {
                        zos.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从 zip（参考格式）导入表情：读取 custom_stickers.json，
     * 将对应图片复制到内部存储并逐个 addSticker。
     * @return (成功数, 跳过数)
     */
    fun importFromZip(input: InputStream): Pair<Int, Int> {
        return try {
            var success = 0
            var skipped = 0
            val images = HashMap<String, ByteArray>()
            var manifest: String? = null

            ZipInputStream(input.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name == "custom_stickers.json" -> manifest = zis.readBytes().toString(Charsets.UTF_8)
                            else -> images[entry.name] = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifestJson = manifest ?: return 0 to 0
            val items = try {
                gson.fromJson<List<StickerExportItem>>(
                    manifestJson, object : TypeToken<List<StickerExportItem>>() {}.type
                ) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            items.forEach { item ->
                val desc = item.description.trim()
                val data = images[item.fileName]
                if (desc.isEmpty() || data == null || data.isEmpty()) {
                    skipped++
                    return@forEach
                }
                val safeName = item.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                var dest = File(stickersImageDir, safeName)
                var counter = 1
                while (dest.exists()) {
                    dest = File(stickersImageDir, "${counter}_$safeName")
                    counter++
                }
                try {
                    dest.writeBytes(data)
                    if (addSticker(dest.absolutePath, desc)) success++
                    else skipped++
                } catch (_: Exception) {
                    skipped++
                }
            }
            success to skipped
        } catch (_: Exception) {
            0 to 0
        }
    }

    /** 导入用：将 zip 内图片字节落盘为本地文件（避免与 importFromZip 的解压逻辑重复）。 */
    fun saveImportedImage(bytes: ByteArray, originalName: String): String? {
        return try {
            val safeName = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "sticker_${UUID.randomUUID().toString().substring(0, 8)}.png" }
            var dest = File(stickersImageDir, safeName)
            var counter = 1
            while (dest.exists()) {
                dest = File(stickersImageDir, "${counter}_$safeName")
                counter++
            }
            dest.writeBytes(bytes)
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
