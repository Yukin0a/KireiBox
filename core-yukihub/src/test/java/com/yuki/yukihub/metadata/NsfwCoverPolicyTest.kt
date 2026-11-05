package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.policy.NsfwCoverPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NsfwCoverPolicyTest {
    private fun metadata(origin: String = "vndb", rating: String = "suggestive") =
        com.yuki.yukihub.metadata.model.VnMetadata(coverUrl = "selected", coverSexual = 2.0,
            externalRefs = "bangumi:17,vndb:v45247",
            covers = mutableListOf(com.yuki.yukihub.metadata.model.MetadataCover("selected", source = origin, sexual = rating)))

    @Test fun `NextMoe requires selected VNDB image and work anchor`() {
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, metadata()))
        for (origin in listOf("ai_redraw", "bangumi", ""))
            assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, metadata(origin)))
        for (rating in listOf("safe", "unknown", "NaN", "3"))
            assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, metadata(rating = rating)))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, metadata().apply { externalRefs = "bangumi:17" }))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, metadata().apply { externalRefs = "vndb:17" }))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, metadata().apply { coverUrl = "another-image" }))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, metadata().apply { covers.clear() }))
    }

    @Test fun `selected image takes precedence over stale work score while legacy VNDB survives`() {
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, metadata(rating = "safe")))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, metadata(rating = "unknown")))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, metadata().apply { coverUrl = "another-image" }))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, metadata().apply { covers.clear(); coverSexual = 1.13 }))
    }

    @Test
    fun `sexual image ratings include suggestive and censored nudity`() {
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 0.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 0.5))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 0.5001))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 1.0))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 1.4999))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 1.13))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 1.5))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 2.0))
    }

    @Test
    fun `hikarinagi is supported but other providers are not`() {
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.HIKARINAGI, 2.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.BANGUMI, 2.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.BANGUMI_MIRROR, 2.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.YMGAL, 2.0))
    }

    @Test
    fun `NextMoe scalar alone is not proof of a VNDB image rating`() {
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, 1.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, 2.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.NEXTMOE, 3.0))
    }

    @Test
    fun `non finite scores never mark a cover`() {
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, Double.NaN))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, Double.POSITIVE_INFINITY))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.HIKARINAGI, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `handled detection cannot overwrite a later manual disable`() {
        assertTrue(
            NsfwCoverPolicy.shouldAutoMark(
                CoverSourceMode.VNDB,
                2.0,
                alreadyNsfw = false,
                detectionHandled = false,
            ),
        )
        assertFalse(
            NsfwCoverPolicy.shouldAutoMark(
                CoverSourceMode.VNDB,
                2.0,
                alreadyNsfw = false,
                detectionHandled = true,
            ),
        )
        assertFalse(
            NsfwCoverPolicy.shouldAutoMark(
                CoverSourceMode.VNDB,
                2.0,
                alreadyNsfw = true,
                detectionHandled = false,
            ),
        )
    }
}
