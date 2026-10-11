package com.kireibox.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Window insets converted to dp, as reported by Compose WindowInsets.
 * All values are non-negative; validation rejects negative insets.
 */
data class AdaptiveSafeFrame(
    val leftDp: Float,
    val topDp: Float,
    val rightDp: Float,
    val bottomDp: Float,
) {
    init {
        require(leftDp >= 0f && topDp >= 0f && rightDp >= 0f && bottomDp >= 0f) {
            "safe insets must be non-negative"
        }
    }
}

/**
 * Shared responsive metrics for the immersive home page, the game library and the settings
 * page. Only portrait/landscape semantics exist: no phone-landscape product mode is produced
 * here. All rects and sizes are in dp and are safe-area aware.
 */
data class AdaptiveUiMetrics(
    val orientation: HomeOrientation,
    val safeFrame: AdaptiveSafeFrame,
    val scale: Float,
    val pageGutterDp: Float,
    val headerHeightDp: Float,
    /** Optical content frame as an absolute rect (x/y/w/h), symmetric around the window
     * center in landscape. x + w is the right edge and y + h is the bottom edge. */
    val contentFrame: HomeRect,
    val gridSpacingDp: Float,
    val dockRect: HomeRect,
    val libraryColumns: Int,
    val libraryCardWidthDp: Float,
    val settingsContentWidthDp: Float,
    val bottomContentPaddingDp: Float,
) {
    /** The optical safe frame used to derive [contentFrame] and [dockRect]. */
    val safeFrameRect: HomeRect
        get() =
            HomeRect(
                x = contentFrame.x - pageGutterDp,
                y = safeFrame.topDp,
                w = contentFrame.w + pageGutterDp * 2f,
                h = contentFrame.h,
            )

    /** The explicit extra clearance beyond the actual Dock bottom edge. */
    val bottomClearanceDp: Float
        get() = AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP
}

object AdaptiveLayoutConstants {
    const val PHONE_REFERENCE_SHORT_EDGE_DP = 411f
    const val MIN_SCALE = 0.90f
    const val MAX_SCALE = 1.20f
    const val PAGE_GUTTER_DP = 18f
    const val GRID_SPACING_DP = 12f
    const val HEADER_PORTRAIT_DP = 56f
    const val HEADER_LANDSCAPE_DP = 48f
    const val PORTRAIT_LIBRARY_COLUMNS = 2
    const val LANDSCAPE_FOUR_COLUMN_HEIGHT_DP = 460f
    const val FIVE_COLUMN_MIN_CARD_WIDTH_DP = 136f
    const val DOCK_PORTRAIT_WIDTH_FRACTION = 0.7113f
    const val DOCK_LANDSCAPE_WIDTH_FRACTION_16_9 = 0.4408f
    const val DOCK_LANDSCAPE_WIDTH_FRACTION_20_9 = 0.46f
    const val DOCK_LANDSCAPE_HEIGHT_FRACTION = 0.1063f

    /** Fixed portrait dock height so every phone ratio renders the same dock. */
    const val DOCK_PORTRAIT_HEIGHT_DP = 64f

    /** Fixed portrait gap between the dock bottom and the navigation inset. */
    const val DOCK_PORTRAIT_BOTTOM_GAP_DP = 16f

    /** Portrait dock width floor keeps three buttons at least 48dp (52dp with default gaps). */
    const val DOCK_PORTRAIT_MIN_WIDTH_DP = 200f
    const val DOCK_MIN_HEIGHT_DP = 48f
    const val DOCK_MAX_HEIGHT_DP = 88f

    /** Golden landscape gap above the navigation inset (fraction of the safe height): the highest the Dock may sit. */
    const val DOCK_BOTTOM_GAP_FRACTION = 0.0585f

    /** Landscape: gap between the dock bottom and the navigation / gesture area (dp). */
    const val DOCK_LANDSCAPE_BOTTOM_GAP_DP = 12f

    /** Landscape: smallest distance from the dock bottom to the window edge when there is no bottom inset (dp). */
    const val DOCK_LANDSCAPE_MIN_EDGE_GAP_DP = 16f
    const val SETTINGS_MAX_CONTENT_WIDTH_DP = 720f

    /**
     * Extra bottom padding so the final grid/settings row can scroll fully clear of the
     * glass dock; earlier rows still pass behind it for Haze sampling.
     */
    const val CONTENT_DOCK_CLEARANCE_DP = 8f
}

