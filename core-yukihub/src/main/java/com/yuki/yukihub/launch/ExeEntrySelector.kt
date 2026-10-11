package com.yuki.yukihub.launch

import java.util.Locale

/**
 * Picks the game's own exe among the .exe files of a folder: tools that ship beside a game
 * (uninstaller, redistributable setup, config / crash helpers) lose to the game, a Chinese build
 * (`game_chs.exe`, `汉化版.exe`; simplified before traditional) wins over the original, then a name
 * that matches the folder, and otherwise the largest file is the game.
 */
object ExeEntrySelector {
    data class Entry(
        val name: String,
        val size: Long,
    )

    private val AUXILIARY =
        listOf(
            "unins", "uninst", "setup", "install", "vcredist", "vc_redist", "dxsetup", "directx", "dotnet",
            "crash", "update", "config", "setting", "設定", "设置",
        )

    /** Name parts of a simplified Chinese build; short Latin tags must stand alone (`game_chs`, `cn`). */
    private val SIMPLIFIED_TAGS = listOf("chs", "sc", "cn", "zhcn", "gb", "chinese")
    private val SIMPLIFIED_WORDS = listOf("汉化", "中文", "简体", "简中")
    private val TRADITIONAL_TAGS = listOf("cht", "tc", "tw", "zhtw", "big5")
    private val TRADITIONAL_WORDS = listOf("繁體", "繁体", "繁中")

    /** 0 = simplified Chinese build, 1 = traditional Chinese build, 2 = anything else. */
    fun chineseRank(name: String): Int {
        val stem = stem(name)
        val tags = stem.split(Regex("[^a-z0-9]+")).filter(String::isNotEmpty)
        val endsWith = { tag: String -> tags.any { it == tag || (tag.length >= 3 && it.endsWith(tag) && it.length > tag.length) } }
        return when {
            SIMPLIFIED_WORDS.any(stem::contains) || SIMPLIFIED_TAGS.any(endsWith) -> 0
            TRADITIONAL_WORDS.any(stem::contains) || TRADITIONAL_TAGS.any(endsWith) -> 1
            else -> 2
        }
    }

    fun isAuxiliary(name: String): Boolean {
        val stem = stem(name)
        return AUXILIARY.any { stem.contains(it) }
    }

    /** All [entries] ordered best first: the game's likeliest exe leads, helper tools come last. */
    fun rank(
        entries: List<Entry>,
        folderName: String?,
    ): List<String> {
        val folder = normalize(folderName.orEmpty())
        return entries
            .sortedWith(
                compareBy<Entry> { isAuxiliary(it.name) }
                    .thenBy { chineseRank(it.name) }
                    .thenBy { folder.isEmpty() || normalize(stem(it.name)) != folder }
                    .thenByDescending { it.size }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
            .map(Entry::name)
    }

    fun select(
        entries: List<Entry>,
        folderName: String?,
    ): String? = rank(entries, folderName).firstOrNull()

    private fun stem(name: String): String = name.substringBeforeLast('.').lowercase(Locale.ROOT)

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
