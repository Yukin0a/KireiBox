package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveHomeWiringTest {
    private fun source(): String {
        return UiSourceFiles.all()
    }

    /**
     * Every `ImmersiveHome*.kt` file, so the entry point can be split into sibling files
     * (backdrop, config sheet, ...) without these wiring assertions losing their subject.
     */
    private fun immersiveHomeSources(): String = UiSourceFiles.group("ImmersiveHome")

    /**
     * Slice from [marker] up to the next top-level declaration (a column-0 line starting with a
     * letter or `@`, e.g. the next `@Composable` or the following file's `package`). Asserts the
     * marker exists, because a missing delimiter would otherwise widen the slice silently.
     */
    private fun topLevelDeclaration(
        source: String,
        marker: String,
    ): String {
        val start = source.indexOf(marker)
        assertTrue("$marker missing", start >= 0)
        val end = Regex("\n[@A-Za-z]").find(source, start + marker.length)?.range?.first ?: source.length
        return source.substring(start, end)
    }

    private fun immersiveHomeCall(source: String): String {
        // The declaration lives in ImmersiveHome.kt; the call site may appear before it once the
        // package is concatenated alphabetically. Prefer the call site (not preceded by "fun ").
        var start = -1
        var from = 0
        while (true) {
            val at = source.indexOf("ImmersiveHome(", from)
            if (at < 0) break
            val before = source.substring(maxOf(0, at - 4), at)
            if (before != "fun ") {
                start = at
                break
            }
            from = at + 1
        }
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
            call.contains(Regex("safeBounds\\s*=\\s*ImmersiveSafeBounds\\(")),
        )
    }

    @Test
    fun `caller wires the persisted time setting and overview action`() {
        val call = immersiveHomeCall(source())
        assertTrue(call.contains("showPlayTime = state.settings?.immersivePlayTimeVisible != false"))
        assertTrue(call.contains("onOpenPlayTime = onOpenOverview"))
    }

    @Test
    fun `caller does not pass removed onEdit parameter`() {
        val call = immersiveHomeCall(source())
        org.junit.Assert.assertFalse("ImmersiveHome no longer accepts onEdit, got: $call", call.contains("onEdit"))
    }

    @Test
    fun `immersive home uses continuous gallery track and no focus controller`() {
        val source = immersiveHomeSources()
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
        val source = immersiveHomeSources()
        assertTrue(source.contains("dragVelocitySlotsPerSecond"))
        assertTrue(source.contains("controller.dragBy(-event.amount / slotDistance)"))
        assertTrue(source.contains("trackMutations.cancelAndJoin()"))
        assertFalse(source.contains("if (orientation == HomeOrientation.PORTRAIT) controller"))
        assertFalse(source.contains("if (orientation == HomeOrientation.LANDSCAPE) controller"))
    }

    @Test
    fun `gallery drag starts only inside resolved card regions and excludes controls`() {
        val source = immersiveHomeSources()

        assertTrue(source.contains("galleryGestureRegions(layout, orientation)"))
        assertTrue(source.contains(Regex("gestureAccepted\\s*=\\s*gestureRegions\\.any")))
        assertTrue(source.contains("region.contains(HomePoint(position.x, position.y))"))
        assertTrue(source.contains("if (!gestureAccepted) return@detectHorizontalDragGestures"))
    }

    /**
     * r4 (user request 5): the portrait button rides its card. Its geometry is still the resolved
     * layout rect — `layout.play` carried onto the anchor card's live frame by the pure
     * [ImmersivePlayButtonMotion] — never a size policy of its own; landscape keeps the static
     * `layout.play` pill. The renderer consumes the pose rect exactly as it consumed `layout.play`.
     */
    @Test
    fun `play button consumes the resolved geometry rect in both orientations`() {
        val source = UiSourceFiles.file("ImmersiveHomeControls.kt")

        val start = source.indexOf("internal fun ImmersivePlayButton(")
        val end = source.indexOf("internal fun ImmersivePager(", start)
        assertTrue(start >= 0 && end > start)
        val button = source.substring(start, end)
        assertTrue("the rect is the resolved pose", button.contains("val rect = pose.rect"))
        assertTrue(button.contains("pxDp(rect.x, density)"))
        assertTrue(button.contains(".size(pxDp(rect.w, density), pxDp(rect.h, density))"))
        assertFalse(button.contains("SharedControlSizePolicy.playSizeDp"))
        // It turns with its card around the card's center and springs in after the settle.
        assertTrue(button.contains("rotationZ = pose.rotationZ"))
        assertTrue(button.contains("transformOrigin = TransformOrigin(pose.pivotFractionX, pose.pivotFractionY)"))
        assertTrue(button.contains("ImmersivePlayButtonMotion.revealScale(reveal.value)"))
        val reveal = topLevelDeclaration(source, "internal fun rememberImmersivePlayButtonReveal(")
        assertTrue(reveal.contains("settled -> AppleMotion.Spring.Emphasized"))
        assertTrue("reduced motion snaps", reveal.contains("reducedMotion -> snap()"))
        assertTrue(button.contains("role = Role.Button"))

        val motion = UiSourceFiles.file("ImmersivePlayButtonMotion.kt")
        assertTrue("portrait geometry is layout.play carried by the card", motion.contains("ImmersivePlayButtonMotion.carried(track.layout.play, restCard, item.frame)"))
        assertTrue("landscape keeps the static layout.play pill", motion.contains("ImmersivePlayButtonPose(gameId = it, rect = track.layout.play)"))

        val home = UiSourceFiles.file("ImmersiveHome.kt")
        assertTrue(home.contains("resolveImmersivePlayButtonPose(playTrack, currentGame?.id)"))
        assertTrue("the pose follows the same frames the cards render", home.contains("frames = frames,"))
        assertTrue(home.contains("immersivePlayButtonDescription(playGame?.title)"))
        // Whitespace-tolerant: neither a reformatted nor a one-line `ImmersivePlayButton(layout.play, …)` may come back.
        assertFalse(
            "no fixed rect above sliding cards any more",
            Regex("""ImmersivePlayButton\(\s*(rect\s*=\s*)?layout\.play\b""").containsMatchIn(home),
        )
        // A release a fraction of a pixel from rest must not leave the track "settling" (the button stayed hidden).
        assertTrue("tiny settles snap instead of returning early", home.contains("animatable.snapTo(target)"))
        assertTrue(
            "settling uses the same epsilon as the settle effect",
            home.contains("isImmersiveGallerySettling(renderSnapshot.isDragging, renderSnapshot.targetPosition, renderVisualPosition)"),
        )
        assertFalse(home.contains("return@launchLatest"))
    }

    @Test
    fun `play time is positioned independently and landscape only makes text clickable`() {
        val immersiveSource = UiSourceFiles.file("ImmersiveHomeControls.kt")
        val start = immersiveSource.indexOf("internal fun ImmersivePlayTimeIndicator(")
        val end = immersiveSource.indexOf("internal fun ImmersivePager(", start)
        assertTrue("ImmersivePlayTimeIndicator must precede ImmersivePager", start >= 0 && end > start)
        val indicator = immersiveSource.substring(start, end)

        assertTrue(immersiveSource.contains("resolveImmersivePlayTimeAnchor("))
        assertTrue(indicator.contains(".clickable(onClick = onClick)"))
        assertTrue(indicator.contains("Icons.Rounded.Schedule"))
        assertTrue(indicator.contains("Text("))
        assertFalse(indicator.contains("layout.play ="))
    }

    /**
     * r4 · I2 (user request 6): landscape 「尚未游玩」/「已游玩 …」 measure against the whole information column
     * (real text layout, the gallery's longest labels), wrap to two lines before "…", and the play button
     * follows the pill the placement resolved (it narrows only when a short window needs the room).
     */
    @Test
    fun `landscape play time is measured against the information column and wraps before it ellipsizes`() {
        val home = UiSourceFiles.file("ImmersiveHome.kt")
        assertTrue(home.contains("rememberImmersiveLandscapePlayTime(layout, orderedGames, playTimeSpec)"))
        assertTrue(home.contains("val playLayout = landscapePlayTime?.let { layout.copy(play = it.play) } ?: layout"))
        assertTrue("the play button rides the resolved pill", home.contains("layout = playLayout,"))
        assertTrue(home.contains("landscapePlayTime?.anchor() ?: resolveImmersivePlayTimeAnchor("))

        val controls = UiSourceFiles.file("ImmersiveHomeControls.kt")
        assertTrue(controls.contains("ImmersivePlayTimePlacement.landscape(layout, gapPx) { maxWidthPx, form -> measurer.playTimeBox(shown.getValue(form), spec, density, maxWidthPx) }"))
        assertTrue("the drawn label is the measured wording", controls.contains("immersivePlayTimeDisplay(label, form),"))
        assertTrue("TalkBack keeps the full sentence", controls.contains("contentDescription = \"查看\${game.title.orEmpty()}游玩记录，\$label\""))
        assertTrue(controls.contains("maxLines = IMMERSIVE_PLAY_TIME_MAX_LINES"))
        assertTrue("measured and rendered text share one style", controls.contains("style = immersivePlayTimeTextStyle(visualSpec)"))
        assertFalse("no cap at the play pill's width", controls.contains("maxWidthPx = minOf(layout.play.w, maxWidth)"))
        assertFalse("no fallback above the play pill", controls.contains("layout.play.y - safeIndicatorHeight - safeGap"))
    }

    @Test
    fun `immersive overlay is the only fallback metadata owner`() {
        val source = UiSourceFiles.file("ImmersiveHomeCards.kt")

        val start = source.indexOf("internal fun ImmersiveGameCard(")
        val end = source.indexOf("internal fun ImmersiveLandscapeMetadata(", start)
        assertTrue(start >= 0 && end > start)
        assertTrue(source.substring(start, end).contains("showFallbackMetadata = false"))
    }

    @Test
    fun `immersive editor sheet leaves vertical scrolling to its LazyColumn`() {
        val source = immersiveHomeSources()

        // Visibility-agnostic marker: the sheet lives in its own ImmersiveHomeConfig.kt (internal).
        // The slice ends at the next top-level declaration, i.e. it covers exactly the sheet body.
        val sheet = topLevelDeclaration(source, "fun ImmersiveGalleryConfigSheet(")
        assertTrue(sheet.contains("KireiActionSheet("))
        assertTrue(sheet.contains("scrollableContent = false"))
        assertTrue(sheet.contains("HomeContentSettingsPanel("))
    }

    @Test
    fun `immersive top chrome reuses the shared component sizing contract`() {
        val source = immersiveHomeSources()

        assertTrue(source.contains("AppleTypography.headlineMedium.copy(fontSize = sharedChrome.titleSizeDp.sp)"))
        assertTrue(source.contains("menuDiameterDp = sharedChrome.menuDiameterDp.dp"))
        assertTrue(source.contains("menuIconSizeDp = sharedChrome.menuIconSizeDp.dp"))
        assertTrue(source.contains("sharedChromePx.menuRect"))
    }

    @Test
    fun `immersive home passes one optical safe bounds to geometry and adaptive metrics`() {
        val source = immersiveHomeSources()
        assertTrue(source.contains("opticalSafeBounds("))
        assertTrue(source.contains(Regex("val opticalBounds\\s*=\\s*remember")))
        assertTrue(source.contains("safeBounds = opticalBounds"))
    }

    @Test
    fun immersiveHomeConsumesTheExistingHeroBitmapForCoverToneAndCardGlow() {
        val source = immersiveHomeSources()

        assertTrue(source.contains("rememberCover("))
        assertTrue(source.contains("CoverToneExtractor"))
        assertTrue(source.contains("coverCacheKey"))
        assertTrue(source.contains("blendedColor"))
        assertTrue(source.contains("reducedTransparency"))
    }

    @Test
    fun immersiveHomeRetainsOnlyTheCircularBackdropTrackAndReleasesTheIdleNeighbor() {
        val source = immersiveHomeSources()

        assertTrue(source.contains("resolveImmersiveBackdropRetention"))
        assertTrue(source.contains("backdropRetention.transitioning"))
        assertFalse(source.contains("renderVisualPosition.coerceIn("))
    }

    /**
     * Takeover review (STANDARD blink): hero cover state follows the game, not the left/right slot,
     * and produced tone / blur values are only used while their key still matches.
     */
    @Test
    fun heroCoverStateFollowsTheGameAndNeverShowsAStaleBackdrop() {
        val source = immersiveHomeSources()
        val retained = topLevelDeclaration(source, "internal fun rememberRetainedHeroCovers(")
        assertTrue("one keyed group per game, in one loop", retained.contains("key(game?.id) { rememberHeroCover(game, widthPx, heightPx) }"))
        assertTrue(source.contains("rememberRetainedHeroCovers(orderedGames, backdropRetention,"))
        assertEquals(
            "a single call site keeps keyed groups movable",
            1,
            Regex("""rememberHeroCover\(\s*orderedGames""").findAll(source).count() + Regex("""\brememberHeroCover\(game,""").findAll(source).count()
        )
        val hero = topLevelDeclaration(source, "internal fun rememberHeroCover(")
        assertTrue(hero.contains("it.key == toneCacheKey && it.bitmap === bitmap"))
        val blur = topLevelDeclaration(source, "private fun rememberBackdropBlur(")
        assertTrue(blur.contains("produced?.takeIf { it.key == blurKey }?.image ?: cached"))
    }

    @Test
    fun aZeroSizedLayoutFrameNeverReachesTheMetrics() {
        // resolveAdaptiveUiMetrics requires a positive window; a display resize once laid the fixture out at 0×0
        // and crashed it (r4 verification). The guard comes before the metrics are resolved.
        val home = UiSourceFiles.file("ImmersiveHome.kt")
        val guard = home.indexOf("if (maxWidth.value <= 0f || maxHeight.value <= 0f) return@BoxWithConstraints")
        assertTrue(guard >= 0)
        assertTrue(guard < home.indexOf("resolveAdaptiveUiMetrics("))
    }

    @Test
    fun immersiveHomeKeepsTheTrackStateHolderAcrossOrderedGameCountChanges() {
        val source = immersiveHomeSources()

        assertTrue(source.contains("val controller = remember { GalleryTrackController(orderedGames.size) }"))
        assertTrue(source.contains("controller.syncGameIds(ids)"))
    }

    @Test
    fun orderedGameChangesKeepTheMutationRunnerUntilCompositionExit() {
        val source = immersiveHomeSources()

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
        val source = immersiveHomeSources()
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

    @Test
    fun visualEffectsArriveAsOneResolvedObjectAndAnimateOnlyInDrawAndLayerScopes() {
        val all = source()
        assertTrue("the app resolves the effects once", all.contains("LocalImmersiveFx provides rememberImmersiveFx(settings, resolvedMotion)"))
        val home = immersiveHomeSources()
        assertTrue("explicit caller flags can only degrade", home.contains("appFx.reconciled(reducedMotion, reducedTransparency)"))
        assertTrue("the gyroscope registers only when the resolved effect is on", home.contains("rememberImmersiveParallax(fx.gyroParallax)"))
        assertTrue("the frame clock only ticks for animated effects", home.contains("rememberImmersiveMotionClock(fx.needsMotionClock)"))
        // Bounded by the declaration and its column-0 "\n) {" (a default lambda containing ") {"
        // cannot truncate it), with the delimiter asserted, so the negative check cannot pass vacuously.
        val declaration = topLevelDeclaration(home, "internal fun ImmersiveHome(")
        val signatureEnd = declaration.indexOf("\n) {")
        assertTrue("ImmersiveHome signature end missing", signatureEnd > 0)
        val signature = declaration.substring(0, signatureEnd)
        assertTrue("the slice is the parameter list", signature.contains("debugFrozenTrackProgress: Float? = null"))
        assertFalse("no raw settings parameter: the baselined signature stays", signature.contains("visual:"))
        val card = topLevelDeclaration(home, "internal fun ImmersiveGameCard(")
        val layer = card.substring(card.indexOf(".graphicsLayer {"), card.indexOf("}.zIndex(frame.zIndex)"))
        assertTrue("tilt is read inside the layer block, so sensor events never recompose", layer.contains("translationX = parallax.x * parallaxPx"))
        assertTrue("side cards desaturate through the dedicated cached modifier", UiSourceFiles.file("ImmersiveHomeMetrics.kt").contains("immersiveDesaturate(saturation)"))
        assertTrue(UiSourceFiles.file("ImmersiveHomeFx.kt").contains("ImmersiveAmbientSpec.of(ambient) ?: return"))
    }

    @Test
    fun componentsModeKeepsItsOwnSaturationEffect() {
        // The immersive desaturation fix must not change components mode (plan §6.1).
        listOf("DashboardPages.kt", "SpotlightCards.kt").forEach { file ->
            assertTrue("$file keeps KireiShell.saturationEffect", UiSourceFiles.file(file).contains(".saturationEffect("))
        }
        val shell = UiSourceFiles.file("KireiShell.kt")
        val effect = shell.substring(shell.indexOf("internal fun Modifier.saturationEffect("))
        assertTrue("components mode keeps the API 31+ RenderEffect path", effect.contains("Build.VERSION.SDK_INT"))
    }

    @Test
    fun emptyGalleryStatesOfferTheGalleryEditorInsteadOfADeadEnd() {
        // r3 review: with 最近游玩 and no history the home showed only 「暂无游玩记录」 although the
        // library had games. Both empty sources now open the 编辑沉浸式展示图 editor.
        val home = UiSourceFiles.file("ImmersiveHome.kt")
        assertTrue(home.contains("ImmersiveGalleryEmptyState.NO_PLAY_HISTORY -> ImmersiveNoPlayHistoryState(palette) { editorOpen = true }"))
        assertTrue(home.contains("ImmersiveGalleryEmptyState.NO_FAVORITES -> ImmersiveNoFavoritesState(palette) { editorOpen = true }"))
        val states = UiSourceFiles.file("ImmersiveHomeStates.kt")
        listOf("internal fun ImmersiveNoPlayHistoryState(", "internal fun ImmersiveNoFavoritesState(").forEach { marker ->
            val body = topLevelDeclaration(states, marker)
            assertTrue("$marker offers the editor", body.contains("ImmersiveEmptyAction(IMMERSIVE_CHANGE_GALLERY_LABEL, palette, onOpenEditor)"))
        }
    }
}
