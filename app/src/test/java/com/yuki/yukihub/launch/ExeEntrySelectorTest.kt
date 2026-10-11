package com.yuki.yukihub.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExeEntrySelectorTest {
    private fun entry(
        name: String,
        size: Long,
    ) = ExeEntrySelector.Entry(name, size)

    @Test
    fun theGameBeatsItsHelperTools() {
        val picked = ExeEntrySelector.select(listOf(entry("unins000.exe", 9_000), entry("vcredist_x86.exe", 9_000), entry("Game.exe", 100)), "Other")
        assertEquals("Game.exe", picked)
    }

    @Test
    fun anExeNamedLikeTheFolderWinsOverALargerOne() {
        val picked = ExeEntrySelector.select(listOf(entry("launcher.exe", 5_000), entry("Clannad.exe", 100)), "CLANNAD")
        assertEquals("Clannad.exe", picked)
    }

    @Test
    fun withoutANameMatchTheLargestFileIsTheGame() {
        assertEquals("b.exe", ExeEntrySelector.select(listOf(entry("a.exe", 10), entry("b.exe", 99)), "Folder"))
    }

    @Test
    fun onlyHelpersAreStillOffered() {
        assertEquals(listOf("setup.exe"), ExeEntrySelector.rank(listOf(entry("setup.exe", 1)), "x"))
        assertTrue(ExeEntrySelector.isAuxiliary("Uninstall.exe") || ExeEntrySelector.isAuxiliary("unins000.exe"))
        assertFalse(ExeEntrySelector.isAuxiliary("Game.exe"))
    }

    @Test
    fun aChineseBuildWinsOverTheOriginalAndTheFolderName() {
        val files = listOf(entry("Clannad.exe", 9_000), entry("Clannad_chs.exe", 100), entry("Clannad_cht.exe", 100))
        assertEquals(listOf("Clannad_chs.exe", "Clannad_cht.exe", "Clannad.exe"), ExeEntrySelector.rank(files, "Clannad"))
        assertEquals("汉化版启动.exe", ExeEntrySelector.select(listOf(entry("game.exe", 9_000), entry("汉化版启动.exe", 1)), "x"))
        assertEquals("game-zh_cn.exe", ExeEntrySelector.select(listOf(entry("game.exe", 9_000), entry("game-zh_cn.exe", 1)), "x"))
        assertEquals("GameCHS.exe", ExeEntrySelector.select(listOf(entry("Game.exe", 9_000), entry("GameCHS.exe", 1)), "x"))
    }

    @Test
    fun chineseTagsMustStandAloneOrEndTheName() {
        assertEquals(2, ExeEntrySelector.chineseRank("scene.exe"))
        assertEquals(2, ExeEntrySelector.chineseRank("Script.exe"))
        assertEquals(2, ExeEntrySelector.chineseRank("matchsticks.exe"))
        assertEquals(0, ExeEntrySelector.chineseRank("game_sc.exe"))
        assertEquals(1, ExeEntrySelector.chineseRank("game_繁體.exe"))
    }

    @Test
    fun aChineseHelperToolStillLosesToTheGame() {
        assertEquals("Game.exe", ExeEntrySelector.select(listOf(entry("setup_chs.exe", 9_000), entry("Game.exe", 1)), "x"))
    }

    @Test
    fun nothingToPickFromIsNull() {
        assertNull(ExeEntrySelector.select(emptyList(), "x"))
    }
}
