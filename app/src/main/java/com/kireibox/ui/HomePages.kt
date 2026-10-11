package com.kireibox.ui
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.MotionMode
import dev.chrisbanes.haze.HazeState

// Home surfaces: components home, dashboard, spotlight and focus gallery.

@Composable
internal fun HomePage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    layoutMode: KireiLayoutMode,
    safeDrawingPadding: PaddingValues,
    bottomContentPadding: Dp,
    navigationBottom: Dp,
    statusBarBottomDp: Dp,
    displayCutout: HeaderRectDp?,
    adaptiveMetrics: AdaptiveUiMetrics,
    windowClass: ResponsiveWindowClass,
    currentSpotlightPage: Int,
    onSpotlightPageChanged: (Int) -> Unit,
    onOpenOverview: () -> Unit,
    onEditDashboard: (DashboardProfile) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val kind =
            HomePresentationPolicy.renderKindForSafeArea(
                mode = state.settings?.homePresentationMode,
                fullWidthPx = with(density) { maxWidth.toPx() },
                fullHeightPx = with(density) { maxHeight.toPx() },
                safeLeftPx = with(density) { safeDrawingPadding.calculateLeftPadding(layoutDirection).toPx() },
                safeTopPx = with(density) { safeDrawingPadding.calculateTopPadding().toPx() },
                safeRightPx = with(density) { safeDrawingPadding.calculateRightPadding(layoutDirection).toPx() },
                safeBottomPx = with(density) { safeDrawingPadding.calculateBottomPadding().toPx() },
                sizeClass = windowClass.homeSizeClass(),
            )
        val profile = ResponsiveLayoutPolicy.dashboardPresentation(layoutMode, maxHeight.value).profile
        if (kind == HomeRenderKind.IMMERSIVE_PORTRAIT || kind == HomeRenderKind.IMMERSIVE_LANDSCAPE) {
            ImmersiveHome(
                games = LibraryOverviewPolicy.sorted(state.games, LibraryOverviewSort.RECENT),
                colors = colors,
                actions = actions,
                haze = haze,
                renderKind = kind,
                galleryMode = state.immersiveGalleryMode,
                manualIds = state.immersiveGalleryIds,
                source = state.immersiveGallerySource,
                strategy = state.immersiveGalleryStrategy,
                count = state.immersiveGalleryCount,
                onGalleryModeChange = viewModel::setImmersiveGalleryMode,
                onGalleryIdsChange = viewModel::setImmersiveGalleryIds,
                onGallerySourceChange = viewModel::setImmersiveGallerySource,
                onGalleryStrategyChange = viewModel::setImmersiveGalleryStrategy,
                onGalleryCountChange = viewModel::setImmersiveGalleryCount,
                safeBounds =
                    ImmersiveSafeBounds(
                        leftPx = with(density) { safeDrawingPadding.calculateLeftPadding(layoutDirection).toPx() },
                        topPx = with(density) { safeDrawingPadding.calculateTopPadding().toPx() },
                        rightPx =
                            with(density) {
                                maxWidth.toPx() - safeDrawingPadding.calculateRightPadding(layoutDirection).toPx()
                            },
                        bottomPx =
                            with(density) {
                                maxHeight.toPx() - safeDrawingPadding.calculateBottomPadding().toPx()
                            },
                    ),
                reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED,
                showPlayTime = state.settings?.immersivePlayTimeVisible != false,
                onOpenPlayTime = onOpenOverview,
                reducedTransparency = state.settings?.reducedTransparency == true,
                adaptiveMetrics = adaptiveMetrics,
            )
        } else {
            ComponentsHomePage(
                state = state,
                colors = colors,
                haze = haze,
                viewModel = viewModel,
                actions = actions,
                layoutMode = layoutMode,
                safeDrawingPadding = safeDrawingPadding,
                bottomContentPadding = bottomContentPadding,
                adaptiveMetrics = adaptiveMetrics,
                navigationBottom = navigationBottom,
                statusBarBottomDp = statusBarBottomDp,
                displayCutout = displayCutout,
                currentSpotlightPage = currentSpotlightPage,
                windowClass = windowClass,
                onSpotlightPageChanged = onSpotlightPageChanged,
                onOpenOverview = onOpenOverview,
                onEditDashboard = onEditDashboard,
            )
        }
    }
}

