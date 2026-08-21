package com.yuki.yukihub.launch

import java.text.Normalizer
import java.util.Locale

/**
 * Ranks KRKR XP3 archives to pick the real launch entry instead of the first
 * alphabetical archive (asset packs such as bgimage.xp3 are never valid entries).
 *
 * Mirrors the practical Tyranor/Kirikiroid2 behavior: prefer data.xp3, then an
 * archive named after the game folder/title, then the largest non-asset archive;
 * returns null when only asset packs remain so callers can fall back to directory
 * mode and let the engine locate startup.tjs inside the archives itself.
 */
object Xp3EntrySelector {

    data class Entry(
        @JvmField val name: String,
        @JvmField val size: Long,
    )

    /** Pure asset packs that can never be the game entry archive. */
    val ASSET_STEMS: Set<String> = setOf(
        "bgimage", "bgm", "evimage", "stimage", "fgimage", "voice", "video", "movie",
        "op", "opmv", "op_movie", "ed", "char", "system", "title", "image", "sound",
        "se", "sysvoice", "cm", "icon", "logo", "fan", "pkg", "backimage", "background",
    )

    /**
     * Returns the best entry file name, or null when there is no reliable
     * candidate (empty input, or only asset packs present).
     */
    @JvmStatic
    @JvmOverloads
    fun select(
        entries: List<Entry>,
        folderName: String? = null,
        gameTitle: String? = null,
    ): String? {
        if (entries.isEmpty()) return null
        // 1) data.xp3 is the conventional KRKR entry (Tyranor default).
        entries.firstOrNull { it.name.equals("data.xp3", ignoreCase = true) }?.let { return it.name }
        // 2) exact folder-name / game-title match.
        val folderStem = stemOf(folderName)
        if (!folderStem.isNullOrEmpty()) {
            entries.firstOrNull { normalize(it.stem()) == folderStem }?.let { return it.name }
        }
        val titleStem = stemOf(gameTitle)
        if (!titleStem.isNullOrEmpty()) {
            entries.firstOrNull { normalize(it.stem()) == titleStem }?.let { return it.name }
        }
        // 3) contains-based folder/title match.
        entries.firstOrNull { matchesContains(it.stem(), folderStem) }?.let { return it.name }
        entries.firstOrNull { matchesContains(it.stem(), titleStem) }?.let { return it.name }
        // 4) largest non-asset archive; patch.xp3 stays a valid candidate.
        val nonAsset = entries.filterNot { isAssetName(it.name) }
        if (nonAsset.isEmpty()) return null
        return nonAsset.maxByOrNull { it.size }?.name
    }

    /** True when the file name is a pure asset pack (never the entry). */
    @JvmStatic
    fun isAssetName(name: String?): Boolean {
        val stem = stemOf(name) ?: return false
        return stem in ASSET_STEMS
    }

    /** True when a stored launch target points at a pure asset pack. */
    @JvmStatic
    fun isAssetTarget(target: String?): Boolean {
        if (target.isNullOrBlank() || target.startsWith("[")) return false
        return isAssetName(target.substringAfterLast('/').substringAfterLast('\\'))
    }

    private fun Entry.stem(): String = name.substringBeforeLast('.')

    private fun stemOf(value: String?): String? {
        val base = value?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.substringBeforeLast('.')?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val normalized = normalize(base)
        return normalized.takeIf(String::isNotEmpty)
    }

    private fun matchesContains(stem: String, reference: String?): Boolean {
        if (reference.isNullOrEmpty() || reference.length < 2) return false
        val a = normalize(stem)
        if (a.isEmpty()) return false
        return a.contains(reference) || reference.contains(a)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")
}