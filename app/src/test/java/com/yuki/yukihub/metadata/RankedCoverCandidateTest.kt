package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.match.TitleMatchScorer
import com.yuki.yukihub.metadata.model.VnMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedCoverCandidateTest {
    @Test fun ranksExactTitleFirstAndDropsMissingImages() {
        val exact =
            VnMetadata().apply {
                id = "v1"
                originalTitle = "ATRI -My Dear Moments-"
                coverUrl = "https://example.test/atri.jpg"
            }
        val wrong =
            VnMetadata().apply {
                id = "v2"
                originalTitle = "Clannad"
                coverUrl = "https://example.test/clannad.jpg"
            }
        val missing =
            VnMetadata().apply {
                id = "v3"
                originalTitle = "ATRI -My Dear Moments-"
                coverUrl = ""
            }

        val ranked = YukiMetadataService.rankCandidates("ATRI -My Dear Moments-", listOf(wrong, missing, exact))

        assertEquals(listOf("v1", "v2"), ranked.map { it.metadata.id })
        assertTrue(ranked.first().score >= 96.0)
        assertTrue(TitleMatchScorer.isAutomaticMatch(ranked.map { TitleMatchScorer.Match(it.metadata, it.score) }))
    }
}
