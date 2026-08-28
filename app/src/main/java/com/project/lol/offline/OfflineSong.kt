package com.project.lol.offline

data class OfflineSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String? = null,
    val filePath: String = "",
)
