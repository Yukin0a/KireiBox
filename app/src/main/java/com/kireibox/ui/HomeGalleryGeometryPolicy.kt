package com.kireibox.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class HomeOrientation {
    PORTRAIT,
    LANDSCAPE,
}

data class DesignCanvas(
    val orientation: HomeOrientation,
    val width: Float,
    val height: Float,
)

data class HomeRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

data class HomePoint(
    val x: Float,
    val y: Float,
)

data class ImmersiveSafeBounds(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
)

enum class ImmersiveSlot {
    EXIT_LEFT,
    FAR_LEFT,
    NEAR_LEFT,
    CENTER,
    NEAR_RIGHT,
    FAR_RIGHT,
    ENTER_RIGHT,
}

internal fun shouldRenderImmersiveCardOverlay(slot: ImmersiveSlot): Boolean = slot == ImmersiveSlot.CENTER

data class ImmersiveCardFrame(
    val rect: HomeRect,
    val rotationZ: Float = 0f,
    val rotationY: Float = 0f,
    val alpha: Float = 1f,
    val saturation: Float = 1f,
    val elevationPx: Float = 0f,
    val zIndex: Float = 0f,
    /** 1.0 = focused center card, 0.0 = side/edge card; drives the corner radius interpolation. */
    val focusFraction: Float = 0f,
)

data class ImmersiveHomeLayout(
    val brand: HomePoint,
    val menu: HomeRect,
    val metadata: HomeRect?,
    val title: HomeRect?,
    val subtitle: HomeRect?,
    val play: HomeRect,
    val dock: HomeRect,
    val pagerCenters: List<HomePoint>,
    val cardSlots: Map<ImmersiveSlot, ImmersiveCardFrame>,
    /** Shared render scale for landscape typography and controls derived from the resolved frame. */
    val renderScale: Float = 1f,
)

data class ImmersiveTextBudget(
    val metadataHeightPx: Float,
    val titleHeightPx: Float,
    val titleLineHeightPx: Float,
    val subtitleHeightPx: Float,
)

private const val IMMERSIVE_TITLE_MAX_LINES = 2f

const val IMMERSIVE_LANDSCAPE_REFERENCE_HEIGHT_PX = 941f
const val IMMERSIVE_LANDSCAPE_CENTER_FRACTION = .63f
const val IMMERSIVE_LANDSCAPE_CENTER_HEIGHT_FRACTION = .5356f
const val IMMERSIVE_LANDSCAPE_METADATA_X_FRACTION = .053f
const val IMMERSIVE_LANDSCAPE_METADATA_MIN_SAFE_GAP_PX = 8f
const val IMMERSIVE_LANDSCAPE_METADATA_MAX_DOWNWARD_OFFSET_PX = 24f
const val IMMERSIVE_LANDSCAPE_RENDER_SCALE_MIN = .55f
const val IMMERSIVE_LANDSCAPE_RENDER_SCALE_MAX = 1.15f

/** Smallest font scale the immersive text budget plans for; values below it clamp. */
const val IMMERSIVE_FONT_SCALE_SAFE_MIN = .75f

/** Fallback text scale for non-finite font scales so the geometry never NaNs. */
const val IMMERSIVE_FONT_SCALE_FALLBACK = 1f

/** The play control's 48dp-equivalent touch floor in the policy's reference space. */
const val IMMERSIVE_PLAY_MIN_HEIGHT_PX = 48f

/**
 * Resolves the scale used by the immersive renderer from the same geometry that positions it.
 * Landscape is height-driven: the short 1339x597 frame must be allowed below the adaptive
 * components-page floor, while very tall frames stop at the reference scale plus a small bound.
 */
fun resolveImmersiveRenderScale(
    orientation: HomeOrientation,
    safeWidthPx: Float,
    safeHeightPx: Float,
): Float = when (orientation) {
    HomeOrientation.PORTRAIT ->
        min(safeWidthPx / 852f, safeHeightPx / 1846f).coerceAtLeast(.01f)
    HomeOrientation.LANDSCAPE ->
        (safeHeightPx / IMMERSIVE_LANDSCAPE_REFERENCE_HEIGHT_PX)
            .coerceIn(
                IMMERSIVE_LANDSCAPE_RENDER_SCALE_MIN,
                IMMERSIVE_LANDSCAPE_RENDER_SCALE_MAX,
            )
}

fun resolveImmersiveTextBudget(
    safeHeightPx: Float,
    fontScale: Float,
    renderScale: Float = resolveImmersiveRenderScale(
        HomeOrientation.LANDSCAPE,
        safeWidthPx = IMMERSIVE_LANDSCAPE_REFERENCE_HEIGHT_PX,
        safeHeightPx = safeHeightPx,
    ),
): ImmersiveTextBudget {
    // The font size is rendered as sp and therefore receives LocalDensity.fontScale once in
    // Compose. This budget mirrors that rendered height once; it never pre-scales the design
    // size and then applies fontScale again at the call site. The actual finite scale is used
    // with only a safe lower bound: Compose applies the reported scale verbatim, so an upper
    // cap here would disagree with the rendered text at 2.0+ accessibility settings.
    val textScale = if (fontScale.isFinite()) {
        fontScale.coerceAtLeast(IMMERSIVE_FONT_SCALE_SAFE_MIN)
    } else {
        IMMERSIVE_FONT_SCALE_FALLBACK
    }
    val geometryScale = renderScale.coerceIn(
        IMMERSIVE_LANDSCAPE_RENDER_SCALE_MIN,
        IMMERSIVE_LANDSCAPE_RENDER_SCALE_MAX,
    )
    val lineHeight = (IMMERSIVE_TITLE_LINE_HEIGHT_PX * geometryScale * textScale)
        .coerceAtLeast(1f)
    return ImmersiveTextBudget(
        metadataHeightPx = (IMMERSIVE_METADATA_LINE_HEIGHT_PX * geometryScale * textScale)
            .coerceAtLeast(1f),
        titleHeightPx = (lineHeight * IMMERSIVE_TITLE_MAX_LINES).coerceAtLeast(1f),
        titleLineHeightPx = lineHeight,
        subtitleHeightPx = (IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX * geometryScale * textScale)
            .coerceAtLeast(1f),
    )
}

