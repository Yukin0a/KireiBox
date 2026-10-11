package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KireiPublicCopyTest {
    @Test
    fun aboutTextContainsOnlyUserFacingProjectInformation() {
        assertEquals(
            """Rinne Mobile的分支版本 QwQ
开源地址:
https://github.com/Yukin0a/KireiBox""",
            KireiPublicCopy.ABOUT_TEXT,
        )
    }

    @Test
    fun publicCoverSourcesDoNotExposeBuilderInstructions() {
        val ymgal = KireiPublicCopy.coverSourceDescription(CoverSourceMode.YMGAL)
        val hikarinagi = KireiPublicCopy.coverSourceDescription(CoverSourceMode.HIKARINAGI)

        assertEquals("历史资料来源，已停止联网", ymgal)
        assertEquals("需登录 Hikarinagi 账号", hikarinagi)
        assertFalse(ymgal.contains("构建者"))
        assertFalse(hikarinagi.contains("构建者"))
    }
}
