package com.kireibox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSearchField
import com.kireibox.designsystem.components.KireiSearchFieldOptions
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.MotionMode
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

// Settings search UI (r4 · S2): the capsule under the root's large title, the results that replace
// the root groups while a query is typed, the empty state, and the root's back handling. Matching is
// SettingsSearch, the data SettingsSearchIndex, and the jump to a row SettingsSearchAnchors.

/** Search capsule under the large title, with 「取消」 beside it while the search is active. */
@Composable
internal fun SettingsSearchBar(
    search: SettingsSearchState,
    colors: KireiColors,
) {
    val focusManager = LocalFocusManager.current
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    // As tall as 取消's touch target from the start, so the page does not shift when it appears.
    Row(Modifier.fillMaxWidth().padding(bottom = KireiSpacing.lg).heightIn(min = SearchBarMinHeight), verticalAlignment = Alignment.CenterVertically) {
        KireiSearchField(
            value = search.query,
            onValueChange = { search.query = it },
            colors = colors,
            modifier = Modifier.weight(1f),
            options =
                KireiSearchFieldOptions(
                    placeholder = "搜索设置",
                    focusRequester = search.focusRequester,
                    onFocusChange = { search.focused = it },
                    // The keyboard's 搜索 key only puts the keyboard away: results update as you type.
                    onSearch = { focusManager.clearFocus() },
                    onComposingChange = { search.composing = it },
                ),
        )
        AnimatedVisibility(
            visible = search.active,
            enter = if (reducedMotion) EnterTransition.None else fadeIn() + expandHorizontally(),
            exit = if (reducedMotion) ExitTransition.None else fadeOut() + shrinkHorizontally(),
        ) {
            KireiButton(
                "取消",
                colors,
                Modifier.padding(start = KireiSpacing.xxs),
                KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.MEDIUM),
            ) {
                search.cancel()
                focusManager.clearFocus(force = true)
            }
        }
    }
}

private val SearchBarMinHeight = 48.dp

/**
 * Results for the current query: one list of rows (title, where it lives, the page's tile), best
 * match first, or the empty state with suggestions. Tapping a row hands it to [onOpen].
 */
@Composable
internal fun SettingsSearchResults(
    search: SettingsSearchState,
    context: SettingsPageContext,
    onOpen: (SettingsSearchEntry) -> Unit,
) {
    val colors = context.colors
    val focusManager = LocalFocusManager.current
    val homeMode = context.state.settings?.homePresentationMode ?: HomePresentationMode.IMMERSIVE
    val results = remember(search.resultsQuery) { SettingsSearch.search(SettingsSearchIndex.entries, search.resultsQuery) }
    SettingsSearchAnnouncement(if (results.isEmpty()) SETTINGS_SEARCH_EMPTY else "找到 ${results.size} 项", search.composing)
    if (results.isEmpty()) {
        SettingsSearchEmpty(colors) { suggestion -> search.fill(suggestion) }
    } else {
        KireiListGroup(colors, SettingsGroupSpacing, title = "找到 ${results.size} 项") {
            results.forEach { entry ->
                item(key = entry.id) {
                    val (icon, tile) = settingsSearchPageVisual(entry.page)
                    KireiListRow(
                        KireiListRowSpec(title = entry.title, subtitle = settingsSearchSubtitle(entry, homeMode), icon = icon, tile = tile, trailing = KireiRowTrailing.Chevron),
                        colors,
                        onClick = {
                            focusManager.clearFocus(force = true)
                            onOpen(entry)
                        },
                    )
                }
            }
        }
    }
    // The last results can scroll up above the keyboard.
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
}

private val EmptyIconSize = 40.dp
internal const val SETTINGS_SEARCH_EMPTY = "没有找到相关设置"

/** How long the result count has to hold before TalkBack hears it (not on every keystroke). */
private const val ANNOUNCE_SETTLE_MS = 500L

/**
 * One persistent, visually empty status node for TalkBack: 「找到 N 项」 or 「没有找到相关设置」, announced
 * politely once typing pauses (never mid-composition). It stays in place while the results and the empty
 * state swap below it, so every change is a text change TalkBack reliably announces.
 */
@Composable
private fun SettingsSearchAnnouncement(
    status: String,
    composing: Boolean,
) {
    var announced by remember { mutableStateOf("") }
    LaunchedEffect(status, composing) {
        if (!composing) {
            delay(ANNOUNCE_SETTLE_MS)
            announced = status
        }
    }
    Box(
        Modifier.fillMaxWidth().height(1.dp).clearAndSetSemantics {
            if (announced.isNotEmpty()) {
                contentDescription = announced
                liveRegion = LiveRegionMode.Polite
            }
        },
    )
}

