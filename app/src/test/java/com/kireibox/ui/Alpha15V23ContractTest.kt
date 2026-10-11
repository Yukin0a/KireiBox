package com.kireibox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Alpha15V23ContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/$relative"), File("../app/src/main/$relative"))
        return candidates.firstOrNull(File::isFile)?.readText()
            // The shell was split into several files; fall back to the whole ui package so wiring
            // assertions keep working wherever a call site now lives.
            ?: UiSourceFiles.all()
    }

    @Test
    fun manualCoverSearchDoesNotPersistReviewOrRetryBeforeUserChoice() {
        val source = source("java/com/kireibox/ui/KireiViewModel.kt")

        assertTrue(source.contains("resolvePersistedCoverImmediately("))
        assertTrue(source.contains("coverQueue.resolvePersistedCover("))
    }

    @Test
    fun sortChoiceLivesOnLibraryHubAndNumberedEditorIsWiredInBothLayouts() {
        val source = UiSourceFiles.all()
        val hub = source.substringAfter("fun LibraryHubSettings(").substringBefore("fun InterfaceSettings(")
        val scan = source.substringAfter("fun LibrarySettings(").substringBefore("fun PermissionSettings(")

        assertTrue(hub.contains("\"游戏库顺序\""))
        assertFalse(scan.contains("\"游戏库顺序\""))
        assertTrue(source.split("LibraryEditSortMenu(").size >= 4)
        assertTrue(source.contains("依次点按卡片，设置 1、2、3… 顺序"))
        assertTrue(source.contains("customOrderPosition.toString()"))
    }

    @Test
    fun artemisUsesOneAnchoredCheckedDropdownInsteadOfInlineActionRows() {
        val source = UiSourceFiles.all()
        val block =
            source.substringAfter("if (game.engine == EngineType.ARTEMIS)")
                .substringBefore("Spacer(Modifier.height(6.dp))")

        // P2: the anchored dropdown is the themed KireiMenu (a DropdownMenu wrapper) and the
        // selected version is flagged on its KireiMenuItem, which draws the trailing check.
        assertTrue(block.contains("KireiMenu("))
        assertTrue(block.contains("ArtemisVersion.entries"))
        assertTrue(block.contains("selected = version == selectedArtemisVersion"))
        assertFalse(block.contains("AnimatedVisibility("))
        val menu = UiSourceFiles.designSystemFile("components/KireiMenu.kt")
        assertTrue("KireiMenu stays an anchored dropdown", menu.contains("DropdownMenu("))
        assertTrue("the selected item shows the check icon", menu.contains("Icons.Rounded.Check"))
    }
}
