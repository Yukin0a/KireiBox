package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle

/** Coordinates the compact-landscape title/action lane with measured system geometry. */
data class HeaderRectDp(
    val leftDp: Float,
    val topDp: Float,
    val rightDp: Float,
    val bottomDp: Float,
) {
    fun expanded(byDp: Float): HeaderRectDp = HeaderRectDp(
        leftDp - byDp,
        topDp - byDp,
        rightDp + byDp,
        bottomDp + byDp,
    )

    fun intersects(other: HeaderRectDp): Boolean =
        leftDp < other.rightDp && rightDp > other.leftDp &&
            topDp < other.bottomDp && bottomDp > other.topDp
}

data class HeaderGeometryDp(
    val statusBarBottomDp: Float?,
    val safeDrawingTopDp: Float?,
    val cutout: HeaderRectDp?,
)

data class HeaderAnchorRegionsDp(
    val leftTitle: HeaderRectDp,
    val rightAction: HeaderRectDp,
)

data class HeaderLaneDp(
    val topDp: Float,
    val heightDp: Float,
) {
    val centerYDp: Float get() = topDp + (heightDp / 2f)
}

object HeaderTransitionPolicy {
    const val NORMAL_TOP_GAP_DP = 5f
    const val CUTOUT_GUARD_DP = 6f
    const val IMMERSIVE_CUTOUT_SAFE_TOP_DP = 4f
    const val HEADER_LANE_HEIGHT_DP = 48f

    /** A missing status-bar/cutout report is treated conservatively as the safe drawing top. */
    fun normalTopDp(statusBarBottomDp: Float?, safeDrawingTopDp: Float?): Float =
        statusBarBottomDp?.takeIf { it >= 0f }?.plus(NORMAL_TOP_GAP_DP)
            ?: safeDrawingTopDp?.coerceAtLeast(0f)
            ?: 0f

    /**
     * Computes one shared title/action lane. The cutout only changes the lane when either
     * measured anchor actually intersects its six dp expanded bounds.
     */
    fun topDp(
        geometry: HeaderGeometryDp,
        anchors: HeaderAnchorRegionsDp,
    ): Float {
        val normal = normalTopDp(geometry.statusBarBottomDp, geometry.safeDrawingTopDp)
        val cutout = geometry.cutout ?: return normal
        val expanded = cutout.expanded(CUTOUT_GUARD_DP)
        val overlapsAnchor = anchors.leftTitle.intersects(expanded) || anchors.rightAction.intersects(expanded)
        return if (overlapsAnchor) {
            maxOf(normal, cutout.bottomDp + CUTOUT_GUARD_DP)
        } else {
            // A center notch can inflate safeDrawingTop for the whole display. Keep the
            // side title/action lane on the status-bar baseline unless its measured anchor
            // actually intersects the expanded cutout.
            sideLaneTopDp(geometry.statusBarBottomDp, geometry.safeDrawingTopDp)
        }
    }

    fun sideLaneTopDp(statusBarBottomDp: Float?, safeDrawingTopDp: Float?): Float {
        val status = statusBarBottomDp?.takeIf { it >= 0f }
            ?: safeDrawingTopDp?.coerceAtLeast(0f)
            ?: 0f
        // Center cutouts often inflate the global safe top while the physical side
        // status lane remains close to the 20 dp baseline. Keep the full 48 dp touch
        // lane, but bias its top toward that nominal side baseline so the title does
        // not drift below the compact landscape viewport.
        return minOf(status, 20f) + 3f
    }

    /**
     * Compact-landscape immersive top. The hidden status bar cannot be used as a baseline,
     * so the lane anchors on the display cutout plus a visual margin, or on the safe
     * drawing top when no cutout is measured. A cutout only lowers the lane when one of the
     * measured anchors actually intersects its expanded bounds, so side punch-holes that
     * do not cross the title/menu lane never push the header down.
     */
    fun immersiveTopDp(
        cutout: HeaderRectDp?,
        safeDrawingTopDp: Float?,
        anchors: HeaderAnchorRegionsDp,
    ): Float {
        val base = maxOf(safeDrawingTopDp?.coerceAtLeast(0f) ?: 0f, NORMAL_TOP_GAP_DP)
        val cutout = cutout ?: return base
        val expanded = cutout.expanded(CUTOUT_GUARD_DP)
        val overlapsAnchor = anchors.leftTitle.intersects(expanded) || anchors.rightAction.intersects(expanded)
        return if (overlapsAnchor) {
            maxOf(base, cutout.bottomDp + IMMERSIVE_CUTOUT_SAFE_TOP_DP)
        } else {
            base
        }
    }

