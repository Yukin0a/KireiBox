package com.kireibox.ui

import com.yuki.yukihub.metadata.RankedCoverCandidate
import com.yuki.yukihub.metadata.match.CoverMatchConfidence
import com.yuki.yukihub.metadata.match.CoverMatchDecision
import com.yuki.yukihub.metadata.match.TitleMatchScorer
import com.yuki.yukihub.metadata.match.WorkMatch
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata

/**
 * Bridges the merged cover cards to the pure [CoverMatchConfidence] scorer.
 *
 * The scorer needs [WorkMatch] evidence (contributing sources, best result rank, lexical score,
 * id-link flag); this object builds it from a [CoverCandidateUi] and exposes the resulting display
 * order and badge reasons. An exact lexical hit scores 100 and therefore always ranks first.
 */
internal object CoverCandidateEvidence {
    fun lexicalScore(
        query: String,
        metadata: VnMetadata
    ): Double =
        (listOf(metadata.chineseTitle, metadata.originalTitle, metadata.romanTitle) + metadata.titleAliases)
            .maxOfOrNull { TitleMatchScorer.score(query, it) } ?: 0.0

    /** True when the query matches an alias better than any primary title (an alias-only hit). */
    fun aliasHit(
        query: String,
        metadata: VnMetadata
    ): Boolean {
        val primary =
            listOf(metadata.chineseTitle, metadata.originalTitle, metadata.romanTitle)
                .maxOfOrNull { TitleMatchScorer.score(query, it) } ?: 0.0
        val alias = metadata.titleAliases.maxOfOrNull { TitleMatchScorer.score(query, it) } ?: 0.0
        return alias > 0.0 && alias > primary
    }

    fun workMatch(
        query: String,
        candidate: CoverCandidateUi
    ): WorkMatch =
        WorkMatch(
            metadata = candidate.metadata,
            sources = candidate.sourceBadges,
            bestRank = candidate.bestRank,
            lexicalScore = lexicalScore(query, candidate.metadata),
            linkedById = candidate.linkedById,
        )

    fun decision(
        query: String,
        candidate: CoverCandidateUi
    ): CoverMatchDecision = CoverMatchConfidence.evaluate(listOf(workMatch(query, candidate)), query = query)

    /**
     * Scores every merged card and returns them ordered by [CoverMatchConfidence.displayScore]
     * (an exact lexical hit at 100 always wins), then by best result rank, then by the dynamic
     * source [order]. The decision's reasons are attached so the card can render its badges.
     */
    fun decorate(
        query: String,
        candidates: List<CoverCandidateUi>,
        order: List<CoverSourceMode> = CoverSourceMode.active,
    ): List<CoverCandidateUi> =
        candidates
            .map { candidate -> candidate to decision(query, candidate) }
            .sortedWith(
                compareByDescending<Pair<CoverCandidateUi, CoverMatchDecision>> { it.second.score }
                    .thenBy {
                        val rank = it.first.bestRank
                        if (rank > 0) rank else Int.MAX_VALUE
                    }
                    .thenBy {
                        order.indexOf(it.first.source).let { index -> if (index < 0) Int.MAX_VALUE else index }
                    },
            )
            .map { (candidate, decision) ->
                candidate.copy(
                    score = decision.score,
                    matchReasons = decision.reasons,
                    aliasHit = aliasHit(query, candidate.metadata),
                )
            }
}

/**
 * Maps a ranked candidate to its picker card. Hybrid candidates remember their own catalog and
 * cover provenance; a single-source path falls back to the caller's [fallbackSource].
 */
internal fun RankedCoverCandidate.toUi(fallbackSource: CoverSourceMode): CoverCandidateUi =
    CoverCandidateUi(
        metadata = metadata,
        source = source ?: fallbackSource,
        score = score,
        sources = sources,
        coverSources = coverSources,
        bestRank = sourceRank,
        matchReasons = matchReasons,
    )

/** Maps a merged picker card back to a queue candidate, preserving source/cover provenance. */
internal fun CoverCandidateUi.toRankedCandidate(): RankedCoverCandidate =
    RankedCoverCandidate(
        metadata = metadata,
        score = score,
        sourceRank = bestRank,
        source = source,
        sources = sourceBadges,
        coverSources = coverSources,
    )
