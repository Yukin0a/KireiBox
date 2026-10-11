package com.kireibox

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kireibox.app.LauncherActivity
import com.kireibox.cover.KireiCoverStore
import com.kireibox.ui.KireiSettingsPage
import com.kireibox.ui.KireiTab
import com.kireibox.ui.KireiViewModel
import com.yuki.yukihub.auth.CoverAccounts
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.data.MetadataRepository
import com.yuki.yukihub.metadata.client.BangumiClient
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.YukiSettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real emulator checks. No successful account authorization is fabricated here. */
@RunWith(AndroidJUnit4::class)
class CoverAccountPixelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun anonymousBangumiAndVerifiedCoverPreserveManualChoiceAndOldMetadata() =
        runBlocking {
            val results = BangumiClient.searchCandidates("ATRI", "", 3, BangumiClient.OFFICIAL_BASE, false)
            assertTrue("Bangumi anonymous response", results.isNotEmpty())
            val candidate = results.first { it.id == "297264" }
            val repo = GameRepository(context)
            val fixture = Game(title = "ATRI [封面验收]", rootUri = "file:///kireibox-cover-test-${System.nanoTime()}", engine = EngineType.UNKNOWN)
            repo.insert(fixture)
            try {
                val store = KireiCoverStore(context)
                val uri = store.confirm(fixture.id, CoverSourceMode.BANGUMI, candidate, "MANUAL", 100.0).getOrThrow()
                val file = File(Uri.parse(uri).path!!)
                assertTrue(file.length() > 0)
                val savedBytes = file.readBytes()
                val old = VnMetadata(id = "historical", originalTitle = "历史月幕资料")
                MetadataRepository(context).saveYmgal(fixture.id, old)
                assertEquals("historical", MetadataRepository(context).getYmgal(fixture.id)?.id)
                val auto = store.confirm(fixture.id, CoverSourceMode.BANGUMI, candidate, "AUTO", 100.0).getOrThrow()
                assertEquals(uri, auto)
                assertEquals("MANUAL", repo.findById(fixture.id)?.vndbBindingMode)
                assertArrayEquals(savedBytes, file.readBytes())
                val bad = VnMetadata(id = "bad", coverUrl = "https://api.bgm.tv/v0/me")
                assertTrue(store.confirm(fixture.id, CoverSourceMode.BANGUMI, bad).isFailure)
                assertEquals(uri, repo.findById(fixture.id)?.coverUri)
                assertArrayEquals(savedBytes, file.readBytes())
                assertEquals("297264", MetadataRepository(context).getBangumi(fixture.id)?.id)
            } finally {
                repo.delete(fixture.id)
                File(context.filesDir, "covers/yukihub/game_${fixture.id}").deleteRecursively()
            }
        }

    @Test fun sourceMigrationRetainsValidSelectionsAndNeverDeletesHistoricalRows() {
        val prefs = context.getSharedPreferences(YukiSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
        val key = YukiSettingsStore.KEY_METADATA_SOURCE
        val previous = prefs.getString(key, null)
        try {
            prefs.edit().remove(key).commit()
            assertEquals("bangumi", YukiSettingsStore(context).snapshot().metadataSource)
            listOf("vndb", "hikarinagi", "nextmoe", "bangumi").forEach {
                prefs.edit().putString(key, it).commit()
                assertEquals(it, YukiSettingsStore(context).snapshot().metadataSource)
            }
            listOf("ymgal", "bangumi_mirror").forEach {
                prefs.edit().putString(key, it).commit()
                assertEquals("bangumi", YukiSettingsStore(context).snapshot().metadataSource)
            }
        } finally {
            prefs.edit().apply { if (previous == null) remove(key) else putString(key, previous) }.commit()
        }
    }

    @Test fun keystoreStorageIsEncryptedAndDisconnectInvalidatesPendingWrites() {
        val accounts = CoverAccounts.get(context)
        val source = CoverSourceMode.NEXTMOE
        org.junit.Assume.assumeFalse("Do not alter a real user's account", accounts.status.value[source]?.connected == true)
        val epoch = accounts.generation(source)
        try {
            accounts.saveOAuth(source, "storage-fixture-access", "storage-fixture-refresh", 900, epoch)
            val raw = context.getSharedPreferences("kireibox_cover_accounts", Context.MODE_PRIVATE).getString(source.key, "").orEmpty()
            assertTrue(raw.isNotEmpty())
            assertFalse(raw.contains("storage-fixture"))
            assertEquals("storage-fixture-access", accounts.accessToken(source))
            accounts.disconnect(source)
            assertFalse(accounts.canSearch(source))
            assertNull(context.getSharedPreferences("kireibox_cover_accounts", Context.MODE_PRIVATE).getString(source.key, null))
            assertTrue(runCatching { accounts.saveOAuth(source, "late", "late-refresh", 900, epoch) }.isFailure)
        } finally {
            accounts.disconnect(source)
        }
    }

    @Test fun invalidPersonalTokenDoesNotConnectAnAccount() {
        val accounts = CoverAccounts.get(context)
        val before = accounts.status.value[CoverSourceMode.BANGUMI]
        val error = runCatching { accounts.connectBangumi("kireibox-invalid-token-for-negative-test") }.exceptionOrNull()
        assertTrue("Expected token rejection, got ${error?.javaClass?.simpleName}: ${error?.message}", error?.message.orEmpty().contains("Token 无效"))
        assertEquals(before, accounts.status.value[CoverSourceMode.BANGUMI])
    }

    @Test fun coverSettingsAndLibraryRemainAccessibleWithoutAccounts() {
        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            val dialogDeadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < dialogDeadline) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val ok = root?.findAccessibilityNodeInfosByText("OK")?.firstOrNull { it.text?.toString() == "OK" }
                if (ok != null) {
                    ok.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    break
                }
                Thread.sleep(100)
            }
            var vm: KireiViewModel? = null
            scenario.onActivity { vm = ViewModelProvider(it)[KireiViewModel::class.java] }
            val deadline = System.currentTimeMillis() + 15000
            while (vm!!.state.value.settings == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            scenario.onActivity { vm!!.openSettingsPage(KireiSettingsPage.COVERS) }
            instrumentation.waitForIdleSync()
            Thread.sleep(500)
            assertTrue(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName)
            saveScreenshot("cover-settings.png")
            assertEquals(KireiSettingsPage.COVERS, vm!!.state.value.settingsPage)
            var node = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.text?.contains("默认来源") == true }
            while (!node.isClickable && node.parent != null) node = node.parent
            assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            Thread.sleep(500)
            val root = instrumentation.uiAutomation.rootInActiveWindow
            listOf("Bangumi", "NextMoe", "Hikarinagi", "VNDB").forEach { assertTrue(it, nodes(root).any { node -> node.text?.contains(it) == true }) }
            assertTrue(nodes(root).none { it.text?.contains("月幕") == true })
            assertTrue(nodes(root).none { it.text?.contains("镜像") == true })
            saveScreenshot("source-order.png")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            scenario.onActivity { vm!!.selectTab(KireiTab.GAMES) }
            instrumentation.waitForIdleSync()
            assertEquals(KireiTab.GAMES, vm!!.state.value.tab)
            saveScreenshot("library-without-login.png")
        }
    }

    private fun nodes(root: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> =
        if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }

    private fun saveScreenshot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "cover-test-evidence").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
