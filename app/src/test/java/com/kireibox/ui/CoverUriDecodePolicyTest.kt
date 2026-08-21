package com.kireibox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverUriDecodePolicyTest {
    @Test
    fun `content and android resource schemes require the content resolver`() {
        assertTrue(requiresContentResolverStream("content"))
        assertTrue(requiresContentResolverStream("android.resource"))
    }

    @Test
    fun `file http https and schemeless uris decode without the content resolver`() {
        assertFalse(requiresContentResolverStream("file"))
        assertFalse(requiresContentResolverStream("http"))
        assertFalse(requiresContentResolverStream("https"))
        assertFalse(requiresContentResolverStream(null))
    }
}
