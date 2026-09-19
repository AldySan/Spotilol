package com.project.lol.offline

import android.content.Context
import android.content.SharedPreferences

enum class DownloadFormat(val ext: String, val mime: String) {
    M4A("m4a", "audio/mp4"),
    MP3("mp3", "audio/mpeg");

    companion object {
        fun from(value: String?): DownloadFormat =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: M4A
    }
}

object DownloadSettings {
    const val PREFS_NAME = "spotilol_prefs"
    private const val KEY_FORMAT = "DlFormat"
    private const val KEY_SUBFOLDER = "DlSubfolder"
    private const val KEY_TAGS = "DlTags"

    const val DEFAULT_SUBFOLDER = "Spotilol"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun format(context: Context): DownloadFormat =
        DownloadFormat.from(prefs(context).getString(KEY_FORMAT, DownloadFormat.M4A.name))

    @JvmStatic
    fun setFormat(context: Context, format: DownloadFormat) {
        prefs(context).edit().putString(KEY_FORMAT, format.name).apply()
    }

    @JvmStatic
    fun subfolder(context: Context): String =
        prefs(context).getString(KEY_SUBFOLDER, DEFAULT_SUBFOLDER)
            ?.trim()
            ?.replace(Regex("""[/\\]+"""), "-")
            ?.replace(Regex("""[{}^`!#\[\]&$<>=;:"'?,*|]"""), "_")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SUBFOLDER

    @JvmStatic
    fun setSubfolder(context: Context, value: String) {
        prefs(context).edit().putString(KEY_SUBFOLDER, value).apply()
    }

    @JvmStatic
    fun writeTags(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TAGS, true)

    @JvmStatic
    fun setWriteTags(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_TAGS, value).apply()
    }
}
