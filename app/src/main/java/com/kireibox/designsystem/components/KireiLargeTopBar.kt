package com.kireibox.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiSpacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Optional leading (back) and trailing (actions) content of a [KireiLargeTopBar]. With a [haze]
 * state (API 31+, transparency not reduced) the collapsed bar blurs the content scrolling under it.
 * [topInset] is the space above the compact bar (null = the status-bar inset; pages that already
 * know their safe frame pass it so cutouts and hidden status bars are handled the same way), and
 * [horizontalInset] moves the slots in from the screen edges so they line up with the content.
 */
internal data class KireiTopBarSlots(
    val navigation: (@Composable () -> Unit)? = null,
    val actions: (@Composable RowScope.() -> Unit)? = null,
    val haze: HazeState? = null,
    val topInset: Dp? = null,
    val horizontalInset: Dp = 0.dp,
)

internal object KireiTopBarDefaults {
    /** Compact bar height below the status bar. */
    val CompactHeight = 52.dp

    /** Scroll distance over which the large title hands over to the compact title. */
    val CollapseDistance = 52.dp

    /** Horizontal room kept free for the slots so the centered title never runs under them. */
    val SlotWidth = 64.dp

    val BlurRadius = 24.dp

    /** 0 = large title fully visible, 1 = compact centered title + glass bar. */
    fun collapseFraction(
        scrolledPx: Float,
        density: Density,
    ): Float = with(density) { (scrolledPx / CollapseDistance.toPx()).coerceIn(0f, 1f) }

    /**
     * A page that can scroll less than [CollapseDistance] (扫描目录, 关于) collapses over the
     * [maxScrollPx] it has, so its end always shows the compact title instead of stopping halfway
     * with both titles half visible.
     */
    fun collapseFraction(
        scrolledPx: Float,
        maxScrollPx: Float,
        density: Density,
    ): Float {
        val distance = with(density) { CollapseDistance.toPx() }
        val span = if (maxScrollPx > 0f) minOf(distance, maxScrollPx) else distance
        return (scrolledPx / span).coerceIn(0f, 1f)
    }

    fun collapseFraction(
        state: ScrollState,
        density: Density,
    ): Float = collapseFraction(state.value.toFloat(), state.maxValue.toFloat(), density)

    /** The large title fades out over the first half of the collapse... */
    fun largeTitleAlpha(fraction: Float): Float = (1f - fraction * 2f).coerceIn(0f, 1f)

    /** ...and the compact title fades in over the second half, so the two never show together. */
    fun compactTitleAlpha(fraction: Float): Float = (fraction * 2f - 1f).coerceIn(0f, 1f)

    /** Lists collapse fully once the first item (the large title) has scrolled away. */
    fun collapseFraction(
        state: LazyListState,
        density: Density,
    ): Float = if (state.firstVisibleItemIndex > 0) 1f else collapseFraction(state.firstVisibleItemScrollOffset.toFloat(), density)
}

/** The 34sp large title (spec §6 displayLarge); place it as the first item of the scrolling content. */
@Composable
internal fun KireiLargeTitle(
    title: String,
    colors: KireiColors,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        color = colors.text,
        style = AppleTypography.displayLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().padding(top = KireiSpacing.xxs, bottom = KireiSpacing.xs).semantics { heading() },
    )
}

/**
 * Spec §8 large-title bar, drawn over scrolling content: while [collapse] is 0 only the slots
 * show; as the content scrolls the frosted bar (glass token over Haze, opaque surfaceOverlay
 * without it) and its hairline fade in and the headlineMedium title appears centered once the
 * large title has faded (second half of the collapse). [collapse] is read in draw/effect scopes so
 * scrolling never recomposes the bar.
 */
@Composable
internal fun KireiLargeTopBar(
    title: String,
    collapse: () -> Float,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    slots: KireiTopBarSlots = KireiTopBarSlots(),
) {
    val haze = slots.haze
    val bar =
        if (haze != null) {
            // Frosted glass (spec §5 e3 glass token); the block re-runs when collapse() changes.
            Modifier.hazeEffect(haze) {
                alpha = collapse()
                blurRadius = KireiTopBarDefaults.BlurRadius
                noiseFactor = 0f
                backgroundColor = colors.background
                tints = listOf(HazeTint(colors.glass))
            }
        } else {
            // Without blur a see-through bar would muddle the content: use the opaque e3 fallback.
            Modifier.drawBehind { drawRect(colors.surfaceOverlay, alpha = collapse()) }
        }
    Box(
        modifier.fillMaxWidth()
            .then(bar)
            .drawBehind {
                val fraction = collapse()
                if (fraction <= 0f) return@drawBehind
                val stroke = KireiElevation.strokeWidth.toPx()
                drawLine(colors.stroke, Offset(0f, size.height - stroke / 2f), Offset(size.width, size.height - stroke / 2f), stroke, alpha = fraction)
            }
            .then(slots.topInset?.let { Modifier.padding(top = it) } ?: Modifier.statusBarsPadding())
            .padding(horizontal = slots.horizontalInset)
            .height(KireiTopBarDefaults.CompactHeight),
    ) {
        slots.navigation?.let { navigation ->
            Box(Modifier.align(Alignment.CenterStart).padding(start = KireiSpacing.xxs)) { navigation() }
        }
        Text(
            title,
            color = colors.text,
            style = AppleTypography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.align(Alignment.Center)
                    .padding(horizontal = KireiTopBarDefaults.SlotWidth)
                    .graphicsLayer { alpha = KireiTopBarDefaults.compactTitleAlpha(collapse()) }
                    // The large title is the page heading; the compact copy is visual only.
                    .clearAndSetSemantics {},
        )
        slots.actions?.let { actions ->
            Row(Modifier.align(Alignment.CenterEnd).padding(end = KireiSpacing.xxs), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
