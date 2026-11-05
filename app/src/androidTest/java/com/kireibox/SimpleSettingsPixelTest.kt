package com.kireibox

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kireibox.app.LauncherActivity
import com.kireibox.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run at normal and enlarged display density; verifies actual Compose navigation and visible rows. */
@RunWith(AndroidJUnit4::class)
class SimpleSettingsPixelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun simplifiedRootAndCoverControlsRemainReachable() = withSettings { vm ->
        awaitText("外观")
        assertFalse(texts().contains("游戏库"))
        assertFalse(texts().contains("封面来源账号"))
        click("封面与元数据")
        awaitText("NextMoe")
        assertEquals(KireiSettingsPage.COVERS, vm.state.value.settingsPage)
        assertFalse(texts().any { it in listOf("默认来源", "Bangumi 镜像地址", "同时查找多个来源", "仅 Wi-Fi 自动匹配", "仅标题一致时自动设置", "清除无结果记录") })
        click("封面来源账号")
        assertEquals(KireiSettingsPage.COVER_ACCOUNTS, vm.state.value.settingsPage)
        instrumentation.runOnMainSync { vm.navigateUpSettings() }
        awaitText("NextMoe")
        assertEquals(KireiSettingsPage.COVERS, vm.state.value.settingsPage)
    }

    @Test fun homeControlsLiveInAppearanceAndScanRootsReturnToStorage() = withSettings { vm ->
        click("外观")
        awaitText("首页样式")
        assertEquals(KireiSettingsPage.APPEARANCE, vm.state.value.settingsPage)
        instrumentation.runOnMainSync { vm.openSettingsPage(KireiSettingsPage.INTERFACE) }
        assertEquals(KireiSettingsPage.APPEARANCE, vm.state.value.settingsPage)
        instrumentation.runOnMainSync { vm.openSettingsPage(KireiSettingsPage.LIBRARY_HUB) }
        awaitText("扫描目录")
        assertEquals(KireiSettingsPage.PERMISSIONS, vm.state.value.settingsPage)
        click("扫描目录")
        awaitText("添加扫描目录")
        assertEquals(KireiSettingsPage.LIBRARY, vm.state.value.settingsPage)
        instrumentation.runOnMainSync { vm.navigateUpSettings() }
        awaitText("扫描目录")
        assertEquals(KireiSettingsPage.PERMISSIONS, vm.state.value.settingsPage)
    }

    private fun withSettings(check: (KireiViewModel) -> Unit) {
        ActivityScenario.launch<LauncherActivity>(Intent(instrumentation.targetContext, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use { scenario ->
            lateinit var vm: KireiViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[KireiViewModel::class.java] }
            val deadline = System.currentTimeMillis() + 15000
            while (vm.state.value.settings == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertNotNull(vm.state.value.settings)
            scenario.onActivity { vm.openSettingsPage(KireiSettingsPage.ROOT) }
            check(vm)
        }
    }

    private fun click(text: String) {
        awaitText(text)
        var node = nodes().first { it.text?.toString() == text }
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue("click $text", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        Thread.sleep(250)
    }

    private fun awaitText(text: String) {
        val deadline = System.currentTimeMillis() + 10000
        var swipes = 0
        while (System.currentTimeMillis() < deadline) {
            if (text in texts()) return
            Thread.sleep(300)
            if (++swipes % 3 == 0) {
                val dm = instrumentation.targetContext.resources.displayMetrics
                val command = "input swipe ${dm.widthPixels / 2} ${(dm.heightPixels * .75).toInt()} ${dm.widthPixels / 2} ${(dm.heightPixels * .35).toInt()} 250"
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
                instrumentation.waitForIdleSync()
            }
        }
        fail("Missing $text; visible: ${texts()}")
    }

    private fun texts() = nodes().mapNotNull { it.text?.toString() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        fun visit(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
            if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { visit(root.getChild(it)) }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }
}
