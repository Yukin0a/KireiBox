package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitCoverPolicyTest {
    @Test
    fun `ratio is exactly 9 to 16`() {
        assertEquals(9f / 16f, PortraitCoverPolicy.WIDTH_TO_HEIGHT_RATIO, 1e-6f)
        assertEquals(180f, PortraitCoverPolicy.widthForHeight(320f), .001f)
        assertEquals(320f, PortraitCoverPolicy.heightForWidth(180f), .001f)
    }

    @Test
    fun `taller source crops vertically centered and fills the full card width`() {
        val layout =
            PortraitCoverPolicy.layout(
                srcWidthPx = 900,
                srcHeightPx = 2000,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
            )
        assertEquals(180f, layout.contentWidthPx, .001f)
        assertEquals(400f, layout.contentHeightPx, .001f)
        assertEquals(0f, layout.offsetXPx, .001f)
        assertEquals(-40f, layout.offsetYPx, .001f)
        assertTrue(layout.hasContent)
    }

    @Test
    fun `exact 9 to 16 source fills the box with no crop`() {
        val layout =
            PortraitCoverPolicy.layout(
                srcWidthPx = 900,
                srcHeightPx = 1600,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
            )
        assertEquals(320f, layout.contentHeightPx, .001f)
        assertEquals(180f, layout.contentWidthPx, .001f)
        assertEquals(0f, layout.offsetYPx, .001f)
        assertEquals(0f, layout.offsetXPx, .001f)
        assertTrue(layout.hasContent)
    }

    @Test
    fun `wide source center crops horizontally to fill the 9 to 16 card`() {
        val layout =
            PortraitCoverPolicy.layout(
                srcWidthPx = 1600,
                srcHeightPx = 900,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
            )
        // Scale is driven by height: 320/900, so the width overflows and is center-cropped.
        assertEquals(320f, layout.contentHeightPx, .001f)
        assertEquals(568.888f, layout.contentWidthPx, .01f)
        assertEquals(0f, layout.offsetYPx, .001f)
        assertTrue(layout.offsetXPx < 0f)
        assertTrue(layout.hasContent)
    }

    @Test
    fun `very wide source still fills the card with no letterbox`() {
        val layout =
            PortraitCoverPolicy.layout(
                srcWidthPx = 3000,
                srcHeightPx = 1000,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
            )
        assertEquals(320f, layout.contentHeightPx, .001f)
        assertEquals(960f, layout.contentWidthPx, .001f)
        assertEquals(0f, layout.offsetYPx, .001f)
        assertTrue(layout.offsetXPx < 0f)
        assertTrue(layout.hasContent)
    }

    @Test
    fun `unknown source has no content so the caller renders the placeholder`() {
        val layout =
            PortraitCoverPolicy.layout(
                srcWidthPx = 0,
                srcHeightPx = 0,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
            )
        assertEquals(0f, layout.contentHeightPx, .001f)
        assertEquals(0f, layout.contentWidthPx, .001f)
        assertFalse(layout.hasContent)
    }

    @Test
    fun `crop keeps the source aspect and always covers both target axes`() {
        val targetWidthPx = 240f
        val targetHeightPx = 320f
        val layout =
            PortraitCoverPolicy.layout(
                srcWidthPx = 1200,
                srcHeightPx = 2400,
                targetWidthPx = targetWidthPx,
                targetHeightPx = targetHeightPx,
            )
        assertEquals(480f, layout.contentHeightPx, .001f)
        assertEquals(240f, layout.contentWidthPx, .001f)
        assertEquals(240f / 480f, 1200f / 2400f, 1e-4f)
        // Crop semantics: content >= target on both axes.
        assertTrue(layout.contentWidthPx >= targetWidthPx)
        assertTrue(layout.contentHeightPx >= targetHeightPx)
    }

    @Test
    fun `fit layout keeps the complete source centered inside the target`() {
        val fit =
            PortraitCoverPolicy.fitLayout(
                srcWidthPx = 900,
                srcHeightPx = 1600,
                targetWidthPx = 438f,
                targetHeightPx = 247f,
            )
        assertEquals(138.94f, fit.contentWidthPx, .01f)
        assertEquals(247f, fit.contentHeightPx, .01f)
        assertEquals((438f - 138.94f) / 2f, fit.offsetXPx, .01f)
        assertEquals(0f, fit.offsetYPx, .001f)
        assertTrue(fit.hasContent)
    }

    @Test
    fun `adaptive blend maps p zero half and one continuously`() {
        val zero = FocusCoverArtworkPolicy.adaptiveBlend(0f)
        assertEquals(1f, zero.sharpCropAlpha, .001f)
        assertEquals(0f, zero.blurredFillAlpha, .001f)
        assertEquals(0f, zero.fitAlpha, .001f)
        assertEquals(0f, zero.overlayStrength, .001f)

        val half = FocusCoverArtworkPolicy.adaptiveBlend(.5f)
        assertEquals(.5f, half.sharpCropAlpha, .001f)
        assertEquals(.5f, half.blurredFillAlpha, .001f)
        assertEquals(.5f, half.fitAlpha, .001f)
        assertEquals(.5f, half.overlayStrength, .001f)

        val one = FocusCoverArtworkPolicy.adaptiveBlend(1f)
        assertEquals(0f, one.sharpCropAlpha, .001f)
        assertEquals(1f, one.blurredFillAlpha, .001f)
        assertEquals(1f, one.fitAlpha, .001f)
        assertEquals(1f, one.overlayStrength, .001f)
    }

    @Test
    fun `D adaptive mode fits a portrait source with blurred fill`() {
        val plan =
            FocusCoverArtworkPolicy.plan(
                srcWidthPx = 900,
                srcHeightPx = 1600,
                targetWidthPx = 438f,
                targetHeightPx = 247f,
                style = LandscapeHomeStyle.IMMERSIVE_COVER,
            )
        assertEquals(FocusCoverMode.ADAPTIVE_BLUR_FILL, plan.mode)
        assertTrue(plan.ratioMismatch > FocusCoverArtworkPolicy.MISMATCH_THRESHOLD)
        assertNotNull(plan.foreground)
        assertTrue(plan.hasContent)
        // The fill still covers the whole card (crop), the foreground keeps the whole
        // source inside the box (fit).
        assertTrue(plan.fill.contentWidthPx >= 438f)
        assertTrue(plan.fill.contentHeightPx >= 247f)
        val foreground = requireNotNull(plan.foreground)
        assertTrue(foreground.contentWidthPx <= 438f + .001f)
        assertTrue(foreground.contentHeightPx <= 247f + .001f)
    }

    @Test
    fun `C adaptive mode fits a wide source with blurred fill`() {
        val plan =
            FocusCoverArtworkPolicy.plan(
                srcWidthPx = 1600,
                srcHeightPx = 900,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
                style = LandscapeHomeStyle.FOCUS_POSTER,
            )
        assertEquals(FocusCoverMode.ADAPTIVE_BLUR_FILL, plan.mode)
        assertTrue(plan.ratioMismatch > FocusCoverArtworkPolicy.MISMATCH_THRESHOLD)
        assertNotNull(plan.foreground)
        val foreground = requireNotNull(plan.foreground)
        assertEquals(180f, foreground.contentWidthPx, .001f)
        assertEquals(101.25f, foreground.contentHeightPx, .01f)
    }

    @Test
    fun `matched aspect stays single layer crop`() {
        val dMatched =
            FocusCoverArtworkPolicy.plan(
                srcWidthPx = 1600,
                srcHeightPx = 900,
                targetWidthPx = 438f,
                targetHeightPx = 247f,
                style = LandscapeHomeStyle.IMMERSIVE_COVER,
            )
        assertEquals(FocusCoverMode.CROP, dMatched.mode)
        assertEquals(null, dMatched.foreground)
        assertTrue(dMatched.ratioMismatch <= FocusCoverArtworkPolicy.MISMATCH_THRESHOLD)

        val cMatched =
            FocusCoverArtworkPolicy.plan(
                srcWidthPx = 900,
                srcHeightPx = 1600,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
                style = LandscapeHomeStyle.FOCUS_POSTER,
            )
        assertEquals(FocusCoverMode.CROP, cMatched.mode)
        assertEquals(null, cMatched.foreground)
    }

    @Test
    fun `moderate mismatch below threshold stays crop`() {
        // 3:4 source inside a 9:16 card: mismatch = (3/4)/(9/16) = 1.333 < 1.35.
        val plan =
            FocusCoverArtworkPolicy.plan(
                srcWidthPx = 900,
                srcHeightPx = 1200,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
                style = LandscapeHomeStyle.FOCUS_POSTER,
            )
        assertEquals(FocusCoverMode.CROP, plan.mode)
        assertEquals(null, plan.foreground)
    }

    @Test
    fun `unknown source has no adaptive content so the caller renders the placeholder`() {
        val plan =
            FocusCoverArtworkPolicy.plan(
                srcWidthPx = 0,
                srcHeightPx = 0,
                targetWidthPx = 180f,
                targetHeightPx = 320f,
                style = LandscapeHomeStyle.IMMERSIVE_COVER,
            )
        assertFalse(plan.hasContent)
    }
}
