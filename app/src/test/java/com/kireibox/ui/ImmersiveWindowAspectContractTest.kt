package com.kireibox.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImmersiveWindowAspectContractTest {
    @Test
    fun launcherDeclaresUltrawideResizableWindowSupport() {
        val candidates =
            listOf(
                File("src/main/AndroidManifest.xml"),
                File("../app/src/main/AndroidManifest.xml"),
            )
        val manifest =
            candidates.firstOrNull(File::isFile)?.readText()
                ?: error("AndroidManifest.xml not found")
        assertTrue(manifest.contains("android:resizeableActivity=\"true\""))
        assertTrue(manifest.contains("android:maxAspectRatio=\"2.4\""))
    }
}
