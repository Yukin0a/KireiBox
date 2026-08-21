package com.kireibox.ui

import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentSettingsContractTest {
    @Test
    fun keepsPhonePortraitChoiceCardsInOneColumn() {
        assertEquals(
            HomeContentSettingsLayout.SINGLE_COLUMN,
            HomeContentSettingsPolicy.layoutFor(widthDp = 360f),
        )
        assertEquals(
            HomeContentSettingsLayout.SINGLE_COLUMN,
            HomeContentSettingsPolicy.layoutFor(widthDp = 411f),
        )
    }

    @Test
    fun twoColumnsStartAt600DpOnlyWhenCardsFit() {
        assertEquals(
            HomeContentSettingsLayout.TWO_COLUMNS,
            HomeContentSettingsPolicy.layoutFor(widthDp = 600f),
        )
        assertEquals(
            HomeContentSettingsLayout.SINGLE_COLUMN,
            HomeContentSettingsPolicy.layoutFor(widthDp = 600f, fontScale = 1.3f),
        )
        assertEquals(
            HomeContentSettingsLayout.SINGLE_COLUMN,
            HomeContentSettingsPolicy.layoutFor(widthDp = 600f, fontScale = 1.5f),
        )
        assertEquals(
            HomeContentSettingsLayout.TWO_COLUMNS,
            HomeContentSettingsPolicy.layoutFor(widthDp = 840f, fontScale = 1.2f),
        )
    }

    @Test
    fun settingsNormalizeManualOrderSourceAndCountContract() {
        val normalized = HomeContentSettings(
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
        val games = listOf(
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
        val source = source("app/src/main/java/com/kireibox/ui/KireiBoxApp.kt")

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
        val app = source("app/src/main/java/com/kireibox/ui/KireiBoxApp.kt")

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
        val bottomPaddingBlock =
            source.substringAfter("val navigationBarBottom = ")
        assertTrue(bottomPaddingBlock.contains("navigationBars"))
        assertTrue(bottomPaddingBlock.contains("asPaddingValues"))
        assertTrue(source.contains("calculateBottomPadding()"))
        assertTrue(source.contains("top = 16.dp"))
        assertTrue(source.contains("bottom = navigationBarBottom + 24.dp"))
        assertTrue(source.contains("testTag(\"home-content-settings-count\")"))
        assertTrue(source.contains("maxLines = 2"))
        assertTrue(source.contains("maxLines = 3"))
        assertTrue(source.contains("TextOverflow.Ellipsis"))
        assertTrue(source.contains("options.chunked(2)"))
        assertTrue(source.contains("最近打开按时间排序，其余来源使用稳定顺序"))
        assertFalse(source.contains("最近打开按时间，其余来源使用稳定顺序"))
    }

    private fun source(relativePath: String): String {
        val root = listOf(File("."), File(".."), File("../.."))
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("KireiBox workspace not found from ${File("").absolutePath}")
        return File(root, relativePath).readText()
    }
}
