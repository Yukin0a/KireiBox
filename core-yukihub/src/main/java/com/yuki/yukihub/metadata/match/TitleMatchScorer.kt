package com.yuki.yukihub.metadata.match
import com.yuki.yukihub.metadata.model.EvidenceMatch
import com.yuki.yukihub.metadata.model.MatchEvidence
import com.yuki.yukihub.metadata.model.TitleEvidence
import com.yuki.yukihub.metadata.model.TitleEvidenceSource
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.metadata.model.VndbSearchHit
import com.yuki.yukihub.metadata.policy.MetadataUtils
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/** Deterministic local title scoring. Network result ordering is supporting evidence only. */
object TitleMatchScorer {
    data class Match(val metadata: VnMetadata, val score: Double)

    private val editionNoise =
        Regex(
            """(?ix)(?:AI\s*翻译|机翻|汉化(?:版|补丁)?|中文(?:版|补丁)?|日文版|体验版|trial|demo|patch|complete|备份|副本|原版|原始版|FHD|HD版)""",
        )

    @JvmStatic
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val cleaned =
            TitleQueryPlanner.stripEngineDecorations(
                Normalizer.normalize(value, Normalizer.Form.NFKC),
            )
                .replace(editionNoise, " ")
        val compact =
            MetadataUtils.cleanTitle(cleaned)
                .lowercase(Locale.ROOT)
                .replace(Regex("""[\p{P}\p{S}\s]+"""), "")
        return canonicalKnownBrand(compact)
    }

    @JvmStatic
    fun score(
        query: String?,
        candidate: String?,
    ): Double {
        val left = normalize(query)
        val right = normalize(candidate)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 100.0
        val shorter = minOf(left.length, right.length).toDouble()
        val longer = maxOf(left.length, right.length).toDouble()
        // Common-substring evidence is only meaningful on the Han runs. Running it on the whole
        // normalized string let a shared *Latin* brand ("9nine") score every sequel 92, so the
        // correct episode could not outrank the wrong ones. Compare Han-only projections instead.
        val leftCjk = cjkOnly(left)
        val rightCjk = cjkOnly(right)
        if (leftCjk.isNotEmpty() && rightCjk.isNotEmpty()) {
            val common = longestCommonSubstring(leftCjk, rightCjk)
            if (common >= 4) {
                val shorterCjk = minOf(leftCjk.length, rightCjk.length)
                return if (common == shorterCjk) 96.0 else 92.0
            }
        }
        if ((left.contains(right) || right.contains(left)) && shorter / longer >= 0.65) return 94.0
        // Whole-token coverage: "ATRI" should match "ATRI -My Dear Moments-" (its leading token)
        // even though punctuation stripping makes the raw strings share no 65 %-length substring.
        wholeTokenScore(query, candidate).takeIf { it > 0.0 }?.let { return it }
        val similarity = 1.0 - levenshtein(left, right).toDouble() / longer
        val token = tokenSimilarity(query, candidate)
        return (similarity * 72.0 + token * 28.0).coerceIn(0.0, 100.0)
    }

    /**
     * Whole-token coverage for a short Latin/kana query against a longer title, the analogue of the
     * CJK common-substring rule above: tokens come from the pre-truncation title so a bracketed
     * prefix ("[KR] ATRI …") still resolves. Returns 94 when the title leads with the query's
     * tokens, 92 when a single distinctive token (≥3 chars) appears anywhere, else 0.
     */
    private fun wholeTokenScore(
        query: String?,
        candidate: String?,
    ): Double {
        val queryTokens = matchTokens(query)
        val candidateTokens = if (queryTokens.isEmpty()) emptyList() else matchTokens(candidate)
        return when {
            candidateTokens.size <= queryTokens.size -> 0.0
            candidateTokens.subList(0, queryTokens.size) == queryTokens -> 94.0
            queryTokens.size == 1 && queryTokens[0].length >= 3 && candidateTokens.contains(queryTokens[0]) -> 92.0
            else -> 0.0
        }
    }

    @JvmStatic
    fun best(
        query: String?,
        candidates: List<VnMetadata>,
    ): Match? =
        candidates
            .map { metadata -> Match(metadata, titleScore(query, metadata)) }
            .maxByOrNull { it.score }

    @JvmStatic
    fun ranked(
        queries: List<String>,
        candidates: List<VnMetadata>,
    ): List<Match> =
        candidates
            .map { metadata ->
                val base = queries.maxOfOrNull { query -> titleScore(query, metadata) } ?: 0.0
                Match(metadata, adjustEpisode(base, queries, metadata))
            }
            .sortedByDescending(Match::score)

    /**
     * Best score across the three primary titles **and every alias**. Aliases matter for sources
     * that publish a translation only as an alternate title (NextMoe `titles[]`), where the
     * primary fields alone would never match a Chinese folder name.
     */
    private fun titleScore(
        query: String?,
        metadata: VnMetadata,
    ): Double {
        var best =
            max(
                score(query, metadata.chineseTitle),
                max(score(query, metadata.originalTitle), score(query, metadata.romanTitle)),
            )
        metadata.titleAliases.forEach { alias -> best = max(best, score(query, alias)) }
        return best
    }

    @JvmStatic
    fun rankedWithEvidence(
        queries: List<String>,
        hits: List<VndbSearchHit>,
    ): List<EvidenceMatch> {
        return rankedFromEvidence(
            queries.map { TitleEvidence(it, TitleEvidenceSource.FOLDER) },
            hits,
        )
    }

    @JvmStatic
    fun rankedFromEvidence(
        evidence: List<TitleEvidence>,
        hits: List<VndbSearchHit>,
    ): List<EvidenceMatch> {
        if (evidence.isEmpty() || hits.isEmpty()) return emptyList()
        return hits.groupBy { hit ->
            hit.metadata.id.ifBlank {
                "${hit.metadata.romanTitle}|${hit.metadata.originalTitle}|${hit.metadata.chineseTitle}"
            }
        }.values.map { candidateHits ->
            val metadata = candidateHits.first().metadata
            val titleFields =
                (
                    listOf(
                        "chinese" to metadata.chineseTitle,
                        "original" to metadata.originalTitle,
                        "roman" to metadata.romanTitle,
                    ) + metadata.titleAliases.mapIndexed { index, value -> "alias_$index" to value }
                )
                    .filter { it.second.isNotBlank() }
            var bestEvidence: TitleEvidence? = null
            var bestField = ""
            var bestTitleScore = 0.0
            val strongScores = LinkedHashMap<TitleEvidenceSource, Double>()
            evidence.forEach { titleEvidence ->
                titleFields.forEach { (field, value) ->
                    val fieldScore = score(titleEvidence.value, value)
                    if (fieldScore > bestTitleScore) {
                        bestTitleScore = fieldScore
                        bestEvidence = titleEvidence
                        bestField = field
                    }
                    if (titleEvidence.source.strong) {
                        strongScores[titleEvidence.source] =
                            max(
                                strongScores[titleEvidence.source] ?: 0.0,
                                fieldScore,
                            )
                    }
                }
            }
            val firstPlaceQueries =
                candidateHits
                    .filter { it.resultRank == 1 }
                    .map { normalize(it.query) }
                    .filter(String::isNotBlank)
                    .distinct()
            val bestRank = candidateHits.minOf(VndbSearchHit::resultRank)
            val bestRankQuery = candidateHits.minByOrNull(VndbSearchHit::resultRank)?.query.orEmpty()
            // Search ordering is only a tie breaker. Weak filename evidence may
            // suggest a candidate for review, but can never auto-bind by itself.
            val base =
                if (bestEvidence?.source?.strong == true) {
                    bestTitleScore
                } else {
                    bestTitleScore.coerceAtMost(89.0)
                }
            val episode = episodeAdjustment(base, evidence.map(TitleEvidence::value), metadata)
            EvidenceMatch(
                metadata = metadata,
                score = episode.score,
                evidence =
                    MatchEvidence(
                        normalizedQuery = normalize(bestEvidence?.value ?: bestRankQuery),
                        matchedField = bestField.ifBlank { "vndb_search_rank" },
                        resultRank = bestRank,
                        titleScore = bestTitleScore,
                        episodeScore = episode.score - base,
                        queryVoteCount = firstPlaceQueries.size,
                        strongSourceCount = strongScores.values.count { it >= 90.0 },
                        episodeConflict = episode.conflict,
                    ),
            )
        }.sortedWith(compareByDescending<EvidenceMatch> { it.score }.thenBy { it.evidence.resultRank })
    }

    @JvmStatic
    fun shouldAutoBind(ranked: List<EvidenceMatch>): Boolean {
        val first = ranked.firstOrNull() ?: return false
        if (first.evidence.episodeConflict) return false
        val second = ranked.getOrNull(1)?.score ?: 0.0
        val lead = first.score - second
        return (
            first.evidence.strongSourceCount >= 1 &&
                first.evidence.titleScore >= 96.0 &&
                first.evidence.matchedField != "vndb_search_rank" && lead >= 8.0
        ) ||
            (
                first.evidence.strongSourceCount >= 2 &&
                    first.evidence.titleScore >= 90.0 && lead >= 5.0
            )
    }

    @JvmStatic
    fun isReviewCandidate(match: EvidenceMatch?): Boolean = match != null && match.score >= 40.0 && match.metadata.coverUrl.isNotBlank()

    @JvmStatic
    fun best(
        queries: List<String>,
        candidates: List<VnMetadata>,
    ): Match? = ranked(queries, candidates).firstOrNull()

    @JvmStatic
    fun isAutomaticMatch(score: Double): Boolean = score >= 88.0

    @JvmStatic
    fun isAutomaticMatch(ranked: List<Match>): Boolean {
        val first = ranked.firstOrNull() ?: return false
        if (first.score >= 96.0) return true
        val second = ranked.getOrNull(1)?.score ?: 0.0
        return first.score >= 88.0 && first.score - second >= 5.0
    }

    private fun adjustEpisode(
        base: Double,
        queries: List<String>,
        metadata: VnMetadata,
    ): Double {
        return episodeAdjustment(base, queries, metadata).score
    }

    private data class EpisodeAdjustment(val score: Double, val conflict: Boolean)

    private val partMarker = Regex("""(?<!\d)(\d{1,2})\s*[部章話话]""")
    private val cjkPartMarker = Regex("""第?\s*([一二两三四五六七八九十]{1,2})\s*[部章話话]""")
    private val episodeWord = Regex("""(?i)\b(?:episode|ep)\s*[-_.]?\s*(\d{1,2})\b""")
    private val chapterMarker = Regex("""第\s*(\d{1,2})\s*[部章話话]?""")
    private val nineFranchise = Regex("""(?i)9\s*[-_.]?\s*nine\s*[-_.]?\s*(\d{1,2})(?!\d)""")
    private val ordinalMarker = Regex("""(?i)(\d{1,2})\s*(?:st|nd|rd|th)\b""")
    private val trailingNumber = Regex("""(?i)(?<![a-z0-9])(\d{1,2})\s*$""")

    private val cjkNumeral =
        mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
        )

    private fun cjkNumber(raw: String): Int? =
        when {
            raw.isEmpty() -> null
            raw.length == 1 -> cjkNumeral[raw[0]]
            cjkNumeral[raw[0]] == null || cjkNumeral[raw[1]] == null -> null
            raw[0] == '十' -> 10 + cjkNumeral.getValue(raw[1])
            raw[1] == '十' -> cjkNumeral.getValue(raw[0]) * 10
            else -> null
        }

    private fun episodeAdjustment(
        base: Double,
        queries: List<String>,
        metadata: VnMetadata,
    ): EpisodeAdjustment {
        val requested =
            queries.mapNotNull(::episodeNumber).firstOrNull()
                ?: return EpisodeAdjustment(base, false)
        val candidateValues =
            listOf(metadata.chineseTitle, metadata.originalTitle, metadata.romanTitle)
                .plus(metadata.titleAliases)
                .mapNotNull(::episodeNumber)
        if (candidateValues.isEmpty()) return EpisodeAdjustment(base, false)
        return if (requested in candidateValues) {
            EpisodeAdjustment((base + 5.0).coerceAtMost(100.0), false)
        } else {
            EpisodeAdjustment((base - 30.0).coerceAtMost(59.0).coerceAtLeast(0.0), true)
        }
    }

    /**
     * The work's own chapter/part number, not just any digit. The old version returned the first
     * number in the string, so "9-nine-2" read as episode 9 (the franchise digit) and every sequel
     * matched every other one. Markers are tried most-specific first:
     * `2部 / 2章 / 第2部 / Episode 2 / ep2 / 9-nine-2 / 9 nine 2 / 2nd`, then a trailing number.
     */
    private fun episodeNumber(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        return partMarker.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: cjkPartMarker.find(normalized)?.groupValues?.getOrNull(1)?.let(::cjkNumber)
            ?: episodeWord.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: chapterMarker.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: nineFranchise.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: ordinalMarker.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: trailingNumber.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun tokenSimilarity(
        left: String?,
        right: String?,
    ): Double {
        val a = tokenize(left)
        val b = tokenize(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble()
    }

    private fun tokenize(value: String?): Set<String> =
        Normalizer
            .normalize(value.orEmpty(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter(String::isNotBlank)
            .toSet()

    /**
     * Ordered word tokens of a title, after dropping engine decorations and NFKC-normalizing. Used by
     * the whole-token coverage rule so a short query matches a longer title's leading word(s).
     */
    private fun matchTokens(value: String?): List<String> =
        value
            ?.takeIf(String::isNotBlank)
            ?.let {
                TitleQueryPlanner
                    .stripEngineDecorations(Normalizer.normalize(it, Normalizer.Form.NFKC))
                    .lowercase(Locale.ROOT)
                    .split(Regex("""[^\p{L}\p{N}]+"""))
                    .filter(String::isNotBlank)
            }.orEmpty()

    private fun cjkOnly(value: String): String =
        value.filter {
            when (Character.UnicodeScript.of(it.code)) {
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                -> true
                else -> false
            }
        }

    private fun canonicalKnownBrand(value: String): String =
        when {
            value == "asnine" -> "as9nine"
            value.matches(
                Regex("""(?i)^(?:as)?9nine(?:arteisia|アズナイン|阿尔特西亚|阿耳忒弥西亚)?$"""),
            ) -> "as9nine"
            else -> value
        }

    private fun longestCommonSubstring(
        left: String,
        right: String,
    ): Int {
        val row = IntArray(right.length + 1)
        var best = 0
        for (i in left.indices) {
            for (j in right.indices.reversed()) {
                row[j + 1] = if (left[i] == right[j]) row[j] + 1 else 0
                if (row[j + 1] > best) best = row[j + 1]
            }
        }
        return best
    }

    private fun levenshtein(
        left: String,
        right: String,
    ): Int {
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] =
                    minOf(
                        current[j] + 1,
                        previous[j + 1] + 1,
                        previous[j] + cost,
                    )
            }
            previous = current
        }
        return previous[right.length]
    }
}
