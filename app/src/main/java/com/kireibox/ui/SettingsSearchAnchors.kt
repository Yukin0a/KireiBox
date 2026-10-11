package com.kireibox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.MotionMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Settings search navigation (r4 · S2): a tapped result opens its page with a one-shot
// SettingsSearchTarget; the row registered under that key (SettingsAnchor) scrolls itself into view
// below the top bar and pulses an accentContainer highlight (~1.2 s; static under reduced motion).
// Nothing is kept in a registry: each anchor only compares its own key with the pending target, and
// the highlight lives and dies with the row.

/** Where a search result leads: [page], then the row registered as [anchor]; [expandAdvanced] opens 「高级选项」 first. */
@Immutable
internal data class SettingsSearchTarget(
    val page: KireiSettingsPage,
    val anchor: String?,
    val expandAdvanced: Boolean = false,
)

/**
 * The target of a result. Immersive-only settings do not exist while the home uses 组件模式, so
 * they lead to 首页样式, the switch that brings them back.
 */
internal fun settingsSearchTarget(
    entry: SettingsSearchEntry,
    homeMode: HomePresentationMode,
): SettingsSearchTarget {
    val scope = entry.location.scope
    return if (scope != SettingsSearchScope.ALWAYS && homeMode == HomePresentationMode.COMPONENTS) {
        SettingsSearchTarget(KireiSettingsPage.INTERFACE, SettingsSearchIndex.HOME_STYLE_ANCHOR)
    } else {
        SettingsSearchTarget(entry.page, entry.anchor, expandAdvanced = scope == SettingsSearchScope.IMMERSIVE_ADVANCED)
    }
}

/** A vertical span in root pixels. */
internal data class SettingsRowSpan(
    val top: Float,
    val bottom: Float,
) {
    val height: Float get() = bottom - top
}

internal object SettingsRevealMath {
    /** A row that has to move lands about this far down the visible band (not glued under the bar). */
    const val COMFORT_FRACTION = .3f

    /**
     * The scroll value that shows [row] inside [band] (between the top bar and the dock), or null
     * when it already sits fully inside it, so a visible row is highlighted without moving the page.
     */
    fun scrollTarget(
        row: SettingsRowSpan,
        band: SettingsRowSpan,
        scroll: Int,
        maxScroll: Int,
    ): Int? {
        if (row.top >= band.top && row.bottom <= band.bottom) return null
        val room = (band.height - row.height).coerceAtLeast(0f)
        val desiredTop = band.top + room * COMFORT_FRACTION
        return (scroll + row.top - desiredTop).roundToInt().coerceIn(0, maxScroll.coerceAtLeast(0)).takeIf { it != scroll }
    }
}

/** Highlight timing (spec §7 durations): fade in, hold, fade out ≈ 1.2 s; reduced motion holds a static tint as long. */
internal object SettingsAnchorMotion {
    /** Lets the page's slide-in finish before the scroll starts. */
    const val ARRIVAL_DELAY_MS = 220L
    const val SCROLL_MS = 360
    const val PULSE_IN_MS = 200
    const val PULSE_HOLD_MS = 350L
    const val PULSE_OUT_MS = 650
    const val STATIC_HOLD_MS = 1_200L

    /** A target whose row never appears (a conditional row) is dropped after this. */
    const val STALE_TARGET_MS = 2_500L

    /** Frames to wait for expanding sections to settle before measuring. */
    const val MAX_SETTLE_FRAMES = 45
}

/** Root-pixel edges of the band a found row must end up in: [topPx] under the compact bar, [bottomPx] above the dock. */
@Immutable
internal data class SettingsAnchorInsets(
    val topPx: Float,
    val bottomPx: Float,
)

/** What one settings page gives its anchors: which page it is, the pending target, its scroll state and the band insets. */
@Stable
internal class SettingsAnchorHost(
    val page: KireiSettingsPage,
    val reveal: SettingsRevealState,
    val scroll: ScrollState,
    val colors: KireiColors,
    val insets: SettingsAnchorInsets,
)

internal val LocalSettingsAnchorHost = compositionLocalOf<SettingsAnchorHost?> { null }

/** The anchor host of a page with an id (sub-pages); also drops a target whose row never shows up. */
@Composable
internal fun rememberSettingsAnchorHost(
    page: KireiSettingsPage?,
    context: SettingsPageContext,
    scroll: ScrollState,
    frame: SettingsFrame,
): SettingsAnchorHost? {
    if (page == null) return null
    val density = LocalDensity.current
    val insets =
        with(density) {
            SettingsAnchorInsets(topPx = (frame.top + KireiTopBarDefaults.CompactHeight + KireiSpacing.xs).toPx(), bottomPx = (frame.bottom + KireiSpacing.xs).toPx())
        }
    val target = context.reveal.searchTarget
    LaunchedEffect(target) {
        if (target?.page == page) {
            delay(SettingsAnchorMotion.STALE_TARGET_MS)
            context.reveal.consumeSearchTarget(target)
        }
    }
    return remember(page, context.reveal, scroll, context.colors, insets) { SettingsAnchorHost(page, context.reveal, scroll, context.colors, insets) }
}

/** The layout node of one anchor, read when its row has to be scrolled into view. */
private class SettingsAnchorPlacement {
    var coordinates: LayoutCoordinates? = null

    fun span(): SettingsRowSpan? =
        coordinates?.takeIf { it.isAttached }?.let { node ->
            val top = node.positionInRoot().y
            SettingsRowSpan(top, top + node.size.height)
        }