fun resolveImmersiveLayout(
    orientation: HomeOrientation,
    windowWidthPx: Float,
    windowHeightPx: Float,
    safeBounds: ImmersiveSafeBounds,
    dockRect: HomeRect? = null,
    bottomClearancePx: Float = AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP,
    fontScale: Float = 1f,
    topChrome: HomeTopChromeSpec? = null,
    minimumPlayHeightPx: Float = IMMERSIVE_PLAY_MIN_HEIGHT_PX,
): ImmersiveHomeLayout {
    val safe = opticalSafeBounds(safeBounds, windowWidthPx, windowHeightPx)
    val safeWidth = safe.rightPx - safe.leftPx
    val safeHeight = safe.bottomPx - safe.topPx
    val safeFrame = HomeRect(safe.leftPx, safe.topPx, safeWidth, safeHeight)
    val resolvedMinimumPlayHeightPx = minimumPlayHeightPx
        .takeIf { it.isFinite() }
        ?.coerceAtLeast(1f)
        ?: IMMERSIVE_PLAY_MIN_HEIGHT_PX
    val chrome = (topChrome ?: HomePresentationPolicy.topChromeSpec(safeFrame)).let {
        it.copy(menuRect = it.menuRect.inside(safe))
    }
    val resolvedDock = (dockRect ?: resolveDefaultImmersiveDock(windowWidthPx, windowHeightPx, safe))
        .inside(safe)
    // [bottomClearancePx] is an extra gap, not the component page's full scroll padding. The
    // dock itself is the source of truth for the pager position; this keeps a moved/resized dock
    // from leaving a stale golden-canvas gap behind it.
    val dockPagerClearance = max(
        bottomClearancePx.coerceAtLeast(0f),
        IMMERSIVE_PAGER_GAP_PX,
    )
    val pagerY = (resolvedDock.y - dockPagerClearance)
        .coerceIn(safe.topPx, safe.bottomPx)

    return when (orientation) {
        HomeOrientation.PORTRAIT -> {
            val scale = min(safeWidth / 852f, safeHeight / 1846f).coerceAtLeast(.01f)
            val topAnchor = max(
                safe.topPx + safeHeight * .12f,
                chrome.menuRect.y + chrome.menuRect.h + safeHeight * .08f,
            )
            val centerWidth = safeWidth * .641f
            val availableHeight = (pagerY - topAnchor - IMMERSIVE_PAGER_GAP_PX).coerceAtLeast(1f)
            val centerHeight = min(centerWidth * 16f / 9f, availableHeight)
            val resolvedCenterWidth = centerHeight * 9f / 16f
            val centerX = safe.leftPx + safeWidth / 2f
            val center = HomeRect(
                centerX - resolvedCenterWidth / 2f,
                topAnchor,
                resolvedCenterWidth,
                centerHeight,
            )
            val centerCx = center.x + center.w / 2f
            val centerCy = center.y + center.h / 2f
            fun rearFrame(scaleW: Float, scaleH: Float, dx: Float, rotation: Float, alpha: Float, z: Float) =
                ImmersiveCardFrame(
                    HomeRect(
                        centerCx + dx - center.w * scaleW / 2f,
                        centerCy + center.h * (1f - scaleH) * .18f - center.h * scaleH / 2f,
                        center.w * scaleW,
                        center.h * scaleH,
                    ), rotation, rotation * -.82f, alpha, .58f, 2f, z,
                )
            val sideOffset = center.w * .28f
            val slots = linkedMapOf(
                ImmersiveSlot.FAR_LEFT to rearFrame(.50f, .50f, -center.w * .95f, 8.5f, 0f, 1f),
                ImmersiveSlot.NEAR_LEFT to rearFrame(.62f, .62f, -sideOffset, 5f, .48f, 2f),
                ImmersiveSlot.CENTER to ImmersiveCardFrame(
                    center, 2.5f, 0f, 1f, 1f, 12f * scale, 3f, focusFraction = 1f,
                ),
                ImmersiveSlot.NEAR_RIGHT to rearFrame(.62f, .62f, sideOffset, -2.5f, .48f, 2f),
                ImmersiveSlot.FAR_RIGHT to rearFrame(.50f, .50f, center.w * .95f, -3.5f, 0f, 1f),
            ).mapValues { (_, frame) -> frame.copy(rect = frame.rect.inside(safe)) }
            val maxPlaySize = center.w * .32f
            val playSize = min(84f * scale, maxPlaySize)
                .coerceAtLeast(minOf(resolvedMinimumPlayHeightPx, maxPlaySize).coerceAtLeast(1f))
            ImmersiveHomeLayout(
                brand = chrome.titlePosition,
                menu = chrome.menuRect,
                metadata = null,
                title = null,
                subtitle = null,
                play = HomeRect(
                    center.x + center.w - playSize * 1.35f,
                    center.y + center.h - playSize * 1.35f,
                    playSize,
                    playSize,
                ).inside(safe),
                dock = resolvedDock,
                pagerCenters = centeredPager(
                    windowWidthPx / 2f,
                    pagerY,
                    ImmersivePagerPolicy.MAX_VISIBLE,
                    36f * scale,
                    safe.leftPx,
                    safe.rightPx,
                    13f * scale / 2f,
                ),
                cardSlots = slots,
                renderScale = scale,
            )
        }

        HomeOrientation.LANDSCAPE -> {
            val renderScale = resolveImmersiveRenderScale(
                HomeOrientation.LANDSCAPE,
                safeWidth,
                safeHeight,
            )

            // Reserve the information column before solving the card geometry. This makes the
            // engine/title/Continue stack a bounded input to the gallery rather than an overlay
            // that can grow into the cards when fontScale is 1.3 or 1.5.
            val textX = max(
                windowWidthPx * IMMERSIVE_LANDSCAPE_METADATA_X_FRACTION,
                safe.leftPx + IMMERSIVE_LANDSCAPE_METADATA_MIN_SAFE_GAP_PX,
            )
            val textBudget = resolveImmersiveTextBudget(
                safeHeightPx = safeHeight,
                fontScale = fontScale,
                renderScale = renderScale,
            )
            val baseMetadataY = max(
                safe.topPx + safeHeight * .16f,
                chrome.titleRect.y + chrome.titleRect.h + 12f * renderScale,
            )
            val playHeight = (104f * renderScale)
                .coerceAtLeast(resolvedMinimumPlayHeightPx)
            val textBottom = resolveImmersiveTextBottom(pagerY, renderScale)
                .coerceAtLeast(safe.topPx)
            // Restore the landscape information order: metadata, title, optional subtitle, then
            // the launch control. The portrait card owns the launch-above-title treatment.
            val availableTextHeight = (textBottom - baseMetadataY).coerceAtLeast(0f)
            val metadataToTitleGap = 10f * renderScale
            val titleToSubtitleGap = 8f * renderScale
            val textToPlayGap = 20f * renderScale
            val minimumResolvedPlayHeight = minOf(resolvedMinimumPlayHeightPx, playHeight)
            val fullStackHeight = textBudget.metadataHeightPx + metadataToTitleGap +
                textBudget.titleHeightPx + titleToSubtitleGap + textBudget.subtitleHeightPx +
                textToPlayGap + playHeight
            val twoLineNoSubtitleHeight = textBudget.metadataHeightPx + metadataToTitleGap +
                textBudget.titleHeightPx + textToPlayGap + minimumResolvedPlayHeight
            val oneLineNoSubtitleHeight = textBudget.metadataHeightPx + metadataToTitleGap +
                textBudget.titleLineHeightPx + textToPlayGap + minimumResolvedPlayHeight
            val (resolvedTitleHeight, showSubtitle, resolvedPlayHeight) = when {
                availableTextHeight >= fullStackHeight -> {
                    Triple(textBudget.titleHeightPx, true, playHeight)
                }
                availableTextHeight >= twoLineNoSubtitleHeight -> {
                    Triple(
                        textBudget.titleHeightPx,
                        false,
                        (availableTextHeight - textBudget.metadataHeightPx - metadataToTitleGap -
                            textBudget.titleHeightPx - textToPlayGap)
                            .coerceIn(minimumResolvedPlayHeight, playHeight),
                    )
                }
                availableTextHeight >= oneLineNoSubtitleHeight -> {
                    Triple(
                        textBudget.titleLineHeightPx,
                        false,
                        (availableTextHeight - textBudget.metadataHeightPx - metadataToTitleGap -
                            textBudget.titleLineHeightPx - textToPlayGap)
                            .coerceIn(minimumResolvedPlayHeight, playHeight),
                    )
                }
                else -> Triple(
                    textBudget.titleLineHeightPx,
                    false,
                    (availableTextHeight - textBudget.metadataHeightPx - metadataToTitleGap -
                        textBudget.titleLineHeightPx - textToPlayGap)
                        .coerceIn(1f, playHeight),
                )
            }
            val stackHeight = textBudget.metadataHeightPx + metadataToTitleGap +
                resolvedTitleHeight + (if (showSubtitle) {
                    titleToSubtitleGap + textBudget.subtitleHeightPx
                } else 0f) + textToPlayGap + resolvedPlayHeight
            val metadataOffset = resolveLandscapeMetadataOffset(
                baseY = baseMetadataY,
                textBottom = textBottom,
                minimumStackHeight = stackHeight,
            )
            val metadataY = baseMetadataY + metadataOffset
            val titleY = metadataY + textBudget.metadataHeightPx + metadataToTitleGap
            val subtitleY = titleY + resolvedTitleHeight + titleToSubtitleGap
            val textBottomY = if (showSubtitle) {
                subtitleY + textBudget.subtitleHeightPx
            } else {
                titleY + resolvedTitleHeight
            }
            val playY = textBottomY + textToPlayGap

            val cardTop = max(
                safe.topPx + safeHeight * .08f,
                chrome.menuRect.y + chrome.menuRect.h + 16f * renderScale,
            )
            val centerHeight = min(
                safeHeight * IMMERSIVE_LANDSCAPE_CENTER_HEIGHT_FRACTION,
                (pagerY - cardTop - IMMERSIVE_PAGER_GAP_PX).coerceAtLeast(1f),
            )
            val centerWidth = centerHeight * (357f / 504f)
            val centerY = cardTop + centerHeight / 2f
            val nearHeight = centerHeight * .7917f
            val nearWidth = nearHeight * (167f / 399f)
            val farHeight = centerHeight * .7123f
            val farWidth = farHeight * (142f / 359f)
            val nearCenterDistance = safeWidth * (276f / 1672f)
            val farCenterDistance = safeWidth * (448.5f / 1672f)
            val galleryExtent = max(
                centerWidth / 2f,
                max(
                    nearCenterDistance + nearWidth / 2f,
                    farCenterDistance + farWidth / 2f,
                ),
            )
            val desiredGalleryCenterX =
                safe.leftPx + safeWidth * IMMERSIVE_LANDSCAPE_CENTER_FRACTION
            val minGalleryCenterX = safe.leftPx + galleryExtent
            val maxGalleryCenterX = safe.rightPx - galleryExtent
            val galleryCenterX = if (minGalleryCenterX <= maxGalleryCenterX) {
                desiredGalleryCenterX.coerceIn(minGalleryCenterX, maxGalleryCenterX)
            } else {
                safe.leftPx + safeWidth / 2f
            }
            fun frameAt(
                cx: Float,
                cy: Float,
                width: Float,
                height: Float,
                rotationZ: Float,
                rotationY: Float,
                alpha: Float,
                saturation: Float,
                elevation: Float,
                z: Float,
            ) = ImmersiveCardFrame(
                HomeRect(cx - width / 2f, cy - height / 2f, width, height).inside(safe),
                rotationZ, rotationY, alpha, saturation, elevation, z,
            )
            val nearY = centerY + centerHeight * .03f
            val farY = centerY + centerHeight * .045f
            val edgeWidth = max(1f, farWidth)
            val slots = linkedMapOf(
                ImmersiveSlot.EXIT_LEFT to frameAt(safe.leftPx + edgeWidth / 2f, farY, edgeWidth, farHeight, -7f, -15f, 0f, .4f, 0f, 0f),
                ImmersiveSlot.FAR_LEFT to frameAt(galleryCenterX - farCenterDistance, farY, farWidth, farHeight, -5f, -10f, .62f, .58f, 1f, 1f),
                ImmersiveSlot.NEAR_LEFT to frameAt(galleryCenterX - nearCenterDistance, nearY, nearWidth, nearHeight, -5f, -6f, .82f, .72f, 3f, 2f),
                ImmersiveSlot.CENTER to frameAt(galleryCenterX, centerY, centerWidth, centerHeight, 0f, 0f, 1f, 1f, 16f, 3f),
                ImmersiveSlot.NEAR_RIGHT to frameAt(galleryCenterX + nearCenterDistance, nearY, nearWidth, nearHeight, 5f, 6f, .82f, .72f, 3f, 2f),
                ImmersiveSlot.FAR_RIGHT to frameAt(galleryCenterX + farCenterDistance, farY, farWidth, farHeight, 5f, 10f, .62f, .58f, 1f, 1f),
                ImmersiveSlot.ENTER_RIGHT to frameAt(safe.rightPx - edgeWidth / 2f, farY, edgeWidth, farHeight, 7f, 15f, 0f, .4f, 0f, 0f),
            )
            // The left information column never exceeds 34% of the safe width and always
            // keeps a measured gap from the left-most visible card.
            val farLeftCard = slots.getValue(ImmersiveSlot.FAR_LEFT).rect
            val textRight = min(
                safe.leftPx + safeWidth * .42f,
                farLeftCard.x - LANDSCAPE_TEXT_CARD_GAP_PX * renderScale,
            )
            val titleWidth = (textRight - textX).coerceAtLeast(1f)
            val supportingTextWidth = titleWidth
            ImmersiveHomeLayout(
                brand = chrome.titlePosition,
                menu = chrome.menuRect,
                metadata = HomeRect(textX, metadataY, supportingTextWidth, textBudget.metadataHeightPx).inside(safe),
                title = HomeRect(textX, titleY, titleWidth, resolvedTitleHeight).inside(safe),
                subtitle = if (showSubtitle) {
                    HomeRect(textX, subtitleY, supportingTextWidth, textBudget.subtitleHeightPx).inside(safe)
                } else {
                    null
                },
                play = HomeRect(textX, playY, min(292f * renderScale, titleWidth), resolvedPlayHeight).inside(safe),
                dock = resolvedDock,
                pagerCenters = centeredPager(
                    windowWidthPx / 2f,
                    pagerY,
                    ImmersivePagerPolicy.MAX_VISIBLE,
                    40f * renderScale,
                    safe.leftPx,
                    safe.rightPx,
                    28f * renderScale / 2f,
                ),
                cardSlots = slots,
                renderScale = renderScale,
            )
        }
    }
}

