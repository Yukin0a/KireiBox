package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.match.CoverSourcePriority
import com.yuki.yukihub.metadata.model.CoverSourceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSourcePriorityTest {
    @Test
    fun anonymousOnlySourcesAreBangumiThenVndb() {
        assertEquals(
            listOf(CoverSourceMode.BANGUMI, CoverSourceMode.VNDB),
            CoverSourcePriority.searchOrder { false },
        )
    }

    @Test
    fun nextmoeLeadsWhenLoggedInAndVndbStaysLast() {
        val order = CoverSourcePriority.searchOrder { it == CoverSourceMode.NEXTMOE }
        assertEquals(CoverSourceMode.NEXTMOE, order.first())
        assertEquals(CoverSourceMode.VNDB, order.last())
        // VNDB is still on the chain even when everything else is usable.
        assertEquals(
            listOf(
                CoverSourceMode.NEXTMOE,
                CoverSourceMode.BANGUMI,
                CoverSourceMode.HIKARINAGI,
                CoverSourceMode.VNDB,
            ),
            CoverSourcePriority.searchOrder { true },
        )
    }

    @Test
    fun vndbIsNeverDropped() {
        val connectedOptions =
            listOf<((CoverSourceMode) -> Boolean)>(
                { false },
                { it == CoverSourceMode.NEXTMOE },
                { it == CoverSourceMode.HIKARINAGI },
            )
        connectedOptions.forEach { connected ->
            assert(CoverSourceMode.VNDB in CoverSourcePriority.searchOrder(connected))
        }
    }

    @Test
    fun rankIsStableAndUnknownSourcesSink() {
        val connected = { source: CoverSourceMode -> source == CoverSourceMode.NEXTMOE }
        assertEquals(0, CoverSourcePriority.rank(CoverSourceMode.NEXTMOE, connected))
        assertEquals(Int.MAX_VALUE, CoverSourcePriority.rank(CoverSourceMode.YMGAL, connected))
    }
}
