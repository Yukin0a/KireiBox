package com.kireibox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.yuki.yukihub.settings.AccentChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the design-token values to doc/2026-10-02-ui-alpha17-design-spec.md. */
class KireiTokensTest {
    @Test
    fun neutralTokensMatchSpecSection21() {
        val dark = KireiTokens.neutrals(isDark = true)
        assertEquals(Color(0xFF0E0E11), dark.background)
        assertEquals(Color(0xFF17171B), dark.surface)
        assertEquals(Color(0xFF202026), dark.surface2)
        assertEquals(Color(0xFF2A2A31), dark.surface3)
        assertEquals(Color(0xFF1B1B20), dark.surfaceOverlay)
        assertEquals(Color(0xFFF4F4F6), dark.text)
        assertEquals(Color(0xFFA3A3AD), dark.secondary)
        assertEquals(Color(0xFF6C6C76), dark.tertiary)
        assertEquals(Color(0xFF3A3A42), dark.outline)
        assertEquals(.08f, dark.stroke.alpha, .005f)
        assertEquals(.14f, dark.strokeStrong.alpha, .005f)

        val light = KireiTokens.neutrals(isDark = false)
        assertEquals(Color(0xFFF5F5F7), light.background)
        assertEquals(Color(0xFFFFFFFF), light.surface)
        assertEquals(Color(0xFFEFEFF2), light.surface2)
        assertEquals(Color(0xFFE6E6EB), light.surface3)
        assertEquals(Color(0xFF111114), light.text)
        assertEquals(Color(0xFF6B6B74), light.secondary)
        assertEquals(Color(0xFFD8D8DE), light.outline)
        assertEquals(Color(0xFF1FA463), light.success)
        assertEquals(Color(0xFFD98200), light.warning)
        assertEquals(Color(0xFFE5383B), light.danger)
    }

    @Test
    fun darkThemeIsGraphiteNotPureBlack() {
        val dark = kireiColorsFromTokens(isDark = true)
        assertNotEquals("the old pure-black theme is gone", Color.Black, dark.background)
        // r4 (user decision): purple is the default accent again, now on the graphite neutrals.
        assertEquals("purple is the default accent", KireiTokens.accent(AccentChoice.CLASSIC, isDark = true).accent, dark.accent)
        assertEquals(Color(0xFF5B45C7), kireiColorsFromTokens(isDark = false).accent)
    }

    @Test
    fun otherAccentsNeverFallBackToThePurples() {
        val purples = listOf(Color(0xFFB3ACFF), Color(0xFF5B45C7), Color(0xFF9B8CFF), Color(0xFF6C5CE7))
        AccentChoice.entries.filter { it != AccentChoice.DEFAULT }.forEach { choice ->
            listOf(true, false).forEach { dark ->
                val accent = kireiColorsFromTokens(isDark = dark, accent = choice).accent
                assertFalse("$choice dark=$dark", accent in purples)
            }
        }
    }

    @Test
    fun highContrastStrengthensInkStrokesAndMakesGlassOpaque() {
        val dark = KireiTokens.neutrals(isDark = true, highContrast = true)
        assertEquals(Color(0xFFC4C4CC), dark.secondary)
        assertEquals(Color(0xFF8E8E98), dark.tertiary)
        assertEquals(.16f, dark.stroke.alpha, .005f)
        assertTrue(dark.strokeStrong.alpha > dark.stroke.alpha)
        assertEquals(dark.surfaceOverlay, dark.glass)
        assertTrue(dark.highContrast)

        val light = KireiTokens.neutrals(isDark = false, highContrast = true)
        assertEquals(Color(0xFF4A4A52), light.secondary)
        assertEquals(Color(0xFF7A7A84), light.tertiary)
        assertEquals(.14f, light.stroke.alpha, .005f)
        assertEquals(1f, light.glass.alpha, .001f)
    }

