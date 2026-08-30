package com.kireibox.ui

import android.app.Activity
import android.content.res.Configuration
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.LruCache
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yuki.yukihub.BuildConfig
import com.kireibox.krkr.KrkrGameSettings
import com.kireibox.update.UpdateUiState
import com.kireibox.cover.CoverQueueStatus
import com.kireibox.cover.CoverQueueUiState
import com.kireibox.cover.CoverTaskContainer
import com.kireibox.cover.CoverTaskPresentationPolicy
import com.kireibox.cover.CoverTaskRowAction
import com.kireibox.cover.CoverTaskStatus
import com.apps.theme.AppleDesignTheme
import com.apps.theme.AppleMotion
import com.apps.theme.AppleTypography
import com.apps.theme.LocalAppleColors
import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.data.GameSearchIndex
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.YukiSettingsStore
import com.yuki.yukihub.settings.MotionDecisionInputs
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.MotionModePolicy
import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.OrientationMode
import com.yuki.yukihub.settings.DashboardStyles
import com.yuki.yukihub.settings.DashboardWidgetSize
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.LandscapeHomeStyle
import com.yuki.yukihub.settings.SpotlightSelector
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import com.yuki.yukihub.settings.TrendMetric
import com.yuki.yukihub.settings.TrendRange
import com.yuki.yukihub.settings.TrendVisualStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private val spotlightProcessSeed = System.nanoTime()

/** Reads the platform cutout rectangles in window coordinates for the header policy. */
private fun displayCutoutBoundsDp(view: View, density: androidx.compose.ui.unit.Density): HeaderRectDp? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val rects = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
    if (rects.isEmpty()) return null
    val left = rects.minOf { it.left }
    val top = rects.minOf { it.top }
    val right = rects.maxOf { it.right }
    val bottom = rects.maxOf { it.bottom }
    return with(density) {
        HeaderRectDp(
            left.toDp().value,
            top.toDp().value,
            right.toDp().value,
            bottom.toDp().value,
        )
    }
}

private fun headerRectDp(
    coordinates: androidx.compose.ui.layout.LayoutCoordinates,
    density: androidx.compose.ui.unit.Density,
): HeaderRectDp {
    val bounds = coordinates.boundsInWindow()
    return with(density) {
        HeaderRectDp(
            bounds.left.toDp().value,
            bounds.top.toDp().value,
            bounds.right.toDp().value,
            bounds.bottom.toDp().value,
        )
    }
}

data class KireiActions(
    val chooseDirectory: () -> Unit,
    val chooseLaunchFile: () -> Unit,
    val chooseAndroidApp: () -> Unit,
    val chooseCoverFromAlbum: () -> Unit,
    val chooseCoverFromFiles: () -> Unit,
    val launchGame: (Game) -> Unit,
    val selectedGame: (Game) -> Unit,
    val launchKrkrNative: () -> Unit,
    val checkPermissions: () -> Unit,
    val repairPermission: (String?) -> Unit,
    val copyLogs: () -> Unit,
    val exportLogs: () -> Unit,
    val setDarkMode: (Boolean) -> Unit,
    val showAbout: () -> Unit,
    val installUpdate: (String) -> Unit,
)

data class KireiPrompt(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String? = null,
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {}
)

enum class BuiltinFileBrowserMode { DIRECTORY, LAUNCH_FILE }

data class BuiltinFileBrowserRequest(
    val mode: BuiltinFileBrowserMode,
    val startPath: String,
    val appPath: String
)

private fun ResponsiveWindowClass.homeSizeClass(): HomeSizeClass = when (this) {
    ResponsiveWindowClass.PHONE_PORTRAIT,
    ResponsiveWindowClass.PHONE_LANDSCAPE,
    -> HomeSizeClass.COMPACT
    ResponsiveWindowClass.PAD_PORTRAIT,
    ResponsiveWindowClass.PAD_LANDSCAPE,
    -> HomeSizeClass.WIDE
}

/** Bridges the four-way runtime classifier to legacy two-value task/presentation APIs. */
private fun ResponsiveWindowClass.legacyLayoutMode(): KireiLayoutMode = when (homeSizeClass()) {
    HomeSizeClass.COMPACT -> KireiLayoutMode.PHONE_PORTRAIT
    HomeSizeClass.WIDE -> KireiLayoutMode.WIDE
}

private val LocalResolvedMotionMode = staticCompositionLocalOf { MotionMode.REDUCED }

/**
 * True when system-bar icons must be light (white) for the currently composed page:
 * the immersive home always paints a dark backdrop, so sheets and menus keep white icons
 * even in light theme. Provided at the app root and consumed by overlays that would
 * otherwise reset the window appearance from their own theme.
 */
private val LocalSystemBarIconTone = staticCompositionLocalOf { false }

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

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
    val dark = settings?.darkMode ?: true
    val immersiveHome = state.tab == KireiTab.HOME &&
        (settings?.homePresentationMode ?: HomePresentationMode.IMMERSIVE) == HomePresentationMode.IMMERSIVE
    LaunchedEffect(dark, immersiveHome) {
        onSystemBarIconsLight(SystemBarVisibilityPolicy.statusBarIconsLight(dark, immersiveHome))
    }
    AppleDesignTheme(darkTheme = dark) {
        val context = LocalContext.current
        val orientationMode = settings?.orientationMode ?: OrientationMode.AUTO
        LaunchedEffect(orientationMode, context) {
            context.findActivity()?.requestedOrientation = when (orientationMode) {
                OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        val resolvedMotion = remember(settings?.motionMode, context) {
            resolveMotionMode(context, settings?.motionMode ?: MotionMode.AUTO)
        }
        val reducedMotion = resolvedMotion == MotionMode.REDUCED
        CompositionLocalProvider(LocalResolvedMotionMode provides resolvedMotion) {
        CompositionLocalProvider(
            LocalSystemBarIconTone provides SystemBarVisibilityPolicy.statusBarIconsLight(dark, immersiveHome),
        ) {
        val apple = LocalAppleColors.current
        val colorDuration = if (reducedMotion) 0 else 220
        val background by animateColorAsState(apple.background, tween(colorDuration), label = "backgroundTone")
        val surface by animateColorAsState(apple.surface, tween(colorDuration), label = "surfaceTone")
        val surfaceRaised by animateColorAsState(apple.surfaceAlt, tween(colorDuration), label = "raisedTone")
        val text by animateColorAsState(apple.textPrimary, tween(colorDuration), label = "textTone")
        val secondary by animateColorAsState(apple.textSecondary, tween(colorDuration), label = "secondaryTone")
        val accent by animateColorAsState(apple.primary, tween(colorDuration), label = "accentTone")
        val colors = KireiColors(
            background = background,
            surface = surface,
            surfaceRaised = surfaceRaised,
            text = text,
            secondary = secondary,
            accent = accent,
            success = if (dark) Color(0xFF78E7A0) else Color(0xFF4FCC7A),
            outline = if (dark) Color(0xFF3F3C49) else Color(0xFFD9D5E4),
            onAccent = if (dark) Color(0xFF211A4A) else Color.White,
            danger = apple.danger,
            isDark = dark
        )
        var renameGame by remember { mutableStateOf<Game?>(null) }
        var renameText by remember { mutableStateOf("") }
        var removeGame by remember { mutableStateOf<Game?>(null) }
        var removingGameId by remember { mutableStateOf<Long?>(null) }
        var homeEditorProfile by remember { mutableStateOf<DashboardProfile?>(null) }
        val homeSpotlightPage by viewModel.homeSpotlightPage.collectAsState()
        var showLibraryOverview by remember { mutableStateOf(false) }
        var libraryOverviewSort by remember { mutableStateOf(LibraryOverviewSort.RECENT) }
        val uiScope = rememberCoroutineScope()
        val rootBackHasPriorityScreen = prompt == null &&
            fileBrowserRequest == null &&
            state.xp3Picker == null &&
            state.androidAppPicker == null &&
            state.krkrEditorGame == null &&
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
                val safeWidth = maxWidth - safeDrawingPadding.calculateLeftPadding(layoutDirection) -
                    safeDrawingPadding.calculateRightPadding(layoutDirection)
                val safeHeight = maxHeight - safeDrawingPadding.calculateTopPadding() -
                    safeDrawingPadding.calculateBottomPadding()
                val configuration = LocalConfiguration.current
                val windowClass = ResponsiveLayoutPolicy.resolveWindowClass(
                    safeWidthDp = safeWidth.value,
                    safeHeightDp = safeHeight.value,
                    smallestScreenWidthDp = configuration.smallestScreenWidthDp,
                    screenLayoutSize = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK,
                )
                // Keep legacy callers, such as the task center, on the compact branch for a
                // compact phone landscape. They must not infer Pad/WIDE from aspect alone.
                val layoutMode = windowClass.legacyLayoutMode()
                val adaptiveMetrics = resolveAdaptiveUiMetrics(
                    windowWidthDp = maxWidth.value,
                    windowHeightDp = maxHeight.value,
                    safeLeftDp = safeDrawingPadding.calculateLeftPadding(layoutDirection).value,
                    safeTopDp = safeDrawingPadding.calculateTopPadding().value,
                    safeRightDp = safeDrawingPadding.calculateRightPadding(layoutDirection).value,
                    safeBottomDp = safeDrawingPadding.calculateBottomPadding().value,
                )
                val responsiveDockContentPadding = adaptiveMetrics.bottomContentPaddingDp.dp
                val libraryDockContentPadding = resolveLibraryGridBottomContentPaddingDp(
                    windowHeightDp = maxHeight.value,
                    dockRect = adaptiveMetrics.dockRect,
                    safeBottomDp = safeDrawingPadding.calculateBottomPadding().value,
                ).dp
                val editorGame = state.krkrEditorGame
                val coverSearch = state.coverSearch
                val xp3Picker = state.xp3Picker
                val androidAppPicker = state.androidAppPicker
                val showMainDock = fileBrowserRequest == null &&
                    xp3Picker == null &&
                    androidAppPicker == null &&
                    editorGame == null &&
                    coverSearch == null &&
                    homeEditorProfile == null
                Box(
                    Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze) else Modifier)
                ) {
                when {
                    fileBrowserRequest != null -> BuiltinFileBrowserPage(
                        request = fileBrowserRequest,
                        colors = colors,
                        haze = haze,
                        onDismiss = onFileBrowserDismiss,
                        onUseSaf = onFileBrowserUseSaf,
                        onSelected = onFileBrowserSelected
                    )
                    xp3Picker != null -> Xp3PickerPage(
                        picker = xp3Picker,
                        colors = colors,
                        haze = haze,
                        onDismiss = viewModel::dismissXp3Picker,
                        onSelected = viewModel::selectXp3LaunchTarget,
                    )
                    androidAppPicker != null -> AndroidAppPickerPage(
                        picker = androidAppPicker,
                        colors = colors,
                        haze = haze,
                        onDismiss = viewModel::dismissAndroidAppPicker,
                        onSelected = viewModel::selectAndroidApp,
                    )
                    editorGame != null -> Box(Modifier.fillMaxSize()) { KrkrGameSettingsPage(
                        game = editorGame,
                        state = state,
                        colors = colors,
                        haze = haze,
                        viewModel = viewModel
                    ) }
                    coverSearch != null -> Box(Modifier.fillMaxSize()) { CoverPickerPage(
                        search = coverSearch,
                        colors = colors,
                        haze = haze,
                        viewModel = viewModel,
                        actions = actions,
                    ) }
                    homeEditorProfile != null -> Box(Modifier.fillMaxSize()) {
                        DashboardEditorPage(
                            profile = homeEditorProfile!!,
                            state = state,
                            colors = colors,
                            haze = haze,
                            viewModel = viewModel,
                            onClose = { homeEditorProfile = null }
                        )
                    }
                    else -> Box(Modifier.fillMaxSize()) {
                        Crossfade(
                            targetState = state.tab,
                            animationSpec = tween(if (reducedMotion) 0 else 190),
                            label = "mainNavigation"
                        ) { tab ->
                            when (tab) {
                                KireiTab.HOME -> HomePage(
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
                                    onOpenOverview = { showLibraryOverview = true },
                                    onEditDashboard = { homeEditorProfile = it },
                                )
                                KireiTab.GAMES -> LibraryPage(
                                    state, colors, haze, viewModel, actions, removingGameId, layoutMode,
                                    safeDrawingPadding, libraryDockContentPadding, statusBarBottomDp, displayCutout,
                                    adaptiveMetrics, windowClass
                                ) { removeGame = it }
                                KireiTab.SETTINGS -> SettingsPage(
                                    state, colors, haze, viewModel, actions, layoutMode,
                                    safeDrawingPadding, responsiveDockContentPadding, adaptiveMetrics
                                )
                            }
                        }
                    }
                }
                }
                if (showMainDock && !imeVisible && HomePresentationPolicy.shouldShowGlobalDock(
                        state.tab,
                        state.settings?.homePresentationMode,
                    )
                ) {
                    KireiMainDock(
                        tab = state.tab,
                        colors = if (
                            state.tab == KireiTab.HOME &&
                            (state.settings?.homePresentationMode ?: HomePresentationMode.IMMERSIVE) ==
                            HomePresentationMode.IMMERSIVE
                        ) {
                            immersiveDockKireiColors(colors)
                        } else {
                            colors
                        },
                        haze = haze,
                        viewModel = viewModel,
                        metrics = adaptiveMetrics,
                        reducedTransparency = state.settings?.reducedTransparency == true,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                state.notice?.let { notice ->
                    Notice(message = notice, colors = colors, haze = haze, onDismiss = viewModel::clearNotice)
                }
                state.undoRemoval?.let { undo ->
                    UndoRemovalNotice(undo.game.title ?: "游戏", colors, haze, viewModel::undoLastRemoval, viewModel::dismissUndoRemoval)
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
                val homeErrorPrompt = if (prompt == null &&
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
                    onIgnore = viewModel::ignoreAvailableUpdate,
                    onDownload = viewModel::downloadAvailableUpdate,
                    onCancelDownload = viewModel::cancelUpdateDownload,
                    onInstall = actions.installUpdate,
                    onDiscard = viewModel::discardReadyUpdate,
                    onDismiss = viewModel::dismissUpdateUi,
                )
            }
        }
        }
        }
    }
}

internal data class KireiColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val success: Color,
    val outline: Color,
    val onAccent: Color,
    val danger: Color,
    val isDark: Boolean
)

/** Dark palette used for chrome that always sits on the dark immersive backdrop. */
internal fun darkKireiColors(): KireiColors = KireiColors(
    background = Color(0xFF05070D),
    surface = Color(0xFF10131D),
    surfaceRaised = Color(0xFF171A25),
    text = Color.White,
    secondary = Color(0xFFA8A6AF),
    accent = Color(0xFFAAA5FC),
    success = Color(0xFF78E7A0),
    outline = Color(0xFF555765),
    onAccent = Color(0xFF21164E),
    danger = Color(0xFFFF6B6B),
    isDark = true,
)