    fun lane(topDp: Float, heightDp: Float = HEADER_LANE_HEIGHT_DP): HeaderLaneDp =
        HeaderLaneDp(topDp = topDp, heightDp = heightDp)

    /** Mirrors the larger physical side inset so both rotations keep the same content frame. */
    fun symmetricHorizontalGuardDp(
        safeDrawingLeftDp: Float,
        safeDrawingRightDp: Float,
        basePaddingDp: Float = 18f,
    ): Float = maxOf(safeDrawingLeftDp, safeDrawingRightDp, 0f) + basePaddingDp.coerceAtLeast(0f)

    /** Search is a full-width field; unlike the toolbar it never adds a compact-landscape offset. */
    fun librarySearchTopDp(safeDrawingTopDp: Float, compactLandscape: Boolean = false): Float =
        safeDrawingTopDp.coerceAtLeast(0f)
}

/** Keeps the compact-landscape geometry path out of portrait/WIDE recomposition. */
object HomeCompactHeaderPolicy {
    fun topDp(
        compactLandscape: Boolean,
        safeDrawingTopDp: Float,
        geometry: HeaderGeometryDp,
        anchors: HeaderAnchorRegionsDp,
    ): Float = if (compactLandscape) {
        HeaderTransitionPolicy.immersiveTopDp(geometry.cutout, geometry.safeDrawingTopDp, anchors)
    } else {
        safeDrawingTopDp.coerceAtLeast(0f)
    }
}

/**
 * Compact phone-landscape component-page gutters: max(cutout-safe + 24dp, ~6vw), clamped
 * to 48-96dp on both edges. Dashboard cards, the LazyColumn content and all interactive
 * controls stay inside these bounds; only the background and the centered Dock may reach
 * the physical edges.
 */
object LandscapeGutterPolicy {
    const val GUTTER_FRACTION = .06f
    const val GUTTER_SAFE_DP = 24f
    const val GUTTER_MIN_DP = 48f
    const val GUTTER_MAX_DP = 96f

    fun contentGutterDp(viewportWidthDp: Float, symmetricSafeGuardDp: Float): Float =
        (maxOf(
            viewportWidthDp * GUTTER_FRACTION,
            symmetricSafeGuardDp.coerceAtLeast(0f) + GUTTER_SAFE_DP,
        )).coerceIn(GUTTER_MIN_DP, GUTTER_MAX_DP)
}

/**
 * Alpha 8 V3 hero gallery geometry: center ~31vw clamped 280-330dp, side ~18vw clamped
 * 156-196dp, side-card centers near 13.5vw/86.5vw with an outer margin of max(cutout-safe
 * + 10dp, ~4.5vw). All geometry is derived in one place and is symmetric under both
 * rotations because it consumes the larger physical side guard for both edges. Width,
 * scale, alpha, saturation and radius interpolate continuously with the pager offset so
 * side-to-center motion never snaps or pops.
 */
object LandscapeHeroSizingPolicy {
    const val CENTER_FRACTION = .275f
    const val CENTER_MIN_WIDTH_DP = 244f
    const val CENTER_MAX_WIDTH_DP = 294f
    const val SIDE_FRACTION = .145f
    const val SIDE_MIN_WIDTH_DP = 132f
    const val SIDE_MAX_WIDTH_DP = 176f
    const val SIDE_CENTER_FRACTION = .11f
    const val OUTER_MARGIN_FRACTION = .04f
    const val OUTER_MARGIN_SAFE_DP = 10f
    const val SIDE_ALPHA = .60f
    const val SIDE_SCALE = .78f
    const val SIDE_BLUR_DP = 1f
    const val SIDE_SATURATION = .72f
    const val CENTER_RADIUS_DP = 26f
    const val SIDE_RADIUS_DP = 22f

    /** Center card width is ~31% of the effective viewport width, clamped to 280-330dp. */
    fun centerCardWidthDp(viewportWidthDp: Float): Float {
        return (viewportWidthDp * CENTER_FRACTION).coerceIn(CENTER_MIN_WIDTH_DP, CENTER_MAX_WIDTH_DP)
    }

