package com.project.lol.offline

import android.content.Context
import org.json.JSONObject

object OfflineStore {
    private const val PREF = "offline_downloads"

    fun getSongs(context: Context): List<OfflineSong> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.all.values
            .mapNotNull { (it as? String)?.let { s -> parse(s) } }
            .sortedByDescending { it.id }
    }

    fun addSong(context: Context, song: OfflineSong) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(song.id, song.toJson())
            .apply()
    }

    fun removeSong(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = prefs.getString(id, null)
        json?.let { parse(it) }?.filePath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { java.io.File(path).delete() }
        }
        prefs.edit().remove(id).apply()
    }

    fun isDownloaded(context: Context, id: String): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains(id)

    fun clearAll(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val entries = getSongs(context)
        entries.forEach { song ->
            song.filePath.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { java.io.File(path).delete() }
            }
        }
        prefs.edit().clear().apply()
        return entries.size
    }

    private fun OfflineSong.toJson(): String = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("coverUrl", coverUrl ?: "")
        put("filePath", filePath)
    }.toString()

    private fun parse(json: String): OfflineSong? = runCatching {
        val o = JSONObject(json)
        val path = o.optString("filePath")
        if (path.isNotBlank() && !java.io.File(path).exists()) return null
        OfflineSong(
            id = o.getString("id"),
            title = o.getString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            coverUrl = o.optString("coverUrl").ifBlank { null },
            filePath = path,
        )
    }.getOrNull()
}