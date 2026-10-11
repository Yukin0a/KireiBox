package com.kireibox.ui

import kotlin.math.abs
import kotlin.math.min

object HomeGalleryGeometryPolicy {
    const val LANDSCAPE_MIRROR_AXIS_X = 1079.5f

    /** Maps the parent's safe-area render kind to the immersive gallery's design orientation. */
    fun orientationForRenderKind(renderKind: HomeRenderKind): HomeOrientation =
        when (renderKind) {
            HomeRenderKind.IMMERSIVE_PORTRAIT,
            HomeRenderKind.COMPONENTS_PORTRAIT,
            -> HomeOrientation.PORTRAIT
            HomeRenderKind.IMMERSIVE_LANDSCAPE,
            HomeRenderKind.COMPONENTS_LANDSCAPE,
            -> HomeOrientation.LANDSCAPE
        }

    fun landscapeCardZIndex(role: HomeCardSlot): Float =
        when (role) {
            HomeCardSlot.FAR_LEFT, HomeCardSlot.FAR_RIGHT -> 1f
            HomeCardSlot.NEAR_LEFT, HomeCardSlot.NEAR_RIGHT -> 2f
            HomeCardSlot.CENTER -> 3f
        }

    fun portraitCardZIndex(
        depth: HomeCardDepth,
        rearDistance: Int
    ): Float =
        when (depth) {
            HomeCardDepth.FRONT -> 3f
            HomeCardDepth.REAR -> 2f - rearDistance.coerceAtLeast(1) * .01f
        }

    fun portraitCardSpec(
        gameIndex: Int,
        selectedIndex: Int
    ): HomePortraitCardSpec {
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

    fun landscapeCardSaturation(slot: HomeCardSlot): Float = if (slot == HomeCardSlot.CENTER) 1f else .72f

    fun dockItemWidth(
        outerWidth: Float,
        horizontalPadding: Float,
        itemCount: Int,
    ): Float {
        if (itemCount <= 0) return 0f
        return ((outerWidth - horizontalPadding * 2f).coerceAtLeast(0f) / itemCount)
    }

    fun scaledTextWidth(
        designWidth: Float,
        frameScale: Float
    ): Float = (designWidth * frameScale).coerceAtLeast(0f)

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
    ): List<HomeGestureRegion> =
        when (orientation) {
            HomeOrientation.PORTRAIT ->
                listOf(
                    HomeGestureRegion(
                        bounds = anchors.heroSlots.single().rect,
                        excludedRects = listOf(anchors.menuRect, anchors.playRect, anchors.dockRect),
                    ),
                )
            HomeOrientation.LANDSCAPE ->
                anchors.heroSlots.map { slot ->
                    HomeGestureRegion(
                        bounds = slot.rect,
                        excludedRects =
                            listOf(
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
    ): List<HomeGestureRegion> =
        when (orientation) {
            HomeOrientation.PORTRAIT ->
                listOf(
                    HomeGestureRegion(
                        bounds = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect,
                        excludedRects = listOf(layout.menu, layout.play, layout.dock),
                    ),
                )
            HomeOrientation.LANDSCAPE ->
                layout.cardSlots.values.map { frame ->
                    HomeGestureRegion(
                        bounds = frame.rect,
                        excludedRects =
                            listOfNotNull(
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

    fun designCanvas(orientation: HomeOrientation): DesignCanvas =
        when (orientation) {
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
    ): HomePoint =
        HomePoint(
            x = (viewportW - designW * scale) / 2f,
            y = (viewportH - designH * scale) / 2f,
        )

    fun designToViewport(
        designPoint: HomePoint,
        scale: Float,
        offset: HomePoint,
    ): HomePoint =
        HomePoint(
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

    fun referenceAnchors(orientation: HomeOrientation): HomeGalleryAnchors =
        when (orientation) {
            HomeOrientation.PORTRAIT ->
                HomeGalleryAnchors(
                    brandOrigin = HomePoint(61f, 52f),
                    menuRect = HomeRect(722.5f, 52f, 67f, 67f),
                    playRect = HomeRect(565f, 1186f, 84f, 84f),
                    dockRect = HomeRect(121f, 1591f, 606f, 147f),
                    pagerBaseline = 1495f,
                    pagerCenters =
                        listOf(
                            HomePoint(394f, 1495f),
                            HomePoint(426f, 1495f),
                            HomePoint(458f, 1495f),
                        ),
                    heroSlots =
                        listOf(
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
            heroSlots =
                reference.heroSlots.map { slot ->
                    slot.copy(rect = scaleRect(slot.rect, scale, offset))
                },
        )
    }

    fun slotWindow(
        selectedIndex: Int,
        slotCount: Int
    ): List<Int> {
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
        val style =
            if (orientation == HomeOrientation.PORTRAIT) {
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
        val farLeft =
            HomeSlotTransform(
                slotIndex = 0,
                rect = HomeRect(560f, 264f, 142f, 359f),
                rotationDeg = -5f,
                alpha = .62f,
                role = HomeCardSlot.FAR_LEFT,
            )
        val nearLeft =
            HomeSlotTransform(
                slotIndex = 1,
                rect = HomeRect(720f, 237f, 167f, 399f),
                rotationDeg = -5f,
                alpha = .82f,
                role = HomeCardSlot.NEAR_LEFT,
            )
        val center =
            HomeSlotTransform(
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
            pagerCenters =
                listOf(
                    HomePoint(977f, 715f),
                    HomePoint(1018f, 715f),
                    HomePoint(1058f, 715f),
                    HomePoint(1098f, 715f),
                    HomePoint(1138f, 715f),
                    HomePoint(1178f, 715f),
                ),
            heroSlots =
                listOf(
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
    ): HomeSlotTransform =
        left.copy(
            slotIndex = slotIndex,
            role = role,
            rect =
                left.rect.copy(
                    x = 2f * LANDSCAPE_MIRROR_AXIS_X - (left.rect.x + left.rect.w),
                ),
            rotationDeg = -left.rotationDeg,
        )

    private fun scaleRect(
        rect: HomeRect,
        scale: Float,
        offset: HomePoint
    ): HomeRect {
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
