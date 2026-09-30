package com.wechat.agent.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 用户自定义图片表情：从相册选取图片 + 备注名称/快捷名称，保存为 json 文件
 * （filesDir/emoji_stickers.json），供聊天界面与大模型读取使用。
 */
data class EmojiSticker(
    val name: String,          // 备注名称（聊天框/大模型触发用）
    val shortcut: String = "", // 快捷名称（可选）
    val imagePath: String      // 图片绝对路径（内部存储，退应用不丢失）
)

class EmojiManager(context: Context) {

    private val gson = Gson()
    private val stickersFile: File = File(context.filesDir, "emoji_stickers.json")

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
}
