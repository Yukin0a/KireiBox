package com.yuki.yukihub.data

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSearchIndexTest {
    @Test
    fun `metadata aliases are searchable independent of case and punctuation`() {
        val index =
            GameSearchIndex.build(
                Game(title = "恋爱，我就走了", originalTitle = "Koi, I Left"),
                listOf("Love Departure", "恋愛・離脱"),
            )
        assertTrue(GameSearchIndex.matches(index, "love departure"))
        assertTrue(GameSearchIndex.matches(index, "恋愛離脱"))
        assertTrue(GameSearchIndex.matches(index, "KOI LEFT"))
        assertFalse(GameSearchIndex.matches(index, "bgi"))
    }
}
