package com.kireibox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

enum class SearchRevealPhase {
    HIDDEN,
    DRAGGING,
    SHOWN,
    SETTLING
}

object SearchRevealMath {
    /** Fling velocity (px/s) at which a settle stops trusting the offset threshold. Exact ±800. */
    const val DIRECTIONAL_VELOCITY_THRESHOLD_PX_S = 800f

    /** Settle break-even: an offset at or above half the reveal height stays shown. */
    const val SETTLE_THRESHOLD_FRACTION = 0.5f

    data class Move(val offsetPx: Float, val consumedPx: Float)
    data class FlingSplit(val consumedVelocityY: Float, val residualVelocityY: Float)


    /** Normalizes a reveal height: NaN/negative become 0 and +Infinity becomes a finite bound. */
    fun sanitizeMax(maxOffsetPx: Float): Float = when {
        maxOffsetPx.isNaN() || maxOffsetPx < 0f -> 0f
        maxOffsetPx.isInfinite() -> Float.MAX_VALUE
        else -> maxOffsetPx
    }

    /** Total clamp into [0, max]: NaN -> 0, -Infinity -> 0, +Infinity -> max. */
    fun clampOffset(offsetPx: Float, maxOffsetPx: Float): Float {
        val max = sanitizeMax(maxOffsetPx)
        return when {
            offsetPx.isNaN() -> 0f
            offsetPx.isInfinite() -> if (offsetPx > 0f) max else 0f
            else -> offsetPx.coerceIn(0f, max)
        }
    }

    /** Half the sanitized reveal height: the settle break-even. */
    fun settleThreshold(maxOffsetPx: Float): Float =
        sanitizeMax(maxOffsetPx) * SETTLE_THRESHOLD_FRACTION

    /**
     * One reveal step for a list-unconsumed [deltaPx].
     *
     * Routing contract: mid-list is represented as a 0 delta (nothing consumed);
     * only an unconsumed downward delta at the list start can reveal. Upward
     * deltas first consume the visible reveal height and the caller hands
     * deltaPx - consumedPx onward to the list. NaN/infinite inputs normalize
     * (NaN delta -> no movement) and the offset always stays in [0, max].
     */
    fun move(
        offsetPx: Float,
        maxOffsetPx: Float,
        deltaPx: Float,
        atListStart: Boolean = true,
    ): Move {
        val max = sanitizeMax(maxOffsetPx)
        val old = clampOffset(offsetPx, max)
        val delta = if (deltaPx.isNaN()) 0f else deltaPx
        if (delta > 0f && !atListStart) return Move(old, 0f)
        val next = clampOffset(old + delta, max)
        return Move(next, next - old)
    }

    fun shouldShow(
        offsetPx: Float,
        thresholdPx: Float,
        velocityY: Float,
        keepShown: Boolean
    ): Boolean {
        if (keepShown) return true
        val safeVelocity = velocityY.takeIf(Float::isFinite) ?: 0f
        return when {
            safeVelocity >= DIRECTIONAL_VELOCITY_THRESHOLD_PX_S -> true
            safeVelocity <= -DIRECTIONAL_VELOCITY_THRESHOLD_PX_S -> false
            else -> {
                val safeThreshold = thresholdPx.takeIf(Float::isFinite)
                    ?.coerceAtLeast(0f)
                    ?: Float.MAX_VALUE
                clampOffset(offsetPx, sanitizeMax(maxOf(offsetPx, safeThreshold))) >= safeThreshold
            }
        }
    }

    fun splitUpwardFling(
        velocityY: Float,
        projectedDistancePx: Float,
        remainingCollapsePx: Float
    ): FlingSplit {
        // A non-finite fling measurement consumes nothing and hands nothing onward.
        if (!velocityY.isFinite()) return FlingSplit(0f, 0f)
        val remaining =
            if (remainingCollapsePx.isNaN()) 0f else remainingCollapsePx.coerceAtLeast(0f)
        if (velocityY >= 0f || remaining <= 0f) {
            return FlingSplit(0f, velocityY)
        }
        val projected =
            if (projectedDistancePx.isNaN()) 0f else projectedDistancePx.coerceAtLeast(0f)
        if (projected <= remaining || projected == 0f) {
            return FlingSplit(velocityY, 0f)
        }
        if (projected.isInfinite()) return FlingSplit(0f, velocityY)
        val residualRatio = sqrt(((projected - remaining) / projected).coerceIn(0f, 1f))
        val residual = velocityY * residualRatio
        return FlingSplit(velocityY - residual, residual)
    }
}

