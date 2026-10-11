package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class NextmoeBridgeRefsTest {
    private fun candidate(
        source: CoverSourceMode,
        id: String,
        refs: String = ""
    ): Pair<CoverSourceMode, RankedCoverCandidate> = source to RankedCoverCandidate(VnMetadata(id = id, externalRefs = refs), 50.0)

    @Test
    fun extractsVndbAndBangumiIdsOnly() {
        val refs =
            YukiMetadataService.bridgeRefs(
                listOf(
                    candidate(CoverSourceMode.VNDB, "v27448"),
                    candidate(CoverSourceMode.BANGUMI, "172612"),
                    candidate(CoverSourceMode.NEXTMOE, "12"),
                    candidate(CoverSourceMode.HIKARINAGI, "99"),
                )
            )
        assertEquals(listOf("vndb:v27448", "bangumi:172612"), refs)
    }

    @Test
    fun includesDeclaredExternalRefsAndDeduplicates() {
        val refs =
            YukiMetadataService.bridgeRefs(
                listOf(
                    candidate(CoverSourceMode.VNDB, "v27448", "vndb:v27448,bangumi:297264,dlsite:RJ1"),
                    candidate(CoverSourceMode.BANGUMI, "297264"),
                )
            )
        assertEquals(listOf("vndb:v27448", "bangumi:297264"), refs)
    }

    @Test
    fun dropsBlankIds() {
        val refs =
            YukiMetadataService.bridgeRefs(
                listOf(
                    candidate(CoverSourceMode.VNDB, "   "),
                    candidate(CoverSourceMode.BANGUMI, "172612"),
                )
            )
        assertEquals(listOf("bangumi:172612"), refs)
    }

    @Test
    fun capsAtTheBatchLimit() {
        val many = (1..150).map { candidate(CoverSourceMode.BANGUMI, it.toString()) }
        assertEquals(100, YukiMetadataService.bridgeRefs(many).size)
    }
}
