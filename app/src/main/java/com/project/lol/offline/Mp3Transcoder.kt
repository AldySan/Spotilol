package com.project.lol.offline

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method

object Mp3Transcoder {
    private const val TAG = "Spl-DL"
    private const val DEFAULT_BITRATE = 320

    private val nativeBridge: NativeBridge? by lazy { NativeBridge.create() }

    fun transcode(
        input: File,
        output: File,
        bitrateKbps: Int = DEFAULT_BITRATE,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean {
        val bridge = nativeBridge
        if (bridge != null) {
            val ok = runCatching {
                transcodeNative(bridge, input, output, bitrateKbps, onProgress)
            }.getOrElse {
                Log.w(TAG, "transcode: native encoder failed: ${it.message}", it)
                runCatching { output.delete() }
                false
            }
            if (ok) return true
            Log.w(TAG, "transcode: native path failed, falling back to java encoder")
        }
        return Mp3TranscoderJava.transcode(input, output, bitrateKbps, onProgress)
    }

    private class NativeBridge private constructor(private val clazz: Class<*>) {
        private val nInit: Method = clazz.getDeclaredMethod(
            "nInit",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        private val nEncodeShort: Method = clazz.getDeclaredMethod(
            "nEncodeShort",
            java.lang.Long.TYPE, ShortArray::class.java, ShortArray::class.java,
            Int::class.javaPrimitiveType, ByteArray::class.java
        )
        private val nFlush: Method = clazz.getDeclaredMethod(
            "nFlush", java.lang.Long.TYPE, ByteArray::class.java
        )
        private val nClose: Method = clazz.getDeclaredMethod("nClose", java.lang.Long.TYPE)
        private val bufferSize: Method = clazz.getDeclaredMethod(
            "mGetMp3bufferSize", java.lang.Long.TYPE
        )

        init {
            nInit.isAccessible = true
            nEncodeShort.isAccessible = true
            nFlush.isAccessible = true
            nClose.isAccessible = true
            bufferSize.isAccessible = true
        }

        fun init(sampleRate: Int, channels: Int, bitrate: Int, model: Int, quality: Int): Long {
            val ptr = nInit.invoke(null, sampleRate, channels, sampleRate, bitrate, model, quality) as Long
            if (ptr == 0L || ptr in -65536..65536) {
                throw IllegalStateException("lame init failed: $ptr")
            }
            return ptr
        }

        fun encode(ptr: Long, left: ShortArray, right: ShortArray, samples: Int, out: ByteArray): Int =
            nEncodeShort.invoke(null, ptr, left, right, samples, out) as Int

        fun flush(ptr: Long, out: ByteArray): Int = nFlush.invoke(null, ptr, out) as Int

        fun mp3BufferSize(ptr: Long): Int = bufferSize.invoke(null, ptr) as Int

        fun close(ptr: Long) {
            runCatching { nClose.invoke(null, ptr) }
        }

        companion object {
            fun create(): NativeBridge? {
                return runCatching {
                    NativeBridge(Class.forName("net.qiujuer.lame.Lame"))
                }.onFailure {
                    Log.i(TAG, "native lame bridge unavailable: ${it.message}")
                }.getOrNull()
            }
        }
    }

    private fun transcodeNative(
        bridge: NativeBridge,
        input: File,
        output: File,
        bitrateKbps: Int,
        onProgress: ((Int) -> Unit)?
    ): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(input.absolutePath)
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    format = f
                    extractor.selectTrack(i)
                    break
                }
            }
            val audioFormat = format ?: run {
                Log.w(TAG, "transcodeNative: no audio track in ${input.name}")
                return false
            }

            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            val model = if (channelCount == 1) 3 else 1
            val ptr = bridge.init(sampleRate, channelCount, bitrateKbps, model, 7)
            try {
                val mp3Buf = ByteArray(bridge.mp3BufferSize(ptr).coerceAtLeast(7200))
                FileOutputStream(output).use { out ->
                    val bufferInfo = MediaCodec.BufferInfo()
                    var sawInputEOS = false
                    var sawOutputEOS = false

                    while (!sawOutputEOS) {
                        if (!sawInputEOS) {
                            val inIdx = codec.dequeueInputBuffer(10_000)
                            if (inIdx >= 0) {
                                val ib = codec.getInputBuffer(inIdx)!!
                                val size = extractor.readSampleData(ib, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(
                                        inIdx, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    sawInputEOS = true
                                } else {
                                    codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }

                        val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                        when {
                            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                            outIdx >= 0 -> {
                                val ob = codec.getOutputBuffer(outIdx)!!
                                if (bufferInfo.size > 0) {
                                    ob.position(bufferInfo.offset)
                                    ob.limit(bufferInfo.offset + bufferInfo.size)
                                    encodeChunkNative(bridge, ptr, ob, channelCount, mp3Buf, out)
                                    if (onProgress != null && durationUs > 0 && bufferInfo.presentationTimeUs > 0) {
                                        onProgress(
                                            (bufferInfo.presentationTimeUs * 100 / durationUs)
                                                .toInt().coerceIn(0, 100)
                                        )
                                    }
                                }
                                codec.releaseOutputBuffer(outIdx, false)
                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    sawOutputEOS = true
                                }
                            }
                        }
                    }

                    val flushed = bridge.flush(ptr, mp3Buf)
                    if (flushed > 0) out.write(mp3Buf, 0, flushed)
                }
            } finally {
                bridge.close(ptr)
            }
            return output.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "transcodeNative: failed: ${e.message}", e)
            runCatching { output.delete() }
            return false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun encodeChunkNative(
        bridge: NativeBridge,
        ptr: Long,
        pcm: java.nio.ByteBuffer,
        channels: Int,
        mp3Buf: ByteArray,
        out: FileOutputStream
    ) {
        val sampleCount = pcm.remaining() / (2 * channels)
        if (sampleCount <= 0) return
        val step = 1152
        val left = ShortArray(step)
        val right = ShortArray(step)
        var written = 0
        while (written < sampleCount) {
            val n = minOf(step, sampleCount - written)
            for (i in 0 until n) {
                left[i] = pcm.short
                right[i] = if (channels == 2) pcm.short else left[i]
            }
            val encoded = bridge.encode(ptr, left, right, n, mp3Buf)
            if (encoded > 0) out.write(mp3Buf, 0, encoded)
            else if (encoded < 0) {
                Log.w(TAG, "encodeChunkNative: encoder error $encoded")
                return
            }
            written += n
        }
    }

    fun sniffContainer(file: File): String? {
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(16)
                var read = 0
                while (read < header.size) {
                    val r = input.read(header, read, header.size - read)
                    if (r < 0) break
                    read += r
                }
                if (read < 12) return@runCatching null
                when {
                    header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() &&
                        header[2] == '3'.code.toByte() -> "mp3"
                    header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0 -> "mp3"
                    header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                        header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte() -> "m4a"
                    header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                        header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> "webm"
                    else -> null
                }
            }
        }.getOrNull()
    }
}
