package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePlayTimeAnchorTest {
    @Test
    fun portraitAnchorStaysBetweenTheCenterCardAndPager() {
        val layout = layout(
            play = HomeRect(800f, 1050f, 100f, 100f),
            pagerY = 1500f,
            center = HomeRect(200f, 500f, 600f, 700f),
        )

        val anchor = assertNotNullAnchor(
            resolveImmersivePlayTimeAnchor(
                HomeOrientation.PORTRAIT,
                layout,
                ImmersiveSafeBounds(0f, 0f, 1000f, 2000f),
                1f,
                indicatorHeightPx = 120f,
                gapPx = 30f,
            ),
        )

        assertEquals(500f, anchor.xPx, .001f)
        assertTrue(anchor.yPx >= 1230f)
        assertTrue(anchor.yPx + 120f <= 1470f)
        assertEquals(HomeRect(800f, 1050f, 100f, 100f), layout.play)
    }

    @Test
    fun landscapeAnchorPrefersBelowTheUnmovedPlayButton() {
        val layout = layout(
            play = HomeRect(120f, 300f, 260f, 100f),
            pagerY = 600f,
            center = HomeRect(500f, 100f, 300f, 400f),
        )

        val anchor = assertNotNullAnchor(
            resolveImmersivePlayTimeAnchor(
                HomeOrientation.LANDSCAPE,
                layout,
                ImmersiveSafeBounds(0f, 0f, 1000f, 700f),
                1f,
                indicatorHeightPx = 90f,
                gapPx = 15f,
            ),
        )

        assertEquals(415f, anchor.yPx, .001f)
        assertEquals(120f, anchor.xPx, .001f)
        assertEquals(260f, anchor.maxWidthPx, .001f)
        assertEquals(HomeRect(120f, 300f, 260f, 100f), layout.play)
    }

    @Test
    fun shortLandscapeFallsBackAboveTheUnmovedPlayButton() {
        val layout = layout(
            play = HomeRect(120f, 300f, 260f, 100f),
            pagerY = 430f,
            center = HomeRect(500f, 100f, 300f, 300f),
        )

        val anchor = assertNotNullAnchor(
            resolveImmersivePlayTimeAnchor(
                HomeOrientation.LANDSCAPE,
                layout,
                ImmersiveSafeBounds(0f, 20f, 1000f, 460f),
                1f,
                indicatorHeightPx = 90f,
                gapPx = 15f,
            ),
        )

        assertEquals(195f, anchor.yPx, .001f)
        assertEquals(HomeRect(120f, 300f, 260f, 100f), layout.play)
    }

    @Test
    fun compactPhonesNeverShrinkBelowReadableDpAndSpFloors() {
        val portrait = resolveImmersivePlayTimeVisualSpec(
            HomeOrientation.PORTRAIT,
            adaptiveScale = .9f,
        )
        val landscape = resolveImmersivePlayTimeVisualSpec(
            HomeOrientation.LANDSCAPE,
            adaptiveScale = .9f,
        )

        assertEquals(14.5f, portrait.fontSizeSp, .001f)
        assertEquals(44f, portrait.minimumHeightDp, .001f)
        assertEquals(18f, portrait.iconSizeDp, .001f)
        assertEquals(14f, landscape.fontSizeSp, .001f)
        assertEquals(36f, landscape.minimumHeightDp, .001f)
        assertEquals(16f, landscape.iconSizeDp, .001f)
    }

    @Test
    fun largerWindowsGrowSlightlyWithoutBecomingAnotherPrimaryAction() {
        val portrait = resolveImmersivePlayTimeVisualSpec(
            HomeOrientation.PORTRAIT,
            adaptiveScale = 1.2f,
        )
        val landscape = resolveImmersivePlayTimeVisualSpec(
            HomeOrientation.LANDSCAPE,
            adaptiveScale = 1.2f,
        )

        assertTrue(portrait.fontSizeSp in 16f..17f)
        assertTrue(portrait.minimumHeightDp in 50f..51f)
        assertTrue(landscape.fontSizeSp in 16f..17f)
        assertTrue(landscape.minimumHeightDp in 41f..42f)
    }

    @Test
    fun accessibilityFontScaleExpandsTheReservedHeight() {
        val normal = resolveImmersivePlayTimeVisualSpec(
            HomeOrientation.PORTRAIT,
            adaptiveScale = 1f,
            fontScale = 1f,
        )
        val enlarged = resolveImmersivePlayTimeVisualSpec(
            HomeOrientation.PORTRAIT,
            adaptiveScale = 1f,
            fontScale = 2f,
        )

        assertEquals(44f, normal.minimumHeightDp, .001f)
        assertTrue(enlarged.minimumHeightDp > normal.minimumHeightDp)
    }

    @Test
    fun lightPortraitUnplayedUsesNeutralBorderOnly() {
        val light = ImmersivePaletteResolver.resolve(isDark = false)
        val dark = ImmersivePaletteResolver.resolve(isDark = true)

        assertEquals(
            light.secondary.copy(alpha = .38f),
            immersivePlayTimeBorderColor(light, HomeOrientation.PORTRAIT, 0L),
        )
        assertEquals(
            light.glassBorder,
            immersivePlayTimeBorderColor(light, HomeOrientation.PORTRAIT, 60_000L),
        )
        assertEquals(
            light.glassBorder,
            immersivePlayTimeBorderColor(light, HomeOrientation.LANDSCAPE, 0L),
        )
        assertEquals(
            dark.glassBorder,
            immersivePlayTimeBorderColor(dark, HomeOrientation.PORTRAIT, 0L),
        )
    }

    private fun assertNotNullAnchor(anchor: ImmersivePlayTimeAnchor?): ImmersivePlayTimeAnchor {
        assertNotNull(anchor)
        return requireNotNull(anchor)
    }

    private fun layout(
        play: HomeRect,
        pagerY: Float,
        center: HomeRect,
    ): ImmersiveHomeLayout = ImmersiveHomeLayout(
        brand = HomePoint(0f, 0f),
        menu = HomeRect(0f, 0f, 1f, 1f),
        metadata = null,
        title = null,
        subtitle = null,
        play = play,
        dock = HomeRect(0f, 0f, 1f, 1f),
        pagerCenters = listOf(HomePoint(500f, pagerY)),
        cardSlots = mapOf(
            ImmersiveSlot.CENTER to ImmersiveCardFrame(center),
        ),
    )
}