/**
 * Resolves the shared adaptive metrics for a window with the given safe insets (dp).
 *
 * Rules:
 * - Orientation is landscape iff the safe width is strictly greater than the safe height.
 * - scale = clamp(shortSafeEdgeDp / 411, 0.90, 1.20).
 * - Content uses sideGuard = max(safeLeft, safeRight) so asymmetric cutouts and rotation keep
 *   the same optical frame, content center axis and dock geometry.
 * - Library columns: portrait 2; landscape 4 when safe height < 460dp; otherwise 5 if a
 *   five-column card is at least 136dp wide, else 4.
 * - Dock width: landscape 44.08%..46% of the safe width interpolated between 16:9 and 20:9,
 *   portrait max(71.13% safe width, 200dp), both clamped to the safe width; portrait dock
 *   height is a fixed 64dp with a fixed 16dp gap above the navigation inset, landscape
 *   dock height is max(48dp, 10.63% of the safe height) sitting 12dp above the navigation
 *   inset (16dp from the window edge when there is no bottom inset), never higher than the
 *   golden 5.85% gap. The dock rect is the same on every tab. The dock left edge is clamped
 *   into the safe frame so extremely narrow windows never invert the clamp.
 * - Settings width is capped at 720 * scale dp. bottomContentPaddingDp is measured from the
 *   actual dockRect top plus an 8dp clearance, so changing dock geometry cannot stale the
 *   scroll clearance.
 */
