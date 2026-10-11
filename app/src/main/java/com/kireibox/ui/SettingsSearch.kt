package com.kireibox.ui

import java.util.Locale

// Settings search matching (r4 · S2): pure functions over SettingsSearchIndex. A query is
// normalized (trimmed, lower-cased, full-width folded to half-width), split on spaces, and every
// word must match the title, a keyword, the subtitle or the path of an entry. Spaces and joining
// punctuation are ignored inside words, so 「wifi」 finds 「仅 Wi-Fi 自动匹配」 and 「krkr引擎」 finds
// 「KRKR 引擎」. Pinyin initials are not supported.

internal object SettingsSearch {
    /** How well an entry matches, best first: exact title, title prefix, title contains, keyword, subtitle, path. */
    enum class Tier {
        EXACT_TITLE,
        TITLE_PREFIX,
        TITLE_CONTAINS,
        KEYWORD_EXACT,
        KEYWORD,
        SUBTITLE,
        BREADCRUMB,
    }

    /**
     * A longer Chinese phrase also matches a keyword inside it (「怎么开夜间模式」 → 夜间模式). Only
     * Chinese keywords of two or more characters count: English ones would match inside unrelated
     * words (「log」 in 「login」).
     */
    private const val MIN_CONTAINED_KEYWORD = 2

    /** Characters that only join words: 「Wi-Fi」, 「3D 深度」, 「外观 › 显示」. */
    private val joiners = Regex("""[\s\-_·.・、，,/|›>:：「」『』（）()]+""")

    /** Trims, lower-cases and folds full-width ASCII (ＫＲＫＲ, full-width spaces) to half-width. */
    fun normalize(text: String): String {
        val folded =
            buildString(text.length) {
                text.forEach { char ->
                    append(
                        when (char) {
                            '　' -> ' '
                            in '！'..'～' -> char - FULL_WIDTH_OFFSET
                            else -> char
                        },
                    )
                }
            }
        return folded.lowercase(Locale.ROOT).trim().replace(Regex("""\s+"""), " ")
    }

    /** [normalize]d text without spaces or joining punctuation: the form both sides are compared in. */
    fun compact(text: String): String = normalize(text).replace(joiners, "")

    /** The query's words, each [compact]ed; empty when the query has nothing to match. */
    fun words(query: String): List<String> = normalize(query).split(' ').map(::compact).filter(String::isNotEmpty)

    /** The best tier at which [word] (already compact) matches [entry], or null. */
    fun tier(
        entry: SettingsSearchEntry,
        word: String,
    ): Tier? {
        val forms = entry.forms
        val title = forms.title
        val keywords = forms.keywords
        return when {
            title == word -> Tier.EXACT_TITLE
            title.startsWith(word) -> Tier.TITLE_PREFIX
            title.contains(word) -> Tier.TITLE_CONTAINS
            keywords.any { it == word } -> Tier.KEYWORD_EXACT
            keywords.any { it.contains(word) || containsPhrase(word, it) } -> Tier.KEYWORD
            forms.subtitle?.contains(word) == true -> Tier.SUBTITLE
            forms.breadcrumb.contains(word) -> Tier.BREADCRUMB
            else -> null
        }
    }

    /** An entry matches when every word does; its tier is its weakest word's. */
    fun match(
        entry: SettingsSearchEntry,
        words: List<String>,
    ): Tier? {
        if (words.isEmpty()) return null
        val tiers = words.map { tier(entry, it) }
        return if (tiers.any { it == null }) null else tiers.filterNotNull().maxBy { it.ordinal }
    }

    /**
     * Matching entries ranked by tier. Within a title tier the shorter title wins (more of it is
     * the query: 「更新」 → 检查更新 before 目录自动更新); then an entry whose path also matches
     * (it lives on the page the query names: 「封面」 → 封面与元数据's own switches before an effect
     * with a cover keyword); otherwise the index order holds (pages first, then page by page).
     */
    fun search(
        entries: List<SettingsSearchEntry>,
        query: String,
    ): List<SettingsSearchEntry> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        return entries
            .mapIndexedNotNull { index, entry -> match(entry, words)?.let { tier -> Ranked(entry, tier, index, inPath(entry, words)) } }
            .sortedWith(compareBy({ it.tier.ordinal }, { it.titleWeight }, { if (it.inPath) 0 else 1 }, { it.index }))
            .map { it.entry }
    }

    private fun inPath(
        entry: SettingsSearchEntry,
        words: List<String>,
    ): Boolean = entry.forms.breadcrumb.let { path -> words.all { path.contains(it) } }

    private class Ranked(
        val entry: SettingsSearchEntry,
        val tier: Tier,
        val index: Int,
        val inPath: Boolean,
    ) {
        val titleWeight: Int = if (tier <= Tier.TITLE_CONTAINS) entry.forms.title.length else 0
    }

    private fun containsPhrase(
        word: String,
        keyword: String,
    ): Boolean = keyword.length >= MIN_CONTAINED_KEYWORD && keyword.any { it.code > ASCII_MAX } && word.contains(keyword)

    private const val FULL_WIDTH_OFFSET = 0xFEE0
    private const val ASCII_MAX = 0x7F
}

/** An entry's title, keywords, subtitle and path in [SettingsSearch.compact] form. */
internal class SettingsSearchForms(entry: SettingsSearchEntry) {
    val title: String = SettingsSearch.compact(entry.title)
    val keywords: List<String> = entry.keywords.map(SettingsSearch::compact)
    val subtitle: String? = entry.subtitle?.let(SettingsSearch::compact)
    val breadcrumb: String = SettingsSearch.compact(entry.location.breadcrumb)
}
