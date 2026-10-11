package com.kireibox.ui

import com.kireibox.designsystem.components.KireiTopBarDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryHeaderTransitionPolicyTest {
    @Test
    fun scrollFractionRunsFromZeroAtRestToOnceTheRangeIsScrolled() {
        assertEquals(0f, LibraryHeaderTransitionPolicy.scrollFraction(0, 0, 100f), .001f)
        assertEquals(.5f, LibraryHeaderTransitionPolicy.scrollFraction(0, 50, 100f), .001f)
        assertEquals(1f, LibraryHeaderTransitionPolicy.scrollFraction(0, 100, 100f), .001f)
        assertEquals(1f, LibraryHeaderTransitionPolicy.scrollFraction(0, 400, 100f), .001f)
    }

    @Test
    fun firstItemScrolledAwayMeansFullyCollapsed() {
        assertEquals(1f, LibraryHeaderTransitionPolicy.scrollFraction(1, 0, 100f), .001f)
        assertEquals(1f, LibraryHeaderTransitionPolicy.scrollFraction(7, 12, 100f), .001f)
    }

    @Test
    fun fractionStaysInRangeForDegenerateInput() {
        assertEquals(0f, LibraryHeaderTransitionPolicy.scrollFraction(0, -20, 100f), .001f)
        assertEquals(0f, LibraryHeaderTransitionPolicy.scrollFraction(0, 50, 0f), .001f)
        assertEquals(0f, LibraryHeaderTransitionPolicy.scrollFraction(0, 50, -1f), .001f)
    }

    @Test
    fun landscapeToolbarLaneIsTheSharedCompactBar() {
        assertEquals(KireiTopBarDefaults.CompactHeight.value, LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP, .001f)
    }

    @Test
    fun landscapeGridClearsTheBarWithTheMinimumGap() {
        val bottom = LibraryCardLayoutPolicy.resolvedHeaderBottomDp(headerTopDp = 20f, toolbarHeightDp = LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP)
        assertEquals(72f, bottom, .001f)
        assertEquals(80f, LibraryCardLayoutPolicy.gridTopPaddingDp(gridViewportTopDp = 0f, headerBottomDp = bottom), .001f)
        // A grid that starts below the bar already has the bar's room behind it.
        assertEquals(LibraryCardLayoutPolicy.MIN_HEADER_GAP_DP, LibraryCardLayoutPolicy.gridTopPaddingDp(gridViewportTopDp = 100f, headerBottomDp = bottom), .001f)
        assertEquals(LibraryCardLayoutPolicy.MIN_HEADER_GAP_DP, LibraryCardLayoutPolicy.gridTopPaddingDp(gridViewportTopDp = 0f, headerBottomDp = 0f), .001f)
    }
}
