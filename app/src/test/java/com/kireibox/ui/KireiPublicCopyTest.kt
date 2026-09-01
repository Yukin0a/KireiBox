package com.kireibox.ui

import com.yuki.yukihub.metadata.CoverSourceMode
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
            KireiPublicCopy.aboutText,
        )
    }

    @Test
    fun publicCoverSourcesDoNotExposeBuilderInstructions() {
        val ymgal = KireiPublicCopy.coverSourceDescription(CoverSourceMode.YMGAL)
        val hikarinagi = KireiPublicCopy.coverSourceDescription(CoverSourceMode.HIKARINAGI)

        assertEquals("月幕 Gal 公开服务 · 用户无需 Token。", ymgal)
        assertEquals("Hikarinagi 公开服务 · 用户无需 Token。", hikarinagi)
        assertFalse(ymgal.contains("构建者"))
        assertFalse(hikarinagi.contains("构建者"))
    }
}
