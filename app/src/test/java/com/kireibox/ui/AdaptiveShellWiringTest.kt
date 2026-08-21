package com.kireibox.ui

import java.io.File
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveShellWiringTest {
    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/com/kireibox/ui/KireiBoxApp.kt"),
            File("../app/src/main/java/com/kireibox/ui/KireiBoxApp.kt"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("KireiBoxApp.kt not found from ${File("").absolutePath}")
    }

    @Test
    fun rootResolvesSharedAdaptiveMetricsAndPassesItToAllPages() {
        val text = source()
        assertTrue(text.contains("resolveAdaptiveUiMetrics("))
        assertTrue(text.contains("adaptiveMetrics.libraryColumns"))
        assertTrue(text.contains("adaptiveMetrics.settingsContentWidthDp"))
        assertTrue(text.contains("adaptiveMetrics.bottomContentPaddingDp"))
        assertTrue(text.contains("KireiMainDock("))
    }

    @Test
    fun immersiveDockUsesThemeResolvedPaletteAndGridFadeIsDrawPhase() {
        val dock = Alpha11KireiBoxAppIntegration.dockHaze(
            isDark = true,
            reducedTransparency = false,
            hazeAvailable = true,
            apiSupportsHaze = true,
        )
        assertFalse("the normal dock path must use the sampled overlay", dock.useOpaqueFallback)
        assertEquals(
            dev.chrisbanes.haze.HazeInputScale.Fixed(.5f),
            dock.inputScale,
        )

        // The grid fade is represented by the existing policy stops and visibility gate. This
        // keeps the overlay fade testable without coupling it to a production local variable.
        assertTrue(LibraryGridTopFadePolicy.visible(firstVisibleItemIndex = 0, searchActive = false))
        assertFalse(LibraryGridTopFadePolicy.visible(firstVisibleItemIndex = 1, searchActive = false))
        assertFalse(LibraryGridTopFadePolicy.visible(firstVisibleItemIndex = 0, searchActive = true))
        assertEquals(listOf(0f, 72f), LibraryGridTopFadePolicy.stops.map { it.offsetDp })
        assertEquals(listOf(.45f, 0f), LibraryGridTopFadePolicy.stops.map { it.alpha })

        // A collapsed/non-overlay grid has no extra fade start; an expanded overlay derives its
        // start from measured header geometry rather than a hard-coded source marker.
        assertEquals(
            0f,
            LibraryCardLayoutPolicy.gridFadeStartDp(
                gridViewportTopDp = 20f,
                headerBottomDp = 92f,
                toolbarCollapsed = true,
            ),
            .001f,
        )
        assertEquals(
            72f,
            LibraryCardLayoutPolicy.gridFadeStartDp(
                gridViewportTopDp = 20f,
                headerBottomDp = 92f,
                toolbarCollapsed = false,
            ),
            .001f,
        )
    }

    @Test
    fun retiredCompactLandscapeAndFixedDockPaddingAreGone() {
        val text = source()
        assertFalse(text.contains("val compactLandscape = false"))
        assertFalse(text.contains("navigationBottom + 106.dp"))
    }

    @Test
    fun libraryGridUsesRenderedDockTopForFinalRowClearance() {
        val text = source()
        assertTrue(text.contains("resolveLibraryGridBottomContentPaddingDp("))
        assertTrue(text.contains("dockRect = adaptiveMetrics.dockRect"))
        assertTrue(text.contains("safeBottomDp = safeDrawingPadding.calculateBottomPadding().value"))
        // Final row clearance is maxOf(rendered dock padding, policy minimum).dp in one construct.
        val clearanceStart = text.indexOf("bottom = maxOf(")
        assertTrue("grid bottom must use maxOf(...).dp", clearanceStart >= 0)
        assertTrue(
            "maxOf must take the rendered dock padding",
            text.indexOf("bottomContentPadding.value,", clearanceStart) > clearanceStart,
        )
        assertTrue(
            "maxOf must take the policy minimum dock clearance",
            text.indexOf("LibraryCardLayoutPolicy.MIN_DOCK_CLEARANCE_DP,", clearanceStart) > clearanceStart,
        )
        assertTrue(
            "the max must stay in dp",
            text.indexOf(").dp,", clearanceStart) > clearanceStart,
        )
        assertTrue(
            "the dock clearance contract keeps the final row at least 8dp above the dock",
            LibraryCardLayoutPolicy.MIN_DOCK_CLEARANCE_DP >= 8f,
        )
    }

    @Test
    fun lowLandscapeDockUsesHorizontalPresentationThatFitsItsRect() {
        val presentation = resolveDockPresentation(320f, 44f)

        assertEquals(DockPresentationLayout.HORIZONTAL, presentation.layout)
        assertTrue(presentation.touchHeightDp >= 48f)
        assertTrue(presentation.contentHeightDp <= 44f)
        assertTrue(presentation.iconSizeDp + presentation.labelHeightDp + presentation.gapDp <= 44f)
    }

    @Test
    fun goldenHeightDockKeepsVerticalPresentation() {
        val presentation = resolveDockPresentation(320f, 100f)

        assertEquals(DockPresentationLayout.VERTICAL, presentation.layout)
        assertTrue(presentation.contentHeightDp <= 100f)
    }

    @Test
    fun sharedDockUsesRectAwarePresentationInsteadOfFixedNonCompactButton() {
        val text = source()
        assertTrue(text.contains("forceVertical = metrics.orientation == HomeOrientation.PORTRAIT"))
        assertTrue(text.contains("Arrangement.spacedBy(presentation.slotGapDp.dp)"))
        assertFalse(text.contains("DockButton(\"首页\", Icons.Rounded.Home, tab == KireiTab.HOME, colors, false)"))
    }

    @Test
    fun dockButtonsKeepFixedWidthsAndNeverOverflowTheirRect() {
        val widths = listOf(320f, 360f, 411f, 600f, 900f)
        val heights = listOf(48f, 64f, 100f)
        for (width in widths) {
            for (height in heights) {
                val presentation = resolveDockPresentation(width, height)
                val total = presentation.buttonWidthDp * 3f +
                    presentation.horizontalPaddingDp * 2f +
                    presentation.slotGapDp * 2f
                assertTrue(
                    "width=$width height=$height total=$total",
                    total <= width + .01f,
                )
                assertTrue(presentation.touchHeightDp >= 48f)
                assertTrue(presentation.buttonWidthDp >= 0f)
                assertTrue(presentation.contentHeightDp <= height + .01f)
            }
        }
        // Roomy rects keep the Alpha 8 fixed widths (82 vertical / 96 horizontal).
        val vertical = resolveDockPresentation(606f, 100f)
        assertEquals(82f, vertical.buttonWidthDp, .001f)
        val horizontal = resolveDockPresentation(340f, 44f)
        assertEquals(96f, horizontal.buttonWidthDp, .001f)
        // Tight rects shrink evenly instead of overflowing.
        val tiny = resolveDockPresentation(120f, 44f)
        assertTrue(
            tiny.buttonWidthDp * 3f + tiny.horizontalPaddingDp * 2f + tiny.slotGapDp * 2f <= 120.01f,
        )
    }

    @Test
    fun sharedPlaySizeResolverMatchesSpotlightRuleForBothHomeModes() {
        // SpotlightPage cell rule: width < 240 => 48dp; else height < 216 => 54dp; else 62dp.
        assertEquals(48f, SharedControlSizePolicy.playSizeDp(220f, 272f), .001f)
        assertEquals(54f, SharedControlSizePolicy.playSizeDp(300f, 200f), .001f)
        assertEquals(62f, SharedControlSizePolicy.playSizeDp(300f, 272f), .001f)
        // Immersive center card frame on a 411dp phone (~263.5 x ~434 dp) resolves through
        // the same resolver, so both modes agree on the same play size.
        assertEquals(62f, SharedControlSizePolicy.playSizeDp(263.5f, 434f), .001f)
        assertEquals(48f, SharedControlSizePolicy.playSizeDp(160f, 120f), .001f)
        // The narrow-width rule wins even when the cell is also short.
        assertEquals(48f, SharedControlSizePolicy.playSizeDp(200f, 200f), .001f)
    }

    @Test
    fun immersiveSharedSpecMirrorsComponentsControlSizes() {
        val sharedChrome = HomePresentationPolicy.topChromeSpec(
            HomeRect(0f, 0f, 411f, 900f),
            HomeSizeClass.COMPACT,
        )
        assertEquals(48f, sharedChrome.menuDiameterDp, .001f)
        assertEquals(HOME_MIN_TOUCH_TARGET_DP, sharedChrome.menuHitRect.w, .001f)
        assertEquals(34f, ImmersiveSharedSpec.brandFontSizeSp(HomeOrientation.PORTRAIT), .001f)
        assertEquals(18f, ImmersiveSharedSpec.brandFontSizeSp(HomeOrientation.LANDSCAPE), .001f)
        assertEquals(16f, ImmersiveSharedSpec.CARD_RADIUS_CENTER_DP, .001f)
        assertEquals(12f, ImmersiveSharedSpec.CARD_RADIUS_SIDE_DP, .001f)
        assertEquals(12f, ImmersiveSharedSpec.cardRadiusDp(0f), .001f)
        assertEquals(16f, ImmersiveSharedSpec.cardRadiusDp(1f), .001f)
        assertEquals(14f, ImmersiveSharedSpec.cardRadiusDp(.5f), .001f)
    }

    @Test
    fun dockGlassFallsBackToOpaqueWhenHazeUnavailableOrReducedTransparency() {
        assertTrue(DockGlassPolicy.useOpaqueFallback(reducedTransparency = false, hazeAvailable = false))
        assertTrue(DockGlassPolicy.useOpaqueFallback(reducedTransparency = true, hazeAvailable = true))
        assertFalse(DockGlassPolicy.useOpaqueFallback(reducedTransparency = false, hazeAvailable = true))
        assertEquals(null, DockGlassPolicy.tint(isDark = true, reducedTransparency = true, hazeAvailable = true))
        assertEquals(null, DockGlassPolicy.tint(isDark = false, reducedTransparency = false, hazeAvailable = false))
        assertNotNull(DockGlassPolicy.tint(isDark = true, reducedTransparency = false, hazeAvailable = true))
        // Opaque fallback keeps the clear reduced-transparency surface colors.
        assertEquals(
            Color(0xF20C101C),
            DockGlassPolicy.containerColor(
                isDark = true,
                pageBackground = Color.White,
                reducedTransparency = true,
                hazeAvailable = true,
            ),
        )
        assertEquals(
            Color(0xFFF4F2FA),
            DockGlassPolicy.containerColor(
                isDark = false,
                pageBackground = Color.Black,
                reducedTransparency = false,
                hazeAvailable = false,
            ),
        )
        // With Haze available the dock samples content behind a transparent base (Alpha 8).
        assertEquals(
            Color.Transparent,
            DockGlassPolicy.containerColor(
                isDark = true,
                pageBackground = Color.White,
                reducedTransparency = false,
                hazeAvailable = true,
            ),
        )
    }

    @Test
    fun lowLandscapeDockExpandsItsOuterRectBeforeDrawing() {
        val renderRect = resolveDockRenderRect(HomeRect(100f, 200f, 320f, 32f))

        assertEquals(48f, renderRect.h, 0.001f)
        assertEquals(192f, renderRect.y, 0.001f)
        assertTrue(resolveDockPresentation(renderRect.w, renderRect.h).contentHeightDp <= renderRect.h)
    }

    @Test
    fun dockGlassDisablesHazeNoiseAndKeepsASingleTintLayer() {
        val text = source()
        assertTrue("dock glass must set noiseFactor = 0", text.contains("noiseFactor = 0f"))
        assertTrue("dock glass keeps one tint layer", text.contains("tints = listOf(dev.chrisbanes.haze.HazeTint(tint ?: base))"))
    }

    @Test
    fun overlaysReuseTheRootSystemBarIconToneInsteadOfTheirTheme() {
        val text = source()
        assertTrue("root tone local must exist", text.contains("LocalSystemBarIconTone"))
        assertTrue(
            "modal overlay must apply the root light-icons tone",
            text.contains("isAppearanceLightStatusBars = !lightIcons"),
        )
        assertTrue(
            "modal overlay must apply the root navigation tone",
            text.contains("isAppearanceLightNavigationBars = !lightIcons"),
        )
    }

    @Test
    fun modalSystemBarsConsumeTheFourWayClassAndReapplyOnTransitions() {
        val text = source()
        val start = text.indexOf("private fun ModalSystemBarVisibilityEffect")
        val end = text.indexOf("\n@Composable\nprivate fun LibraryOverviewSheet", start)
        assertTrue("modal system-bar effect must be present", start >= 0 && end > start)
        val modal = text.substring(start, end)

        assertTrue(modal.contains("ResponsiveLayoutPolicy.resolveWindowClass("))
        assertTrue(modal.contains("SystemBarVisibilityPolicy.shouldHideStatusBar(windowClass)"))
        assertFalse(modal.contains("resolveLayoutMode("))
        assertFalse(modal.contains("configuration.orientation"))
        assertTrue(modal.contains("DisposableEffect(view, windowClass, hideStatusBar, lightIcons, isDark)"))
        assertTrue(modal.contains("LaunchedEffect(view, windowClass, hideStatusBar, lightIcons, isDark)"))
        assertTrue(modal.contains("show(WindowInsetsCompat.Type.navigationBars())"))
    }

    @Test
    fun landscapeLibraryUsesFixedToolbarLaneAndSingleMeasuredGap() {
        val text = source()
        assertTrue(
            "overlay toolbar lane must be the fixed policy height",
            text.contains(".height(LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP.dp)"),
        )
        assertTrue(
            "compact landscape grid top must be derived by LibraryCardLayoutPolicy",
            text.contains("val compactGridTopPaddingDp = LibraryCardLayoutPolicy.gridTopPaddingDp("),
        )
        assertTrue(
            "the derived top must be wired as the grid content top",
            text.contains("contentTopDp = compactGridTopPaddingDp"),
        )
        assertTrue(
            "the measured header bottom must include the grid header's measured title/actions gap",
            text.contains("val resolvedLibraryHeaderBottomDp = LibraryCardLayoutPolicy.resolvedHeaderBottomDp(") &&
                text.contains("measuredLibraryTitleBounds?.bottomDp") &&
                text.contains("measuredLibraryActionsBounds?.bottomDp"),
        )
        assertFalse(
            "the retired hardcoded first-row top must not be assigned directly",
            text.contains("contentTopDp = LibraryHeaderTransitionPolicy.firstRowTopDp()"),
        )
        assertTrue(
            "the top fade must not consume pointer input",
            !text.substring(
                text.indexOf("private fun LibraryGameGrid"),
                text.indexOf("private fun LibraryPage"),
            ).contains("pointerInput"),
        )
    }

    @Test
    fun libraryGridBranchesAreExclusiveSoLandscapeCannotComposeTwoGrids() {
        val text = source()
        val libraryPage = text.substring(
            text.indexOf("private fun LibraryPage"),
            text.indexOf("private fun CoverTaskToolbarAnchor"),
        )
        val layoutBranches = libraryPage.substring(
            libraryPage.indexOf("val gridColumns = GridCells.Fixed"),
            libraryPage.indexOf("if (state.taskCenterOpen && CoverTaskPresentationPolicy.containerFor"),
        )
        val regularBranchMarker = "\n        } else {\n            Row(Modifier.fillMaxWidth()"
        val regularBranchStart = layoutBranches.indexOf(regularBranchMarker)
        val firstGrid = layoutBranches.indexOf("LibraryGameGrid(")
        val regularGrid = layoutBranches.lastIndexOf("LibraryGameGrid(")

        assertEquals(
            "LibraryPage keeps one grid implementation per mutually exclusive layout branch",
            2,
            Regex("LibraryGameGrid\\(").findAll(layoutBranches).count(),
        )
        assertTrue(
            "the narrow grid must be composed before the layout-level else branch",
            firstGrid in 0 until regularBranchStart,
        )
        assertTrue(
            "the regular grid must stay inside the layout-level else branch",
            regularBranchStart >= 0 && regularGrid > regularBranchStart,
        )
    }

    @Test
    fun wideCoverTaskCenterUsesFixedPopupInsteadOfIntrinsicDropdownMenu() {
        val text = source()
        val taskToolbarAnchor = text.substring(
            text.indexOf("private fun CoverTaskToolbarAnchor"),
            text.indexOf("private fun CoverTaskButton"),
        )

        assertTrue(
            "wide task center must use the fixed popup host",
            taskToolbarAnchor.contains("CoverTaskPopup("),
        )
        assertFalse(
            "DropdownMenu adds IntrinsicSize.Max around the nested LazyColumn and crashes",
            taskToolbarAnchor.contains("DropdownMenu("),
        )
    }

    @Test
    fun gameLibraryRequestsDedicatedSharpCoverRole() {
        val text = source()
        val gameCard = text.substring(
            text.indexOf("private fun GameCard"),
            text.indexOf("private fun SettingsPage"),
        )

        assertTrue(
            "library cards must not reuse the fixed 256x384 thumbnail role",
            gameCard.contains("role = CoverImageRole.LIBRARY_CARD"),
        )
    }

    @Test
    fun settingsRootUsesCompactLandscapeRowsSoFourCategoriesClearTheDock() {
        val landscape = resolveSettingsCategoryRowMetrics(HomeOrientation.LANDSCAPE)
        val portrait = resolveSettingsCategoryRowMetrics(HomeOrientation.PORTRAIT)

        assertTrue(landscape.verticalPaddingDp < portrait.verticalPaddingDp)
        assertTrue(landscape.minimumHeightDp < portrait.minimumHeightDp)
        assertTrue(landscape.iconSizeDp < portrait.iconSizeDp)
        assertTrue(source().contains("adaptiveMetrics.orientation,"))
        assertTrue(source().contains("bottom = adaptiveMetrics.bottomContentPaddingDp.dp"))
    }

    @Test
    fun componentPagesUseTheSameOpticalContentFrameForCutoutSymmetry() {
        val text = source()
        assertTrue(text.contains("adaptiveMetrics.contentFrame.x.dp"))
        assertTrue(text.contains("adaptiveMetrics.contentFrame.w"))
        assertTrue(text.contains("GridCells.Fixed(adaptiveMetrics.libraryColumns)"))
        assertTrue(text.contains("GameCard("))
    }

    @Test
    fun settingsPaddingIsDerivedFromAdaptiveDockMetrics() {
        val metrics = resolveAdaptiveUiMetrics(
            windowWidthDp = 914f,
            windowHeightDp = 411f,
            safeLeftDp = 0f,
            safeTopDp = 0f,
            safeRightDp = 0f,
            safeBottomDp = 0f,
        )

        // Alpha 10 review: padding clears the dock so the final row can scroll above it.
        assertTrue(metrics.bottomContentPaddingDp >= metrics.dockRect.h)
        assertFalse(source().contains("navigationBottom + 106.dp"))
        assertFalse(source().contains("navigationBottom + 106"))
    }

}