private fun resolveDefaultImmersiveDock(
    windowWidthPx: Float,
    windowHeightPx: Float,
    safe: ImmersiveSafeBounds,
): HomeRect = resolveAdaptiveUiMetrics(
    windowWidthDp = windowWidthPx,
    windowHeightDp = windowHeightPx,
    safeLeftDp = safe.leftPx,
    safeTopDp = safe.topPx,
    safeRightDp = windowWidthPx - safe.rightPx,
    safeBottomDp = windowHeightPx - safe.bottomPx,
).dockRect

/**
 * Converts asymmetric physical insets into one optical frame. Each side keeps its own inset:
 * a cutout on the left only adds left padding, a cutout on the right only adds right padding,
 * so rotating the device never produces a symmetric blank gap on the opposite side.
 */
fun opticalSafeBounds(
    safeBounds: ImmersiveSafeBounds,
    windowWidthPx: Float,
    windowHeightPx: Float,
): ImmersiveSafeBounds {
    val normalized = safeBounds.normalized(windowWidthPx, windowHeightPx)
    val leftInset = normalized.leftPx
    val rightInset = windowWidthPx - normalized.rightPx
    val half = (windowWidthPx / 2f).coerceAtLeast(0f)
    return ImmersiveSafeBounds(
        leftPx = leftInset.coerceAtMost(half),
        topPx = normalized.topPx,
        rightPx = (windowWidthPx - rightInset).coerceAtLeast(half),
        bottomPx = normalized.bottomPx,
    )
}

