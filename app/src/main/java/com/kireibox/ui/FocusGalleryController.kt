package com.kireibox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import com.yuki.yukihub.BuildConfig
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.abs

/**
 * Pure four-slot state machine: the current index commits only after the transition
 * completes. Opposite drag input first unwinds the current progress to its anchor and only
 * then changes direction, so a new gesture owns the transition without teleporting a card.
 */
internal class FocusGalleryMachine {
    var currentGameIndex: Int = 0
        internal set
    var targetGameIndex: Int = 0
        private set
    var direction: FocusDirection = FocusDirection.ADVANCE
        private set
    var progress: Float = 0f
        private set
    var isSettling: Boolean = false
        private set

    /** Monotonic commit counter: every successful settle bumps it exactly once. */
    var commitSequence: Long = 0L
        internal set

    /**
     * Monotonic counter of explicit user interactions: side taps, dot navigation and the
     * first frame of every drag that takes over from idle. The auto-advance timer captures
     * it together with [commitSequence]; any change invalidates a pending timer token.
     */
    var manualInteractionSequence: Long = 0L
        internal set

    var games: List<Game> = emptyList()

    fun beginTransition(
        gameCount: Int,
        requestedDirection: FocusDirection,
    ) {
        if (gameCount <= 0) return
        val target =
            when (requestedDirection) {
                FocusDirection.ADVANCE -> FocusGalleryIndexPolicy.next(currentGameIndex, gameCount)
                FocusDirection.BACK -> FocusGalleryIndexPolicy.previous(currentGameIndex, gameCount)
            }
        direction = requestedDirection
        targetGameIndex = target
        progress = 0f
        isSettling = true
    }

    /**
     * Explicit-target transition used by side taps and dot navigation. The target is
     * resolved synchronously from the card the user actually saw, so the result stays
     * deterministic even when a stale auto job commits in the same frame.
     */
    fun beginTransitionTo(
        targetIndex: Int,
        gameCount: Int,
        requestedDirection: FocusDirection,
    ) {
        if (gameCount <= 0) return
        direction = requestedDirection
        targetGameIndex = targetIndex.mod(gameCount)
        progress = 0f
        isSettling = true
    }

    fun noteManualInteraction() {
        manualInteractionSequence++
    }

    /** At the idle anchor the first drag always owns direction, including after BACK. */
    fun interruptWithDirection(
        gameCount: Int,
        requestedDirection: FocusDirection,
    ) {
        if (gameCount <= 0) return
        if (progress <= 0f) {
            direction = requestedDirection
            targetGameIndex =
                when (requestedDirection) {
                    FocusDirection.ADVANCE -> FocusGalleryIndexPolicy.next(currentGameIndex, gameCount)
                    FocusDirection.BACK -> FocusGalleryIndexPolicy.previous(currentGameIndex, gameCount)
                }
            isSettling = true
        }
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        if (progress > 0f) isSettling = true
    }

    fun commitCurrent(gameCount: Int): Boolean {
        if (gameCount <= 0 || !isSettling || !FocusGalleryInteractionPolicy.canCommit(progress)) {
            return false
        }
        currentGameIndex = targetGameIndex
        targetGameIndex = currentGameIndex
        progress = 0f
        isSettling = false
        commitSequence++
        return true
    }

    fun cancelTransition() {
        targetGameIndex = currentGameIndex
        progress = 0f
        isSettling = false
    }

    fun snapshot(gameCount: Int): FocusGallerySnapshot {
        val index = if (gameCount <= 0) 0 else currentGameIndex.mod(gameCount)
        val cards =
            FocusGalleryMappingPolicy.cards(
                gameCount = gameCount,
                currentGameIndex = index,
                direction = direction,
            )
        return FocusGallerySnapshot(
            currentGameIndex = index,
            targetGameIndex = targetGameIndex,
            direction = direction,
            progress = progress,
            cards = FocusGalleryGameResolver.resolve(cards, games),
            focusedGameId = games.getOrNull(currentGameIndex)?.id,
            isSettling = isSettling,
            commitSequence = commitSequence,
            manualInteractionSequence = manualInteractionSequence,
        )
    }

