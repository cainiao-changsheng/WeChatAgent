package com.wechat.agent.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * "发现"页观察记录：以全知全能观察者视角记录好友的客观行为（时间戳 + 行为描述）。
 * 按好友（agentId）独立存储为 filesDir/observations/<agentId>.json，
 * 列表按时间倒序（最新在前），供发现页时间线展示。
 */
data class ObservationEntry(
    val id: String,
    val agentId: String,
    val agentName: String,
    val timestamp: Long,
    val behavior: String
)

class ObservationStore(context: Context) {

    private val gson = Gson()
    private val dir = File(context.filesDir, "observations").apply { mkdirs() }

    private fun fileFor(agentId: String): File =
        File(dir, "${agentId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    @Synchronized
    fun getObservations(agentId: String): List<ObservationEntry> {
        val f = fileFor(agentId)
        if (!f.exists()) return emptyList()
        return try {
            gson.fromJson<List<ObservationEntry>>(
                f.readText(), object : TypeToken<List<ObservationEntry>>() {}.type
            ) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addObservation(entry: ObservationEntry): List<ObservationEntry> {
        val list = (getObservations(entry.agentId) + entry)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
        try {
            val f = fileFor(entry.agentId)
            f.parentFile?.mkdirs()
            f.writeText(gson.toJson(list))
        } catch (_: Exception) {}
        return list
    }
}