@Composable
private fun KireiPromptHost(
    prompt: KireiPrompt?,
    colors: KireiColors,
    haze: HazeState?,
    reducedMotion: Boolean,
    onPromptDismiss: () -> Unit
) {
    var displayedPrompt by remember { mutableStateOf(prompt) }
    var visible by remember { mutableStateOf(prompt != null) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val enterDuration = if (reducedMotion) 80 else 180
    val exitDuration = if (reducedMotion) 80 else 140
    val cardScale by animateFloatAsState(
        targetValue = if (visible || reducedMotion) 1f else .94f,
        animationSpec = tween(if (visible) enterDuration else exitDuration),
        label = "promptScale"
    )

    LaunchedEffect(prompt) {
        if (prompt != null) {
            displayedPrompt = prompt
            closing = false
            visible = true
        } else if (displayedPrompt != null && !closing) {
            visible = false
            delay(exitDuration.toLong())
            displayedPrompt = null
        }
    }

    fun close(confirm: Boolean) {
        if (closing) return
        val active = displayedPrompt ?: return
        closing = true
        visible = false
        scope.launch {
            delay(exitDuration.toLong())
            displayedPrompt = null
            closing = false
            onPromptDismiss()
            if (confirm) active.onConfirm() else active.onDismiss()
        }
    }

    val activePrompt = displayedPrompt
    BackHandler(enabled = activePrompt?.dismissLabel != null && !closing) { close(false) }
    AnimatedVisibility(
        visible = activePrompt != null && visible,
        enter = fadeIn(tween(enterDuration)),
        exit = fadeOut(tween(exitDuration))
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = if (colors.isDark) .24f else .14f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            contentAlignment = Alignment.Center
        ) {
            activePrompt?.let { current ->
                GlassSurface(
                    colors = colors,
                    haze = haze,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp).graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                    },
                    shape = RoundedCornerShape(30.dp),
                    role = KireiGlassRole.ALERT
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp)) {
                        Text(
                            current.title,
                            color = colors.text,
                            style = AppleTypography.headlineMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            current.message,
                            color = colors.secondary,
                            style = AppleTypography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 20.dp)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            current.dismissLabel?.let { label ->
                                Surface(
                                    onClick = { close(false) },
                                    enabled = !closing,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                    color = colors.surfaceRaised
                                ) {
                                    Text(
                                        label,
                                        color = colors.text,
                                        style = AppleTypography.bodyMediumEmphasis,
                                        modifier = Modifier.padding(vertical = 13.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Surface(
                                onClick = { close(true) },
                                enabled = !closing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                color = colors.accent
                            ) {
                                Text(
                                    current.confirmLabel,
                                    color = colors.onAccent,
                                    style = AppleTypography.bodyMediumEmphasis,
                                    modifier = Modifier.padding(vertical = 13.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatePromptHost(
    state: UpdateUiState,
    colors: KireiColors,
    haze: HazeState?,
    onIgnore: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == UpdateUiState.Idle || state == UpdateUiState.Checking(manual = false)) return
    // Update prompts require an explicit action so Back/outside taps cannot silently
    // suppress a version without recording "忽略此版本".
    BackHandler { }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = if (colors.isDark) .28f else .16f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            colors = colors,
            haze = haze,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            shape = RoundedCornerShape(30.dp),
            role = KireiGlassRole.ALERT,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state) {
                    is UpdateUiState.Checking -> {
                        Text("正在检查更新", color = colors.text, style = AppleTypography.headlineMedium)
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accent,
                            trackColor = colors.outline.copy(alpha = .24f),
                        )
                    }
                    is UpdateUiState.Available -> {
                        Text("发现新版本", color = colors.text, style = AppleTypography.headlineMedium)
                        Text(
                            "${BuildConfig.VERSION_NAME}  →  ${state.release.manifest.versionName} · ${formatUpdateBytes(state.release.apkSize)}",
                            color = colors.secondary,
                            style = AppleTypography.bodyMedium,
                        )
                        if (state.release.notes.isNotBlank()) {
                            Text(
                                state.release.notes,
                                color = colors.secondary,
                                style = AppleTypography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                        UpdatePromptButtons("忽略此版本", onIgnore, "下载更新", onDownload, colors)
                    }
                    is UpdateUiState.Downloading -> {
                        val progress = if (state.totalBytes > 0L) {
                            (state.bytesRead.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        Text("正在下载更新", color = colors.text, style = AppleTypography.headlineMedium)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accent,
                            trackColor = colors.outline.copy(alpha = .24f),
                        )
                        Text(
                            "${formatUpdateBytes(state.bytesRead)} / ${formatUpdateBytes(state.totalBytes)} · ${formatUpdateBytes(state.bytesPerSecond)}/秒",
                            color = colors.secondary,
                            style = AppleTypography.bodySmall,
                        )
                        UpdateSinglePromptButton("取消下载", onCancelDownload, colors, emphasized = false)
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Text("更新已校验", color = colors.text, style = AppleTypography.headlineMedium)
                        Text(
                            "包名、版本、ABI、SDK、SHA-256 和签名证书均已通过校验。安装仍需由 Android 系统确认。",
                            color = colors.secondary,
                            style = AppleTypography.bodyMedium,
                        )
                        UpdatePromptButtons("稍后", onDiscard, "安装更新", { onInstall(state.apkPath) }, colors)
                    }
                    is UpdateUiState.Error -> {
                        Text("更新检查失败", color = colors.text, style = AppleTypography.headlineMedium)
                        Text(state.message, color = colors.secondary, style = AppleTypography.bodyMedium)
                        if (state.release == null) {
                            UpdateSinglePromptButton("关闭", onDismiss, colors, emphasized = false)
                        } else {
                            UpdatePromptButtons("关闭", onDismiss, "重试下载", onDownload, colors)
                        }
                    }
                    UpdateUiState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun UpdatePromptButtons(
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    colors: KireiColors,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UpdatePromptButton(secondaryLabel, onSecondary, colors, emphasized = false, Modifier.weight(1f))
        UpdatePromptButton(primaryLabel, onPrimary, colors, emphasized = true, Modifier.weight(1f))
    }
}

@Composable
private fun UpdateSinglePromptButton(
    label: String,
    onClick: () -> Unit,
    colors: KireiColors,
    emphasized: Boolean,
) {
    UpdatePromptButton(label, onClick, colors, emphasized, Modifier.fillMaxWidth())
}

@Composable
private fun UpdatePromptButton(
    label: String,
    onClick: () -> Unit,
    colors: KireiColors,
    emphasized: Boolean,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (emphasized) colors.accent else colors.surfaceRaised,
    ) {
        Text(
            label,
            color = if (emphasized) colors.onAccent else colors.text,
            style = AppleTypography.bodyMediumEmphasis,
            modifier = Modifier.padding(vertical = 13.dp),
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatUpdateBytes(bytes: Long): String = when {
    bytes <= 0L -> "未知大小"
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

@Composable
private fun Xp3PickerPage(
    picker: Xp3PickerUi,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val selected = picker.game.launchTarget.orEmpty().replace('\\', '/')
    Box(
        Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 840.dp).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "返回", colors, haze, onDismiss)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("选择 XP3 启动文件", color = colors.text, style = AppleTypography.headlineMedium)
                    Text(
                        picker.game.title.orEmpty(),
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "只会修改这个游戏的启动入口，不会移动或修改 XP3 文件。",
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
            )
            GlassSurface(
                colors,
                haze,
                Modifier.fillMaxWidth().weight(1f),
                RoundedCornerShape(26.dp),
                KireiGlassRole.CONTENT,
            ) {
                when {
                    picker.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                    picker.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(picker.error, color = colors.danger, textAlign = TextAlign.Center)
                    }
                    picker.candidates.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "这个游戏目录中没有找到 XP3 文件，请检查目录授权或重新添加游戏。",
                            color = colors.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(picker.candidates, key = { it }) { target ->
                            val checked = target.equals(selected, ignoreCase = true)
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelected(target) }
                                    .padding(horizontal = 18.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(42.dp).background(
                                        if (checked) colors.accent.copy(alpha = .18f) else colors.surfaceRaised,
                                        RoundedCornerShape(13.dp),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (checked) Icons.Rounded.Check else Icons.Rounded.Description,
                                        null,
                                        tint = if (checked) colors.accent else colors.secondary,
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                                    Text(
                                        target.substringAfterLast('/'),
                                        color = colors.text,
                                        style = AppleTypography.bodyLargeEmphasis,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        target,
                                        color = colors.secondary,
                                        style = AppleTypography.labelMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (checked) Text("当前", color = colors.accent, style = AppleTypography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidAppPickerPage(
    picker: AndroidAppPickerUi,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
    onSelected: (AndroidAppEntry) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var query by remember { mutableStateOf("") }
    val normalized = query.trim()
    val visible = remember(picker.entries, normalized) {
        if (normalized.isEmpty()) picker.entries else picker.entries.filter {
            it.label.contains(normalized, ignoreCase = true) ||
                it.packageName.contains(normalized, ignoreCase = true)
        }
    }
    Box(
        Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 840.dp).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "返回", colors, haze, onDismiss)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("选择 Android 应用", color = colors.text, style = AppleTypography.headlineMedium)
                    Text("不需要存储权限", color = colors.secondary, style = AppleTypography.labelLarge)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("搜索应用名称或包名") },
                shape = RoundedCornerShape(16.dp),
            )
            GlassSurface(
                colors,
                haze,
                Modifier.fillMaxWidth().weight(1f),
                RoundedCornerShape(26.dp),
                KireiGlassRole.CONTENT,
            ) {
                when {
                    picker.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                    picker.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(picker.error, color = colors.danger, textAlign = TextAlign.Center)
                    }
                    visible.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank()) "没有查询到可启动的应用" else "没有匹配的应用",
                            color = colors.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(visible, key = AndroidAppEntry::componentKey) { entry ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelected(entry) }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(44.dp).background(colors.surfaceRaised, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Gamepad, null, tint = colors.accent)
                                }
                                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                                    Text(
                                        entry.label,
                                        color = colors.text,
                                        style = AppleTypography.bodyLargeEmphasis,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        entry.packageName,
                                        color = colors.secondary,
                                        style = AppleTypography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltinFileBrowserPage(
    request: BuiltinFileBrowserRequest,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
    onUseSaf: () -> Unit,
    onSelected: (Uri) -> Unit
) {
    val internalRoot = remember(request) { File(request.startPath).safeCanonical() }
    val appRoot = remember(request) { File(request.appPath).safeCanonical() }
    var navigationRoot by remember(request) { mutableStateOf(internalRoot) }
    var current by remember(request) { mutableStateOf(internalRoot) }
    val entries = remember(current, request.mode) {
        runCatching { current.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { it.isDirectory || request.mode == BuiltinFileBrowserMode.DIRECTORY || isBuiltinLaunchFile(it.name) }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    val goBack: () -> Unit = {
        val parent = current.parentFile?.safeCanonical()
        if (parent != null && current.safeCanonical() != navigationRoot.safeCanonical() &&
            parent.path.startsWith(navigationRoot.safeCanonical().path)
        ) {
            current = parent
        } else {
            onDismiss()
        }
    }
    BackHandler(onBack = goBack)

    Box(
        Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 920.dp).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "返回", colors, haze, goBack)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        if (request.mode == BuiltinFileBrowserMode.DIRECTORY) "选择游戏目录" else "选择启动文件",
                        color = colors.text,
                        style = AppleTypography.headlineMedium
                    )
                    Text(
                        if (request.mode == BuiltinFileBrowserMode.DIRECTORY) "当前文件夹可直接加入扫描" else "仅显示支持的启动文件",
                        color = colors.secondary,
                        style = AppleTypography.labelLarge
                    )
                }
                TextButton(onClick = onUseSaf) { Text("系统 SAF", color = colors.accent) }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("内部存储", navigationRoot == internalRoot, colors) {
                    navigationRoot = internalRoot
                    current = internalRoot
                }
                FilterChip("应用目录", navigationRoot == appRoot, colors) {
                    navigationRoot = appRoot
                    current = appRoot
                }
                FilterChip("SD 卡 / 受限存储", false, colors, onUseSaf)
            }

            val breadcrumbs = remember(current, navigationRoot) {
                buildList {
                    var cursor: File? = current.safeCanonical()
                    val rootPath = navigationRoot.safeCanonical().path
                    while (cursor != null && cursor.path.startsWith(rootPath)) {
                        add(cursor)
                        if (cursor.path == rootPath) break
                        cursor = cursor.parentFile
                    }
                }.asReversed()
            }
            if (!BuiltinFileBrowserCrumbPolicy.shouldHideRow(breadcrumbs.size)) {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(breadcrumbs, key = { _, file -> file.path }) { index, file ->
                    if (index > 0) Icon(
                        Icons.Rounded.ChevronRight,
                        null,
                        tint = colors.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    TextButton(onClick = { current = file }) {
                        Text(
                            if (index == 0) {
                                if (navigationRoot == internalRoot) "内部存储" else "应用目录"
                            } else file.name,
                            color = if (file == current) colors.text else colors.accent,
                            maxLines = 1
                        )
                    }
                }
            }
            }

            GlassSurface(
                colors,
                haze,
                Modifier.fillMaxWidth().weight(1f),
                RoundedCornerShape(26.dp),
                KireiGlassRole.CONTENT
            ) {
                if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("这个文件夹里没有可选择的内容", color = colors.secondary)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(entries, key = { it.path }) { file ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (file.isDirectory) current = file.safeCanonical()
                                    else if (request.mode == BuiltinFileBrowserMode.LAUNCH_FILE) {
                                        onSelected(Uri.fromFile(file))
                                    }
                                }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(44.dp).background(colors.surfaceRaised, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (file.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                                        null,
                                        tint = if (file.isDirectory) colors.accent else colors.secondary
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(
                                        file.name,
                                        color = colors.text,
                                        style = AppleTypography.bodyLargeEmphasis,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (file.isDirectory) {
                                            "${runCatching { file.list()?.size }.getOrNull() ?: 0} 项"
                                        } else {
                                            "${formatFileSize(file.length())} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.lastModified()))}"
                                        },
                                        color = colors.secondary,
                                        style = AppleTypography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondary)
                            }
                        }
                    }
                }
            }

            if (request.mode == BuiltinFileBrowserMode.DIRECTORY) {
                Button(
                    onClick = { onSelected(Uri.fromFile(current)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                ) {
                    Text("选择此文件夹", style = AppleTypography.bodyLargeEmphasis)
                }
            }
        }
    }
}

private fun File.safeCanonical(): File = runCatching { canonicalFile }.getOrElse { absoluteFile }

private fun isBuiltinLaunchFile(name: String): Boolean {
    val lower = name.trim().lowercase()
    return lower.endsWith(".xp3") || lower.endsWith(".nsa") || lower.endsWith(".sar") ||
        lower.endsWith(".ns2") || lower.endsWith(".nt2") || lower.endsWith(".nt3") ||
        lower == "0.txt" || lower == "00.txt" || lower == "index.html" || lower == "index.htm" ||
        lower.endsWith(".pfs") || lower.endsWith(".iet") || lower == "startup.tjs"
}

/**
 * Approximates the outer LazyColumn scroll position in pixels from its layout info so the
 * custom settle animation can drive it frame by frame with the required easing.
 */
/**
 * Saturation only exists as a RenderEffect on API 31+; older API levels simply keep the
 * full-saturation artwork, matching the plan's "effect enabled on API 31+" rule.
 */
private fun Modifier.saturationEffect(saturation: Float): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && saturation != 1f) {
        graphicsLayer {
            renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply { setSaturation(saturation) }
                )
            ).asComposeRenderEffect()
        }
    } else {
        this
    }

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> String.format("%.1f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
}

@Composable
private fun HomePage(
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
        val kind = HomePresentationPolicy.renderKindForSafeArea(
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
                safeBounds = ImmersiveSafeBounds(
                    leftPx = with(density) { safeDrawingPadding.calculateLeftPadding(layoutDirection).toPx() },
                    topPx = with(density) { safeDrawingPadding.calculateTopPadding().toPx() },
                    rightPx = with(density) {
                        maxWidth.toPx() - safeDrawingPadding.calculateRightPadding(layoutDirection).toPx()
                    },
                    bottomPx = with(density) {
                        maxHeight.toPx() - safeDrawingPadding.calculateBottomPadding().toPx()
                    },
                ),
                reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED,
                reducedTransparency = state.settings?.reducedTransparency == true,
                adaptiveMetrics = adaptiveMetrics,
                onOpenOverview = onOpenOverview,
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
private fun ComponentsHomePage(
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
    val recent = LibraryOverviewPolicy.sorted(state.games, LibraryOverviewSort.RECENT)
        .filter { it.lastPlayedAt > 0L }
    var menuExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = menuExpanded) { menuExpanded = false }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val homeWindowWidthDp = maxWidth.value
        val homeWindowHeightDp = maxHeight.value
        val presentation = ResponsiveLayoutPolicy.dashboardPresentation(layoutMode, homeWindowHeightDp)
        val profile = presentation.profile
        val compactHeight = presentation.density == DashboardDensity.COMPACT_HEIGHT ||
            windowClass == ResponsiveWindowClass.PHONE_LANDSCAPE
        val twoPane = profile == DashboardProfile.WIDE
        val storedLayout = if (profile == DashboardProfile.WIDE) state.dashboardWide else state.dashboardCompact
        val visibleGameIds = state.games.mapTo(HashSet()) { it.id }
        val effectiveLayout = remember(storedLayout, visibleGameIds) {
            storedLayout.copy(widgets = storedLayout.widgets.filter { spec ->
                spec.type != DashboardWidgetType.GAME_SPOTLIGHT || spec.gameId == null || spec.gameId in visibleGameIds
            })
        }
        val heroInstanceId = effectiveLayout.widgets.firstOrNull { it.type == DashboardWidgetType.GAME_SPOTLIGHT }?.instanceId
        val layoutDirection = LocalLayoutDirection.current
        val safeLeftDp = safeDrawingPadding.calculateLeftPadding(layoutDirection).value
        val safeTopDp = safeDrawingPadding.calculateTopPadding().value
        val safeRightDp = safeDrawingPadding.calculateRightPadding(layoutDirection).value
        val safeBottomDp = safeDrawingPadding.calculateBottomPadding().value
        val safeFrame = HomeRect(
            x = safeLeftDp,
            y = safeTopDp,
            w = (homeWindowWidthDp - safeLeftDp - safeRightDp).coerceAtLeast(1f),
            h = (homeWindowHeightDp - safeTopDp - safeBottomDp).coerceAtLeast(1f),
        )
        val topChrome = HomePresentationPolicy.topChromeSpec(
            safeFrame = safeFrame,
            sizeClass = windowClass.homeSizeClass(),
        )
        val chromeTopDp = minOf(topChrome.titleRect.y, topChrome.menuRect.y)
        val chromeBottomDp = maxOf(
            topChrome.titleRect.y + topChrome.titleRect.h,
            topChrome.menuRect.y + topChrome.menuRect.h,
        )
        val headerHeight = (chromeBottomDp - chromeTopDp).coerceAtLeast(HOME_MIN_TOUCH_TARGET_DP).dp
        val contentLeft = adaptiveMetrics.contentFrame.x.dp
        val contentRight = (homeWindowWidthDp - adaptiveMetrics.contentFrame.x - adaptiveMetrics.contentFrame.w)
            .coerceAtLeast(0f).dp
        val headerGap = if (compactHeight) 8.dp else if (twoPane) 17.dp else 14.dp
        val pageVerticalPadding = if (compactHeight) 8.dp else 16.dp
        val pageFrame = Modifier.align(Alignment.TopCenter).fillMaxSize()
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
                        RoundedCornerShape(28.dp),
                    ) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
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
                            spotlightInitialPage = if (spec.instanceId == heroInstanceId) {
                                currentSpotlightPage
                            } else {
                                0
                            },
                            onSpotlightPageChanged = if (spec.instanceId == heroInstanceId) {
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
            Column(
                Modifier.offset(topChrome.titleRect.x.dp, topChrome.titleRect.y.dp)
                    .size(topChrome.titleRect.w.dp, topChrome.titleRect.h.dp),
            ) {
                Text(
                    "KireiBox",
                    color = colors.text,
                    style = AppleTypography.headlineMedium.copy(fontSize = topChrome.titleSizeDp.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compactHeight) {
                    Text("继续你的故事", color = colors.secondary, style = AppleTypography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
            HomeControlMenu(
                expanded = menuExpanded,
                colors = colors,
                haze = haze,
                editLabel = "编辑首页",
                menuDiameterDp = topChrome.menuDiameterDp.dp,
                menuIconSizeDp = topChrome.menuIconSizeDp.dp,
                opaqueFill = true,
                modifier = Modifier.offset(
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
    /** Immersive home has no stats card, so it reaches the play-time ranking from this menu. */
    onOpenOverview: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val polished = LocalResolvedMotionMode.current == MotionMode.POLISHED
    val menuWidthDp = if (onOpenOverview != null) 202.dp else 150.dp
    Box(modifier.size(menuWidthDp, menuDiameterDp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (polished) 150 else 90)) +
                slideInHorizontally(tween(if (polished) 180 else 90)) { it * 2 },
            exit = fadeOut(tween(if (polished) 110 else 90)) +
                slideOutHorizontally(tween(if (polished) 130 else 90)) { it * 2 },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            HomeMenuAction(Icons.Rounded.Edit, editLabel, colors, haze, onEdit)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (polished) 175 else 90)) +
                slideInHorizontally(tween(if (polished) 205 else 90)) { it },
            exit = fadeOut(tween(if (polished) 100 else 90)) +
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
        if (onOpenOverview != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(if (polished) 200 else 90)) +
                    slideInHorizontally(tween(if (polished) 230 else 90)) { it / 2 },
                exit = fadeOut(tween(if (polished) 90 else 90)) +
                    slideOutHorizontally(tween(if (polished) 110 else 90)) { it / 2 },
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 104.dp)
            ) {
                HomeMenuAction(Icons.Rounded.Timer, "游玩时长排名", colors, haze, onOpenOverview)
            }
        }
        GlassSurface(
            colors,
            haze,
            Modifier.align(Alignment.CenterEnd).size(menuDiameterDp),
            CircleShape,
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
private fun HomeMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    colors: KireiColors,
    haze: HazeState?,
    onClick: () -> Unit
) {
    GlassSurface(colors, haze, Modifier.size(44.dp), CircleShape, KireiGlassRole.ICON, onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = colors.text, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DashboardGrid(
    layout: DashboardLayout,
    profile: DashboardProfile,
    density: DashboardDensity,
    modifier: Modifier = Modifier,
    content: @Composable (DashboardWidgetSpec) -> Unit
) {
    val placements = remember(layout, profile) { DashboardLayouts.pack(layout, profile) }
    val compactHeight = density == DashboardDensity.COMPACT_HEIGHT
    val horizontalGap = if (compactHeight) 8.dp else 10.dp
    val verticalStep = if (compactHeight) 6.dp else DashboardLayouts.HEIGHT_STEP_DP.dp
    val polished = LocalResolvedMotionMode.current == MotionMode.POLISHED
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - horizontalGap * (profile.columns - 1)) / profile.columns
        val rows = placements.maxOfOrNull { it.row + it.spec.heightSteps } ?: 0
        val targetHeight = verticalStep * rows
        Box(Modifier.fillMaxWidth().animateContentSize(if (polished) spring(.82f, Spring.StiffnessMediumLow) else tween(90)).height(targetHeight)) {
            placements.forEach { placement ->
                key(placement.spec.instanceId) {
                    val spec = placement.spec
                    val animation = if (polished) spring<Dp>(.82f, Spring.StiffnessMediumLow) else tween(90)
                    val x by animateDpAsState((cellWidth + horizontalGap) * placement.column, animation, label = "dashboardX")
                    val y by animateDpAsState(verticalStep * placement.row, animation, label = "dashboardY")
                    val width by animateDpAsState(
                        cellWidth * spec.size.columns + horizontalGap * (spec.size.columns - 1),
                        animation,
                        label = "dashboardWidth"
                    )
                    val height by animateDpAsState(verticalStep * spec.heightSteps, animation, label = "dashboardHeight")
                    Box(Modifier.offset(x, y).size(width, height)) { content(spec) }
                }
            }
        }
    }
}

@Composable
private fun HomeDashboardWidget(
    spec: DashboardWidgetSpec,
    profile: DashboardProfile,
    games: List<Game>,
    recent: List<Game>,
    playTrend: List<PlayTrendPoint>,
    colors: KireiColors,
    haze: HazeState?,
    onOpenOverview: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenGame: (Game) -> Unit,
    onLaunchGame: (Game) -> Unit,
    onChooseDirectory: () -> Unit,
    onOpenSpotlightEditor: () -> Unit = {},
    interactiveTrend: Boolean = true,
    spotlightInitialPage: Int = 0,
    onSpotlightPageChanged: (Int) -> Unit = {},
    landscapeStyle: LandscapeHomeStyle? = null,
    safeContentPadding: Dp = 0.dp,
    constrainRecentRow: Boolean = false,
) {
    when (spec.type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> {
            val pages = SpotlightSelector.selectPages(
                spec = spec,
                games = games,
                sessionSeed = spotlightProcessSeed,
                epochDay = LocalDate.now().toEpochDay(),
            )
            if (pages.isNotEmpty()) {
                HomeSpotlight(
                    pages = pages,
                    colors = colors,
                    modifier = Modifier.fillMaxSize(),
                    styleKey = spec.styleKey,
                    onOpen = onOpenGame,
                    onLaunch = onLaunchGame,
                    landscapeStyle = landscapeStyle,
                    safeContentPadding = safeContentPadding,
                    initialPage = spotlightInitialPage,
                    onPageChanged = onSpotlightPageChanged,
                )
            } else {
                HomeEmptySpotlight(
                    colors = colors,
                    emptyState = ImmersiveGalleryEditorPolicy.emptyState(
                        games = games,
                        mode = spec.spotlightSelectionMode,
                        manualIds = spec.spotlightGameIds,
                        source = spec.spotlightSource,
                    ),
                    onChooseDirectory = onChooseDirectory,
                    onOpenSpotlightEditor = onOpenSpotlightEditor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        DashboardWidgetType.LIBRARY_OVERVIEW -> HomeInsightRail(
            gameCount = games.size,
            favoriteCount = games.count { it.favorite },
            playTime = games.sumOf { it.totalPlayTime },
            colors = colors,
            modifier = Modifier.fillMaxSize(),
            compact = spec.size.columns == 1,
            styleKey = spec.styleKey,
            onOpenLibrary = onOpenOverview
        )
        DashboardWidgetType.RECENT_GAMES -> DashboardRecentWidget(
            games = recent.take(3),
            showThreeAcross = profile == DashboardProfile.WIDE && spec.size.columns == 4,
            constrainToInnerBounds = landscapeStyle != null || constrainRecentRow,
            compact = spec.size.columns == 1,
            styleKey = spec.styleKey,
            colors = colors,
            haze = haze,
            onOpenLibrary = onOpenLibrary,
            onOpenGame = onOpenGame,
            onLaunchGame = onLaunchGame
        )
        DashboardWidgetType.FAVORITES -> DashboardFavoritesWidget(
            games.filter { it.favorite }, spec.styleKey, colors, haze, onOpenLibrary
        )
        DashboardWidgetType.LIBRARY_SHORTCUT -> DashboardLibraryShortcut(
            games.size, games.count { it.lastPlayedAt > 0L }, spec.styleKey, colors, haze, onOpenLibrary
        )
        DashboardWidgetType.PLAY_TREND -> DashboardTrendWidget(playTrend, spec, colors, haze, interactiveTrend)
    }
}

@Composable
private fun CompactLandscapeRootBackdrop(
    style: LandscapeHomeStyle,
    pages: List<Game>,
    currentPage: Int,
    targetPage: Int,
    crossfadeProgress: Float,
    visibilityAlpha: Float,
    colors: KireiColors,
    reducedTransparency: Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val plan = ImmersiveBackdropPolicy.plan(
        style = style,
        reducedTransparency = reducedTransparency,
        darkTheme = colors.isDark,
    )
    val shownPage = if (crossfadeProgress >= .5f) targetPage else currentPage
    val currentGame = pages.getOrNull(shownPage)
    val backdropContent: @Composable (Game?) -> Unit = { game ->
        Box(modifier.graphicsLayer { this.alpha = visibilityAlpha.coerceIn(0f, 1f) }) {
            Box(Modifier.fillMaxSize().background(colors.background))
            if (game != null) {
                if (reducedTransparency) {
                    GameCoverArtwork(
                        game = game,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            this.alpha = plan.staticCoverAlpha
                        },
                        contentScale = ContentScale.Crop,
                        cornerRadius = 0.dp,
                        role = CoverImageRole.HERO,
                    )
                } else {
                    GameCoverArtwork(
                        game = game,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = ImmersiveBackdropPolicy.BACKDROP_SCALE
                            scaleY = ImmersiveBackdropPolicy.BACKDROP_SCALE
                        }
                            .blur(plan.blurRadiusDp.dp)
                            .saturationEffect(plan.saturation),
                        contentScale = ContentScale.Crop,
                        cornerRadius = 0.dp,
                        role = CoverImageRole.HERO,
                    )
                }
            }
            if (plan.drawBackdrop && !reducedTransparency) {
                Box(
                    Modifier.fillMaxSize().background(
                        if (colors.isDark) {
                            Color.Black.copy(alpha = plan.fullOverlayAlpha)
                        } else {
                            Color.White.copy(alpha = plan.fullOverlayAlpha)
                        }
                    )
                )
                val start = if (colors.isDark) Color.Black else Color.White
                val gradientColors = plan.topGradientStops.map { stop ->
                    when (stop.colorToken) {
                        ImmersiveBackdropPolicy.TRANSPARENT -> Color.Transparent
                        ImmersiveBackdropPolicy.LIGHT_START -> Color.White.copy(alpha = stop.alpha)
                        else -> start.copy(alpha = stop.alpha)
                    }
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = gradientColors,
                            startY = 0f,
                            endY = with(density) {
                                ImmersiveBackdropPolicy.TOP_GRADIENT_EXTENT_DP.dp.toPx()
                            },
                        )
                    )
                )
            }
        }
    }
    if (reducedTransparency) {
        backdropContent(currentGame)
    } else {
        Crossfade(
            targetState = currentGame?.id,
            animationSpec = tween(220),
            label = "compactRootBackdrop",
        ) { gameId ->
            backdropContent(pages.firstOrNull { it.id == gameId })
        }
    }
}

@Composable
private fun HomeSpotlight(
    pages: List<Game>,
    colors: KireiColors,
    modifier: Modifier,
    styleKey: String,
    onOpen: (Game) -> Unit,
    onLaunch: (Game) -> Unit,
    landscapeStyle: LandscapeHomeStyle? = null,
    safeContentPadding: Dp = 0.dp,
    symmetricSafeGuardDp: Dp = 0.dp,
    reducedTransparency: Boolean = false,
    cardHeightDp: Float? = null,
    cardBottomGapDp: Float = 0f,
        initialPage: Int = 0,
        onPageChanged: (Int) -> Unit = {},
        onFocusStateChanged: (FocusGalleryBackdropState) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    var isVisible by remember { mutableStateOf(true) }
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val scope = rememberCoroutineScope()
    val landscapePager = landscapeStyle != null
    // Only the real first-page hero enables the four-slot Focus stage; component-page
    // embedded spotlights pass a null style and keep the legacy pager carousel.
    val useFocusStage = landscapeStyle != null && pages.isNotEmpty()
    val virtualPageCount = SpotlightCircularCarouselPolicy.virtualPageCount(pages.size)
    val pagerState = rememberPagerState(
        initialPage = SpotlightCircularCarouselPolicy.startVirtualPage(pages.size, initialPage),
    ) { virtualPageCount }
    val userDragActive by pagerState.interactionSource.collectIsDraggedAsState()
    val userPressActive by pagerState.interactionSource.collectIsPressedAsState()
    val timerPausedForDrag = SpotlightCarouselPolicy.timerPausedForPager(
        userDragActive = userDragActive,
        scrollInProgress = pagerState.isScrollInProgress,
    )
    val timerAnchorPage = SpotlightCarouselPolicy.timerAnchorPage(
        currentPage = pagerState.currentPage,
        settledPage = pagerState.settledPage,
    )
    val currentGameIndex = if (pages.isEmpty()) {
        0
    } else {
        SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(pagerState.currentPage, pages.size)
    }

    LaunchedEffect(pages.map { it.id }, useFocusStage) {
        if (useFocusStage || pages.size <= 1) return@LaunchedEffect
        if (pages.size > 1) {
            val currentIndex = SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(
                pagerState.currentPage,
                pages.size,
            )
            val target = SpotlightCircularCarouselPolicy.nearestVirtualPage(
                gameIndex = currentIndex,
                pageCount = pages.size,
                fromPage = pagerState.currentPage,
            )
            if (target != pagerState.currentPage) pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage, useFocusStage) {
        if (useFocusStage) return@LaunchedEffect
        if (pages.isNotEmpty()) {
            onPageChanged(
                SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(pagerState.settledPage, pages.size),
            )
        }
    }

    LaunchedEffect(
        pages.size,
        timerAnchorPage,
        timerPausedForDrag,
        userPressActive,
        isVisible,
        isResumed,
        reducedMotion,
        useFocusStage,
    ) {
        if (useFocusStage) return@LaunchedEffect
        if (!SpotlightCarouselPolicy.shouldAutoAdvance(
                pageCount = pages.size,
                elapsedMillis = SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS,
                isVisible = isVisible,
                isResumed = isResumed,
                isDragging = timerPausedForDrag || userPressActive,
            )
        ) return@LaunchedEffect
        delay(SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS)
        if (!SpotlightCarouselPolicy.shouldAutoAdvance(
                pageCount = pages.size,
                elapsedMillis = SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS,
                isVisible = isVisible,
                isResumed = isResumed,
                isDragging = timerPausedForDrag || userPressActive,
            )
        ) return@LaunchedEffect
        val next = SpotlightCarouselPolicy.nextPage(timerAnchorPage, virtualPageCount)
        if (reducedMotion) {
            pagerState.scrollToPage(next)
        } else {
            pagerState.animateScrollToPage(next, animationSpec = tween(SpotlightCarouselPolicy.ANIMATE_MILLIS))
        }
    }

    BoxWithConstraints(
        modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            isVisible = bounds.width > 0f && bounds.height > 0f &&
                bounds.bottom > 0f && bounds.top < screenHeightPx
        },
    ) {
        val componentWidthPx = with(density) { maxWidth.roundToPx() }
        val componentHeightPx = with(density) { maxHeight.roundToPx() }
        if (useFocusStage) {
            // useFocusStage implies a concrete style; the fallback only satisfies the
            // compiler's nullable smart-cast for the geometry/stage style parameters.
            val focusStyle = landscapeStyle ?: LandscapeHomeStyle.IMMERSIVE_COVER
            val focusScope = rememberCoroutineScope()
            val initialIndex = initialPage.mod(pages.size.coerceAtLeast(1))
            val controller = remember(pages.size) {
                FocusGalleryController(
                    scope = focusScope,
                    initialGameIndex = initialIndex,
                    onSnapshot = { },
                )
            }
            val context = LocalContext.current
            DisposableEffect(controller) {
                if (!BuildConfig.DEBUG) return@DisposableEffect onDispose { }
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                        val progress = intent?.getIntExtra("focus_gallery_progress", -1)
                        controller.freezeOverride = progress.takeIf { it in 0..100 }?.let { it / 100f }
                        android.util.Log.d(
                            "FocusFreeze",
                            "receiver progress=$progress freeze=${controller.freezeOverride}",
                        )
                    }
                }
                val filter = android.content.IntentFilter("com.kireibox.debug.FOCUS_GALLERY_FREEZE")
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                onDispose { context.unregisterReceiver(receiver) }
            }
            controller.reducedMotion = reducedMotion
            val snapshotState = remember {
                mutableStateOf(controller.machine.snapshot(pages.size))
            }
            val snapshotUpdater by rememberUpdatedState { value: FocusGallerySnapshot ->
                snapshotState.value = value
                onFocusStateChanged(
                    FocusGalleryBackdropState(
                        currentGameIndex = value.currentGameIndex,
                        targetGameIndex = value.targetGameIndex,
                        progress = value.progress,
                    ),
                )
            }
            LaunchedEffect(controller) {
                controller.setGames(pages)
                snapshotState.value = controller.machine.snapshot(pages.size)
                controller.onSnapshot = snapshotUpdater
            }
            LaunchedEffect(pages.map { it.id }) {
                controller.setGames(pages)
            }
            val snapshot = snapshotState.value
            // The Focus controller commits currentGameIndex after every completed
            // transition; keep the outer page state in sync with the focused game.
            LaunchedEffect(snapshot.currentGameIndex, pages.size) {
                if (pages.isNotEmpty()) {
                    onPageChanged(snapshot.currentGameIndex.mod(pages.size))
                }
            }
            var galleryCardPressed by remember { mutableStateOf(false) }
            val galleryIdle = !snapshot.isSettling && snapshot.progress <= 0f
            // Key contract: any commit, manual tap, drag takeover, idle toggle or
            // lifecycle change restarts the full six-second interval. The token is
            // captured when the timer starts and re-checked atomically when the delay
            // fires, so a stale cancelled delay can never advance after a manual
            // takeover (device race: tap 9nine then auto timer pushed back to ATRI).
            val autoTimerKeys = FocusAutoAdvanceTimerPolicy.timerKeys(
                galleryIdle = galleryIdle,
                galleryCardPressed = galleryCardPressed,
                currentGameIndex = snapshot.currentGameIndex,
                commitSequence = snapshot.commitSequence,
                manualInteractionSequence = snapshot.manualInteractionSequence,
                gameCount = pages.size,
                isVisible = isVisible,
                isResumed = isResumed,
                reducedMotion = reducedMotion,
            )
            LaunchedEffect(autoTimerKeys) {
                if (!FocusGalleryAutoAdvancePolicy.shouldRunTimer(
                        gameCount = pages.size,
                        galleryIdle = galleryIdle,
                        isVisible = isVisible,
                        isResumed = isResumed,
                        isCardPressed = galleryCardPressed,
                    )
                ) return@LaunchedEffect
                val token = FocusAutoAdvanceTimerPolicy.tokenFor(
                    commitSequence = snapshot.commitSequence,
                    manualInteractionSequence = snapshot.manualInteractionSequence,
                )
                delay(SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS)
                if (!FocusGalleryAutoAdvancePolicy.shouldRunTimer(
                        gameCount = pages.size,
                        galleryIdle = galleryIdle,
                        isVisible = isVisible,
                        isResumed = isResumed,
                        isCardPressed = galleryCardPressed,
                    )
                ) return@LaunchedEffect
                controller.beginAutoAdvanceIfCurrent(
                    gameCount = pages.size,
                    expectedCommitSequence = token.commitSequence,
                    expectedManualInteractionSequence = token.manualInteractionSequence,
                )
            }
            val geometry = FocusGalleryGeometryPolicy.geometry(
                viewportWidthDp = maxWidth.value,
                symmetricSafeGuardDp = symmetricSafeGuardDp.value,
                dockTopDp = (maxHeight.value - cardBottomGapDp).coerceAtLeast(0f),
                stageHeightDp = maxHeight.value,
                style = focusStyle,
            )
            FocusGalleryStage(
                snapshot = snapshot,
                games = pages,
                colors = colors,
                geometry = geometry,
                style = focusStyle,
                reducedMotion = reducedMotion,
                gameCount = pages.size,
                modifier = Modifier.fillMaxSize().focusGalleryDrag(
                    enabled = true,
                    controller = controller,
                    density = density,
                    geometry = geometry,
                    gameCount = pages.size,
                ),
                onOpen = { game -> onOpen(game) },
                onLaunch = { game -> onLaunch(game) },
                onPressedChanged = { pressed -> galleryCardPressed = pressed },
                onSelect = { card ->
                    controller.switchToGame(card.gameId, pages.size)
                },
            )
            if (pages.size > 1) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = geometry.bottomGapDp.dp + 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    pages.indices.forEach { index ->
                        val selected = index == snapshot.currentGameIndex.mod(pages.size)
                        Box(
                            Modifier.size(if (selected) 18.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (selected) .95f else .5f))
                                .semantics { contentDescription = "第${index + 1}页，共${pages.size}页" }
                                .clickable {
                                    controller.goToGame(index, pages.size)
                                },
                        )
                    }
                }
            }
            HeroCoverPrefetch(pages, snapshot.currentGameIndex, componentWidthPx, componentHeightPx)
            return@BoxWithConstraints
        }
        val cardWidthDp = LandscapeHeroSizingPolicy.centerCardWidthDp(maxWidth.value)
        val cardWidth = cardWidthDp.dp
        // With PageSize.Fixed the pager places the current page at the content padding,
        // so the padding must center the card: (viewport - cardWidth) / 2 on both sides.
        val cardSidePadding = ((maxWidth - cardWidth) / 2f).coerceAtLeast(0.dp)
        val pagerMaxHeightDp = maxHeight.value
        val effectiveCardHeightDp = cardHeightDp ?: pagerMaxHeightDp
        val sideCardWidthDp = LandscapeHeroSizingPolicy.sideCardWidthDp(maxWidth.value)
        val carouselOuterMarginDp = LandscapeHeroSizingPolicy.outerMarginDp(
            viewportWidthDp = maxWidth.value,
            symmetricSafeGuardDp = symmetricSafeGuardDp.value,
        )
        val sideCenterLeftDp = LandscapeHeroSizingPolicy.sideCenterLeftDp(
            viewportWidthDp = maxWidth.value,
            outerMarginDp = carouselOuterMarginDp,
            sideWidthDp = sideCardWidthDp,
        )
        val slotCenterDistanceDp = LandscapeHeroSizingPolicy.slotCenterDistanceDp(
            viewportWidthDp = maxWidth.value,
            sideCenterLeftDp = sideCenterLeftDp,
        )
        val landscapePageSpacing = LandscapeHeroSizingPolicy.pageSpacingDp(
            centerWidthDp = cardWidthDp,
            slotCenterDistanceDp = slotCenterDistanceDp,
        ).dp
        HorizontalPager(
            state = pagerState,
            pageSize = if (landscapePager) PageSize.Fixed(cardWidth) else PageSize.Fill,
            contentPadding = if (landscapePager) {
                PaddingValues(horizontal = cardSidePadding)
            } else {
                PaddingValues()
            },
            pageSpacing = if (landscapePager) landscapePageSpacing else 0.dp,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (pages.isEmpty()) return@HorizontalPager
            val game = pages[SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(page, pages.size)]
            val distanceFromCenter = if (landscapePager) {
                ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
            } else {
                0f
            }
            val pageOffset = abs(distanceFromCenter).coerceIn(0f, 1f)
            // Click semantics follow the pager's current center page while all visual
            // geometry below interpolates continuously with the raw page offset. During a
            // settle every card is inert so a moving center can never open game settings.
            val pagerSettling = landscapePager && pagerState.isScrollInProgress
            val isCenterCard = landscapePager && page == pagerState.currentPage && !pagerSettling
            val cardAlpha = if (landscapePager) {
                LandscapeHeroSizingPolicy.cardAlphaAt(pageOffset)
            } else {
                1f
            }
            val cardSaturation = if (landscapePager) {
                LandscapeHeroSizingPolicy.cardSaturationAt(pageOffset)
            } else {
                1f
            }
            val cardScale = if (landscapePager) {
                LandscapeHeroSizingPolicy.cardScaleAt(pageOffset)
            } else {
                1f
            }
            val interpolatedCardWidthDp = if (landscapePager) {
                LandscapeHeroSizingPolicy.cardWidthDpAt(cardWidthDp, sideCardWidthDp, pageOffset)
            } else {
                cardWidthDp
            }
            val interpolatedRadiusDp = if (landscapePager) {
                LandscapeHeroSizingPolicy.cardRadiusAt(pageOffset)
            } else {
                LandscapeHeroSizingPolicy.CENTER_RADIUS_DP
            }
            val infoAlpha = if (landscapePager) {
                LandscapeHeroSizingPolicy.infoAlphaAt(pageOffset)
            } else {
                1f
            }
            Box(Modifier.fillMaxSize().zIndex(1f - pageOffset)) {
                if (landscapeStyle != null) {
                    LandscapeSpotlightPage(
                        game = game,
                        colors = colors,
                        style = landscapeStyle,
                        safeContentPadding = safeContentPadding,
                        reducedTransparency = reducedTransparency,
                        cardHeightDp = effectiveCardHeightDp,
                        cardBottomGapDp = cardBottomGapDp,
                        isCenterCard = isCenterCard,
                        cardWidthDp = interpolatedCardWidthDp,
                        radiusDp = interpolatedRadiusDp,
                        cardScale = cardScale,
                        cardAlpha = cardAlpha,
                        cardSaturation = cardSaturation,
                        infoAlpha = infoAlpha,
                        interactionEnabled = !pagerSettling,
                        centerActionEnabled = isCenterCard,
                        onOpen = { onOpen(game) },
                        onLaunch = { onLaunch(game) },
                        onSelect = {
                            scope.launch {
                                if (reducedMotion) {
                                    pagerState.scrollToPage(page)
                                } else {
                                    pagerState.animateScrollToPage(
                                        page,
                                        animationSpec = tween(SpotlightCarouselPolicy.ANIMATE_MILLIS),
                                    )
                                }
                            }
                        },
                    )
                } else {
                    SpotlightPage(game, colors, styleKey, { onOpen(game) }) { onLaunch(game) }
                }
            }
        }
        if (pages.size > 1) {
            val pagerBottomGap = if (cardHeightDp != null) {
                cardBottomGapDp
            } else {
                (pagerMaxHeightDp - effectiveCardHeightDp).coerceAtLeast(0f)
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = pagerBottomGap.dp + 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                pages.indices.forEach { index ->
                    val selected = index == currentGameIndex
                    Box(
                        Modifier.size(if (selected) 18.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) .95f else .5f))
                            .semantics { contentDescription = "第${index + 1}页，共${pages.size}页" }
                            .clickable {
                                scope.launch {
                                    val target = SpotlightCircularCarouselPolicy.nearestVirtualPage(
                                        gameIndex = index,
                                        pageCount = pages.size,
                                        fromPage = pagerState.currentPage,
                                    )
                                    if (reducedMotion) pagerState.scrollToPage(target)
                                    else pagerState.animateScrollToPage(
                                        target,
                                        animationSpec = tween(SpotlightCarouselPolicy.ANIMATE_MILLIS),
                                    )
                                }
                            },
                    )
                }
            }
        }
        HeroCoverPrefetch(pages, currentGameIndex, componentWidthPx, componentHeightPx)
    }
}

@Composable
private fun FocusGalleryStage(
    snapshot: FocusGallerySnapshot,
    games: List<Game>,
    colors: KireiColors,
    geometry: FocusGalleryGeometry,
    style: LandscapeHomeStyle,
    reducedMotion: Boolean,
    gameCount: Int,
    modifier: Modifier = Modifier,
    onOpen: (Game) -> Unit,
    onLaunch: (Game) -> Unit,
    onSelect: (FocusCard) -> Unit,
    onPressedChanged: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val progress = snapshot.progress.coerceIn(0f, 1f)
    val transitioning = snapshot.isSettling
    val stageWidthPx = with(density) { (geometry.centerSlotCenterXDp * 2f).dp.toPx() }
    Box(modifier.clipToBounds()) {
        snapshot.cards.forEach { card ->
            key(card.key) {
                val game = games.firstOrNull { it.id == card.gameId }
                if (game != null) {
                    val cardGeometry = FocusGalleryGeometryPolicy.cardGeometry(
                        card = card,
                        progress = progress,
                        geometry = geometry,
                        reducedMotion = reducedMotion,
                    )
                    val cardCenterXPx = with(density) { cardGeometry.centerXDp.dp.toPx() }
                    val offsetXPx = cardCenterXPx - stageWidthPx / 2f
                    val offsetXDp = with(density) { offsetXPx.toDp() }
                    val centerActionEnabled = FocusGalleryInteractionPolicy.centerActionEnabled(
                        sourceSlot = card.sourceSlot,
                        progress = progress,
                        isSettling = snapshot.isSettling,
                        infoAlpha = cardGeometry.infoAlpha,
                    )
                    // C clears switch semantics on the underlying card; the transparent
                    // overlay below owns the action so a screen reader never announces the
                    // duplicated candidate twice. D keeps the direct card action.
                    val switchActionVisible = FocusGallerySemanticsPolicy.cardExposesSwitchSemantics(
                        style = style,
                        gameCount = gameCount,
                        card = card,
                        isSettling = snapshot.isSettling,
                    )
                    val sideSelectable = !transitioning &&
                        (card.sourceSlot == FocusSlot.LEFT || card.sourceSlot == FocusSlot.RIGHT)
                    Box(
                        Modifier
                            .width(cardGeometry.widthDp.dp)
                            .height(cardGeometry.heightDp.dp)
                            .offset(
                                x = offsetXDp,
                                y = FocusGalleryVisualPolicy.outerOffsetY(
                                    style = style,
                                    arcLift = cardGeometry.offsetYDp,
                                    bottomGap = geometry.bottomGapDp,
                                ).dp,
                            )
                            .align(Alignment.BottomCenter)
                            .zIndex(cardGeometry.zIndex)
                            .graphicsLayer {
                                scaleX = cardGeometry.scale
                                scaleY = cardGeometry.scale
                                // D-only readability lift; C keeps the raw policy alpha.
                                alpha = FocusGalleryVisualPolicy.stageAlpha(style, cardGeometry.alpha)
                                rotationY = cardGeometry.rotationYDegrees
                                cameraDistance = 6f * cardGeometry.heightDp.dp.toPx()
                            },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (FocusGalleryVisualPolicy.ownsOuterArtwork(style)) {
                            FocusCoverArtwork(
                                game = game,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = cardGeometry.radiusDp.dp,
                                role = CoverImageRole.HERO,
                                style = style,
                                darkTheme = colors.isDark,
                                focusAlpha = cardGeometry.infoAlpha,
                            )
                            FocusStageDOutline(
                                radiusDp = cardGeometry.radiusDp.dp,
                                infoAlpha = cardGeometry.infoAlpha,
                                darkTheme = colors.isDark,
                            )
                        }
                        LandscapeSpotlightPage(
                            game = game,
                            colors = colors,
                            style = style,
                            safeContentPadding = 0.dp,
                            reducedTransparency = false,
                            onOpen = { if (centerActionEnabled) onOpen(game) },
                            onLaunch = { if (centerActionEnabled) onLaunch(game) },
                            onSelect = { if (sideSelectable) onSelect(card) },
                            onPressedChanged = onPressedChanged,
                            cardHeightDp = cardGeometry.heightDp,
                            cardBottomGapDp = FocusGalleryVisualPolicy.innerBottomGap(
                                style = style,
                                bottomGap = geometry.bottomGapDp,
                            ),
                            isCenterCard = centerActionEnabled,
                            interactionEnabled = centerActionEnabled || sideSelectable,
                            exposeSwitchSemantics = switchActionVisible,
                            centerActionEnabled = centerActionEnabled,
                            applySideBlur = false,
                            cardWidthDp = cardGeometry.widthDp,
                            radiusDp = cardGeometry.radiusDp,
                            cardScale = 1f,
                            cardAlpha = 1f,
                            cardSaturation = cardGeometry.saturation,
                            infoAlpha = cardGeometry.infoAlpha,
                            drawArtwork = !FocusGalleryVisualPolicy.ownsOuterArtwork(style),
                            drawStyleBorder = !FocusGalleryVisualPolicy.ownsOuterArtwork(style),
                        )
                    }
                }
            }
        }
        // C-only transparent side hit targets: idle frames only, outside the center card's
        // left/right edge, vertically covering the scaled side strip. D cards stay fully
        // visible and keep their direct card clicks, so no overlay is emitted for D.
        val hitCards = snapshot.cards
            .filter { it.sourceSlot == FocusSlot.LEFT || it.sourceSlot == FocusSlot.RIGHT }
            .associateBy { it.sourceSlot }
        listOf(FocusSlot.LEFT, FocusSlot.RIGHT).forEach { side ->
            val bounds = FocusGalleryHitTargetPolicy.sideHitBounds(
                side = side,
                geometry = geometry,
                style = style,
                progress = progress,
                isSettling = snapshot.isSettling,
            )
            val card = hitCards[side]
            val game = card?.let { target -> games.firstOrNull { it.id == target.gameId } }
            if (bounds != null && card != null && game != null) {
                val exposeSwitch = FocusGallerySemanticsPolicy.overlayExposesSwitchSemantics(
                    style = style,
                    gameCount = gameCount,
                    card = card,
                    isSettling = snapshot.isSettling,
                )
                // zIndex .5 sits above the resting side-card visuals (z=0) and below the
                // center card (z=3), and the bounds never overlap the center span, so one
                // tap hits only this overlay and cannot double-fire with the card below.
                Box(
                    Modifier
                        .offset(x = bounds.leftDp.dp, y = bounds.topDp.dp)
                        .width(bounds.widthDp.dp)
                        .height(bounds.heightDp.dp)
                        .zIndex(.5f)
                        .then(
                            if (exposeSwitch) {
                                Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = "切换到 ${game.title.orEmpty()}"
                                    role = Role.Button
                                }
                            } else {
                                Modifier.clearAndSetSemantics { }
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSelect(card)
                        },
                )
            }
        }
    }
}

/**
 * Shared D card outline: the same 1dp theme-aware highlight that the FocusGalleryStage
 * outer card draws for IMMERSIVE_COVER and LandscapeSpotlightPage draws for non-stage
 * callers, so the D outer stage and the default page path stay pixel-identical.
 */
@Composable
private fun FocusStageDOutline(
    radiusDp: Dp,
    infoAlpha: Float,
    darkTheme: Boolean,
) {
    Box(
        Modifier.fillMaxSize().border(
            width = 1.dp,
            color = if (darkTheme) {
                Color.White.copy(
                    alpha = FocusGalleryVisualPolicy.outlineAlpha(
                        infoAlpha = infoAlpha,
                        darkTheme = true,
                    ),
                )
            } else {
                Color.Black.copy(
                    alpha = FocusGalleryVisualPolicy.outlineAlpha(
                        infoAlpha = infoAlpha,
                        darkTheme = false,
                    ),
                )
            },
            shape = RoundedCornerShape(radiusDp),
        ),
    )
}

@Composable
private fun LandscapeSpotlightPage(
    game: Game,
    colors: KireiColors,
    style: LandscapeHomeStyle,
    safeContentPadding: Dp,
    reducedTransparency: Boolean,
    onOpen: () -> Unit,
    onLaunch: () -> Unit,
    onSelect: () -> Unit = {},
    onPressedChanged: (Boolean) -> Unit = {},
    cardHeightDp: Float? = null,
    cardBottomGapDp: Float = 0f,
    isCenterCard: Boolean = true,
    interactionEnabled: Boolean = true,
    exposeSwitchSemantics: Boolean = !isCenterCard,
    centerActionEnabled: Boolean = isCenterCard,
    applySideBlur: Boolean = !isCenterCard,
    cardWidthDp: Float = 0f,
    radiusDp: Float = LandscapeHeroSizingPolicy.CENTER_RADIUS_DP,
    cardScale: Float = 1f,
    cardAlpha: Float = 1f,
    cardSaturation: Float = 1f,
    infoAlpha: Float = 1f,
    /** False when the FocusGalleryStage outer card already owns the artwork (D stage). */
    drawArtwork: Boolean = true,
    /** False when the outer stage already draws the D style border. */
    drawStyleBorder: Boolean = true,
) {
    // C and D share the same focus-card pipeline; the style controls geometry and whether
    // the immersive 16:9 card edge highlight is drawn.
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = if (cardHeightDp == null) Alignment.Center else Alignment.BottomCenter,
    ) {
        PressScaleBox(
            Modifier
                .then(
                    if (cardHeightDp == null) {
                        Modifier.fillMaxHeight()
                    } else {
                        Modifier.height(cardHeightDp.dp)
                    }
                )
                .offset(y = -cardBottomGapDp.dp)
                .width(cardWidthDp.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .then(
                    if (!applySideBlur) {
                        Modifier
                    } else {
                        Modifier.blur(LandscapeHeroSizingPolicy.SIDE_BLUR_DP.dp)
                            .saturationEffect(cardSaturation)
                    }
                )
                .then(
                    if (centerActionEnabled) {
                        Modifier.semantics {
                            contentDescription = "查看 ${game.title.orEmpty()}"
                        }
                    } else if (exposeSwitchSemantics) {
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "切换到 ${game.title.orEmpty()}"
                            role = Role.Button
                        }
                    } else {
                        Modifier
                    }
                ),
            RoundedCornerShape(radiusDp.dp),
            if (centerActionEnabled) onOpen else onSelect,
            pressedScale = .992f,
            enabled = interactionEnabled,
            exposeClickSemantics = centerActionEnabled || exposeSwitchSemantics || infoAlpha > 0f,
            onPressedChanged = onPressedChanged,
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radiusDp.dp))) {
                if (drawArtwork) {
                    FocusCoverArtwork(
                        game = game,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = radiusDp.dp,
                        role = CoverImageRole.HERO,
                        style = style,
                        darkTheme = colors.isDark,
                        focusAlpha = infoAlpha,
                    )
                }
                if (drawStyleBorder && style == LandscapeHomeStyle.IMMERSIVE_COVER) {
                    FocusStageDOutline(
                        radiusDp = radiusDp.dp,
                        infoAlpha = infoAlpha,
                        darkTheme = colors.isDark,
                    )
                }
                val focused = centerActionEnabled && infoAlpha >= .99f
                if (infoAlpha > 0f) {
                    // Local bottom-26% gradient capped at .78 keeps the metadata readable
                    // without a half-black card.
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(
                                if (cardHeightDp == null) {
                                    Modifier.fillMaxHeight(.46f)
                                } else {
                                    Modifier.height(cardHeightDp.dp * .46f)
                                },
                            )
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = .78f),
                                    ),
                                ),
                            )
                            .graphicsLayer { alpha = infoAlpha },
                    )
                    // The launch control leads the title stack. The title owns the full card
                    // width, so normal titles keep two unobstructed lines instead of sharing a
                    // row with the play button.
                    Column(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .graphicsLayer { alpha = infoAlpha },
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            PlayTriangleButton(
                                colors = colors,
                                description = "启动 ${game.title.orEmpty()}",
                                size = 54.dp,
                                onClick = onLaunch,
                                enabled = focused,
                            )
                        }
                        Text(
                            game.engine?.displayName ?: "游戏",
                            color = Color.White.copy(alpha = .78f),
                            style = AppleTypography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            game.title.orEmpty(),
                            color = Color.White,
                            style = AppleTypography.headlineMedium.copy(
                                shadow = Shadow(Color.Black.copy(alpha = .65f), blurRadius = 2f),
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One shared play-button size rule for both home modes. It mirrors the SpotlightPage cell
 * rule exactly: cells narrower than 240dp use 48dp, cells shorter than 216dp use 54dp,
 * and regular cells use 62dp. The immersive home resolves its center card frame through
 * the same resolver so both modes agree on the same device.
 */
internal object SharedControlSizePolicy {
    const val NARROW_CELL_WIDTH_DP = 240f
    const val COMPACT_CELL_HEIGHT_DP = 216f
    const val PLAY_NARROW_DP = 48f
    const val PLAY_COMPACT_DP = 54f
    const val PLAY_REGULAR_DP = 62f

    fun playSizeDp(cellWidthDp: Float, cellHeightDp: Float): Float = when {
        cellWidthDp < NARROW_CELL_WIDTH_DP -> PLAY_NARROW_DP
        cellHeightDp < COMPACT_CELL_HEIGHT_DP -> PLAY_COMPACT_DP
        else -> PLAY_REGULAR_DP
    }
}

@Composable
private fun SpotlightPage(
    game: Game,
    colors: KireiColors,
    styleKey: String,
    onOpen: () -> Unit,
    onLaunch: () -> Unit,
) {
    if (styleKey == DashboardStyles.SPOTLIGHT_HORIZONTAL) {
        PressScaleBox(Modifier.fillMaxSize(), RoundedCornerShape(24.dp), onOpen, pressedScale = .985f) {
            Row(
                Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(24.dp))
                    .border(.7.dp, colors.outline, RoundedCornerShape(24.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameCoverArtwork(
                    game,
                    Modifier.fillMaxHeight().widthIn(min = 72.dp, max = 150.dp).aspectRatio(.9f),
                    ContentScale.Crop,
                    role = CoverImageRole.HERO,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(game.title.orEmpty(), color = colors.text, style = AppleTypography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(game.engine?.displayName.orEmpty(), color = colors.secondary, style = AppleTypography.labelLarge)
                }
                PlayTriangleButton(colors, "启动 ${game.title.orEmpty()}", 44.dp, onLaunch)
            }
        }
        return
    }
    PressScaleBox(Modifier.fillMaxSize(), RoundedCornerShape(32.dp), onOpen, pressedScale = .985f) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
            val narrow = maxWidth < 240.dp
            val compactHeight = maxHeight < 216.dp
            val inset = if (narrow) 16.dp else if (compactHeight) 20.dp else 28.dp
            val playSize = SharedControlSizePolicy.playSizeDp(maxWidth.value, maxHeight.value).dp
            GameCoverArtwork(
                game,
                Modifier.fillMaxSize(),
                if (styleKey == DashboardStyles.SPOTLIGHT_POSTER) ContentScale.Fit else ContentScale.Crop,
                role = CoverImageRole.HERO,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = .04f), Color.Black.copy(alpha = .18f), Color.Black.copy(alpha = .91f))
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start = inset, top = inset, end = inset + playSize + 12.dp, bottom = inset)
            ) {
                Text(game.engine?.displayName ?: "游戏", color = Color.White.copy(alpha = .78f), style = AppleTypography.bodySmall)
                Text(
                    game.title.orEmpty(),
                    color = Color.White,
                    style = if (narrow || compactHeight) AppleTypography.headlineSmall else AppleTypography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
                if (!narrow && !compactHeight) {
                    Text(
                        "点按卡片查看更多操作",
                        color = Color.White.copy(alpha = .68f),
                        style = AppleTypography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(inset)) {
                PlayTriangleButton(
                    colors = colors,
                    description = "启动 ${game.title.orEmpty()}",
                    size = playSize,
                    onClick = onLaunch
                )
            }
        }
    }
}

@Composable
private fun HomeEmptySpotlight(
    colors: KireiColors,
    emptyState: ImmersiveGalleryEmptyState,
    onChooseDirectory: () -> Unit,
    onOpenSpotlightEditor: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(310.dp)
) {
    GlassSurface(colors, null, modifier, RoundedCornerShape(32.dp)) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            when (emptyState) {
                ImmersiveGalleryEmptyState.BUILD_LIBRARY -> {
                    Icon(Icons.Rounded.CollectionsBookmark, null, tint = colors.accent, modifier = Modifier.size(48.dp))
                    Text("开始建立游戏库", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                    Text("选择目录后，YukiHub 会识别 KRKR、ONS、Tyrano 与 Artemis。", color = colors.secondary, modifier = Modifier.padding(top = 8.dp))
                    Button(
                        onClick = onChooseDirectory,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.padding(top = 20.dp)
                    ) { Text("选择游戏目录") }
                }
                ImmersiveGalleryEmptyState.SELECT_GAMES -> {
                    Icon(Icons.Rounded.Edit, null, tint = colors.accent, modifier = Modifier.size(48.dp))
                    Text("请选择游戏", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                    Text("在设置中选择要展示的游戏。", color = colors.secondary, modifier = Modifier.padding(top = 8.dp))
                    Button(
                        onClick = onOpenSpotlightEditor,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.padding(top = 20.dp),
                    ) { Text("打开设置") }
                }
                ImmersiveGalleryEmptyState.NO_PLAY_HISTORY -> {
                    Text("暂无游玩记录", color = colors.text, style = AppleTypography.headlineSmall)
                    Text("游玩任意游戏后，这里会显示最近游玩。", color = colors.secondary, modifier = Modifier.padding(top = 8.dp))
                }
                ImmersiveGalleryEmptyState.NO_FAVORITES -> {
                    Text("暂无收藏", color = colors.text, style = AppleTypography.headlineSmall)
                }
            }
        }
    }
}

@Composable
internal fun GameCoverArtwork(
    game: Game,
    modifier: Modifier,
    contentScale: ContentScale,
    cornerRadius: Dp = 12.dp,
    role: CoverImageRole = CoverImageRole.THUMBNAIL,
    showFallbackMetadata: Boolean = true,
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(cornerRadius))) {
        val density = LocalDensity.current
        val bitmap = rememberCover(
            uri = game.coverUri,
            role = role,
            componentWidthPx = with(density) { maxWidth.roundToPx() },
            componentHeightPx = with(density) { maxHeight.roundToPx() },
        )
        if (bitmap == null) {
            EngineDefaultCover(
                engine = game.engine,
                modifier = Modifier.fillMaxSize(),
                showFallbackMetadata = showFallbackMetadata,
            )
        } else {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                filterQuality = CoverImagePolicy.filterQuality(role),
            )
        }
    }
}