    fun currentGameId(): Long? = games.getOrNull(currentGameIndex)?.id
}

/**
 * Compose-facing controller: one transition path for drag, side tap and auto advance.
 * Progress is an Animatable so interruption never teleports and every frame is continuous.
 */
internal class FocusGalleryController(
    private val scope: CoroutineScope,
    initialGameIndex: Int = 0,
    var onSnapshot: (FocusGallerySnapshot) -> Unit = {},
) {
    companion object {
        /** Standard focus transition: continuous position/size/scale motion, >= 520ms. */
        const val TRANSITION_MILLIS = 520

        /** Reduced-motion transition: short opacity-only crossfade, no movement or scale. */
        const val REDUCED_TRANSITION_MILLIS = 160

        /** Drag-release settle spring: critically damped so it never overshoots the page. */
        const val SETTLE_DAMPING_RATIO = Spring.DampingRatioNoBouncy
        const val SETTLE_STIFFNESS = Spring.StiffnessMediumLow
    }

    val machine =
        FocusGalleryMachine().apply {
            currentGameIndex = initialGameIndex.coerceAtLeast(0)
        }

    private val progressAnimator = Animatable(0f)
    private var animationJob: Job? = null

    /**
     * Owns the transition. Every take-over (new sequence, drag, cancel, data reset)
     * bumps it; a job may only commit or emit while its captured generation is current,
     * so stale auto/previous-tap coroutines can never rewind a newer settle.
     */
    private var transitionGeneration = 0L

    internal val currentTransitionGeneration: Long get() = transitionGeneration

    /** True only while the caller's captured generation still owns the transition. */
    internal fun canCommitFor(generation: Long): Boolean = generation == transitionGeneration

    /** Effective only in Debug builds; no receiver or behavior is exposed in release. */
    var freezeOverride: Float? = null

    var reducedMotion: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                animationJob?.cancel()
                animationJob = null
                transitionGeneration++
                if (machine.isSettling && machine.progress >= .5f) {
                    machine.setProgress(1f)
                    machine.commitCurrent(machine.games.size)
                } else {
                    machine.cancelTransition()
                }
                scope.launch { progressAnimator.snapTo(0f) }
                emitSnapshot()
            }
        }

    private fun emitSnapshot() {
        onSnapshot(machine.snapshot(machine.games.size))
    }

    fun setGames(games: List<Game>) {
        val idsChanged = machine.games.map { it.id } != games.map { it.id }
        val wasSettling = machine.isSettling
        machine.games = games
        val count = games.size
        if (count > 0 && machine.currentGameIndex >= count) {
            machine.currentGameIndex = 0
        }
        if (wasSettling && idsChanged) {
            animationJob?.cancel()
            animationJob = null
            transitionGeneration++
            machine.cancelTransition()
            scope.launch { progressAnimator.snapTo(0f) }
        }
        emitSnapshot()
    }

    fun beginAdvance(gameCount: Int) {
        if (gameCount <= 1) return
        val target = FocusGalleryIndexPolicy.next(machine.currentGameIndex, gameCount)
        val plan =
            FocusGalleryInteractionPolicy.navigationPlan(
                currentIndex = machine.currentGameIndex,
                targetIndex = target,
                gameCount = gameCount,
            ) ?: return
        startSequence(gameCount, plan)
    }

    /**
     * Atomic auto-advance guarded by a token captured when the six-second timer started.
     * Returns true only when the machine is still idle and BOTH the commit sequence and
     * the manual interaction sequence exactly match the captured values; only then does it
     * start the advance. Any tap, drag, commit or cancel after the token was captured makes
     * this call return false without bumping the generation or mutating state, so a
     * cancelled old timer can never start a new transition or re-time the interval.
     */
    fun beginAutoAdvanceIfCurrent(
        gameCount: Int,
        expectedCommitSequence: Long,
        expectedManualInteractionSequence: Long,
    ): Boolean {
        if (gameCount <= 1) return false
        if (machine.isSettling || machine.progress > 0f) return false
        if (machine.commitSequence != expectedCommitSequence) return false
        if (machine.manualInteractionSequence != expectedManualInteractionSequence) return false
        beginAdvance(gameCount)
        return true
    }

    fun beginBack(gameCount: Int) {
        if (gameCount <= 1) return
        val target = FocusGalleryIndexPolicy.previous(machine.currentGameIndex, gameCount)
        val plan =
            FocusGalleryInteractionPolicy.navigationPlan(
                currentIndex = machine.currentGameIndex,
                targetIndex = target,
                gameCount = gameCount,
            ) ?: return
        startSequence(gameCount, plan)
    }

    /** Dot navigation to an explicit game index using the shortest direction. */
    fun goToGame(
        gameIndex: Int,
        gameCount: Int
    ) {
        val plan =
            FocusGalleryInteractionPolicy.navigationPlan(
                currentIndex = machine.currentGameIndex,
                targetIndex = gameIndex,
                gameCount = gameCount,
            ) ?: return
        machine.noteManualInteraction()
        startSequence(gameCount, plan)
    }

    /**
     * Explicit side-card tap: resolve the tapped card's game id to an index at call time
     * and run the transition to that exact target. The target is the card the user saw,
     * so the outcome cannot depend on whether a stale auto job committed a frame earlier;
     * the generation token also makes the commit atomic (exactly one per settle).
     */
    fun switchToGame(
        gameId: Long,
        gameCount: Int
    ): FocusSwitchResult {
        val target = machine.games.indexOfFirst { it.id == gameId }
        if (target < 0) return FocusSwitchResult.IGNORED
        machine.noteManualInteraction()
        val decision =
            FocusSwitchPolicy.decide(
                currentGameIndex = machine.currentGameIndex,
                targetGameIndex = target,
                isSettling = machine.isSettling,
                settlingTargetIndex = machine.targetGameIndex,
            )
        when (decision) {
            FocusSwitchResult.IGNORED -> return FocusSwitchResult.IGNORED
            FocusSwitchResult.CONTINUING -> {
                // Publish the interaction so the auto timer restarts immediately.
                emitSnapshot()
                return FocusSwitchResult.CONTINUING
            }
            FocusSwitchResult.STAYED -> {
                if (machine.isSettling) {
                    // Tap on the card being left behind: unwind the in-flight transition.
                    animateTo(0f, gameCount, commit = false)
                } else {
                    emitSnapshot()
                }
                return FocusSwitchResult.STAYED
            }
            FocusSwitchResult.NAVIGATED -> Unit
        }
        val plan =
            FocusGalleryInteractionPolicy.navigationPlan(
                currentIndex = machine.currentGameIndex,
                targetIndex = target,
                gameCount = gameCount,
            )
        if (plan == null) return FocusSwitchResult.STAYED
        startSequence(gameCount, plan)
        return FocusSwitchResult.NAVIGATED
    }

    /**
     * Shared by pointer drag only; side tap/auto use the same controller but full tween.
     * Every frame maps the pointer delta 1:1 to page progress; the machine clamps the
     * total inside [0, 1], so dragging past a page stops at the boundary without a
     * rubber-band (the gallery is cyclic) and a gesture can never cross more than one page.
     */
    fun updateDrag(
        deltaXDp: Float,
        requestedDirection: FocusDirection,
        slotDistanceDp: Float,
        gameCount: Int
    ) {
        animationJob?.cancel()
        animationJob = null
        transitionGeneration++
        val firstDragFrameFromIdle = !machine.isSettling && machine.progress <= 0f
        if (gameCount <= 1 || slotDistanceDp <= 0f) {
            if (firstDragFrameFromIdle) {
                machine.noteManualInteraction()
                emitSnapshot()
            }
            return
        }
        if (firstDragFrameFromIdle) {
            // A drag that takes over from idle is a manual interaction: it bumps the
            // sequence exactly once so any pending auto-advance token becomes stale and
            // the timer restarts a full interval after the gesture ends.
            machine.noteManualInteraction()
        }
        val deltaProgress = abs(deltaXDp) / slotDistanceDp
        if (requestedDirection != machine.direction && machine.progress > 0f) {
            val remaining = machine.progress - deltaProgress
            if (remaining >= 0f) {
                machine.setProgress(remaining)
            } else {
                machine.cancelTransition()
                machine.beginTransition(gameCount, requestedDirection)
                machine.setProgress(-remaining)
            }
        } else {
            machine.interruptWithDirection(gameCount, requestedDirection)
            machine.setProgress(machine.progress + deltaProgress)
        }
        emitSnapshot()
    }

    /**
     * Drag release: normalize the pointer velocity to pages per second, decide the
     * single-page settle target (0 = return to the anchor, 1 = commit the neighbor)
     * against the 0.28 page distance threshold and the 0.95 page/second velocity
     * threshold, then spring there so the release inherits the gesture's momentum.
     */
    fun settleDrag(
        velocityDpPerSecond: Float,
        requestedDirection: FocusDirection,
        slotDistanceDp: Float,
        gameCount: Int,
    ) {
        val effectiveDirection = if (machine.progress > 0f) machine.direction else requestedDirection
        val velocityPagesPerSecond =
            if (slotDistanceDp > 0f) {
                velocityDpPerSecond / slotDistanceDp
            } else {
                0f
            }
        val decision =
            FocusGalleryInteractionPolicy.settle(
                progress = machine.progress,
                velocityPagesPerSecond = velocityPagesPerSecond,
                requestedDirection = effectiveDirection,
            )
        machine.interruptWithDirection(gameCount, decision.direction)
        settleTo(
            target = decision.targetProgress,
            gameCount = gameCount,
            commit = decision.settleToCommitted,
            velocityPagesPerSecond = velocityPagesPerSecond,
        )
    }

    fun cancelTransition(gameCount: Int) {
        animationJob?.cancel()
        animationJob = null
        transitionGeneration++
        machine.cancelTransition()
        scope.launch { progressAnimator.snapTo(0f) }
        emitSnapshot()
    }

    /**
     * Multi-step plans (dots / forced-forward navigation) commit one adjacent card per
     * step. Every iteration targets only the neighbor in [FocusNavigationPlan.direction]
     * from the just-committed index; the last step is pinned to the captured
     * [FocusNavigationPlan.targetIndex] so the sequence always ends on the exact
     * destination. Committing the final index on the first step would leave later steps
     * animating an already-committed card.
     */
    internal fun startSequence(
        gameCount: Int,
        plan: FocusNavigationPlan
    ) {
        animationJob?.cancel()
        val generation = ++transitionGeneration
        animationJob =
            scope.launch {
                repeat(plan.stepCount) { step ->
                    if (!isActive || generation != transitionGeneration) return@launch
                    val stepTarget =
                        if (step == plan.stepCount - 1) {
                            plan.targetIndex
                        } else {
                            when (plan.direction) {
                                FocusDirection.ADVANCE ->
                                    FocusGalleryIndexPolicy.next(machine.currentGameIndex, gameCount)
                                FocusDirection.BACK ->
                                    FocusGalleryIndexPolicy.previous(machine.currentGameIndex, gameCount)
                            }
                        }
                    machine.beginTransitionTo(stepTarget, gameCount, plan.direction)
                    progressAnimator.snapTo(0f)
                    if (!isActive || generation != transitionGeneration) return@launch
                    emitSnapshot()
                    animateCurrentProgress(target = 1f, generation = generation)
                    if (!isActive || generation != transitionGeneration) return@launch
                    // Commit on the post-settle frame, never in the animation's final callback.
                    yield()
                    if (!isActive || generation != transitionGeneration) return@launch
                    machine.setProgress(1f)
                    machine.commitCurrent(gameCount)
                    progressAnimator.snapTo(0f)
                    emitSnapshot()
                }
            }
    }

    /** Tween transition for taps, dots and auto advance (fixed-duration, non-velocity). */
    private fun animateTo(
        target: Float,
        gameCount: Int,
        commit: Boolean = true
    ) {
        animationJob?.cancel()
        val generation = ++transitionGeneration
        animationJob =
            scope.launch {
                progressAnimator.snapTo(machine.progress)
                if (!isActive || generation != transitionGeneration) return@launch
                animateCurrentProgress(target, generation)
                finishSettle(target, gameCount, commit, generation)
            }
    }

    /**
     * Single-page spring settle for drag release. The critically damped spring inherits
     * the gesture velocity, so a fast fling carries through to the committed page and a
     * subthreshold release decelerates back to the anchor. The target is always 0 or 1,
     * so a gesture can never cross more than one page. Reduced motion keeps the short
     * opacity-only crossfade instead of the spring.
     */
    private fun settleTo(
        target: Float,
        gameCount: Int,
        commit: Boolean,
        velocityPagesPerSecond: Float,
    ) {
        animationJob?.cancel()
        val generation = ++transitionGeneration
        animationJob =
            scope.launch {
                progressAnimator.snapTo(machine.progress)
                if (!isActive || generation != transitionGeneration) return@launch
                if (reducedMotion) {
                    animateCurrentProgress(target, generation)
                } else {
                    // Sign the initial velocity toward the settle target: a commit fling keeps
                    // its momentum and a returned gesture decelerates before coming back.
                    val initialVelocity =
                        if (target >= machine.progress) {
                            abs(velocityPagesPerSecond)
                        } else {
                            -abs(velocityPagesPerSecond)
                        }
                    progressAnimator.animateTo(
                        targetValue = target,
                        animationSpec =
                            spring(
                                dampingRatio = SETTLE_DAMPING_RATIO,
                                stiffness = SETTLE_STIFFNESS,
                            ),
                        initialVelocity = initialVelocity,
                    ) {
                        // The frame callback is not a suspend lambda; job cancellation already
                        // stops the animation, so the generation token guards stale writes.
                        if (generation == transitionGeneration) {
                            machine.setProgress(value)
                            emitSnapshot()
                        }
                    }
                }
                finishSettle(target, gameCount, commit, generation)
            }
    }

    /**
     * Shared settle completion: commit exactly once when the target reached the page end,
     * otherwise cancel back to the anchor, then park the animator at rest.
     */
    private suspend fun finishSettle(
        target: Float,
        gameCount: Int,
        commit: Boolean,
        generation: Long
    ) {
        if (!currentCoroutineContext().isActive || generation != transitionGeneration) return
        if (commit && target >= 1f) {
            yield()
            if (!currentCoroutineContext().isActive || generation != transitionGeneration) return
            machine.setProgress(1f)
            machine.commitCurrent(gameCount)
        } else if (target <= 0f) {
            machine.cancelTransition()
        }
        progressAnimator.snapTo(0f)
        emitSnapshot()
    }

    private suspend fun animateCurrentProgress(
        target: Float,
        generation: Long
    ) {
        var freeze = if (BuildConfig.DEBUG) freezeOverride?.coerceIn(0f, 1f) else null
        if (freeze != null) {
            while (currentCoroutineContext().isActive && generation == transitionGeneration && freeze != null) {
                if (abs(machine.progress - freeze) > .0001f) {
                    progressAnimator.snapTo(freeze)
                    machine.setProgress(freeze)
                    emitSnapshot()
                    android.util.Log.d(
                        "FocusFreeze",
                        "held progress=$freeze direction=${machine.direction} current=${machine.currentGameIndex}",
                    )
                }
                delay(40)
                freeze = freezeOverride?.coerceIn(0f, 1f)
            }
        }
        val duration = if (reducedMotion) REDUCED_TRANSITION_MILLIS else TRANSITION_MILLIS
        val easing = if (reducedMotion) LinearEasing else LinearOutSlowInEasing
        progressAnimator.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = duration, easing = easing),
        ) {
            // The frame callback is not a suspend lambda; job cancellation already stops
            // the animation, so the generation token is the stale-write guard.
            if (generation == transitionGeneration) {
                machine.setProgress(value)
                emitSnapshot()
            }
        }
    }
}

