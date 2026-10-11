package com.kireibox.ui

import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LibraryOverviewPolicyTest {
    @Test fun recentSortPutsPlayedGamesFirstThenUsesTitle() {
        val never = Game(id = 1, title = "Never", lastPlayedAt = 0)
        val beta = Game(id = 2, title = "Beta", lastPlayedAt = 20)
        val alpha = Game(id = 3, title = "Alpha", lastPlayedAt = 20)
        assertEquals(
            listOf(3L, 2L, 1L),
            LibraryOverviewPolicy.sorted(listOf(never, beta, alpha), LibraryOverviewSort.RECENT).map { it.id }
        )
    }

    @Test fun playTimeSortUsesTitleForTies() {
        val short = Game(id = 1, title = "Short", totalPlayTime = 60)
        val beta = Game(id = 2, title = "Beta", totalPlayTime = 300)
        val alpha = Game(id = 3, title = "Alpha", totalPlayTime = 300)
        assertEquals(
            listOf(3L, 2L, 1L),
            LibraryOverviewPolicy.sorted(listOf(short, beta, alpha), LibraryOverviewSort.PLAY_TIME).map { it.id }
        )
    }

    @Test fun compactTimeTreatsStoredDurationsAsMilliseconds() {
        assertEquals("0 分钟", LibraryOverviewPolicy.compactPlayTime(-1))
        assertEquals("0 分钟", LibraryOverviewPolicy.compactPlayTime(0))
        assertEquals("1 分钟", LibraryOverviewPolicy.compactPlayTime(7_200))
        assertEquals("1 分钟", LibraryOverviewPolicy.compactPlayTime(30_000))
        assertEquals("1 分钟", LibraryOverviewPolicy.compactPlayTime(59_000))
        assertEquals("1 分钟", LibraryOverviewPolicy.compactPlayTime(60_000))
        assertEquals("5 分钟", LibraryOverviewPolicy.compactPlayTime(300_000))
        assertEquals("45 分钟", LibraryOverviewPolicy.compactPlayTime(2_700_000))
        assertEquals("1 小时", LibraryOverviewPolicy.compactPlayTime(3_600_000))
        assertEquals("1.5 小时", LibraryOverviewPolicy.compactPlayTime(5_400_000))
        assertEquals("12.5 小时", LibraryOverviewPolicy.compactPlayTime(45_000_000))
        // Rounds to one decimal, and a whole result never shows ".0".
        assertEquals("2 小时", LibraryOverviewPolicy.compactPlayTime(7_199_999))
        assertEquals("52 小时", LibraryOverviewPolicy.compactPlayTime(187_200_000))
    }

    @Test fun summaryTimeTreatsStoredDurationsAsMilliseconds() {
        assertEquals("0 分钟", LibraryOverviewPolicy.summaryPlayTime(-1))
        assertEquals("0 分钟", LibraryOverviewPolicy.summaryPlayTime(0))
        assertEquals("0 分钟", LibraryOverviewPolicy.summaryPlayTime(7_200))
        assertEquals("0 分钟", LibraryOverviewPolicy.summaryPlayTime(59_000))
        assertEquals("1 分钟", LibraryOverviewPolicy.summaryPlayTime(60_000))
        assertEquals("5 分钟", LibraryOverviewPolicy.summaryPlayTime(300_000))
        assertEquals("1 小时", LibraryOverviewPolicy.summaryPlayTime(3_600_000))
        assertEquals("1 小时", LibraryOverviewPolicy.summaryPlayTime(5_400_000))
        assertEquals("2 小时", LibraryOverviewPolicy.summaryPlayTime(7_200_000))
    }

    @Test fun immersiveTimeUsesReadableChineseThresholds() {
        assertEquals("尚未游玩", LibraryOverviewPolicy.immersivePlayTime(-1))
        assertEquals("尚未游玩", LibraryOverviewPolicy.immersivePlayTime(0))
        assertEquals("不足 1 分钟", LibraryOverviewPolicy.immersivePlayTime(1))
        assertEquals("不足 1 分钟", LibraryOverviewPolicy.immersivePlayTime(59_999))
        assertEquals("已游玩 1 分钟", LibraryOverviewPolicy.immersivePlayTime(60_000))
        assertEquals("已游玩 59 分钟", LibraryOverviewPolicy.immersivePlayTime(3_599_999))
        assertEquals("已游玩 1 小时", LibraryOverviewPolicy.immersivePlayTime(3_600_000))
        assertEquals("已游玩 1 小时 5 分钟", LibraryOverviewPolicy.immersivePlayTime(3_900_000))
    }

    @Test fun overviewSheetResetsToTheFirstItemForSortChangesAndReselection() {
        val source = UiSourceFiles.all()
        val sheet =
            source.substringAfter("fun LibraryOverviewSheet(")
                .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertTrue(sheet.contains("val listState = rememberLazyListState()"))
        assertTrue(sheet.contains("LaunchedEffect(sort)"))
        assertTrue(sheet.contains("scope.launch { listState.scrollToItem(0) }"))
        assertTrue(sheet.contains("state = listState"))
        assertTrue(sheet.contains("itemsIndexed(orderedGames, key = { _, game -> game.id })"))
        assertTrue(sheet.contains("游玩记录 · \${games.size} 部游戏"))
    }

    @Test fun dockPaddingIncludesInsetsAndClearance() {
        assertEquals(106f, LibraryOverviewPolicy.dockContentPaddingDp(0f), .001f)
        assertEquals(130f, LibraryOverviewPolicy.dockContentPaddingDp(24f), .001f)
        assertEquals(106f, LibraryOverviewPolicy.dockContentPaddingDp(-4f), .001f)
    }

    @Test fun emptyStateIsCenteredAndHasNoGlowLayer() {
        // Scope to the library overview sheet; an immersive gallery empty state shares the same
        // `orderedGames.isEmpty()` guard elsewhere in the package.
        val source =
            UiSourceFiles.all()
                .substringAfter("fun LibraryOverviewSheet(")
        val emptyState =
            source
                .substringAfter("if (orderedGames.isEmpty()) {")
                .substringBefore("} else {")

        assertTrue(emptyState.contains("Color.Transparent"))
        assertTrue(emptyState.contains("contentAlignment = Alignment.Center"))
        assertTrue(emptyState.contains("Text(\"游戏库还没有内容\""))
        assertFalse(emptyState.contains("LibraryOverviewEmptyGlow"))
        assertFalse(emptyState.contains("radialGradient"))
        assertFalse(emptyState.contains("drawBehind"))
        assertFalse(emptyState.contains("Brush."))
    }

    @Test fun pixel8Landscape914x411FinalRowStopsAboveRenderedDock() {
        val metrics =
            resolveAdaptiveUiMetrics(
                windowWidthDp = 914f,
                windowHeightDp = 411f,
                safeLeftDp = 0f,
                safeTopDp = 0f,
                safeRightDp = 0f,
                safeBottomDp = 24f,
            )
        val dock = resolveDockRenderRect(metrics.dockRect)
        val padding = resolveLibraryGridBottomContentPaddingDp(411f, metrics.dockRect, 24f)

        assertEquals(dock.y - 8f, 411f - padding, .001f)
        assertTrue(padding >= 24f + 8f)
    }

    @Test fun wideLandscape2358x1080EquivalentFinalRowStopsAboveRenderedDock() {
        val metrics =
            resolveAdaptiveUiMetrics(
                windowWidthDp = 2358f,
                windowHeightDp = 1080f,
                safeLeftDp = 0f,
                safeTopDp = 0f,
                safeRightDp = 0f,
                safeBottomDp = 24f,
            )
        val dock = resolveDockRenderRect(metrics.dockRect)
        val padding = resolveLibraryGridBottomContentPaddingDp(1080f, metrics.dockRect, 24f)

        assertEquals(dock.y - 8f, 1080f - padding, .001f)
        assertTrue(padding >= 24f + 8f)
    }

    @Test fun androidAppFallbackLeavesOneEngineLabelToTheLibraryCard() {
        val source = UiSourceFiles.all()
        val card = UiSourceFiles.file("LibraryCard.kt")

        assertEquals("Android 应用", libraryCardEngineLabel(EngineType.ANDROID_APP))
        assertEquals(1, LibraryCardLayoutPolicy.ENGINE_LABELS_PER_CARD)
        // The shared cover artwork still draws the engine name by default; the library card has its own
        // cover (skeleton / fallback glyph) so the meta line under the title is the card's only engine label.
        assertTrue(source.contains("showFallbackMetadata: Boolean = true"))
        assertFalse("the card does not use the labelled fallback", card.contains("GameCoverArtwork("))
        assertFalse("no engine pill on top of the cover", card.contains("KireiShapes.xs"))
        assertEquals("the meta line is the one place the engine is named", 1, card.split("\$engineLabel · ").size - 1)
        assertTrue("the meta line is shown in both orientations", !card.contains("if (!compact)"))
    }

    @Test fun shortLandscape2400x1080UsesNearSquareCoverAndPortraitKeepsRatio() {
        assertTrue(LibraryCardLayoutPolicy.isShortLandscape(2400f, 1080f))
        assertTrue(LibraryCardLayoutPolicy.coverAspectRatio(true) in .90f..1.0f)
        assertEquals(.94f, LibraryCardLayoutPolicy.coverAspectRatio(true), .001f)
        assertEquals(.72f, LibraryCardLayoutPolicy.coverAspectRatio(false), .001f)
    }

    @Test fun longTitlesStayEllipsizedWithAtLeastSixDpBelowTheCover() {
        val source = UiSourceFiles.all()
        val card = gameCardSource(source)

        assertEquals(1, LibraryCardLayoutPolicy.titleMaxLines(true))
        assertEquals(2, LibraryCardLayoutPolicy.titleMaxLines(false))
        assertEquals(6f, LibraryCardLayoutPolicy.titleGapDp(true), .001f)
        assertEquals(9f, LibraryCardLayoutPolicy.titleGapDp(false), .001f)
        assertTrue(card.contains("TextOverflow.Ellipsis"))
        assertTrue(card.contains("LibraryCardLayoutPolicy.titleMaxLines(compact)"))
        assertTrue(card.contains("LibraryCardLayoutPolicy.titleGapDp(compact).dp"))
    }

    @Test fun oneAndFourCardRowsBothUseTheSameDockClearanceContract() {
        assertEquals(1, LibraryCardLayoutPolicy.rowCount(itemCount = 1, columnCount = 4))
        assertEquals(1, LibraryCardLayoutPolicy.rowCount(itemCount = 4, columnCount = 4))
        assertEquals(2, LibraryCardLayoutPolicy.rowCount(itemCount = 5, columnCount = 4))

        val metrics = resolveAdaptiveUiMetrics(2400f, 1080f, 0f, 0f, 0f, 24f)
        val dock = resolveDockRenderRect(metrics.dockRect)
        val padding = resolveLibraryGridBottomContentPaddingDp(1080f, metrics.dockRect, 24f)
        assertTrue(metrics.libraryColumns >= 4)
        assertEquals(dock.y - 8f, 1080f - padding, .001f)
        assertTrue(padding >= 8f)
    }

    private fun source(relativePath: String): String {
        val root =
            listOf(File("."), File(".."), File("../.."))
                .firstOrNull { File(it, "app/src/main").isDirectory }
                ?: error("KireiBox workspace not found from ${File("").absolutePath}")
        val file = File(root, relativePath)
        // The shell was split into several files; fall back to the whole ui package when the
        // requested file no longer exists.
        return if (file.isFile) file.readText() else UiSourceFiles.all()
    }

    private fun gameCardSource(source: String): String {
        // End at the next top-level @Composable rather than a specific following function, so the
        // slice survives the shell being split across files.
        val start = source.indexOf("fun GameCard(")
        require(start >= 0) { "GameCard missing" }
        val end = source.indexOf("\n@Composable", start + 1).let { if (it < 0) source.length else it }
        return source.substring(start, end)
    }
}
