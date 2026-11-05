package com.kireibox

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuki.yukihub.data.YukiDatabaseHelper
import com.yuki.yukihub.sync.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaytimeLedgerPixelTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun cloudPageAndManualTimeDialogWorkWithoutAnAccount() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(com.yuki.yukihub.auth.CoverAccounts.get(context).status.value[com.yuki.yukihub.metadata.model.CoverSourceMode.NEXTMOE]?.connected != true)
        val repo = com.yuki.yukihub.data.GameRepository(context)
        val fixture = com.yuki.yukihub.model.Game(title="Alpha19 时长验收", engine=com.yuki.yukihub.model.EngineType.UNKNOWN, rootUri="test://alpha19-ui-${System.nanoTime()}")
        repo.insert(fixture)
        try {
            androidx.test.core.app.ActivityScenario.launch(com.kireibox.app.LauncherActivity::class.java).use { scenario ->
                var vm: com.kireibox.ui.KireiViewModel? = null
                scenario.onActivity { vm = androidx.lifecycle.ViewModelProvider(it)[com.kireibox.ui.KireiViewModel::class.java] }
                val deadline = System.currentTimeMillis() + 15000
                while(vm!!.state.value.settings == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
                scenario.onActivity { vm!!.openSettingsPage(com.kireibox.ui.KireiSettingsPage.PLAYTIME_SYNC) }
                instrumentation.waitForIdleSync(); Thread.sleep(700)
                screenshot("alpha19-cloud-page.png")
                assertEquals(com.kireibox.ui.KireiSettingsPage.PLAYTIME_SYNC, vm!!.state.value.settingsPage)
                assertTrue(nodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.contains("云端时长") == true })
                scenario.onActivity { vm!!.selectedGame(fixture) }
                instrumentation.waitForIdleSync(); Thread.sleep(500)
                var node = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.text?.toString() == "游玩时长" }
                while(!node.isClickable && node.parent != null) node = node.parent
                assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                instrumentation.waitForIdleSync(); Thread.sleep(500)
                screenshot("alpha19-time-dialog.png")
                val fields = nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isEditable }
                assertEquals(2, fields.size)
                fields[0].performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "2") })
                fields[1].performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "0") })
                Thread.sleep(300)
                node = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.text?.toString() == "保存并同步" }
                while(!node.isClickable && node.parent != null) node = node.parent
                assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                val saveDeadline = System.currentTimeMillis() + 5000
                while(repo.findById(fixture.id)?.totalPlayTime != 7_200_000L && System.currentTimeMillis() < saveDeadline) Thread.sleep(100)
                assertEquals(7_200_000L, repo.findById(fixture.id)?.totalPlayTime)
            }
        } finally { repo.delete(fixture.id) }
    }

    private fun nodes(root: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> = if(root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    private fun screenshot(name: String) {
        val dir = java.io.File(context.getExternalFilesDir(null), "alpha19-evidence").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(dir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @Test fun upgrades22AndCreates23WithoutLosingTotals() {
        val helper = YukiDatabaseHelper(context)
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE games(id INTEGER PRIMARY KEY, total_play_time INTEGER)")
            db.execSQL("CREATE TABLE nextmoe_playtime_sync(game_id INTEGER PRIMARY KEY, account_id TEXT, work_id TEXT)")
            db.execSQL("INSERT INTO games VALUES(1, 123456)")
            db.execSQL("INSERT INTO nextmoe_playtime_sync VALUES(1, 'test', '200')")
            helper.onUpgrade(db, 22, 23)
            db.rawQuery("SELECT total_play_time, cloud_import_ms FROM games", null).use { c -> c.moveToFirst(); assertEquals(123456L, c.getLong(0)); assertEquals(0L, c.getLong(1)) }
            db.rawQuery("SELECT work_id, force_write, retry_after FROM nextmoe_playtime_sync", null).use { c -> c.moveToFirst(); assertEquals("200", c.getString(0)); assertEquals(0, c.getInt(1)) }
            helper.onDowngrade(db, 23, 22)
        }
        SQLiteDatabase.create(null).use { db ->
            helper.onCreate(db)
            db.rawQuery("SELECT cloud_import_ms FROM games", null).close()
            db.rawQuery("SELECT * FROM nextmoe_cloud_works", null).close()
        }
        helper.close()
    }

    @Test fun realLedgerImportsExactDeltaAndUndoPreservesSubsequentPlay() {
        val helper = YukiDatabaseHelper(context)
        val db = helper.writableDatabase
        val account = "alpha19-ledger-test"
        db.execSQL("INSERT INTO games(title,engine,root_uri,created_at,updated_at,total_play_time) VALUES('Alpha19 ledger fixture','KIRIKIRI','test://alpha19-ledger',1,1,90000)")
        val id = db.rawQuery("SELECT last_insert_rowid()", null).use { it.moveToFirst(); it.getLong(0) }
        val ledger = NextmoePlaytimeLedger(context)
        try {
            ledger.save(id, account, PlaytimeLedgerRow(workId="200", remoteMinutes=300, forceWrite=true, retryAfter=12345, lastResult="FAILED:HTTP_500"))
            ledger.raiseLocal(id, 300)
            var game = ledger.libraryGames(account).first { it.gameId == id }
            assertEquals(17_910_000L, game.cloudImportMs)
            assertEquals(300, game.localMinutes)
            assertTrue(game.row!!.forceWrite)
            assertEquals(12345L, game.row!!.retryAfter)
            db.execSQL("UPDATE games SET total_play_time=total_play_time+120000 WHERE id=?", arrayOf(id))
            ledger.undoImport(id)
            game = ledger.libraryGames(account).first { it.gameId == id }
            assertEquals(3, game.localMinutes)
            assertEquals(0L, game.cloudImportMs)
            ledger.snapshot(account, listOf(CloudPlaytimeWork("200", 300)))
            assertEquals(300, ledger.cloud(account).single().minutes)
            assertTrue(ledger.cloud("another-account").isEmpty())
        } finally {
            ledger.snapshot(account, emptyList())
            db.delete("games", "id=?", arrayOf(id.toString()))
            helper.close()
        }
    }
}
