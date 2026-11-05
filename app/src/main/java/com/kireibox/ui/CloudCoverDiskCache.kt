package com.kireibox.ui

import java.io.File
import java.security.MessageDigest

/** Original cloud artwork survives process restarts; Android may reclaim this bounded cache. */
internal class CloudCoverDiskCache(private val directory: File, private val maxBytes: Long = 64L * 1024 * 1024) {
    @Synchronized
    fun find(source: String, url: String): File? = file(source, url).takeIf { it.isFile && it.length() > 0 }?.also {
        it.setLastModified(System.currentTimeMillis())
    }

    /** Only call after successful image decoding, so errors and non-image responses never persist. */
    @Synchronized
    fun put(source: String, url: String, downloaded: File) {
        if (downloaded.length() !in 1..maxBytes) return
        directory.mkdirs()
        val temporary = File.createTempFile("pending-", ".tmp", directory)
        try {
            downloaded.copyTo(temporary, overwrite = true)
            check(temporary.renameTo(file(source, url)))
            val entries = directory.listFiles()?.filter { it.extension == "img" }?.sortedBy { it.lastModified() }.orEmpty()
            var bytes = entries.sumOf { it.length() }
            for (entry in entries) {
                if (bytes <= maxBytes) break
                val size = entry.length()
                if (entry.delete()) bytes -= size
            }
        } finally {
            temporary.delete()
        }
    }

    private fun file(source: String, url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest("$source\n$url".toByteArray())
        return File(directory, digest.joinToString("") { "%02x".format(it) } + ".img")
    }
}
