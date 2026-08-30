package com.kireibox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.apps.theme.AppleTypography
import com.yuki.yukihub.settings.ImmersiveGalleryAutoSource
import com.yuki.yukihub.settings.ImmersiveGallerySettings
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSelector
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import com.yuki.yukihub.settings.DashboardWidgetSize
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.model.Game
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cover-internal overlay tokens: always dark in both themes so the lower-left title
 * stays readable over any cover artwork. Outside-card chrome uses ImmersivePalette.
 */
private val CoverOverlayGradientBottom = Color(0xF005070D)
private val CoverOverlayAccentBright = Color(0xFFB8A7FF)
private val CoverOverlayInk = Color.White
private val immersiveGalleryProcessSeed = System.nanoTime()

private data class HeroCoverVisual(
    val bitmap: ImageBitmap?,
    val tone: CoverTone,
    val coverCacheKey: String?,
)

/**
 * Runs the bounded tone extraction away from Compose's composition dispatcher.
 * The suspending boundary is also the lifecycle boundary used by [produceState]:
 * a changed bitmap/cache key or a disposed hero cancels the pending computation
 * before it can publish a stale tone.
 */
internal suspend fun computeImmersiveTone(
    cacheKey: String,
    bitmap: ImageBitmap?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    toneFor: suspend (String, ImageBitmap?) -> CoverTone = { key, image ->
        CoverToneExtractor.toneFor(key, image)
    },
): CoverTone = withContext(dispatcher) {
    coroutineContext.ensureActive()
    val tone = toneFor(cacheKey, bitmap)
    coroutineContext.ensureActive()
    tone
}

private sealed interface ImmersiveGalleryGestureEvent {
    object Start : ImmersiveGalleryGestureEvent

    data class Drag(
        val amount: Float,
        val uptimeMillis: Long,
        val position: Offset,
    ) : ImmersiveGalleryGestureEvent

    data class End(val velocity: Float) : ImmersiveGalleryGestureEvent

    object Cancel : ImmersiveGalleryGestureEvent
}

