package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveUiMetricsTest {
    private fun resolve(
        width: Float,
        height: Float,
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0f,
        bottom: Float = 0f,
    ): AdaptiveUiMetrics = resolveAdaptiveUiMetrics(width, height, left, top, right, bottom)

    private fun safeWidth(
        m: AdaptiveUiMetrics,
        windowWidth: Float
    ): Float = windowWidth - m.safeFrame.leftDp - m.safeFrame.rightDp

    private fun safeHeight(
        m: AdaptiveUiMetrics,
        windowHeight: Float
    ): Float = windowHeight - m.safeFrame.topDp - m.safeFrame.bottomDp

    @Test
    fun pixel8PortraitKeepsUnitScaleAndPortraitSemantics() {
        val m = resolve(411f, 914f, top = 24f, bottom = 9f)
        assertEquals(HomeOrientation.PORTRAIT, m.orientation)
        assertEquals(1f, m.scale, .0001f)
        assertEquals(18f, m.pageGutterDp, .001f)
        assertEquals(56f, m.headerHeightDp, .001f)
        assertEquals(12f, m.gridSpacingDp, .001f)
    }

    @Test
    fun pixel8PortraitLibraryStaysTwoColumns() {
        val m = resolve(411f, 914f, top = 24f, bottom = 9f)
        assertEquals(2, m.libraryColumns)
        assertEquals(181.5f, m.libraryCardWidthDp, .001f)
        assertEquals(375f, m.settingsContentWidthDp, .001f)
    }

    @Test
    fun portraitDockUsesFixedSixtyFourDpHeightAndSixteenDpGap() {
        val m = resolve(411f, 914f, top = 24f, bottom = 9f)
        assertEquals(292.3443f, m.dockRect.w, .01f)
        assertEquals(64f, m.dockRect.h, .001f)
        assertEquals(889f, m.dockRect.y + m.dockRect.h, .001f)
        assertTrue(m.dockRect.y + m.dockRect.h <= 914f - 9f)
        assertTrue(m.dockRect.x >= 0f)
    }

    @Test
    fun pixel8LandscapeWithCutoutUsesFourLargeColumns() {
        val m = resolve(914f, 411f, left = 33f, bottom = 9f)
        assertEquals(HomeOrientation.LANDSCAPE, m.orientation)
        assertEquals(402f, safeHeight(m, 411f), .001f)
        assertTrue(safeHeight(m, 411f) < 460f)
        assertEquals(4, m.libraryColumns)
        assertEquals(194.394f, m.libraryCardWidthDp, .01f)
    }

    @Test
    fun landscapeDockWidthInterpolatesFromSixteenNineToTwentyNine() {
        val sixteen = resolve(1600f, 900f)
        val eighteen = resolve(1800f, 900f)
        val twenty = resolve(2000f, 900f)
        assertEquals(1600f * .4408f, sixteen.dockRect.w, .05f)
        assertEquals(1800f * .4504f, eighteen.dockRect.w, .05f)
        assertEquals(2000f * .46f, twenty.dockRect.w, .05f)
    }

    @Test
    fun landscapeDockHeightIsAtLeastFortyEightDp() {
        assertEquals(48f, resolve(914f, 411f, left = 33f, bottom = 9f).dockRect.h, .001f)
        assertEquals(85.04f, resolve(1280f, 800f).dockRect.h, .01f)
    }

    @Test
    fun portraitDockHeightIsFixedAcrossShortAndTallWindows() {
        // Alpha 10: every phone ratio renders the same 64dp portrait dock.
        assertEquals(64f, resolve(360f, 500f).dockRect.h, .001f)
        assertEquals(64f, resolve(360f, 640f).dockRect.h, .001f)
        assertEquals(64f, resolve(411f, 915f).dockRect.h, .001f)
        assertEquals(64f, resolve(414f, 896f).dockRect.h, .001f)
        assertEquals(64f, resolve(411f, 1200f).dockRect.h, .001f)
    }

    @Test
    fun landscapeRotationKeepsTheSameOpticalSafeFrameAndDock() {
        val cameraOnLeft = resolve(914f, 411f, left = 33f, bottom = 9f)
        val cameraOnRight = resolve(914f, 411f, right = 33f, bottom = 9f)
        assertEquals(cameraOnLeft.contentFrame, cameraOnRight.contentFrame)
        assertEquals(cameraOnLeft.dockRect, cameraOnRight.dockRect)
        assertEquals(cameraOnLeft.libraryColumns, cameraOnRight.libraryColumns)
        assertEquals(cameraOnLeft.libraryCardWidthDp, cameraOnRight.libraryCardWidthDp, 0f)
        val center = cameraOnLeft.contentFrame.x + cameraOnLeft.contentFrame.w / 2f
        assertEquals(457f, center, .001f)
    }

    @Test
    fun contentFrameIsAnAbsoluteRectNotAnInsetFrame() {
        val m = resolve(914f, 411f, left = 33f, bottom = 9f)
        val f = m.contentFrame
        assertEquals(50.6058f, f.x, .01f)
        assertEquals(0f, f.y, .001f)
        assertEquals(812.7883f, f.w, .01f)
        assertEquals(402f, f.h, .001f)
        // Right edge is x + w (the optical right edge), never a right inset value.
        assertEquals(863.3942f, f.x + f.w, .01f)
        // Bottom edge is y + h, which equals windowHeight - safeBottom, not windowHeight.
        assertEquals(402f, f.y + f.h, .001f)
        assertTrue(f.x + f.w <= 914f)
        assertTrue(f.y + f.h <= 411f)
    }

    @Test
    fun portraitContentFrameIsAlsoAnAbsoluteRect() {
        val m = resolve(411f, 914f, top = 24f, bottom = 9f)
        val f = m.contentFrame
        assertEquals(18f, f.x, .001f)
        assertEquals(24f, f.y, .001f)
        assertEquals(375f, f.w, .001f)
        assertEquals(881f, f.h, .001f)
        assertEquals(393f, f.x + f.w, .001f)
        assertEquals(905f, f.y + f.h, .001f)
    }

    @Test
    fun landscapeFiveColumnsWhenTallEnoughAndCardsWideEnough() {
        val m = resolve(1080f, 500f, top = 20f, bottom = 20f)
        assertEquals(460f, safeHeight(m, 500f), .001f)
        assertEquals(5, m.libraryColumns)
        assertTrue(m.libraryCardWidthDp >= 136f)
        assertEquals(197.197f, m.libraryCardWidthDp, .01f)
    }

    @Test
    fun landscapeFallsBackToFourColumnsWhenFiveTooNarrow() {
        val m = resolve(720f, 500f, left = 10f, right = 10f, top = 10f, bottom = 10f)
        assertEquals(480f, safeHeight(m, 500f), .001f)
        assertEquals(4, m.libraryColumns)
        assertEquals(153.978f, m.libraryCardWidthDp, .01f)
    }

    @Test
    fun tabletLandscapeKeepsFiveColumnsAboveTabletCardMinimum() {
        val m = resolve(1280f, 800f)
        assertEquals(5, m.libraryColumns)
        assertTrue(m.libraryCardWidthDp >= 168f * m.scale)
    }

    @Test
    fun tabletPortraitAndSquareWindowsUseTwoColumns() {
        assertEquals(2, resolve(800f, 1280f).libraryColumns)
        assertEquals(HomeOrientation.PORTRAIT, resolve(800f, 800f).orientation)
        assertEquals(2, resolve(800f, 800f).libraryColumns)
    }

    @Test
    fun scaleClampsForVerySmallAndVeryLargeWindows() {
        assertEquals(.9f, resolve(320f, 640f).scale, .0001f)
        assertEquals(1.2f, resolve(900f, 1600f).scale, .0001f)
    }

    @Test
    fun settingsShowFourCompleteRowsOnPixel8Landscape() {
        val m = resolve(914f, 411f, left = 33f, bottom = 9f)
        val available = safeHeight(m, 411f) - m.headerHeightDp - m.bottomContentPaddingDp
        val rowHeight = 56f * m.scale
        assertTrue(available >= 4f * rowHeight)
    }

    @Test
    fun settingsContentWidthCapsAtScaledMaximum() {
        assertEquals(704.2336f, resolve(914f, 411f, left = 33f, bottom = 9f).settingsContentWidthDp, .01f)
    }

    @Test
    fun bottomContentPaddingLetsTheFinalRowClearTheDock() {
        val m = resolve(914f, 411f, left = 33f, bottom = 9f)
        // The clearance is measured from the actual Dock top, then adds exactly 8dp.
        assertEquals(411f - m.dockRect.y + 8f, m.bottomContentPaddingDp, .01f)
        assertEquals(8f, m.bottomClearanceDp, .001f)
        assertTrue(m.bottomContentPaddingDp >= 9f + m.dockRect.h)
    }

    @Test
    fun invalidWindowsAndInsetsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveAdaptiveUiMetrics(0f, 914f, 0f, 0f, 0f, 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveAdaptiveUiMetrics(411f, 914f, -1f, 0f, 0f, 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveAdaptiveUiMetrics(411f, 914f, 0f, 0f, 0f, -5f)
        }
    }

    @Test
    fun portraitBottomContentPaddingClearsFixedDockAndGap() {
        // 9 (navigation) + 16 (fixed dock gap) + 64 (fixed dock) + 8*1.0 (clearance) = 97
        assertEquals(97f, resolve(411f, 914f, top = 24f, bottom = 9f).bottomContentPaddingDp, .01f)
    }

    @Test
    fun extremeNarrowWindowsClampDockWithinSafeWidth() {
        val m = resolve(120f, 640f)
        assertTrue(m.dockRect.w <= 120f + .001f)
        assertTrue(m.dockRect.x >= 0f)
        assertTrue(m.dockRect.x + m.dockRect.w <= 120f + .001f)
        val presentation = resolveDockPresentation(m.dockRect.w, m.dockRect.h)
        assertTrue(presentation.touchHeightDp >= 48f)

        val inset = resolve(150f, 700f, left = 10f, right = 10f)
        assertTrue(inset.dockRect.w <= 130f + .001f)
        assertTrue(inset.dockRect.x >= 10f - .001f)
        assertTrue(inset.dockRect.x + inset.dockRect.w <= 140f + .001f)

        val asymmetric = resolve(200f, 800f, left = 60f, right = 20f)
        assertTrue(asymmetric.dockRect.w <= 120f + .001f)
        assertTrue(asymmetric.dockRect.x >= 60f - .001f)
        assertTrue(asymmetric.dockRect.x + asymmetric.dockRect.w <= 180f + .001f)
    }

    @Test
    fun dockRectAlwaysFitsThreeEqualWidthButtons() {
        val windows =
            listOf(
                320f to 640f,
                360f to 800f,
                411f to 914f,
                600f to 960f,
                900f to 1600f,
            )
        for ((width, height) in windows) {
            val metrics = resolve(width, height)
            val presentation = resolveDockPresentation(metrics.dockRect.w, metrics.dockRect.h)
            val total =
                presentation.buttonWidthDp * 3f +
                    presentation.horizontalPaddingDp * 2f +
                    presentation.slotGapDp * 2f
            assertTrue(
                "window=${width}x$height dock=${metrics.dockRect} total=$total",
                total <= metrics.dockRect.w + .01f,
            )
            assertTrue(presentation.touchHeightDp >= 48f)
        }
    }

    @Test
    fun landscapeDockAlsoFitsThreeEqualWidthButtons() {
        val metrics = resolve(914f, 411f, left = 33f, bottom = 9f)
        val presentation = resolveDockPresentation(metrics.dockRect.w, metrics.dockRect.h)
        val total =
            presentation.buttonWidthDp * 3f +
                presentation.horizontalPaddingDp * 2f +
                presentation.slotGapDp * 2f
        assertTrue(total <= metrics.dockRect.w + .01f)
        assertTrue(presentation.touchHeightDp >= 48f)
    }

    @Test
    fun requiredWindowTableStaysInsideSafeFrameWithAsymmetricInsets() {
        data class Case(
            val widthDp: Float,
            val heightDp: Float,
            val leftDp: Float,
            val topDp: Float,
            val rightDp: Float,
            val bottomDp: Float,
            val expectedOrientation: HomeOrientation,
        )

        val cases =
            listOf(
                Case(360f, 800f, 0f, 24f, 0f, 9f, HomeOrientation.PORTRAIT),
                Case(411f, 914f, 8f, 24f, 24f, 9f, HomeOrientation.PORTRAIT),
                Case(640f, 360f, 28f, 0f, 12f, 9f, HomeOrientation.LANDSCAPE),
                Case(914f, 411f, 33f, 0f, 9f, 9f, HomeOrientation.LANDSCAPE),
                Case(600f, 1024f, 0f, 24f, 0f, 12f, HomeOrientation.PORTRAIT),
                Case(800f, 1280f, 20f, 24f, 8f, 12f, HomeOrientation.PORTRAIT),
                Case(1280f, 800f, 40f, 0f, 16f, 12f, HomeOrientation.LANDSCAPE),
            )

        cases.forEach { case ->
            val metrics =
                resolveAdaptiveUiMetrics(
                    windowWidthDp = case.widthDp,
                    windowHeightDp = case.heightDp,
                    safeLeftDp = case.leftDp,
                    safeTopDp = case.topDp,
                    safeRightDp = case.rightDp,
                    safeBottomDp = case.bottomDp,
                )
            val safeHeight = case.heightDp - case.topDp - case.bottomDp
            val sideGuard = maxOf(case.leftDp, case.rightDp)
            val opticalSafeWidth = case.widthDp - sideGuard * 2f

            assertEquals("${case.widthDp}x${case.heightDp}", case.expectedOrientation, metrics.orientation)
            assertTrue(metrics.contentFrame.x >= sideGuard)
            assertTrue(metrics.contentFrame.x + metrics.contentFrame.w <= case.widthDp - sideGuard + .01f)
            assertTrue(metrics.contentFrame.y >= case.topDp)
            assertTrue(metrics.contentFrame.y + metrics.contentFrame.h <= case.heightDp - case.bottomDp + .01f)
            assertTrue(metrics.dockRect.x >= case.leftDp - .01f)
            assertTrue(metrics.dockRect.x + metrics.dockRect.w <= case.widthDp - case.rightDp + .01f)
            assertEquals(
                case.heightDp - metrics.dockRect.y + 8f,
                metrics.bottomContentPaddingDp,
                .01f,
            )
            assertEquals(opticalSafeWidth, metrics.safeFrameRect.w, .01f)
            assertEquals(safeHeight, metrics.safeFrameRect.h, .01f)
        }
    }

    @Test
    fun mirroredAsymmetricInsetsProduceIdenticalOpticalGeometry() {
        val leftCutout = resolve(914f, 411f, left = 33f, right = 9f, bottom = 9f)
        val rightCutout = resolve(914f, 411f, left = 9f, right = 33f, bottom = 9f)

        assertEquals(leftCutout.contentFrame, rightCutout.contentFrame)
        assertEquals(leftCutout.dockRect, rightCutout.dockRect)
        assertEquals(leftCutout.safeFrameRect, rightCutout.safeFrameRect)
    }
}
