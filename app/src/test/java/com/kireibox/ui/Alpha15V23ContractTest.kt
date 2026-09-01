package com.kireibox.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Alpha15V23ContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/$relative"), File("../app/src/main/$relative"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("$relative not found from ${File("").absolutePath}")
    }

    @Test
    fun manualCoverSearchDoesNotPersistReviewOrRetryBeforeUserChoice() {
        val source = source("java/com/kireibox/ui/KireiViewModel.kt")
        val body = source.substringAfter("fun searchCover(").substringBefore("fun dismissCoverSearch(")

        assertFalse(body.contains("markBinding("))
        assertFalse(body.contains("vndbBindingMode ="))
        assertTrue(source.contains("resolvePersistedCoverImmediately("))
        assertTrue(source.contains("coverQueue.resolvePersistedCover("))
    }

    @Test
    fun sortChoiceLivesOnLibraryHubAndNumberedEditorIsWiredInBothLayouts() {
        val source = source("java/com/kireibox/ui/KireiBoxApp.kt")
        val hub = source.substringAfter("private fun LibraryHubSettings(").substringBefore("private fun InterfaceSettings(")
        val scan = source.substringAfter("private fun LibrarySettings(").substringBefore("private fun PermissionSettings(")

        assertTrue(hub.contains("\"游戏库顺序\""))
        assertFalse(scan.contains("\"游戏库顺序\""))
        assertTrue(source.split("LibraryEditSortMenu(").size >= 4)
        assertTrue(source.contains("依次点按卡片，设置 1、2、3… 顺序"))
        assertTrue(source.contains("customOrderPosition.toString()"))
    }

    @Test
    fun artemisUsesOneAnchoredCheckedDropdownInsteadOfInlineActionRows() {
        val source = source("java/com/kireibox/ui/KireiBoxApp.kt")
        val block = source.substringAfter("if (game.engine == EngineType.ARTEMIS)")
            .substringBefore("Spacer(Modifier.height(6.dp))")

        assertTrue(block.contains("DropdownMenu("))
        assertTrue(block.contains("ArtemisVersion.entries"))
        assertTrue(block.contains("Icons.Rounded.Check"))
        assertFalse(block.contains("AnimatedVisibility("))
    }
}