@Composable
internal fun ImmersiveHome(
    games: List<Game>,
    colors: KireiColors,
    actions: KireiActions,
    galleryMode: SpotlightSelectionMode = SpotlightSelectionMode.AUTOMATIC,
    manualIds: List<Long> = emptyList(),
    source: SpotlightSource? = null,
    strategy: SpotlightStrategy? = null,
    count: Int? = null,
    /** Compatibility input for callers that have not migrated to canonical gallery settings. */
    autoSource: ImmersiveGalleryAutoSource? = null,
    onGalleryModeChange: (SpotlightSelectionMode) -> Unit = {},
    onGalleryIdsChange: (List<Long>) -> Unit = {},
    onGallerySourceChange: (SpotlightSource) -> Unit = {},
    onGalleryStrategyChange: (SpotlightStrategy) -> Unit = {},
    onGalleryCountChange: (Int) -> Unit = {},
    renderKind: HomeRenderKind = HomeRenderKind.IMMERSIVE_PORTRAIT,
    safeBounds: ImmersiveSafeBounds = ImmersiveSafeBounds(0f, 0f, Float.MAX_VALUE, Float.MAX_VALUE),
    reducedMotion: Boolean,
    reducedTransparency: Boolean = false,
    adaptiveMetrics: AdaptiveUiMetrics? = null,
    haze: HazeState? = null,
    onOpenOverview: (() -> Unit)? = null,
    debugFrozenTrackProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val palette = remember(colors.isDark) { ImmersivePaletteResolver.resolve(colors.isDark) }
    BoxWithConstraints(modifier.fillMaxSize().background(palette.base).testTag("immersive-home")) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val orientation = HomeGalleryGeometryPolicy.orientationForRenderKind(renderKind)
        val opticalBounds = remember(widthPx, heightPx, safeBounds) {
            opticalSafeBounds(safeBounds, widthPx, heightPx)
        }
        val metrics = adaptiveMetrics ?: resolveAdaptiveUiMetrics(
            windowWidthDp = maxWidth.value,
            windowHeightDp = maxHeight.value,
            safeLeftDp = with(density) { opticalBounds.leftPx.toDp().value },
            safeTopDp = with(density) { opticalBounds.topPx.toDp().value },
            safeRightDp = with(density) { (widthPx - opticalBounds.rightPx).toDp().value },
            safeBottomDp = with(density) { (heightPx - opticalBounds.bottomPx).toDp().value },
        )
        val chromeSizeClass = if (
            orientation == HomeOrientation.LANDSCAPE && metrics.libraryColumns >= 5
        ) HomeSizeClass.WIDE else HomeSizeClass.COMPACT
        val safeFrameDp = HomeRect(
            with(density) { opticalBounds.leftPx.toDp().value },
            with(density) { opticalBounds.topPx.toDp().value },
            with(density) { (opticalBounds.rightPx - opticalBounds.leftPx).toDp().value },
            with(density) { (opticalBounds.bottomPx - opticalBounds.topPx).toDp().value },
        )
        val sharedChrome = remember(safeFrameDp, chromeSizeClass) {
            HomePresentationPolicy.topChromeSpec(safeFrameDp, chromeSizeClass)
        }
        val sharedChromePx = remember(sharedChrome, density.density) { sharedChrome.toPx(density) }
        val dockRectPx = remember(metrics.dockRect, density.density) { metrics.dockRect.toPx(density) }
        val dockClearancePx = with(density) {
            AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP.dp.toPx()
        }
        val minimumPlayHeightPx = with(density) { 48.dp.toPx() }
        val layout = remember(
            orientation,
            widthPx,
            heightPx,
            opticalBounds,
            dockRectPx,
            dockClearancePx,
            sharedChromePx,
            density.fontScale,
            minimumPlayHeightPx,
        ) {
            resolveImmersiveLayout(
                orientation = orientation,
                windowWidthPx = widthPx,
                windowHeightPx = heightPx,
                safeBounds = opticalBounds,
                dockRect = dockRectPx,
                bottomClearancePx = dockClearancePx,
                fontScale = density.fontScale,
                topChrome = sharedChromePx,
                minimumPlayHeightPx = minimumPlayHeightPx,
            )
        }
        // Landscape controls must follow the same height-derived scale as the resolved gallery;
        // the components-page short-edge floor is intentionally not reused here. Portrait keeps
        // the existing adaptive metric so its visual composition remains unchanged.
        val visualScale = if (orientation == HomeOrientation.LANDSCAPE) {
            layout.renderScale
        } else {
            metrics.scale.coerceAtLeast(.01f)
        }
        val canonicalSource = source ?: when (autoSource) {
            ImmersiveGalleryAutoSource.FAVORITES -> SpotlightSource.FAVORITES
            ImmersiveGalleryAutoSource.RECENT_FIVE,
            ImmersiveGalleryAutoSource.RECENT_ALL,
            null,
            -> SpotlightSource.RECENT
        }
        val canonicalStrategy = strategy ?: SpotlightStrategy.FIRST
        val canonicalCount = (count ?: ImmersiveGallerySettings.DEFAULT_COUNT)
            .coerceIn(ImmersiveGallerySettings.MIN_COUNT, ImmersiveGallerySettings.MAX_COUNT)
        val orderedGames = remember(
            games,
            galleryMode,
            manualIds,
            canonicalSource,
            canonicalStrategy,
            canonicalCount,
        ) {
            val spec = DashboardWidgetSpec(
                instanceId = "immersive-gallery",
                type = DashboardWidgetType.GAME_SPOTLIGHT,
                size = DashboardWidgetSize(columns = 1, rows = 1),
                spotlightSource = canonicalSource.takeUnless { it == SpotlightSource.FIXED }
                    ?: SpotlightSource.RECENT,
                spotlightStrategy = canonicalStrategy,
                spotlightSelectionMode = galleryMode,
                spotlightGameIds = manualIds,
                spotlightMaxPages = canonicalCount,
            )
            SpotlightSelector.selectPages(
                spec = spec,
                games = games,
                sessionSeed = immersiveGalleryProcessSeed,
                epochDay = LocalDate.now().toEpochDay(),
            )
        }
        val scope = rememberCoroutineScope()
        val controller = remember { GalleryTrackController(orderedGames.size) }
        val animatable = remember { Animatable(controller.snapshot().visualPosition) }
        val trackMutations = remember(animatable) { SerializedTrackMutationRunner(scope) }
        var snapshot by remember { mutableStateOf(controller.snapshot()) }
        val ids = remember(orderedGames) { orderedGames.map { it.id } }

        DisposableEffect(trackMutations) {
            onDispose { trackMutations.dispose() }
        }

        LaunchedEffect(controller, ids) {
            trackMutations.cancelAndJoin()
            val synced = controller.syncGameIds(ids)
            if (debugFrozenTrackProgress == null) {
                animatable.snapTo(synced.visualPosition)
            }
            snapshot = synced
        }
        LaunchedEffect(snapshot.targetPosition, snapshot.isDragging, reducedMotion) {
            if (!shouldStartImmersiveSettle(
                    isDragging = snapshot.isDragging,
                    frozen = debugFrozenTrackProgress != null,
                    itemCount = orderedGames.size,
                )
            ) return@LaunchedEffect
            trackMutations.launchLatest {
                val start = animatable.value
                val target = snapshot.targetPosition
                if (abs(target - start) < .001f) return@launchLatest
                if (reducedMotion) {
                    animatable.animateTo(
                        target,
                        tween(GalleryTrackController.REDUCED_MOTION_MS, easing = LinearEasing),
                    ) {
                        snapshot = controller.onVisualFrame(value, velocity)
                    }
                } else {
                    animatable.animateTo(
                        target,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) {
                        snapshot = controller.onVisualFrame(value, velocity)
                    }
                }
                snapshot = controller.onVisualFrame(target, 0f)
            }
        }

        val frozenSnapshot = debugFrozenTrackProgress?.coerceIn(0f, 1f)?.let { progress ->
            val hasNextCard = orderedGames.size > 1
            GalleryTrackSnapshot(
                visualPosition = if (hasNextCard) progress else 0f,
                targetPosition = if (hasNextCard) 1f else 0f,
                velocity = 0f,
                activeGameIndex = if (hasNextCard && progress >= .5f) 1 else 0,
                isDragging = false,
            )
        }
            val renderSnapshot = frozenSnapshot ?: snapshot
            val renderVisualPosition = frozenSnapshot?.visualPosition ?: animatable.value
            val currentIndex = renderSnapshot.activeGameIndex.coerceIn(0, (orderedGames.size - 1).coerceAtLeast(0))
            val currentGame = orderedGames.getOrNull(currentIndex)
            val gallerySettling = !renderSnapshot.isDragging &&
                abs(renderSnapshot.targetPosition - renderVisualPosition) > .0001f
            val galleryInteractionBlocked = renderSnapshot.isDragging || gallerySettling
        val backdropTrack = resolveImmersiveBackdropTrack(renderVisualPosition, orderedGames.size)
        val backdropRetention = resolveImmersiveBackdropRetention(renderVisualPosition, orderedGames.size)
        val backdropLeftHero = rememberHeroCover(
            orderedGames.getOrNull(backdropRetention.indexes.getOrNull(0) ?: -1),
            widthPx.roundToInt(),
            heightPx.roundToInt(),
        )
        val backdropRightHero = if (backdropRetention.transitioning) {
            rememberHeroCover(
                orderedGames.getOrNull(backdropRetention.indexes.getOrNull(1) ?: -1),
                widthPx.roundToInt(),
                heightPx.roundToInt(),
            )
        } else {
            null
        }
        ImmersiveBackdrop(
            leftHero = backdropLeftHero,
            rightHero = backdropRightHero,
            fraction = backdropTrack.fraction,
            layout = layout,
            reducedTransparency = reducedTransparency,
            scale = visualScale,
            density = density,
            palette = palette,
        )

        Text(
            "KireiBox",
            color = palette.primary,
            style = AppleTypography.headlineMedium.copy(fontSize = sharedChrome.titleSizeDp.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(
                pxDp(sharedChromePx.titleRect.x, density),
                pxDp(sharedChromePx.titleRect.y, density),
            )
                .size(
                    pxDp(sharedChromePx.titleRect.w, density),
                    pxDp(sharedChromePx.titleRect.h, density),
                )
                .zIndex(8f),
        )
        var menuExpanded by remember { mutableStateOf(false) }
        var editorOpen by rememberSaveable { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().zIndex(10f)) {
            HomeControlMenu(
                expanded = menuExpanded,
                colors = colors,
                haze = haze,
                editLabel = "编辑沉浸式展示图",
                menuDiameterDp = sharedChrome.menuDiameterDp.dp,
                menuIconSizeDp = sharedChrome.menuIconSizeDp.dp,
                opaqueFill = true,
                modifier = Modifier.align(Alignment.TopEnd).offset(
                    pxDp(sharedChromePx.menuRect.x + sharedChromePx.menuRect.w - widthPx, density),
                    pxDp(sharedChromePx.menuRect.y, density),
                ),
                onToggle = { menuExpanded = !menuExpanded },
                onEdit = {
                    menuExpanded = false
                    editorOpen = true
                },
                onTheme = {
                    menuExpanded = false
                    actions.setDarkMode(!colors.isDark)
                },
                onOpenOverview = onOpenOverview?.let { open ->
                    {
                        menuExpanded = false
                        open()
                    }
                },
            )
        }

        if (orderedGames.isEmpty()) {
            when (ImmersiveGalleryEditorPolicy.emptyState(games, galleryMode, manualIds, canonicalSource)) {
                ImmersiveGalleryEmptyState.BUILD_LIBRARY -> ImmersiveEmptyState(actions.chooseDirectory, palette)
                ImmersiveGalleryEmptyState.SELECT_GAMES -> ImmersiveSelectGamesState(palette) {
                    editorOpen = true
                }
                ImmersiveGalleryEmptyState.NO_PLAY_HISTORY -> ImmersiveNoPlayHistoryState(palette)
                ImmersiveGalleryEmptyState.NO_FAVORITES -> ImmersiveNoFavoritesState(palette)
            }
        } else {
            val frames = resolveImmersiveFrames(renderVisualPosition, layout, ids, orientation, reducedMotion)
            val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
            val side = layout.cardSlots[ImmersiveSlot.NEAR_RIGHT]?.rect ?: center
            val slotDistance = abs(side.x - center.x).coerceAtLeast(center.w * .35f)
            val gestureRegions = remember(layout, orientation) {
                HomeGalleryGeometryPolicy.galleryGestureRegions(layout, orientation)
            }
            val gestureModifier = if (debugFrozenTrackProgress == null) Modifier.pointerInput(
                orderedGames.size,
                slotDistance,
                gestureRegions,
            ) {
                    val velocityTracker = VelocityTracker()
                    val events = Channel<ImmersiveGalleryGestureEvent>(Channel.UNLIMITED)
                    var gestureAccepted = false
                    coroutineScope {
                        val consumer = launch {
                            for (event in events) {
                                when (event) {
                                    ImmersiveGalleryGestureEvent.Start -> {
                                        trackMutations.cancelAndJoin()
                                        val started = controller.beginDrag()
                                        trackMutations.replaceAndJoin {
                                            animatable.snapTo(started.visualPosition)
                                        }
                                        snapshot = started
                                    }

                                    is ImmersiveGalleryGestureEvent.Drag -> {
                                        val next = controller.dragBy(-event.amount / slotDistance)
                                        snapshot = next
                                        trackMutations.launchLatest {
                                            animatable.snapTo(next.visualPosition)
                                        }
                                    }

                                    is ImmersiveGalleryGestureEvent.End -> {
                                        trackMutations.cancelAndJoin()
                                        val released = controller.endDrag(event.velocity)
                                        trackMutations.replaceAndJoin {
                                            animatable.snapTo(released.visualPosition)
                                        }
                                        snapshot = released
                                    }

                                    ImmersiveGalleryGestureEvent.Cancel -> {
                                        trackMutations.cancelAndJoin()
                                        val cancelled = controller.cancelDrag()
                                        trackMutations.replaceAndJoin {
                                            animatable.snapTo(cancelled.visualPosition)
                                        }
                                        snapshot = cancelled
                                    }
                                }
                            }
                        }
                        try {
                            detectHorizontalDragGestures(
                                onDragStart = { position ->
                                    gestureAccepted = gestureRegions.any { region ->
                                        region.contains(HomePoint(position.x, position.y))
                                    }
                                    if (!gestureAccepted) return@detectHorizontalDragGestures
                                    velocityTracker.resetTracking()
                                    events.trySend(ImmersiveGalleryGestureEvent.Start)
                                },
                                onHorizontalDrag = { change, amount ->
                                    if (!gestureAccepted) return@detectHorizontalDragGestures
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    events.trySend(
                                        ImmersiveGalleryGestureEvent.Drag(
                                            amount = amount,
                                            uptimeMillis = change.uptimeMillis,
                                            position = change.position,
                                        ),
                                    )
                                },
                                onDragEnd = {
                                    if (!gestureAccepted) return@detectHorizontalDragGestures
                                    events.trySend(
                                        ImmersiveGalleryGestureEvent.End(
                                            dragVelocitySlotsPerSecond(
                                                velocityTracker.calculateVelocity().x,
                                                slotDistance,
                                            ),
                                        ),
                                    )
                                    gestureAccepted = false
                                },
                                onDragCancel = {
                                    if (gestureAccepted) {
                                        events.trySend(ImmersiveGalleryGestureEvent.Cancel)
                                        gestureAccepted = false
                                    }
                                },
                            )
                        } finally {
                            events.close()
                            consumer.join()
                        }
                    }
                } else Modifier
            Box(Modifier.fillMaxSize().then(gestureModifier)) {
                frames.sortedBy { it.frame.zIndex }.forEach { item ->
                    val game = orderedGames.firstOrNull { it.id == item.gameId } ?: return@forEach
                    key(item.virtualIndex) {
                        val titleEndInset = if (
                            orientation == HomeOrientation.PORTRAIT &&
                            item.slot == ImmersiveSlot.CENTER &&
                            item.gameId == currentGame?.id
                        ) {
                            pxDp(layout.play.w + 20f * visualScale, density)
                        } else {
                            0.dp
                        }
                        ImmersiveGameCard(
                            game, item.frame, item.slot, orientation, density, visualScale,
                            selected = item.gameId == currentGame?.id,
                            titleEndInset = titleEndInset,
                            onClick = {
                                if (!galleryInteractionBlocked) {
                                    val tappedIndex = Math.floorMod(item.virtualIndex, orderedGames.size)
                                    val centered = item.slot == ImmersiveSlot.CENTER && tappedIndex == currentIndex
                                    if (centered) {
                                        actions.selectedGame(game)
                                    } else {
                                        snapshot = controller.jumpTo(tappedIndex)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            if (orientation == HomeOrientation.LANDSCAPE && currentGame != null) {
                ImmersiveLandscapeMetadata(currentGame, layout, density, visualScale, palette)
            }
            ImmersivePlayButton(
                layout.play,
                density,
                visualScale,
                "播放" + (currentGame?.title.orEmpty()),
                orientation,
                palette,
            ) {
                currentGame?.let(actions.launchGame)
            }
            ImmersivePager(orderedGames, currentIndex, orientation, layout, density, visualScale, palette) { index ->
                if (debugFrozenTrackProgress == null) snapshot = controller.jumpTo(index)
            }
        }
        if (editorOpen) {
            ImmersiveGalleryConfigSheet(
                games = games,
                mode = galleryMode,
                manualIds = manualIds,
                source = canonicalSource,
                strategy = canonicalStrategy,
                count = canonicalCount,
                colors = colors,
                onDismiss = { editorOpen = false },
                onMode = onGalleryModeChange,
                onManualIds = onGalleryIdsChange,
                onSource = onGallerySourceChange,
                onStrategy = onGalleryStrategyChange,
                onCount = onGalleryCountChange,
            )
        }
    }
}

private fun adaptiveMetricsFor(layout: ImmersiveHomeLayout, widthPx: Float, heightPx: Float, safe: ImmersiveSafeBounds) =
    resolveAdaptiveUiMetrics(
        windowWidthDp = widthPx,
        windowHeightDp = heightPx,
        safeLeftDp = safe.leftPx,
        safeTopDp = safe.topPx,
        safeRightDp = widthPx - safe.rightPx,
        safeBottomDp = heightPx - safe.bottomPx,
    )

@Composable
private fun rememberHeroCover(game: Game?, widthPx: Int, heightPx: Int): HeroCoverVisual? {
    if (game == null) return null
    val uri = game.coverUri
    val cacheKey = remember(uri, widthPx, heightPx) {
        coverCacheKey(uri, CoverImageRole.IMMERSIVE_BACKDROP, widthPx, heightPx)
    }
    val bitmap = rememberCover(
        uri = uri,
        role = CoverImageRole.IMMERSIVE_BACKDROP,
        componentWidthPx = widthPx,
        componentHeightPx = heightPx,
    )
    val toneCacheKey = cacheKey ?: "immersive-backdrop-fallback"
    val tone by produceState(
        initialValue = CoverToneExtractor.fromArgbPixels(intArrayOf()),
        key1 = toneCacheKey,
        key2 = bitmap,
    ) {
        value = computeImmersiveTone(toneCacheKey, bitmap)
    }
    return remember(cacheKey, bitmap, tone) {
        HeroCoverVisual(bitmap = bitmap, tone = tone, coverCacheKey = cacheKey)
    }
}

@Composable
private fun ImmersiveBackdrop(
    leftHero: HeroCoverVisual?, rightHero: HeroCoverVisual?, fraction: Float,
    layout: ImmersiveHomeLayout,
    reducedTransparency: Boolean, scale: Float, density: Density,
    palette: ImmersivePalette,
) {
    val transition = normalizeImmersiveBackdropTransition(fraction)
    val currentTone = leftHero?.tone ?: CoverToneExtractor.fromArgbPixels(intArrayOf())
    val targetTone = rightHero?.tone ?: currentTone
    val currentColor = Color(currentTone.argb)
    val targetColor = Color(targetTone.argb)
    val blendedColor = Color(
        red = currentColor.red + (targetColor.red - currentColor.red) * transition,
        green = currentColor.green + (targetColor.green - currentColor.green) * transition,
        blue = currentColor.blue + (targetColor.blue - currentColor.blue) * transition,
        alpha = 1f,
    )
    val drawPlan = immersiveBackdropDrawPlan(
        leftBitmapAvailable = leftHero?.bitmap != null,
        rightBitmapAvailable = rightHero?.bitmap != null,
        fraction = transition,
        reducedTransparency = reducedTransparency,
    )
    val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
    Canvas(Modifier.fillMaxSize().zIndex(0f)) {
        drawRect(color = palette.base)
        if (drawPlan.drawLeftBitmap) {
            leftHero?.bitmap?.let { drawCroppedBackdropBitmap(it, drawPlan.leftBitmapAlpha) }
        }
        if (drawPlan.drawRightBitmap) {
            rightHero?.bitmap?.let { drawCroppedBackdropBitmap(it, drawPlan.rightBitmapAlpha) }
        }
        if (drawPlan.drawBlendRadial) {
            drawRect(
                brush = Brush.radialGradient(
                    listOf(blendedColor.copy(alpha = palette.blendAlpha), Color.Transparent),
                    radius = 900f * scale,
                ),
            )
        }
        if (drawPlan.drawHorizontalScrim) {
            drawRect(brush = Brush.horizontalGradient(listOf(palette.scrimStart, palette.scrimMid, Color.Transparent)))
        }
        if (drawPlan.drawRadial) {
            drawRect(
                brush = Brush.radialGradient(
                    listOf(
                        blendedColor.copy(
                            alpha = if (reducedTransparency) palette.radialReducedAlpha else palette.radialAlpha,
                        ),
                        Color.Transparent,
                    ),
                    radius = 1500f * scale,
                ),
            )
        }
        if (drawPlan.drawGlow) {
            val glowWidth = center.w * 2.24f
            val glowHeight = center.h * .34f
            drawOval(
                brush = Brush.radialGradient(
                    listOf(
                        blendedColor.copy(
                            alpha = if (reducedTransparency) palette.glowReducedAlpha else palette.glowAlpha,
                        ),
                        Color.Transparent,
                    ),
                    radius = maxOf(glowWidth, glowHeight) * .50f,
                ),
                topLeft = Offset(center.x - center.w * .62f, center.y + center.h * .78f),
                size = Size(glowWidth, glowHeight),
            )
        }
    }
}

private fun DrawScope.drawCroppedBackdropBitmap(bitmap: ImageBitmap, alpha: Float) {
    if (bitmap.width <= 0 || bitmap.height <= 0 || size.width <= 0f || size.height <= 0f) return
    val destinationWidth = size.width.roundToInt().coerceAtLeast(1)
    val destinationHeight = size.height.roundToInt().coerceAtLeast(1)
    val destinationAspect = size.width / size.height
    val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    val sourceWidth: Int
    val sourceHeight: Int
    val sourceLeft: Int
    val sourceTop: Int
    if (bitmapAspect > destinationAspect) {
        sourceHeight = bitmap.height
        sourceWidth = (bitmap.height * destinationAspect).roundToInt().coerceIn(1, bitmap.width)
        sourceLeft = ((bitmap.width - sourceWidth) / 2).coerceAtLeast(0)
        sourceTop = 0
    } else {
        sourceWidth = bitmap.width
        sourceHeight = (bitmap.width / destinationAspect).roundToInt().coerceIn(1, bitmap.height)
        sourceLeft = 0
        sourceTop = ((bitmap.height - sourceHeight) / 2).coerceAtLeast(0)
    }
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(sourceLeft, sourceTop),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(destinationWidth, destinationHeight),
        alpha = alpha,
    )
}

@Composable
private fun ImmersiveGameCard(
    game: Game,
    frame: ImmersiveCardFrame,
    slot: ImmersiveSlot,
    orientation: HomeOrientation,
    density: Density,
    scale: Float,
    selected: Boolean,
    titleEndInset: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val cardRadiusDp = ImmersiveSharedSpec.cardRadiusDp(frame.focusFraction)
    val shape = RoundedCornerShape(cardRadiusDp.dp)
    Box(
        Modifier.homeRect(frame.rect, density).graphicsLayer {
            rotationZ = frame.rotationZ; rotationY = frame.rotationY; alpha = frame.alpha
            shadowElevation = immersiveCardShadowElevationPx(frame.focusFraction, frame.elevationPx)
            this.shape = shape; clip = true
        }.zIndex(frame.zIndex).border(pxDp(.8f * scale, density), Color(0x887D7A86), shape)
            .clickable(onClick = onClick).testTag("immersive-card-${game.id}")
            .semantics { contentDescription = "${game.title.orEmpty()}，${if (selected) "已选中" else "点击选择"}" },
    ) {
        GameCoverArtwork(
            game,
            Modifier.fillMaxSize().desaturate(frame.saturation)
                .testTag("immersive-card-role-${slot.name.lowercase()}"),
            ContentScale.Crop,
            cardRadiusDp.dp,
            if (slot == ImmersiveSlot.CENTER) CoverImageRole.HERO else CoverImageRole.THUMBNAIL,
            showFallbackMetadata = false,
        )
        if (shouldRenderImmersiveCardOverlay(slot) && orientation == HomeOrientation.PORTRAIT && selected) {
            Column(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, CoverOverlayGradientBottom)))
                    .padding(pxDp(20f * scale, density))
                    .padding(end = titleEndInset),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    game.engine?.displayName ?: "游戏",
                    color = CoverOverlayAccentBright,
                    fontSize = designSp(26f, density, scale),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    game.title.orEmpty(),
                    color = CoverOverlayInk,
                    fontSize = designSp(58f, density, scale),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ImmersiveLandscapeMetadata(game: Game, layout: ImmersiveHomeLayout, density: Density, scale: Float, palette: ImmersivePalette) {
    layout.metadata?.let {
        Text(
            game.engine?.displayName ?: "游戏",
            color = palette.accentBright,
            fontSize = designSp(30f, density, scale),
            lineHeight = designSp(IMMERSIVE_METADATA_LINE_HEIGHT_PX, density, scale),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.homeRect(it, density).zIndex(7f),
        )
    }
    layout.title?.let {
        Text(
            game.title.orEmpty(),
            color = palette.primary,
            fontSize = designSp(72f, density, scale),
            lineHeight = designSp(IMMERSIVE_TITLE_LINE_HEIGHT_PX, density, scale),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.homeRect(it, density).zIndex(7f).semantics {
                contentDescription = game.title.orEmpty()
            },
        )
    }
    layout.subtitle?.let {
        Text(
            "继续游戏",
            color = palette.secondary,
            fontSize = designSp(32f, density, scale),
            lineHeight = designSp(IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX, density, scale),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.homeRect(it, density).zIndex(7f),
        )
    }
}

@Composable
private fun ImmersivePlayButton(
    rect: HomeRect,
    density: Density,
    scale: Float,
    description: String,
    orientation: HomeOrientation,
    palette: ImmersivePalette,
    onClick: () -> Unit,
) {
    val shape = if (orientation == HomeOrientation.LANDSCAPE) {
        RoundedCornerShape(pxDp(rect.h / 2f, density))
    } else {
        CircleShape
    }
    Box(
        Modifier.offset(pxDp(rect.x, density), pxDp(rect.y, density))
        .size(pxDp(rect.w, density), pxDp(rect.h, density))
        .shadow(pxDp(18f * scale, density), shape).clip(shape)
        .background(Brush.horizontalGradient(listOf(palette.accent, palette.accentBright)))
        .border(pxDp(scale, density), palette.glassBorder, shape)
        .clickable(onClick = onClick).testTag("immersive-play").zIndex(9f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(pxDp(min(rect.w, rect.h) * .46f, density))) {
            val path = Path().apply { moveTo(size.width * .30f, size.height * .18f); lineTo(size.width * .78f, size.height * .50f); lineTo(size.width * .30f, size.height * .82f); close() }
            drawPath(path, palette.playInk)
        }
        Box(Modifier.matchParentSize().semantics { contentDescription = description })
    }
}

@Composable
private fun ImmersivePager(games: List<Game>, currentIndex: Int, orientation: HomeOrientation, layout: ImmersiveHomeLayout, density: Density, scale: Float, palette: ImmersivePalette, onSelect: (Int) -> Unit) {
    val indexes = ImmersivePagerPolicy.visibleIndexes(games.size, currentIndex)
    if (indexes.isEmpty()) return
    val visibleCenters = ImmersivePagerPolicy.visibleCenters(layout.pagerCenters, indexes.size)
    indexes.forEachIndexed { position, index ->
        val point = visibleCenters.getOrNull(position) ?: return@forEachIndexed
        val width = if (orientation == HomeOrientation.PORTRAIT) 13f else 28f
        val height = if (orientation == HomeOrientation.PORTRAIT) 13f else 5f
        Box(Modifier.offset(pxDp(point.x - width * scale / 2f, density), pxDp(point.y - height * scale / 2f, density)).size(pxDp(width * scale, density), pxDp(height * scale, density)).clip(if (orientation == HomeOrientation.PORTRAIT) CircleShape else RoundedCornerShape(99.dp)).background(if (index == currentIndex) palette.accentBright else palette.primary.copy(alpha = .25f)).clickable { onSelect(index) }.zIndex(8f).semantics { contentDescription = "切换到第${index + 1}项" })
    }
}

@Composable
private fun ImmersiveEmptyState(onChooseDirectory: () -> Unit, palette: ImmersivePalette) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("还没有游戏", color = palette.primary, fontWeight = FontWeight.SemiBold)
        Text("选择游戏目录后开始你的故事", color = palette.secondary, modifier = Modifier.padding(top = 10.dp))
        Text("选择目录", color = palette.playInk, modifier = Modifier.padding(top = 20.dp).clip(CircleShape).background(palette.accent).clickable(onClick = onChooseDirectory).padding(horizontal = 24.dp, vertical = 14.dp))
    }
}

@Composable
private fun ImmersiveNoPlayHistoryState(palette: ImmersivePalette) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("暂无游玩记录", color = palette.primary, fontWeight = FontWeight.SemiBold)
        Text(
            "游玩任意游戏后，这里会按最近游玩展示游戏",
            color = palette.secondary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ImmersiveSelectGamesState(palette: ImmersivePalette, onOpenEditor: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("请选择游戏", color = palette.primary, fontWeight = FontWeight.SemiBold)
        Text(
            "在设置中选择要展示的游戏",
            color = palette.secondary,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "打开设置",
            color = palette.playInk,
            modifier = Modifier
                .padding(top = 20.dp)
                .clip(CircleShape)
                .background(palette.accent)
                .clickable(onClick = onOpenEditor)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun ImmersiveNoFavoritesState(palette: ImmersivePalette) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("暂无收藏", color = palette.primary, fontWeight = FontWeight.SemiBold)
    }
}

private fun Modifier.desaturate(saturation: Float): Modifier = if (saturation >= .999f) this else this
private fun Modifier.homeRect(rect: HomeRect, density: Density): Modifier = offset(pxDp(rect.x, density), pxDp(rect.y, density)).size(pxDp(rect.w, density), pxDp(rect.h, density))
private fun Modifier.pointOffset(point: HomePoint, density: Density): Modifier = offset(pxDp(point.x, density), pxDp(point.y, density))
private fun pxDp(px: Float, density: Density): Dp = with(density) { px.toDp() }
private fun designSp(px: Float, density: Density, scale: Float) = (px * scale / density.density).sp

private fun HomeRect.toPx(density: Density): HomeRect = HomeRect(
    x = with(density) { x.dp.toPx() },
    y = with(density) { y.dp.toPx() },
    w = with(density) { w.dp.toPx() },
    h = with(density) { h.dp.toPx() },
)

private fun HomeTopChromeSpec.toPx(density: Density): HomeTopChromeSpec = copy(
    titleRect = titleRect.toPx(density),
    menuRect = menuRect.toPx(density),
    menuHitRect = menuHitRect.toPx(density),
    titleSizeDp = with(density) { titleSizeDp.dp.toPx() },
    menuDiameterDp = with(density) { menuDiameterDp.dp.toPx() },
    menuIconSizeDp = with(density) { menuIconSizeDp.dp.toPx() },
)

@Composable
private fun ImmersiveGalleryConfigSheet(
    games: List<Game>,
    mode: SpotlightSelectionMode,
    manualIds: List<Long>,
    source: SpotlightSource,
    strategy: SpotlightStrategy,
    count: Int,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onMode: (SpotlightSelectionMode) -> Unit,
    onManualIds: (List<Long>) -> Unit,
    onSource: (SpotlightSource) -> Unit,
    onStrategy: (SpotlightStrategy) -> Unit,
    onCount: (Int) -> Unit,
) {
    val current = HomeContentSettings(
        mode = mode,
        manualGameIds = manualIds,
        source = source,
        strategy = strategy,
        count = count,
    ).normalized()
    KireiActionSheet(
        "编辑沉浸式展示图",
        colors,
        onDismiss,
        expanded = true,
        scrollableContent = false,
    ) {
        HomeContentSettingsPanel(
            settings = current,
            games = games,
            colors = colors,
            onSettingsChange = { next ->
                val normalized = next.normalized()
                if (normalized.mode != current.mode) onMode(normalized.mode)
                if (normalized.manualGameIds != current.manualGameIds) onManualIds(normalized.manualGameIds)
                if (normalized.source != current.source) onSource(normalized.source)
                if (normalized.strategy != current.strategy) onStrategy(normalized.strategy)
                if (normalized.count != current.count) onCount(normalized.count)
            },
        )
    }
}

@Composable
internal fun HomePresentationModeRow(value: com.yuki.yukihub.settings.HomePresentationMode, colors: KireiColors, onChange: (com.yuki.yukihub.settings.HomePresentationMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().testTag("home-mode-current").clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("首页模式", color = colors.text); Text("沉浸式画廊或组件网格", color = colors.secondary) }
        Text(value.displayName, color = colors.accent)
    }
    if (expanded) {
        KireiActionSheet("首页模式", colors, { expanded = false }, expanded = true) {
            com.yuki.yukihub.settings.HomePresentationMode.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        expanded = false
                        onChange(option)
                    }.padding(horizontal = 24.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.displayName, color = colors.text)
                        Text(
                            if (option == com.yuki.yukihub.settings.HomePresentationMode.IMMERSIVE) {
                                "封面优先的沉浸式画廊"
                            } else {
                                "可编辑的组件网格首页"
                            },
                            color = colors.secondary,
                        )
                    }
                    if (option == value) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent)
                }
            }
        }
    }
}

