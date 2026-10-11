package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NsfwCoverWiringTest {
    private fun workspaceRoot(): File =
        listOf(File("."), File(".."))
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("workspace root not found")

    private fun source(relative: String): String {
        val file = File(workspaceRoot(), relative)
        // UI source may have been split out of KireiBoxApp.kt into sibling files.
        return if (file.isFile) file.readText() else UiSourceFiles.all()
    }

    /**
     * Text strictly between [startMarker] and the first [endMarker] after it (the same slice as
     * `substringAfter(start).substringBefore(end)`), but asserting both delimiters exist: Kotlin's
     * substring helpers return the whole input when a delimiter is missing, which would silently
     * widen the slice and weaken the assertions made on it.
     */
    private fun between(
        source: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = source.indexOf(startMarker)
        assertTrue("$startMarker missing", start >= 0)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("$endMarker missing after $startMarker", end >= 0)
        return source.substring(start + startMarker.length, end)
    }

    @Test
    fun `persisted cover surfaces use the shared nsfw blur state`() {
        val app = UiSourceFiles.all()
        // The immersive backdrop may live in any ImmersiveHome*.kt sibling (ImmersiveHomeBackdrop.kt).
        val immersive = UiSourceFiles.group("ImmersiveHome")

        val artwork = between(app, "fun GameCoverArtwork(", "fun EngineDefaultCover(")
        val focus = between(app, "fun FocusCoverArtwork(", "fun HomeInsightRail(")
        // Visibility-agnostic markers: rememberHeroCover and ImmersiveBackdrop stay adjacent and in
        // this order, so the slice is exactly the hero-cover loader.
        val backdrop = between(immersive, "fun rememberHeroCover(", "fun ImmersiveBackdrop(")

        assertTrue(artwork.contains("blurred = game.nsfw && LocalNsfwBlurEnabled.current"))
        assertTrue(focus.contains("blurred = game.nsfw && LocalNsfwBlurEnabled.current"))
        assertTrue(backdrop.contains("val blurred = game.nsfw && LocalNsfwBlurEnabled.current"))
        assertTrue(backdrop.contains("blurred = blurred"))
        // Plan §6.3: the blurred backdrop (BLUR_COVER / AURORA) is a second pixel path. It must be
        // built from the already NSFW-blurred cover, under a cache key that carries the blur flag.
        assertTrue(backdrop.contains("coverCacheKey(uri, CoverImageRole.IMMERSIVE_BACKDROP, widthPx, heightPx, blurred)"))
        assertTrue(backdrop.contains("rememberBackdropBlur(toneCacheKey, bitmap)"))
        val blurLoader = between(immersive, "private fun rememberBackdropBlur(", "\n}\n")
        assertTrue(blurLoader.contains("computeImmersiveBlur("))
        // 封面氛围 (the default) draws the decoded hero bitmap itself — the NSFW-aware cover above, never
        // a re-decode — and needs no blurred copy; the sources that do use the copy come from it too.
        assertTrue(blurLoader.contains("val spec = immersiveBlurSpec(fx.intensity)"))
        assertTrue(blurLoader.contains("computeImmersiveBlur(blurKey, bitmap, spec)"))
        assertTrue(immersive.contains("ImmersiveBackdropCoverSource.ATMOSPHERE -> this?.bitmap"))
        // r5: the neighbour prefetch is the only other caller; it requests each cover exactly like
        // rememberHeroCover (the shared blur flag, the blur flag in the cache key).
        val prefetch = between(immersive, "internal fun ImmersiveBackdropPrefetch(", "\n}\n")
        assertTrue(prefetch.contains("val nsfwBlurEnabled = LocalNsfwBlurEnabled.current"))
        assertTrue(prefetch.contains("val blurred = game.nsfw && nsfwBlurEnabled"))
        assertTrue(prefetch.contains("CoverDecodePolicy.request(uri, CoverImageRole.IMMERSIVE_BACKDROP, widthPx, heightPx, blurred = blurred)"))
        assertTrue(prefetch.contains("computeImmersiveBlur(immersiveBlurCacheKey(request.cacheKey, it), bitmap, it)"))
        assertEquals(
            "computeImmersiveBlur is only called from rememberBackdropBlur and the prefetch (both fed by the NSFW-aware cover)",
            2,
            Regex("""computeImmersiveBlur\(""").findAll(immersive.replace("internal suspend fun computeImmersiveBlur(", "")).count(),
        )
    }

    @Test
    fun `global and per game controls are wired while candidate picker stays clear`() {
        val app = UiSourceFiles.all()
        val viewModel = source("app/src/main/java/com/kireibox/ui/KireiViewModel.kt")
        // r5 · B split the picker into CoverPickerPages.kt (page, search side, actions) and
        // CoverPickerResults.kt (candidate cards, cover versions): the slice is now every CoverPicker*
        // file instead of the CoverPickerPage function alone, so a card moved into its own
        // composable stays covered.
        assertTrue("CoverPickerPage missing", app.contains("fun CoverPickerPage("))
        val picker = UiSourceFiles.group("CoverPicker")
        assertTrue(picker.contains("fun CoverPickerPage("))

        assertTrue(app.contains("viewModel::setNsfwBlurEnabled"))
        assertTrue(app.contains("viewModel.toggleNsfwCover(game)"))
        assertTrue(viewModel.contains("core.settings.markNsfwDetected(game.id)"))
        // Candidates may pass a source key for image-host headers, but must never opt into blur:
        // the picker always shows the true artwork so a choice is informed.
        assertTrue(picker.contains("rememberCover(candidate.metadata.coverUrl"))
        assertTrue("cover versions load unblurred too", picker.contains("rememberCover(cover.url"))
        assertFalse(picker.contains("blurred ="))
        assertFalse(picker.contains("LocalNsfwBlurEnabled"))
    }

    @Test
    fun `desktop shortcut generation does not use nsfw blur pipeline`() {
        val shortcut = source("app/src/main/java/com/kireibox/shortcut/DesktopGameShortcutFactory.kt")
        assertFalse(shortcut.contains("CoverBlurRenderer"))
        assertFalse(shortcut.contains("LocalNsfwBlurEnabled"))
    }

    @Test
    fun `blur failure does not return an unobscured source`() {
        val blur = source("app/src/main/java/com/kireibox/ui/CoverBlurRenderer.kt")
        assertTrue(blur.contains("const val RADIUS = 22f"))
        assertTrue(blur.contains("PIXELATION_DIVISOR = 8"))
        assertTrue(blur.contains("pixelate(mutable)"))
        assertTrue(blur.contains("return null"))
    }
}
