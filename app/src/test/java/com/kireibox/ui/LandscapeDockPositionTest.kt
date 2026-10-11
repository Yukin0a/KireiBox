package com.kireibox.ui

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * r5 (user: 「dock 栏横屏的位置就像沉浸式首页一样向下移动一些，游戏库和设置页的 dock 也要移到同一位置」): in landscape the one
 * shared Dock sits 12dp above the navigation / gesture area (16dp above the window edge without a bottom inset) on
 * every tab. Size and x stay the golden rect's, portrait is unchanged, the content clearance follows the lowered Dock
 * and the immersive gallery still clears it.
 */
class LandscapeDockPositionTest {
    private data class Device(val name: String, val widthPx: Float, val heightPx: Float, val density: Float)

    /** The landscape emulator matrix (window px, density), as in ImmersivePlayTimePlacementTest. */
    private val devices =
        listOf(
            Device("2400x1080@420", 2400f, 1080f, 2.625f),
            Device("1600x720@320", 1600f, 720f, 2f),
            Device("1920x1080@480", 1920f, 1080f, 3f),
            Device("2340x1080@440", 2340f, 1080f, 2.75f),
            Device("1280x800@213", 1280f, 800f, 1.33125f),
            Device("2560x1600@320", 2560f, 1600f, 2f),
            Device("3200x1440@560", 3200f, 1440f, 3.5f),
            Device("2208x1840@420", 2208f, 1840f, 2.625f),
        )

    /** Gesture navigation's bottom inset and the Pixel's mandatory system-gesture zone, in dp. */
    private val gestureNavDp = 24f
    private val mandatoryGestureZoneDp = 32f

    /** The landscape rect before r5: the golden 5.85% of the safe height above the navigation area. */
    private fun goldenDock(
        metrics: AdaptiveUiMetrics,
        heightDp: Float,
    ): HomeRect {
        val safeBottom = metrics.safeFrame.bottomDp
        val safeHeight = heightDp - metrics.safeFrame.topDp - safeBottom
        val bottom = heightDp - safeBottom - safeHeight * AdaptiveLayoutConstants.DOCK_BOTTOM_GAP_FRACTION
        return metrics.dockRect.copy(y = bottom - metrics.dockRect.h)
    }

    @Test
    fun pixel8LandscapeDockSitsTwelveDpAboveTheGestureArea() {
        val metrics = resolveAdaptiveUiMetrics(914.2857f, 411.4286f, 0f, 0f, 0f, gestureNavDp)
        val dock = metrics.dockRect
        assertEquals("12dp above the 24dp gesture area", 411.4286f - gestureNavDp - 12f, dock.y + dock.h, .01f)
        // Before: 22.7dp (5.85% of the safe height) above the gesture area, 46.7dp above the edge; after 36dp.
        val golden = goldenDock(metrics, 411.4286f)
        assertEquals(46.66f, 411.4286f - (golden.y + golden.h), .05f)
        assertEquals(10.66f, dock.y - golden.y, .05f)
        assertEquals("same size and x", golden.copy(y = dock.y), dock)
    }

    @Test
    fun theDockNeverEntersTheGestureAreaAndIsNeverHigherThanTheGoldenRect() {
        for (device in devices) {
            val widthDp = device.widthPx / device.density
            val heightDp = device.heightPx / device.density
            val metrics = resolveAdaptiveUiMetrics(widthDp, heightDp, 0f, 0f, 0f, gestureNavDp)
            val dock = metrics.dockRect
            val bottomGap = heightDp - (dock.y + dock.h)
            assertEquals(device.name, gestureNavDp + AdaptiveLayoutConstants.DOCK_LANDSCAPE_BOTTOM_GAP_DP, bottomGap, .01f)
            assertTrue("${device.name} stays out of the mandatory gesture zone", bottomGap >= mandatoryGestureZoneDp)
            assertTrue("${device.name} is never higher than the golden rect", dock.y >= goldenDock(metrics, heightDp).y)
            assertEquals("${device.name} keeps its size and x", goldenDock(metrics, heightDp).copy(y = dock.y), dock)
            assertTrue("${device.name} keeps the render height floor", resolveDockRenderRect(dock) == dock)
        }
    }

    @Test
    fun withoutABottomInsetTheDockKeepsSixteenDpFromTheEdge() {
        // Three-button navigation sits at the side in landscape: no bottom inset at all.
        val metrics = resolveAdaptiveUiMetrics(914f, 411f, 0f, 0f, 48f, 0f)
        assertEquals(411f - AdaptiveLayoutConstants.DOCK_LANDSCAPE_MIN_EDGE_GAP_DP, metrics.dockRect.y + metrics.dockRect.h, .01f)
        // A thin bottom inset still gets the 12dp gap above it, which is already more than 16dp from the edge.
        val thin = resolveAdaptiveUiMetrics(914f, 411f, 0f, 0f, 0f, 9f)
        assertEquals(411f - 9f - 12f, thin.dockRect.y + thin.dockRect.h, .01f)
    }

