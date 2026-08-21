package com.kireibox.ui

import androidx.compose.runtime.MonotonicFrameClock
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusGalleryControllerTest {
    /**
     * Advances 16ms per frame so [androidx.compose.animation.core.Animatable] animations
     * started on the test dispatcher can complete instead of hitting the missing
     * production frame clock.
     */
    private class VirtualFrameClock(private var nowNanos: Long = 0L) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nowNanos += 16_000_000L
            return onFrame(nowNanos)
        }
    }

    private fun TestScope.controllerWithFrameClock() =
        FocusGalleryController(CoroutineScope(coroutineContext + VirtualFrameClock()))

    private fun games(): List<Game> = listOf(Game(id = 10L), Game(id = 20L), Game(id = 30L))

    private fun fiveGames(): List<Game> =
        listOf(Game(id = 10L), Game(id = 20L), Game(id = 30L), Game(id = 40L), Game(id = 50L))

    @Test
    fun `first leftward drag after prior back commit owns advance direction at idle`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.machine.beginTransition(3, FocusDirection.BACK)
        controller.machine.setProgress(1f)
        assertTrue(controller.machine.commitCurrent(3))
        assertEquals(2, controller.machine.currentGameIndex)
        assertEquals(FocusDirection.BACK, controller.machine.direction)

        controller.updateDrag(-20f, FocusDirection.ADVANCE, 100f, 3)

        assertEquals(FocusDirection.ADVANCE, controller.machine.direction)
        assertEquals(0, controller.machine.targetGameIndex)
        assertEquals(.2f, controller.machine.progress, .001f)
        assertTrue(controller.machine.isSettling)
    }

    @Test
    fun `first rightward drag after prior advance commit owns back direction at idle`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.machine.beginTransition(3, FocusDirection.ADVANCE)
        controller.machine.setProgress(1f)
        assertTrue(controller.machine.commitCurrent(3))
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(FocusDirection.ADVANCE, controller.machine.direction)

        controller.updateDrag(25f, FocusDirection.BACK, 100f, 3)

        assertEquals(FocusDirection.BACK, controller.machine.direction)
        assertEquals(0, controller.machine.targetGameIndex)
        assertEquals(.25f, controller.machine.progress, .001f)
    }

    @Test
    fun `opposite drag unwinds current transition before changing direction`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.machine.beginTransition(3, FocusDirection.ADVANCE)
        controller.machine.setProgress(.6f)

        controller.updateDrag(20f, FocusDirection.BACK, 100f, 3)
        assertEquals(FocusDirection.ADVANCE, controller.machine.direction)
        assertEquals(.4f, controller.machine.progress, .001f)

        controller.updateDrag(50f, FocusDirection.BACK, 100f, 3)
        assertEquals(FocusDirection.BACK, controller.machine.direction)
        assertEquals(2, controller.machine.targetGameIndex)
        assertEquals(.1f, controller.machine.progress, .001f)
    }

    @Test
    fun `commit follows the direction selected by the idle drag`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.updateDrag(10f, FocusDirection.BACK, 100f, 3)
        controller.machine.setProgress(1f)
        assertTrue(controller.machine.commitCurrent(3))
        assertEquals(2, controller.machine.currentGameIndex)
        assertEquals(FocusDirection.BACK, controller.machine.direction)
        assertFalse(controller.machine.isSettling)
    }

    @Test
    fun `setGames emits an initial snapshot`() = runTest {
        val controller = FocusGalleryController(this)
        var snapshots = 0
        var lastIndex = -1
        controller.onSnapshot = { snapshot ->
            snapshots++
            lastIndex = snapshot.currentGameIndex
        }

        controller.setGames(games())

        assertTrue(snapshots >= 1)
        assertEquals(0, lastIndex)
    }

    @Test
    fun `committed snapshot reports the new current index and resolved game ids`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.machine.beginTransition(3, FocusDirection.ADVANCE)
        controller.machine.setProgress(1f)
        assertTrue(controller.machine.commitCurrent(3))

        val snapshot = controller.machine.snapshot(3)

        assertEquals(1, snapshot.currentGameIndex)
        assertEquals(1, snapshot.targetGameIndex)
        assertEquals(20L, snapshot.focusedGameId)
        assertFalse(snapshot.isSettling)
        assertEquals(0f, snapshot.progress, .001f)
        assertTrue(snapshot.cards.all { it.gameId in listOf(10L, 20L, 30L) })
    }

    @Test
    fun `snapshot keeps current index stable during transition and commits only at the end`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.machine.beginTransition(3, FocusDirection.ADVANCE)
        controller.machine.setProgress(.5f)

        val mid = controller.machine.snapshot(3)
        assertEquals(0, mid.currentGameIndex)
        assertEquals(1, mid.targetGameIndex)
        assertTrue(mid.isSettling)
        assertEquals(.5f, mid.progress, .001f)

        controller.machine.setProgress(1f)
        assertTrue(controller.machine.commitCurrent(3))
        val end = controller.machine.snapshot(3)
        assertEquals(1, end.currentGameIndex)
        assertFalse(end.isSettling)
        assertEquals(0f, end.progress, .001f)
    }

    @Test
    fun `commit bumps the commit sequence exactly once per settle`() {
        val machine = FocusGalleryMachine()
        machine.beginTransition(3, FocusDirection.ADVANCE)
        machine.setProgress(1f)

        assertTrue(machine.commitCurrent(3))
        assertEquals(1L, machine.commitSequence)
        // A second commit attempt on the same settle must not double-count.
        assertFalse(machine.commitCurrent(3))
        assertEquals(1L, machine.commitSequence)
    }

    @Test
    fun `beginTransitionTo commits the explicit tapped target`() {
        val machine = FocusGalleryMachine()
        machine.beginTransitionTo(2, 5, FocusDirection.BACK)
        assertEquals(2, machine.targetGameIndex)
        assertEquals(FocusDirection.BACK, machine.direction)

        machine.setProgress(1f)
        assertTrue(machine.commitCurrent(5))
        assertEquals(2, machine.currentGameIndex)
        assertEquals(1L, machine.commitSequence)
    }

    @Test
    fun `two game side tap resolves to the alternate game and records the interaction`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(listOf(Game(id = 10L), Game(id = 20L)))

        assertEquals(FocusSwitchResult.NAVIGATED, controller.switchToGame(20L, 2))
        assertEquals(1L, controller.machine.manualInteractionSequence)
        // The queued transition targets the tapped id deterministically.
        val plan = FocusGalleryInteractionPolicy.navigationPlan(0, 1, 2)
        assertEquals(1, requireNotNull(plan).targetIndex)
        // A full settle commits exactly once to the tapped game.
        advanceUntilIdle()
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.commitSequence)
        // Repeated tap of the same side is idempotent at idle: target == current.
        assertEquals(FocusSwitchResult.STAYED, controller.switchToGame(20L, 2))
        assertEquals(FocusSwitchResult.NAVIGATED, controller.switchToGame(10L, 2))
        advanceUntilIdle()
        assertEquals(0, controller.machine.currentGameIndex)
        assertEquals(2L, controller.machine.commitSequence)
    }

    @Test
    fun `switch continues an in-flight transition to the same target without restart`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.machine.beginTransitionTo(1, 3, FocusDirection.ADVANCE)
        controller.machine.setProgress(.5f)

        assertEquals(FocusSwitchResult.CONTINUING, controller.switchToGame(20L, 3))
        assertEquals(1L, controller.machine.manualInteractionSequence)
        assertEquals(1, controller.machine.targetGameIndex)
    }

    @Test
    fun `switch stays on the leaving card and cancels an in-flight transition`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())
        controller.machine.beginTransitionTo(1, 3, FocusDirection.ADVANCE)
        controller.machine.setProgress(.8f)

        assertEquals(FocusSwitchResult.STAYED, controller.switchToGame(10L, 3))
        // The unwind job is owned by a fresh generation; the old one can no longer commit.
        val generation = controller.currentTransitionGeneration
        assertTrue(controller.canCommitFor(generation))
        advanceUntilIdle()
        assertFalse(controller.machine.isSettling)
        assertEquals(0, controller.machine.currentGameIndex)
        assertEquals(0L, controller.machine.commitSequence)
    }

    @Test
    fun `manual takeover invalidates the stale auto settle token`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.beginAdvance(3)
        val staleGeneration = controller.currentTransitionGeneration
        assertTrue(controller.canCommitFor(staleGeneration))

        controller.cancelTransition(3)

        assertFalse(controller.canCommitFor(staleGeneration))
        assertTrue(controller.canCommitFor(controller.currentTransitionGeneration))
    }

    @Test
    fun `setGames during settling cancels a stale transition when ids change`() = runTest {
        val controller = FocusGalleryController(this)
        controller.setGames(games())
        controller.machine.beginTransitionTo(1, 3, FocusDirection.ADVANCE)
        controller.machine.setProgress(.9f)
        assertTrue(controller.machine.isSettling)

        controller.setGames(listOf(Game(id = 40L), Game(id = 50L), Game(id = 60L)))

        assertFalse(controller.machine.isSettling)
        assertEquals(0f, controller.machine.progress, .001f)
        assertEquals(0, controller.machine.currentGameIndex)
    }

    @Test
    fun `three game multi-step commits one adjacent card per step and lands on the final target`() = runTest {
        val controller = controllerWithFrameClock()
        val three = games()
        val snapshots = mutableListOf<FocusGallerySnapshot>()
        controller.onSnapshot = { snapshots += it }
        controller.setGames(three)
        snapshots.clear()

        // Forced-forward two-step plan: 0 -> 1 -> 2. The shortest-path dot policy never
        // emits this for three games, so the plan is exercised directly through the
        // sequence entry point.
        controller.startSequence(
            gameCount = 3,
            plan = FocusNavigationPlan(FocusDirection.ADVANCE, stepCount = 2, targetIndex = 2),
        )
        advanceUntilIdle()

        val commits = snapshots.filter { !it.isSettling && it.commitSequence > 0L }
        assertEquals(listOf(1, 2), commits.map { it.currentGameIndex })
        assertEquals(2L, commits.last().commitSequence)
        assertEquals(2, commits.last().currentGameIndex)
        assertEquals(2, controller.machine.currentGameIndex)
        assertFalse(controller.machine.isSettling)

        // Every commit matches the adjacent/incoming card the settle frame exposed.
        var settleTarget = -1
        var settleIncomingGameId = -1L
        var seenCommits = 0L
        for (snapshot in snapshots) {
            if (snapshot.isSettling && snapshot.progress >= .999f) {
                settleTarget = snapshot.targetGameIndex
                settleIncomingGameId = snapshot.cards
                    .first { it.role == FocusCardRole.SIDE_TO_CENTER }
                    .gameId
            } else if (!snapshot.isSettling && snapshot.commitSequence > seenCommits) {
                seenCommits = snapshot.commitSequence
                assertEquals(settleTarget, snapshot.currentGameIndex)
                assertEquals(three[settleTarget].id, settleIncomingGameId)
            }
        }
        assertEquals(2L, seenCommits)
    }

    @Test
    fun `five game dot navigation commits each adjacent card and lands on the tapped target`() = runTest {
        val controller = controllerWithFrameClock()
        val five = fiveGames()
        val snapshots = mutableListOf<FocusGallerySnapshot>()
        controller.onSnapshot = { snapshots += it }
        controller.setGames(five)
        snapshots.clear()

        // Real dot path: 0 -> 2 is two ADVANCE steps, 2 -> 0 is two BACK steps.
        controller.goToGame(2, 5)
        advanceUntilIdle()
        assertEquals(2, controller.machine.currentGameIndex)
        assertEquals(2L, controller.machine.commitSequence)

        controller.goToGame(0, 5)
        advanceUntilIdle()
        assertEquals(0, controller.machine.currentGameIndex)
        assertEquals(4L, controller.machine.commitSequence)

        val commits = snapshots.filter { !it.isSettling && it.commitSequence > 0L }
        assertEquals(listOf(1, 2, 1, 0), commits.map { it.currentGameIndex })
        // Every commit matches the adjacent/incoming card the settle frame exposed.
        var settleTarget = -1
        var settleIncomingGameId = -1L
        var seenCommits = 0L
        for (snapshot in snapshots) {
            if (snapshot.isSettling && snapshot.progress >= .999f) {
                settleTarget = snapshot.targetGameIndex
                settleIncomingGameId = snapshot.cards
                    .first { it.role == FocusCardRole.SIDE_TO_CENTER }
                    .gameId
            } else if (!snapshot.isSettling && snapshot.commitSequence > seenCommits) {
                seenCommits = snapshot.commitSequence
                assertEquals(settleTarget, snapshot.currentGameIndex)
                assertEquals(five[settleTarget].id, settleIncomingGameId)
            }
        }
        assertEquals(4L, seenCommits)
    }

    @Test
    fun `new multi-step sequence invalidates a stale one so it cannot insert commits`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(fiveGames())

        controller.startSequence(
            gameCount = 5,
            plan = FocusNavigationPlan(FocusDirection.ADVANCE, stepCount = 2, targetIndex = 2),
        )
        val staleGeneration = controller.currentTransitionGeneration
        assertTrue(controller.canCommitFor(staleGeneration))

        controller.startSequence(
            gameCount = 5,
            plan = FocusNavigationPlan(FocusDirection.BACK, stepCount = 2, targetIndex = 3),
        )
        val currentGeneration = controller.currentTransitionGeneration
        assertFalse(controller.canCommitFor(staleGeneration))
        assertTrue(controller.canCommitFor(currentGeneration))

        advanceUntilIdle()
        // Only the second sequence commits (0 -> 4 -> 3); the stale one cannot insert
        // its two commits.
        assertEquals(3, controller.machine.currentGameIndex)
        assertEquals(2L, controller.machine.commitSequence)
    }

    @Test
    fun `stale auto token is rejected after a manual side tap`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())
        val stale = FocusAutoAdvanceTimerPolicy.tokenFor(
            commitSequence = controller.machine.commitSequence,
            manualInteractionSequence = controller.machine.manualInteractionSequence,
        )

        assertEquals(FocusSwitchResult.NAVIGATED, controller.switchToGame(20L, 3))
        advanceUntilIdle()

        assertFalse(
            controller.beginAutoAdvanceIfCurrent(3, stale.commitSequence, stale.manualInteractionSequence),
        )
        assertFalse(controller.machine.isSettling)
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.manualInteractionSequence)
        assertEquals(1L, controller.machine.commitSequence)
    }

    @Test
    fun `stale auto token is rejected after a manual drag commit`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())
        val stale = FocusAutoAdvanceTimerPolicy.tokenFor(
            commitSequence = controller.machine.commitSequence,
            manualInteractionSequence = controller.machine.manualInteractionSequence,
        )

        controller.updateDrag(-20f, FocusDirection.ADVANCE, 100f, 3)
        controller.machine.setProgress(1f)
        assertTrue(controller.machine.commitCurrent(3))

        assertFalse(
            controller.beginAutoAdvanceIfCurrent(3, stale.commitSequence, stale.manualInteractionSequence),
        )
        assertFalse(controller.machine.isSettling)
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.manualInteractionSequence)
        assertEquals(1L, controller.machine.commitSequence)
    }

    @Test
    fun `fresh auto token starts exactly one advance and a consumed token is rejected`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())
        val fresh = FocusAutoAdvanceTimerPolicy.tokenFor(
            commitSequence = controller.machine.commitSequence,
            manualInteractionSequence = controller.machine.manualInteractionSequence,
        )

        assertTrue(controller.beginAutoAdvanceIfCurrent(3, fresh.commitSequence, fresh.manualInteractionSequence))
        advanceUntilIdle()

        // Exactly one advance and one commit from the fresh token.
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.commitSequence)
        assertFalse(controller.machine.isSettling)
        // The consumed token can never start a second transition once the machine left
        // the captured sequences (settling or committed).
        assertFalse(controller.beginAutoAdvanceIfCurrent(3, fresh.commitSequence, fresh.manualInteractionSequence))
        assertFalse(controller.machine.isSettling)
    }

    @Test
    fun `auto token is rejected while the machine is settling`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())
        val token = FocusAutoAdvanceTimerPolicy.tokenFor(
            commitSequence = controller.machine.commitSequence,
            manualInteractionSequence = controller.machine.manualInteractionSequence,
        )
        controller.machine.beginTransitionTo(1, 3, FocusDirection.ADVANCE)
        assertTrue(controller.machine.isSettling)

        assertFalse(controller.beginAutoAdvanceIfCurrent(3, token.commitSequence, token.manualInteractionSequence))
        // The rejected call must not mutate state or bump the generation.
        assertTrue(controller.machine.isSettling)
        assertEquals(0, controller.machine.currentGameIndex)
        assertEquals(0L, controller.machine.commitSequence)
    }

    @Test
    fun `six second auto timer exposes the token and key contract`() {
        assertEquals(6_000L, FocusAutoAdvanceTimerPolicy.AUTO_ADVANCE_MILLIS)
        assertEquals(
            FocusAutoAdvanceToken(commitSequence = 3L, manualInteractionSequence = 2L),
            FocusAutoAdvanceTimerPolicy.tokenFor(commitSequence = 3L, manualInteractionSequence = 2L),
        )

        val base = FocusAutoAdvanceTimerPolicy.timerKeys(
            galleryIdle = true,
            galleryCardPressed = false,
            currentGameIndex = 0,
            commitSequence = 0L,
            manualInteractionSequence = 0L,
            gameCount = 3,
            isVisible = true,
            isResumed = true,
            reducedMotion = false,
        )
        val afterManual = FocusAutoAdvanceTimerPolicy.timerKeys(
            galleryIdle = true,
            galleryCardPressed = false,
            currentGameIndex = 0,
            commitSequence = 0L,
            manualInteractionSequence = 1L,
            gameCount = 3,
            isVisible = true,
            isResumed = true,
            reducedMotion = false,
        )
        val afterCommit = FocusAutoAdvanceTimerPolicy.timerKeys(
            galleryIdle = true,
            galleryCardPressed = false,
            currentGameIndex = 1,
            commitSequence = 1L,
            manualInteractionSequence = 1L,
            gameCount = 3,
            isVisible = true,
            isResumed = true,
            reducedMotion = false,
        )

        assertNotEquals(base, afterManual)
        assertNotEquals(base, afterCommit)
        assertEquals(listOf(true, false, 0, 0L, 0L, 3, true, true, false), base)
    }

    @Test
    fun `drag takeover bumps manual sequence once per gesture and rejects the stale auto token`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())
        val stale = FocusAutoAdvanceTimerPolicy.tokenFor(
            commitSequence = controller.machine.commitSequence,
            manualInteractionSequence = controller.machine.manualInteractionSequence,
        )

        controller.updateDrag(-10f, FocusDirection.ADVANCE, 100f, 3)
        controller.updateDrag(-20f, FocusDirection.ADVANCE, 100f, 3)

        // Exactly one manual interaction per gesture: the first frame from idle, not each delta.
        assertEquals(1L, controller.machine.manualInteractionSequence)
        assertFalse(
            controller.beginAutoAdvanceIfCurrent(3, stale.commitSequence, stale.manualInteractionSequence),
        )

        controller.machine.setProgress(1f)
        assertTrue(controller.machine.commitCurrent(3))
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.commitSequence)
    }

    @Test
    fun `drag cancel to anchor invalidates the old auto token and a fresh token advances from idle`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())
        val stale = FocusAutoAdvanceTimerPolicy.tokenFor(
            commitSequence = controller.machine.commitSequence,
            manualInteractionSequence = controller.machine.manualInteractionSequence,
        )

        // 0.2 page stays below the 0.28 distance threshold, so the release returns.
        controller.updateDrag(20f, FocusDirection.BACK, 100f, 3)
        controller.settleDrag(
            velocityDpPerSecond = 0f,
            requestedDirection = FocusDirection.BACK,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()

        assertFalse(controller.machine.isSettling)
        assertEquals(0f, controller.machine.progress, .001f)
        assertEquals(0, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.manualInteractionSequence)
        assertEquals(0L, controller.machine.commitSequence)
        // Cancel back to the anchor without a commit still invalidates the old timer token.
        assertFalse(
            controller.beginAutoAdvanceIfCurrent(3, stale.commitSequence, stale.manualInteractionSequence),
        )

        val fresh = FocusAutoAdvanceTimerPolicy.tokenFor(
            commitSequence = controller.machine.commitSequence,
            manualInteractionSequence = controller.machine.manualInteractionSequence,
        )
        assertTrue(controller.beginAutoAdvanceIfCurrent(3, fresh.commitSequence, fresh.manualInteractionSequence))
        advanceUntilIdle()
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.commitSequence)
        assertFalse(controller.machine.isSettling)
    }

    @Test
    fun `subthreshold drag returns to the anchor without committing`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())

        controller.updateDrag(-27f, FocusDirection.ADVANCE, 100f, 3)
        assertEquals(.27f, controller.machine.progress, .001f)
        controller.settleDrag(
            velocityDpPerSecond = 0f,
            requestedDirection = FocusDirection.ADVANCE,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()

        assertFalse(controller.machine.isSettling)
        assertEquals(0f, controller.machine.progress, .001f)
        assertEquals(0, controller.machine.currentGameIndex)
        assertEquals(0L, controller.machine.commitSequence)
    }

    @Test
    fun `threshold drag commits the next page on release`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())

        controller.updateDrag(-28f, FocusDirection.ADVANCE, 100f, 3)
        controller.settleDrag(
            velocityDpPerSecond = 0f,
            requestedDirection = FocusDirection.ADVANCE,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()

        assertFalse(controller.machine.isSettling)
        assertEquals(0f, controller.machine.progress, .001f)
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(FocusDirection.ADVANCE, controller.machine.direction)
        assertEquals(1L, controller.machine.commitSequence)
    }

    @Test
    fun `fast one-page fling commits exactly one page at any fling speed`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())

        // 0.1 page of drag plus a 1.1 page/sec fling: velocity alone crosses the commit
        // threshold, and the settle can never jump more than one page.
        controller.updateDrag(-10f, FocusDirection.ADVANCE, 100f, 3)
        controller.settleDrag(
            velocityDpPerSecond = -110f,
            requestedDirection = FocusDirection.ADVANCE,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()
        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.commitSequence)
        assertFalse(controller.machine.isSettling)

        // A much faster fling still lands on exactly the next page, never two.
        controller.updateDrag(-5f, FocusDirection.ADVANCE, 100f, 3)
        controller.settleDrag(
            velocityDpPerSecond = -600f,
            requestedDirection = FocusDirection.ADVANCE,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()
        assertEquals(2, controller.machine.currentGameIndex)
        assertEquals(2L, controller.machine.commitSequence)
        assertFalse(controller.machine.isSettling)
    }

    @Test
    fun `fast fling wraps backward to the last game and forward past the end wraps to zero`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())

        // BACK from index 0 wraps to the last game (2), preserving cyclic index semantics.
        controller.updateDrag(10f, FocusDirection.BACK, 100f, 3)
        controller.settleDrag(
            velocityDpPerSecond = 120f,
            requestedDirection = FocusDirection.BACK,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()
        assertEquals(2, controller.machine.currentGameIndex)
        assertEquals(FocusDirection.BACK, controller.machine.direction)
        assertEquals(1L, controller.machine.commitSequence)
        assertFalse(controller.machine.isSettling)

        // ADVANCE from the last game wraps back to 0.
        controller.updateDrag(-10f, FocusDirection.ADVANCE, 100f, 3)
        controller.settleDrag(
            velocityDpPerSecond = -120f,
            requestedDirection = FocusDirection.ADVANCE,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()
        assertEquals(0, controller.machine.currentGameIndex)
        assertEquals(2L, controller.machine.commitSequence)
        assertFalse(controller.machine.isSettling)
    }

    @Test
    fun `new drag interrupts a spring settle continuously without committing the stale job`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())

        controller.updateDrag(-30f, FocusDirection.ADVANCE, 100f, 3)
        controller.settleDrag(
            velocityDpPerSecond = 0f,
            requestedDirection = FocusDirection.ADVANCE,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        val settleGeneration = controller.currentTransitionGeneration
        assertTrue(controller.canCommitFor(settleGeneration))

        // Interrupt before the spring completes: the opposite drag unwinds the current
        // progress instead of snapping, and the stale settle can no longer commit.
        controller.updateDrag(10f, FocusDirection.BACK, 100f, 3)
        assertEquals(.2f, controller.machine.progress, .001f)
        assertEquals(FocusDirection.ADVANCE, controller.machine.direction)
        assertFalse(controller.canCommitFor(settleGeneration))
        assertTrue(controller.machine.isSettling)

        // Unwinding past zero hands the transition to the new direction continuously.
        controller.updateDrag(60f, FocusDirection.BACK, 100f, 3)
        assertEquals(FocusDirection.BACK, controller.machine.direction)
        assertEquals(.4f, controller.machine.progress, .001f)

        controller.settleDrag(
            velocityDpPerSecond = 0f,
            requestedDirection = FocusDirection.BACK,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()
        // The interrupted ADVANCE settle never commits; BACK commits exactly one page.
        assertEquals(1L, controller.machine.commitSequence)
        assertEquals(2, controller.machine.currentGameIndex)
        assertFalse(controller.machine.isSettling)
    }

    @Test
    fun `reduced motion settle keeps the short crossfade and commits one page`() = runTest {
        val controller = controllerWithFrameClock()
        controller.setGames(games())
        controller.reducedMotion = true

        controller.updateDrag(-30f, FocusDirection.ADVANCE, 100f, 3)
        controller.settleDrag(
            velocityDpPerSecond = -500f,
            requestedDirection = FocusDirection.ADVANCE,
            slotDistanceDp = 100f,
            gameCount = 3,
        )
        advanceUntilIdle()

        assertEquals(1, controller.machine.currentGameIndex)
        assertEquals(1L, controller.machine.commitSequence)
        assertFalse(controller.machine.isSettling)
        assertEquals(0f, controller.machine.progress, .001f)
    }
}
