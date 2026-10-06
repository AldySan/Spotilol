package com.project.lol.spicy

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SpicyKey {

    private const val BLOB =
        "OED5YU3iR6/WvqDxH7N1pcDi5CBdqKm6VNFbRL+bIF3ObyDs066y9KhQD3qJKPZlZKCaPWt9023SaEHa8Tnljxc="

    private const val XOR_MASK = "spl.x1|Spotilol|2026"
    private const val RC4_A = "spl.x2|Spotilol|2026|rc4"
    private const val AES_PASS = "spl.x3|Spotilol|2026|aesgcm"
    private const val RC4_B = "spl.x4|Spotilol|2026|rc4b"

    private val cache: String by lazy { decode() }

    fun reveal(): String = cache

    private fun decode(): String {
        var b = Base64.getDecoder().decode(BLOB)
        b = rc4(RC4_B.toByteArray(Charsets.UTF_8), b)
        b = aesGcmDecrypt(AES_PASS, b)
        b = rc4(RC4_A.toByteArray(Charsets.UTF_8), b)
        b = xor(XOR_MASK.toByteArray(Charsets.UTF_8), b)
        return String(b, Charsets.UTF_8)
    }

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val t = s[i]; s[i] = s[j]; s[j] = t
        }
        val out = ByteArray(data.size)
        var i = 0
        j = 0
        for (n in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val t = s[i]; s[i] = s[j]; s[j] = t
            out[n] = (data[n].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
        }
        return out
    }

    private fun xor(mask: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) out[i] = (data[i].toInt() xor mask[i % mask.size].toInt()).toByte()
        return out
    }

    private fun aesGcmDecrypt(pass: String, blob: ByteArray): ByteArray {
        val key = MessageDigest.getInstance("SHA-256").digest(pass.toByteArray(Charsets.UTF_8))
        val iv = MessageDigest.getInstance("SHA-256")
            .digest((pass + "|nonce").toByteArray(Charsets.UTF_8))
            .copyOf(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(blob)
    }
}
