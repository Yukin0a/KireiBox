package com.kireibox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kireibox.app.LauncherActivity
import com.kireibox.cover.KireiCoverStore
import com.kireibox.ui.KireiTab
import com.kireibox.ui.KireiViewModel
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.data.MetadataRepository
import com.yuki.yukihub.metadata.client.BangumiClient
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Host runs seed, disables emulator networking, stops the process, then runs verify. */
@RunWith(AndroidJUnit4::class)
class CoverOfflineProcessTest {
    @Test fun persistedCoverAcrossOfflineProcessRestart(): Unit =
        runBlocking {
            val step = InstrumentationRegistry.getArguments().getString("offlineStep")
            assumeTrue("Requires the two-process offline harness", step in listOf("seed", "verify"))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val repo = GameRepository(context)
            val marker = File(context.noBackupFilesDir, "cover-offline-test.json")
            if (step == "seed") {
                check(!marker.exists()) { "Previous offline fixture must be verified/cleaned first" }
                val candidate = BangumiClient.searchCandidates("ATRI", "", 3, BangumiClient.OFFICIAL_BASE, false).first { it.id == "297264" }
                val gameDir = File(context.filesDir, "cover-offline-launch-fixture").apply { mkdirs() }
                File(gameDir, "index.html").writeText("<html><body style='background:#15223a;color:white;font-size:32px'>KireiBox offline launch test</body></html>")
                val game = Game(title = "ATRI [离线验收]", rootUri = Uri.fromFile(gameDir).toString(), engine = EngineType.TYRANO)
                repo.insert(game)
                try {
                    val uri = KireiCoverStore(context).confirm(game.id, CoverSourceMode.BANGUMI, candidate).getOrThrow()
                    val image = File(Uri.parse(uri).path!!)
                    marker.writeText(JSONObject().put("id", game.id).put("uri", uri).put("sha256", digest(image)).toString())
                } catch (error: Throwable) {
                    repo.delete(game.id)
                    throw error
                }
            } else {
                val record = JSONObject(marker.readText())
                val id = record.getLong("id")
                try {
                    val image = File(Uri.parse(record.getString("uri")).path!!)
                    assertEquals(record.getString("uri"), repo.findById(id)?.coverUri)
                    assertEquals(record.getString("sha256"), digest(image))
                    assertNotNull(BitmapFactory.decodeFile(image.path))
                    assertEquals("297264", MetadataRepository(context).getBangumi(id)?.id)
                    ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
                        val noticeDeadline = System.currentTimeMillis() + 5000
                        while (System.currentTimeMillis() < noticeDeadline) {
                            if (dismissCompatibilityNotice()) break
                            Thread.sleep(100)
                        }
                        var vm: KireiViewModel? = null
                        scenario.onActivity { vm = ViewModelProvider(it)[KireiViewModel::class.java] }
                        val deadline = System.currentTimeMillis() + 15000
                        while (vm!!.state.value.games.none { it.id == id } && System.currentTimeMillis() < deadline) Thread.sleep(100)
                        assertTrue(vm!!.state.value.games.any { it.id == id && it.coverUri == record.getString("uri") })
                        scenario.onActivity { vm!!.selectTab(KireiTab.GAMES) }
                        instrumentation.waitForIdleSync()
                        Thread.sleep(1000)
                        val dir = File(context.getExternalFilesDir(null), "cover-test-evidence").apply { mkdirs() }
                        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                            File(dir, "offline-restart-library.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                        scenario.onActivity { vm!!.selectedGame(repo.findById(id)!!) }
                        instrumentation.waitForIdleSync()
                        Thread.sleep(500)
                        var launch = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.text?.contains("启动游戏") == true }
                        while (!launch.isClickable && launch.parent != null) launch = launch.parent
                        assertTrue(launch.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                        val launchDeadline = System.currentTimeMillis() + 15000
                        while (
                            nodes(instrumentation.uiAutomation.rootInActiveWindow)
                                .none { it.text?.contains("KireiBox offline launch test") == true } &&
                            System.currentTimeMillis() < launchDeadline
                        ) {
                            dismissCompatibilityNotice()
                            Thread.sleep(100)
                        }
                        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                            File(dir, "offline-tyrano-fixture.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                        assertTrue(
                            "Tyrano local HTML fixture must render without source login",
                            nodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.contains("KireiBox offline launch test") == true }
                        )
                        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    }
                } finally {
                    repo.delete(id)
                    File(context.filesDir, "covers/yukihub/game_$id").deleteRecursively()
                    marker.delete()
                    File(context.filesDir, "cover-offline-launch-fixture").deleteRecursively()
                }
            }
        }

    private fun dismissCompatibilityNotice(): Boolean {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
        val all = nodes(root)
        if (all.none { it.text?.toString() == "Android App Compatibility" }) return false
        return all.firstOrNull { it.text?.toString() == "OK" }?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun nodes(root: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> =
        if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }

    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
