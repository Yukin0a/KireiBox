package com.kireibox.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.yuki.yukihub.model.Game
import kotlin.math.ceil
import kotlin.math.min

/** Fixed inputs of the play button: everything except the per-frame pose and reveal. */
internal data class ImmersivePlayButtonLook(
    val density: Density,
    val scale: Float,
    /** Spoken name: 「开始游戏 <title>」. */
    val description: String,
    val palette: ImmersivePalette,
)

/**
 * Spoken name of the play button for [title]. A plain verb + the game, so TalkBack users hear what
 * the button starts (the card itself reads 「<title>，已选中」).
 */
internal fun immersivePlayButtonDescription(title: String?): String = "开始游戏 ${title.orEmpty()}".trimEnd()

/**
 * Reveal progress of the portrait play button: springs to 1 (AppleMotion.Spring.Emphasized) once
 * the gallery has settled and retracts with the quick press spring when a drag starts; reduced
 * motion snaps (an instant crossfade, see [ImmersivePlayButtonMotion]).
 */
@Composable
internal fun rememberImmersivePlayButtonReveal(
    settled: Boolean,
    reducedMotion: Boolean,
): State<Float> =
    animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec =
            when {
                reducedMotion -> snap()
                settled -> AppleMotion.Spring.Emphasized
                else -> AppleMotion.Spring.Press
            },
        label = "immersivePlayReveal",
    )

/**
 * The play button. Its geometry is the resolved [ImmersivePlayButtonPose] (portrait: `layout.play`
 * carried on the hero card; landscape: the static `layout.play` pill). The outer layer applies the
 * card's own rotation around the card's center, so the button turns and slides with the card; the
 * inner layer scales it around its own center for the settle reveal. Only a settled, fully revealed
 * button is clickable; swipes keep starting on the card (the gesture regions exclude `layout.play`).
 */
@Composable
internal fun ImmersivePlayButton(
    pose: ImmersivePlayButtonPose,
    look: ImmersivePlayButtonLook,
    reveal: State<Float>,
    onClick: () -> Unit,
) {
    val rect = pose.rect
    val density = look.density
    val palette = look.palette
    val fx = LocalImmersiveFx.current
    val parallax = LocalImmersiveParallax.current
    val depth = fx.cardDepth.cardDepthScale()
    val parallaxPx = if (fx.gyroParallax && pose.ridesWithCard) IMMERSIVE_PARALLAX_CARD_DP * density.density else 0f
    val shadowPx = with(density) { KireiElevation.e2Shadow.toPx() }
    Box(
        Modifier.offset(pxDp(rect.x, density), pxDp(rect.y, density))
            .size(pxDp(rect.w, density), pxDp(rect.h, density))
            .zIndex(9f)
            .graphicsLayer {
                rotationZ = pose.rotationZ
                rotationY = pose.rotationY * depth
                cameraDistance = IMMERSIVE_PLAY_CAMERA_DISTANCE * density.density
                transformOrigin = TransformOrigin(pose.pivotFractionX, pose.pivotFractionY)
                translationX = parallax.x * parallaxPx
                translationY = parallax.y * parallaxPx
                alpha = pose.alpha
            },
    ) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer {
                    val scale =
                        if (pose.ridesWithCard) ImmersivePlayButtonMotion.revealScale(reveal.value) * ImmersivePlayButtonMotion.presenceScale(pose.presence) else 1f
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = shadowPx
                    shape = KireiShapes.full
                    // No outline clip: the tilted circle is filled as an anti-aliased vector shape below, so its
                    // edge does not depend on how the GPU clips a rotated layer.
                    clip = false
                    ambientShadowColor = KireiElevation.shadowColor(KireiElevation.e2ShadowAlpha(palette.isDark))
                    spotShadowColor = palette.accent
                }
                .background(Brush.linearGradient(listOf(palette.accentBright, palette.accent)), KireiShapes.full)
                .border(pxDp(look.scale, density), palette.glassBorder, KireiShapes.full)
                .clickable(enabled = pose.alpha >= 1f || !pose.ridesWithCard, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = look.description }
                .testTag("immersive-play"),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(pxDp(min(rect.w, rect.h) * .46f, density))) {
                val path =
                    Path().apply {
                        moveTo(size.width * .30f, size.height * .18f)
                        lineTo(size.width * .78f, size.height * .50f)
                        lineTo(size.width * .30f, size.height * .82f)
                        close()
                    }
                drawPath(path, palette.playInk)
            }
        }
    }
}

/** Same camera as the cards (ImmersiveHomeCards), so the button's tilt projects exactly like its card. */
private const val IMMERSIVE_PLAY_CAMERA_DISTANCE = 12f

