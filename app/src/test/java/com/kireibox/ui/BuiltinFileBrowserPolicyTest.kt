package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BuiltinFileBrowserPolicyTest {
    @Test
    fun rootListingHidesBreadcrumbRow() {
        assertTrue(BuiltinFileBrowserPolicy.shouldHideCrumbRow(crumbCount = 1))
    }

    @Test
    fun nestedFolderKeepsBreadcrumbRow() {
        assertEquals(false, BuiltinFileBrowserPolicy.shouldHideCrumbRow(crumbCount = 2))
    }

    @Test
    fun modifiedDatesAreChineseStyleAndOmitTheYearOnlyWithinTheCurrentYear() {
        val zone = ZoneId.of("Asia/Shanghai")

        fun millis(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            minute: Int = 0,
        ) = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

        val now = millis(2026, 10, 7, 18, 0)

        assertEquals("10月7日 17:20", BuiltinFileBrowserPolicy.modifiedLabel(millis(2026, 10, 7, 17, 20), now, zone))
        assertEquals("1月2日 03:05", BuiltinFileBrowserPolicy.modifiedLabel(millis(2026, 1, 2, 3, 5), now, zone))
        assertEquals("2025年10月7日", BuiltinFileBrowserPolicy.modifiedLabel(millis(2025, 10, 7, 17, 20), now, zone))
        assertEquals("", BuiltinFileBrowserPolicy.modifiedLabel(0L, now, zone))
    }
}
