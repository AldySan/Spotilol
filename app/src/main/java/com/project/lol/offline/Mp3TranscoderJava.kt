package com.project.lol.offline

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.ID3TagSpec
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.PsyModel
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.File
import java.io.FileOutputStream

object Mp3TranscoderJava {
    private const val TAG = "Spl-DL"
    private const val DEFAULT_BITRATE = 320

    fun transcode(
        input: File,
        output: File,
        bitrateKbps: Int = DEFAULT_BITRATE,
        onProgress: ((Int) -> Unit)? = null
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
                Log.w(TAG, "transcodeJava: no audio track in ${input.name}")
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

            val (lame, gf) = newLame(sampleRate, channelCount, bitrateKbps)
            try {
                FileOutputStream(output).use { out ->
                    val bufferInfo = MediaCodec.BufferInfo()
                    var sawInputEOS = false
                    var sawOutputEOS = false
                    val mp3Buf = ByteArray(Lame.LAME_MAXMP3BUFFER)

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
                                    encodeChunk(lame, gf, ob, channelCount, mp3Buf, out)
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

                    val flushed = lame.lame_encode_flush(gf, mp3Buf, 0, mp3Buf.size)
                    if (flushed > 0) out.write(mp3Buf, 0, flushed)
                }
            } finally {
                lame.lame_close(gf)
            }
            return output.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "transcodeJava: failed: ${e.message}", e)
            runCatching { output.delete() }
            return false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun newLame(sampleRate: Int, channels: Int, bitrateKbps: Int): Pair<Lame, LameGlobalFlags> {
        val ga = GainAnalysis()
        val vbr = VBRTag()
        val ver = Version()
        val p = Presets()
        val common = Common()
        val intf = Interface()
        intf.setModules(vbr, common)
        val mpg = MPGLib()
        mpg.setModules(intf, common)
        val bs = BitStream()
        bs.setModules(ga, mpg, ver, vbr)
        val rv = Reservoir()
        rv.setModules(bs)
        val id3 = ID3Tag()
        id3.setModules(bs, ver)
        val tak = Takehiro()
        val psy = PsyModel()
        val qupvt = QuantizePVT()
        qupvt.setModules(tak, rv, psy)
        val qu = Quantize()
        qu.setModules(bs, rv, qupvt, tak)
        val lame = Lame()
        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        p.setModules(lame)

        val gf: LameGlobalFlags = lame.lame_init()
        gf.internal_flags.tag_spec = ID3TagSpec()
        gf.write_id3tag_automatic = false
        gf.num_channels = channels
        gf.in_samplerate = sampleRate
        gf.brate = bitrateKbps
        gf.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        gf.quality = 9
        gf.bWriteVbrTag = false
        val rc = lame.lame_init_params(gf)
        if (rc != 0) throw IllegalStateException("lame_init_params failed: $rc")
        return lame to gf
    }

    private fun encodeChunk(
        lame: Lame,
        gf: LameGlobalFlags,
        pcm: java.nio.ByteBuffer,
        channels: Int,
        mp3Buf: ByteArray,
        out: FileOutputStream
    ) {
        val sampleCount = pcm.remaining() / (2 * channels)
        if (sampleCount <= 0) return
        val left = IntArray(sampleCount)
        val right = IntArray(sampleCount)
        for (i in 0 until sampleCount) {
            left[i] = pcm.short.toInt() shl 16
            right[i] = if (channels == 2) pcm.short.toInt() shl 16 else left[i]
        }
        val encoded = lame.lame_encode_buffer_int(gf, left, right, sampleCount, mp3Buf, 0, mp3Buf.size)
        if (encoded > 0) out.write(mp3Buf, 0, encoded)
    }
}
