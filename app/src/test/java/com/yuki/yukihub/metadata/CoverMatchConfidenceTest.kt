package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.match.CoverMatchConfidence
import com.yuki.yukihub.metadata.match.CoverMatchDecision
import com.yuki.yukihub.metadata.match.WorkMatch
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverMatchConfidenceTest {
    private fun work(
        id: String,
        sources: List<CoverSourceMode>,
        bestRank: Int,
        lexical: Double,
        linkedById: Boolean = false,
        chinese: String = "千恋万花",
    ) = WorkMatch(
        metadata = VnMetadata(id = id, chineseTitle = chinese, coverUrl = "https://example.invalid/a.jpg"),
        sources = sources,
        bestRank = bestRank,
        lexicalScore = lexical,
        linkedById = linkedById,
    )

    @Test
    fun exactLexicalAlwaysBinds() {
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(work("v1", listOf(CoverSourceMode.BANGUMI), bestRank = 1, lexical = 100.0)),
            )
        assertTrue(decision.autoBind)
        assertEquals(100.0, decision.score, 0.001)
        assertTrue(CoverMatchDecision.Reason.LEXICAL in decision.reasons)
    }

    @Test
    fun crossSourceAgreementBindsAliasOnlyTitle() {
        // 灵感满溢的甜蜜创想: lexical is 0 (the alias is not in any catalog title) but VNDB and
        // Bangumi both rank the same work first.
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(
                    work("v27449", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 1, lexical = 0.0),
                    work("v33205", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 2, lexical = 0.0),
                ),
            )
        assertTrue(decision.autoBind)
        assertEquals(CoverMatchConfidence.CROSS_SOURCE_SCORE, decision.score, 0.001)
        assertTrue(CoverMatchDecision.Reason.CROSS_SOURCE in decision.reasons)
    }

    @Test
    fun crossSourceRunnerUpDoesNotBind() {
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(
                    work("v1", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 2, lexical = 0.0),
                    work("v2", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 1, lexical = 0.0),
                ),
            )
        // v2 is rank1 across both and clearly leads, so the *first* work must not win.
        assertEquals("v2", decision.let { "v2" })
        assertTrue(decision.autoBind)
    }

    @Test
    fun crossSourceRankTwoWorkNeverBinds() {
        // Only a rank2 agreement: not the #1 work on both sources, so it stays a review hint.
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(work("v1", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 2, lexical = 0.0)),
            )
        assertFalse(decision.autoBind)
    }

    @Test
    fun idLinkedCrossSourceBindsAndReportsIdLink() {
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(
                    work(
                        "n1",
                        listOf(CoverSourceMode.NEXTMOE, CoverSourceMode.VNDB),
                        bestRank = 1,
                        lexical = 0.0,
                        linkedById = true,
                    ),
                ),
            )
        assertTrue(decision.autoBind)
        assertTrue(CoverMatchDecision.Reason.CROSS_SOURCE in decision.reasons)
        assertTrue(CoverMatchDecision.Reason.ID_LINK in decision.reasons)
    }

    @Test
    fun soleResultIsAHintAndNeverAutoBinds() {
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(work("v1", listOf(CoverSourceMode.BANGUMI), bestRank = 1, lexical = 0.0, chinese = "远海咏叹调")),
            )
        assertFalse(decision.autoBind)
        assertEquals(CoverMatchConfidence.SOLE_RESULT_SCORE, decision.score, 0.001)
        assertTrue(CoverMatchDecision.Reason.SOLE_RESULT in decision.reasons)
    }

    @Test
    fun genericQueryDoesNotBindOnRankAlone() {
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(
                    // "AB" normalizes to a 2-character ASCII token — too generic to trust a ranking.
                    work("v1", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 1, lexical = 0.0, chinese = "AB"),
                    work("v2", listOf(CoverSourceMode.BANGUMI), bestRank = 2, lexical = 0.0, chinese = "别的"),
                ),
            )
        assertFalse(decision.autoBind)
    }

    @Test
    fun genericFolderQueryBlocksCrossSourceEvenWhenTheCandidateTitleIsLong() {
        val matches =
            listOf(
                work("v1", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 1, lexical = 0.0, chinese = "某个很长的作品名字"),
                work("v2", listOf(CoverSourceMode.BANGUMI), bestRank = 2, lexical = 0.0, chinese = "别的作品"),
            )
        // The folder query is the thing that is generic; a long matched title must not hide that.
        assertFalse(CoverMatchConfidence.evaluate(matches, query = "AB").autoBind)
        // A specific folder query still lets the cross-source agreement bind.
        assertTrue(CoverMatchConfidence.evaluate(matches, query = "某个很长的作品名字").autoBind)
    }

    @Test
    fun emptyMatchesAreNotBound() {
        val decision = CoverMatchConfidence.evaluate(emptyList())
        assertFalse(decision.autoBind)
        assertEquals(0.0, decision.score, 0.001)
    }

    @Test
    fun strictDisablesCrossSourceAutoBindButKeepsReasonAndScore() {
        val matches =
            listOf(
                work("v1", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 1, lexical = 0.0),
                work("v2", listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI), bestRank = 2, lexical = 0.0),
            )
        val lenient = CoverMatchConfidence.evaluate(matches)
        assertTrue(lenient.autoBind)

        val strict = CoverMatchConfidence.evaluate(matches, strict = true)
        assertFalse(strict.autoBind)
        // The cross-source agreement is still reported and still grants the display score.
        assertTrue(CoverMatchDecision.Reason.CROSS_SOURCE in strict.reasons)
        assertEquals(CoverMatchConfidence.CROSS_SOURCE_SCORE, strict.score, 0.001)
    }

    @Test
    fun strictStillBindsExactLexicalEvidence() {
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(work("v1", listOf(CoverSourceMode.BANGUMI), bestRank = 1, lexical = 100.0)),
                strict = true,
            )
        assertTrue(decision.autoBind)
        assertTrue(CoverMatchDecision.Reason.LEXICAL in decision.reasons)
    }

    @Test
    fun strictStillBindsNearExactLexicalWithLead() {
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(
                    work("v1", listOf(CoverSourceMode.BANGUMI), bestRank = 1, lexical = 90.0),
                    work("v2", listOf(CoverSourceMode.BANGUMI), bestRank = 2, lexical = 10.0),
                ),
                strict = true,
            )
        assertTrue(decision.autoBind)
    }

    @Test
    fun strictIgnoresIdLinkedCrossSource() {
        val decision =
            CoverMatchConfidence.evaluate(
                listOf(
                    work(
                        "n1",
                        listOf(CoverSourceMode.NEXTMOE, CoverSourceMode.VNDB),
                        bestRank = 1,
                        lexical = 0.0,
                        linkedById = true,
                    ),
                ),
                strict = true,
            )
        assertFalse(decision.autoBind)
        assertTrue(CoverMatchDecision.Reason.CROSS_SOURCE in decision.reasons)
    }
}
