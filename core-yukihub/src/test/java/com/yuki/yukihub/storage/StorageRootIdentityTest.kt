package com.yuki.yukihub.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRootIdentityTest {
    @Test
    fun primaryFileAndSafDocumentUrisShareOneIdentity() {
        val file = "file:///storage/emulated/0/Games/ATRI"
        val saf = "content://com.android.externalstorage.documents/tree/primary%3AGames/document/primary%3AGames%2FATRI"
        assertEquals("primary:/games/atri", StorageRootIdentity.key(file))
        assertEquals(StorageRootIdentity.key(file), StorageRootIdentity.key(saf))
    }

    @Test
    fun primaryAliasesAndHomeDocumentsAreCanonical() {
        assertEquals("primary:/games", StorageRootIdentity.key("file:///sdcard/Games/"))
        assertEquals("primary:/games", StorageRootIdentity.key("/mnt/sdcard/Games"))
        assertEquals(
            "primary:/documents/visualnovels",
            StorageRootIdentity.key("content://com.android.externalstorage.documents/tree/home%3AVisualNovels"),
        )
    }

    @Test
    fun removableStorageUsesVolumeIdentity() {
        val file = "file:///storage/1234-ABCD/Games/ATRI"
        val saf = "content://com.android.externalstorage.documents/tree/1234-ABCD%3AGames/document/1234-ABCD%3AGames%2FATRI"
        assertEquals("volume:1234-abcd:/games/atri", StorageRootIdentity.key(file))
        assertEquals(StorageRootIdentity.key(file), StorageRootIdentity.key(saf))
    }

    @Test
    fun containmentUsesPathSegmentBoundaries() {
        assertTrue(StorageRootIdentity.contains("file:///sdcard/Games", "file:///storage/emulated/0/Games/ATRI"))
        assertFalse(StorageRootIdentity.contains("file:///sdcard/Games", "file:///storage/emulated/0/Games2/ATRI"))
    }

    @Test
    fun unknownProvidersRemainAuthorityScoped() {
        val first = "content://provider.one/tree/account%3AGames"
        val second = "content://provider.two/tree/account%3AGames"
        assertFalse(StorageRootIdentity.equivalent(first, second))
    }
}
