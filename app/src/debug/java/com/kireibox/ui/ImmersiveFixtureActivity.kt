package com.kireibox.ui
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.kireibox.designsystem.AppleDesignTheme
import com.kireibox.designsystem.kireiColorsFromTokens
import com.yuki.yukihub.settings.SpotlightSource
import android.graphics.Color as AndroidColor

class ImmersiveFixtureActivity : ComponentActivity() {
    /** "dark" extra (default true): renders the fixture with the light tokens when false. */
    private val fixtureDark: Boolean
        get() = intent.getBooleanExtra(EXTRA_DARK, true)

    private fun applyFixtureSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())
            isAppearanceLightNavigationBars = !fixtureDark
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFixtureSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ImmersiveVisualFixture.enabled(intent)) {
            finish()
            return
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes =
                window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyFixtureSystemBars()
        val viewModel = ViewModelProvider(this)[KireiViewModel::class.java]
        val fixtureGames =
            ImmersiveVisualFixture.games(
                this,
                nsfwCovers = intent.getBooleanExtra(ImmersiveVisualFixture.EXTRA_NSFW_COVERS, false),
                firstGamePlayMinutes = ImmersiveVisualFixture.playTimeMinutes(intent),
                count = ImmersiveVisualFixture.gameCount(intent),
                firstTitle = ImmersiveVisualFixture.firstTitle(intent),
            )
        val dark = fixtureDark
        val fixtureFx = ImmersiveVisualFixture.resolvedFx(intent)
        setContent {
            AppleDesignTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalNsfwBlurEnabled provides
                        intent.getBooleanExtra(
                            ImmersiveVisualFixture.EXTRA_NSFW_GLOBAL_ENABLED,
                            true,
                        ),
                    LocalImmersiveFx provides fixtureFx,
                ) {
                    // Mirrors the app's token palette (graphite + the default purple accent) for the requested theme.
                    val colors = remember(dark) { kireiColorsFromTokens(isDark = dark) }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val layoutDirection = LocalLayoutDirection.current
                        val physicalWidth = with(density) { maxWidth.toPx() }
                        val physicalHeight = with(density) { maxHeight.toPx() }
                        val safeLeft = WindowInsets.safeDrawing.getLeft(density, layoutDirection).toFloat()
                        val safeTop = WindowInsets.safeDrawing.getTop(density).toFloat()
                        val safeRight = WindowInsets.safeDrawing.getRight(density, layoutDirection).toFloat()
                        val safeBottom = WindowInsets.safeDrawing.getBottom(density).toFloat()
                        val exactCanvas = intent.getBooleanExtra("exact_canvas", false)
                        val frozenTrackProgress = ImmersiveVisualFixture.frozenTrackProgress(intent)
                        ImmersiveHome(
                            games = fixtureGames,
                            source = SpotlightSource.ALL,
                            colors = colors,
                            actions =
                                KireiActions(
                                    chooseDirectory = {},
                                    pickAddGame = {},
                                    chooseAndroidApp = {},
                                    chooseExternalShortcuts = {},
                                    chooseCoverFromAlbum = {},
                                    chooseCoverFromFiles = {},
                                    launchGame = {},
                                    pinGameShortcut = {},
                                    selectedGame = {},
                                    launchKrkrNative = {},
                                    checkPermissions = {},
                                    repairPermission = { _ -> },
                                    restoreRemovedGame = {},
                                    copyLogs = {},
                                    exportLogs = {},
                                    setDarkMode = {},
                                    installUpdate = {},
                                ),
                            renderKind =
                                if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                    HomeRenderKind.IMMERSIVE_LANDSCAPE
                                } else {
                                    HomeRenderKind.IMMERSIVE_PORTRAIT
                                },
                            safeBounds =
                                ImmersiveSafeBounds(
                                    leftPx = if (exactCanvas) 0f else safeLeft,
                                    topPx = if (exactCanvas) 0f else safeTop,
                                    rightPx = if (exactCanvas) physicalWidth else physicalWidth - safeRight,
                                    bottomPx = if (exactCanvas) physicalHeight else physicalHeight - safeBottom,
                                ),
                            // "reduced_motion" / "reduced_transparency" and the IMMERSIVE_REDUCED_* extras are one switch.
                            reducedMotion = ImmersiveVisualFixture.reducedMotion(intent),
                            reducedTransparency = ImmersiveVisualFixture.reducedTransparency(intent),
                            debugFrozenTrackProgress = frozenTrackProgress,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_DARK = "dark"
    }
}