    fun sideCardWidthDp(viewportWidthDp: Float): Float =
        (viewportWidthDp * SIDE_FRACTION).coerceIn(SIDE_MIN_WIDTH_DP, SIDE_MAX_WIDTH_DP)

    /**
     * The larger physical side guard is mirrored to both edges so left and right rotation
     * produce the same geometry. [symmetricSafeGuardDp] is expected to already be mirrored.
     */
    fun outerMarginDp(viewportWidthDp: Float, symmetricSafeGuardDp: Float): Float =
        maxOf(viewportWidthDp * OUTER_MARGIN_FRACTION, symmetricSafeGuardDp.coerceAtLeast(0f) + OUTER_MARGIN_SAFE_DP)

    /** Left side-card center: ~13.5vw, pushed in only when the cutout-safe margin requires it. */
    fun sideCenterLeftDp(
        viewportWidthDp: Float,
        outerMarginDp: Float,
        sideWidthDp: Float,
    ): Float = maxOf(viewportWidthDp * SIDE_CENTER_FRACTION, outerMarginDp + sideWidthDp / 2f)

    fun sideCenterRightDp(
        viewportWidthDp: Float,
        outerMarginDp: Float,
        sideWidthDp: Float,
    ): Float = viewportWidthDp - sideCenterLeftDp(viewportWidthDp, outerMarginDp, sideWidthDp)

    /** Distance between the center slot and an adjacent side slot. */
    fun slotCenterDistanceDp(viewportWidthDp: Float, sideCenterLeftDp: Float): Float =
        (viewportWidthDp / 2f - sideCenterLeftDp).coerceAtLeast(0f)

    fun pageSpacingDp(centerWidthDp: Float, slotCenterDistanceDp: Float): Float =
        (slotCenterDistanceDp - centerWidthDp).coerceAtLeast(0f)

    /** 1 when the card is the center, 0 when it is fully a side preview. */
    fun centerFocus(pageOffset: Float): Float = (1f - pageOffset.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    fun cardWidthDpAt(
        centerWidthDp: Float,
        sideWidthDp: Float,
        pageOffset: Float,
    ): Float = sideWidthDp + (centerWidthDp - sideWidthDp) * centerFocus(pageOffset)

    fun cardAlphaAt(pageOffset: Float): Float =
        SIDE_ALPHA + (1f - SIDE_ALPHA) * centerFocus(pageOffset)

    fun cardScaleAt(pageOffset: Float): Float =
        SIDE_SCALE + (1f - SIDE_SCALE) * centerFocus(pageOffset)

    fun cardSaturationAt(pageOffset: Float): Float =
        SIDE_SATURATION + (1f - SIDE_SATURATION) * centerFocus(pageOffset)

    fun cardRadiusAt(pageOffset: Float): Float =
        SIDE_RADIUS_DP + (CENTER_RADIUS_DP - SIDE_RADIUS_DP) * centerFocus(pageOffset)

    /** Title/play information fades in only as focus approaches the center. */
    fun infoAlphaAt(pageOffset: Float): Float =
        ((centerFocus(pageOffset) - .5f) / .5f).coerceIn(0f, 1f)

    /** Input gate: the play button is only actionable when the card is visually near center. */
    fun isActionableCenter(pageOffset: Float): Boolean = centerFocus(pageOffset) >= .5f
}

data class ImmersiveGradientStop(
    val offsetDp: Float,
    val alpha: Float,
    val colorToken: String,
)

data class ImmersiveBackdropPlan(
    val drawBackdrop: Boolean,
    val topGradientStops: List<ImmersiveGradientStop>,
    val drawHorizontalScrim: Boolean,
    val drawBottomScrim: Boolean,
    val fixedBackdropAlphaDim: Boolean,
    val static: Boolean,
    val blurRadiusDp: Float,
    val saturation: Float,
    val fullOverlayAlpha: Float,
    val staticCoverAlpha: Float,
)

/**
 * Pure rendering contract for the landscape V3 immersive cover. The cover is a low-frequency
 * atmosphere: cropped, ~18dp blur, ~0.85 saturation, slightly scaled for the blur edge, with a
 * theme-adaptive full-screen overlay and a top safe-area gradient so the hidden status bar and
 * header lane keep text readable. Dark mode uses Black 0.62 plus a Black 0.84-to-transparent top
 * gradient; light mode uses White 0.72 plus White 0.90-to-transparent. Both fade with the page-0
 * backdrop progress. Reduced transparency drops blur and crossfade and renders a static ~0.21
 * cover over the theme background.
 */
object ImmersiveBackdropPolicy {
    const val LIGHT_START = "white"
    const val DARK_START = "black"
    const val TRANSPARENT = "transparent"
    const val BACKDROP_BLUR_DP = 18f
    const val BACKDROP_SATURATION = .85f
    const val BACKDROP_SCALE = 1.06f
    const val STATIC_COVER_ALPHA = .21f
    const val DARK_FULL_OVERLAY_ALPHA = .62f
    const val LIGHT_FULL_OVERLAY_ALPHA = .72f
    /** C backdrop overlays are slightly lighter than D but keep text readable. */
    const val C_DARK_FULL_OVERLAY_ALPHA = .55f
    const val C_LIGHT_FULL_OVERLAY_ALPHA = .66f
    const val DARK_TOP_GRADIENT_START_ALPHA = .84f
    const val LIGHT_TOP_GRADIENT_START_ALPHA = .90f
    const val TOP_GRADIENT_EXTENT_DP = 132f

