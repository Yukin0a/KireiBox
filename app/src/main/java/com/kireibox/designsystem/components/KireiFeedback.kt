package com.kireibox.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing

/**
 * Empty state (plan §4.2): optional illustration (mascot or icon well), headlineMedium title,
 * secondary bodyMedium message and one primary action, centered.
 */
@Composable
internal fun KireiEmptyState(
    spec: KireiEmptyStateSpec,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    illustration: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = KireiSpacing.xl, vertical = KireiSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
    ) {
        if (illustration != null) {
            Box(Modifier.padding(bottom = KireiSpacing.xs), contentAlignment = Alignment.Center) { illustration() }
        }
        Text(spec.title, color = colors.text, style = AppleTypography.headlineMedium, textAlign = TextAlign.Center)
        spec.message?.let {
            Text(it, color = colors.secondary, style = AppleTypography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (spec.actionLabel != null && onAction != null) {
            KireiButton(
                spec.actionLabel,
                colors,
                Modifier.padding(top = KireiSpacing.sm),
                KireiButtonOptions(size = KireiButtonSize.MEDIUM),
                onAction,
            )
        }
    }
}

/** Shimmer sweep duration; one pass per period. */
private const val SHIMMER_PERIOD_MS = 1_200
private const val DARK_HIGHLIGHT = .07f
private const val LIGHT_HIGHLIGHT = .85f

/**
 * Loading placeholder (cover/text skeleton): surfaceRaised block with a soft diagonal shimmer.
 * Static under reduced motion, so no infinite animation runs.
 */
@Composable
internal fun KireiSkeleton(
    colors: KireiColors,
    modifier: Modifier = Modifier,
    shape: Shape = KireiShapes.md,
) {
    if (kireiReducedMotion()) {
        Box(modifier.clip(shape).background(colors.surfaceRaised))
    } else {
        ShimmerSkeleton(colors, modifier, shape)
    }
}

@Composable
private fun ShimmerSkeleton(
    colors: KireiColors,
    modifier: Modifier,
    shape: Shape,
) {
    val base = colors.surfaceRaised
    val highlight: Color = if (colors.isDark) lerp(base, colors.text, DARK_HIGHLIGHT) else lerp(base, colors.surface, LIGHT_HIGHLIGHT)
    val transition = rememberInfiniteTransition(label = "kireiSkeleton")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "kireiSkeletonSweep",
    )
    Box(
        modifier.clip(shape).drawBehind {
            val band = size.width.coerceAtLeast(size.height)
            val x = -band + (size.width + band * 2f) * sweep
            drawRect(base)
            drawRect(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x - band / 2f, 0f),
                    end = Offset(x + band / 2f, size.height),
                ),
            )
        },
    )
}

/** Rounded accent-on-surface3 progress bar; [progress] = null is indeterminate. */
@Composable
internal fun KireiProgressBar(
    progress: Float?,
    colors: KireiColors,
    modifier: Modifier = Modifier,
) {
    val barModifier = modifier.fillMaxWidth().height(PROGRESS_HEIGHT).clip(KireiShapes.full)
    if (progress == null) {
        LinearProgressIndicator(
            modifier = barModifier,
            color = colors.accent,
            trackColor = colors.surface3,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = barModifier,
            color = colors.accent,
            trackColor = colors.surface3,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private val PROGRESS_HEIGHT = 6.dp

/** Copy of an empty state; [actionLabel] adds the primary button. */
internal data class KireiEmptyStateSpec(
    val title: String,
    val message: String? = null,
    val actionLabel: String? = null,
)
