package com.kireibox.ui
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleColorPalette
import com.kireibox.designsystem.AppleDesignTheme
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiThemeResolver
import com.kireibox.designsystem.LocalAppleColors
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.animateKireiColorsAsState
import com.kireibox.designsystem.rememberSystemNight
import com.kireibox.update.UpdateUiState
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.MotionMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KireiBoxApp(
    viewModel: KireiViewModel,
    actions: KireiActions,
    prompt: KireiPrompt? = null,
    onPromptDismiss: () -> Unit = {},
    fileBrowserRequest: BuiltinFileBrowserRequest? = null,
    onFileBrowserDismiss: () -> Unit = {},
    onFileBrowserUseSaf: () -> Unit = {},
    onFileBrowserSelected: (Uri) -> Unit = {},
    /** Lets the Activity keep system-bar icon tone readable against the real page background. */
    onSystemBarIconsLight: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val settings = state.settings
    val dark =
        KireiThemeResolver.resolveDark(
            themeMode = settings?.themeMode,
            systemNight = rememberSystemNight(),
            activityNight = KireiThemeResolver.isNightUiMode(LocalConfiguration.current.uiMode),
        )
    val immersiveHome =
        state.tab == KireiTab.HOME &&
            (settings?.homePresentationMode ?: HomePresentationMode.IMMERSIVE) == HomePresentationMode.IMMERSIVE
    LaunchedEffect(dark, immersiveHome) {
        onSystemBarIconsLight(SystemBarVisibilityPolicy.statusBarIconsLight(dark, immersiveHome))
    }
    AppleDesignTheme(
        darkTheme = dark,
        accent = settings?.accentChoice ?: AccentChoice.DEFAULT,
        highContrast = settings?.increasedContrast == true,
    ) {
        val context = LocalContext.current
        // Null until settings load: keep the orientation LauncherActivity locked in onCreate.
        val orientationMode = settings?.orientationMode
        LaunchedEffect(orientationMode, context) {
            if (orientationMode != null) context.findActivity()?.requestedOrientation = orientationMode.activityOrientation()
        }
        val resolvedMotion =
            remember(settings?.motionMode, context) {
                resolveMotionMode(context, settings?.motionMode ?: MotionMode.AUTO)
            }
        val reducedMotion = resolvedMotion == MotionMode.REDUCED
        CompositionLocalProvider(
            LocalResolvedMotionMode provides resolvedMotion,
            LocalNsfwBlurEnabled provides (settings?.nsfwBlurEnabled != false),
            LocalImmersiveFx provides rememberImmersiveFx(settings, resolvedMotion),
        ) {
            CompositionLocalProvider(
                LocalSystemBarIconTone provides SystemBarVisibilityPolicy.statusBarIconsLight(dark, immersiveHome),
            ) {
                val apple = LocalAppleColors.current
                val colorDuration = if (reducedMotion) 0 else AppleMotion.Duration.MEDIUM
                val colors = animateKireiColorsAsState(remember(apple) { kireiColorsOf(apple) }, colorDuration)
                var renameGame by remember { mutableStateOf<Game?>(null) }
                var renameText by remember { mutableStateOf("") }
                var removeGame by remember { mutableStateOf<Game?>(null) }
                var removingGameId by remember { mutableStateOf<Long?>(null) }
                var homeEditorProfile by remember { mutableStateOf<DashboardProfile?>(null) }
                val homeSpotlightPage by viewModel.homeSpotlightPage.collectAsState()
                var showLibraryOverview by remember { mutableStateOf(false) }
                var libraryOverviewSort by remember { mutableStateOf(LibraryOverviewSort.RECENT) }
                val openPlayTimeOverview: () -> Unit = {
                    libraryOverviewSort = LibraryOverviewSort.PLAY_TIME
                    showLibraryOverview = true
                }
                val uiScope = rememberCoroutineScope()
                val rootBackHasPriorityScreen =
                    prompt == null &&
                        fileBrowserRequest == null &&
                        state.xp3Picker == null &&
                        state.androidAppPicker == null &&
                        state.externalShortcutPicker == null &&
                        state.addGame == null &&
                        state.krkrEditorGame == null &&
                        state.winlatorEditorGame == null &&
                        state.coverSearch == null &&
                        homeEditorProfile == null &&
                        state.selectedGame == null &&
                        !showLibraryOverview &&
                        renameGame == null &&
                        removeGame == null
                val rootBackDestination = KireiBackPolicy.resolveRoot(state.tab, state.settingsPage)
                BackHandler(enabled = rootBackHasPriorityScreen && rootBackDestination != KireiBackDestination.EXIT) {
                    when (rootBackDestination) {
                        KireiBackDestination.SETTINGS_PARENT -> viewModel.navigateUpSettings()
                        KireiBackDestination.HOME -> viewModel.selectTab(KireiTab.HOME)
                        KireiBackDestination.EXIT -> Unit
                    }
                }
                Surface(Modifier.fillMaxSize(), color = colors.background) {
                    val haze = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings?.reducedTransparency != true) remember { HazeState() } else null
                    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
                        val statusBarBottomDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        val displayCutout = displayCutoutBoundsDp(LocalView.current, LocalDensity.current)
                        val layoutDirection = LocalLayoutDirection.current
                        val safeWidth =
                            maxWidth - safeDrawingPadding.calculateLeftPadding(layoutDirection) -
                                safeDrawingPadding.calculateRightPadding(layoutDirection)
                        val safeHeight =
                            maxHeight - safeDrawingPadding.calculateTopPadding() -
                                safeDrawingPadding.calculateBottomPadding()
                        val configuration = LocalConfiguration.current
                        val windowClass =
                            ResponsiveLayoutPolicy.resolveWindowClass(
                                safeWidthDp = safeWidth.value,
                                safeHeightDp = safeHeight.value,
                                smallestScreenWidthDp = configuration.smallestScreenWidthDp,
                                screenLayoutSize = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK,
                            )
                        // Keep legacy callers, such as the task center, on the compact branch for a
                        // compact phone landscape. They must not infer Pad/WIDE from aspect alone.
                        val layoutMode = windowClass.legacyLayoutMode()
                        val adaptiveMetrics =
                            resolveAdaptiveUiMetrics(
                                windowWidthDp = maxWidth.value,
                                windowHeightDp = maxHeight.value,
                                safeLeftDp = safeDrawingPadding.calculateLeftPadding(layoutDirection).value,
                                safeTopDp = safeDrawingPadding.calculateTopPadding().value,
                                safeRightDp = safeDrawingPadding.calculateRightPadding(layoutDirection).value,
                                safeBottomDp = safeDrawingPadding.calculateBottomPadding().value,
                            )
                        val responsiveDockContentPadding = adaptiveMetrics.bottomContentPaddingDp.dp
                        val libraryDockContentPadding =
                            resolveLibraryGridBottomContentPaddingDp(
                                windowHeightDp = maxHeight.value,
                                dockRect = adaptiveMetrics.dockRect,
                                safeBottomDp = safeDrawingPadding.calculateBottomPadding().value,
                            ).dp
                        val editorGame = state.krkrEditorGame
                        val winlatorEditorGame = state.winlatorEditorGame
                        val coverSearch = state.coverSearch
                        val xp3Picker = state.xp3Picker
                        val androidAppPicker = state.androidAppPicker
                        val externalShortcutPicker = state.externalShortcutPicker
                        val addGame = state.addGame
                        val showMainDock =
                            fileBrowserRequest == null &&
                                xp3Picker == null &&
                                androidAppPicker == null &&
                                externalShortcutPicker == null &&
                                addGame == null &&
                                editorGame == null &&
                                winlatorEditorGame == null &&
                                coverSearch == null &&
                                homeEditorProfile == null
                        Box(
                            Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze) else Modifier)
                        ) {
                            when {
                                fileBrowserRequest != null ->
                                    BuiltinFileBrowserPage(
                                        request = fileBrowserRequest,
                                        colors = colors,
                                        haze = haze,
                                        onDismiss = onFileBrowserDismiss,
                                        onUseSaf = onFileBrowserUseSaf,
                                        onSelected = onFileBrowserSelected
                                    )
                                xp3Picker != null ->
                                    Xp3PickerPage(
                                        picker = xp3Picker,
                                        colors = colors,
                                        haze = haze,
                                        onDismiss = viewModel::dismissXp3Picker,
                                        onSelected = viewModel::selectXp3LaunchTarget,
                                    )
                                androidAppPicker != null ->
                                    AndroidAppPickerPage(
                                        picker = androidAppPicker,
                                        colors = colors,
                                        haze = haze,
                                        onDismiss = viewModel::dismissAndroidAppPicker,
                                        onSelected = viewModel::selectAndroidApp,
                                    )
                                externalShortcutPicker != null ->
                                    ExternalShortcutPickerPage(
                                        picker = externalShortcutPicker,
                                        colors = colors,
                                        haze = haze,
                                        onDismiss = viewModel::dismissExternalShortcutPicker,
                                        onToggle = viewModel::toggleExternalShortcutSelection,
                                        onToggleAll = viewModel::toggleAllExternalShortcuts,
                                        onImport = viewModel::importSelectedExternalShortcuts,
                                    )
                                addGame != null ->
                                    AddGameHost(
                                        state = state,
                                        colors = colors,
                                        haze = haze,
                                        viewModel = viewModel,
                                        actions = actions,
                                    )
                                editorGame != null ->
                                    Box(Modifier.fillMaxSize()) {
                                        KrkrGameSettingsPage(
                                            game = editorGame,
                                            state = state,
                                            colors = colors,
                                            haze = haze,
                                            viewModel = viewModel
                                        )
                                    }
                                winlatorEditorGame != null ->
                                    Box(Modifier.fillMaxSize()) {
                                        WinlatorGameSettingsPage(
                                            game = winlatorEditorGame,
                                            state = state,
                                            colors = colors,
                                            haze = haze,
                                            viewModel = viewModel
                                        )
                                    }
                                coverSearch != null ->
                                    Box(Modifier.fillMaxSize()) {
                                        CoverPickerPage(
                                            search = coverSearch,
                                            colors = colors,
                                            haze = haze,
                                            viewModel = viewModel,
                                            actions = actions,
                                        )
                                    }
                                homeEditorProfile != null ->
                                    Box(Modifier.fillMaxSize()) {
                                        DashboardEditorPage(
                                            profile = homeEditorProfile!!,
                                            state = state,
                                            colors = colors,
                                            haze = haze,
                                            viewModel = viewModel,
                                            onClose = { homeEditorProfile = null }
                                        )
                                    }
                                else ->
                                    Box(Modifier.fillMaxSize()) {
                                        Crossfade(
                                            targetState = state.tab,
                                            animationSpec = tween(if (reducedMotion) 0 else 190),
                                            label = "mainNavigation"
                                        ) { tab ->
                                            when (tab) {
                                                KireiTab.HOME ->
                                                    HomePage(
                                                        state = state, colors = colors, haze = haze, viewModel = viewModel,
                                                        actions = actions, layoutMode = layoutMode, safeDrawingPadding = safeDrawingPadding,
                                                        bottomContentPadding = responsiveDockContentPadding,
                                                        navigationBottom = navigationBottom,
                                                        statusBarBottomDp = statusBarBottomDp,
                                                        displayCutout = displayCutout,
                                                        adaptiveMetrics = adaptiveMetrics,
                                                        windowClass = windowClass,
                                                        currentSpotlightPage = homeSpotlightPage,
                                                        onSpotlightPageChanged = viewModel::setHomeSpotlightPage,
                                                        onOpenOverview = openPlayTimeOverview,
                                                        onEditDashboard = { homeEditorProfile = it },
                                                    )
                                                KireiTab.GAMES ->
                                                    LibraryPage(
                                                        state, colors, haze, viewModel, actions, removingGameId, layoutMode,
                                                        safeDrawingPadding, libraryDockContentPadding, adaptiveMetrics, windowClass
                                                    ) { removeGame = it }
                                                KireiTab.SETTINGS ->
                                                    SettingsPage(
                                                        state, colors, haze, viewModel, actions, layoutMode,
                                                        safeDrawingPadding, responsiveDockContentPadding, adaptiveMetrics,
                                                        onOpenPlayTimeOverview = openPlayTimeOverview,
                                                    )
                                            }
                                        }
                                    }
                            }
                        }
                        val immersiveHomeShown =
                            state.tab == KireiTab.HOME &&
                                (state.settings?.homePresentationMode ?: HomePresentationMode.IMMERSIVE) ==
                                HomePresentationMode.IMMERSIVE
                        if (showMainDock && !imeVisible &&
                            HomePresentationPolicy.shouldShowGlobalDock(
                                state.tab,
                                state.settings?.homePresentationMode,
                            )
                        ) {
                            KireiMainDock(
                                spec =
                                    MainDockSpec(
                                        tab = state.tab,
                                        // r5 · B: covers needing attention (review or failed), else the one-time 快速设置 dot.
                                        settingsBadge = DockSettingsBadgePolicy.resolve(state.coverAttentionCount, state.settings?.quickSetupDone),
                                        metrics = adaptiveMetrics,
                                        reducedTransparency = state.settings?.reducedTransparency == true,
                                    ),
                                colors = if (immersiveHomeShown) immersiveDockKireiColors(colors) else colors,
                                haze = haze,
                                viewModel = viewModel,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                        state.notice?.let { notice ->
                            Notice(
                                message = notice,
                                tone = NoticeTonePolicy.resolve(notice, state.noticeTone),
                                colors = colors,
                                haze = haze,
                                onDismiss = viewModel::clearNotice,
                            )
                        }
                        when (state.winlatorGuide) {
                            WinlatorGuideStage.NONE -> Unit
                            WinlatorGuideStage.NOTICE ->
                                WinlatorGuideNotice(
                                    colors = colors,
                                    haze = haze,
                                    stacked = state.notice != null,
                                    onOpen = viewModel::openWinlatorGuide,
                                    onDismiss = viewModel::dismissWinlatorGuide,
                                )
                            WinlatorGuideStage.SHEET ->
                                WinlatorGuideSheet(colors, onDone = viewModel::completeWinlatorGuide, onDismiss = viewModel::dismissWinlatorGuide)
                        }
                        state.undoRemoval?.let { undo ->
                            UndoRemovalNotice(undo.game.title ?: "游戏", colors, haze, viewModel::undoLastRemoval, viewModel::dismissUndoRemoval)
                        }
                        state.undoScanRootRemoval?.let { undo ->
                            UndoRemovalNotice(
                                "目录和 ${undo.gameIds.size} 部游戏",
                                colors,
                                haze,
                                viewModel::undoScanRootRemoval,
                                viewModel::dismissUndoScanRootRemoval,
                            )
                        }
                        state.selectedGame?.let { game ->
                            GameActionSheet(
                                game = game,
                                state = state,
                                colors = colors,
                                viewModel = viewModel,
                                actions = actions,
                                onDismiss = viewModel::dismissSelectedGame,
                                onRename = {
                                    renameText = game.title.orEmpty()
                                    renameGame = game
                                    viewModel.dismissSelectedGame()
                                },
                                onRemove = {
                                    removeGame = game
                                    viewModel.dismissSelectedGame()
                                }
                            )
                        }
                        if (showLibraryOverview) {
                            LibraryOverviewSheet(
                                games = state.games,
                                sort = libraryOverviewSort,
                                colors = colors,
                                onSortChange = { libraryOverviewSort = it },
                                onDismiss = { showLibraryOverview = false },
                                onGame = { game ->
                                    showLibraryOverview = false
                                    viewModel.selectedGame(game)
                                }
                            )
                        }
                        renameGame?.let { game ->
                            RenameGameDialog(
                                game = game,
                                value = renameText,
                                onValueChange = { renameText = it },
                                colors = colors,
                                onDismiss = { renameGame = null },
                                onSave = {
                                    viewModel.renameGame(game, renameText)
                                    renameGame = null
                                }
                            )
                        }
                        removeGame?.let { game ->
                            RemoveGameSheet(
                                game = game,
                                colors = colors,
                                onDismiss = { removeGame = null },
                                onConfirm = {
                                    removeGame = null
                                    removingGameId = game.id
                                    uiScope.launch {
                                        delay(if (reducedMotion) 100 else 180)
                                        viewModel.removeFromLibrary(game)
                                        removingGameId = null
                                    }
                                }
                            )
                        }
                        if (state.homeLoadState == HomeLoadState.INITIAL_LOADING &&
                            state.games.isEmpty() &&
                            prompt == null
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.accent)
                            }
                        }
                        val homeErrorPrompt =
                            if (prompt == null &&
                                state.homeLoadState == HomeLoadState.RELOAD_FAILED &&
                                state.homeLoadError != null
                            ) {
                                KireiPrompt(
                                    title = "加载失败",
                                    message = state.homeLoadError ?: "加载失败",
                                    confirmLabel = "重试",
                                    dismissLabel = "稍后",
                                    onConfirm = viewModel::reload,
                                    onDismiss = viewModel::dismissHomeLoadError,
                                )
                            } else {
                                null
                            }
                        KireiPromptHost(
                            prompt = prompt ?: homeErrorPrompt,
                            colors = colors,
                            haze = haze,
                            reducedMotion = reducedMotion,
                            onPromptDismiss = {
                                if (prompt != null) onPromptDismiss() else viewModel.dismissHomeLoadError()
                            }
                        )
                        UpdatePromptHost(
                            state = if (prompt == null && homeErrorPrompt == null) state.updateState else UpdateUiState.Idle,
                            colors = colors,
                            haze = haze,
                            onDownload = viewModel::downloadAvailableUpdate,
                            onCancelDownload = viewModel::cancelUpdateDownload,
                            onInstall = actions.installUpdate,
                            onDiscard = viewModel::discardReadyUpdate,
                            onIgnore = viewModel::ignoreAvailableUpdate,
                            onDismiss = viewModel::dismissUpdateUi,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The single construction site of the app palette: maps the resolved theme ([LocalAppleColors],
 * built from the design tokens for the current theme mode, accent and contrast) onto [KireiColors].
 */
internal fun kireiColorsOf(apple: AppleColorPalette): KireiColors =
    KireiColors(
        background = apple.background,
        surface = apple.surface,
        surfaceRaised = apple.surfaceAlt,
        text = apple.textPrimary,
        secondary = apple.textSecondary,
        accent = apple.primary,
        success = apple.success,
        outline = apple.outline,
        onAccent = apple.onPrimary,
        danger = apple.danger,
        isDark = apple.isDark,
        surface3 = apple.surface3,
        surfaceOverlay = apple.surfaceOverlay,
        stroke = apple.stroke,
        strokeStrong = apple.strokeStrong,
        tertiary = apple.textTertiary,
        accentContainer = apple.primaryContainer,
        warning = apple.warning,
        scrim = apple.scrim,
        glass = apple.glass,
        highContrast = apple.highContrast,
    )
