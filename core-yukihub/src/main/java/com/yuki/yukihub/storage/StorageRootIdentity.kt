package com.yuki.yukihub.storage

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Stable identity for a physical storage directory across direct file access and SAF.
 *
 * Android exposes the same primary-storage directory as both
 * `file:///storage/emulated/0/...` and an ExternalStorageProvider document id such as
 * `primary:...`.  Raw URI equality therefore cannot be used for scan-root or game identity.
 */
object StorageRootIdentity {
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    @JvmStatic
    fun key(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val parsed = runCatching { URI(raw) }.getOrNull()
        return when (parsed?.scheme?.lowercase(Locale.ROOT)) {
            "content" -> contentKey(parsed)
            "file" -> fileKey(decode(parsed.rawPath.orEmpty()))
            null, "" -> fileKey(decode(raw))
            else -> genericKey(raw)
        }
    }

    @JvmStatic
    fun equivalent(first: String?, second: String?): Boolean {
        val left = key(first)
        return left.isNotEmpty() && left == key(second)
    }

    /** True only for the same directory or a real path-segment descendant. */
    @JvmStatic
    fun contains(root: String?, candidate: String?): Boolean {
        val rootKey = key(root).trimEnd('/')
        val candidateKey = key(candidate).trimEnd('/')
        if (rootKey.isEmpty() || candidateKey.isEmpty()) return false
        return candidateKey == rootKey || candidateKey.startsWith("$rootKey/")
    }

    private fun contentKey(uri: URI): String {
        val authority = uri.authority?.lowercase(Locale.ROOT).orEmpty()
        val encodedPath = uri.rawPath.orEmpty()
        val documentId = encodedSegmentAfter(encodedPath, "/document/")
            ?: encodedSegmentAfter(encodedPath, "/tree/")
        val decodedId = decode(documentId.orEmpty())
        if (authority == EXTERNAL_STORAGE_AUTHORITY && decodedId.isNotEmpty()) {
            return externalStorageDocumentKey(decodedId)
        }
        val stableDocument = normalizedSegments(decodedId.ifEmpty { decode(encodedPath) })
        return "content:$authority:/$stableDocument".trimEnd('/')
    }

    private fun encodedSegmentAfter(path: String, marker: String): String? {
        val index = path.lastIndexOf(marker)
        if (index < 0) return null
        return path.substring(index + marker.length).substringBefore('/').takeIf(String::isNotEmpty)
    }

    private fun externalStorageDocumentKey(documentId: String): String {
        val decoded = documentId.replace('\\', '/').trim('/')
        val volume = decoded.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
        val relative = decoded.substringAfter(':', missingDelimiterValue = "")
        return when (volume) {
            "primary" -> storageKey("primary", relative)
            "home" -> storageKey("primary", listOf("documents", relative).filter(String::isNotBlank).joinToString("/"))
            "" -> "content:$EXTERNAL_STORAGE_AUTHORITY:/${normalizedSegments(decoded)}".trimEnd('/')
            else -> storageKey("volume:$volume", relative)
        }
    }

    private fun fileKey(path: String): String {
        val normalized = "/${normalizedSegments(path)}".replace(Regex("/+"), "/")
        val lower = normalized.lowercase(Locale.ROOT).trimEnd('/').ifEmpty { "/" }
        val primaryPrefixes = listOf(
            "/storage/emulated/0",
            "/storage/self/primary",
            "/mnt/sdcard",
            "/sdcard",
        )
        primaryPrefixes.firstOrNull { lower == it || lower.startsWith("$it/") }?.let { prefix ->
            return storageKey("primary", lower.removePrefix(prefix).trim('/'))
        }
        val storagePrefix = "/storage/"
        if (lower.startsWith(storagePrefix)) {
            val remainder = lower.removePrefix(storagePrefix)
            val volume = remainder.substringBefore('/')
            if (volume.isNotBlank() && volume != "emulated" && volume != "self") {
                return storageKey("volume:$volume", remainder.substringAfter('/', ""))
            }
        }
        return "file:$lower"
    }

    private fun storageKey(volume: String, relative: String): String {
        val clean = normalizedSegments(relative)
        return if (clean.isEmpty()) "$volume:/" else "$volume:/$clean"
    }

    private fun genericKey(raw: String): String = raw
        .replace('\\', '/')
        .replace(Regex("/+"), "/")
        .trimEnd('/')
        .lowercase(Locale.ROOT)

    private fun normalizedSegments(value: String): String {
        val output = ArrayList<String>()
        decode(value).replace('\\', '/').split('/').forEach { rawSegment ->
            val segment = rawSegment.trim()
            when (segment) {
                "", "." -> Unit
                ".." -> if (output.isNotEmpty()) output.removeAt(output.lastIndex)
                else -> output += segment.lowercase(Locale.ROOT)
            }
        }
        return output.joinToString("/")
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
