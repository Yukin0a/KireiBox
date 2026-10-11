package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCandidateEvidenceTest {
    private val dynamicOrder =
        listOf(
            CoverSourceMode.NEXTMOE,
            CoverSourceMode.BANGUMI,
            CoverSourceMode.HIKARINAGI,
            CoverSourceMode.VNDB,
        )

    private fun candidate(
        id: String,
        source: CoverSourceMode = CoverSourceMode.BANGUMI,
        sources: List<CoverSourceMode> = emptyList(),
        bestRank: Int = 0,
        chinese: String = "",
        aliases: List<String> = emptyList(),
        linkedById: Boolean = false,
    ) = CoverCandidateUi(
        metadata = VnMetadata(id = id, chineseTitle = chinese).apply { titleAliases.addAll(aliases) },
        source = source,
        score = 50.0,
        sources = sources,
        bestRank = bestRank,
        linkedById = linkedById,
    )

    @Test
    fun exactLexicalAlwaysRanksFirst() {
        val aliasOnly = candidate("v-cross", sources = listOf(CoverSourceMode.BANGUMI, CoverSourceMode.VNDB), bestRank = 1)
        val exact = candidate("v-exact", chinese = "千恋万花", bestRank = 1)

        val ranked = CoverCandidateEvidence.decorate("千恋万花", listOf(aliasOnly, exact), dynamicOrder)

        assertEquals("v-exact", ranked.first().metadata.id)
        assertEquals(100.0, ranked.first().score, 0.001)
    }

    @Test
    fun crossSourceAgreementIsBadged() {
        val cross = candidate("v-cross", sources = listOf(CoverSourceMode.BANGUMI, CoverSourceMode.VNDB), bestRank = 1)

        val card = CoverCandidateEvidence.decorate("灵感满溢的甜蜜创想", listOf(cross), dynamicOrder).single()

        assertTrue(card.crossSource)
        assertTrue(card.matchBadges.contains("跨源一致"))
    }

    @Test
    fun soleResultIsAHintBadgeOnly() {
        val sole = candidate("v-sole", bestRank = 1)

        val card = CoverCandidateEvidence.decorate("远海咏叹调", listOf(sole), dynamicOrder).single()

        assertTrue(card.isUniqueResult)
        assertTrue(card.matchBadges.contains("唯一结果"))
        assertEquals(85.0, card.score, 0.001)
    }

    @Test
    fun aliasOnlyHitIsBadged() {
        val alias =
            candidate(
                "v-alias",
                chinese = "Primary Title",
                aliases = listOf("灵感满溢的甜蜜创想"),
                bestRank = 1,
            )

        val card = CoverCandidateEvidence.decorate("灵感满溢的甜蜜创想", listOf(alias), dynamicOrder).single()

        assertTrue(card.aliasHit)
        assertTrue(card.matchBadges.contains("别名命中"))
    }

    @Test
    fun idLinkedCardIsBadgedAsAliasHit() {
        val linked =
            candidate(
                "v-linked",
                sources = listOf(CoverSourceMode.NEXTMOE, CoverSourceMode.VNDB),
                bestRank = 1,
                linkedById = true,
            )

        val card = CoverCandidateEvidence.decorate("whatever", listOf(linked), dynamicOrder).single()

        assertTrue(card.matchBadges.contains("别名命中"))
        assertTrue(card.matchBadges.contains("跨源一致"))
    }
}
