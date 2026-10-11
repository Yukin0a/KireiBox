package com.kireibox.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.kireiPressScale
import com.kireibox.designsystem.components.kireiReducedMotion
import com.kireibox.designsystem.components.rememberPressProgress
import com.yuki.yukihub.sync.PlaytimeSyncStatus
import kotlinx.coroutines.delay

private fun syncButtonLabel(tone: SyncButtonTone): String =
    when (tone) {
        SyncButtonTone.RUNNING -> "同步中…"
        SyncButtonTone.SYNCED -> "已同步"
        SyncButtonTone.ATTENTION -> "待确认"
        SyncButtonTone.FAILED -> "同步失败"
        SyncButtonTone.OFF, SyncButtonTone.IDLE -> "同步数据"
    }

/** Fill and ink per tone: the accent while idle or running, the AA tile pairs otherwise. */
private fun syncButtonColors(
    tone: SyncButtonTone,
    colors: KireiColors,
): Pair<Color, Color> {
    val tile =
        when (tone) {
            SyncButtonTone.SYNCED -> KireiTileColor.GREEN
            SyncButtonTone.ATTENTION -> KireiTileColor.ORANGE
            SyncButtonTone.FAILED -> KireiTileColor.RED
            SyncButtonTone.OFF -> KireiTileColor.GRAY
            SyncButtonTone.IDLE, SyncButtonTone.RUNNING -> null
        }
    return tile?.let { it.container(colors.isDark) to it.glyph(colors.isDark) } ?: (colors.accentContainer to colors.accent)
}

/**
 * 同步数据 with its state in color (green synced, orange waiting for the user, red failed, gray not
 * signed in); colors cross-fade, and while a sync runs the icon spins and the fill breathes.
 * Reduced motion keeps only the colors.
 */
@Composable
internal fun PlaytimeSyncButton(
    status: PlaytimeSyncStatus,
    connected: Boolean,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // A sync with little to do ends within one frame, before the running state ever shows: a tap
    // spins for at least MIN_SPIN_MS so it never looks like the button did nothing.
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(tapped) {
        if (tapped) {
            delay(MIN_SPIN_MS)
            tapped = false
        }
    }
    val stateTone = syncButtonTone(status, connected)
    val tone = if (tapped && stateTone != SyncButtonTone.OFF) SyncButtonTone.RUNNING else stateTone
    val reducedMotion = kireiReducedMotion()
    val (fill, ink) = syncButtonColors(tone, colors)
    val colorSpec = tween<Color>(if (reducedMotion) 0 else AppleMotion.Duration.MEDIUM)
    val container by animateColorAsState(fill, colorSpec, label = "syncFill")
    val content by animateColorAsState(ink, colorSpec, label = "syncInk")
    val animate = tone == SyncButtonTone.RUNNING && !reducedMotion
    val transition = rememberInfiniteTransition(label = "syncRunning")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)), label = "syncSpin")
    val breath by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(BREATH_MS), RepeatMode.Reverse), label = "syncBreath")
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val label = syncButtonLabel(tone)
    Box(
        modifier.minimumInteractiveComponentSize()
            .graphicsLayer {
                val scale = kireiPressScale(press)
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = KireiButtonSize.SMALL.height)
            .clip(KireiShapes.full)
            .drawBehind {
                drawRect(container)
                if (animate) drawRect(content, alpha = BREATH_ALPHA * breath)
            }
            .clickable(interaction, indication = null, role = Role.Button) {
                if (stateTone != SyncButtonTone.OFF && stateTone != SyncButtonTone.RUNNING) tapped = true
                onClick()
            }
            .padding(horizontal = KireiButtonSize.SMALL.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
            Icon(Icons.Rounded.Sync, contentDescription = null, tint = content, modifier = Modifier.size(ICON_SIZE).rotate(if (animate) -angle else 0f))
            Text(label, color = content, style = AppleTypography.bodySmallEmphasis, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val ICON_SIZE = 18.dp
private const val SPIN_MS = 1000
private const val MIN_SPIN_MS = 800L
private const val BREATH_MS = 900
private const val BREATH_ALPHA = .14f
