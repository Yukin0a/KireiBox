package com.kireibox.ui

import com.yuki.yukihub.sync.CloudPlaytimeWork
import com.yuki.yukihub.sync.PlaytimeGame
import com.yuki.yukihub.sync.PlaytimeLedgerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudPlaytimeSubtitleTest {
    private val work = CloudPlaytimeWork("1", 128, gameId = 7, match = "BOUND")
    private val game = PlaytimeGame(7, "game", 128, PlaytimeLedgerRow(lastResult = "IN_SYNC"))

    @Test fun equalBoundTotalsShowOnlyCloudTimeAndSynced() {
        assertEquals("云端 2小时8分钟 · 已同步", cloudPlaytimeSubtitle(work, game))
        assertEquals("云端 0小时0分钟 · 已同步", cloudPlaytimeSubtitle(work.copy(minutes = 0), game.copy(localMinutes = 0)))
    }

    @Test fun differencesAndFailuresKeepLocalTimeAndStatus() {
        assertEquals("云端 2小时8分钟 · 本机 2小时9分钟 · 待上传", cloudPlaytimeSubtitle(work, game.copy(localMinutes = 129, row = PlaytimeLedgerRow(lastResult = "WAITING_UPLOAD"))))
        assertTrue(cloudPlaytimeSubtitle(work, game.copy(row = PlaytimeLedgerRow(rejected = "HTTP_422", lastResult = "FAILED:HTTP_422"))).contains("写入失败"))
        assertEquals("云端 2小时8分钟 · 选择本地游戏", cloudPlaytimeSubtitle(work.copy(match = "NONE"), null))
        assertTrue(cloudPlaytimeSubtitle(work.copy(match = "SUGGESTED"), game).contains("本机"))
    }
}
