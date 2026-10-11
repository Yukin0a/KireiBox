package com.kireibox.ui

import androidx.compose.ui.graphics.Color
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.dangerContainer
import com.kireibox.designsystem.kireiColorsFromTokens
import com.kireibox.designsystem.onDanger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the alpha.17 color/token sweep (design spec §2, §5, §9): shell chrome reads every color
 * from the tokens, selection tints use accentContainer, and the only literal page colors left are
 * the documented engine default-cover gradients.
 */
class ColorTokenSweepContractTest {
    private val chromeFiles =
        listOf(
            "KireiCommonUi.kt",
            "HazeLiquidActionButton.kt",
            "SheetsPages.kt",
            "KireiShell.kt",
            "LibraryCard.kt",
            "CoverTaskCenter.kt",
            "CoverPickerPages.kt",
            // r5 · B: the cover picker's results and the Dock's 快速设置 dot.
            "CoverPickerResults.kt",
            "DockSettingsBadge.kt",
            "PickerPages.kt",
            // r6: the pickers' shared frame and the app-icon loader.
            "PickerScaffold.kt",
            "AndroidAppIcons.kt",
            "SettingsComponents.kt",
            "SettingsCoverPages.kt",
            "SettingsKrkrPages.kt",
            "ImmersiveHomeCards.kt",
            // P3–P5 pages and their helpers (takeover review): held to the same rules so a literal
            // color added later fails here instead of passing silently.
            "SettingsOverviewCard.kt",
            "SettingsImmersiveVisual.kt",
            "SettingsSegmentedRow.kt",
            "SettingsAppearancePage.kt",
            // P3 settings template, root, choice rows, accounts and About pages.
            "SettingsScaffold.kt",
            "SettingsRootPage.kt",
            "SettingsChoiceRows.kt",
            "SettingsCoverAccounts.kt",
            "SettingsAboutPage.kt",
            "SettingsLibraryPages.kt",
            "SettingsPages.kt",
            // r4 S1: the 首页 page, preset cards and their drawn preview, and the 快速设置 guide.
            "SettingsHomePage.kt",
            "SettingsImmersivePresets.kt",
            "ImmersivePresetPreview.kt",
            "SettingsQuickSetup.kt",
            // r4 · S2: settings search (capsule, results, row anchors and their highlight).
            "SettingsSearchPage.kt",
            "SettingsSearchAnchors.kt",
            "SettingsSearchState.kt",
            "LibraryGrid.kt",
            "LibraryHeaders.kt",
            "LibraryPages.kt",
            "Branding.kt",
            "ImmersiveHomeStates.kt",
            "ImmersiveHomeFx.kt",
            "ImmersiveAmbientParticles.kt",
            "ImmersiveBackdropTint.kt",
        )

    private val accentAlpha = Regex("""accent\.copy\(\s*alpha""")

    @Test
    fun shellChromeHasNoLiteralColors() {
        // P2 moved shared chrome into designsystem/components and the toasts into KireiNotices.kt;
        // they are held to the same no-literal rule as the shell files they came from.
        val sources = chromeFiles.map { it to UiSourceFiles.file(it) } + ("KireiNotices.kt" to UiSourceFiles.file("KireiNotices.kt")) + ("components/" to UiSourceFiles.components())
        sources.forEach { (name, source) ->
            assertFalse("$name: Color(0x literal", source.contains("Color(0x"))
            assertFalse("$name: Color.White literal", source.contains("Color.White"))
            assertFalse("$name: Color.Black literal", source.contains("Color.Black"))
        }
    }

    @Test
    fun selectionTintsUseAccentContainerInsteadOfAccentAlpha() {
        chromeFiles.forEach { name ->
            assertFalse("$name: accent.copy(alpha = ...) tint", accentAlpha.containsMatchIn(UiSourceFiles.file(name)))
        }
        assertFalse("components: accent.copy(alpha = ...) tint", accentAlpha.containsMatchIn(UiSourceFiles.components()))
        // P2: sheet action rows render through KireiListRow, which now owns the accent/danger tile tints.
        assertTrue(UiSourceFiles.file("SheetsPages.kt").contains("KireiListRow("))
        val listRow = UiSourceFiles.designSystemFile("components/KireiListRow.kt")
        assertTrue(listRow.contains("colors.accentContainer"))
        assertTrue(listRow.contains("colors.dangerContainer"))
    }

    @Test
    fun onlyEngineCoverGradientsKeepLiteralColorsInPages() {
        val dashboard = UiSourceFiles.file("DashboardPages.kt")
        val literalLines = dashboard.lines().filter { it.contains("Color(0x") }
        assertEquals("five engine gradients, two stops each", 5, literalLines.size)
        assertTrue(literalLines.all { it.trimStart().startsWith("val ") && it.contains(") to Color(0x") })
        assertFalse("engine gradients are no longer purple", dashboard.contains("0xFF716BD8") || dashboard.contains("0xFF8B67C9"))
    }

    @Test
    fun sheetsAndPromptsUseOverlayContainerAndScrimTokens() {
        // P2: every sheet renders through the shared KireiSheet, which owns the container/scrim tokens.
        val sheet = UiSourceFiles.designSystemFile("components/KireiSheet.kt")
        // Grouped sheets (game actions, 添加游戏) take the grouped page background in light mode only.
        assertTrue(sheet.contains("val sheetColor = if (spec.grouped && !colors.isDark) colors.background else colors.surfaceOverlay"))
        assertTrue(sheet.contains("scrimColor = colors.scrim"))
        assertTrue(UiSourceFiles.file("SheetsPages.kt").contains("KireiSheet("))
        assertTrue(UiSourceFiles.designSystemFile("components/KireiDialog.kt").contains("setDimAmount(colors.scrim.alpha)"))
        assertEquals(2, Regex("""\.background\(colors\.scrim\)""").findAll(UiSourceFiles.file("KireiShell.kt")).count())
    }

    @Test
    fun derivedDangerTokensFollowTheContainerAlphas() {
        val dark = kireiColorsFromTokens(isDark = true)
        val light = kireiColorsFromTokens(isDark = false)
        assertEquals(dark.danger.copy(alpha = .16f), dark.dangerContainer)
        assertEquals(light.danger.copy(alpha = .12f), light.dangerContainer)
        assertEquals(Color.White, dark.onDanger)
        assertEquals(KireiTokens.OnSolid, light.onDanger)
    }

    @Test
    fun coverToneFallbackIsNoLongerPurple() {
        // A hue check instead of repeating CoverToneExtractorTest's exact-value pin: any violet/blue
        // purple fallback (240°–300°) would fail, not only the one retired literal.
        val argb = CoverToneExtractor.DEFAULT_FALLBACK_ARGB
        val hsv = CoverToneExtractor.rgbToHsv(((argb ushr 16) and 0xFF) / 255f, ((argb ushr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f)
        assertTrue("fallback hue ${hsv[0]} must not be purple", hsv[0] !in 240f..300f)
        assertNotEquals(0xFF5B5AC8.toInt(), argb)
    }
}
