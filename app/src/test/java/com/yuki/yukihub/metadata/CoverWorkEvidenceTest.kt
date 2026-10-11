package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.match.CoverWorkEvidence
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverWorkEvidenceTest {
    private fun ranked(
        source: CoverSourceMode,
        id: String,
        refs: String = "",
        chinese: String = "",
        roman: String = "",
        rank: Int = 0,
    ): Pair<CoverSourceMode, RankedCoverCandidate> =
        source to
            RankedCoverCandidate(
                metadata =
                    VnMetadata(
                        id = id,
                        externalRefs = refs,
                        chineseTitle = chinese,
                        romanTitle = roman,
                        coverUrl = "https://example.invalid/$id.jpg",
                    ),
                score = 50.0,
                sourceRank = rank,
            )

    @Test
    fun mergesCrossSourceAgreementByAnchorAndBindsAliasOnlyTitle() {
        // NextMoe publishes the alias in `titles[]`, not in a title field the query matches, and
        // VNDB has no CJK title at all: lexical 0, but both rank the linked work #1.
        val collected =
            listOf(
                ranked(
                    CoverSourceMode.NEXTMOE,
                    "12",
                    refs = "vndb:v27449",
                    roman = "Sweet Creation Overflowing With Inspiration",
                    rank = 1,
                ),
                ranked(CoverSourceMode.VNDB, "v27449", rank = 1),
            )
        val merged = CoverWorkEvidence.merge(collected, "灵感满溢的甜蜜创想")
        assertEquals(1, merged.size)
        assertEquals(2, merged.single().sources.size)
        assertEquals(1, merged.single().bestRank)
        assertTrue(merged.single().linkedById)
        assertTrue(CoverWorkEvidence.hasAutoBind(collected, "灵感满溢的甜蜜创想"))
    }

    @Test
    fun soleResultNeverAutoBinds() {
        val collected = listOf(ranked(CoverSourceMode.BANGUMI, "172612", chinese = "另一部作品", rank = 1))
        assertFalse(CoverWorkEvidence.hasAutoBind(collected, "远海咏叹调"))
    }

    @Test
    fun crossSourceRankTwoDoesNotBind() {
        val collected =
            listOf(
                ranked(CoverSourceMode.BANGUMI, "172612", chinese = "别的作品", rank = 2),
                ranked(CoverSourceMode.VNDB, "v19073", refs = "bangumi:172612", chinese = "别的作品", rank = 2),
            )
        val decision = CoverWorkEvidence.evaluate(collected, "续作")
        assertFalse(decision.autoBind)
    }

    @Test
    fun exactLexicalBindsInASingleSource() {
        val collected = listOf(ranked(CoverSourceMode.BANGUMI, "172612", chinese = "千恋万花", rank = 1))
        assertTrue(CoverWorkEvidence.hasAutoBind(collected, "千恋万花"))
    }

    @Test
    fun emptyCandidatesProduceNoEvidence() {
        assertTrue(CoverWorkEvidence.merge(emptyList(), "任意").isEmpty())
        assertFalse(CoverWorkEvidence.hasAutoBind(emptyList(), "任意"))
    }

    @Test
    fun strictDisablesCrossSourceConsensus() {
        // Same alias-only work that binds by cross-source agreement in lenient mode.
        val collected =
            listOf(
                ranked(
                    CoverSourceMode.NEXTMOE,
                    "12",
                    refs = "vndb:v27449",
                    roman = "Sweet Creation Overflowing With Inspiration",
                    rank = 1,
                ),
                ranked(CoverSourceMode.VNDB, "v27449", rank = 1),
            )
        assertTrue(CoverWorkEvidence.hasAutoBind(collected, "灵感满溢的甜蜜创想"))
        assertFalse(CoverWorkEvidence.hasAutoBind(collected, "灵感满溢的甜蜜创想", strict = true))
    }
}
