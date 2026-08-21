package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Alpha8HeaderTransitionPolicyTest {
    @Test
    fun toolbarCollapseStaysExpandedBeforeAnchor() {
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseFraction(0, 0, 224), .001f)
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseFraction(0, 100, 224), .001f)
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseFraction(0, 223, 224), .001f)
    }

    @Test
    fun toolbarCollapseFullyCollapsesOnceAnchorReached() {
        assertEquals(1f, LibraryToolbarCollapsePolicy.collapseFraction(0, 224, 224), .001f)
        assertEquals(1f, LibraryToolbarCollapsePolicy.collapseFraction(0, 1000, 224), .001f)
        assertEquals(1f, LibraryToolbarCollapsePolicy.collapseFraction(1, 0, 224), .001f)
        assertEquals(1f, LibraryToolbarCollapsePolicy.collapseFraction(3, 40, 224), .001f)
    }

    @Test
    fun toolbarCollapseExpandsAgainWhenScrolledBackAboveAnchor() {
        assertEquals(1f, LibraryToolbarCollapsePolicy.collapseFraction(0, 224, 224), .001f)
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseFraction(0, 223, 224), .001f)
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseFraction(0, 0, 224), .001f)
    }

    @Test
    fun toolbarCollapseFractionIsZeroWhenAnchorIsUnknown() {
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseFraction(2, 100, 0), .001f)
        assertFalse(LibraryToolbarCollapsePolicy.anchored(2, 100, 0))
    }

    @Test
    fun libraryGridStartClearsMeasuredHeaderWhenToolbarIsExpanded() {
        val headerBottom = LibraryCardLayoutPolicy.resolvedHeaderBottomDp(
            headerTopDp = 20f,
            toolbarHeightDp = 56f,
            measuredTitleBottomDp = 84f,
            measuredActionsBottomDp = 92f,
        )
        val topPadding = LibraryCardLayoutPolicy.gridTopPaddingDp(
            gridViewportTopDp = 20f,
            headerBottomDp = headerBottom,
            toolbarCollapsed = false,
        )

        assertEquals(92f, headerBottom, .001f)
        assertEquals(80f, topPadding, .001f)
        assertTrue(20f + topPadding >= headerBottom + LibraryCardLayoutPolicy.MIN_HEADER_GAP_DP)
    }

    @Test
    fun libraryGridStartKeepsEightDpClearanceAfterToolbarCollapses() {
        val topPadding = LibraryCardLayoutPolicy.gridTopPaddingDp(
            gridViewportTopDp = 20f,
            headerBottomDp = 92f,
            toolbarCollapsed = true,
        )

        assertEquals(8f, topPadding, .001f)
        assertTrue(20f + topPadding >= 20f + LibraryCardLayoutPolicy.MIN_HEADER_GAP_DP)
        assertEquals(0f, LibraryCardLayoutPolicy.gridFadeStartDp(20f, 92f, true), .001f)
    }

    @Test
    fun toolbarAnchorEqualsToolbarHeightPlusFirstRowGapAndDurationsAreFixed() {
        assertEquals(
            LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP + LibraryHeaderTransitionPolicy.FIRST_ROW_GAP_DP,
            LibraryToolbarCollapsePolicy.ANCHOR_OFFSET_DP,
            .001f,
        )
        assertEquals(220, LibraryToolbarCollapsePolicy.COLLAPSE_DURATION_MS)
        assertEquals(260, LibraryToolbarCollapsePolicy.EXPAND_DURATION_MS)
    }

    @Test
    fun toolbarCollapseAlphaIsOneExpandedAndExactlyZeroCollapsed() {
        assertEquals(1f, LibraryToolbarCollapsePolicy.collapseAlpha(0f), .001f)
        assertEquals(.5f, LibraryToolbarCollapsePolicy.collapseAlpha(.5f), .001f)
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseAlpha(1f), .001f)
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseAlpha(2f), .001f)
        assertEquals(1f, LibraryToolbarCollapsePolicy.collapseAlpha(-1f), .001f)
    }

    @Test
    fun toolbarCollapseOffsetSlidesUpAtMostOneToolbarHeight() {
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseOffsetDp(0f), .001f)
        assertEquals(-28f, LibraryToolbarCollapsePolicy.collapseOffsetDp(.5f), .001f)
        assertEquals(-56f, LibraryToolbarCollapsePolicy.collapseOffsetDp(1f), .001f)
        assertEquals(-56f, LibraryToolbarCollapsePolicy.collapseOffsetDp(2f), .001f)
        assertEquals(-56f, LibraryToolbarCollapsePolicy.collapseOffsetDp(1f, 56f), .001f)
        assertEquals(-64f, LibraryToolbarCollapsePolicy.collapseOffsetDp(1f, 64f), .001f)
        assertEquals(0f, LibraryToolbarCollapsePolicy.collapseOffsetDp(-1f), .001f)
    }

    @Test
    fun headerUsesStatusBarBottomWithFiveDpNormalTopWhenNoCutout() {
        val top = HeaderTransitionPolicy.topDp(
            HeaderGeometryDp(statusBarBottomDp = 28f, safeDrawingTopDp = 31f, cutout = null),
            HeaderAnchorRegionsDp(
                leftTitle = HeaderRectDp(68f, 28f, 200f, 76f),
                rightAction = HeaderRectDp(760f, 28f, 846f, 76f),
            ),
        )
        assertEquals(33f, top, .001f)
    }

    @Test
    fun intersectingCutoutLowersOnlyTheAffectedHeaderLane() {
        val top = HeaderTransitionPolicy.topDp(
            HeaderGeometryDp(statusBarBottomDp = 28f, safeDrawingTopDp = 31f, cutout = HeaderRectDp(420f, 0f, 494f, 36f)),
            HeaderAnchorRegionsDp(
                leftTitle = HeaderRectDp(68f, 28f, 450f, 76f),
                rightAction = HeaderRectDp(520f, 28f, 846f, 76f),
            ),
        )
        assertEquals(42f, top, .001f)
    }

    @Test
    fun cutoutIntersectionIsSymmetricAcrossRotations() {
        val leftCamera = HeaderTransitionPolicy.topDp(
            HeaderGeometryDp(28f, 31f, HeaderRectDp(0f, 0f, 74f, 36f)),
            HeaderAnchorRegionsDp(HeaderRectDp(68f, 28f, 300f, 76f), HeaderRectDp(610f, 28f, 846f, 76f)),
        )
        val rightCamera = HeaderTransitionPolicy.topDp(
            HeaderGeometryDp(28f, 31f, HeaderRectDp(840f, 0f, 914f, 36f)),
            HeaderAnchorRegionsDp(HeaderRectDp(68f, 28f, 300f, 76f), HeaderRectDp(614f, 28f, 846f, 76f)),
        )
        assertEquals(leftCamera, rightCamera, .001f)
        assertEquals(42f, leftCamera, .001f)
    }

    @Test
    fun missingStatusAndCutoutGeometryFallsBackToSafeDrawingTop() {
        assertEquals(
            37f,
            HeaderTransitionPolicy.topDp(
                HeaderGeometryDp(statusBarBottomDp = null, safeDrawingTopDp = 37f, cutout = null),
                HeaderAnchorRegionsDp(HeaderRectDp(0f, 37f, 300f, 85f), HeaderRectDp(610f, 37f, 900f, 85f)),
            ),
            .001f,
        )
    }

    @Test
    fun centeredCutoutKeepsNonIntersectingSideLaneOnStatusBaseline() {
        val top = HeaderTransitionPolicy.topDp(
            HeaderGeometryDp(statusBarBottomDp = 24f, safeDrawingTopDp = 42f, cutout = HeaderRectDp(420f, 0f, 494f, 36f)),
            HeaderAnchorRegionsDp(HeaderRectDp(68f, 24f, 300f, 72f), HeaderRectDp(614f, 24f, 846f, 72f)),
        )
        assertEquals(23f, top, .001f)
    }

    @Test
    fun rotationsKeepHeaderCenterWithinOneDp() {
        val portrait = HeaderTransitionPolicy.lane(
            HeaderTransitionPolicy.topDp(
                HeaderGeometryDp(28f, 31f, null),
                HeaderAnchorRegionsDp(HeaderRectDp(68f, 28f, 300f, 76f), HeaderRectDp(614f, 28f, 846f, 76f)),
            ),
        )
        val landscape = HeaderTransitionPolicy.lane(
            HeaderTransitionPolicy.topDp(
                HeaderGeometryDp(28f, 31f, null),
                HeaderAnchorRegionsDp(HeaderRectDp(68f, 28f, 300f, 76f), HeaderRectDp(614f, 28f, 846f, 76f)),
            ),
        )
        assertTrue(kotlin.math.abs(portrait.centerYDp - landscape.centerYDp) <= 1f)
        assertEquals(48f, portrait.heightDp, .001f)
    }

    @Test
    fun immersiveTopIgnoresHiddenStatusBarAndUsesCutoutOrVisualMargin() {
        val nonIntersecting = HeaderAnchorRegionsDp(
            leftTitle = HeaderRectDp(180f, 0f, 500f, 48f),
            rightAction = HeaderRectDp(700f, 0f, 900f, 48f),
        )
        val withCutout = HeaderTransitionPolicy.immersiveTopDp(
            cutout = HeaderRectDp(0f, 0f, 74f, 36f),
            safeDrawingTopDp = 0f,
            anchors = nonIntersecting,
        )
        assertEquals(5f, withCutout, .001f)
        val noCutout = HeaderTransitionPolicy.immersiveTopDp(
            cutout = null,
            safeDrawingTopDp = 0f,
            anchors = nonIntersecting,
        )
        assertEquals(5f, noCutout, .001f)
        val safeTop = HeaderTransitionPolicy.immersiveTopDp(
            cutout = null,
            safeDrawingTopDp = 24f,
            anchors = nonIntersecting,
        )
        assertEquals(24f, safeTop, .001f)
    }

    @Test
    fun immersiveTopLowersOnlyWhenAnchorIntersectsCutout() {
        val intersecting = HeaderAnchorRegionsDp(
            leftTitle = HeaderRectDp(0f, 0f, 120f, 48f),
            rightAction = HeaderRectDp(700f, 0f, 900f, 48f),
        )
        val lowered = HeaderTransitionPolicy.immersiveTopDp(
            cutout = HeaderRectDp(0f, 0f, 74f, 36f),
            safeDrawingTopDp = 0f,
            anchors = intersecting,
        )
        assertEquals(40f, lowered, .001f)
    }

    @Test
    fun immersiveTopKeepsBothRotationsSymmetric() {
        val anchors = HeaderAnchorRegionsDp(
            leftTitle = HeaderRectDp(0f, 0f, 300f, 48f),
            rightAction = HeaderRectDp(614f, 0f, 846f, 48f),
        )
        val left = HeaderTransitionPolicy.immersiveTopDp(
            HeaderRectDp(0f, 0f, 74f, 36f),
            safeDrawingTopDp = 0f,
            anchors = anchors,
        )
        val right = HeaderTransitionPolicy.immersiveTopDp(
            HeaderRectDp(840f, 0f, 914f, 36f),
            safeDrawingTopDp = 0f,
            anchors = anchors,
        )
        assertEquals(left, right, .001f)
    }

    @Test
    fun immersiveBackdropHasDarkOverlayAndTopGradientWithLowFrequencyBlur() {
        val plan = ImmersiveBackdropPolicy.plan(LandscapeHomeStyle.IMMERSIVE_COVER, reducedTransparency = false, darkTheme = true)
        assertTrue(plan.drawBackdrop)
        assertEquals(
            listOf(
                ImmersiveGradientStop(0f, .84f, ImmersiveBackdropPolicy.DARK_START),
                ImmersiveGradientStop(132f, 0f, ImmersiveBackdropPolicy.TRANSPARENT),
            ),
            plan.topGradientStops,
        )
        assertFalse(plan.drawHorizontalScrim)
        assertFalse(plan.drawBottomScrim)
        assertFalse(plan.fixedBackdropAlphaDim)
        assertEquals(.62f, plan.fullOverlayAlpha, .001f)
        assertEquals(18f, plan.blurRadiusDp, .001f)
        assertEquals(.85f, plan.saturation, .001f)
        assertEquals(1.06f, ImmersiveBackdropPolicy.BACKDROP_SCALE, .001f)
        assertTrue(plan.staticCoverAlpha in 0.18f..0.24f)
    }

    @Test
    fun lightBackdropUsesWhiteOverlayAndTopGradient() {
        val dark = ImmersiveBackdropPolicy.plan(LandscapeHomeStyle.IMMERSIVE_COVER, reducedTransparency = false, darkTheme = true)
        val light = ImmersiveBackdropPolicy.plan(LandscapeHomeStyle.IMMERSIVE_COVER, reducedTransparency = false, darkTheme = false)
        assertEquals(.72f, light.fullOverlayAlpha, .001f)
        assertEquals(
            listOf(
                ImmersiveGradientStop(0f, .90f, ImmersiveBackdropPolicy.LIGHT_START),
                ImmersiveGradientStop(132f, 0f, ImmersiveBackdropPolicy.TRANSPARENT),
            ),
            light.topGradientStops,
        )
        assertEquals(ImmersiveBackdropPolicy.LIGHT_START, light.topGradientStops.first().colorToken)
        assertEquals(dark.blurRadiusDp, light.blurRadiusDp, .001f)
        assertEquals(dark.saturation, light.saturation, .001f)
    }

    @Test
    fun reducedTransparencyUsesStaticCoverOverThemeBackground() {
        val plan = ImmersiveBackdropPolicy.plan(LandscapeHomeStyle.IMMERSIVE_COVER, reducedTransparency = true, darkTheme = false)
        assertTrue(plan.static)
        assertTrue(plan.topGradientStops.isEmpty())
        assertEquals(0f, plan.blurRadiusDp, .001f)
        assertEquals(1f, plan.saturation, .001f)
        assertEquals(0f, plan.fullOverlayAlpha, .001f)
        assertTrue(plan.staticCoverAlpha in 0.18f..0.24f)
    }

    @Test
    fun backdropScopesToBothHeroStylesAndVisibleHero() {
        assertTrue(ImmersiveBackdropPolicy.shouldRender(LandscapeHomeStyle.IMMERSIVE_COVER, heroVisible = true))
        assertTrue(ImmersiveBackdropPolicy.shouldRender(LandscapeHomeStyle.FOCUS_POSTER, heroVisible = true))
        assertFalse(ImmersiveBackdropPolicy.shouldRender(LandscapeHomeStyle.IMMERSIVE_COVER, heroVisible = false))
        assertFalse(ImmersiveBackdropPolicy.shouldRender(LandscapeHomeStyle.FOCUS_POSTER, heroVisible = false))
    }

    @Test
    fun focusPosterBackdropUsesSlightlyLighterOverlayWithTheSameBlur() {
        val dDark = ImmersiveBackdropPolicy.plan(
            LandscapeHomeStyle.IMMERSIVE_COVER, reducedTransparency = false, darkTheme = true,
        )
        val cDark = ImmersiveBackdropPolicy.plan(
            LandscapeHomeStyle.FOCUS_POSTER, reducedTransparency = false, darkTheme = true,
        )
        val cLight = ImmersiveBackdropPolicy.plan(
            LandscapeHomeStyle.FOCUS_POSTER, reducedTransparency = false, darkTheme = false,
        )
        assertTrue(cDark.drawBackdrop)
        assertTrue(cLight.drawBackdrop)
        assertEquals(18f, cDark.blurRadiusDp, .001f)
        assertEquals(.85f, cDark.saturation, .001f)
        assertTrue(cDark.fullOverlayAlpha < dDark.fullOverlayAlpha)
        assertTrue(cLight.fullOverlayAlpha < .72f)
        assertEquals(
            ImmersiveBackdropPolicy.DARK_TOP_GRADIENT_START_ALPHA,
            cDark.topGradientStops.first().alpha,
            .001f,
        )
        val cReduced = ImmersiveBackdropPolicy.plan(
            LandscapeHomeStyle.FOCUS_POSTER, reducedTransparency = true, darkTheme = false,
        )
        assertTrue(cReduced.static)
        assertTrue(cReduced.topGradientStops.isEmpty())
        assertEquals(0f, cReduced.blurRadiusDp, .001f)
        assertEquals(0f, cReduced.fullOverlayAlpha, .001f)
    }

    @Test
    fun compactFirstPageIsBoundedByDockTop() {
        val page = CompactLandscapeViewportPolicy.firstPage(
            windowHeightDp = 411f,
            listTopDp = 79f,
            listBottomDp = 403f,
            dockHeightDp = 48f,
            dockBottomInsetDp = 32f,
        )
        assertEquals(331f, page.viewportBottomDp, .001f)
        assertEquals(252f, page.heightDp, .001f)
        assertEquals(403f, page.componentsStartDp, .001f)
        assertEquals(72f, page.bottomGapDp, .001f)
        assertTrue(page.componentsStartDp >= page.viewportBottomDp)
        assertFalse(CompactLandscapeViewportPolicy.componentPeeksUnderDock(page, dockTopDp = 331f))
    }

    @Test
    fun libraryGridFadeOnlyAtTopAndOutsideSearch() {
        assertTrue(LibraryGridTopFadePolicy.visible(firstVisibleItemIndex = 0, searchActive = false))
        assertFalse(LibraryGridTopFadePolicy.visible(firstVisibleItemIndex = 1, searchActive = false))
        assertFalse(LibraryGridTopFadePolicy.visible(firstVisibleItemIndex = 0, searchActive = true))
        assertEquals(72f, LibraryGridTopFadePolicy.EXTENT_DP, .001f)
        assertEquals(listOf(.45f, 0f), LibraryGridTopFadePolicy.stops.map { it.alpha })
        assertEquals(56f, LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP, .001f)
        assertEquals(12f, LibraryHeaderTransitionPolicy.FIRST_ROW_GAP_DP, .001f)
        assertTrue(
            "first row must sit 8..16dp below the toolbar bottom",
            LibraryHeaderTransitionPolicy.firstRowTopDp() - LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP in 8f..16f,
        )
        assertTrue(
            "the 72dp fade must cover the first row top",
            LibraryHeaderTransitionPolicy.firstRowTopDp() <
                LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP + LibraryGridTopFadePolicy.EXTENT_DP,
        )
    }

    @Test
    fun libraryHeaderTransitionUsesSharedScaledTopGap() {
        assertEquals(0f, LibraryHeaderTransitionPolicy.TOP_GAP_DP, .001f)
        assertEquals(0f, LibraryHeaderTransitionPolicy.topGapDp(1f), .001f)
        assertEquals(0f, LibraryHeaderTransitionPolicy.topGapDp(.9f), .001f)
        assertEquals(0f, LibraryHeaderTransitionPolicy.topGapDp(1.2f), .001f)
        assertEquals(0f, LibraryHeaderTransitionPolicy.topGapDp(0f), .001f)
        // The same fade is used for the initial and the scrolled-at-top state.
        assertTrue(LibraryGridTopFadePolicy.visible(firstVisibleItemIndex = 0, searchActive = false))
        assertTrue(LibraryGridTopFadePolicy.stops.first().alpha == LibraryGridTopFadePolicy.START_ALPHA)
        assertTrue(LibraryGridTopFadePolicy.stops.last().alpha == LibraryGridTopFadePolicy.END_ALPHA)
    }

    @Test
    fun librarySearchUsesFullSafeDrawingTop() {
        assertEquals(31f, HeaderTransitionPolicy.librarySearchTopDp(safeDrawingTopDp = 31f, compactLandscape = true), .001f)
        assertEquals(31f, HeaderTransitionPolicy.librarySearchTopDp(safeDrawingTopDp = 31f, compactLandscape = false), .001f)
    }

    @Test
    fun portraitHeaderIgnoresCompactCutoutMeasurementFeedback() {
        val portrait = HomeCompactHeaderPolicy.topDp(
            compactLandscape = false,
            safeDrawingTopDp = 42f,
            geometry = HeaderGeometryDp(24f, 42f, HeaderRectDp(420f, 0f, 494f, 36f)),
            anchors = HeaderAnchorRegionsDp(
                HeaderRectDp(68f, 42f, 300f, 90f),
                HeaderRectDp(614f, 42f, 846f, 90f),
            ),
        )
        assertEquals(42f, portrait, .001f)
    }

    @Test
    fun compactHeaderUsesImmersiveTopInsteadOfHiddenStatusBar() {
        val top = HomeCompactHeaderPolicy.topDp(
            compactLandscape = true,
            safeDrawingTopDp = 0f,
            geometry = HeaderGeometryDp(
                statusBarBottomDp = 0f,
                safeDrawingTopDp = 0f,
                cutout = HeaderRectDp(0f, 0f, 74f, 36f),
            ),
            anchors = HeaderAnchorRegionsDp(
                HeaderRectDp(0f, 0f, 300f, 48f),
                HeaderRectDp(614f, 0f, 846f, 48f),
            ),
        )
        assertEquals(40f, top, .001f)
    }

    @Test
    fun centerCardIsTwentySevenPointFivePercentClampedToTwoFortyFourToTwoNinetyFour() {
        assertEquals(.275f, LandscapeHeroSizingPolicy.CENTER_FRACTION, .001f)
        assertEquals(275f, LandscapeHeroSizingPolicy.centerCardWidthDp(1000f), .001f)
        assertEquals(244f, LandscapeHeroSizingPolicy.centerCardWidthDp(800f), .001f)
        assertEquals(294f, LandscapeHeroSizingPolicy.centerCardWidthDp(1200f), .001f)
        assertEquals(26f, LandscapeHeroSizingPolicy.CENTER_RADIUS_DP, .001f)
    }

    @Test
    fun sideCardsStayAtFourteenPointFivePercentClampedToOneThirtyTwoToOneSeventySix() {
        val viewport = 914f
        val sideWidth = LandscapeHeroSizingPolicy.sideCardWidthDp(viewport)
        assertEquals(.145f, sideWidth / viewport, .001f)
        assertEquals(132.53f, sideWidth, .01f)
        assertEquals(132f, LandscapeHeroSizingPolicy.sideCardWidthDp(600f), .001f)
        assertEquals(176f, LandscapeHeroSizingPolicy.sideCardWidthDp(1400f), .001f)
        assertTrue(LandscapeHeroSizingPolicy.SIDE_ALPHA in .56f..0.64f)
        assertTrue(LandscapeHeroSizingPolicy.SIDE_SCALE in .76f..0.80f)
        assertEquals(22f, LandscapeHeroSizingPolicy.SIDE_RADIUS_DP, .001f)
    }

    @Test
    fun sideCardCentersAreSymmetricAndCutoutSafe() {
        val viewport = 914f
        val centerWidth = LandscapeHeroSizingPolicy.centerCardWidthDp(viewport)
        val sideWidth = LandscapeHeroSizingPolicy.sideCardWidthDp(viewport)
        val outer = LandscapeHeroSizingPolicy.outerMarginDp(viewport, symmetricSafeGuardDp = 0f)
        val leftCenter = LandscapeHeroSizingPolicy.sideCenterLeftDp(viewport, outer, sideWidth)
        assertEquals(102.83f, leftCenter, .01f)
        assertEquals(
            LandscapeHeroSizingPolicy.sideCenterRightDp(viewport, outer, sideWidth),
            viewport - leftCenter,
            .001f,
        )
        val slotDistance = LandscapeHeroSizingPolicy.slotCenterDistanceDp(viewport, leftCenter)
        val spacing = LandscapeHeroSizingPolicy.pageSpacingDp(centerWidth, slotDistance)
        assertTrue(spacing > 0f)
        assertEquals(centerWidth + spacing, slotDistance, .001f)
        val guardedOuter = LandscapeHeroSizingPolicy.outerMarginDp(viewport, symmetricSafeGuardDp = 60f)
        assertEquals(70f, guardedOuter, .001f)
        val guardedCenter = LandscapeHeroSizingPolicy.sideCenterLeftDp(viewport, guardedOuter, sideWidth)
        assertEquals(136.27f, guardedCenter, .01f)
        assertTrue(guardedCenter >= guardedOuter + sideWidth / 2f)
    }

    @Test
    fun sideToCenterInterpolationIsContinuousAtSampleOffsets() {
        val viewport = 914f
        val centerWidth = LandscapeHeroSizingPolicy.centerCardWidthDp(viewport)
        val sideWidth = LandscapeHeroSizingPolicy.sideCardWidthDp(viewport)
        listOf(0f, .25f, .5f, .75f, 1f).forEach { offset ->
            val width = LandscapeHeroSizingPolicy.cardWidthDpAt(centerWidth, sideWidth, offset)
            val alpha = LandscapeHeroSizingPolicy.cardAlphaAt(offset)
            val scale = LandscapeHeroSizingPolicy.cardScaleAt(offset)
            val saturation = LandscapeHeroSizingPolicy.cardSaturationAt(offset)
            val radius = LandscapeHeroSizingPolicy.cardRadiusAt(offset)
            assertTrue(width in sideWidth..centerWidth)
            assertTrue(alpha in LandscapeHeroSizingPolicy.SIDE_ALPHA..1f)
            assertTrue(scale in LandscapeHeroSizingPolicy.SIDE_SCALE..1f)
            assertTrue(saturation in LandscapeHeroSizingPolicy.SIDE_SATURATION..1f)
            assertTrue(radius in LandscapeHeroSizingPolicy.SIDE_RADIUS_DP..LandscapeHeroSizingPolicy.CENTER_RADIUS_DP)
            assertTrue(LandscapeHeroSizingPolicy.infoAlphaAt(offset) in 0f..1f)
        }
        assertEquals(sideWidth, LandscapeHeroSizingPolicy.cardWidthDpAt(centerWidth, sideWidth, 1f), .001f)
        assertEquals(centerWidth, LandscapeHeroSizingPolicy.cardWidthDpAt(centerWidth, sideWidth, 0f), .001f)
        assertEquals(1f, LandscapeHeroSizingPolicy.infoAlphaAt(0f), .001f)
        assertEquals(0f, LandscapeHeroSizingPolicy.infoAlphaAt(1f), .001f)
        assertTrue(LandscapeHeroSizingPolicy.isActionableCenter(.25f))
        assertTrue(!LandscapeHeroSizingPolicy.isActionableCenter(.75f))
    }

    @Test
    fun componentGuttersAreSymmetricClampedAndCutoutSafe() {
        val viewport = 914f
        assertEquals(54.84f, LandscapeGutterPolicy.contentGutterDp(viewport, symmetricSafeGuardDp = 0f), .01f)
        assertEquals(48f, LandscapeGutterPolicy.contentGutterDp(500f, 0f), .001f)
        assertEquals(96f, LandscapeGutterPolicy.contentGutterDp(2000f, 0f), .001f)
        assertEquals(84f, LandscapeGutterPolicy.contentGutterDp(viewport, symmetricSafeGuardDp = 60f), .001f)
    }

    @Test
    fun carouselOuterMarginTracksCutoutSafeAndFraction() {
        val viewport = 914f
        assertEquals(36.56f, LandscapeHeroSizingPolicy.outerMarginDp(viewport, 0f), .01f)
        assertEquals(70f, LandscapeHeroSizingPolicy.outerMarginDp(viewport, 60f), .001f)
    }

    @Test
    fun dockWidthIsThirtyFivePercentClampedToThreeFortyToThreeSixty() {
        assertEquals(340f, LandscapeDockPolicy.widthDp(800f), .001f)
        assertEquals(350f, LandscapeDockPolicy.widthDp(1000f), .001f)
        assertEquals(360f, LandscapeDockPolicy.widthDp(1200f), .001f)
        assertEquals(48f, LandscapeDockPolicy.HEIGHT_DP, .001f)
        assertEquals(24f, LandscapeDockPolicy.RADIUS_DP, .001f)
    }

    @Test
    fun dockBottomInsetPrefersNavigationPlusGapOrViewportFraction() {
        assertEquals(32f, LandscapeDockPolicy.bottomInsetDp(24f, 411f), .001f)
        assertEquals(14f, LandscapeDockPolicy.bottomInsetDp(0f, 400f), .001f)
        assertEquals(35f, LandscapeDockPolicy.bottomInsetDp(0f, 1000f), .001f)
        assertEquals(18f, LandscapeDockPolicy.bottomInsetDp(10f, 400f), .001f)
    }

    @Test
    fun topbarOffsetsFollowFractionsButRespectSafeGuard() {
        val viewport = 914f
        assertEquals(50.27f, CompactLandscapeTopbarPolicy.titleLeftDp(viewport, 0f), .001f)
        assertEquals(50.27f, CompactLandscapeTopbarPolicy.titleLeftDp(viewport, 38.27f), .001f)
        assertEquals(15.4125f, CompactLandscapeTopbarPolicy.titleTopDp(10f, 411f), .001f)
        assertEquals(50.27f, CompactLandscapeTopbarPolicy.menuRightDp(viewport, 38.27f), .001f)
        assertEquals(14.385f, CompactLandscapeTopbarPolicy.menuTopDp(10f, 411f), .001f)
        assertTrue(CompactLandscapeTopbarPolicy.menuTopDp(10f, 411f) <=
            CompactLandscapeTopbarPolicy.titleTopDp(10f, 411f))
    }

    @Test
    fun topbarIsSymmetricAcrossRotations() {
        val viewport = 914f
        val symmetric = maxOf(60f, 0f)
        assertEquals(
            CompactLandscapeTopbarPolicy.titleLeftDp(viewport, symmetric),
            CompactLandscapeTopbarPolicy.menuRightDp(viewport, symmetric),
            .001f,
        )
        assertEquals(72f, CompactLandscapeTopbarPolicy.titleLeftDp(viewport, symmetric), .001f)
    }

    @Test
    fun backdropAlphaFadesOutAsPagerProgressGrows() {
        assertEquals(1f, LandscapePageTransitionPolicy.backdropAlpha(0f), .001f)
        assertEquals(.5f, LandscapePageTransitionPolicy.backdropAlpha(.5f), .001f)
        assertEquals(0f, LandscapePageTransitionPolicy.backdropAlpha(1f), .001f)
    }

    @Test
    fun recentGamesSurfaceUsesThemeTranslucencyRanges() {
        assertTrue(RecentGamesSurfacePolicy.surfaceAlpha(true) in 0.72f..0.80f)
        assertTrue(RecentGamesSurfacePolicy.surfaceAlpha(false) in 0.84f..0.90f)
        assertEquals(22f, RecentGamesSurfacePolicy.OUTER_RADIUS_DP, .001f)
        assertEquals(18f, RecentGamesSurfacePolicy.INNER_RADIUS_DP, .001f)
    }

    @Test
    fun recentWidgetOuterAndActionBordersAreRemovedSinceAlpha9() {
        assertFalse(RecentGamesSurfacePolicy.SHOW_OUTER_BORDER)
        assertFalse(RecentGamesSurfacePolicy.SHOW_ACTION_BORDER)
    }

    @Test
    fun `pixel 8 landscape content width fits two complete recent items`() {
        // Phone-landscape components page recent widget viewport (~484dp).
        val available = 484f
        assertEquals(2, RecentGamesRowPolicy.itemCount(available, maxItems = 2))
        val width = RecentGamesRowPolicy.cardWidthDp(available, 2)
        assertEquals((484f - RecentGamesRowPolicy.ITEM_GAP_DP) / 2f, width, .001f)
        assertTrue(width >= RecentGamesRowPolicy.MIN_ITEM_WIDTH_DP)
        assertTrue(width >= RecentGamesRowPolicy.MIN_TOUCH_WIDTH_DP)
        // No overflow: two cards plus the gap fit the viewport exactly.
        assertTrue(2f * width + RecentGamesRowPolicy.ITEM_GAP_DP <= available + .001f)
    }

    @Test
    fun `extremely short window falls back to one complete item`() {
        val narrow = 300f
        assertEquals(1, RecentGamesRowPolicy.itemCount(narrow, maxItems = 2))
        assertEquals(narrow, RecentGamesRowPolicy.cardWidthDp(narrow, 1), .001f)
    }

    @Test
    fun `three across only when the width really fits three min cards`() {
        assertTrue(RecentGamesRowPolicy.fits(600f, 3))
        assertFalse(RecentGamesRowPolicy.fits(500f, 3))
        assertEquals(2, RecentGamesRowPolicy.itemCount(500f, maxItems = 3))
    }

    @Test
    fun `every visible item stays above the touch minimum`() {
        val available = 380f
        val count = RecentGamesRowPolicy.itemCount(available, maxItems = 2)
        val width = RecentGamesRowPolicy.cardWidthDp(available, count)
        assertTrue(width >= RecentGamesRowPolicy.MIN_TOUCH_WIDTH_DP)
        assertTrue(RecentGamesRowPolicy.fits(available, count))
    }
}
