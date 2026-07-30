package com.kireibox.ui

import android.content.Context
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.LruCache
import androidx.compose.animation.Crossfade
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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kireibox.krkr.KrkrGameSettings
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
import com.yuki.yukihub.settings.DashboardStyles
import com.yuki.yukihub.settings.DashboardWidgetSize
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.SpotlightSelector
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private val spotlightProcessSeed = System.nanoTime()

data class KireiActions(
    val chooseDirectory: () -> Unit,
    val chooseLaunchFile: () -> Unit,
    val chooseAndroidApp: () -> Unit,
    val launchGame: (Game) -> Unit,
    val launchKrkrNative: () -> Unit,
    val checkPermissions: () -> Unit,
    val repairPermission: (String?) -> Unit,
    val copyLogs: () -> Unit,
    val exportLogs: () -> Unit,
    val setDarkMode: (Boolean) -> Unit,
    val showAbout: () -> Unit
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

private val LocalResolvedMotionMode = staticCompositionLocalOf { MotionMode.REDUCED }

@Composable
fun KireiBoxApp(
    viewModel: KireiViewModel,
    actions: KireiActions,
    prompt: KireiPrompt? = null,
    onPromptDismiss: () -> Unit = {},
    fileBrowserRequest: BuiltinFileBrowserRequest? = null,
    onFileBrowserDismiss: () -> Unit = {},
    onFileBrowserUseSaf: () -> Unit = {},
    onFileBrowserSelected: (Uri) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val settings = state.settings
    val dark = settings?.darkMode ?: true
    AppleDesignTheme(darkTheme = dark) {
        val context = LocalContext.current
        val resolvedMotion = remember(settings?.motionMode, context) {
            resolveMotionMode(context, settings?.motionMode ?: MotionMode.AUTO)
        }
        val reducedMotion = resolvedMotion == MotionMode.REDUCED
        CompositionLocalProvider(LocalResolvedMotionMode provides resolvedMotion) {
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
        var showLibraryOverview by remember { mutableStateOf(false) }
        var libraryOverviewSort by remember { mutableStateOf(LibraryOverviewSort.RECENT) }
        val uiScope = rememberCoroutineScope()
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            val haze = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings?.reducedTransparency != true) remember { HazeState() } else null
            val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val dockContentPadding = navigationBottom + 106.dp
            Box(Modifier.fillMaxSize()) {
                val editorGame = state.krkrEditorGame
                val coverSearch = state.coverSearch
                val xp3Picker = state.xp3Picker
                val androidAppPicker = state.androidAppPicker
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
                    editorGame != null -> Box(
                        Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze) else Modifier)
                    ) { KrkrGameSettingsPage(
                        game = editorGame,
                        state = state,
                        colors = colors,
                        haze = haze,
                        viewModel = viewModel
                    ) }
                    coverSearch != null -> Box(
                        Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze) else Modifier)
                    ) { CoverPickerPage(
                        search = coverSearch,
                        colors = colors,
                        haze = haze,
                        viewModel = viewModel
                    ) }
                    homeEditorProfile != null -> Box(
                        Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze) else Modifier)
                    ) {
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
                        Box(
                            Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze) else Modifier)
                        ) {
                            Crossfade(
                                targetState = state.tab,
                                animationSpec = tween(if (reducedMotion) 80 else 190),
                                label = "mainNavigation"
                            ) { tab ->
                                when (tab) {
                                    KireiTab.HOME -> HomePage(
                                        state, colors, haze, viewModel, actions, dockContentPadding,
                                        onOpenOverview = { showLibraryOverview = true },
                                        onEditDashboard = { homeEditorProfile = it }
                                    )
                                    KireiTab.GAMES -> LibraryPage(
                                        state, colors, haze, viewModel, actions, removingGameId, dockContentPadding
                                    ) { removeGame = it }
                                    KireiTab.SETTINGS -> SettingsPage(
                                        state, colors, haze, viewModel, actions, dockContentPadding
                                    )
                                }
                            }
                        }
                        GlassDock(
                            tab = state.tab,
                            colors = colors,
                            haze = haze,
                            viewModel = viewModel,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
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
                KireiPromptHost(
                    prompt = prompt,
                    colors = colors,
                    haze = haze,
                    reducedMotion = reducedMotion,
                    onPromptDismiss = onPromptDismiss
                )
            }
        }
        }
    }
}