    fun rootHeight(): Float = coordinates?.takeIf { it.isAttached }?.findRootCoordinates()?.size?.height?.toFloat() ?: 0f
}

/**
 * Registers [content] as the search anchor [key] on the current settings page: when a search
 * result targets it, it scrolls into view and pulses the highlight behind the row. Outside a page
 * with an anchor host (the root, the 快速设置 sheet) it only lays out [content].
 */
@Composable
internal fun SettingsAnchor(
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val host = LocalSettingsAnchorHost.current
    val highlight = remember { Animatable(0f) }
    val placement = remember { SettingsAnchorPlacement() }
    if (host != null) SettingsAnchorReveal(host, key, placement, highlight)
    val colors = host?.colors
    val drawn = if (colors == null) Modifier else Modifier.drawBehind { drawRowHighlight(colors, highlight.value) }
    Box(modifier.onPlaced { placement.coordinates = it }.then(drawn)) { content() }
}

/**
 * A whole list group as one anchor (待确认封面 / 已设置封面: its rows are covers, not settings). The group
 * (caption to footer) scrolls into view; [content] receives the wash for the group's card — pass it to
 * `KireiListGroupScope.cardModifier` — so the tint covers the rows only, not the caption or the footer.
 */
@Composable
internal fun SettingsGroupAnchor(
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable (cardHighlight: Modifier) -> Unit,
) {
    val host = LocalSettingsAnchorHost.current
    val highlight = remember { Animatable(0f) }
    val placement = remember { SettingsAnchorPlacement() }
    if (host != null) SettingsAnchorReveal(host, key, placement, highlight)
    val colors = host?.colors
    val wash = if (colors == null) Modifier else Modifier.drawWithContent { drawGroupHighlight(colors, highlight.value) }
    Box(modifier.onPlaced { placement.coordinates = it }) { content(wash) }
}

/** Takes the pending target when it names [key] on this page and runs the reveal in the row's own scope. */
@Composable
private fun SettingsAnchorReveal(
    host: SettingsAnchorHost,
    key: String,
    placement: SettingsAnchorPlacement,
    highlight: Animatable<Float, AnimationVector1D>,
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val scope = rememberCoroutineScope()
    val targeted by remember(host, key) { derivedStateOf { host.reveal.searchTarget?.let { it.page == host.page && it.anchor == key } == true } }
    LaunchedEffect(targeted) {
        if (targeted) {
            host.reveal.consumeSearchTarget()
            // Not in this effect: consuming the target restarts it. The row's scope ends with the row.
            scope.launch { host.revealAnchor(placement, highlight, reducedMotion) }
        }
    }
}

private suspend fun SettingsAnchorHost.revealAnchor(
    placement: SettingsAnchorPlacement,
    highlight: Animatable<Float, AnimationVector1D>,
    reducedMotion: Boolean,
) {
    if (!reducedMotion) delay(SettingsAnchorMotion.ARRIVAL_DELAY_MS)
    val row = awaitSettledSpan(placement) ?: return
    val band = SettingsRowSpan(insets.topPx, placement.rootHeight() - insets.bottomPx)
    SettingsRevealMath.scrollTarget(row, band, scroll.value, scroll.maxValue)?.let { target ->
        if (reducedMotion) scroll.scrollTo(target) else scroll.animateScrollTo(target, tween(SettingsAnchorMotion.SCROLL_MS, easing = FastOutSlowInEasing))
    }
    pulseSettingsHighlight(highlight, reducedMotion)
}

/** The anchor's span once it (and the page's scroll range) stopped moving: expanding sections settle first. */
private suspend fun SettingsAnchorHost.awaitSettledSpan(placement: SettingsAnchorPlacement): SettingsRowSpan? {
    var previous: Pair<SettingsRowSpan, Int>? = null
    repeat(SettingsAnchorMotion.MAX_SETTLE_FRAMES) {
        withFrameNanos { }
        val current = placement.span()?.let { it to scroll.maxValue }
        if (current != null && current == previous) return current.first
        previous = current
    }
    return previous?.first
}

/** Fade in, hold, fade out; under reduced motion a static tint for the same time. */
internal suspend fun pulseSettingsHighlight(
    highlight: Animatable<Float, AnimationVector1D>,
    reducedMotion: Boolean,
) {
    if (reducedMotion) {
        highlight.snapTo(1f)
        delay(SettingsAnchorMotion.STATIC_HOLD_MS)
        highlight.snapTo(0f)
    } else {
        highlight.animateTo(1f, tween(SettingsAnchorMotion.PULSE_IN_MS))
        delay(SettingsAnchorMotion.PULSE_HOLD_MS)
        highlight.animateTo(0f, tween(SettingsAnchorMotion.PULSE_OUT_MS))
    }
}

/** Behind the row: the same accentContainer tint as a prominent row, clipped by the group card. */
private fun DrawScope.drawRowHighlight(
    colors: KireiColors,
    alpha: Float,
) {
    if (alpha > 0f) drawRect(colors.accentContainer, alpha = alpha)
}

/** Over a group's card (its rows are opaque): an accentContainer wash, rounded by the card's own lg clip. */
private fun ContentDrawScope.drawGroupHighlight(
    colors: KireiColors,
    alpha: Float,
) {
    drawContent()
    if (alpha > 0f) drawRect(colors.accentContainer, alpha = alpha)
}