/** 「没有找到相关设置」 and a few words that are known to find something (wrapping at large font scales). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSearchEmpty(
    colors: KireiColors,
    onSuggestion: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = KireiSpacing.md, vertical = KireiSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
    ) {
        Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(EmptyIconSize))
        // Announced by SettingsSearchAnnouncement; here it is the heading TalkBack lands on.
        Text(
            SETTINGS_SEARCH_EMPTY,
            color = colors.text,
            style = AppleTypography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = KireiSpacing.xs).semantics { heading() },
        )
        Text("换个说法试试，比如：", color = colors.secondary, style = AppleTypography.bodyMedium, textAlign = TextAlign.Center)
        FlowRow(
            Modifier.fillMaxWidth().padding(top = KireiSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
        ) {
            SettingsSearchIndex.suggestions.forEach { word ->
                KireiButton(word, colors, options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.SMALL)) { onSuggestion(word) }
            }
        }
    }
}

/**
 * A result's second line: where the setting lives (「外观 › 显示」), a page's own summary, or — for
 * an immersive-only setting while the home uses 组件模式 — that it needs the immersive home.
 */
internal fun settingsSearchSubtitle(
    entry: SettingsSearchEntry,
    homeMode: HomePresentationMode,
): String =
    when {
        entry.subtitle != null -> entry.subtitle
        entry.location.scope != SettingsSearchScope.ALWAYS && homeMode == HomePresentationMode.COMPONENTS -> "${entry.location.breadcrumb} · 需使用沉浸式首页"
        else -> entry.location.breadcrumb
    }

/** The tile of a result: its page's root category icon and hue, so a result looks like where it leads. */
internal fun settingsSearchPageVisual(page: KireiSettingsPage): Pair<ImageVector, KireiTileColor> =
    when (page) {
        KireiSettingsPage.APPEARANCE -> Icons.Rounded.Palette to KireiTileColor.PINK
        KireiSettingsPage.INTERFACE -> Icons.Rounded.Home to KireiTileColor.PURPLE
        KireiSettingsPage.LIBRARY_HUB, KireiSettingsPage.LIBRARY -> Icons.Rounded.GridView to KireiTileColor.BLUE
        KireiSettingsPage.COVERS -> Icons.Rounded.CollectionsBookmark to KireiTileColor.TEAL
        KireiSettingsPage.COVER_ACCOUNTS, KireiSettingsPage.PLAYTIME_SYNC -> Icons.Rounded.AccountCircle to KireiTileColor.INDIGO
        KireiSettingsPage.ENGINES,
        KireiSettingsPage.KRKR,
        KireiSettingsPage.ONS,
        KireiSettingsPage.ARTEMIS,
        KireiSettingsPage.TYRANO,
        KireiSettingsPage.WINLATOR,
        -> Icons.Rounded.Gamepad to KireiTileColor.ORANGE
        KireiSettingsPage.PERMISSIONS -> Icons.Rounded.Folder to KireiTileColor.GREEN
        KireiSettingsPage.DIAGNOSTICS -> Icons.Rounded.Terminal to KireiTileColor.SLATE
        KireiSettingsPage.ABOUT -> Icons.Rounded.Info to KireiTileColor.GRAY
        // Only 快速设置 lives on the root: the guide's own sparkle, in the 个性化 family's purple (it sets
        // 外观 and 首页), like the purple suggestion card — not INDIGO, which is 检查更新 (关于).
        KireiSettingsPage.ROOT -> Icons.Rounded.AutoAwesome to KireiTileColor.PURPLE
    }

/**
 * Opens a result: 快速设置 opens its sheet on the root; anything else opens its page and, for a
 * row, leaves a one-shot target the row picks up (SettingsAnchor) to scroll into view and pulse.
 */
internal fun openSettingsSearchResult(
    entry: SettingsSearchEntry,
    context: SettingsPageContext,
    openQuickSetup: () -> Unit,
) {
    if (entry.anchor == SettingsSearchIndex.QUICK_SETUP_ANCHOR) {
        openQuickSetup()
        return
    }
    val target = settingsSearchTarget(entry, context.state.settings?.homePresentationMode ?: HomePresentationMode.IMMERSIVE)
    if (target.anchor != null) context.reveal.requestSearchTarget(target)
    // Back from the result's page returns to these results, even from a second-level page (扫描目录, 来源账号).
    context.viewModel.openSettingsShortcut(target.page)
}

/** The root bar's trailing search button, shown once the large title (and the capsule) scrolled away. */
@Composable
internal fun RowScope.SettingsCollapsedSearchButton(
    scroll: ScrollState,
    colors: KireiColors,
    haze: HazeState?,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val collapsed by remember(scroll, density) { derivedStateOf { KireiTopBarDefaults.collapseFraction(scroll, density) >= 1f } }
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    AnimatedVisibility(
        visible = collapsed,
        enter = if (reducedMotion) EnterTransition.None else fadeIn(),
        exit = if (reducedMotion) ExitTransition.None else fadeOut(),
    ) {
        GlassIconButton(Icons.Rounded.Search, "搜索设置", colors, haze, onClick)
    }
}
