package com.yuki.yukihub.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSourceModeTest {
    @Test
    fun readsYukiHubSourceKeysAndDefaultsSafely() {
        assertEquals(CoverSourceMode.VNDB, CoverSourceMode.fromStored("VNDB"))
        assertEquals(CoverSourceMode.BANGUMI, CoverSourceMode.fromStored("bangumi"))
        assertEquals(CoverSourceMode.BANGUMI_MIRROR, CoverSourceMode.fromStored("bangumi_mirror"))
        assertEquals(CoverSourceMode.YMGAL, CoverSourceMode.fromStored("ymgal"))
        assertEquals(CoverSourceMode.HIKARINAGI, CoverSourceMode.fromStored("hikarinagi"))
        assertEquals(CoverSourceMode.VNDB, CoverSourceMode.fromStored("unknown"))
    }
}
