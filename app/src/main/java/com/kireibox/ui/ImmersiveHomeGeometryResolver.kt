package com.kireibox.ui

import kotlin.math.min

private const val IMMERSIVE_TITLE_MAX_LINES = 2f

/**
 * Resolves the scale used by the immersive renderer from the same geometry that positions it.
 * Landscape is height-driven: the short 1339x597 frame must be allowed below the adaptive
 * components-page floor, while very tall frames stop at the reference scale plus a small bound.
 */
fun resolveImmersiveRenderScale(
    orientation: HomeOrientation,
    safeWidthPx: Float,
    safeHeightPx: Float,
): Float =
    when (orientation) {
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
    renderScale: Float =
        resolveImmersiveRenderScale(
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
    val textScale =
        if (fontScale.isFinite()) {
            fontScale.coerceAtLeast(IMMERSIVE_FONT_SCALE_SAFE_MIN)
        } else {
            IMMERSIVE_FONT_SCALE_FALLBACK
        }
    val geometryScale =
        renderScale.coerceIn(
            IMMERSIVE_LANDSCAPE_RENDER_SCALE_MIN,
            IMMERSIVE_LANDSCAPE_RENDER_SCALE_MAX,
        )
    val lineHeight =
        (IMMERSIVE_TITLE_LINE_HEIGHT_PX * geometryScale * textScale)
            .coerceAtLeast(1f)
    return ImmersiveTextBudget(
        metadataHeightPx =
            (IMMERSIVE_METADATA_LINE_HEIGHT_PX * geometryScale * textScale)
                .coerceAtLeast(1f),
        titleHeightPx = (lineHeight * IMMERSIVE_TITLE_MAX_LINES).coerceAtLeast(1f),
        titleLineHeightPx = lineHeight,
        subtitleHeightPx =
            (IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX * geometryScale * textScale)
                .coerceAtLeast(1f),
    )
}

/** The Dock the immersive home is drawn with: the shared rect, lowered in landscape (see [resolveAdaptiveUiMetrics]). */
internal fun resolveDefaultImmersiveDock(
    windowWidthPx: Float,
    windowHeightPx: Float,
    safe: ImmersiveSafeBounds,
): HomeRect =
    resolveAdaptiveUiMetrics(
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

internal fun resolveImmersiveTextBottom(
    pagerY: Float,
    renderScale: Float,
): Float =
    pagerY -
        IMMERSIVE_PAGER_INDICATOR_HALF_HEIGHT_PX * renderScale -
        IMMERSIVE_PAGER_TEXT_GAP_PX * renderScale

private fun goldenImmersiveLayout(orientation: HomeOrientation): ImmersiveHomeLayout {
    val anchors = HomeGalleryGeometryPolicy.referenceAnchors(orientation)
    val slots =
        anchors.heroSlots.associate { slot ->
            val immersiveSlot =
                when (slot.role) {
                    HomeCardSlot.FAR_LEFT -> ImmersiveSlot.FAR_LEFT
                    HomeCardSlot.NEAR_LEFT -> ImmersiveSlot.NEAR_LEFT
                    HomeCardSlot.CENTER -> ImmersiveSlot.CENTER
                    HomeCardSlot.NEAR_RIGHT -> ImmersiveSlot.NEAR_RIGHT
                    HomeCardSlot.FAR_RIGHT -> ImmersiveSlot.FAR_RIGHT
                }
            immersiveSlot to
                ImmersiveCardFrame(
                    rect = slot.rect,
                    rotationZ = slot.rotationDeg,
                    alpha = ImmersiveCardDepth.presence(slot.alpha),
                    depth = ImmersiveCardDepth.depth(slot.alpha),
                    saturation = if (slot.role == HomeCardSlot.CENTER) 1f else .72f,
                    elevationPx = if (slot.role == HomeCardSlot.CENTER) 16f else 2f,
                    zIndex = HomeGalleryGeometryPolicy.landscapeCardZIndex(slot.role),
                    focusFraction = if (slot.role == HomeCardSlot.CENTER) 1f else 0f,
                )
        }
    val portraitCenter = slots[ImmersiveSlot.CENTER]
    val portraitSlots =
        if (orientation == HomeOrientation.PORTRAIT && portraitCenter != null) {
            val center = portraitCenter.rect
            val sideWidth = center.w * .62f
            val sideHeight = center.h * .62f
            val sideOffset = center.w * .28f
            val sideY = center.y + center.h * .26f
            slots +
                linkedMapOf(
                    ImmersiveSlot.FAR_LEFT to
                        ImmersiveCardFrame(
                            HomeRect(
                                center.x - center.w * .95f - sideWidth / 2f,
                                center.y + center.h * .42f,
                                sideWidth * .5f,
                                sideHeight * .5f,
                            ),
                            rotationZ = -6.5f, rotationY = -3f, alpha = 0f, saturation = .58f, elevationPx = 1f, zIndex = 1f, depth = 1f,
                        ),
                    ImmersiveSlot.NEAR_LEFT to
                        ImmersiveCardFrame(
                            HomeRect(
                                center.x + center.w / 2f - sideOffset - sideWidth / 2f,
                                sideY,
                                sideWidth,
                                sideHeight,
                            ),
                            rotationZ = -4.5f, rotationY = -2f, alpha = 1f, saturation = .72f, elevationPx = 2f, zIndex = 2f, depth = 1f,
                        ),
                    ImmersiveSlot.NEAR_RIGHT to
                        ImmersiveCardFrame(
                            HomeRect(
                                center.x + center.w / 2f + sideOffset - sideWidth / 2f,
                                sideY,
                                sideWidth,
                                sideHeight,
                            ),
                            rotationZ = 4.5f, rotationY = 2f, alpha = 1f, saturation = .72f, elevationPx = 2f, zIndex = 2f, depth = 1f,
                        ),
                    ImmersiveSlot.FAR_RIGHT to
                        ImmersiveCardFrame(
                            HomeRect(
                                center.x + center.w * .95f - sideWidth / 2f,
                                center.y + center.h * .42f,
                                sideWidth * .5f,
                                sideHeight * .5f,
                            ),
                            rotationZ = 6.5f, rotationY = 3f, alpha = 0f, saturation = .58f, elevationPx = 1f, zIndex = 1f, depth = 1f,
                        ),
                )
        } else {
            slots
        }
    val centerSlot = portraitSlots[ImmersiveSlot.CENTER]
    val galleryCenterX =
        centerSlot?.let { it.rect.x + it.rect.w / 2f }
            ?: anchors.pagerCenters.first().x
    val pagerCenters =
        (0 until ImmersivePagerPolicy.MAX_VISIBLE).map { index ->
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

private fun ImmersiveSafeBounds.normalized(
    windowWidth: Float,
    windowHeight: Float
): ImmersiveSafeBounds {
    val left = leftPx.coerceIn(0f, windowWidth.coerceAtLeast(0f))
    val top = topPx.coerceIn(0f, windowHeight.coerceAtLeast(0f))
    val right = rightPx.coerceIn(left + .01f, windowWidth.coerceAtLeast(left + .01f))
    val bottom = bottomPx.coerceIn(top + .01f, windowHeight.coerceAtLeast(top + .01f))
    return ImmersiveSafeBounds(left, top, right, bottom)
}

private fun HomeRect.shifted(
    dx: Float,
    dy: Float
) = copy(x = x + dx, y = y + dy)

internal fun HomeRect.inside(bounds: ImmersiveSafeBounds): HomeRect {
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

internal fun centeredPager(
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
    val centerX =
        if (safeLeft.isFinite() && safeRight.isFinite()) {
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

private fun lerp(
    start: Float,
    end: Float,
    progress: Float
): Float = start + (end - start) * progress
