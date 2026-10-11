package com.kireibox.ui

import com.yuki.yukihub.data.ScanImportSummary
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notice tone is decided where a notice is raised; wording inference is only the fallback. */
class NoticeToneTest {
    @Test
    fun failureBranchesKeepTheErrorToneForBareExceptionText() {
        // Exception messages carry none of the 失败 / 无法 words the wording fallback looks for.
        val state = KireiUiState().withNotice("SQLITE_BUSY: database is locked", NoticeTone.ERROR)

        assertEquals("SQLITE_BUSY: database is locked", state.notice)
        assertEquals(NoticeTone.INFO, NoticeTonePolicy.infer(state.notice!!))
        assertEquals(NoticeTone.ERROR, NoticeTonePolicy.resolve(state.notice!!, state.noticeTone))
    }

    @Test
    fun aTagOnlyAppliesToItsOwnMessage() {
        val failed = KireiUiState().withNotice("磁盘已满", NoticeTone.ERROR)
        // A later notice raised without a tag (plain copy) must not inherit the stale error tone.
        val later = failed.copy(notice = "封面已保存")

        assertEquals(NoticeTone.SUCCESS, NoticeTonePolicy.resolve(later.notice!!, later.noticeTone))
        assertEquals(NoticeTone.INFO, NoticeTonePolicy.resolve("GitHub 暂无公开版本", null))
    }

    @Test
    fun unauthorizedFileIsAWarningRatherThanInfo() {
        val state = KireiUiState().withNotice("所选文件不在授权目录中", NoticeTone.WARNING)

        assertEquals(NoticeTone.WARNING, NoticeTonePolicy.resolve(state.notice!!, state.noticeTone))
    }

    @Test
    fun autoRefreshToneSeparatesPartialSuccessFromFailure() {
        val added = ScanImportSummary(addedGames = listOf(Game(id = 1L), Game(id = 2L)))

        assertEquals(NoticeTone.SUCCESS, DirectoryAutoRefreshPolicy.noticeTone(added, 0))
        assertEquals(NoticeTone.SUCCESS, DirectoryAutoRefreshPolicy.noticeTone(ScanImportSummary(), 0, 3))
        // "添加 2 个新游戏，1 个目录扫描失败" changed the library: a warning, not an error.
        assertEquals(NoticeTone.WARNING, DirectoryAutoRefreshPolicy.noticeTone(added, 1))
        assertEquals(NoticeTone.WARNING, DirectoryAutoRefreshPolicy.noticeTone(ScanImportSummary(), 2, 1))
        assertEquals(NoticeTone.ERROR, DirectoryAutoRefreshPolicy.noticeTone(ScanImportSummary(), 1))
    }

    @Test
    fun everyResultFailureNoticeInTheControllersCarriesAnExplicitTone() {
        val source = UiSourceFiles.all()
        // Widened (takeover review): exception text also reached showNotice(error.message …) and
        // showNotice(it.message …) untagged, e.g. "Token 无效或已过期" read as INFO. Definitions of
        // showNotice itself take a `message: String` parameter and do not match these patterns.
        val untagged = Regex("""notice\s*=\s*result\.exceptionOrNull\(\)|showNotice\(\s*(?:error|it|throwable|e)\.message""")
        assertTrue(
            "failure notices built from exception text must use withNotice(..., NoticeTone.ERROR): ${untagged.find(source)?.value}",
            !untagged.containsMatchIn(source),
        )
        assertTrue(source.contains("NoticeTonePolicy.resolve(notice, state.noticeTone)"))
        // The login result already knows success/failure; "授权已取消/已超时" must not infer SUCCESS.
        val login = source.substring(source.indexOf("fun completeCoverLogin("), source.indexOf("fun setIncludeAdult("))
        assertTrue(login.contains("withNotice(message, if (success) NoticeTone.SUCCESS else NoticeTone.WARNING)"))
        assertTrue(source.contains("withNotice(error.message ?: \"账号验证失败\", NoticeTone.ERROR)"))
    }

    /** WCAG 4.1.3 / 2.2.1: notices are live regions and stay up for the user's a11y timeout. */
    @Test
    fun noticesAreAnnouncedAndHonourTheAccessibilityTimeout() {
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Assertive, NoticeA11yPolicy.liveRegion(NoticeTone.ERROR))
        listOf(NoticeTone.INFO, NoticeTone.SUCCESS, NoticeTone.WARNING).forEach {
            assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, NoticeA11yPolicy.liveRegion(it))
        }
        val notices = UiSourceFiles.file("KireiNotices.kt")
        assertTrue(notices.contains("semantics { liveRegion = NoticeA11yPolicy.liveRegion(tone) }"))
        assertTrue("the undo toast is announced too", notices.contains("semantics { liveRegion = LiveRegionMode.Polite }"))
        assertTrue(notices.contains("calculateRecommendedTimeoutMillis("))
        assertTrue(notices.contains("rememberNoticeTimeout(NoticeA11yPolicy.NOTICE_MILLIS, containsControls = false)"))
        assertTrue(notices.contains("rememberNoticeTimeout(NoticeA11yPolicy.UNDO_MILLIS, containsControls = true)"))
        assertTrue("no hard-coded dismissal delays", !Regex("""delay\((?:2_200|5_000)\)""").containsMatchIn(notices))
    }

    @Test
    fun noticesOnlyFadeUnderReducedMotion() {
        val notices = UiSourceFiles.file("KireiNotices.kt")
        assertTrue(notices.contains("LocalResolvedMotionMode.current == MotionMode.REDUCED"))
        assertTrue(notices.contains("enter = noticeEnter(reducedMotion, fromTop = true)"))
        assertTrue(notices.contains("enter = noticeEnter(reducedMotion, fromTop = false)"))
    }
}
