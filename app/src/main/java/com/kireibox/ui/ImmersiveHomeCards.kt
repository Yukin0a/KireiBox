package com.kireibox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.LocalAppleColors
import com.yuki.yukihub.model.Game
import kotlin.math.roundToInt

/**
 * Cover-internal overlay tokens: always dark in both themes so the lower-left title
 * stays readable over any cover artwork. Outside-card chrome uses ImmersivePalette.
 */
private val CoverOverlayGradientBottom = KireiTokens.Immersive.CoverOverlayBottom
private val CoverOverlayInk = KireiTokens.OnImage.ink

/** Engine label on the always-dark cover overlay: the dark-theme highlight of the app accent. */
private val CoverOverlayAccentBright: Color
    @Composable get() = ImmersivePaletteResolver.resolve(isDark = true, accent = LocalAppleColors.current.accentChoice).accentBright

/**
 * Per-frame card inputs that change while dragging: the blended cover tone and the game whose card
 * has settled in the center (null while dragging or settling). Provided by ImmersiveHome.
 */
internal data class ImmersiveCardContext(
    val tint: Color = Color.Unspecified,
    val settledGameId: Long? = null,
)

internal val LocalImmersiveCardContext = compositionLocalOf { ImmersiveCardContext() }

/**
 * Card inputs for one frame: the cover tones of the outgoing and incoming hero blended by the
 * swipe fraction (the card glow, rim light and sheen follow it), and the settled game id.
 */
internal fun immersiveCardContext(
    left: HeroCoverVisual?,
    right: HeroCoverVisual?,
    fraction: Float,
    settledGameId: Long?,
): ImmersiveCardContext {
    val leftTint = Color(left?.tone?.argb ?: CoverToneExtractor.DEFAULT_FALLBACK_ARGB)
    val rightTint = right?.tone?.argb?.let(::Color) ?: leftTint
    val blendedColor = blendImmersiveColors(leftTint, rightTint, fraction)
    return ImmersiveCardContext(tint = blendedColor, settledGameId = settledGameId)
}

/** Camera distance for the side-card tilt in RenderNode units (8 is the platform default). */
private const val IMMERSIVE_CARD_CAMERA_DISTANCE = 12f

/**
 * Transparent margin (physical px) around a card inside its own offscreen layer. A tilted card is a
 * rotated texture: with the card's straight edges on the texture border the GPU draws that border
 * without anti-aliasing (stair-stepped edges on a 2.5° tilt or a 3D turn). With a margin the border
 * falls on transparent texels, the card's rounded edge is anti-aliased inside the layer in unrotated
 * space and the rotated layer is sampled bilinearly, so the edge stays smooth on every GPU.
 */
private const val IMMERSIVE_CARD_LAYER_MARGIN_PX = 2f

/** The card's rounded rect inside its layer margin: the outline the elevation shadow follows. */
private class ImmersiveCardOutline(
    private val cornerPx: Float,
    private val insetPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline =
        Outline.Rounded(
            RoundRect(insetPx, insetPx, size.width - insetPx, size.height - insetPx, CornerRadius(cornerPx)),
        )
}

