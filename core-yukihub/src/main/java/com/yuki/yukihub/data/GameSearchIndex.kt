package com.yuki.yukihub.data

import com.yuki.yukihub.model.Game
import java.text.Normalizer
import java.util.Locale

/** Accent-, punctuation- and spacing-insensitive library title/alias index. */
object GameSearchIndex {
    fun build(game: Game, aliases: Iterable<String>): String =
        sequenceOf(game.title.orEmpty(), game.originalTitle.orEmpty())
            .plus(aliases.asSequence())
            .map(::normalize)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString("|")

    fun matches(indexedText: String?, query: String): Boolean {
        if (query.isBlank()) return true
        val haystack = indexedText.orEmpty()
        return query.split(Regex("\\s+"))
            .map(::normalize)
            .filter(String::isNotEmpty)
            .all(haystack::contains)
    }

    fun normalize(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        return buildString(normalized.length) {
            normalized.forEach { character ->
                if (Character.isLetterOrDigit(character)) append(character)
            }
        }
    }
}