private data class KireiColors(
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
    bottomContentPadding: Dp,
    onOpenOverview: () -> Unit,
    onEditDashboard: (DashboardProfile) -> Unit
) {
    val recent = LibraryOverviewPolicy.sorted(state.games, LibraryOverviewSort.RECENT)
        .filter { it.lastPlayedAt > 0L }
    var menuExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = menuExpanded) { menuExpanded = false }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = ResponsiveLayoutPolicy.useTwoPane(maxWidth.value, maxHeight.value)
        val profile = if (twoPane) DashboardProfile.WIDE else DashboardProfile.COMPACT
        val storedLayout = if (profile == DashboardProfile.WIDE) state.dashboardWide else state.dashboardCompact
        val visibleGameIds = state.games.mapTo(HashSet()) { it.id }
        val effectiveLayout = remember(storedLayout, visibleGameIds) {
            storedLayout.copy(widgets = storedLayout.widgets.filter { spec ->
                spec.type != DashboardWidgetType.GAME_SPOTLIGHT || spec.gameId == null || spec.gameId in visibleGameIds
            })
        }
        val horizontalPadding = if (twoPane) 28.dp else 20.dp
        val density = LocalDensity.current
        var headerHeightPx by remember(profile, density) {
            mutableIntStateOf(with(density) { 64.dp.roundToPx() })
        }
        val headerHeight = with(density) { headerHeightPx.toDp() }
        val headerGap = if (twoPane) 17.dp else 14.dp
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 1180.dp).fillMaxSize()
                .statusBarsPadding().padding(horizontal = horizontalPadding, vertical = 16.dp)
        ) {
            Spacer(Modifier.height(headerHeight + headerGap))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                if (effectiveLayout.widgets.isEmpty()) {
                    GlassSurface(colors, haze, Modifier.fillMaxWidth().height(180.dp), RoundedCornerShape(28.dp)) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text("首页还没有组件", color = colors.text, style = AppleTypography.headlineMedium)
                            Text("从右上角收纳菜单进入编辑模式并添加组件。", color = colors.secondary, style = AppleTypography.bodyMedium)
                        }
                    }
                } else {
                    DashboardGrid(effectiveLayout, profile, Modifier.fillMaxWidth()) { spec ->
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
                            onChooseDirectory = actions.chooseDirectory
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
        Row(
            Modifier.align(Alignment.TopCenter).widthIn(max = 1180.dp).fillMaxWidth()
                .statusBarsPadding().padding(horizontal = horizontalPadding, vertical = 16.dp)
                .onSizeChanged { headerHeightPx = it.height }
                .zIndex(2f),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text("KireiBox", color = colors.text, style = AppleTypography.displayLarge)
                Text("继续你的故事", color = colors.secondary, style = AppleTypography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
            HomeControlMenu(
                expanded = menuExpanded,
                colors = colors,
                haze = haze,
                onToggle = { menuExpanded = !menuExpanded },
                onEdit = {
                    menuExpanded = false
                    onEditDashboard(profile)
                },
                onTheme = {
                    menuExpanded = false
                    actions.setDarkMode(!colors.isDark)
                }
            )
        }
    }
}

@Composable
private fun HomeControlMenu(
    expanded: Boolean,
    colors: KireiColors,
    haze: HazeState?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTheme: () -> Unit
) {
    val polished = LocalResolvedMotionMode.current == MotionMode.POLISHED
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(if (polished) 180 else 90),
        label = "homeHubRotation"
    )
    Box(Modifier.size(150.dp, 48.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (polished) 150 else 90)) +
                slideInHorizontally(tween(if (polished) 180 else 90)) { it * 2 },
            exit = fadeOut(tween(if (polished) 110 else 90)) +
                slideOutHorizontally(tween(if (polished) 130 else 90)) { it * 2 },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            HomeMenuAction(Icons.Rounded.Edit, "编辑首页", colors, haze, onEdit)
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
        GlassSurface(
            colors,
            haze,
            Modifier.align(Alignment.CenterEnd).size(48.dp),
            CircleShape,
            KireiGlassRole.ICON,
            onToggle
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    if (expanded) "收起首页操作" else "展开首页操作",
                    tint = colors.accent,
                    modifier = Modifier.size(27.dp).graphicsLayer { rotationZ = rotation }
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
    modifier: Modifier = Modifier,
    content: @Composable (DashboardWidgetSpec) -> Unit
) {
    val placements = remember(layout, profile) { DashboardLayouts.pack(layout, profile) }
    val horizontalGap = 10.dp
    val verticalStep = DashboardLayouts.HEIGHT_STEP_DP.dp
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
    onChooseDirectory: () -> Unit
) {
    when (spec.type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> {
            val game = SpotlightSelector.select(
                spec = spec,
                games = games,
                sessionSeed = spotlightProcessSeed,
                epochDay = LocalDate.now().toEpochDay(),
            )
            if (game != null) {
                HomeSpotlight(game, colors, Modifier.fillMaxSize(), spec.styleKey, { onOpenGame(game) }) { onLaunchGame(game) }
            } else {
                HomeEmptySpotlight(colors, onChooseDirectory, Modifier.fillMaxSize())
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
        DashboardWidgetType.PLAY_TREND -> DashboardTrendWidget(playTrend, spec.styleKey, colors, haze)
    }
}

@Composable
private fun HomeSpotlight(
    game: Game,
    colors: KireiColors,
    modifier: Modifier,
    styleKey: String,
    onOpen: () -> Unit,
    onLaunch: () -> Unit
) {
    if (styleKey == DashboardStyles.SPOTLIGHT_HORIZONTAL) {
        PressScaleBox(modifier, RoundedCornerShape(24.dp), onOpen, pressedScale = .985f) {
            Row(
                Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(24.dp))
                    .border(.7.dp, colors.outline, RoundedCornerShape(24.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameCoverArtwork(game, Modifier.fillMaxHeight().widthIn(min = 72.dp, max = 150.dp).aspectRatio(.9f), ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(game.title.orEmpty(), color = colors.text, style = AppleTypography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(game.engine?.displayName.orEmpty(), color = colors.secondary, style = AppleTypography.labelLarge)
                }
                PlayTriangleButton(colors, "启动 ${game.title.orEmpty()}", 44.dp, onLaunch)
            }
        }
        return
    }
    PressScaleBox(modifier, RoundedCornerShape(32.dp), onOpen, pressedScale = .985f) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
            val narrow = maxWidth < 240.dp
            val compactHeight = maxHeight < 216.dp
            val inset = if (narrow) 16.dp else if (compactHeight) 20.dp else 28.dp
            val playSize = if (narrow) 48.dp else if (compactHeight) 54.dp else 62.dp
            GameCoverArtwork(
                game,
                Modifier.fillMaxSize(),
                if (styleKey == DashboardStyles.SPOTLIGHT_POSTER) ContentScale.Fit else ContentScale.Crop
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
    onChooseDirectory: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(310.dp)
) {
    GlassSurface(colors, null, modifier, RoundedCornerShape(32.dp)) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
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
    }
}

@Composable
private fun GameCoverArtwork(
    game: Game,
    modifier: Modifier,
    contentScale: ContentScale,
    cornerRadius: Dp = 12.dp
) {
    val bitmap = rememberCover(game.coverUri)
    Box(modifier.clip(RoundedCornerShape(cornerRadius))) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(bitmap, null, Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            EngineDefaultCover(game.engine, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun EngineDefaultCover(engine: EngineType?, modifier: Modifier = Modifier) {
    val (start, end) = when (engine) {
        EngineType.KIRIKIRI -> Color(0xFF716BD8) to Color(0xFF3D386F)
        EngineType.ONS -> Color(0xFF5E83D6) to Color(0xFF30496F)
        EngineType.TYRANO -> Color(0xFFC8845D) to Color(0xFF704735)
        EngineType.ARTEMIS -> Color(0xFF8B67C9) to Color(0xFF493568)
        else -> Color(0xFF636366) to Color(0xFF2C2C2E)
    }
    val icon = when (engine) {
        EngineType.KIRIKIRI -> Icons.Rounded.AutoStories
        EngineType.ONS -> Icons.Rounded.Description
        EngineType.TYRANO -> Icons.Rounded.Terminal
        else -> Icons.Rounded.Layers
    }
    Box(modifier.background(Brush.linearGradient(listOf(start, end)))) {
        Box(Modifier.size(96.dp).align(Alignment.TopEnd).background(Color.White.copy(alpha = .07f), CircleShape))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Icon(icon, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(26.dp))
            Text(engine?.displayName ?: "游戏", color = Color.White, style = AppleTypography.labelLarge, modifier = Modifier.padding(top = 5.dp))
            Text("KireiBox", color = Color.White.copy(alpha = .62f), style = AppleTypography.labelSmall)
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
                listOf("游戏" to "$gameCount", "收藏" to "$favoriteCount", "时长" to formatPlayTime(playTime)).forEach { (label, value) ->
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
                Text(formatPlayTime(playTime), color = colors.accent, style = AppleTypography.displayLarge, maxLines = 1)
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
                        if (compact) formatPlayTime(playTime).replace(" ", "") else formatPlayTime(playTime),
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
                Text(formatPlayTime(playTime), color = colors.text, style = AppleTypography.headlineMedium, maxLines = 1)
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
                Text(formatPlayTime(playTime), color = colors.text, style = AppleTypography.headlineSmall, maxLines = 1)
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
                TextButton(onClick = onOpenLibrary, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("查看全部", color = colors.accent, style = AppleTypography.labelLarge, maxLines = 1)
                }
            }
            if (games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("还没有游玩记录", color = colors.secondary, style = AppleTypography.bodySmall)
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val cardWidth = when {
                        showThreeAcross -> (maxWidth - 24.dp) / 3
                        compact -> maxWidth * .88f
                        else -> maxWidth * .82f
                    }
                    LazyRow(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = if (showThreeAcross) 0.dp else 24.dp)
                    ) {
                        items(games.take(3), key = { it.id }) { game ->
                        PressScaleBox(
                            Modifier.width(cardWidth).fillMaxHeight(),
                            RoundedCornerShape(18.dp),
                            { onOpenGame(game) },
                            pressedScale = .98f
                        ) {
                            if (styleKey == DashboardStyles.RECENT_COVERS) {
                                Box(
                                    Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(18.dp))
                                        .border(.7.dp, colors.outline, RoundedCornerShape(18.dp))
                                ) {
                                    GameCoverArtwork(game, Modifier.fillMaxSize(), ContentScale.Crop, 18.dp)
                                    Box(
                                        Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))
                                        )
                                    )
                                    Text(
                                        game.title.orEmpty(),
                                        color = Color.White,
                                        style = AppleTypography.bodyLargeEmphasis,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                                    )
                                }
                            } else {
                                Row(
                                    Modifier.fillMaxSize().background(colors.surface, RoundedCornerShape(18.dp))
                                        .border(.7.dp, colors.outline, RoundedCornerShape(18.dp)).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GameCoverArtwork(game, Modifier.size(if (compact) 42.dp else 52.dp, if (compact) 52.dp else 66.dp), ContentScale.Crop)
                                    Column(Modifier.weight(1f).padding(horizontal = if (compact) 7.dp else 10.dp)) {
                                        Text(game.title.orEmpty(), color = colors.text, style = AppleTypography.bodyLargeEmphasis, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(game.engine?.displayName.orEmpty(), color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 1)
                                    }
                                    PlayTriangleButton(colors, "启动 ${game.title.orEmpty()}", if (compact) 36.dp else 40.dp) { onLaunchGame(game) }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    if (styleKey == DashboardStyles.RECENT_GLASS) {
        GlassSurface(colors, haze, Modifier.fillMaxSize(), RoundedCornerShape(22.dp), content = content)
    } else {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) { content() }
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
private fun DashboardTrendWidget(
    points: List<PlayTrendPoint>,
    styleKey: String,
    colors: KireiColors,
    haze: HazeState?
) {
    val launchMetric = styleKey == DashboardStyles.TREND_LAUNCH_7_DAY ||
        styleKey == DashboardStyles.TREND_LAUNCH_30_DAY
    val days = if (
        styleKey == DashboardStyles.TREND_30_DAY ||
        styleKey == DashboardStyles.TREND_LAUNCH_30_DAY
    ) 30 else 7
    val visible = points.takeLast(days)
    val total = visible.sumOf { it.duration }
    val launchTotal = visible.sumOf { it.distinctGamesLaunched }
    val todayLaunches = visible.lastOrNull()?.distinctGamesLaunched ?: 0
    val dateLabel = remember { java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault()) }
    GlassSurface(colors, haze, Modifier.fillMaxSize(), RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (launchMetric) "今天打开 $todayLaunches 款" else "游玩 ${formatPlayTime(total)}",
                        color = colors.text,
                        style = AppleTypography.bodyLargeEmphasis
                    )
                    Text(
                        if (launchMetric) {
                            "最近 $days 天 · $launchTotal 款次（日内去重）"
                        } else {
                            "最近 $days 天 · 仅统计引擎前台时长"
                        },
                        color = colors.secondary,
                        style = AppleTypography.labelLarge
                    )
                }
                Icon(Icons.Rounded.ShowChart, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 7.dp)) {
                if (visible.isEmpty()) return@Canvas
                val values = visible.map {
                    if (launchMetric) it.distinctGamesLaunched.toFloat() else it.duration.toFloat()
                }
                val maximum = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                val chartHeight = size.height * .86f
                repeat(3) { guide ->
                    val y = size.height - chartHeight * guide / 2f
                    drawLine(
                        colors.outline.copy(alpha = .42f),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                if (days == 7) {
                    val slot = size.width / values.size.coerceAtLeast(1)
                    val barWidth = slot * .52f
                    values.forEachIndexed { index, value ->
                        val height = (value / maximum) * chartHeight
                        drawRoundRect(
                            color = if (index == values.lastIndex) colors.accent else colors.accent.copy(alpha = .42f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                slot * index + (slot - barWidth) / 2f,
                                size.height - height,
                            ),
                            size = androidx.compose.ui.geometry.Size(barWidth, height.coerceAtLeast(2.dp.toPx())),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                        )
                    }
                } else {
                    val step = if (visible.size <= 1) size.width else size.width / (visible.size - 1)
                    val line = Path()
                    values.forEachIndexed { index, value ->
                        val x = step * index
                        val y = size.height - (value / maximum) * chartHeight
                        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(area, colors.accent.copy(alpha = .14f))
                    drawPath(line, colors.accent, style = Stroke(width = 2.5.dp.toPx()))
                }
            }
            if (visible.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(visible.first(), visible[visible.lastIndex / 2], visible.last()).forEach { point ->
                        Text(
                            dateLabel.format(Date(point.dayStart)),
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
                    DashboardGrid(draft, profile, Modifier.fillMaxWidth().animateContentSize()) { spec ->
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
                    onSource = { source ->
                        draft = DashboardLayouts.setSpotlight(
                            draft,
                            profile,
                            instanceId,
                            source,
                            spec.spotlightStrategy,
                            spec.gameId,
                        )
                    },
                    onStrategy = { strategy ->
                        draft = DashboardLayouts.setSpotlight(
                            draft,
                            profile,
                            instanceId,
                            spec.spotlightSource,
                            strategy,
                            spec.gameId,
                        )
                    },
                    onGame = { game ->
                        draft = DashboardLayouts.setSpotlight(
                            draft,
                            profile,
                            instanceId,
                            SpotlightSource.FIXED,
                            spec.spotlightStrategy,
                            game.id,
                        )
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
        spec.spotlightSource == SpotlightSource.FIXED &&
        spec.gameId != null &&
        games.none { it.id == spec.gameId }
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
                onOpenOverview = {},
                onOpenLibrary = {},
                onOpenGame = {},
                onLaunchGame = {},
                onChooseDirectory = {}
            )
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = if (colors.isDark) .10f else .04f), RoundedCornerShape(24.dp)))
        }

        if (DashboardStyles.allowed(spec.type).size > 1) {
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
    val existingTypes = layout.widgets.mapTo(HashSet()) { it.type }
    val usedGames = layout.widgets.mapNotNullTo(HashSet()) { it.gameId }
    KireiActionSheet("添加首页组件", colors, onDismiss, expanded = true) {
        if (layout.widgets.size >= DashboardLayouts.MAX_WIDGETS) {
            Text("最多可添加 ${DashboardLayouts.MAX_WIDGETS} 个组件。", color = colors.secondary, modifier = Modifier.padding(24.dp))
            return@KireiActionSheet
        }
        DashboardWidgetType.entries.filter { it != DashboardWidgetType.GAME_SPOTLIGHT && it !in existingTypes }.forEach { type ->
            SheetActionRow(dashboardWidgetIcon(type), dashboardWidgetLabel(type), dashboardWidgetDescription(type), colors) {
                onAdd(type, null)
            }
        }
        if (layout.widgets.none {
                it.type == DashboardWidgetType.GAME_SPOTLIGHT &&
                    it.spotlightSource != SpotlightSource.FIXED
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
    onSource: (SpotlightSource) -> Unit,
    onStrategy: (SpotlightStrategy) -> Unit,
    onGame: (Game) -> Unit,
) {
    KireiActionSheet("设置头图", colors, onDismiss, expanded = true) {
        Text(
            "内容来源",
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
        listOf(
            SpotlightSource.RECENT to ("最近打开" to "优先显示最近实际启动过的游戏"),
            SpotlightSource.FAVORITES to ("收藏" to "只从收藏游戏中选择"),
            SpotlightSource.ALL to ("全部游戏" to "从当前游戏库中选择"),
        ).forEach { (source, labels) ->
            Row(
                Modifier.fillMaxWidth().clickable { onSource(source) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(labels.first, color = colors.text, style = AppleTypography.bodyLargeEmphasis)
                    Text(labels.second, color = colors.secondary, style = AppleTypography.bodySmall)
                }
                if (spec.spotlightSource == source) {
                    Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                }
            }
        }
        Text(
            "展示方式",
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
        listOf(
            SpotlightStrategy.FIRST to ("按顺序" to "最近打开按时间，其余来源使用稳定顺序"),
            SpotlightStrategy.RANDOM_EACH_LAUNCH to ("每次启动随机" to "本次打开 KireiBox 期间保持不变"),
            SpotlightStrategy.DAILY_RANDOM to ("每日随机" to "每天自动切换一次并保持稳定"),
        ).forEach { (strategy, labels) ->
            Row(
                Modifier.fillMaxWidth().clickable { onStrategy(strategy) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(labels.first, color = colors.text, style = AppleTypography.bodyLargeEmphasis)
                    Text(labels.second, color = colors.secondary, style = AppleTypography.bodySmall)
                }
                if (spec.spotlightStrategy == strategy) {
                    Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                }
            }
        }
        Text(
            "固定某个游戏",
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
        games.forEach { game ->
            Row(
                Modifier.fillMaxWidth().clickable { onGame(game) }
                    .padding(horizontal = 24.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameCoverArtwork(game, Modifier.size(38.dp, 50.dp), ContentScale.Crop, 9.dp)
                Text(
                    game.title.orEmpty(),
                    color = colors.text,
                    style = AppleTypography.bodyLargeEmphasis,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                if (spec.spotlightSource == SpotlightSource.FIXED && spec.gameId == game.id) {
                    Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                }
            }
        }
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LibraryPage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    removingGameId: Long?,
    bottomContentPadding: Dp,
    onRequestRemove: (Game) -> Unit
) {
    val polishedMotion = LocalResolvedMotionMode.current == MotionMode.POLISHED
    val reducedMotion = !polishedMotion
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<EngineType?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var showAddGame by remember { mutableStateOf(false) }
    BackHandler(enabled = editMode) { editMode = false }
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val revealHeightPx = with(density) { 72.dp.toPx() }
    val thresholdPx = with(density) { 44.dp.toPx() }
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val searchReveal = remember { SearchRevealController(scope) }
    LaunchedEffect(revealHeightPx) { searchReveal.configure(revealHeightPx) }
    LaunchedEffect(query, searchFocused) {
        if (query.isNotBlank() || searchFocused) searchReveal.show()
    }
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, query, searchFocused) {
        if (gridState.canScrollBackward && query.isBlank() && !searchFocused && searchReveal.offsetPx > 0f) {
            searchReveal.snapHidden()
        }
    }
    val nested = remember(gridState, query, searchFocused, reducedMotion, decay) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                val atTop = !gridState.canScrollBackward
                if (source == NestedScrollSource.UserInput && atTop && available.y > 0f) {
                    return androidx.compose.ui.geometry.Offset(0f, searchReveal.dragBy(available.y))
                }
                if (source == NestedScrollSource.UserInput && atTop && available.y < 0f &&
                    query.isBlank() && !searchFocused && searchReveal.offsetPx > 0f
                ) {
                    return androidx.compose.ui.geometry.Offset(0f, searchReveal.dragBy(available.y))
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!gridState.canScrollBackward && searchReveal.offsetPx > 0f) {
                    if (available.y < 0f && query.isBlank() && !searchFocused) {
                        val projected = kotlin.math.abs(decay.calculateTargetValue(0f, available.y))
                        val split = SearchRevealMath.splitUpwardFling(available.y, projected, searchReveal.offsetPx)
                        searchReveal.collapseForFling(available.y, reducedMotion)
                        return Velocity(0f, split.consumedVelocityY)
                    }
                    searchReveal.settle(thresholdPx, available.y, query.isNotBlank() || searchFocused)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val gridColumns = when (ResponsiveLayoutPolicy.libraryGrid(maxWidth.value)) {
        ResponsiveLayoutPolicy.LibraryGrid.PHONE_TWO_COLUMNS -> GridCells.Fixed(2)
        ResponsiveLayoutPolicy.LibraryGrid.MEDIUM_ADAPTIVE -> GridCells.Adaptive(160.dp)
        ResponsiveLayoutPolicy.LibraryGrid.WIDE_ADAPTIVE -> GridCells.Adaptive(168.dp)
    }
    Column(
        Modifier.align(Alignment.TopCenter).widthIn(max = 1200.dp).fillMaxSize()
            .statusBarsPadding().padding(horizontal = 18.dp)
            .nestedScroll(nested)
    ) {
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
                GlassIconButton(Icons.Rounded.Add, "添加游戏", colors, haze) { showAddGame = true }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(with(density) { searchReveal.offsetPx.toDp() })
                .clipToBounds().graphicsLayer { alpha = searchReveal.fraction }
        ) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(top = 12.dp).onFocusChanged { searchFocused = it.isFocused },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus(force = true)
                        query = ""
                        searchFocused = false
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
            listOf(EngineType.KIRIKIRI, EngineType.ONS, EngineType.TYRANO, EngineType.ARTEMIS, EngineType.ANDROID_APP).forEach { engine ->
                FilterChip(engine.displayName, filter == engine, colors) { filter = engine; editMode = false }
            }
        }
        LazyVerticalGrid(gridColumns, state = gridState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = bottomContentPadding), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            items(visible, key = { it.id }) { game ->
                GameCard(
                    game = game,
                    colors = colors,
                    modifier = if (polishedMotion) {
                        Modifier.animateItem(
                            fadeInSpec = tween(180),
                            placementSpec = spring(
                                dampingRatio = .82f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            fadeOutSpec = tween(160)
                        )
                    } else {
                        Modifier.animateItem(
                            fadeInSpec = tween(100),
                            placementSpec = tween(1),
                            fadeOutSpec = tween(100)
                        )
                    },
                    editMode = editMode,
                    removing = removingGameId == game.id,
                    reducedMotion = reducedMotion,
                    onOpen = { viewModel.selectedGame(game) },
                    onLongClick = { editMode = true },
                    onRemove = { onRequestRemove(game) }
                )
            }
        }
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
@OptIn(ExperimentalFoundationApi::class)
private fun GameCard(
    game: Game,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    editMode: Boolean,
    removing: Boolean,
    reducedMotion: Boolean,
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
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                rotationZ = rotation
                alpha = removeProgress
                scaleX = .92f + .08f * removeProgress
                scaleY = .92f + .08f * removeProgress
            }.combinedClickable(
                onClick = { if (!editMode) onOpen() },
                onLongClick = onLongClick
            )
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(20.dp))) {
                GameCoverArtwork(game, Modifier.fillMaxSize(), ContentScale.Crop, 20.dp)
                if (game.favorite) {
                    Surface(
                        color = colors.danger,
                        contentColor = Color.White,
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Favorite, "已收藏", Modifier.size(18.dp))
                        }
                    }
                }
            }
            Text(game.title.orEmpty(), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
            Text(game.engine?.displayName ?: "", color = colors.secondary, style = AppleTypography.labelLarge, modifier = Modifier.padding(top = 2.dp))
        }
        if (editMode) {
            Surface(
                onClick = onRemove,
                color = colors.danger,
                contentColor = Color.White,
                shape = CircleShape,
                shadowElevation = 5.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Remove, "移出游戏库", Modifier.size(20.dp)) }
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
    bottomContentPadding: Dp
) {
    if (state.settingsPage != KireiSettingsPage.ROOT) {
        SettingsSubPage(state, colors, haze, viewModel, actions, bottomContentPadding)
        return
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.align(Alignment.TopCenter).widthIn(max = 820.dp).fillMaxSize()
            .statusBarsPadding().padding(horizontal = 22.dp)
            .verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = bottomContentPadding)
    ) {
        Text("设置", color = colors.text, style = AppleTypography.displayLarge)
        SettingsSection("游戏库", colors) {
            SettingsRow(Icons.Rounded.Folder, "扫描目录", "${state.settings?.scanRoots?.size ?: 0} 个目录", colors) { viewModel.openSettingsPage(KireiSettingsPage.LIBRARY) }
            SettingsRow(
                Icons.Rounded.CollectionsBookmark,
                "封面与元数据",
                if (state.pendingCoverCount > 0) "待确认 ${state.pendingCoverCount} · YukiHub 来源与候选" else "YukiHub 来源与候选",
                colors
            ) { viewModel.openSettingsPage(KireiSettingsPage.COVERS) }
        }
        SettingsSection("引擎", colors) {
            SettingsRow(Icons.Rounded.Gamepad, "KRKR", "全局默认设置；单游戏设置在游戏卡片菜单中", colors) { viewModel.openSettingsPage(KireiSettingsPage.KRKR) }
        }
        SettingsSection("存储与权限", colors) {
            SettingsRow(Icons.Rounded.Folder, "检查权限", "SAF 目录和兼容权限", colors, actions.checkPermissions)
        }
        SettingsSection("界面与辅助", colors) {
            MotionModeRow(
                requested = state.settings?.motionMode ?: MotionMode.AUTO,
                resolved = LocalResolvedMotionMode.current,
                colors = colors,
                onChange = viewModel::setMotionMode
            )
            SwitchRow("减少透明度", state.settings?.reducedTransparency == true, colors, viewModel::setReducedTransparency)
            SwitchRow("增强对比度", state.settings?.increasedContrast == true, colors, viewModel::setContrast)
        }
        SettingsSection("日志与诊断", colors) {
            SwitchRow(
                "记录诊断日志",
                state.diagnosticLoggingEnabled,
                colors,
                viewModel::setDiagnosticLogging,
            )
            Text(
                "默认关闭。仅在排查问题时开启；导出和复制前会自动遮盖常见 Token。",
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingsRow(Icons.Rounded.Description, "导出日志", "生成文本文件并选择分享目标", colors, actions.exportLogs)
            SettingsRow(Icons.Rounded.Terminal, "复制日志", "复制设备信息与最近日志到剪贴板", colors, actions.copyLogs)
        }
        SettingsSection("关于", colors) {
            SettingsRow(Icons.Rounded.Update, "GitHub Releases", "更新通道预留 · 开发中", colors) {
                viewModel.showNotice("GitHub Releases 更新功能将在后续版本开放")
            }
            SettingsRow(Icons.Rounded.Info, "关于 KireiBox", "版本与技术栈报告", colors, actions.showAbout)
        }
        Spacer(Modifier.height(20.dp))
    }
    }
}

@Composable
private fun SettingsSubPage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    bottomContentPadding: Dp
) {
    val page = state.settingsPage
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.align(Alignment.TopCenter).widthIn(max = 820.dp).fillMaxSize()
            .statusBarsPadding().padding(horizontal = 22.dp)
            .verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = bottomContentPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.ArrowBack, "返回", colors, haze) { viewModel.openSettingsPage(KireiSettingsPage.ROOT) }
            Text(when (page) { KireiSettingsPage.KRKR -> "KRKR 设置"; KireiSettingsPage.COVERS -> "封面"; KireiSettingsPage.LIBRARY -> "游戏库"; KireiSettingsPage.PERMISSIONS -> "权限"; else -> "设置" }, color = colors.text, style = AppleTypography.headlineMedium, modifier = Modifier.padding(start = 14.dp))
        }
        Spacer(Modifier.height(22.dp))
        when (page) {
            KireiSettingsPage.KRKR -> KrkrSettings(state, colors, viewModel, actions)
            KireiSettingsPage.COVERS -> CoverSettings(state, colors, haze, viewModel)
            KireiSettingsPage.LIBRARY -> LibrarySettings(state, colors, viewModel, actions)
            KireiSettingsPage.PERMISSIONS -> PermissionSettings(state, colors, actions)
            else -> Unit
        }
        Spacer(Modifier.height(20.dp))
    }
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
        Text(
            "KRKR 存档固定保存在每个游戏自己的 savedata 文件夹中，例如 ATRI/savedata。启动前会检查目录写入权限，无法写入时会提示处理，不会改存到应用私有目录。",
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
private fun KireiActionSheet(
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
            Box(Modifier.fillMaxWidth().height(listHeight), contentAlignment = Alignment.Center) {
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
    var showTokenEditor by remember { mutableStateOf(false) }
    var token by remember(state.settings?.bangumiToken) { mutableStateOf(state.settings?.bangumiToken.orEmpty()) }
    SettingsSection("封面数据来源", colors) {
        CoverSourceChoiceRow("默认来源", source, colors) { viewModel.setMetadataSource(it) }
        Text(
            coverSourceDescription(source),
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SettingsRow(
            Icons.Rounded.CollectionsBookmark,
            "Bangumi Token",
            if (state.settings?.bangumiToken.isNullOrBlank()) "未设置" else "已设置",
            colors
        ) { showTokenEditor = true }
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
    if (showTokenEditor) {
        Dialog(onDismissRequest = { showTokenEditor = false }) {
            GlassSurface(colors, haze, Modifier.fillMaxWidth().padding(18.dp), RoundedCornerShape(26.dp), KireiGlassRole.ALERT) {
                Column(Modifier.padding(20.dp)) {
                    Text("Bangumi Token", color = colors.text, style = AppleTypography.headlineSmall)
                    Text("仅用于 Bangumi 官方接口。", color = colors.secondary, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
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
            "SAF 用于扫描、封面和目录管理；KRKR 原生内核直接读取 XP3，还需要单独检查所有文件访问权限。",
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
private fun CoverPickerPage(search: CoverSearchUi, colors: KireiColors, haze: HazeState?, viewModel: KireiViewModel) {
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
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, colors: KireiColors, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowColor by animateColorAsState(if (pressed) colors.surfaceRaised else Color.Transparent, tween(90), label = "settingsPress")
    Row(
        Modifier.fillMaxWidth().background(rowColor)
            .clickable(interactionSource = interaction, indication = null, enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).background(colors.accent.copy(alpha = .16f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(title, color = colors.text)
            Text(subtitle, color = colors.secondary, style = AppleTypography.bodySmall, maxLines = 2)
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondary)
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
                Row(
                    Modifier.fillMaxWidth().clickable {
                        expanded = false
                        onChange(option)
                    }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, color = colors.text, style = AppleTypography.bodyLargeEmphasis)
                        Text(
                            coverSourceDescription(option),
                            color = colors.secondary,
                            style = AppleTypography.bodySmall,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (value == option) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                }
            }
        }
    }
}

private fun coverSourceDescription(source: CoverSourceMode): String = when (source) {
    CoverSourceMode.VNDB -> "公开数据源 · 无需 Token；中文条目覆盖可能有限。"
    CoverSourceMode.BANGUMI -> "Bangumi 官方接口 · 需要在下方填写个人 Token。"
    CoverSourceMode.BANGUMI_MIRROR -> "公开镜像接口 · 无需个人 Token；可用性取决于镜像服务。"
    CoverSourceMode.YMGAL -> "月幕 Gal 公开服务 · 用户无需 Token；构建者需要配置 OAuth 客户端凭据。"
    CoverSourceMode.HIKARINAGI -> "Hikarinagi 公开服务 · 用户无需 Token；构建者需要配置 OAuth 客户端凭据。"
}

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
private fun GlassIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, colors: KireiColors, haze: HazeState?, onClick: () -> Unit) {
    GlassSurface(colors, haze, Modifier.size(48.dp), CircleShape, KireiGlassRole.ICON, onClick) {
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
    onClick: () -> Unit
) {
    PressScaleBox(Modifier.size(size), CircleShape, onClick, pressedScale = .9f) {
        Box(
            Modifier.fillMaxSize().background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                description,
                tint = colors.onAccent,
                modifier = Modifier.size(size * .56f).graphicsLayer { translationX = size.value * .035f }
            )
        }
    }
}

@Composable
private fun GlassDock(
    tab: KireiTab,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        GlassSurface(colors, haze, Modifier.widthIn(max = 520.dp).fillMaxWidth().height(66.dp), RoundedCornerShape(32.dp), KireiGlassRole.DOCK) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                DockButton("首页", Icons.Rounded.Home, tab == KireiTab.HOME, colors) { viewModel.selectTab(KireiTab.HOME) }
                DockButton("游戏", Icons.Rounded.Gamepad, tab == KireiTab.GAMES, colors) { viewModel.selectTab(KireiTab.GAMES) }
                DockButton("设置", Icons.Rounded.Settings, tab == KireiTab.SETTINGS, colors) { viewModel.selectTab(KireiTab.SETTINGS) }
            }
        }
    }
}

@Composable
private fun DockButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, colors: KireiColors, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) colors.accent else colors.secondary, tween(160), label = "dockTint")
    PressScaleBox(Modifier.width(82.dp), RoundedCornerShape(18.dp), onClick, pressedScale = .92f) {
        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(23.dp))
            Text(label, color = tint, style = AppleTypography.labelSmall)
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

private enum class KireiGlassRole { CONTENT, DOCK, ICON, NOTICE, ALERT }

@Composable
private fun GlassSurface(
    colors: KireiColors,
    haze: HazeState?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    role: KireiGlassRole = KireiGlassRole.CONTENT,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = when (role) {
        KireiGlassRole.CONTENT -> colors.surface
        KireiGlassRole.DOCK -> if (colors.isDark) Color(0xE62C2C2E) else Color(0xF2FFFFFF)
        KireiGlassRole.ICON -> if (colors.isDark) Color(0xF02C2C2E) else Color(0xFFFDFDFF)
        KireiGlassRole.NOTICE -> if (colors.isDark) Color(0xF02C2C2E) else Color(0xFFF5F2FF)
        KireiGlassRole.ALERT -> if (colors.isDark) Color(0xF51C1C1E) else Color(0xFFFFFEFF)
    }
    val blur = if (role == KireiGlassRole.CONTENT) 0.dp else if (role == KireiGlassRole.DOCK) 28.dp else 24.dp
    val border = if (colors.isDark) Color.White.copy(alpha = .22f) else colors.outline
    val elevation = when (role) {
        KireiGlassRole.CONTENT -> 0.dp
        KireiGlassRole.ICON -> 4.dp
        KireiGlassRole.NOTICE -> 7.dp
        KireiGlassRole.ALERT -> 10.dp
        KireiGlassRole.DOCK -> 12.dp
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, AppleMotion.Spring.Press, label = "glassPress")
    Box(
        modifier.shadow(elevation, shape, clip = false)
            .then(if (onClick != null) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier)
            .clip(shape)
            .then(
                if (haze != null && role != KireiGlassRole.CONTENT && (colors.isDark || role == KireiGlassRole.DOCK)) Modifier.hazeEffect(haze) {
                    blurRadius = blur
                    backgroundColor = colors.background
                    tints = listOf(dev.chrisbanes.haze.HazeTint(base))
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
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, AppleMotion.Spring.Press, label = "pressScale")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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

private object CoverBitmapCache : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 1024L / 16L).coerceIn(8L * 1024L, 16L * 1024L).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}

private val CoverPreviewClient = okhttp3.OkHttpClient.Builder()
    .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    .build()

@Composable
private fun rememberCover(uri: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    return produceState<ImageBitmap?>(null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (uri.isNullOrBlank()) return@withContext null
            CoverBitmapCache.get(uri)?.asImageBitmap() ?: decodeSampledCover(context, uri)?.also {
                CoverBitmapCache.put(uri, it)
            }?.asImageBitmap()
        }
    }.value
}

private fun decodeSampledCover(context: Context, source: String): Bitmap? = runCatching {
    val uri = android.net.Uri.parse(source)
    if (uri.scheme == "http" || uri.scheme == "https") {
        val request = okhttp3.Request.Builder().url(source).header("User-Agent", "KireiBox/0.3").build()
        return@runCatching CoverPreviewClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val input = response.body?.byteStream() ?: return@use null
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                total += count
                if (total > 8 * 1024 * 1024) return@use null
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
            var sample = 1
            while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 1080) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (uri.scheme == "content") {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    } else {
        BitmapFactory.decodeFile(uri.path ?: source, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 1080) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    if (uri.scheme == "content") {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } else {
        BitmapFactory.decodeFile(uri.path ?: source, options)
    }
}.getOrNull()
private fun formatPlayTime(seconds: Long): String = if (seconds < 3600) "${seconds / 60} 分钟" else "${seconds / 3600} 小时"
