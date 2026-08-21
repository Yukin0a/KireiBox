package com.kireibox.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveHomeMotionPolicyTest {
    private val layout = resolveImmersiveLayout(
        HomeOrientation.LANDSCAPE,
        1672f,
        941f,
        ImmersiveSafeBounds(0f, 0f, 1672f, 941f),
    )

    private val portraitLayout = resolveImmersiveLayout(
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
        val samples = listOf(2f, 2.25f, 2.5f, 2.75f, 3f).map { position ->
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
        val ids = listOf(10L, 20L, 30L)
        val frames = resolveImmersiveFrames(-.25f, layout, ids, HomeOrientation.LANDSCAPE)
        val centerAtRest = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.x
        val centerDuringBackwardMotion = frames.single { it.slot == ImmersiveSlot.CENTER }.frame.rect.x

        assertTrue(frames.any { it.gameId == 30L })
        assertTrue(frames.any { it.gameId == 10L })
        assertEquals(10L, frames.single { it.slot == ImmersiveSlot.CENTER }.gameId)
        assertTrue(centerDuringBackwardMotion > centerAtRest)
    }

    @Test
    fun circularFramesKeepBothSeamGamesForTwoThreeAndFiveItemLibraries() {
        for (count in listOf(2, 3, 5)) {
            val ids = (1L..count.toLong()).toList()
            val forward = resolveImmersiveFrames(
                count - .25f,
                layout,
                ids,
                HomeOrientation.LANDSCAPE,
            )
            val back = resolveImmersiveFrames(
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
        val slotOrder = listOf(
            ImmersiveSlot.EXIT_LEFT,
            ImmersiveSlot.FAR_LEFT,
            ImmersiveSlot.NEAR_LEFT,
            ImmersiveSlot.CENTER,
            ImmersiveSlot.NEAR_RIGHT,
            ImmersiveSlot.FAR_RIGHT,
            ImmersiveSlot.ENTER_RIGHT,
        )
        val slotIndexes = slotOrder.withIndex().associate { it.value to it.index }

        fun assertContinuous(samples: List<Float>, ids: List<Long>) {
            val frames = samples.map { position ->
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

        for (count in listOf(2, 3, 5)) {
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

            val transition = resolveImmersiveFrames(.5f, layout, ids, HomeOrientation.LANDSCAPE)
            assertTrue(
                "count=$count must retain repeated virtual instances during the seam",
                transition.groupingBy { it.gameId }.eachCount().values.any { it > 1 },
            )
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
        val frames = resolveImmersiveFrames(
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
    fun reducedMotionOnlyCrossfadesAtTheCenter() {
        val frames = resolveImmersiveFrames(
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
        val frames = resolveImmersiveFrames(
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
        val inputs = listOf(
            GalleryTrackGestureInput(3, .2f, .1f),
            GalleryTrackGestureInput(3, .7f, 1.2f),
            GalleryTrackGestureInput(3, 2.1f, 3.5f),
            GalleryTrackGestureInput(3, -.9f, -2.2f),
            GalleryTrackGestureInput(3, .4f, -6f, recentIntent = -1),
        )
        assertEquals(
            listOf(3, 4, 4, 2, 2),
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
    fun releaseMutationCancelsPointerSnapBeforeSettling() = runTest {
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
    fun disposeCancelsQueuedPredecessorAndSuccessorAndRejectsLaterLaunch() = runTest {
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
}
