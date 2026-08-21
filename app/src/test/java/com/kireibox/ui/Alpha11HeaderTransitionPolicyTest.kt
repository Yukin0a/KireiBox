package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class Alpha11HeaderTransitionPolicyTest {
    @Test
    fun libraryHeaderUsesFixed68DpRangeAtDensityOne() {
        assertEquals(
            0f,
            LibraryHeaderTransitionPolicy.fractionForScroll(scrollOffsetPx = 0f, density = 1f),
            .001f,
        )
        assertEquals(
            .5f,
            LibraryHeaderTransitionPolicy.fractionForScroll(scrollOffsetPx = 34f, density = 1f),
            .001f,
        )
        assertEquals(
            1f,
            LibraryHeaderTransitionPolicy.fractionForScroll(scrollOffsetPx = 68f, density = 1f),
            .001f,
        )
        assertEquals(
            1f,
            LibraryHeaderTransitionPolicy.fractionForScroll(scrollOffsetPx = 100f, density = 1f),
            .001f,
        )
    }

    @Test
    fun libraryHeaderUsesFixed68DpRangeAtDensityTwo() {
        assertEquals(
            0f,
            LibraryHeaderTransitionPolicy.fractionForScroll(scrollOffsetPx = 0f, density = 2f),
            .001f,
        )
        assertEquals(
            .5f,
            LibraryHeaderTransitionPolicy.fractionForScroll(scrollOffsetPx = 68f, density = 2f),
            .001f,
        )
        assertEquals(
            1f,
            LibraryHeaderTransitionPolicy.fractionForScroll(scrollOffsetPx = 136f, density = 2f),
            .001f,
        )
        assertEquals(
            1f,
            LibraryHeaderTransitionPolicy.fractionForScroll(scrollOffsetPx = 200f, density = 2f),
            .001f,
        )
    }

    @Test
    fun libraryHeaderIdleSnapUsesHalfwayBoundary() {
        assertEquals(0f, LibraryHeaderTransitionPolicy.idleSnapTarget(.49f), .001f)
        assertEquals(1f, LibraryHeaderTransitionPolicy.idleSnapTarget(.5f), .001f)
    }

    @Test
    fun libraryHeaderOnlyCollapsesInLandscapeAndForcedStatesKeepItExpanded() {
        assertEquals(
            0f,
            LibraryHeaderTransitionPolicy.resolvedFraction(
                scrollOffsetPx = 68f,
                density = 1f,
                orientation = LibraryHeaderOrientation.PORTRAIT,
            ),
            .001f,
        )
        assertEquals(
            1f,
            LibraryHeaderTransitionPolicy.resolvedFraction(
                scrollOffsetPx = 68f,
                density = 1f,
                orientation = LibraryHeaderOrientation.LANDSCAPE_PHONE,
            ),
            .001f,
        )
        assertEquals(
            1f,
            LibraryHeaderTransitionPolicy.resolvedFraction(
                scrollOffsetPx = 68f,
                density = 1f,
                orientation = LibraryHeaderOrientation.LANDSCAPE_PAD,
            ),
            .001f,
        )
        listOf(
            { LibraryHeaderTransitionPolicy.resolvedFraction(68f, 1f, LibraryHeaderOrientation.LANDSCAPE_PHONE, searchActive = true) },
            { LibraryHeaderTransitionPolicy.resolvedFraction(68f, 1f, LibraryHeaderOrientation.LANDSCAPE_PHONE, editMode = true) },
            { LibraryHeaderTransitionPolicy.resolvedFraction(68f, 1f, LibraryHeaderOrientation.LANDSCAPE_PHONE, taskCenterOpen = true) },
        ).forEach { forced -> assertEquals(0f, forced(), .001f) }
    }

    @Test
    fun libraryHeaderInterpolationHelpersRemainContinuous() {
        assertEquals(16f, LibraryHeaderTransitionPolicy.interpolate(24f, 8f, .5f), .001f)
        assertEquals(12f, LibraryHeaderTransitionPolicy.paddingDp(24f, 0f, .5f), .001f)
        assertEquals(.5f, LibraryHeaderTransitionPolicy.fadeAlpha(.5f), .001f)
        assertEquals(.5f, LibraryHeaderTransitionPolicy.visibilityFraction(.5f), .001f)
    }

    @Test
    fun libraryHeaderFractionClampsAtScrollBoundaries() {
        assertEquals(0f, LibraryHeaderTransitionPolicy.fractionForScroll(-1f, 1f), .001f)
        assertEquals(0f, LibraryHeaderTransitionPolicy.fractionForScroll(0f, 1f), .001f)
        assertEquals(1f, LibraryHeaderTransitionPolicy.fractionForScroll(68f, 1f), .001f)
        assertEquals(1f, LibraryHeaderTransitionPolicy.fractionForScroll(100f, 1f), .001f)
        assertEquals(
            1f,
            LibraryHeaderTransitionPolicy.resolvedFraction(
                scrollOffsetPx = 34f,
                density = 1f,
                orientation = LibraryHeaderOrientation.LANDSCAPE_PHONE,
                idle = true,
            ),
            .001f,
        )
    }
}
