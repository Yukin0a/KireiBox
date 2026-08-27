package com.kireibox.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCoverImportPolicyTest {
    @Test
    fun `local cover byte limit accepts nonempty files through twenty megabytes`() {
        assertFalse(LocalCoverImportPolicy.acceptsByteCount(0L))
        assertTrue(LocalCoverImportPolicy.acceptsByteCount(LocalCoverImportPolicy.MAX_BYTES))
        assertFalse(LocalCoverImportPolicy.acceptsByteCount(LocalCoverImportPolicy.MAX_BYTES + 1L))
    }

    @Test
    fun `local cover rejects invalid and oversized dimensions`() {
        assertFalse(LocalCoverImportPolicy.acceptsDimensions(0, 100))
        assertTrue(LocalCoverImportPolicy.acceptsDimensions(6_000, 10_000))
        assertFalse(LocalCoverImportPolicy.acceptsDimensions(6_001, 10_000))
    }

    @Test
    fun `mime types map to stable managed extensions`() {
        assertEquals("jpg", LocalCoverImportPolicy.extensionFor("image/jpeg"))
        assertEquals("png", LocalCoverImportPolicy.extensionFor("image/png"))
        assertEquals("webp", LocalCoverImportPolicy.extensionFor("image/webp"))
        assertEquals("heic", LocalCoverImportPolicy.extensionFor("image/heif"))
        assertEquals("img", LocalCoverImportPolicy.extensionFor(null))
    }
}
