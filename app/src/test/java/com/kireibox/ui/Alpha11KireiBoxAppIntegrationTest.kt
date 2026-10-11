package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavioral contract for the single Alpha 11 shell integration owner. */
class Alpha11KireiBoxAppIntegrationTest {
    @Test
    fun sourceRowsAreMutedOnlyWhenUnselectedAndBangumiFamilyControlsTokenVisibility() {
        val defaultVndb = Alpha11KireiBoxAppIntegration.sourcePresentation(CoverSourceMode.VNDB, null)
        val bangumi = Alpha11KireiBoxAppIntegration.sourcePresentation(CoverSourceMode.BANGUMI, CoverSourceMode.VNDB)
        val selectedBangumi = Alpha11KireiBoxAppIntegration.sourcePresentation(CoverSourceMode.BANGUMI, CoverSourceMode.BANGUMI)

        assertFalse(defaultVndb.isHighlighted)
        assertTrue(defaultVndb.muted)
        assertFalse(bangumi.muted)
        assertTrue(bangumi.enabled)
        assertTrue(bangumi.accessible)
        assertTrue(selectedBangumi.isHighlighted)
        assertFalse(selectedBangumi.muted)
        assertTrue(Alpha11KireiBoxAppIntegration.tokenVisible(CoverSourceMode.BANGUMI))
        assertFalse(Alpha11KireiBoxAppIntegration.tokenVisible(CoverSourceMode.BANGUMI_MIRROR))
        assertFalse(Alpha11KireiBoxAppIntegration.tokenVisible(CoverSourceMode.VNDB))
    }

    @Test
    fun badgeKeepsRealCountWhileRenderingAnAdaptive22DpCapsule() {
        val hidden = Alpha11KireiBoxAppIntegration.badge(0)
        val nine = Alpha11KireiBoxAppIntegration.badge(9)
        val large = Alpha11KireiBoxAppIntegration.badge(1000)

        assertFalse(hidden.visible)
        assertTrue(nine.visible)
        assertEquals("9", nine.label)
        assertEquals(9, nine.accessibilityCount)
        assertEquals("99+", large.label)
        assertEquals(1000, large.accessibilityCount)
        assertEquals(22f, large.heightDp, .0001f)
        assertEquals(22f, large.minWidthDp, .0001f)
        assertTrue(large.horizontalPaddingDp > 0f)
    }

    @Test
    fun taskButtonKeeps48DpClickTargetWhileBadgeIsAnUnclippedNonClickableSibling() {
        val layout = Alpha11KireiBoxAppIntegration.taskButtonLayout(1000)

        assertEquals(48f, layout.buttonSizeDp, .0001f)
        assertEquals(CoverTaskBadgePlacement.OUTER_BOX_SIBLING, layout.badgePlacement)
        assertFalse(layout.badgeClippedByButton)
        assertFalse(layout.badgeConsumesClicks)
        assertTrue(layout.badgeHasIndependentSemantics)
        assertEquals("99+", layout.badgeLabel)
        assertEquals(1000, layout.badgeAccessibilityCount)
        assertEquals(22f, layout.badgeHeightDp, .0001f)
    }

    @Test
    fun sourceChoiceUsesRadioButtonSelectionStateWithoutChangingPresentationPolicy() {
        val selected =
            Alpha11KireiBoxAppIntegration.sourceAccessibility(
                source = CoverSourceMode.BANGUMI,
                selectedSource = CoverSourceMode.BANGUMI,
            )
        val muted =
            Alpha11KireiBoxAppIntegration.sourceAccessibility(
                source = CoverSourceMode.YMGAL,
                selectedSource = CoverSourceMode.BANGUMI,
            )

        assertEquals(CoverSourceAccessibilityRole.RADIO_BUTTON, selected.role)
        assertTrue(selected.selected)
        assertEquals("已选择", selected.stateDescription)
        assertTrue(selected.enabled)
        assertTrue(selected.tokenVisible)

        assertEquals(CoverSourceAccessibilityRole.RADIO_BUTTON, muted.role)
        assertFalse(muted.selected)
        assertEquals("未选择", muted.stateDescription)
        assertFalse(muted.enabled)
        assertEquals(CoverSourcePresentationPolicy.MUTED_ALPHA, muted.alpha, .0001f)
        assertFalse(muted.tokenVisible)
    }

    @Test
    fun shellUsesTheFourBoundedBitmapBudgets() {
        assertEquals(8 * 1024, Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.THUMBNAIL))
        assertEquals(24 * 1024, Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.LIBRARY_CARD))
        assertEquals(20 * 1024, Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.HERO))
        assertEquals(12 * 1024, Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.IMMERSIVE_BACKDROP))
    }

    @Test
    fun dockUsesFixedHalfScaleAndAnOpaqueStableFallback() {
        val glass =
            Alpha11KireiBoxAppIntegration.dockHaze(
                isDark = true,
                reducedTransparency = false,
                hazeAvailable = true,
                apiSupportsHaze = true,
            )
        val fallback =
            Alpha11KireiBoxAppIntegration.dockHaze(
                isDark = true,
                reducedTransparency = true,
                hazeAvailable = true,
                apiSupportsHaze = true,
            )

        assertEquals(dev.chrisbanes.haze.HazeInputScale.Fixed(.5f), glass.inputScale)
        assertFalse(glass.useOpaqueFallback)
        assertTrue(fallback.useOpaqueFallback)
        assertEquals(null, fallback.inputScale)
        assertTrue(fallback.backgroundColor.alpha > .9f)
    }
}
