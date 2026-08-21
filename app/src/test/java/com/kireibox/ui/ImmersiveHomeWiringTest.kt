package com.kireibox.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveHomeWiringTest {
    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/com/kireibox/ui/KireiBoxApp.kt"),
            File("../app/src/main/java/com/kireibox/ui/KireiBoxApp.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("KireiBoxApp.kt not found from ${File("").absolutePath}")
        return file.readText()
    }

    private fun immersiveHomeCall(source: String): String {
        val start = source.indexOf("ImmersiveHome(")
        assertTrue("ImmersiveHome call missing", start >= 0)
        var depth = 0
        var i = start
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
            i++
        }
        error("unterminated ImmersiveHome call")
    }

    @Test
    fun `caller passes safe area render kind into ImmersiveHome`() {
        val call = immersiveHomeCall(source())
        assertTrue("expected renderKind = kind, got: $call", call.contains("renderKind = kind"))
    }

    @Test
    fun `caller passes physical safe bounds into ImmersiveHome`() {
        val call = immersiveHomeCall(source())
        assertTrue(
            "expected safeBounds = ImmersiveSafeBounds(...), got: $call",
            call.contains("safeBounds = ImmersiveSafeBounds("),
        )
    }

    @Test
    fun `caller does not pass removed onEdit parameter`() {
        val call = immersiveHomeCall(source())
        org.junit.Assert.assertFalse("ImmersiveHome no longer accepts onEdit, got: $call", call.contains("onEdit"))
    }

    @Test
    fun `immersive home uses continuous gallery track and no focus controller`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")
        assertTrue(source.contains("GalleryTrackController("))
        assertTrue(source.contains("Animatable("))
        assertTrue(source.contains("SerializedTrackMutationRunner"))
        assertTrue(source.contains("Spring.DampingRatioNoBouncy"))
        assertFalse(source.contains("GalleryTrackTargetPolicy"))
        assertFalse(source.contains("FocusGalleryController"))
        assertFalse(source.contains("scope.launch { animatable"))
    }

    @Test
    fun `gesture input is normalized once without orientation-specific drag branches`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")
        assertTrue(source.contains("dragVelocitySlotsPerSecond"))
        assertTrue(source.contains("controller.dragBy(-event.amount / slotDistance)"))
        assertTrue(source.contains("trackMutations.cancelAndJoin()"))
        assertFalse(source.contains("if (orientation == HomeOrientation.PORTRAIT) controller"))
        assertFalse(source.contains("if (orientation == HomeOrientation.LANDSCAPE) controller"))
    }

    @Test
    fun `gallery drag starts only inside resolved card regions and excludes controls`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        assertTrue(source.contains("galleryGestureRegions(layout, orientation)"))
        assertTrue(source.contains("gestureAccepted = gestureRegions.any"))
        assertTrue(source.contains("region.contains(HomePoint(position.x, position.y))"))
        assertTrue(source.contains("if (!gestureAccepted) return@detectHorizontalDragGestures"))
    }

    @Test
    fun `play button consumes the resolved geometry rect in both orientations`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        val start = source.indexOf("private fun ImmersivePlayButton(")
        val end = source.indexOf("private fun ImmersivePager(", start)
        assertTrue(start >= 0 && end > start)
        val button = source.substring(start, end)
        assertTrue(button.contains("pxDp(rect.x, density)"))
        assertTrue(button.contains(".size(pxDp(rect.w, density), pxDp(rect.h, density))"))
        assertFalse(button.contains("SharedControlSizePolicy.playSizeDp"))
    }

    @Test
    fun `immersive overlay is the only fallback metadata owner`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        val start = source.indexOf("private fun ImmersiveGameCard(")
        val end = source.indexOf("private fun ImmersiveLandscapeMetadata(", start)
        assertTrue(start >= 0 && end > start)
        assertTrue(source.substring(start, end).contains("showFallbackMetadata = false"))
    }

    @Test
    fun `immersive editor sheet leaves vertical scrolling to its LazyColumn`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        val start = source.indexOf("private fun ImmersiveGalleryConfigSheet(")
        val sheet = source.substring(start, source.indexOf("private fun", start + 32).takeIf { it > start } ?: source.length)
        assertTrue(sheet.contains("KireiActionSheet("))
        assertTrue(sheet.contains("scrollableContent = false"))
        assertTrue(sheet.contains("HomeContentSettingsPanel("))
    }

    @Test
    fun `immersive top chrome reuses the shared component sizing contract`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        assertTrue(source.contains("AppleTypography.headlineMedium.copy(fontSize = sharedChrome.titleSizeDp.sp)"))
        assertTrue(source.contains("menuDiameterDp = sharedChrome.menuDiameterDp.dp"))
        assertTrue(source.contains("menuIconSizeDp = sharedChrome.menuIconSizeDp.dp"))
        assertTrue(source.contains("sharedChromePx.menuRect"))
    }

    @Test
    fun `immersive home passes one optical safe bounds to geometry and adaptive metrics`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")
        assertTrue(source.contains("opticalSafeBounds("))
        assertTrue(source.contains("val opticalBounds = remember"))
        assertTrue(source.contains("safeBounds = opticalBounds"))
    }

    @Test
    fun immersiveHomeConsumesTheExistingHeroBitmapForCoverToneAndCardGlow() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        assertTrue(source.contains("rememberCover("))
        assertTrue(source.contains("CoverToneExtractor"))
        assertTrue(source.contains("coverCacheKey"))
        assertTrue(source.contains("blendedColor"))
        assertTrue(source.contains("reducedTransparency"))
    }

    @Test
    fun immersiveHomeRetainsOnlyTheCircularBackdropTrackAndReleasesTheIdleNeighbor() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        assertTrue(source.contains("resolveImmersiveBackdropRetention"))
        assertTrue(source.contains("backdropRetention.transitioning"))
        assertFalse(source.contains("renderVisualPosition.coerceIn("))
    }

    @Test
    fun immersiveHomeKeepsTheTrackStateHolderAcrossOrderedGameCountChanges() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        assertTrue(source.contains("val controller = remember { GalleryTrackController(orderedGames.size) }"))
        assertTrue(source.contains("controller.syncGameIds(ids)"))
    }

    @Test
    fun orderedGameChangesKeepTheMutationRunnerUntilCompositionExit() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")

        assertTrue(
            source.contains(
                "val trackMutations = remember(animatable) { SerializedTrackMutationRunner(scope) }",
            ),
        )
        assertTrue(source.contains("DisposableEffect(trackMutations)"))
        assertFalse(source.contains("DisposableEffect(controller, ids)"))

        val syncStart = source.indexOf("LaunchedEffect(controller, ids)")
        val syncEnd = source.indexOf("LaunchedEffect(snapshot.targetPosition", syncStart)
        assertTrue(syncStart >= 0 && syncEnd > syncStart)
        val syncEffect = source.substring(syncStart, syncEnd)
        assertTrue(syncEffect.contains("trackMutations.cancelAndJoin()"))
        assertTrue(syncEffect.contains("controller.syncGameIds(ids)"))

        val disposeStart = source.indexOf("DisposableEffect(trackMutations)")
        val disposeEnd = source.indexOf("LaunchedEffect(controller, ids)", disposeStart)
        assertTrue(disposeStart >= 0 && disposeEnd > disposeStart)
        assertTrue(
            source.substring(disposeStart, disposeEnd)
                .contains("onDispose { trackMutations.dispose() }"),
        )
    }

    @Test
    fun `immersive home does not own an internal dock`() {
        val source = listOf(
            File("src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
            File("../app/src/main/java/com/kireibox/ui/ImmersiveHome.kt"),
        ).firstOrNull { it.isFile }?.readText() ?: error("ImmersiveHome.kt missing")
        assertFalse(source.contains("KireiMainDock("))
        assertFalse(source.contains("dockRectOverride"))
    }

    @Test
    fun dragStateNeverQualifiesForSettleUntilReleased() {
        assertFalse(shouldStartImmersiveSettle(isDragging = true, frozen = false, itemCount = 3))
        assertFalse(shouldStartImmersiveSettle(isDragging = false, frozen = true, itemCount = 3))
        assertFalse(shouldStartImmersiveSettle(isDragging = false, frozen = false, itemCount = 1))
        assertTrue(shouldStartImmersiveSettle(isDragging = false, frozen = false, itemCount = 3))
    }
}
