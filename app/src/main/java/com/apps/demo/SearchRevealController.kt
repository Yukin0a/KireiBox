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
    data class Move(val offsetPx: Float, val consumedPx: Float)
    data class FlingSplit(val consumedVelocityY: Float, val residualVelocityY: Float)

    fun move(offsetPx: Float, maxOffsetPx: Float, deltaPx: Float): Move {
        val old = offsetPx.coerceIn(0f, maxOffsetPx.coerceAtLeast(0f))
        val next = (old + deltaPx).coerceIn(0f, maxOffsetPx.coerceAtLeast(0f))
        return Move(next, next - old)
    }

    fun shouldShow(
        offsetPx: Float,
        thresholdPx: Float,
        velocityY: Float,
        keepShown: Boolean
    ): Boolean = keepShown ||
        velocityY > 900f ||
        (velocityY >= -900f && offsetPx >= thresholdPx)

    fun splitUpwardFling(
        velocityY: Float,
        projectedDistancePx: Float,
        remainingCollapsePx: Float
    ): FlingSplit {
        if (velocityY >= 0f || remainingCollapsePx <= 0f) {
            return FlingSplit(0f, velocityY)
        }
        val projected = projectedDistancePx.coerceAtLeast(0f)
        if (projected <= remainingCollapsePx || projected == 0f) {
            return FlingSplit(velocityY, 0f)
        }
        val residualRatio = sqrt(((projected - remainingCollapsePx) / projected).coerceIn(0f, 1f))
        val residual = velocityY * residualRatio
        return FlingSplit(velocityY - residual, residual)
    }
}

/**
 * Single source of truth for the inline library search reveal.
 *
 * Layout height, alpha and gesture consumption must all derive from [offsetPx].
 * This prevents a settled boolean from fighting an in-flight spring and putting
 * the search field over the fixed title row.
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
    var settledTarget by mutableStateOf(
        if (initiallyShown) SearchRevealPhase.SHOWN else SearchRevealPhase.HIDDEN
    )
        private set

    private var initiallyShownPending = initiallyShown
    private var settleJob: Job? = null

    val fraction: Float
        get() = if (maxOffsetPx <= 0f) 0f else (offsetPx / maxOffsetPx).coerceIn(0f, 1f)

    fun configure(maxOffsetPx: Float) {
        val safeMax = maxOffsetPx.coerceAtLeast(0f)
        if (this.maxOffsetPx == safeMax) return
        this.maxOffsetPx = safeMax
        offsetPx = when {
            initiallyShownPending -> safeMax
            phase == SearchRevealPhase.SHOWN -> safeMax
            else -> offsetPx.coerceIn(0f, safeMax)
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
    fun dragBy(deltaPx: Float): Float {
        if (deltaPx == 0f || maxOffsetPx <= 0f) return 0f
        if (phase != SearchRevealPhase.DRAGGING) beginGesture()
        val move = SearchRevealMath.move(offsetPx, maxOffsetPx, deltaPx)
        offsetPx = move.offsetPx
        normalizeBoundary()
        return move.consumedPx
    }

    fun settle(
        thresholdPx: Float,
        velocityY: Float = 0f,
        keepShown: Boolean = false
    ) {
        val targetShown = SearchRevealMath.shouldShow(
            offsetPx,
            thresholdPx,
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
        val duration = if (abs(initialVelocityY) < 1f) {
            120
        } else {
            ((offsetPx / abs(initialVelocityY)) * 1000f).toInt().coerceIn(55, 140)
        }
        try {
            val animation = Animatable(offsetPx)
            animation.animateTo(
                0f,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            ) {
                offsetPx = value.coerceIn(0f, maxOffsetPx)
            }
            offsetPx = 0f
            phase = SearchRevealPhase.HIDDEN
        } finally {
            if (settleJob === job) settleJob = null
        }
    }

    fun normalizeToNearestStableState() {
        settle(maxOffsetPx * .5f)
    }

    private fun animateTo(target: Float) {
        val bounded = target.coerceIn(0f, maxOffsetPx)
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
                offsetPx = value.coerceIn(0f, maxOffsetPx)
            }
            offsetPx = bounded
            phase = settledTarget
        }
    }

    private fun normalizeBoundary() {
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