@Suppress("LongParameterList")
@Composable
internal fun ImmersiveGameCard(
    game: Game,
    frame: ImmersiveCardFrame,
    slot: ImmersiveSlot,
    orientation: HomeOrientation,
    density: Density,
    scale: Float,
    selected: Boolean,
    titleEndInset: Dp = 0.dp,
    titleRestRect: HomeRect = frame.rect,
    onClick: () -> Unit,
) {
    val fx = LocalImmersiveFx.current
    val cardContext = LocalImmersiveCardContext.current
    val parallax = LocalImmersiveParallax.current
    val cardRadiusDp = ImmersiveSharedSpec.cardRadiusDp(frame.focusFraction)
    val shape = RoundedCornerShape(cardRadiusDp.dp)
    val depth = fx.cardDepth.cardDepthScale()
    val parallaxPx = if (fx.gyroParallax) IMMERSIVE_PARALLAX_CARD_DP * density.density else 0f
    val rimColor = immersiveRimColor(fx, cardContext.tint)
    val cornerPx = with(density) { cardRadiusDp.dp.toPx() }
    // Full physical-pixel stroke: a sub-pixel width made the rim look beaded/pixelated on some
    // screens. Drawn as an anti-aliased vector round-rect (Skia), never a bitmap.
    val strokePx = maxOf(1f, .8f * scale)
    val margin = IMMERSIVE_CARD_LAYER_MARGIN_PX
    val layerRect = HomeRect(frame.rect.x - margin, frame.rect.y - margin, frame.rect.w + 2f * margin, frame.rect.h + 2f * margin)
    val outline = remember(cornerPx, margin) { ImmersiveCardOutline(cornerPx, margin) }
    Box(
        Modifier.homeRect(layerRect, density).graphicsLayer {
            rotationZ = frame.rotationZ
            rotationY = frame.rotationY * depth
            // RenderNode camera units (~72px each); scaled with density so the subtle tilt keeps a
            // consistent, mild perspective on every screen.
            cameraDistance = IMMERSIVE_CARD_CAMERA_DISTANCE * density.density
            translationX = parallax.x * parallaxPx
            translationY = parallax.y * parallaxPx
            alpha = frame.alpha
            shadowElevation = immersiveCardShadowElevationPx(frame.focusFraction, frame.elevationPx)
            // The rounded clip happens inside the layer (below), not on the rotated node.
            this.shape = outline
            clip = false
            compositingStrategy = CompositingStrategy.Offscreen
        }.zIndex(frame.zIndex),
    ) {
        Box(
            Modifier.padding(pxDp(margin, density)).fillMaxSize().clip(shape).drawWithContent {
                drawContent()
                val half = strokePx / 2f
                drawRoundRect(
                    color = rimColor,
                    topLeft = Offset(half, half),
                    size = Size((size.width - strokePx).coerceAtLeast(0f), (size.height - strokePx).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius((cornerPx - half).coerceAtLeast(0f)),
                    style = Stroke(width = strokePx),
                )
            }
                .clickable(onClick = onClick).testTag("immersive-card-${game.id}")
                .semantics { contentDescription = "${game.title.orEmpty()}，${if (selected) "已选中" else "点击选择"}" },
        ) {
            GameCoverArtwork(
                game,
                Modifier.fillMaxSize().desaturate(frame.saturation)
                    .testTag("immersive-card-role-${slot.name.lowercase()}"),
                ContentScale.Crop,
                cardRadiusDp.dp,
                // Component-sized HERO for every card: a THUMBNAIL (256 px) side card is displayed around
                // a thousand pixels wide, so its artwork was upscaled ~4x and looked blocky/jagged (齿感).
                CoverImageRole.HERO,
                showFallbackMetadata = false,
            )
            // Depth as a scrim inside the opaque card (never layer alpha: cards would turn to glass).
            if (frame.depth > 0f) ImmersiveCardDepthScrim(frame.depth)
            if (fx.sheen && slot == ImmersiveSlot.CENTER) {
                ImmersiveCardSheen(settled = selected && cardContext.settledGameId == game.id, tint = cardContext.tint)
            }
            val titleReveal = immersiveTitleReveal(frame.focusFraction)
            if (orientation == HomeOrientation.PORTRAIT && titleReveal > 0f) {
                ImmersiveCardTitleOverlay(game, ImmersiveTitlePose(titleRestRect, frame.rect.w / titleRestRect.w, titleReveal), density, scale, titleEndInset)
            }
        }
    }
}

/** Darkens (dark theme) or washes out (light theme) a card that sits [depth] back in the carousel. */
@Composable
private fun BoxScope.ImmersiveCardDepthScrim(depth: Float) {
    val dark = LocalAppleColors.current.isDark
    val scrim = if (dark) KireiTokens.Immersive.CardDepthDark else KireiTokens.Immersive.CardDepthLight
    val maxAlpha = if (dark) ImmersiveCardDepth.DARK_SCRIM_ALPHA else ImmersiveCardDepth.LIGHT_WASH_ALPHA
    Spacer(Modifier.matchParentSize().drawBehind { drawRect(scrim, alpha = maxAlpha * depth.coerceIn(0f, 1f)) })
}

/** Rim light (plan §6.1 边缘光): the hairline edge leans toward a lifted cover tone (or accent). */
@Composable
private fun immersiveRimColor(
    fx: ResolvedImmersiveFx,
    tint: Color,
): Color {
    if (!fx.rimLight) return KireiTokens.Immersive.CardEdge
    val source = if (fx.dynamicTint && tint != Color.Unspecified) tint else CoverOverlayAccentBright
    return immersiveRimHighlight(source)
}

private const val SHEEN_DURATION_MS = 300
private const val SHEEN_ALPHA = .16f

/** One 300ms diagonal highlight once the focused card has settled (plan §6.1 高光扫过). */
@Composable
private fun BoxScope.ImmersiveCardSheen(
    settled: Boolean,
    tint: Color,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(settled) {
        if (settled) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(SHEEN_DURATION_MS))
        } else {
            progress.snapTo(0f)
        }
    }
    // Only a settled card can sweep, so dragging (where the tint changes every frame) composes
    // no sheen node at all; the band brush is built once per size and moved by translation.
    if (!settled) return
    val sheenTint = if (tint != Color.Unspecified) tint else KireiTokens.OnImage.ink
    Spacer(
        Modifier.matchParentSize().drawWithCache {
            val brush = immersiveSheenBrush(size, sheenTint, SHEEN_ALPHA)
            onDrawBehind { drawImmersiveCardSheen(progress.value, brush) }
        },
    )
}

