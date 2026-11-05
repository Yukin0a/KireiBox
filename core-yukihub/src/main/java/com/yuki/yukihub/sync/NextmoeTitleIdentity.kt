package com.yuki.yukihub.sync

import com.yuki.yukihub.metadata.model.VnMetadata
import java.text.Normalizer
import java.util.Locale

/** Conservative identity key: remove packaging labels, never sequel numbers or arbitrary subtitles. */
object NextmoeTitleIdentity {
    private val brackets = Regex("""\[[^\[\]]*]|\([^()]*\)|【[^【】]*】""")
    private val packaging = Regex("""(?i)(?:v\d+|cn|jp|en|chs|cht|简中|繁中|中文|汉化|汉化版|汉化硬盘版|简体中文|繁体中文|krkr|kirikiri|tyrano|ty|ons|onscripter|artemis|android|pc|安卓版|v?\d+(?:\.\d+)+)""")

    fun key(title: String): String {
        val normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val cleaned = brackets.replace(normalized) { match ->
            val label = match.value.drop(1).dropLast(1).trim()
            if(label.split(Regex("[+ /_・]+" )).all { it.isNotBlank() && packaging.matches(it) }) " " else match.value
        }.replace(Regex("""\.(?:exe|xp3|zip|7z)$"""), "")
        val unpacked = cleaned.replace(Regex("""(?i)(?:[ _-]*(?:krkr|kirikiri|onscripter|tyrano|artemis|安卓版))$"""), "")
        return unpacked.filter { it.isLetterOrDigit() }
    }

    fun aliases(work: VnMetadata): Set<String> =
        (listOf(work.chineseTitle, work.originalTitle, work.romanTitle) + work.titleAliases)
            .map(::key).filter { it.isNotBlank() }.toSet()
}
