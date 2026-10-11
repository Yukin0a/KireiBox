package com.yuki.yukihub.metadata.match
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata

/**
 * One work after cross-source merging, with the evidence needed to decide whether it can be
 * bound automatically or only offered for review.
 */
data class WorkMatch(
    val metadata: VnMetadata,
    val sources: List<CoverSourceMode>,
    /** Best (lowest) source order across the sources that returned this work; 0 = unknown. */
    val bestRank: Int,
    /** Highest lexical title score across the work's titles and aliases. */
    val lexicalScore: Double,
    /** True when the work was linked by an id/anchor (NextMoe `refs` bridge) rather than by title. */
    val linkedById: Boolean,
)

data class CoverMatchDecision(
    val score: Double,
    val autoBind: Boolean,
    val reasons: List<Reason>,
) {
    enum class Reason { LEXICAL, CROSS_SOURCE, SOLE_RESULT, ID_LINK }
}

/**
 * Decides whether a merged work is trustworthy enough to bind without asking the user.
 *
 * Two rules may auto-bind:
 * - **L1 lexical** — the local title (or one of its aliases) matches the folder name strongly.
 * - **L3 cross-source** — at least two sources independently ranked this same work first. This is
 *   what recovers alias-only titles (e.g. 「灵感满溢的甜蜜创想」) that the lexical scorer cannot see.
 *
 * A single source returning exactly **one** result is only ever a *hint* ("唯一结果"): sparse
 * indexes and vague queries make it too weak to bind on its own, which is exactly why YukiHub's
 * "one result ⇒ trust" rule is deliberately not copied here.
 *
 * [evaluate] takes a [strict] flag (default `false`, so the legacy behaviour is unchanged). When
 * strict is on, the cross-source L3 agreement no longer auto-binds: only lexical evidence (L1
 * exact or L1 near-exact with a clear lead) may bind. A cross-source agreement is still reported
 * as a reason and still drives the display score, it just cannot bind without a lexical hit.
 */
object CoverMatchConfidence {
    /** Lexical score that auto-binds on its own. */
    const val LEXICAL_EXACT = 96.0

    /** Lower lexical score that still auto-binds when it clearly leads the runner-up. */
    const val LEXICAL_LEAD_SCORE = 88.0
    const val LEXICAL_LEAD_GAP = 5.0

    /** Display score granted to a cross-source agreement. */
    const val CROSS_SOURCE_SCORE = 96.0
    const val CROSS_SOURCE_LEAD_GAP = 3.0

    /** Display score granted to a sole result (hint only, never auto-binds). */
    const val SOLE_RESULT_SCORE = 85.0

    /** Queries this short are too generic to trust a ranking on. */
    private const val GENERIC_CJK_LENGTH = 2
    private const val GENERIC_ASCII_LENGTH = 3

    @JvmStatic
    fun evaluate(
        matches: List<WorkMatch>,
        strict: Boolean = false,
        query: String? = null,
    ): CoverMatchDecision {
        if (matches.isEmpty()) return CoverMatchDecision(0.0, false, emptyList())
        val ranked =
            matches.sortedWith(
                compareByDescending<WorkMatch> { displayScore(it) }
                    .thenBy { if (it.bestRank <= 0) Int.MAX_VALUE else it.bestRank },
            )
        val best = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.let(::displayScore) ?: 0.0
        val score = displayScore(best)
        val lead = score - runnerUp

        val reasons = LinkedHashSet<CoverMatchDecision.Reason>()
        if (best.lexicalScore >= LEXICAL_EXACT) reasons += CoverMatchDecision.Reason.LEXICAL
        if (best.sources.size >= 2 && best.bestRank == 1) {
            reasons += CoverMatchDecision.Reason.CROSS_SOURCE
            if (best.linkedById) reasons += CoverMatchDecision.Reason.ID_LINK
        }
        if (isSoleResult(best)) reasons += CoverMatchDecision.Reason.SOLE_RESULT

        val generic = isGenericQuery(query, best)
        val autoBind =
            isLexicalAutoBind(best, lead) || isCrossSourceAutoBind(best, lead, generic, strict)

        return CoverMatchDecision(score, autoBind, reasons.toList())
    }

    /** L1: an exact hit always binds; a near-exact hit must clearly lead the runner-up. */
    private fun isLexicalAutoBind(
        best: WorkMatch,
        lead: Double,
    ): Boolean =
        best.lexicalScore >= LEXICAL_EXACT ||
            (best.lexicalScore >= LEXICAL_LEAD_SCORE && lead >= LEXICAL_LEAD_GAP)

    /**
     * L3: two sources ranking the same work first is enough on its own, unless [strict] mode
     * disables the rule, the query is too generic, or the lead is not clear.
     */
    private fun isCrossSourceAutoBind(
        best: WorkMatch,
        lead: Double,
        generic: Boolean,
        strict: Boolean,
    ): Boolean = !strict && best.sources.size >= 2 && best.bestRank == 1 && lead >= CROSS_SOURCE_LEAD_GAP && !generic

    @JvmStatic
    fun displayScore(match: WorkMatch): Double =
        maxOf(
            match.lexicalScore,
            if (match.sources.size >= 2 && match.bestRank == 1) CROSS_SOURCE_SCORE else 0.0,
            if (isSoleResult(match)) SOLE_RESULT_SCORE else 0.0,
        )

    /** A sole result across all contributing sources; used as a hint, never to auto-bind. */
    private fun isSoleResult(match: WorkMatch): Boolean = match.sources.size == 1 && match.bestRank == 1

    /**
     * Whether the query is too short/generic to trust a source ranking on. It must inspect the
     * *folder* query (the thing the user actually typed), not the matched work's title: a long
     * candidate behind a 2-character folder name would otherwise slip past the guard.
     */
    private fun isGenericQuery(
        query: String?,
        match: WorkMatch,
    ): Boolean {
        val source =
            query?.takeIf(String::isNotBlank)
                ?: match.metadata.chineseTitle.ifBlank { match.metadata.romanTitle }
        val normalized = TitleMatchScorer.normalize(source)
        if (normalized.isEmpty()) return true
        val hasCjk = normalized.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val length = normalized.length
        return if (hasCjk) length <= GENERIC_CJK_LENGTH else length <= GENERIC_ASCII_LENGTH
    }
}
