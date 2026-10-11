package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * r5 (user request 「沉浸式不足五个封面的时候切换会很奇怪，会无中生有」): galleries of one to four games.
 * A circular track with fewer games than visible slots had to show a game twice, so switching made a
 * copy of a card fade or pop into a side slot. Two to four games are now a bounded track; these pin
 * that every visible card is a real, distinct game and moves continuously, for n = 1 … 5.
 */
class ImmersiveSmallGalleryTest {
    private val landscape = resolveImmersiveLayout(HomeOrientation.LANDSCAPE, 1672f, 941f, ImmersiveSafeBounds(0f, 0f, 1672f, 941f))
    private val portrait = resolveImmersiveLayout(HomeOrientation.PORTRAIT, 1080f, 2400f, ImmersiveSafeBounds(0f, 0f, 1080f, 2400f))
    private val layouts = listOf(portrait to HomeOrientation.PORTRAIT, landscape to HomeOrientation.LANDSCAPE)
    private val slotOrder =
        listOf(
            ImmersiveSlot.EXIT_LEFT,
            ImmersiveSlot.FAR_LEFT,
            ImmersiveSlot.NEAR_LEFT,
            ImmersiveSlot.CENTER,
            ImmersiveSlot.NEAR_RIGHT,
            ImmersiveSlot.FAR_RIGHT,
            ImmersiveSlot.ENTER_RIGHT,
        )

    private fun ids(count: Int) = (1L..count.toLong()).toList()

    /** Slot → game id of the resting frames. */
    private fun restingSlots(
        index: Int,
        count: Int,
        layout: ImmersiveHomeLayout,
        orientation: HomeOrientation,
    ): Map<ImmersiveSlot, Long> = resolveImmersiveFrames(index.toFloat(), layout, ids(count), orientation).associate { it.slot to it.gameId }

    @Test
    fun oneGameIsASingleCentredCardAtEveryPosition() {
        layouts.forEach { (layout, orientation) ->
            listOf(0f, -.4f, .35f, 3.7f).forEach { position ->
                val frames = resolveImmersiveFrames(position, layout, ids(1), orientation)
                assertEquals(1, frames.size)
                assertEquals(ImmersiveSlot.CENTER, frames.single().slot)
                assertEquals(layout.cardSlots.getValue(ImmersiveSlot.CENTER), frames.single().frame)
            }
        }
    }

    @Test
    fun restingSideSlotsHoldOnlyRealNeighbours() {
        // Behaviour table (spec §10 r5): portrait shows one neighbour per side, landscape up to two.
        assertEquals(mapOf(ImmersiveSlot.CENTER to 1L, ImmersiveSlot.NEAR_RIGHT to 2L), restingSlots(0, 2, portrait, HomeOrientation.PORTRAIT))
        assertEquals(mapOf(ImmersiveSlot.NEAR_LEFT to 1L, ImmersiveSlot.CENTER to 2L), restingSlots(1, 2, portrait, HomeOrientation.PORTRAIT))
        assertEquals(mapOf(ImmersiveSlot.NEAR_LEFT to 1L, ImmersiveSlot.CENTER to 2L, ImmersiveSlot.NEAR_RIGHT to 3L), restingSlots(1, 3, portrait, HomeOrientation.PORTRAIT))
        assertEquals(mapOf(ImmersiveSlot.CENTER to 1L, ImmersiveSlot.NEAR_RIGHT to 2L, ImmersiveSlot.FAR_RIGHT to 3L), restingSlots(0, 3, landscape, HomeOrientation.LANDSCAPE))
        assertEquals(
            mapOf(ImmersiveSlot.NEAR_LEFT to 1L, ImmersiveSlot.CENTER to 2L, ImmersiveSlot.NEAR_RIGHT to 3L, ImmersiveSlot.FAR_RIGHT to 4L),
            restingSlots(1, 4, landscape, HomeOrientation.LANDSCAPE),
        )
        assertEquals(
            mapOf(ImmersiveSlot.FAR_LEFT to 1L, ImmersiveSlot.NEAR_LEFT to 2L, ImmersiveSlot.CENTER to 3L, ImmersiveSlot.NEAR_RIGHT to 4L),
            restingSlots(2, 4, landscape, HomeOrientation.LANDSCAPE),
        )
        // Five games stay circular: the first game has the last one as its left neighbour.
        assertEquals(5L, restingSlots(0, 5, portrait, HomeOrientation.PORTRAIT)[ImmersiveSlot.NEAR_LEFT])
    }