@Composable
private fun EngineDefaultCover(
    engine: EngineType?,
    modifier: Modifier = Modifier,
    showFallbackMetadata: Boolean = true,
) {
    val (start, end) = engineCoverColors(engine)
    val icon = when (engine) {
        EngineType.KIRIKIRI -> Icons.Rounded.AutoStories
        EngineType.ONS -> Icons.Rounded.Description
        EngineType.TYRANO -> Icons.Rounded.Terminal
        else -> Icons.Rounded.Layers
    }
    Box(modifier.background(Brush.linearGradient(listOf(start, end)))) {
        Box(Modifier.size(96.dp).align(Alignment.TopEnd).background(Color.White.copy(alpha = .07f), CircleShape))
        if (showFallbackMetadata) {
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Icon(icon, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(26.dp))
                Text(engine?.displayName ?: "游戏", color = Color.White, style = AppleTypography.labelLarge, modifier = Modifier.padding(top = 5.dp))
                Text("KireiBox", color = Color.White.copy(alpha = .62f), style = AppleTypography.labelSmall)
            }
        } else {
            Icon(
                icon,
                null,
                tint = Color.White.copy(alpha = .9f),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(26.dp),
            )
        }
    }
}

/** Engine theme gradient shared by the missing-cover placeholder and portrait extension. */
private fun engineCoverColors(engine: EngineType?): Pair<Color, Color> = when (engine) {
        EngineType.KIRIKIRI -> Color(0xFF716BD8) to Color(0xFF3D386F)
        EngineType.ONS -> Color(0xFF5E83D6) to Color(0xFF30496F)
        EngineType.TYRANO -> Color(0xFFC8845D) to Color(0xFF704735)
        EngineType.ARTEMIS -> Color(0xFF8B67C9) to Color(0xFF493568)
        else -> Color(0xFF636366) to Color(0xFF2C2C2E)
}

/**
 * Style-aware hero cover artwork for the C/D focus stage.
 *
 * Matched-aspect sources draw one full-bleed Crop layer (single cover, no letterbox).
 * Severely mismatched sources (>= 1.35x) draw an adaptive stack: the same bitmap is
 * cropped, slightly enlarged, blurred (~16dp), desaturated and dimmed as the in-card
 * fill, with the complete artwork rendered on top via ContentScale.Fit. This keeps every
 * card full-bleed and never shows a half cover, black bars or a readable double cover:
 * the fill reads as atmosphere behind the full foreground image.
 */