/** The pager lane keeps at least this 12dp-equivalent gap from the front card bottom (design px). */
const val IMMERSIVE_PAGER_GAP_PX = 32f

/** Landscape pager indicator height mirrored by [ImmersivePager]. */
const val IMMERSIVE_PAGER_INDICATOR_HEIGHT_PX = 5f

/** Extra information-to-indicator gap after subtracting the indicator half-height. */
const val IMMERSIVE_PAGER_TEXT_GAP_PX = 16f

const val IMMERSIVE_PAGER_INDICATOR_HALF_HEIGHT_PX = IMMERSIVE_PAGER_INDICATOR_HEIGHT_PX / 2f

internal fun resolveImmersiveTextBottom(
    pagerY: Float,
    renderScale: Float,
): Float = pagerY -
    IMMERSIVE_PAGER_INDICATOR_HALF_HEIGHT_PX * renderScale -
    IMMERSIVE_PAGER_TEXT_GAP_PX * renderScale

/** The landscape information column keeps a 24dp-equivalent gap from the left-most card (design px). */
const val LANDSCAPE_TEXT_CARD_GAP_PX = 63f

/** Explicit Compose line boxes mirrored by [resolveImmersiveTextBudget]. */
const val IMMERSIVE_METADATA_LINE_HEIGHT_PX = 44f
const val IMMERSIVE_TITLE_LINE_HEIGHT_PX = 72f
const val IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX = 36f

/**
 * Pure pager window policy for the immersive gallery: one dot/bar per game up to seven,
 * with a stable seven-item window centered on the selection for longer libraries.
 */
object ImmersivePagerPolicy {
    const val MAX_VISIBLE = 7

    fun visibleCount(count: Int): Int = when {
        count <= 1 -> 0
        count <= MAX_VISIBLE -> count
        else -> MAX_VISIBLE
    }

    fun windowStart(count: Int, selected: Int): Int {
        val visible = visibleCount(count)
        if (visible == 0) return 0
        if (visible == count) return 0
        val safeSelected = Math.floorMod(selected, count)
        return Math.floorMod(safeSelected - visible / 2, count)
    }

    fun visibleIndexes(count: Int, selected: Int): List<Int> {
        val visible = visibleCount(count)
        if (visible == 0) return emptyList()
        if (visible == count) return (0 until count).toList()
        val start = windowStart(count, selected)
        return List(visible) { offset -> Math.floorMod(start + offset, count) }
    }

    fun visibleCenters(centers: List<HomePoint>, count: Int): List<HomePoint> {
        if (centers.isEmpty() || count <= 0) return emptyList()
        val visible = count.coerceAtMost(centers.size)
        val centerX = (centers.first().x + centers.last().x) / 2f
        val centerY = centers[centers.lastIndex / 2].y
        val gap = centers.getOrNull(1)?.let { it.x - centers.first().x } ?: 0f
        return List(visible) { index ->
            HomePoint(
                centerX + (index - (visible - 1) / 2f) * gap,
                centerY,
            )
        }
    }
}

/** Converts pointer velocity into the gallery-track velocity used by [GalleryTrackController]. */
internal fun dragVelocitySlotsPerSecond(
    pointerVelocityPxPerSecond: Float,
    slotDistancePx: Float,
): Float = if (slotDistancePx > 0f) {
    -pointerVelocityPxPerSecond / slotDistancePx
} else {
    0f
}

internal data class ImmersiveBackdropTrack(
    val leftIndex: Int,
    val rightIndex: Int,
    val fraction: Float,
)

internal data class ImmersiveBackdropRetention(
    val indexes: List<Int>,
    val transitioning: Boolean,
)

