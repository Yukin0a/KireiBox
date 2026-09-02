package com.yuki.yukihub.scanner

import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.storage.StorageNodes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileStorageEngineDetectorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun physicalFolderUsesTheSharedKrkrDetector() {
        val root = temporaryFolder.newFolder("ATRI")
        root.resolve("data.xp3").writeBytes(byteArrayOf(0))
        root.resolve("startup.tjs").writeText("/* test */")
        val result = EngineDetector.detect(StorageNodes.fromFile(root), 2)
        assertEquals(EngineType.KIRIKIRI, result.engine)
        assertEquals("data.xp3", result.launchTarget)
    }

    @Test fun physicalFolderUsesTheSharedTyranoDetector() {
        val root = temporaryFolder.newFolder("Tyrano")
        root.resolve("index.html").writeText("<html></html>")
        root.resolve("tyrano").mkdirs()
        root.resolve("tyrano/tyrano.css").writeText("")
        val result = EngineDetector.detect(StorageNodes.fromFile(root), 2)
        assertEquals(EngineType.TYRANO, result.engine)
    }

    @Test fun appAsarWithoutResourcesIsDetectedAsTyrano() {
        val root = temporaryFolder.newFolder("AsarGame")
        root.resolve("app.asar").writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        val result = EngineDetector.detect(StorageNodes.fromFile(root), 2)
        assertEquals(EngineType.TYRANO, result.engine)
        assertEquals("[游戏目录]", result.launchTarget)
    }

    @Test fun resourcesAppAsarIsDetectedAsTyrano() {
        val root = temporaryFolder.newFolder("AsarWrapped")
        root.resolve("resources").mkdirs()
        root.resolve("resources/app.asar").writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        val result = EngineDetector.detect(StorageNodes.fromFile(root), 2)
        assertEquals(EngineType.TYRANO, result.engine)
        assertEquals("[游戏目录]", result.launchTarget)
    }
}
