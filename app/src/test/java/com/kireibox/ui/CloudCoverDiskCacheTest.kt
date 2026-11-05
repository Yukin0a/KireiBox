package com.kireibox.ui

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloudCoverDiskCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun survivesRecreationAndSeparatesSourcesAndUrls() {
        val directory = temp.newFolder()
        val original = temp.newFile().apply { writeText("image fixture") }
        CloudCoverDiskCache(directory).put("nextmoe", "https://test/1", original)
        val restored = CloudCoverDiskCache(directory)
        assertEquals("image fixture", restored.find("nextmoe", "https://test/1")?.readText())
        assertNull(restored.find("bangumi", "https://test/1"))
        assertNull(restored.find("nextmoe", "https://test/2"))
        assertTrue(original.exists())
    }

    @Test fun evictsOldestAndDoesNotStoreEmptyOrOversizeFiles() {
        val directory = temp.newFolder()
        val cache = CloudCoverDiskCache(directory, maxBytes = 8)
        val original = temp.newFile().apply { writeText("123456") }
        cache.put("nextmoe", "old", original)
        cache.find("nextmoe", "old")!!.setLastModified(1)
        cache.put("nextmoe", "new", original)
        assertNull(cache.find("nextmoe", "old"))
        assertEquals("123456", cache.find("nextmoe", "new")?.readText())
        original.writeText("")
        cache.put("nextmoe", "empty", original)
        assertNull(cache.find("nextmoe", "empty"))
        original.writeText("123456789")
        cache.put("nextmoe", "large", original)
        assertNull(cache.find("nextmoe", "large"))
        assertFalse(directory.listFiles()!!.any { it.extension == "tmp" })
    }
}
