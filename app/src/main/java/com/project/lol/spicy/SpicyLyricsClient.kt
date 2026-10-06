package com.project.lol.spicy

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

object SpicyLyricsClient {

    private const val SPICY_ENDPOINT = "https://api.spicylyrics.org/v1/lyrics/"
    private const val LRCLIB_GET = "https://lrclib.net/api/get"
    private const val LRCLIB_SEARCH = "https://lrclib.net/api/search"
    private const val USER_AGENT = "Spotilol/1.1.8 (https://github.com/lyssadev/Spotilol)"
    private const val CACHE_MAX = 64
    private const val MIN_SCALE = 0.5
    private const val MAX_SCALE = 2.5
    private const val MAX_WORDS = 1200

    private val cache = object : LinkedHashMap<String, String>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > CACHE_MAX
    }

    private class Result(val json: String, val cacheable: Boolean)

    fun fetch(trackId: String, title: String?, artist: String?, durationSec: Int): String? {
        if (trackId.length != 22) return null
        synchronized(cache) { cache[trackId]?.let { return it } }
        val result = runCatching { build(trackId, title, artist, durationSec) }.getOrNull() ?: return null
        if (result.cacheable) synchronized(cache) { cache[trackId] = result.json }
        return result.json
    }

    private fun build(trackId: String, title: String?, artist: String?, durationSec: Int): Result? {
        val raw = runCatching { request(SPICY_ENDPOINT + trackId, spicy = true) }.getOrNull()
        val body = raw?.let { runCatching { JSONObject(it).optJSONObject("Body") }.getOrNull() }
        val type = body?.optString("Type", "").orEmpty()
        if (body != null && type.isNotEmpty()) {
            val payload = convert(body, type, title, artist, durationSec) ?: return null
            return Result(payload, !(type == "Static" && title.isNullOrBlank()))
        }
        if (title.isNullOrBlank()) return null
        val fallback = lrcOnly(title, durationSec) ?: return null
        return Result(fallback, true)
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    private fun convert(body: JSONObject, type: String, title: String?, artist: String?, durationSec: Int): String? =
        when (type) {
            "Static" -> {
                val arr = body.optJSONArray("Lines") ?: return null
                val texts = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    texts.add(clean(arr.optJSONObject(i)?.optString("Text", "") ?: ""))
                }
                if (texts.isEmpty()) return null
                val lrc = if (title.isNullOrBlank()) null else findLrc(title, artist, durationSec, texts)
                val times = lrc?.let { align(texts, it.lines, it.scale) }
                buildPayload(texts, times, if (durationSec > 0) durationSec * 1000L else 0L)
            }
            "Line", "Syllable" -> buildTimed(body, type)
            else -> null
        }

    private fun buildPayload(texts: List<String>, times: LongArray?, durationMs: Long): String {
        val lines = JSONArray()
        for (i in texts.indices) {
            val start = times?.get(i) ?: 0L
            val end = when {
                times == null -> 0L
                i + 1 < times.size -> times[i + 1]
                durationMs > start -> durationMs
                else -> start + 5000L
            }
            lines.put(lineObj(texts[i], start.toString(), end.toString(), JSONArray()))
        }
        return wrap(if (times == null) "UNSYNCED" else "LINE_SYNCED", lines)
    }

    private fun buildTimed(body: JSONObject, type: String): String? {
        val arr = body.optJSONArray("Content") ?: return null
        val lines = JSONArray()
        for (i in 0 until arr.length()) {
            val lead = arr.optJSONObject(i)?.optJSONObject("Lead") ?: continue
            val syllables = lead.optJSONArray("Syllables") ?: JSONArray()
            val text = StringBuilder()
            val out = JSONArray()
            for (j in 0 until syllables.length()) {
                val s = syllables.optJSONObject(j) ?: continue
                val prev = if (j > 0) syllables.optJSONObject(j - 1) else null
                val raw = clean(s.optString("Text", ""))
                val token = if (j > 0 && prev != null && !prev.optBoolean("IsPartOfWord", false)) " $raw" else raw
                text.append(token)
                out.put(JSONObject().apply {
                    put("startTimeMs", ms(s.optDouble("StartTime", 0.0)))
                    put("endTimeMs", ms(s.optDouble("EndTime", 0.0)))
                    put("text", token)
                })
            }
            lines.put(
                lineObj(
                    text.toString(),
                    ms(lead.optDouble("StartTime", 0.0)),
                    "0",
                    if (type == "Syllable") out else JSONArray()
                )
            )
        }
        if (lines.length() == 0) return null
        return wrap(if (type == "Syllable") "SYLLABLE_SYNCED" else "LINE_SYNCED", lines)
    }

    private fun wrap(syncType: String, lines: JSONArray): String =
        JSONObject().apply {
            put("lyrics", JSONObject().apply {
                put("syncType", syncType)
                put("lines", lines)
                put("provider", "SpicyLyrics")
                put("providerLyricsId", "")
                put("providerDisplayName", "SpicyLyrics")
                put("syncLyricsUri", "")
                put("isDenseTypeface", false)
                put("alternatives", JSONArray())
                put("language", "en")
                put("isRtlLanguage", false)
                put("capStatus", "UNCAPPED")
                put("previewLines", JSONArray().also { p ->
                    for (i in 0 until minOf(5, lines.length())) p.put(lines.get(i))
                })
                put("translationLanguages", JSONArray())
                put("availableTranslationLanguages", JSONArray())
            })
            put("colors", JSONObject().apply {
                put("background", -16777216)
                put("text", -1)
                put("highlightText", -1)
            })
            put("hasVocalRemoval", false)
        }.toString()

    private class Lrc(val lines: List<Pair<Double, String>>, val scale: Double)

    private fun findLrc(title: String, artist: String?, durationSec: Int, texts: List<String>): Lrc? {
        val exact = runCatching { request(lrcGetUrl(title, artist, durationSec), spicy = false) }.getOrNull()
        if (exact != null) {
            val obj = runCatching { JSONObject(exact) }.getOrNull()
            if (obj != null) {
                val parsed = parseLrc(obj.optString("syncedLyrics", ""))
                if (parsed.isNotEmpty()) {
                    return Lrc(parsed, scaleFor(durationSec, obj.optDouble("duration", 0.0)))
                }
            }
        }
        val ourWords = wordSet(texts.joinToString(" "))
        if (ourWords.isEmpty()) return null
        val target = targetRatio(title)
        val seen = collect(title)
        var best: JSONObject? = null
        var bestScore = -1.0
        var bestText = 0.0
        var bestFit = 0.0
        for (item in seen.values) {
            val raw = if (durationSec > 0) durationSec / item.optDouble("duration", 1.0) else 1.0
            val fit = maxOf(0.0, 1.0 - abs(raw - target) / 0.8)
            val text = containment(ourWords, wordSet(item.optString("syncedLyrics", "")))
            val score = text + 0.15 * fit
            if (score > bestScore) {
                bestScore = score
                best = item
                bestText = text
                bestFit = fit
            }
        }
        if (best != null && bestText >= 0.85 && bestFit >= 0.55) {
            val parsed = parseLrc(best.optString("syncedLyrics", ""))
            if (parsed.isNotEmpty()) {
                return Lrc(parsed, scaleFor(durationSec, canonicalDuration(best, seen.values)))
            }
        }
        return null
    }

    private fun collect(title: String): Map<Int, JSONObject> {
        val seen = HashMap<Int, JSONObject>()
        for (query in candidates(title)) {
            val body = runCatching { request(lrcSearchUrl(query), spicy = false) }.getOrNull() ?: continue
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (item.optString("syncedLyrics", "").isBlank()) continue
                if (item.optDouble("duration", 0.0) <= 0.0) continue
                val id = item.optInt("id", -1)
                if (id != -1 && seen.containsKey(id)) continue
                seen[if (id == -1) i + query.hashCode() else id] = item
            }
        }
        return seen
    }

    private fun lrcOnly(title: String, durationSec: Int): String? {
        val titleWords = wordSet(cleanTitle(title))
        if (titleWords.isEmpty()) return null
        val target = targetRatio(title)
        val seen = collect(title)
        var best: JSONObject? = null
        var bestScore = -1.0
        var bestTitle = 0.0
        var bestFit = 0.0
        for (item in seen.values) {
            val raw = if (durationSec > 0) durationSec / item.optDouble("duration", 1.0) else 1.0
            val fit = maxOf(0.0, 1.0 - abs(raw - target) / 0.8)
            val titleSim = containment(titleWords, wordSet(item.optString("trackName", "")))
            val score = titleSim + 0.15 * fit
            if (score > bestScore) {
                bestScore = score
                best = item
                bestTitle = titleSim
                bestFit = fit
            }
        }
        if (best == null || bestTitle < 0.9 || bestFit < 0.55) return null
        val lrc = parseLrc(best.optString("syncedLyrics", ""))
        if (lrc.isEmpty()) return null
        val scale = scaleFor(durationSec, canonicalDuration(best, seen.values))
        val texts = lrc.map { it.second }
        val times = LongArray(lrc.size) { (lrc[it].first * scale * 1000.0).toLong() }
        return buildPayload(texts, times, if (durationSec > 0) durationSec * 1000L else 0L)
    }

    private fun scaleFor(current: Int, original: Double): Double {
        if (current <= 0 || original <= 0.0) return 1.0
        return (current / original).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    private fun artistKey(name: String): String = norm(name).take(2).joinToString(" ")

    private fun canonicalDuration(best: JSONObject, all: Collection<JSONObject>): Double {
        val fallback = best.optDouble("duration", 0.0)
        val key = artistKey(best.optString("artistName", ""))
        if (key.isEmpty()) return fallback
        val durations = all
            .filter { artistKey(it.optString("artistName", "")) == key }
            .map { it.optDouble("duration", 0.0) }
            .filter { it > 0.0 }
            .sorted()
        if (durations.size < 3) return fallback
        return durations[durations.size / 2]
    }

    private fun lrcGetUrl(title: String, artist: String?, durationSec: Int): String {
        val sb = StringBuilder(LRCLIB_GET)
        sb.append("?track_name=").append(enc(title))
        if (!artist.isNullOrBlank()) sb.append("&artist_name=").append(enc(artist))
        if (durationSec > 0) sb.append("&duration=").append(durationSec)
        return sb.toString()
    }

    private fun lrcSearchUrl(query: String): String = LRCLIB_SEARCH + "?track_name=" + enc(query)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun candidates(title: String): List<String> {
        val out = LinkedHashSet<String>()
        val cleaned = cleanTitle(title)
        for (sep in listOf(" - ", " \u2013 ", " \u2014 ", " | ", " / ")) {
            if (cleaned.contains(sep)) {
                val head = cleaned.substringBefore(sep).trim()
                if (head.length >= 2) out.add(head)
            }
        }
        if (cleaned.length >= 2) out.add(cleaned)
        val raw = title.trim()
        if (raw.length >= 2) out.add(raw)
        return out.take(3)
    }

    private val REMIX_WORDS = listOf(
        "slowed", "slowed down", "reverb", "reverbed", "sped up", "spedup", "speed up", "speedup",
        "nightcore", "ultra", "super", "remix", "edit", "version", "mix", "extended", "instrumental",
        "acoustic", "cover", "live", "8d", "bass boosted", "boosted", "tiktok", "viral", "mashup",
        "bootleg", "flip", "rework", "vip", "radio edit", "karaoke", "chopped", "screwed", "tribute"
    )

    private val TRAILING_REMIX = Regex(
        "(?i)\\s*[-\\u2013\\u2014|,/]?\\s*(slowed down|slowed|reverbed|reverb|sped up|spedup|speed up|" +
            "nightcore|ultra|bass boosted|8d audio|remix|extended|instrumental)\\s*$"
    )

    private fun cleanTitle(title: String): String {
        var s = Regex("\\(([^)]*)\\)|\\[([^\\]]*)\\]|\\{([^}]*)\\}").replace(title) { m ->
            val inner = (m.groupValues[1] + " " + m.groupValues[2] + " " + m.groupValues[3]).lowercase()
            if (REMIX_WORDS.any { inner.contains(it) }) " " else m.value
        }
        repeat(2) { s = TRAILING_REMIX.replace(s, "") }
        return s.replace(Regex("\\s+"), " ").trim().trim('-', '\u2013', '\u2014', '|', ',', '.', ' ')
    }

    private fun norm(value: String): List<String> =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    private fun wordSet(text: String): Set<String> = norm(text).filter { it.length > 1 }.toHashSet()

    private fun containment(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.count { b.contains(it) }.toDouble() / minOf(a.size, b.size)
    }

    private fun targetRatio(title: String): Double {
        val l = title.lowercase()
        return when {
            l.contains("ultra slowed") || l.contains("super slowed") -> 1.7
            l.contains("nightcore") -> 0.75
            l.contains("sped up") || l.contains("spedup") || l.contains("speed up") -> 0.85
            l.contains("slowed") -> 1.25
            l.contains("reverb") -> 1.1
            else -> 1.0
        }
    }

    private fun parseLrc(text: String): List<Pair<Double, String>> {
        if (text.isBlank()) return emptyList()
        val re = Regex("^\\[(\\d{1,2}):(\\d{1,2}(?:\\.\\d+)?)]\\s*(.*)$")
        val out = ArrayList<Pair<Double, String>>()
        for (raw in text.split('\n')) {
            val m = re.find(raw.trim()) ?: continue
            val min = m.groupValues[1].toIntOrNull() ?: continue
            val sec = m.groupValues[2].toDoubleOrNull() ?: continue
            val content = m.groupValues[3].trim()
            if (content.isEmpty()) continue
            out.add((min * 60 + sec) to content)
        }
        return out
    }

    private fun align(texts: List<String>, lrc: List<Pair<Double, String>>, scale: Double): LongArray? {
        val oNorm = ArrayList<String>()
        val oTime = ArrayList<Double>()
        for (i in lrc.indices) {
            val t = lrc[i].first
            val next = if (i + 1 < lrc.size) lrc[i + 1].first else t + 3.0
            val words = norm(lrc[i].second)
            if (words.isEmpty()) continue
            for (j in words.indices) {
                oNorm.add(words[j])
                oTime.add(t + (next - t) * (j.toDouble() / words.size))
            }
        }
        val sNorm = ArrayList<String>()
        val sLine = ArrayList<Int>()
        for (li in texts.indices) {
            for (w in norm(texts[li])) {
                sNorm.add(w)
                sLine.add(li)
            }
        }
        val n = sNorm.size
        val m = oNorm.size
        if (n == 0 || m == 0 || n > MAX_WORDS || m > MAX_WORDS) return null
        val dp = Array(n + 1) { ShortArray(m + 1) }
        for (i in 1..n) {
            val row = dp[i]
            val up = dp[i - 1]
            val word = sNorm[i - 1]
            for (j in 1..m) {
                row[j] = if (word == oNorm[j - 1]) (up[j - 1] + 1).toShort() else maxOf(up[j], row[j - 1])
            }
        }
        val times = LongArray(texts.size) { -1L }
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            if (sNorm[i - 1] == oNorm[j - 1]) {
                val li = sLine[i - 1]
                val t = (oTime[j - 1] * scale * 1000.0).toLong()
                if (times[li] < 0 || t < times[li]) times[li] = t
                i--
                j--
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        var matched = 0
        for (t in times) if (t >= 0) matched++
        if (matched < texts.size / 3) return null
        var prev = 0L
        for (k in times.indices) {
            if (times[k] >= 0) prev = times[k] else times[k] = prev
        }
        for (k in 1 until times.size) if (times[k] < times[k - 1]) times[k] = times[k - 1]
        return times
    }

    private fun request(url: String, spicy: Boolean): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (spicy) conn.setRequestProperty("Authorization", "Bearer " + SpicyKey.reveal())
            if (conn.responseCode != 200) null
            else conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun ms(seconds: Double): String = (seconds * 1000.0).toLong().toString()

    private fun clean(text: String): String = text.replace("\\'", "'")

    private fun lineObj(words: String, startMs: String, endMs: String, syllables: JSONArray): JSONObject =
        JSONObject().apply {
            put("startTimeMs", startMs)
            put("words", words)
            put("syllables", syllables)
            put("endTimeMs", endMs)
            put("transliteratedWords", "")
        }
}
