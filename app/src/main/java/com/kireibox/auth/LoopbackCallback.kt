package com.kireibox.auth

import java.io.InputStream
import java.net.URI
import java.net.URLDecoder

/** Only a bounded origin-form request for this session can complete native authorization. */
internal object LoopbackCallback {
    const val MAX_HEAD_BYTES = 8192

    /**
     * Reads the whole request head and returns its request line. The headers must be drained before the
     * reply: closing a socket with unread input resets it, and the browser then shows a connection error
     * instead of the page. Null when the head is oversized or no complete line arrived.
     */
    fun readRequestLine(input: InputStream): String? {
        var total = 0
        var first: String? = null
        var headEnded = false
        val line = StringBuilder()
        while (!headEnded) {
            val byte = input.read()
            if (byte < 0) break
            if (++total > MAX_HEAD_BYTES) return null
            if (byte == '\n'.code) {
                val text = line.toString().trimEnd('\r')
                when (first) {
                    null -> first = text
                    else -> headEnded = text.isEmpty()
                }
                line.setLength(0)
            } else {
                line.append(byte.toChar())
            }
        }
        return first
    }

    fun parse(
        line: String,
        redirect: String,
        expectedState: String?
    ): String? =
        runCatching {
            require(line.length < MAX_HEAD_BYTES && !expectedState.isNullOrBlank())
            val parts = line.trim().split(' ')
            require(parts.size == 3 && parts[0] == "GET" && parts[2] in setOf("HTTP/1.0", "HTTP/1.1"))
            require(parts[1].startsWith("/callback?") && !parts[1].contains('#'))
            val base = URI(redirect)
            val target = base.resolve(parts[1])
            require(target.host == "127.0.0.1" && target.port == base.port && target.path == "/callback")
            val fields =
                target.rawQuery.split('&').map {
                    val pair = it.split('=', limit = 2)
                    URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
                }
            require(fields.map { it.first }.distinct().size == fields.size)
            val query = fields.toMap()
            require(query["state"] == expectedState)
            require(!query["code"].isNullOrBlank() || !query["error"].isNullOrBlank())
            target.toString()
        }.getOrNull()
}