/**
 * Alpha 9 shared control specs: immersive home mirrors the components home on the same
 * device/orientation. Brand font follows the components header (displayLarge portrait /
 * headlineMedium landscape), the menu button is the 48dp components header action and the
 * card corner radius mirrors the default spotlight surface (32dp). The play button size
 * is resolved through [SharedControlSizePolicy] from the actual center card frame, so both
 * home modes share one rule.
 */
internal object ImmersiveSharedSpec {
    const val BRAND_PORTRAIT_FONT_SIZE_SP = 34f
    const val BRAND_LANDSCAPE_FONT_SIZE_SP = 18f
    const val CARD_RADIUS_CENTER_DP = 16f
    const val CARD_RADIUS_SIDE_DP = 12f

    /** Corner contract calibrated to the rendered card scale: focused 16dp, side 12dp, interpolated in motion. */
    fun cardRadiusDp(focusFraction: Float): Float =
        CARD_RADIUS_SIDE_DP + (CARD_RADIUS_CENTER_DP - CARD_RADIUS_SIDE_DP) * focusFraction.coerceIn(0f, 1f)

    fun brandFontSizeSp(orientation: HomeOrientation): Float =
        if (orientation == HomeOrientation.PORTRAIT) {
            BRAND_PORTRAIT_FONT_SIZE_SP
        } else {
            BRAND_LANDSCAPE_FONT_SIZE_SP
        }
}