    @Test
    fun aBottomCutoutOrTallNavigationBarIsPartOfTheSafeBottom() {
        // safeDrawing includes display cutouts: a 40dp bottom inset keeps the Dock 12dp above it.
        val metrics = resolveAdaptiveUiMetrics(914f, 411f, 30f, 0f, 30f, 40f)
        val dock = metrics.dockRect
        assertEquals(411f - 40f - 12f, dock.y + dock.h, .01f)
        assertTrue(dock.x >= 30f && dock.x + dock.w <= 914f - 30f)
    }

    @Test
    fun portraitAndTinyWindowsKeepTheirDock() {
        for ((width, height) in listOf(411f to 914f, 360f to 800f, 800f to 1280f)) {
            val metrics = resolveAdaptiveUiMetrics(width, height, 0f, 24f, 0f, gestureNavDp)
            assertEquals(
                "fixed 16dp portrait gap above the navigation area",
                height - gestureNavDp - AdaptiveLayoutConstants.DOCK_PORTRAIT_BOTTOM_GAP_DP,
                metrics.dockRect.y + metrics.dockRect.h,
                .01f,
            )
        }
        // A window so short that the golden gap is already under 12dp: the Dock is never lifted.
        val short = resolveAdaptiveUiMetrics(640f, 150f, 0f, 0f, 0f, 0f)
        assertEquals(150f * AdaptiveLayoutConstants.DOCK_BOTTOM_GAP_FRACTION, 150f - (short.dockRect.y + short.dockRect.h), .01f)
    }

    @Test
    fun contentClearanceFollowsTheLoweredDockWithoutLeavingABand() {
        for (device in devices) {
            val d = device.density
            val widthDp = device.widthPx / d
            val heightDp = device.heightPx / d
            val metrics = resolveAdaptiveUiMetrics(widthDp, heightDp, 0f, 0f, 0f, gestureNavDp)
            val dock = metrics.dockRect
            val clearance = AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP
            // Settings / components home: exactly the room from the Dock top to the window bottom plus the 8dp clearance.
            assertEquals(device.name, heightDp - dock.y + clearance, metrics.bottomContentPaddingDp, .01f)
            // Library grid: same value, taken from the drawn Dock.
            val library = resolveLibraryGridBottomContentPaddingDp(heightDp, dock, gestureNavDp)
            assertEquals(device.name, metrics.bottomContentPaddingDp, library, .01f)
            assertTrue("${device.name} the last row scrolls fully above the Dock", heightDp - library + clearance <= dock.y + .01f)
            // The lowered Dock hands the golden gap's surplus back to the content.
            val golden = goldenDock(metrics, heightDp)
            assertTrue("${device.name} gives space back", metrics.bottomContentPaddingDp < heightDp - golden.y + clearance)
        }
    }

    @Test
    fun theLoweredDockStillClearsThePagerAndTheCards() {
        for (device in devices) {
            for (fontScale in listOf(1f, 1.3f)) {
                val d = device.density
                val density = Density(d, fontScale)
                val heightDp = device.heightPx / d
                val metrics = resolveAdaptiveUiMetrics(device.widthPx / d, heightDp, 0f, 0f, 0f, gestureNavDp)
                val shared =
                    resolveImmersiveLayout(
                        HomeOrientation.LANDSCAPE,
                        device.widthPx,
                        device.heightPx,
                        ImmersiveSafeBounds(0f, 0f, device.widthPx, device.heightPx - gestureNavDp * d),
                        dockRect = goldenDock(metrics, heightDp).toPx(density),
                        bottomClearancePx = 8f * d,
                        fontScale = fontScale,
                    )
                val layout =
                    resolveImmersiveLayout(
                        HomeOrientation.LANDSCAPE,
                        device.widthPx,
                        device.heightPx,
                        ImmersiveSafeBounds(0f, 0f, device.widthPx, device.heightPx - gestureNavDp * d),
                        dockRect = metrics.dockRect.toPx(density),
                        bottomClearancePx = 8f * d,
                        fontScale = fontScale,
                    )
                val case = "${device.name} fs$fontScale"
                val pagerY = layout.pagerCenters.first().y
                val cardBottom = layout.cardSlots.values.maxOf { it.rect.y + it.rect.h }
                assertTrue("$case pager clears the Dock", pagerY <= layout.dock.y - IMMERSIVE_PAGER_GAP_PX + .01f)
                assertTrue("$case cards clear the pager", cardBottom <= pagerY - IMMERSIVE_PAGER_GAP_PX + .01f)
                assertTrue("$case the pager follows the Dock down", pagerY >= shared.pagerCenters.first().y - .01f)
                val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
                val sharedCenter = shared.cardSlots.getValue(ImmersiveSlot.CENTER).rect
                assertTrue("$case the gallery never shrinks", center.h >= sharedCenter.h - .01f)
                assertEquals("$case 「继续游戏」 is still shown", shared.subtitle != null, layout.subtitle != null)
            }
        }
    }
}
