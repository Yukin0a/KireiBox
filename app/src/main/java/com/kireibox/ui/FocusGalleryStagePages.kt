package com.kireibox.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.LandscapeHomeStyle

@Composable
internal fun FocusGalleryStage(
    snapshot: FocusGallerySnapshot,
    games: List<Game>,
    colors: KireiColors,
    geometry: FocusGalleryGeometry,
    style: LandscapeHomeStyle,
    reducedMotion: Boolean,
    gameCount: Int,
    modifier: Modifier = Modifier,
    onOpen: (Game) -> Unit,
    onLaunch: (Game) -> Unit,
    onSelect: (FocusCard) -> Unit,
    onPressedChanged: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val progress = snapshot.progress.coerceIn(0f, 1f)
    val transitioning = snapshot.isSettling
    val stageWidthPx = with(density) { (geometry.centerSlotCenterXDp * 2f).dp.toPx() }
    Box(modifier.clipToBounds()) {
        snapshot.cards.forEach { card ->
            key(card.key) {
                val game = games.firstOrNull { it.id == card.gameId }
                if (game != null) {
                    val cardGeometry =
                        FocusGalleryGeometryPolicy.cardGeometry(
                            card = card,
                            progress = progress,
                            geometry = geometry,
                            reducedMotion = reducedMotion,
                        )
                    val cardCenterXPx = with(density) { cardGeometry.centerXDp.dp.toPx() }
                    val offsetXPx = cardCenterXPx - stageWidthPx / 2f
                    val offsetXDp = with(density) { offsetXPx.toDp() }
                    val centerActionEnabled =
                        FocusGalleryInteractionPolicy.centerActionEnabled(
                            sourceSlot = card.sourceSlot,
                            progress = progress,
                            isSettling = snapshot.isSettling,
                            infoAlpha = cardGeometry.infoAlpha,
                        )
                    // C clears switch semantics on the underlying card; the transparent
                    // overlay below owns the action so a screen reader never announces the
                    // duplicated candidate twice. D keeps the direct card action.
                    val switchActionVisible =
                        FocusGallerySemanticsPolicy.cardExposesSwitchSemantics(
                            style = style,
                            gameCount = gameCount,
                            card = card,
                            isSettling = snapshot.isSettling,
                        )
                    val sideSelectable =
                        !transitioning &&
                            (card.sourceSlot == FocusSlot.LEFT || card.sourceSlot == FocusSlot.RIGHT)
                    Box(
                        Modifier
                            .width(cardGeometry.widthDp.dp)
                            .height(cardGeometry.heightDp.dp)
                            .offset(
                                x = offsetXDp,
                                y =
                                    FocusGalleryVisualPolicy.outerOffsetY(
                                        style = style,
                                        arcLift = cardGeometry.offsetYDp,
                                        bottomGap = geometry.bottomGapDp,
                                    ).dp,
                            )
                            .align(Alignment.BottomCenter)
                            .zIndex(cardGeometry.zIndex)
                            .graphicsLayer {
                                scaleX = cardGeometry.scale
                                scaleY = cardGeometry.scale
                                // D-only readability lift; C keeps the raw policy alpha.
                                alpha = FocusGalleryVisualPolicy.stageAlpha(style, cardGeometry.alpha)
                                rotationY = cardGeometry.rotationYDegrees
                                cameraDistance = 6f * cardGeometry.heightDp.dp.toPx()
                            },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (FocusGalleryVisualPolicy.ownsOuterArtwork(style)) {
                            FocusCoverArtwork(
                                game = game,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = cardGeometry.radiusDp.dp,
                                role = CoverImageRole.HERO,
                                style = style,
                                darkTheme = colors.isDark,
                                focusAlpha = cardGeometry.infoAlpha,
                            )
                            FocusStageDOutline(
                                radiusDp = cardGeometry.radiusDp.dp,
                                infoAlpha = cardGeometry.infoAlpha,
                                darkTheme = colors.isDark,
                            )
                        }
                        LandscapeSpotlightPage(
                            game = game,
                            colors = colors,
                            style = style,
                            safeContentPadding = 0.dp,
                            reducedTransparency = false,
                            onOpen = { if (centerActionEnabled) onOpen(game) },
                            onLaunch = { if (centerActionEnabled) onLaunch(game) },
                            onSelect = { if (sideSelectable) onSelect(card) },
                            onPressedChanged = onPressedChanged,
                            cardHeightDp = cardGeometry.heightDp,
                            cardBottomGapDp =
                                FocusGalleryVisualPolicy.innerBottomGap(
                                    style = style,
                                    bottomGap = geometry.bottomGapDp,
                                ),
                            isCenterCard = centerActionEnabled,
                            interactionEnabled = centerActionEnabled || sideSelectable,
                            exposeSwitchSemantics = switchActionVisible,
                            centerActionEnabled = centerActionEnabled,
                            applySideBlur = false,
                            cardWidthDp = cardGeometry.widthDp,
                            radiusDp = cardGeometry.radiusDp,
                            cardScale = 1f,
                            cardAlpha = 1f,
                            cardSaturation = cardGeometry.saturation,
                            infoAlpha = cardGeometry.infoAlpha,
                            drawArtwork = !FocusGalleryVisualPolicy.ownsOuterArtwork(style),
                            drawStyleBorder = !FocusGalleryVisualPolicy.ownsOuterArtwork(style),
                        )
                    }
                }
            }
        }
        // C-only transparent side hit targets: idle frames only, outside the center card's
        // left/right edge, vertically covering the scaled side strip. D cards stay fully
        // visible and keep their direct card clicks, so no overlay is emitted for D.
        val hitCards =
            snapshot.cards
                .filter { it.sourceSlot == FocusSlot.LEFT || it.sourceSlot == FocusSlot.RIGHT }
                .associateBy { it.sourceSlot }
        listOf(FocusSlot.LEFT, FocusSlot.RIGHT).forEach { side ->
            val bounds =
                FocusGalleryHitTargetPolicy.sideHitBounds(
                    side = side,
                    geometry = geometry,
                    style = style,
                    progress = progress,
                    isSettling = snapshot.isSettling,
                )
            val card = hitCards[side]
            val game = card?.let { target -> games.firstOrNull { it.id == target.gameId } }
            if (bounds != null && card != null && game != null) {
                val exposeSwitch =
                    FocusGallerySemanticsPolicy.overlayExposesSwitchSemantics(
                        style = style,
                        gameCount = gameCount,
                        card = card,
                        isSettling = snapshot.isSettling,
                    )
                // zIndex .5 sits above the resting side-card visuals (z=0) and below the
                // center card (z=3), and the bounds never overlap the center span, so one
                // tap hits only this overlay and cannot double-fire with the card below.
                Box(
                    Modifier
                        .offset(x = bounds.leftDp.dp, y = bounds.topDp.dp)
                        .width(bounds.widthDp.dp)
                        .height(bounds.heightDp.dp)
                        .zIndex(.5f)
                        .then(
                            if (exposeSwitch) {
                                Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = "切换到 ${game.title.orEmpty()}"
                                    role = Role.Button
                                }
                            } else {
                                Modifier.clearAndSetSemantics { }
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSelect(card)
                        },
                )
            }
        }
    }
}

/**
 * Shared D card outline: the same 1dp theme-aware highlight that the FocusGalleryStage
 * outer card draws for IMMERSIVE_COVER and LandscapeSpotlightPage draws for non-stage
 * callers, so the D outer stage and the default page path stay pixel-identical.
 */
@Composable
internal fun FocusStageDOutline(
    radiusDp: Dp,
    infoAlpha: Float,
    darkTheme: Boolean,
) {
    Box(
        Modifier.fillMaxSize().border(
            width = 1.dp,
            color =
                if (darkTheme) {
                    Color.White.copy(
                        alpha =
                            FocusGalleryVisualPolicy.outlineAlpha(
                                infoAlpha = infoAlpha,
                                darkTheme = true,
                            ),
                    )
                } else {
                    Color.Black.copy(
                        alpha =
                            FocusGalleryVisualPolicy.outlineAlpha(
                                infoAlpha = infoAlpha,
                                darkTheme = false,
                            ),
                    )
                },
            shape = RoundedCornerShape(radiusDp),
        ),
    )
}
