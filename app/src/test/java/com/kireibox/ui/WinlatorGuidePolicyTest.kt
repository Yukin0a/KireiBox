package com.kireibox.ui

import com.kireibox.external.winlator.WinlatorAvailability
import com.yuki.yukihub.model.EngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinlatorGuidePolicyTest {
    @Test
    fun theNoticeIsRaisedOnceForTheFirstExeGame() {
        assertTrue(WinlatorGuidePolicy.shouldRaiseNotice(EngineType.WINDOWS, guideDone = false, noticeShown = false))
        assertFalse("already raised", WinlatorGuidePolicy.shouldRaiseNotice(EngineType.WINDOWS, guideDone = false, noticeShown = true))
        assertFalse("the user already created the container", WinlatorGuidePolicy.shouldRaiseNotice(EngineType.WINDOWS, guideDone = true, noticeShown = false))
    }

    @Test
    fun otherEnginesNeverRaiseIt() {
        EngineType.entries.filter { it != EngineType.WINDOWS }.forEach {
            assertFalse("$it", WinlatorGuidePolicy.shouldRaiseNotice(it, guideDone = false, noticeShown = false))
        }
    }

    @Test
    fun theMessageNamesWhatIsMissing() {
        assertEquals(3, WinlatorAvailability.entries.map(WinlatorGuidePolicy::noticeMessage).toSet().size)
        assertTrue(WinlatorGuidePolicy.noticeMessage(WinlatorAvailability.READY).contains("容器"))
        assertTrue(WinlatorGuidePolicy.noticeMessage(WinlatorAvailability.NOT_INSTALLED).contains("安装"))
    }
}