    @Test
    fun noGameIsEverShownTwiceAndEveryCardMovesContinuously() {
        layouts.forEach { (layout, orientation) ->
            for (count in 2..4) {
                // From a rubber-banded pull before the first game to one past the last, in small steps.
                val positions = (-8..(count - 1) * 20 + 8).map { it / 20f }
                val samples = positions.map { position -> resolveImmersiveFrames(position, layout, ids(count), orientation) }
                samples.forEachIndexed { step, frames -> assertRealDistinctCards("$orientation n=$count @ ${positions[step]}", frames, positions[step], count) }
                samples.zipWithNext().forEach { (from, to) -> assertContinuous("$orientation n=$count", from, to, layout) }
            }
        }
    }

    private fun assertRealDistinctCards(
        label: String,
        frames: List<ImmersiveGameFrame>,
        position: Float,
        count: Int,
    ) {
        assertEquals("$label: distinct games", frames.size, frames.map { it.gameId }.distinct().size)
        assertTrue("$label: only real games", frames.all { it.gameId in ids(count) && it.virtualIndex == (it.gameId - 1).toInt() })
        assertTrue("$label: the centre is never empty", frames.any { abs(it.virtualIndex - position) <= .5f })
    }

    private fun assertContinuous(
        label: String,
        from: List<ImmersiveGameFrame>,
        to: List<ImmersiveGameFrame>,
        layout: ImmersiveHomeLayout,
    ) {
        val before = from.associateBy { it.gameId }
        val halfCard = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.w * .5f
        to.forEach { card ->
            val previous = before[card.gameId] ?: return@forEach
            val jump = abs(slotOrder.indexOf(card.slot) - slotOrder.indexOf(previous.slot))
            assertTrue("$label: game ${card.gameId} jumped ${previous.slot} → ${card.slot}", jump <= 1)
            assertTrue("$label: game ${card.gameId} moves smoothly", abs(card.frame.rect.x - previous.frame.rect.x) < halfCard)
        }
    }

    @Test
    fun aCardOnlyAppearsFromBehindItsNeighbour() {
        // The card that becomes visible mid-swipe enters from an invisible rear slot (alpha 0 in
        // portrait) or the off-screen edge slot — never as a copy of a card leaving the other side.
        layouts.forEach { (layout, orientation) ->
            for (count in 3..4) {
                val atRest = resolveImmersiveFrames(0f, layout, ids(count), orientation).map { it.gameId }.toSet()
                val moving = resolveImmersiveFrames(.3f, layout, ids(count), orientation)
                moving.filter { it.gameId !in atRest }.forEach { entering ->
                    assertTrue("$orientation n=$count: game ${entering.gameId} enters on the right", slotOrder.indexOf(entering.slot) > slotOrder.indexOf(ImmersiveSlot.CENTER))
                    assertTrue("$orientation n=$count: game ${entering.gameId} fades in", entering.frame.alpha < 1f)
                }
            }
        }
    }

    @Test
    fun pullingPastAnEndShowsNoCardBeyondIt() {
        layouts.forEach { (layout, orientation) ->
            val start = resolveImmersiveFrames(-.25f, layout, ids(3), orientation)
            val centerX = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect.x
            assertEquals("$orientation: the first game leads the pull", 1L, start.minBy { it.virtualIndex }.gameId)
            assertTrue("$orientation: it moves right of the centre", start.first { it.gameId == 1L }.frame.rect.x > centerX)
            assertFalse("$orientation: the last game does not wrap in on the left", start.any { it.gameId == 3L && it.frame.rect.x < centerX })

            val end = resolveImmersiveFrames(2.25f, layout, ids(3), orientation)
            assertTrue("$orientation: the last game moves left of the centre", end.first { it.gameId == 3L }.frame.rect.x < centerX)
            assertFalse("$orientation: the first game does not wrap in on the right", end.any { it.gameId == 1L && it.frame.rect.x > centerX })
        }
    }