/**
 * Immutable token captured at the moment a six-second auto-advance timer starts. The timer
 * may only advance if the machine still holds the exact same commit and manual interaction
 * sequences; any tap, drag, commit or cancel invalidates it.
 */
internal data class FocusAutoAdvanceToken(
    val commitSequence: Long,
    val manualInteractionSequence: Long,
)

/**
 * Pure contract for the six-second auto-advance timer. KireiBoxApp drives the timer with
 * [timerKeys] so Compose cancels the stale delay and starts a fresh full interval on any
 * manual interaction, commit or lifecycle change; the controller atomically re-checks the
 * captured [FocusAutoAdvanceToken] when the delay fires.
 */
internal object FocusAutoAdvanceTimerPolicy {
    /** Single source of truth for the interval is [SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS]. */
    val AUTO_ADVANCE_MILLIS: Long get() = SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS

    /** Captures the machine sequences at the moment the timer starts. */
    fun tokenFor(
        commitSequence: Long,
        manualInteractionSequence: Long
    ): FocusAutoAdvanceToken = FocusAutoAdvanceToken(commitSequence, manualInteractionSequence)

    /**
     * LaunchedEffect key contract: every value that must restart a full six-second
     * interval is part of the key set, so a manual tap, drag, commit, idle toggle or
     * lifecycle change cancels the stale delay and starts a fresh timer.
     */
    fun timerKeys(
        galleryIdle: Boolean,
        galleryCardPressed: Boolean,
        currentGameIndex: Int,
        commitSequence: Long,
        manualInteractionSequence: Long,
        gameCount: Int,
        isVisible: Boolean,
        isResumed: Boolean,
        reducedMotion: Boolean,
    ): List<Any> =
        listOf(
            galleryIdle,
            galleryCardPressed,
            currentGameIndex,
            commitSequence,
            manualInteractionSequence,
            gameCount,
            isVisible,
            isResumed,
            reducedMotion,
        )
}

