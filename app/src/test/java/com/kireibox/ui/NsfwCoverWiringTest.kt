package com.kireibox.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NsfwCoverWiringTest {
    private fun workspaceRoot(): File = listOf(File("."), File(".."))
        .firstOrNull { File(it, "app/src/main").isDirectory }
        ?: error("workspace root not found")

    private fun source(relative: String): String = File(workspaceRoot(), relative).readText()

    @Test
    fun `persisted cover surfaces use the shared nsfw blur state`() {
        val app = source("app/src/main/java/com/kireibox/ui/KireiBoxApp.kt")
        val immersive = source("app/src/main/java/com/kireibox/ui/ImmersiveHome.kt")

        val artwork = app.substringAfter("internal fun GameCoverArtwork(")
            .substringBefore("private fun EngineDefaultCover(")
        val focus = app.substringAfter("private fun FocusCoverArtwork(")
            .substringBefore("private fun HomeInsightRail(")
        val backdrop = immersive.substringAfter("private fun rememberHeroCover(")
            .substringBefore("private fun ImmersiveBackdrop(")

        assertTrue(artwork.contains("blurred = game.nsfw && LocalNsfwBlurEnabled.current"))
        assertTrue(focus.contains("blurred = game.nsfw && LocalNsfwBlurEnabled.current"))
        assertTrue(backdrop.contains("val blurred = game.nsfw && LocalNsfwBlurEnabled.current"))
        assertTrue(backdrop.contains("blurred = blurred"))
    }

    @Test
    fun `global and per game controls are wired while candidate picker stays clear`() {
        val app = source("app/src/main/java/com/kireibox/ui/KireiBoxApp.kt")
        val viewModel = source("app/src/main/java/com/kireibox/ui/KireiViewModel.kt")
        val picker = app.substringAfter("private fun CoverPickerPage(")
            .substringBefore("private fun SectionTitle(")

        assertTrue(app.contains("viewModel::setNsfwBlurEnabled"))
        assertTrue(app.contains("viewModel.toggleNsfwCover(game)"))
        assertTrue(viewModel.contains("core.settings.markNsfwDetected(game.id)"))
        assertTrue(picker.contains("rememberCover(candidate.metadata.coverUrl)"))
        assertFalse(picker.contains("blurred ="))
    }

    @Test
    fun `desktop shortcut generation does not use nsfw blur pipeline`() {
        val shortcut = source("app/src/main/java/com/kireibox/shortcut/DesktopGameShortcutFactory.kt")
        assertFalse(shortcut.contains("NsfwCoverBlur"))
        assertFalse(shortcut.contains("LocalNsfwBlurEnabled"))
    }

    @Test
    fun `blur failure does not return an unobscured source`() {
        val blur = source("app/src/main/java/com/kireibox/ui/NsfwCoverBlur.kt")
        assertTrue(blur.contains("const val RADIUS = 22f"))
        assertTrue(blur.contains("PIXELATION_DIVISOR = 8"))
        assertTrue(blur.contains("pixelate(mutable)"))
        assertTrue(blur.contains("return null"))
    }
}
