package com.kireibox.ui

import com.yuki.yukihub.settings.HomePresentationMode
import kotlin.math.abs
import kotlin.math.max

enum class HomeSizeClass {
    COMPACT,
    WIDE,
}

enum class HomeRenderKind {
    IMMERSIVE_PORTRAIT,
    IMMERSIVE_LANDSCAPE,
    COMPONENTS_PORTRAIT,
    COMPONENTS_LANDSCAPE,
}

enum class HomeContentDensity {
    COMPACT,
    COMPACT_HEIGHT,
    STANDARD,
}

/**
 * Pure geometry for the home title/menu chrome. The input is an absolute safe frame, so the
 * result never depends on a historical design canvas or on the full, unsafe window bounds.
 */
data class HomeTopChromeSpec(
    val titleRect: HomeRect,
    val menuRect: HomeRect,
    val menuHitRect: HomeRect,
    val titleSizeDp: Float,
    val menuDiameterDp: Float,
    val menuIconSizeDp: Float,
) {
    init {
        require(titleSizeDp > 0f) { "title size must be positive" }
        require(menuDiameterDp > 0f && menuIconSizeDp > 0f) {
            "menu sizes must be positive"
        }
        require(menuHitRect.w > 0f && menuHitRect.h > 0f) {
            "menu hit target must have positive size"
        }
    }

    val titlePosition: HomePoint
        get() = HomePoint(titleRect.x, titleRect.y)

    val menuPosition: HomePoint
        get() = HomePoint(menuRect.x, menuRect.y)

    /** Compatibility aliases for callers that use shorter field names. */
    val title: HomeRect
        get() = titleRect

    val menu: HomeRect
        get() = menuRect

    val menuHitTarget: HomeRect
        get() = menuHitRect

    val titleFontSizeDp: Float
        get() = titleSizeDp

    val menuIconSize: Float
        get() = menuIconSizeDp
}

data class HomePresentationSpec(
    val mode: HomePresentationMode,
    val renderKind: HomeRenderKind,
    val sizeClass: HomeSizeClass,
    val contentDensity: HomeContentDensity,
    val topChrome: HomeTopChromeSpec,
)

enum class HomeCardDepth {
    FRONT,
    REAR,
}

data class HomeCardAssignment(
    val gameIndex: Int,
    val slot: HomeCardSlot,
    val depth: HomeCardDepth,
)

const val HOME_MIN_TOUCH_TARGET_DP = 48f