internal fun resolveImmersiveBackdropTrack(
    visualPosition: Float,
    gameCount: Int,
): ImmersiveBackdropTrack {
    if (gameCount <= 0 || !visualPosition.isFinite()) {
        return ImmersiveBackdropTrack(-1, -1, 0f)
    }
    val leftPosition = kotlin.math.floor(visualPosition).toInt()
    return ImmersiveBackdropTrack(
        leftIndex = Math.floorMod(leftPosition, gameCount),
        rightIndex = Math.floorMod(leftPosition + 1, gameCount),
        fraction = (visualPosition - leftPosition).coerceIn(0f, 1f),
    )
}

internal fun resolveImmersiveBackdropRetention(
    visualPosition: Float,
    gameCount: Int,
): ImmersiveBackdropRetention {
    val track = resolveImmersiveBackdropTrack(visualPosition, gameCount)
    if (track.leftIndex < 0) return ImmersiveBackdropRetention(emptyList(), transitioning = false)
    val transitioning = track.leftIndex != track.rightIndex && track.fraction > 0f
    val indexes = if (transitioning) {
        listOf(track.leftIndex, track.rightIndex).distinct()
    } else {
        listOf(track.leftIndex)
    }
    return ImmersiveBackdropRetention(indexes, transitioning)
}

private fun goldenImmersiveLayout(orientation: HomeOrientation): ImmersiveHomeLayout {
    val anchors = HomeGalleryGeometryPolicy.referenceAnchors(orientation)
    val slots = anchors.heroSlots.associate { slot ->
        val immersiveSlot = when (slot.role) {
            HomeCardSlot.FAR_LEFT -> ImmersiveSlot.FAR_LEFT
            HomeCardSlot.NEAR_LEFT -> ImmersiveSlot.NEAR_LEFT
            HomeCardSlot.CENTER -> ImmersiveSlot.CENTER
            HomeCardSlot.NEAR_RIGHT -> ImmersiveSlot.NEAR_RIGHT
            HomeCardSlot.FAR_RIGHT -> ImmersiveSlot.FAR_RIGHT
        }
        immersiveSlot to ImmersiveCardFrame(
            rect = slot.rect,
            rotationZ = slot.rotationDeg,
            alpha = slot.alpha,
            saturation = if (slot.role == HomeCardSlot.CENTER) 1f else .72f,
            elevationPx = if (slot.role == HomeCardSlot.CENTER) 16f else 2f,
            zIndex = HomeGalleryGeometryPolicy.landscapeCardZIndex(slot.role),
            focusFraction = if (slot.role == HomeCardSlot.CENTER) 1f else 0f,
        )
    }
    val portraitCenter = slots[ImmersiveSlot.CENTER]
    val portraitSlots = if (orientation == HomeOrientation.PORTRAIT && portraitCenter != null) {
        val center = portraitCenter.rect
        val sideWidth = center.w * .62f
        val sideHeight = center.h * .62f
        val sideOffset = center.w * .28f
        val sideY = center.y + center.h * .26f
        slots + linkedMapOf(
            ImmersiveSlot.FAR_LEFT to ImmersiveCardFrame(
                HomeRect(
                    center.x - center.w * .95f - sideWidth / 2f,
                    center.y + center.h * .42f,
                    sideWidth * .5f,
                    sideHeight * .5f,
                ),
                rotationZ = -6.5f, rotationY = -3f, alpha = 0f, saturation = .58f, elevationPx = 1f, zIndex = 1f,
            ),
            ImmersiveSlot.NEAR_LEFT to ImmersiveCardFrame(
                HomeRect(
                    center.x + center.w / 2f - sideOffset - sideWidth / 2f,
                    sideY,
                    sideWidth,
                    sideHeight,
                ),
                rotationZ = -4.5f, rotationY = -2f, alpha = .48f, saturation = .72f, elevationPx = 2f, zIndex = 2f,
            ),
            ImmersiveSlot.NEAR_RIGHT to ImmersiveCardFrame(
                HomeRect(
                    center.x + center.w / 2f + sideOffset - sideWidth / 2f,
                    sideY,
                    sideWidth,
                    sideHeight,
                ),
                rotationZ = 4.5f, rotationY = 2f, alpha = .48f, saturation = .72f, elevationPx = 2f, zIndex = 2f,
            ),
            ImmersiveSlot.FAR_RIGHT to ImmersiveCardFrame(
                HomeRect(
                    center.x + center.w * .95f - sideWidth / 2f,
                    center.y + center.h * .42f,
                    sideWidth * .5f,
                    sideHeight * .5f,
                ),
                rotationZ = 6.5f, rotationY = 3f, alpha = 0f, saturation = .58f, elevationPx = 1f, zIndex = 1f,
            ),
        )
    } else {
        slots
    }
    val centerSlot = portraitSlots[ImmersiveSlot.CENTER]
    val galleryCenterX = centerSlot?.let { it.rect.x + it.rect.w / 2f }
        ?: anchors.pagerCenters.first().x
    val pagerCenters = (0 until ImmersivePagerPolicy.MAX_VISIBLE).map { index ->
        val gap = if (orientation == HomeOrientation.PORTRAIT) 36f else 40f
        HomePoint(
            galleryCenterX + (index - (ImmersivePagerPolicy.MAX_VISIBLE - 1) / 2f) * gap,
            anchors.pagerBaseline,
        )
    }
    return ImmersiveHomeLayout(
        brand = anchors.brandOrigin,
        menu = anchors.menuRect,
        metadata = anchors.metadataOrigin.takeIf { orientation == HomeOrientation.LANDSCAPE }?.let { HomeRect(it.x, 150f, 408f, 34f) },
        title = anchors.titleOrigin.takeIf { orientation == HomeOrientation.LANDSCAPE }?.let { HomeRect(it.x, 322f, 408f, 170f) },
        subtitle = anchors.subtitleOrigin.takeIf { orientation == HomeOrientation.LANDSCAPE }?.let { HomeRect(it.x, 508f, 408f, 36f) },
        play = anchors.playRect.let { if (orientation == HomeOrientation.LANDSCAPE) it.copy(y = 204f, h = 104f) else it },
        dock = anchors.dockRect,
        pagerCenters = pagerCenters,
        cardSlots = portraitSlots,
    )
}

