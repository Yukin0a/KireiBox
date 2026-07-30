package com.yuki.yukihub.metadata

data class VndbSearchHit(
    val metadata: VnMetadata,
    val query: String,
    val resultRank: Int
)

data class MatchEvidence(
    val normalizedQuery: String,
    val matchedField: String,
    val resultRank: Int,
    val titleScore: Double,
    val episodeScore: Double,
    val queryVoteCount: Int,
    val strongSourceCount: Int = 0,
    val episodeConflict: Boolean = false
)

data class EvidenceMatch(
    val metadata: VnMetadata,
    val score: Double,
    val evidence: MatchEvidence
)