/**
 * Single source of truth for the inline library search reveal.
 *
 * Ownership contract: [offsetPx] is the only public state that owns the reveal
 * position, [maxOffsetPx] is configuration and [phase] is read-only interaction
 * state that rests on HIDDEN/SHOWN whenever the offset is on a boundary;
 * [settledTarget] is a private detail of the in-flight spring, not a second
 * public owner. Layout height, alpha and gesture consumption all derive from
 * [offsetPx], so a settled flag can never fight an in-flight animation and put
 * the search field over the fixed title row. Every input is normalized
 * (NaN/±Infinity) and the offset is clamped before it is published.
*/
@Stable
class SearchRevealController(
    private val scope: CoroutineScope,
    initiallyShown: Boolean = false
) {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var maxOffsetPx by mutableFloatStateOf(0f)
        private set
    var phase by mutableStateOf(
        if (initiallyShown) SearchRevealPhase.SHOWN else SearchRevealPhase.HIDDEN
    )
        private set
    private var settledTarget by mutableStateOf(
        if (initiallyShown) SearchRevealPhase.SHOWN else SearchRevealPhase.HIDDEN
    )

    private var initiallyShownPending = initiallyShown
    private var settleJob: Job? = null

    val fraction: Float
        get() = if (maxOffsetPx <= 0f) 0f else (offsetPx / maxOffsetPx).coerceIn(0f, 1f)

    fun configure(maxOffsetPx: Float) {
        val safeMax = SearchRevealMath.sanitizeMax(maxOffsetPx)
        if (this.maxOffsetPx == safeMax) return
        this.maxOffsetPx = safeMax
        offsetPx = when {
            initiallyShownPending -> safeMax
            phase == SearchRevealPhase.SHOWN -> safeMax
            else -> SearchRevealMath.clampOffset(offsetPx, safeMax)
        }
        initiallyShownPending = false
        normalizeBoundary()
    }

    fun beginGesture() {
        settleJob?.cancel()
        settleJob = null
        phase = SearchRevealPhase.DRAGGING
    }

    /** Returns only the distance actually moved by the search field. */
    fun dragBy(deltaPx: Float, atListStart: Boolean = true): Float {
        if (deltaPx == 0f || deltaPx.isNaN() || maxOffsetPx <= 0f) return 0f
        if (phase != SearchRevealPhase.DRAGGING) beginGesture()
        val move = SearchRevealMath.move(offsetPx, maxOffsetPx, deltaPx, atListStart)
        offsetPx = move.offsetPx
        normalizeBoundary()
        return move.consumedPx
    }

    fun settle(
        velocityY: Float = 0f,
        keepShown: Boolean = false
    ) {
        val targetShown = SearchRevealMath.shouldShow(
            offsetPx,
            SearchRevealMath.settleThreshold(maxOffsetPx),
            velocityY,
            keepShown
        )
        animateTo(if (targetShown) maxOffsetPx else 0f)
    }

    fun show() = animateTo(maxOffsetPx)

    fun hide() = animateTo(0f)

    fun snapHidden() {
        settleJob?.cancel()
        settleJob = null
        offsetPx = 0f
        settledTarget = SearchRevealPhase.HIDDEN
        phase = SearchRevealPhase.HIDDEN
    }

    suspend fun collapseForFling(initialVelocityY: Float, reducedMotion: Boolean) {
        settleJob?.cancel()
        if (offsetPx <= 0f) {
            snapHidden()
            return
        }
        if (reducedMotion) {
            snapHidden()
            return
        }
        val job = currentCoroutineContext()[Job]
        settleJob = job
        settledTarget = SearchRevealPhase.HIDDEN
        phase = SearchRevealPhase.SETTLING
        val safeVelocityY = initialVelocityY.takeIf(Float::isFinite) ?: 0f
        val duration = if (abs(safeVelocityY) < 1f) {
            120
        } else {
            ((offsetPx / abs(safeVelocityY)) * 1000f).toInt().coerceIn(55, 140)
        }
        try {
            val animation = Animatable(offsetPx)
            animation.animateTo(
                0f,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            ) {
                offsetPx = SearchRevealMath.clampOffset(value, maxOffsetPx)
            }
            offsetPx = 0f
            phase = SearchRevealPhase.HIDDEN
        } finally {
            if (settleJob === job) settleJob = null
        }
    }

    fun normalizeToNearestStableState() {
        settle()
    }

    private fun animateTo(target: Float) {
        val bounded = SearchRevealMath.clampOffset(target, maxOffsetPx)
        val targetPhase = if (bounded == 0f) SearchRevealPhase.HIDDEN else SearchRevealPhase.SHOWN
        if (phase == SearchRevealPhase.SETTLING && settledTarget == targetPhase) return
        settleJob?.cancel()
        settledTarget = targetPhase
        if (offsetPx == bounded) {
            phase = settledTarget
            return
        }
        phase = SearchRevealPhase.SETTLING
        settleJob = scope.launch {
            val animation = Animatable(offsetPx)
            animation.animateTo(
                bounded,
                animationSpec = tween(
                    durationMillis = if (bounded == 0f) 120 else 160,
                    easing = FastOutSlowInEasing
                )
            ) {
                offsetPx = SearchRevealMath.clampOffset(value, maxOffsetPx)
            }
            offsetPx = bounded
            phase = settledTarget
        }
    }

    private fun normalizeBoundary() {
        offsetPx = SearchRevealMath.clampOffset(offsetPx, maxOffsetPx)
        when {
            offsetPx <= 0f -> {
                offsetPx = 0f
                settledTarget = SearchRevealPhase.HIDDEN
                phase = SearchRevealPhase.HIDDEN
            }
            offsetPx >= maxOffsetPx -> {
                offsetPx = maxOffsetPx
                settledTarget = SearchRevealPhase.SHOWN
                phase = SearchRevealPhase.SHOWN
            }
        }
    }

}