    /**
     * Both hero styles (D 16:9 arc Cover Flow and C 9:16 stacked poster) render the
     * current-game cover backdrop on page 0; the component page fades it out via the pager
     * progress.
     */
    fun shouldRender(style: LandscapeHomeStyle, heroVisible: Boolean): Boolean =
        heroVisible && (
            style == LandscapeHomeStyle.IMMERSIVE_COVER ||
                style == LandscapeHomeStyle.FOCUS_POSTER
            )

    fun plan(
        style: LandscapeHomeStyle,
        reducedTransparency: Boolean,
        darkTheme: Boolean,
    ): ImmersiveBackdropPlan {
        if (style != LandscapeHomeStyle.IMMERSIVE_COVER &&
            style != LandscapeHomeStyle.FOCUS_POSTER
        ) {
            return ImmersiveBackdropPlan(
                drawBackdrop = false,
                topGradientStops = emptyList(),
                drawHorizontalScrim = false,
                drawBottomScrim = false,
                fixedBackdropAlphaDim = false,
                static = reducedTransparency,
                blurRadiusDp = 0f,
                saturation = 1f,
                fullOverlayAlpha = 0f,
                staticCoverAlpha = 0f,
            )
        }
        val start = if (darkTheme) DARK_START else LIGHT_START
        val startAlpha = if (darkTheme) {
            DARK_TOP_GRADIENT_START_ALPHA
        } else {
            LIGHT_TOP_GRADIENT_START_ALPHA
        }
        val fullOverlayAlpha = if (reducedTransparency) {
            0f
        } else if (darkTheme) {
            if (style == LandscapeHomeStyle.FOCUS_POSTER) {
                C_DARK_FULL_OVERLAY_ALPHA
            } else {
                DARK_FULL_OVERLAY_ALPHA
            }
        } else {
            if (style == LandscapeHomeStyle.FOCUS_POSTER) {
                C_LIGHT_FULL_OVERLAY_ALPHA
            } else {
                LIGHT_FULL_OVERLAY_ALPHA
            }
        }
        return ImmersiveBackdropPlan(
            drawBackdrop = true,
            topGradientStops = if (reducedTransparency) {
                emptyList()
            } else {
                listOf(
                    ImmersiveGradientStop(0f, startAlpha, start),
                    ImmersiveGradientStop(TOP_GRADIENT_EXTENT_DP, 0f, TRANSPARENT),
                )
            },
            drawHorizontalScrim = false,
            drawBottomScrim = false,
            fixedBackdropAlphaDim = false,
            static = reducedTransparency,
            blurRadiusDp = if (reducedTransparency) 0f else BACKDROP_BLUR_DP,
            saturation = if (reducedTransparency) 1f else BACKDROP_SATURATION,
            fullOverlayAlpha = fullOverlayAlpha,
            staticCoverAlpha = STATIC_COVER_ALPHA,
        )
    }
}

data class CompactLandscapeViewport(
    val viewportBottomDp: Float,
    val heightDp: Float,
    val componentsStartDp: Float,
    val bottomGapDp: Float = 0f,
)

object CompactLandscapeViewportPolicy {
    /**
     * Page one's card height is bounded by the actual space between the header lane and
     * the fixed Dock, so the whole card stays visible on any phone landscape window.
     * Coordinates are window-relative: [listTopDp] is the hero stage top and [listBottomDp]
     * is the stage bottom, while [bottomGapDp] is the empty stage space below the Dock top.
     */
    fun firstPage(
        windowHeightDp: Float,
        listTopDp: Float,
        listBottomDp: Float,
        dockHeightDp: Float,
        dockBottomInsetDp: Float = 0f,
    ): CompactLandscapeViewport {
        val dockTop = (windowHeightDp.coerceAtLeast(0f) -
            dockBottomInsetDp.coerceAtLeast(0f) - dockHeightDp.coerceAtLeast(0f)).coerceAtLeast(0f)
        return CompactLandscapeViewport(
            viewportBottomDp = dockTop,
            heightDp = (dockTop - listTopDp).coerceAtLeast(0f),
            componentsStartDp = listBottomDp,
            bottomGapDp = (listBottomDp - dockTop).coerceAtLeast(0f),
        )
    }

