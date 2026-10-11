package com.kireibox.ui

import com.yuki.yukihub.metadata.match.CoverSourcePriority
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.MetadataCover
import com.yuki.yukihub.metadata.model.VnMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCandidateAggregatorTest {
    private fun candidate(
        source: CoverSourceMode,
        id: String,
        refs: String = "",
        chinese: String = "",
        original: String = "",
        roman: String = "",
        developer: String = "",
        coverUrl: String = "",
        covers: List<MetadataCover> = emptyList(),
        rank: Int = 0,
    ) = CoverCandidateUi(
        metadata =
            VnMetadata(
                id = id,
                externalRefs = refs,
                chineseTitle = chinese,
                originalTitle = original,
                romanTitle = roman,
                developer = developer,
                coverUrl = coverUrl,
                covers = ArrayList(covers),
            ),
        source = source,
        score = 50.0,
        bestRank = rank,
    )

    @Test
    fun mergesCatalogsSharingAnExternalRef() {
        val vndb =
            candidate(
                CoverSourceMode.VNDB,
                "v27448",
                chinese = "千恋万花",
                developer = "ゆずソフト",
                coverUrl = "https://t.vndb.org/a.jpg",
                covers = listOf(MetadataCover("https://t.vndb.org/a.jpg", 600, 900)),
            )
        val nextmoe =
            candidate(
                CoverSourceMode.NEXTMOE,
                "12",
                refs = "vndb:v27448,bangumi:172612",
                coverUrl = "https://image.kungal.iloveren.link/a.webp",
                covers = listOf(MetadataCover("https://image.kungal.iloveren.link/a.webp", 600, 900)),
            )
        val merged = CoverCandidateAggregator.aggregate(listOf(vndb, nextmoe))
        assertEquals(1, merged.size)
        // NextMoe ranks above VNDB in CoverSourceMode.active, so it keeps the representative slot.
        assertEquals(CoverSourceMode.NEXTMOE, merged.single().source)
        assertEquals(listOf(CoverSourceMode.NEXTMOE, CoverSourceMode.VNDB), merged.single().sources)
        assertEquals(2, merged.single().metadata.covers.size)
        assertEquals(CoverSourceMode.NEXTMOE, merged.single().coverSources["https://image.kungal.iloveren.link/a.webp"])
        assertEquals(CoverSourceMode.VNDB, merged.single().coverSources["https://t.vndb.org/a.jpg"])
        assertTrue(merged.single().metadata.externalRefs.contains("vndb:v27448"))
        assertTrue(merged.single().metadata.externalRefs.contains("bangumi:172612"))
        assertTrue(merged.single().metadata.externalRefs.contains("nextmoe:12"))
    }

    @Test
    fun mergesByNormalizedTitleWhenDevelopersMatch() {
        val bangumi =
            candidate(
                CoverSourceMode.BANGUMI,
                "172612",
                chinese = "千恋＊万花",
                developer = "ゆずソフト",
                coverUrl = "https://lain.bgm.tv/a.jpg",
            )
        val vndb =
            candidate(
                CoverSourceMode.VNDB,
                "v19073",
                chinese = "千恋万花",
                developer = "ゆずソフト",
                coverUrl = "https://t.vndb.org/b.jpg",
            )
        val merged = CoverCandidateAggregator.aggregate(listOf(bangumi, vndb))
        assertEquals(1, merged.size)
        assertEquals(CoverSourceMode.BANGUMI, merged.single().source)
        assertEquals(listOf(CoverSourceMode.BANGUMI, CoverSourceMode.VNDB), merged.single().sources)
        assertEquals(2, merged.single().metadata.covers.size)
    }

    @Test
    fun keepsSameTitleWithDifferentDevelopersApart() {
        val first = candidate(CoverSourceMode.BANGUMI, "1", chinese = "千恋万花", developer = "ゆずソフト")
        val second = candidate(CoverSourceMode.VNDB, "v1", chinese = "千恋万花", developer = "Key")
        assertEquals(2, CoverCandidateAggregator.aggregate(listOf(first, second)).size)
    }

    @Test
    fun keepsUnrelatedTitlesApart() {
        val first = candidate(CoverSourceMode.BANGUMI, "1", chinese = "千恋万花")
        val second = candidate(CoverSourceMode.VNDB, "v1", chinese = "Summer Pockets")
        assertEquals(2, CoverCandidateAggregator.aggregate(listOf(first, second)).size)
    }

    @Test
    fun singleCandidateIsReturnedUntouched() {
        val only = candidate(CoverSourceMode.VNDB, "v1", chinese = "X")
        assertEquals(listOf(only), CoverCandidateAggregator.aggregate(listOf(only)))
    }

    @Test
    fun dynamicPriorityChoosesRepresentativeAndRecordsEvidence() {
        val bangumi =
            candidate(
                CoverSourceMode.BANGUMI,
                "172612",
                chinese = "千恋万花",
                developer = "ゆずソフト",
            )
        val nextmoe =
            candidate(
                CoverSourceMode.NEXTMOE,
                "12",
                refs = "bangumi:172612",
                chinese = "千恋万花",
            )

        // Default keeps the legacy `CoverSourceMode.active` representative (Bangumi ahead of NextMoe).
        val legacy = CoverCandidateAggregator.aggregate(listOf(bangumi, nextmoe))
        assertEquals(CoverSourceMode.BANGUMI, legacy.single().source)
        assertEquals(listOf(CoverSourceMode.BANGUMI, CoverSourceMode.NEXTMOE), legacy.single().sources)
        assertTrue(legacy.single().linkedById)

        // The dynamic priority puts a logged-in NextMoe first.
        val dynamic =
            CoverCandidateAggregator.aggregate(
                listOf(bangumi, nextmoe),
                CoverSourcePriority.searchOrder { true },
            )
        assertEquals(CoverSourceMode.NEXTMOE, dynamic.single().source)
        assertEquals(listOf(CoverSourceMode.NEXTMOE, CoverSourceMode.BANGUMI), dynamic.single().sources)
        assertTrue(dynamic.single().linkedById)
    }

    @Test
    fun mergedCardRecordsLowestRankAndTitleOnlyLink() {
        val bangumi =
            candidate(
                CoverSourceMode.BANGUMI,
                "172612",
                chinese = "千恋万花",
                developer = "ゆずソフト",
                rank = 2,
            )
        val vndb =
            candidate(
                CoverSourceMode.VNDB,
                "v19073",
                chinese = "千恋万花",
                developer = "ゆずソフト",
                rank = 1,
            )
        val merged = CoverCandidateAggregator.aggregate(listOf(bangumi, vndb)).single()
        assertEquals(2, merged.sources.size)
        assertEquals(1, merged.bestRank)
        // They merged by normalized title + developer, not by an id anchor.
        assertFalse(merged.linkedById)
    }
}
