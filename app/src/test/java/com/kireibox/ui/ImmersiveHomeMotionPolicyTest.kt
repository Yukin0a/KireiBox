package com.kireibox.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveHomeMotionPolicyTest {
    private val layout =
        resolveImmersiveLayout(
            HomeOrientation.LANDSCAPE,
            1672f,
            941f,
            ImmersiveSafeBounds(0f, 0f, 1672f, 941f),
        )

    private val portraitLayout =
        resolveImmersiveLayout(
            HomeOrientation.PORTRAIT,
            1080f,
            2400f,
            ImmersiveSafeBounds(0f, 0f, 1080f, 2400f),
        )

    @Test
    fun circularTrackKeepsDistinctCardsAcrossDampedFractionalPositions() {
        val ids = (10L..70L step 10).toList()
        val atQuarter = resolveImmersiveFrames(.25f, layout, ids, HomeOrientation.LANDSCAPE)
        val atHalf = resolveImmersiveFrames(.5f, layout, ids, HomeOrientation.LANDSCAPE)

        assertTrue(atQuarter.size >= 3)
        assertEquals(atQuarter.size, atQuarter.map { it.gameId }.distinct().size)
        assertTrue(atQuarter.map { it.frame.rect.x }.toSet() != atHalf.map { it.frame.rect.x }.toSet())
    }

    @Test
    fun idleFiveGamesOccupyCircularSlotsWithoutDuplicates() {
        val frames = resolveImmersiveFrames(2f, layout, listOf(10L, 20L, 30L, 40L, 50L), HomeOrientation.LANDSCAPE)

        assertEquals(5, frames.size)
        assertEquals(5, frames.map { it.gameId }.distinct().size)
        assertEquals(30L, frames.single { it.slot == ImmersiveSlot.CENTER }.gameId)
    }

    @Test
    fun advanceMovesTheCircularTrackContinuously() {
        val ids = (1L..7L).toList()
        val samples =
            listOf(2f, 2.25f, 2.5f, 2.75f, 3f).map { position ->
                resolveImmersiveFrames(position, layout, ids, HomeOrientation.LANDSCAPE).associateBy { it.gameId }
            }
        val centerId = 3L
        val xs = samples.map { it.getValue(centerId).frame.rect.x }

        assertTrue(xs.zipWithNext().all { (a, b) -> b < a })
        assertEquals(layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.x, xs.first(), .001f)
        assertEquals(layout.cardSlots.getValue(ImmersiveSlot.NEAR_LEFT).rect.x, xs.last(), .001f)
    }

    @Test
    fun circularTrackWrapsCardsAcrossEitherListEdge() {
        val ids = listOf(10L, 20L, 30L, 40L, 50L)
        val first = resolveImmersiveFrames(0f, layout, ids, HomeOrientation.LANDSCAPE)
        val last = resolveImmersiveFrames(4f, layout, ids, HomeOrientation.LANDSCAPE)

        assertTrue(first.any { it.gameId == 50L })
        assertTrue(last.any { it.gameId == 10L })
        assertEquals(5, first.size)
        assertEquals(5, last.size)
    }

    @Test
    fun backwardSeamKeepsBothAdjacentGamesWithoutAnEdgeBounce() {
        // r5: the seam exists only on the circular track of five or more games (2–4 are bounded,
        // see ImmersiveSmallGalleryTest).
        val ids = listOf(10L, 20L, 30L, 40L, 50L)
        val frames = resolveImmersiveFrames(-.25f, layout, ids, HomeOrientation.LANDSCAPE)
        val centerAtRest = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.x
        val centerDuringBackwardMotion = frames.single { it.slot == ImmersiveSlot.CENTER }.frame.rect.x

        assertTrue(frames.any { it.gameId == 50L })
        assertTrue(frames.any { it.gameId == 10L })
        assertEquals(10L, frames.single { it.slot == ImmersiveSlot.CENTER }.gameId)
        assertTrue(centerDuringBackwardMotion > centerAtRest)
    }

    @Test
    fun circularFramesKeepBothSeamGamesForFiveAndMoreItemLibraries() {
        // r5: 2–4 games no longer have a seam (bounded track, ImmersiveSmallGalleryTest).
        for (count in listOf(5, 6, 7)) {
            val ids = (1L..count.toLong()).toList()
            val forward =
                resolveImmersiveFrames(
                    count - .25f,
                    layout,
                    ids,
                    HomeOrientation.LANDSCAPE,
                )
            val back =
                resolveImmersiveFrames(
                    -.25f,
                    layout,
                    ids,
                    HomeOrientation.LANDSCAPE,
                )

            assertTrue(forward.any { it.gameId == count.toLong() })
            assertTrue(forward.any { it.gameId == 1L })
            assertTrue(back.any { it.gameId == 1L })
            assertTrue(back.any { it.gameId == count.toLong() })
        }
    }

    @Test
    fun virtualSeamKeepsRepeatedGameIdsAsDistinctCardsAndMovesOnlyAcrossAdjacentSlots() {
        val slotOrder =
            listOf(
                ImmersiveSlot.EXIT_LEFT,
                ImmersiveSlot.FAR_LEFT,
                ImmersiveSlot.NEAR_LEFT,
                ImmersiveSlot.CENTER,
                ImmersiveSlot.NEAR_RIGHT,
                ImmersiveSlot.FAR_RIGHT,
                ImmersiveSlot.ENTER_RIGHT,
            )
        val slotIndexes = slotOrder.withIndex().associate { it.value to it.index }

        fun assertContinuous(
            samples: List<Float>,
            ids: List<Long>
        ) {
            val frames =
                samples.map { position ->
                    resolveImmersiveFrames(position, layout, ids, HomeOrientation.LANDSCAPE)
                        .also { rendered ->
                            assertEquals(
                                "virtual card identities must be unique at $position",
                                rendered.size,
                                rendered.map { it.virtualIndex }.distinct().size,
                            )
                        }
                        .associateBy { it.virtualIndex }
                }
            frames.zipWithNext().forEach { (from, to) ->
                from.keys.intersect(to.keys).forEach { virtualIndex ->
                    val fromSlot = slotIndexes.getValue(from.getValue(virtualIndex).slot)
                    val toSlot = slotIndexes.getValue(to.getValue(virtualIndex).slot)
                    assertTrue(
                        "virtual card $virtualIndex jumped from ${slotOrder[fromSlot]} to ${slotOrder[toSlot]}",
                        kotlin.math.abs(toSlot - fromSlot) <= 1,
                    )
                }
            }
        }

        // r5: only five or more games are circular; smaller galleries never repeat a game
        // (ImmersiveSmallGalleryTest pins the opposite for 2–4).
        for (count in listOf(5, 6)) {
            val ids = (1L..count.toLong()).toList()
            for (round in 0 until 3) {
                val seam = (round + 1) * count.toFloat()
                assertContinuous(
                    listOf(seam - .75f, seam - .5f, seam - .25f, seam, seam + .25f),
                    ids,
                )
                assertContinuous(
                    listOf(seam + .75f, seam + .5f, seam + .25f, seam, seam - .25f),
                    ids,
                )
            }

            if (count == 5) {
                // Five games in landscape (five slots) still pass one game out on the left while it
                // comes back on the right; that behaviour is deliberately unchanged in r5.
                val transition = resolveImmersiveFrames(.5f, layout, ids, HomeOrientation.LANDSCAPE)
                assertTrue(
                    "count=$count must retain repeated virtual instances during the seam",
                    transition.groupingBy { it.gameId }.eachCount().values.any { it > 1 },
                )
            }
        }
    }

    @Test
    fun circularFramesMapMultipleRevolutionsWithFloorMod() {
        val ids = listOf(10L, 20L, 30L, 40L, 50L)

        val beforeSeam = resolveImmersiveFrames(10.25f, layout, ids, HomeOrientation.LANDSCAPE)
        val afterSeam = resolveImmersiveFrames(10.75f, layout, ids, HomeOrientation.LANDSCAPE)

        assertEquals(10L, beforeSeam.single { it.slot == ImmersiveSlot.CENTER }.gameId)
        assertEquals(20L, afterSeam.single { it.slot == ImmersiveSlot.CENTER }.gameId)
    }

    @Test
    fun oneGameFrameRemainsAStaticCenterAtAnyVirtualPosition() {
        val frames =
            resolveImmersiveFrames(
                -123.75f,
                layout,
                listOf(99L),
                HomeOrientation.LANDSCAPE,
            )

        assertEquals(1, frames.size)
        assertEquals(99L, frames.single().gameId)
        assertEquals(ImmersiveSlot.CENTER, frames.single().slot)
    }

    @Test
    fun backdropRetainsOneIdleCoverAndOnlyAdjacentTransitionCovers() {
        assertEquals(
            ImmersiveBackdropRetention(listOf(2), transitioning = false),
            resolveImmersiveBackdropRetention(2f, 5),
        )
        assertEquals(
            ImmersiveBackdropRetention(listOf(2, 3), transitioning = true),
            resolveImmersiveBackdropRetention(2.25f, 5),
        )
        assertEquals(
            ImmersiveBackdropRetention(listOf(4, 0), transitioning = true),
            resolveImmersiveBackdropRetention(4.25f, 5),
        )
        assertEquals(
            ImmersiveBackdropRetention(listOf(4, 0), transitioning = true),
            resolveImmersiveBackdropRetention(-.25f, 5),
        )
    }

    @Test
    fun visibleCardsAreOpaqueAndShowDepthWithAScrimNotWithLayerAlpha() {
        // r3 review: side cards at layer alpha .48 read as glass (the next card's art showed
        // through the current one mid-swipe). Visible cards are opaque; depth is a scrim.
        listOf(layout to HomeOrientation.LANDSCAPE, portraitLayout to HomeOrientation.PORTRAIT).forEach { (resolved, orientation) ->
            val center = resolved.cardSlots.getValue(ImmersiveSlot.CENTER)
            assertEquals(1f, center.alpha, 0f)
            assertEquals("the focused card is not dimmed", 0f, center.depth, 0f)
            listOf(ImmersiveSlot.NEAR_LEFT, ImmersiveSlot.NEAR_RIGHT).forEach { slot ->
                val side = resolved.cardSlots.getValue(slot)
                assertEquals("$orientation $slot is opaque", 1f, side.alpha, 0f)
                assertTrue("$orientation $slot sits back", side.depth > 0f)
            }
            // Mid-swipe, the outgoing and the incoming card (the two around the center) stay opaque.
            listOf(.25f, .5f, .75f).forEach { position ->
                val crossing = resolveImmersiveFrames(position, resolved, listOf(10L, 20L, 30L, 40L, 50L), orientation).filter { it.virtualIndex == 0 || it.virtualIndex == 1 }
                assertEquals(2, crossing.size)
                crossing.forEach { frame -> assertEquals("$orientation @ $position card ${frame.virtualIndex} stays opaque", 1f, frame.frame.alpha, .0001f) }
            }
        }
        assertEquals(1f, ImmersiveCardDepth.depth(.48f), .0001f)
        assertEquals(0f, ImmersiveCardDepth.depth(1f), .0001f)
        assertEquals(0f, ImmersiveCardDepth.presence(0f), 0f)
        assertTrue(UiSourceFiles.file("ImmersiveHomeCards.kt").contains("if (frame.depth > 0f) ImmersiveCardDepthScrim(frame.depth)"))
    }

    @Test
    fun reducedMotionOnlyCrossfadesAtTheCenter() {
        val frames =
            resolveImmersiveFrames(
                .5f,
                layout,
                listOf(10L, 20L, 30L),
                HomeOrientation.LANDSCAPE,
                reducedMotion = true,
            )
        val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER)

        assertEquals(2, frames.size)
        assertTrue(frames.all { it.frame.rect == center.rect })
        assertEquals(.5f, frames[0].frame.alpha, .001f)
        assertEquals(.5f, frames[1].frame.alpha, .001f)
        assertNotEquals(frames[0].gameId, frames[1].gameId)
    }

    @Test
    fun idlePortraitShowsOnlyPreviousCurrentNextCircularCards() {
        val frames =
            resolveImmersiveFrames(
                2f,
                portraitLayout,
                listOf(10L, 20L, 30L, 40L, 50L),
                HomeOrientation.PORTRAIT,
            )

        assertEquals(3, frames.size)
        assertEquals(20L, frames.single { it.slot == ImmersiveSlot.NEAR_LEFT }.gameId)
        assertEquals(30L, frames.single { it.slot == ImmersiveSlot.CENTER }.gameId)
        assertEquals(40L, frames.single { it.slot == ImmersiveSlot.NEAR_RIGHT }.gameId)
    }

    @Test
    fun normalizedPortraitAndLandscapeInputsResolveToTheSameTarget() {
        val inputs =
            listOf(
                GalleryTrackGestureInput(3, .2f, .1f),
                GalleryTrackGestureInput(3, .7f, 1.2f),
                GalleryTrackGestureInput(3, 2.1f, 3.5f),
                GalleryTrackGestureInput(3, -.9f, -2.2f),
                GalleryTrackGestureInput(3, .4f, -6f, recentIntent = -1),
            )
        assertEquals(
            listOf(3, 4, 6, 2, 2),
            inputs.map { GalleryTrackTargetPolicy.resolveTarget(it, itemCount = 8) },
        )
    }

    @Test
    fun inBoundsDragTracksThePointerOneToOne() {
        val raw = .7f
        val visual = GalleryTrackTargetPolicy.resistedDragPosition(0f, raw, itemCount = 6)

        assertEquals(raw, visual, 0f)
    }

    @Test
    fun unboundedDragKeepsItsFullVirtualDistance() {
        val visual = GalleryTrackTargetPolicy.resistedDragPosition(0f, -10_000f, itemCount = 6)

        assertEquals(-10_000f, visual, 0f)
    }

    @Test
    fun releaseMutationCancelsPointerSnapBeforeSettling() =
        runTest {
            val runner = SerializedTrackMutationRunner(this)
            val firstStarted = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()

            runner.launchLatest {
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    order += "pointer-cancelled"
                }
            }
            firstStarted.await()

            runner.replaceAndJoin { order += "release-settle" }

            assertEquals(listOf("pointer-cancelled", "release-settle"), order)
        }

    @Test
    fun disposeCancelsQueuedPredecessorAndSuccessorAndRejectsLaterLaunch() =
        runTest {
            // dispose is a one-shot lifecycle transition: queued work is canceled and later launches
            // return canceled jobs without executing a mutation.
            val runner = SerializedTrackMutationRunner(this)
            var mutationRuns = 0

            val predecessor = runner.launchLatest { mutationRuns += 1 }
            val successor = runner.launchLatest { mutationRuns += 1 }

            runner.dispose()
            runCurrent()

            assertTrue("predecessor must be canceled before it can run", predecessor.isCancelled)
            assertTrue("successor must be canceled before it can run", successor.isCancelled)
            assertEquals(0, mutationRuns)

            val afterDispose = runner.launchLatest { mutationRuns += 1 }
            runCurrent()

            assertTrue("launchLatest after dispose must be rejected", afterDispose.isCancelled)
            assertEquals(0, mutationRuns)
        }

    @Test
    fun releaseAFractionOfAPixelFromRestCountsAsSettled() {
        // A drag released .0005 slot from rest is snapped by the settle effect; it must not count as moving.
        assertFalse(isImmersiveGallerySettling(isDragging = false, targetPosition = 2f, visualPosition = 2.0005f))
        assertFalse(isImmersiveGallerySettling(isDragging = false, targetPosition = 2f, visualPosition = 2f))
        assertTrue(isImmersiveGallerySettling(isDragging = false, targetPosition = 2f, visualPosition = 2.01f))
        assertFalse("a drag is dragging, not settling", isImmersiveGallerySettling(isDragging = true, targetPosition = 2f, visualPosition = 2.5f))
    }

    @Test
    fun settleSpringInheritsTheReleaseVelocityTowardTheTargetOnly() {
        // Forward release keeps its speed, capped by the remaining gap.
        assertEquals(4f, immersiveSettleInitialVelocity(start = 1.6f, target = 3f, runningVelocity = 0f, trackVelocity = 4f), 1e-4f)
        assertEquals(.4f * IMMERSIVE_SETTLE_MAX_VELOCITY_PER_SLOT, immersiveSettleInitialVelocity(2.6f, 3f, 0f, 40f), 1e-4f)
        // A release moving away from the target (spring-back) starts from rest.
        assertEquals(0f, immersiveSettleInitialVelocity(.2f, 0f, 0f, 3f), 0f)
        assertEquals(0f, immersiveSettleInitialVelocity(1f, 1f, 0f, 3f), 0f)
        assertEquals(0f, immersiveSettleInitialVelocity(0f, 1f, 0f, Float.NaN), 0f)
        // A running animation keeps its own velocity.
        assertEquals(-2f, immersiveSettleInitialVelocity(0f, 1f, -2f, 3f), 0f)
    }

    @Test
    fun optionalPortraitShuffleLeavesRestingSlotsUntouchedAndSeparatesPassingCards() {
        val ids = (10L..70L step 10).toList()
        val rest = resolveImmersiveFrames(3f, portraitLayout, ids, HomeOrientation.PORTRAIT)
        assertEquals(portraitLayout.cardSlots.getValue(ImmersiveSlot.CENTER).rect, rest.single { it.virtualIndex == 3 }.frame.rect)
        assertEquals(portraitLayout.cardSlots.getValue(ImmersiveSlot.NEAR_RIGHT).rect, rest.single { it.virtualIndex == 4 }.frame.rect)

        // Default soft switch: a straight interpolation with no swing.
        val soft = resolveImmersiveFrames(3.5f, portraitLayout, ids, HomeOrientation.PORTRAIT)
        val softLeaving = soft.single { it.virtualIndex == 3 }.frame
        val softArriving = soft.single { it.virtualIndex == 4 }.frame
        assertTrue((softArriving.rect.x + softArriving.rect.w / 2f) - (softLeaving.rect.x + softLeaving.rect.w / 2f) < portraitLayout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.w * .4f)
        assertEquals(rest, rest.withPortraitShuffle(3f, portraitLayout))

        val half = soft.withPortraitShuffle(3.5f, portraitLayout)
        val leaving = half.single { it.virtualIndex == 3 }.frame
        val arriving = half.single { it.virtualIndex == 4 }.frame
        val center = portraitLayout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
        val leavingCx = leaving.rect.x + leaving.rect.w / 2f
        val arrivingCx = arriving.rect.x + arriving.rect.w / 2f
        // Where the stacking order swaps the two covers sit on either side of the centre, apart.
        assertTrue(leavingCx < center.x + center.w / 2f)
        assertTrue(arrivingCx > center.x + center.w / 2f)
        assertTrue(arrivingCx - leavingCx > center.w * .7f)
        assertTrue(leaving.rotationY < 0f && arriving.rotationY > 0f)
    }

    @Test
    fun portraitTitleFadesWithTheCardFocus() {
        assertEquals(1f, immersiveTitleReveal(1f), 0f)
        assertEquals(.5f, immersiveTitleReveal(.5f), 1e-4f)
        assertEquals(0f, immersiveTitleReveal(0f), 0f)
        assertTrue(immersiveTitleReveal(.75f) > .75f)
        assertTrue(immersiveTitleReveal(.25f) < .25f)
    }
}
