package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveShellWiringTest {
    private fun source(): String {
        return UiSourceFiles.all()
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
    fun dockUsesThemeResolvedPaletteAndTheSampledOverlay() {
        val dock =
            Alpha11KireiBoxAppIntegration.dockHaze(
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
        // Whitespace-tolerant so ktlint's argument wrapping cannot decouple the arguments.
        assertTrue(
            "grid bottom must use maxOf(rendered dock padding, policy minimum).dp",
            text.contains(
                Regex(
                    "bottom\\s*=\\s*maxOf\\(\\s*" +
                        "bottomContentPadding\\.value,\\s*" +
                        "LibraryCardLayoutPolicy\\.MIN_DOCK_CLEARANCE_DP,?\\s*\\)\\.dp",
                ),
            ),
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
                val total =
                    presentation.buttonWidthDp * 3f +
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
        val sharedChrome =
            HomePresentationPolicy.topChromeSpec(
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
    fun lowLandscapeDockExpandsItsOuterRectBeforeDrawing() {
        val renderRect = resolveDockRenderRect(HomeRect(100f, 200f, 320f, 32f))

        assertEquals(48f, renderRect.h, 0.001f)
        assertEquals(192f, renderRect.y, 0.001f)
        assertTrue(resolveDockPresentation(renderRect.w, renderRect.h).contentHeightDp <= renderRect.h)
    }

    @Test
    fun everyTabDrawsTheSameDockAtTheMetricsRectAndTheContentClearanceFollowsIt() {
        // r5: one Dock rect per window. The shell draws it at the measured position on every tab (no per-tab
        // offset, no glide), and every page that reserves room for it reads the same metrics.
        val app = UiSourceFiles.file("KireiBoxApp.kt")
        assertTrue(app.contains("metrics = adaptiveMetrics,"))
        assertTrue(app.contains("modifier = Modifier.align(Alignment.BottomCenter)\n"))
        assertFalse("no per-tab Dock offset", app.contains("immersiveDockDrop") || app.contains("rememberImmersiveDockDrop"))
        assertTrue("components home and settings keep the metrics clearance", app.contains("val responsiveDockContentPadding = adaptiveMetrics.bottomContentPaddingDp.dp"))
        assertTrue("the library clearance is measured from the drawn Dock", app.contains("dockRect = adaptiveMetrics.dockRect,"))
        assertTrue(UiSourceFiles.file("KireiCommonUi.kt").contains("resolveDockRenderRect(metrics.dockRect)"))
        assertTrue(UiSourceFiles.file("SettingsScaffold.kt").contains("bottom = adaptiveMetrics.bottomContentPaddingDp.dp"))
        // The immersive layout is solved against that same rect.
        assertTrue(UiSourceFiles.file("ImmersiveHome.kt").contains("metrics.dockRect.toPx(density)"))

        // Landscape: 12dp above the 24dp gesture area on every page; portrait keeps its fixed 16dp gap.
        val landscape = resolveAdaptiveUiMetrics(914f, 411f, 0f, 0f, 0f, 24f)
        assertEquals(411f - 24f - AdaptiveLayoutConstants.DOCK_LANDSCAPE_BOTTOM_GAP_DP, landscape.dockRect.y + landscape.dockRect.h, .01f)
        val portrait = resolveAdaptiveUiMetrics(411f, 914f, 0f, 24f, 0f, 24f)
        assertEquals(914f - 24f - AdaptiveLayoutConstants.DOCK_PORTRAIT_BOTTOM_GAP_DP, portrait.dockRect.y + portrait.dockRect.h, .01f)
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
        val start = text.indexOf("fun ModalSystemBarVisibilityEffect(")
        val end = text.indexOf("\n@Composable\ninternal fun LibraryOverviewSheet(", start)
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
    fun libraryBarSitsOverTheGridAndLandscapeGridClearsIt() {
        val page = UiSourceFiles.file("LibraryPages.kt")
        assertTrue(
            "the landscape grid top is derived from the bar's bottom edge by LibraryCardLayoutPolicy",
            page.contains(Regex("val landscapeGridTopDp\\s*=\\s*LibraryCardLayoutPolicy\\.gridTopPaddingDp\\(")) &&
                page.contains(Regex("val resolvedLibraryHeaderBottomDp\\s*=\\s*LibraryCardLayoutPolicy\\.resolvedHeaderBottomDp\\(")) &&
                page.contains("toolbarHeightDp = LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP"),
        )
        assertTrue(
            "the derived top is wired as the grid content top (portrait starts at the safe top: the header block scrolls under the bar)",
            page.contains("contentTopDp = if (landscape) landscapeGridTopDp else safeDrawingTop.value"),
        )
        assertTrue("the grid fills the window and is the bar's haze source", page.contains("Modifier.fillMaxSize().then(if (barHaze != null) Modifier.hazeSource(barHaze) else Modifier)"))
        assertTrue("one glass bar is drawn over the grid", page.contains("LibraryTopBar("))
        assertFalse("the landscape toolbar no longer fades away on scroll", UiSourceFiles.file("LibraryHeaders.kt").contains("toolbarAlpha"))
        assertFalse("the retired hardcoded first-row top must not be assigned directly", page.contains("firstRowTopDp"))
        assertTrue(
            "the grid must not consume pointer input",
            // LibraryGameGrid lives in LibraryGrid.kt; read that file directly so the slice cannot
            // pick up LibraryPage's imports from the concatenated package.
            !UiSourceFiles.file("LibraryGrid.kt").contains("pointerInput"),
        )
    }

    @Test
    fun libraryPageComposesOneGridForBothOrientations() {
        val libraryPage = UiSourceFiles.file("LibraryPages.kt")
        require(libraryPage.contains("fun LibraryPage(")) { "LibraryPage missing" }

        assertEquals(
            "LibraryPage keeps one grid: landscape just leaves the portrait header block out",
            1,
            Regex("LibraryGameGrid\\(").findAll(libraryPage).count(),
        )
        assertTrue(libraryPage.contains("header = portraitHeader.takeUnless { landscape }"))
        assertTrue("the grid keeps the same Dock clearance in both orientations", libraryPage.contains("bottomContentPadding = bottomContentPadding"))
    }

    @Test
    fun wideCoverTaskCenterUsesFixedPopupInsteadOfIntrinsicDropdownMenu() {
        val text = source()
        val taskToolbarAnchor =
            text.substring(
                text.indexOf("fun CoverTaskToolbarAnchor("),
                text.indexOf("fun CoverTaskButton("),
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
        val gameCardStart = text.indexOf("fun GameCard(")
        val gameCardEnd = text.indexOf("\n@Composable", gameCardStart + 1).let { if (it < 0) text.length else it }
        val gameCard = text.substring(gameCardStart, gameCardEnd)

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
        val metrics =
            resolveAdaptiveUiMetrics(
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