    @Test
    fun reducedMotionCrossfadesBetweenRealGamesOnly() {
        val center = landscape.cardSlots.getValue(ImmersiveSlot.CENTER)
        val crossing = resolveImmersiveFrames(1.5f, landscape, ids(3), HomeOrientation.LANDSCAPE, reducedMotion = true)
        assertEquals(listOf(2L, 3L), crossing.map { it.gameId })
        assertTrue(crossing.all { it.frame.rect == center.rect })
        val pulled = resolveImmersiveFrames(-.3f, landscape, ids(3), HomeOrientation.LANDSCAPE, reducedMotion = true)
        assertEquals("a pull past the start rests on the first game", 1L, pulled.single { it.slot == ImmersiveSlot.CENTER }.gameId)
        assertFalse(pulled.any { it.gameId == 3L && it.slot == ImmersiveSlot.CENTER })
    }

    @Test
    fun rubberBandResistsPastEitherEndAndInvertsExactly() {
        assertEquals(.5f, GalleryTrackBounds.rubberBanded(.5f, 3), 0f)
        val pulled = GalleryTrackBounds.rubberBanded(-1f, 3)
        assertTrue(pulled < 0f && pulled > -.34f)
        assertTrue("further pulls give less and less", GalleryTrackBounds.rubberBanded(-2f, 3) - pulled > -.15f)
        assertTrue(GalleryTrackBounds.rubberBanded(-50f, 3) > -GalleryTrackBounds.RUBBER_BAND_SPAN)
        assertEquals(2f + -pulled, GalleryTrackBounds.rubberBanded(3f, 3), .0001f)
        listOf(-1.7f, -.3f, 0f, 1.2f, 2f, 2.4f, 4f).forEach { raw ->
            assertEquals(raw, GalleryTrackBounds.unbanded(GalleryTrackBounds.rubberBanded(raw, 3), 3), .001f)
        }
        assertEquals("circular tracks are never banded", -3f, GalleryTrackBounds.rubberBanded(-3f, 5), 0f)
        assertEquals(-3f, GalleryTrackBounds.unbanded(-3f, 5), 0f)
    }

    @Test
    fun backdropOfAPulledEndKeepsItsOwnCover() {
        assertEquals(0f, GalleryTrackBounds.backdropPosition(-.3f, 3), 0f)
        assertEquals(2f, GalleryTrackBounds.backdropPosition(2.2f, 3), 0f)
        assertEquals(1.4f, GalleryTrackBounds.backdropPosition(1.4f, 3), 0f)
        assertEquals("circular tracks project across the seam as before", -.3f, GalleryTrackBounds.backdropPosition(-.3f, 5), 0f)
        assertEquals(ImmersiveBackdropRetention(listOf(0), transitioning = false), resolveImmersiveBackdropRetention(GalleryTrackBounds.backdropPosition(-.3f, 3), 3))
    }

    @Test
    fun neighbourBackdropsArePreparedOnlyForRealNeighbours() {
        assertEquals(emptyList<Int>(), resolveImmersiveBackdropPrefetch(0, 1))
        assertEquals(listOf(1), resolveImmersiveBackdropPrefetch(0, 2))
        assertEquals(listOf(0), resolveImmersiveBackdropPrefetch(1, 2))
        assertEquals(listOf(1), resolveImmersiveBackdropPrefetch(0, 3))
        assertEquals(listOf(2, 0), resolveImmersiveBackdropPrefetch(1, 3))
        assertEquals(listOf(2), resolveImmersiveBackdropPrefetch(3, 4))
        assertEquals("five games wrap", listOf(1, 4), resolveImmersiveBackdropPrefetch(0, 5))
        assertEquals(emptyList<Int>(), resolveImmersiveBackdropPrefetch(-1, 5))
        assertNull(resolveImmersiveFrames(0f, landscape, emptyList(), HomeOrientation.LANDSCAPE).firstOrNull())
    }

    @Test
    fun theHomeWiresTheBoundedBackdropAndThePrefetch() {
        val home = UiSourceFiles.file("ImmersiveHome.kt")
        assertTrue(home.contains("val backdropPosition = GalleryTrackBounds.backdropPosition(renderVisualPosition, orderedGames.size)"))
        assertTrue(home.contains("resolveImmersiveBackdropTrack(backdropPosition, orderedGames.size)"))
        assertTrue(home.contains("resolveImmersiveBackdropRetention(backdropPosition, orderedGames.size)"))
        assertTrue(home.contains("ImmersiveBackdropPrefetch(orderedGames, resolveImmersiveBackdropPrefetch(currentIndex, orderedGames.size)"))
    }
}
