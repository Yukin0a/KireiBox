package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** P4 library browse polish: empty states, the card meta line and the always-visible sort menu. */
class LibraryBrowsePolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
    ): Long = LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun emptyLibraryIsOnlyClaimedOnceTheLibraryHasLoaded() {
        assertNull("the shell owns the first-load spinner", LibraryEmptyStatePolicy.resolve(HomeLoadState.INITIAL_LOADING, 0, filtering = false))
        assertNull("the shell owns the load-failure prompt", LibraryEmptyStatePolicy.resolve(HomeLoadState.RELOAD_FAILED, 0, filtering = false))
        assertEquals(LibraryEmptyKind.LIBRARY_EMPTY, LibraryEmptyStatePolicy.resolve(HomeLoadState.LOADED, 0, filtering = false))
        assertEquals(LibraryEmptyKind.LIBRARY_EMPTY, LibraryEmptyStatePolicy.resolve(HomeLoadState.LOADED, 0, filtering = true))
        assertEquals(LibraryEmptyKind.NO_MATCH, LibraryEmptyStatePolicy.resolve(HomeLoadState.LOADED, 3, filtering = true))
        assertEquals(LibraryEmptyKind.NO_MATCH, LibraryEmptyStatePolicy.resolve(HomeLoadState.RELOAD_FAILED, 3, filtering = true))
        assertNull(LibraryEmptyStatePolicy.resolve(HomeLoadState.LOADED, 3, filtering = false))
    }

    @Test
    fun lastPlayedLabelIsRelativeAndChinese() {
        val now = at(2026, 10, 3, hour = 20)
        assertEquals("未游玩", LibraryLastPlayedLabel.of(0L, now, zone))
        assertEquals("今天", LibraryLastPlayedLabel.of(at(2026, 10, 3, hour = 1), now, zone))
        assertEquals("昨天", LibraryLastPlayedLabel.of(at(2026, 10, 2, hour = 23), now, zone))
        assertEquals("3 天前", LibraryLastPlayedLabel.of(at(2026, 9, 30), now, zone))
        assertEquals("2 周前", LibraryLastPlayedLabel.of(at(2026, 9, 18), now, zone))
        assertEquals("8月1日", LibraryLastPlayedLabel.of(at(2026, 8, 1), now, zone))
        assertEquals("2025年12月", LibraryLastPlayedLabel.of(at(2025, 12, 24), now, zone))
        assertEquals("a clock skewed into the future still reads as today", "今天", LibraryLastPlayedLabel.of(now + 3_600_000L, now, zone))
    }

    @Test
    fun gameCardMetaLineUsesTheRelativeLabel() {
        val card = UiSourceFiles.file("LibraryCard.kt")
        assertTrue(card.contains("LibraryLastPlayedLabel.of(game.lastPlayedAt, now)"))
        assertTrue(
            "the meta line stays on one line",
            Regex("""LibraryLastPlayedLabel\.of\(game\.lastPlayedAt, now\)}",[\s\S]{0,240}?maxLines = 1,\s*overflow = TextOverflow\.Ellipsis""").containsMatchIn(card)
        )
    }

    @Test
    fun choosingCustomSortWhileBrowsingEntersEditMode() {
        // The sort menu is reachable outside edit mode; numbering cards (tap-to-number, 完成, back)
        // only works in edit mode, so the manual branch must turn it on before starting the draft.
        val page = UiSourceFiles.file("LibraryPages.kt")
        val start = page.indexOf("val selectLibrarySort: (String) -> Unit = { mode ->")
        assertTrue(start >= 0)
        val body = page.substring(start, page.indexOf("val finishLibraryEdit", start))
        val editOn = body.indexOf("editMode = true")
        val draft = body.indexOf("customOrderDraft = ManualOrderSelectionPolicy.empty()")
        assertTrue("manual sort enables edit mode", editOn >= 0)
        assertTrue("before the numbering draft starts", draft > editOn)
        assertTrue("the header chrome (browse and edit) shows the same sort menu", page.contains("onSortChange = selectLibrarySort"))
    }
}
