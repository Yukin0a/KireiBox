package com.kireibox.ui

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
