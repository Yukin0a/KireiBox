package com.kireibox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardWidgetSize
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.ImmersiveCardSwitch
import com.yuki.yukihub.settings.ImmersiveGalleryAutoSource
import com.yuki.yukihub.settings.ImmersiveGallerySettings
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSelector
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val immersiveGalleryProcessSeed = System.nanoTime()

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
    showPlayTime: Boolean = true,
    onOpenPlayTime: () -> Unit = {},
    reducedTransparency: Boolean = false,
    adaptiveMetrics: AdaptiveUiMetrics? = null,
    haze: HazeState? = null,
    debugFrozenTrackProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val palette = remember(colors.isDark, colors.accent, colors.onAccent) { ImmersivePaletteResolver.resolve(colors.isDark, colors.accent, colors.onAccent) }
    // One resolved effect set (plan §6.4); the explicit params of fixtures/callers can only degrade it.
    val appFx = LocalImmersiveFx.current
    val fx = remember(appFx, reducedMotion, reducedTransparency) { appFx.reconciled(reducedMotion, reducedTransparency) }
    val parallax = rememberImmersiveParallax(fx.gyroParallax)
    val motionClock = rememberImmersiveMotionClock(fx.needsMotionClock)
    BoxWithConstraints(modifier.fillMaxSize().background(palette.base).testTag("immersive-home")) {
        // A display resize (wm size, a posture change) can lay the home out at 0×0 for a frame; the
        // metrics and layout require a positive window, so that frame only paints the base.
        if (maxWidth.value <= 0f || maxHeight.value <= 0f) return@BoxWithConstraints
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val orientation = HomeGalleryGeometryPolicy.orientationForRenderKind(renderKind)
        val opticalBounds =
            remember(widthPx, heightPx, safeBounds) {
                opticalSafeBounds(safeBounds, widthPx, heightPx)
            }
        val metrics =
            adaptiveMetrics ?: resolveAdaptiveUiMetrics(
                windowWidthDp = maxWidth.value,
                windowHeightDp = maxHeight.value,
                safeLeftDp = with(density) { opticalBounds.leftPx.toDp().value },
                safeTopDp = with(density) { opticalBounds.topPx.toDp().value },
                safeRightDp = with(density) { (widthPx - opticalBounds.rightPx).toDp().value },
                safeBottomDp = with(density) { (heightPx - opticalBounds.bottomPx).toDp().value },
            )
        val chromeSizeClass =
            if (
                orientation == HomeOrientation.LANDSCAPE && metrics.libraryColumns >= 5
            ) {
                HomeSizeClass.WIDE
            } else {
                HomeSizeClass.COMPACT
            }
        val safeFrameDp =
            HomeRect(
                with(density) { opticalBounds.leftPx.toDp().value },
                with(density) { opticalBounds.topPx.toDp().value },
                with(density) { (opticalBounds.rightPx - opticalBounds.leftPx).toDp().value },
                with(density) { (opticalBounds.bottomPx - opticalBounds.topPx).toDp().value },
            )
        val sharedChrome =
            remember(safeFrameDp, chromeSizeClass) {
                HomePresentationPolicy.topChromeSpec(safeFrameDp, chromeSizeClass)
            }
        val sharedChromePx = remember(sharedChrome, density.density) { sharedChrome.toPx(density) }
        // The immersive layout is solved against the rect the shell draws the Dock at (lowered in landscape).
        val dockRectPx = remember(metrics, density.density) { metrics.dockRect.toPx(density) }
        val dockClearancePx =
            with(density) {
                AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP.dp.toPx()
            }
        val minimumPlayHeightPx = with(density) { 48.dp.toPx() }
        val layout =
            remember(
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
        val visualScale =
            if (orientation == HomeOrientation.LANDSCAPE) {
                layout.renderScale
            } else {
                metrics.scale.coerceAtLeast(.01f)
            }
        val canonicalSource =
            source ?: when (autoSource) {
                ImmersiveGalleryAutoSource.FAVORITES -> SpotlightSource.FAVORITES
                ImmersiveGalleryAutoSource.RECENT_FIVE,
                ImmersiveGalleryAutoSource.RECENT_ALL,
                null,
                -> SpotlightSource.RECENT
            }
        val canonicalStrategy = strategy ?: SpotlightStrategy.FIRST
        val canonicalCount =
            (count ?: ImmersiveGallerySettings.DEFAULT_COUNT)
                .coerceIn(ImmersiveGallerySettings.MIN_COUNT, ImmersiveGallerySettings.MAX_COUNT)
        val orderedGames =
            remember(
                games,
                galleryMode,
                manualIds,
                canonicalSource,
                canonicalStrategy,
                canonicalCount,
            ) {
                val spec =
                    DashboardWidgetSpec(
                        instanceId = "immersive-gallery",
                        type = DashboardWidgetType.GAME_SPOTLIGHT,
                        size = DashboardWidgetSize(columns = 1, rows = 1),
                        spotlightSource =
                            canonicalSource.takeUnless { it == SpotlightSource.FIXED }
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
            ) {
                return@LaunchedEffect
            }
            trackMutations.launchLatest {
                val start = animatable.value
                val target = snapshot.targetPosition
                if (abs(target - start) < IMMERSIVE_SETTLE_EPSILON) {
                    // Too close to animate: snap so the track really rests (see isImmersiveGallerySettling).
                    animatable.snapTo(target)
                } else if (reducedMotion) {
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
                        initialVelocity =
                            immersiveSettleInitialVelocity(
                                start = start,
                                target = target,
                                runningVelocity = animatable.velocity,
                                trackVelocity = snapshot.velocity,
                            ),
                    ) {
                        snapshot = controller.onVisualFrame(value, velocity)
                    }
                }
                snapshot = controller.onVisualFrame(target, 0f)
            }
        }

        val frozenSnapshot =
            debugFrozenTrackProgress?.coerceIn(0f, 1f)?.let { progress ->
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
        val gallerySettling =
            isImmersiveGallerySettling(renderSnapshot.isDragging, renderSnapshot.targetPosition, renderVisualPosition)
        val galleryInteractionBlocked = renderSnapshot.isDragging || gallerySettling
        // r5: a small (bounded) gallery pulled past an end keeps its own cover behind it.
        val backdropPosition = GalleryTrackBounds.backdropPosition(renderVisualPosition, orderedGames.size)
        val backdropTrack = resolveImmersiveBackdropTrack(backdropPosition, orderedGames.size)
        val backdropRetention = resolveImmersiveBackdropRetention(backdropPosition, orderedGames.size)
        // Hero state follows the game, not the left/right slot (see rememberRetainedHeroCovers).
        val retainedHeroes = rememberRetainedHeroCovers(orderedGames, backdropRetention, widthPx.roundToInt(), heightPx.roundToInt())
        ImmersiveBackdropPrefetch(orderedGames, resolveImmersiveBackdropPrefetch(currentIndex, orderedGames.size), widthPx.roundToInt(), heightPx.roundToInt())
        val backdropLeftHero = retainedHeroes.getOrNull(0)
        val backdropRightHero = if (backdropRetention.transitioning) retainedHeroes.getOrNull(1) else null
        CompositionLocalProvider(
            LocalImmersiveFx provides fx,
            LocalImmersiveParallax provides parallax,
            LocalImmersiveMotionClock provides motionClock,
        ) {
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
            ImmersiveAmbientLayer(fx.ambient, palette.isDark, motionClock)
        }

        // Landscape: one left edge with the information column below (r4 QA).
        val brandRect = immersiveBrandTitleRect(sharedChromePx.titleRect, layout, orientation)
        Text(
            "KireiBox",
            color = palette.primary,
            style = AppleTypography.headlineMedium.copy(fontSize = sharedChrome.titleSizeDp.sp).withImmersiveTitleShadow(fx.increasedContrast, palette.isDark),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.offset(pxDp(brandRect.x, density), pxDp(brandRect.y, density))
                    .size(pxDp(brandRect.w, density), pxDp(brandRect.h, density))
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
                modifier =
                    Modifier.align(Alignment.TopEnd).offset(
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
            )
        }

        if (orderedGames.isEmpty()) {
            when (ImmersiveGalleryEditorPolicy.emptyState(games, galleryMode, manualIds, canonicalSource)) {
                ImmersiveGalleryEmptyState.BUILD_LIBRARY -> ImmersiveEmptyState(actions.chooseDirectory, palette)
                ImmersiveGalleryEmptyState.SELECT_GAMES ->
                    ImmersiveSelectGamesState(palette) {
                        editorOpen = true
                    }
                // No history / no favorites yet: offer the gallery editor (switch to 全部 or pick games).
                ImmersiveGalleryEmptyState.NO_PLAY_HISTORY -> ImmersiveNoPlayHistoryState(palette) { editorOpen = true }
                ImmersiveGalleryEmptyState.NO_FAVORITES -> ImmersiveNoFavoritesState(palette) { editorOpen = true }
            }
        } else {
            val cardContext =
                immersiveCardContext(backdropLeftHero, backdropRightHero, backdropTrack.fraction, if (galleryInteractionBlocked) null else currentGame?.id)
            val stagger = fx.cardSwitch == ImmersiveCardSwitch.STAGGER && orientation == HomeOrientation.PORTRAIT && !reducedMotion
            val frames =
                resolveImmersiveFrames(renderVisualPosition, layout, ids, orientation, reducedMotion).let { resolved ->
                    if (stagger) resolved.withPortraitShuffle(renderVisualPosition, layout) else resolved
                }
            val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
            val side = layout.cardSlots[ImmersiveSlot.NEAR_RIGHT]?.rect ?: center
            val slotDistance = abs(side.x - center.x).coerceAtLeast(center.w * .35f)
            val gestureRegions =
                remember(layout, orientation) {
                    HomeGalleryGeometryPolicy.galleryGestureRegions(layout, orientation)
                }
            val gestureModifier =
                if (debugFrozenTrackProgress == null) {
                    Modifier.pointerInput(
                        orderedGames.size,
                        slotDistance,
                        gestureRegions,
                    ) {
                        val velocityTracker = VelocityTracker()
                        val events = Channel<ImmersiveGalleryGestureEvent>(Channel.UNLIMITED)
                        var gestureAccepted = false
                        coroutineScope {
                            val consumer =
                                launch {
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
                                        gestureAccepted =
                                            gestureRegions.any { region ->
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
                    }
                } else {
                    Modifier
                }
            CompositionLocalProvider(
                LocalImmersiveFx provides fx,
                LocalImmersiveParallax provides parallax,
                LocalImmersiveCardContext provides cardContext,
            ) {
                Box(Modifier.fillMaxSize().then(gestureModifier)) {
                    frames.sortedBy { it.frame.zIndex }.forEach { item ->
                        val game = orderedGames.firstOrNull { it.id == item.gameId } ?: return@forEach
                        key(item.virtualIndex) {
                            // Portrait titles ride every card at the resting centre size (room for the play button included).
                            val titleEndInset =
                                if (orientation == HomeOrientation.PORTRAIT) pxDp(layout.play.w + 20f * visualScale, density) else 0.dp
                            ImmersiveGameCard(
                                game, item.frame, item.slot, orientation, density, visualScale,
                                selected = item.gameId == currentGame?.id,
                                titleEndInset = titleEndInset,
                                titleRestRect = center,
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
            }
            if (orientation == HomeOrientation.LANDSCAPE && currentGame != null) {
                ImmersiveLandscapeMetadata(currentGame, layout, density, visualScale, palette)
            }
            val playTimeSpec =
                resolveImmersivePlayTimeVisualSpec(
                    orientation = orientation,
                    adaptiveScale = metrics.scale,
                    fontScale = density.fontScale,
                )
            // r4 · I2: landscape play time is measured against the whole information column; on short
            // windows it sits beside the play pill, which narrows only if the label needs the room.
            val landscapePlayTime =
                if (showPlayTime && orientation == HomeOrientation.LANDSCAPE) {
                    rememberImmersiveLandscapePlayTime(layout, orderedGames, playTimeSpec)
                } else {
                    null
                }
            val playLayout = landscapePlayTime?.let { layout.copy(play = it.play) } ?: layout
            // r4: the portrait button rides its card and springs back in once the gallery settles;
            // the frozen debug fixture counts as settled so screenshots show the resting button.
            val playSettled = debugFrozenTrackProgress != null || !galleryInteractionBlocked
            val playReveal = rememberImmersivePlayButtonReveal(settled = playSettled, reducedMotion = reducedMotion)
            val playTrack =
                ImmersivePlayButtonTrack(
                    frames = frames,
                    layout = playLayout,
                    orientation = orientation,
                    visualPosition = renderVisualPosition,
                    targetPosition = renderSnapshot.targetPosition,
                    dragging = renderSnapshot.isDragging || debugFrozenTrackProgress != null,
                    settled = playSettled,
                    reducedMotion = reducedMotion,
                )
            resolveImmersivePlayButtonPose(playTrack, currentGame?.id)?.let { pose ->
                val playGame = orderedGames.firstOrNull { it.id == pose.gameId }
                ImmersivePlayButton(pose, ImmersivePlayButtonLook(density, visualScale, immersivePlayButtonDescription(playGame?.title), palette), playReveal) {
                    playGame?.let(actions.launchGame)
                }
            }
            if (showPlayTime && currentGame != null) {
                val playTimeAnchor =
                    landscapePlayTime?.anchor() ?: resolveImmersivePlayTimeAnchor(
                        orientation = orientation,
                        layout = layout,
                        safeBounds = opticalBounds,
                        scale = visualScale,
                        indicatorHeightPx = with(density) { playTimeSpec.minimumHeightDp.dp.toPx() },
                        gapPx = with(density) { playTimeSpec.anchorGapDp.dp.toPx() },
                    )
                playTimeAnchor?.let { anchor ->
                    ImmersivePlayTimeIndicator(
                        game = currentGame,
                        orientation = orientation,
                        anchor = anchor,
                        density = density,
                        visualSpec = playTimeSpec,
                        palette = palette,
                        reducedMotion = reducedMotion,
                        onClick = onOpenPlayTime,
                    )
                }
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
