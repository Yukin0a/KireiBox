package com.yuki.yukihub.data.db

import com.yuki.yukihub.model.Game
import com.yuki.yukihub.storage.StorageRootIdentity
import java.util.ArrayList
import java.util.Locale

/**
 * Canonical identity keys for a game row (root URI + entry key).
 *
 * Extracted from `com.yuki.yukihub.data.GameRepository` so identity normalization lives in one
 * place. GameRepository keeps its public companion API by delegating here, so callers and tests
 * are unchanged.
 */
internal object GameIdentityKey {
    fun normalizeRootUriKey(value: String?): String = StorageRootIdentity.key(value)

    fun normalizeEntryKey(value: String?): String {
        if (value.isNullOrBlank()) return ""
        if (value.contains("://")) return ""
        val parts = value.trim().replace('\\', '/').split('/')
        val safe = ArrayList<String>(parts.size)
        for (part in parts) {
            val segment = part.trim()
            if (segment.isEmpty() || segment == ".") continue
            if (segment == ".." || segment.contains('\u0000')) return ""
            safe += segment
        }
        return safe.joinToString("/").lowercase(Locale.ROOT)
    }

    fun gameIdentityKey(game: Game?): String {
        if (game == null) return ""
        return gameIdentityKey(game.rootUri, game.entryKey)
    }

    fun gameIdentityKey(
        rootUri: String?,
        entryKey: String?,
    ): String {
        val root = normalizeRootUriKey(rootUri)
        val entry = normalizeEntryKey(entryKey)
        return if (entry.isBlank()) root else "$root#entry=$entry"
    }

    /** Path leaf used for conservative "same folder" fallback matching. */
    fun portableRootLeaf(value: String?): String {
        val key = normalizeRootUriKey(value)
        if (key.isEmpty()) return ""
        val slash = key.lastIndexOf('/')
        var leaf = if (slash >= 0) key.substring(slash + 1) else key
        val colon = leaf.lastIndexOf(':')
        if (colon >= 0 && colon < leaf.length - 1) leaf = leaf.substring(colon + 1)
        return normalizeIdentityText(leaf)
    }

    fun normalizeIdentityText(value: String?): String {
        if (value == null) return ""
        return value.trim().replace("\\s+".toRegex(), " ").lowercase(Locale.ROOT)
    }
}
