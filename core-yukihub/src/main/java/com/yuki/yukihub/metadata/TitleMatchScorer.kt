package com.yuki.yukihub.metadata

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/** Deterministic local title scoring. Network result ordering is supporting evidence only. */
object TitleMatchScorer {
    data class Match(val metadata: VnMetadata, val score: Double)

    private val editionNoise = Regex(
        """(?ix)(?:AI\s*翻译|机翻|汉化(?:版|补丁)?|中文(?:版|补丁)?|日文版|体验版|trial|demo|patch|complete|备份|副本|原版|原始版|FHD|HD版)"""
    )

    @JvmStatic
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val cleaned = TitleQueryPlanner.stripEngineDecorations(
            Normalizer.normalize(value, Normalizer.Form.NFKC)
        )
            .replace(editionNoise, " ")
        val compact = MetadataUtils.cleanTitle(cleaned)
            .lowercase(Locale.ROOT)
            .replace(Regex("""[\p{P}\p{S}\s]+"""), "")
        return canonicalKnownBrand(compact)
    }

    @JvmStatic
    fun score(query: String?, candidate: String?): Double {
        val left = normalize(query)
        val right = normalize(candidate)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 100.0
        val shorter = minOf(left.length, right.length).toDouble()
        val longer = maxOf(left.length, right.length).toDouble()
        if (isCjk(left) && isCjk(right)) {
            val common = longestCommonSubstring(left, right)
            if (common >= 4) return if (common == shorter.toInt()) 96.0 else 92.0
        }
        if ((left.contains(right) || right.contains(left)) && shorter / longer >= 0.65) return 94.0
        val similarity = 1.0 - levenshtein(left, right).toDouble() / longer
        val token = tokenSimilarity(query, candidate)
        return (similarity * 72.0 + token * 28.0).coerceIn(0.0, 100.0)
    }

    @JvmStatic
    fun best(query: String?, candidates: List<VnMetadata>): Match? = candidates
        .map { metadata ->
            Match(
                metadata,
                max(
                    score(query, metadata.chineseTitle),
                    max(score(query, metadata.originalTitle), score(query, metadata.romanTitle))
                )
            )
        }
        .maxByOrNull { it.score }

    @JvmStatic
    fun ranked(queries: List<String>, candidates: List<VnMetadata>): List<Match> = candidates
        .map { metadata ->
            val base = queries.maxOfOrNull { query ->
                max(
                    score(query, metadata.chineseTitle),
                    max(score(query, metadata.originalTitle), score(query, metadata.romanTitle))
                )
            } ?: 0.0
            Match(metadata, adjustEpisode(base, queries, metadata))
        }
        .sortedByDescending(Match::score)

    @JvmStatic
    fun rankedWithEvidence(queries: List<String>, hits: List<VndbSearchHit>): List<EvidenceMatch> {
        return rankedFromEvidence(
            queries.map { TitleEvidence(it, TitleEvidenceSource.FOLDER) },
            hits
        )
    }

    @JvmStatic
    fun rankedFromEvidence(evidence: List<TitleEvidence>, hits: List<VndbSearchHit>): List<EvidenceMatch> {
        if (evidence.isEmpty() || hits.isEmpty()) return emptyList()
        return hits.groupBy { hit ->
            hit.metadata.id.ifBlank {
                "${hit.metadata.romanTitle}|${hit.metadata.originalTitle}|${hit.metadata.chineseTitle}"
            }
        }.values.map { candidateHits ->
            val metadata = candidateHits.first().metadata
            val titleFields = (listOf(
                "chinese" to metadata.chineseTitle,
                "original" to metadata.originalTitle,
                "roman" to metadata.romanTitle
            ) + metadata.titleAliases.mapIndexed { index, value -> "alias_$index" to value })
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
                        strongScores[titleEvidence.source] = max(
                            strongScores[titleEvidence.source] ?: 0.0,
                            fieldScore
                        )
                    }
                }
            }
            val firstPlaceQueries = candidateHits
                .filter { it.resultRank == 1 }
                .map { normalize(it.query) }
                .filter(String::isNotBlank)
                .distinct()
            val bestRank = candidateHits.minOf(VndbSearchHit::resultRank)
            val bestRankQuery = candidateHits.minByOrNull(VndbSearchHit::resultRank)?.query.orEmpty()
            // Search ordering is only a tie breaker. Weak filename evidence may
            // suggest a candidate for review, but can never auto-bind by itself.
            val base = if (bestEvidence?.source?.strong == true) {
                bestTitleScore
            } else {
                bestTitleScore.coerceAtMost(89.0)
            }
            val episode = episodeAdjustment(base, evidence.map(TitleEvidence::value), metadata)
            EvidenceMatch(
                metadata = metadata,
                score = episode.score,
                evidence = MatchEvidence(
                    normalizedQuery = normalize(bestEvidence?.value ?: bestRankQuery),
                    matchedField = bestField.ifBlank { "vndb_search_rank" },
                    resultRank = bestRank,
                    titleScore = bestTitleScore,
                    episodeScore = episode.score - base,
                    queryVoteCount = firstPlaceQueries.size,
                    strongSourceCount = strongScores.values.count { it >= 90.0 },
                    episodeConflict = episode.conflict
                )
            )
        }.sortedWith(compareByDescending<EvidenceMatch> { it.score }.thenBy { it.evidence.resultRank })
    }

    @JvmStatic
    fun shouldAutoBind(ranked: List<EvidenceMatch>): Boolean {
        val first = ranked.firstOrNull() ?: return false
        if (first.evidence.episodeConflict) return false
        val second = ranked.getOrNull(1)?.score ?: 0.0
        val lead = first.score - second
        return (first.evidence.strongSourceCount >= 1 &&
            first.evidence.titleScore >= 96.0 &&
            first.evidence.matchedField != "vndb_search_rank" && lead >= 8.0) ||
            (first.evidence.strongSourceCount >= 2 &&
                first.evidence.titleScore >= 90.0 && lead >= 5.0)
    }

    @JvmStatic
    fun isReviewCandidate(match: EvidenceMatch?): Boolean =
        match != null && match.score >= 40.0 && match.metadata.coverUrl.isNotBlank()

    @JvmStatic
    fun best(queries: List<String>, candidates: List<VnMetadata>): Match? =
        ranked(queries, candidates).firstOrNull()

    @JvmStatic
    fun isAutomaticMatch(score: Double): Boolean = score >= 88.0

    @JvmStatic
    fun isAutomaticMatch(ranked: List<Match>): Boolean {
        val first = ranked.firstOrNull() ?: return false
        if (first.score >= 96.0) return true
        val second = ranked.getOrNull(1)?.score ?: 0.0
        return first.score >= 88.0 && first.score - second >= 5.0
    }

    private fun adjustEpisode(base: Double, queries: List<String>, metadata: VnMetadata): Double {
        return episodeAdjustment(base, queries, metadata).score
    }

    private data class EpisodeAdjustment(val score: Double, val conflict: Boolean)

    private fun episodeAdjustment(base: Double, queries: List<String>, metadata: VnMetadata): EpisodeAdjustment {
        val requested = queries.mapNotNull(::episodeNumber).firstOrNull()
            ?: return EpisodeAdjustment(base, false)
        val candidateValues = listOf(metadata.chineseTitle, metadata.originalTitle, metadata.romanTitle)
            .plus(metadata.titleAliases)
            .mapNotNull(::episodeNumber)
        if (candidateValues.isEmpty()) return EpisodeAdjustment(base, false)
        return if (requested in candidateValues) {
            EpisodeAdjustment((base + 5.0).coerceAtMost(100.0), false)
        } else {
            EpisodeAdjustment((base - 30.0).coerceAtMost(59.0).coerceAtLeast(0.0), true)
        }
    }

    private fun episodeNumber(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        return Regex("""(?i)(?:^|[\s._-])(?:episode|ep)?\s*(\d{1,2})(?=$|[\s._-]|[\p{IsHan}\p{IsHiragana}\p{IsKatakana}])""")
            .find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun tokenSimilarity(left: String?, right: String?): Double {
        val a = tokenize(left)
        val b = tokenize(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble()
    }

    private fun tokenize(value: String?): Set<String> = Normalizer
        .normalize(value.orEmpty(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .split(Regex("""[^\p{L}\p{N}]+"""))
        .filter(String::isNotBlank)
        .toSet()

    private fun isCjk(value: String): Boolean =
        value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    private fun canonicalKnownBrand(value: String): String = when {
        value == "asnine" -> "as9nine"
        value.matches(
            Regex("""(?i)^(?:as)?9nine(?:arteisia|アズナイン|阿尔特西亚|阿耳忒弥西亚)?$""")
        ) -> "as9nine"
        else -> value
    }

    private fun longestCommonSubstring(left: String, right: String): Int {
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

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}
