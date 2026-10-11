package com.kireibox.ui

import com.yuki.yukihub.sync.PlaytimeLedgerSummary
import com.yuki.yukihub.sync.PlaytimeSyncProblem
import com.yuki.yukihub.sync.PlaytimeSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaytimeSyncUiTest {
    private val minute = 60_000L
    private val now = 100 * 24 * 60 * minute

    @Test
    fun lastSyncReadsAsElapsedTime() {
        assertEquals("未同步", lastSyncLabel(0L, now))
        assertEquals("刚刚", lastSyncLabel(now - 30_000L, now))
        assertEquals("3 分钟前", lastSyncLabel(now - 3 * minute, now))
        assertEquals("2 小时前", lastSyncLabel(now - 125 * minute, now))
        assertEquals("4 天前", lastSyncLabel(now - 4 * 24 * 60 * minute, now))
    }

    @Test
    fun theStatusLineShowsWhatNeedsTheUserFirst() {
        val synced = PlaytimeSyncStatus(lastSyncAt = now - 3 * minute, summary = PlaytimeLedgerSummary(synced = 12))
        assertEquals("已同步 12 款 · 3 分钟前", playtimeSyncLine(synced, connected = true, now = now))
        assertNull("off: the switch says it", playtimeSyncLine(synced.copy(enabled = false), true, now))
        assertEquals("登录 NextMoe 后开始同步", playtimeSyncLine(synced, connected = false, now = now))
        val confirm = synced.copy(summary = PlaytimeLedgerSummary(synced = 12, needsConfirm = 3), problem = PlaytimeSyncProblem.QUOTA)
        assertEquals("3 款待确认 · 已同步 12 款", playtimeSyncLine(confirm, true, now))
        assertEquals("需要重新登录 NextMoe", playtimeSyncLine(confirm.copy(problem = PlaytimeSyncProblem.NEEDS_LOGIN), true, now))
        assertEquals("今日额度已用完，明天继续", playtimeSyncLine(synced.copy(problem = PlaytimeSyncProblem.QUOTA), true, now))
        assertEquals("正在同步…", playtimeSyncLine(synced.copy(running = true), true, now))
        assertEquals("2 款等待上传", playtimeSyncLine(synced.copy(summary = PlaytimeLedgerSummary(waiting = 2)), true, now))
        assertEquals("尚未同步", playtimeSyncLine(PlaytimeSyncStatus(), true, now))
        assertEquals("never 「已同步 0 款」", "已是最新 · 刚刚", playtimeSyncLine(PlaytimeSyncStatus(lastSyncAt = now), true, now))
        assertEquals(4, playtimeAttentionCount(PlaytimeSyncStatus(summary = PlaytimeLedgerSummary(needsConfirm = 3, rejected = 1))))
    }

    @Test
    fun thePageSitsUnderTheAccountsPage() {
        assertEquals(KireiSettingsPage.COVER_ACCOUNTS, KireiBackPolicy.settingsParent(KireiSettingsPage.PLAYTIME_SYNC))
        assertEquals("同步游玩时长", settingsPageTitle(KireiSettingsPage.PLAYTIME_SYNC))
        assertEquals("NextMoe 上找不到这部作品", playtimeRejectionReason("NOT_FOUND"))
    }

    @Test
    fun theSyncButtonColorFollowsTheState() {
        val synced = PlaytimeSyncStatus(lastSyncAt = now, summary = PlaytimeLedgerSummary(synced = 2))
        assertEquals(SyncButtonTone.SYNCED, syncButtonTone(synced, connected = true))
        assertEquals(SyncButtonTone.OFF, syncButtonTone(synced, connected = false))
        assertEquals(SyncButtonTone.OFF, syncButtonTone(synced.copy(enabled = false), connected = true))
        assertEquals(SyncButtonTone.RUNNING, syncButtonTone(synced.copy(running = true, problem = PlaytimeSyncProblem.NETWORK), true))
        assertEquals(SyncButtonTone.FAILED, syncButtonTone(synced.copy(problem = PlaytimeSyncProblem.NETWORK), true))
        assertEquals(SyncButtonTone.ATTENTION, syncButtonTone(synced.copy(summary = PlaytimeLedgerSummary(needsConfirm = 1)), true))
        assertEquals(SyncButtonTone.IDLE, syncButtonTone(PlaytimeSyncStatus(), true))
        assertEquals(SyncButtonTone.IDLE, syncButtonTone(synced.copy(summary = PlaytimeLedgerSummary(waiting = 1)), true))
    }
}
