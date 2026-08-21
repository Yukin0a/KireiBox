package com.kireibox.ui

import android.content.res.Configuration
import com.yuki.yukihub.settings.DashboardProfile
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

    @Test fun pixel8LandscapeResolvesWide() {
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(914f, 411f),
        )
    }

    @Test fun narrowLandscapePhoneStaysCompactProfile() {
        assertEquals(
            KireiLayoutMode.PHONE_PORTRAIT,
            ResponsiveLayoutPolicy.resolveLayoutMode(700f, 400f),
        )
    }

    @Test fun sixHundredDpHeightIsNotCompactLandscape() {
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(1050f, 600f),
        )
    }

    @Test fun tabletAndTallWideWindowsStayWide() {
        assertEquals(KireiLayoutMode.WIDE, ResponsiveLayoutPolicy.resolveLayoutMode(1024f, 768f))
        assertEquals(KireiLayoutMode.WIDE, ResponsiveLayoutPolicy.resolveLayoutMode(1280f, 800f))
        assertEquals(KireiLayoutMode.WIDE, ResponsiveLayoutPolicy.resolveLayoutMode(1280f, 720f))
    }

    @Test fun portraitPhoneUsesPortraitMode() {
        assertEquals(
            KireiLayoutMode.PHONE_PORTRAIT,
            ResponsiveLayoutPolicy.resolveLayoutMode(411f, 914f),
        )
    }

    @Test fun wideDashboardUsesCompactHeightDensityForShortWindows() {
        val presentation = ResponsiveLayoutPolicy.dashboardPresentation(
            KireiLayoutMode.WIDE,
            availableHeightDp = 411f,
        )

        assertEquals(DashboardProfile.WIDE, presentation.profile)
        assertEquals(DashboardDensity.COMPACT_HEIGHT, presentation.density)
    }

    @Test fun pixel8LandscapeUsesTheSameOpticalSafeFrameInBothRotations() {
        val cameraOnLeft = ResponsiveLayoutPolicy.compactLibraryGridMetrics(
            widthDp = 914f,
            horizontalPaddingDp = 18f,
            safeDrawingLeftDp = 50.3f,
            safeDrawingRightDp = 0f,
        )
        val cameraOnRight = ResponsiveLayoutPolicy.compactLibraryGridMetrics(
            widthDp = 914f,
            horizontalPaddingDp = 18f,
            safeDrawingLeftDp = 0f,
            safeDrawingRightDp = 50.3f,
        )

        assertEquals(cameraOnLeft, cameraOnRight)
        assertEquals(68.3f, cameraOnLeft.contentLeftPaddingDp, .001f)
        assertEquals(68.3f, cameraOnLeft.contentRightPaddingDp, .001f)
        assertEquals(777.4f, cameraOnLeft.contentWidthDp, .001f)
        assertEquals(5, cameraOnLeft.columnCount)
    }

    @Test fun compactGridKeepsBasePaddingSymmetricWithoutSafeInsets() {
        val metrics = ResponsiveLayoutPolicy.compactLibraryGridMetrics(
            widthDp = 914f,
            horizontalPaddingDp = 18f,
            safeDrawingLeftDp = 0f,
            safeDrawingRightDp = 0f,
        )

        assertEquals(18f, metrics.contentLeftPaddingDp, .001f)
        assertEquals(18f, metrics.contentRightPaddingDp, .001f)
        assertEquals(878f, metrics.contentWidthDp, .001f)
    }

    @Test fun compactGridMirrorsTheLargerOfUnequalSafeInsets() {
        val metrics = ResponsiveLayoutPolicy.compactLibraryGridMetrics(
            widthDp = 914f,
            horizontalPaddingDp = 18f,
            safeDrawingLeftDp = 46f,
            safeDrawingRightDp = 24f,
        )

        assertEquals(64f, metrics.contentLeftPaddingDp, .001f)
        assertEquals(64f, metrics.contentRightPaddingDp, .001f)
        assertEquals(786f, metrics.contentWidthDp, .001f)
        assertEquals(5, metrics.columnCount)
    }

    @Test
    fun pixel8LandscapeMatrixResolvesWide() {
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(
                widthDp = 914f,
                heightDp = 411f,
                smallestScreenWidthDp = 411,
                screenLayoutSize = Configuration.SCREENLAYOUT_SIZE_NORMAL,
            ),
        )
    }

    @Test
    fun pixel8PortraitMatrixUsesPhonePortrait() {
        assertEquals(
            KireiLayoutMode.PHONE_PORTRAIT,
            ResponsiveLayoutPolicy.resolveLayoutMode(
                widthDp = 411f,
                heightDp = 914f,
                smallestScreenWidthDp = 411,
                screenLayoutSize = Configuration.SCREENLAYOUT_SIZE_NORMAL,
            ),
        )
    }

    @Test
    fun pixelTabletLandscapeIsAlwaysWide() {
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(
                widthDp = 1280f,
                heightDp = 800f,
                smallestScreenWidthDp = 800,
                screenLayoutSize = Configuration.SCREENLAYOUT_SIZE_NORMAL,
            ),
        )
    }

    @Test
    fun padSplitAndShortWindowsNeverEnterPhoneCompactHero() {
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(700f, 400f, 700, Configuration.SCREENLAYOUT_SIZE_NORMAL),
        )
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(840f, 700f, 840, Configuration.SCREENLAYOUT_SIZE_NORMAL),
        )
    }

    @Test
    fun foldedPhoneWindowNeverUsesRetiredCompactProductMode() {
        val mode = ResponsiveLayoutPolicy.resolveLayoutMode(660f, 340f, 340, Configuration.SCREENLAYOUT_SIZE_NORMAL)
        assertEquals(KireiLayoutMode.PHONE_PORTRAIT, mode)
    }

    @Test
    fun ultraWideExternalWindowNeverUsesPhoneHero() {
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(1000f, 500f, 500, Configuration.SCREENLAYOUT_SIZE_NORMAL),
        )
    }

    @Test
    fun largeScreenLayoutClassIsPadEvenWithSmallWindow() {
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(700f, 400f, 400, Configuration.SCREENLAYOUT_SIZE_LARGE),
        )
        assertEquals(
            KireiLayoutMode.WIDE,
            ResponsiveLayoutPolicy.resolveLayoutMode(700f, 400f, 400, Configuration.SCREENLAYOUT_SIZE_XLARGE),
        )
    }

    @Test
    fun padShortWindowUsesWideCompactDensityOnly() {
        val short = ResponsiveLayoutPolicy.dashboardPresentation(KireiLayoutMode.WIDE, availableHeightDp = 400f)
        assertEquals(DashboardProfile.WIDE, short.profile)
        assertEquals(DashboardDensity.COMPACT_HEIGHT, short.density)

        val tall = ResponsiveLayoutPolicy.dashboardPresentation(KireiLayoutMode.WIDE, availableHeightDp = 700f)
        assertEquals(DashboardProfile.WIDE, tall.profile)
        assertEquals(DashboardDensity.STANDARD, tall.density)
    }

    @Test
    fun retiredCompactProductModeIsNeverProduced() {
        val windows = listOf(
            Triple(914f, 411f, 411),
            Triple(700f, 400f, 400),
            Triple(660f, 340f, 340),
            Triple(1000f, 500f, 500),
            Triple(411f, 914f, 411),
            Triple(1050f, 600f, 600),
        )
        windows.forEach { (width, height, smallest) ->
            val mode = ResponsiveLayoutPolicy.resolveLayoutMode(width, height, smallest, Configuration.SCREENLAYOUT_SIZE_NORMAL)
            assertTrue("$width x $height", mode == KireiLayoutMode.PHONE_PORTRAIT || mode == KireiLayoutMode.WIDE)
        }
    }

    @Test
    fun dashboardProfileContractSurvivesAlpha8ResponsiveWork() {
        assertEquals(
            DashboardPresentation(DashboardProfile.COMPACT, DashboardDensity.STANDARD),
            ResponsiveLayoutPolicy.dashboardPresentation(KireiLayoutMode.PHONE_PORTRAIT),
        )
        assertEquals(
            DashboardPresentation(DashboardProfile.WIDE, DashboardDensity.STANDARD),
            ResponsiveLayoutPolicy.dashboardPresentation(KireiLayoutMode.WIDE),
        )
    }

    @Test
    fun twoArgumentResolveOverloadStillExists() {
        assertEquals(KireiLayoutMode.WIDE, ResponsiveLayoutPolicy.resolveLayoutMode(914f, 411f))
        assertEquals(KireiLayoutMode.PHONE_PORTRAIT, ResponsiveLayoutPolicy.resolveLayoutMode(411f, 914f))
    }

    @Test
    fun compactGridApiContractIsStableForAlpha8() {
        assertEquals(68.3f, ResponsiveLayoutPolicy.compactHorizontalContentPaddingDp(18f, 50.3f, 0f), .001f)
        assertEquals(68.3f, ResponsiveLayoutPolicy.compactHorizontalContentPaddingDp(18f, 0f, 50.3f), .001f)
        assertEquals(ResponsiveLayoutPolicy.LibraryGrid.PHONE_TWO_COLUMNS, ResponsiveLayoutPolicy.libraryGrid(411f))
        assertEquals(ResponsiveLayoutPolicy.LibraryGrid.MEDIUM_ADAPTIVE, ResponsiveLayoutPolicy.libraryGrid(700f))
        assertEquals(ResponsiveLayoutPolicy.LibraryGrid.WIDE_ADAPTIVE, ResponsiveLayoutPolicy.libraryGrid(840f))
    }

    @Test
    fun requiredWindowTableClassifiesPhoneAndPadFromSafeFrameAndSizeClass() {
        data class Case(
            val widthDp: Float,
            val heightDp: Float,
            val sizeClass: HomeSizeClass,
            val expected: ResponsiveWindowClass,
        )

        val cases = listOf(
            Case(360f, 800f, HomeSizeClass.COMPACT, ResponsiveWindowClass.PHONE_PORTRAIT),
            Case(411f, 914f, HomeSizeClass.COMPACT, ResponsiveWindowClass.PHONE_PORTRAIT),
            Case(640f, 360f, HomeSizeClass.COMPACT, ResponsiveWindowClass.PHONE_LANDSCAPE),
            Case(914f, 411f, HomeSizeClass.COMPACT, ResponsiveWindowClass.PHONE_LANDSCAPE),
            Case(600f, 1024f, HomeSizeClass.WIDE, ResponsiveWindowClass.PAD_PORTRAIT),
            Case(800f, 1280f, HomeSizeClass.WIDE, ResponsiveWindowClass.PAD_PORTRAIT),
            Case(1280f, 800f, HomeSizeClass.WIDE, ResponsiveWindowClass.PAD_LANDSCAPE),
        )

        cases.forEach { case ->
            assertEquals(
                "${case.widthDp}x${case.heightDp}",
                case.expected,
                ResponsiveLayoutPolicy.resolveWindowClass(
                    ResponsiveSafeFrame(case.widthDp, case.heightDp),
                    case.sizeClass,
                ),
            )
        }
    }

    @Test
    fun sizeClassChangesDeviceBucketWithoutChangingSafeFrameOrientation() {
        assertEquals(
            ResponsiveWindowClass.PHONE_LANDSCAPE,
            ResponsiveLayoutPolicy.resolveWindowClass(640f, 360f, HomeSizeClass.COMPACT),
        )
        assertEquals(
            ResponsiveWindowClass.PAD_LANDSCAPE,
            ResponsiveLayoutPolicy.resolveWindowClass(640f, 360f, HomeSizeClass.WIDE),
        )
    }

    @Test
    fun insetsCanChangeClassificationWhenTheyChangeTheSafeFrameAspect() {
        val safe = ResponsiveSafeFrame(
            windowWidthDp = 914f,
            windowHeightDp = 411f,
            leftDp = 300f,
            rightDp = 300f,
        )

        assertEquals(
            ResponsiveWindowClass.PHONE_PORTRAIT,
            ResponsiveLayoutPolicy.resolveWindowClass(safe, HomeSizeClass.COMPACT),
        )
    }

}
