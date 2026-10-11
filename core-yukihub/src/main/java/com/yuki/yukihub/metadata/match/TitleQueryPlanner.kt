package com.yuki.yukihub.metadata.match
import com.yuki.yukihub.metadata.model.TitleEvidence
import com.yuki.yukihub.metadata.model.TitleEvidenceSource
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.Locale

/**
 * Builds a bounded, ordered query set from real-world folder names.
 *
 * Variants are search-only. They never replace the player's visible folder
 * title, and malformed brackets are intentionally tolerated.
 */
object TitleQueryPlanner {
    private const val ENGINE_TAG = "(?:krkr|kr|onscripter|ons|artemis|art|tyrano|ipc|pc)"
    private val bracketedLeadingEngine =
        Regex(
            """(?ix)^\s*[\[【（(〔「]\s*(?:i?pc\s*[+&/_-]\s*)?$ENGINE_TAG(?:\s*[+&/_-]\s*i?pc)?\s*[\]】）)〕」]\s*[:：_+\-/]*\s*""",
        )
    private val mismatchedLeadingEngine =
        Regex(
            """(?ix)^\s*(?:i?pc\s*[+&/_-]\s*)?$ENGINE_TAG(?:\s*[+&/_-]\s*i?pc)?\s*[\]】）)〕」]\s*[:：_+\-/]*\s*""",
        )
    private val attachedCjkLeadingEngine =
        Regex(
            """(?ix)^\s*$ENGINE_TAG(?=[\p{IsHan}\p{IsHiragana}\p{IsKatakana}])""",
        )
    private val separatedLeadingEngine =
        Regex(
            """(?ix)^\s*$ENGINE_TAG(?:\s+|[:：_+/.\-]+)(?!\d)\s*""",
        )
    private val bracketedTrailingEngine =
        Regex(
            """(?ix)\s*[:：_+\-/]*\s*[\[【（(〔「]\s*$ENGINE_TAG\s*[\]】）)〕」]\s*$""",
        )
    private val attachedCjkTrailingEngine =
        Regex(
            """(?ix)(?<=[\p{IsHan}\p{IsHiragana}\p{IsKatakana}])$ENGINE_TAG\s*$""",
        )
    private val separatedTrailingEngine =
        Regex(
            """(?ix)(?:\s+|[._+\-/]+)$ENGINE_TAG\s*$""",
        )
    private val attachedLatinTrailingEngine =
        Regex(
            """(?ix)^(.{4,}?)(krkr|kr)\s*$""",
        )
    private val noise =
        Regex(
            """(?ix)(?:AI\s*翻译|机翻|汉化(?:版|补丁)?|中文(?:版|补丁)?|日文版|体验版|trial|demo|patch|complete|备份|副本|原版|原始版|FHD|HD版)""",
        )
    private val cjkRun = Regex("""[\p{IsHan}\p{IsHiragana}\p{IsKatakana}]{2,}""")
    private val cjkSentencePunctuation = Regex("""[？?！!。，,、；;：:]""")
    private val latinRun = Regex("""[A-Za-z0-9]+(?:[\s._'!+\-]+[A-Za-z0-9]+)*""")
    private val nineEpisode = Regex("""(?i)\b9\s*[-_]?\s*nine\s*[-_]?\s*(\d{1,2})(?=$|[^A-Za-z0-9])""")
    private val nineBrand = Regex("""(?i)(?:^|[^a-z0-9])9\s*[-_.]?\s*nine(?:[^a-z0-9]|$)""")
    private val bracketBlock = Regex("""[\[【（(〔「][^\]】）)〕」]{0,48}[\]】）)〕」]""")

    /**
     * The canonical `9-nine` brand query, when the folder is part of that franchise. Sources accept
     * `9nine`/`9-nine` and return the whole series, which is the only way to reach an entry whose
     * CJK subtitle the fan translation spells differently (之 vs 籁).
     */
    private fun nineBrandQuery(raw: String): String? = "9nine".takeIf { nineBrand.containsMatchIn(raw) }

    @JvmStatic
    fun variants(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        sourceCandidates(raw).forEach { source ->
            val cleaned = clean(source)
            addVariants(cleaned, out)
            stripAttachedLatinEngineVariant(cleaned)?.let { addVariants(it, out) }
        }
        return out
            .map(String::trim)
            .filter { it.length >= 2 }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(MAX_VARIANTS)
    }

    @JvmStatic
    fun evidence(
        raw: String?,
        source: TitleEvidenceSource,
    ): List<TitleEvidence> = variants(raw).map { TitleEvidence(it, source) }

