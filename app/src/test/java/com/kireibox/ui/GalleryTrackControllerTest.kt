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
    fun twoGameTrackWrapsBetweenBothVirtualSlots() {
        val c = GalleryTrackController(2)
        c.onVisualFrame(1.5f, 0f)
        assertEquals(0, c.snapshot().activeGameIndex)
        c.jumpTo(0)
        assertEquals(2f, c.snapshot().targetPosition, 0f)
        c.stepBy(1)
        assertEquals(3f, c.snapshot().targetPosition, 0f)
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
    fun forwardStepAtLastGameCrossesToTheFirstVirtualPage() {
        val c = GalleryTrackController(3, initialIndex = 2)

        c.stepBy(1)
        assertEquals(3f, c.snapshot().targetPosition, 0f)

        c.onVisualFrame(2.5f, 0f)
        assertEquals(0, c.snapshot().activeGameIndex)
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
        val positive = listOf(10f, 16f, 22f).map { velocity ->
            GalleryTrackTargetPolicy.resolveTarget(
                GalleryTrackGestureInput(4, dragDistance = .1f, velocity = velocity),
                itemCount = 10,
            )
        }
        val negative = listOf(-10f, -16f, -22f).map { velocity ->
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
        val resisted = GalleryTrackTargetPolicy.resistedDragPosition(
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
        assertEquals(-1f, c.snapshot().targetPosition, 0f)
    }

    @Test
    fun longEdgeDragKeepsItsVirtualDistance() {
        val resisted = GalleryTrackTargetPolicy.resistedDragPosition(
            anchorPosition = 0f,
            rawDragDistance = -10_000f,
            itemCount = 6,
        )

        assertEquals(-10_000f, resisted, 0f)
    }

    @Test
    fun trailingEdgeDragKeepsItsVirtualDistanceAndWrapsForward() {
        val resisted = GalleryTrackTargetPolicy.resistedDragPosition(
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
        assertEquals(6f, c.snapshot().targetPosition, 0f)
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
    fun twoThreeAndFiveGameTracksCrossBothSeamsWithoutEdgeResistance() {
        for (count in listOf(2, 3, 5)) {
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
        val c = GalleryTrackController(3)
        c.syncGameIds(listOf(10L, 20L, 30L))
        c.onVisualFrame(2f, 0f)

        c.syncGameIds(listOf(30L, 10L, 20L))
        assertEquals(0, c.snapshot().activeGameIndex)
        assertEquals(3f, c.snapshot().visualPosition, 0f)

        c.syncGameIds(listOf(10L, 20L))
        assertEquals(0, c.snapshot().activeGameIndex)
        assertEquals(4f, c.snapshot().visualPosition, 0f)
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
