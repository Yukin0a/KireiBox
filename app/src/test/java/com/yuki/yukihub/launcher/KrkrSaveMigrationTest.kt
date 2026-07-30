package com.yuki.yukihub.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class KrkrSaveMigrationTest {
    @Test
    fun `legacy migration copies missing files and never overwrites conflicts`() {
        val root = Files.createTempDirectory("kireibox-save-test").toFile()
        try {
            val source = root.resolve("legacy").apply { mkdirs() }
            val target = root.resolve("game/savedata").apply { mkdirs() }
            source.resolve("slot1.dat").writeText("legacy-one")
            source.resolve("nested").mkdirs()
            source.resolve("nested/slot2.dat").writeText("legacy-two")
            target.resolve("slot1.dat").writeText("current")

            val result = KrkrLauncher.copyMissingFilesRecursively(source, target)

            assertEquals(1, result.copied)
            assertEquals(1, result.conflicts)
            assertEquals("current", target.resolve("slot1.dat").readText())
            assertEquals("legacy-two", target.resolve("nested/slot2.dat").readText())
            assertTrue(source.resolve("slot1.dat").isFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
