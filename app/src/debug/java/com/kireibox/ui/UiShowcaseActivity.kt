package com.kireibox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.kireibox.designsystem.AppleDesignTheme
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.LocalAppleColors
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiLargeTitle
import com.kireibox.designsystem.components.KireiLargeTopBar
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.kireibox.designsystem.components.KireiTopBarSlots
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.MotionMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Debug-only gallery of the alpha.17 design-system components (doc/2026-10-02-ui-redesign-plan.md
 * §4.3), for adb screenshots without library data:
 *
 * adb shell am start -S -n com.kireibox.app.debug/com.kireibox.ui.UiShowcaseActivity \
 *   --ez dark true --es accent sakura --ez contrast false [--es open sheet|dialog|confirm|menu|notice]
 *
 * `--es page tiles --es tiles legacy|solid|tonal|current` compares settings tile palettes.
 *
 * `--es clip <text>` copies text to the clipboard and finishes (paste it into a field of the real app).
 *
 * Real pages on fixture data (ShowcasePageRouter, ShowcasePages.ids): `--es page library | library-edit |
 * library-empty | task-center | components-home | dashboard-editor | pickers (= picker-dir) | picker-dir |
 * picker-xp3 | picker-app | picker-shortcuts | sheet-actions | sheet-rename | sheet-remove |
 * sheet-overview | sheet-add | accounts`; `sheet-actions` also takes `--ei game <fixture index>`.
 *
 * `--es page krkr` instead shows the real per-game KRKR settings page for a fixture KIRIKIRI game
 * (the emulator library has no KRKR game). Leave it with back; 保存 would store that fixture's
 * KRKR settings, so screenshots never press it.
 */
class UiShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshot helper: `--es clip 颜色` puts text on the clipboard and finishes, so a field in
        // the real app can be filled with Chinese text (adb's `input text` only types ASCII).
        intent.getStringExtra(EXTRA_CLIP)?.let { text ->
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("showcase", text))
            finish()
            return
        }
        enableEdgeToEdge()
        val dark = intent.getBooleanExtra(EXTRA_DARK, true)
        val accent = AccentChoice.fromStored(intent.getStringExtra(EXTRA_ACCENT))
        val contrast = intent.getBooleanExtra(EXTRA_CONTRAST, false)
        val reducedMotion = intent.getBooleanExtra(EXTRA_REDUCED_MOTION, false)
        val initialOverlay = ShowcaseOverlay.fromExtra(intent.getStringExtra(EXTRA_OPEN))
        val page = intent.getStringExtra(EXTRA_PAGE)
        val krkrPage = page.equals(PAGE_KRKR, ignoreCase = true)
        val tilesPage = page.equals(PAGE_TILES, ignoreCase = true)
        val fixturePage = ShowcasePages.handles(page)
        val tileStyle = ShowcaseTileStyle.entries.firstOrNull { it.name.equals(intent.getStringExtra(EXTRA_TILES), ignoreCase = true) } ?: ShowcaseTileStyle.CURRENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        setContent {
            AppleDesignTheme(darkTheme = dark, accent = accent, highContrast = contrast) {
                CompositionLocalProvider(LocalResolvedMotionMode provides if (reducedMotion) MotionMode.REDUCED else MotionMode.POLISHED) {
                    val apple = LocalAppleColors.current
                    val colors = remember(apple) { kireiColorsOf(apple) }
                    when {
                        krkrPage -> ShowcaseKrkrGamePage(this, colors)
                        tilesPage -> ShowcaseTilesPage(colors, tileStyle)
                        fixturePage -> ShowcasePageRouter(this, intent, page.orEmpty(), colors)
                        else -> UiShowcaseScreen(colors, initialOverlay, onBack = ::finish)
                    }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_DARK = "dark"
        const val EXTRA_ACCENT = "accent"
        const val EXTRA_CONTRAST = "contrast"
        const val EXTRA_REDUCED_MOTION = "reduced_motion"
        const val EXTRA_OPEN = "open"
        const val EXTRA_PAGE = "page"
        const val PAGE_KRKR = "krkr"
        const val PAGE_TILES = "tiles"
        const val EXTRA_TILES = "tiles"
        const val EXTRA_CLIP = "clip"
    }
}

/**
 * The production KrkrGameSettingsPage for the first fixture game (KIRIKIRI, bundled cover). The
 * editor is opened through the view model like the game sheet does, so back (system or top bar)
 * closes it through closeKrkrEditor; once it has closed, the showcase finishes.
 */
@Composable
private fun ShowcaseKrkrGamePage(
    activity: ComponentActivity,
    colors: KireiColors,
) {
    val viewModel = remember { ViewModelProvider(activity)[KireiViewModel::class.java] }
    val game = remember { ImmersiveVisualFixture.games(activity).first { it.engine == EngineType.KIRIKIRI } }
    val haze = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !colors.highContrast) HazeState() else null }
    val state by viewModel.state.collectAsState()
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.openKrkrEditor(game) }
    LaunchedEffect(state.krkrEditorGame) {
        if (state.krkrEditorGame != null) {
            opened = true
        } else if (opened) {
            activity.finish()
        }
    }
    KrkrGameSettingsPage(game, state, colors, haze, viewModel)
}