/** Pure routing policy for the home page; Compose and viewport measurement stay at the edge. */
object HomePresentationPolicy {
    /**
     * Resolves the shared title/menu geometry from the safe frame alone. Identical safe frames
     * and size classes therefore produce identical geometry in immersive and components mode.
     */
    fun topChromeSpec(
        safeFrame: HomeRect,
        sizeClass: HomeSizeClass = HomeSizeClass.COMPACT,
    ): HomeTopChromeSpec {
        require(safeFrame.w > 0f && safeFrame.h > 0f) {
            "safe frame must have positive size"
        }
        val isLandscape = safeFrame.w > safeFrame.h
        val horizontalInset =
            when (sizeClass) {
                HomeSizeClass.COMPACT -> 16f
                HomeSizeClass.WIDE -> 24f
            }
        val verticalInset =
            when {
                isLandscape && sizeClass == HomeSizeClass.COMPACT -> 8f
                isLandscape -> 12f
                else -> 16f
            }
        val titleSize =
            when (sizeClass) {
                HomeSizeClass.COMPACT -> if (isLandscape) 22f else 24f
                HomeSizeClass.WIDE -> if (isLandscape) 24f else 26f
            }
        val requestedMenuDiameter =
            when (sizeClass) {
                HomeSizeClass.COMPACT -> 48f
                HomeSizeClass.WIDE -> 52f
            }
        val physicallyAvailableDiameter = minOf(safeFrame.w, safeFrame.h)
        val menuDiameter = requestedMenuDiameter.coerceAtMost(physicallyAvailableDiameter)
        val requestedMenuIconSize =
            when (sizeClass) {
                HomeSizeClass.COMPACT -> 22f
                HomeSizeClass.WIDE -> 24f
            }
        val menuIconSize = requestedMenuIconSize.coerceAtMost(menuDiameter)
        val menuX =
            (safeFrame.x + safeFrame.w - horizontalInset - menuDiameter)
                .coerceIn(safeFrame.x, safeFrame.x + safeFrame.w - menuDiameter)
        val menuY =
            (safeFrame.y + verticalInset)
                .coerceIn(safeFrame.y, safeFrame.y + safeFrame.h - menuDiameter)
        val titleX =
            (safeFrame.x + horizontalInset)
                .coerceIn(safeFrame.x, safeFrame.x + safeFrame.w)
        val titleHeight =
            max(HOME_MIN_TOUCH_TARGET_DP, titleSize * 1.35f)
                .coerceAtMost(safeFrame.h)
        val titleY =
            (safeFrame.y + verticalInset)
                .coerceIn(safeFrame.y, safeFrame.y + safeFrame.h - titleHeight)
        val titleRight =
            (menuX - horizontalInset)
                .coerceIn(titleX, safeFrame.x + safeFrame.w)
        val titleWidth = (titleRight - titleX).coerceAtLeast(0f)
        val menuRect = HomeRect(menuX, menuY, menuDiameter, menuDiameter)
        val menuHitDiameter =
            if (physicallyAvailableDiameter < HOME_MIN_TOUCH_TARGET_DP) {
                physicallyAvailableDiameter
            } else {
                max(HOME_MIN_TOUCH_TARGET_DP, menuDiameter)
                    .coerceAtMost(physicallyAvailableDiameter)
            }
        val menuHitX =
            (menuRect.x + menuRect.w / 2f - menuHitDiameter / 2f)
                .coerceIn(safeFrame.x, safeFrame.x + safeFrame.w - menuHitDiameter)
        val menuHitY =
            (menuRect.y + menuRect.h / 2f - menuHitDiameter / 2f)
                .coerceIn(safeFrame.y, safeFrame.y + safeFrame.h - menuHitDiameter)
        val menuHitRect =
            HomeRect(
                x = menuHitX,
                y = menuHitY,
                w = menuHitDiameter,
                h = menuHitDiameter,
            )
        return HomeTopChromeSpec(
            titleRect = HomeRect(titleX, titleY, titleWidth, titleHeight),
            menuRect = menuRect,
            menuHitRect = menuHitRect,
            titleSizeDp = titleSize,
            menuDiameterDp = menuDiameter,
            menuIconSizeDp = menuIconSize,
        )
    }

    fun resolveTopChromeSpec(
        safeFrame: HomeRect,
        sizeClass: HomeSizeClass = HomeSizeClass.COMPACT,
    ): HomeTopChromeSpec = topChromeSpec(safeFrame, sizeClass)

    fun contentDensityFor(
        sizeClass: HomeSizeClass,
        safeHeightDp: Float,
    ): HomeContentDensity =
        when (sizeClass) {
            HomeSizeClass.COMPACT -> HomeContentDensity.COMPACT
            HomeSizeClass.WIDE ->
                if (safeHeightDp < 600f) {
                    HomeContentDensity.COMPACT_HEIGHT
                } else {
                    HomeContentDensity.STANDARD
                }
        }

    /**
     * Resolves mode-dependent rendering and size-dependent density independently. A rotation or
     * size-class change may change density/chrome, but never replaces the user's selected mode.
     */
    fun resolvePresentation(
        mode: HomePresentationMode?,
        safeFrame: HomeRect,
        sizeClass: HomeSizeClass,
    ): HomePresentationSpec {
        val selectedMode = mode ?: HomePresentationMode.IMMERSIVE
        return HomePresentationSpec(
            mode = selectedMode,
            renderKind =
                renderKind(
                    mode = selectedMode,
                    viewportWidth = safeFrame.w,
                    viewportHeight = safeFrame.h,
                    sizeClass = sizeClass,
                ),
            sizeClass = sizeClass,
            contentDensity = contentDensityFor(sizeClass, safeFrame.h),
            topChrome = topChromeSpec(safeFrame, sizeClass),
        )
    }

    fun cardAssignments(
        gameCount: Int,
        selected: Int,
        orientation: HomeOrientation,
    ): List<HomeCardAssignment> {
        if (gameCount <= 0) return emptyList()
        val safeSelected = selected.coerceIn(0, gameCount - 1)
        return when (orientation) {
            HomeOrientation.PORTRAIT -> portraitAssignments(gameCount, safeSelected)
            HomeOrientation.LANDSCAPE -> landscapeAssignments(gameCount, safeSelected)
        }
    }

    fun renderStoredKind(
        storedMode: String?,
        viewportWidth: Float,
        viewportHeight: Float,
        sizeClass: HomeSizeClass,
    ): HomeRenderKind =
        renderKind(
            HomePresentationMode.fromStored(storedMode),
            viewportWidth,
            viewportHeight,
            sizeClass,
        )