@Composable
private fun FocusCoverArtwork(
    game: Game,
    modifier: Modifier,
    cornerRadius: Dp,
    role: CoverImageRole = CoverImageRole.HERO,
    style: LandscapeHomeStyle,
    darkTheme: Boolean,
    focusAlpha: Float,
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(cornerRadius))) {
        val density = LocalDensity.current
        val targetWidthPx = with(density) { maxWidth.roundToPx() }.toFloat()
        val targetHeightPx = with(density) { maxHeight.roundToPx() }.toFloat()
        val bitmap = rememberCover(
            uri = game.coverUri,
            role = role,
            componentWidthPx = with(density) { maxWidth.roundToPx() },
            componentHeightPx = with(density) { maxHeight.roundToPx() },
        )
        if (bitmap == null) {
            EngineDefaultCover(game.engine, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val plan = remember(
            bitmap.width,
            bitmap.height,
            targetWidthPx.toInt(),
            targetHeightPx.toInt(),
            style,
        ) {
            FocusCoverArtworkPolicy.plan(
                srcWidthPx = bitmap.width,
                srcHeightPx = bitmap.height,
                targetWidthPx = targetWidthPx,
                targetHeightPx = targetHeightPx,
                style = style,
            )
        }
        if (plan.mode == FocusCoverMode.CROP) {
            androidx.compose.foundation.Image(
                bitmap,
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val blend = remember(focusAlpha) {
                FocusCoverArtworkPolicy.adaptiveBlend(focusAlpha)
            }
            Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.Image(
                    bitmap,
                    null,
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = FocusCoverArtworkPolicy.FILL_SCALE
                            scaleY = FocusCoverArtworkPolicy.FILL_SCALE
                            alpha = blend.blurredFillAlpha
                        }
                        .blur(FocusCoverArtworkPolicy.FILL_BLUR_DP.dp)
                        .saturationEffect(FocusCoverArtworkPolicy.FILL_SATURATION),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        if (darkTheme) {
                            Color.Black.copy(
                                alpha = FocusCoverArtworkPolicy.FILL_DARK_ALPHA * blend.overlayStrength,
                            )
                        } else {
                            Color.White.copy(
                                alpha = FocusCoverArtworkPolicy.FILL_LIGHT_ALPHA * blend.overlayStrength,
                            )
                        }
                    )
                )
                androidx.compose.foundation.Image(
                    bitmap,
                    null,
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = blend.sharpCropAlpha
                    },
                    contentScale = ContentScale.Crop,
                )
                androidx.compose.foundation.Image(
                    bitmap,
                    null,
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = blend.fitAlpha
                    },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun HomeInsightRail(
    gameCount: Int,
    favoriteCount: Int,
    playTime: Long,
    colors: KireiColors,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
    styleKey: String = DashboardStyles.OVERVIEW_SPLIT,
    onOpenLibrary: () -> Unit
) {
    PressScaleBox(modifier, RoundedCornerShape(22.dp), onOpenLibrary, pressedScale = .985f) {
        if (styleKey == DashboardStyles.OVERVIEW_TILES) {
            Row(
                Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(22.dp))
                    .border(.7.dp, colors.outline, RoundedCornerShape(22.dp)).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("游戏" to "$gameCount", "收藏" to "$favoriteCount", "时长" to LibraryOverviewPolicy.summaryPlayTime(playTime)).forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().background(colors.surfaceRaised, RoundedCornerShape(15.dp))
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(value, color = colors.text, style = AppleTypography.bodyLargeEmphasis, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(label, color = colors.secondary, style = AppleTypography.labelMedium)
                    }
                }
            }
            return@PressScaleBox
        }
        if (styleKey == DashboardStyles.OVERVIEW_FOCUS) {
            Column(
                Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(22.dp))
                    .border(.7.dp, colors.outline, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(LibraryOverviewPolicy.summaryPlayTime(playTime), color = colors.accent, style = AppleTypography.displayLarge, maxLines = 1)
                Text("累计游玩 · $gameCount 款游戏 · $favoriteCount 款收藏", color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 1)
            }
            return@PressScaleBox
        }
        if (styleKey == DashboardStyles.OVERVIEW_RING) {
            Row(
                Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(22.dp))
                    .border(.7.dp, colors.outline, RoundedCornerShape(22.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(if (compact) 50.dp else 62.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { if (gameCount == 0) 0f else favoriteCount.toFloat() / gameCount },
                        color = colors.accent,
                        trackColor = colors.surfaceRaised,
                        strokeWidth = if (compact) 6.dp else 7.dp
                    )
                    Text("$favoriteCount", color = colors.text, style = AppleTypography.bodyLargeEmphasis)
                }
                Column(Modifier.padding(start = if (compact) 8.dp else 12.dp).weight(1f)) {
                    Text("累计游玩", color = colors.secondary, style = AppleTypography.labelMedium)
                    Text(
                        if (compact) LibraryOverviewPolicy.summaryPlayTime(playTime).replace(" ", "") else LibraryOverviewPolicy.summaryPlayTime(playTime),
                        color = colors.text,
                        style = if (compact) AppleTypography.bodyLargeEmphasis else AppleTypography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("$gameCount 部游戏", color = colors.secondary, style = AppleTypography.labelLarge)
                }
            }
            return@PressScaleBox
        }
        if (compact || styleKey == DashboardStyles.OVERVIEW_MINIMAL) {
            Column(
                Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(22.dp))
                    .border(.7.dp, colors.outline, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("累计游玩", color = colors.secondary, style = AppleTypography.labelMedium)
                Text(LibraryOverviewPolicy.summaryPlayTime(playTime), color = colors.text, style = AppleTypography.headlineMedium, maxLines = 1)
                Text("$gameCount 部游戏 · $favoriteCount 部收藏", color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 2)
            }
            return@PressScaleBox
        }
        Row(
            Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(22.dp))
                .border(.7.dp, colors.outline, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1.65f)) {
                Text("累计游玩", color = colors.secondary, style = AppleTypography.labelMedium)
                Text(LibraryOverviewPolicy.summaryPlayTime(playTime), color = colors.text, style = AppleTypography.headlineSmall, maxLines = 1)
                Text("继续积累你的故事时间", color = colors.secondary, style = AppleTypography.labelMedium, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.width(1.dp).height(58.dp).background(colors.outline))
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeInsight("游戏", "$gameCount 部", colors, horizontal = true)
                HomeInsight("收藏", "$favoriteCount 部", colors, horizontal = true)
            }
        }
    }
}

@Composable
private fun HomeInsight(label: String, value: String, colors: KireiColors, horizontal: Boolean = false) {
    if (horizontal) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.secondary, style = AppleTypography.labelMedium)
            Text(value, color = colors.text, style = AppleTypography.headlineSmall, maxLines = 1)
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = colors.secondary, style = AppleTypography.labelMedium)
            Text(value, color = colors.text, style = AppleTypography.headlineSmall, maxLines = 1)
        }
    }
}