/** Overlay the showcase opens on launch (`--es open …`) or from its buttons. */
internal enum class ShowcaseOverlay {
    NONE,
    SHEET,
    DIALOG,
    CONFIRM,
    MENU,
    NOTICE,
    ;

    companion object {
        fun fromExtra(value: String?): ShowcaseOverlay = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: NONE
    }
}

@Composable
private fun UiShowcaseScreen(
    colors: KireiColors,
    initialOverlay: ShowcaseOverlay,
    onBack: () -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var overlay by remember { mutableStateOf(initialOverlay) }
    val haze = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !colors.highContrast) HazeState() else null }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier.fillMaxSize()
                .then(if (haze != null) Modifier.hazeSource(haze) else Modifier)
                .verticalScroll(scroll)
                .padding(horizontal = AdaptiveLayoutConstants.PAGE_GUTTER_DP.dp),
        ) {
            Spacer(Modifier.statusBarsPadding().height(KireiTopBarDefaults.CompactHeight))
            KireiLargeTitle("组件库", colors)
            Text(
                "alpha.17 设计系统 · ${if (colors.isDark) "深色" else "浅色"}${if (colors.highContrast) " · 增强对比度" else ""}",
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                modifier = Modifier.padding(bottom = KireiSpacing.xl),
            )
            ShowcaseSettingsGroups(colors)
            ShowcaseButtons(colors)
            ShowcaseChipsAndBadges(colors)
            ShowcaseInputs(colors)
            ShowcaseLoading(colors)
            ShowcaseEmptyState(colors) { overlay = ShowcaseOverlay.NOTICE }
            ShowcaseOverlays(colors, overlay) { overlay = it }
            Spacer(Modifier.navigationBarsPadding().height(KireiSpacing.xxxl))
        }
        KireiLargeTopBar(
            title = "组件库",
            collapse = { KireiTopBarDefaults.collapseFraction(scroll, density) },
            colors = colors,
            modifier = Modifier.align(Alignment.TopCenter),
            slots =
                KireiTopBarSlots(
                    navigation = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.accent) } },
                    actions = {
                        ShowcaseTopBarMenu(
                            colors = colors,
                            expanded = overlay == ShowcaseOverlay.MENU,
                            onExpandedChange = { overlay = if (it) ShowcaseOverlay.MENU else ShowcaseOverlay.NONE },
                        )
                    },
                    haze = haze,
                ),
        )
        ShowcaseModalLayers(colors, haze, overlay) { overlay = ShowcaseOverlay.NONE }
    }
}
