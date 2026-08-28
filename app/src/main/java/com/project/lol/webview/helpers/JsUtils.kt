package com.project.lol.webview.helpers

import androidx.collection.LruCache

/**
 * Strips `console.log(...)` calls from JavaScript before it is handed to
 * the WebView.
 *
 * ## Implementation notes
 *
 * This class previously used a single regex:
 *
 *   Regex("console\\.log\\((?:[^()]|\\([^()]*\\))*\\);?")
 *
 * That approach had correctness bugs that could corrupt the injected JS
 * (see git history for details). The main issues fixed here:
 *
 *  - FIX: Nested parentheses beyond one level were never matched.
 *  - FIX: Unbalanced parens inside string literals caused partial matches
 *         that left dangling tokens (syntax errors).
 *  - FIX: `window.console.log(...)` was truncated to `window.`.
 *  - FIX: Literal "console.log(...)" text inside strings was stripped,
 *         silently changing runtime values.
 *  - FIX: Deleting the statement broke `if (x) console.log(x); next();` -
 *         `next()` became the if-body.
 *  - FIX (perf): every intercepted response was re-scanned. Results are
 *         now memoized in a bounded LRU cache, so each asset is scanned
 *         at most once per session.
 *
 * The scanner resolves these by lexing string/comment/regex literals and
 * tracking balanced parentheses. Matched calls are replaced with the
 * expression `void 0` - the exact return value of console.log (undefined) -
 * so the surrounding code remains valid in every syntactic position.
 *
 * Complexity is O(n) with no backtracking. Throughput is kept close to the
 * old regex by bulk-copying uninteresting regions instead of copying
 * character by character.
 */
object JsUtils {

    /**
     * Characters that may begin (or affect the interpretation of) a region
     * we cannot bulk-copy: string literals, comments, division-vs-regex
     * ambiguity, and the identifier `console`.
     */
    private val INTERESTING = charArrayOf('"', '\'', '`', '/', 'c')

    /**
     * Keywords after which a `/` must be treated as the start of a regex
     * literal rather than a division operator. Standard heuristic used by
     * minifiers; covers the practically relevant cases.
     */
    private val REGEX_KEYWORDS = setOf(
        "return", "typeof", "instanceof", "in", "of", "new",
        "delete", "void", "case", "do", "else", "yield", "await"
    )

    /**
     * Upper bound for cached payloads, in bytes (UTF-16: 2 bytes per char).
     * ~2 MB is enough for dozens of typical JS bundles while capping memory
     * use in long-running WebView sessions.
     */
    private const val MAX_CACHE_BYTES = 2 * 1024 * 1024

