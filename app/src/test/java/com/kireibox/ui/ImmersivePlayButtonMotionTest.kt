package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * r4 (user request 5): the portrait play button belongs to the hero card instead of floating still
 * above cards sliding underneath. These pin the pure motion policy the renderer follows.
 */
class ImmersivePlayButtonMotionTest {
    private val portrait =
        resolveImmersiveLayout(
            HomeOrientation.PORTRAIT,
            1080f,
            2400f,
            ImmersiveSafeBounds(0f, 0f, 1080f, 2400f),
        )
    private val landscape =
        resolveImmersiveLayout(
            HomeOrientation.LANDSCAPE,
            1672f,
            941f,
            ImmersiveSafeBounds(0f, 0f, 1672f, 941f),
        )
    private val ids = listOf(10L, 20L, 30L, 40L, 50L)

    /** A gallery frame; it is settled exactly when nothing holds or moves the track. */
    private fun track(
        visual: Float,
        target: Float = visual,
        dragging: Boolean = false,
        reducedMotion: Boolean = false,
        landscapeLayout: Boolean = false,
    ): ImmersivePlayButtonTrack {
        val layout = if (landscapeLayout) landscape else portrait
        val orientation = if (landscapeLayout) HomeOrientation.LANDSCAPE else HomeOrientation.PORTRAIT
        return ImmersivePlayButtonTrack(
            frames = resolveImmersiveFrames(visual, layout, ids, orientation, reducedMotion),
            layout = layout,
            orientation = orientation,
            visualPosition = visual,
            targetPosition = target,
            dragging = dragging,
            settled = !dragging && visual == target,
            reducedMotion = reducedMotion,
        )
    }

    @Test
    fun presenceIsFullAtRestAndGoneOnceTheCardIsAFifthOfASlotAway() {
        // r4 QA: a short fade window (it was .35 slot), so a slow drag never parks a half-transparent button.
        assertEquals(.20f, ImmersivePlayButtonMotion.HIDDEN_AT, 0f)
        assertEquals(1f, ImmersivePlayButtonMotion.presence(0f), 0f)
        assertEquals(1f, ImmersivePlayButtonMotion.presence(ImmersivePlayButtonMotion.FULL_UNTIL), 0f)
        assertEquals(0f, ImmersivePlayButtonMotion.presence(ImmersivePlayButtonMotion.HIDDEN_AT), 0f)
        assertEquals(0f, ImmersivePlayButtonMotion.presence(.5f), 0f)
        assertEquals("symmetric in both swipe directions", ImmersivePlayButtonMotion.presence(.2f), ImmersivePlayButtonMotion.presence(-.2f), 0f)
        assertEquals("non-finite input hides it", 0f, ImmersivePlayButtonMotion.presence(Float.NaN), 0f)
        var previous = 1f
        var d = 0f
        while (d <= .5f) {
            val value = ImmersivePlayButtonMotion.presence(d)
            assertTrue("fades monotonically ($d)", value <= previous + 1e-6f)
            previous = value
            d += .01f
        }
    }

    @Test
    fun atRestThePortraitButtonSitsExactlyOnTheResolvedLayoutRect() {
        val pose = resolveImmersivePlayButtonPose(track(0f), activeGameId = 10L)
        assertNotNull("visible at rest", pose)
        pose!!
        val center = portrait.cardSlots.getValue(ImmersiveSlot.CENTER)

        assertEquals(10L, pose.gameId)
        assertRect(portrait.play, pose.rect)
        assertEquals("it turns with the card's own tilt", center.rotationZ, pose.rotationZ, 0f)
        assertEquals(1f, pose.alpha, 0f)
        assertTrue(pose.ridesWithCard)
        // The pivot is the card's center, so the layer rotates the button exactly like its card.
        assertEquals(center.rect.x + center.rect.w / 2f, pose.rect.x + pose.pivotFractionX * pose.rect.w, .01f)
        assertEquals(center.rect.y + center.rect.h / 2f, pose.rect.y + pose.pivotFractionY * pose.rect.h, .01f)
        // The touch target never shrinks below the 48dp-equivalent floor of the layout at rest.
        assertTrue(pose.rect.w >= IMMERSIVE_PLAY_MIN_HEIGHT_PX - .01f)
    }