    @Test
    fun accentTableMatchesSpecSection22() {
        val expected =
            mapOf(
                // Light accents amended 2026-10-03 (spec §2.2/§10): same hues, deep enough for AA.
                AccentChoice.SAKURA to listOf(0xFFFF8FAB, 0xFF3A0A1A, 0xFFBA2859, 0xFFFFFFFF),
                AccentChoice.OCEAN to listOf(0xFF6FB8FF, 0xFF04213D, 0xFF1C63C0, 0xFFFFFFFF),
                AccentChoice.AMBER to listOf(0xFFFFB547, 0xFF3A2400, 0xFF955600, 0xFFFFFFFF),
                AccentChoice.MINT to listOf(0xFF5EDBB0, 0xFF00301F, 0xFF097353, 0xFFFFFFFF),
                AccentChoice.CLASSIC to listOf(0xFFB3ACFF, 0xFF211E47, 0xFF5B45C7, 0xFFFFFFFF),
            )
        assertEquals(AccentChoice.entries.toSet(), expected.keys)
        expected.forEach { (choice, values) ->
            assertEquals(Color(values[0]), KireiTokens.accent(choice, isDark = true).accent)
            assertEquals(Color(values[1]), KireiTokens.accent(choice, isDark = true).onAccent)
            assertEquals(Color(values[2]), KireiTokens.accent(choice, isDark = false).accent)
            assertEquals(Color(values[3]), KireiTokens.accent(choice, isDark = false).onAccent)
        }
    }

    /**
     * WCAG 1.4.3 AA for small text on every accent pairing the components use: onAccent on an accent
     * fill (chips, primary buttons, badges), accent text on the page and the group surface (text
     * buttons) and accent on its own accentContainer (secondary buttons, the selected Dock label).
     */
    @Test
    fun everyAccentMeetsAaForSmallTextInBothThemes() {
        listOf(true, false).forEach { dark ->
            val neutral = KireiTokens.neutrals(dark)
            AccentChoice.entries.forEach { choice ->
                val tone = KireiTokens.accent(choice, dark)
                val label = "$choice dark=$dark"
                assertAtLeast("$label onAccent on accent", 4.5, contrast(tone.onAccent, tone.accent))
                assertAtLeast("$label accent on background", 4.5, contrast(tone.accent, neutral.background))
                assertAtLeast("$label accent on surface", 4.5, contrast(tone.accent, neutral.surface))
                listOf(neutral.background, neutral.surface).forEach { under ->
                    val container = KireiTokens.accentContainer(tone.accent, dark).compositeOver(under)
                    assertAtLeast("$label accent on accentContainer", 4.5, contrast(tone.accent, container))
                }
            }
        }
    }

