package com.yuki.yukihub.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Xp3EntrySelectorTest {

    private fun entries(vararg names: String): List<Xp3EntrySelector.Entry> =
        names.mapIndexed { index, name -> Xp3EntrySelector.Entry(name, 1024L * (index + 1)) }

    @Test
    fun `title-named archive wins over alphabetical asset packs`() {
        val list = entries(
            "bgimage.xp3", "bgm.xp3", "evimage.xp3", "patch.xp3",
            "stimage.xp3", "voice.xp3", "恋爱，我就借走了.xp3",
        )
        assertEquals(
            "恋爱，我就借走了.xp3",
            Xp3EntrySelector.select(list, folderName = "恋爱，我就借走了"),
        )
    }

    @Test
    fun `data xp3 is the conventional default`() {
        val list = entries("bgimage.xp3", "data.xp3", "voice.xp3")
        assertEquals("data.xp3", Xp3EntrySelector.select(list, folderName = "随便什么目录"))
    }

    @Test
    fun `no title match picks the largest non-asset archive`() {
        val list = entries("bgimage.xp3", "bgm.xp3", "voice.xp3", "patch.xp3")
        // patch.xp3 has the largest size (index 3) and is not an asset pack.
        assertEquals("patch.xp3", Xp3EntrySelector.select(list, folderName = "game-xyz"))
    }

    @Test
    fun `only asset packs return null for directory-mode fallback`() {
        val list = entries("bgimage.xp3", "bgm.xp3", "evimage.xp3", "voice.xp3")
        assertNull(Xp3EntrySelector.select(list, folderName = "game-xyz"))
    }

    @Test
    fun `single non-asset archive is returned directly`() {
        assertEquals(
            "data.xp3",
            Xp3EntrySelector.select(entries("data.xp3"), folderName = "game"),
        )
    }

    @Test
    fun `empty input returns null`() {
        assertNull(Xp3EntrySelector.select(emptyList(), folderName = "game"))
    }

    @Test
    fun `asset target detection`() {
        assertTrue(Xp3EntrySelector.isAssetTarget("bgimage.xp3"))
        assertTrue(Xp3EntrySelector.isAssetTarget("dir/bgm.xp3"))
        assertFalse(Xp3EntrySelector.isAssetTarget("data.xp3"))
        assertFalse(Xp3EntrySelector.isAssetTarget("恋爱，我就借走了.xp3"))
        assertFalse(Xp3EntrySelector.isAssetTarget("[游戏目录]"))
        assertFalse(Xp3EntrySelector.isAssetTarget(null))
    }

    @Test
    fun `patch xp3 is a valid candidate, not an asset pack`() {
        assertFalse(Xp3EntrySelector.isAssetName("patch.xp3"))
    }
}