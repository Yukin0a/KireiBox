package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusGalleryPolicyTest {
    private fun cards(
        gameCount: Int,
        index: Int,
        direction: FocusDirection
    ): List<FocusCard> = FocusGalleryMappingPolicy.cards(gameCount, index, direction)

    private fun geometry(
        style: LandscapeHomeStyle = LandscapeHomeStyle.IMMERSIVE_COVER,
        viewportWidthDp: Float = 914f,
        symmetricSafeGuardDp: Float = 0f,
        dockTopDp: Float = 332f,
        stageHeightDp: Float = 332f,
    ): FocusGalleryGeometry =
        FocusGalleryGeometryPolicy.geometry(
            viewportWidthDp = viewportWidthDp,
            symmetricSafeGuardDp = symmetricSafeGuardDp,
            dockTopDp = dockTopDp,
            stageHeightDp = stageHeightDp,
            style = style,
        )

    @Test
    fun `advance mapping moves right to center and fills right`() {
        val cards = cards(5, 0, FocusDirection.ADVANCE)
        assertEquals(listOf(4L, 0L, 1L, 2L), cards.map { it.gameId })
        assertEquals(FocusSlot.LEFT, cards[0].sourceSlot)
        assertEquals(FocusSlot.LEFT, cards[1].targetSlot)
        assertEquals(FocusSlot.CENTER, cards[2].targetSlot)
        assertEquals(FocusSlot.RIGHT, cards[3].targetSlot)
        assertEquals(FocusCardRole.EXITING, cards[0].role)
        assertEquals(FocusCardRole.CENTER_TO_SIDE, cards[1].role)
        assertEquals(FocusCardRole.SIDE_TO_CENTER, cards[2].role)
        assertEquals(FocusCardRole.ENTERING, cards[3].role)
    }

    @Test
    fun `back mapping mirrors advance and entering fills left`() {
        val cards = cards(5, 1, FocusDirection.BACK)
        assertEquals(listOf(0L, 1L, 2L, 4L), cards.map { it.gameId })
        assertEquals(FocusSlot.CENTER, cards[0].targetSlot)
        assertEquals(FocusSlot.RIGHT, cards[1].targetSlot)
        assertEquals(FocusSlot.RIGHT, cards[2].sourceSlot)
        assertEquals(FocusSlot.LEFT, cards[3].targetSlot)
        assertEquals(FocusCardRole.ENTERING, cards[3].role)
        assertEquals(FocusCardRole.EXITING, cards[2].role)
    }

    @Test
    fun `two game previews duplicate the alternate but keep unique slot keys`() {
        val cards = cards(2, 0, FocusDirection.ADVANCE)
        assertEquals(1L, cards.first { it.sourceSlot == FocusSlot.LEFT }.gameId)
        assertEquals(1L, cards.first { it.sourceSlot == FocusSlot.RIGHT }.gameId)
        assertEquals(4, cards.map { it.key }.distinct().size)
        assertNotEquals(cards[0].key, cards[2].key)
    }

    @Test
    fun `single game mapping remains safe`() {
        val cards = cards(1, 0, FocusDirection.ADVANCE)
        assertEquals(4, cards.size)
        assertTrue(cards.all { it.gameId == 0L })
    }

    @Test
    fun `commit is rejected before one and accepted at one`() {
        val machine = FocusGalleryMachine()
        machine.beginTransition(3, FocusDirection.ADVANCE)
        machine.setProgress(.99f)
        assertFalse(machine.commitCurrent(3))
        assertEquals(0, machine.currentGameIndex)
        machine.setProgress(1f)
        assertTrue(machine.commitCurrent(3))
        assertEquals(1, machine.currentGameIndex)
        assertFalse(machine.isSettling)
        assertEquals(0f, machine.progress, .001f)
    }

    @Test
    fun `stage geometry is symmetric and D follows 16 to 9 while C follows 9 to 16`() {
        val g = geometry()
        assertEquals(246.78f, g.centerHeightDp, .01f)
        assertEquals(438.72f, g.centerWidthDp, .01f)
        assertEquals(103.65f, g.sideHeightDp, .01f)
        assertEquals(184.26f, g.sideWidthDp, .01f)
        assertEquals(FocusGalleryGeometryPolicy.D_WIDTH_TO_HEIGHT_RATIO, g.centerWidthDp / g.centerHeightDp, .001f)
        assertEquals(FocusGalleryGeometryPolicy.D_WIDTH_TO_HEIGHT_RATIO, g.sideWidthDp / g.sideHeightDp, .001f)
        assertEquals(g.rightSlotCenterXDp, 914f - g.leftSlotCenterXDp, .01f)
        assertEquals(457f, g.centerSlotCenterXDp, .01f)
        assertTrue(g.bottomGapDp >= 12f)
        assertTrue(g.exitLeftXDp < 0f)
        assertTrue(g.exitRightXDp > 914f)
    }

    @Test
    fun `D side card width is exactly 42 percent of the center card`() {
        assertEquals(.42f, FocusGalleryGeometryPolicy.D_SIDE_WIDTH_FRACTION, .001f)
        listOf(
            // Pixel 8 914dp stage.
            geometry(),
            // short window.
            geometry(viewportWidthDp = 700f, dockTopDp = 320f, stageHeightDp = 320f),
        ).forEach { g ->
            assertEquals(.42f, g.sideWidthDp / g.centerWidthDp, .001f)
        }
    }

    @Test
    fun `center and side cards follow the exact style aspect`() {
        FocusDirection.entries.forEach { _ ->
            listOf(
                LandscapeHomeStyle.IMMERSIVE_COVER,
                LandscapeHomeStyle.FOCUS_POSTER,
            ).forEach { style ->
                val g = geometry(style)
                val aspect =
                    if (style == LandscapeHomeStyle.IMMERSIVE_COVER) {
                        FocusGalleryGeometryPolicy.D_WIDTH_TO_HEIGHT_RATIO
                    } else {
                        PortraitCoverPolicy.WIDTH_TO_HEIGHT_RATIO
                    }
                assertEquals(aspect, g.centerWidthDp / g.centerHeightDp, .001f)
                assertEquals(aspect, g.sideWidthDp / g.sideHeightDp, .001f)
            }
        }
    }

    @Test
    fun `immersive spreads side cards to the edges while focus poster tucks them behind center`() {
        val immersive = geometry(LandscapeHomeStyle.IMMERSIVE_COVER)
        val poster = geometry(LandscapeHomeStyle.FOCUS_POSTER)

        // D: the slot center is derived from the scaled half-width, so after SIDE_SCALE the
        // visible outer edge sits exactly on the safe outer margin (edge spread).
        val outer = FocusGalleryGeometryPolicy.outerMarginDp(914f, 0f)
        assertEquals(
            outer + immersive.sideWidthDp * FocusGalleryGeometryPolicy.SIDE_SCALE / 2f,
            immersive.leftSlotCenterXDp,
            .001f,
        )
        assertEquals(outer, FocusGalleryGeometryPolicy.sideVisualLeftDp(immersive), .001f)
        assertTrue(
            FocusGalleryGeometryPolicy.sideInnerGapDp(immersive) >=
                FocusGalleryGeometryPolicy.D_INNER_GAP_MIN_DP,
        )
        // V6: Pixel 8 pure-geometry inner gap stays clearly above 35dp.
        assertTrue(FocusGalleryGeometryPolicy.sideInnerGapDp(immersive) >= 35f)
        // C: the visible strip after the SIDE_SCALE shrink stays recognizable and mirrored.
        assertEquals(
            FocusGalleryHitTargetPolicy.TARGET_VISIBLE_SIDE_WIDTH_DP,
            FocusGalleryHitTargetPolicy.visibleSideWidthDp(poster),
            .001f,
        )
        assertEquals(
            poster.rightSlotCenterXDp,
            914f - poster.leftSlotCenterXDp,
            .001f,
        )
        // D: side center is outside the center span.
        assertTrue(
            kotlin.math.abs(immersive.leftSlotCenterXDp - immersive.centerSlotCenterXDp) >
                immersive.centerWidthDp / 2f,
        )
        // C side heights are taller than D side heights and both stay below the center.
        assertTrue(poster.sideHeightDp > immersive.sideHeightDp)
        assertTrue(poster.sideHeightDp < poster.centerHeightDp)
        assertTrue(immersive.sideHeightDp < immersive.centerHeightDp)
    }

    @Test
    fun `short landscape keeps the whole 16 to 9 D card above the dock`() {
        val g =
            geometry(
                viewportWidthDp = 700f,
                dockTopDp = 320f,
                stageHeightDp = 320f,
            )
        assertEquals(336f, g.centerWidthDp, .001f)
        assertEquals(189f, g.centerHeightDp, .001f)
        assertEquals(141.12f, g.sideWidthDp, .001f)
        assertEquals(79.38f, g.sideHeightDp, .001f)
        assertEquals(131f, g.bottomGapDp, .001f)
        assertTrue(g.centerSlotCenterXDp - g.centerWidthDp / 2f >= 0f)
        assertTrue(g.centerSlotCenterXDp + g.centerWidthDp / 2f <= 700f)
        assertTrue(g.leftSlotCenterXDp - g.sideWidthDp / 2f >= 0f)
        assertTrue(g.rightSlotCenterXDp + g.sideWidthDp / 2f <= 700f)
    }

    @Test
    fun `extreme short window never lets D exceed the height budget`() {
        // dockTop = 120dp leaves only 108dp of usable height; 108 * 16/9 = 192dp of
        // width is less than the nominal 300dp minimum, so the center card must shrink
        // below the minimum instead of overflowing past the Dock.
        val g =
            geometry(
                viewportWidthDp = 914f,
                dockTopDp = 120f,
                stageHeightDp = 120f,
            )
        val heightBudget = 120f - FocusGalleryGeometryPolicy.DOCK_GAP_DP
        val widthByHeight = heightBudget * FocusGalleryGeometryPolicy.D_WIDTH_TO_HEIGHT_RATIO
        assertEquals(192f, widthByHeight, .001f)
        assertTrue(g.centerWidthDp <= widthByHeight + .001f)
        assertTrue(g.centerWidthDp <= FocusGalleryGeometryPolicy.D_CENTER_MIN_WIDTH_DP)
        assertTrue(g.centerHeightDp <= heightBudget + .001f)
        assertTrue(g.centerHeightDp <= 120f)
        assertTrue(g.sideHeightDp <= g.centerHeightDp + .001f)
        assertTrue(g.bottomGapDp >= 0f)
        // The whole card still fits inside the viewport horizontally.
        assertTrue(g.centerSlotCenterXDp - g.centerWidthDp / 2f >= 0f)
        assertTrue(g.centerSlotCenterXDp + g.centerWidthDp / 2f <= 914f)
        assertTrue(g.leftSlotCenterXDp - g.sideWidthDp / 2f >= 0f)
        assertTrue(g.rightSlotCenterXDp + g.sideWidthDp / 2f <= 914f)
    }

    @Test
    fun `left and right cutouts mirror to identical geometry`() {
        val guarded = geometry(symmetricSafeGuardDp = 50f)
        val outer = FocusGalleryGeometryPolicy.outerMarginDp(914f, 50f)
        assertEquals(
            outer + guarded.sideWidthDp * FocusGalleryGeometryPolicy.SIDE_SCALE / 2f,
            guarded.leftSlotCenterXDp,
            .001f,
        )
        assertEquals(
            914f - (outer + guarded.sideWidthDp * FocusGalleryGeometryPolicy.SIDE_SCALE / 2f),
            guarded.rightSlotCenterXDp,
            .001f,
        )
        // Both scaled side-card outer edges sit exactly on the mirrored guard margin.
        assertEquals(60f, FocusGalleryGeometryPolicy.sideVisualLeftDp(guarded), .001f)
        assertEquals(
            60f,
            914f - (
                guarded.rightSlotCenterXDp +
                    guarded.sideWidthDp * FocusGalleryGeometryPolicy.SIDE_SCALE / 2f
            ),
            .001f,
        )
    }

    @Test
    fun `D scaled side cards keep the outer edge at the safe margin with a 35dp inner gap`() {
        val g = geometry() // Pixel 8 914dp stage.
        val outer = FocusGalleryGeometryPolicy.outerMarginDp(914f, 0f)
        // Visible (post-SIDE_SCALE) outer edge sits exactly on the safe margin; the inner
        // edge lands at outer + sideWidth*SIDE_SCALE.
        assertEquals(outer, FocusGalleryGeometryPolicy.sideVisualLeftDp(g), .001f)
        assertEquals(
            outer + g.sideWidthDp * FocusGalleryGeometryPolicy.SIDE_SCALE,
            FocusGalleryGeometryPolicy.sideVisualRightDp(g),
            .001f,
        )
        // V6: pure-geometry inner gap is clearly >= 35dp (~57dp on Pixel 8 with the 0.42
        // side width) and mirrored left/right.
        val leftGap = FocusGalleryGeometryPolicy.sideInnerGapDp(g)
        assertTrue(leftGap >= 35f)
        assertTrue(leftGap >= FocusGalleryGeometryPolicy.D_INNER_GAP_MIN_DP)
        val centerRight = g.centerSlotCenterXDp + g.centerWidthDp / 2f
        val rightVisualLeft =
            g.rightSlotCenterXDp - g.sideWidthDp * FocusGalleryGeometryPolicy.SIDE_SCALE / 2f
        assertEquals(leftGap, rightVisualLeft - centerRight, .001f)
        assertEquals(g.rightSlotCenterXDp, 914f - g.leftSlotCenterXDp, .001f)
    }

    @Test
    fun `D arc lifts and rotates side cards toward the center continuously`() {
        val g = geometry()
        assertEquals(24f, g.arcLiftDp, .001f)
        assertEquals(16f, g.arcRotationDegrees, .001f)

        val mapped = cards(5, 2, FocusDirection.ADVANCE)
        val left =
            FocusGalleryGeometryPolicy.cardGeometry(
                mapped.first { it.sourceSlot == FocusSlot.LEFT },
                0f,
                g,
            )
        val right =
            FocusGalleryGeometryPolicy.cardGeometry(
                mapped.first { it.sourceSlot == FocusSlot.RIGHT },
                0f,
                g,
            )
        val center =
            FocusGalleryGeometryPolicy.cardGeometry(
                mapped.first { it.sourceSlot == FocusSlot.CENTER },
                0f,
                g,
            )
        assertEquals(FocusGalleryGeometryPolicy.D_ARC_LIFT_DP, left.offsetYDp, .001f)
        assertEquals(FocusGalleryGeometryPolicy.D_ARC_LIFT_DP, right.offsetYDp, .001f)
        assertEquals(0f, center.offsetYDp, .001f)
        assertEquals(-FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES, left.rotationYDegrees, .001f)
        assertEquals(FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES, right.rotationYDegrees, .001f)
        assertEquals(0f, center.rotationYDegrees, .001f)

        val incoming = mapped.first { it.role == FocusCardRole.SIDE_TO_CENTER }
        val oldCenter = mapped.first { it.role == FocusCardRole.CENTER_TO_SIDE }
        val entering = mapped.first { it.role == FocusCardRole.ENTERING }
        listOf(0f, .25f, .5f, .75f, 1f).forEach { p ->
            val incomingGeometry = FocusGalleryGeometryPolicy.cardGeometry(incoming, p, g)
            val oldGeometry = FocusGalleryGeometryPolicy.cardGeometry(oldCenter, p, g)
            val enteringGeometry = FocusGalleryGeometryPolicy.cardGeometry(entering, p, g)
            // Incoming side un-lifts and un-rotates toward the center with shared progress.
            assertEquals(
                FocusGalleryGeometryPolicy.D_ARC_LIFT_DP * (1f - p),
                incomingGeometry.offsetYDp,
                .001f,
            )
            assertEquals(
                FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES * (1f - p),
                incomingGeometry.rotationYDegrees,
                .001f,
            )
            // Old center lifts/rotates toward its exit side with the same progress.
            assertEquals(FocusGalleryGeometryPolicy.D_ARC_LIFT_DP * p, oldGeometry.offsetYDp, .001f)
            assertEquals(
                -FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES * p,
                oldGeometry.rotationYDegrees,
                .001f,
            )
            // Entering keeps the side arc of its target slot.
            assertEquals(FocusGalleryGeometryPolicy.D_ARC_LIFT_DP, enteringGeometry.offsetYDp, .001f)
            assertEquals(
                FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES,
                enteringGeometry.rotationYDegrees,
                .001f,
            )
            assertTrue(incomingGeometry.offsetYDp >= 0f)
            assertTrue(oldGeometry.offsetYDp >= 0f)
            assertTrue(
                kotlin.math.abs(incomingGeometry.rotationYDegrees) <=
                    FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES + .001f
            )
        }

        // Endpoints equal the committed resting frame (no teleport after commit).
        val endIncoming = FocusGalleryGeometryPolicy.cardGeometry(incoming, 1f, g)
        assertEquals(0f, endIncoming.offsetYDp, .001f)
        assertEquals(0f, endIncoming.rotationYDegrees, .001f)
        val endOld = FocusGalleryGeometryPolicy.cardGeometry(oldCenter, 1f, g)
        assertEquals(FocusGalleryGeometryPolicy.D_ARC_LIFT_DP, endOld.offsetYDp, .001f)
        assertEquals(-FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES, endOld.rotationYDegrees, .001f)
        val committed = FocusGalleryMappingPolicy.committedCurrentIndex(2, 5, FocusDirection.ADVANCE)
        val committedLeft =
            cards(5, committed, FocusDirection.ADVANCE)
                .first { it.sourceSlot == FocusSlot.LEFT }
        val committedGeometry = FocusGalleryGeometryPolicy.cardGeometry(committedLeft, 0f, g)
        assertEquals(endOld.offsetYDp, committedGeometry.offsetYDp, .001f)
        assertEquals(endOld.rotationYDegrees, committedGeometry.rotationYDegrees, .001f)
    }

    @Test
    fun `D back direction mirrors the arc signs`() {
        val g = geometry()
        val mapped = cards(5, 2, FocusDirection.BACK)
        val incoming = mapped.first { it.role == FocusCardRole.SIDE_TO_CENTER }
        val oldCenter = mapped.first { it.role == FocusCardRole.CENTER_TO_SIDE }
        listOf(0f, .25f, .5f, .75f, 1f).forEach { p ->
            val incomingGeometry = FocusGalleryGeometryPolicy.cardGeometry(incoming, p, g)
            val oldGeometry = FocusGalleryGeometryPolicy.cardGeometry(oldCenter, p, g)
            // BACK: the left side moves to center (rotation starts negative and ends 0),
            // the old center exits right (rotation grows toward positive).
            assertEquals(
                -FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES * (1f - p),
                incomingGeometry.rotationYDegrees,
                .001f,
            )
            assertEquals(
                FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES * p,
                oldGeometry.rotationYDegrees,
                .001f,
            )
        }
    }

    @Test
    fun `C keeps zero arc so the stacked geometry is unchanged`() {
        val g = geometry(LandscapeHomeStyle.FOCUS_POSTER)
        assertEquals(0f, g.arcLiftDp, .001f)
        assertEquals(0f, g.arcRotationDegrees, .001f)
        cards(5, 2, FocusDirection.ADVANCE).forEach { card ->
            listOf(0f, .25f, .5f, .75f, 1f).forEach { p ->
                val frame = FocusGalleryGeometryPolicy.cardGeometry(card, p, g)
                assertEquals(0f, frame.offsetYDp, .001f)
                assertEquals(0f, frame.rotationYDegrees, .001f)
            }
        }
    }

    @Test
    fun `reduced motion keeps the resting arc pose without motion`() {
        val g = geometry()
        cards(5, 0, FocusDirection.BACK).forEach { card ->
            val frames =
                listOf(.25f, .5f, .75f, 1f).map {
                    FocusGalleryGeometryPolicy.cardGeometry(card, it, g, reducedMotion = true)
                }
            frames.drop(1).forEach { frame ->
                assertEquals(frames.first().offsetYDp, frame.offsetYDp, .001f)
                assertEquals(frames.first().rotationYDegrees, frame.rotationYDegrees, .001f)
            }
            val idle = FocusGalleryGeometryPolicy.cardGeometry(card, 0f, g, reducedMotion = true)
            when (card.sourceSlot) {
                FocusSlot.LEFT -> {
                    assertEquals(FocusGalleryGeometryPolicy.D_ARC_LIFT_DP, idle.offsetYDp, .001f)
                    assertEquals(-FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES, idle.rotationYDegrees, .001f)
                }
                FocusSlot.RIGHT -> {
                    assertEquals(FocusGalleryGeometryPolicy.D_ARC_LIFT_DP, idle.offsetYDp, .001f)
                    assertEquals(FocusGalleryGeometryPolicy.D_ARC_ROTATION_DEGREES, idle.rotationYDegrees, .001f)
                }
                FocusSlot.CENTER -> {
                    assertEquals(0f, idle.offsetYDp, .001f)
                    assertEquals(0f, idle.rotationYDegrees, .001f)
                }
                FocusSlot.ENTERING -> Unit
            }
        }
    }

    @Test
    fun `focus poster geometry stays inside a cutout guarded short window`() {
        val g =
            geometry(
                style = LandscapeHomeStyle.FOCUS_POSTER,
                viewportWidthDp = 660f,
                symmetricSafeGuardDp = 60f,
                dockTopDp = 260f,
                stageHeightDp = 260f,
            )
        assertEquals(248f, g.centerHeightDp, .001f)
        assertEquals(139.5f, g.centerWidthDp, .001f)
        assertTrue(g.leftSlotCenterXDp >= g.sideWidthDp / 2f)
        assertTrue(g.rightSlotCenterXDp <= 660f - g.sideWidthDp / 2f)
        assertTrue(g.slotDistanceDp > 0f)
        assertTrue(
            FocusGalleryHitTargetPolicy.visibleSideWidthDp(g) >=
                FocusGalleryHitTargetPolicy.MIN_VISIBLE_SIDE_WIDTH_DP,
        )
    }

    @Test
    fun `focus poster exposes mirrored side hit targets outside the center card`() {
        val g = geometry(LandscapeHomeStyle.FOCUS_POSTER)
        val left =
            requireNotNull(
                FocusGalleryHitTargetPolicy.sideHitBounds(
                    FocusSlot.LEFT,
                    g,
                    LandscapeHomeStyle.FOCUS_POSTER,
                    0f,
                    false,
                ),
            )
        val right =
            requireNotNull(
                FocusGalleryHitTargetPolicy.sideHitBounds(
                    FocusSlot.RIGHT,
                    g,
                    LandscapeHomeStyle.FOCUS_POSTER,
                    0f,
                    false,
                ),
            )
        val centerLeft = g.centerSlotCenterXDp - g.centerWidthDp / 2f
        val centerRight = g.centerSlotCenterXDp + g.centerWidthDp / 2f

        // Both hit targets are at least 48dp wide and exactly mirrored.
        assertTrue(left.widthDp >= FocusGalleryHitTargetPolicy.MIN_HIT_WIDTH_DP)
        assertEquals(left.widthDp, right.widthDp, .001f)
        assertEquals(left.heightDp, right.heightDp, .001f)
        // LEFT ends exactly at the center's left edge; RIGHT starts at the center's right edge.
        assertEquals(centerLeft, left.leftDp + left.widthDp, .001f)
        assertEquals(centerRight, right.leftDp, .001f)
        // They never overlap the center card span.
        assertTrue(left.leftDp + left.widthDp <= centerLeft + .001f)
        assertTrue(right.leftDp >= centerRight - .001f)
        // Vertically they cover the scaled side strip above the card bottom.
        val visualHeight = g.sideHeightDp * FocusGalleryGeometryPolicy.SIDE_SCALE
        assertEquals(visualHeight, left.heightDp, .001f)
        assertEquals(g.centerHeightDp, left.topDp + left.heightDp, .001f)
        assertEquals(g.centerHeightDp, right.topDp + right.heightDp, .001f)
    }

    @Test
    fun `side hit targets exist only for C in idle frames`() {
        val gC = geometry(LandscapeHomeStyle.FOCUS_POSTER)
        val gD = geometry(LandscapeHomeStyle.IMMERSIVE_COVER)
        assertNull(
            FocusGalleryHitTargetPolicy.sideHitBounds(
                FocusSlot.LEFT,
                gD,
                LandscapeHomeStyle.IMMERSIVE_COVER,
                0f,
                false,
            ),
        )
        assertNull(
            FocusGalleryHitTargetPolicy.sideHitBounds(
                FocusSlot.LEFT,
                gC,
                LandscapeHomeStyle.FOCUS_POSTER,
                .01f,
                false,
            ),
        )
        assertNull(
            FocusGalleryHitTargetPolicy.sideHitBounds(
                FocusSlot.LEFT,
                gC,
                LandscapeHomeStyle.FOCUS_POSTER,
                0f,
                true,
            ),
        )
        assertNull(
            FocusGalleryHitTargetPolicy.sideHitBounds(
                FocusSlot.CENTER,
                gC,
                LandscapeHomeStyle.FOCUS_POSTER,
                0f,
                false,
            ),
        )
        assertNotNull(
            FocusGalleryHitTargetPolicy.sideHitBounds(
                FocusSlot.LEFT,
                gC,
                LandscapeHomeStyle.FOCUS_POSTER,
                0f,
                false,
            ),
        )
    }

    @Test
    fun `shared 520ms animation moves both cards continuously with no endpoint jump`() {
        val g = geometry()
        val samples =
            listOf(80f, 160f, 260f, 360f, 440f, 520f)
                .map { it / FocusGalleryController.TRANSITION_MILLIS }
        FocusDirection.entries.forEach { direction ->
            val mapped = cards(5, 2, direction)
            val incoming = mapped.first { it.role == FocusCardRole.SIDE_TO_CENTER }
            val oldCenter = mapped.first { it.role == FocusCardRole.CENTER_TO_SIDE }
            val incomingFrames = samples.map { FocusGalleryGeometryPolicy.cardGeometry(incoming, it, g) }
            val oldFrames = samples.map { FocusGalleryGeometryPolicy.cardGeometry(oldCenter, it, g) }
            listOf(incomingFrames, oldFrames).forEach { frames ->
                frames.zipWithNext().forEach { (before, after) ->
                    assertTrue(kotlin.math.abs(after.centerXDp - before.centerXDp) > .001f)
                    assertTrue(kotlin.math.abs(after.widthDp - before.widthDp) > .001f)
                    assertTrue(kotlin.math.abs(after.heightDp - before.heightDp) > .001f)
                    assertTrue(kotlin.math.abs(after.scale - before.scale) > .0001f)
                }
            }
            // Endpoint of the shared progress equals the committed resting frame.
            val endIncoming = FocusGalleryGeometryPolicy.cardGeometry(incoming, 1f, g)
            assertEquals(g.centerSlotCenterXDp, endIncoming.centerXDp, .001f)
            assertEquals(g.centerWidthDp, endIncoming.widthDp, .001f)
            assertEquals(g.centerHeightDp, endIncoming.heightDp, .001f)
            assertEquals(1f, endIncoming.scale, .001f)
            val endOldCenter = FocusGalleryGeometryPolicy.cardGeometry(oldCenter, 1f, g)
            assertEquals(
                if (direction == FocusDirection.ADVANCE) g.leftSlotCenterXDp else g.rightSlotCenterXDp,
                endOldCenter.centerXDp,
                .001f,
            )
            assertEquals(g.sideWidthDp, endOldCenter.widthDp, .001f)
            assertEquals(g.sideHeightDp, endOldCenter.heightDp, .001f)
            assertEquals(FocusGalleryGeometryPolicy.SIDE_SCALE, endOldCenter.scale, .001f)
        }
    }

    @Test
    fun `resting left and right are identical side treatment in both directions`() {
        val g = geometry()
        FocusDirection.entries.forEach { direction ->
            val mapped = cards(5, 2, direction)
            val left =
                FocusGalleryGeometryPolicy.cardGeometry(
                    mapped.first { it.sourceSlot == FocusSlot.LEFT },
                    0f,
                    g,
                )
            val center =
                FocusGalleryGeometryPolicy.cardGeometry(
                    mapped.first { it.sourceSlot == FocusSlot.CENTER },
                    0f,
                    g,
                )
            val right =
                FocusGalleryGeometryPolicy.cardGeometry(
                    mapped.first { it.sourceSlot == FocusSlot.RIGHT },
                    0f,
                    g,
                )
            listOf(left, right).forEach { side ->
                assertEquals(g.sideWidthDp, side.widthDp, .001f)
                assertEquals(g.sideHeightDp, side.heightDp, .001f)
                assertEquals(FocusGalleryGeometryPolicy.SIDE_SCALE, side.scale, .001f)
                // Policy geometry keeps the raw .58; the .84 boost is a Compose visual
                // layer applied only for D, so C and the state machine stay untouched.
                assertEquals(FocusGalleryGeometryPolicy.SIDE_ALPHA, side.alpha, .001f)
                assertEquals(FocusGalleryGeometryPolicy.SIDE_SATURATION, side.saturation, .001f)
                assertEquals(FocusGalleryGeometryPolicy.SIDE_RADIUS_DP, side.radiusDp, .001f)
            }
            assertEquals(g.centerWidthDp, center.widthDp, .001f)
            assertEquals(g.centerHeightDp, center.heightDp, .001f)
            assertEquals(1f, center.scale, .001f)
            assertEquals(1f, center.alpha, .001f)
            assertEquals(1f, center.saturation, .001f)
            assertEquals(FocusGalleryGeometryPolicy.CENTER_RADIUS_DP, center.radiusDp, .001f)
        }
    }

    @Test
    fun `advance and back motion stay continuous at sampled progress`() {
        val g = geometry()
        val samples = listOf(0f, .25f, .5f, .75f, 1f)
        FocusDirection.entries.forEach { direction ->
            cards(5, 2, direction).forEach { card ->
                val values = samples.map { FocusGalleryGeometryPolicy.cardGeometry(card, it, g) }
                values.zipWithNext().forEach { (before, after) ->
                    assertTrue(kotlin.math.abs(after.centerXDp - before.centerXDp) <= g.slotDistanceDp + .01f)
                    assertTrue(kotlin.math.abs(after.widthDp - before.widthDp) <= g.centerWidthDp - g.sideWidthDp + .01f)
                    assertTrue(kotlin.math.abs(after.heightDp - before.heightDp) <= g.centerHeightDp - g.sideHeightDp + .01f)
                    assertTrue(kotlin.math.abs(after.scale - before.scale) <= 1f - FocusGalleryGeometryPolicy.SIDE_SCALE + .001f)
                    assertTrue(after.alpha in 0f..1f)
                }
            }
            val incoming = cards(5, 2, direction).first { it.role == FocusCardRole.SIDE_TO_CENTER }
            val oldCenter = cards(5, 2, direction).first { it.role == FocusCardRole.CENTER_TO_SIDE }
            assertEquals(g.centerSlotCenterXDp, FocusGalleryGeometryPolicy.cardGeometry(incoming, 1f, g).centerXDp, .01f)
            val expectedSide = if (direction == FocusDirection.ADVANCE) g.leftSlotCenterXDp else g.rightSlotCenterXDp
            assertEquals(expectedSide, FocusGalleryGeometryPolicy.cardGeometry(oldCenter, 1f, g).centerXDp, .01f)
        }
    }

    @Test
    fun `progress one matches post commit resting frame without stack or teleport`() {
        val g = geometry()
        FocusDirection.entries.forEach { direction ->
            val beforeCommit =
                cards(5, 2, direction)
                    .associateBy({ it.gameId }, { FocusGalleryGeometryPolicy.cardGeometry(it, 1f, g) })
                    .filterValues { it.alpha > .001f }
            val committed = FocusGalleryMappingPolicy.committedCurrentIndex(2, 5, direction)
            val afterCommit =
                cards(5, committed, direction)
                    .associateBy({ it.gameId }, { FocusGalleryGeometryPolicy.cardGeometry(it, 0f, g) })
                    .filterValues { it.alpha > .001f }
            assertEquals(beforeCommit.keys, afterCommit.keys)
            beforeCommit.forEach { (id, before) ->
                val after = requireNotNull(afterCommit[id])
                assertEquals(before.centerXDp, after.centerXDp, .001f)
                assertEquals(before.widthDp, after.widthDp, .001f)
                assertEquals(before.heightDp, after.heightDp, .001f)
                assertEquals(before.scale, after.scale, .001f)
                assertEquals(before.alpha, after.alpha, .001f)
                assertEquals(before.saturation, after.saturation, .001f)
                assertEquals(before.radiusDp, after.radiusDp, .001f)
                assertEquals(before.offsetYDp, after.offsetYDp, .001f)
                assertEquals(before.rotationYDegrees, after.rotationYDegrees, .001f)
            }
        }
    }

    @Test
    fun `incoming card owns overlap and metadata remains on the focus card`() {
        val g = geometry()
        val mapped = cards(5, 0, FocusDirection.ADVANCE)
        val incoming = mapped.first { it.role == FocusCardRole.SIDE_TO_CENTER }
        val oldCenter = mapped.first { it.role == FocusCardRole.CENTER_TO_SIDE }
        val entering = mapped.first { it.role == FocusCardRole.ENTERING }
        listOf(.25f, .5f, .75f).forEach { p ->
            val incomingGeometry = FocusGalleryGeometryPolicy.cardGeometry(incoming, p, g)
            val oldGeometry = FocusGalleryGeometryPolicy.cardGeometry(oldCenter, p, g)
            val enteringGeometry = FocusGalleryGeometryPolicy.cardGeometry(entering, p, g)
            assertEquals(2f + 2f * p, incomingGeometry.zIndex, .001f)
            assertEquals(3f - p, oldGeometry.zIndex, .001f)
            assertEquals(1f, enteringGeometry.zIndex, .001f)
            // The incoming card owns overlap from the takeover point (p > 1/3) onward.
            if (p >= .5f) assertTrue(incomingGeometry.zIndex > oldGeometry.zIndex)
            assertTrue(incomingGeometry.infoAlpha >= 0f)
            assertTrue(oldGeometry.infoAlpha >= 0f)
        }
        assertEquals(0f, FocusGalleryGeometryPolicy.cardGeometry(entering, .5f, g).alpha, .001f)
        val exiting = mapped.first { it.role == FocusCardRole.EXITING }
        assertEquals(0f, FocusGalleryGeometryPolicy.cardGeometry(exiting, .5f, g).alpha, .001f)
    }

    @Test
    fun `zIndex follows the incoming takeover formula for advance and back`() {
        val g = geometry()
        FocusDirection.entries.forEach { direction ->
            val mapped = cards(5, 0, direction)
            val incoming = mapped.first { it.role == FocusCardRole.SIDE_TO_CENTER }
            val oldCenter = mapped.first { it.role == FocusCardRole.CENTER_TO_SIDE }
            listOf(0f, .25f, .5f, .75f, 1f).forEach { p ->
                assertEquals(2f + 2f * p, FocusGalleryGeometryPolicy.zIndexAt(incoming, p), .001f)
                assertEquals(3f - p, FocusGalleryGeometryPolicy.zIndexAt(oldCenter, p), .001f)
            }
            assertTrue(
                FocusGalleryGeometryPolicy.zIndexAt(incoming, .3f) <
                    FocusGalleryGeometryPolicy.zIndexAt(oldCenter, .3f)
            )
            assertTrue(
                FocusGalleryGeometryPolicy.zIndexAt(incoming, .6f) >
                    FocusGalleryGeometryPolicy.zIndexAt(oldCenter, .6f)
            )
        }
    }

    @Test
    fun `resting center is above every side card in both styles and directions`() {
        listOf(
            LandscapeHomeStyle.IMMERSIVE_COVER,
            LandscapeHomeStyle.FOCUS_POSTER,
        ).forEach { style ->
            val g = geometry(style)
            FocusDirection.entries.forEach { direction ->
                val mapped = cards(5, 2, direction)
                val center =
                    FocusGalleryGeometryPolicy.cardGeometry(
                        mapped.first { it.sourceSlot == FocusSlot.CENTER },
                        0f,
                        g,
                    )
                val sides =
                    mapped.filter {
                        it.sourceSlot == FocusSlot.LEFT || it.sourceSlot == FocusSlot.RIGHT
                    }.map { FocusGalleryGeometryPolicy.cardGeometry(it, 0f, g) }
                assertTrue(sides.all { center.zIndex > it.zIndex })
            }
        }
    }

    @Test
    fun `reduced motion rests cards at their slots with the center above sides`() {
        val g = geometry(LandscapeHomeStyle.FOCUS_POSTER)
        FocusDirection.entries.forEach { direction ->
            val mapped = cards(5, 2, direction)
            val center =
                FocusGalleryGeometryPolicy.cardGeometry(
                    mapped.first { it.sourceSlot == FocusSlot.CENTER },
                    0f,
                    g,
                    reducedMotion = true,
                )
            val sides =
                mapped.filter {
                    it.sourceSlot == FocusSlot.LEFT || it.sourceSlot == FocusSlot.RIGHT
                }.map { FocusGalleryGeometryPolicy.cardGeometry(it, 0f, g, reducedMotion = true) }
            assertTrue(sides.all { center.zIndex > it.zIndex })
            sides.forEach { side ->
                assertEquals(g.sideWidthDp, side.widthDp, .001f)
                assertEquals(g.sideHeightDp, side.heightDp, .001f)
                // Raw policy alpha remains .58; D's .84 readability lift is Compose-only.
                assertEquals(FocusGalleryGeometryPolicy.SIDE_ALPHA, side.alpha, .001f)
                assertEquals(FocusGalleryGeometryPolicy.SIDE_SCALE, side.scale, .001f)
            }
            assertEquals(g.centerWidthDp, center.widthDp, .001f)
            assertEquals(g.centerHeightDp, center.heightDp, .001f)
            assertEquals(1f, center.alpha, .001f)
        }
    }

    @Test
    fun `reduced motion handoff holds fixed position width and scale while fading`() {
        val g = geometry()
        cards(5, 0, FocusDirection.BACK).forEach { card ->
            val frames =
                listOf(.25f, .5f, .75f, 1f).map {
                    FocusGalleryGeometryPolicy.cardGeometry(card, it, g, reducedMotion = true)
                }
            frames.drop(1).forEach { frame ->
                assertEquals(frames.first().centerXDp, frame.centerXDp, .001f)
                assertEquals(frames.first().widthDp, frame.widthDp, .001f)
                assertEquals(frames.first().heightDp, frame.heightDp, .001f)
                assertEquals(frames.first().scale, frame.scale, .001f)
                assertEquals(frames.first().radiusDp, frame.radiusDp, .001f)
            }
            // The idle frame rests at the slot geometry, not a mid-air center crossfade.
            val idle = FocusGalleryGeometryPolicy.cardGeometry(card, 0f, g, reducedMotion = true)
            if (card.role == FocusCardRole.SIDE_TO_CENTER) {
                assertEquals(g.sideWidthDp, idle.widthDp, .001f)
                assertEquals(g.sideHeightDp, idle.heightDp, .001f)
                // Still the raw policy alpha; the D-only boost happens at graphicsLayer.
                assertEquals(FocusGalleryGeometryPolicy.SIDE_ALPHA, idle.alpha, .001f)
            }
        }
    }

    @Test
    fun `navigation plan reaches a tapped game multiple steps away`() {
        assertEquals(
            FocusNavigationPlan(FocusDirection.ADVANCE, 2, 2),
            FocusGalleryInteractionPolicy.navigationPlan(0, 2, 5),
        )
        assertEquals(
            FocusNavigationPlan(FocusDirection.BACK, 2, 3),
            FocusGalleryInteractionPolicy.navigationPlan(0, 3, 5),
        )
        assertEquals(
            FocusNavigationPlan(FocusDirection.ADVANCE, 1, 0),
            FocusGalleryInteractionPolicy.navigationPlan(2, 0, 3),
        )
        assertNull(FocusGalleryInteractionPolicy.navigationPlan(2, 2, 5))
    }

    @Test
    fun `two game plans resolve both side taps to the same alternate target`() {
        val leftTap = FocusGalleryInteractionPolicy.navigationPlan(0, 1, 2)
        val rightTap = FocusGalleryInteractionPolicy.navigationPlan(0, 1, 2)
        assertEquals(1, requireNotNull(leftTap).targetIndex)
        assertEquals(1, requireNotNull(rightTap).targetIndex)
        assertEquals(
            FocusNavigationPlan(FocusDirection.ADVANCE, 1, 0),
            FocusGalleryInteractionPolicy.navigationPlan(1, 0, 2),
        )
    }

    @Test
    fun `switch policy keeps the tapped card deterministic during settle`() {
        assertEquals(
            FocusSwitchResult.NAVIGATED,
            FocusSwitchPolicy.decide(
                currentGameIndex = 0,
                targetGameIndex = 1,
                isSettling = false,
                settlingTargetIndex = 0,
            ),
        )
        assertEquals(
            FocusSwitchResult.CONTINUING,
            FocusSwitchPolicy.decide(
                currentGameIndex = 0,
                targetGameIndex = 1,
                isSettling = true,
                settlingTargetIndex = 1,
            ),
        )
        assertEquals(
            FocusSwitchResult.STAYED,
            FocusSwitchPolicy.decide(
                currentGameIndex = 0,
                targetGameIndex = 0,
                isSettling = true,
                settlingTargetIndex = 1,
            ),
        )
        assertEquals(
            FocusSwitchResult.STAYED,
            FocusSwitchPolicy.decide(
                currentGameIndex = 0,
                targetGameIndex = 0,
                isSettling = false,
                settlingTargetIndex = 0,
            ),
        )
        assertEquals(
            FocusSwitchResult.IGNORED,
            FocusSwitchPolicy.decide(
                currentGameIndex = 0,
                targetGameIndex = -1,
                isSettling = false,
                settlingTargetIndex = 0,
            ),
        )
    }

    @Test
    fun `center and play actions require a settled center with visible metadata`() {
        assertTrue(FocusGalleryInteractionPolicy.centerActionEnabled(FocusSlot.CENTER, 0f, false, 1f))
        assertFalse(FocusGalleryInteractionPolicy.centerActionEnabled(FocusSlot.CENTER, .5f, true, 1f))
        assertFalse(FocusGalleryInteractionPolicy.centerActionEnabled(FocusSlot.CENTER, 1f, true, 1f))
        assertFalse(FocusGalleryInteractionPolicy.centerActionEnabled(FocusSlot.RIGHT, 0f, false, 1f))
        assertFalse(FocusGalleryInteractionPolicy.centerActionEnabled(FocusSlot.CENTER, 0f, false, .8f))
    }

    @Test
    fun `two game accessibility exposes only one duplicate switch action`() {
        val cards = cards(2, 0, FocusDirection.ADVANCE)
        assertEquals(
            1,
            cards.count { FocusGalleryInteractionPolicy.exposesSwitchAction(2, it, isSettling = false) },
        )
        assertEquals(
            2,
            cards(5, 0, FocusDirection.ADVANCE)
                .count { FocusGalleryInteractionPolicy.exposesSwitchAction(5, it, isSettling = false) },
        )
        assertEquals(
            0,
            cards.count { FocusGalleryInteractionPolicy.exposesSwitchAction(2, it, isSettling = true) },
        )
    }

    @Test
    fun `C semantics live only on the overlay and never duplicate the underlying card`() {
        listOf(
            FocusDirection.ADVANCE,
            FocusDirection.BACK,
        ).forEach { direction ->
            val twoGameCards = cards(2, 0, direction)
            val fiveGameCards = cards(5, 0, direction)
            listOf(2, 5).forEach { gameCount ->
                val mapped = if (gameCount == 2) twoGameCards else fiveGameCards
                // Underlying cards never expose the action in C.
                val onCard =
                    mapped.count {
                        FocusGallerySemanticsPolicy.cardExposesSwitchSemantics(
                            LandscapeHomeStyle.FOCUS_POSTER,
                            gameCount,
                            it,
                            isSettling = false,
                        )
                    }
                assertEquals(0, onCard)
                // Only the overlay exposes it; two-game duplicates dedupe to exactly one.
                val onOverlay =
                    mapped.count {
                        FocusGallerySemanticsPolicy.overlayExposesSwitchSemantics(
                            LandscapeHomeStyle.FOCUS_POSTER,
                            gameCount,
                            it,
                            isSettling = false,
                        )
                    }
                assertEquals(if (gameCount == 2) 1 else 2, onOverlay)
                assertEquals(onOverlay, onCard + onOverlay)
            }
        }
    }

    @Test
    fun `D keeps card switch semantics and never adds an overlay action`() {
        val mapped = cards(5, 0, FocusDirection.ADVANCE)
        val onCard =
            mapped.count {
                FocusGallerySemanticsPolicy.cardExposesSwitchSemantics(
                    LandscapeHomeStyle.IMMERSIVE_COVER,
                    5,
                    it,
                    isSettling = false,
                )
            }
        assertEquals(2, onCard)
        val onOverlay =
            mapped.count {
                FocusGallerySemanticsPolicy.overlayExposesSwitchSemantics(
                    LandscapeHomeStyle.IMMERSIVE_COVER,
                    5,
                    it,
                    isSettling = false,
                )
            }
        assertEquals(0, onOverlay)
    }

    @Test
    fun `C overlay semantics disappear while settling`() {
        val mapped = cards(2, 0, FocusDirection.ADVANCE)
        assertTrue(
            FocusGallerySemanticsPolicy.overlayExposesSwitchSemantics(
                LandscapeHomeStyle.FOCUS_POSTER,
                2,
                mapped[0],
                isSettling = false,
            ),
        )
        assertFalse(
            FocusGallerySemanticsPolicy.overlayExposesSwitchSemantics(
                LandscapeHomeStyle.FOCUS_POSTER,
                2,
                mapped[0],
                isSettling = true,
            ),
        )
    }

    @Test
    fun `auto timer pauses for press and restarts only from idle release`() {
        assertTrue(FocusGalleryAutoAdvancePolicy.shouldRunTimer(2, true, true, true, false))
        assertFalse(FocusGalleryAutoAdvancePolicy.shouldRunTimer(2, true, true, true, true))
        assertFalse(FocusGalleryAutoAdvancePolicy.shouldRunTimer(2, false, true, true, false))
        assertFalse(FocusGalleryAutoAdvancePolicy.shouldRunTimer(2, true, false, true, false))
        assertFalse(FocusGalleryAutoAdvancePolicy.shouldRunTimer(2, true, true, false, false))
    }

    @Test
    fun `drag delta and settle policy retain direction semantics`() {
        assertEquals(.5f, FocusGalleryInteractionPolicy.progressFromDragDelta(-.5f, FocusDirection.ADVANCE, 1f), .001f)
        assertEquals(.5f, FocusGalleryInteractionPolicy.progressFromDragDelta(.5f, FocusDirection.BACK, 1f), .001f)
        assertTrue(FocusGalleryInteractionPolicy.settle(.8f, 0f, FocusDirection.ADVANCE).settleToCommitted)
        assertFalse(FocusGalleryInteractionPolicy.settle(.2f, 0f, FocusDirection.ADVANCE).settleToCommitted)
        assertTrue(FocusGalleryInteractionPolicy.settle(.3f, -1.2f, FocusDirection.ADVANCE).settleToCommitted)
        assertTrue(FocusGalleryInteractionPolicy.settle(.3f, 1.2f, FocusDirection.BACK).settleToCommitted)
    }

    @Test
    fun `settle thresholds are 0-28 page distance and 0-95 page per second velocity`() {
        assertEquals(.28f, FocusGalleryInteractionPolicy.CLAIM_PROGRESS, .001f)
        assertEquals(.95f, FocusGalleryInteractionPolicy.CLAIM_VELOCITY_PAGES_PER_SECOND, .001f)
    }

    @Test
    fun `subthreshold release returns to the anchor and never commits`() {
        val decision = FocusGalleryInteractionPolicy.settle(.27f, 0f, FocusDirection.ADVANCE)
        assertFalse(decision.settleToCommitted)
        assertEquals(0f, decision.targetProgress, .001f)
        // A slow fling below both thresholds still returns to the anchor.
        val slowFling = FocusGalleryInteractionPolicy.settle(.27f, .5f, FocusDirection.ADVANCE)
        assertFalse(slowFling.settleToCommitted)
        assertEquals(0f, slowFling.targetProgress, .001f)
    }

    @Test
    fun `threshold commit settles to the next page on distance alone`() {
        val decision = FocusGalleryInteractionPolicy.settle(.28f, 0f, FocusDirection.ADVANCE)
        assertTrue(decision.settleToCommitted)
        assertEquals(1f, decision.targetProgress, .001f)
        assertEquals(FocusDirection.ADVANCE, decision.direction)
    }

    @Test
    fun `fast fling commits one page regardless of how far it crossed the threshold`() {
        listOf(
            FocusGalleryInteractionPolicy.settle(.05f, -1f, FocusDirection.ADVANCE),
            FocusGalleryInteractionPolicy.settle(.05f, -6f, FocusDirection.ADVANCE),
            FocusGalleryInteractionPolicy.settle(.05f, 1f, FocusDirection.BACK),
            FocusGalleryInteractionPolicy.settle(.05f, 6f, FocusDirection.BACK),
        ).forEach { decision ->
            // Max one page: the target is always exactly 1.0, never beyond.
            assertTrue(decision.settleToCommitted)
            assertEquals(1f, decision.targetProgress, .001f)
        }
    }

    @Test
    fun `fling direction must match the requested direction to commit`() {
        // Leftward velocity with BACK (or rightward with ADVANCE) is a reversal: return.
        val reversed = FocusGalleryInteractionPolicy.settle(.05f, -1.2f, FocusDirection.BACK)
        assertFalse(reversed.settleToCommitted)
        assertEquals(0f, reversed.targetProgress, .001f)
        val reversedAdvance = FocusGalleryInteractionPolicy.settle(.05f, 1.2f, FocusDirection.ADVANCE)
        assertFalse(reversedAdvance.settleToCommitted)
        assertEquals(0f, reversedAdvance.targetProgress, .001f)
    }

    @Test
    fun `settle tolerance stays one pixel and five thousandths scale`() {
        assertTrue(FocusGalleryInteractionPolicy.isSettled(1f, 354f))
        assertTrue(FocusGalleryInteractionPolicy.isSettled(.998f, 354f))
        assertFalse(FocusGalleryInteractionPolicy.isSettled(.997f, 354f))
    }

    @Test
    fun `D alpha boost maps endpoints and sample midpoints`() {
        val visual = FocusGalleryVisualPolicy
        assertEquals(0f, visual.boostDAlpha(0f), .001f)
        assertEquals(visual.D_SIDE_ALPHA, visual.boostDAlpha(FocusGalleryGeometryPolicy.SIDE_ALPHA), .001f)
        assertEquals(1f, visual.boostDAlpha(1f), .001f)
        // .29 -> .29 * .84/.58 = .42; .79 -> .84 + (.79-.58) * .16/.42 = .92.
        assertEquals(.42f, visual.boostDAlpha(.29f), .001f)
        assertEquals(.92f, visual.boostDAlpha(.79f), .001f)
    }

    @Test
    fun `D alpha boost stays monotonic and clamped`() {
        val samples = (0..20).map { it / 20f }
        samples.zipWithNext().forEach { (lower, upper) ->
            assertTrue(
                FocusGalleryVisualPolicy.boostDAlpha(upper) >=
                    FocusGalleryVisualPolicy.boostDAlpha(lower) - .0001f,
            )
        }
        assertTrue(
            FocusGalleryVisualPolicy.boostDAlpha(.57f) <=
                FocusGalleryVisualPolicy.boostDAlpha(.58f) + .0001f,
        )
        assertTrue(
            FocusGalleryVisualPolicy.boostDAlpha(.58f) <=
                FocusGalleryVisualPolicy.boostDAlpha(.59f) + .0001f,
        )
        assertEquals(0f, FocusGalleryVisualPolicy.boostDAlpha(-.5f), .001f)
        assertEquals(1f, FocusGalleryVisualPolicy.boostDAlpha(1.5f), .001f)
        listOf(0f, .1f, .29f, .5f, .58f, .79f, .9f, 1f).forEach {
            assertTrue(FocusGalleryVisualPolicy.boostDAlpha(it) in 0f..1f)
        }
    }

    @Test
    fun `C stage alpha stays identity and only D applies the readability boost`() {
        val g = geometry()
        FocusDirection.entries.forEach { direction ->
            cards(5, 2, direction).forEach { card ->
                listOf(0f, .25f, .5f, .75f, 1f).forEach { p ->
                    val frame = FocusGalleryGeometryPolicy.cardGeometry(card, p, g)
                    assertEquals(
                        frame.alpha,
                        FocusGalleryVisualPolicy.stageAlpha(LandscapeHomeStyle.FOCUS_POSTER, frame.alpha),
                        .001f,
                    )
                }
            }
        }
        // The policy geometry still reports the raw .58; the boost lives only in the
        // Compose visual layer for D, so C and the state machine are untouched.
        val side =
            FocusGalleryGeometryPolicy.cardGeometry(
                cards(5, 2, FocusDirection.ADVANCE).first { it.sourceSlot == FocusSlot.LEFT },
                0f,
                g,
            )
        assertEquals(FocusGalleryGeometryPolicy.SIDE_ALPHA, side.alpha, .001f)
        assertEquals(
            FocusGalleryVisualPolicy.D_SIDE_ALPHA,
            FocusGalleryVisualPolicy.stageAlpha(LandscapeHomeStyle.IMMERSIVE_COVER, side.alpha),
            .001f,
        )
    }

    @Test
    fun `D outline alpha fades from the side to the center by info alpha`() {
        val visual = FocusGalleryVisualPolicy
        assertEquals(visual.D_OUTLINE_ALPHA_DARK_SIDE, visual.outlineAlpha(0f, darkTheme = true), .001f)
        assertEquals(visual.D_OUTLINE_ALPHA_DARK_CENTER, visual.outlineAlpha(1f, darkTheme = true), .001f)
        assertEquals(visual.D_OUTLINE_ALPHA_LIGHT_SIDE, visual.outlineAlpha(0f, darkTheme = false), .001f)
        assertEquals(visual.D_OUTLINE_ALPHA_LIGHT_CENTER, visual.outlineAlpha(1f, darkTheme = false), .001f)
        assertEquals(.38f, visual.outlineAlpha(.5f, darkTheme = true), .001f)
        assertEquals(.28f, visual.outlineAlpha(.5f, darkTheme = false), .001f)
        assertTrue(visual.outlineAlpha(0f, darkTheme = true) > visual.outlineAlpha(1f, darkTheme = true))
        assertTrue(visual.outlineAlpha(0f, darkTheme = false) > visual.outlineAlpha(1f, darkTheme = false))
    }

    @Test
    fun `D owns outer artwork and C keeps artwork inside the page`() {
        assertTrue(FocusGalleryVisualPolicy.ownsOuterArtwork(LandscapeHomeStyle.IMMERSIVE_COVER))
        assertFalse(FocusGalleryVisualPolicy.ownsOuterArtwork(LandscapeHomeStyle.FOCUS_POSTER))
    }

    @Test
    fun `D merges bottom gap into the outer offset and C keeps identity`() {
        val visual = FocusGalleryVisualPolicy
        val gD = geometry(LandscapeHomeStyle.IMMERSIVE_COVER)
        val gC = geometry(LandscapeHomeStyle.FOCUS_POSTER)
        // D: the outer offset absorbs the bottom gap, so the inner page stops offsetting.
        assertEquals(
            -(gD.arcLiftDp + gD.bottomGapDp),
            visual.outerOffsetY(
                LandscapeHomeStyle.IMMERSIVE_COVER,
                gD.arcLiftDp,
                gD.bottomGapDp,
            ),
            .001f,
        )
        assertEquals(
            0f,
            visual.innerBottomGap(LandscapeHomeStyle.IMMERSIVE_COVER, gD.bottomGapDp),
            .001f,
        )
        // C: identity outer offset and unchanged inner gap lock the existing stacked layout.
        assertEquals(0f, gC.arcLiftDp, .001f)
        assertEquals(
            -gC.arcLiftDp,
            visual.outerOffsetY(
                LandscapeHomeStyle.FOCUS_POSTER,
                gC.arcLiftDp,
                gC.bottomGapDp,
            ),
            .001f,
        )
        assertEquals(
            gC.bottomGapDp,
            visual.innerBottomGap(LandscapeHomeStyle.FOCUS_POSTER, gC.bottomGapDp),
            .001f,
        )
        // C identity also holds for a hypothetical nonzero arc: the gap is never merged.
        assertEquals(-12.5f, visual.outerOffsetY(LandscapeHomeStyle.FOCUS_POSTER, 12.5f, 80f), .001f)
        assertEquals(80f, visual.innerBottomGap(LandscapeHomeStyle.FOCUS_POSTER, 80f), .001f)
    }

    @Test
    fun `outer and inner offsets compose to the same card bottom for C and D`() {
        val visual = FocusGalleryVisualPolicy
        listOf(
            LandscapeHomeStyle.IMMERSIVE_COVER,
            LandscapeHomeStyle.FOCUS_POSTER,
        ).forEach { style ->
            val g = geometry(style)
            val outerY = visual.outerOffsetY(style, g.arcLiftDp, g.bottomGapDp)
            val innerY = -visual.innerBottomGap(style, g.bottomGapDp)
            // Both styles rest the card bottom exactly arcLift + gap above the stage bottom.
            assertEquals(-(g.arcLiftDp + g.bottomGapDp), outerY + innerY, .001f)
        }
    }
}