/**
 * Where the play-time label goes. Portrait: a centred pill of at most [maxWidthPx]. Landscape (r4 · I2):
 * the measured area — [maxWidthPx] is the room up to the information column's right edge, [heightPx] the
 * height the label needs (one or two lines); [centeredOnPlay] when it sits beside the play pill;
 * [compactLabel] when only the amount fits (「12 小时 30 分钟」).
 */
internal data class ImmersivePlayTimeAnchor(
    val xPx: Float,
    val yPx: Float,
    val maxWidthPx: Float,
    val heightPx: Float = 0f,
    val centeredOnPlay: Boolean = false,
    val compactLabel: Boolean = false,
)

internal data class ImmersivePlayTimeVisualSpec(
    val fontSizeSp: Float,
    val lineHeightSp: Float,
    val iconSizeDp: Float,
    val horizontalPaddingDp: Float,
    val verticalPaddingDp: Float,
    val contentGapDp: Float,
    val minimumHeightDp: Float,
    val anchorGapDp: Float,
)

/**
 * Keeps the play-time affordance readable in dp/sp instead of shrinking design pixels by the
 * device density. Compact phones retain a 14sp floor; larger windows grow only slightly so the
 * control remains secondary to the cover and launch action.
 */
internal fun resolveImmersivePlayTimeVisualSpec(
    orientation: HomeOrientation,
    adaptiveScale: Float,
    fontScale: Float = 1f,
): ImmersivePlayTimeVisualSpec {
    val sizeScale = adaptiveScale.takeIf(Float::isFinite)?.coerceIn(1f, 1.15f) ?: 1f
    val accessibilityScale = fontScale.takeIf(Float::isFinite)?.coerceAtLeast(.75f) ?: 1f
    val portrait = orientation == HomeOrientation.PORTRAIT
    val fontSize = (if (portrait) 14.5f else 14f) * sizeScale
    val lineHeight = fontSize * 1.25f
    val verticalPadding = (if (portrait) 8f else 6f) * sizeScale
    val contentHeight = lineHeight * accessibilityScale + verticalPadding * 2f
    val iconSize = (if (portrait) 18f else 16f) * sizeScale
    val baseHeight = (if (portrait) 44f else 36f) * sizeScale
    return ImmersivePlayTimeVisualSpec(
        fontSizeSp = fontSize,
        lineHeightSp = lineHeight,
        iconSizeDp = iconSize,
        horizontalPaddingDp = (if (portrait) 14f else 2f) * sizeScale,
        verticalPaddingDp = verticalPadding,
        contentGapDp = (if (portrait) 8f else 6f) * sizeScale,
        minimumHeightDp = maxOf(baseHeight, contentHeight, iconSize + verticalPadding * 2f),
        anchorGapDp = (if (portrait) 12f else 6f) * sizeScale,
    )
}

internal fun resolveImmersivePlayTimeAnchor(
    orientation: HomeOrientation,
    layout: ImmersiveHomeLayout,
    safeBounds: ImmersiveSafeBounds,
    scale: Float,
    indicatorHeightPx: Float = 42f * scale.coerceAtLeast(.01f),
    gapPx: Float = 12f * scale.coerceAtLeast(.01f),
): ImmersivePlayTimeAnchor? {
    val pagerY = layout.pagerCenters.firstOrNull()?.y ?: return null
    val safeIndicatorHeight =
        indicatorHeightPx.takeIf(Float::isFinite)?.coerceAtLeast(1f)
            ?: return null
    val safeGap = gapPx.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: return null
    return if (orientation == HomeOrientation.PORTRAIT) {
        val center = layout.cardSlots[ImmersiveSlot.CENTER]?.rect ?: return null
        val earliestTop = center.y + center.h + safeGap
        val latestTop = pagerY - safeIndicatorHeight - safeGap
        if (latestTop < earliestTop) return null
        ImmersivePlayTimeAnchor(
            xPx = (safeBounds.leftPx + safeBounds.rightPx) / 2f,
            yPx = earliestTop + (latestTop - earliestTop) * .48f,
            maxWidthPx = (safeBounds.rightPx - safeBounds.leftPx - safeGap.coerceAtLeast(1f) * 2f).coerceAtLeast(1f),
        )
    } else {
        // r4 · I2: without a measured label the landscape label still gets the whole information column
        // below the play pill, or the room beside it — never above the pill, where it covered 「继续游戏」.
        ImmersivePlayTimePlacement.landscape(layout, safeGap) { _, _ -> ImmersiveLabelBox(widthPx = 0f, heightPx = safeIndicatorHeight) }?.anchor()
    }
}