private fun ImmersiveSafeBounds.normalized(windowWidth: Float, windowHeight: Float): ImmersiveSafeBounds {
    val left = leftPx.coerceIn(0f, windowWidth.coerceAtLeast(0f))
    val top = topPx.coerceIn(0f, windowHeight.coerceAtLeast(0f))
    val right = rightPx.coerceIn(left + .01f, windowWidth.coerceAtLeast(left + .01f))
    val bottom = bottomPx.coerceIn(top + .01f, windowHeight.coerceAtLeast(top + .01f))
    return ImmersiveSafeBounds(left, top, right, bottom)
}

private fun HomeRect.shifted(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)

private fun HomeRect.inside(bounds: ImmersiveSafeBounds): HomeRect {
    val width = w.coerceIn(0f, bounds.rightPx - bounds.leftPx)
    val height = h.coerceIn(0f, bounds.bottomPx - bounds.topPx)
    return HomeRect(
        x = x.coerceIn(bounds.leftPx, bounds.rightPx - width),
        y = y.coerceIn(bounds.topPx, bounds.bottomPx - height),
        w = width,
        h = height,
    )
}

internal fun resolveLandscapeMetadataOffset(
    baseY: Float,
    textBottom: Float,
    minimumStackHeight: Float,
    maxOffsetPx: Float = IMMERSIVE_LANDSCAPE_METADATA_MAX_DOWNWARD_OFFSET_PX,
): Float {
    if (!baseY.isFinite() || !textBottom.isFinite() || !minimumStackHeight.isFinite()) return 0f
    return (textBottom - baseY - minimumStackHeight)
        .coerceAtLeast(0f)
        .coerceAtMost(maxOffsetPx.coerceAtLeast(0f))
}

private fun centeredPager(
    idealCenterX: Float,
    y: Float,
    count: Int,
    gap: Float,
    safeLeft: Float = Float.NEGATIVE_INFINITY,
    safeRight: Float = Float.POSITIVE_INFINITY,
    itemHalfWidth: Float = 0f,
): List<HomePoint> {
    if (count <= 0) return emptyList()
    val halfSpan = (count - 1) * gap / 2f + itemHalfWidth.coerceAtLeast(0f)
    val centerX = if (safeLeft.isFinite() && safeRight.isFinite()) {
        val minCenter = safeLeft + halfSpan
        val maxCenter = safeRight - halfSpan
        if (minCenter <= maxCenter) {
            idealCenterX.coerceIn(minCenter, maxCenter)
        } else {
            (safeLeft + safeRight) / 2f
        }
    } else {
        idealCenterX
    }
    val start = centerX - (count - 1) * gap / 2f
    return List(count) { HomePoint(start + it * gap, y) }
}

private fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress

enum class HomeCardSlot {
    FAR_LEFT,
    NEAR_LEFT,
    CENTER,
    NEAR_RIGHT,
    FAR_RIGHT,
}

data class HomeSlotTransform(
    val slotIndex: Int,
    val rect: HomeRect,
    val rotationDeg: Float = 0f,
    val alpha: Float = 1f,
    val role: HomeCardSlot = HomeCardSlot.CENTER,
)

data class HomePortraitCardSpec(
    val rect: HomeRect,
    val translationX: Float,
    val translationY: Float,
    val rotationDeg: Float,
    val alpha: Float,
    val zIndex: Float,
)

data class HomeGestureRegion(
    val bounds: HomeRect,
    val excludedRects: List<HomeRect> = emptyList(),
) {
    fun contains(point: HomePoint): Boolean =
        point.x >= bounds.x &&
            point.x <= bounds.x + bounds.w &&
            point.y >= bounds.y &&
            point.y <= bounds.y + bounds.h &&
            excludedRects.none { excluded ->
                point.x >= excluded.x &&
                    point.x <= excluded.x + excluded.w &&
                    point.y >= excluded.y &&
                    point.y <= excluded.y + excluded.h
            }
}

data class HomeGalleryAnchors(
    val brandOrigin: HomePoint,
    val menuRect: HomeRect,
    val playRect: HomeRect,
    val dockRect: HomeRect,
    val metadataOrigin: HomePoint = HomePoint(0f, 0f),
    val titleOrigin: HomePoint = HomePoint(0f, 0f),
    val subtitleOrigin: HomePoint = HomePoint(0f, 0f),
    val pagerBaseline: Float,
    val pagerCenters: List<HomePoint>,
    val heroSlots: List<HomeSlotTransform>,
) {
    val brand: HomePoint get() = brandOrigin
    val menu: HomeRect get() = menuRect
    val play: HomeRect get() = playRect
    val dock: HomeRect get() = dockRect
}

enum class HomeIndicatorStyle {
    DOT,
    BAR,
}

data class HomeIndicatorSpec(
    val visible: Boolean,
    val visibleCount: Int,
    val selectedIndex: Int,
    val style: HomeIndicatorStyle,
)

object HomeGalleryGeometryPolicy {
    const val LANDSCAPE_MIRROR_AXIS_X = 1079.5f

    /** Maps the parent's safe-area render kind to the immersive gallery's design orientation. */
    fun orientationForRenderKind(renderKind: HomeRenderKind): HomeOrientation = when (renderKind) {
        HomeRenderKind.IMMERSIVE_PORTRAIT,
        HomeRenderKind.COMPONENTS_PORTRAIT,
        -> HomeOrientation.PORTRAIT
        HomeRenderKind.IMMERSIVE_LANDSCAPE,
        HomeRenderKind.COMPONENTS_LANDSCAPE,
        -> HomeOrientation.LANDSCAPE
    }

    fun landscapeCardZIndex(role: HomeCardSlot): Float = when (role) {
        HomeCardSlot.FAR_LEFT, HomeCardSlot.FAR_RIGHT -> 1f
        HomeCardSlot.NEAR_LEFT, HomeCardSlot.NEAR_RIGHT -> 2f
        HomeCardSlot.CENTER -> 3f
    }

    fun portraitCardZIndex(depth: HomeCardDepth, rearDistance: Int): Float = when (depth) {
        HomeCardDepth.FRONT -> 3f
        HomeCardDepth.REAR -> 2f - rearDistance.coerceAtLeast(1) * .01f
    }

    fun portraitCardSpec(gameIndex: Int, selectedIndex: Int): HomePortraitCardSpec {
        val front = referenceAnchors(HomeOrientation.PORTRAIT).heroSlots.single()
        val rearDistance = abs(gameIndex - selectedIndex)
        if (rearDistance == 0) {
            return HomePortraitCardSpec(
                rect = front.rect,
                translationX = 0f,
                translationY = 0f,
                rotationDeg = front.rotationDeg,
                alpha = 1f,
                zIndex = portraitCardZIndex(HomeCardDepth.FRONT, 0),
            )
        }

        val depth = rearDistance.coerceAtLeast(1)
        val side = if (gameIndex < selectedIndex) -1f else 1f
        return HomePortraitCardSpec(
            rect = front.rect,
            translationX = front.rect.w * (.12f + depth * .035f) * side,
            translationY = -front.rect.h * (.02f - depth * .008f),
            rotationDeg = front.rotationDeg - side * (4f + depth * .75f),
            alpha = (.44f - depth * .035f).coerceIn(.22f, .44f),
            zIndex = portraitCardZIndex(HomeCardDepth.REAR, depth),
        )
    }

