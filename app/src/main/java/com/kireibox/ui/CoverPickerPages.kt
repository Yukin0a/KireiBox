package com.kireibox.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiSearchField
import com.kireibox.designsystem.components.KireiSearchFieldOptions
import com.kireibox.designsystem.components.KireiStatusChip
import com.kireibox.designsystem.dangerContainer
import com.kireibox.designsystem.dangerSolid
import com.yuki.yukihub.metadata.model.CoverSourceMode
import dev.chrisbanes.haze.HazeState

// Cover picker (r5 · B, spec §10「r5 · B」): the search side (header, query, source status, other
// ways to set a cover) and the actions. The results live in CoverPickerResults.kt.

@Composable
internal fun CoverPickerPage(
    search: CoverSearchUi,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
) {
    val accountState by viewModel.state.collectAsState()
    BackHandler(onBack = viewModel::dismissCoverSearch)
    CoverPickerKeyboardResize()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        // Decided from the whole window, so the keyboard only shortens the panes and never flips the layout.
        val frame = CoverPickerLayoutPolicy.resolve(maxWidth.value, maxHeight.value)
        val scope = CoverPickerScope(search, accountState, colors, viewModel, actions, raised = frame.arrangement == CoverPickerArrangement.CARD)
        val safe = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        when (frame.arrangement) {
            CoverPickerArrangement.TWO_PANE -> CoverPickerTwoPane(scope, haze, frame, imeVisible, safe)
            CoverPickerArrangement.CARD ->
                Box(safe.padding(KireiSpacing.xxl), contentAlignment = Alignment.Center) {
                    CoverPickerSinglePane(
                        scope,
                        haze,
                        imeVisible,
                        Modifier.widthIn(max = CARD_MAX_WIDTH).heightIn(max = CARD_MAX_HEIGHT).fillMaxSize()
                            .clip(KireiShapes.xl)
                            .background(colors.surface)
                            .border(KireiElevation.strokeWidth, colors.stroke, KireiShapes.xl)
                            .padding(horizontal = KireiSpacing.xl, vertical = KireiSpacing.md),
                    )
                }
            CoverPickerArrangement.SINGLE ->
                CoverPickerSinglePane(scope, haze, imeVisible, safe.padding(horizontal = AdaptiveLayoutConstants.PAGE_GUTTER_DP.dp, vertical = KireiSpacing.sm))
        }
    }
}

private val CARD_MAX_WIDTH = 760.dp
private val CARD_MAX_HEIGHT = 960.dp

/**
 * While the picker is open the window resizes for the keyboard instead of panning: the results end
 * at the keyboard's top (safeDrawing includes the IME, which reaches Compose below API 30 only with
 * adjust-resize) and the header never slides under the status bar. Restored when the picker closes.
 */
@Composable
private fun CoverPickerKeyboardResize() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val previous = window?.attributes?.softInputMode
        if (window != null && previous != null) {
            window.setSoftInputMode((previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        onDispose { if (window != null && previous != null) window.setSoftInputMode(previous) }
    }
}

/** Portrait phones and the tablet card: header, query, sources, results, then the actions. */
@Composable
private fun CoverPickerSinglePane(
    scope: CoverPickerScope,
    haze: HazeState?,
    imeVisible: Boolean,
    modifier: Modifier,
) {
    Column(modifier) {
        CoverPickerHeader(scope, haze)
        CoverSearchRow(scope, Modifier.padding(top = KireiSpacing.sm))
        CoverSourceStatus(scope, Modifier.padding(top = KireiSpacing.sm))
        CoverPickerError(scope, Modifier.padding(top = KireiSpacing.sm))
        CoverResults(scope, Modifier.weight(1f).padding(top = KireiSpacing.sm))
        // While typing, the keyboard takes the actions' place and the results keep the room above it.
        if (!imeVisible) {
            CoverAlternatives(scope, sidePane = false, Modifier.padding(top = KireiSpacing.xxs))
            CoverPickerActions(scope, fill = true, Modifier.padding(top = KireiSpacing.xxs))
        }
    }
}

/** Landscape phones: the search side on the left, the results and the actions on the right. */
@Composable
private fun CoverPickerTwoPane(
    scope: CoverPickerScope,
    haze: HazeState?,
    frame: CoverPickerFrame,
    imeVisible: Boolean,
    modifier: Modifier,
) {
    Row(
        modifier.padding(horizontal = AdaptiveLayoutConstants.PAGE_GUTTER_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(KireiSpacing.lg),
    ) {
        CoverPickerSidePane(scope, haze, Modifier.width(frame.sidePaneWidthDp.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight()) {
            CoverResults(scope, Modifier.weight(1f).padding(top = KireiSpacing.sm))
            if (!imeVisible) CoverPickerActions(scope, fill = false, Modifier.padding(vertical = KireiSpacing.xs))
        }
    }
}

/**
 * Left pane: scrolls on its own when a short window or a large font needs it (the focused query
 * field stays in view above the keyboard); with room to spare the other ways sit at its bottom,
 * level with the actions on the right.
 */
@Composable
private fun CoverPickerSidePane(
    scope: CoverPickerScope,
    haze: HazeState?,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(vertical = KireiSpacing.sm),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                CoverPickerHeader(scope, haze)
                CoverSearchRow(scope, Modifier.padding(top = KireiSpacing.sm))
                CoverSourceStatus(scope, Modifier.padding(top = KireiSpacing.sm))
                CoverPickerError(scope, Modifier.padding(top = KireiSpacing.sm))
            }
            CoverAlternatives(scope, sidePane = true, Modifier.padding(top = KireiSpacing.lg))
        }
    }
}