    /**
     * At most three ordered network queries. Ranking still uses the broader
     * [variants] set, while this bounded list protects mobile data and APIs.
     */
    @JvmStatic
    @JvmOverloads
    fun networkQueries(
        raw: String?,
        limit: Int = 3,
    ): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val bounded = limit.coerceIn(1, 3)
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).replace('\u0000', ' ').trim()
        val out = LinkedHashSet<String>()

        fun add(value: String?) {
            value?.trim()?.takeIf { it.length >= 2 }?.let { candidate ->
                if (out.none { it.equals(candidate, ignoreCase = true) }) out += candidate
            }
        }
        // Series/brand first: sources index a franchise by its Latin stem, while a folder carries a
        // fan-translated CJK subtitle ("9 nine 天色天歌天之音" vs the catalog's "9-nine-天色天歌天籁音").
        // Querying the stem returns the whole series; the local scorer plus the episode number then
        // picks the right entry despite the subtitle variant (which no API can search by).
        add(nineBrandQuery(normalized))
        add(clean(normalized.replace(bracketBlock, " ")))
        val engineStripped = clean(normalized)
        add(engineStripped)
        // Sentence punctuation inside a title must not split one CJK phrase into two runs.
        val cjkSource = engineStripped.replace(cjkSentencePunctuation, "")
        val cjk =
            cjkRun.findAll(cjkSource).map { it.value.trim() }
                .filter { it.length >= 4 }.maxByOrNull(String::length)
        add(cjk)
        // A shorter CJK fragment survives fan-translation particle differences ("谈恋爱" vs the
        // catalog's "谈个恋爱"): the full run is not a substring of the catalog title, so it misses,
        // but dropping the leading verb exposes a searchable fragment.
        add(cjk?.takeIf { it.length >= 6 }?.drop(2))
        variants(engineStripped).forEach(::add)
        return out.take(bounded)
    }

    @JvmStatic
    fun stripEngineDecorations(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var value = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
        repeat(8) {
            val previous = value
            value =
                value
                    .replace(bracketedLeadingEngine, "")
                    .replace(mismatchedLeadingEngine, "")
                    .replace(attachedCjkLeadingEngine, "")
                    .replace(separatedLeadingEngine, "")
                    .replace(bracketedTrailingEngine, "")
                    .replace(attachedCjkTrailingEngine, "")
                    .replace(separatedTrailingEngine, "")
                    .trim(' ', '-', '_', '.', '+', '/', '：', ':')
            if (value == previous) return value
        }
        return value
    }

    private fun sourceCandidates(raw: String): List<String> {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).replace('\u0000', ' ')
        val candidates = LinkedHashSet<String>()
        normalized.split(Regex("""[\r\n/／|｜]+"""))
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach(candidates::add)
        candidates += normalized
        repairMojibake(normalized).forEach(candidates::add)
        return candidates.toList()
    }

    private fun clean(value: String): String {
        var result =
            stripEngineDecorations(value)
                .replace(noise, " ")
                .replace(Regex("""^[\[\]【】（()）\s+_-]+"""), "")
                .replace(Regex("""[\[\]【】（()）]\s*$"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim(' ', '-', '_', '.', '+', '!', '！')
        // Mismatched brackets such as "[KRKR】" and "IPC+KR】" are common.
        result = result.replace(Regex("""^[\[\]【】（()）]+"""), "").trim()
        return stripEngineDecorations(result)
    }

    private fun addVariants(
        value: String,
        out: LinkedHashSet<String>,
    ) {
        if (value.isBlank()) return
        out += value

        if (isAsNineBrand(value)) {
            out += "as9nine"
            out += "as:9-nine-"
            out += "Asnine"
            out += "as:9-nine- ARTEISIA"
        }

        nineEpisode.find(value)?.let { match ->
            val episode = match.groupValues[1]
            out += "9-nine- Episode $episode"
            out += "9nine $episode"
            out += "9-nine-$episode"
        }

        cjkRun.findAll(value)
            .map { it.value.trim() }
            .filter { it.length >= 2 }
            .forEach { cjk ->
                out += cjk
                splitCjkConnectors(cjk).forEach(out::add)
            }

        latinRun.findAll(value)
            .map { it.value.trim() }
            .filter { it.length >= 2 }
            .forEach { latin ->
                out += latin
                out +=
                    latin.replace(Regex("""[._]+"""), " ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()
            }
    }

    /**
     * Latin/digit folder names often attach the engine suffix without a separator
     * (for example `as9ninekrkr`). Keep the original query and add a stripped
     * query-only variant; never mutate the player's displayed title.
     */
    private fun stripAttachedLatinEngineVariant(value: String): String? {
        val match = attachedLatinTrailingEngine.matchEntire(value.trim()) ?: return null
        val stem = match.groupValues[1].trimEnd(' ', '-', '_', '.', '+', '/')
        if (stem.length < 4 || stem.none { it.isLetterOrDigit() }) return null
        // Numbered titles such as KR-17 are engine-looking names, not decorations.
        if (stem.matches(Regex("""(?i)^kr[._-]?\d+$"""))) return null
        return stem.takeIf { !it.equals(value, ignoreCase = true) }
    }

    private fun isAsNineBrand(value: String): Boolean {
        val compact =
            Normalizer.normalize(value, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
                .replace(Regex("""[\p{P}\p{S}\s]+"""), "")
        return compact.matches(
            Regex("""(?i)^(?:as)?9nine(?:arteisia|アズナイン|阿尔特西亚|阿耳忒弥西亚)?$"""),
        ) || compact == "asnine"
    }

    private fun splitCjkConnectors(value: String): List<String> {
        if (value.length < 7) return emptyList()
        return value.split(Regex("""[的与之]"""))
            .map(String::trim)
            .filter { it.length >= 4 }
            .take(2)
    }

    /**
     * Produces only replacement-free repair candidates. A repaired string is a
     * query hint and never mutates the stored title.
     */
    private fun repairMojibake(value: String): List<String> {
        if (value.none { it in MOJIBAKE_MARKERS }) return emptyList()
        val result = LinkedHashSet<String>()
        val pairs =
            listOf(
                Charsets.ISO_8859_1 to Charsets.UTF_8,
                Charset.forName("windows-1252") to Charsets.UTF_8,
                Charset.forName("GB18030") to Charsets.UTF_8,
                Charset.forName("Shift_JIS") to Charsets.UTF_8,
            )
        pairs.forEach { (source, target) ->
            runCatching {
                val repaired = String(value.toByteArray(source), target)
                if ('\uFFFD' !in repaired && repaired != value && repaired.count(Char::isLetter) >= 2) {
                    result += repaired
                }
            }
        }
        return result.toList()
    }

    private const val MAX_VARIANTS = 12
    private const val MOJIBAKE_MARKERS =
        "脙脗忙莽氓盲茅猫矛貌霉閺勯惃鍕勾娑撳秵鐖跺〒閹村繒浼閻忓灚"
}
