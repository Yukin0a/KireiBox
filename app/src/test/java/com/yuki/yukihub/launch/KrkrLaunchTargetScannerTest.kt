package com.yuki.yukihub.launch

import com.yuki.yukihub.storage.StorageNodes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KrkrLaunchTargetScannerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun listsArchivesAndSkipsSaveData() {
        val root = temporaryFolder.newFolder("game")
        root.resolve("bgi_data.xp3").writeBytes(byteArrayOf(1))
        root.resolve("恋爱，我接走了了.xp3").writeBytes(byteArrayOf(1))
        root.resolve("data").mkdirs()
        root.resolve("data/patch.xp3").writeBytes(byteArrayOf(1))
        root.resolve("savedata").mkdirs()
        root.resolve("savedata/ignored.xp3").writeBytes(byteArrayOf(1))

        assertEquals(
            listOf("bgi_data.xp3", "data/patch.xp3", "恋爱，我接走了了.xp3"),
            KrkrLaunchTargetScanner.list(StorageNodes.fromFile(root)),
        )
    }

    @Test
    fun respectsDepthLimit() {
        val root = temporaryFolder.newFolder("deep")
        root.resolve("a/b/c/d").mkdirs()
        root.resolve("a/b/c/d/deep.xp3").writeBytes(byteArrayOf(1))

        assertEquals(emptyList<String>(), KrkrLaunchTargetScanner.list(StorageNodes.fromFile(root), 3))
        assertEquals(listOf("a/b/c/d/deep.xp3"), KrkrLaunchTargetScanner.list(StorageNodes.fromFile(root), 5))
    }
}
