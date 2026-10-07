package com.wechat.agent.data.speech

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 语音模型清单中的单个模型条目（与 assets/models.json 对应）。
 * sizeBytes/sha256 目前为占位（0/空），真实值在实测下载后回填；
 * sha256 为空时跳过校验，避免首版因占位哈希误判。
 */
data class SpeechModel(
    val id: String,
    val type: String,          // "asr" | "tts"
    val name: String,
    val version: String,
    val required: Boolean,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val archive: String,       // "zip" | "tar.bz2"
    val targetDir: String,
    val description: String
)

object ModelCatalog {

    fun load(context: Context): List<SpeechModel> {
        val raw = context.assets.open("models.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val arr = root.getJSONArray("models")
        val list = mutableListOf<SpeechModel>()
        for (i in 0 until arr.length()) {
            val o: JSONObject = arr.getJSONObject(i)
            list += SpeechModel(
                id = o.getString("id"),
                type = o.getString("type"),
                name = o.getString("name"),
                version = o.optString("version", ""),
                required = o.optBoolean("required", false),
                url = o.getString("url"),
                sha256 = o.optString("sha256", ""),
                sizeBytes = o.optLong("sizeBytes", 0L),
                archive = o.optString("archive", "zip"),
                targetDir = o.getString("targetDir"),
                description = o.optString("description", "")
            )
        }
        return list
    }

    fun requiredModels(all: List<SpeechModel>): List<SpeechModel> = all.filter { it.required }

    /** 模型是否已就绪（目标目录存在且非空）。 */
    fun isReady(model: SpeechModel, modelsRoot: java.io.File): Boolean {
        val dir = java.io.File(modelsRoot, model.targetDir)
        return dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }
}
