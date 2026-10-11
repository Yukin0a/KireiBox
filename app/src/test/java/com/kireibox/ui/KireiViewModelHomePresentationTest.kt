package com.kireibox.ui

import android.app.Application
import com.yuki.yukihub.settings.HomePresentationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KireiViewModelHomePresentationTest {
    // --- C1 fix: real behavioral test with injected seams ---

    @Test
    fun `setHomePresentationMode delegates to writer and triggers reload`() {
        var writtenMode: HomePresentationMode? = null
        var reloadCount = 0

        val writer: (HomePresentationMode) -> Unit = { mode -> writtenMode = mode }
        val reloader: () -> Unit = { reloadCount++ }

        val vm =
            KireiViewModel(
                Application(),
                testHomePresentationWriter = writer,
                testReloadAction = reloader
            )

        // Act
        vm.setHomePresentationMode(HomePresentationMode.COMPONENTS)

        // Assert
        assertEquals("Writer should receive COMPONENTS", HomePresentationMode.COMPONENTS, writtenMode)
        assertEquals("Reload should be triggered exactly once", 1, reloadCount)
    }

    @Test
    fun `setHomePresentationMode to IMMERSIVE delegates correctly`() {
        var writtenMode: HomePresentationMode? = null
        var reloadCount = 0

        val vm =
            KireiViewModel(
                Application(),
                testHomePresentationWriter = { mode -> writtenMode = mode },
                testReloadAction = { reloadCount++ }
            )

        vm.setHomePresentationMode(HomePresentationMode.IMMERSIVE)

        assertEquals(HomePresentationMode.IMMERSIVE, writtenMode)
        assertEquals(1, reloadCount)
    }

    // Existing reflection-based tests retained for API contract verification
    @Test
    fun `setHomePresentationMode method signature verified`() {
        val method =
            KireiViewModel::class.java.getDeclaredMethod(
                "setHomePresentationMode",
                HomePresentationMode::class.java
            )
        assertTrue(
            "setHomePresentationMode should accept HomePresentationMode",
            method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == HomePresentationMode::class.java
        )
    }
}