/** The play-time text style: the body style the label always inherited (M3 `bodyLarge`) at the spec's size. */
internal fun immersivePlayTimeTextStyle(spec: ImmersivePlayTimeVisualSpec): TextStyle = AppleTypography.bodyLarge.copy(fontSize = spec.fontSizeSp.sp, lineHeight = spec.lineHeightSp.sp)

/**
 * Landscape play-time placement for the gallery (r4 · I2): the label is measured with the real text layout
 * against the information column, and the gallery's widest labels decide the slot, so the label and the
 * play pill stay put while swiping between games.
 */
@Composable
internal fun rememberImmersiveLandscapePlayTime(
    layout: ImmersiveHomeLayout,
    games: List<Game>,
    spec: ImmersivePlayTimeVisualSpec,
): ImmersiveLandscapePlayTime? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labels = remember(games) { immersivePlayTimeReferenceLabels(games) }
    return remember(layout, labels, spec, density, measurer) {
        val gapPx = with(density) { spec.anchorGapDp.dp.toPx() }
        val shown = ImmersiveLabelForm.entries.associateWith { form -> labels.map { immersivePlayTimeDisplay(it, form) } }
        ImmersivePlayTimePlacement.landscape(layout, gapPx) { maxWidthPx, form -> measurer.playTimeBox(shown.getValue(form), spec, density, maxWidthPx) }
    }
}

/**
 * The widest of [labels] at [maxWidthPx]: icon, gap and padding rounded like Compose lays them out. A label
 * whose part (「已游玩」, 「12 小时」…) is wider than the line does not fit: the line breaker would split the
 * part mid-word, so it counts as cut, like "…".
 */
private fun TextMeasurer.playTimeBox(
    labels: List<String>,
    spec: ImmersivePlayTimeVisualSpec,
    density: Density,
    maxWidthPx: Float,
): ImmersiveLabelBox {
    val chromePx = with(density) { spec.iconSizeDp.dp.roundToPx() + spec.contentGapDp.dp.roundToPx() + spec.horizontalPaddingDp.dp.roundToPx() * 2 }
    val verticalPx = with(density) { spec.verticalPaddingDp.dp.roundToPx() * 2 }
    val floorPx = with(density) { maxOf(spec.minimumHeightDp.dp.toPx(), spec.iconSizeDp.dp.toPx()) }
    val constraints = Constraints(maxWidth = (maxWidthPx.toInt() - chromePx).coerceAtLeast(1))
    val style = immersivePlayTimeTextStyle(spec)
    return labels
        .map { label ->
            val text = measure(label, style, TextOverflow.Ellipsis, maxLines = IMMERSIVE_PLAY_TIME_MAX_LINES, constraints = constraints)
            val splitsAPart = immersivePlayTimeParts(label).any { part -> measure(part, style, maxLines = 1).size.width > constraints.maxWidth }
            // The widest laid-out line (a wrapped layout is as wide as its limit), so a narrowed pill keeps every pixel it can.
            val widestLine = (0 until text.lineCount).maxOfOrNull { text.getLineRight(it) - text.getLineLeft(it) } ?: 0f
            ImmersiveLabelBox(ceil(widestLine) + chromePx, maxOf(floorPx, (text.size.height + verticalPx).toFloat()), text.hasVisualOverflow || splitsAPart)
        }.reduceOrNull { a, b -> ImmersiveLabelBox(maxOf(a.widthPx, b.widthPx), maxOf(a.heightPx, b.heightPx), a.ellipsized || b.ellipsized) }
        ?: ImmersiveLabelBox(0f, floorPx)
}

