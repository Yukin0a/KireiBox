package com.yuki.yukihub.scanner

import com.yuki.yukihub.data.GameRepository
import java.text.Normalizer
import java.util.Locale

object ScanExclusionMatcher {
    @JvmStatic
    fun matchesName(
        name: String?,
        patterns: Collection<String>,
    ): Boolean {
        val normalizedName = normalize(name)
        if (normalizedName.isEmpty()) return false
        return patterns.any { glob(normalizedName, normalize(it)) }
    }

    @JvmStatic
    fun matchesUri(
        uri: String?,
        ignoredUriKeys: Collection<String>,
    ): Boolean {
        val key = GameRepository.normalizeRootUriKey(uri)
        return key.isNotEmpty() && ignoredUriKeys.contains(key)
    }

    @JvmStatic
    fun normalize(value: String?): String =
        Normalizer
            .normalize(value.orEmpty(), Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)

    private fun glob(
        value: String,
        pattern: String,
    ): Boolean {
        if (pattern.isEmpty()) return false
        val starts = pattern.startsWith("*")
        val ends = pattern.endsWith("*")
        val core = pattern.trim('*')
        if (core.isEmpty()) return false
        return when {
            starts && ends -> value.contains(core)
            starts -> value.endsWith(core)
            ends -> value.startsWith(core)
            else -> value == core
        }
    }
}