@Composable
private fun CoverPickerHeader(
    scope: CoverPickerScope,
    haze: HazeState?,
) {
    val colors = scope.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "稍后处理", colors, haze, scope.viewModel::dismissCoverSearch)
        Column(Modifier.padding(start = KireiSpacing.sm).weight(1f)) {
            Text("选择封面", color = colors.text, style = AppleTypography.headlineMedium, maxLines = 1, modifier = Modifier.semantics { heading() })
            Text(scope.search.game?.title.orEmpty(), color = colors.secondary, style = AppleTypography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The query: the keyboard's 「搜索」 action and the 搜索 button both search every source again and
 * close the keyboard, so the results get the whole pane back.
 */
@Composable
private fun CoverSearchRow(
    scope: CoverPickerScope,
    modifier: Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val runSearch = {
        scope.viewModel.retryCoverSearch()
        keyboard?.hide()
        focus.clearFocus()
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
        KireiSearchField(
            scope.search.keyword,
            scope.viewModel::updateCoverKeyword,
            scope.colors,
            Modifier.weight(1f),
            KireiSearchFieldOptions(placeholder = "输入游戏名称", onSearch = runSearch),
        )
        KireiButton(
            "搜索",
            scope.colors,
            options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.MEDIUM, enabled = !scope.search.saving),
            onClick = runSearch,
        )
    }
}

/** Per-source status chips (the dot carries the tone, the words say the same) and the login action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverSourceStatus(
    scope: CoverPickerScope,
    modifier: Modifier,
) {
    val search = scope.search
    // "Connected" here means "can be searched", matching CoverAccounts.canSearch: Bangumi and VNDB
    // are anonymous, so an anonymous Bangumi must not read「未连接」(that implied login was needed).
    // missingLogins only looks at requiresLogin sources, so it still names NextMoe/Hikarinagi.
    val connected = { source: CoverSourceMode ->
        !source.requiresLogin ||
            scope.state.coverAccounts[source]?.let { it.connected && !it.needsReauth } == true
    }
    Column(modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs), verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
            CoverSourceMode.active.forEach { source ->
                val attempted = source in search.sourceHits
                val failed = source in search.sourceFailed
                val hits = search.sourceHits[source] ?: 0
                val status = CoverHybridStatus.text(source, connected(source), search.loading, attempted, failed, hits)
                KireiStatusChip("${source.label} · $status", CoverHybridStatus.tone(search.loading, attempted, failed, hits), scope.colors)
            }
        }
        val missing = CoverHybridStatus.missingLogins(CoverSourceMode.active, connected)
        if (missing.isNotEmpty()) {
            KireiButton(
                CoverHybridStatus.loginActionLabel(missing),
                scope.colors,
                Modifier.padding(top = KireiSpacing.xxs),
                KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL, enabled = !search.saving),
                scope.viewModel::openCoverAccountsFromPicker,
            )
        }
    }
}

@Composable
private fun CoverPickerError(
    scope: CoverPickerScope,
    modifier: Modifier,
) {
    val error = scope.search.error ?: return
    val colors = scope.colors
    Row(
        modifier.fillMaxWidth().background(colors.dangerContainer, KireiShapes.md).padding(KireiSpacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.dangerSolid.accent, modifier = Modifier.size(ERROR_ICON_SIZE))
        Text(error, color = colors.text, style = AppleTypography.bodySmall)
    }
}

private val ERROR_ICON_SIZE = 20.dp

/** Other ways to set a cover: an image from the album or from files, or the engine's default cover. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverAlternatives(
    scope: CoverPickerScope,
    sidePane: Boolean,
    modifier: Modifier,
) {
    val search = scope.search
    val options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL, enabled = !search.saving && !search.loading)
    Column(modifier.fillMaxWidth()) {
        if (sidePane) Text("其他方式", color = scope.colors.secondary, style = AppleTypography.labelLarge, modifier = Modifier.padding(start = KireiSpacing.xxs))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (sidePane) Arrangement.spacedBy(KireiSpacing.xxs) else Arrangement.spacedBy(KireiSpacing.xxs, Alignment.CenterHorizontally),
        ) {
            KireiButton("从相册选择", scope.colors, options = options, onClick = scope.actions.chooseCoverFromAlbum)
            KireiButton("从手机文件选择", scope.colors, options = options, onClick = scope.actions.chooseCoverFromFiles)
            KireiButton("默认封面", scope.colors, options = options.copy(enabled = !search.saving), onClick = scope.viewModel::useDefaultCover)
        }
    }
}

/** 稍后处理 keeps the cover as it is; 使用此封面 saves the selected candidate (and version). */
@Composable
private fun CoverPickerActions(
    scope: CoverPickerScope,
    fill: Boolean,
    modifier: Modifier,
) {
    val search = scope.search
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KireiSpacing.sm, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        KireiButton(
            "稍后处理",
            scope.colors,
            if (fill) Modifier.weight(1f) else Modifier,
            KireiButtonOptions(style = KireiButtonStyle.PLAIN, enabled = !search.saving),
            scope.viewModel::dismissCoverSearch,
        )
        KireiButton(
            if (search.saving) "正在保存…" else "使用此封面",
            scope.colors,
            if (fill) Modifier.weight(PRIMARY_WEIGHT) else Modifier.widthIn(min = PRIMARY_MIN_WIDTH),
            KireiButtonOptions(enabled = search.selectedKey != null && !search.loading && !search.saving),
            scope.viewModel::confirmSelectedCover,
        )
    }
}

private const val PRIMARY_WEIGHT = 1.4f
private val PRIMARY_MIN_WIDTH = 160.dp
