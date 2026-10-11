package com.kireibox.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.yuki.yukihub.BuildConfig
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.LandscapeHomeStyle
import com.yuki.yukihub.settings.MotionMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Spotlight pager and the focus gallery stage.

@Composable
internal fun HomeSpotlight(
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
        val observer =
            LifecycleEventObserver { _, _ ->
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
    val pagerState =
        rememberPagerState(
            initialPage = SpotlightCircularCarouselPolicy.startVirtualPage(pages.size, initialPage),
        ) { virtualPageCount }
    val userDragActive by pagerState.interactionSource.collectIsDraggedAsState()
    val userPressActive by pagerState.interactionSource.collectIsPressedAsState()
    val timerPausedForDrag =
        SpotlightCarouselPolicy.timerPausedForPager(
            userDragActive = userDragActive,
            scrollInProgress = pagerState.isScrollInProgress,
        )
    val timerAnchorPage =
        SpotlightCarouselPolicy.timerAnchorPage(
            currentPage = pagerState.currentPage,
            settledPage = pagerState.settledPage,
        )
    val currentGameIndex =
        if (pages.isEmpty()) {
            0
        } else {
            SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(pagerState.currentPage, pages.size)
        }

    LaunchedEffect(pages.map { it.id }, useFocusStage) {
        if (useFocusStage || pages.size <= 1) return@LaunchedEffect
        if (pages.size > 1) {
            val currentIndex =
                SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(
                    pagerState.currentPage,
                    pages.size,
                )
            val target =
                SpotlightCircularCarouselPolicy.nearestVirtualPage(
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
        ) {
            return@LaunchedEffect
        }
        delay(SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS)
        if (!SpotlightCarouselPolicy.shouldAutoAdvance(
                pageCount = pages.size,
                elapsedMillis = SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS,
                isVisible = isVisible,
                isResumed = isResumed,
                isDragging = timerPausedForDrag || userPressActive,
            )
        ) {
            return@LaunchedEffect
        }
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
            val controller =
                remember(pages.size) {
                    FocusGalleryController(
                        scope = focusScope,
                        initialGameIndex = initialIndex,
                        onSnapshot = { },
                    )
                }
            val context = LocalContext.current
            DisposableEffect(controller) {
                if (!BuildConfig.DEBUG) return@DisposableEffect onDispose { }
                val receiver =
                    object : android.content.BroadcastReceiver() {
                        override fun onReceive(
                            context: android.content.Context?,
                            intent: android.content.Intent?
                        ) {
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
            val snapshotState =
                remember {
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
            val autoTimerKeys =
                FocusAutoAdvanceTimerPolicy.timerKeys(
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
                ) {
                    return@LaunchedEffect
                }
                val token =
                    FocusAutoAdvanceTimerPolicy.tokenFor(
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
                ) {
                    return@LaunchedEffect
                }
                controller.beginAutoAdvanceIfCurrent(
                    gameCount = pages.size,
                    expectedCommitSequence = token.commitSequence,
                    expectedManualInteractionSequence = token.manualInteractionSequence,
                )
            }
            val geometry =
                FocusGalleryGeometryPolicy.geometry(
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
                modifier =
                    Modifier.fillMaxSize().focusGalleryDrag(
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
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
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
        val carouselOuterMarginDp =
            LandscapeHeroSizingPolicy.outerMarginDp(
                viewportWidthDp = maxWidth.value,
                symmetricSafeGuardDp = symmetricSafeGuardDp.value,
            )
        val sideCenterLeftDp =
            LandscapeHeroSizingPolicy.sideCenterLeftDp(
                viewportWidthDp = maxWidth.value,
                outerMarginDp = carouselOuterMarginDp,
                sideWidthDp = sideCardWidthDp,
            )
        val slotCenterDistanceDp =
            LandscapeHeroSizingPolicy.slotCenterDistanceDp(
                viewportWidthDp = maxWidth.value,
                sideCenterLeftDp = sideCenterLeftDp,
            )
        val landscapePageSpacing =
            LandscapeHeroSizingPolicy.pageSpacingDp(
                centerWidthDp = cardWidthDp,
                slotCenterDistanceDp = slotCenterDistanceDp,
            ).dp
        HorizontalPager(
            state = pagerState,
            pageSize = if (landscapePager) PageSize.Fixed(cardWidth) else PageSize.Fill,
            contentPadding =
                if (landscapePager) {
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
            val distanceFromCenter =
                if (landscapePager) {
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
            val cardAlpha =
                if (landscapePager) {
                    LandscapeHeroSizingPolicy.cardAlphaAt(pageOffset)
                } else {
                    1f
                }
            val cardSaturation =
                if (landscapePager) {
                    LandscapeHeroSizingPolicy.cardSaturationAt(pageOffset)
                } else {
                    1f
                }
            val cardScale =
                if (landscapePager) {
                    LandscapeHeroSizingPolicy.cardScaleAt(pageOffset)
                } else {
                    1f
                }
            val interpolatedCardWidthDp =
                if (landscapePager) {
                    LandscapeHeroSizingPolicy.cardWidthDpAt(cardWidthDp, sideCardWidthDp, pageOffset)
                } else {
                    cardWidthDp
                }
            val interpolatedRadiusDp =
                if (landscapePager) {
                    LandscapeHeroSizingPolicy.cardRadiusAt(pageOffset)
                } else {
                    LandscapeHeroSizingPolicy.CENTER_RADIUS_DP
                }
            val infoAlpha =
                if (landscapePager) {
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
            val pagerBottomGap =
                if (cardHeightDp != null) {
                    cardBottomGapDp
                } else {
                    (pagerMaxHeightDp - effectiveCardHeightDp).coerceAtLeast(0f)
                }
            Row(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
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
                                    val target =
                                        SpotlightCircularCarouselPolicy.nearestVirtualPage(
                                            gameIndex = index,
                                            pageCount = pages.size,
                                            fromPage = pagerState.currentPage,
                                        )
                                    if (reducedMotion) {
                                        pagerState.scrollToPage(target)
                                    } else {
                                        pagerState.animateScrollToPage(
                                            target,
                                            animationSpec = tween(SpotlightCarouselPolicy.ANIMATE_MILLIS),
                                        )
                                    }
                                }
                            },
                    )
                }
            }
        }
        HeroCoverPrefetch(pages, currentGameIndex, componentWidthPx, componentHeightPx)
    }
}