    /** Footers, badges and the off switch outline (spec §10 amendments) clear their WCAG minimums. */
    @Test
    fun footerInkBadgesAndControlOutlineMeetTheirContrastMinimums() {
        listOf(true, false).forEach { dark ->
            val neutral = KireiTokens.neutrals(dark)
            assertAtLeast("footer (secondary) on background, dark=$dark", 4.5, contrast(neutral.secondary, neutral.background))
            val badge = KireiTokens.badgeDanger(dark)
            assertAtLeast("danger badge digits, dark=$dark", 4.5, contrast(badge.onAccent, badge.accent))
        }
        assertTrue("tertiary stays a decorative tone (it fails AA on the light page)", contrast(KireiTokens.LightNeutrals.tertiary, KireiTokens.LightNeutrals.background) < 4.5)
        listOf(true, false).forEach { dark ->
            val neutral = KireiTokens.neutrals(dark)
            // Destructive buttons and row / menu titles use the badge pair (r3 review): white on the
            // raw danger is 3.05:1 (dark) / 4.23:1 (light), below AA for 15–16sp labels.
            val solid = KireiTokens.badgeDanger(dark)
            assertAtLeast("destructive button label, dark=$dark", 4.5, contrast(solid.onAccent, solid.accent))
            listOf(neutral.surface, neutral.background, neutral.surfaceOverlay).forEach { under ->
                assertAtLeast("destructive title ink, dark=$dark", 4.5, contrast(solid.accent, under))
            }
        }
        // Input placeholders sit on the surface3 field fill.
        assertAtLeast("light placeholder on surface3", 4.5, contrast(KireiTokens.PlaceholderLight, KireiTokens.LightNeutrals.surface3))
        assertAtLeast("dark placeholder on surface3", 4.5, contrast(KireiTokens.DarkNeutrals.secondary, KireiTokens.DarkNeutrals.surface3))
        assertAtLeast("light off-switch outline vs the white group (1.4.11)", 3.0, contrast(KireiTokens.ControlBorderLight, KireiTokens.LightNeutrals.surface))
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + .05) / (minOf(la, lb) + .05)
    }

    private fun assertAtLeast(
        label: String,
        minimum: Double,
        actual: Double,
    ) = assertTrue("$label: %.2f < $minimum".format(actual), actual >= minimum)

    @Test
    fun accentContainerIsTheSingleSelectionTint() {
        val accent = Color(0xFFFF8FAB)
        assertEquals(accent.copy(alpha = .16f), KireiTokens.accentContainer(accent, isDark = true))
        assertEquals(accent.copy(alpha = .12f), KireiTokens.accentContainer(accent, isDark = false))
        val colors = kireiColorsFromTokens(isDark = true)
        assertEquals(KireiTokens.accentContainer(colors.accent, isDark = true), colors.accentContainer)
        assertNotEquals(colors.accent, colors.accentStrong)
    }

    @Test
    fun legacyFieldsRemapToGraphiteTokens() {
        val dark = kireiColorsFromTokens(isDark = true)
        val tokens = KireiTokens.neutrals(isDark = true)
        assertEquals(tokens.surface2, dark.surfaceRaised)
        assertEquals(tokens.secondary, dark.secondary)
        assertEquals(Color(0xFF3A3A42), dark.outline)
        assertEquals(tokens.success, dark.success)
        assertEquals(tokens.danger, dark.danger)
        assertEquals(Color(0xFFD8D8DE), kireiColorsFromTokens(isDark = false).outline)
    }

    @Test
    fun namedArgumentConstructorsDefaultTheNewFieldsFromTokens() {
        val legacy =
            KireiColors(
                background = Color.Black,
                surface = Color.Black,
                surfaceRaised = Color.Black,
                text = Color.White,
                secondary = Color.Gray,
                accent = Color(0xFFFF8FAB),
                success = Color.Green,
                outline = Color.Gray,
                onAccent = Color.Black,
                danger = Color.Red,
                isDark = true,
            )
        val tokens = KireiTokens.neutrals(isDark = true)
        assertEquals(tokens.surface3, legacy.surface3)
        assertEquals(tokens.stroke, legacy.stroke)
        assertEquals(tokens.warning, legacy.warning)
        assertEquals(KireiTokens.accentContainer(legacy.accent, isDark = true), legacy.accentContainer)
        assertFalse(legacy.highContrast)
    }

    @Test
    fun darkKireiColorsAreGraphiteOnTheImmersiveBaseWithTheDefaultPurpleAccent() {
        val chrome = darkKireiColors()
        assertTrue(chrome.isDark)
        // r4: the immersive base is near-black graphite (alpha.17 depth without the blue cast).
        assertEquals(Color(0xFF0A0A0C), chrome.background)
        assertEquals(KireiTokens.Immersive.DarkBase, chrome.background)
        assertEquals(KireiTokens.DarkNeutrals.surface, chrome.surface)
        // r4: purple is the default accent.
        assertEquals(Color(0xFFB3ACFF), chrome.accent)
        assertEquals(Color(0xFF211E47), chrome.onAccent)
    }

    @Test
    fun paletteLerpHitsBothEndpointsAndSwitchesFlagsImmediately() {
        val dark = kireiColorsFromTokens(isDark = true)
        val light = kireiColorsFromTokens(isDark = false, accent = AccentChoice.OCEAN)
        assertEquals(light, lerpKireiColors(dark, light, 1f))
        val start = lerpKireiColors(dark, light, 0f)
        assertEquals(dark.background, start.background)
        assertEquals(dark.accent, start.accent)
        assertFalse("isDark follows the target immediately", start.isDark)
        val mid = lerpKireiColors(dark, light, .5f)
        assertNotEquals(dark.background, mid.background)
        assertNotEquals(light.background, mid.background)
    }

    @Test
    fun materialSchemeInheritsGraphiteAndAccent() {
        val palette = AppleColorPalette.resolve(isDark = true, accent = AccentChoice.SAKURA)
        val scheme = palette.toMaterialColorScheme()
        assertEquals(palette.primary, scheme.primary)
        assertEquals(palette.onPrimary, scheme.onPrimary)
        assertEquals(palette.primary, scheme.secondary)
        assertEquals(palette.background, scheme.background)
        assertEquals(palette.surface, scheme.surface)
        assertEquals(palette.surfaceAlt, scheme.surfaceVariant)
        assertEquals(palette.surfaceOverlay, scheme.surfaceContainerHigh)
        assertEquals(palette.surface3, scheme.surfaceContainerHighest)
        assertEquals(palette.outline, scheme.outline)
        assertEquals(palette.line, scheme.outlineVariant)
        assertEquals(palette.danger, scheme.error)
        assertEquals("tonal elevation must not tint surfaces with the accent", palette.surface, scheme.surfaceTint)
        assertEquals(1f, scheme.primaryContainer.alpha, .001f)
    }

    @Test
    fun shapeSpacingAndElevationTokensMatchSpec() {
        assertEquals(listOf(6.dp, 10.dp, 14.dp, 20.dp, 28.dp), with(KireiShapes.Radius) { listOf(xs, sm, md, lg, xl) })
        assertEquals(KireiShapes.xl, KireiShapes.material.extraLarge)
        assertEquals(KireiShapes.md, KireiShapes.material.medium)
        assertEquals(
            listOf(4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp, 40.dp),
            with(KireiSpacing) { listOf(xxs, xs, sm, md, lg, xl, xxl, xxxl) },
        )
        assertEquals(52.dp, KireiSpacing.rowMinHeightSingleLine)
        assertEquals(64.dp, KireiSpacing.rowMinHeightTwoLine)
        assertEquals(0.6.dp, KireiElevation.strokeWidth)
        assertEquals(.30f, KireiElevation.e2ShadowAlpha(isDark = true), .001f)
        assertEquals(.10f, KireiElevation.e2ShadowAlpha(isDark = false), .001f)
        // Spec §2.3 (r4 S1): ten tonal tiles (tinted container + same-hue glyph) derived in OKLCH; the
        // values, equal lightness and glyph contrast are pinned in KireiTileColorTest. Every hue stays
        // distinct so each root category keeps its own color.
        assertEquals(10, KireiTileColor.entries.size)
        assertEquals(Color(0xFFFDE1ED), KireiTileColor.PINK.container(isDark = false))
        assertEquals(Color(0xFF7B52A4), KireiTileColor.PURPLE.glyph(isDark = false))
        assertEquals(KireiTileColor.entries.size, KireiTileColor.entries.map { it.hue }.distinct().size)
        assertEquals(KireiTileColor.entries.size, KireiTileColor.entries.map { it.container(isDark = true) }.distinct().size)
    }

    @Test
    fun typographyAndMotionMatchSpecSections6And7() {
        assertEquals(FontWeight.Bold, AppleTypography.displayLarge.fontWeight)
        assertEquals(34f, AppleTypography.displayLarge.fontSize.value, .001f)
        assertEquals(41f, AppleTypography.displayLarge.lineHeight.value, .001f)
        assertEquals(FontWeight.SemiBold, AppleTypography.headlineMedium.fontWeight)
        assertEquals(FontWeight.Medium, AppleTypography.bodyLarge.fontWeight)
        assertEquals(13f, AppleTypography.bodySmall.fontSize.value, .001f)
        assertEquals(0.02.em, AppleTypography.labelLarge.letterSpacing)
        listOf(AppleTypography.numericLarge, AppleTypography.numericMedium, AppleTypography.numericSmall).forEach {
            assertEquals("tnum", it.fontFeatureSettings)
        }
        assertEquals(listOf(120, 200, 320), with(AppleMotion.Duration) { listOf(SHORT, MEDIUM, LONG) })
        assertEquals(androidx.compose.animation.core.spring<Float>(dampingRatio = .82f, stiffness = 300f), AppleMotion.Spring.Emphasized)
        assertEquals(androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 380f), AppleMotion.Spring.Default)
        assertEquals(androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 700f), AppleMotion.Spring.Press)
    }
}
