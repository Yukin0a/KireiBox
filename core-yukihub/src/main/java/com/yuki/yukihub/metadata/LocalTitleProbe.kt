package com.yuki.yukihub.metadata

import android.content.Context
import com.yuki.yukihub.storage.StorageNodes
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Reads only small, known text configuration files. It never opens XP3 archives
 * and never modifies the selected game directory.
 */
object LocalTitleProbe {
    private const val MAX_TOTAL_BYTES = 256 * 1024
    private const val MAX_FILE_BYTES = 96 * 1024
    private val supportedFiles = setOf(
        "config.tjs", "startup.tjs", "system.ini", "game.ini", "config.ini"
    )
    private val titlePattern = Regex(
        """(?im)(?:System\s*\.\s*title|window\s*\.\s*title|caption|gameTitle|title)\s*[:=]\s*["']([^"'\r\n]{2,100})["']"""
    )

    @JvmStatic
    fun queryHints(context: Context, rootUri: String?): List<String> {
        if (rootUri.isNullOrBlank()) return emptyList()
        val root = runCatching { StorageNodes.resolve(context, rootUri) }.getOrNull() ?: return emptyList()
        var remaining = MAX_TOTAL_BYTES
        val hints = LinkedHashSet<String>()
        runCatching { root.children() }.getOrDefault(emptyList())
            .asSequence()
            .filter { it.isFile }
            .filter { it.name.lowercase() in supportedFiles }
            .forEach { file ->
                if (remaining <= 0) return@forEach
                val bytes = runCatching {
                    file.openInputStream(context)?.use { input ->
                        readLimited(input, minOf(MAX_FILE_BYTES, remaining))
                    }
                }.getOrNull() ?: return@forEach
                remaining -= bytes.size
                decodeCandidates(bytes).forEach { text ->
                    titlePattern.findAll(text).forEach { match ->
                        val title = match.groupValues[1].trim()
                        if (title.length >= 2) hints += title
                    }
                }
            }
        return hints.take(4)
    }

    @JvmStatic
    fun collectEvidence(context: Context, rootUri: String?, folderTitle: String?): List<TitleEvidence> {
        val evidence = ArrayList<TitleEvidence>()
        evidence += TitleQueryPlanner.evidence(folderTitle, TitleEvidenceSource.FOLDER).take(4)
        evidence += queryHints(context, rootUri)
            .flatMap { TitleQueryPlanner.evidence(it, TitleEvidenceSource.CONFIG) }
            .take(6)
        evidence += xp3TitleHints(context, rootUri)
            .flatMap { TitleQueryPlanner.evidence(it, TitleEvidenceSource.XP3_FILE) }
            .take(4)
        return evidence.distinctBy { it.source to TitleMatchScorer.normalize(it.value) }.take(18)
    }

    private fun xp3TitleHints(context: Context, rootUri: String?): List<String> {
        if (rootUri.isNullOrBlank()) return emptyList()
        val root = runCatching { StorageNodes.resolve(context, rootUri) }.getOrNull() ?: return emptyList()
        return runCatching { root.children() }.getOrDefault(emptyList())
            .asSequence()
            .filter { it.isFile }
            .map { it.name }
            .filter { it.endsWith(".xp3", ignoreCase = true) }
            .map { it.substringBeforeLast('.').trim() }
            .filter { stem ->
                val normalized = stem.lowercase()
                normalized !in GENERIC_XP3_NAMES &&
                    normalized.length >= 4 &&
                    normalized.any(Char::isLetter)
            }
            .take(3)
            .toList()
    }

    private fun decodeCandidates(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val charsets = listOf(
            Charsets.UTF_8,
            Charsets.UTF_16LE,
            Charsets.UTF_16BE,
            Charset.forName("Shift_JIS"),
            Charset.forName("GB18030")
        )
        return charsets.mapNotNull { charset ->
            runCatching { String(bytes, charset) }.getOrNull()
                ?.takeIf { '\uFFFD' !in it && it.count(Char::isLetter) >= 2 }
        }.distinct()
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(4096)
        var remaining = limit
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count <= 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    private val GENERIC_XP3_NAMES = setOf(
        "data", "patch", "update", "voice", "video", "movie", "bgm",
        "scenario", "system", "savedata", "plugin", "font", "startup"
    )
}