    fun renderKind(
        mode: HomePresentationMode?,
        viewportWidth: Float,
        viewportHeight: Float,
        @Suppress("UNUSED_PARAMETER") sizeClass: HomeSizeClass,
    ): HomeRenderKind {
        val orientation =
            if (viewportWidth > viewportHeight) {
                HomeOrientation.LANDSCAPE
            } else {
                HomeOrientation.PORTRAIT
            }
        return when (mode ?: HomePresentationMode.IMMERSIVE) {
            HomePresentationMode.IMMERSIVE ->
                when (orientation) {
                    HomeOrientation.PORTRAIT -> HomeRenderKind.IMMERSIVE_PORTRAIT
                    HomeOrientation.LANDSCAPE -> HomeRenderKind.IMMERSIVE_LANDSCAPE
                }
            HomePresentationMode.COMPONENTS ->
                when (orientation) {
                    HomeOrientation.PORTRAIT -> HomeRenderKind.COMPONENTS_PORTRAIT
                    HomeOrientation.LANDSCAPE -> HomeRenderKind.COMPONENTS_LANDSCAPE
                }
        }
    }

    private fun portraitAssignments(
        gameCount: Int,
        selected: Int
    ): List<HomeCardAssignment> {
        val assignments =
            mutableListOf(
                HomeCardAssignment(selected, HomeCardSlot.CENTER, HomeCardDepth.FRONT),
            )
        (0 until gameCount)
            .filter { it != selected }
            .sortedWith(compareBy<Int> { abs(it - selected) }.thenBy { it })
            .forEach { index ->
                assignments +=
                    HomeCardAssignment(
                        gameIndex = index,
                        slot = if (index < selected) HomeCardSlot.NEAR_LEFT else HomeCardSlot.NEAR_RIGHT,
                        depth = HomeCardDepth.REAR,
                    )
            }
        return assignments
    }

    private fun landscapeAssignments(
        gameCount: Int,
        selected: Int
    ): List<HomeCardAssignment> {
        val bySlot = linkedMapOf<HomeCardSlot, Int>()

        fun addIfValid(
            slot: HomeCardSlot,
            index: Int
        ) {
            if (index in 0 until gameCount && index != selected && index !in bySlot.values) {
                bySlot[slot] = index
            }
        }
        addIfValid(HomeCardSlot.NEAR_LEFT, selected - 1)
        addIfValid(HomeCardSlot.FAR_LEFT, selected - 2)
        addIfValid(HomeCardSlot.NEAR_RIGHT, selected + 1)
        addIfValid(HomeCardSlot.FAR_RIGHT, selected + 2)
        bySlot[HomeCardSlot.CENTER] = selected
        return listOf(
            HomeCardSlot.FAR_LEFT,
            HomeCardSlot.NEAR_LEFT,
            HomeCardSlot.CENTER,
            HomeCardSlot.NEAR_RIGHT,
            HomeCardSlot.FAR_RIGHT,
        ).mapNotNull { slot ->
            bySlot[slot]?.let {
                HomeCardAssignment(
                    gameIndex = it,
                    slot = slot,
                    depth = if (slot == HomeCardSlot.CENTER) HomeCardDepth.FRONT else HomeCardDepth.REAR,
                )
            }
        }
    }

    /**
     * The shell always draws exactly one dock outside the page hazeSource, for every tab
     * including immersive Home. The immersive page no longer owns an internal dock, so the
     * glass layer can never sample itself. Both home modes use the same metrics.dockRect.
     */
    @Suppress("UNUSED_PARAMETER")
    fun shouldShowGlobalDock(
        selectedTab: KireiTab,
        mode: HomePresentationMode?
    ): Boolean = true

    /** Orientation comes from the safe window area so cutout/inset insets cannot flip the render kind. */
    fun renderKindForSafeArea(
        mode: HomePresentationMode?,
        fullWidthPx: Float,
        fullHeightPx: Float,
        safeLeftPx: Float,
        safeTopPx: Float,
        safeRightPx: Float,
        safeBottomPx: Float,
        sizeClass: HomeSizeClass,
    ): HomeRenderKind =
        renderKind(
            mode = mode,
            viewportWidth = (fullWidthPx - safeLeftPx - safeRightPx).coerceAtLeast(0f),
            viewportHeight = (fullHeightPx - safeTopPx - safeBottomPx).coerceAtLeast(0f),
            sizeClass = sizeClass,
        )
}
