package com.yuki.yukihub.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LandscapeHomeStyleTest {
    @Test
    fun `stored values and labels are stable`() {
        assertEquals("immersive_cover", LandscapeHomeStyle.IMMERSIVE_COVER.storedValue)
        assertEquals("16:9 弧线画廊（D）", LandscapeHomeStyle.IMMERSIVE_COVER.label)
        assertEquals("focus_poster", LandscapeHomeStyle.FOCUS_POSTER.storedValue)
        assertEquals("9:16 专注海报（C）", LandscapeHomeStyle.FOCUS_POSTER.label)
    }

    @Test
    fun `stored values round trip case insensitively`() {
        LandscapeHomeStyle.entries.forEach { style ->
            assertEquals(style, LandscapeHomeStyle.fromStored(style.storedValue))
            assertEquals(style, LandscapeHomeStyle.fromStored(style.storedValue.uppercase()))
        }
    }

    @Test
    fun `blank unknown and null values use immersive cover`() {
        assertEquals(LandscapeHomeStyle.IMMERSIVE_COVER, LandscapeHomeStyle.fromStored(null))
        assertEquals(LandscapeHomeStyle.IMMERSIVE_COVER, LandscapeHomeStyle.fromStored("  "))
        assertEquals(LandscapeHomeStyle.IMMERSIVE_COVER, LandscapeHomeStyle.fromStored("other"))
    }
}
