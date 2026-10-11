package com.kireibox.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiLargeTitle
import com.kireibox.designsystem.components.KireiLargeTopBar
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.kireibox.designsystem.components.KireiTopBarSlots
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

// The one settings page template (plan §5 P3, spec §8): a large title that collapses into the
// compact centered title of a glass KireiLargeTopBar, over KireiListGroup content.

/** What every settings page shares: the state it shows, the shell chrome and the adaptive frame. */
@Suppress("LongParameterList") // one bundle so pages take a single parameter
internal data class SettingsPageContext(
    val state: KireiUiState,
    val colors: KireiColors,
    val haze: HazeState?,
    val viewModel: KireiViewModel,
    val actions: KireiActions,
    val adaptiveMetrics: AdaptiveUiMetrics,
    val reveal: SettingsRevealState = SettingsRevealState(),
    /** Opens the 游玩记录 sheet sorted by play time (the overview's 游戏时长 tile). */
    val openPlayTimeOverview: () -> Unit = {},
)

/**
 * One-shot scroll targets that survive a page change: the overview card's 「查看待确认封面」 asks
 * the cover page to bring its 待确认封面 group under the top bar once it opens, and a tapped search
 * result asks its page to scroll its row into view and highlight it (SettingsAnchor, r4 · S2).
 */
@Stable
internal class SettingsRevealState {
    var pendingCovers by mutableStateOf(false)
        private set

    var searchTarget by mutableStateOf<SettingsSearchTarget?>(null)
        private set

    fun requestPendingCovers() {
        pendingCovers = true
    }

    fun consumePendingCovers() {
        pendingCovers = false
    }

    fun requestSearchTarget(target: SettingsSearchTarget) {
        searchTarget = target
    }

    /** The anchor took it, or [target] went stale (its row never appeared); a newer target stays. */
    fun consumeSearchTarget(target: SettingsSearchTarget? = searchTarget) {
        if (searchTarget === target) searchTarget = null
    }
}

/**
 * Title (and on sub-pages the back action) of one settings page. [page] lets the page's rows
 * answer search results (SettingsAnchor); [onSearch] adds the root's search button to the collapsed bar.
 */
internal data class SettingsHeaderSpec(
    val title: String,
    val onBack: (() -> Unit)? = null,
    val page: KireiSettingsPage? = null,
    val onSearch: (() -> Unit)? = null,
)

/**
 * Where a large-title page draws. [left]/[right] are the content gutters from the window edges,
 * [top] the safe-area top (the bar starts there), [contentTop] the room above the large title
 * (the compact bar height on sub-pages), [bottom] the scroll clearance and [maxContentWidth] the
 * centered column cap.
 */
internal data class SettingsFrame(
    val left: Dp,
    val right: Dp,
    val top: Dp,
    val contentTop: Dp,
    val bottom: Dp,
    val maxContentWidth: Dp,
)

internal data class SettingsLayoutSpec(
    val header: SettingsHeaderSpec,
    val frame: SettingsFrame,
    val haze: HazeState?,
)

/** Vertical rhythm between list groups (spec §4: 24dp). */
internal val SettingsGroupSpacing: Modifier = Modifier.padding(bottom = KireiSpacing.groupGap)

/** Extra room between the large title and the first group caption (the title already adds xs). */
private val SettingsTitleGap = KireiSpacing.sm

/**
 * Settings root and sub-pages: the shared [SettingsLargeTitleLayout] inside the adaptive content
 * frame (cutout-symmetric gutters, capped settings width, dock clearance at the bottom).
 */
@Composable
internal fun SettingsPageScaffold(
    header: SettingsHeaderSpec,
    context: SettingsPageContext,
    scroll: ScrollState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val adaptiveMetrics = context.adaptiveMetrics
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame =
            SettingsFrame(
                left = adaptiveMetrics.contentFrame.x.dp,
                right = (maxWidth.value - adaptiveMetrics.contentFrame.x - adaptiveMetrics.contentFrame.w).dp,
                top = adaptiveMetrics.safeFrame.topDp.dp,
                // The root has no back button, so its title starts right under the safe area.
                contentTop = if (header.onBack == null) adaptiveMetrics.headerHeightDp.dp / 3f else KireiTopBarDefaults.CompactHeight,
                bottom = adaptiveMetrics.bottomContentPaddingDp.dp,
                maxContentWidth = adaptiveMetrics.settingsContentWidthDp.dp,
            )
        val host = rememberSettingsAnchorHost(header.page, context, scroll, frame)
        CompositionLocalProvider(LocalSettingsAnchorHost provides host) {
            SettingsLargeTitleLayout(SettingsLayoutSpec(header, frame, context.haze), context.colors, scroll, content)
        }
    }
}

/**
 * Scrolling column with a [KireiLargeTitle] as its first item and a [KireiLargeTopBar] drawn over
 * it: at rest only the back button floats above the large title; scrolling hands the title over
 * to the compact centered one on the frosted bar. The large title fades out over the first half of
 * the collapse and the compact one fades in over the second, so both titles are never visible at
 * once; a page that scrolls less than the collapse distance collapses over the scroll it has (spec
 * §10). The bar's slots line up with the content column.
 */
@Composable
internal fun SettingsLargeTitleLayout(
    spec: SettingsLayoutSpec,
    colors: KireiColors,
    scroll: ScrollState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val frame = spec.frame
    // A bar inside the shell's own hazeSource cannot blur that source (it would sample itself), so
    // the page content is its own source for the bar; null keeps the opaque fallback (no blur).
    val pageHaze = remember { HazeState() }
    val barHaze = pageHaze.takeIf { spec.haze != null }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = (maxWidth - frame.left - frame.right).coerceAtLeast(0.dp)
        val columnStart = frame.left + (available - available.coerceAtMost(frame.maxContentWidth)) / 2f
        Column(
            Modifier.fillMaxSize()
                .then(if (barHaze != null) Modifier.hazeSource(barHaze) else Modifier)
                .verticalScroll(scroll)
                .absolutePadding(left = frame.left, right = frame.right)
                .padding(top = frame.top + frame.contentTop, bottom = frame.bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = frame.maxContentWidth).fillMaxWidth()) {
                KireiLargeTitle(
                    spec.header.title,
                    colors,
                    Modifier.padding(bottom = SettingsTitleGap).graphicsLayer {
                        alpha = KireiTopBarDefaults.largeTitleAlpha(KireiTopBarDefaults.collapseFraction(scroll, density))
                    },
                )
                content()
            }
        }
        KireiLargeTopBar(
            title = spec.header.title,
            collapse = { KireiTopBarDefaults.collapseFraction(scroll, density) },
            colors = colors,
            modifier = Modifier.align(Alignment.TopCenter),
            slots =
                KireiTopBarSlots(
                    navigation =
                        spec.header.onBack?.let { back ->
                            { GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", colors, barHaze, back) }
                        },
                    // The root's search button: only once the field has scrolled away under the bar.
                    actions = spec.header.onSearch?.let { search -> { SettingsCollapsedSearchButton(scroll, colors, barHaze, search) } },
                    haze = barHaze,
                    topInset = frame.top,
                    // The slot adds xxs itself: the back button's edge sits on the column edge.
                    horizontalInset = (columnStart - KireiSpacing.xxs).coerceAtLeast(0.dp),
                ),
        )
    }
}
