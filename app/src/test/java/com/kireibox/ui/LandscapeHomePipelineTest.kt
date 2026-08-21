package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real pipeline JVM test: CompactLandscapeViewportPolicy (Dock metrics + first-page viewport)
 * feeds FocusGalleryGeometryPolicy exactly like the app wires it in KireiBoxApp. Locks the
 * D 16:9 arc cards / C 9:16 stacked cards, full in-viewport display and the strict 12dp
 * Dock gap.
 */
class LandscapeHomePipelineTest {
    private fun pipelineGeometry(
        viewportWidthDp: Float,
        windowHeightDp: Float,
        listTopDp: Float,
        listBottomDp: Float,
        navigationBottomDp: Float,
        style: LandscapeHomeStyle = LandscapeHomeStyle.IMMERSIVE_COVER,
    ): Pair<FocusGalleryGeometry, Float> {
        val dock = LandscapeDockPolicy.metrics(viewportWidthDp, windowHeightDp, navigationBottomDp)
        val viewport = CompactLandscapeViewportPolicy.firstPage(
            windowHeightDp = windowHeightDp,
            listTopDp = listTopDp,
            listBottomDp = listBottomDp,
            dockHeightDp = dock.heightDp,
            dockBottomInsetDp = dock.bottomInsetDp,
        )
        // App wiring: the gallery stage height is the pager page height and the geometry
        // dock top is that height minus the viewport bottom gap (= dock top, stage-relative).
        val stageHeightDp = (listBottomDp - listTopDp).coerceAtLeast(0f)
        val dockTopStageRelativeDp = viewport.viewportBottomDp - listTopDp
        val geometry = FocusGalleryGeometryPolicy.geometry(
            viewportWidthDp = viewportWidthDp,
            symmetricSafeGuardDp = 0f,
            dockTopDp = dockTopStageRelativeDp,
            stageHeightDp = stageHeightDp,
            style = style,
        )
        return geometry to dockTopStageRelativeDp
    }

    private fun assertAspectAndDockGap(
        geometry: FocusGalleryGeometry,
        stageHeightDp: Float,
        dockTopStageRelativeDp: Float,
        viewportWidthDp: Float,
        style: LandscapeHomeStyle,
    ) {
        val aspect = when (style) {
            LandscapeHomeStyle.IMMERSIVE_COVER -> FocusGalleryGeometryPolicy.D_WIDTH_TO_HEIGHT_RATIO
            LandscapeHomeStyle.FOCUS_POSTER -> PortraitCoverPolicy.WIDTH_TO_HEIGHT_RATIO
        }
        // Strict per-style aspect for both card sizes.
        assertEquals(
            aspect,
            geometry.centerWidthDp / geometry.centerHeightDp,
            .001f,
        )
        assertEquals(
            aspect,
            geometry.sideWidthDp / geometry.sideHeightDp,
            .001f,
        )
        // C fills the full height budget (bottom exactly DOCK_GAP_DP above the Dock top).
        // D is width-driven (min of ~48vw and the 16:9 height budget), so it must never
        // exceed that same budget but can leave extra bottom space on wide short windows.
        if (style == LandscapeHomeStyle.FOCUS_POSTER) {
            assertEquals(
                dockTopStageRelativeDp - FocusGalleryGeometryPolicy.DOCK_GAP_DP,
                geometry.centerHeightDp,
                .001f,
            )
        } else {
            assertTrue(
                geometry.centerHeightDp <=
                    dockTopStageRelativeDp - FocusGalleryGeometryPolicy.DOCK_GAP_DP + .001f,
            )
        }
        // Full vertical display: card spans the stage top to Dock top - gap.
        assertTrue(geometry.centerHeightDp <= stageHeightDp)
        assertEquals(
            stageHeightDp - geometry.centerHeightDp,
            geometry.bottomGapDp,
            .001f,
        )
        // Full horizontal display: center and both side cards fit inside the viewport.
        assertTrue(geometry.centerSlotCenterXDp - geometry.centerWidthDp / 2f >= 0f)
        assertTrue(geometry.centerSlotCenterXDp + geometry.centerWidthDp / 2f <= viewportWidthDp)
        assertTrue(geometry.leftSlotCenterXDp - geometry.sideWidthDp / 2f >= 0f)
        assertTrue(geometry.rightSlotCenterXDp + geometry.sideWidthDp / 2f <= viewportWidthDp)
    }

