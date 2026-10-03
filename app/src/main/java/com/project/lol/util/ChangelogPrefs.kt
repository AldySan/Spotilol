package com.project.lol.util

import android.content.Context
import android.os.Build

object ChangelogPrefs {

    private const val PREFS_NAME = "spotilol_changelog"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version_code"

    fun shouldShowOnUpdate(context: Context): Boolean {
        val current = currentVersionCode(context) ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(KEY_LAST_SEEN_VERSION, -1L)
        if (lastSeen == -1L) {
            prefs.edit().putLong(KEY_LAST_SEEN_VERSION, current).apply()
            return false
        }
        return current > lastSeen
    }

    fun markShown(context: Context) {
        val current = currentVersionCode(context) ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SEEN_VERSION, current)
            .apply()
    }

    private fun currentVersionCode(context: Context): Long? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrNull()
}