@Composable
internal fun ComponentsHomePage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    layoutMode: KireiLayoutMode,
    safeDrawingPadding: PaddingValues,
    bottomContentPadding: Dp,
    navigationBottom: Dp,
    statusBarBottomDp: Dp,
    displayCutout: HeaderRectDp?,
    adaptiveMetrics: AdaptiveUiMetrics,
    windowClass: ResponsiveWindowClass,
    currentSpotlightPage: Int,
    onSpotlightPageChanged: (Int) -> Unit,
    onOpenOverview: () -> Unit,
    onEditDashboard: (DashboardProfile) -> Unit,
) {
    val recent =
        LibraryOverviewPolicy.sorted(state.games, LibraryOverviewSort.RECENT)
            .filter { it.lastPlayedAt > 0L }
    var menuExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = menuExpanded) { menuExpanded = false }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val homeWindowWidthDp = maxWidth.value
        val homeWindowHeightDp = maxHeight.value
        val presentation = ResponsiveLayoutPolicy.dashboardPresentation(layoutMode, homeWindowHeightDp)
        val profile = presentation.profile
        val compactHeight =
            presentation.density == DashboardDensity.COMPACT_HEIGHT ||
                windowClass == ResponsiveWindowClass.PHONE_LANDSCAPE
        val twoPane = profile == DashboardProfile.WIDE
        val storedLayout = if (profile == DashboardProfile.WIDE) state.dashboardWide else state.dashboardCompact
        val visibleGameIds = state.games.mapTo(HashSet()) { it.id }
        val effectiveLayout =
            remember(storedLayout, visibleGameIds) {
                storedLayout.copy(
                    widgets =
                        storedLayout.widgets.filter { spec ->
                            spec.type != DashboardWidgetType.GAME_SPOTLIGHT || spec.gameId == null || spec.gameId in visibleGameIds
                        }
                )
            }
        val heroInstanceId = effectiveLayout.widgets.firstOrNull { it.type == DashboardWidgetType.GAME_SPOTLIGHT }?.instanceId
        val layoutDirection = LocalLayoutDirection.current
        val safeLeftDp = safeDrawingPadding.calculateLeftPadding(layoutDirection).value
        val safeTopDp = safeDrawingPadding.calculateTopPadding().value
        val safeRightDp = safeDrawingPadding.calculateRightPadding(layoutDirection).value
        val safeBottomDp = safeDrawingPadding.calculateBottomPadding().value
        val safeFrame =
            HomeRect(
                x = safeLeftDp,
                y = safeTopDp,
                w = (homeWindowWidthDp - safeLeftDp - safeRightDp).coerceAtLeast(1f),
                h = (homeWindowHeightDp - safeTopDp - safeBottomDp).coerceAtLeast(1f),
            )
        val topChrome =
            HomePresentationPolicy.topChromeSpec(
                safeFrame = safeFrame,
                sizeClass = windowClass.homeSizeClass(),
            )
        val chromeTopDp = minOf(topChrome.titleRect.y, topChrome.menuRect.y)
        val chromeBottomDp =
            maxOf(
                topChrome.titleRect.y + topChrome.titleRect.h,
                topChrome.menuRect.y + topChrome.menuRect.h,
            )
        val headerHeight = (chromeBottomDp - chromeTopDp).coerceAtLeast(HOME_MIN_TOUCH_TARGET_DP).dp
        val contentLeft = adaptiveMetrics.contentFrame.x.dp
        val contentRight =
            (homeWindowWidthDp - adaptiveMetrics.contentFrame.x - adaptiveMetrics.contentFrame.w)
                .coerceAtLeast(0f).dp
        val headerGap =
            if (compactHeight) {
                8.dp
            } else if (twoPane) {
                17.dp
            } else {
                14.dp
            }
        val pageVerticalPadding = if (compactHeight) 8.dp else 16.dp
        val pageFrame =
            Modifier.align(Alignment.TopCenter).fillMaxSize()
                .absolutePadding(left = contentLeft, right = contentRight)
                .padding(top = chromeTopDp.dp, bottom = pageVerticalPadding)
        Column(pageFrame) {
            Spacer(Modifier.height(headerHeight + headerGap))

            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                if (effectiveLayout.widgets.isEmpty()) {
                    GlassSurface(
                        colors,
                        haze,
                        Modifier.fillMaxWidth().height(180.dp),
                        KireiShapes.lg,
                    ) {
                        Column(Modifier.fillMaxSize().padding(KireiSpacing.xl), verticalArrangement = Arrangement.Center) {
                            Text("首页还没有组件", color = colors.text, style = AppleTypography.headlineMedium)
                            Text("从右上角收纳菜单进入编辑模式并添加组件。", color = colors.secondary, style = AppleTypography.bodyMedium)
                        }
                    }
                } else {
                    DashboardGrid(effectiveLayout, profile, presentation.density, Modifier.fillMaxWidth()) { spec ->
                        HomeDashboardWidget(
                            spec = spec,
                            profile = profile,
                            games = state.games,
                            recent = recent,
                            playTrend = state.playTrend,
                            colors = colors,
                            haze = haze,
                            onOpenOverview = onOpenOverview,
                            onOpenLibrary = { viewModel.selectTab(KireiTab.GAMES) },
                            onOpenGame = viewModel::selectedGame,
                            onLaunchGame = actions.launchGame,
                            onChooseDirectory = actions.chooseDirectory,
                            onOpenSpotlightEditor = { onEditDashboard(profile) },
                            spotlightInitialPage =
                                if (spec.instanceId == heroInstanceId) {
                                    currentSpotlightPage
                                } else {
                                    0
                                },
                            onSpotlightPageChanged =
                                if (spec.instanceId == heroInstanceId) {
                                    onSpotlightPageChanged
                                } else {
                                    { _ -> }
                                },
                        )
                    }
                }
                Spacer(Modifier.height(bottomContentPadding))
            }
        }
        if (menuExpanded) {
            Box(
                Modifier.fillMaxSize().zIndex(1f).clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { menuExpanded = false }
            )
        }
        Box(Modifier.fillMaxSize().zIndex(2f)) {
            Text(
                "KireiBox",
                color = colors.text,
                style = AppleTypography.headlineMedium.copy(fontSize = topChrome.titleSizeDp.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.offset(topChrome.titleRect.x.dp, topChrome.titleRect.y.dp)
                        .size(topChrome.titleRect.w.dp, topChrome.titleRect.h.dp)
                        .semantics { heading() },
            )
            HomeControlMenu(
                expanded = menuExpanded,
                colors = colors,
                haze = haze,
                editLabel = "编辑首页",
                menuDiameterDp = topChrome.menuDiameterDp.dp,
                menuIconSizeDp = topChrome.menuIconSizeDp.dp,
                opaqueFill = true,
                modifier =
                    Modifier.offset(
                        x = (topChrome.menuRect.x + topChrome.menuRect.w - 150f).dp,
                        y = topChrome.menuRect.y.dp,
                    ),
                onToggle = { menuExpanded = !menuExpanded },
                onEdit = {
                    menuExpanded = false
                    onEditDashboard(profile)
                },
                onTheme = {
                    menuExpanded = false
                    actions.setDarkMode(!colors.isDark)
                },
            )
        }
    }
}