fun resolveAdaptiveUiMetrics(
    windowWidthDp: Float,
    windowHeightDp: Float,
    safeLeftDp: Float,
    safeTopDp: Float,
    safeRightDp: Float,
    safeBottomDp: Float,
): AdaptiveUiMetrics {
    require(windowWidthDp > 0f && windowHeightDp > 0f) { "window must have positive size" }
    val safe = AdaptiveSafeFrame(safeLeftDp, safeTopDp, safeRightDp, safeBottomDp)
    val safeWidth = (windowWidthDp - safeLeftDp - safeRightDp).coerceAtLeast(1f)
    val safeHeight = (windowHeightDp - safeTopDp - safeBottomDp).coerceAtLeast(1f)
    val orientation =
        if (safeWidth > safeHeight) HomeOrientation.LANDSCAPE else HomeOrientation.PORTRAIT

    val shortEdge = min(safeWidth, safeHeight)
    val scale =
        (shortEdge / AdaptiveLayoutConstants.PHONE_REFERENCE_SHORT_EDGE_DP)
            .coerceIn(AdaptiveLayoutConstants.MIN_SCALE, AdaptiveLayoutConstants.MAX_SCALE)
    val gutter = AdaptiveLayoutConstants.PAGE_GUTTER_DP * scale

    val sideGuard = max(safeLeftDp, safeRightDp)
    val contentLeft = sideGuard + gutter
    val contentRight = windowWidthDp - sideGuard - gutter
    val contentWidth = (contentRight - contentLeft).coerceAtLeast(0f)
    val contentFrame =
        HomeRect(
            x = contentLeft,
            y = safeTopDp,
            w = contentWidth,
            h = (windowHeightDp - safeTopDp - safeBottomDp).coerceAtLeast(0f),
        )

    val headerHeight =
        if (orientation == HomeOrientation.LANDSCAPE) {
            AdaptiveLayoutConstants.HEADER_LANDSCAPE_DP * scale
        } else {
            AdaptiveLayoutConstants.HEADER_PORTRAIT_DP * scale
        }

    val dockGap =
        if (orientation == HomeOrientation.LANDSCAPE) {
            // safeBottomDp already includes a bottom cutout, so the gap is measured from the real navigation / gesture area.
            min(
                safeHeight * AdaptiveLayoutConstants.DOCK_BOTTOM_GAP_FRACTION,
                max(
                    AdaptiveLayoutConstants.DOCK_LANDSCAPE_BOTTOM_GAP_DP,
                    AdaptiveLayoutConstants.DOCK_LANDSCAPE_MIN_EDGE_GAP_DP - safeBottomDp,
                ),
            )
        } else {
            AdaptiveLayoutConstants.DOCK_PORTRAIT_BOTTOM_GAP_DP
        }
    val dockHeight =
        if (orientation == HomeOrientation.LANDSCAPE) {
            max(
                AdaptiveLayoutConstants.DOCK_MIN_HEIGHT_DP,
                safeHeight * AdaptiveLayoutConstants.DOCK_LANDSCAPE_HEIGHT_FRACTION,
            )
        } else {
            AdaptiveLayoutConstants.DOCK_PORTRAIT_HEIGHT_DP
        }
    // safeWidth is >= 1dp by construction, so coerceIn(1f, safeWidth) can never invert.
    val dockWidth =
        if (orientation == HomeOrientation.LANDSCAPE) {
            val aspect = safeWidth / safeHeight
            val t = ((aspect - 16f / 9f) / (20f / 9f - 16f / 9f)).coerceIn(0f, 1f)
            val fraction =
                AdaptiveLayoutConstants.DOCK_LANDSCAPE_WIDTH_FRACTION_16_9 +
                    (
                        AdaptiveLayoutConstants.DOCK_LANDSCAPE_WIDTH_FRACTION_20_9 -
                            AdaptiveLayoutConstants.DOCK_LANDSCAPE_WIDTH_FRACTION_16_9
                    ) * t
            (safeWidth * fraction).coerceIn(1f, safeWidth)
        } else {
            max(
                safeWidth * AdaptiveLayoutConstants.DOCK_PORTRAIT_WIDTH_FRACTION,
                AdaptiveLayoutConstants.DOCK_PORTRAIT_MIN_WIDTH_DP,
            ).coerceIn(1f, safeWidth)
        }

    val centerX = windowWidthDp / 2f
    val dockBottom = windowHeightDp - safeBottomDp - dockGap
    // Clamp the left edge into [safeLeft, safeLeft + safeWidth - dockWidth]; the span is
    // always non-negative because dockWidth <= safeWidth, so coerceIn never inverts even
    // on extremely narrow windows with asymmetric insets.
    val dockLeftMin = safeLeftDp
    val dockLeftMax = safeLeftDp + (safeWidth - dockWidth).coerceAtLeast(0f)
    val dockLeft = (centerX - dockWidth / 2f).coerceIn(dockLeftMin, dockLeftMax)
    val dockRect =
        HomeRect(
            x = dockLeft,
            y = dockBottom - dockHeight,
            w = dockWidth,
            h = dockHeight,
        )

    val gridSpacing = AdaptiveLayoutConstants.GRID_SPACING_DP * scale
    val columns =
        if (orientation == HomeOrientation.PORTRAIT) {
            AdaptiveLayoutConstants.PORTRAIT_LIBRARY_COLUMNS
        } else if (safeHeight < AdaptiveLayoutConstants.LANDSCAPE_FOUR_COLUMN_HEIGHT_DP) {
            4
        } else {
            val fiveColumnCardWidth = (contentWidth - gridSpacing * 4f) / 5f
            if (fiveColumnCardWidth >= AdaptiveLayoutConstants.FIVE_COLUMN_MIN_CARD_WIDTH_DP) 5 else 4
        }
    val cardWidth = ((contentWidth - gridSpacing * (columns - 1)) / columns).coerceAtLeast(1f)

    val settingsWidth =
        min(contentWidth, AdaptiveLayoutConstants.SETTINGS_MAX_CONTENT_WIDTH_DP * scale)
    val bottomContentPadding =
        resolveBottomContentPaddingDp(
            windowHeightDp = windowHeightDp,
            dockRect = dockRect,
            clearanceDp = AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP,
        )

    return AdaptiveUiMetrics(
        orientation = orientation,
        safeFrame = safe,
        scale = scale,
        pageGutterDp = gutter,
        headerHeightDp = headerHeight,
        contentFrame = contentFrame,
        gridSpacingDp = gridSpacing,
        dockRect = dockRect,
        libraryColumns = columns,
        libraryCardWidthDp = cardWidth,
        settingsContentWidthDp = settingsWidth,
        bottomContentPaddingDp = bottomContentPadding,
    )
}

/**
 * Returns the scroll padding needed to place the final item below the real dock top. Keeping
 * this derived from [dockRect] is important: the dock is responsive in both size and position,
 * and a copied height/gap formula can drift when its geometry changes.
 */
fun resolveBottomContentPaddingDp(
    windowHeightDp: Float,
    dockRect: HomeRect,
    clearanceDp: Float = AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP,
): Float {
    require(windowHeightDp > 0f) { "window must have positive height" }
    require(dockRect.w >= 0f && dockRect.h >= 0f) { "dock rect must have non-negative size" }
    require(clearanceDp >= 0f) { "dock clearance must be non-negative" }
    return (windowHeightDp - dockRect.y + clearanceDp).coerceAtLeast(0f)
}
