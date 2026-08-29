package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinFileBrowserCrumbPolicyTest {
    @Test
    fun rootListingHidesBreadcrumbRow() {
        assertTrue(BuiltinFileBrowserCrumbPolicy.shouldHideRow(crumbCount = 1))
    }

    @Test
    fun nestedFolderKeepsBreadcrumbRow() {
        assertEquals(false, BuiltinFileBrowserCrumbPolicy.shouldHideRow(crumbCount = 2))
    }
}
