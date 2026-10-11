package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverHybridStatusTest {
    private fun status(
        connected: Boolean = true,
        loading: Boolean = false,
        attempted: Boolean = false,
        failed: Boolean = false,
        hits: Int = 0,
    ) = CoverHybridStatus.text(CoverSourceMode.BANGUMI, connected, loading, attempted, failed, hits)

    @Test
    fun reportsEachStage() {
        assertEquals("待查询", status())
        assertEquals("查询中…", status(loading = true))
        assertEquals("未连接", status(connected = false))
        assertEquals("3 条", status(attempted = true, hits = 3))
        assertEquals("无结果", status(attempted = true, hits = 0))
        assertEquals("不可达", status(attempted = true, failed = true))
    }

    @Test
    fun failureWinsOverCount() {
        assertEquals("不可达", status(attempted = true, failed = true, hits = 5))
    }

    @Test
    fun loginActionNamesTheMissingSources() {
        assertEquals("登录 NextMoe", CoverHybridStatus.loginActionLabel(listOf(CoverSourceMode.NEXTMOE)))
        assertEquals("登录 NextMoe / Hikarinagi", CoverHybridStatus.loginActionLabel(listOf(CoverSourceMode.NEXTMOE, CoverSourceMode.HIKARINAGI)))
    }

    @Test
    fun loginHintOnlyWhenALoginSourceIsMissing() {
        val connected = { source: CoverSourceMode -> source != CoverSourceMode.NEXTMOE }
        assertTrue(CoverHybridStatus.needsLoginHint(CoverSourceMode.active, connected))
        assertFalse(CoverHybridStatus.needsLoginHint(CoverSourceMode.active) { true })
    }
}
