package com.kireibox.ui

import com.yuki.yukihub.metadata.CoverSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSourcePresentationPolicyTest {
    @Test
    fun nonSelectedNonVndbSourcesAreMutedButRemainEnabledAndAccessible() {
        val selected = CoverSourceMode.VNDB

        CoverSourceMode.entries.forEach { source ->
            val presentation = CoverSourcePresentationPolicy.presentation(source, selected)
            assertTrue("${source.name} stays enabled", presentation.enabled)
            assertTrue("${source.name} stays accessible", presentation.accessible)
            if (source == CoverSourceMode.VNDB) {
                assertFalse(presentation.muted)
            } else {
                assertTrue("${source.name} is visually muted", presentation.muted)
            }
        }
    }

    @Test
    fun optionsUseVndbAsTheFallbackAndHighlightOnlyTheSelectedSource() {
        val fallback = CoverSourcePresentationPolicy.options(null)
        assertEquals(CoverSourceMode.entries.toList(), fallback.map { it.source })
        assertFalse(fallback.first().muted)
        assertTrue(fallback.first().isHighlighted)

        val selected = CoverSourcePresentationPolicy.options(CoverSourceMode.BANGUMI)
        val bySource = selected.associateBy { it.source }
        assertTrue(bySource.getValue(CoverSourceMode.BANGUMI).isHighlighted)
        assertFalse(bySource.getValue(CoverSourceMode.VNDB).isHighlighted)
        assertFalse(bySource.getValue(CoverSourceMode.VNDB).muted)
        assertTrue(bySource.getValue(CoverSourceMode.YMGAL).muted)
    }

    @Test
    fun tokenVisibilityIsLimitedToTheBangumiFamily() {
        assertEquals(
            listOf(false, true, true, false, false),
            CoverSourceMode.entries.map(CoverSourcePresentationPolicy::tokenVisible),
        )
    }

    @Test
    fun switchingSourceClosesEditorWithoutClearingStoredToken() {
        val before = CoverSourceEditorState(
            selectedSource = CoverSourceMode.BANGUMI,
            tokenEditorOpen = true,
            storedToken = "keep-me",
        )

        val after = CoverSourcePresentationPolicy.switchSource(before, CoverSourceMode.YMGAL)

        assertEquals(CoverSourceMode.YMGAL, after.selectedSource)
        assertFalse(after.tokenEditorOpen)
        assertEquals("keep-me", after.storedToken)
    }
}
