package com.kireibox.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCoverAutoRefreshWiringTest {
    private fun source(module: String, relative: String): String {
        val candidates = listOf(
            File("$module/src/main/$relative"),
            File("../$module/src/main/$relative"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("$relative not found from ${File("").absolutePath}")
    }

    @Test
    fun coverPageExposesAlbumAndImageFileActions() {
        val app = source("app", "java/com/kireibox/ui/KireiBoxApp.kt")
        val activity = source("app", "java/com/apps/LauncherActivity.kt")
        assertTrue(app.contains("\"从相册选择\""))
        assertTrue(app.contains("\"从手机文件选择\""))
        assertTrue(app.contains("actions.chooseCoverFromAlbum"))
        assertTrue(app.contains("actions.chooseCoverFromFiles"))
        assertTrue(activity.contains("ActivityResultContracts.PickVisualMedia()"))
        assertTrue(activity.contains("coverFilePicker.launch(arrayOf(\"image/*\"))"))
    }

    @Test
    fun launcherResumeAndSettingsSwitchesAreWiredToAutomaticRefresh() {
        val app = source("app", "java/com/kireibox/ui/KireiBoxApp.kt")
        val activity = source("app", "java/com/apps/LauncherActivity.kt")
        assertTrue(activity.contains("viewModel.onAppForeground()"))
        assertTrue(app.contains("\"目录自动更新\""))
        assertTrue(app.contains("viewModel::setAutoScanOnStartup"))
        assertTrue(app.contains("\"已移除游戏更新目录不会再添加\""))
        assertTrue(app.contains("viewModel::setKeepRemovedGamesHiddenOnRefresh"))
    }
}