    fun componentPeeksUnderDock(page: CompactLandscapeViewport, dockTopDp: Float): Boolean =
        page.componentsStartDp < page.viewportBottomDp || dockTopDp > page.componentsStartDp
}

/** Fixed compact-landscape Dock geometry: 34-36vw clamped to 340-360dp, 48dp tall. */
data class LandscapeDockMetrics(
    val widthDp: Float,
    val heightDp: Float,
    val bottomInsetDp: Float,
    val topDp: Float,
)

object LandscapeDockPolicy {
    const val HEIGHT_DP = 48f
    const val RADIUS_DP = 24f
    const val WIDTH_FRACTION = .35f
    const val MIN_WIDTH_DP = 340f
    const val MAX_WIDTH_DP = 360f
    const val BOTTOM_GAP_DP = 8f
    const val BOTTOM_HEIGHT_FRACTION = .035f

    fun widthDp(viewportWidthDp: Float): Float =
        (viewportWidthDp * WIDTH_FRACTION).coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

    /** Navigation bar plus 8dp, or roughly 3.5% of the viewport height when no bar exists. */
    fun bottomInsetDp(navigationBottomDp: Float, viewportHeightDp: Float): Float =
        maxOf(
            navigationBottomDp.coerceAtLeast(0f) + BOTTOM_GAP_DP,
            viewportHeightDp.coerceAtLeast(0f) * BOTTOM_HEIGHT_FRACTION,
        )

    fun metrics(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        navigationBottomDp: Float,
    ): LandscapeDockMetrics {
        val bottom = bottomInsetDp(navigationBottomDp, viewportHeightDp)
        val top = (viewportHeightDp.coerceAtLeast(0f) - bottom - HEIGHT_DP).coerceAtLeast(0f)
        return LandscapeDockMetrics(
            widthDp = widthDp(viewportWidthDp),
            heightDp = HEIGHT_DP,
            bottomInsetDp = bottom,
            topDp = top,
        )
    }
}

/**
 * Compact-landscape top bar: ~5.5% side insets with a 12dp cutout-safe floor, ~3.5-4vh top
 * anchors with a 4dp cutout-safe floor, and symmetric right placement. The lane sits visibly
 * higher than the previous 6.5vh/7.5vw anchors and never depends on the hidden status bar.
 */
object CompactLandscapeTopbarPolicy {
    const val TITLE_LEFT_FRACTION = .055f
    const val TITLE_LEFT_SAFE_DP = 12f
    const val TITLE_TOP_FRACTION = .0375f
    const val MENU_RIGHT_FRACTION = .055f
    const val MENU_RIGHT_SAFE_DP = 12f
    const val MENU_TOP_FRACTION = .035f

    fun titleLeftDp(viewportWidthDp: Float, symmetricSafeGuardDp: Float): Float =
        maxOf(viewportWidthDp * TITLE_LEFT_FRACTION, symmetricSafeGuardDp + TITLE_LEFT_SAFE_DP)

    fun titleTopDp(headerTopDp: Float, viewportHeightDp: Float): Float =
        maxOf(headerTopDp, viewportHeightDp * TITLE_TOP_FRACTION)

