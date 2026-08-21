package com.yuki.yukihub.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePresentationModeTest {
    @Test
    fun `fromStored null returns immersive`() {
        assertEquals(HomePresentationMode.IMMERSIVE, HomePresentationMode.fromStored(null))
    }

    @Test
    fun `fromStored blank returns immersive`() {
        assertEquals(HomePresentationMode.IMMERSIVE, HomePresentationMode.fromStored("  "))
    }

    @Test
    fun `fromStored immersive returns immersive`() {
        assertEquals(HomePresentationMode.IMMERSIVE, HomePresentationMode.fromStored("immersive"))
    }

    @Test
    fun `fromStored components returns components`() {
        assertEquals(HomePresentationMode.COMPONENTS, HomePresentationMode.fromStored("components"))
    }

    @Test
    fun `fromStored immersive_cover maps to immersive`() {
        assertEquals(HomePresentationMode.IMMERSIVE, HomePresentationMode.fromStored("immersive_cover"))
    }

    @Test
    fun `fromStored focus_poster maps to immersive`() {
        assertEquals(HomePresentationMode.IMMERSIVE, HomePresentationMode.fromStored("focus_poster"))
    }

    @Test
    fun `fromStored unknown maps to immersive`() {
        assertEquals(HomePresentationMode.IMMERSIVE, HomePresentationMode.fromStored("something_else"))
    }

    @Test
    fun `fromStored case insensitive`() {
        assertEquals(HomePresentationMode.IMMERSIVE, HomePresentationMode.fromStored("IMMERSIVE"))
        assertEquals(HomePresentationMode.COMPONENTS, HomePresentationMode.fromStored("COMPONENTS"))
    }

    @Test
    fun `storedValue and displayName correct`() {
        assertEquals("immersive", HomePresentationMode.IMMERSIVE.storedValue)
        assertEquals("沉浸式", HomePresentationMode.IMMERSIVE.displayName)
        assertEquals("components", HomePresentationMode.COMPONENTS.storedValue)
        assertEquals("组件模式", HomePresentationMode.COMPONENTS.displayName)
    }
}
