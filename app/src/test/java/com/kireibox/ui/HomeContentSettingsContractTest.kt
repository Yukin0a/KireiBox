package com.kireibox.ui

import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeContentSettingsContractTest {
    @Test
    fun panelUsesSettingsGroupsAndSegmentedControlsInsteadOfBespokeChoiceCards() {
        val source = source("app/src/main/java/com/kireibox/ui/HomeContentSettings.kt")

        // Same vocabulary as the settings pages: captioned KireiListGroup cards, inline KireiSegmented
        // for short choices, radio rows only where every option needs its sentence.
        assertTrue(source.contains("KireiListGroup("))
        assertTrue(source.contains("KireiSegmented("))
        assertTrue(source.contains("Role.RadioButton"))
        listOf("GlassSurface(", "IconButton(", "RoundedCornerShape(", "Color(0x", "HomeContentChoiceCard").forEach { forbidden ->
            assertFalse("$forbidden is gone from the panel", source.contains(forbidden))
        }
        // The old width-driven card grid is gone with its policy.
        assertFalse(source.contains("HomeContentSettingsLayout"))
    }

    @Test
    fun settingsNormalizeManualOrderSourceAndCountContract() {
        val normalized =
            HomeContentSettings(
                mode = SpotlightSelectionMode.MANUAL,
                manualGameIds = listOf(3L, 3L, 0L, 1L, 2L, 4L, 5L),
                source = SpotlightSource.FIXED,
                strategy = SpotlightStrategy.DAILY_RANDOM,
                count = 99,
            ).normalized()

        assertEquals(listOf(3L, 1L, 2L, 4L, 5L), normalized.manualGameIds)
        assertEquals(SpotlightSource.RECENT, normalized.source)
        assertEquals(SpotlightStrategy.DAILY_RANDOM, normalized.strategy)
        assertEquals(5, normalized.count)
    }

    @Test
    fun manualGamesRenderSelectedOrderBeforeRemainingLibrary() {
        val games =
            listOf(
                Game(id = 1L, title = "One"),
                Game(id = 2L, title = "Two"),
                Game(id = 3L, title = "Three"),
            )

        assertEquals(
            listOf(3L, 1L, 2L),
            HomeContentSettingsPolicy.orderedManualGames(games, listOf(3L, 1L)).map { it.id },
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            HomeContentSettingsPolicy.moveManualId(listOf(3L, 1L, 2L), from = 0, to = 2),
        )
    }

    @Test
    fun dashboardSheetWiresSharedPanelThroughDraftOnlySavePath() {
        val source = UiSourceFiles.all()

        assertTrue(source.contains("HomeContentSettingsPanel("))
        assertTrue(source.contains("onSettings = { next ->"))
        assertTrue(source.contains("DashboardLayouts.setSpotlightManual"))
        assertTrue(source.contains("DashboardLayouts.setSpotlightAutomatic"))
        assertTrue(source.contains("expanded = true"))
        assertFalse(source.contains("SpotlightModeOption("))
        assertFalse(source.contains("SpotlightMaxPagesRow("))
    }

    @Test
    fun settingsPanelOwnsScrollAndGroupsHeadersWithTheirFirstContent() {
        val panel = source("app/src/main/java/com/kireibox/ui/HomeContentSettings.kt")
        val app = UiSourceFiles.all()

        assertTrue(panel.contains("LazyColumn("))
        assertTrue(panel.contains("fillMaxHeight()"))
        assertTrue(panel.contains("item(key = \"selection-settings-section\")"))
        assertTrue(panel.contains("item(key = \"manual-settings-section\")"))
        assertTrue(panel.contains("item(key = \"source-settings-section\")"))
        assertTrue(panel.contains("item(key = \"strategy-settings-section\")"))
        assertTrue(app.contains("scrollableContent = false"))
    }

    @Test
    fun sharedPanelBoundsLongTextAndKeepsCountAboveSystemBottomInset() {
        val source = source("app/src/main/java/com/kireibox/ui/HomeContentSettings.kt")

        // Formatting-insensitive: the inset chain may legitimately wrap across lines,
        // so assert both semantic tokens inside the bottom-padding computation block.
        // The old delimiter ("val navigationBarBottom = " on one line) no longer existed, so
        // substringAfter returned the whole file; the block is now the declaration up to the next
        // statement, with the marker asserted.
        val marker = Regex("""val navigationBarBottom\s*=""").find(source) ?: error("navigationBarBottom declaration missing")
        val blockEnd = source.indexOf(".calculateBottomPadding()", marker.range.last)
        assertTrue("bottom inset computation missing", blockEnd > marker.range.last)
        val bottomPaddingBlock = source.substring(marker.range.last + 1, blockEnd)
        assertTrue(bottomPaddingBlock.contains("navigationBars"))
        assertTrue(bottomPaddingBlock.contains("asPaddingValues"))
        assertTrue(source.contains("calculateBottomPadding()"))
        assertTrue(source.contains("top = 16.dp"))
        assertTrue(source.contains("bottom = navigationBarBottom + 24.dp"))
        assertTrue(source.contains("testTag(\"home-content-settings-count\")"))
        // Descriptions and titles stay bounded inside the rows (KireiListRow ellipsizes titles at 2 lines).
        assertTrue(source.contains("subtitleMaxLines = 3"))
        assertTrue(source.contains("subtitleMaxLines = 1"))
        assertTrue(source.contains("最近打开按时间排序，其余来源使用稳定顺序"))
        assertFalse(source.contains("最近打开按时间，其余来源使用稳定顺序"))
    }

    private fun source(relativePath: String): String {
        val root =
            listOf(File("."), File(".."), File("../.."))
                .firstOrNull { File(it, "app/src/main").isDirectory }
                ?: error("KireiBox workspace not found from ${File("").absolutePath}")
        val file = File(root, relativePath)
        // UI source may have been split out of KireiBoxApp.kt into sibling files.
        return if (file.isFile) file.readText() else UiSourceFiles.all()
    }
}
