package com.kireibox.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.update.UpdateUiState
import com.yuki.yukihub.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Settings root (plan §5 P3 IA, r4 S1/S2): large title, the search capsule, overview card, the
// one-time 快速设置 suggestion, then 个性化 (外观 · 首页) / 游戏 / 系统 / 关于. Every category row
// has its own tile hue (spec §2.3), so no two rows on the root look alike. A query replaces the
// overview and groups with search results (SettingsSearchPage.kt).

@Composable
internal fun SettingsRootPage(context: SettingsPageContext) {
    val state = context.state
    val colors = context.colors
    val viewModel = context.viewModel
    val adaptiveMetrics = context.adaptiveMetrics
    val categoryPages =
        remember {
            listOf(
                KireiSettingsPage.APPEARANCE,
                KireiSettingsPage.INTERFACE,
                KireiSettingsPage.LIBRARY_HUB,
                KireiSettingsPage.COVERS,
                KireiSettingsPage.COVER_ACCOUNTS,
                KireiSettingsPage.ENGINES,
                KireiSettingsPage.PERMISSIONS,
                KireiSettingsPage.DIAGNOSTICS,
                KireiSettingsPage.ABOUT,
            )
        }
    val categoryFocusRequesters = remember { categoryPages.associateWith { FocusRequester() } }
    var lastFocusedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    // r4 S2: the query survives a visit to a result's page, so the root can come back showing
    // results; the category rows (and their focus requesters) only exist without a query.
    val search = rememberSettingsSearchState()
    LaunchedEffect(Unit) {
        lastFocusedCategory
            ?.takeUnless { search.searching }
            ?.let { saved -> categoryPages.firstOrNull { it.name == saved } }
            ?.let { categoryFocusRequesters[it] }
            ?.requestFocus()
    }
    val openCategory: (KireiSettingsPage) -> Unit = { page ->
        lastFocusedCategory = page.name
        // A second-level page (来源账号) opened from here is a shortcut: back returns to this root.
        if (KireiBackPolicy.settingsParent(page) == KireiSettingsPage.ROOT) viewModel.openSettingsPage(page) else viewModel.openSettingsShortcut(page)
    }
    val rows = SettingsCategoryRowContext(colors, categoryFocusRequesters, adaptiveMetrics.orientation, openCategory)
    // 快速设置: suggested once (banner) until finished, skipped or dismissed; afterwards it is a
    // search result (「快速设置」) — the overview card's button is 同步数据.
    var quickSetupOpen by rememberSaveable { mutableStateOf(false) }
    val quickSetupSuggested = state.settings?.quickSetupDone == false
    // r4 S2: the search capsule leads the page; while a query is typed its results replace the groups.
    val scroll = rememberScrollState()
    SettingsSearchEffects(search, scroll)
    val header = SettingsHeaderSpec(settingsPageTitle(KireiSettingsPage.ROOT), onSearch = rememberSettingsSearchShortcut(search, scroll))
    SettingsPageScaffold(header, context, scroll) {
        SettingsSearchBar(search, colors)
        if (search.searching) {
            SettingsSearchResults(search, context) { entry -> openSettingsSearchResult(entry, context) { quickSetupOpen = true } }
        } else {
            SettingsOverviewCard(
                state,
                colors,
                compact = adaptiveMetrics.orientation == HomeOrientation.LANDSCAPE,
                SettingsOverviewActions(
                    onOpenPendingCovers = {
                        context.reveal.requestPendingCovers()
                        openCategory(KireiSettingsPage.COVERS)
                    },
                    onSync = viewModel::syncPlaytimeNow,
                    onOpenPlayTime = context.openPlayTimeOverview,
                    onOpenSync = { viewModel.openSettingsShortcut(KireiSettingsPage.PLAYTIME_SYNC) },
                ),
            )
            Spacer(Modifier.height(KireiSpacing.groupGap))
            if (quickSetupSuggested) {
                QuickSetupSuggestion(colors, onStart = { quickSetupOpen = true }, onDismiss = { viewModel.setQuickSetupDone(true) })
            }
            SettingsPersonalGroup(state, rows)
            SettingsGamesGroup(state, rows)
            SettingsSystemGroup(rows)
            SettingsAboutGroup(state, viewModel, rows)
        }
    }
    if (quickSetupOpen) {
        QuickSetupSheet(context) {
            quickSetupOpen = false
            viewModel.setQuickSetupDone(true)
        }
    }
}

