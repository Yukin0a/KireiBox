package com.kireibox.ui

import kotlin.math.max
import kotlin.math.min

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
    val resolvedMinimumPlayHeightPx =
        minimumPlayHeightPx
            .takeIf { it.isFinite() }
            ?.coerceAtLeast(1f)
            ?: IMMERSIVE_PLAY_MIN_HEIGHT_PX
    val chrome =
        (topChrome ?: HomePresentationPolicy.topChromeSpec(safeFrame)).let {
            it.copy(menuRect = it.menuRect.inside(safe))
        }
    val resolvedDock =
        (dockRect ?: resolveDefaultImmersiveDock(windowWidthPx, windowHeightPx, safe))
            .inside(safe)
    // [bottomClearancePx] is an extra gap, not the component page's full scroll padding. The
    // dock itself is the source of truth for the pager position; this keeps a moved/resized dock
    // from leaving a stale golden-canvas gap behind it.
    val dockPagerClearance =
        max(
            bottomClearancePx.coerceAtLeast(0f),
            IMMERSIVE_PAGER_GAP_PX,
        )
    val pagerY =
        (resolvedDock.y - dockPagerClearance)
            .coerceIn(safe.topPx, safe.bottomPx)

    return when (orientation) {
        HomeOrientation.PORTRAIT -> {
            val scale = min(safeWidth / 852f, safeHeight / 1846f).coerceAtLeast(.01f)
            val topAnchor =
                max(
                    safe.topPx + safeHeight * .12f,
                    chrome.menuRect.y + chrome.menuRect.h + safeHeight * .08f,
                )
            val centerWidth = safeWidth * .641f
            val availableHeight = (pagerY - topAnchor - IMMERSIVE_PAGER_GAP_PX).coerceAtLeast(1f)
            val centerHeight = min(centerWidth * 16f / 9f, availableHeight)
            val resolvedCenterWidth = centerHeight * 9f / 16f
            val centerX = safe.leftPx + safeWidth / 2f
            val center =
                HomeRect(
                    centerX - resolvedCenterWidth / 2f,
                    topAnchor,
                    resolvedCenterWidth,
                    centerHeight,
                )
            val centerCx = center.x + center.w / 2f
            val centerCy = center.y + center.h / 2f

            fun rearFrame(
                scaleW: Float,
                scaleH: Float,
                dx: Float,
                rotation: Float,
                alpha: Float,
                z: Float
            ) = ImmersiveCardFrame(
                HomeRect(
                    centerCx + dx - center.w * scaleW / 2f,
                    centerCy + center.h * (1f - scaleH) * .18f - center.h * scaleH / 2f,
                    center.w * scaleW,
                    center.h * scaleH,
                ),
                rotation,
                rotation * -.82f,
                ImmersiveCardDepth.presence(alpha),
                .58f,
                2f,
                z,
                depth = ImmersiveCardDepth.depth(alpha),
            )
            val sideOffset = center.w * .28f
            val slots =
                linkedMapOf(
                    ImmersiveSlot.FAR_LEFT to rearFrame(.50f, .50f, -center.w * .95f, 8.5f, 0f, 1f),
                    ImmersiveSlot.NEAR_LEFT to rearFrame(.62f, .62f, -sideOffset, 5f, .48f, 2f),
                    ImmersiveSlot.CENTER to
                        ImmersiveCardFrame(
                            center, 2.5f, 0f, 1f, 1f, 12f * scale, 3f, focusFraction = 1f,
                        ),
                    ImmersiveSlot.NEAR_RIGHT to rearFrame(.62f, .62f, sideOffset, -2.5f, .48f, 2f),
                    ImmersiveSlot.FAR_RIGHT to rearFrame(.50f, .50f, center.w * .95f, -3.5f, 0f, 1f),
                ).mapValues { (_, frame) -> frame.copy(rect = frame.rect.inside(safe)) }
            val maxPlaySize = center.w * .32f
            val playSize =
                min(84f * scale, maxPlaySize)
                    .coerceAtLeast(minOf(resolvedMinimumPlayHeightPx, maxPlaySize).coerceAtLeast(1f))
            ImmersiveHomeLayout(
                brand = chrome.titlePosition,
                menu = chrome.menuRect,
                metadata = null,
                title = null,
                subtitle = null,
                play =
                    HomeRect(
                        center.x + center.w - playSize * 1.35f,
                        center.y + center.h - playSize * 1.35f,
                        playSize,
                        playSize,
                    ).inside(safe),
                dock = resolvedDock,
                pagerCenters =
                    centeredPager(
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
            val renderScale =
                resolveImmersiveRenderScale(
                    HomeOrientation.LANDSCAPE,
                    safeWidth,
                    safeHeight,
                )

            // Reserve the information column before solving the card geometry. This makes the
            // engine/title/Continue stack a bounded input to the gallery rather than an overlay
            // that can grow into the cards when fontScale is 1.3 or 1.5.
            val textX =
                max(
                    windowWidthPx * IMMERSIVE_LANDSCAPE_METADATA_X_FRACTION,
                    safe.leftPx + IMMERSIVE_LANDSCAPE_METADATA_MIN_SAFE_GAP_PX,
                )
            val textBudget =
                resolveImmersiveTextBudget(
                    safeHeightPx = safeHeight,
                    fontScale = fontScale,
                    renderScale = renderScale,
                )
            val baseMetadataY =
                max(
                    safe.topPx + safeHeight * .16f,
                    chrome.titleRect.y + chrome.titleRect.h + 12f * renderScale,
                )
            val playHeight =
                (104f * renderScale)
                    .coerceAtLeast(resolvedMinimumPlayHeightPx)
            val textBottom =
                resolveImmersiveTextBottom(pagerY, renderScale)
                    .coerceAtLeast(safe.topPx)
            // Restore the landscape information order: metadata, title, optional subtitle, then
            // the launch control. The portrait card owns the launch-above-title treatment.
            val availableTextHeight = (textBottom - baseMetadataY).coerceAtLeast(0f)
            val metadataToTitleGap = 10f * renderScale
            val titleToSubtitleGap = 8f * renderScale
            val textToPlayGap = 20f * renderScale
            val subtitleToPlayGap = IMMERSIVE_SUBTITLE_PLAY_GAP_PX * renderScale
            val minimumResolvedPlayHeight = minOf(resolvedMinimumPlayHeightPx, playHeight)
            val fullStackHeight =
                textBudget.metadataHeightPx + metadataToTitleGap +
                    textBudget.titleHeightPx + titleToSubtitleGap + textBudget.subtitleHeightPx +
                    subtitleToPlayGap + playHeight
            val twoLineNoSubtitleHeight =
                textBudget.metadataHeightPx + metadataToTitleGap +
                    textBudget.titleHeightPx + textToPlayGap + minimumResolvedPlayHeight
            val oneLineNoSubtitleHeight =
                textBudget.metadataHeightPx + metadataToTitleGap +
                    textBudget.titleLineHeightPx + textToPlayGap + minimumResolvedPlayHeight
            val (resolvedTitleHeight, showSubtitle, resolvedPlayHeight) =
                when {
                    availableTextHeight >= fullStackHeight -> {
                        Triple(textBudget.titleHeightPx, true, playHeight)
                    }
                    availableTextHeight >= twoLineNoSubtitleHeight -> {
                        Triple(
                            textBudget.titleHeightPx,
                            false,
                            (
                                availableTextHeight - textBudget.metadataHeightPx - metadataToTitleGap -
                                    textBudget.titleHeightPx - textToPlayGap
                            )
                                .coerceIn(minimumResolvedPlayHeight, playHeight),
                        )
                    }
                    availableTextHeight >= oneLineNoSubtitleHeight -> {
                        Triple(
                            textBudget.titleLineHeightPx,
                            false,
                            (
                                availableTextHeight - textBudget.metadataHeightPx - metadataToTitleGap -
                                    textBudget.titleLineHeightPx - textToPlayGap
                            )
                                .coerceIn(minimumResolvedPlayHeight, playHeight),
                        )
                    }
                    else ->
                        Triple(
                            textBudget.titleLineHeightPx,
                            false,
                            (
                                availableTextHeight - textBudget.metadataHeightPx - metadataToTitleGap -
                                    textBudget.titleLineHeightPx - textToPlayGap
                            )
                                .coerceIn(1f, playHeight),
                        )
                }
            val playGap = if (showSubtitle) subtitleToPlayGap else textToPlayGap
            val stackHeight =
                textBudget.metadataHeightPx + metadataToTitleGap +
                    resolvedTitleHeight + (
                        if (showSubtitle) {
                            titleToSubtitleGap + textBudget.subtitleHeightPx
                        } else {
                            0f
                        }
                    ) + playGap + resolvedPlayHeight
            val metadataOffset =
                resolveLandscapeMetadataOffset(
                    baseY = baseMetadataY,
                    textBottom = textBottom,
                    minimumStackHeight = stackHeight,
                )
            val metadataY = baseMetadataY + metadataOffset
            val titleY = metadataY + textBudget.metadataHeightPx + metadataToTitleGap
            val subtitleY = titleY + resolvedTitleHeight + titleToSubtitleGap
            val textBottomY =
                if (showSubtitle) {
                    subtitleY + textBudget.subtitleHeightPx
                } else {
                    titleY + resolvedTitleHeight
                }
            val playY = textBottomY + playGap

            val cardTop =
                max(
                    safe.topPx + safeHeight * .08f,
                    chrome.menuRect.y + chrome.menuRect.h + 16f * renderScale,
                )
            val centerHeight =
                min(
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
            val galleryExtent =
                max(
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
            val galleryCenterX =
                if (minGalleryCenterX <= maxGalleryCenterX) {
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
                rotationZ,
                rotationY,
                ImmersiveCardDepth.presence(alpha),
                saturation,
                elevation,
                z,
                depth = ImmersiveCardDepth.depth(alpha),
            )
            val nearY = centerY + centerHeight * .03f
            val farY = centerY + centerHeight * .045f
            val edgeWidth = max(1f, farWidth)
            val slots =
                linkedMapOf(
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
            val textRight =
                min(
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
                subtitle =
                    if (showSubtitle) {
                        HomeRect(textX, subtitleY, supportingTextWidth, textBudget.subtitleHeightPx).inside(safe)
                    } else {
                        null
                    },
                play = HomeRect(textX, playY, min(292f * renderScale, titleWidth), resolvedPlayHeight).inside(safe),
                dock = resolvedDock,
                pagerCenters =
                    centeredPager(
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

/**
 * The KireiBox brand's rect in the immersive home. Landscape lines it up with the information column
 * below it (engine · title · 继续游戏 · play pill), so the left column has one left edge on every
 * device, with or without a cutout; the right edge stays the shared chrome's. Portrait (and the
 * components home, which never calls this) keeps the shared top chrome.
 */
internal fun immersiveBrandTitleRect(
    chromeTitle: HomeRect,
    layout: ImmersiveHomeLayout,
    orientation: HomeOrientation,
): HomeRect {
    val column = layout.title
    if (orientation != HomeOrientation.LANDSCAPE || column == null) return chromeTitle
    val right = chromeTitle.x + chromeTitle.w
    return chromeTitle.copy(x = column.x, w = (right - column.x).coerceAtLeast(0f))
}
