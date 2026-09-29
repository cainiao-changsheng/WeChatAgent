package com.wechat.agent.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 表情管理：内置表情列表 + 用户自定义表情，持久化存储
 */
class EmojiManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("emoji_manager", Context.MODE_PRIVATE)
    private val gson = Gson()

    val builtinEmojis = listOf(
        "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "🥰",
        "😉", "😎", "🤔", "🤗", "🙄", "😅", "😭", "😢",
        "😡", "🥺", "😳", "😴", "🤤", "😋", "😜", "🤪",
        "👍", "👎", "👌", "🙏", "👏", "💪", "🤝", "✌️",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💖",
        "🔥", "✨", "⭐", "🌹", "🎉", "🎂", "💯", "🌙",
        "☀️", "🌈", "🍀", "🐱", "🐶", "🐰", "🦊", "🐼",
        "🤙", "🫶", "😤", "😩", "😱", "🤯", "🥳", "😇"
    )

    private fun customKey() = "custom_emojis"

    fun getCustomEmojis(): List<String> {
        val json = prefs.getString(customKey(), null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun getAllEmojis(): List<String> = builtinEmojis + getCustomEmojis()

    fun addCustomEmoji(emoji: String): Boolean {
        val trimmed = emoji.trim()
        if (trimmed.isEmpty()) return false
        val current = getCustomEmojis().toMutableList()
        if (current.contains(trimmed)) return false
        current.add(trimmed)
        prefs.edit().putString(customKey(), gson.toJson(current)).apply()
        return true
    }

    fun removeCustomEmoji(emoji: String) {
        val current = getCustomEmojis().toMutableList()
        if (current.remove(emoji)) {
            prefs.edit().putString(customKey(), gson.toJson(current)).apply()
        }
    }
}
