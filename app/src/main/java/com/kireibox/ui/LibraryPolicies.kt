package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.model.EngineType

// Library card layout, cover-task badge models and the Alpha 11 integration facade.

internal fun resolveLibraryGridBottomContentPaddingDp(
    windowHeightDp: Float,
    dockRect: HomeRect,
    safeBottomDp: Float,
    clearanceDp: Float = AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP,
): Float {
    val renderedDock = resolveDockRenderRect(dockRect)
    val dockClearance =
        resolveBottomContentPaddingDp(
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
    const val SHORT_LANDSCAPE_COVER_ASPECT_RATIO = .94f
    const val SHORT_LANDSCAPE_MAX_HEIGHT_FRACTION = .60f
    const val MIN_HEADER_GAP_DP = 8f
    const val MIN_TITLE_GAP_DP = 6f
    const val MIN_DOCK_CLEARANCE_DP = 8f
    const val ENGINE_LABELS_PER_CARD = 1

    fun isShortLandscape(
        windowWidthDp: Float,
        windowHeightDp: Float
    ): Boolean =
        windowWidthDp > windowHeightDp &&
            windowHeightDp > 0f &&
            windowHeightDp / windowWidthDp <= SHORT_LANDSCAPE_MAX_HEIGHT_FRACTION

    fun coverAspectRatio(shortLandscape: Boolean): Float = if (shortLandscape) SHORT_LANDSCAPE_COVER_ASPECT_RATIO else PORTRAIT_COVER_ASPECT_RATIO

    fun titleMaxLines(shortLandscape: Boolean): Int = if (shortLandscape) 1 else 2

    fun titleGapDp(shortLandscape: Boolean): Float = maxOf(MIN_TITLE_GAP_DP, if (shortLandscape) 6f else 9f)

    /** Bottom edge of the library bar: the safe-area top plus the bar's own height. */
    fun resolvedHeaderBottomDp(
        headerTopDp: Float,
        toolbarHeightDp: Float,
    ): Float = headerTopDp.coerceAtLeast(0f) + toolbarHeightDp.coerceAtLeast(0f)

    /**
     * Grid top padding in the grid's local coordinate space: the room the bar covers, plus at least
     * [MIN_HEADER_GAP_DP] so the first row never touches it.
     */
    fun gridTopPaddingDp(
        gridViewportTopDp: Float,
        headerBottomDp: Float,
        gapDp: Float = MIN_HEADER_GAP_DP,
    ): Float {
        val viewportTop = gridViewportTopDp.coerceAtLeast(0f)
        val covered = maxOf(viewportTop, headerBottomDp.coerceAtLeast(0f)) - viewportTop
        return covered + gapDp.coerceAtLeast(MIN_HEADER_GAP_DP)
    }

    fun rowCount(
        itemCount: Int,
        columnCount: Int
    ): Int {
        require(itemCount >= 0) { "item count must be non-negative" }
        require(columnCount > 0) { "column count must be positive" }
        return if (itemCount == 0) 0 else (itemCount + columnCount - 1) / columnCount
    }
}

internal fun libraryCardEngineLabel(engine: EngineType?): String = engine?.displayName ?: "游戏"

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
    fun sourcePresentation(
        source: CoverSourceMode,
        selectedSource: CoverSourceMode?,
    ): CoverSourcePresentation = CoverSourcePresentationPolicy.presentation(source, selectedSource)

    fun tokenVisible(source: CoverSourceMode): Boolean = CoverSourcePresentationPolicy.tokenVisible(source)

    fun badge(pendingCount: Int): CoverTaskBadgePresentation = CoverTaskBadgePolicy.presentation(pendingCount)

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

    fun cacheBudgetKib(role: CoverImageRole): Int = CoverBitmapCaches.budgetKibFor(role)

    fun dockHaze(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): DockHazeConfiguration =
        DockHazePolicy.configuration(
            isDark = isDark,
            reducedTransparency = reducedTransparency,
            hazeAvailable = hazeAvailable,
            apiSupportsHaze = apiSupportsHaze,
        )
}
