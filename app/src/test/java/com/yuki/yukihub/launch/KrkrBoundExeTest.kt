package com.yuki.yukihub.launch

import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.scanner.EngineDetector
import com.yuki.yukihub.storage.StorageNode
import com.yuki.yukihub.storage.StorageNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KrkrBoundExeTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun anXp3AppendedAfterTheLastSectionIsFound() {
        assertTrue(bound(pe(imageSize = 0x30000, overlay = archive())))
        assertEquals(0x30000L, KrkrBoundExe.overlayStart(pe(imageSize = 0x30000, overlay = ByteArray(0))))
    }

    @Test fun anXp3AfterOtherAppendedDataIsFound() {
        assertTrue(bound(pe(imageSize = 0x30000, overlay = ByteArray(0x12345 / 16 * 16) + archive())))
        assertTrue("mark past the first read block", bound(pe(imageSize = 0x800, overlay = ByteArray(100_000 / 16 * 16) + archive())))
    }

    @Test fun plainWindowsProgramsAndOtherFilesAreRejected() {
        assertFalse(bound(pe(imageSize = 0x30000, overlay = ByteArray(0))))
        assertFalse("an xp3 inside a section is not a bound archive", bound(pe(imageSize = 0x30000, overlay = ByteArray(0), inImage = archive())))
        assertFalse("misaligned mark", bound(pe(imageSize = 0x30000, overlay = ByteArray(3) + archive())))
        assertFalse(bound(archive()))
        assertFalse(bound(ByteArray(0)))
    }

    @Test fun anExeWithoutReadablePeHeadersIsSearchedLikeKrkr() {
        val stub = ByteArray(0x200)
        stub[0] = 'M'.code.toByte()
        stub[1] = 'Z'.code.toByte()
        assertTrue(bound(stub + archive()))
    }

    @Test fun aChineseKrkrExeBecomesTheLaunchFileBesideDataXp3() {
        val root = temporaryFolder.newFolder("Game")
        root.resolve("data.xp3").writeBytes(archive())
        root.resolve("game.exe").writeBytes(pe(imageSize = 0x1000, overlay = ByteArray(0)))
        root.resolve("game_chs.exe").writeBytes(pe(imageSize = 0x1000, overlay = archive()))
        root.resolve("unins000.exe").writeBytes(pe(imageSize = 0x1000, overlay = archive()))
        val result = detect(root)
        assertEquals(EngineType.KIRIKIRI, result.engine)
        assertEquals("game_chs.exe", result.launchTarget)
        assertEquals(listOf("game_chs.exe", "data.xp3"), result.xp3Candidates)
        assertEquals("data.xp3", EngineDetector.detect(StorageNodes.fromFile(root), 2).launchTarget)
    }

    @Test fun aFolderOfOnlyAKrkrExeIsAKrkrGame() {
        val root = temporaryFolder.newFolder("Single")
        root.resolve("Single.exe").writeBytes(pe(imageSize = 0x1000, overlay = archive()))
        val result = detect(root)
        assertEquals(EngineType.KIRIKIRI, result.engine)
        assertEquals("Single.exe", result.launchTarget)
        val plain = temporaryFolder.newFolder("Plain")
        plain.resolve("Plain.exe").writeBytes(pe(imageSize = 0x1000, overlay = ByteArray(0)))
        assertEquals(EngineType.UNKNOWN, detect(plain).engine)
    }

    @Test fun thePickerListsKrkrExesWithTheArchives() {
        val root = temporaryFolder.newFolder("Picker")
        root.resolve("data.xp3").writeBytes(archive())
        root.resolve("game.exe").writeBytes(pe(imageSize = 0x1000, overlay = ByteArray(0)))
        root.resolve("game_chs.exe").writeBytes(pe(imageSize = 0x1000, overlay = archive()))
        val node = StorageNodes.fromFile(root)
        assertEquals(listOf("data.xp3", "game_chs.exe"), KrkrLaunchTargetScanner.list(node, 1, ".xp3", probe(root)))
        assertEquals(listOf("data.xp3"), KrkrLaunchTargetScanner.list(node, 1))
    }

    private fun detect(root: File) = EngineDetector.detect(StorageNodes.fromFile(root), 2, null, probe(root))

    private fun probe(root: File): (StorageNode) -> Boolean = { node -> root.resolve(node.name).inputStream().use(KrkrBoundExe::isBound) }

    private fun bound(bytes: ByteArray) = KrkrBoundExe.isBound(ByteArrayInputStream(bytes))

    /** A minimal XP3: mark, index offset, empty index. */
    private fun archive(): ByteArray {
        val mark = byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
        return mark + le(8, 19L) + byteArrayOf(0) + le(8, 0L)
    }

    /** One-section PE whose raw data ends at [imageSize], followed by [overlay]. */
    private fun pe(
        imageSize: Int,
        overlay: ByteArray,
        inImage: ByteArray = ByteArray(0),
    ): ByteArray {
        val image = ByteArray(imageSize)
        image[0] = 'M'.code.toByte()
        image[1] = 'Z'.code.toByte()
        le(4, 0x80L).copyInto(image, 0x3c)
        "PE\u0000\u0000".toByteArray(Charsets.ISO_8859_1).copyInto(image, 0x80)
        le(2, 1L).copyInto(image, 0x80 + 6)
        le(2, 0xe0L).copyInto(image, 0x80 + 20)
        val section = 0x80 + 24 + 0xe0
        le(4, (imageSize - 0x400).toLong()).copyInto(image, section + 16)
        le(4, 0x400L).copyInto(image, section + 20)
        inImage.copyInto(image, 0x800)
        return image + overlay
    }

    private fun le(
        width: Int,
        value: Long,
    ): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array().copyOf(width)
}
