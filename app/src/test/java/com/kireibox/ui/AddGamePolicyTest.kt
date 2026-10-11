package com.kireibox.ui

import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.scanner.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddGamePolicyTest {
    private fun scan(
        engine: EngineType?,
        launch: String = "[游戏目录]",
        candidates: List<String>? = null,
        uri: String = "file:///sdcard/Games/Clannad",
    ) = ScanResult(title = "Clannad", uri = uri, engine = engine, confidence = 95, launchTarget = launch, xp3Candidates = candidates)

    @Test
    fun detectedEngineAndLaunchFileAreKept() {
        val candidate = AddGamePolicy.candidateFor(scan(EngineType.KIRIKIRI, "data.xp3", listOf("a.xp3", "data.xp3")))
        assertEquals(EngineType.KIRIKIRI, candidate.engine)
        assertEquals(EngineType.KIRIKIRI, candidate.detectedEngine)
        assertEquals("data.xp3", candidate.launchTarget)
        assertEquals(listOf("a.xp3", "data.xp3"), candidate.launchOptions)
    }

    @Test
    fun unrecognisedFolderNeedsAnEngineChoice() {
        val candidate = AddGamePolicy.candidateFor(scan(EngineType.UNKNOWN, ""))
        assertEquals(EngineType.UNKNOWN, candidate.engine)
        assertEquals(AddGamePolicy.DIRECTORY_TARGET, candidate.launchTarget)
    }

    @Test
    fun chosenEngineWinsAndDropsTheDetectorsLaunchFile() {
        val candidate = AddGamePolicy.candidateFor(scan(EngineType.KIRIKIRI, "data.xp3", listOf("data.xp3", "b.xp3")), chosen = EngineType.ONS)
        assertEquals(EngineType.ONS, candidate.engine)
        assertEquals(EngineType.KIRIKIRI, candidate.detectedEngine)
        assertEquals(AddGamePolicy.DIRECTORY_TARGET, candidate.launchTarget)
        assertTrue(candidate.launchOptions.isEmpty())
    }

    @Test
    fun chosenEngineMatchingDetectionKeepsIt() {
        val candidate = AddGamePolicy.candidateFor(scan(EngineType.KIRIKIRI, "data.xp3"), chosen = EngineType.KIRIKIRI)
        assertEquals("data.xp3", candidate.launchTarget)
    }

    @Test
    fun switchingEngineAwayAndBackRestoresTheDetectedLaunchFile() {
        val detected = AddGamePolicy.candidateFor(scan(EngineType.KIRIKIRI, "data.xp3"))
        val away = AddGamePolicy.withEngine(detected, EngineType.TYRANO)
        assertEquals(AddGamePolicy.DIRECTORY_TARGET, away.launchTarget)
        assertEquals("data.xp3", AddGamePolicy.withEngine(detected, EngineType.KIRIKIRI).launchTarget)
    }

    @Test
    fun aFolderPickedForWinlatorStartsItsLikeliestExe() {
        val found = scan(EngineType.KIRIKIRI, "data.xp3").apply { exeCandidates = listOf("game_chs.exe", "game.exe") }
        val candidate = AddGamePolicy.candidateFor(found, EngineType.WINDOWS)
        assertEquals(EngineType.WINDOWS, candidate.engine)
        assertEquals("Clannad", candidate.title)
        assertEquals("game_chs.exe", candidate.launchTarget)
        assertEquals(listOf("game_chs.exe", "game.exe"), candidate.launchOptions)
        assertFalse(AddGamePolicy.candidateFor(scan(EngineType.UNKNOWN, ""), EngineType.WINDOWS).ready)
    }

    @Test
    fun queueDropsDuplicateFoldersAndGamesAlreadyInTheLibrary() {
        val a = AddGamePolicy.candidateFor(scan(EngineType.ONS, uri = "file:///sdcard/Games/A"))
        val aAgain = a.copy(title = "A again")
        val b = AddGamePolicy.candidateFor(scan(EngineType.TYRANO, uri = "file:///sdcard/Games/B")).copy(inLibrary = true)
        val c = AddGamePolicy.candidateFor(scan(EngineType.ARTEMIS, uri = "file:///sdcard/Games/C"))
        val (queue, present) = AddGamePolicy.buildQueue(listOf(a, aAgain, b, c))
        assertEquals(listOf(a.rootUri, c.rootUri), queue.map { it.rootUri })
        assertEquals(1, present)
        assertFalse(queue.any { it.inLibrary })
    }

    @Test
    fun summaryReportsAddedAndSkipped() {
        assertEquals("没有添加游戏", AddGamePolicy.summary(AddGameUi()))
        assertEquals("已添加 3 个游戏", AddGamePolicy.summary(AddGameUi(addedCount = 3)))
        assertEquals("已添加 2 个游戏，跳过 1 个", AddGamePolicy.summary(AddGameUi(addedCount = 2, skippedCount = 1)))
    }

    @Test
    fun aFolderOfOnlyAnExeIsAWindowsGame() {
        val candidate = AddGamePolicy.candidateFor(scan(EngineType.WINDOWS, "game.exe").copy(exeCandidates = listOf("game.exe", "config.exe")))
        assertEquals(EngineType.WINDOWS, candidate.engine)
        assertEquals("game.exe", candidate.launchTarget)
        assertEquals(listOf("game.exe", "config.exe"), candidate.launchOptions)
        assertTrue(candidate.ready)
    }

    @Test
    fun anyFolderWithExesCanBeSwitchedToWindowsAndBack() {
        val krkr = AddGamePolicy.candidateFor(scan(EngineType.KIRIKIRI, "data.xp3").copy(exeCandidates = listOf("start.exe", "unins000.exe")))
        val windows = AddGamePolicy.withEngine(krkr, EngineType.WINDOWS)
        assertEquals("start.exe", windows.launchTarget)
        assertEquals(listOf("start.exe", "unins000.exe"), windows.launchOptions)
        val back = AddGamePolicy.withEngine(windows, EngineType.KIRIKIRI)
        assertEquals("the exe never leaks into the KRKR launch file", "data.xp3", back.launchTarget)
    }

    @Test
    fun aWindowsGameWithoutAnExeIsNotReady() {
        val candidate = AddGamePolicy.withEngine(AddGamePolicy.candidateFor(scan(EngineType.ONS)), EngineType.WINDOWS)
        assertFalse(candidate.ready)
        assertFalse(AddGamePolicy.candidateFor(scan(EngineType.UNKNOWN, "")).ready)
        assertFalse(AddGamePolicy.candidateFor(scan(EngineType.ONS)).copy(title = "  ").ready)
    }

    @Test
    fun onlyReadySelectedCandidatesAreImported() {
        val a = AddGamePolicy.candidateFor(scan(EngineType.ONS, uri = "file:///a"))
        val b = AddGamePolicy.candidateFor(scan(EngineType.TYRANO, uri = "file:///b"))
        val unknown = AddGamePolicy.candidateFor(scan(EngineType.UNKNOWN, "", uri = "file:///c"))
        val ui = AddGameUi(stage = AddGameStage.REVIEW, queue = listOf(a, b, unknown), selected = setOf(a.key, unknown.key))
        assertEquals(listOf(a), ui.importable)
        assertTrue(ui.listReview)
        assertFalse(ui.copy(selected = setOf(unknown.key)).canConfirm)
        assertFalse(ui.copy(queue = listOf(a)).listReview)
    }

    @Test
    fun theListShowsEngineAndLaunchFile() {
        val krkr = AddGamePolicy.candidateFor(scan(EngineType.KIRIKIRI, "sub/data.xp3"))
        assertEquals("KRKR · data.xp3", addGameSummary(krkr))
        assertEquals("ONS · 游戏目录", addGameSummary(AddGamePolicy.candidateFor(scan(EngineType.ONS))))
        assertEquals("未识别到引擎，点击修改", addGameSummary(AddGamePolicy.candidateFor(scan(EngineType.UNKNOWN, ""))))
    }
}
