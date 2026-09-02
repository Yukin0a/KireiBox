package com.yuki.yukihub.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NsfwCoverPolicyTest {
    @Test
    fun `vndb threshold is strict`() {
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 0.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 0.5))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 0.5001))
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.VNDB, 2.0))
    }

    @Test
    fun `hikarinagi is supported but other providers are not`() {
        assertTrue(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.HIKARINAGI, 0.5001))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.BANGUMI, 2.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.BANGUMI_MIRROR, 2.0))
        assertFalse(NsfwCoverPolicy.shouldAutoMark(CoverSourceMode.YMGAL, 2.0))
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
                0.5001,
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
