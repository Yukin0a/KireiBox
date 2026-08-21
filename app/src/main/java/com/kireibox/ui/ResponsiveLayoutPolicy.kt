package com.kireibox.ui

import android.content.res.Configuration
import com.yuki.yukihub.settings.DashboardProfile
import kotlin.math.roundToInt

/**
 * The four physical window buckets used by the first-wave responsive contracts.
 *
 * This deliberately lives beside, rather than inside, [KireiLayoutMode]. The latter is an
 * older two-value API consumed by existing presentation and task policies; adding enum values
 * there would make those untouched callers non-exhaustive. New code that needs device and
 * orientation semantics should use this classifier.
 */
enum class ResponsiveWindowClass {
    PHONE_PORTRAIT,
    PHONE_LANDSCAPE,
    PAD_PORTRAIT,
    PAD_LANDSCAPE,
}

/** Readable aliases for callers that use the device/window terminology. */
typealias KireiWindowClass = ResponsiveWindowClass
typealias ResponsiveDeviceClass = ResponsiveWindowClass
typealias ResponsiveSizeClass = HomeSizeClass

/** A physical window and its safe insets, all expressed in dp. */
data class ResponsiveSafeFrame(
    val windowWidthDp: Float,
    val windowHeightDp: Float,
    val leftDp: Float = 0f,
    val topDp: Float = 0f,
    val rightDp: Float = 0f,
    val bottomDp: Float = 0f,
) {
    init {
        require(windowWidthDp > 0f && windowHeightDp > 0f) {
            "window must have positive size"
        }
        require(leftDp >= 0f && topDp >= 0f && rightDp >= 0f && bottomDp >= 0f) {
            "safe insets must be non-negative"
        }
    }

    val safeWidthDp: Float
        get() = (windowWidthDp - leftDp - rightDp).coerceAtLeast(0f)

    val safeHeightDp: Float
        get() = (windowHeightDp - topDp - bottomDp).coerceAtLeast(0f)
}

enum class KireiLayoutMode {
    PHONE_PORTRAIT,
    WIDE,
}

enum class DashboardDensity {
    STANDARD,
    COMPACT_HEIGHT,
}

data class DashboardPresentation(
    val profile: DashboardProfile,
    val density: DashboardDensity,
)

data class CompactLibraryGridMetrics(
    val contentLeftPaddingDp: Float,
    val contentRightPaddingDp: Float,
    val contentWidthDp: Float,
    val columnCount: Int,
)

object ResponsiveLayoutPolicy {
    enum class LibraryGrid { PHONE_TWO_COLUMNS, MEDIUM_ADAPTIVE, WIDE_ADAPTIVE }

    const val PAD_MIN_SMALLEST_SCREEN_WIDTH_DP = 600
    const val PHONE_COMPACT_MAX_WIDTH_DP = 960f
    const val PHONE_COMPACT_MIN_ASPECT = 1.75f
    const val PHONE_COMPACT_MAX_HEIGHT_DP = 600f
    const val WIDE_COMPACT_HEIGHT_DP = 600f

    /**
     * A device is always a Pad (and therefore always WIDE) when its smallest screen width
     * reaches 600dp or the platform reports a LARGE/X-LARGE screen layout class. This rule
     * intentionally wins over aspect/height heuristics so Pad split-screen and short windows
     * can never enter the phone compact-landscape hero path.
     */
    fun isPad(smallestScreenWidthDp: Int, screenLayoutSize: Int): Boolean =
        smallestScreenWidthDp >= PAD_MIN_SMALLEST_SCREEN_WIDTH_DP ||
            screenLayoutSize == Configuration.SCREENLAYOUT_SIZE_LARGE ||
            screenLayoutSize == Configuration.SCREENLAYOUT_SIZE_XLARGE

    fun sizeClassFor(
        smallestScreenWidthDp: Int,
        screenLayoutSize: Int,
    ): HomeSizeClass = if (isPad(smallestScreenWidthDp, screenLayoutSize)) {
        HomeSizeClass.WIDE
    } else {
        HomeSizeClass.COMPACT
    }

