package com.kireibox.ui

data class ImmersiveSafeBounds(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
)

enum class ImmersiveSlot {
    EXIT_LEFT,
    FAR_LEFT,
    NEAR_LEFT,
    CENTER,
    NEAR_RIGHT,
    FAR_RIGHT,
    ENTER_RIGHT,
}

data class ImmersiveCardFrame(
    val rect: HomeRect,
    val rotationZ: Float = 0f,
    val rotationY: Float = 0f,
    val alpha: Float = 1f,
    val saturation: Float = 1f,
    val elevationPx: Float = 0f,
    val zIndex: Float = 0f,
    /** 1.0 = focused center card, 0.0 = side/edge card; drives the corner radius interpolation. */
    val focusFraction: Float = 0f,
    /** 0 = in focus, 1 = deepest visible slot: drawn as a scrim inside the opaque card ([ImmersiveCardDepth]). */
    val depth: Float = 0f,
)

/**
 * Depth cue of the immersive carousel. Side cards used to be dimmed with layer alpha, which made
 * them see-through glass (the next card's art showing through the current one mid-swipe, grey
 * slabs on the light backdrop). Cards are now opaque artwork: a slot's nominal alpha only says
 * whether it is present (edge slots at 0 fade in/out) and how deep it sits, and depth is drawn as a
 * scrim inside the clipped card — black in dark, a white wash in light — on top of the existing
 * desaturation.
 */
internal object ImmersiveCardDepth {
    /** Nominal alpha of the deepest visible slot (the portrait neighbours). */
    private const val DEEPEST_ALPHA = .48f

    /** Scrim alpha at depth 1: black over dark pages, white over light ones. */
    const val DARK_SCRIM_ALPHA = .40f
    const val LIGHT_WASH_ALPHA = .35f

    /** Layer alpha of a slot: every visible slot is fully opaque; only the edge slots (0) are absent. */
    fun presence(nominalAlpha: Float): Float = if (nominalAlpha > 0f) 1f else 0f

    /** How far back a slot sits: 0 for the center, 1 for the deepest visible slot (and beyond). */
    fun depth(nominalAlpha: Float): Float = ((1f - nominalAlpha) / (1f - DEEPEST_ALPHA)).coerceIn(0f, 1f)
}

data class ImmersiveHomeLayout(
    val brand: HomePoint,
    val menu: HomeRect,
    val metadata: HomeRect?,
    val title: HomeRect?,
    val subtitle: HomeRect?,
    val play: HomeRect,
    val dock: HomeRect,
    val pagerCenters: List<HomePoint>,
    val cardSlots: Map<ImmersiveSlot, ImmersiveCardFrame>,
    /** Shared render scale for landscape typography and controls derived from the resolved frame. */
    val renderScale: Float = 1f,
)

data class ImmersiveTextBudget(
    val metadataHeightPx: Float,
    val titleHeightPx: Float,
    val titleLineHeightPx: Float,
    val subtitleHeightPx: Float,
)

const val IMMERSIVE_LANDSCAPE_REFERENCE_HEIGHT_PX = 941f
const val IMMERSIVE_LANDSCAPE_CENTER_FRACTION = .63f
const val IMMERSIVE_LANDSCAPE_CENTER_HEIGHT_FRACTION = .5356f
const val IMMERSIVE_LANDSCAPE_METADATA_X_FRACTION = .053f
const val IMMERSIVE_LANDSCAPE_METADATA_MIN_SAFE_GAP_PX = 8f
const val IMMERSIVE_LANDSCAPE_METADATA_MAX_DOWNWARD_OFFSET_PX = 24f
const val IMMERSIVE_LANDSCAPE_RENDER_SCALE_MIN = .55f
const val IMMERSIVE_LANDSCAPE_RENDER_SCALE_MAX = 1.15f

/** Smallest font scale the immersive text budget plans for; values below it clamp. */
const val IMMERSIVE_FONT_SCALE_SAFE_MIN = .75f

/** Fallback text scale for non-finite font scales so the geometry never NaNs. */
const val IMMERSIVE_FONT_SCALE_FALLBACK = 1f

/** The play control's 48dp-equivalent touch floor in the policy's reference space. */
const val IMMERSIVE_PLAY_MIN_HEIGHT_PX = 48f

/** The pager lane keeps at least this 12dp-equivalent gap from the front card bottom (design px). */
const val IMMERSIVE_PAGER_GAP_PX = 32f

/** Landscape pager indicator height mirrored by [ImmersivePager]. */
const val IMMERSIVE_PAGER_INDICATOR_HEIGHT_PX = 5f

/** Extra information-to-indicator gap after subtracting the indicator half-height. */
const val IMMERSIVE_PAGER_TEXT_GAP_PX = 16f

const val IMMERSIVE_PAGER_INDICATOR_HALF_HEIGHT_PX = IMMERSIVE_PAGER_INDICATOR_HEIGHT_PX / 2f

/** The landscape information column keeps a 24dp-equivalent gap from the left-most card (design px). */
const val LANDSCAPE_TEXT_CARD_GAP_PX = 63f

/** Explicit Compose line boxes mirrored by [resolveImmersiveTextBudget]. */
const val IMMERSIVE_METADATA_LINE_HEIGHT_PX = 44f
const val IMMERSIVE_TITLE_LINE_HEIGHT_PX = 72f

/**
 * 「继续游戏」 (r5 · A2): a 32 design px label in a 44 px line box (1.375 em, was 36 = 1.125 em). Compose kept the
 * one-line label at the CJK font's own 1.45 em height, so the 1.125 em box cut the bottoms of the glyphs off; the
 * label is now laid out with [immersiveContinueLineStyle] (Trim.None), which keeps exactly this box, and the glyphs
 * (ink about .22–1.22 em below the line top) sit inside it.
 */
const val IMMERSIVE_SUBTITLE_FONT_SIZE_PX = 32f
const val IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX = 44f

/**
 * Gap between the 「继续游戏」 line box and the play pill (design px; it was the title-to-pill gap of 20). It gave the
 * line box the 8 px it gained, so at 1× font scale the information stack keeps its height (36 + 20 = 44 + 12) and the
 * pill its position; larger font scales grow the box by 8 px × (fontScale − 1), like every other line of the column.
 */
const val IMMERSIVE_SUBTITLE_PLAY_GAP_PX = 12f
