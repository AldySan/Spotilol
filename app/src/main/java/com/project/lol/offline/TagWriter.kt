package com.project.lol.offline

import android.graphics.BitmapFactory
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.mp4.Mp4FieldKey
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.mp4.field.Mp4TagCoverField
import java.io.File

object TagWriter {
    private const val TAG = "Spl-DL"

    fun writeTags(
        file: File,
        format: DownloadFormat,
        title: String,
        artist: String,
        album: String,
        coverFile: File?
    ) {
        runCatching {
            when (format) {
                DownloadFormat.M4A -> writeMp4Tags(file, title, artist, album, coverFile)
                DownloadFormat.MP3 -> writeMp3Tags(file, title, artist, album, coverFile)
            }
        }.onFailure {
            Log.w(TAG, "writeTags: failed for ${file.name}: ${it.message}")
        }
    }

    private fun writeMp4Tags(
        file: File,
        title: String,
        artist: String,
        album: String,
        coverFile: File?
    ) {
        val f = AudioFileIO.read(file)
        val tag = f.tag as? Mp4Tag ?: Mp4Tag().also { f.tag = it }
        if (title.isNotBlank()) tag.setField(Mp4FieldKey.TITLE, title)
        if (artist.isNotBlank()) tag.setField(Mp4FieldKey.ARTIST, artist)
        if (album.isNotBlank()) tag.setField(Mp4FieldKey.ALBUM, album)
        writeCoverMp4(tag, coverFile)
        f.commit()
    }

    private fun writeMp3Tags(
        file: File,
        title: String,
        artist: String,
        album: String,
        coverFile: File?
    ) {
        val mp3 = MP3File(file)
        val tag = ID3v24Tag()
        if (title.isNotBlank()) tag.setField(FieldKey.TITLE, title)
        if (artist.isNotBlank()) tag.setField(FieldKey.ARTIST, artist)
        if (album.isNotBlank()) tag.setField(FieldKey.ALBUM, album)
        writeCoverMp3(tag, coverFile)
        mp3.tag = tag
        mp3.commit()
    }

    private fun writeCoverMp4(tag: Mp4Tag, coverFile: File?) {
        val data = readCoverBytes(coverFile) ?: return
        runCatching {
            tag.setField(Mp4TagCoverField(data))
        }.onFailure {
            Log.w(TAG, "writeCoverMp4: cover failed: ${it.message}")
        }
    }

    private fun writeCoverMp3(tag: ID3v24Tag, coverFile: File?) {
        val data = readCoverBytes(coverFile) ?: return
        runCatching {
            val artwork = AndroidArtwork()
            artwork.binaryData = data
            artwork.mimeType = "image/jpeg"
            artwork.pictureType = 3
            artwork.description = ""
            tag.setField(artwork)
        }.onFailure {
            Log.w(TAG, "writeCoverMp3: cover failed: ${it.message}")
        }
    }

    private fun readCoverBytes(coverFile: File?): ByteArray? {
        if (coverFile == null || !coverFile.exists() || coverFile.length() == 0L) return null
        return runCatching {
            val bytes = coverFile.readBytes()
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth > 0) bytes else null
        }.getOrNull()
    }
}