    fun menuRightDp(viewportWidthDp: Float, symmetricSafeGuardDp: Float): Float =
        maxOf(viewportWidthDp * MENU_RIGHT_FRACTION, symmetricSafeGuardDp + MENU_RIGHT_SAFE_DP)

    fun menuTopDp(headerTopDp: Float, viewportHeightDp: Float): Float =
        maxOf(headerTopDp, viewportHeightDp * MENU_TOP_FRACTION)
}

/** Background fade contract for the two-page vertical pager. */
object LandscapePageTransitionPolicy {
    fun backdropAlpha(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)
}

/** Recent-games surfaces share a 22dp outer frame with a theme-aware translucent fill.
 * Alpha 9 removes the widget's outer edge border and the "查看全部" action border; the
 * internal game-card borders stay untouched. */
object RecentGamesSurfacePolicy {
    const val OUTER_RADIUS_DP = 22f
    const val INNER_RADIUS_DP = 18f
    const val DARK_ALPHA = .76f
    const val LIGHT_ALPHA = .87f
    /** The dashboard "最近打开" widget draws no outer edge border since Alpha 9. */
    const val SHOW_OUTER_BORDER = false
    /** The widget's "查看全部" action button draws no glass highlight border. */
    const val SHOW_ACTION_BORDER = false

    fun surfaceAlpha(isDark: Boolean): Float =
        if (isDark) DARK_ALPHA else LIGHT_ALPHA
}

/**
 * Responsive recent-games row sizing for constrained landscape surfaces. Every visible
 * item must be complete and touch-safe, and the row must never overflow its viewport, so
 * a second item can never be clipped to a sliver by a horizontal Row or LazyRow.
 */
object RecentGamesRowPolicy {
    const val ITEM_GAP_DP = 12f
    /** Minimum width that keeps cover + title column + play action complete. */
    const val MIN_ITEM_WIDTH_DP = 180f
    const val MIN_TOUCH_WIDTH_DP = 48f

    /** Largest item count (<= [maxItems]) that fits with the min card width. */
    fun itemCount(availableWidthDp: Float, maxItems: Int = 2): Int {
        val cap = maxItems.coerceIn(1, 4)
        var count = cap
        while (count > 1) {
            if (fits(availableWidthDp, count)) return count
            count--
        }
        return 1
    }

    fun fits(availableWidthDp: Float, itemCount: Int): Boolean {
        val count = itemCount.coerceAtLeast(1)
        if (count == 1) return availableWidthDp >= MIN_TOUCH_WIDTH_DP
        return availableWidthDp >= count * MIN_ITEM_WIDTH_DP + (count - 1) * ITEM_GAP_DP
    }

    /** Equal split of the viewport; the item-count guard keeps the result overflow-free. */
    fun cardWidthDp(availableWidthDp: Float, itemCount: Int): Float {
        val count = itemCount.coerceAtLeast(1)
        if (count == 1) return availableWidthDp.coerceAtLeast(0f)
        val width = (availableWidthDp - (count - 1) * ITEM_GAP_DP) / count
        return width.coerceAtLeast(MIN_ITEM_WIDTH_DP)
    }
}

data class LibraryGridFadeStop(val offsetDp: Float, val alpha: Float)

enum class LibraryHeaderOrientation { PORTRAIT, LANDSCAPE_PHONE, LANDSCAPE_PAD }

/**
 * Header-to-grid transition: the grid starts directly below the header/filter lane (no
 * extra gap) and draws a continuous 72dp fade from the toolbar bottom to 0 whenever the
 * grid rests or scrolls at the first row, so cards visibly scroll under the toolbar
 * through a surface-to-transparent band. The same stops are used for the initial and the
 * scrolled state so the transition never pops.
 */
object LibraryHeaderTransitionPolicy {
    const val TOP_GAP_DP = 0f
    const val HEADER_SCROLL_RANGE_DP = 68f
    /** Compact landscape toolbar lane height the grid scrolls underneath. */
    const val TOOLBAR_HEIGHT_DP = 56f
    /** Visual gap between the toolbar bottom and the first card row (8..16dp target). */
    const val FIRST_ROW_GAP_DP = 12f

    /** Combined offset from the grid top to the first card row. */
    fun firstRowTopDp(): Float = TOOLBAR_HEIGHT_DP + FIRST_ROW_GAP_DP

    fun topGapDp(adaptiveScale: Float): Float =
        TOP_GAP_DP * adaptiveScale.coerceAtLeast(0f)