/**
 * 个性化 (r4 S1): 外观 (theme, accent, display and orientation; the current theme as its value) and
 * 首页 (home style, shown games and immersive effects; the current home style as its value). Each
 * setting lives on exactly one of the two pages (SettingsIaStructureTest).
 */
@Composable
private fun SettingsPersonalGroup(
    state: KireiUiState,
    rows: SettingsCategoryRowContext,
) {
    KireiListGroup(rows.colors, SettingsGroupSpacing, title = "个性化") {
        item {
            SettingsCategoryRow(
                KireiSettingsPage.APPEARANCE,
                SettingsCategoryRowSpec(Icons.Rounded.Palette, "外观", null, KireiTileColor.PINK, value = state.settings?.themeMode?.displayName),
                rows,
            )
        }
        item {
            SettingsCategoryRow(
                KireiSettingsPage.INTERFACE,
                SettingsCategoryRowSpec(Icons.Rounded.Home, "首页", null, KireiTileColor.PURPLE, value = state.settings?.homePresentationMode?.displayName),
                rows,
            )
        }
    }
}

/** 游戏: 游戏库, 封面与元数据 (with the attention badge), 来源账号 (a shortcut to its page) and 全局引擎设置. */
@Composable
private fun SettingsGamesGroup(
    state: KireiUiState,
    rows: SettingsCategoryRowContext,
) {
    // Review + failed tasks: a failed automatic match still needs the user, so it lights the row.
    val attention = state.coverAttentionCount
    // No footer: the attention badge explains itself whenever it shows.
    KireiListGroup(rows.colors, SettingsGroupSpacing, title = "游戏") {
        item {
            SettingsCategoryRow(KireiSettingsPage.LIBRARY_HUB, SettingsCategoryRowSpec(Icons.Rounded.GridView, "游戏库", null, KireiTileColor.BLUE), rows)
        }
        item {
            SettingsCategoryRow(
                KireiSettingsPage.COVERS,
                SettingsCategoryRowSpec(
                    Icons.Rounded.CollectionsBookmark,
                    "封面与元数据",
                    if (attention > 0) "$attention 个封面待处理" else null,
                    KireiTileColor.TEAL,
                    badgeCount = attention,
                ),
                rows,
            )
        }
        item {
            SettingsCategoryRow(
                KireiSettingsPage.COVER_ACCOUNTS,
                SettingsCategoryRowSpec(Icons.Rounded.AccountCircle, "封面来源账号", null, KireiTileColor.INDIGO, value = coverAccountSummary(state)),
                rows,
            )
        }
        item {
            SettingsCategoryRow(KireiSettingsPage.ENGINES, SettingsCategoryRowSpec(Icons.Rounded.Gamepad, "全局引擎设置", null, KireiTileColor.ORANGE), rows)
        }
    }
}

/** 系统: 存储与权限 and 日志与诊断. */
@Composable
private fun SettingsSystemGroup(rows: SettingsCategoryRowContext) {
    KireiListGroup(rows.colors, SettingsGroupSpacing, title = "系统") {
        item {
            SettingsCategoryRow(
                KireiSettingsPage.PERMISSIONS,
                SettingsCategoryRowSpec(Icons.Rounded.Folder, "存储与权限", null, KireiTileColor.GREEN),
                rows,
            )
        }
        item {
            SettingsCategoryRow(
                KireiSettingsPage.DIAGNOSTICS,
                SettingsCategoryRowSpec(Icons.Rounded.Terminal, "日志与诊断", null, KireiTileColor.SLATE),
                rows,
            )
        }
    }
}

