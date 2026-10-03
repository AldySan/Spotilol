package com.project.lol.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

internal fun coverFile(context: Context): File =
    File(context.cacheDir, "spotilol_widget_cover.png")

internal object CoverCache {

    @Volatile private var key: String = ""
    @Volatile private var cached: Bitmap? = null

    fun load(context: Context, version: Long): Bitmap? {
        if (version <= 0L) return null
        val currentKey = "${context.cacheDir.absolutePath}#$version"
        cached?.let { if (key == currentKey) return it }
        val file = coverFile(context)
        if (!file.exists()) return null
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return null
        key = currentKey
        cached = decoded
        return decoded
    }
}