    fun landscapeCardSaturation(slot: HomeCardSlot): Float =
        if (slot == HomeCardSlot.CENTER) 1f else .72f

    fun dockItemWidth(
        outerWidth: Float,
        horizontalPadding: Float,
        itemCount: Int,
    ): Float {
        if (itemCount <= 0) return 0f
        return ((outerWidth - horizontalPadding * 2f).coerceAtLeast(0f) / itemCount)
    }

    fun scaledTextWidth(designWidth: Float, frameScale: Float): Float =
        (designWidth * frameScale).coerceAtLeast(0f)

    fun preserveSelectedIndex(
        previousSelectedKey: Long?,
        previousSelectedIndex: Int,
        updatedKeys: List<Long>,
    ): Int {
        if (updatedKeys.isEmpty()) return 0
        val keyIndex = previousSelectedKey?.let(updatedKeys::indexOf) ?: -1
        return if (keyIndex >= 0) keyIndex else previousSelectedIndex.coerceIn(0, updatedKeys.lastIndex)
    }

    fun galleryGestureRegions(
        anchors: HomeGalleryAnchors,
        orientation: HomeOrientation,
        frameScale: Float = 1f,
    ): List<HomeGestureRegion> = when (orientation) {
        HomeOrientation.PORTRAIT -> listOf(
            HomeGestureRegion(
                bounds = anchors.heroSlots.single().rect,
                excludedRects = listOf(anchors.menuRect, anchors.playRect, anchors.dockRect),
            ),
        )
        HomeOrientation.LANDSCAPE -> anchors.heroSlots.map { slot ->
            HomeGestureRegion(
                bounds = slot.rect,
                excludedRects = listOf(
                    anchors.menuRect,
                    anchors.playRect,
                    anchors.dockRect,
                    HomeRect(anchors.metadataOrigin.x, anchors.metadataOrigin.y, scaledTextWidth(620f, frameScale), scaledTextWidth(32f, frameScale)),
                    HomeRect(anchors.titleOrigin.x, anchors.titleOrigin.y, scaledTextWidth(700f, frameScale), scaledTextWidth(74f, frameScale)),
                    HomeRect(anchors.subtitleOrigin.x, anchors.subtitleOrigin.y, scaledTextWidth(620f, frameScale), scaledTextWidth(34f, frameScale)),
                ),
            )
        }
    }

    /**
     * Gesture regions derived from the resolved layout instead of the reference anchors:
     * every exclusion uses the actual current rect, so menu, play, dock and the budget-sized
     * metadata/title/subtitle rects stay in sync with fontScale and window changes.
     */
    fun galleryGestureRegions(
        layout: ImmersiveHomeLayout,
        orientation: HomeOrientation,
    ): List<HomeGestureRegion> = when (orientation) {
        HomeOrientation.PORTRAIT -> listOf(
            HomeGestureRegion(
                bounds = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect,
                excludedRects = listOf(layout.menu, layout.play, layout.dock),
            ),
        )
        HomeOrientation.LANDSCAPE -> layout.cardSlots.values.map { frame ->
            HomeGestureRegion(
                bounds = frame.rect,
                excludedRects = listOfNotNull(
                    layout.menu,
                    layout.play,
                    layout.dock,
                    layout.metadata,
                    layout.title,
                    layout.subtitle,
                ),
            )
        }
    }

    fun designCanvas(orientation: HomeOrientation): DesignCanvas = when (orientation) {
        HomeOrientation.PORTRAIT -> DesignCanvas(orientation, 852f, 1846f)
        HomeOrientation.LANDSCAPE -> DesignCanvas(orientation, 1672f, 941f)
    }

    fun uniformScale(
        viewportW: Float,
        viewportH: Float,
        designW: Float,
        designH: Float,
    ): Float {
        if (viewportW <= 0f || viewportH <= 0f || designW <= 0f || designH <= 0f) return 0f
        return min(viewportW / designW, viewportH / designH)
    }

    fun centerOffset(
        viewportW: Float,
        viewportH: Float,
        designW: Float,
        designH: Float,
        scale: Float,
    ): HomePoint = HomePoint(
        x = (viewportW - designW * scale) / 2f,
        y = (viewportH - designH * scale) / 2f,
    )

    fun designToViewport(
        designPoint: HomePoint,
        scale: Float,
        offset: HomePoint,
    ): HomePoint = HomePoint(
        x = designPoint.x * scale + offset.x,
        y = designPoint.y * scale + offset.y,
    )

    fun viewportToDesign(
        viewportPoint: HomePoint,
        scale: Float,
        offset: HomePoint,
    ): HomePoint {
        if (scale == 0f) return HomePoint(0f, 0f)
        return HomePoint(
            x = (viewportPoint.x - offset.x) / scale,
            y = (viewportPoint.y - offset.y) / scale,
        )
    }

    fun referenceAnchors(orientation: HomeOrientation): HomeGalleryAnchors = when (orientation) {
        HomeOrientation.PORTRAIT -> HomeGalleryAnchors(
            brandOrigin = HomePoint(61f, 52f),
            menuRect = HomeRect(722.5f, 52f, 67f, 67f),
            playRect = HomeRect(565f, 1186f, 84f, 84f),
            dockRect = HomeRect(121f, 1591f, 606f, 147f),
            pagerBaseline = 1495f,
            pagerCenters = listOf(
                HomePoint(394f, 1495f),
                HomePoint(426f, 1495f),
                HomePoint(458f, 1495f),
            ),
            heroSlots = listOf(
                HomeSlotTransform(
                    slotIndex = 0,
                    rect = HomeRect(160f, 340f, 546f, 970.67f),
                    rotationDeg = 2.5f,
                    role = HomeCardSlot.CENTER,
                ),
            ),
        )

        HomeOrientation.LANDSCAPE -> landscapeReferenceAnchors()
    }

