package com.kireibox

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kireibox.app.LauncherActivity
import com.kireibox.designsystem.kireiColorsFromTokens
import com.kireibox.ui.CloudCoverDiskCache
import com.kireibox.ui.CloudWorkArtwork
import com.kireibox.ui.CoverDecodePolicy
import com.kireibox.ui.CoverImageRole
import com.kireibox.ui.KireiSettingsPage
import com.kireibox.ui.KireiViewModel
import com.kireibox.ui.decodeNetworkCover
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.sync.CloudPlaytimeWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Alpha19CloudCoverPixelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun accountRowOpensSeparatePageAndReturnsToCovers() {
        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            lateinit var vm: KireiViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[KireiViewModel::class.java] }
            val deadline = System.currentTimeMillis() + 15000
            while (vm.state.value.settings == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            scenario.onActivity { vm.openSettingsPage(KireiSettingsPage.COVERS) }
            instrumentation.waitForIdleSync()
            Thread.sleep(700)
            awaitLabel("自动同步游玩时长")
            screenshot("alpha19-cover-settings.png")
            var row = nodes().first { it.text?.contains("封面来源账号") == true }
            while (!row.isClickable && row.parent != null) row = row.parent
            assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            Thread.sleep(700)
            assertEquals(KireiSettingsPage.COVER_ACCOUNTS, vm.state.value.settingsPage)
            awaitLabel("Bangumi")
            assertFalse(nodes().any { it.text?.contains("自动同步游玩时长") == true })
            screenshot("alpha19-cover-accounts.png")
            scenario.onActivity { vm.navigateUpSettings() }
            assertEquals(KireiSettingsPage.COVERS, vm.state.value.settingsPage)
        }
    }

    @Test fun cachedImageDecodesOfflineAndMissingCoverShowsCloudIcon() =
        runBlocking(Dispatchers.IO) {
            val url = "http://127.0.0.1:1/alpha19-offline-cover.png"
            val file = File(context.cacheDir, "alpha19-cover-fixture.png")
            val bitmap = Bitmap.createBitmap(80, 112, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(160, 140, 220)) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val disk = CloudCoverDiskCache(File(context.cacheDir, "cloud-covers"))
            disk.put("nextmoe", url, file)
            val request = CoverDecodePolicy.request(url, CoverImageRole.THUMBNAIL, 80, 112, sourceKey = "nextmoe")
            try {
                assertNotNull(decodeNetworkCover(context, request))
                ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.setContent {
                            val colors = kireiColorsFromTokens(true)
                            MaterialTheme {
                                Column(Modifier.fillMaxSize().background(colors.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("云端封面验收", color = colors.text)
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        CloudWorkArtwork(CloudPlaytimeWork("cached", 128, VnMetadata(id = "cached", coverUrl = url)), Modifier.size(80.dp, 112.dp), false, colors)
                                        CloudWorkArtwork(CloudPlaytimeWork("missing", 0), Modifier.size(80.dp, 112.dp), false, colors)
                                    }
                                    Text("云端 2小时8分钟 · 已同步", color = colors.secondary)
                                }
                            }
                        }
                    }
                    instrumentation.waitForIdleSync()
                    Thread.sleep(1000)
                    assertTrue(nodes().any { it.contentDescription?.toString() == "云端封面" })
                    assertFalse(nodes().any { it.text?.toString() == "云" })
                    screenshot("alpha19-cloud-covers.png")
                }
            } finally {
                disk.find("nextmoe", url)?.delete()
                file.delete()
            }
        }

    private fun awaitLabel(label: String) {
        val deadline = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < deadline) {
            if (nodes().any { it.text?.contains(label) == true || it.contentDescription?.contains(label) == true }) return
            Thread.sleep(200)
        }
        screenshot("alpha19-navigation-failure.png")
        fail("Missing $label; visible: ${nodes().map { it.text ?: it.contentDescription }}")
    }

    private fun nodes(root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): List<AccessibilityNodeInfo> =
        if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }

    private fun screenshot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "alpha19-evidence").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()!!.let { bitmap ->
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
