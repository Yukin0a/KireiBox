package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryTrackControllerTest {
    @Test
    fun startsOnRequestedGame() {
        val c = GalleryTrackController(6, initialIndex = 2)
        val s = c.snapshot()
        assertEquals(2f, s.visualPosition, 0f)
        assertEquals(2f, s.targetPosition, 0f)
        assertEquals(2, s.activeGameIndex)
        assertEquals(0f, s.velocity, 0f)
        assertFalse(s.isDragging)
    }

    @Test
    fun emptyLibraryIsInert() {
        val c = GalleryTrackController(0)
        c.stepBy(1)
        c.jumpTo(0)
        c.beginDrag()
        c.dragBy(3f)
        c.endDrag(5f)
        val s = c.snapshot()
        assertEquals(-1, s.activeGameIndex)
        assertEquals(0f, s.visualPosition, 0f)
        assertEquals(0f, s.targetPosition, 0f)
        assertFalse(s.isDragging)
    }

    @Test
    fun singleGameIsInertAndAlwaysActive() {
        val c = GalleryTrackController(1)
        c.stepBy(1)
        c.stepBy(-1)
        c.jumpTo(0)
        c.beginDrag()
        c.dragBy(2f)
        c.endDrag(3f)
        val s = c.snapshot()
        assertEquals(0, s.activeGameIndex)
        assertEquals(0f, s.visualPosition, 0f)
        assertEquals(0f, s.targetPosition, 0f)
    }

    @Test
    fun clicksAccumulateAtMostThreeSlotsAhead() {
        val c = GalleryTrackController(6)
        c.stepBy(1)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(2f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun clickDuringAnimationMovesTargetWithoutResettingVisualOrVelocity() {
        val c = GalleryTrackController(6)
        c.onVisualFrame(1.2f, 1.5f)
        c.jumpTo(2)
        assertEquals(2f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(4f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(4f, c.snapshot().targetPosition, 0f)
        assertEquals(1.2f, c.snapshot().visualPosition, 0f)
        assertEquals(1.5f, c.snapshot().velocity, 0f)
    }

    @Test
    fun reverseClickTakesOverAtTheNearestVirtualAnchor() {
        val c = GalleryTrackController(6)
        repeat(3) { c.stepBy(1) }
        assertEquals(3f, c.snapshot().targetPosition, 0f)
        c.stepBy(-1)
        assertEquals(-1f, c.snapshot().targetPosition, 0f)
        c.stepBy(-1)
        assertEquals(-2f, c.snapshot().targetPosition, 0f)
        c.stepBy(-1)
        assertEquals(-3f, c.snapshot().targetPosition, 0f)
        c.stepBy(-1)
        assertEquals(-3f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun reverseClickDuringForwardAnimationJumpsBehindVisual() {
        val c = GalleryTrackController(6)
        c.onVisualFrame(.4f, 1.5f)
        repeat(3) { c.stepBy(1) }
        assertEquals(3f, c.snapshot().targetPosition, 0f)
        c.stepBy(-1)
        val s = c.snapshot()
        assertEquals(-1f, s.targetPosition, 0f)
        assertTrue(s.targetPosition < s.visualPosition)
        assertEquals(1.5f, s.velocity, 0f)
    }

    @Test
    fun forwardClickDuringBackwardAnimationJumpsAheadOfVisual() {
        val c = GalleryTrackController(6)
        c.onVisualFrame(1.2f, -1.5f)
        c.stepBy(-1)
        assertEquals(-1f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        val s = c.snapshot()
        assertEquals(2f, s.targetPosition, 0f)
        assertTrue(s.targetPosition > s.visualPosition)
    }

    @Test
    fun sameDirectionAccumulationStillCapsAtThreeSlotsAhead() {
        val c = GalleryTrackController(6)
        c.onVisualFrame(.4f, 0f)
        repeat(3) { c.stepBy(1) }
        assertEquals(3f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun pagerJumpsToTheNearestEquivalentVirtualSlot() {
        val c1 = GalleryTrackController(6)
        c1.onVisualFrame(.2f, 0f)
        c1.jumpTo(5)
        assertEquals(-1f, c1.snapshot().targetPosition, 0f)

        val c2 = GalleryTrackController(6)
        c2.onVisualFrame(.2f, 0f)
        c2.jumpTo(3)
        assertEquals(3f, c2.snapshot().targetPosition, 0f)

        val c3 = GalleryTrackController(6)
        c3.onVisualFrame(5.8f, 0f)
        c3.jumpTo(0)
        assertEquals(6f, c3.snapshot().targetPosition, 0f)

        val c4 = GalleryTrackController(6)
        c4.onVisualFrame(5.8f, 0f)
        c4.jumpTo(1)
        assertEquals(7f, c4.snapshot().targetPosition, 0f)
    }

    @Test
    fun multiRevolutionDragReleaseKeepsTheVirtualAnchor() {
        val c = GalleryTrackController(5)
        c.onVisualFrame(12.2f, 0f)
        c.beginDrag()
        c.dragBy(.3f)
        c.endDrag(.05f)

        assertEquals(13f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun evenCountTieBreaksForward() {
        val c = GalleryTrackController(6)
        c.jumpTo(3)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun pagerClickOnAlreadyTargetedGameIsANoOp() {
        val c = GalleryTrackController(6)
        c.onVisualFrame(.3f, 1.2f)
        c.stepBy(1)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
        c.jumpTo(1)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
        assertEquals(1.2f, c.snapshot().velocity, 0f)
    }

    @Test
    fun dragOverridesTargetAndSettlesToNearestInteger() {
        val c = GalleryTrackController(6)
        c.stepBy(1)
        c.beginDrag()
        assertTrue(c.snapshot().isDragging)
        assertEquals(0f, c.snapshot().targetPosition, 0f)
        c.dragBy(.7f)
        val during = c.snapshot()
        assertEquals(.7f, during.targetPosition, 0f)
        assertEquals(.7f, during.visualPosition, 0f)
        assertEquals(1, during.activeGameIndex)
        c.stepBy(1)
        assertEquals(.7f, c.snapshot().targetPosition, 0f)
        c.endDrag(.1f)
        assertFalse(c.snapshot().isDragging)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
        assertEquals(.1f, c.snapshot().velocity, 0f)
    }

    @Test
    fun flingProjectsOneSlotAhead() {
        val c = GalleryTrackController(6)
        c.beginDrag()
        c.dragBy(.3f)
        c.endDrag(2f)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
        assertEquals(2f, c.snapshot().velocity, 0f)
    }

    @Test
    fun reverseFlingSettlesBackwards() {
        val c = GalleryTrackController(6, initialIndex = 4)
        c.beginDrag()
        c.dragBy(.8f)
        c.dragBy(-.6f)
        c.endDrag(-1.5f)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun activeGameSwitchesAtomicallyAtHalfStepWithFloorMod() {
        val c = GalleryTrackController(6)
        c.onVisualFrame(2.49f, 0f)
        assertEquals(2, c.snapshot().activeGameIndex)
        c.onVisualFrame(2.5f, 0f)
        assertEquals(3, c.snapshot().activeGameIndex)
        c.onVisualFrame(-.51f, 0f)
        assertEquals(5, c.snapshot().activeGameIndex)
        c.onVisualFrame(5.5f, 0f)
        assertEquals(0, c.snapshot().activeGameIndex)
    }

    @Test
    fun twoGameTrackIsBoundedAndNeverWraps() {
        // r5: two games used to wrap (virtual slots 2, 3 …), so the other game was shown on both sides.
        val c = GalleryTrackController(2)
        c.onVisualFrame(1.5f, 0f)
        assertEquals("past the last card the last game stays active", 1, c.snapshot().activeGameIndex)
        c.jumpTo(0)
        assertEquals(0f, c.snapshot().targetPosition, 0f)
        c.stepBy(-1)
        assertEquals("no card before the first one", 0f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals("no card after the last one", 1f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun forwardStepAtLastGameContinuesOnTheVirtualTrack() {
        val c = GalleryTrackController(6, initialIndex = 5)
        c.stepBy(1)
        assertEquals(6f, c.snapshot().targetPosition, 0f)
        c.onVisualFrame(6f, 0f)
        assertEquals(0, c.snapshot().activeGameIndex)
        c.stepBy(1)
        assertEquals(7f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun forwardStepAtLastGameOfASmallGalleryStaysOnIt() {
        // r5: a three-game gallery is bounded; it used to cross to the first game's virtual page.
        val c = GalleryTrackController(3, initialIndex = 2)

        c.stepBy(1)
        assertEquals(2f, c.snapshot().targetPosition, 0f)

        c.onVisualFrame(2.5f, 0f)
        assertEquals(2, c.snapshot().activeGameIndex)
    }

    @Test
    fun settleDurationIsProportionalAndReducedMotionOverrides() {
        val c = GalleryTrackController(6)
        c.stepBy(1)
        assertEquals(520, c.settleDurationMs())
        assertEquals(160, c.settleDurationMs(reducedMotion = true))
        c.stepBy(1)
        c.stepBy(1)
        assertEquals(520, c.settleDurationMs())

        val half = GalleryTrackController(6)
        half.onVisualFrame(1.5f, 0f)
        half.jumpTo(2)
        assertEquals(260, half.settleDurationMs())

        val tiny = GalleryTrackController(6)
        tiny.onVisualFrame(1.9f, 0f)
        tiny.jumpTo(2)
        assertEquals(160, tiny.settleDurationMs())

        val jumped = GalleryTrackController(6)
        jumped.jumpTo(4)
        assertEquals(520, jumped.settleDurationMs())
    }

    @Test
    fun cancelAnimationReturnsToCurrentVisualPosition() {
        val c = GalleryTrackController(6)
        c.onVisualFrame(.4f, 1.2f)
        c.stepBy(1)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
        c.cancelAnimation()
        assertEquals(.4f, c.snapshot().targetPosition, 0f)
        assertEquals(0f, c.snapshot().velocity, 0f)
    }

    @Test
    fun cancelAnimationClearsDraggingSoLaterDragsAreIgnored() {
        val c = GalleryTrackController(6)
        c.beginDrag()
        assertTrue(c.snapshot().isDragging)
        c.cancelAnimation()
        assertFalse(c.snapshot().isDragging)
        c.dragBy(1f)
        assertEquals(0f, c.snapshot().visualPosition, 0f)
    }

    @Test
    fun pagerJumpReplacesFractionalCancelledTargetWithoutTruncationBug() {
        val c = GalleryTrackController(6)
        c.onVisualFrame(1.9f, 0f)
        c.cancelAnimation()
        assertEquals(1.9f, c.snapshot().targetPosition, 0f)
        c.jumpTo(1)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun settleDurationRejectsMaxMsBelowMinimum() {
        val c = GalleryTrackController(6)
        c.stepBy(1)
        assertThrows(IllegalArgumentException::class.java) { c.settleDurationMs(maxMs = 159) }
        assertThrows(IllegalArgumentException::class.java) { c.settleDurationMs(maxMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { c.settleDurationMs(maxMs = 159, reducedMotion = true) }
    }

    @Test
    fun visualFramesNeverMoveTheTarget() {
        val c = GalleryTrackController(6)
        c.stepBy(1)
        c.onVisualFrame(.75f, 1.1f)
        assertEquals(1f, c.snapshot().targetPosition, 0f)
        assertEquals(.75f, c.snapshot().visualPosition, 0f)
    }

    @Test
    fun subThresholdSlowDragReturnsToTheOwningCard() {
        val c = GalleryTrackController(6, initialIndex = 2)
        c.beginDrag()
        c.dragBy(.2f)
        val during = c.snapshot().visualPosition
        assertEquals(2.2f, during, 0f)
        c.endDrag(.05f)
        assertEquals(2f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun ordinaryCommittedSwipeAdvancesExactlyOneCard() {
        val c = GalleryTrackController(6, initialIndex = 2)
        c.beginDrag()
        c.dragBy(.6f)
        assertEquals(2.6f, c.snapshot().visualPosition, 0f)
        c.endDrag(.1f)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun pointTwoZeroDragReturnsAndPointThreeZeroCommits() {
        val returning = GalleryTrackController(6, initialIndex = 2)
        returning.beginDrag()
        returning.dragBy(.2f)
        returning.endDrag(.05f)
        assertEquals(2f, returning.snapshot().targetPosition, 0f)

        val committing = GalleryTrackController(6, initialIndex = 2)
        committing.beginDrag()
        committing.dragBy(.3f)
        committing.endDrag(.05f)
        assertEquals(3f, committing.snapshot().targetPosition, 0f)
    }

    @Test
    fun inBoundsDragTracksFingerExactlyOneToOne() {
        assertEquals(
            2.45f,
            GalleryTrackTargetPolicy.resistedDragPosition(
                anchorPosition = 2f,
                rawDragDistance = .45f,
                itemCount = 6,
            ),
            0f,
        )
        assertEquals(
            2.75f,
            GalleryTrackTargetPolicy.resistedDragPosition(
                anchorPosition = 3f,
                rawDragDistance = -.25f,
                itemCount = 6,
            ),
            0f,
        )
    }

    @Test
    fun fastFlingAdvancesAtMostOneCardInEitherDirection() {
        val positive =
            listOf(10f, 16f, 22f).map { velocity ->
                GalleryTrackTargetPolicy.resolveTarget(
                    GalleryTrackGestureInput(4, dragDistance = .1f, velocity = velocity),
                    itemCount = 10,
                )
            }
        val negative =
            listOf(-10f, -16f, -22f).map { velocity ->
                GalleryTrackTargetPolicy.resolveTarget(
                    GalleryTrackGestureInput(4, dragDistance = -.1f, velocity = velocity),
                    itemCount = 10,
                )
            }

        assertEquals(listOf(5, 5, 5), positive)
        assertEquals(listOf(3, 3, 3), negative)
    }

    @Test
    fun flingIsCappedAtOneCardWithoutFiniteBoundaryClamping() {
        assertEquals(
            5,
            GalleryTrackTargetPolicy.resolveTarget(
                GalleryTrackGestureInput(4, dragDistance = .1f, velocity = 40f),
                itemCount = 20,
            ),
        )
        assertEquals(
            0,
            GalleryTrackTargetPolicy.resolveTarget(
                GalleryTrackGestureInput(1, dragDistance = -.1f, velocity = -40f),
                itemCount = 20,
            ),
        )
        assertEquals(
            5,
            GalleryTrackTargetPolicy.resolveTarget(
                GalleryTrackGestureInput(4, dragDistance = .1f, velocity = 40f),
                itemCount = 5,
            ),
        )
    }

    @Test
    fun edgeDragRemainsUnboundedAndReleasesAcrossTheSeam() {
        val resisted =
            GalleryTrackTargetPolicy.resistedDragPosition(
                anchorPosition = 0f,
                rawDragDistance = -2f,
                itemCount = 6,
            )
        assertEquals(-2f, resisted, 0f)

        val c = GalleryTrackController(6)
        c.beginDrag()
        c.dragBy(-2f)
        assertTrue(c.snapshot().visualPosition < 0f)
        assertEquals(-2f, c.snapshot().visualPosition, 0f)
        c.endDrag(0f)
        assertEquals(-2f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun longEdgeDragKeepsItsVirtualDistance() {
        val resisted =
            GalleryTrackTargetPolicy.resistedDragPosition(
                anchorPosition = 0f,
                rawDragDistance = -10_000f,
                itemCount = 6,
            )

        assertEquals(-10_000f, resisted, 0f)
    }

    @Test
    fun trailingEdgeDragKeepsItsVirtualDistanceAndWrapsForward() {
        val resisted =
            GalleryTrackTargetPolicy.resistedDragPosition(
                anchorPosition = 5f,
                rawDragDistance = 2f,
                itemCount = 6,
            )
        assertEquals(7f, resisted, 0f)

        val c = GalleryTrackController(6, initialIndex = 5)
        c.beginDrag()
        c.dragBy(2f)
        assertEquals(7f, c.snapshot().visualPosition, 0f)
        c.endDrag(0f)
        assertEquals(7f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun longDragKeepsEveryCardItCrossedAndSettlesOnTheNearest() {
        fun release(
            distance: Float,
            velocity: Float,
            recentIntent: Int = 0,
        ) = GalleryTrackTargetPolicy.resolveTarget(
            GalleryTrackGestureInput(10, dragDistance = distance, velocity = velocity, recentIntent = recentIntent),
            itemCount = 30,
        )

        assertEquals(12, release(2.3f, 0f))
        assertEquals(13, release(2.6f, 0f))
        assertEquals(7, release(-3.4f, 0f))
        // Still moving at release: the velocity adds one card, never more.
        assertEquals(13, release(2.1f, 3.5f))
        assertEquals(13, release(2.1f, 40f))
        // A late wiggle or reverse flick lands on the last card crossed instead of springing all the way back.
        assertEquals(12, release(2.4f, -.3f, recentIntent = -1))
        assertEquals(12, release(2.7f, -12f, recentIntent = -1))
        assertEquals(11, release(1.2f, -12f, recentIntent = -1))
    }

    @Test
    fun longDragIsCappedPerRelease() {
        assertEquals(
            10 + GalleryTrackTargetPolicy.MAX_RELEASE_CARDS,
            GalleryTrackTargetPolicy.resolveTarget(GalleryTrackGestureInput(10, dragDistance = 20f, velocity = 0f), itemCount = 30),
        )
        // Five games never travel a full revolution.
        assertEquals(
            4,
            GalleryTrackTargetPolicy.resolveTarget(GalleryTrackGestureInput(0, dragDistance = 7f, velocity = 0f), itemCount = 5),
        )
    }

    @Test
    fun longDragOnASmallGalleryStopsAtTheLastGame() {
        val c = GalleryTrackController(4)
        c.syncGameIds(listOf(1L, 2L, 3L, 4L))
        c.beginDrag()
        c.dragBy(2.2f)
        c.endDrag(0f)
        assertEquals(2f, c.snapshot().targetPosition, 0f)
        c.onVisualFrame(2f, 0f)
        c.beginDrag()
        c.dragBy(2.2f)
        c.endDrag(0f)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun dominantRecentReleaseVelocityReversesWithoutWrongWayJump() {
        val c = GalleryTrackController(10, initialIndex = 3)
        c.beginDrag()
        c.dragBy(.8f)
        c.dragBy(-.6f)
        c.endDrag(-8f)
        assertEquals(2f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun cancelDragReturnsToTheGestureAnchor() {
        val c = GalleryTrackController(6, initialIndex = 2)
        c.beginDrag()
        c.dragBy(.8f)
        c.cancelDrag()
        assertFalse(c.snapshot().isDragging)
        assertEquals(2f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun selectedIdSurvivesReorderingAndIndexIsClampedWhenRemoved() {
        val c = GalleryTrackController(3)
        c.syncGameIds(listOf(10L, 20L, 30L))
        c.jumpTo(2)
        c.onVisualFrame(2f, 0f)
        c.syncGameIds(listOf(30L, 10L, 20L))
        assertEquals(0, c.snapshot().activeGameIndex)
        c.syncGameIds(listOf(10L))
        assertEquals(0, c.snapshot().activeGameIndex)
        assertEquals(0f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun fiveAndMoreGameTracksCrossBothSeamsWithoutEdgeResistance() {
        // r5: only five or more games are circular (2–4 are bounded, see smallGalleriesStopAtBothEnds).
        for (count in listOf(5, 6, 9)) {
            val forward = GalleryTrackController(count, initialIndex = count - 1)
            forward.stepBy(1)
            assertEquals(count.toFloat(), forward.snapshot().targetPosition, 0f)
            forward.onVisualFrame(count - .5f, 0f)
            assertEquals(0, forward.snapshot().activeGameIndex)

            val back = GalleryTrackController(count, initialIndex = 0)
            back.stepBy(-1)
            assertEquals(-1f, back.snapshot().targetPosition, 0f)
            back.onVisualFrame(-.51f, 0f)
            assertEquals(count - 1, back.snapshot().activeGameIndex)
        }
    }

    @Test
    fun pagerChoosesTheNearestEquivalentVirtualPageAfterMultipleRevolutions() {
        val c = GalleryTrackController(5)

        c.onVisualFrame(12.1f, 0f)
        c.jumpTo(1)
        assertEquals(11f, c.snapshot().targetPosition, 0f)

        c.onVisualFrame(.2f, 0f)
        c.jumpTo(4)
        assertEquals(-1f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun selectedGameIdKeepsItsNearestVirtualPageWhenTheListReordersOrRemovesIt() {
        val c = GalleryTrackController(6)
        c.syncGameIds(listOf(10L, 20L, 30L, 40L, 50L, 60L))
        c.onVisualFrame(8f, 0f)
        assertEquals(2, c.snapshot().activeGameIndex)

        c.syncGameIds(listOf(30L, 10L, 20L, 40L, 50L, 60L))
        assertEquals(0, c.snapshot().activeGameIndex)
        assertEquals(6f, c.snapshot().visualPosition, 0f)

        c.syncGameIds(listOf(10L, 20L, 30L, 40L, 50L))
        assertEquals(2, c.snapshot().activeGameIndex)
        assertEquals(7f, c.snapshot().visualPosition, 0f)

        // r5: shrinking below five games moves to the bounded track, where the position is the index.
        c.syncGameIds(listOf(20L, 30L, 40L))
        assertEquals(1, c.snapshot().activeGameIndex)
        assertEquals(1f, c.snapshot().visualPosition, 0f)
        c.syncGameIds(listOf(10L, 20L, 30L, 40L, 50L, 60L))
        assertEquals("growing back keeps the game where it is", 2, c.snapshot().activeGameIndex)
        assertEquals(2f, c.snapshot().visualPosition, 0f)
    }

    @Test
    fun smallGalleriesStopAtBothEnds() {
        // r5 (「不足五个封面的时候切换会很奇怪，会无中生有」): 2–4 games never wrap, so no game is shown twice.
        for (count in 2..4) {
            val forward = GalleryTrackController(count, initialIndex = count - 1)
            forward.stepBy(1)
            assertEquals("$count: no step past the last game", count - 1f, forward.snapshot().targetPosition, 0f)
            forward.jumpTo(0)
            assertEquals("$count: the pager jumps to the game itself", 0f, forward.snapshot().targetPosition, 0f)

            val back = GalleryTrackController(count, initialIndex = 0)
            back.stepBy(-1)
            assertEquals("$count: no step before the first game", 0f, back.snapshot().targetPosition, 0f)
            back.onVisualFrame(-.51f, 0f)
            assertEquals("$count: an overscrolled start keeps the first game active", 0, back.snapshot().activeGameIndex)
            back.onVisualFrame(count - .49f, 0f)
            assertEquals(count - 1, back.snapshot().activeGameIndex)
        }
    }

    @Test
    fun smallGalleryDragPastAnEndIsRubberBandedAndSpringsBack() {
        for (count in 2..4) {
            val start = GalleryTrackController(count)
            start.beginDrag()
            start.dragBy(-1f)
            val pulled = start.snapshot().visualPosition
            assertTrue("$count: a whole card past the start moves less than a third of a card ($pulled)", pulled < 0f && pulled > -.34f)
            assertEquals(GalleryTrackBounds.rubberBanded(-1f, count), pulled, .0001f)
            start.endDrag(-5f)
            assertEquals("$count: even a fling past the start springs back to the first game", 0f, start.snapshot().targetPosition, 0f)

            val end = GalleryTrackController(count, initialIndex = count - 1)
            end.beginDrag()
            end.dragBy(.5f)
            assertTrue(end.snapshot().visualPosition > count - 1f && end.snapshot().visualPosition < count - .75f)
            end.endDrag(5f)
            assertEquals(count - 1f, end.snapshot().targetPosition, 0f)

            // Inside the bounds a drag still tracks the finger 1:1 and commits one card.
            val inside = GalleryTrackController(count)
            inside.beginDrag()
            inside.dragBy(.6f)
            assertEquals(.6f, inside.snapshot().visualPosition, .0001f)
            inside.endDrag(.1f)
            assertEquals(1f, inside.snapshot().targetPosition, 0f)
        }
    }

    @Test
    fun aDragCaughtMidSpringBackContinuesWithoutAJump() {
        val c = GalleryTrackController(3)
        c.beginDrag()
        c.dragBy(-.8f)
        val banded = c.snapshot().visualPosition
        c.endDrag(0f)
        assertEquals(0f, c.snapshot().targetPosition, 0f)
        // The spring has not moved yet when the finger lands again: nothing jumps.
        c.onVisualFrame(banded, 0f)
        c.beginDrag()
        c.dragBy(0f)
        assertEquals(banded, c.snapshot().visualPosition, .0001f)
    }

    @Test
    fun oneGameDoesNotSwipeAndFiveOrMoreStayCircular() {
        val single = GalleryTrackController(1)
        single.beginDrag()
        single.dragBy(-.7f)
        assertEquals(0f, single.snapshot().visualPosition, 0f)
        assertFalse(single.snapshot().isDragging)

        assertFalse(GalleryTrackBounds.isBounded(1))
        assertTrue((2..4).all(GalleryTrackBounds::isBounded))
        assertFalse(GalleryTrackBounds.isBounded(5))
        val five = GalleryTrackController(5)
        five.beginDrag()
        five.dragBy(-1f)
        assertEquals("five games keep the unresisted circular drag", -1f, five.snapshot().visualPosition, 0f)
    }

    @Test
    fun initialIndexClampsToFiniteBounds() {
        assertEquals(5f, GalleryTrackController(6, initialIndex = 8).snapshot().visualPosition, 0f)
        assertEquals(0f, GalleryTrackController(6, initialIndex = -1).snapshot().visualPosition, 0f)
        assertEquals(-1, GalleryTrackController(0, initialIndex = 3).snapshot().activeGameIndex)
    }

    @Test
    fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GalleryTrackController(-1) }
        assertThrows(IllegalArgumentException::class.java) { GalleryTrackController(6).stepBy(2) }
        assertThrows(IllegalArgumentException::class.java) { GalleryTrackController(6).stepBy(0) }
    }
}