/** 关于: 关于 KireiBox and the manual 检查更新 command (the current version as its subtitle). */
@Composable
private fun SettingsAboutGroup(
    state: KireiUiState,
    viewModel: KireiViewModel,
    rows: SettingsCategoryRowContext,
) {
    KireiListGroup(rows.colors, SettingsGroupSpacing, title = "关于") {
        item {
            SettingsCategoryRow(KireiSettingsPage.ABOUT, SettingsCategoryRowSpec(Icons.Rounded.Info, "关于 KireiBox", null, KireiTileColor.GRAY), rows)
        }
        item {
            KireiListRow(
                updateRowSpec(state.updateState, KireiTileColor.INDIGO, "当前版本 ${BuildConfig.VERSION_NAME}").withCategoryMetrics(resolveSettingsCategoryRowMetrics(rows.orientation)),
                rows.colors,
                onClick = { viewModel.checkForUpdates(manual = true) },
            )
        }
    }
}

/**
 * 检查更新 command row shared by the root and the About page: ACTION tone like every command that
 * runs at once (清除无结果记录, 检查访问权限). [subtitle] is the current version on the root and the
 * last check on 关于, whose header already shows the version. There is no 「新版本」 badge: an
 * available, downloading or ready release always raises the modal update prompt, and dismissing it
 * (稍后) ignores that release, so a badge on this row could never be seen.
 */
internal fun updateRowSpec(
    update: UpdateUiState,
    tile: KireiTileColor,
    subtitle: String,
): KireiListRowSpec =
    KireiListRowSpec(
        title = "检查更新",
        subtitle = subtitle,
        icon = Icons.Rounded.Update,
        tile = tile,
        tone = KireiRowTone.ACTION,
        trailing = if (update is UpdateUiState.Checking) KireiRowTrailing.Value("正在检查…", chevron = false) else KireiRowTrailing.None,
    )

/** 关于's 检查更新 subtitle: when the last check ran (the stored automatic or manual check time). */
internal fun lastUpdateCheckLabel(checkedAtMillis: Long?): String =
    if (checkedAtMillis == null || checkedAtMillis <= 0L) {
        "尚未检查过更新"
    } else {
        "上次检查：${SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(checkedAtMillis))}"
    }

/**
 * What the overview card can open: 待确认封面, and 快速设置 when [onQuickSetup] is set (null while
 * the root shows the one-time 快速设置 suggestion instead, so only one entry is visible at a time).
 */
internal data class SettingsOverviewActions(
    val onOpenPendingCovers: () -> Unit,
    val onSync: () -> Unit = {},
    val onOpenPlayTime: () -> Unit = {},
    val onOpenSync: () -> Unit = {},
)

/** A root category row: tile, title, subtitle and either the current value, a badge or a chevron. */
internal data class SettingsCategoryRowSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val tile: KireiTileColor,
    val badgeCount: Int = 0,
    val value: String? = null,
)

/** What every root category row shares: palette, focus restoration, row metrics and navigation. */
internal data class SettingsCategoryRowContext(
    val colors: KireiColors,
    val focusRequesters: Map<KireiSettingsPage, FocusRequester>,
    val orientation: HomeOrientation,
    val open: (KireiSettingsPage) -> Unit,
)

@Composable
internal fun SettingsCategoryRow(
    page: KireiSettingsPage,
    spec: SettingsCategoryRowSpec,
    context: SettingsCategoryRowContext,
) {
    val trailing =
        when {
            spec.badgeCount > 0 -> KireiRowTrailing.Badge(spec.badgeCount)
            spec.value != null -> KireiRowTrailing.Value(spec.value)
            else -> KireiRowTrailing.Chevron
        }
    KireiListRow(
        spec =
            KireiListRowSpec(title = spec.title, subtitle = spec.subtitle, icon = spec.icon, tile = spec.tile, trailing = trailing)
                .withCategoryMetrics(resolveSettingsCategoryRowMetrics(context.orientation)),
        colors = context.colors,
        modifier = Modifier.focusRequester(context.focusRequesters.getValue(page)),
        onClick = { context.open(page) },
    )
}