/** Horizontal drag ownership for the focus stage; the vertical pager owns vertical motion. */
internal fun Modifier.focusGalleryDrag(
    enabled: Boolean,
    controller: FocusGalleryController,
    density: Density,
    geometry: FocusGalleryGeometry,
    gameCount: Int,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(controller, geometry, gameCount) {
            var lastDirection = FocusDirection.ADVANCE
            var lastDeltaDp = 0f
            var lastEventMillis = 0L
            var velocityDpPerSecond = 0f
            detectHorizontalDragGestures(
                onDragStart = { },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val deltaDp = with(density) { dragAmount.toDp().value }
                    val now = change.uptimeMillis
                    if (lastEventMillis > 0L && now > lastEventMillis) {
                        val dtSeconds = (now - lastEventMillis) / 1000f
                        velocityDpPerSecond =
                            if (dtSeconds > 0f) {
                                (deltaDp + lastDeltaDp) / 2f / dtSeconds
                            } else {
                                velocityDpPerSecond
                            }
                    }
                    lastDeltaDp = deltaDp
                    lastEventMillis = now
                    lastDirection = if (deltaDp < 0f) FocusDirection.ADVANCE else FocusDirection.BACK
                    controller.updateDrag(deltaDp, lastDirection, geometry.slotDistanceDp, gameCount)
                },
                onDragEnd = {
                    controller.settleDrag(
                        velocityDpPerSecond = velocityDpPerSecond,
                        requestedDirection = lastDirection,
                        slotDistanceDp = geometry.slotDistanceDp,
                        gameCount = gameCount,
                    )
                },
                onDragCancel = {
                    controller.settleDrag(
                        velocityDpPerSecond = 0f,
                        requestedDirection = lastDirection,
                        slotDistanceDp = geometry.slotDistanceDp,
                        gameCount = gameCount,
                    )
                },
            )
        }
    }