    /**
     * Classifies the safe frame into phone/pad and portrait/landscape without consulting the
     * full canvas. [sizeClass] is an input, not an output heuristic: a compact 640x360 window is
     * still a phone landscape window, while a wide 600x1024 window is a Pad portrait window.
     */
    fun resolveWindowClass(
        safeWidthDp: Float,
        safeHeightDp: Float,
        sizeClass: HomeSizeClass,
    ): ResponsiveWindowClass {
        require(safeWidthDp > 0f && safeHeightDp > 0f) {
            "safe frame must have positive size"
        }
        val landscape = safeWidthDp > safeHeightDp
        return when (sizeClass) {
            HomeSizeClass.COMPACT -> if (landscape) {
                ResponsiveWindowClass.PHONE_LANDSCAPE
            } else {
                ResponsiveWindowClass.PHONE_PORTRAIT
            }
            HomeSizeClass.WIDE -> if (landscape) {
                ResponsiveWindowClass.PAD_LANDSCAPE
            } else {
                ResponsiveWindowClass.PAD_PORTRAIT
            }
        }
    }

    fun resolveWindowClass(
        safeFrame: ResponsiveSafeFrame,
        sizeClass: HomeSizeClass,
    ): ResponsiveWindowClass = resolveWindowClass(
        safeWidthDp = safeFrame.safeWidthDp,
        safeHeightDp = safeFrame.safeHeightDp,
        sizeClass = sizeClass,
    )

    /** Safe-frame overload for callers that already carry an absolute [HomeRect]. */
    fun resolveWindowClass(
        safeFrame: HomeRect,
        sizeClass: HomeSizeClass,
    ): ResponsiveWindowClass = resolveWindowClass(safeFrame.w, safeFrame.h, sizeClass)

    /**
     * Derives the size class from Android's configuration fields, then classifies the safe
     * dimensions. This is the explicit device/orientation resolver used by system-bar policy.
     */
    fun resolveWindowClass(
        safeWidthDp: Float,
        safeHeightDp: Float,
        smallestScreenWidthDp: Int,
        screenLayoutSize: Int,
    ): ResponsiveWindowClass = resolveWindowClass(
        safeWidthDp = safeWidthDp,
        safeHeightDp = safeHeightDp,
        sizeClass = sizeClassFor(smallestScreenWidthDp, screenLayoutSize),
    )

    fun resolveWindowClass(
        window: ResponsiveSafeFrame,
        smallestScreenWidthDp: Int,
        screenLayoutSize: Int,
    ): ResponsiveWindowClass = resolveWindowClass(
        safeWidthDp = window.safeWidthDp,
        safeHeightDp = window.safeHeightDp,
        smallestScreenWidthDp = smallestScreenWidthDp,
        screenLayoutSize = screenLayoutSize,
    )

    fun resolveDeviceClass(
        safeWidthDp: Float,
        safeHeightDp: Float,
        sizeClass: HomeSizeClass,
    ): ResponsiveWindowClass = resolveWindowClass(safeWidthDp, safeHeightDp, sizeClass)

    fun classifyWindow(
        safeFrame: ResponsiveSafeFrame,
        sizeClass: HomeSizeClass,
    ): ResponsiveWindowClass = resolveWindowClass(safeFrame, sizeClass)

    /**
     * Full responsive classification. The Pad branch is evaluated first; phone compact
     * landscape additionally requires a non-Pad device, width <= ~960dp, aspect >= 1.75
     * and height below 600dp so ultra-wide external windows never misuse the hero layout.
     */
    fun resolveLayoutMode(
        widthDp: Float,
        heightDp: Float,
        smallestScreenWidthDp: Int,
        screenLayoutSize: Int,
    ): KireiLayoutMode {
        val windowClass = resolveWindowClass(
            safeWidthDp = widthDp,
            safeHeightDp = heightDp,
            smallestScreenWidthDp = smallestScreenWidthDp,
            screenLayoutSize = screenLayoutSize,
        )
        if (windowClass == ResponsiveWindowClass.PAD_PORTRAIT ||
            windowClass == ResponsiveWindowClass.PAD_LANDSCAPE
        ) {
            return KireiLayoutMode.WIDE
        }
        // PHONE_LANDSCAPE_COMPACT is retired as a produced product mode: immersive home is
        // orientation-driven in both orientations and components mode uses COMPACT/WIDE only.
        return if (useTwoPane(widthDp, heightDp)) {
            KireiLayoutMode.WIDE
        } else {
            KireiLayoutMode.PHONE_PORTRAIT
        }
    }

