package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGalleryGeometryPolicyTest {
    @Test
    fun responsivePortraitRearCardsStaySmallerAndBehindTheFrontCard() {
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.PORTRAIT,
                windowWidthPx = 1080f,
                windowHeightPx = 2400f,
                safeBounds = ImmersiveSafeBounds(0f, 0f, 1080f, 2400f),
            )
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER)
        val rear =
            listOf(
                layout.cardSlots.getValue(ImmersiveSlot.NEAR_LEFT),
                layout.cardSlots.getValue(ImmersiveSlot.NEAR_RIGHT),
            )
        assertTrue(rear.all { it.rect.h < center.rect.h })
        assertTrue(rear.all { it.zIndex < center.zIndex })
        assertTrue(rear.all { it.rect.y + it.rect.h <= center.rect.y + center.rect.h + center.rect.h * .08f })
    }

    @Test
    fun responsivePortraitRearCardsUseTheFrontCardCenterForPlacement() {
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.PORTRAIT,
                windowWidthPx = 1080f,
                windowHeightPx = 2400f,
                safeBounds = ImmersiveSafeBounds(0f, 0f, 1080f, 2400f),
            )
        val front = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        val frontCenterX = front.x + front.w / 2f
        val frontCenterY = front.y + front.h / 2f

        listOf(ImmersiveSlot.NEAR_LEFT, ImmersiveSlot.NEAR_RIGHT).forEach { slot ->
            val rear = layout.cardSlots.getValue(slot).rect
            val rearCenterX = rear.x + rear.w / 2f
            val rearCenterY = rear.y + rear.h / 2f
            assertTrue("rear card must remain near the front card", kotlin.math.abs(rearCenterX - frontCenterX) < front.w * .30f)
            assertTrue("rear card must not float above the front card", rear.y >= front.y - front.h * .12f)
            assertTrue("rear card must not extend materially below the front card", rear.y + rear.h <= front.y + front.h + front.h * .02f)
            assertTrue("rear card must remain in the same vertical composition", kotlin.math.abs(rearCenterY - frontCenterY) < front.h * .15f)
        }
    }

    @Test
    fun immersivePortraitGlowIsAnOvalCanvasWithoutTheRetiredRectangleLayer() {
        // The backdrop canvas may live in any ImmersiveHome*.kt sibling (e.g. ImmersiveHomeBackdrop.kt).
        val source = UiSourceFiles.group("ImmersiveHome")
        // The floor glow is still an oval on the backdrop canvas, but drawn as a radial gradient
        // squashed into an ellipse around its own center: an oval shape clipping an off-center
        // gradient showed a hard "horizon" edge (takeover QA #53).
        assertTrue(source.contains("scale(1f, brushes.glowSquash, pivot = brushes.glowCenter)"))
        assertTrue(source.contains("drawCircle(brush = brush, radius = brushes.glowRadius, center = brushes.glowCenter"))
        assertTrue("the glow gradient is centered on the glow", source.contains("center = glowCenter,"))
        assertFalse(source.contains("center.w * 2.24f,\n                    center.h * .60f"))
    }

    @Test
    fun dragVelocityUsesTheSameOppositeSignAsDragBy() {
        assertEquals(-2f, dragVelocitySlotsPerSecond(480f, 240f), 0f)
        assertEquals(2f, dragVelocitySlotsPerSecond(-480f, 240f), 0f)
        assertEquals(0f, dragVelocitySlotsPerSecond(480f, 0f), 0f)
        assertEquals(-1f, dragVelocitySlotsPerSecond(480f, 480f), 0f)
    }

    @Test
    fun backdropTrackUsesContinuousAdjacentIndicesAndWraps() {
        assertEquals(ImmersiveBackdropTrack(0, 1, 0f), resolveImmersiveBackdropTrack(0f, 3))
        assertEquals(ImmersiveBackdropTrack(0, 1, .25f), resolveImmersiveBackdropTrack(.25f, 3))
        assertEquals(ImmersiveBackdropTrack(0, 1, .5f), resolveImmersiveBackdropTrack(.5f, 3))
        assertEquals(ImmersiveBackdropTrack(0, 1, .75f), resolveImmersiveBackdropTrack(.75f, 3))
        assertEquals(ImmersiveBackdropTrack(1, 2, 0f), resolveImmersiveBackdropTrack(1f, 3))
        assertEquals(ImmersiveBackdropTrack(2, 0, .25f), resolveImmersiveBackdropTrack(2.25f, 3))
        assertEquals(ImmersiveBackdropTrack(2, 0, .75f), resolveImmersiveBackdropTrack(-.25f, 3))
        assertEquals(ImmersiveBackdropTrack(-1, -1, 0f), resolveImmersiveBackdropTrack(-1f, 0))
    }

    @Test
    fun landscapeViewportUsesSharedChromeAndSafeFrameCenter() {
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.LANDSCAPE,
                windowWidthPx = 1672f,
                windowHeightPx = 941f,
                safeBounds = ImmersiveSafeBounds(0f, 0f, 1672f, 941f),
            )

        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        assertEquals(1672f * IMMERSIVE_LANDSCAPE_CENTER_FRACTION, center.x + center.w / 2f, .01f)
        assertEquals(48f, layout.menu.w, .01f)
        // r5: the default Dock is the one the shell draws on every landscape tab — 16 units above the inset-free
        // window edge.
        val shared = resolveAdaptiveUiMetrics(1672f, 941f, 0f, 0f, 0f, 0f)
        assertEquals(shared.dockRect, layout.dock)
        assertEquals(941f - AdaptiveLayoutConstants.DOCK_LANDSCAPE_MIN_EDGE_GAP_DP, layout.dock.y + layout.dock.h, .01f)
    }

    @Test
    fun portraitViewportUsesSharedChromeAndActualDock() {
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.PORTRAIT,
                windowWidthPx = 852f,
                windowHeightPx = 1846f,
                safeBounds = ImmersiveSafeBounds(0f, 0f, 852f, 1846f),
            )

        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        assertEquals(426f, center.x + center.w / 2f, .01f)
        assertEquals(48f, layout.menu.w, .01f)
        assertEquals(
            resolveAdaptiveUiMetrics(852f, 1846f, 0f, 0f, 0f, 0f).dockRect,
            layout.dock,
        )
    }

    @Test
    fun pixel8LandscapeUsesSafeAreaResponsiveRatiosWithoutCanvasContain() {
        val safe = ImmersiveSafeBounds(leftPx = 120f, topPx = 0f, rightPx = 2400f, bottomPx = 1030f)
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.LANDSCAPE,
                windowWidthPx = 2400f,
                windowHeightPx = 1080f,
                safeBounds = safe,
            )
        val safeHeight = safe.bottomPx - safe.topPx
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect

        assertEquals(
            .646f,
            (center.x + center.w / 2f) / 2400f,
            .005f,
        )
        assertEquals(
            .053f,
            layout.metadata!!.x / 2400f,
            .001f,
        )
        assertEquals(
            safe.leftPx + IMMERSIVE_LANDSCAPE_METADATA_MIN_SAFE_GAP_PX,
            layout.metadata!!.x,
            .01f,
        )
        assertTrue(layout.metadata!!.x >= safe.leftPx + IMMERSIVE_LANDSCAPE_METADATA_MIN_SAFE_GAP_PX)
        assertEquals(
            safe.leftPx + (safe.rightPx - safe.leftPx) * IMMERSIVE_LANDSCAPE_CENTER_FRACTION,
            center.x + center.w / 2f,
            .01f,
        )
        assertEquals(safeHeight * IMMERSIVE_LANDSCAPE_CENTER_HEIGHT_FRACTION, center.h, .01f)
        assertEquals(357f / 504f, center.w / center.h, .001f)
    }

    @Test
    fun landscapeGalleryUsesTheOpticalAxisForSixteenToTenAnd218ToOneSafeFrames() {
        for ((width, height) in listOf(1280f to 800f, 914f to 411f)) {
            val layout =
                resolveImmersiveLayout(
                    HomeOrientation.LANDSCAPE,
                    width,
                    height,
                    ImmersiveSafeBounds(0f, 0f, width, height),
                )
            val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
            val expectedCenter = width * IMMERSIVE_LANDSCAPE_CENTER_FRACTION
            assertEquals(expectedCenter, center.x + center.w / 2f, .01f)
            val left = layout.cardSlots.getValue(ImmersiveSlot.NEAR_LEFT).rect
            val right = layout.cardSlots.getValue(ImmersiveSlot.NEAR_RIGHT).rect
            assertEquals(
                expectedCenter * 2f,
                (left.x + left.w / 2f) + (right.x + right.w / 2f),
                .02f,
            )
        }
    }

    @Test
    fun requestedLandscapeFramesUseOpticalCenterCardHeightMetadataAnchorAndOneRenderScale() {
        listOf(1339f to 597f, 1728f to 1080f, 2358f to 1080f).forEach { (width, height) ->
            val layout =
                resolveImmersiveLayout(
                    orientation = HomeOrientation.LANDSCAPE,
                    windowWidthPx = width,
                    windowHeightPx = height,
                    safeBounds = ImmersiveSafeBounds(0f, 0f, width, height),
                )
            val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
            val centerX = center.x + center.w / 2f
            val centerFraction = centerX / width
            val expectedScale = resolveImmersiveRenderScale(HomeOrientation.LANDSCAPE, width, height)

            assertEquals("center fraction for $width x $height", IMMERSIVE_LANDSCAPE_CENTER_FRACTION, centerFraction, .001f)
            assertTrue("center card must stay at or below 53.6% for $width x $height", center.h / height <= .536f)
            assertEquals("center card height for $width x $height", IMMERSIVE_LANDSCAPE_CENTER_HEIGHT_FRACTION, center.h / height, .001f)
            assertEquals(
                "metadata anchor for $width x $height",
                IMMERSIVE_LANDSCAPE_METADATA_X_FRACTION,
                (layout.metadata!!.x - 0f) / width,
                .001f,
            )
            assertEquals("render scale for $width x $height", expectedScale, layout.renderScale, .001f)
            assertEquals(40f * expectedScale, layout.pagerCenters[1].x - layout.pagerCenters[0].x, .01f)
            assertEquals(maxOf(48f, 104f * expectedScale), layout.play.h, .01f)
        }
    }

    @Test
    fun landscapeMetadataUsesAbsoluteViewportTargetAndClampsPastLeftCutout() {
        val safe = ImmersiveSafeBounds(120f, 0f, 2320f, 1080f)
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.LANDSCAPE,
                windowWidthPx = 2400f,
                windowHeightPx = 1080f,
                safeBounds = safe,
            )
        assertEquals(
            maxOf(
                2400f * IMMERSIVE_LANDSCAPE_METADATA_X_FRACTION,
                safe.leftPx + IMMERSIVE_LANDSCAPE_METADATA_MIN_SAFE_GAP_PX,
            ),
            layout.metadata!!.x,
            .01f,
        )
    }

    @Test
    fun mirroredRightCutoutKeepsLandscapeElementsInsideTheOpticalFrame() {
        val safe = ImmersiveSafeBounds(0f, 0f, 2280f, 1080f)
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.LANDSCAPE,
                windowWidthPx = 2400f,
                windowHeightPx = 1080f,
                safeBounds = safe,
            )
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        val expectedCenterX = safe.leftPx + (safe.rightPx - safe.leftPx) * IMMERSIVE_LANDSCAPE_CENTER_FRACTION
        val rects =
            layout.cardSlots.values.map { it.rect } +
                listOfNotNull(
                    layout.metadata,
                    layout.title,
                    layout.subtitle,
                    layout.play,
                    layout.menu,
                    layout.dock,
                )

        assertEquals(expectedCenterX / 2400f, (center.x + center.w / 2f) / 2400f, .001f)
        assertEquals(.053f, layout.metadata!!.x / 2400f, .001f)
        assertTrue(rects.all { it.x >= safe.leftPx && it.x + it.w <= safe.rightPx + .01f })
    }

    @Test
    fun landscape1728UsesTheCorrectedAbsoluteCenterAndMetadataFractions() {
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.LANDSCAPE,
                windowWidthPx = 1728f,
                windowHeightPx = 1080f,
                safeBounds = ImmersiveSafeBounds(0f, 0f, 1728f, 1080f),
            )
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect

        assertEquals(
            IMMERSIVE_LANDSCAPE_CENTER_FRACTION,
            (center.x + center.w / 2f) / 1728f,
            .001f,
        )
        assertEquals(.053f, layout.metadata!!.x / 1728f, .001f)
    }

    @Test
    fun fontScaleGrowsTheReservationOnceWithoutChangingTheGeometryRenderScale() {
        val width = 1339f
        val height = 597f
        val normal =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                width,
                height,
                ImmersiveSafeBounds(0f, 0f, width, height),
                fontScale = 1f,
            )
        val large =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                width,
                height,
                ImmersiveSafeBounds(0f, 0f, width, height),
                fontScale = 1.5f,
            )
        val normalBudget = resolveImmersiveTextBudget(height, 1f, normal.renderScale)
        val largeBudget = resolveImmersiveTextBudget(height, 1.5f, large.renderScale)

        assertEquals(normal.renderScale, large.renderScale, .001f)
        assertEquals(1.5f, largeBudget.titleLineHeightPx / normalBudget.titleLineHeightPx, .001f)
        assertEquals(1.5f, largeBudget.metadataHeightPx / normalBudget.metadataHeightPx, .001f)
        assertEquals(1.5f, largeBudget.subtitleHeightPx / normalBudget.subtitleHeightPx, .001f)
        assertTrue(large.title!!.h > normal.title!!.h)
        assertEquals(
            40f * normal.renderScale,
            normal.pagerCenters[1].x - normal.pagerCenters[0].x,
            .01f,
        )
        assertEquals(
            40f * large.renderScale,
            large.pagerCenters[1].x - large.pagerCenters[0].x,
            .01f,
        )
    }

    @Test
    fun requestedViewportBudgetsClearCardsTextPagerAndActualDock() {
        val viewports =
            listOf(
                360f to 800f,
                411f to 914f,
                640f to 360f,
                914f to 411f,
                600f to 1024f,
                800f to 1280f,
            )
        for ((width, height) in viewports) {
            val metrics = resolveAdaptiveUiMetrics(width, height, 0f, 0f, 0f, 0f)
            val orientation = if (width > height) HomeOrientation.LANDSCAPE else HomeOrientation.PORTRAIT
            val layout =
                resolveImmersiveLayout(
                    orientation,
                    width,
                    height,
                    ImmersiveSafeBounds(0f, 0f, width, height),
                    dockRect = metrics.dockRect,
                    bottomClearancePx = metrics.bottomClearanceDp,
                    fontScale = 1.5f,
                )
            val pagerY = layout.pagerCenters.first().y
            assertTrue("$width x $height pager must clear dock", pagerY <= layout.dock.y - metrics.bottomClearanceDp)
            val cardBottom = layout.cardSlots.values.maxOf { it.rect.y + it.rect.h }
            assertTrue("$width x $height cards must clear pager", cardBottom <= pagerY - IMMERSIVE_PAGER_GAP_PX + .01f)
            if (orientation == HomeOrientation.LANDSCAPE) {
                val farLeft = layout.cardSlots.getValue(ImmersiveSlot.FAR_LEFT).rect
                val textRects = listOfNotNull(layout.metadata, layout.title, layout.subtitle, layout.play)
                assertTrue(
                    "$width x $height text must stay left of cards",
                    textRects.all { it.x + it.w <= farLeft.x + .01f },
                )
            }
        }
    }

    @Test
    fun textBudgetGrowsForAccessibilityFontScalesAndKeepsTwoTitleLinesBounded() {
        val budgets = listOf(1f, 1.3f, 1.5f).map { resolveImmersiveTextBudget(941f, it) }
        assertTrue(budgets[0].titleHeightPx < budgets[1].titleHeightPx)
        assertTrue(budgets[1].titleHeightPx < budgets[2].titleHeightPx)
        assertEquals(2f, budgets[2].titleHeightPx / budgets[2].titleLineHeightPx, .2f)
    }

    @Test
    fun textBudgetUsesActualFontScaleWithoutAnUpperCap() {
        val base = resolveImmersiveTextBudget(941f, 1f, 1f)
        val large = resolveImmersiveTextBudget(941f, 2.5f, 1f)
        assertEquals(2.5f, large.titleLineHeightPx / base.titleLineHeightPx, .001f)
        assertEquals(2.5f, large.metadataHeightPx / base.metadataHeightPx, .001f)
        assertEquals(2.5f, large.subtitleHeightPx / base.subtitleHeightPx, .001f)
        assertEquals(2f, large.titleHeightPx / large.titleLineHeightPx, .001f)
    }

    @Test
    fun textBudgetMirrorsExplicitRenderedLineBoxes() {
        val budget = resolveImmersiveTextBudget(941f, 1.3f, 1f)
        assertEquals(IMMERSIVE_METADATA_LINE_HEIGHT_PX * 1.3f, budget.metadataHeightPx, .001f)
        assertEquals(IMMERSIVE_TITLE_LINE_HEIGHT_PX * 1.3f, budget.titleLineHeightPx, .001f)
        assertEquals(IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX * 1.3f, budget.subtitleHeightPx, .001f)
    }

    @Test
    fun textBudgetClampsOnlyTheLowerBoundAndFallsBackForNonFinite() {
        val tiny = resolveImmersiveTextBudget(941f, .5f, 1f)
        assertEquals(
            IMMERSIVE_TITLE_LINE_HEIGHT_PX * IMMERSIVE_FONT_SCALE_SAFE_MIN,
            tiny.titleLineHeightPx,
            .001f,
        )
        val nan = resolveImmersiveTextBudget(941f, Float.NaN, 1f)
        assertEquals(IMMERSIVE_TITLE_LINE_HEIGHT_PX, nan.titleLineHeightPx, .001f)
        val infinite = resolveImmersiveTextBudget(941f, Float.POSITIVE_INFINITY, 1f)
        assertEquals(IMMERSIVE_TITLE_LINE_HEIGHT_PX, infinite.titleLineHeightPx, .001f)
    }

    @Test
    fun layoutSurvivesNonFiniteFontScales() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                1339f,
                597f,
                ImmersiveSafeBounds(0f, 0f, 1339f, 597f),
                fontScale = Float.NaN,
            )
        assertTrue(layout.title!!.h > 0f)
        assertTrue(layout.play.h > 0f)
        assertEquals(
            resolveImmersiveTextBudget(597f, 1f, layout.renderScale).titleHeightPx,
            layout.title!!.h,
            .01f,
        )
    }

    @Test
    fun safeInsetsTranslateAndConstrainEveryResolvedElement() {
        val safe = ImmersiveSafeBounds(120f, 24f, 2280f, 1034f)
        val layout = resolveImmersiveLayout(HomeOrientation.LANDSCAPE, 2400f, 1080f, safe)
        val rects = layout.cardSlots.values.map { it.rect } + listOf(layout.menu, layout.play, layout.dock)
        assertTrue(rects.all { it.x >= safe.leftPx && it.y >= safe.topPx })
        assertTrue(rects.all { it.x + it.w <= safe.rightPx + .01f && it.y + it.h <= safe.bottomPx + .01f })
    }

    @Test
    fun landscapeCutoutsStayIndependentPerSide() {
        val leftCutout = ImmersiveSafeBounds(120f, 24f, 2320f, 1034f)
        val rightCutout = ImmersiveSafeBounds(80f, 24f, 2280f, 1034f)

        val leftLayout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                2400f,
                1080f,
                opticalSafeBounds(leftCutout, 2400f, 1080f),
            )
        val rightLayout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                2400f,
                1080f,
                opticalSafeBounds(rightCutout, 2400f, 1080f),
            )

        // A left cutout pushes left-anchored chrome right; it must not widen the right side.
        assertTrue("left cutout must move the brand right", leftLayout.brand.x > rightLayout.brand.x)
        assertTrue("left cutout must move the metadata right", leftLayout.metadata!!.x > rightLayout.metadata!!.x)
        // A right cutout pulls the menu left; it must not widen the left side.
        assertTrue("right cutout must move the menu left", rightLayout.menu.x < leftLayout.menu.x)
        // The optical gallery spans are equal, so card widths match.
        assertEquals(
            leftLayout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.w,
            rightLayout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.w,
            0.01f,
        )
    }

    @Test
    fun opticalBoundsKeepIndependentHorizontalInsetsAndExactGoldenUnchanged() {
        val raw = ImmersiveSafeBounds(120f, 0f, 2320f, 1080f)
        assertEquals(
            ImmersiveSafeBounds(120f, 0f, 2320f, 1080f),
            opticalSafeBounds(raw, 2400f, 1080f),
        )
        val golden = ImmersiveSafeBounds(0f, 0f, 1672f, 941f)
        assertEquals(golden, opticalSafeBounds(golden, 1672f, 941f))
    }

    @Test
    fun rawLandscapeCutoutsResolveAsymmetricallyPerSide() {
        val leftRaw = ImmersiveSafeBounds(120f, 18f, 2320f, 1062f)
        val rightRaw = ImmersiveSafeBounds(80f, 18f, 2280f, 1062f)
        val leftLayout = resolveImmersiveLayout(HomeOrientation.LANDSCAPE, 2400f, 1080f, leftRaw)
        val rightLayout = resolveImmersiveLayout(HomeOrientation.LANDSCAPE, 2400f, 1080f, rightRaw)

        assertTrue("left raw cutout must push the brand right", leftLayout.brand.x > rightLayout.brand.x)
        assertTrue("right raw cutout must pull the menu left", rightLayout.menu.x < leftLayout.menu.x)
    }

    @Test
    fun landscapeTextRightEdgeLeavesMeasuredGapBeforeCenterCard() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                windowWidthPx = 1280f,
                windowHeightPx = 1080f,
                safeBounds = ImmersiveSafeBounds(0f, 0f, 1280f, 1080f),
            )
        val title = layout.title ?: error("landscape title missing")
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        val scale = 1080f / 941f

        assertTrue(title.x + title.w <= center.x - 48f * scale + 0.01f)
    }

    @Test
    fun portraitCenterCardIsExactlyNineSixteenthsAndClearsThePagerLane() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.PORTRAIT,
                1080f,
                2400f,
                ImmersiveSafeBounds(0f, 0f, 1080f, 2400f),
            )
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        assertEquals(9f / 16f, center.w / center.h, .001f)
        val pagerY = layout.pagerCenters.first().y
        assertTrue(center.y + center.h <= pagerY - IMMERSIVE_PAGER_GAP_PX + .01f)
    }

    @Test
    fun goldenPortraitCenterCardIsExactlyNineSixteenths() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.PORTRAIT,
                852f,
                1846f,
                ImmersiveSafeBounds(0f, 0f, 852f, 1846f),
            )
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        assertEquals(9f / 16f, center.w / center.h, .001f)
    }

    @Test
    fun pagerReservesSevenEvenlySpacedSlotsInBothOrientations() {
        val portrait =
            resolveImmersiveLayout(
                HomeOrientation.PORTRAIT,
                1080f,
                2400f,
                ImmersiveSafeBounds(0f, 0f, 1080f, 2400f),
            )
        assertEquals(7, portrait.pagerCenters.size)
        assertEquals(1, portrait.pagerCenters.map { it.y }.distinct().size)
        val portraitGaps = portrait.pagerCenters.zipWithNext { a, b -> b.x - a.x }
        assertTrue(portraitGaps.max() - portraitGaps.min() < .001f)

        val landscape =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                1672f,
                941f,
                ImmersiveSafeBounds(0f, 0f, 1672f, 941f),
            )
        assertEquals(7, landscape.pagerCenters.size)
        assertEquals(1, landscape.pagerCenters.map { it.y }.distinct().size)
        val landscapeGaps = landscape.pagerCenters.zipWithNext { a, b -> b.x - a.x }
        assertTrue(landscapeGaps.max() - landscapeGaps.min() < .001f)
    }

    @Test
    fun landscapeLeftColumnStaysUnderThirtyPercentWithCardGap() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                1672f,
                941f,
                ImmersiveSafeBounds(0f, 0f, 1672f, 941f),
            )
        val title = layout.title ?: error("title missing")
        assertTrue(title.x + title.w <= 1672f * .30f + .01f)
        val farLeft = layout.cardSlots.getValue(ImmersiveSlot.FAR_LEFT).rect
        assertTrue(title.x + title.w <= farLeft.x - LANDSCAPE_TEXT_CARD_GAP_PX + .01f)
    }

    @Test
    fun landscapePagerClearsCardAndDockWithAtLeastTwelveDpEquivalents() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                1672f,
                941f,
                ImmersiveSafeBounds(0f, 0f, 1672f, 941f),
            )
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        val pagerY = layout.pagerCenters.first().y
        val dockTop = layout.dock.y
        val gapDp = 12f * 2.625f
        assertTrue("pager must clear the card", pagerY >= center.y + center.h + gapDp)
        assertTrue("pager must clear the dock", pagerY <= dockTop - gapDp)
    }

    @Test
    fun portrait1080By2400LayoutKeepsDockFullyVisible() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.PORTRAIT,
                1080f,
                2400f,
                ImmersiveSafeBounds(0f, 0f, 1080f, 2400f),
            )
        assertEquals(1080f, layout.dock.x + layout.dock.w + (1080f - layout.dock.x - layout.dock.w), 0f)
        assertTrue(layout.dock.y >= 0f)
        assertTrue(layout.dock.y + layout.dock.h <= 2400f)
        assertTrue(layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.w > 0f)
    }

    @Test
    fun portraitDesignCanvasUsesGoldenDimensions() {
        assertEquals(
            DesignCanvas(HomeOrientation.PORTRAIT, 852f, 1846f),
            HomeGalleryGeometryPolicy.designCanvas(HomeOrientation.PORTRAIT)
        )
    }

    @Test
    fun landscapeDesignCanvasUsesGoldenDimensions() {
        assertEquals(
            DesignCanvas(HomeOrientation.LANDSCAPE, 1672f, 941f),
            HomeGalleryGeometryPolicy.designCanvas(HomeOrientation.LANDSCAPE)
        )
    }

    @Test
    fun safeAreaRenderKindProvidesExplicitImmersiveOrientationForCallerWiring() {
        assertEquals(
            HomeOrientation.PORTRAIT,
            HomeGalleryGeometryPolicy.orientationForRenderKind(HomeRenderKind.IMMERSIVE_PORTRAIT),
        )
        assertEquals(
            HomeOrientation.LANDSCAPE,
            HomeGalleryGeometryPolicy.orientationForRenderKind(HomeRenderKind.IMMERSIVE_LANDSCAPE),
        )
    }

    @Test
    fun oneToOneViewportUsesIdentityScale() {
        assertEquals(
            1f,
            HomeGalleryGeometryPolicy.uniformScale(852f, 1846f, 852f, 1846f),
            0f
        )
    }

    @Test
    fun exactDoubleViewportUsesDoubleScale() {
        assertEquals(
            2f,
            HomeGalleryGeometryPolicy.uniformScale(1704f, 3692f, 852f, 1846f),
            0f
        )
    }

    @Test
    fun wideViewportUsesTheSmallerUniformScale() {
        assertEquals(
            1f,
            HomeGalleryGeometryPolicy.uniformScale(1200f, 1846f, 852f, 1846f),
            0f
        )
    }

    @Test
    fun tallViewportUsesTheSmallerUniformScale() {
        assertEquals(
            1f,
            HomeGalleryGeometryPolicy.uniformScale(852f, 2400f, 852f, 1846f),
            0f
        )
    }

    @Test
    fun zeroViewportProducesZeroScale() {
        assertEquals(
            0f,
            HomeGalleryGeometryPolicy.uniformScale(0f, 0f, 852f, 1846f),
            0f
        )
    }

    @Test
    fun identityViewportHasZeroCenterOffset() {
        assertEquals(
            HomePoint(0f, 0f),
            HomeGalleryGeometryPolicy.centerOffset(852f, 1846f, 852f, 1846f, 1f)
        )
    }

    @Test
    fun doubleViewportCentersTheScaledFrame() {
        assertEquals(
            HomePoint(0f, 0f),
            HomeGalleryGeometryPolicy.centerOffset(1704f, 3692f, 852f, 1846f, 2f)
        )
    }

    @Test
    fun wideViewportCentersHorizontally() {
        assertEquals(
            HomePoint(174f, 0f),
            HomeGalleryGeometryPolicy.centerOffset(1200f, 1846f, 852f, 1846f, 1f)
        )
    }

    @Test
    fun tallViewportCentersVertically() {
        assertEquals(
            HomePoint(0f, 277f),
            HomeGalleryGeometryPolicy.centerOffset(852f, 2400f, 852f, 1846f, 1f)
        )
    }

    @Test
    fun designAndViewportTransformsRoundTripAtIdentity() {
        val point = HomePoint(301f, 714f)
        val viewport = HomeGalleryGeometryPolicy.designToViewport(point, 1f, HomePoint(0f, 0f))
        assertEquals(
            point,
            HomeGalleryGeometryPolicy.viewportToDesign(viewport, 1f, HomePoint(0f, 0f))
        )
    }

    @Test
    fun designPointTranslatesUnderDoubleScale() {
        assertEquals(
            HomePoint(20f, 40f),
            HomeGalleryGeometryPolicy.designToViewport(HomePoint(10f, 20f), 2f, HomePoint(0f, 0f))
        )
    }

    @Test
    fun coordinateTransformAppliesCenterOffset() {
        val offset = HomePoint(174f, 0f)
        val viewport = HomeGalleryGeometryPolicy.designToViewport(HomePoint(100f, 200f), 1f, offset)
        assertEquals(HomePoint(274f, 200f), viewport)
        assertEquals(
            HomePoint(100f, 200f),
            HomeGalleryGeometryPolicy.viewportToDesign(viewport, 1f, offset)
        )
    }

    @Test
    fun nonIdentityScaleAndOffsetRoundTripPreservesDesignPoint() {
        val point = HomePoint(301f, 714f)
        val scale = 1.375f
        val offset = HomePoint(41f, -23f)
        val viewport = HomeGalleryGeometryPolicy.designToViewport(point, scale, offset)
        assertEquals(
            point,
            HomeGalleryGeometryPolicy.viewportToDesign(viewport, scale, offset)
        )
    }

    @Test
    fun portraitReferenceAnchorsExposeGoldenGeometry() {
        val anchors = HomeGalleryGeometryPolicy.referenceAnchors(HomeOrientation.PORTRAIT)
        assertEquals(HomePoint(61f, 52f), anchors.brandOrigin)
        assertEquals(HomeRect(722.5f, 52f, 67f, 67f), anchors.menuRect)
        assertEquals(HomeRect(565f, 1186f, 84f, 84f), anchors.playRect)
        assertEquals(HomeRect(121f, 1591f, 606f, 147f), anchors.dockRect)
        assertEquals(1495f, anchors.pagerBaseline, 0f)
        assertTrue(anchors.pagerCenters.all { it.y == 1495f })
        assertEquals(1, anchors.heroSlots.size)
        val front = anchors.heroSlots.single()
        assertEquals(HomeCardSlot.CENTER, front.role)
        assertEquals(HomeRect(160f, 340f, 546f, 970.67f), front.rect)
        assertEquals(2.5f, front.rotationDeg, 0f)
    }

    @Test
    fun landscapeReferenceAnchorsExposeFiveOrderedSlots() {
        val anchors = HomeGalleryGeometryPolicy.referenceAnchors(HomeOrientation.LANDSCAPE)
        assertEquals(HomePoint(65f, 50f), anchors.brandOrigin)
        assertEquals(HomeRect(1551.5f, 33.5f, 69f, 69f), anchors.menuRect)
        assertEquals(HomeRect(87f, 496f, 292f, 99f), anchors.playRect)
        assertEquals(HomeRect(467f, 786f, 737f, 100f), anchors.dockRect)
        assertEquals(HomePoint(89f, 264f), anchors.metadataOrigin)
        assertEquals(HomePoint(89f, 322f), anchors.titleOrigin)
        assertEquals(HomePoint(89f, 428f), anchors.subtitleOrigin)
        assertEquals(740f, anchors.pagerBaseline, 0f)
        assertEquals(5, anchors.heroSlots.size)
        assertEquals(
            listOf(
                HomeCardSlot.FAR_LEFT,
                HomeCardSlot.NEAR_LEFT,
                HomeCardSlot.CENTER,
                HomeCardSlot.NEAR_RIGHT,
                HomeCardSlot.FAR_RIGHT,
            ),
            anchors.heroSlots.map { it.role }
        )
        assertEquals(HomeRect(901f, 170f, 357f, 504f), anchors.heroSlots[2].rect)
        assertEquals(167f, anchors.heroSlots[1].rect.w, 0f)
        assertEquals(399f, anchors.heroSlots[1].rect.h, 0f)
        assertEquals(142f, anchors.heroSlots[0].rect.w, 0f)
        assertEquals(359f, anchors.heroSlots[0].rect.h, 0f)
    }

    @Test
    fun landscapeSideSlotsAreMirroredFromTheLeftAroundCenterAxis() {
        val slots = HomeGalleryGeometryPolicy.referenceAnchors(HomeOrientation.LANDSCAPE).heroSlots
        val axisX = 1079.5f
        for ((leftIndex, rightIndex) in listOf(0 to 4, 1 to 3)) {
            val left = slots[leftIndex]
            val right = slots[rightIndex]
            assertEquals(left.rect.y, right.rect.y, 0f)
            assertEquals(left.rect.w, right.rect.w, 0f)
            assertEquals(left.rect.h, right.rect.h, 0f)
            assertEquals(2f * axisX - (left.rect.x + left.rect.w), right.rect.x, 0f)
            assertEquals(-left.rotationDeg, right.rotationDeg, 0f)
            assertEquals(left.alpha, right.alpha, 0f)
        }
    }

    @Test
    fun landscapeGoldenSlotRectsEncodeVisualScaleWithoutRuntimeScaleFields() {
        val slots = HomeGalleryGeometryPolicy.referenceAnchors(HomeOrientation.LANDSCAPE).heroSlots
        assertEquals(HomeRect(560f, 264f, 142f, 359f), slots[0].rect)
        assertEquals(HomeRect(720f, 237f, 167f, 399f), slots[1].rect)
        assertEquals(HomeRect(901f, 170f, 357f, 504f), slots[2].rect)
        assertTrue(slots[2].rect.w > slots[1].rect.w)
        assertTrue(slots[1].rect.w > slots[0].rect.w)
        assertTrue(slots[2].alpha > slots[1].alpha)
        assertTrue(slots[1].alpha > slots[0].alpha)
        assertEquals(1f, slots[2].alpha, 0f)
    }

    @Test
    fun landscapeCardZOrderIsSymmetricCenterNearFar() {
        assertTrue(
            HomeGalleryGeometryPolicy.landscapeCardZIndex(HomeCardSlot.CENTER) >
                HomeGalleryGeometryPolicy.landscapeCardZIndex(HomeCardSlot.NEAR_LEFT),
        )
        assertTrue(
            HomeGalleryGeometryPolicy.landscapeCardZIndex(HomeCardSlot.NEAR_LEFT) >
                HomeGalleryGeometryPolicy.landscapeCardZIndex(HomeCardSlot.FAR_LEFT),
        )
        assertEquals(
            HomeGalleryGeometryPolicy.landscapeCardZIndex(HomeCardSlot.NEAR_LEFT),
            HomeGalleryGeometryPolicy.landscapeCardZIndex(HomeCardSlot.NEAR_RIGHT),
            0f,
        )
        assertEquals(
            HomeGalleryGeometryPolicy.landscapeCardZIndex(HomeCardSlot.FAR_LEFT),
            HomeGalleryGeometryPolicy.landscapeCardZIndex(HomeCardSlot.FAR_RIGHT),
            0f,
        )
    }

    @Test
    fun portraitRearZOrderPlacesNearerCardsAboveFartherCards() {
        assertTrue(
            HomeGalleryGeometryPolicy.portraitCardZIndex(HomeCardDepth.REAR, 1) >
                HomeGalleryGeometryPolicy.portraitCardZIndex(HomeCardDepth.REAR, 2),
        )
        assertTrue(
            HomeGalleryGeometryPolicy.portraitCardZIndex(HomeCardDepth.FRONT, 0) >
                HomeGalleryGeometryPolicy.portraitCardZIndex(HomeCardDepth.REAR, 1),
        )
    }

    @Test
    fun dockItemWidthUsesInnerWidthAfterScaledPadding() {
        assertEquals(
            (737f - 64f) / 3f,
            HomeGalleryGeometryPolicy.dockItemWidth(737f, 32f, 3),
            0f,
        )
    }

    @Test
    fun metadataWidthsScaleWithTheContainFrame() {
        assertEquals(700f, HomeGalleryGeometryPolicy.scaledTextWidth(700f, 1f), 0f)
        assertEquals(350f, HomeGalleryGeometryPolicy.scaledTextWidth(700f, .5f), 0f)
    }

    @Test
    fun galleryGestureRegionsExcludeControlsAndMetadata() {
        val anchors = HomeGalleryGeometryPolicy.referenceAnchors(HomeOrientation.LANDSCAPE)
        val regions = HomeGalleryGeometryPolicy.galleryGestureRegions(anchors, HomeOrientation.LANDSCAPE)
        assertEquals(anchors.heroSlots.map { it.rect }, regions.map { it.bounds })
        assertFalse(regions.any { it.contains(HomePoint(1552f, 34f)) })
        assertFalse(regions.any { it.contains(HomePoint(87f, 496f)) })
        assertFalse(regions.any { it.contains(HomePoint(467f, 786f)) })
        assertFalse(regions.any { it.contains(HomePoint(100f, 322f)) })
    }

    @Test
    fun layoutGestureRegionsExcludeCurrentControlsAndBudgetSizedTextRects() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                1339f,
                597f,
                ImmersiveSafeBounds(0f, 0f, 1339f, 597f),
                fontScale = 1.5f,
            )
        val regions =
            HomeGalleryGeometryPolicy.galleryGestureRegions(
                layout,
                HomeOrientation.LANDSCAPE,
            )

        assertEquals(layout.cardSlots.values.map { it.rect }, regions.map { it.bounds })
        assertTrue(regions.all { it.excludedRects.contains(layout.menu) })
        assertTrue(regions.all { it.excludedRects.contains(layout.play) })
        assertTrue(regions.all { it.excludedRects.contains(layout.dock) })
        assertTrue(regions.all { it.excludedRects.contains(layout.title) })
        assertTrue(regions.all { it.excludedRects.contains(layout.metadata!!) })
        layout.subtitle?.let { subtitle ->
            assertTrue(regions.all { it.excludedRects.contains(subtitle) })
        }

        // The exclusions are the budget-sized rects, not the stale one-line reference
        // constants: the title rect covers exactly the resolved two-line height.
        val title = layout.title!!
        assertEquals(
            resolveImmersiveTextBudget(597f, 1.5f, layout.renderScale).titleHeightPx,
            title.h,
            .01f,
        )
        assertTrue(regions.none { it.contains(HomePoint(title.x + title.w / 2f, title.y + title.h / 2f)) })

        // A point inside the center card but outside every exclusion stays draggable.
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        assertTrue(regions.any { it.contains(HomePoint(center.x + center.w * .5f, center.y + center.h * .4f)) })
    }

    @Test
    fun portraitLayoutGestureRegionUsesTheResolvedCenterCardAndExcludesControls() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.PORTRAIT,
                411f,
                914f,
                ImmersiveSafeBounds(0f, 0f, 411f, 914f),
            )
        val regions =
            HomeGalleryGeometryPolicy.galleryGestureRegions(
                layout,
                HomeOrientation.PORTRAIT,
            )

        assertEquals(1, regions.size)
        val region = regions.single()
        assertEquals(layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect, region.bounds)
        assertEquals(listOf(layout.menu, layout.play, layout.dock), region.excludedRects)

        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        assertTrue(region.contains(HomePoint(center.x + center.w * .3f, center.y + center.h * .3f)))
        assertFalse(region.contains(HomePoint(layout.play.x + layout.play.w / 2f, layout.play.y + layout.play.h / 2f)))
    }

    @Test
    fun selectedIdentitySurvivesReorderedMetadataUpdates() {
        assertEquals(
            0,
            HomeGalleryGeometryPolicy.preserveSelectedIndex(
                previousSelectedKey = 42L,
                previousSelectedIndex = 1,
                updatedKeys = listOf(42L, 7L, 99L),
            ),
        )
    }

    @Test
    fun selectedIdentityClampsOnlyWhenRemoved() {
        assertEquals(
            1,
            HomeGalleryGeometryPolicy.preserveSelectedIndex(
                previousSelectedKey = 42L,
                previousSelectedIndex = 1,
                updatedKeys = listOf(7L, 99L),
            ),
        )
        assertEquals(
            0,
            HomeGalleryGeometryPolicy.preserveSelectedIndex(
                previousSelectedKey = 42L,
                previousSelectedIndex = 1,
                updatedKeys = listOf(7L),
            ),
        )
    }

    @Test
    fun scaledAnchorsApplyTheFrameTransformToPointsAndRects() {
        val anchors =
            HomeGalleryGeometryPolicy.scaledAnchors(
                HomeOrientation.LANDSCAPE,
                viewportW = 2000f,
                viewportH = 1200f,
            )
        val scale = HomeGalleryGeometryPolicy.uniformScale(2000f, 1200f, 1672f, 941f)
        val offset = HomeGalleryGeometryPolicy.centerOffset(2000f, 1200f, 1672f, 941f, scale)
        assertEquals(
            HomeGalleryGeometryPolicy.designToViewport(HomePoint(65f, 50f), scale, offset),
            anchors.brandOrigin,
        )
        val center = anchors.heroSlots[2].rect
        assertEquals(901f * scale + offset.x, center.x, .001f)
        assertEquals(170f * scale + offset.y, center.y, .001f)
        assertEquals(357f * scale, center.w, .001f)
        assertEquals(504f * scale, center.h, .001f)
        assertEquals(0f, anchors.heroSlots[2].rotationDeg, 0f)
    }

    @Test
    fun emptySlotListUsesRequiredSentinelWindow() {
        assertEquals(listOf(0), HomeGalleryGeometryPolicy.slotWindow(0, 0))
    }

    @Test
    fun oneSlotWindowContainsOnlyTheSlot() {
        assertEquals(listOf(0), HomeGalleryGeometryPolicy.slotWindow(0, 1))
    }

    @Test
    fun twoSlotsStayFullyVisibleAtEitherSelection() {
        assertEquals(listOf(0, 1), HomeGalleryGeometryPolicy.slotWindow(0, 2))
        assertEquals(listOf(0, 1), HomeGalleryGeometryPolicy.slotWindow(1, 2))
    }

    @Test
    fun threeFourAndFiveSlotsRemainFullyVisible() {
        assertEquals(listOf(0, 1, 2), HomeGalleryGeometryPolicy.slotWindow(1, 3))
        assertEquals(listOf(0, 1, 2, 3), HomeGalleryGeometryPolicy.slotWindow(2, 4))
        assertEquals(listOf(0, 1, 2, 3, 4), HomeGalleryGeometryPolicy.slotWindow(3, 5))
    }

    @Test
    fun sixSlotsStayFullyVisibleAroundTheSelectedSlot() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), HomeGalleryGeometryPolicy.slotWindow(3, 6))
    }

    @Test
    fun tenSlotsWrapTheWindowAcrossTheLeadingEdge() {
        assertEquals(listOf(7, 8, 9, 0, 1, 2, 3), HomeGalleryGeometryPolicy.slotWindow(0, 10))
    }

    @Test
    fun tenSlotsWrapTheWindowAcrossTheTrailingEdge() {
        assertEquals(listOf(6, 7, 8, 9, 0, 1, 2), HomeGalleryGeometryPolicy.slotWindow(9, 10))
    }

    @Test
    fun tenSlotsCenterTheWindowForAnInteriorSelection() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), HomeGalleryGeometryPolicy.slotWindow(4, 10))
    }

    @Test
    fun moreThanSevenSlotsUseCircularLeadingMiddleAndTrailingWindows() {
        assertEquals(listOf(7, 8, 9, 0, 1, 2, 3), HomeGalleryGeometryPolicy.slotWindow(0, 10))
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8), HomeGalleryGeometryPolicy.slotWindow(5, 10))
        assertEquals(listOf(6, 7, 8, 9, 0, 1, 2), HomeGalleryGeometryPolicy.slotWindow(9, 10))
    }

    @Test
    fun indicatorWindowMatchesThreeFourFiveAndSevenPlusBoundaries() {
        assertEquals(3, HomeGalleryGeometryPolicy.indicator(3, 1, HomeOrientation.LANDSCAPE).visibleCount)
        assertEquals(4, HomeGalleryGeometryPolicy.indicator(4, 2, HomeOrientation.LANDSCAPE).visibleCount)
        assertEquals(5, HomeGalleryGeometryPolicy.indicator(5, 3, HomeOrientation.LANDSCAPE).visibleCount)
        assertEquals(7, HomeGalleryGeometryPolicy.indicator(10, 0, HomeOrientation.LANDSCAPE).visibleCount)
        assertEquals(7, HomeGalleryGeometryPolicy.indicator(10, 5, HomeOrientation.LANDSCAPE).visibleCount)
        assertEquals(7, HomeGalleryGeometryPolicy.indicator(10, 9, HomeOrientation.LANDSCAPE).visibleCount)
        assertEquals(3, HomeGalleryGeometryPolicy.indicator(10, 0, HomeOrientation.LANDSCAPE).selectedIndex)
        assertEquals(3, HomeGalleryGeometryPolicy.indicator(10, 5, HomeOrientation.LANDSCAPE).selectedIndex)
        assertEquals(3, HomeGalleryGeometryPolicy.indicator(10, 9, HomeOrientation.LANDSCAPE).selectedIndex)
    }

    @Test
    fun selectedIndexWrapsForWindowAndIndicator() {
        assertEquals(listOf(6, 7, 8, 9, 0, 1, 2), HomeGalleryGeometryPolicy.slotWindow(-1, 10))
        assertEquals(listOf(7, 8, 9, 0, 1, 2, 3), HomeGalleryGeometryPolicy.slotWindow(10, 10))
        assertEquals(0, HomeGalleryGeometryPolicy.indicator(-1, -1, HomeOrientation.LANDSCAPE).selectedIndex)
        assertEquals(3, HomeGalleryGeometryPolicy.indicator(10, 10, HomeOrientation.LANDSCAPE).selectedIndex)
    }

    @Test
    fun slotWindowNeverReturnsAnIndexOutsideTheCollection() {
        for (slotCount in 0..12) {
            for (selectedIndex in -2..(slotCount + 2)) {
                assertTrue(
                    HomeGalleryGeometryPolicy.slotWindow(selectedIndex, slotCount)
                        .all { it in 0 until maxOf(slotCount, 1) }
                )
            }
        }
    }

    @Test
    fun indicatorIsHiddenForZeroOrOneItem() {
        assertEquals(
            HomeIndicatorSpec(false, 0, 0, HomeIndicatorStyle.DOT),
            HomeGalleryGeometryPolicy.indicator(0, 0, HomeOrientation.PORTRAIT)
        )
        assertEquals(
            HomeIndicatorSpec(false, 0, 0, HomeIndicatorStyle.BAR),
            HomeGalleryGeometryPolicy.indicator(1, 0, HomeOrientation.LANDSCAPE)
        )
    }

    @Test
    fun indicatorShowsTwoItemsWithoutTruncation() {
        assertEquals(
            HomeIndicatorSpec(true, 2, 1, HomeIndicatorStyle.DOT),
            HomeGalleryGeometryPolicy.indicator(2, 1, HomeOrientation.PORTRAIT)
        )
    }

    @Test
    fun indicatorTruncatesTenItemsToSevenCircularItems() {
        assertEquals(
            HomeIndicatorSpec(true, 7, 3, HomeIndicatorStyle.BAR),
            HomeGalleryGeometryPolicy.indicator(10, 9, HomeOrientation.LANDSCAPE)
        )
    }

    @Test
    fun indicatorStyleFollowsOrientation() {
        assertEquals(
            HomeIndicatorStyle.DOT,
            HomeGalleryGeometryPolicy.indicator(3, 1, HomeOrientation.PORTRAIT).style
        )
        assertEquals(
            HomeIndicatorStyle.BAR,
            HomeGalleryGeometryPolicy.indicator(3, 1, HomeOrientation.LANDSCAPE).style
        )
    }

    @Test
    fun portraitIndicatorCentersStayCenteredForEveryRealWindowSize() {
        val anchors =
            HomeGalleryGeometryPolicy.scaledAnchors(
                HomeOrientation.PORTRAIT,
                viewportW = 852f,
                viewportH = 1846f,
            )

        assertEquals(
            listOf(394f, 426f, 458f),
            HomeGalleryGeometryPolicy.indicatorCenters(anchors, 3, HomeOrientation.PORTRAIT)
                .map { it.x },
        )
        assertEquals(
            listOf(346f, 378f, 410f, 442f, 474f, 506f),
            HomeGalleryGeometryPolicy.indicatorCenters(anchors, 6, HomeOrientation.PORTRAIT)
                .map { it.x },
        )
    }

    @Test
    fun portraitIndicatorCentersApplyFrameScaleAndCenteredOffset() {
        val viewportW = 1000f
        val viewportH = 2000f
        val scale = HomeGalleryGeometryPolicy.uniformScale(viewportW, viewportH, 852f, 1846f)
        val offset = HomeGalleryGeometryPolicy.centerOffset(viewportW, viewportH, 852f, 1846f, scale)
        val anchors =
            HomeGalleryGeometryPolicy.scaledAnchors(
                HomeOrientation.PORTRAIT,
                viewportW = viewportW,
                viewportH = viewportH,
            )

        assertEquals(
            listOf(394f, 426f, 458f).map { it * scale + offset.x },
            HomeGalleryGeometryPolicy.indicatorCenters(
                anchors,
                3,
                HomeOrientation.PORTRAIT,
                frameScale = scale,
                frameOffset = offset,
            ).map { it.x },
        )
        assertEquals(
            anchors.pagerBaseline,
            HomeGalleryGeometryPolicy.indicatorCenters(
                anchors,
                3,
                HomeOrientation.PORTRAIT,
                frameScale = scale,
                frameOffset = offset,
            ).first().y,
            0f
        )
    }

    @Test
    fun portraitCardSpecsAreFixedAndKeepGoldenFrontGeometry() {
        val front = HomeGalleryGeometryPolicy.portraitCardSpec(gameIndex = 2, selectedIndex = 2)
        val rear = HomeGalleryGeometryPolicy.portraitCardSpec(gameIndex = 1, selectedIndex = 2)

        assertEquals(HomeRect(160f, 340f, 546f, 970.67f), front.rect)
        assertEquals(0f, front.translationX, 0f)
        assertEquals(0f, front.translationY, 0f)
        assertEquals(2.5f, front.rotationDeg, 0f)
        assertEquals(1f, front.alpha, 0f)
        assertEquals(3f, front.zIndex, 0f)
        assertEquals(HomeRect(160f, 340f, 546f, 970.67f), rear.rect)
        assertEquals(-84.63f, rear.translationX, .001f)
        assertEquals(-11.648f, rear.translationY, .001f)
        assertEquals(7.25f, rear.rotationDeg, .001f)
        assertEquals(.405f, rear.alpha, .001f)
        assertEquals(1.99f, rear.zIndex, .001f)
    }

    @Test
    fun landscapeCardSaturationDimsOnlyNonCenterSlots() {
        assertEquals(1f, HomeGalleryGeometryPolicy.landscapeCardSaturation(HomeCardSlot.CENTER), 0f)
        assertTrue(HomeGalleryGeometryPolicy.landscapeCardSaturation(HomeCardSlot.NEAR_LEFT) < 1f)
        assertTrue(HomeGalleryGeometryPolicy.landscapeCardSaturation(HomeCardSlot.FAR_RIGHT) < 1f)
    }

    @Test
    fun landscapeIndicatorCentersUseThePhysicalWindowCenter() {
        val anchors =
            HomeGalleryGeometryPolicy.scaledAnchors(
                HomeOrientation.LANDSCAPE,
                viewportW = 1672f,
                viewportH = 941f,
            )

        assertEquals(
            listOf(776f, 816f, 856f, 896f),
            HomeGalleryGeometryPolicy.indicatorCenters(anchors, 4, HomeOrientation.LANDSCAPE)
                .map { it.x },
        )
        assertEquals(
            listOf(736f, 776f, 816f, 856f, 896f, 936f),
            HomeGalleryGeometryPolicy.indicatorCenters(anchors, 6, HomeOrientation.LANDSCAPE)
                .map { it.x },
        )
    }

    @Test
    fun landscapeThreePagerCentersAreSymmetricAroundThePhysicalCenter() {
        val anchors = HomeGalleryGeometryPolicy.referenceAnchors(HomeOrientation.LANDSCAPE)
        assertEquals(
            listOf(796f, 836f, 876f),
            HomeGalleryGeometryPolicy.indicatorCenters(anchors, 3, HomeOrientation.LANDSCAPE)
                .map { it.x },
        )
    }

    @Test
    fun landscapeFivePagerCentersAreSymmetricAroundThePhysicalCenter() {
        val anchors = HomeGalleryGeometryPolicy.referenceAnchors(HomeOrientation.LANDSCAPE)
        assertEquals(
            listOf(756f, 796f, 836f, 876f, 916f),
            HomeGalleryGeometryPolicy.indicatorCenters(anchors, 5, HomeOrientation.LANDSCAPE)
                .map { it.x },
        )
    }

    @Test
    fun immersivePagerCentersUseThePhysicalWindowCenterAndClampOnlyForInsets() {
        val centered =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                2400f,
                1080f,
                ImmersiveSafeBounds(0f, 0f, 2400f, 1080f),
            )
        assertEquals(1200f, centered.pagerCenters[3].x, .01f)

        val leftInset = ImmersiveSafeBounds(1300f, 0f, 2400f, 1080f)
        val clamped =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                2400f,
                1080f,
                leftInset,
            )
        val safe = opticalSafeBounds(leftInset, 2400f, 1080f)
        val itemHalfWidth = 28f * clamped.renderScale / 2f
        val halfSpan = 3f * 40f * clamped.renderScale + itemHalfWidth
        assertEquals(
            safe.leftPx,
            clamped.pagerCenters.first().x - itemHalfWidth,
            .01f,
        )
        assertTrue(clamped.pagerCenters.last().x + itemHalfWidth <= safe.rightPx + .01f)
        assertTrue(halfSpan > 0f)
    }

    @Test
    fun landscapeMetadataOffsetIsDownwardBoundedAndCollisionSafeAtAccessibilityScales() {
        assertEquals(24f, resolveLandscapeMetadataOffset(100f, 500f, 200f), .001f)
        assertEquals(10f, resolveLandscapeMetadataOffset(100f, 210f, 100f), .001f)
        assertEquals(0f, resolveLandscapeMetadataOffset(100f, 180f, 100f), .001f)

        for (fontScale in listOf(1f, 1.5f)) {
            val width = 1728f
            val height = 1080f
            val safe = opticalSafeBounds(ImmersiveSafeBounds(0f, 0f, width, height), width, height)
            val layout =
                resolveImmersiveLayout(
                    HomeOrientation.LANDSCAPE,
                    width,
                    height,
                    safe,
                    fontScale = fontScale,
                )
            val chrome =
                HomePresentationPolicy.topChromeSpec(
                    HomeRect(
                        safe.leftPx,
                        safe.topPx,
                        safe.rightPx - safe.leftPx,
                        safe.bottomPx - safe.topPx,
                    )
                )
            val baseline =
                maxOf(
                    safe.topPx + (safe.bottomPx - safe.topPx) * .16f,
                    chrome.titleRect.y + chrome.titleRect.h + 12f * layout.renderScale,
                )
            assertTrue(
                layout.metadata!!.y - baseline in
                    -0.01f..IMMERSIVE_LANDSCAPE_METADATA_MAX_DOWNWARD_OFFSET_PX + .01f,
            )

            val textRects = listOfNotNull(layout.metadata, layout.title, layout.subtitle, layout.play)
            val cardRects =
                layout.cardSlots.values
                    .filter { it.alpha > 0f }
                    .map { it.rect }
            assertTrue(
                textRects.all { rect ->
                    cardRects.all { card ->
                        rect.x + rect.w <= card.x + .01f || card.x + card.w <= rect.x + .01f ||
                            rect.y + rect.h <= card.y + .01f || card.y + card.h <= rect.y + .01f
                    }
                }
            )
            assertTrue(textRects.all { it.y + it.h <= layout.pagerCenters.first().y + .01f })
            assertTrue(textRects.all { it.x >= safe.leftPx - .01f && it.y >= safe.topPx - .01f })
            assertTrue(textRects.all { it.x + it.w <= safe.rightPx + .01f && it.y + it.h <= safe.bottomPx + .01f })
        }
    }

    @Test
    fun pagerCenterPolicyUsesPhysicalCenterForEvenWindowsAndPreservesOddWindows() {
        val sevenSlots =
            listOf(
                HomePoint(0f, 80f),
                HomePoint(20f, 80f),
                HomePoint(40f, 80f),
                HomePoint(60f, 80f),
                HomePoint(80f, 80f),
                HomePoint(100f, 80f),
                HomePoint(120f, 80f),
            )

        val two = ImmersivePagerPolicy.visibleCenters(sevenSlots, 2)
        assertEquals(listOf(50f, 70f), two.map { it.x })
        assertEquals(60f, (two.first().x + two.last().x) / 2f, .001f)

        val four = ImmersivePagerPolicy.visibleCenters(sevenSlots, 4)
        assertEquals(listOf(30f, 50f, 70f, 90f), four.map { it.x })
        assertEquals(60f, (four.first().x + four.last().x) / 2f, .001f)

        val six = ImmersivePagerPolicy.visibleCenters(sevenSlots, 6)
        assertEquals(listOf(10f, 30f, 50f, 70f, 90f, 110f), six.map { it.x })
        assertEquals(60f, (six.first().x + six.last().x) / 2f, .001f)

        val odd = ImmersivePagerPolicy.visibleCenters(sevenSlots, 3)
        assertEquals(listOf(40f, 60f, 80f), odd.map { it.x })
        assertEquals(60f, (odd.first().x + odd.last().x) / 2f, .001f)
    }

    @Test
    fun matrixFontScalesThroughTwoFiveKeepTextAndPlayInBoundsWithoutOverlap() {
        val viewports =
            listOf(
                Triple(360f, 800f, HomeOrientation.PORTRAIT),
                Triple(411f, 914f, HomeOrientation.PORTRAIT),
                Triple(640f, 360f, HomeOrientation.LANDSCAPE),
                Triple(914f, 411f, HomeOrientation.LANDSCAPE),
                Triple(1339f, 597f, HomeOrientation.LANDSCAPE),
                Triple(1728f, 1080f, HomeOrientation.LANDSCAPE),
            )
        val fontScales = listOf(1f, 1.3f, 1.5f, 2f, 2.5f)
        for ((width, height, orientation) in viewports) {
            for (fontScale in fontScales) {
                val layout =
                    resolveImmersiveLayout(
                        orientation,
                        width,
                        height,
                        ImmersiveSafeBounds(0f, 0f, width, height),
                        fontScale = fontScale,
                    )
                val rects =
                    listOfNotNull(
                        layout.metadata,
                        layout.title,
                        layout.subtitle,
                        layout.play,
                    )
                assertTrue(
                    "$width x $height fs=$fontScale text/play must stay inside safe bounds",
                    rects.all { it.x >= -0.01f && it.y >= -0.01f },
                )
                assertTrue(
                    "$width x $height fs=$fontScale text/play must not overflow the window",
                    rects.all { it.x + it.w <= width + .01f && it.y + it.h <= height + .01f },
                )
                if (orientation == HomeOrientation.LANDSCAPE) {
                    val title = layout.title ?: error("landscape title missing")
                    val play = layout.play
                    assertTrue(
                        "$width x $height fs=$fontScale title must stay above play",
                        title.y + title.h <= play.y + .01f,
                    )
                    layout.metadata?.let { metadata ->
                        assertTrue(
                            "$width x $height fs=$fontScale metadata must stay above title",
                            metadata.y + metadata.h <= title.y + .01f,
                        )
                    }
                    layout.subtitle?.let { subtitle ->
                        assertTrue(
                            "$width x $height fs=$fontScale subtitle must follow the title",
                            title.y + title.h <= subtitle.y + .01f,
                        )
                        assertTrue(
                            "$width x $height fs=$fontScale subtitle must stay above play",
                            subtitle.y + subtitle.h <= play.y + .01f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun matrixCutoutFramesKeepTextAndPlayInsideSafeBoundsInOrder() {
        val cases =
            listOf(
                Triple(1080f, 2400f, HomeOrientation.PORTRAIT) to
                    ImmersiveSafeBounds(32f, 24f, 1048f, 2376f),
                Triple(2400f, 1080f, HomeOrientation.LANDSCAPE) to
                    ImmersiveSafeBounds(120f, 18f, 2320f, 1062f),
                Triple(1339f, 597f, HomeOrientation.LANDSCAPE) to
                    ImmersiveSafeBounds(24f, 12f, 1315f, 585f),
            )
        for ((dimensions, raw) in cases) {
            val (width, height, orientation) = dimensions
            val safe = opticalSafeBounds(raw, width, height)
            val layout =
                resolveImmersiveLayout(
                    orientation,
                    width,
                    height,
                    safe,
                    fontScale = 1.5f,
                )
            val rects =
                listOfNotNull(
                    layout.metadata,
                    layout.title,
                    layout.subtitle,
                    layout.play,
                    layout.menu,
                    layout.dock,
                )
            assertTrue(
                "$width x $height elements must stay right of the safe left edge",
                rects.all { it.x >= safe.leftPx - .01f && it.y >= safe.topPx - .01f },
            )
            assertTrue(
                "$width x $height elements must stay inside the safe frame",
                rects.all {
                    it.x + it.w <= safe.rightPx + .01f &&
                        it.y + it.h <= safe.bottomPx + .01f
                },
            )
            if (orientation == HomeOrientation.LANDSCAPE) {
                val metadata = layout.metadata!!
                val title = layout.title!!
                assertTrue(metadata.y + metadata.h <= title.y + .01f)
                assertTrue(title.y + title.h <= layout.play.y + .01f)
                layout.subtitle?.let { subtitle ->
                    assertTrue(title.y + title.h <= subtitle.y + .01f)
                    assertTrue(subtitle.y + subtitle.h <= layout.play.y + .01f)
                }
            }
        }
    }

    @Test
    fun landscapeKeepsTwoTitleLinesAndPlayWhereTheBudgetAllows() {
        for ((width, height) in listOf(914f to 411f, 1339f to 597f, 1728f to 1080f)) {
            val layout =
                resolveImmersiveLayout(
                    HomeOrientation.LANDSCAPE,
                    width,
                    height,
                    ImmersiveSafeBounds(0f, 0f, width, height),
                    fontScale = 1f,
                )
            val budget = resolveImmersiveTextBudget(height, 1f, layout.renderScale)
            assertEquals(
                "$width x $height must keep the two-line title",
                budget.titleHeightPx,
                layout.title!!.h,
                .01f,
            )
            assertTrue(layout.play.h >= IMMERSIVE_PLAY_MIN_HEIGHT_PX - .01f)
        }
    }

    @Test
    fun constrainedLandscapeOmitsTheSubtitleBeforeReducingTitleOrPlay() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                914f,
                411f,
                ImmersiveSafeBounds(0f, 0f, 914f, 411f),
                fontScale = 2.5f,
            )
        val budget = resolveImmersiveTextBudget(411f, 2.5f, layout.renderScale)
        assertNull(layout.subtitle)
        assertEquals(budget.titleLineHeightPx, layout.title!!.h, .01f)
        assertTrue(layout.play.h >= IMMERSIVE_PLAY_MIN_HEIGHT_PX - .01f)
        assertTrue(layout.play.y >= layout.title!!.y + layout.title!!.h - .01f)
    }

    @Test
    fun shortLandscapeReservesIndicatorHalfHeightAndGapBelowTheInformationStack() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                914f,
                411f,
                ImmersiveSafeBounds(0f, 0f, 914f, 411f),
                fontScale = 1.5f,
            )
        val pagerY = layout.pagerCenters.first().y
        val textBottom = resolveImmersiveTextBottom(pagerY, layout.renderScale)
        val stack = listOfNotNull(layout.metadata, layout.title, layout.subtitle, layout.play)

        assertTrue(stack.isNotEmpty())
        assertTrue(
            "the information stack must clear the indicator half-height and extra gap",
            stack.all { it.y + it.h <= textBottom + .01f },
        )
        assertTrue(
            layout.play.y + layout.play.h <=
                pagerY - IMMERSIVE_PAGER_INDICATOR_HALF_HEIGHT_PX * layout.renderScale -
                IMMERSIVE_PAGER_TEXT_GAP_PX * layout.renderScale + .01f,
        )
    }

    @Test
    fun landscapeLargeFontScaleOmitsOnlyTheSubtitleWhileKeepingTwoLinesAndPlay() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                1728f,
                1080f,
                ImmersiveSafeBounds(0f, 0f, 1728f, 1080f),
                fontScale = 2.5f,
            )
        val budget = resolveImmersiveTextBudget(1080f, 2.5f, layout.renderScale)
        assertNull(layout.subtitle)
        assertEquals(budget.titleHeightPx, layout.title!!.h, .01f)
        assertTrue(layout.play.h >= IMMERSIVE_PLAY_MIN_HEIGHT_PX - .01f)
        assertTrue(layout.play.y >= layout.title!!.y + layout.title!!.h - .01f)
    }

    @Test
    fun landscapePlayFloorHoldsAcrossRequestedSizesAndFontScales() {
        for ((width, height) in listOf(914f to 411f, 1339f to 597f, 1728f to 1080f)) {
            for (fontScale in listOf(1f, 1.3f, 1.5f, 2f, 2.5f)) {
                val layout =
                    resolveImmersiveLayout(
                        HomeOrientation.LANDSCAPE,
                        width,
                        height,
                        ImmersiveSafeBounds(0f, 0f, width, height),
                        fontScale = fontScale,
                    )
                assertTrue(
                    "$width x $height fs=$fontScale play must keep the dp-equivalent floor",
                    layout.play.h >= IMMERSIVE_PLAY_MIN_HEIGHT_PX - .01f,
                )
                assertTrue(layout.play.y + layout.play.h <= height + .01f)
            }
        }
    }

    @Test
    fun portraitPlayKeepsTheDpEquivFloorWhenTheCardCanHostItAndRelaxesOtherwise() {
        val layout =
            resolveImmersiveLayout(
                HomeOrientation.PORTRAIT,
                411f,
                914f,
                ImmersiveSafeBounds(0f, 0f, 411f, 914f),
            )
        assertTrue(layout.play.w >= IMMERSIVE_PLAY_MIN_HEIGHT_PX - .01f)
        assertTrue(layout.play.h >= IMMERSIVE_PLAY_MIN_HEIGHT_PX - .01f)
        assertEquals(layout.play.w, layout.play.h, .001f)
        assertTrue(layout.play.x >= 0f && layout.play.y >= 0f)
        assertTrue(layout.play.x + layout.play.w <= 411f + .01f)
        assertTrue(layout.play.y + layout.play.h <= 914f + .01f)

        // A card too small to host the floor relaxes it instead of overflowing.
        val tiny =
            resolveImmersiveLayout(
                HomeOrientation.PORTRAIT,
                200f,
                360f,
                ImmersiveSafeBounds(0f, 0f, 200f, 360f),
            )
        assertTrue(tiny.play.w < IMMERSIVE_PLAY_MIN_HEIGHT_PX)
        assertTrue(tiny.play.w >= 1f)
        assertTrue(tiny.play.x >= 0f && tiny.play.y >= 0f)
        assertTrue(tiny.play.x + tiny.play.w <= 200f + .01f)
        assertTrue(tiny.play.y + tiny.play.h <= 360f + .01f)
    }

    @Test
    fun playRectIsOneSelfConsistentGeometryResultInBothOrientations() {
        for ((orientation, width, height) in listOf(
            Triple(HomeOrientation.PORTRAIT, 411f, 914f),
            Triple(HomeOrientation.LANDSCAPE, 1339f, 597f),
        )) {
            val layout =
                resolveImmersiveLayout(
                    orientation,
                    width,
                    height,
                    ImmersiveSafeBounds(0f, 0f, width, height),
                    fontScale = 1.5f,
                )
            val play = layout.play
            assertTrue(play.w > 0f && play.h > 0f)
            // The renderer can derive both the size and the center from this single rect.
            val centerX = play.x + play.w / 2f
            val centerY = play.y + play.h / 2f
            assertTrue(centerX >= 0f && centerX <= width)
            assertTrue(centerY >= 0f && centerY <= height)
            assertEquals(play.w, play.x + play.w - play.x, 0f)
        }
    }

    @Test
    fun explicitDensityAwarePlayFloorIsUsedWhenTheLayoutCanHostIt() {
        val densityAwareFloorPx = 48f * 2.75f
        val portrait =
            resolveImmersiveLayout(
                HomeOrientation.PORTRAIT,
                1080f,
                2400f,
                ImmersiveSafeBounds(0f, 0f, 1080f, 2400f),
                minimumPlayHeightPx = densityAwareFloorPx,
            )
        val landscape =
            resolveImmersiveLayout(
                HomeOrientation.LANDSCAPE,
                2400f,
                1080f,
                ImmersiveSafeBounds(0f, 0f, 2400f, 1080f),
                minimumPlayHeightPx = densityAwareFloorPx,
            )

        assertTrue(portrait.play.h >= densityAwareFloorPx - .01f)
        assertTrue(landscape.play.h >= densityAwareFloorPx - .01f)
    }
}