@Composable
internal fun ImmersivePlayTimeIndicator(
    game: Game,
    orientation: HomeOrientation,
    anchor: ImmersivePlayTimeAnchor,
    density: Density,
    visualSpec: ImmersivePlayTimeVisualSpec,
    palette: ImmersivePalette,
    reducedMotion: Boolean,
    onClick: () -> Unit,
) {
    val target = game.id to LibraryOverviewPolicy.immersivePlayTime(game.totalPlayTime)
    if (orientation == HomeOrientation.PORTRAIT) {
        Box(
            Modifier.offset(
                pxDp(anchor.xPx - anchor.maxWidthPx / 2f, density),
                pxDp(anchor.yPx, density),
            ).width(pxDp(anchor.maxWidthPx, density))
                .height(visualSpec.minimumHeightDp.dp)
                .zIndex(9f),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = target,
                animationSpec = tween(if (reducedMotion) 0 else 150),
                label = "immersivePlayTime",
            ) { (_, label) ->
                Row(
                    Modifier.clip(CircleShape)
                        .background(palette.glass)
                        .border(
                            1.dp,
                            immersivePlayTimeBorderColor(palette, orientation, game.totalPlayTime),
                            CircleShape,
                        )
                        .clickable(onClick = onClick)
                        .heightIn(min = visualSpec.minimumHeightDp.dp)
                        .widthIn(max = pxDp(anchor.maxWidthPx, density))
                        .padding(
                            horizontal = visualSpec.horizontalPaddingDp.dp,
                            vertical = visualSpec.verticalPaddingDp.dp,
                        )
                        .testTag("immersive-play-time")
                        .semantics {
                            contentDescription = "查看${game.title.orEmpty()}游玩记录，$label"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(visualSpec.contentGapDp.dp),
                ) {
                    Icon(
                        Icons.Rounded.Schedule,
                        null,
                        tint = palette.accentBright,
                        modifier = Modifier.size(visualSpec.iconSizeDp.dp),
                    )
                    Text(
                        label,
                        color = palette.secondary,
                        fontSize = visualSpec.fontSizeSp.sp,
                        lineHeight = visualSpec.lineHeightSp.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else {
        // r4 · I2: the label owns the measured area — the whole information column below the play pill,
        // or the room beside it (centred on the pill) — and wraps to a second line before it ellipsizes.
        val rowMinHeight = if (anchor.centeredOnPlay) maxOf(visualSpec.minimumHeightDp.dp, pxDp(anchor.heightPx, density)) else visualSpec.minimumHeightDp.dp
        val form = if (anchor.compactLabel) ImmersiveLabelForm.COMPACT else ImmersiveLabelForm.FULL
        Crossfade(
            targetState = target,
            animationSpec = tween(if (reducedMotion) 0 else 150),
            label = "immersivePlayTime",
            modifier =
                Modifier.offset(pxDp(anchor.xPx, density), pxDp(anchor.yPx, density))
                    .width(pxDp(anchor.maxWidthPx, density))
                    .heightIn(min = rowMinHeight)
                    .zIndex(9f),
        ) { (_, label) ->
            Row(
                Modifier.heightIn(min = rowMinHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(visualSpec.contentGapDp.dp),
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    null,
                    tint = palette.accentBright,
                    modifier = Modifier.size(visualSpec.iconSizeDp.dp),
                )
                Text(
                    immersivePlayTimeDisplay(label, form),
                    color = palette.secondary,
                    style = immersivePlayTimeTextStyle(visualSpec),
                    maxLines = IMMERSIVE_PLAY_TIME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable(onClick = onClick)
                            .padding(
                                horizontal = visualSpec.horizontalPaddingDp.dp,
                                vertical = visualSpec.verticalPaddingDp.dp,
                            )
                            .testTag("immersive-play-time")
                            .semantics {
                                contentDescription = "查看${game.title.orEmpty()}游玩记录，$label"
                            },
                )
            }
        }
    }
}

internal fun immersivePlayTimeBorderColor(
    palette: ImmersivePalette,
    orientation: HomeOrientation,
    totalPlayTime: Long,
): Color =
    if (!palette.isDark && orientation == HomeOrientation.PORTRAIT && totalPlayTime <= 0L) {
        palette.secondary.copy(alpha = .38f)
    } else {
        palette.glassBorder
    }

@Composable
internal fun ImmersivePager(
    games: List<Game>,
    currentIndex: Int,
    orientation: HomeOrientation,
    layout: ImmersiveHomeLayout,
    density: Density,
    scale: Float,
    palette: ImmersivePalette,
    onSelect: (Int) -> Unit
) {
    val indexes = ImmersivePagerPolicy.visibleIndexes(games.size, currentIndex)
    if (indexes.isEmpty()) return
    val visibleCenters = ImmersivePagerPolicy.visibleCenters(layout.pagerCenters, indexes.size)
    indexes.forEachIndexed { position, index ->
        val point = visibleCenters.getOrNull(position) ?: return@forEachIndexed
        val width = if (orientation == HomeOrientation.PORTRAIT) 13f else 28f
        val height = if (orientation == HomeOrientation.PORTRAIT) 13f else 5f
        Box(
            Modifier.offset(
                pxDp(point.x - width * scale / 2f, density),
                pxDp(point.y - height * scale / 2f, density)
            ).size(
                pxDp(width * scale, density),
                pxDp(height * scale, density)
            ).clip(
                if (orientation == HomeOrientation.PORTRAIT) CircleShape else RoundedCornerShape(99.dp)
            ).background(if (index == currentIndex) palette.accentBright else palette.primary.copy(alpha = .25f)).clickable {
                onSelect(index)
            }.zIndex(8f).semantics { contentDescription = "切换到第${index + 1}项" }
        )
    }
}
