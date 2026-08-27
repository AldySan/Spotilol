package com.project.lol.util

object DebugLogStore {
    private const val MAX = 250
    private data class Entry(val t: Long, val tag: String, val msg: String)

    private val buf = ArrayDeque<Entry>()
    private val lock = Any()

    fun log(tag: String, msg: String) {
        val e = Entry(System.currentTimeMillis(), tag, msg.take(600))
        synchronized(lock) {
            buf.addLast(e)
            while (buf.size > MAX) buf.removeFirst()
        }
        android.util.Log.d("SpotilolDbg", "[$tag] $msg")
    }

    fun snapshot(): List<String> = synchronized(lock) {
        val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        buf.map { "${fmt.format(it.t)} [${it.tag}] ${it.msg}" }
    }

    @Suppress("unused")
    fun count(): Int = synchronized(lock) { buf.size }

    fun clear() = synchronized(lock) { buf.clear() }
}