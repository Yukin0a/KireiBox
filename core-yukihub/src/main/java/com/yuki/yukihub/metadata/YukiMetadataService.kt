package com.yuki.yukihub.metadata

import android.content.Context

data class RankedCoverCandidate(val metadata: VnMetadata, val score: Double)
data class RankedCoverSearchResult(
    val candidates: List<RankedCoverCandidate>,
    val attemptedQueries: List<String>,
    val automatic: Boolean
)

/** Source-neutral search/save API used by the Compose cover page. */
class YukiMetadataService(context: Context) {
    private val controller = YukiMetadataController(context.applicationContext)

    fun search(source: CoverSourceMode, keyword: String, limit: Int = 8): List<VnMetadata> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        val bounded = limit.coerceIn(1, 10)
        return when (source) {
            CoverSourceMode.VNDB -> VndbClient.searchCandidates(query, bounded).orEmpty()
            CoverSourceMode.BANGUMI -> BangumiClient.searchCandidates(
                query, controller.bangumiToken(), bounded, false
            ).orEmpty()
            CoverSourceMode.BANGUMI_MIRROR -> BangumiClient.searchCandidates(
                query, controller.bangumiToken(), bounded, true
            ).orEmpty()
            CoverSourceMode.YMGAL -> YmgalClient.searchCandidates(query, bounded).orEmpty()
            CoverSourceMode.HIKARINAGI -> HikarinagiClient.searchCandidates(query, bounded).orEmpty()
        }
    }

    fun searchRanked(source: CoverSourceMode, keyword: String, limit: Int = 8): List<RankedCoverCandidate> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        return rankCandidates(query, search(source, query, limit))
    }

    /** Sequential bounded search. Stops as soon as the established automatic threshold is met. */
    fun searchRankedStaged(
        source: CoverSourceMode,
        keyword: String,
        limit: Int = 8,
        maxQueries: Int = 3
    ): RankedCoverSearchResult {
        val queries = TitleQueryPlanner.networkQueries(keyword, maxQueries)
        if (queries.isEmpty()) return RankedCoverSearchResult(emptyList(), emptyList(), false)
        val attempted = ArrayList<String>()
        val candidates = LinkedHashMap<String, VnMetadata>()
        var ranked = emptyList<RankedCoverCandidate>()
        for (query in queries) {
            attempted += query
            search(source, query, limit).forEach { metadata ->
                val key = metadata.id.ifBlank {
                    "${metadata.romanTitle}|${metadata.originalTitle}|${metadata.chineseTitle}|${metadata.coverUrl}"
                }
                candidates.putIfAbsent(key, metadata)
            }
            ranked = rankCandidates(queries.take(attempted.size), candidates.values.toList())
            if (TitleMatchScorer.isAutomaticMatch(ranked.map { TitleMatchScorer.Match(it.metadata, it.score) })) {
                return RankedCoverSearchResult(ranked, attempted, true)
            }
        }
        return RankedCoverSearchResult(ranked, attempted, false)
    }

    fun save(gameId: Long, source: CoverSourceMode, metadata: VnMetadata) {
        controller.saveMetadata(gameId, source, metadata)
    }

    fun visibleSource(gameId: Long): CoverSourceMode? = controller.visibleSource(gameId)
    fun setVisibleSource(gameId: Long, source: CoverSourceMode?) = controller.setVisibleSource(gameId, source)

    companion object {
        @JvmStatic
        fun rankCandidates(keyword: String, candidates: List<VnMetadata>): List<RankedCoverCandidate> {
            val query = keyword.trim()
            if (query.isEmpty()) return emptyList()
            val variants = TitleQueryPlanner.variants(query).ifEmpty { listOf(query) }
            return TitleMatchScorer.ranked(variants, candidates)
                .filter { it.metadata.coverUrl.isNotBlank() }
                .map { RankedCoverCandidate(it.metadata, it.score.coerceIn(0.0, 100.0)) }
        }

        @JvmStatic
        fun rankCandidates(keywords: List<String>, candidates: List<VnMetadata>): List<RankedCoverCandidate> {
            val queries = keywords.flatMap(TitleQueryPlanner::variants).distinct()
            if (queries.isEmpty()) return emptyList()
            return TitleMatchScorer.ranked(queries, candidates)
                .filter { it.metadata.coverUrl.isNotBlank() }
                .map { RankedCoverCandidate(it.metadata, it.score.coerceIn(0.0, 100.0)) }
        }
    }
}