    /**
     * FIX (perf): memoizes stripped output so each asset is scanned at most
     * once. LruCache is thread-safe, evicts least-recently-used entries when
     * [MAX_CACHE_BYTES] is exceeded, and is keyed by the caller-supplied
     * request URL. Using a plain HashMap here would grow unbounded for the
     * lifetime of the process.
     */
    private val cache = object : LruCache<String, String>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: String): Int =
            ((key.length + value.length) * 2).coerceAtLeast(1)
    }

    /**
     * Cached variant of [stripConsoleLogs] for use in
     * `shouldInterceptRequest`, where the same asset is intercepted on
     * every page load.
     *
     * @param key stable identity of the asset, typically the request URL.
     *   For mutable/remote responses, include a version or content hash in
     *   the key; volatile query parameters (session tokens, timestamps)
     *   should be stripped from the key to avoid cache churn.
     * @param code the original JavaScript source.
     * @return the stripped source; after the first call for [key], the
     *   memoized result is returned without re-scanning.
     */
    fun stripConsoleLogsCached(key: String, code: String): String {
        // FIX (perf): check the cache before scanning. LruCache synchronizes
        // internally per call, so no lock is held while we scan - concurrent
        // misses on different URLs proceed in parallel.
        cache[key]?.let { return it }

        val stripped = stripConsoleLogs(code)

        // Note: on a concurrent duplicate miss, the second result simply
        // overwrites the first - the outputs are deterministic, so this is
        // harmless.
        cache.put(key, stripped)
        return stripped
    }

    /**
     * Replaces every `console.log(...)` call with `void 0`.
     *
     * Safe against calls appearing inside strings, comments, and regex
     * literals; handles arbitrary paren nesting inside the call arguments.
     * If the input does not contain the substring "console", the original
     * instance is returned without allocation.
     */
    fun stripConsoleLogs(code: String): String {
        // FIX (perf): fast path - most assets contain no logging at all.
        if (!code.contains("console")) return code

        val sb = StringBuilder(code.length)
        var i = 0
        val n = code.length

        while (i < n) {
            when (val c = code[i]) {
                // FIX: strings are copied verbatim so that parens/braces
                // inside them (e.g. ":-)") can never break matching, and a
                // literal "console.log" inside a string is never stripped.
                '"', '\'', '`' -> {
                    val end = skipString(code, i)
                    sb.append(code, i, end); i = end
                }
                '/' -> when {
                    // Line comment - copied verbatim.
                    i + 1 < n && code[i + 1] == '/' -> {
                        val nl = code.indexOf('\n', i)
                        val end = if (nl == -1) n else nl
                        sb.append(code, i, end); i = end
                    }
                    // Block comment - copied verbatim.
                    i + 1 < n && code[i + 1] == '*' -> {
                        val cm = code.indexOf("*/", i + 2)
                        val end = if (cm == -1) n else cm + 2
                        sb.append(code, i, end); i = end
                    }
                    // FIX: a '/' may start a regex literal; consuming it
                    // verbatim prevents its contents (e.g. `[(]`) from
                    // confusing the paren matcher below.
                    isRegexStart(code, i) -> {
                        val end = skipRegex(code, i)
                        sb.append(code, i, end); i = end
                    }
                    else -> { sb.append(c); i++ } // division operator
                }
                'c' -> {
                    val openEnd = matchCallStart(code, i)
                    if (openEnd != -1) {
                        val close = findMatchingParen(code, openEnd)
                        if (close != -1) {
                            // FIX: substitute `void 0` instead of deleting
                            // the statement. Deleting broke single-statement
                            // bodies (`if (x) console.log(x); next();`) and
                            // expression positions. `void 0` evaluates to
                            // undefined - identical to console.log's return
                            // value - so behavior is preserved.
                            sb.append("void 0")
                            i = close + 1 // consume ')'; a trailing ';' is harmless
                        } else { sb.append(c); i++ }
                    } else {
                        // Regular identifier starting with 'c' (class, catch…).
                        sb.append(c); i++
                    }
                }
                else -> {
                    // FIX (perf): bulk-copy until the next character that
                    // requires inspection. Avoids per-char loop overhead and
                    // keeps throughput comparable to the previous regex.
                    val next = code.indexOfAny(INTERESTING, i + 1)
                    val end = if (next == -1) n else next
                    sb.append(code, i, end); i = end
                }
            }
        }
        return sb.toString()
    }

    /**
     * Returns the index just past the '(' if [code] starting at [start] is
     * a `console.log(` token sequence; -1 otherwise.
     *
     * FIX: the previous regex only matched `console.log` verbatim, so
     * `window.console.log(...)` was partially stripped (leaving `window.`)
     * and whitespace such as `console . log (...)` was missed. Here we
     * verify the call is not preceded by an identifier character or a '.',
     * and allow whitespace between tokens as the JS grammar does.
     */
    private fun matchCallStart(code: String, start: Int): Int {
        val n = code.length
        if (!code.regionMatches(start, "console", 0, 7)) return -1
        val prev = if (start > 0) code[start - 1] else ' '
        if (prev.isLetterOrDigit() || prev == '_' || prev == '$' || prev == '.') return -1
        var i = skipWs(code, start + 7)
        if (i >= n || code[i] != '.') return -1
        i = skipWs(code, i + 1)
        if (!code.regionMatches(i, "log", 0, 3)) return -1
        i = skipWs(code, i + 3)
        return if (i < n && code[i] == '(') i + 1 else -1
    }

    /**
     * Returns the index of the ')' matching the '(' just before [from].
     *
     * FIX: the old regex supported at most one nesting level, so calls like
     * `console.log(a.map(x => f(x)))` were left in place. Depth tracking
     * supports arbitrary nesting. String/comment/regex literals are skipped
     * so their contents cannot affect the count.
     */
    private fun findMatchingParen(code: String, from: Int): Int {
        var depth = 1
        var i = from
        val n = code.length
        while (i < n) {
            when (code[i]) {
                '(' -> { depth++; i++ }
                ')' -> { depth--; if (depth == 0) return i; i++ }
                '"', '\'', '`' -> i = skipString(code, i)
                '/' -> when {
                    i + 1 < n && code[i + 1] == '/' -> {
                        val nl = code.indexOf('\n', i); i = if (nl == -1) n else nl
                    }
                    i + 1 < n && code[i + 1] == '*' -> {
                        val cm = code.indexOf("*/", i + 2); i = if (cm == -1) n else cm + 2
                    }
                    isRegexStart(code, i) -> i = skipRegex(code, i)
                    else -> i++
                }
                else -> i++
            }
        }
        return -1
    }

    /**
     * Heuristic: decides whether the '/' at [i] starts a regex literal or
     * is a division operator, based on the previous significant token.
     *
     * Known limitation: a regex literal written directly after `)` (e.g.
     * `if (x) /re/.test(y)`) is classified as division. The consequence is
     * that a call may be skipped - output is never corrupted.
     */
    private fun isRegexStart(code: String, i: Int): Boolean {
        var j = i - 1
        while (j >= 0 && code[j].isWhitespace()) j--
        if (j < 0) return true
        val p = code[j]
        if (p.isLetterOrDigit() || p == '_' || p == '$') {
            var k = j
            while (k >= 0 && (code[k].isLetterOrDigit() || code[k] == '_' || code[k] == '$')) k--
            return code.substring(k + 1, j + 1) in REGEX_KEYWORDS
        }
        return p != ')' && p != ']' && p != '}' && p != '"' && p != '\'' && p != '`'
    }

    /** Consumes a regex literal starting at [start]; assumes it is one. */
    private fun skipRegex(code: String, start: Int): Int {
        var i = start + 1
        val n = code.length
        var inClass = false
        while (i < n) {
            when (code[i]) {
                '\\' -> i += 2
                '[' -> { inClass = true; i++ }
                ']' -> { inClass = false; i++ }
                '/' -> if (!inClass) return i + 1 else i++
                // A newline means this was not a valid regex after all;
                // fall back to treating the '/' as a plain division sign.
                '\n' -> return start + 1
                else -> i++
            }
        }
        return start + 1
    }

    /** Consumes a quoted string starting at [start], honoring escapes. */
    private fun skipString(code: String, start: Int): Int {
        val q = code[start]
        var i = start + 1
        val n = code.length
        while (i < n) {
            if (code[i] == '\\') i += 2
            else if (code[i] == q) return i + 1
            else i++
        }
        return n
    }

    private fun skipWs(code: String, from: Int): Int {
        var i = from
        while (i < code.length && code[i].isWhitespace()) i++
        return i
    }
}