@Composable
internal fun HomeControlMenu(
    expanded: Boolean,
    colors: KireiColors,
    haze: HazeState?,
    editLabel: String,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTheme: () -> Unit,
    menuDiameterDp: Dp = 48.dp,
    menuIconSizeDp: Dp = 27.dp,
    opaqueFill: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val polished = LocalResolvedMotionMode.current == MotionMode.POLISHED
    Box(modifier.size(150.dp, menuDiameterDp)) {
        AnimatedVisibility(
            visible = expanded,
            enter =
                fadeIn(tween(if (polished) 150 else 90)) +
                    slideInHorizontally(tween(if (polished) 180 else 90)) { it * 2 },
            exit =
                fadeOut(tween(if (polished) 110 else 90)) +
                    slideOutHorizontally(tween(if (polished) 130 else 90)) { it * 2 },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            HomeMenuAction(Icons.Rounded.Edit, editLabel, colors, haze, onEdit)
        }
        AnimatedVisibility(
            visible = expanded,
            enter =
                fadeIn(tween(if (polished) 175 else 90)) +
                    slideInHorizontally(tween(if (polished) 205 else 90)) { it },
            exit =
                fadeOut(tween(if (polished) 100 else 90)) +
                    slideOutHorizontally(tween(if (polished) 120 else 90)) { it },
            modifier = Modifier.align(Alignment.CenterStart).offset(x = 52.dp)
        ) {
            HomeMenuAction(
                if (colors.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                if (colors.isDark) "切换浅色模式" else "切换深色模式",
                colors,
                haze,
                onTheme
            )
        }
        GlassSurface(
            colors,
            haze,
            Modifier.align(Alignment.CenterEnd).size(menuDiameterDp),
            KireiShapes.full,
            KireiGlassRole.ICON,
            onToggle,
            opaqueFill = opaqueFill,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    if (expanded) "收起首页操作" else "更多首页操作",
                    tint = colors.accent,
                    modifier = Modifier.size(menuIconSizeDp),
                )
            }
        }
    }
}

@Composable
internal fun HomeMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    colors: KireiColors,
    haze: HazeState?,
    onClick: () -> Unit
) {
    // 40dp glass circle inside a 48dp touch target.
    Box(
        Modifier.size(48.dp).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(colors, haze, Modifier.size(40.dp), KireiShapes.full, KireiGlassRole.ICON) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, description, tint = colors.text, modifier = Modifier.size(22.dp))
            }
        }
    }
}