    @Test
    fun whileDraggingTheButtonRidesItsCardAndFades() {
        val rest = resolveImmersivePlayButtonPose(track(0f), 10L)!!
        val dragged = resolveImmersivePlayButtonPose(track(.12f, dragging = true), 10L)!!
        val card = resolveImmersiveFrames(.12f, portrait, ids, HomeOrientation.PORTRAIT).first { it.virtualIndex == 0 }.frame

        assertEquals("it stays on the card that started at rest", 10L, dragged.gameId)
        assertTrue("it moves left with the card", dragged.rect.x < rest.rect.x - 1f)
        assertTrue("it fades", dragged.alpha < 1f && dragged.alpha > 0f)
        // Same spot on the card: the offset from the card's corner scales with the card.
        val scale = card.rect.w / portrait.cardSlots.getValue(ImmersiveSlot.CENTER).rect.w
        assertEquals(card.rect.x + (portrait.play.x - portrait.cardSlots.getValue(ImmersiveSlot.CENTER).rect.x) * scale, dragged.rect.x, .01f)
        assertEquals(portrait.play.w * scale, dragged.rect.w, .01f)
        assertEquals(card.rotationZ, dragged.rotationZ, 0f)
        // The fade is carried by scale as well: a half-faded button is also visibly smaller.
        assertTrue(dragged.presence < 1f && dragged.presence > 0f)
        assertTrue(ImmersivePlayButtonMotion.presenceScale(dragged.presence) < 1f)
        assertEquals(1f, ImmersivePlayButtonMotion.presenceScale(rest.presence), 0f)

        assertNull("half way between cards nothing is shown", resolveImmersivePlayButtonPose(track(.5f, dragging = true), 10L))
    }

    @Test
    fun onlyOneCardEverCarriesTheButtonAndItIsTheNearestWhileDragging() {
        val pastHalf = resolveImmersivePlayButtonPose(track(.9f, dragging = true), 10L)!!
        assertEquals("past the half the incoming card carries it", 20L, pastHalf.gameId)
        assertEquals(1, ImmersivePlayButtonMotion.anchorVirtualIndex(.9f, 0f, dragging = true))
        assertEquals("settling: the destination card", 3, ImmersivePlayButtonMotion.anchorVirtualIndex(1f, 3f, dragging = false))
    }

    @Test
    fun aPagerJumpNeverFlashesTheButtonOnTheCardsPassedOnTheWay() {
        // Settling from 0 to 3 passes card 1 exactly at rest: still hidden, the destination is 3.
        assertNull(resolveImmersivePlayButtonPose(track(1f, target = 3f), 10L))
        val landing = resolveImmersivePlayButtonPose(track(2.9f, target = 3f), 10L)
        assertEquals("it fades in on the destination as it lands", 40L, landing?.gameId)
    }

    @Test
    fun reducedMotionShowsTheButtonOnlyWhileSettledWithoutAFade() {
        assertNull(resolveImmersivePlayButtonPose(track(.02f, dragging = true, reducedMotion = true), 10L))
        val settled = resolveImmersivePlayButtonPose(track(0f, reducedMotion = true), 10L)!!
        assertEquals(1f, settled.alpha, 0f)
        assertRect(portrait.play, settled.rect)
    }

    @Test
    fun theRevealSpringScalesFromTheHiddenSizeToFull() {
        assertEquals(ImmersivePlayButtonMotion.HIDDEN_SCALE, ImmersivePlayButtonMotion.revealScale(0f), 0f)
        assertEquals(1f, ImmersivePlayButtonMotion.revealScale(1f), 0f)
        assertEquals("a spring overshoot is clamped", 1f, ImmersivePlayButtonMotion.revealScale(1.04f), 0f)
    }

    @Test
    fun landscapeKeepsTheStaticPillAtTheLayoutRect() {
        listOf(0f, .3f, .5f).forEach { visual ->
            val pose = resolveImmersivePlayButtonPose(track(visual, dragging = true, landscapeLayout = true), 20L)!!
            assertEquals(landscape.play, pose.rect)
            assertEquals(20L, pose.gameId)
            assertEquals(0f, pose.rotationZ, 0f)
            assertEquals(1f, pose.alpha, 0f)
            assertTrue(!pose.ridesWithCard)
        }
        assertNull(resolveImmersivePlayButtonPose(track(0f, landscapeLayout = true), activeGameId = null))
    }

    @Test
    fun theSpokenNameSaysWhatTheButtonStarts() {
        assertEquals("开始游戏 ATRI", immersivePlayButtonDescription("ATRI"))
        assertEquals("开始游戏", immersivePlayButtonDescription(null))
    }

    private fun assertRect(
        expected: HomeRect,
        actual: HomeRect,
    ) {
        assertEquals(expected.x, actual.x, .01f)
        assertEquals(expected.y, actual.y, .01f)
        assertEquals(expected.w, actual.w, .01f)
        assertEquals(expected.h, actual.h, .01f)
    }
}