/**
 * Portrait title visibility from the card's focus: fully shown at rest in the centre, gone at a
 * side slot. Every card between the centre and a side slot carries its own title, so during a swipe
 * the leaving title travels out on its card while the arriving one comes in on its own.
 */
internal fun immersiveTitleReveal(focusFraction: Float): Float {
    val focus = focusFraction.coerceIn(0f, 1f)
    return focus * focus * (3f - 2f * focus)
}

/**
 * The portrait title is laid out once at the resting centre-card size ([restRect]) and scaled with
 * the card ([cardScale] = card width / rest width) from the card's lower-left corner, so the text,
 * its line breaks and its mask move, shrink and tilt with the cover instead of re-flowing.
 */
private class ImmersiveTitlePose(
    val restRect: HomeRect,
    val cardScale: Float,
    val reveal: Float,
)

@Composable
private fun BoxScope.ImmersiveCardTitleOverlay(
    game: Game,
    pose: ImmersiveTitlePose,
    density: Density,
    scale: Float,
    titleEndInset: Dp,
) {
    // Increased contrast (plan §6.4): a deeper title mask and a text shadow over busy artwork.
    val contrast = LocalImmersiveFx.current.increasedContrast
    val maskBottom = if (contrast) CoverOverlayGradientBottom.copy(alpha = 1f) else CoverOverlayGradientBottom
    Column(
        Modifier.align(Alignment.BottomStart)
            .wrapContentSize(Alignment.BottomStart, unbounded = true)
            .requiredSize(pxDp(pose.restRect.w, density), pxDp(pose.restRect.h, density))
            .graphicsLayer {
                alpha = pose.reveal
                scaleX = pose.cardScale
                scaleY = pose.cardScale
                transformOrigin = TransformOrigin(0f, 1f)
            }
            .background(Brush.verticalGradient(listOf(Color.Transparent, maskBottom)))
            .padding(pxDp(20f * scale, density))
            .padding(end = titleEndInset),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            game.engine?.displayName ?: "游戏",
            color = CoverOverlayAccentBright,
            style = AppleTypography.labelLarge.withImmersiveTitleShadow(contrast, isDark = true),
            fontSize = designSp(26f, density, scale),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.title.orEmpty(),
            color = CoverOverlayInk,
            style = AppleTypography.displayLarge.copy(lineHeight = TextUnit.Unspecified).withImmersiveTitleShadow(contrast, isDark = true),
            fontSize = designSp(58f, density, scale),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ImmersiveLandscapeMetadata(
    game: Game,
    layout: ImmersiveHomeLayout,
    density: Density,
    scale: Float,
    palette: ImmersivePalette
) {
    layout.metadata?.let {
        Text(
            game.engine?.displayName ?: "游戏",
            color = palette.accentBright,
            fontSize = designSp(30f, density, scale),
            lineHeight = designSp(IMMERSIVE_METADATA_LINE_HEIGHT_PX, density, scale),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.homeRect(it, density).zIndex(7f),
        )
    }
    layout.title?.let {
        val title = game.title.orEmpty()
        val titleStyle =
            LocalTextStyle.current.copy(
                fontSize = designSp(72f, density, scale),
                lineHeight = designSp(IMMERSIVE_TITLE_LINE_HEIGHT_PX, density, scale),
                fontWeight = FontWeight.Bold,
            )
        // A one-line title keeps the default line style (and its position). A title that wraps used to outgrow the
        // two-line box under the default Trim.Both (1.0 em lines, the CJK font is ~1.45 em) and was ellipsized by
        // height to one line (「Summer Pock…」): it now gets exact 72 px lines (Trim.None) and a 60 px font, so both
        // lines and their CJK ink stay inside the box, clear of 「继续游戏」.
        val measurer = rememberTextMeasurer()
        val wraps =
            remember(title, titleStyle, it.w) {
                measurer.measure(title, titleStyle, maxLines = 1, constraints = Constraints(maxWidth = it.w.roundToInt().coerceAtLeast(0))).hasVisualOverflow
            }
        Text(
            title,
            color = palette.primary,
            style =
                if (wraps) {
                    titleStyle.copy(fontSize = designSp(IMMERSIVE_WRAPPED_TITLE_FONT_SIZE_PX, density, scale), lineHeightStyle = ImmersiveWrappedTitleLineStyle)
                } else {
                    titleStyle
                },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                (if (wraps) Modifier.homeTextRect(it, density) else Modifier.homeRect(it, density)).zIndex(7f).semantics {
                    contentDescription = title
                },
        )
    }
    layout.subtitle?.let {
        // r5 · A2: the whole line box is kept (Trim.None) and only floors the height, so the glyphs are never cut.
        val continueStyle =
            remember(density, scale) {
                immersiveContinueLineStyle(
                    fontSize = designSp(IMMERSIVE_SUBTITLE_FONT_SIZE_PX, density, scale),
                    lineHeight = designSp(IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX, density, scale),
                )
            }
        Text(
            "继续游戏",
            color = palette.secondary,
            style = continueStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.homeTextRect(it, density).zIndex(7f),
        )
    }
}

/** Two-line landscape titles: each line exactly [IMMERSIVE_TITLE_LINE_HEIGHT_PX], so both fit the title box. */
private val ImmersiveWrappedTitleLineStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None)
private const val IMMERSIVE_WRAPPED_TITLE_FONT_SIZE_PX = 60f

/**
 * Alpha 9 shared control specs: immersive home mirrors the components home on the same
 * device/orientation. Brand font follows the components header (displayLarge portrait /
 * headlineMedium landscape), the menu button is the 48dp components header action and the
 * card corner radius mirrors the default spotlight surface (32dp). The play button size
 * is resolved through [SharedControlSizePolicy] from the actual center card frame, so both
 * home modes share one rule.
 */
internal object ImmersiveSharedSpec {
    const val BRAND_PORTRAIT_FONT_SIZE_SP = 34f
    const val BRAND_LANDSCAPE_FONT_SIZE_SP = 18f
    const val CARD_RADIUS_CENTER_DP = 16f
    const val CARD_RADIUS_SIDE_DP = 12f

    /** Corner contract calibrated to the rendered card scale: focused 16dp, side 12dp, interpolated in motion. */
    fun cardRadiusDp(focusFraction: Float): Float = CARD_RADIUS_SIDE_DP + (CARD_RADIUS_CENTER_DP - CARD_RADIUS_SIDE_DP) * focusFraction.coerceIn(0f, 1f)

    fun brandFontSizeSp(orientation: HomeOrientation): Float =
        if (orientation == HomeOrientation.PORTRAIT) {
            BRAND_PORTRAIT_FONT_SIZE_SP
        } else {
            BRAND_LANDSCAPE_FONT_SIZE_SP
        }
}
