package com.yuki.yukihub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanImportClassificationTest {
    @Test
    fun existingGameNeverCountsAsAdded() {
        assertNull(ScanImportClassification.addedIdFor(existingId = 7L, insertedId = 7L))
        assertNull(ScanImportClassification.addedIdFor(existingId = 7L, insertedId = -1L))
    }

    @Test
    fun successfulInsertCountsAsAdded() {
        assertEquals(9L, ScanImportClassification.addedIdFor(existingId = 0L, insertedId = 9L))
    }

    @Test
    fun ignoredConflictIsNotAdded() {
        assertNull(ScanImportClassification.addedIdFor(existingId = 0L, insertedId = -1L))
        assertNull(ScanImportClassification.addedIdFor(existingId = 0L, insertedId = 0L))
    }
}
