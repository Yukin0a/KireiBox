package com.kireibox.ui

import com.yuki.yukihub.settings.HomePresentationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePresentationPolicyTest {
    @Test
    fun immersivePortraitUsesPortraitImmersiveRenderKind() {
        assertEquals(
            HomeRenderKind.IMMERSIVE_PORTRAIT,
            HomePresentationPolicy.renderKind(HomePresentationMode.IMMERSIVE, 852f, 1846f, HomeSizeClass.COMPACT),
        )
    }

    @Test
    fun immersiveLandscapeUsesLandscapeImmersiveRenderKind() {
        assertEquals(
            HomeRenderKind.IMMERSIVE_LANDSCAPE,
            HomePresentationPolicy.renderKind(HomePresentationMode.IMMERSIVE, 1672f, 941f, HomeSizeClass.WIDE),
        )
    }

    @Test
    fun componentsPortraitUsesPortraitComponentsRenderKind() {
        assertEquals(
            HomeRenderKind.COMPONENTS_PORTRAIT,
            HomePresentationPolicy.renderKind(HomePresentationMode.COMPONENTS, 852f, 1846f, HomeSizeClass.WIDE),
        )
    }

    @Test
    fun componentsLandscapeUsesLandscapeComponentsRenderKind() {
        assertEquals(
            HomeRenderKind.COMPONENTS_LANDSCAPE,
            HomePresentationPolicy.renderKind(HomePresentationMode.COMPONENTS, 1672f, 941f, HomeSizeClass.COMPACT),
        )
    }

    @Test
    fun emptyOrUnknownStoredModeFallsBackToImmersive() {
        assertEquals(
            HomeRenderKind.IMMERSIVE_PORTRAIT,
            HomePresentationPolicy.renderStoredKind(null, 852f, 1846f, HomeSizeClass.COMPACT),
        )
        assertEquals(
            HomeRenderKind.IMMERSIVE_LANDSCAPE,
            HomePresentationPolicy.renderStoredKind("not-a-mode", 1672f, 941f, HomeSizeClass.WIDE),
        )
    }

    @Test
    fun sizeClassDoesNotChangeRenderKindWithinAnOrientation() {
        assertEquals(
            HomePresentationPolicy.renderKind(HomePresentationMode.IMMERSIVE, 852f, 1846f, HomeSizeClass.COMPACT),
            HomePresentationPolicy.renderKind(HomePresentationMode.IMMERSIVE, 852f, 1846f, HomeSizeClass.WIDE),
        )
        assertEquals(
            HomePresentationPolicy.renderKind(HomePresentationMode.COMPONENTS, 1672f, 941f, HomeSizeClass.COMPACT),
            HomePresentationPolicy.renderKind(HomePresentationMode.COMPONENTS, 1672f, 941f, HomeSizeClass.WIDE),
        )
    }

    @Test
    fun emptyGalleryHasNoCardAssignments() {
        assertEquals(emptyList<HomeCardAssignment>(), HomePresentationPolicy.cardAssignments(0, 0, HomeOrientation.PORTRAIT))
        assertEquals(emptyList<HomeCardAssignment>(), HomePresentationPolicy.cardAssignments(0, 0, HomeOrientation.LANDSCAPE))
    }

    @Test
    fun oneGameUsesOnlyTheCenterAssignment() {
        val assignments = HomePresentationPolicy.cardAssignments(1, 99, HomeOrientation.LANDSCAPE)
        assertEquals(listOf(HomeCardAssignment(0, HomeCardSlot.CENTER, HomeCardDepth.FRONT)), assignments)
    }

    @Test
    fun portraitAssignmentsKeepFrontAndRearIndicesUniqueAndClickable() {
        val assignments = HomePresentationPolicy.cardAssignments(4, 2, HomeOrientation.PORTRAIT)
        assertEquals(listOf(2, 1, 3, 0), assignments.map { it.gameIndex })
        assertEquals(HomeCardDepth.FRONT, assignments.first().depth)
        assertEquals(listOf(HomeCardDepth.REAR, HomeCardDepth.REAR, HomeCardDepth.REAR), assignments.drop(1).map { it.depth })
        assertEquals(assignments.size, assignments.map { it.gameIndex }.toSet().size)
        assertEquals(setOf(0, 1, 2, 3), assignments.map { it.gameIndex }.toSet())
    }

    @Test
    fun twoThroughFourGamesAreAssignedWithoutDuplication() {
        for (count in 2..4) {
            for (orientation in HomeOrientation.entries) {
                val assignments = HomePresentationPolicy.cardAssignments(count, count - 1, orientation)
                assertEquals(assignments.size, assignments.map { it.gameIndex }.toSet().size)
                if (orientation == HomeOrientation.LANDSCAPE) {
                    assertEquals(count - 1, assignments.single { it.slot == HomeCardSlot.CENTER }.gameIndex)
                }
            }
        }
    }

    @Test
    fun landscapeSelectedGameIsAlwaysCenterAfterClamping() {
        for (selected in listOf(-2, 0, 2, 99)) {
            val assignments = HomePresentationPolicy.cardAssignments(6, selected, HomeOrientation.LANDSCAPE)
            val center = assignments.single { it.slot == HomeCardSlot.CENTER }
            assertEquals(selected.coerceIn(0, 5), center.gameIndex)
            assertEquals(HomeCardDepth.FRONT, center.depth)
            assertEquals(assignments.size, assignments.map { it.gameIndex }.toSet().size)
            assertEquals(
                true,
                assignments.filter { it.slot != HomeCardSlot.CENTER }.all {
                    it.slot in setOf(HomeCardSlot.NEAR_LEFT, HomeCardSlot.NEAR_RIGHT, HomeCardSlot.FAR_LEFT, HomeCardSlot.FAR_RIGHT)
                },
            )
        }
    }

    @Test
    fun landscapeBoundariesUsePrevNextNeighborsInStableVisualSlotOrder() {
        for (count in listOf(2, 3, 4, 5, 6, 8)) {
            val selections = listOf(0, count / 2, count - 1).distinct()
            for (selected in selections) {
                val assignments = HomePresentationPolicy.cardAssignments(count, selected, HomeOrientation.LANDSCAPE)
                assertEquals(assignments.sortedBy { it.slot.ordinal }, assignments)
                assertEquals(assignments.size, assignments.map { it.gameIndex }.toSet().size)
                assertEquals(selected, assignments.single { it.slot == HomeCardSlot.CENTER }.gameIndex)
                assertEquals(HomeCardDepth.FRONT, assignments.single { it.slot == HomeCardSlot.CENTER }.depth)
                val expected = linkedMapOf(
                    HomeCardSlot.NEAR_LEFT to (selected - 1).takeIf { it >= 0 },
                    HomeCardSlot.FAR_LEFT to (selected - 2).takeIf { it >= 0 },
                    HomeCardSlot.NEAR_RIGHT to (selected + 1).takeIf { it < count },
                    HomeCardSlot.FAR_RIGHT to (selected + 2).takeIf { it < count },
                )
                expected.forEach { (slot, gameIndex) ->
                    val assignment = assignments.firstOrNull { it.slot == slot }
                    if (gameIndex == null) {
                        assertEquals(null, assignment)
                    } else {
                        assertEquals(gameIndex, assignment?.gameIndex)
                    }
                }
            }
        }
    }

    @Test
    fun sixGamesKeepFiveVisibleAssignmentsAndSixPaginationBars() {
        val assignments = HomePresentationPolicy.cardAssignments(6, 3, HomeOrientation.LANDSCAPE)
        val indicator = HomeGalleryGeometryPolicy.indicator(6, 3, HomeOrientation.LANDSCAPE)
        assertEquals(5, assignments.size)
        assertEquals(6, indicator.visibleCount)
        assertEquals(3, indicator.selectedIndex)
        assertEquals(3, assignments.single { it.slot == HomeCardSlot.CENTER }.gameIndex)
    }

    @Test
    fun globalDockAlwaysVisibleForEveryTabAndMode() {
        org.junit.Assert.assertTrue(
            HomePresentationPolicy.shouldShowGlobalDock(KireiTab.HOME, HomePresentationMode.IMMERSIVE),
        )
        org.junit.Assert.assertTrue(
            HomePresentationPolicy.shouldShowGlobalDock(KireiTab.HOME, HomePresentationMode.COMPONENTS),
        )
        org.junit.Assert.assertTrue(
            HomePresentationPolicy.shouldShowGlobalDock(KireiTab.HOME, null),
        )
        org.junit.Assert.assertTrue(
            HomePresentationPolicy.shouldShowGlobalDock(KireiTab.GAMES, HomePresentationMode.IMMERSIVE),
        )
        org.junit.Assert.assertTrue(
            HomePresentationPolicy.shouldShowGlobalDock(KireiTab.SETTINGS, HomePresentationMode.IMMERSIVE),
        )
    }

    @Test
    fun safeWindowDimensionsDriveRenderKindNotFullFrame() {
        assertEquals(
            HomeRenderKind.IMMERSIVE_PORTRAIT,
            HomePresentationPolicy.renderKindForSafeArea(
                mode = HomePresentationMode.IMMERSIVE,
                fullWidthPx = 1672f,
                fullHeightPx = 941f,
                safeLeftPx = 600f,
                safeTopPx = 60f,
                safeRightPx = 600f,
                safeBottomPx = 60f,
                sizeClass = HomeSizeClass.WIDE,
            ),
        )
    }

    @Test
    fun safeAreaWithoutInsetsStillUsesFullFrameOrientation() {
        assertEquals(
            HomeRenderKind.IMMERSIVE_LANDSCAPE,
            HomePresentationPolicy.renderKindForSafeArea(
                mode = HomePresentationMode.IMMERSIVE,
                fullWidthPx = 1672f,
                fullHeightPx = 941f,
                safeLeftPx = 0f,
                safeTopPx = 0f,
                safeRightPx = 0f,
                safeBottomPx = 0f,
                sizeClass = HomeSizeClass.WIDE,
            ),
        )
    }

    @Test
    fun topChromeUsesTheSafeFrameAndKeepsMenuHitTargetAtLeast48Dp() {
        val safeFrame = HomeRect(x = 24f, y = 30f, w = 864f, h = 351f)
        val spec = HomePresentationPolicy.topChromeSpec(safeFrame, HomeSizeClass.COMPACT)

        assertEquals(40f, spec.titleRect.x, .001f)
        assertEquals(38f, spec.titleRect.y, .001f)
        assertEquals(22f, spec.titleSizeDp, .001f)
        assertEquals(48f, spec.menuDiameterDp, .001f)
        assertEquals(22f, spec.menuIconSizeDp, .001f)
        assertEquals(48f, spec.menuHitRect.w, .001f)
        assertEquals(48f, spec.menuHitRect.h, .001f)
        assertEquals(spec.menuRect.x, spec.menuHitRect.x, .001f)
        assertEquals(spec.menuRect.y, spec.menuHitRect.y, .001f)
        assertEquals(safeFrame.x + safeFrame.w - 16f - 48f, spec.menuRect.x, .001f)
    }

    @Test
    fun identicalSafeFramesProduceIdenticalChromeAcrossPresentationModes() {
        val safeFrame = HomeRect(x = 18f, y = 24f, w = 375f, h = 881f)
        val immersive = HomePresentationPolicy.resolvePresentation(
            mode = HomePresentationMode.IMMERSIVE,
            safeFrame = safeFrame,
            sizeClass = HomeSizeClass.COMPACT,
        )
        val components = HomePresentationPolicy.resolvePresentation(
            mode = HomePresentationMode.COMPONENTS,
            safeFrame = safeFrame,
            sizeClass = HomeSizeClass.COMPACT,
        )

        assertEquals(immersive.topChrome, components.topChrome)
        assertEquals(HomePresentationMode.IMMERSIVE, immersive.mode)
        assertEquals(HomePresentationMode.COMPONENTS, components.mode)
    }

    @Test
    fun sizeClassChangesChromeDensityButNeverChangesSelectedMode() {
        val safeFrame = HomeRect(x = 50f, y = 0f, w = 812f, h = 402f)
        val compact = HomePresentationPolicy.resolvePresentation(
            mode = HomePresentationMode.COMPONENTS,
            safeFrame = safeFrame,
            sizeClass = HomeSizeClass.COMPACT,
        )
        val wide = HomePresentationPolicy.resolvePresentation(
            mode = HomePresentationMode.COMPONENTS,
            safeFrame = safeFrame,
            sizeClass = HomeSizeClass.WIDE,
        )

        assertEquals(HomePresentationMode.COMPONENTS, compact.mode)
        assertEquals(HomePresentationMode.COMPONENTS, wide.mode)
        assertEquals(HomeContentDensity.COMPACT, compact.contentDensity)
        assertEquals(HomeContentDensity.COMPACT_HEIGHT, wide.contentDensity)
        assertTrue(wide.topChrome.titleSizeDp > compact.topChrome.titleSizeDp)
        assertTrue(wide.topChrome.menuDiameterDp > compact.topChrome.menuDiameterDp)
    }

    @Test
    fun narrowSafeFramesKeepChromeInsideAndUseTheLargestPhysicalHitTarget() {
        listOf(24f, 32f, 48f).forEach { widthDp ->
            val safeFrame = HomeRect(x = 0f, y = 12f, w = widthDp, h = 96f)
            val spec = HomePresentationPolicy.topChromeSpec(safeFrame, HomeSizeClass.COMPACT)
            val expectedHitSize = minOf(widthDp, safeFrame.h)

            assertTrue("menu x for $widthDp", spec.menuRect.x >= safeFrame.x)
            assertTrue("menu right for $widthDp", spec.menuRect.x + spec.menuRect.w <= safeFrame.x + safeFrame.w)
            assertTrue("menu y for $widthDp", spec.menuRect.y >= safeFrame.y)
            assertTrue("menu bottom for $widthDp", spec.menuRect.y + spec.menuRect.h <= safeFrame.y + safeFrame.h)
            assertTrue("hit x for $widthDp", spec.menuHitRect.x >= safeFrame.x)
            assertTrue("hit right for $widthDp", spec.menuHitRect.x + spec.menuHitRect.w <= safeFrame.x + safeFrame.w)
            assertTrue("hit y for $widthDp", spec.menuHitRect.y >= safeFrame.y)
            assertTrue("hit bottom for $widthDp", spec.menuHitRect.y + spec.menuHitRect.h <= safeFrame.y + safeFrame.h)
            assertEquals(expectedHitSize, spec.menuHitRect.w, .001f)
            assertEquals(expectedHitSize, spec.menuHitRect.h, .001f)
            assertTrue("title x for $widthDp", spec.titleRect.x >= safeFrame.x)
            assertTrue("title right for $widthDp", spec.titleRect.x + spec.titleRect.w <= safeFrame.x + safeFrame.w)
        }
    }
}