    fun scaledAnchors(
        orientation: HomeOrientation,
        viewportW: Float,
        viewportH: Float,
    ): HomeGalleryAnchors {
        val canvas = designCanvas(orientation)
        val scale = uniformScale(viewportW, viewportH, canvas.width, canvas.height)
        val offset = centerOffset(viewportW, viewportH, canvas.width, canvas.height, scale)
        val reference = referenceAnchors(orientation)
        return reference.copy(
            brandOrigin = designToViewport(reference.brandOrigin, scale, offset),
            menuRect = scaleRect(reference.menuRect, scale, offset),
            playRect = scaleRect(reference.playRect, scale, offset),
            dockRect = scaleRect(reference.dockRect, scale, offset),
            metadataOrigin = designToViewport(reference.metadataOrigin, scale, offset),
            titleOrigin = designToViewport(reference.titleOrigin, scale, offset),
            subtitleOrigin = designToViewport(reference.subtitleOrigin, scale, offset),
            pagerBaseline = offset.y + reference.pagerBaseline * scale,
            pagerCenters = reference.pagerCenters.map { designToViewport(it, scale, offset) },
            heroSlots = reference.heroSlots.map { slot ->
                slot.copy(rect = scaleRect(slot.rect, scale, offset))
            },
        )
    }

    fun slotWindow(selectedIndex: Int, slotCount: Int): List<Int> {
        if (slotCount <= 1) return listOf(0)

        val visibleCount = min(slotCount, MAX_VISIBLE_SLOTS)
        val safeSelectedIndex = Math.floorMod(selectedIndex, slotCount)
        if (visibleCount == slotCount) return (0 until slotCount).toList()
        val start = Math.floorMod(safeSelectedIndex - visibleCount / 2, slotCount)
        return List(visibleCount) { offset -> Math.floorMod(start + offset, slotCount) }
    }

    fun indicator(
        count: Int,
        selectedIndex: Int,
        orientation: HomeOrientation,
    ): HomeIndicatorSpec {
        val style = if (orientation == HomeOrientation.PORTRAIT) {
            HomeIndicatorStyle.DOT
        } else {
            HomeIndicatorStyle.BAR
        }
        if (count <= 1) return HomeIndicatorSpec(false, 0, 0, style)

        val window = slotWindow(selectedIndex, count)
        val safeSelectedIndex = Math.floorMod(selectedIndex, count)
        return HomeIndicatorSpec(
            visible = true,
            visibleCount = window.size,
            selectedIndex = window.indexOf(safeSelectedIndex),
            style = style,
        )
    }

    /**
     * Returns only the indicator positions that correspond to real cards. The portrait
     * reference uses a 32 px rhythm centered on the 852 px canvas; landscape keeps the
     * measured six-bar rhythm and takes a centered contiguous subset for shorter lists.
     */
    fun indicatorCenters(
        anchors: HomeGalleryAnchors,
        count: Int,
        orientation: HomeOrientation,
        frameScale: Float = 1f,
        frameOffset: HomePoint = HomePoint(0f, 0f),
    ): List<HomePoint> {
        val visibleCount = count.coerceIn(0, MAX_VISIBLE_SLOTS)
        if (visibleCount == 0) return emptyList()
        val reference = referenceAnchors(orientation)
        fun toViewport(point: HomePoint): HomePoint = designToViewport(point, frameScale, frameOffset)
        return when (orientation) {
            HomeOrientation.PORTRAIT -> {
                val centerX = designCanvas(orientation).width / 2f
                val gap = 32f
                val startX = centerX - (visibleCount - 1) * gap / 2f
                (0 until visibleCount).map { index ->
                    toViewport(HomePoint(startX + index * gap, reference.pagerBaseline))
                }
            }

            HomeOrientation.LANDSCAPE -> {
                val centerX = designCanvas(orientation).width / 2f
                val gap = 40f
                val startX = centerX - (visibleCount - 1) * gap / 2f
                (0 until visibleCount).map { index ->
                    toViewport(HomePoint(startX + index * gap, reference.pagerBaseline))
                }
            }
        }
    }

    private fun landscapeReferenceAnchors(): HomeGalleryAnchors {
        val farLeft = HomeSlotTransform(
            slotIndex = 0,
            rect = HomeRect(560f, 264f, 142f, 359f),
            rotationDeg = -5f,
            alpha = .62f,
            role = HomeCardSlot.FAR_LEFT,
        )
        val nearLeft = HomeSlotTransform(
            slotIndex = 1,
            rect = HomeRect(720f, 237f, 167f, 399f),
            rotationDeg = -5f,
            alpha = .82f,
            role = HomeCardSlot.NEAR_LEFT,
        )
        val center = HomeSlotTransform(
            slotIndex = 2,
            rect = HomeRect(901f, 170f, 357f, 504f),
            alpha = 1f,
            role = HomeCardSlot.CENTER,
        )

        return HomeGalleryAnchors(
            brandOrigin = HomePoint(65f, 50f),
            menuRect = HomeRect(1551.5f, 33.5f, 69f, 69f),
            playRect = HomeRect(87f, 496f, 292f, 99f),
            dockRect = HomeRect(467f, 786f, 737f, 100f),
            metadataOrigin = HomePoint(89f, 264f),
            titleOrigin = HomePoint(89f, 322f),
            subtitleOrigin = HomePoint(89f, 428f),
            pagerBaseline = 740f,
            pagerCenters = listOf(
                HomePoint(977f, 715f),
                HomePoint(1018f, 715f),
                HomePoint(1058f, 715f),
                HomePoint(1098f, 715f),
                HomePoint(1138f, 715f),
                HomePoint(1178f, 715f),
            ),
            heroSlots = listOf(
                farLeft,
                nearLeft,
                center,
                mirrorSlot(nearLeft, 3, HomeCardSlot.NEAR_RIGHT),
                mirrorSlot(farLeft, 4, HomeCardSlot.FAR_RIGHT),
            ),
        )
    }

    private fun mirrorSlot(
        left: HomeSlotTransform,
        slotIndex: Int,
        role: HomeCardSlot,
    ): HomeSlotTransform = left.copy(
        slotIndex = slotIndex,
        role = role,
        rect = left.rect.copy(
            x = 2f * LANDSCAPE_MIRROR_AXIS_X - (left.rect.x + left.rect.w),
        ),
        rotationDeg = -left.rotationDeg,
    )

    private fun scaleRect(rect: HomeRect, scale: Float, offset: HomePoint): HomeRect {
        val topLeft = designToViewport(HomePoint(rect.x, rect.y), scale, offset)
        return HomeRect(
            x = topLeft.x,
            y = topLeft.y,
            w = rect.w * scale,
            h = rect.h * scale,
        )
    }

    private const val MAX_VISIBLE_SLOTS = 7
}