    @Test
    fun `pixel8 nav24 pipeline locks D 16 to 9 arc and keeps the card above the dock`() {
        val (geometry, dockTopStageRelativeDp) = pipelineGeometry(
            viewportWidthDp = 914f,
            windowHeightDp = 411f,
            listTopDp = 60f,
            listBottomDp = 403f,
            navigationBottomDp = 24f,
        )

        assertEquals(271f, dockTopStageRelativeDp, .001f)
        assertEquals(246.78f, geometry.centerHeightDp, .01f)
        assertEquals(438.72f, geometry.centerWidthDp, .01f)
        assertEquals(103.65f, geometry.sideHeightDp, .01f)
        assertEquals(184.26f, geometry.sideWidthDp, .01f)
        assertEquals(96.22f, geometry.bottomGapDp, .01f)
        assertEquals(FocusGalleryGeometryPolicy.D_ARC_LIFT_DP, geometry.arcLiftDp, .001f)
        assertEquals(FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES, geometry.arcRotationDegrees, .001f)
        // D V6: after SIDE_SCALE the visible outer edge sits exactly on the safe margin and
        // the pure-geometry inner gap is clearly >= 35dp (~57dp on Pixel 8), mirrored.
        val outer = FocusGalleryGeometryPolicy.outerMarginDp(914f, 0f)
        assertEquals(outer, FocusGalleryGeometryPolicy.sideVisualLeftDp(geometry), .001f)
        assertTrue(
            FocusGalleryGeometryPolicy.sideInnerGapDp(geometry) >=
                FocusGalleryGeometryPolicy.D_INNER_GAP_MIN_DP,
        )
        assertTrue(FocusGalleryGeometryPolicy.sideInnerGapDp(geometry) >= 35f)
        val centerRight = geometry.centerSlotCenterXDp + geometry.centerWidthDp / 2f
        val rightVisualLeft = geometry.rightSlotCenterXDp -
            geometry.sideWidthDp * FocusGalleryGeometryPolicy.SIDE_SCALE / 2f
        assertEquals(
            FocusGalleryGeometryPolicy.sideInnerGapDp(geometry),
            rightVisualLeft - centerRight,
            .001f,
        )
        assertAspectAndDockGap(
            geometry,
            stageHeightDp = 343f,
            dockTopStageRelativeDp = 271f,
            viewportWidthDp = 914f,
            style = LandscapeHomeStyle.IMMERSIVE_COVER,
        )
    }

    @Test
    fun `pixel8 nav48 pipeline locks D 16 to 9 and keeps the card above the dock`() {
        val (geometry, dockTopStageRelativeDp) = pipelineGeometry(
            viewportWidthDp = 914f,
            windowHeightDp = 411f,
            listTopDp = 60f,
            listBottomDp = 403f,
            navigationBottomDp = 48f,
        )

        assertEquals(247f, dockTopStageRelativeDp, .001f)
        assertEquals(235f, geometry.centerHeightDp, .001f)
        assertEquals(417.78f, geometry.centerWidthDp, .01f)
        assertEquals(98.7f, geometry.sideHeightDp, .01f)
        assertEquals(175.47f, geometry.sideWidthDp, .01f)
        assertEquals(108f, geometry.bottomGapDp, .001f)
        assertAspectAndDockGap(
            geometry,
            stageHeightDp = 343f,
            dockTopStageRelativeDp = 247f,
            viewportWidthDp = 914f,
            style = LandscapeHomeStyle.IMMERSIVE_COVER,
        )
    }

    @Test
    fun `short 700x400 window keeps the whole 16 to 9 D card visible`() {
        val (geometry, dockTopStageRelativeDp) = pipelineGeometry(
            viewportWidthDp = 700f,
            windowHeightDp = 400f,
            listTopDp = 48f,
            listBottomDp = 392f,
            navigationBottomDp = 24f,
        )

        assertEquals(272f, dockTopStageRelativeDp, .001f)
        assertEquals(189f, geometry.centerHeightDp, .001f)
        assertEquals(336f, geometry.centerWidthDp, .001f)
        assertEquals(79.38f, geometry.sideHeightDp, .01f)
        assertEquals(141.12f, geometry.sideWidthDp, .01f)
        assertEquals(155f, geometry.bottomGapDp, .001f)
        assertAspectAndDockGap(
            geometry,
            stageHeightDp = 344f,
            dockTopStageRelativeDp = 272f,
            viewportWidthDp = 700f,
            style = LandscapeHomeStyle.IMMERSIVE_COVER,
        )
    }

    @Test
    fun `focus poster pipeline keeps the C 9 to 16 stacked geometry with a 56dp strip`() {
        val (geometry, dockTopStageRelativeDp) = pipelineGeometry(
            viewportWidthDp = 914f,
            windowHeightDp = 411f,
            listTopDp = 60f,
            listBottomDp = 403f,
            navigationBottomDp = 24f,
            style = LandscapeHomeStyle.FOCUS_POSTER,
        )

        assertEquals(259f, geometry.centerHeightDp, .001f)
        assertEquals(145.6875f, geometry.centerWidthDp, .001f)
        assertEquals(207.2f, geometry.sideHeightDp, .001f)
        assertEquals(0f, geometry.arcLiftDp, .001f)
        assertEquals(0f, geometry.arcRotationDegrees, .001f)
        assertAspectAndDockGap(
            geometry,
            stageHeightDp = 343f,
            dockTopStageRelativeDp = 271f,
            viewportWidthDp = 914f,
            style = LandscapeHomeStyle.FOCUS_POSTER,
        )
        // C keeps a recognizable visible strip (>= 52dp, target 56dp after SIDE_SCALE)
        // while the side-card centers and whole cards stay on screen and mirrored.
        val visible = FocusGalleryHitTargetPolicy.visibleSideWidthDp(geometry)
        assertTrue(visible >= FocusGalleryHitTargetPolicy.MIN_VISIBLE_SIDE_WIDTH_DP)
        assertEquals(FocusGalleryHitTargetPolicy.TARGET_VISIBLE_SIDE_WIDTH_DP, visible, .001f)
        assertTrue(geometry.leftSlotCenterXDp >= geometry.sideWidthDp / 2f)
        assertTrue(geometry.rightSlotCenterXDp <= 914f - geometry.sideWidthDp / 2f)
        assertEquals(geometry.rightSlotCenterXDp, 914f - geometry.leftSlotCenterXDp, .001f)
    }
}
