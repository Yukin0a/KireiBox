package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Alpha8HeaderTransitionPolicyTest {
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
        val dDark =
            ImmersiveBackdropPolicy.plan(
                LandscapeHomeStyle.IMMERSIVE_COVER,
                reducedTransparency = false,
                darkTheme = true,
            )
        val cDark =
            ImmersiveBackdropPolicy.plan(
                LandscapeHomeStyle.FOCUS_POSTER,
                reducedTransparency = false,
                darkTheme = true,
            )
        val cLight =
            ImmersiveBackdropPolicy.plan(
                LandscapeHomeStyle.FOCUS_POSTER,
                reducedTransparency = false,
                darkTheme = false,
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
        val cReduced =
            ImmersiveBackdropPolicy.plan(
                LandscapeHomeStyle.FOCUS_POSTER,
                reducedTransparency = true,
                darkTheme = false,
            )
        assertTrue(cReduced.static)
        assertTrue(cReduced.topGradientStops.isEmpty())
        assertEquals(0f, cReduced.blurRadiusDp, .001f)
        assertEquals(0f, cReduced.fullOverlayAlpha, .001f)
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
    fun carouselOuterMarginTracksCutoutSafeAndFraction() {
        val viewport = 914f
        assertEquals(36.56f, LandscapeHeroSizingPolicy.outerMarginDp(viewport, 0f), .01f)
        assertEquals(70f, LandscapeHeroSizingPolicy.outerMarginDp(viewport, 60f), .001f)
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