    /**
     * Continuous library-header collapse fraction for the fixed 68dp scroll contract.
     * The caller supplies physical pixels and the current density; no caller-defined range
     * is accepted, so phone and pad paths share the same visual distance.
     */
    fun fractionForScroll(scrollOffsetPx: Float, density: Float): Float {
        val headerRangePx = HEADER_SCROLL_RANGE_DP * density
        return if (headerRangePx <= 0f) 0f else (scrollOffsetPx / headerRangePx).coerceIn(0f, 1f)
    }

    fun idleSnapTarget(fraction: Float): Float = if (fraction < .5f) 0f else 1f

    fun resolvedFraction(
        scrollOffsetPx: Float,
        density: Float,
        orientation: LibraryHeaderOrientation,
        searchActive: Boolean = false,
        editMode: Boolean = false,
        taskCenterOpen: Boolean = false,
        idle: Boolean = false,
    ): Float {
        if (orientation == LibraryHeaderOrientation.PORTRAIT || searchActive || editMode || taskCenterOpen) {
            return 0f
        }
        val fraction = fractionForScroll(scrollOffsetPx, density)
        return if (idle) idleSnapTarget(fraction) else fraction
    }

    fun interpolate(expanded: Float, collapsed: Float, fraction: Float): Float {
        val progress = fraction.coerceIn(0f, 1f)
        return expanded + (collapsed - expanded) * progress
    }

    fun paddingDp(expandedDp: Float, collapsedDp: Float, fraction: Float): Float =
        interpolate(expandedDp, collapsedDp, fraction)

    fun fadeAlpha(fraction: Float): Float = 1f - fraction.coerceIn(0f, 1f)

    fun visibilityFraction(fraction: Float): Float = fadeAlpha(fraction)
}

object LibraryGridTopFadePolicy {
    const val EXTENT_DP = 72f
    const val START_ALPHA = .45f
    const val END_ALPHA = 0f

    val stops = listOf(
        LibraryGridFadeStop(0f, START_ALPHA),
        LibraryGridFadeStop(EXTENT_DP, END_ALPHA),
    )

    fun visible(firstVisibleItemIndex: Int, searchActive: Boolean): Boolean =
        firstVisibleItemIndex == 0 && !searchActive
}

/**
 * Landscape library toolbar collapse. Pure anchor state machine with a binary output:
 * the toolbar stays fully expanded until the first card row reaches the top of the
 * viewport (the library heading anchor = toolbar height + first-row gap), then flips to
 * fully collapsed; it expands again only after scrolling back above the anchor. Motion
 * (220ms collapse / 260ms expand tween, snap under reduced motion) is applied by the
 * caller, so a scroll frame never retargets a per-pixel spring. Search/edit/task-center
 * states keep the toolbar pinned by never asking for a collapse.
 */
object LibraryToolbarCollapsePolicy {
    const val TOOLBAR_HEIGHT_DP = 56f
    /** Scroll distance at which the first card row reaches the viewport top. */
    const val ANCHOR_OFFSET_DP = 68f
    /** Collapse tween duration (FastOutSlowIn). */
    const val COLLAPSE_DURATION_MS = 220
    /** Expand tween duration (LinearOutSlowIn). */
    const val EXPAND_DURATION_MS = 260

    /** True once the first row has reached/passed the anchor. */
    fun anchored(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        anchorOffsetPx: Int,
    ): Boolean {
        if (anchorOffsetPx <= 0) return false
        return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset >= anchorOffsetPx
    }

    /** 0 = expanded, 1 = fully collapsed. Binary: no intermediate values. */
    fun collapseFraction(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        anchorOffsetPx: Int,
    ): Float = if (anchored(firstVisibleItemIndex, firstVisibleItemScrollOffset, anchorOffsetPx)) 1f else 0f

    /** Negative offset that slides the toolbar out of the viewport. */
    fun collapseOffsetDp(fraction: Float, toolbarHeightDp: Float = TOOLBAR_HEIGHT_DP): Float =
        -fraction.coerceIn(0f, 1f) * toolbarHeightDp.coerceAtLeast(0f)

    /** Toolbar opacity: 1 expanded, exactly 0 when fully collapsed. */
    fun collapseAlpha(fraction: Float): Float = 1f - fraction.coerceIn(0f, 1f)
}
