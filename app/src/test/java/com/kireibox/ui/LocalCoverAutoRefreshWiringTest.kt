package com.kireibox.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalCoverAutoRefreshWiringTest {
    @Test
    fun coverPageExposesAlbumAndImageFileActions() {
        val app = UiSourceFiles.all()
        val activity = activitySource()
        assertTrue(app.contains("\"从相册选择\""))
        assertTrue(app.contains("\"从手机文件选择\""))
        assertTrue(app.contains("actions.chooseCoverFromAlbum"))
        assertTrue(app.contains("actions.chooseCoverFromFiles"))
        assertTrue(activity.contains("ActivityResultContracts.PickVisualMedia()"))
        assertTrue(activity.contains("coverFilePicker.launch(arrayOf(\"image/*\"))"))
    }

    @Test
    fun launcherResumeAndSettingsSwitchesAreWiredToAutomaticRefresh() {
        val app = UiSourceFiles.all()
        val activity = activitySource()
        assertTrue(activity.contains("viewModel.onAppForeground()"))
        assertTrue(app.contains("\"目录自动更新\""))
        assertTrue(app.contains("viewModel::setAutoScanOnStartup"))
        assertTrue(app.contains("\"已移除游戏更新目录不会再添加\""))
        assertTrue(app.contains("viewModel::setKeepRemovedGamesHiddenOnRefresh"))
    }

    /** Concatenation of the app shell sources; the launcher was split out of LauncherActivity.kt. */
    private fun activitySource(): String {
        val dir =
            listOf(
                File("src/main/java/com/kireibox/app"),
                File("../app/src/main/java/com/kireibox/app"),
            ).firstOrNull(File::isDirectory)
                ?: error("app source dir not found")
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .joinToString("\n") { it.readText() }
    }
}
