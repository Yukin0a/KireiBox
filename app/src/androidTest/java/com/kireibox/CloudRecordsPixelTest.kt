package com.kireibox

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kireibox.app.LauncherActivity
import com.kireibox.ui.KireiSettingsPage
import com.kireibox.ui.KireiViewModel
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.sync.CloudPlaytimeWork
import com.yuki.yukihub.sync.PlaytimeGame
import com.yuki.yukihub.sync.PlaytimeLedgerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Cloud snapshots stay in memory; temporary local fixtures are removed after each check. */
@RunWith(AndroidJUnit4::class)
class CloudRecordsPixelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun cloudOnlyRecordAppearsInPlayHistoryAndOpensCloudManagement() {
        val game = Game(title = "Alpha19 本机绑定验收", totalPlayTime = 60_000L, engine = com.yuki.yukihub.model.EngineType.TYRANO, rootUri = "test://alpha19-records-${System.nanoTime()}")
        val repo = com.yuki.yukihub.data.GameRepository(instrumentation.targetContext)
        repo.insert(game)
        val rows = listOf(PlaytimeGame(game.id, game.title.orEmpty(), 1, PlaytimeLedgerRow(workId = "100")))
        val cloud = listOf(CloudPlaytimeWork("100", 120), CloudPlaytimeWork("200", 300, VnMetadata(chineseTitle = "云端独有作品")))
        val prefs = instrumentation.targetContext.getSharedPreferences(com.yuki.yukihub.settings.YukiSettingsStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val orientationKey = com.yuki.yukihub.settings.YukiSettingsStore.KEY_ORIENTATION_MODE
        val previousOrientation = prefs.getString(orientationKey, null)
        prefs.edit().putString(orientationKey, com.yuki.yukihub.settings.OrientationMode.PORTRAIT.storedValue).commit()
        try {
            ActivityScenario.launch<LauncherActivity>(
                android.content.Intent(
                    instrumentation.targetContext,
                    LauncherActivity::class.java
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ).use {
                    scenario ->
                var vm: KireiViewModel? = null
                scenario.onActivity { vm = androidx.lifecycle.ViewModelProvider(it)[KireiViewModel::class.java] }
                val deadline = System.currentTimeMillis() + 15000
                while ((vm!!.state.value.settings == null || vm!!.state.value.games.none { it.id == game.id }) && System.currentTimeMillis() < deadline) Thread.sleep(100)
                val expectedCount = vm!!.state.value.games.size + 1
                scenario.onActivity { vm!!.openSettingsPage(KireiSettingsPage.ROOT) }
                instrumentation.waitForIdleSync()
                Thread.sleep(700)
                click("游戏时长")
                instrumentation.waitForIdleSync()
                Thread.sleep(700)
                scenario.onActivity { vm!!.mutableState.value = vm!!.mutableState.value.copy(playtimeLibrary = rows, cloudPlaytimes = cloud) }
                instrumentation.waitForIdleSync()
                val nodes = awaitNodes { all -> all.any { it.text?.contains("游玩记录 · $expectedCount 部游戏") == true } && all.any { it.text?.contains("云端独有作品") == true } }
                val dir = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "alpha19-evidence").apply { mkdirs() }
                instrumentation.uiAutomation.takeScreenshot()?.let {
                        bitmap ->
                    java.io.File(dir, "alpha19-r2-cloud-records.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
                assertTrue(nodes.joinToString(" | ") { "${it.text}/${it.contentDescription}" }, nodes.any { it.text?.contains("游玩记录 · $expectedCount 部游戏") == true })
                assertTrue(nodes.any { it.text?.contains("仅云端") == true })
                assertTrue(nodes.any { it.text?.contains("5 小时") == true })
                click("云端独有作品")
                instrumentation.waitForIdleSync()
                assertEquals(KireiSettingsPage.PLAYTIME_SYNC, vm!!.state.value.settingsPage)
            }
        } finally {
            repo.delete(game.id)
            prefs.edit().apply { if (previousOrientation == null) remove(orientationKey) else putString(orientationKey, previousOrientation) }.commit()
        }
    }

    private fun click(text: String) {
        val all = awaitNodes { all -> all.any { it.text?.toString()?.startsWith(text) == true || it.contentDescription?.toString()?.startsWith(text) == true } }
        var node = all.firstOrNull { it.text?.toString()?.startsWith(text) == true || it.contentDescription?.toString()?.startsWith(text) == true } ?: error("Missing $text: " + all.map { it.text })
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun awaitNodes(ready: (List<android.view.accessibility.AccessibilityNodeInfo>) -> Boolean): List<android.view.accessibility.AccessibilityNodeInfo> {
        val deadline = System.currentTimeMillis() + 15000
        var all = nodes(instrumentation.uiAutomation.rootInActiveWindow)
        while (!ready(all) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            all = nodes(instrumentation.uiAutomation.rootInActiveWindow)
        }
        return all
    }

    @Test fun actualNextmoeCatalogWorksWithoutLogin() {
        val accounts = com.yuki.yukihub.auth.CoverAccounts.get(instrumentation.targetContext)
        org.junit.Assume.assumeTrue(accounts.status.value[com.yuki.yukihub.metadata.model.CoverSourceMode.NEXTMOE]?.connected != true)
        assertTrue(accounts.canSearch(com.yuki.yukihub.metadata.model.CoverSourceMode.NEXTMOE))
        val hits = com.yuki.yukihub.metadata.client.AccountCatalogClient(accounts).searchNextmoe("ATRI", 3, "vndb:v27448")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { "vndb:v27448" in it.externalRefs && it.coverUrl.startsWith("https://") })
        val repo = com.yuki.yukihub.data.GameRepository(instrumentation.targetContext)
        val fixture = Game(title = "Alpha19 图片评级验收", rootUri = "test://alpha19-rating-${System.nanoTime()}", engine = com.yuki.yukihub.model.EngineType.UNKNOWN)
        repo.insert(fixture)
        try {
            val store = com.kireibox.cover.KireiCoverStore(instrumentation.targetContext)
            val settings = com.yuki.yukihub.settings.YukiSettingsStore(instrumentation.targetContext)
            val candidate = VnMetadata.fromJson(hits.first().toJson().toString())!!

            // Synthetic image ratings exercise persistence, not the real ATRI image's content.
            fun rate(
                score: Double,
                origin: String = "vndb"
            ) {
                candidate.coverSexual = score
                candidate.covers.clear()
                candidate.covers += com.yuki.yukihub.metadata.model.MetadataCover(candidate.coverUrl, source = origin, sexual = score.toString())
            }
            kotlinx.coroutines.runBlocking {
                rate(2.0)
                store.confirm(fixture.id, com.yuki.yukihub.metadata.model.CoverSourceMode.NEXTMOE, candidate).getOrThrow()
                assertTrue(repo.findById(fixture.id)!!.nsfw)
                rate(0.0)
                store.confirm(fixture.id, com.yuki.yukihub.metadata.model.CoverSourceMode.NEXTMOE, candidate).getOrThrow()
                assertFalse(repo.findById(fixture.id)!!.nsfw)
                rate(1.0, "ai_redraw")
                store.confirm(fixture.id, com.yuki.yukihub.metadata.model.CoverSourceMode.NEXTMOE, candidate).getOrThrow()
                assertFalse(repo.findById(fixture.id)!!.nsfw)
                settings.markNsfwDetected(fixture.id)
                rate(2.0)
                store.confirm(fixture.id, com.yuki.yukihub.metadata.model.CoverSourceMode.NEXTMOE, candidate).getOrThrow()
                assertFalse(repo.findById(fixture.id)!!.nsfw)
            }
        } finally {
            repo.delete(fixture.id)
        }
    }

    private fun nodes(root: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> =
        if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
}
