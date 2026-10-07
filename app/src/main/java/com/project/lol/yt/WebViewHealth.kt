package com.project.lol.yt

import android.content.Context
import androidx.webkit.WebViewCompat

object WebViewHealth {
    private const val MIN_MAJOR_VERSION = 100

    sealed class Status {
        object Ok : Status()
        object Unavailable : Status()
        data class Outdated(val version: String) : Status()
    }

    @Volatile
    private var cached: Status? = null

    fun status(context: Context?): Status {
        if (context == null) return Status.Unavailable
        cached?.let { return it }
        val computed = compute(context.applicationContext)
        cached = computed
        return computed
    }

    fun isUsable(context: Context?): Boolean = status(context) is Status.Ok

    fun failureReason(context: Context?): String? = when (val s = status(context)) {
        is Status.Ok -> null
        is Status.Unavailable -> "WebView is disabled or missing"
        is Status.Outdated -> "WebView is outdated (${s.version})"
    }

    private fun compute(context: Context): Status {
        val pkg = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
            ?: return Status.Unavailable
        val version = pkg.versionName ?: return Status.Unavailable
        val major = version.substringBefore('.').toIntOrNull() ?: return Status.Unavailable
        if (major < MIN_MAJOR_VERSION) return Status.Outdated(version)
        return Status.Ok
    }
}
