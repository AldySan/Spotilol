package com.project.lol.innertube.models

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class YouTubeLocale(
    val gl: String,
    val hl: String,
) {
    fun sanitized(): YouTubeLocale = YouTubeLocale(
        gl = gl.trim().uppercase(Locale.ROOT).takeIf { code ->
            code.length == 2 && code.all { it in 'A'..'Z' }
        } ?: DEFAULT_GL,
        hl = hl.trim().takeIf { tag ->
            tag.isNotBlank() && !tag.equals("und", ignoreCase = true)
        } ?: DEFAULT_HL,
    )

    companion object {
        const val DEFAULT_GL = "US"
        const val DEFAULT_HL = "en"
    }
}
