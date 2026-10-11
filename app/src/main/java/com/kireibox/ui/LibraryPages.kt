package com.kireibox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.cover.CoverTaskContainer
import com.kireibox.cover.CoverTaskPresentationPolicy
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.yuki.yukihub.data.GameSearchIndex
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.YukiSettingsStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

// Game library: grid, page, edit/sort menu, game card and the cover task center.

@Composable
internal fun LibraryPage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    removingGameId: Long?,
    layoutMode: KireiLayoutMode,
    safeDrawingPadding: PaddingValues,
    bottomContentPadding: Dp,
    adaptiveMetrics: AdaptiveUiMetrics,
    windowClass: ResponsiveWindowClass,
    onRequestRemove: (Game) -> Unit
) {
    val polishedMotion = LocalResolvedMotionMode.current == MotionMode.POLISHED
    val reducedMotion = !polishedMotion
    val narrowLandscape = windowClass == ResponsiveWindowClass.PHONE_LANDSCAPE
    val landscape = narrowLandscape || windowClass == ResponsiveWindowClass.PAD_LANDSCAPE
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<EngineType?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var customOrderDraft by remember { mutableStateOf<ManualOrderSelectionDraft?>(null) }
    var showDiscardCustomOrder by remember { mutableStateOf(false) }
    var showAddGame by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val taskCenterListState = rememberLazyListState()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingRight = safeDrawingPadding.calculateRightPadding(layoutDirection)
    val safeDrawingTop = safeDrawingPadding.calculateTopPadding()
    val safeDrawingBottom = safeDrawingPadding.calculateBottomPadding()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(layoutMode, windowClass) {
        filterMenuExpanded = false
    }
    // Numbering cards needs the whole library: the search field is inert while editing, so let go of it.
    LaunchedEffect(editMode) {
        if (editMode) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    val searchActive = query.isNotBlank() || searchFocused
    val closeSearch: () -> Unit = {
        focusManager.clearFocus(force = true)
        query = ""
        searchFocused = false
    }
    val libraryBackAction =
        KireiBackPolicy.resolveLibraryBack(
            taskCenterOpen = state.taskCenterOpen,
            filterMenuExpanded = filterMenuExpanded,
            imeVisible = WindowInsets.ime.getBottom(density) > 0,
            searchActive = searchActive,
            editMode = editMode,
        )
    BackHandler(
        enabled = libraryBackAction != KireiLibraryBackAction.PAGE,
    ) {
        when (libraryBackAction) {
            KireiLibraryBackAction.CLOSE_TASK_CENTER -> viewModel.closeCoverTaskCenter()
            KireiLibraryBackAction.CLOSE_FILTER_MENU -> filterMenuExpanded = false
            KireiLibraryBackAction.HIDE_IME -> {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                searchFocused = false
            }
            KireiLibraryBackAction.CLOSE_SEARCH -> closeSearch()
            KireiLibraryBackAction.EXIT_EDIT_MODE -> {
                if (customOrderDraft != null) showDiscardCustomOrder = true else editMode = false
            }
            KireiLibraryBackAction.PAGE -> Unit
        }
    }
    val visible =
        remember(state.games, state.gameSearchIndex, filter, query) {
            state.games
                .asSequence()
                .filter { filter == null || it.engine == filter }
                .filter { GameSearchIndex.matches(state.gameSearchIndex[it.id], query) }
                .toList()
        }
    val emptyState =
        libraryEmptyStateModel(
            LibraryEmptyStatePolicy.resolve(state.homeLoadState, state.games.size, filtering = query.isNotBlank() || filter != null),
            onAddDirectory = actions.chooseDirectory,
        ) {
            closeSearch()
            filter = null
        }
    val manualSort = state.settings?.sortMode == YukiSettingsStore.SORT_MANUAL
    val customReorderEnabled = filter == null && query.isBlank() && !searchFocused
    val customPositions =
        remember(state.games, customOrderDraft, manualSort, editMode) {
            ManualOrderSelectionPolicy.positions(
                games = state.games,
                draft = customOrderDraft,
                showExistingOrder = editMode && manualSort,
            )
        }
    val gridActions =
        LibraryGridActions(
            viewModel = viewModel,
            onRequestRemove = onRequestRemove,
            onEditModeChange = {
                editMode = it
                if (!it) customOrderDraft = null
            },
            onCustomToggle = { game ->
                customOrderDraft = customOrderDraft?.let { ManualOrderSelectionPolicy.toggle(it, game) }
            },
        )
    val sortValue = if (customOrderDraft != null) YukiSettingsStore.SORT_MANUAL else state.settings?.sortMode.orEmpty()
    val selectLibrarySort: (String) -> Unit = { mode ->
        if (mode == YukiSettingsStore.SORT_MANUAL) {
            if (!customReorderEnabled) {
                viewModel.showNotice("请清空搜索并切换到全部后再自定义排序")
            } else {
                // The sort menu is also reachable while browsing: numbering cards needs edit mode
                // (tap-to-number, 完成 and back all hang off it).
                editMode = true
                customOrderDraft = ManualOrderSelectionPolicy.empty()
            }
        } else {
            customOrderDraft = null
            viewModel.setSortMode(mode)
        }
    }
    val finishLibraryEdit: () -> Unit = {
        customOrderDraft?.let { draft ->
            val order = ManualOrderSelectionPolicy.finalize(state.games, draft)
            viewModel.saveManualOrder(order.favoriteIds, order.regularIds)
        }
        customOrderDraft = null
        editMode = false
    }
    val taskButtonState = CoverTaskPresentationPolicy.buttonState(state.coverQueue.tasks)
    val shouldDismissCompletedRows =
        CoverTaskPresentationPolicy.shouldDismissCompletedRows(
            tasks = state.coverQueue.tasks,
            panelOpen = state.taskCenterOpen,
        )
    LaunchedEffect(shouldDismissCompletedRows) {
        if (shouldDismissCompletedRows) {
            delay(1_200)
            viewModel.dismissCoverTaskSessionRows()
        }
    }
    val libraryEngines =
        remember {
            listOf(
                EngineType.KIRIKIRI,
                EngineType.ONS,
                EngineType.TYRANO,
                EngineType.ARTEMIS,
                EngineType.ANDROID_APP,
            )
        }
    // The page content is its own haze source for the bar (the shell's source would sample itself).
    val pageHaze = remember { HazeState() }
    val barHaze = pageHaze.takeIf { haze != null }
    val collapseRangePx =
        with(density) { (if (landscape) LibraryHeaderTransitionPolicy.GLASS_FADE_DP.dp else KireiTopBarDefaults.CollapseDistance).toPx() }
    val collapse: () -> Float =
        remember(gridState, collapseRangePx) {
            {
                LibraryHeaderTransitionPolicy.scrollFraction(
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffsetPx = gridState.firstVisibleItemScrollOffset,
                    rangePx = collapseRangePx,
                )
            }
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val libraryWindowWidth = maxWidth
        val libraryWindowHeight = maxHeight
        val compactLibraryCards =
            narrowLandscape ||
                LibraryCardLayoutPolicy.isShortLandscape(
                    windowWidthDp = libraryWindowWidth.value,
                    windowHeightDp = libraryWindowHeight.value,
                )
        val gridModel =
            LibraryGridModel(
                visible = visible,
                appearance =
                    LibraryGridAppearanceSpec(
                        colors = colors,
                        polishedMotion = polishedMotion,
                        editMode = editMode,
                        removingGameId = removingGameId,
                        reducedMotion = reducedMotion,
                        compactCards = compactLibraryCards,
                    ),
                reorder =
                    LibraryGridReorderSpec(
                        manualSort = manualSort,
                        customReorderEnabled = customReorderEnabled,
                        customDraft = customOrderDraft,
                        customPositions = customPositions,
                    ),
                emptyState = emptyState,
            )
        val contentLeft = adaptiveMetrics.contentFrame.x.dp
        val contentRight = (maxWidth.value - adaptiveMetrics.contentFrame.x - adaptiveMetrics.contentFrame.w).dp
        val resolvedLibraryHeaderBottomDp =
            LibraryCardLayoutPolicy.resolvedHeaderBottomDp(
                headerTopDp = safeDrawingTop.value,
                toolbarHeightDp = LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP,
            )
        // Landscape: the grid starts below the bar. Portrait: the header block scrolls under the bar.
        val landscapeGridTopDp =
            LibraryCardLayoutPolicy.gridTopPaddingDp(
                gridViewportTopDp = 0f,
                headerBottomDp = resolvedLibraryHeaderBottomDp,
            )
        val barFrame =
            LibraryBarFrame(
                landscape = landscape,
                topInset = safeDrawingTop,
                gutterLeft = contentLeft,
                gutterRight = contentRight,
                searchWidth = (libraryWindowWidth * LANDSCAPE_SEARCH_WIDTH_FRACTION).coerceIn(LANDSCAPE_SEARCH_MIN_WIDTH, LANDSCAPE_SEARCH_MAX_WIDTH),
                collapse = collapse,
            )
        val headerModel =
            LibraryHeaderModel(
                colors = colors,
                haze = barHaze,
                visibleCount = visible.size,
                query = query,
                filter = filter,
                engines = libraryEngines,
                editMode = editMode,
                sortValue = sortValue,
                libraryEmpty = state.games.isEmpty(),
                taskButtonVisible = taskButtonState.visible,
                filterMenuExpanded = filterMenuExpanded,
            )
        val headerActions =
            LibraryHeaderActions(
                onQueryChange = { query = it },
                onSearchFocusChange = { searchFocused = it },
                onSortChange = selectLibrarySort,
                onSelectFilter = { engine ->
                    filter = engine
                    editMode = false
                    filterMenuExpanded = false
                },
                onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
                onDismissFilterMenu = { filterMenuExpanded = false },
                onFinishEdit = finishLibraryEdit,
                onAddGame = { showAddGame = true },
            )
        val portraitHeader: @Composable () -> Unit = { LibraryPortraitHeader(headerModel, headerActions, barFrame) }
        // The grid fills the window: cards scroll under the glass bar instead of being clipped below it.
        LibraryGameGrid(
            layout =
                LibraryGridLayoutSpec(
                    gridColumns = GridCells.Fixed(adaptiveMetrics.libraryColumns),
                    gridState = gridState,
                    adaptiveMetrics = adaptiveMetrics,
                    bottomContentPadding = bottomContentPadding,
                    contentTopDp = if (landscape) landscapeGridTopDp else safeDrawingTop.value,
                    contentLeft = contentLeft,
                    contentRight = contentRight,
                    modifier = Modifier.fillMaxSize().then(if (barHaze != null) Modifier.hazeSource(barHaze) else Modifier),
                    header = portraitHeader.takeUnless { landscape },
                ),
            model = gridModel,
            actions = gridActions,
        )
        LibraryTopBar(
            model = headerModel,
            actions = headerActions,
            frame = barFrame,
            taskAnchor = {
                CoverTaskToolbarAnchor(
                    queue = state.coverQueue,
                    panelOpen = state.taskCenterOpen,
                    colors = colors,
                    haze = barHaze,
                    layoutMode = layoutMode,
                    windowWidth = libraryWindowWidth,
                    windowHeight = libraryWindowHeight,
                    safeTop = safeDrawingTop,
                    safeRight = if (landscape) contentRight else safeDrawingRight,
                    safeBottom = safeDrawingBottom,
                    listState = taskCenterListState,
                    viewModel = viewModel,
                )
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (state.taskCenterOpen && CoverTaskPresentationPolicy.containerFor(layoutMode) == CoverTaskContainer.BOTTOM_SHEET) {
            CoverTaskBottomSheet(
                queue = state.coverQueue,
                listState = taskCenterListState,
                colors = colors,
                onClose = viewModel::closeCoverTaskCenter,
                onPause = viewModel::pauseCoverQueue,
                onResume = viewModel::resumeCoverQueue,
                onReview = viewModel::openCoverReview,
                onRetry = viewModel::retryCoverTask,
                onCancel = viewModel::cancelCoverTask,
                games = state.games,
            )
        }
        if (showDiscardCustomOrder) {
            KireiDialog(
                spec =
                    KireiDialogSpec(
                        title = "放弃自定义排序？",
                        message = "尚未保存的编号顺序将被放弃。",
                        confirm =
                            KireiDialogAction("放弃更改", {
                                showDiscardCustomOrder = false
                                customOrderDraft = null
                                editMode = false
                            }, style = KireiButtonStyle.DESTRUCTIVE),
                        dismiss = KireiDialogAction("继续编辑", { showDiscardCustomOrder = false }, style = KireiButtonStyle.PLAIN),
                    ),
                colors = colors,
                onDismissRequest = { showDiscardCustomOrder = false },
            )
        }
        if (showAddGame) {
            LibraryAddGameSheet(colors, viewModel::openAddBatch, viewModel::openAddSingle) { showAddGame = false }
        }
    }
}

/** Landscape: the search capsule takes this share of the window, within the bounds, so the bar's actions always fit. */
private const val LANDSCAPE_SEARCH_WIDTH_FRACTION = .28f
private val LANDSCAPE_SEARCH_MIN_WIDTH = 160.dp
private val LANDSCAPE_SEARCH_MAX_WIDTH = 280.dp