@Composable
private fun DashboardRecentWidget(
    games: List<Game>,
    showThreeAcross: Boolean,
    constrainToInnerBounds: Boolean,
    compact: Boolean,
    styleKey: String,
    colors: KireiColors,
    haze: HazeState?,
    onOpenLibrary: () -> Unit,
    onOpenGame: (Game) -> Unit,
    onLaunchGame: (Game) -> Unit
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近打开", color = colors.text, style = if (compact) AppleTypography.bodyLargeEmphasis else AppleTypography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1)
                HazeLiquidActionButton(
                    colors = colors,
                    haze = haze,
                    description = "查看全部",
                    onClick = onOpenLibrary,
                    label = "查看全部",
                    icon = Icons.Rounded.ChevronRight,
                    showBorder = RecentGamesSurfacePolicy.SHOW_ACTION_BORDER,
                )
            }
            if (games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("还没有游玩记录", color = colors.secondary, style = AppleTypography.bodySmall)
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val maxItems = when {
                        showThreeAcross -> 3
                        constrainToInnerBounds -> 2
                        else -> 3
                    }
                    val itemCount = RecentGamesRowPolicy.itemCount(maxWidth.value, maxItems)
                    val visibleGames = games.take(itemCount)
                    val cardWidth = RecentGamesRowPolicy.cardWidthDp(maxWidth.value, itemCount)
                    if (constrainToInnerBounds) {
                        // Weighted row: items always divide the real viewport, so every
                        // recent item stays complete and no second card can be clipped.
                        Row(
                            Modifier.fillMaxWidth().fillMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(RecentGamesRowPolicy.ITEM_GAP_DP.dp),
                        ) {
                            visibleGames.forEach { game ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    RecentGamesItem(
                                        game = game,
                                        modifier = Modifier.fillMaxSize(),
                                        compact = compact,
                                        styleKey = styleKey,
                                        colors = colors,
                                        haze = haze,
                                        onOpenGame = onOpenGame,
                                        onLaunchGame = onLaunchGame,
                                    )
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            Modifier.fillMaxSize().clipToBounds(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(
                                end = if (showThreeAcross) 0.dp else 24.dp,
                            ),
                            userScrollEnabled = true,
                        ) {
                            items(visibleGames, key = { it.id }) { game ->
                                RecentGamesItem(
                                    game = game,
                                    modifier = Modifier.width(cardWidth.dp).fillMaxHeight(),
                                    compact = compact,
                                    styleKey = styleKey,
                                    colors = colors,
                                    haze = haze,
                                    onOpenGame = onOpenGame,
                                    onLaunchGame = onLaunchGame,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    val outerShape = RoundedCornerShape(RecentGamesSurfacePolicy.OUTER_RADIUS_DP.dp)
    val surfaceColor = if (colors.isDark) {
        Color.Black.copy(alpha = RecentGamesSurfacePolicy.surfaceAlpha(isDark = true))
    } else {
        Color.White.copy(alpha = RecentGamesSurfacePolicy.surfaceAlpha(isDark = false))
    }
    Box(
        Modifier.fillMaxSize()
            .clip(outerShape)
            .background(surfaceColor)
            .then(
                if (RecentGamesSurfacePolicy.SHOW_OUTER_BORDER) {
                    Modifier.border(.7.dp, colors.outline, outerShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RecentGamesItem(
    game: Game,
    modifier: Modifier = Modifier,
    compact: Boolean,
    styleKey: String,
    colors: KireiColors,
    haze: HazeState?,
    onOpenGame: (Game) -> Unit,
    onLaunchGame: (Game) -> Unit,
) {
    PressScaleBox(
        modifier,
        RoundedCornerShape(18.dp),
        { onOpenGame(game) },
        pressedScale = .98f,
    ) {
        if (styleKey == DashboardStyles.RECENT_COVERS) {
            Box(
                Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(18.dp))
                    .border(.7.dp, colors.outline, RoundedCornerShape(18.dp)),
            ) {
                GameCoverArtwork(game, Modifier.fillMaxSize(), ContentScale.Crop, 18.dp)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f))),
                    ),
                )
                Text(
                    game.title.orEmpty(),
                    color = Color.White,
                    style = AppleTypography.bodyLargeEmphasis,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                )
            }
        } else {
            Row(
                Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(18.dp))
                    .border(.7.dp, colors.outline, RoundedCornerShape(18.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameCoverArtwork(
                    game,
                    Modifier.size(if (compact) 42.dp else 52.dp, if (compact) 52.dp else 66.dp),
                    ContentScale.Crop,
                )
                Column(Modifier.weight(1f).padding(horizontal = if (compact) 7.dp else 10.dp)) {
                    Text(
                        game.title.orEmpty(),
                        color = colors.text,
                        style = AppleTypography.bodyLargeEmphasis,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        game.engine?.displayName.orEmpty(),
                        color = colors.secondary,
                        style = AppleTypography.labelLarge,
                        maxLines = 1,
                    )
                }
                HazeLiquidActionButton(
                    colors = colors,
                    haze = haze,
                    description = "启动 ${game.title.orEmpty()}",
                    onClick = { onLaunchGame(game) },
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.size(if (compact) 40.dp else 44.dp),
                )
            }
        }
    }
}

@Composable
private fun DashboardFavoritesWidget(
    games: List<Game>,
    styleKey: String,
    colors: KireiColors,
    haze: HazeState?,
    onOpenLibrary: () -> Unit
) {
    GlassSurface(
        colors, haze, Modifier.fillMaxSize(), RoundedCornerShape(22.dp),
        role = KireiGlassRole.CONTENT, onClick = onOpenLibrary
    ) {
        if (styleKey == DashboardStyles.FAVORITES_COUNT) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${games.size}", color = colors.danger, style = AppleTypography.displayLarge)
                Text("款收藏", color = colors.secondary, style = AppleTypography.labelLarge)
            }
            return@GlassSurface
        }
        if (styleKey == DashboardStyles.FAVORITES_FEATURED && games.isNotEmpty()) {
            Box(Modifier.fillMaxSize()) {
                GameCoverArtwork(games.first(), Modifier.fillMaxSize(), ContentScale.Crop, 22.dp)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text("收藏主封面", color = Color.White.copy(alpha = .72f), style = AppleTypography.labelMedium)
                    Text(games.first().title.orEmpty(), color = Color.White, style = AppleTypography.bodyLargeEmphasis, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            return@GlassSurface
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = colors.danger, modifier = Modifier.size(22.dp))
                Text("收藏", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
            }
            Text("${games.size} 部游戏", color = colors.secondary, style = AppleTypography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (games.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (styleKey == DashboardStyles.FAVORITES_MOSAIC) 6.dp else (-8).dp)
                ) {
                    games.take(3).forEach { game ->
                        Box(
                            if (styleKey == DashboardStyles.FAVORITES_MOSAIC) {
                                Modifier.size(42.dp, 54.dp).clip(RoundedCornerShape(9.dp)).background(colors.surfaceRaised)
                            } else {
                                Modifier.size(38.dp).clip(CircleShape).background(colors.surfaceRaised)
                                    .border(2.dp, colors.surface, CircleShape)
                            }
                        ) {
                            GameCoverArtwork(game, Modifier.fillMaxSize(), ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardTrendWidget(
    points: List<PlayTrendPoint>,
    spec: DashboardWidgetSpec,
    colors: KireiColors,
    haze: HazeState?,
    interactive: Boolean = true,
) {
    val compact = spec.size.columns == 1
    val model = remember(points, spec.trendMetric, spec.trendRange, compact) {
        TrendChartPolicy.chart(points, spec.trendMetric, spec.trendRange, compact)
    }
    val dateLabel = remember { java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current
    var chartWidth by remember { mutableFloatStateOf(0f) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    fun selectAt(x: Float) {
        val next = TrendChartPolicy.nearestIndex(x, chartWidth, model.values.size)
        if (next != selectedIndex) {
            val previous = selectedIndex
            selectedIndex = next.takeIf { it >= 0 }
            if (TrendChartPolicy.shouldHaptic(previous, next)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    GlassSurface(colors, haze, Modifier.fillMaxSize(), RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.title,
                        color = colors.text,
                        style = AppleTypography.bodyLargeEmphasis
                    )
                    Text(
                        model.summary,
                        color = colors.secondary,
                        style = AppleTypography.labelLarge
                    )
                }
                Icon(Icons.Rounded.ShowChart, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            }
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 7.dp)) {
                Column(
                    Modifier.width(38.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    model.axis.ticks.reversed().forEach { tick ->
                        Text("${tick.toInt()} ${model.unit}", color = colors.secondary, style = AppleTypography.labelMedium)
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight().onSizeChanged { chartWidth = it.width.toFloat() }
                        .then(
                            if (interactive) Modifier.pointerInput(model.values.size, chartWidth) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { selectAt(it.x) },
                                    onDragCancel = { selectedIndex = null },
                                    onDragEnd = { selectedIndex = null },
                                    onDrag = { change, _ -> change.consume(); selectAt(change.position.x) },
                                )
                            } else Modifier
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val values = model.values
                        if (values.isEmpty()) return@Canvas
                        model.axis.ticks.forEach { tick ->
                            val y = size.height - tick / model.axis.maximum * size.height
                            drawLine(colors.outline.copy(alpha = .42f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
                        }
                        val step = if (values.size <= 1) size.width else size.width / (values.size - 1)
                        val line = Path()
                        values.forEachIndexed { index, value ->
                            val x = if (values.size <= 1) size.width / 2f else step * index
                            val y = size.height - value / model.axis.maximum * size.height
                            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                        }
                        if (spec.trendVisualStyle == TrendVisualStyle.AREA) {
                            drawPath(Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, colors.accent.copy(alpha = .14f))
                        }
                        drawPath(line, colors.accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                        selectedIndex?.takeIf { it in values.indices }?.let { index ->
                            val x = if (values.size <= 1) size.width / 2f else step * index
                            val y = size.height - values[index] / model.axis.maximum * size.height
                            drawLine(colors.accent.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
                            drawCircle(colors.accent, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                        }
                    }
                    selectedIndex?.takeIf { it in model.values.indices }?.let { index ->
                        val point = points.takeLast(spec.trendRange.days).getOrNull(index)
                        Surface(
                            color = colors.text.copy(alpha = .92f), contentColor = colors.background,
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.align(Alignment.TopCenter).padding(4.dp),
                        ) {
                            Text(
                                "${point?.let { dateLabel.format(Date(it.dayStart)) } ?: ""} · ${model.values[index].toInt()} ${model.unit}",
                                style = AppleTypography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            if (model.dateTicks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Spacer(Modifier.width(38.dp))
                    model.dateTicks.forEach { dayStart ->
                        Text(
                            dateLabel.format(Date(dayStart)),
                            color = colors.secondary,
                            style = AppleTypography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardLibraryShortcut(
    gameCount: Int,
    playedCount: Int,
    styleKey: String,
    colors: KireiColors,
    haze: HazeState?,
    onOpenLibrary: () -> Unit
) {
    GlassSurface(
        colors, haze, Modifier.fillMaxSize(), RoundedCornerShape(22.dp),
        role = KireiGlassRole.CONTENT, onClick = onOpenLibrary
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.GridView, null, tint = colors.accent, modifier = Modifier.size(28.dp))
            Text("游戏库", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                if (styleKey == DashboardStyles.LIBRARY_PROGRESS) {
                    "已打开 $playedCount / $gameCount 款"
                } else {
                    "$gameCount 部游戏"
                },
                color = colors.secondary,
                style = AppleTypography.bodySmall
            )
            if (styleKey == DashboardStyles.LIBRARY_PROGRESS) {
                val progress = if (gameCount == 0) 0f else playedCount.toFloat() / gameCount
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp).height(5.dp)
                        .background(colors.surfaceRaised, CircleShape)
                ) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.accent, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun HomeRecentGame(game: Game, colors: KireiColors, modifier: Modifier = Modifier, onOpen: () -> Unit, onLaunch: () -> Unit) {
    PressScaleBox(modifier, RoundedCornerShape(22.dp), onOpen) {
        Row(
            Modifier.fillMaxSize().background(colors.surface).border(.7.dp, colors.outline, RoundedCornerShape(22.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameCoverArtwork(game, Modifier.size(86.dp), ContentScale.Crop, 16.dp)
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(game.title.orEmpty(), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(game.engine?.displayName ?: "游戏", color = colors.secondary, style = AppleTypography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            }
            PlayTriangleButton(colors, "启动", 42.dp, onLaunch)
        }
    }
}

@Composable
private fun DashboardEditorPage(
    profile: DashboardProfile,
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    onClose: () -> Unit
) {
    val recent = LibraryOverviewPolicy.sorted(state.games, LibraryOverviewSort.RECENT)
    val stored = if (profile == DashboardProfile.COMPACT) state.dashboardCompact else state.dashboardWide
    val initial = remember(profile, stored) { DashboardLayouts.normalize(stored, profile) }
    var draft by remember(profile, initial) { mutableStateOf(initial) }
    var showAdd by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var rebindInstanceId by remember { mutableStateOf<String?>(null) }
    var styleInstanceId by remember { mutableStateOf<String?>(null) }
    var trendConfigInstanceId by remember { mutableStateOf<String?>(null) }
    val dirty = draft != DashboardLayouts.normalize(initial, profile)
    val closeOrConfirm: () -> Unit = { if (dirty) showDiscard = true else onClose() }
    BackHandler(onBack = closeOrConfirm)

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxSize().widthIn(max = 1180.dp)
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = closeOrConfirm) { Text("取消", color = colors.secondary) }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("编辑首页", color = colors.text, style = AppleTypography.headlineMedium)
                    Text(
                        if (profile == DashboardProfile.COMPACT) "手机 · 2 列布局" else "宽屏 · 4 列布局",
                        color = colors.secondary,
                        style = AppleTypography.labelLarge
                    )
                }
                TextButton(
                    onClick = {
                        viewModel.saveDashboardLayout(profile, draft)
                        onClose()
                    },
                    enabled = dirty,
                    modifier = Modifier.padding(start = 4.dp)
                ) { Text("保存", color = if (dirty) colors.accent else colors.secondary) }
            }
            Text(
                "长按组件拖动；左下角更换样式，右下角拖动可实时调整高度。",
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 14.dp)
            )
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                if (draft.widgets.isEmpty()) {
                    GlassSurface(colors, haze, Modifier.fillMaxWidth().height(190.dp), RoundedCornerShape(26.dp)) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text("首页是空的", color = colors.text, style = AppleTypography.headlineMedium)
                            Text("点击右下角加号添加第一个组件。", color = colors.secondary, style = AppleTypography.bodyMedium)
                        }
                    }
                } else {
                    DashboardGrid(
                        draft,
                        profile,
                        DashboardDensity.STANDARD,
                        Modifier.fillMaxWidth().animateContentSize(),
                    ) { spec ->
                        DashboardEditorWidget(
                            spec = spec,
                            profile = profile,
                            games = state.games,
                            recent = recent,
                            playTrend = state.playTrend,
                            colors = colors,
                            haze = haze,
                            autoFill = draft.autoFill,
                            onRemove = { draft = DashboardLayouts.remove(draft, profile, spec.instanceId) },
                            onMove = { direction ->
                                val current = draft.widgets.indexOfFirst { it.instanceId == spec.instanceId }
                                draft = DashboardLayouts.move(draft, profile, spec.instanceId, current + direction)
                            },
                            onPlaceDelta = { columnDelta, rowDelta ->
                                draft.widgets.firstOrNull { it.instanceId == spec.instanceId }?.let { current ->
                                    draft = DashboardLayouts.placeAt(
                                        draft, profile, spec.instanceId,
                                        current.column.coerceAtLeast(0) + columnDelta,
                                        current.row.coerceAtLeast(0) + rowDelta
                                    )
                                }
                            },
                            onResizeWidth = { columns ->
                                draft = DashboardLayouts.resizeWidth(draft, profile, spec.instanceId, columns)
                            },
                            onResizeHeight = { heightSteps ->
                                draft = DashboardLayouts.resizeHeight(draft, profile, spec.instanceId, heightSteps)
                            },
                            onStyle = { styleInstanceId = spec.instanceId },
                            onTrendConfig = { trendConfigInstanceId = spec.instanceId },
                            onRebind = { rebindInstanceId = spec.instanceId }
                        )
                    }
                }
                Spacer(Modifier.height(92.dp))
            }
        }
        Surface(
            color = colors.surfaceRaised.copy(alpha = .96f),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 5.dp,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, bottom = 18.dp)
        ) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                DashboardArrangeSegment(
                    selected = draft.autoFill,
                    icon = Icons.Rounded.AutoFixHigh,
                    label = "自动排列",
                    colors = colors
                ) { draft = DashboardLayouts.setAutoFill(draft, profile, true) }
                DashboardArrangeSegment(
                    selected = !draft.autoFill,
                    icon = Icons.Rounded.OpenWith,
                    label = "自由摆放",
                    colors = colors
                ) { draft = DashboardLayouts.setAutoFill(draft, profile, false) }
            }
        }
        GlassSurface(
            colors, haze,
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 18.dp).size(54.dp),
            CircleShape, KireiGlassRole.ICON, onClick = { showAdd = true }
        ) {
            Box(Modifier.fillMaxSize().background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, "添加组件", tint = colors.onAccent, modifier = Modifier.size(28.dp))
            }
        }
        if (showAdd) {
            DashboardAddSheet(
                profile = profile,
                layout = draft,
                games = state.games,
                colors = colors,
                onDismiss = { showAdd = false },
                onAdd = { type, gameId ->
                    draft = DashboardLayouts.add(draft, profile, type, gameId)
                    showAdd = false
                }
            )
        }
        rebindInstanceId?.let { instanceId ->
            val spec = draft.widgets.firstOrNull { it.instanceId == instanceId }
            if (spec != null) {
                SpotlightConfigSheet(
                    spec = spec,
                    games = state.games,
                    colors = colors,
                    onDismiss = { rebindInstanceId = null },
                    onSettings = { next ->
                        val settings = next.normalized()
                        if (settings.mode == SpotlightSelectionMode.MANUAL) {
                            val manualIds = settings.manualGameIds.ifEmpty {
                                if (spec.spotlightSelectionMode == SpotlightSelectionMode.AUTOMATIC) {
                                    listOfNotNull(spec.gameId)
                                } else {
                                    emptyList()
                                }
                            }
                            draft = DashboardLayouts.setSpotlightManual(
                                draft,
                                profile,
                                instanceId,
                                manualIds,
                                settings.count,
                            )
                        } else {
                            draft = DashboardLayouts.setSpotlightAutomatic(
                                draft,
                                profile,
                                instanceId,
                                settings.source,
                                settings.strategy,
                                settings.count,
                            )
                        }
                    },
                )
            }
        }
        styleInstanceId?.let { instanceId ->
            val spec = draft.widgets.firstOrNull { it.instanceId == instanceId }
            if (spec != null) {
                KireiActionSheet("选择组件样式", colors, { styleInstanceId = null }) {
                    DashboardStyles.allowed(spec.type).forEach { style ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                draft = DashboardLayouts.setStyle(draft, profile, instanceId, style)
                                styleInstanceId = null
                            }.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dashboardStyleLabel(style), color = colors.text, style = AppleTypography.bodyLargeEmphasis, modifier = Modifier.weight(1f))
                            if (spec.styleKey == style) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                        }
                    }
                }
            }
        }
        trendConfigInstanceId?.let { instanceId ->
            draft.widgets.firstOrNull { it.instanceId == instanceId && it.type == DashboardWidgetType.PLAY_TREND }?.let { spec ->
                TrendConfigSheet(
                    spec = spec,
                    points = state.playTrend,
                    colors = colors,
                    haze = haze,
                    onDismiss = { trendConfigInstanceId = null },
                    onComplete = { config ->
                        draft = DashboardTrendEditorPolicy.complete(draft, profile, instanceId, config)
                        trendConfigInstanceId = null
                    },
                )
            }
        }
        if (showDiscard) {
            KireiActionSheet("放弃首页修改？", colors, { showDiscard = false }) {
                Text(
                    "尚未保存的组件顺序、大小和增删会丢失。",
                    color = colors.secondary,
                    style = AppleTypography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                )
                SheetActionRow(Icons.Rounded.DeleteOutline, "放弃修改", "返回原来的首页布局", colors, destructive = true) {
                    showDiscard = false
                    onClose()
                }
                TextButton(onClick = { showDiscard = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("继续编辑", color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun DashboardArrangeSegment(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    colors: KireiColors,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) colors.accent else Color.Transparent,
        contentColor = if (selected) colors.onAccent else colors.secondary,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Text(label, style = AppleTypography.labelLarge, modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun DashboardEditorWidget(
    spec: DashboardWidgetSpec,
    profile: DashboardProfile,
    games: List<Game>,
    recent: List<Game>,
    playTrend: List<PlayTrendPoint>,
    colors: KireiColors,
    haze: HazeState?,
    autoFill: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onPlaceDelta: (Int, Int) -> Unit,
    onResizeWidth: (Int) -> Unit,
    onResizeHeight: (Int) -> Unit,
    onStyle: () -> Unit,
    onTrendConfig: () -> Unit,
    onRebind: () -> Unit
) {
    val polished = LocalResolvedMotionMode.current == MotionMode.POLISHED
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var resizing by remember { mutableStateOf(false) }
    var resizeDistance by remember { mutableFloatStateOf(0f) }
    var resizeOvershoot by remember { mutableFloatStateOf(0f) }
    var resizeStartSteps by remember { mutableIntStateOf(spec.heightSteps) }
    val latestHeightSteps by rememberUpdatedState(spec.heightSteps)
    val latestResizeHeight by rememberUpdatedState(onResizeHeight)
    var removing by remember { mutableStateOf(false) }
    val removeProgress by animateFloatAsState(if (removing) 0f else 1f, tween(160), label = "dashboardRemove")
    LaunchedEffect(removing) { if (removing) { delay(160); onRemove() } }
    val wobbleTransition = rememberInfiniteTransition(label = "dashboardWobble")
    val wobbleDirection = if (spec.instanceId.hashCode() and 1 == 0) 1f else -1f
    val wobble by wobbleTransition.animateFloat(
        initialValue = -.55f * wobbleDirection,
        targetValue = .55f * wobbleDirection,
        animationSpec = infiniteRepeatable(tween(140), RepeatMode.Reverse),
        label = "dashboardWobbleAngle"
    )
    val missingGame = spec.type == DashboardWidgetType.GAME_SPOTLIGHT &&
        if (spec.spotlightSelectionMode == SpotlightSelectionMode.MANUAL) {
            spec.spotlightGameIds.isNotEmpty() && spec.spotlightGameIds.none { id -> games.any { it.id == id } }
        } else {
            spec.spotlightSource == SpotlightSource.FIXED &&
                spec.gameId != null &&
                games.none { it.id == spec.gameId }
        }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            rotationZ = if (polished && !dragging && !removing) wobble else 0f
            alpha = removeProgress
            scaleX = removeProgress
            scaleY = removeProgress
            if (dragging && polished) {
                scaleX = 1.025f
                scaleY = 1.025f
                shadowElevation = 12.dp.toPx()
            }
        }.animateContentSize(
            if (polished) spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow) else tween(90)
        )
            .pointerInput(spec.instanceId, autoFill) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragX = 0f
                        dragY = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = { dragging = false; dragX = 0f; dragY = 0f },
                    onDragEnd = { dragging = false; dragX = 0f; dragY = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                        if (autoFill) {
                            val primary = if (kotlin.math.abs(dragY) >= kotlin.math.abs(dragX)) dragY else dragX
                            if (kotlin.math.abs(primary) >= threshold) {
                                onMove(if (primary > 0f) 1 else -1)
                                dragX = 0f; dragY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else {
                            if (kotlin.math.abs(dragX) >= threshold) {
                                onPlaceDelta(if (dragX > 0f) 1 else -1, 0)
                                dragX = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (kotlin.math.abs(dragY) >= threshold) {
                                onPlaceDelta(0, if (dragY > 0f) 1 else -1)
                                dragY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                )
            }
    ) {
        if (missingGame) {
            GlassSurface(colors, haze, Modifier.fillMaxSize(), RoundedCornerShape(26.dp), KireiGlassRole.CONTENT, onRebind) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.Gamepad, null, tint = colors.accent)
                    Text("游戏已移出", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                    Text("点按重新选择游戏", color = colors.secondary, style = AppleTypography.bodySmall)
                }
            }
        } else {
            HomeDashboardWidget(
                spec = spec,
                profile = profile,
                games = games,
                recent = recent,
                playTrend = playTrend,
                colors = colors,
                haze = haze,
                interactiveTrend = false,
                onOpenOverview = {},
                onOpenLibrary = {},
                onOpenGame = {},
                onLaunchGame = {},
                onChooseDirectory = {}
            )
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = if (colors.isDark) .10f else .04f), RoundedCornerShape(24.dp)))
        }

        if (spec.type != DashboardWidgetType.PLAY_TREND && DashboardStyles.allowed(spec.type).size > 1) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = onStyle,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = colors.surfaceRaised.copy(alpha = .96f),
                    contentColor = colors.accent,
                    shadowElevation = 6.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Palette, "更改组件样式", modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        if (spec.type == DashboardWidgetType.PLAY_TREND) {
            Surface(
                onClick = onTrendConfig, color = colors.surfaceRaised.copy(alpha = .96f), contentColor = colors.accent,
                shape = CircleShape, shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp).size(40.dp),
            ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Tune, "趋势图设置", Modifier.size(22.dp)) } }
        }

        if (spec.type == DashboardWidgetType.GAME_SPOTLIGHT) {
            Surface(
                onClick = onRebind,
                color = colors.surfaceRaised.copy(alpha = .96f),
                contentColor = colors.accent,
                shape = CircleShape,
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp).size(40.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tune, "设置头图来源与展示方式", modifier = Modifier.size(22.dp))
                }
            }
        }

        Surface(
            onClick = { if (!removing) removing = true },
            color = colors.danger,
            contentColor = Color.White,
            shape = CircleShape,
            shadowElevation = 4.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(34.dp)
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Remove, "移除组件", Modifier.size(20.dp)) } }

        val allowedWidths = DashboardLayouts.allowedWidths(spec.type, profile)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(4.dp).size(48.dp)
                .graphicsLayer { translationY = resizeOvershoot * .12f }
                .pointerInput(spec.instanceId, spec.type) {
                        detectDragGestures(
                            onDragStart = {
                                resizing = true
                                resizeDistance = 0f
                                resizeOvershoot = 0f
                                resizeStartSteps = latestHeightSteps
                            },
                            onDragCancel = {
                                resizing = false
                                resizeDistance = 0f
                                resizeOvershoot = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeDistance += amount.y
                                val stepPx = 8.dp.toPx()
                                val requested = resizeStartSteps + (resizeDistance / stepPx).roundToInt()
                                val range = DashboardLayouts.heightRange(spec.type)
                                val bounded = requested.coerceIn(range)
                                resizeOvershoot = if (requested == bounded) 0f else {
                                    (resizeOvershoot + amount.y * .35f).coerceIn(-48.dp.toPx(), 48.dp.toPx())
                                }
                                if (bounded != latestHeightSteps) latestResizeHeight(bounded)
                            },
                            onDragEnd = {
                                resizing = false
                                resizeDistance = 0f
                                resizeOvershoot = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }
        ) {
            Surface(
                onClick = {
                    if (allowedWidths.size > 1) {
                        val index = allowedWidths.indexOf(spec.size.columns).coerceAtLeast(0)
                        onResizeWidth(allowedWidths[(index + 1) % allowedWidths.size])
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                shape = CircleShape,
                color = colors.surfaceRaised.copy(alpha = .96f),
                contentColor = colors.accent,
                shadowElevation = 6.dp
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ZoomOutMap, "拖动调整高度，点按切换宽度", modifier = Modifier.size(21.dp))
                }
            }
        }
        if (resizing) {
            Surface(
                color = colors.text.copy(alpha = .92f),
                contentColor = colors.background,
                shape = CircleShape,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
            ) {
                Text(
                    "${latestHeightSteps * DashboardLayouts.HEIGHT_STEP_DP} dp",
                    style = AppleTypography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardAddSheet(
    profile: DashboardProfile,
    layout: DashboardLayout,
    games: List<Game>,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onAdd: (DashboardWidgetType, Long?) -> Unit
) {
    val usedGames = HashSet<Long>().apply {
        layout.widgets.forEach { spec ->
            if (spec.type == DashboardWidgetType.GAME_SPOTLIGHT) {
                if (spec.spotlightSelectionMode == SpotlightSelectionMode.MANUAL) {
                    addAll(spec.spotlightGameIds)
                }
                spec.gameId?.let(::add)
            }
        }
    }
    KireiActionSheet("添加首页组件", colors, onDismiss, expanded = true) {
        if (layout.widgets.size >= DashboardLayouts.MAX_WIDGETS) {
            Text("最多可添加 ${DashboardLayouts.MAX_WIDGETS} 个组件。", color = colors.secondary, modifier = Modifier.padding(24.dp))
            return@KireiActionSheet
        }
        DashboardTrendEditorPolicy.availableTypes(layout).filter { it != DashboardWidgetType.GAME_SPOTLIGHT }.forEach { type ->
            SheetActionRow(dashboardWidgetIcon(type), dashboardWidgetLabel(type), dashboardWidgetDescription(type), colors) {
                onAdd(type, null)
            }
        }
        if (layout.widgets.none {
                it.type == DashboardWidgetType.GAME_SPOTLIGHT &&
                    it.spotlightSelectionMode != SpotlightSelectionMode.MANUAL
            }
        ) {
            SheetActionRow(
                Icons.Rounded.AutoFixHigh,
                "动态头图",
                "默认显示最近打开，可配置收藏、全部游戏与随机方式",
                colors,
            ) {
                onAdd(DashboardWidgetType.GAME_SPOTLIGHT, null)
            }
        }
        Text("游戏展示", color = colors.secondary, style = AppleTypography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        val availableGames = games.filterNot { it.id in usedGames }
        if (availableGames.isEmpty()) {
            Text("没有可添加的游戏", color = colors.secondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
        } else {
            availableGames.forEach { game ->
                DashboardGamePickerRow(game, colors) { onAdd(DashboardWidgetType.GAME_SPOTLIGHT, game.id) }
            }
        }
    }
}

@Composable
private fun SpotlightConfigSheet(
    spec: DashboardWidgetSpec,
    games: List<Game>,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onSettings: (HomeContentSettings) -> Unit,
) {
    KireiActionSheet(
        "设置头图",
        colors,
        onDismiss,
        expanded = true,
        scrollableContent = false,
    ) {
        HomeContentSettingsPanel(
            settings = spec.toHomeContentSettings(),
            games = games,
            colors = colors,
            onSettingsChange = onSettings,
        )
    }
}

@Composable
private fun DashboardGamePickerRow(game: Game, colors: KireiColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameCoverArtwork(game, Modifier.size(44.dp, 58.dp), ContentScale.Crop, 10.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(game.title.orEmpty(), color = colors.text, style = AppleTypography.bodyLargeEmphasis, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(game.engine?.displayName.orEmpty(), color = colors.secondary, style = AppleTypography.bodySmall)
        }
        Icon(Icons.Rounded.Add, "添加", tint = colors.accent)
    }
}

private fun dashboardWidgetLabel(type: DashboardWidgetType): String = when (type) {
    DashboardWidgetType.GAME_SPOTLIGHT -> "游戏展示"
    DashboardWidgetType.LIBRARY_OVERVIEW -> "游戏概览"
    DashboardWidgetType.RECENT_GAMES -> "最近打开"
    DashboardWidgetType.FAVORITES -> "收藏概览"
    DashboardWidgetType.LIBRARY_SHORTCUT -> "游戏库入口"
    DashboardWidgetType.PLAY_TREND -> "游玩趋势"
}

private fun dashboardWidgetDescription(type: DashboardWidgetType): String = when (type) {
    DashboardWidgetType.GAME_SPOTLIGHT -> "展示封面与直接启动按钮"
    DashboardWidgetType.LIBRARY_OVERVIEW -> "累计时长、游戏数与收藏数"
    DashboardWidgetType.RECENT_GAMES -> "最近打开的游戏"
    DashboardWidgetType.FAVORITES -> "收藏数量和封面预览"
    DashboardWidgetType.LIBRARY_SHORTCUT -> "快速进入游戏库"
    DashboardWidgetType.PLAY_TREND -> "7 日或 30 日游玩折线图"
}

private fun dashboardStyleLabel(style: String): String = when (style) {
    DashboardStyles.SPOTLIGHT_IMMERSIVE -> "沉浸头图"
    DashboardStyles.SPOTLIGHT_POSTER -> "海报详情"
    DashboardStyles.SPOTLIGHT_HORIZONTAL -> "横向紧凑"
    DashboardStyles.OVERVIEW_MINIMAL -> "极简数字"
    DashboardStyles.OVERVIEW_SPLIT -> "分栏统计"
    DashboardStyles.OVERVIEW_RING -> "环形统计"
    DashboardStyles.OVERVIEW_TILES -> "三格数据"
    DashboardStyles.OVERVIEW_FOCUS -> "大数字聚焦"
    DashboardStyles.RECENT_STRIP -> "主题卡条"
    DashboardStyles.RECENT_GLASS -> "玻璃列表"
    DashboardStyles.RECENT_COVERS -> "封面横滑"
    DashboardStyles.FAVORITES_STACK -> "叠放封面"
    DashboardStyles.FAVORITES_MOSAIC -> "封面拼图"
    DashboardStyles.FAVORITES_FEATURED -> "主封面"
    DashboardStyles.FAVORITES_COUNT -> "纯数字紧凑"
    DashboardStyles.LIBRARY_ICON -> "图标入口"
    DashboardStyles.LIBRARY_PROGRESS -> "进度摘要"
    DashboardStyles.TREND_7_DAY -> "7 日折线"
    DashboardStyles.TREND_30_DAY -> "30 日面积折线"
    DashboardStyles.TREND_LAUNCH_7_DAY -> "7 日启动款数"
    DashboardStyles.TREND_LAUNCH_30_DAY -> "30 日启动款数"
    else -> "默认样式"
}

private fun dashboardWidgetIcon(type: DashboardWidgetType): androidx.compose.ui.graphics.vector.ImageVector = when (type) {
    DashboardWidgetType.GAME_SPOTLIGHT -> Icons.Rounded.Gamepad
    DashboardWidgetType.LIBRARY_OVERVIEW -> Icons.Rounded.Timer
    DashboardWidgetType.RECENT_GAMES -> Icons.Rounded.Refresh
    DashboardWidgetType.FAVORITES -> Icons.Rounded.Favorite
    DashboardWidgetType.LIBRARY_SHORTCUT -> Icons.Rounded.GridView
    DashboardWidgetType.PLAY_TREND -> Icons.Rounded.ShowChart
}

internal fun resolveLibraryGridBottomContentPaddingDp(
    windowHeightDp: Float,
    dockRect: HomeRect,
    safeBottomDp: Float,
    clearanceDp: Float = AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP,
): Float {
    val renderedDock = resolveDockRenderRect(dockRect)
    val dockClearance = resolveBottomContentPaddingDp(
        windowHeightDp = windowHeightDp,
        dockRect = renderedDock,
        clearanceDp = clearanceDp,
    )
    return maxOf(
        dockClearance,
        safeBottomDp.coerceAtLeast(0f) + clearanceDp,
    )
}

/**
 * Geometry and metadata contracts owned by the library card. Keeping these values pure makes
 * the compact-landscape boundary testable without rendering a Compose tree.
 */
internal object LibraryCardLayoutPolicy {
    const val PORTRAIT_COVER_ASPECT_RATIO = .72f
    const val SHORT_LANDSCAPE_COVER_ASPECT_RATIO = 16f / 9f
    const val SHORT_LANDSCAPE_MAX_HEIGHT_FRACTION = .60f
    const val MIN_HEADER_GAP_DP = 8f
    const val MIN_TITLE_GAP_DP = 6f
    const val MIN_DOCK_CLEARANCE_DP = 8f
    const val ENGINE_LABELS_PER_CARD = 1

    fun isShortLandscape(windowWidthDp: Float, windowHeightDp: Float): Boolean =
        windowWidthDp > windowHeightDp &&
            windowHeightDp > 0f &&
            windowHeightDp / windowWidthDp <= SHORT_LANDSCAPE_MAX_HEIGHT_FRACTION

    fun coverAspectRatio(shortLandscape: Boolean): Float =
        if (shortLandscape) SHORT_LANDSCAPE_COVER_ASPECT_RATIO else PORTRAIT_COVER_ASPECT_RATIO

    fun titleMaxLines(shortLandscape: Boolean): Int = if (shortLandscape) 1 else 2

    fun titleGapDp(shortLandscape: Boolean): Float =
        maxOf(MIN_TITLE_GAP_DP, if (shortLandscape) 6f else 9f)

    fun resolvedHeaderBottomDp(
        headerTopDp: Float,
        toolbarHeightDp: Float,
        measuredTitleBottomDp: Float? = null,
        measuredActionsBottomDp: Float? = null,
    ): Float {
        var bottom = (headerTopDp.coerceAtLeast(0f) + toolbarHeightDp.coerceAtLeast(0f))
            .coerceAtLeast(0f)
        measuredTitleBottomDp?.takeIf { it >= 0f }?.let { bottom = maxOf(bottom, it) }
        measuredActionsBottomDp?.takeIf { it >= 0f }?.let { bottom = maxOf(bottom, it) }
        return bottom
    }

    /**
     * Resolves grid padding in the grid's local coordinate space. An expanded toolbar uses its
     * measured bottom; once it is fully collapsed, only the minimum clearance remains.
     */
    fun gridTopPaddingDp(
        gridViewportTopDp: Float,
        headerBottomDp: Float,
        toolbarCollapsed: Boolean,
        gapDp: Float = MIN_HEADER_GAP_DP,
    ): Float {
        val gap = gapDp.coerceAtLeast(MIN_HEADER_GAP_DP)
        val viewportTop = gridViewportTopDp.coerceAtLeast(0f)
        val visibleHeaderBottom = if (toolbarCollapsed) viewportTop else {
            maxOf(viewportTop, headerBottomDp.coerceAtLeast(0f))
        }
        return maxOf(gap, visibleHeaderBottom - viewportTop + gap)
    }

    fun gridFadeStartDp(
        gridViewportTopDp: Float,
        headerBottomDp: Float,
        toolbarCollapsed: Boolean,
    ): Float = if (toolbarCollapsed) {
        0f
    } else {
        (headerBottomDp - gridViewportTopDp).coerceAtLeast(0f)
    }

    fun rowCount(itemCount: Int, columnCount: Int): Int {
        require(itemCount >= 0) { "item count must be non-negative" }
        require(columnCount > 0) { "column count must be positive" }
        return if (itemCount == 0) 0 else (itemCount + columnCount - 1) / columnCount
    }
}

internal fun libraryCardEngineLabel(engine: EngineType?): String =
    engine?.displayName ?: "游戏"

internal enum class CoverTaskBadgePlacement {
    INNER_BUTTON_CLIP,
    OUTER_BOX_SIBLING,
}

internal data class CoverTaskButtonLayout(
    val buttonSizeDp: Float,
    val badgePlacement: CoverTaskBadgePlacement,
    val badgeClippedByButton: Boolean,
    val badgeConsumesClicks: Boolean,
    val badgeHasIndependentSemantics: Boolean,
    val badgeVisible: Boolean,
    val badgeLabel: String,
    val badgeAccessibilityCount: Int,
    val badgeHeightDp: Float,
    val badgeMinWidthDp: Float,
    val badgeHorizontalPaddingDp: Float,
)

internal enum class CoverSourceAccessibilityRole {
    BUTTON,
    RADIO_BUTTON,
}

internal data class CoverSourceAccessibility(
    val role: CoverSourceAccessibilityRole,
    val selected: Boolean,
    val stateDescription: String?,
    val enabled: Boolean,
    val alpha: Float,
    val tokenVisible: Boolean,
)

/**
 * The small integration seam owned by the shell. It keeps policy selection in the same code
 * path that the composables use, while allowing JVM tests to exercise Alpha 11 behavior
 * without reading source files or depending on an Android renderer.
 */
internal object Alpha11KireiBoxAppIntegration {
    fun headerFraction(
        scrollOffsetPx: Float,
        density: Float,
        orientation: LibraryHeaderOrientation,
        searchActive: Boolean = false,
        editMode: Boolean = false,
        taskCenterOpen: Boolean = false,
    ): Float = LibraryHeaderTransitionPolicy.resolvedFraction(
        scrollOffsetPx = scrollOffsetPx,
        density = density,
        orientation = orientation,
        searchActive = searchActive,
        editMode = editMode,
        taskCenterOpen = taskCenterOpen,
        idle = false,
    )

    fun headerIdleTarget(fraction: Float): Float =
        LibraryHeaderTransitionPolicy.idleSnapTarget(fraction)

    fun sourcePresentation(
        source: CoverSourceMode,
        selectedSource: CoverSourceMode?,
    ): CoverSourcePresentation = CoverSourcePresentationPolicy.presentation(source, selectedSource)

    fun tokenVisible(source: CoverSourceMode): Boolean =
        CoverSourcePresentationPolicy.tokenVisible(source)

    fun badge(pendingCount: Int): CoverTaskBadgePresentation =
        CoverTaskBadgePolicy.presentation(pendingCount)

    fun taskButtonLayout(pendingCount: Int): CoverTaskButtonLayout {
        val badge = CoverTaskBadgePolicy.presentation(pendingCount)
        return CoverTaskButtonLayout(
            buttonSizeDp = 48f,
            badgePlacement = CoverTaskBadgePlacement.OUTER_BOX_SIBLING,
            badgeClippedByButton = false,
            badgeConsumesClicks = false,
            badgeHasIndependentSemantics = badge.visible,
            badgeVisible = badge.visible,
            badgeLabel = badge.label,
            badgeAccessibilityCount = badge.accessibilityCount,
            badgeHeightDp = badge.heightDp,
            badgeMinWidthDp = badge.minWidthDp,
            badgeHorizontalPaddingDp = badge.horizontalPaddingDp,
        )
    }

    fun sourceAccessibility(
        source: CoverSourceMode,
        selectedSource: CoverSourceMode?,
    ): CoverSourceAccessibility {
        val presentation = CoverSourcePresentationPolicy.presentation(source, selectedSource)
        return CoverSourceAccessibility(
            role = CoverSourceAccessibilityRole.RADIO_BUTTON,
            selected = presentation.isSelected,
            stateDescription = if (presentation.isSelected) "已选择" else "未选择",
            enabled = presentation.enabled,
            alpha = presentation.alpha,
            tokenVisible = presentation.tokenVisible,
        )
    }

    fun cacheBudgetKib(role: CoverImageRole): Int = when (role) {
        CoverImageRole.THUMBNAIL -> CoverBitmapCaches.ORDINARY_KIB
        CoverImageRole.LIBRARY_CARD -> CoverBitmapCaches.LIBRARY_CARD_KIB
        CoverImageRole.HERO -> CoverBitmapCaches.HERO_KIB
        CoverImageRole.IMMERSIVE_BACKDROP -> CoverBitmapCaches.BACKDROP_KIB
    }

    fun dockHaze(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): DockHazeConfiguration = DockHazePolicy.configuration(
        isDark = isDark,
        reducedTransparency = reducedTransparency,
        hazeAvailable = hazeAvailable,
        apiSupportsHaze = apiSupportsHaze,
    )
}

@Composable
private fun LibraryGameGrid(
    gridColumns: GridCells,
    gridState: LazyGridState,
    visible: List<Game>,
    colors: KireiColors,
    polishedMotion: Boolean,
    editMode: Boolean,
    removingGameId: Long?,
    reducedMotion: Boolean,
    compactCards: Boolean,
    bottomContentPadding: Dp,
    adaptiveMetrics: AdaptiveUiMetrics,
    viewModel: KireiViewModel,
    onRequestRemove: (Game) -> Unit,
    onEditModeChange: (Boolean) -> Unit,
    contentTopDp: Float,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        gridColumns,
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(
            top = contentTopDp.dp,
            bottom = maxOf(
                bottomContentPadding.value,
                LibraryCardLayoutPolicy.MIN_DOCK_CLEARANCE_DP,
            ).dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(adaptiveMetrics.gridSpacingDp.dp),
        verticalArrangement = Arrangement.spacedBy(adaptiveMetrics.gridSpacingDp.dp),
    ) {
        items(visible, key = { it.id }) { game ->
            GameCard(
                game = game,
                colors = colors,
                modifier = if (polishedMotion) {
                    Modifier.animateItem(
                        fadeInSpec = tween(180),
                        placementSpec = spring(
                            dampingRatio = .82f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        fadeOutSpec = tween(160),
                    )
                } else {
                    Modifier.animateItem(
                        fadeInSpec = tween(100),
                        placementSpec = tween(1),
                        fadeOutSpec = tween(100),
                    )
                },
                editMode = editMode,
                removing = removingGameId == game.id,
                reducedMotion = reducedMotion,
                compact = compactCards,
                onOpen = { viewModel.selectedGame(game) },
                onLongClick = { onEditModeChange(true) },
                onRemove = { onRequestRemove(game) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LibraryPage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    removingGameId: Long?,
    layoutMode: KireiLayoutMode,
    safeDrawingPadding: PaddingValues,
    bottomContentPadding: Dp,
    statusBarBottomDp: Dp,
    displayCutout: HeaderRectDp?,
    adaptiveMetrics: AdaptiveUiMetrics,
    windowClass: ResponsiveWindowClass,
    onRequestRemove: (Game) -> Unit
) {
    val polishedMotion = LocalResolvedMotionMode.current == MotionMode.POLISHED
    val reducedMotion = !polishedMotion
    val narrowLandscape = windowClass == ResponsiveWindowClass.PHONE_LANDSCAPE
    val libraryHeaderOrientation = when (windowClass) {
        ResponsiveWindowClass.PHONE_LANDSCAPE -> LibraryHeaderOrientation.LANDSCAPE_PHONE
        ResponsiveWindowClass.PAD_LANDSCAPE -> LibraryHeaderOrientation.LANDSCAPE_PAD
        else -> LibraryHeaderOrientation.PORTRAIT
    }
    val collapsibleLandscape = libraryHeaderOrientation != LibraryHeaderOrientation.PORTRAIT
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<EngineType?>(null) }
    var searchFocused by rememberSaveable { mutableStateOf(false) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var showAddGame by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val taskCenterListState = rememberLazyListState()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingLeft = safeDrawingPadding.calculateLeftPadding(layoutDirection)
    val safeDrawingRight = safeDrawingPadding.calculateRightPadding(layoutDirection)
    val safeDrawingTop = safeDrawingPadding.calculateTopPadding()
    val safeDrawingBottom = safeDrawingPadding.calculateBottomPadding()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val revealHeightPx = with(density) { 72.dp.toPx() }
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val searchReveal = remember { SearchRevealController(scope) }
    LaunchedEffect(revealHeightPx) { searchReveal.configure(revealHeightPx) }
    LaunchedEffect(layoutMode, windowClass) {
        filterMenuExpanded = false
    }
    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
            requestSearchFocus = false
        }
    }
    LaunchedEffect(searchFocused, searchReveal.offsetPx > 0f) {
        if (searchFocused && searchReveal.offsetPx > 0f) searchFocusRequester.requestFocus()
    }
    val searchActive = query.isNotBlank() || searchFocused ||
        searchReveal.phase != SearchRevealPhase.HIDDEN || searchReveal.offsetPx > 0f
    var measuredLibraryTitleBounds by remember { mutableStateOf<HeaderRectDp?>(null) }
    var measuredLibraryActionsBounds by remember { mutableStateOf<HeaderRectDp?>(null) }
    val libraryBackAction = KireiBackPolicy.resolveLibraryBack(
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
            KireiLibraryBackAction.CLOSE_SEARCH -> {
                focusManager.clearFocus(force = true)
                query = ""
                searchFocused = false
                requestSearchFocus = false
                searchReveal.hide()
            }
            KireiLibraryBackAction.EXIT_EDIT_MODE -> editMode = false
            KireiLibraryBackAction.PAGE -> Unit
        }
    }
    LaunchedEffect(query, searchFocused) {
        if (query.isNotBlank() || searchFocused) searchReveal.show()
    }
    val nested = remember(gridState, query, searchFocused, reducedMotion, decay) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                val atTop = !gridState.canScrollBackward
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    return androidx.compose.ui.geometry.Offset(
                        0f,
                        searchReveal.dragBy(available.y, atListStart = atTop),
                    )
                }
                if (source == NestedScrollSource.UserInput && available.y < 0f &&
                    searchReveal.offsetPx > 0f
                ) {
                    return androidx.compose.ui.geometry.Offset(
                        0f,
                        searchReveal.dragBy(available.y, atListStart = atTop),
                    )
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y < 0f && searchReveal.offsetPx > 0f) {
                    val projected = kotlin.math.abs(decay.calculateTargetValue(0f, available.y))
                    val split = SearchRevealMath.splitUpwardFling(
                        available.y,
                        projected,
                        searchReveal.offsetPx,
                    )
                    searchReveal.collapseForFling(available.y, reducedMotion)
                    return Velocity(0f, split.consumedVelocityY)
                }
                if (!gridState.canScrollBackward) {
                    if (available.y > 0f && searchReveal.offsetPx < searchReveal.maxOffsetPx) {
                        searchReveal.settle(velocityY = available.y, keepShown = true)
                        return available
                    }
                    if (searchReveal.offsetPx > 0f) {
                        searchReveal.settle(
                            velocityY = available.y,
                            keepShown = query.isNotBlank() || searchFocused,
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }
    val visible = remember(state.games, state.gameSearchIndex, filter, query) {
        state.games
            .asSequence()
            .filter { filter == null || it.engine == filter }
            .filter { GameSearchIndex.matches(state.gameSearchIndex[it.id], query) }
            .toList()
    }
    val taskButtonState = CoverTaskPresentationPolicy.buttonState(state.coverQueue.tasks)
    val shouldDismissCompletedRows = CoverTaskPresentationPolicy.shouldDismissCompletedRows(
        tasks = state.coverQueue.tasks,
        panelOpen = state.taskCenterOpen,
    )
    LaunchedEffect(shouldDismissCompletedRows) {
        if (shouldDismissCompletedRows) {
            delay(1_200)
            viewModel.dismissCoverTaskSessionRows()
        }
    }
    val libraryEngines = remember {
        listOf(
            EngineType.KIRIKIRI,
            EngineType.ONS,
            EngineType.TYRANO,
            EngineType.ARTEMIS,
            EngineType.ANDROID_APP,
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val libraryWindowWidth = maxWidth
    val libraryWindowHeight = maxHeight
    val compactLibraryCards = narrowLandscape || LibraryCardLayoutPolicy.isShortLandscape(
        windowWidthDp = libraryWindowWidth.value,
        windowHeightDp = libraryWindowHeight.value,
    )
    val contentLeft = adaptiveMetrics.contentFrame.x.dp
    val contentRight = (maxWidth.value - adaptiveMetrics.contentFrame.x - adaptiveMetrics.contentFrame.w).dp
    val fallbackLibraryAnchors = HeaderAnchorRegionsDp(
        leftTitle = HeaderRectDp(
            leftDp = contentLeft.value,
            topDp = safeDrawingTop.value,
            rightDp = (maxWidth.value * .48f).coerceAtLeast(contentLeft.value),
            bottomDp = safeDrawingTop.value + HeaderTransitionPolicy.HEADER_LANE_HEIGHT_DP,
        ),
        rightAction = HeaderRectDp(
            leftDp = (maxWidth.value * .55f).coerceAtMost(maxWidth.value),
            topDp = safeDrawingTop.value,
            rightDp = maxWidth.value - contentRight.value,
            bottomDp = safeDrawingTop.value + HeaderTransitionPolicy.HEADER_LANE_HEIGHT_DP,
        ),
    )
    val libraryHeaderTop = if (collapsibleLandscape) {
        HeaderTransitionPolicy.topDp(
            geometry = HeaderGeometryDp(
                statusBarBottomDp = statusBarBottomDp.value,
                safeDrawingTopDp = safeDrawingTop.value,
                cutout = displayCutout,
            ),
            anchors = HeaderAnchorRegionsDp(
                leftTitle = measuredLibraryTitleBounds ?: fallbackLibraryAnchors.leftTitle,
                rightAction = measuredLibraryActionsBounds ?: fallbackLibraryAnchors.rightAction,
            ),
        ).dp
    } else {
        HeaderTransitionPolicy.librarySearchTopDp(safeDrawingTop.value).dp
    }
    val gridColumns = GridCells.Fixed(adaptiveMetrics.libraryColumns)
    val resolvedLibraryHeaderBottomDp = LibraryCardLayoutPolicy.resolvedHeaderBottomDp(
        headerTopDp = libraryHeaderTop.value,
        toolbarHeightDp = LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP,
        measuredTitleBottomDp = measuredLibraryTitleBounds?.bottomDp,
        measuredActionsBottomDp = measuredLibraryActionsBounds?.bottomDp,
    )
    val compactGridTopPaddingDp = LibraryCardLayoutPolicy.gridTopPaddingDp(
        gridViewportTopDp = libraryHeaderTop.value,
        headerBottomDp = resolvedLibraryHeaderBottomDp,
        toolbarCollapsed = false,
    )
    val searchRevealHeightDp = with(density) { searchReveal.offsetPx.toDp() }
    val toolbarContentAlpha by remember(gridState, density.density) {
        derivedStateOf {
            val scrollOffsetPx = if (gridState.firstVisibleItemIndex > 0) {
                LibraryHeaderTransitionPolicy.HEADER_SCROLL_RANGE_DP * density.density
            } else {
                gridState.firstVisibleItemScrollOffset.toFloat()
            }
            (1f - LibraryHeaderTransitionPolicy.fractionForScroll(
                scrollOffsetPx = scrollOffsetPx,
                density = density.density,
            )).coerceIn(0f, 1f)
        }
    }
    val toolbarInteractive = toolbarContentAlpha > .05f
    Column(
        Modifier.align(Alignment.TopCenter).fillMaxSize()
            .absolutePadding(left = contentLeft, right = contentRight)
            .padding(top = libraryHeaderTop)
            .nestedScroll(nested)
    ) {
        if (collapsibleLandscape) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                LibraryGameGrid(
                    gridColumns = gridColumns,
                    gridState = gridState,
                    visible = visible,
                    colors = colors,
                    polishedMotion = polishedMotion,
                    editMode = editMode,
                    removingGameId = removingGameId,
                    reducedMotion = reducedMotion,
                    compactCards = compactLibraryCards,
                    bottomContentPadding = bottomContentPadding,
                    adaptiveMetrics = adaptiveMetrics,
                    viewModel = viewModel,
                    onRequestRemove = onRequestRemove,
                    onEditModeChange = { editMode = it },
                    contentTopDp = compactGridTopPaddingDp + searchRevealHeightDp.value,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                ) {
                Row(
                    Modifier.fillMaxWidth()
                        .height(LibraryHeaderTransitionPolicy.TOOLBAR_HEIGHT_DP.dp)
                        .graphicsLayer { alpha = toolbarContentAlpha }
                        .then(
                            if (toolbarInteractive) {
                                Modifier
                            } else {
                                Modifier.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial).changes
                                                .forEach { it.consume() }
                                        }
                                    }
                                }.clearAndSetSemantics { }
                            },
                        )
                        .onGloballyPositioned { coordinates ->
                        val bounds = headerRectDp(coordinates, density)
                        val midpoint = bounds.leftDp + (bounds.rightDp - bounds.leftDp) * .5f
                        measuredLibraryTitleBounds = bounds.copy(rightDp = midpoint)
                        measuredLibraryActionsBounds = bounds.copy(leftDp = midpoint)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("游戏库", color = colors.text, style = AppleTypography.headlineMedium, maxLines = 1)
                    Text(
                        "${visible.size} 部",
                        color = colors.secondary,
                        style = AppleTypography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    if (editMode) {
                        TextButton(onClick = { editMode = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.Done, null, tint = colors.accent)
                            Text("完成", color = colors.accent, modifier = Modifier.padding(start = 4.dp))
                        }
                    } else {
                        Text(
                            filter?.displayName ?: "全部",
                            color = colors.secondary,
                            style = AppleTypography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 96.dp).padding(horizontal = 6.dp),
                        )
                        Box {
                            GlassIconButton(
                                Icons.Rounded.Tune,
                                "筛选游戏",
                                colors,
                                haze,
                                onClick = { filterMenuExpanded = !filterMenuExpanded },
                                opaqueFill = true,
                            )
                            DropdownMenu(
                                expanded = filterMenuExpanded,
                                onDismissRequest = { filterMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (filter == null) "✓ 全部" else "全部") },
                                    onClick = {
                                        filter = null
                                        editMode = false
                                        filterMenuExpanded = false
                                    },
                                )
                                libraryEngines.forEach { engine ->
                                    DropdownMenuItem(
                                        text = { Text(if (filter == engine) "✓ ${engine.displayName}" else engine.displayName) },
                                        onClick = {
                                            filter = engine
                                            editMode = false
                                            filterMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        GlassIconButton(
                            Icons.Rounded.Search,
                            "搜索游戏",
                            colors,
                            haze,
                            onClick = {
                                filterMenuExpanded = false
                                if (state.taskCenterOpen) viewModel.closeCoverTaskCenter()
                                searchReveal.show()
                                requestSearchFocus = true
                            },
                            opaqueFill = true,
                        )
                        Spacer(Modifier.width(6.dp))
                        if (taskButtonState.visible) {
                            CoverTaskToolbarAnchor(
                                queue = state.coverQueue,
                                panelOpen = state.taskCenterOpen,
                                colors = colors,
                                haze = haze,
                                layoutMode = layoutMode,
                                windowWidth = libraryWindowWidth,
                                windowHeight = libraryWindowHeight,
                                safeTop = safeDrawingTop,
                                safeRight = contentRight,
                                safeBottom = safeDrawingBottom,
                                listState = taskCenterListState,
                                viewModel = viewModel,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        GlassIconButton(
                            Icons.Rounded.Add,
                            "添加游戏",
                            colors,
                            haze,
                            onClick = { showAddGame = true },
                            opaqueFill = true,
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth()
                        .height(searchRevealHeightDp)
                        .clipToBounds()
                        .graphicsLayer { alpha = searchReveal.fraction },
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 12.dp)
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { searchFocused = it.isFocused },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                focusManager.clearFocus(force = true)
                                query = ""
                                searchFocused = false
                                requestSearchFocus = false
                                searchReveal.hide()
                            }) { Icon(Icons.Rounded.Close, "关闭搜索") }
                        },
                        placeholder = { Text("搜索游戏") },
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
                }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 18.dp)) {
                    Text("游戏库", color = colors.text, style = AppleTypography.displayLarge)
                    Text("${visible.size} 部游戏", color = colors.secondary)
                }
                if (editMode) {
                    TextButton(onClick = { editMode = false }) {
                        Icon(Icons.Rounded.Done, null, tint = colors.accent)
                        Text("完成", color = colors.accent, modifier = Modifier.padding(start = 4.dp))
                    }
                } else {
                    GlassIconButton(
                        Icons.Rounded.Search,
                        "搜索游戏",
                        colors,
                        haze,
                        onClick = {
                            if (state.taskCenterOpen) viewModel.closeCoverTaskCenter()
                            searchReveal.show()
                            requestSearchFocus = true
                        },
                        opaqueFill = true,
                    )
                    Spacer(Modifier.width(6.dp))
                    if (taskButtonState.visible) {
                        CoverTaskToolbarAnchor(
                            queue = state.coverQueue,
                            panelOpen = state.taskCenterOpen,
                            colors = colors,
                            haze = haze,
                            layoutMode = layoutMode,
                            windowWidth = libraryWindowWidth,
                            windowHeight = libraryWindowHeight,
                            safeTop = safeDrawingTop,
                            safeRight = safeDrawingRight,
                            safeBottom = safeDrawingBottom,
                            listState = taskCenterListState,
                            viewModel = viewModel,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    GlassIconButton(
                        Icons.Rounded.Add,
                        "添加游戏",
                        colors,
                        haze,
                        onClick = { showAddGame = true },
                        opaqueFill = true,
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().height(with(density) { searchReveal.offsetPx.toDp() })
                    .clipToBounds().graphicsLayer { alpha = searchReveal.fraction }
            ) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth().padding(top = 12.dp)
                        .focusRequester(searchFocusRequester)
                        .onFocusChanged { searchFocused = it.isFocused },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            focusManager.clearFocus(force = true)
                            query = ""
                            searchFocused = false
                            requestSearchFocus = false
                            searchReveal.hide()
                        }) { Icon(Icons.Rounded.Close, "关闭搜索") }
                    },
                    placeholder = { Text("搜索游戏") },
                    shape = RoundedCornerShape(16.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                FilterChip("全部", filter == null, colors) { filter = null; editMode = false }
                libraryEngines.forEach { engine ->
                    FilterChip(engine.displayName, filter == engine, colors) { filter = engine; editMode = false }
                }
            }
            LibraryGameGrid(
                gridColumns = gridColumns,
                gridState = gridState,
                visible = visible,
                colors = colors,
                polishedMotion = polishedMotion,
                editMode = editMode,
                removingGameId = removingGameId,
                reducedMotion = reducedMotion,
                compactCards = compactLibraryCards,
                bottomContentPadding = bottomContentPadding,
                adaptiveMetrics = adaptiveMetrics,
                viewModel = viewModel,
                onRequestRemove = onRequestRemove,
                onEditModeChange = { editMode = it },
                contentTopDp = LibraryHeaderTransitionPolicy.topGapDp(adaptiveMetrics.scale),
                modifier = Modifier.weight(1f),
            )
        }
    }
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
        )
    }
    if (showAddGame) {
        KireiActionSheet("添加游戏", colors, { showAddGame = false }) {
            SheetActionRow(
                Icons.Rounded.Folder,
                "选择游戏目录",
                "扫描目录并识别 KRKR、ONS、Tyrano 或 Artemis",
                colors,
            ) {
                showAddGame = false
                actions.chooseDirectory()
            }
            SheetActionRow(
                Icons.Rounded.Description,
                "选择启动文件",
                "直接选择 XP3、脚本、HTML 或引擎入口文件",
                colors,
            ) {
                showAddGame = false
                actions.chooseLaunchFile()
            }
            SheetActionRow(
                Icons.Rounded.Gamepad,
                "选择 Android 应用",
                "显示系统中可启动的软件；不需要存储权限",
                colors,
            ) {
                showAddGame = false
                actions.chooseAndroidApp()
            }
        }
    }
    }
}

@Composable
private fun CoverTaskToolbarAnchor(
    queue: CoverQueueUiState,
    panelOpen: Boolean,
    colors: KireiColors,
    haze: HazeState?,
    layoutMode: KireiLayoutMode,
    windowWidth: Dp,
    windowHeight: Dp,
    safeTop: Dp,
    safeRight: Dp,
    safeBottom: Dp,
    listState: LazyListState,
    viewModel: KireiViewModel,
) {
    val metrics = CoverTaskPresentationPolicy.anchoredContainerMetrics(
        windowWidthDp = windowWidth.value,
        windowHeightDp = windowHeight.value,
        safeTopDp = safeTop.value,
        safeRightDp = safeRight.value,
        safeBottomDp = safeBottom.value,
    )
    Box {
        CoverTaskButton(
            queue = queue,
            colors = colors,
            haze = haze,
            onClick = {
                if (panelOpen) viewModel.closeCoverTaskCenter() else viewModel.openCoverTaskCenter()
            },
        )
        if (panelOpen && CoverTaskPresentationPolicy.containerFor(layoutMode) == CoverTaskContainer.ANCHORED_POPOVER) {
            CoverTaskPopup(
                queue = queue,
                listState = listState,
                colors = colors,
                metrics = metrics,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun CoverTaskButton(
    queue: CoverQueueUiState,
    colors: KireiColors,
    haze: HazeState?,
    onClick: () -> Unit,
) {
    val state = CoverTaskPresentationPolicy.buttonState(queue.tasks)
    val layout = Alpha11KireiBoxAppIntegration.taskButtonLayout(state.pendingCount)
    val progressColor = if (state.hasFailure) colors.danger else colors.accent
    Box(Modifier.size(layout.buttonSizeDp.dp)) {
        GlassSurface(
            colors = colors,
            haze = haze,
            modifier = Modifier.size(layout.buttonSizeDp.dp).semantics(mergeDescendants = true) {
                contentDescription = state.contentDescription
            },
            shape = CircleShape,
            role = KireiGlassRole.ICON,
            onClick = onClick,
            opaqueFill = true,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    val ring = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = colors.outline.copy(alpha = .72f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = ring,
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * state.aggregateProgress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = ring,
                    )
                }
                Icon(
                    imageVector = if (state.showCompletionCheck) Icons.Rounded.Check else Icons.Rounded.CollectionsBookmark,
                    contentDescription = null,
                    tint = when {
                        state.hasFailure -> colors.danger
                        state.showCompletionCheck -> colors.success
                        else -> colors.text
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (layout.badgeVisible) {
            val badgeShape = RoundedCornerShape(percent = 50)
            Box(
                Modifier.align(Alignment.TopEnd)
                    .height(layout.badgeHeightDp.dp)
                    .sizeIn(minWidth = layout.badgeMinWidthDp.dp)
                    .background(if (state.hasFailure) colors.danger else colors.accent, badgeShape)
                    .border(1.dp, colors.surface, badgeShape)
                    .padding(horizontal = layout.badgeHorizontalPaddingDp.dp)
                    .semantics {
                        contentDescription = "待处理 ${layout.badgeAccessibilityCount}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    layout.badgeLabel,
                    color = Color.White,
                    style = AppleTypography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CoverTaskPopup(
    queue: CoverQueueUiState,
    listState: LazyListState,
    colors: KireiColors,
    metrics: CoverTaskPresentationPolicy.AnchoredContainerMetrics,
    viewModel: KireiViewModel,
) {
    val offsetY = with(LocalDensity.current) { 56.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = viewModel::closeCoverTaskCenter,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .width(metrics.widthDp.dp)
                .heightIn(max = metrics.maxHeightDp.dp),
            shape = RoundedCornerShape(22.dp),
            color = colors.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            CoverTaskCenterContent(
                queue = queue,
                listState = listState,
                colors = colors,
                listMaxHeight = (metrics.maxHeightDp.dp - 68.dp).coerceAtLeast(0.dp),
                onClose = viewModel::closeCoverTaskCenter,
                onPause = viewModel::pauseCoverQueue,
                onResume = viewModel::resumeCoverQueue,
                onReview = viewModel::openCoverReview,
                onRetry = viewModel::retryCoverTask,
                onCancel = viewModel::cancelCoverTask,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CoverTaskBottomSheet(
    queue: CoverQueueUiState,
    listState: LazyListState,
    colors: KireiColors,
    onClose: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReview: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surface) {
        CoverTaskCenterContent(
            queue = queue,
            listState = listState,
            colors = colors,
            listMaxHeight = 480.dp,
            onClose = onClose,
            onPause = onPause,
            onResume = onResume,
            onReview = onReview,
            onRetry = onRetry,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun CoverTaskCenterContent(
    queue: CoverQueueUiState,
    listState: LazyListState,
    colors: KireiColors,
    listMaxHeight: Dp,
    onClose: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReview: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("封面任务", color = colors.text, style = AppleTypography.headlineMedium, modifier = Modifier.weight(1f))
            Text("${(queue.aggregateProgress * 100).toInt()}%", color = colors.secondary)
            TextButton(onClick = if (queue.queueStatus == CoverQueueStatus.RUNNING) onPause else onResume) {
                Text(if (queue.queueStatus == CoverQueueStatus.RUNNING) "暂停" else "继续")
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭") }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMaxHeight), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverTaskPresentationPolicy.sections(queue.tasks).forEach { section ->
                item(key = section.title) { Text(section.title, color = colors.secondary, style = AppleTypography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
                items(section.tasks, key = { it.gameId }) { task ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(colors.surfaceRaised, RoundedCornerShape(16.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(CoverTaskPresentationPolicy.phaseLabel(task), color = colors.secondary, style = AppleTypography.labelMedium)
                        if (task.status == CoverTaskStatus.DOWNLOADING) {
                            Text(CoverTaskPresentationPolicy.downloadBytesLabel(task), color = colors.secondary, style = AppleTypography.labelMedium)
                        }
                    }
                    when (CoverTaskPresentationPolicy.rowActionFor(task.status)) {
                        CoverTaskRowAction.SELECT -> TextButton(onClick = { onReview(task.gameId) }) { Text("选择") }
                        CoverTaskRowAction.RETRY -> TextButton(onClick = { onRetry(task.gameId) }) { Text("重试") }
                        CoverTaskRowAction.UNDO -> TextButton(onClick = { onRetry(task.gameId) }) { Text("撤销") }
                        CoverTaskRowAction.CANCEL -> TextButton(onClick = { onCancel(task.gameId) }) { Text("取消", color = colors.danger) }
                        CoverTaskRowAction.NONE -> Unit
                    }
                }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GameCard(
    game: Game,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    editMode: Boolean,
    removing: Boolean,
    reducedMotion: Boolean,
    compact: Boolean,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit
) {
    val rotation = if (editMode && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "libraryEdit")
        val value by transition.animateFloat(
            initialValue = -.7f,
            targetValue = .7f,
            animationSpec = infiniteRepeatable(tween(125), RepeatMode.Reverse),
            label = "cardJiggle"
        )
        value
    } else 0f
    val removeProgress by animateFloatAsState(
        targetValue = if (removing) 0f else 1f,
        animationSpec = tween(if (reducedMotion) 120 else 180),
        label = "cardRemoval"
    )
    val engineLabel = libraryCardEngineLabel(game.engine)
    Box(modifier.fillMaxWidth()) {
        val cardAnimationModifier = if (editMode || removing) {
            Modifier.graphicsLayer {
                rotationZ = rotation
                alpha = removeProgress
                scaleX = .92f + .08f * removeProgress
                scaleY = .92f + .08f * removeProgress
            }
        } else {
            Modifier
        }
        Column(
            Modifier.fillMaxWidth().then(cardAnimationModifier).combinedClickable(
                onClick = { if (!editMode) onOpen() },
                onLongClick = onLongClick
            )
        ) {
            val coverCorner = if (compact) 16.dp else 20.dp
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(LibraryCardLayoutPolicy.coverAspectRatio(compact))
                    .clip(RoundedCornerShape(coverCorner))
            ) {
                GameCoverArtwork(
                    game = game,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = coverCorner,
                    role = CoverImageRole.LIBRARY_CARD,
                    showFallbackMetadata = false,
                )
                if (game.favorite) {
                    Surface(
                        color = colors.danger,
                        contentColor = Color.White,
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        modifier = Modifier.align(Alignment.TopStart)
                            .padding(if (compact) 6.dp else 10.dp)
                            .size(if (compact) 26.dp else 30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Favorite, "已收藏", Modifier.size(if (compact) 16.dp else 18.dp))
                        }
                    }
                }
                if (compact) {
                    Surface(
                        color = Color.Black.copy(alpha = .62f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    ) {
                        Text(
                            engineLabel,
                            style = AppleTypography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Text(
                game.title.orEmpty(),
                color = colors.text,
                maxLines = LibraryCardLayoutPolicy.titleMaxLines(compact),
                overflow = TextOverflow.Ellipsis,
                style = if (compact) AppleTypography.labelLarge else AppleTypography.bodyMedium,
                modifier = Modifier.padding(top = LibraryCardLayoutPolicy.titleGapDp(compact).dp),
            )
            if (!compact) {
                Text(engineLabel, color = colors.secondary, style = AppleTypography.labelLarge, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (editMode) {
            val touchSize = if (compact) 44.dp else 30.dp
            val badgeSize = if (compact) 26.dp else 30.dp
            Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(touchSize).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = colors.danger,
                    contentColor = Color.White,
                    shape = CircleShape,
                    shadowElevation = 5.dp,
                    modifier = Modifier.size(badgeSize),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Remove, "移出游戏库", Modifier.size(if (compact) 17.dp else 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    layoutMode: KireiLayoutMode,
    safeDrawingPadding: PaddingValues,
    bottomContentPadding: Dp,
    adaptiveMetrics: AdaptiveUiMetrics,
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val stateHolder = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = state.settingsPage,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(120)) togetherWith fadeOut(tween(100))
            } else {
                val direction = if (
                    KireiBackPolicy.settingsDepth(targetState) >= KireiBackPolicy.settingsDepth(initialState)
                ) 1 else -1
                (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { width -> direction * (width / 10) }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { width -> -direction * (width / 12) })
            }
        },
        label = "settingsNavigation",
    ) { page ->
        stateHolder.SaveableStateProvider(page.name) {
            if (page == KireiSettingsPage.ROOT) {
                SettingsRootPage(state, colors, viewModel, layoutMode, safeDrawingPadding, bottomContentPadding, adaptiveMetrics)
            } else {
                SettingsSubPage(
                    page, state, colors, haze, viewModel, actions, layoutMode,
                    safeDrawingPadding, bottomContentPadding, adaptiveMetrics,
                )
            }
        }
    }
}

@Composable
private fun SettingsRootPage(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    layoutMode: KireiLayoutMode,
    safeDrawingPadding: PaddingValues,
    bottomContentPadding: Dp,
    adaptiveMetrics: AdaptiveUiMetrics,
) {
    val maxContentWidth = adaptiveMetrics.settingsContentWidthDp.dp
    val categoryPages = remember {
        listOf(
            KireiSettingsPage.LIBRARY_HUB,
            KireiSettingsPage.KRKR,
            KireiSettingsPage.INTERFACE,
            KireiSettingsPage.PERMISSIONS,
            KireiSettingsPage.DIAGNOSTICS,
            KireiSettingsPage.ABOUT,
        )
    }
    val categoryFocusRequesters = remember { categoryPages.associateWith { FocusRequester() } }
    var lastFocusedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        lastFocusedCategory
            ?.let { saved -> categoryPages.firstOrNull { it.name == saved } }
            ?.let { categoryFocusRequesters[it] }
            ?.requestFocus()
    }
    val openCategory: (KireiSettingsPage) -> Unit = { page ->
        lastFocusedCategory = page.name
        viewModel.openSettingsPage(page)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentLeft = adaptiveMetrics.contentFrame.x.dp
        val contentRight = (maxWidth.value - adaptiveMetrics.contentFrame.x - adaptiveMetrics.contentFrame.w).dp
        Box(
            Modifier.fillMaxSize()
                .absolutePadding(left = contentLeft, right = contentRight)
                .padding(top = adaptiveMetrics.safeFrame.topDp.dp)
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = maxContentWidth).fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = adaptiveMetrics.headerHeightDp.dp / 3f, bottom = adaptiveMetrics.bottomContentPaddingDp.dp)
            ) {
            Text(
                "设置",
                color = colors.text,
                style = AppleTypography.displayLarge,
            )
            SettingsSection("设置选项", colors) {
                OrientationModeRow(
                    value = state.settings?.orientationMode ?: OrientationMode.AUTO,
                    colors = colors,
                    orientation = adaptiveMetrics.orientation,
                    onChange = viewModel::setOrientationMode,
                )
                SettingsCategoryRow(
                    Icons.Rounded.Folder,
                    "游戏库",
                    "扫描目录、封面与元数据",
                    colors,
                    categoryFocusRequesters.getValue(KireiSettingsPage.LIBRARY_HUB),
                    adaptiveMetrics.orientation,
                ) {
                    openCategory(KireiSettingsPage.LIBRARY_HUB)
                }
                SettingsCategoryRow(
                    Icons.Rounded.Gamepad,
                    "引擎",
                    "KRKR 全局默认设置",
                    colors,
                    categoryFocusRequesters.getValue(KireiSettingsPage.KRKR),
                    adaptiveMetrics.orientation,
                ) {
                    openCategory(KireiSettingsPage.KRKR)
                }
                SettingsCategoryRow(
                    Icons.Rounded.Palette,
                    "界面与辅助",
                    "动画、透明度与对比度",
                    colors,
                    categoryFocusRequesters.getValue(KireiSettingsPage.INTERFACE),
                    adaptiveMetrics.orientation,
                ) {
                    openCategory(KireiSettingsPage.INTERFACE)
                }
                SettingsCategoryRow(
                    Icons.Rounded.Folder,
                    "存储与权限",
                    "SAF 目录和兼容权限",
                    colors,
                    categoryFocusRequesters.getValue(KireiSettingsPage.PERMISSIONS),
                    adaptiveMetrics.orientation,
                ) {
                    openCategory(KireiSettingsPage.PERMISSIONS)
                }
                SettingsCategoryRow(
                    Icons.Rounded.Terminal,
                    "日志与诊断",
                    "记录、导出或复制诊断日志",
                    colors,
                    categoryFocusRequesters.getValue(KireiSettingsPage.DIAGNOSTICS),
                    adaptiveMetrics.orientation,
                ) {
                    openCategory(KireiSettingsPage.DIAGNOSTICS)
                }
                SettingsCategoryRow(
                    Icons.Rounded.Info,
                    "关于",
                    "KireiBox 项目信息",
                    colors,
                    categoryFocusRequesters.getValue(KireiSettingsPage.ABOUT),
                    adaptiveMetrics.orientation,
                ) {
                    openCategory(KireiSettingsPage.ABOUT)
                }
            }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    colors: KireiColors,
    focusRequester: FocusRequester,
    orientation: HomeOrientation,
    onClick: () -> Unit,
) {
    val metrics = resolveSettingsCategoryRowMetrics(orientation)
    SettingsRowContent(
        icon = icon,
        title = title,
        subtitle = subtitle,
        colors = colors,
        modifier = Modifier.focusRequester(focusRequester),
        onClick = onClick,
        verticalPaddingDp = metrics.verticalPaddingDp,
        minimumHeightDp = metrics.minimumHeightDp,
        iconSizeDp = metrics.iconSizeDp,
    )
}

internal data class SettingsCategoryRowMetrics(
    val verticalPaddingDp: Float,
    val minimumHeightDp: Float,
    val iconSizeDp: Float,
)

internal fun resolveSettingsCategoryRowMetrics(orientation: HomeOrientation): SettingsCategoryRowMetrics =
    if (orientation == HomeOrientation.LANDSCAPE) {
        SettingsCategoryRowMetrics(verticalPaddingDp = 4f, minimumHeightDp = 40f, iconSizeDp = 28f)
    } else {
        SettingsCategoryRowMetrics(verticalPaddingDp = 10f, minimumHeightDp = 56f, iconSizeDp = 34f)
    }

@Composable
private fun SettingsSubPage(
    page: KireiSettingsPage,
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    layoutMode: KireiLayoutMode,
    safeDrawingPadding: PaddingValues,
    bottomContentPadding: Dp
    , adaptiveMetrics: AdaptiveUiMetrics
) {
    val maxContentWidth = adaptiveMetrics.settingsContentWidthDp.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentLeft = adaptiveMetrics.contentFrame.x.dp
        val contentRight = (maxWidth.value - adaptiveMetrics.contentFrame.x - adaptiveMetrics.contentFrame.w).dp
        Box(
            Modifier.fillMaxSize()
                .absolutePadding(left = contentLeft, right = contentRight)
                .padding(top = adaptiveMetrics.safeFrame.topDp.dp)
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = maxContentWidth).fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = adaptiveMetrics.headerHeightDp.dp / 3f,
                        bottom = adaptiveMetrics.bottomContentPaddingDp.dp,
                    )
            ) {
        Row(
            Modifier.heightIn(min = adaptiveMetrics.headerHeightDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.Rounded.ArrowBack, "返回", colors, haze, viewModel::navigateUpSettings)
            Text(
                settingsPageTitle(page),
                color = colors.text,
                style = AppleTypography.headlineMedium,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        Spacer(Modifier.height(adaptiveMetrics.gridSpacingDp.dp))
        when (page) {
            KireiSettingsPage.LIBRARY_HUB -> LibraryHubSettings(state, colors, viewModel)
            KireiSettingsPage.KRKR -> KrkrSettings(state, colors, viewModel, actions)
            KireiSettingsPage.COVERS -> CoverSettings(state, colors, haze, viewModel)
            KireiSettingsPage.LIBRARY -> LibrarySettings(state, colors, viewModel, actions)
            KireiSettingsPage.INTERFACE -> InterfaceSettings(state, colors, viewModel)
            KireiSettingsPage.PERMISSIONS -> PermissionSettings(state, colors, actions)
            KireiSettingsPage.DIAGNOSTICS -> DiagnosticSettings(state, colors, viewModel, actions)
            KireiSettingsPage.ABOUT -> AboutSettings(colors, viewModel, actions)
            else -> Unit
        }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private fun settingsPageTitle(page: KireiSettingsPage): String = when (page) {
    KireiSettingsPage.ROOT -> "设置"
    KireiSettingsPage.LIBRARY_HUB -> "游戏库"
    KireiSettingsPage.LIBRARY -> "扫描目录"
    KireiSettingsPage.COVERS -> "封面与元数据"
    KireiSettingsPage.KRKR -> "KRKR 设置"
    KireiSettingsPage.INTERFACE -> "界面与辅助"
    KireiSettingsPage.PERMISSIONS -> "存储与权限"
    KireiSettingsPage.DIAGNOSTICS -> "日志与诊断"
    KireiSettingsPage.ABOUT -> "关于"
}

@Composable
private fun LibraryHubSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    SettingsSection("游戏库", colors) {
        SettingsRow(
            Icons.Rounded.Folder,
            "扫描目录",
            "${state.settings?.scanRoots?.size ?: 0} 个目录",
            colors,
        ) { viewModel.openSettingsPage(KireiSettingsPage.LIBRARY) }
        SettingsRow(
            Icons.Rounded.CollectionsBookmark,
            "封面与元数据",
            if (state.pendingCoverCount > 0) {
                "待确认 ${state.pendingCoverCount} · YukiHub 来源与候选"
            } else {
                "YukiHub 来源与候选"
            },
            colors,
        ) { viewModel.openSettingsPage(KireiSettingsPage.COVERS) }
    }
}

@Composable
private fun InterfaceSettings(state: KireiUiState, colors: KireiColors, viewModel: KireiViewModel) {
    SettingsSection("显示与交互", colors) {
        MotionModeRow(
            requested = state.settings?.motionMode ?: MotionMode.AUTO,
            resolved = LocalResolvedMotionMode.current,
            colors = colors,
            onChange = viewModel::setMotionMode,
        )
        SwitchRow("减少透明度", state.settings?.reducedTransparency == true, colors, viewModel::setReducedTransparency)
        SwitchRow("增强对比度", state.settings?.increasedContrast == true, colors, viewModel::setContrast)
    }
    SettingsSection("首页", colors) {
        HomePresentationModeRow(
            value = state.settings?.homePresentationMode ?: com.yuki.yukihub.settings.HomePresentationMode.IMMERSIVE,
            colors = colors,
            onChange = viewModel::setHomePresentationMode,
        )
    }
}

@Composable
private fun DiagnosticSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    actions: KireiActions,
) {
    SettingsSection("日志", colors) {
        SwitchRow("记录诊断日志", state.diagnosticLoggingEnabled, colors, viewModel::setDiagnosticLogging)
        SettingsRow(Icons.Rounded.Description, "导出日志", "生成文本文件并选择分享目标", colors, actions.exportLogs)
        SettingsRow(Icons.Rounded.Terminal, "复制日志", "复制设备信息与最近日志到剪贴板", colors, actions.copyLogs)
    }
}

@Composable
private fun AboutSettings(colors: KireiColors, viewModel: KireiViewModel, actions: KireiActions) {
    val context = LocalContext.current
    SettingsSection("KireiBox", colors) {
        SettingsRow(Icons.Rounded.Update, "检查更新", "当前版本 ${BuildConfig.VERSION_NAME}", colors) {
            viewModel.checkForUpdates(manual = true)
        }
        SettingsRow(Icons.Rounded.Update, "GitHub", "https://github.com/Yukin0a/KireiBox", colors) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yukin0a/KireiBox")))
            }.onFailure {
                viewModel.showNotice("无法打开浏览器")
            }
        }
        SettingsRow(Icons.Rounded.Info, "关于 KireiBox", "项目说明", colors, actions.showAbout)
    }
}

@Composable
private fun KrkrSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    actions: KireiActions,
) {
    SettingsSection("全局默认", colors) {
        SettingsRow(
            Icons.Rounded.PlayArrow,
            "启动 KRKR 原生模拟器",
            "打开原生文件选择与兼容工具界面",
            colors,
            actions.launchKrkrNative,
        )
        ChoiceRow("引擎版本", state.settings?.krEngineVersion ?: "auto", listOf("auto", "1.3.9", "1.3.4", "1.2.6"), colors, viewModel::setKrEngineVersion)
        SwitchRow("兼容模式", state.settings?.krCompatMode == true, colors, viewModel::setKrCompat)
        SwitchRow(
            "跳过开头信息弹窗",
            state.settings?.krSkipStartupInfoDialog == true,
            colors,
            viewModel::setKrSkipStartupInfoDialog
        )
        Text(
            "开启后仅自动确认启动后 30 秒内的单按钮信息；多按钮、输入框和剧情弹窗不会跳过。",
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    Text("单个游戏的渲染、内存、FPS 与字体设置，请点按游戏卡片并打开“KRKR 游戏设置”页面。", color = colors.secondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp))
}

@Composable
private fun KrkrGameSettingsPage(
    game: Game,
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel
) {
    var settings by remember(game.id, state.krkrSettings) { mutableStateOf(state.krkrSettings) }
    BackHandler(onBack = viewModel::closeKrkrEditor)
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 840.dp).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "返回", colors, haze, viewModel::closeKrkrEditor)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("KRKR 游戏设置", color = colors.text, style = AppleTypography.headlineMedium)
                    Text(game.title.orEmpty(), color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp),
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                item {
                    Text(GameLocationLabel.forGame(LocalContext.current, game), color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                item { Spacer(Modifier.height(10.dp)) }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceRaised),
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        KrkrGameEditor(settings, { settings = it }, colors)
                    }
                }
            }
            Button(
                onClick = { viewModel.saveKrkrSettings(game, settings) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("保存此游戏设置") }
        }
    }
}

@Composable
private fun KrkrGameEditor(settings: KrkrGameSettings, onChange: (KrkrGameSettings) -> Unit, colors: KireiColors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow("渲染器", settings.renderer, listOf("software", "opengl"), colors) { onChange(settings.copy(renderer = it)) }
            ChoiceRow("内存", settings.memoryUsage, listOf("unlimited", "low", "medium", "high"), colors) { onChange(settings.copy(memoryUsage = it)) }
            ChoiceRow("渲染线程", settings.renderThread, listOf("auto", "1", "2", "3", "4", "6", "8"), colors) { onChange(settings.copy(renderThread = it)) }
            Text("FPS 限制：${settings.fpsLimit}", color = colors.secondary)
            Slider(settings.fpsLimit.toFloat(), { onChange(settings.copy(fpsLimit = it.toInt().coerceIn(15, 240))) }, valueRange = 15f..240f)
            Text("菜单透明度：${(settings.menuOpacity * 100).toInt()}%", color = colors.secondary)
            Slider(settings.menuOpacity, { onChange(settings.copy(menuOpacity = it)) }, valueRange = .05f..1f)
            Text("虚拟光标：${(settings.cursorScale * 100).toInt()}%", color = colors.secondary)
            Slider(settings.cursorScale, { onChange(settings.copy(cursorScale = it)) }, valueRange = .25f..2f)
            SwitchRow("启动游戏默认呼出指针", settings.showVirtualCursorOnStart, colors) { onChange(settings.copy(showVirtualCursorOnStart = it)) }
            SwitchRow("打印日志", settings.outputLog, colors) { onChange(settings.copy(outputLog = it)) }
            SwitchRow("显示 FPS", settings.showFps, colors) { onChange(settings.copy(showFps = it)) }
            SwitchRow("保持屏幕常亮", settings.keepScreenAwake, colors) { onChange(settings.copy(keepScreenAwake = it)) }
            SwitchRow("纹理压缩", settings.textureCompression, colors) { onChange(settings.copy(textureCompression = it)) }
            SwitchRow("强制使用默认字体", settings.forceDefaultFont, colors) { onChange(settings.copy(forceDefaultFont = it)) }
            AnimatedVisibility(
                visible = settings.forceDefaultFont,
                enter = fadeIn(tween(160)) + expandVertically(),
                exit = fadeOut(tween(120)) + shrinkVertically()
            ) {
                OutlinedTextField(
                    value = settings.defaultFont,
                    onValueChange = { onChange(settings.copy(defaultFont = it)) },
                    label = { Text("默认字体文件名") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KireiActionSheet(
    title: String,
    colors: KireiColors,
    onDismiss: () -> Unit,
    expanded: Boolean = false,
    scrollableContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val sheetColor = if (colors.isDark) Color(0xFF1D1B23) else Color(0xFFFBFAFE)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetColor,
        scrimColor = Color.Black.copy(alpha = if (colors.isDark) .34f else .18f),
        dragHandle = null,
        shape = shape,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        ModalSystemBarVisibilityEffect(isDark = colors.isDark)
        Column(
            Modifier.fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxHeight(.88f) else Modifier)
                .background(sheetColor, shape)
                .border(.7.dp, colors.outline, shape)
                .navigationBarsPadding().padding(bottom = 18.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 9.dp, bottom = 11.dp)
                    .size(36.dp, 5.dp).background(colors.secondary.copy(alpha = .42f), CircleShape)
            )
            Text(
                title,
                color = colors.text,
                style = AppleTypography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp)
            )
            val contentModifier = Modifier.fillMaxWidth()
                .then(if (expanded) Modifier.weight(1f) else Modifier.heightIn(max = 560.dp))
                .then(if (scrollableContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            Column(contentModifier, content = content)
        }
    }
}

/**
 * Material bottom sheets own a dialog window, so the Activity controller cannot keep their
 * status bar policy in sync by itself. Apply the same pure device policy to the dialog window
 * and repeat it on the first composed frame, after Material has finished configuring the window.
 */
@Composable
private fun ModalSystemBarVisibilityEffect(isDark: Boolean) {
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val lightIcons = SystemBarVisibilityPolicy.statusBarIconsLight(isDark)
    val windowClass = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
        configuration.screenLayout,
    ) {
        ResponsiveLayoutPolicy.resolveWindowClass(
            safeWidthDp = configuration.screenWidthDp.toFloat(),
            safeHeightDp = configuration.screenHeightDp.toFloat(),
            smallestScreenWidthDp = configuration.smallestScreenWidthDp,
            screenLayoutSize = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK,
        )
    }
    val hideStatusBar = SystemBarVisibilityPolicy.shouldHideStatusBar(windowClass)

    fun applyPolicy() {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
            if (hideStatusBar) {
                hide(WindowInsetsCompat.Type.statusBars())
            } else {
                show(WindowInsetsCompat.Type.statusBars())
            }
            show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    DisposableEffect(view, windowClass, hideStatusBar, lightIcons, isDark) {
        applyPolicy()
        onDispose { }
    }
    LaunchedEffect(view, windowClass, hideStatusBar, lightIcons, isDark) {
        withFrameNanos { }
        applyPolicy()
    }
}

@Composable
private fun LibraryOverviewSheet(
    games: List<Game>,
    sort: LibraryOverviewSort,
    colors: KireiColors,
    onSortChange: (LibraryOverviewSort) -> Unit,
    onDismiss: () -> Unit,
    onGame: (Game) -> Unit
) {
    val orderedGames = remember(games, sort) { LibraryOverviewPolicy.sorted(games, sort) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val listHeight = (screenHeight * .52f).coerceIn(280.dp, 430.dp)
    KireiActionSheet(
        title = "库内 ${games.size} 部游戏",
        colors = colors,
        onDismiss = onDismiss,
        scrollableContent = false
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip("最近游玩", sort == LibraryOverviewSort.RECENT, colors) {
                onSortChange(LibraryOverviewSort.RECENT)
            }
            FilterChip("游玩时长", sort == LibraryOverviewSort.PLAY_TIME, colors) {
                onSortChange(LibraryOverviewSort.PLAY_TIME)
            }
        }
        if (orderedGames.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(listHeight).background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text("游戏库还没有内容", color = colors.secondary, style = AppleTypography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().height(listHeight),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(orderedGames, key = { _, game -> game.id }) { index, game ->
                    PressScaleBox(
                        Modifier.fillMaxWidth(),
                        RoundedCornerShape(18.dp),
                        { onGame(game) },
                        pressedScale = .985f
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (index + 1).toString(),
                                color = colors.secondary,
                                style = AppleTypography.bodyMediumEmphasis,
                                modifier = Modifier.width(34.dp)
                            )
                            GameCoverArtwork(game, Modifier.size(44.dp, 58.dp), ContentScale.Crop, 10.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(
                                    game.title.orEmpty(),
                                    color = colors.text,
                                    style = AppleTypography.bodyLargeEmphasis,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    game.engine?.displayName ?: "游戏",
                                    color = colors.secondary,
                                    style = AppleTypography.bodySmall,
                                    maxLines = 1
                                )
                            }
                            Text(
                                LibraryOverviewPolicy.compactPlayTime(game.totalPlayTime),
                                color = colors.secondary,
                                style = AppleTypography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameActionSheet(
    game: Game,
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    actions: KireiActions,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    KireiActionSheet(game.title ?: "游戏操作", colors, onDismiss, expanded = true) {
            Text(
                GameLocationLabel.forGame(context, game),
                color = colors.secondary,
                style = AppleTypography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
            )
            SheetActionRow(
                icon = Icons.Rounded.PlayArrow,
                title = "启动游戏",
                subtitle = game.launchTarget?.takeIf(String::isNotBlank) ?: "游戏目录",
                colors = colors,
                prominent = true
            ) {
                onDismiss()
                actions.launchGame(game)
            }
            if (game.engine == EngineType.KIRIKIRI) {
                SheetActionRow(
                    icon = Icons.Rounded.Description,
                    title = "选择 XP3 启动文件",
                    subtitle = game.launchTarget?.takeIf(String::isNotBlank) ?: "自动选择",
                    colors = colors,
                    trailing = Icons.Rounded.ChevronRight,
                ) {
                    viewModel.openXp3Picker(game)
                }
            }
            SheetActionRow(Icons.Rounded.Edit, "修改游戏名称", "只修改游戏库显示名称", colors, onClick = onRename)
            SheetActionRow(
                if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (game.favorite) "取消收藏" else "加入收藏",
                "保留游戏文件和游玩记录",
                colors
            ) { viewModel.toggleFavorite(game) }
            if (game.engine == EngineType.KIRIKIRI) {
                SheetActionRow(
                    icon = Icons.Rounded.Tune,
                    title = "KRKR 游戏设置",
                    subtitle = "在独立页面调整渲染、内存、FPS、光标与字体",
                    colors = colors,
                    trailing = Icons.Rounded.ExpandMore
                ) {
                    viewModel.openKrkrEditor(game)
                }
            }
            SheetActionRow(Icons.Rounded.CollectionsBookmark, "搜索并更换封面", "使用当前元数据来源查找候选", colors) {
                onDismiss()
                viewModel.searchCover(game, game.title.orEmpty(), CoverSourceMode.fromStored(state.settings?.metadataSource))
            }
            SheetActionRow(Icons.Rounded.DeleteOutline, "移出游戏库", "不会删除磁盘中的游戏文件", colors, destructive = true, onClick = onRemove)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
                Text("取消", color = colors.secondary)
            }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    colors: KireiColors,
    prominent: Boolean = false,
    destructive: Boolean = false,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        if (pressed) colors.surfaceRaised else if (prominent) colors.accent.copy(alpha = if (colors.isDark) .16f else .10f) else Color.Transparent,
        tween(100),
        label = "sheetAction"
    )
    Row(
        Modifier.fillMaxWidth().background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(
                if (destructive) colors.danger.copy(alpha = .12f) else colors.accent.copy(alpha = .13f),
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (destructive) colors.danger else colors.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, color = if (destructive) colors.danger else colors.text, style = AppleTypography.headlineSmall)
            Text(subtitle, color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Icon(it, null, tint = colors.secondary, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun RenameGameDialog(
    game: Game,
    value: String,
    onValueChange: (String) -> Unit,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改游戏名称", color = colors.text) },
        text = {
            Column {
                Text(game.originalTitle.orEmpty(), color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 2)
                OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = value.isNotBlank()) { Text("保存", color = colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = colors.secondary) } },
        shape = RoundedCornerShape(28.dp),
        containerColor = colors.surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoveGameSheet(game: Game, colors: KireiColors, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    KireiActionSheet("移出游戏库", colors, onDismiss) {
        Text(
            "只会隐藏“${game.title ?: "游戏"}”的卡片，不会删除游戏文件、封面、元数据或游玩记录。",
            color = colors.secondary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)
        )
        SheetActionRow(Icons.Rounded.DeleteOutline, "移出 ${game.title ?: "游戏"}", "可在 5 秒内撤销", colors, destructive = true, onClick = onConfirm)
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("取消", color = colors.secondary) }
    }
}

@Composable
private fun CoverSettings(state: KireiUiState, colors: KireiColors, haze: HazeState?, viewModel: KireiViewModel) {
    val source = CoverSourceMode.fromStored(state.settings?.metadataSource)
    val tokenVisible = Alpha11KireiBoxAppIntegration.tokenVisible(source)
    var showTokenEditor by remember { mutableStateOf(false) }
    var token by remember(state.settings?.bangumiToken) { mutableStateOf(state.settings?.bangumiToken.orEmpty()) }
    LaunchedEffect(source) {
        if (!tokenVisible) showTokenEditor = false
    }
    SettingsSection("封面数据来源", colors) {
        CoverSourceChoiceRow("默认来源", source, colors) { viewModel.setMetadataSource(it) }
        Text(
            coverSourceDescription(source),
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (tokenVisible) {
            SettingsRow(
                Icons.Rounded.CollectionsBookmark,
                "Bangumi Token",
                if (state.settings?.bangumiToken.isNullOrBlank()) "未设置" else "已设置",
                colors
            ) { showTokenEditor = true }
        }
    }
    SettingsSection("选择游戏封面", colors) {
        val orderedGames = state.games.sortedBy { if (it.vndbBindingMode in setOf("REVIEW", "RETRY")) 0 else 1 }
        orderedGames.forEach { game ->
            val subtitle = when {
                game.id in state.coverTaskGameIds -> "正在后台匹配…"
                game.vndbBindingMode == "REVIEW" -> "待确认 · 最高匹配 ${game.vndbMatchScore.toInt()}%"
                game.vndbBindingMode == "RETRY" -> "匹配失败 · 点按重试"
                game.vndbBindingMode == "AUTO" -> "已自动匹配 ${game.vndbMatchScore.toInt()}%"
                game.vndbBindingMode == "MANUAL" -> "已手动选择"
                game.vndbBindingMode == "DISABLED" -> "使用引擎默认封面"
                else -> "搜索候选后确认并下载"
            }
            SettingsRow(Icons.Rounded.CollectionsBookmark, game.title.orEmpty(), subtitle, colors) {
                viewModel.searchCover(game, game.title.orEmpty(), source)
            }
        }
    }
    if (showTokenEditor && tokenVisible) {
        Dialog(onDismissRequest = { showTokenEditor = false }) {
            GlassSurface(colors, haze, Modifier.fillMaxWidth().padding(18.dp), RoundedCornerShape(26.dp), KireiGlassRole.ALERT) {
                Column(Modifier.padding(20.dp)) {
                    Text("Bangumi Token", color = colors.text, style = AppleTypography.headlineSmall)
                    Text("用于 Bangumi 官方与镜像接口。", color = colors.secondary, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                    Row(Modifier.align(Alignment.End).padding(top = 12.dp)) {
                        TextButton(onClick = { showTokenEditor = false }) { Text("取消", color = colors.secondary) }
                        TextButton(onClick = { viewModel.setBangumiToken(token.trim()); showTokenEditor = false }) { Text("保存", color = colors.accent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySettings(state: KireiUiState, colors: KireiColors, viewModel: KireiViewModel, actions: KireiActions) {
    val context = LocalContext.current
    SettingsSection("文件选择", colors) {
        SwitchRow(
            "使用内置文件浏览器",
            state.settings?.useBuiltinFileChooser != false,
            colors,
            viewModel::setUseBuiltinFileChooser
        )
        Text(
            "默认使用 KireiBox 浏览器；SD 卡和受限制存储可随时切换到系统 SAF。",
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    SettingsSection("扫描更新", colors) {
        SwitchRow(
            "目录自动更新",
            state.settings?.autoScanOnStartup != false,
            colors,
            viewModel::setAutoScanOnStartup,
        )
        Text(
            "回到 KireiBox 时自动扫描已开启的目录，只添加新游戏。",
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SwitchRow(
            "已移除游戏更新目录不会再添加",
            state.settings?.keepRemovedGamesHiddenOnRefresh != false,
            colors,
            viewModel::setKeepRemovedGamesHiddenOnRefresh,
        )
        Text(
            "打开时，移出游戏库的记录扫目录也不会加回来。关掉才会重新出现。",
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    SettingsSection("扫描目录", colors) {
        state.settings?.scanRoots.orEmpty().forEachIndexed { index, root ->
            SettingsRow(Icons.Rounded.Folder, "目录 ${index + 1}", GameLocationLabel.format(context, root), colors) { actions.repairPermission(root) }
        }
        SettingsRow(Icons.Rounded.Add, "添加扫描目录", "选择并保存 SAF 读写授权", colors, actions.chooseDirectory)
    }
    SettingsSection("排序", colors) { ChoiceRow("游戏库顺序", state.settings?.sortMode ?: "recent", listOf("recent", "newest", "name"), colors, viewModel::setSortMode) }
}

@Composable
private fun PermissionSettings(state: KireiUiState, colors: KireiColors, actions: KireiActions) {
    val context = LocalContext.current
    SettingsSection("访问状态", colors) {
        Text(
            if (com.yuki.yukihub.engine.EngineStorageProbe.isHuaweiFamily()) {
                "SAF 用于扫描、封面和目录管理；KRKR 原生内核直接读取 XP3，还需单独开启存储权限。华为/HarmonyOS 路径：设置 → 应用 → KireiBox → 权限管理 → 存储 → 允许；HarmonyOS 3 及以上还需开启“所有文件访问”。"
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                "SAF 用于扫描、封面和目录管理；KRKR 原生内核直接读取 XP3，还需要单独检查所有文件访问权限。"
            } else {
                "SAF 用于扫描、封面和目录管理；KRKR 原生内核直接读取 XP3，Android 10 及以下还需要单独开启存储权限。"
            },
            color = colors.secondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
        Button(onClick = actions.checkPermissions, shape = CircleShape) { Text("开始检查") }
        state.settings?.scanRoots.orEmpty().forEach { root ->
            SettingsRow(Icons.Rounded.Folder, GameLocationLabel.format(context, root), "重新授权", colors) { actions.repairPermission(root) }
        }
    }
}

@Composable
private fun CoverPickerPage(search: CoverSearchUi, colors: KireiColors, haze: HazeState?, viewModel: KireiViewModel, actions: KireiActions) {
    BackHandler(onBack = viewModel::dismissCoverSearch)
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        val wide = maxWidth >= 700.dp
        val shape = if (wide) RoundedCornerShape(30.dp) else RoundedCornerShape(0.dp)
        Column(
            Modifier.fillMaxWidth().widthIn(max = 960.dp)
                .then(if (wide) Modifier.fillMaxHeight(.9f) else Modifier.fillMaxHeight())
                .background(colors.surface, shape).border(if (wide) .7.dp else 0.dp, colors.outline, shape)
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = if (wide) 26.dp else 18.dp, vertical = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "稍后处理", colors, haze, viewModel::dismissCoverSearch)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("选择封面", color = colors.text, style = AppleTypography.headlineMedium)
                    Text(search.game?.title.orEmpty(), color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedTextField(
                value = search.keyword,
                onValueChange = viewModel::updateCoverKeyword,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                singleLine = true,
                label = { Text("搜索名称") },
                trailingIcon = {
                    IconButton(onClick = { viewModel.retryCoverSearch() }, enabled = !search.loading && !search.saving) {
                        Icon(Icons.Rounded.Search, "重新搜索")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
            CoverSourceChoiceRow("封面来源", search.source, colors, viewModel::retryCoverSearch)
            if (search.attemptedKeywords.isNotEmpty()) {
                Text(
                    "已尝试：${search.attemptedKeywords.joinToString(" · ")}",
                    color = colors.secondary,
                    style = AppleTypography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
            search.error?.let {
                Text(
                    it,
                    color = colors.danger,
                    style = AppleTypography.bodySmall,
                    modifier = Modifier.fillMaxWidth().background(colors.danger.copy(alpha = .1f), RoundedCornerShape(14.dp)).padding(12.dp)
                )
            }
            when {
                search.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accent)
                        Text("正在查询 ${choiceLabel(search.source.key)}…", color = colors.secondary, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                search.candidates.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("没有找到可用封面", color = colors.secondary)
                        TextButton(onClick = { viewModel.retryCoverSearch() }) { Text("重新搜索", color = colors.accent) }
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(search.candidates, key = { it.key }) { candidate ->
                        val selected = search.selectedKey == candidate.key
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (selected) colors.accent.copy(alpha = .13f) else colors.surfaceRaised)
                                .border(if (selected) 1.4.dp else .7.dp, if (selected) colors.accent else colors.outline, RoundedCornerShape(18.dp))
                                .clickable(enabled = !search.saving) { viewModel.selectCoverCandidate(candidate.key) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val image = rememberCover(candidate.metadata.coverUrl)
                            Box(Modifier.size(66.dp, 88.dp).clip(RoundedCornerShape(11.dp)).background(colors.background)) {
                                if (image != null) androidx.compose.foundation.Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(
                                    candidate.metadata.chineseTitle.ifBlank { candidate.metadata.originalTitle.ifBlank { candidate.metadata.romanTitle } },
                                    color = colors.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("${choiceLabel(candidate.source.key)} · 匹配度 ${candidate.score.toInt()}%", color = colors.secondary, style = AppleTypography.labelLarge, modifier = Modifier.padding(top = 5.dp))
                                Text(candidate.metadata.developer, color = colors.secondary, style = AppleTypography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = actions.chooseCoverFromAlbum,
                    enabled = !search.saving && !search.loading,
                    modifier = Modifier.weight(1f),
                ) { Text("从相册选择", color = colors.accent, maxLines = 1) }
                TextButton(
                    onClick = actions.chooseCoverFromFiles,
                    enabled = !search.saving && !search.loading,
                    modifier = Modifier.weight(1f),
                ) { Text("从手机文件选择", color = colors.accent, maxLines = 1) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = viewModel::useDefaultCover, enabled = !search.saving) {
                    Text("默认封面", color = colors.secondary)
                }
                TextButton(onClick = viewModel::dismissCoverSearch, modifier = Modifier.weight(1f), enabled = !search.saving) {
                    Text("稍后处理", color = colors.secondary)
                }
                Button(
                    onClick = viewModel::confirmSelectedCover,
                    modifier = Modifier.weight(1.5f),
                    enabled = search.selectedKey != null && !search.loading && !search.saving,
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                ) {
                    if (search.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onAccent)
                    else Text("使用此封面")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, colors: KireiColors, onAction: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, color = colors.text, style = AppleTypography.displaySmall, modifier = Modifier.weight(1f)); TextButton(onClick = onAction) { Text(action, color = colors.accent, style = AppleTypography.bodySmallEmphasis) } } }

@Composable private fun FilterChip(label: String, selected: Boolean, colors: KireiColors, onClick: () -> Unit) { Surface(onClick = onClick, shape = CircleShape, color = if (selected) colors.accent else colors.surfaceRaised) { Text(label, color = if (selected) colors.onAccent else colors.secondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = AppleTypography.labelLarge, maxLines = 1) } }
@Composable
private fun SettingsSection(
    title: String,
    colors: KireiColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        title,
        color = colors.secondary,
        style = AppleTypography.bodySmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 5.dp)
    )
    Card(colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}
@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    colors: KireiColors,
    onClick: (() -> Unit)? = null,
) = SettingsRowContent(icon, title, subtitle, colors, Modifier, onClick)

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    colors: KireiColors,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) = SettingsRowContent(icon, title, subtitle, colors, Modifier.focusRequester(focusRequester), onClick)

@Composable
private fun SettingsRowContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    colors: KireiColors,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    verticalPaddingDp: Float = 10f,
    minimumHeightDp: Float = 0f,
    iconSizeDp: Float = 34f,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowColor by animateColorAsState(if (pressed) colors.surfaceRaised else Color.Transparent, tween(90), label = "settingsPress")
    Row(
        modifier.fillMaxWidth().background(rowColor)
            .clickable(interactionSource = interaction, indication = null, enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 16.dp, vertical = verticalPaddingDp.dp)
            .then(if (minimumHeightDp > 0f) Modifier.heightIn(min = minimumHeightDp.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(iconSizeDp.dp).background(colors.accent.copy(alpha = .16f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size((iconSizeDp * .59f).dp))
        }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(title, color = colors.text)
            Text(subtitle, color = colors.secondary, style = AppleTypography.bodySmall, maxLines = 2)
        }
        if (trailingContent != null) trailingContent()
        else if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondary)
    }
}
@Composable private fun SwitchRow(label: String, checked: Boolean, colors: KireiColors, onCheckedChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = colors.text, modifier = Modifier.weight(1f)); Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.success, uncheckedThumbColor = if (colors.isDark) Color(0xFFB7B3C2) else Color(0xFF8E8998), uncheckedTrackColor = colors.surfaceRaised, uncheckedBorderColor = colors.outline)) } }
@Composable
private fun MotionModeRow(
    requested: MotionMode,
    resolved: MotionMode,
    colors: KireiColors,
    onChange: (MotionMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) { expanded = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("动画效果", color = colors.text, style = AppleTypography.bodyLarge)
            Text(
                if (requested == MotionMode.AUTO) "自动 · 当前${motionModeLabel(resolved)}" else motionModeDescription(requested),
                color = colors.secondary,
                style = AppleTypography.bodySmall
            )
        }
        Text(motionModeLabel(requested), color = colors.accent, style = AppleTypography.bodyMediumEmphasis)
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondary, modifier = Modifier.padding(start = 8.dp))
    }
    if (expanded) {
        KireiActionSheet("动画效果", colors, { expanded = false }) {
            MotionMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        expanded = false
                        onChange(mode)
                    }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(motionModeLabel(mode), color = colors.text, style = AppleTypography.bodyLargeEmphasis)
                        Text(
                            if (mode == MotionMode.AUTO) "根据设备性能与省电状态自动选择" else motionModeDescription(mode),
                            color = colors.secondary,
                            style = AppleTypography.bodySmall
                        )
                    }
                    if (requested == mode) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                }
            }
        }
    }
}

private fun motionModeLabel(mode: MotionMode): String = when (mode) {
    MotionMode.AUTO -> "自动"
    MotionMode.POLISHED -> "精美"
    MotionMode.REDUCED -> "精简"
}

private fun motionModeDescription(mode: MotionMode): String = when (mode) {
    MotionMode.AUTO -> "自动选择"
    MotionMode.POLISHED -> "完整弹性、缩放与补位动画"
    MotionMode.REDUCED -> "短淡入淡出，减少持续动画"
}

@Composable
private fun OrientationModeRow(
    value: OrientationMode,
    colors: KireiColors,
    orientation: HomeOrientation,
    onChange: (OrientationMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val metrics = resolveOrientationModeRowMetrics(orientation)
    SettingsRowContent(
        icon = Icons.Rounded.ScreenRotation,
        title = "界面方向",
        subtitle = "自动跟随传感器、固定竖屏或横屏",
        colors = colors,
        modifier = Modifier,
        onClick = { expanded = true },
        verticalPaddingDp = metrics.verticalPaddingDp,
        minimumHeightDp = metrics.minimumHeightDp,
        iconSizeDp = metrics.iconSizeDp,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value.displayName, color = colors.accent, style = AppleTypography.bodyMediumEmphasis)
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = colors.secondary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    )
    if (expanded) {
        KireiActionSheet("界面方向", colors, { expanded = false }, expanded = true) {
            OrientationMode.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        expanded = false
                        onChange(option)
                    }.padding(horizontal = 24.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.displayName, color = colors.text, style = AppleTypography.bodyLargeEmphasis)
                        Text(orientationModeDescription(option), color = colors.secondary, style = AppleTypography.bodySmall)
                    }
                    if (option == value) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                }
            }
        }
    }
}

internal fun resolveOrientationModeRowMetrics(orientation: HomeOrientation): SettingsCategoryRowMetrics =
    resolveSettingsCategoryRowMetrics(orientation)

private fun orientationModeDescription(mode: OrientationMode): String = when (mode) {
    OrientationMode.AUTO -> "根据设备传感器自动切换"
    OrientationMode.PORTRAIT -> "始终使用竖屏布局"
    OrientationMode.LANDSCAPE -> "始终使用横屏布局"
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSourceChoiceRow(
    label: String,
    value: CoverSourceMode,
    colors: KireiColors,
    onChange: (CoverSourceMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
        ) { expanded = true }.padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.text, modifier = Modifier.weight(1f))
        Text(value.label, color = colors.accent)
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondary, modifier = Modifier.padding(start = 8.dp))
    }
    if (expanded) {
        KireiActionSheet(label, colors, { expanded = false }, expanded = true) {
            CoverSourceMode.entries.forEach { option ->
                val presentation = Alpha11KireiBoxAppIntegration.sourcePresentation(option, value)
                val accessibility = Alpha11KireiBoxAppIntegration.sourceAccessibility(option, value)
                Row(
                    Modifier.fillMaxWidth()
                        .graphicsLayer { alpha = accessibility.alpha }
                        .background(
                            if (presentation.isHighlighted) colors.accent.copy(alpha = .12f) else Color.Transparent,
                            RoundedCornerShape(16.dp),
                        )
                        .selectable(
                            selected = accessibility.selected,
                            enabled = accessibility.enabled,
                            role = when (accessibility.role) {
                                CoverSourceAccessibilityRole.BUTTON -> Role.Button
                                CoverSourceAccessibilityRole.RADIO_BUTTON -> Role.RadioButton
                            },
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            expanded = false
                            onChange(option)
                        }
                        .semantics {
                            contentDescription = option.label
                            accessibility.stateDescription?.let { stateDescription = it }
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            option.label,
                            color = if (presentation.isHighlighted) colors.accent else colors.text,
                            style = AppleTypography.bodyLargeEmphasis,
                        )
                        Text(
                            coverSourceDescription(option),
                            color = colors.secondary,
                            style = AppleTypography.bodySmall,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (presentation.isHighlighted) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                }
            }
        }
    }
}

private fun coverSourceDescription(source: CoverSourceMode): String =
    KireiPublicCopy.coverSourceDescription(source)

@Composable
private fun ChoiceRow(label: String, value: String, options: List<String>, colors: KireiColors, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { expanded = true }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.text, modifier = Modifier.weight(1f))
        Text(choiceLabel(value), color = colors.accent)
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondary, modifier = Modifier.padding(start = 8.dp))
    }
    if (expanded) {
        KireiActionSheet(label, colors, { expanded = false }) {
            options.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        expanded = false
                        onChange(option)
                    }.padding(horizontal = 24.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(choiceLabel(option), color = colors.text, modifier = Modifier.weight(1f))
                    if (value == option) Icon(Icons.Rounded.Check, null, tint = colors.accent)
                }
            }
        }
    }
}

private fun choiceLabel(value: String): String = when (value) {
    "vndb" -> "VNDB"
    "bangumi" -> "Bangumi"
    "bangumi_mirror" -> "Bangumi 镜像"
    "ymgal" -> "月幕 Gal"
    "hikarinagi" -> "Hikarinagi"
    "recent" -> "最近游玩"
    "newest" -> "添加时间"
    "name" -> "名称"
    "auto" -> "自动"
    else -> value
}
@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    colors: KireiColors,
    haze: HazeState?,
    onClick: () -> Unit,
    opaqueFill: Boolean = false,
) {
    GlassSurface(
        colors,
        haze,
        Modifier.size(48.dp),
        CircleShape,
        KireiGlassRole.ICON,
        onClick,
        opaqueFill = opaqueFill,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, description, tint = colors.text) }
    }
}

@Composable
private fun ThemeToggleButton(colors: KireiColors, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (colors.isDark) 0f else 18f, AppleMotion.Spring.Default, label = "themeIcon")
    PressScaleBox(Modifier.size(48.dp), CircleShape, onClick, pressedScale = .91f) {
        Box(
            Modifier.fillMaxSize().background(
                if (colors.isDark) Color(0xFF2A2732) else Color(0xFFFFFFFF),
                CircleShape
            ).border(.8.dp, colors.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (colors.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                if (colors.isDark) "切换浅色模式" else "切换深色模式",
                tint = colors.accent,
                modifier = Modifier.size(23.dp).graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun PlayTriangleButton(
    colors: KireiColors,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val effectiveClick: () -> Unit = if (enabled) {
        onClick
    } else {
        { }
    }
    PressScaleBox(
        Modifier.size(size).semantics {
            contentDescription = description
            if (!enabled) disabled()
        },
        CircleShape,
        effectiveClick,
        pressedScale = .9f,
        enabled = enabled,
    ) {
        Box(
            Modifier.fillMaxSize().background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                null,
                tint = colors.onAccent,
                modifier = Modifier.size(size * .56f).graphicsLayer { translationX = size.value * .035f }
            )
        }
    }
}

@Composable
internal fun KireiMainDock(
    tab: KireiTab,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    metrics: AdaptiveUiMetrics,
    reducedTransparency: Boolean,
    modifier: Modifier = Modifier,
) {
    val rect = resolveDockRenderRect(metrics.dockRect)
    val presentation = resolveDockPresentation(
        rect.w,
        rect.h,
        forceVertical = metrics.orientation == HomeOrientation.PORTRAIT,
    )
    val shape = RoundedCornerShape((rect.h / 2f).dp)
    val dockHaze = Alpha11KireiBoxAppIntegration.dockHaze(
        isDark = colors.isDark,
        reducedTransparency = reducedTransparency,
        hazeAvailable = haze != null,
        apiSupportsHaze = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )
    Box(modifier.fillMaxSize()) {
        GlassSurface(
            colors = colors,
            haze = if (dockHaze.useOpaqueFallback) null else haze,
            modifier = Modifier.offset(rect.x.dp, rect.y.dp).size(rect.w.dp, rect.h.dp)
                .testTag("kirei-main-dock"),
            shape = shape,
            role = KireiGlassRole.DOCK,
            containerColor = dockHaze.backgroundColor,
            tint = dockHaze.tint,
            hazeInputScale = dockHaze.inputScale,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = presentation.horizontalPaddingDp.dp),
                horizontalArrangement = Arrangement.spacedBy(presentation.slotGapDp.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockButton("首页", Icons.Rounded.Home, tab == KireiTab.HOME, colors, presentation) { viewModel.selectTab(KireiTab.HOME) }
                DockButton("游戏", Icons.Rounded.Gamepad, tab == KireiTab.GAMES, colors, presentation) { viewModel.selectTab(KireiTab.GAMES) }
                DockButton("设置", Icons.Rounded.Settings, tab == KireiTab.SETTINGS, colors, presentation) { viewModel.selectTab(KireiTab.SETTINGS) }
            }
        }
    }
}

/**
 * Pure dock-glass decision for the shell dock. When reduced transparency is enabled or
 * Haze is unavailable (API < 31, or no HazeState was created), the dock falls back to the
 * clear opaque reduced-transparency colors; otherwise it blurs the sampled page content
 * behind a single low-alpha tint (Alpha 8 content-sampling glass, no opaque base).
 */
internal object DockGlassPolicy {
    const val BLUR_RADIUS_DP = 28f

    fun useOpaqueFallback(reducedTransparency: Boolean, hazeAvailable: Boolean): Boolean =
        reducedTransparency || !hazeAvailable

    fun containerColor(
        isDark: Boolean,
        pageBackground: Color,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
    ): Color = if (useOpaqueFallback(reducedTransparency, hazeAvailable)) {
        if (isDark) Color(0xF20C101C) else Color(0xFFF4F2FA)
    } else {
        Color.Transparent
    }

    fun tint(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
    ): Color? = if (useOpaqueFallback(reducedTransparency, hazeAvailable)) {
        null
    } else {
        if (isDark) Color(0x59141620) else Color(0x45FFFFFF)
    }
}

internal enum class DockPresentationLayout { VERTICAL, HORIZONTAL }

internal data class DockPresentation(
    val layout: DockPresentationLayout,
    val touchHeightDp: Float,
    val contentHeightDp: Float,
    val buttonWidthDp: Float,
    val slotGapDp: Float,
    val iconSizeDp: Float,
    val labelHeightDp: Float,
    val labelFontSizeSp: Float,
    val gapDp: Float,
    val verticalPaddingDp: Float,
    val horizontalPaddingDp: Float,
)

/**
 * Resolves the shared dock presentation from the measured dock rect. Alpha 8 dock
 * contract: the three buttons keep fixed widths (82dp vertical / 96dp horizontal) and the
 * row centers the group; only when the rect cannot fit the fixed group are the buttons
 * shrunk evenly so the dock never overflows.
 */
internal fun resolveDockPresentation(
    rectWidthDp: Float,
    rectHeightDp: Float,
    forceVertical: Boolean = false,
): DockPresentation {
    val width = rectWidthDp.coerceAtLeast(1f)
    val height = rectHeightDp.coerceAtLeast(1f)
    val slotGap = 12f
    val minimumButtonWidth = 48f
    // Alpha 10: portrait docks are always vertical icon+label so every phone ratio renders
    // the same layout; the short-height horizontal fallback stays available for landscape.
    val horizontal = !forceVertical && height < 64f
    val fixedButtonWidth = if (horizontal) 96f else 82f
    val minPadding = if (horizontal) (height * .16f).coerceIn(6f, 10f) else (height * .22f).coerceIn(10f, 18f)
    val reserved = fixedButtonWidth * 3f + slotGap * 2f
    val fitsFixed = width >= reserved + minPadding * 2f
    val compactButtonWidth = ((width - minPadding * 2f - slotGap * 2f) / 3f).coerceAtLeast(0f)
    val minimumGroupWidth = minimumButtonWidth * 3f + slotGap * 2f
    val buttonWidth = when {
        fitsFixed -> fixedButtonWidth
        compactButtonWidth >= minimumButtonWidth -> compactButtonWidth
        width >= minimumGroupWidth -> minimumButtonWidth
        else -> (width - minPadding * 2f - slotGap * 2f).coerceAtLeast(0f) / 3f
    }
    val horizontalPadding = if (fitsFixed) {
        (width - reserved) / 2f
    } else {
        ((width - buttonWidth * 3f - slotGap * 2f) / 2f).coerceAtLeast(0f)
    }
    if (horizontal) {
        val icon = (height * .36f).coerceIn(14f, 18f)
        val font = (height * .27f).coerceIn(10f, 13f)
        val labelHeight = font * 1.2f
        val gap = (height * .08f).coerceIn(2f, 5f)
        return DockPresentation(
            layout = DockPresentationLayout.HORIZONTAL,
            touchHeightDp = maxOf(48f, height),
            contentHeightDp = (maxOf(icon, labelHeight) + gap).coerceAtMost(height),
            buttonWidthDp = buttonWidth,
            slotGapDp = slotGap,
            iconSizeDp = icon,
            labelHeightDp = labelHeight,
            labelFontSizeSp = font,
            gapDp = gap,
            verticalPaddingDp = 0f,
            horizontalPaddingDp = horizontalPadding,
        )
    }
    val icon = (height * .23f).coerceIn(18f, 23f)
    val font = (height * .13f).coerceIn(11f, 14f)
    val labelHeight = font * 1.2f
    val gap = (height * .025f).coerceIn(1f, 3f)
    val padding = ((height - icon - labelHeight - gap) / 2f).coerceAtLeast(0f)
    return DockPresentation(
        layout = DockPresentationLayout.VERTICAL,
        touchHeightDp = maxOf(48f, height),
        contentHeightDp = icon + labelHeight + gap + padding * 2f,
        buttonWidthDp = buttonWidth,
        slotGapDp = slotGap,
        iconSizeDp = icon,
        labelHeightDp = labelHeight,
        labelFontSizeSp = font,
        gapDp = gap,
        verticalPaddingDp = padding,
        horizontalPaddingDp = horizontalPadding,
    )
}

/**
 * Keeps the shared dock's hit surface and its label row usable on short landscape windows.
 * The caller's measured dock remains the visual anchor; expansion is centered so switching
 * between the immersive and component pages cannot move the dock's optical center.
 */
internal fun resolveDockRenderRect(rect: HomeRect): HomeRect {
    val minimumHeight = 48f
    if (rect.h >= minimumHeight) return rect
    val extra = minimumHeight - rect.h
    return rect.copy(y = rect.y - extra / 2f, h = minimumHeight)
}

@Composable
private fun DockButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    colors: KireiColors,
    presentation: DockPresentation,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (selected) colors.accent else colors.secondary, tween(160), label = "dockTint")
    PressScaleBox(
        Modifier.width(presentation.buttonWidthDp.dp)
            .heightIn(min = presentation.touchHeightDp.dp),
        RoundedCornerShape(18.dp), onClick, pressedScale = .92f,
    ) {
        if (presentation.layout == DockPresentationLayout.HORIZONTAL) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, label, tint = tint, modifier = Modifier.size(presentation.iconSizeDp.dp))
                Text(
                    label,
                    color = tint,
                    fontSize = presentation.labelFontSizeSp.sp,
                    lineHeight = presentation.labelHeightDp.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = presentation.gapDp.dp),
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(vertical = presentation.verticalPaddingDp.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, label, tint = tint, modifier = Modifier.size(presentation.iconSizeDp.dp))
                Text(
                    label,
                    color = tint,
                    fontSize = presentation.labelFontSizeSp.sp,
                    lineHeight = presentation.labelHeightDp.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Notice(message: String, colors: KireiColors, haze: HazeState?, onDismiss: () -> Unit) {
    var visible by remember(message) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        visible = false
        scope.launch { delay(150); onDismiss() }
    }
    LaunchedEffect(message) {
        visible = true
        delay(2_200)
        close()
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it }
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
            GlassSurface(
                colors, haze, Modifier.fillMaxWidth(), RoundedCornerShape(20.dp),
                KireiGlassRole.NOTICE, close
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    Text(message, color = colors.text, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Icon(Icons.Rounded.Close, "关闭", tint = colors.secondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun UndoRemovalNotice(
    title: String,
    colors: KireiColors,
    haze: HazeState?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    var visible by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title) {
        visible = true
        delay(5_000)
        visible = false
        delay(150)
        onDismiss()
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it }
    ) {
        Box(
            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 92.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            GlassSurface(colors, haze, Modifier.fillMaxWidth().widthIn(max = 520.dp), RoundedCornerShape(20.dp), KireiGlassRole.NOTICE) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已移出 $title", color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        visible = false
                        onUndo()
                    }) { Text("撤销", color = colors.accent) }
                }
            }
        }
    }
}

internal enum class KireiGlassRole { CONTENT, DOCK, ICON, NOTICE, ALERT }

@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun GlassSurface(
    colors: KireiColors,
    haze: HazeState?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    role: KireiGlassRole = KireiGlassRole.CONTENT,
    onClick: (() -> Unit)? = null,
    reducedMotion: Boolean = false,
    containerColor: Color? = null,
    tint: Color? = null,
    hazeInputScale: HazeInputScale? = null,
    opaqueFill: Boolean = false,
    content: @Composable () -> Unit
) {
    val base = containerColor ?: when (role) {
        KireiGlassRole.CONTENT -> colors.surface
        KireiGlassRole.DOCK -> if (colors.isDark) Color(0xE62C2C2E) else Color(0xF2FFFFFF)
        KireiGlassRole.ICON -> if (colors.isDark) Color(0xF02C2C2E) else Color(0xFFFDFDFF)
        KireiGlassRole.NOTICE -> if (colors.isDark) Color(0xF02C2C2E) else Color(0xFFF5F2FF)
        KireiGlassRole.ALERT -> if (colors.isDark) Color(0xF51C1C1E) else Color(0xFFFFFEFF)
    }
    val solidBase = base.copy(alpha = 1f)
    val hazeBackgroundColor = containerColor?.takeUnless { it.alpha == 0f } ?: colors.background
    val blur = if (role == KireiGlassRole.CONTENT) 0.dp else if (role == KireiGlassRole.DOCK) 28.dp else 24.dp
    val border = if (colors.isDark) Color.White.copy(alpha = .22f) else colors.outline
    val elevation = when (role) {
        KireiGlassRole.CONTENT -> 0.dp
        KireiGlassRole.ICON -> 4.dp
        KireiGlassRole.NOTICE -> 7.dp
        KireiGlassRole.ALERT -> 10.dp
        KireiGlassRole.DOCK -> if (colors.isDark) 12.dp else 0.dp
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (reducedMotion) {
        1f
    } else {
        animateFloatAsState(if (pressed) .97f else 1f, AppleMotion.Spring.Press, label = "glassPress").value
    }
    Box(
        modifier.shadow(elevation, shape, clip = false)
            .then(if (onClick != null) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier)
            .clip(shape)
            .then(
                if (opaqueFill) {
                    Modifier.background(solidBase)
                } else if (haze != null && role != KireiGlassRole.CONTENT && (colors.isDark || role == KireiGlassRole.DOCK)) Modifier.hazeEffect(haze) {
                    blurRadius = blur
                    inputScale = hazeInputScale ?: HazeInputScale.Auto
                    noiseFactor = 0f
                    backgroundColor = hazeBackgroundColor
                    tints = listOf(dev.chrisbanes.haze.HazeTint(tint ?: base))
                } else Modifier.background(base)
            )
            .border(.6.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
    ) { content() }
}

@Composable
private fun PressScaleBox(
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    pressedScale: Float = .97f,
    enabled: Boolean = true,
    exposeClickSemantics: Boolean = true,
    onPressedChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressedChanged(pressed) }
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, AppleMotion.Spring.Press, label = "pressScale")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .then(if (enabled) Modifier else Modifier.semantics { disabled() })
            .then(if (exposeClickSemantics) Modifier else Modifier.clearAndSetSemantics { })
    ) { content() }
}

private fun resolveMotionMode(context: Context, requested: MotionMode): MotionMode {
    val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return MotionModePolicy.resolve(
        requested,
        MotionDecisionInputs(
            animatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
            lowRamDevice = activity.isLowRamDevice,
            memoryClassMb = activity.memoryClass,
            powerSaveMode = power.isPowerSaveMode
        )
    )
}

private object CoverBitmapCache : LruCache<String, Bitmap>(Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.THUMBNAIL)) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}

private object LibraryCardCoverBitmapCache : LruCache<String, Bitmap>(Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.LIBRARY_CARD)) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}

private object HeroCoverBitmapCache : LruCache<String, Bitmap>(Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.HERO)) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}

private object BackdropCoverBitmapCache : LruCache<String, Bitmap>(Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.IMMERSIVE_BACKDROP)) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}

private val CoverDecodeSemaphore = Semaphore(CoverImagePolicy.MAX_PARALLEL_DECODES)

private val CoverLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val CoverLoads = CoverLoadCoordinator<CoverImageRequest, Bitmap>(
    scope = CoverLoadScope,
    readCache = ::cachedCover,
    writeCache = ::putCachedCover,
)

private val CoverPreviewClient = okhttp3.OkHttpClient.Builder()
    .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    .build()

@Composable
internal fun rememberCover(
    uri: String?,
    role: CoverImageRole = CoverImageRole.THUMBNAIL,
    componentWidthPx: Int = CoverImagePolicy.TARGET_WIDTH_PX,
    componentHeightPx: Int = CoverImagePolicy.TARGET_HEIGHT_PX,
): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val request = remember(uri, role, componentWidthPx, componentHeightPx) {
        uri?.takeIf { it.isNotBlank() }?.let {
            CoverImagePolicy.request(it, role, componentWidthPx, componentHeightPx)
        }
    }
    return produceState<ImageBitmap?>(null, request?.cacheKey) {
        value = withContext(Dispatchers.IO) {
            if (request == null) return@withContext null
            val decoded = CoverLoads.load(request) {
                CoverDecodeSemaphore.withPermit { decodeSampledCover(context, request) }
            } ?: return@withContext null
            val imageBitmap = decoded.asImageBitmap()
            imageBitmap
        }
    }.value
}

internal fun coverCacheKey(
    uri: String?,
    role: CoverImageRole,
    componentWidthPx: Int,
    componentHeightPx: Int,
): String? = uri?.takeIf { it.isNotBlank() }?.let {
    CoverImagePolicy.request(it, role, componentWidthPx, componentHeightPx).cacheKey
}

@Composable
private fun HeroCoverPrefetch(
    pages: List<Game>,
    currentPage: Int,
    componentWidthPx: Int,
    componentHeightPx: Int,
) {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(pages, currentPage, componentWidthPx, componentHeightPx) {
        withContext(Dispatchers.IO) {
            SpotlightCarouselPolicy.prefetchIndexes(currentPage, pages.size).drop(1).forEach { index ->
                val source = pages[index].coverUri ?: return@forEach
                val request = CoverImagePolicy.request(
                    uri = source,
                    role = CoverImageRole.HERO,
                    componentWidthPx = componentWidthPx,
                    componentHeightPx = componentHeightPx,
                )
                CoverLoads.load(request) {
                    CoverDecodeSemaphore.withPermit { decodeSampledCover(context, request) }
                }
            }
        }
    }
}

private fun cachedCover(request: CoverImageRequest): Bitmap? = when (request.role) {
    CoverImageRole.THUMBNAIL -> CoverBitmapCache.get(request.cacheKey)
    CoverImageRole.LIBRARY_CARD -> LibraryCardCoverBitmapCache.get(request.cacheKey)
    CoverImageRole.HERO -> HeroCoverBitmapCache.get(request.cacheKey)
    CoverImageRole.IMMERSIVE_BACKDROP -> BackdropCoverBitmapCache.get(request.cacheKey)
}

private fun putCachedCover(request: CoverImageRequest, bitmap: Bitmap) {
    when (request.role) {
        CoverImageRole.THUMBNAIL -> CoverBitmapCache.put(request.cacheKey, bitmap)
        CoverImageRole.LIBRARY_CARD -> LibraryCardCoverBitmapCache.put(request.cacheKey, bitmap)
        CoverImageRole.HERO -> HeroCoverBitmapCache.put(request.cacheKey, bitmap)
        CoverImageRole.IMMERSIVE_BACKDROP -> BackdropCoverBitmapCache.put(request.cacheKey, bitmap)
    }
}

internal fun requiresContentResolverStream(scheme: String?): Boolean =
    scheme == "content" || scheme == "android.resource"

private fun constrainDecodedCover(bitmap: Bitmap?, request: CoverImageRequest): Bitmap? {
    if (bitmap == null || request.role != CoverImageRole.LIBRARY_CARD) return bitmap
    val size = CoverImagePolicy.libraryCardScaledSize(
        decodedWidthPx = bitmap.width,
        decodedHeightPx = bitmap.height,
        targetWidthPx = request.targetWidthPx,
        targetHeightPx = request.targetHeightPx,
    ) ?: return bitmap
    if (size.widthPx == bitmap.width && size.heightPx == bitmap.height) return bitmap
    val scaled = Bitmap.createScaledBitmap(bitmap, size.widthPx, size.heightPx, true)
    if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
    return scaled
}

private suspend fun decodeSampledCover(context: Context, request: CoverImageRequest): Bitmap? {
    return try {
    currentCoroutineContext().ensureActive()
    val uri = android.net.Uri.parse(request.uri)
    if (uri.scheme == "http" || uri.scheme == "https") {
        return decodeNetworkCover(context, request)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (requiresContentResolverStream(uri.scheme)) {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    } else {
        BitmapFactory.decodeFile(uri.path ?: request.uri, bounds)
    }
    currentCoroutineContext().ensureActive()
    val sample = CoverImagePolicy.sampleSize(bounds.outWidth, bounds.outHeight, request) ?: return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = if (requiresContentResolverStream(uri.scheme)) {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } else {
        BitmapFactory.decodeFile(uri.path ?: request.uri, options)
    }
    currentCoroutineContext().ensureActive()
    constrainDecodedCover(bitmap, request)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

private suspend fun decodeNetworkCover(context: Context, coverRequest: CoverImageRequest): Bitmap? {
    val temporary = downloadCoverPreview(
        client = CoverPreviewClient,
        url = coverRequest.uri,
        directory = File(context.cacheDir, "cover-previews"),
        maxBytes = CoverImagePolicy.MAX_NETWORK_BYTES,
    ) ?: return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temporary.path, bounds)
        currentCoroutineContext().ensureActive()
        val sample = CoverImagePolicy.sampleSize(bounds.outWidth, bounds.outHeight, coverRequest) ?: return null
        val bitmap = BitmapFactory.decodeFile(temporary.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
        currentCoroutineContext().ensureActive()
        constrainDecodedCover(bitmap, coverRequest)
    } finally {
        temporary.delete()
    }
}