    /** Compatibility overload: infers the smallest screen width from the window dimensions. */
    fun resolveLayoutMode(widthDp: Float, heightDp: Float): KireiLayoutMode {
        return resolveLayoutMode(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestScreenWidthDp = minOf(widthDp, heightDp).roundToInt(),
            screenLayoutSize = Configuration.SCREENLAYOUT_SIZE_UNDEFINED,
        )
    }

    fun dashboardPresentation(layoutMode: KireiLayoutMode): DashboardPresentation = when (layoutMode) {
        KireiLayoutMode.PHONE_PORTRAIT -> DashboardPresentation(
            DashboardProfile.COMPACT,
            DashboardDensity.STANDARD,
        )
        KireiLayoutMode.WIDE -> DashboardPresentation(
            DashboardProfile.WIDE,
            DashboardDensity.STANDARD,
        )
    }

    /**
     * Height-aware presentation: a Pad/WIDE window below 600dp uses the compact height
     * density only, never the phone compact-landscape hero page.
     */
    fun dashboardPresentation(
        layoutMode: KireiLayoutMode,
        availableHeightDp: Float,
    ): DashboardPresentation = when (layoutMode) {
        KireiLayoutMode.PHONE_PORTRAIT -> DashboardPresentation(
            DashboardProfile.COMPACT,
            DashboardDensity.STANDARD,
        )
        KireiLayoutMode.WIDE -> DashboardPresentation(
            DashboardProfile.WIDE,
            if (availableHeightDp < WIDE_COMPACT_HEIGHT_DP) {
                DashboardDensity.COMPACT_HEIGHT
            } else {
                DashboardDensity.STANDARD
            },
        )
    }

    fun compactLibraryGridMetrics(
        widthDp: Float,
        horizontalPaddingDp: Float,
        safeDrawingLeftDp: Float,
        safeDrawingRightDp: Float,
    ): CompactLibraryGridMetrics {
        val contentPaddingDp = compactHorizontalContentPaddingDp(
            horizontalPaddingDp = horizontalPaddingDp,
            safeDrawingLeftDp = safeDrawingLeftDp,
            safeDrawingRightDp = safeDrawingRightDp,
        )
        val contentWidthDp = (widthDp - contentPaddingDp * 2f).coerceAtLeast(0f)
        return CompactLibraryGridMetrics(
            contentLeftPaddingDp = contentPaddingDp,
            contentRightPaddingDp = contentPaddingDp,
            contentWidthDp = contentWidthDp,
            columnCount = ((contentWidthDp + 10f) / (132f + 10f)).toInt().coerceAtLeast(1),
        )
    }

    fun compactHorizontalContentPaddingDp(
        horizontalPaddingDp: Float,
        safeDrawingLeftDp: Float,
        safeDrawingRightDp: Float,
    ): Float = maxOf(
        safeDrawingLeftDp.coerceAtLeast(0f),
        safeDrawingRightDp.coerceAtLeast(0f),
    ) + horizontalPaddingDp.coerceAtLeast(0f)

    fun compactLibraryColumnCount(widthDp: Float, horizontalPaddingDp: Float): Int =
        compactLibraryGridMetrics(
            widthDp = widthDp,
            horizontalPaddingDp = horizontalPaddingDp,
            safeDrawingLeftDp = 0f,
            safeDrawingRightDp = 0f,
        ).columnCount

    fun dashboardProfile(layoutMode: KireiLayoutMode): DashboardProfile =
        dashboardPresentation(layoutMode).profile

    fun dashboardDensity(
        layoutMode: KireiLayoutMode,
        availableHeightDp: Float = Float.POSITIVE_INFINITY,
    ): DashboardDensity = dashboardPresentation(layoutMode, availableHeightDp).density

    fun useTwoPane(widthDp: Float, heightDp: Float): Boolean {
        if (widthDp >= 840f) return true
        if (widthDp < 720f || heightDp <= 0f) return false
        return widthDp / heightDp >= .68f
    }

    fun libraryGrid(widthDp: Float): LibraryGrid = when {
        widthDp < 600f -> LibraryGrid.PHONE_TWO_COLUMNS
        widthDp < 840f -> LibraryGrid.MEDIUM_ADAPTIVE
        else -> LibraryGrid.WIDE_ADAPTIVE
    }

    fun heroHeightDp(availableHeightDp: Float, twoPane: Boolean): Float =
        if (twoPane) {
            (availableHeightDp * .46f).coerceIn(300f, 410f)
        } else {
            (availableHeightDp * .38f).coerceIn(270f, 370f)
        }
}
