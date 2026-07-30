package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutPolicyTest {
    @Test fun phoneStaysStackedAndTwoColumns() {
        assertFalse(ResponsiveLayoutPolicy.useTwoPane(411f, 914f))
        assertEquals(
            ResponsiveLayoutPolicy.LibraryGrid.PHONE_TWO_COLUMNS,
            ResponsiveLayoutPolicy.libraryGrid(411f)
        )
    }

    @Test fun ipadRatioUsesTwoPanes() {
        assertTrue(ResponsiveLayoutPolicy.useTwoPane(768f, 1024f))
        assertTrue(ResponsiveLayoutPolicy.useTwoPane(1024f, 768f))
    }

    @Test fun tallNarrowTabletDoesNotForceSplit() {
        assertFalse(ResponsiveLayoutPolicy.useTwoPane(720f, 1280f))
        assertEquals(270f, ResponsiveLayoutPolicy.heroHeightDp(640f, false), .001f)
        assertEquals(410f, ResponsiveLayoutPolicy.heroHeightDp(1200f, true), .001f)
    }

    @Test fun compactPhoneLeavesRoomForRecentCardAboveDock() {
        assertEquals(282.72f, ResponsiveLayoutPolicy.heroHeightDp(744f, false), .001f)
    }
}
