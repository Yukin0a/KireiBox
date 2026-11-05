package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.model.CoverSourceMode
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
        assertEquals(CoverSourceMode.NEXTMOE, CoverSourceMode.fromStored("unknown"))
        assertEquals(listOf(CoverSourceMode.BANGUMI, CoverSourceMode.NEXTMOE, CoverSourceMode.HIKARINAGI, CoverSourceMode.VNDB), CoverSourceMode.active)
        assertEquals(CoverSourceMode.NEXTMOE, CoverSourceMode.selected("ymgal"))
        assertEquals(CoverSourceMode.NEXTMOE, CoverSourceMode.selected("bangumi_mirror"))
        assertEquals(CoverSourceMode.VNDB, CoverSourceMode.selected("vndb"))
        assertEquals(CoverSourceMode.HIKARINAGI, CoverSourceMode.selected("hikarinagi"))
    }
}
