package com.kireibox.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kireibox.designsystem.AppleTypography

private fun adaptiveMetricsFor(
    layout: ImmersiveHomeLayout,
    widthPx: Float,
    heightPx: Float,
    safe: ImmersiveSafeBounds
) = resolveAdaptiveUiMetrics(
    windowWidthDp = widthPx,
    windowHeightDp = heightPx,
    safeLeftDp = safe.leftPx,
    safeTopDp = safe.topPx,
    safeRightDp = widthPx - safe.rightPx,
    safeBottomDp = heightPx - safe.bottomPx,
)

internal fun Modifier.desaturate(saturation: Float): Modifier = immersiveDesaturate(saturation)

internal fun Modifier.homeRect(
    rect: HomeRect,
    density: Density
): Modifier = offset(pxDp(rect.x, density), pxDp(rect.y, density)).size(pxDp(rect.w, density), pxDp(rect.h, density))

/**
 * A text line placed at [rect]'s top-left, as wide as [rect] and at least as tall (r5 · A2). Unlike [homeRect] the
 * height is a floor: a fixed box turned the 1 px a ceiled line height can exceed the rounded box into a clip and an
 * ellipsis-by-height.
 */
internal fun Modifier.homeTextRect(
    rect: HomeRect,
    density: Density
): Modifier = offset(pxDp(rect.x, density), pxDp(rect.y, density)).width(pxDp(rect.w, density)).heightIn(min = pxDp(rect.h, density))

/**
 * 「继续游戏」's style (r5 · A2), the body style it always inherited at the column's size. Compose's default
 * LineHeightStyle (Proportional, Trim.Both) keeps a one-line paragraph at the font's own height — about 1.45 em with
 * the CJK fallback font, taller than the label's box — so TextOverflow.Ellipsis clipped the bottom of the glyphs.
 * Trim.None makes the line exactly [lineHeight]; Proportional keeps the font's baseline ratio inside it.
 */
internal fun immersiveContinueLineStyle(
    fontSize: TextUnit,
    lineHeight: TextUnit
): TextStyle =
    AppleTypography.bodyLarge.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
    )

internal fun pxDp(
    px: Float,
    density: Density
): Dp = with(density) { px.toDp() }

internal fun designSp(
    px: Float,
    density: Density,
    scale: Float
) = (px * scale / density.density).sp

internal fun HomeRect.toPx(density: Density): HomeRect =
    HomeRect(
        x = with(density) { x.dp.toPx() },
        y = with(density) { y.dp.toPx() },
        w = with(density) { w.dp.toPx() },
        h = with(density) { h.dp.toPx() },
    )

internal fun HomeTopChromeSpec.toPx(density: Density): HomeTopChromeSpec =
    copy(
        titleRect = titleRect.toPx(density),
        menuRect = menuRect.toPx(density),
        menuHitRect = menuHitRect.toPx(density),
        titleSizeDp = with(density) { titleSizeDp.dp.toPx() },
        menuDiameterDp = with(density) { menuDiameterDp.dp.toPx() },
        menuIconSizeDp = with(density) { menuIconSizeDp.dp.toPx() },
    )
