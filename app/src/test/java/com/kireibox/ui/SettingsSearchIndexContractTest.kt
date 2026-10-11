package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * r4 · S2: the settings search index and the settings pages stay in step. Every row title rendered
 * by a settings page is a search entry (or excluded below with a reason), every group caption is
 * reachable through a title or a result path, every entry's page can be opened, and every entry's
 * anchor is registered on that page (so a tapped result always finds its row). The second half
 * pins the wiring from the search capsule to the highlighted row.
 */
class SettingsSearchIndexContractTest {
    private val entries = SettingsSearchIndex.entries

    /** Rows that are not settings, each with the reason it is not searchable. */
    private val excludedRows =
        mapOf(
            "背景已改用氛围色" to "status hint shown only while 减少透明度 overrides the backdrop; it links to 外观",
            "没有待确认的封面" to "empty-state row of the 待确认封面 list (the list itself is indexed)",
            "还没有已设置的封面" to "empty-state row of the 已设置封面 list (the list itself is indexed)",
            "还没有扫描目录" to "empty-state row of 存储与权限 › 扫描目录",
        ) +
            // The per-game KRKR page belongs to one game and opens from that game's sheet, not from
            // settings; 「KRKR 引擎」 carries these words as keywords and its footer says where they are.
            listOf("渲染器", "渲染线程", "纹理压缩", "内存", "FPS 限制", "显示 FPS", "调整字体大小", "菜单按钮不透明度", "虚拟光标大小", "输出引擎日志", "强制使用默认字体")
                .associateWith { "per-game KRKR setting (KrkrGameEditor), opened from a game's sheet" }

    private val excludedCaptions = listOf("渲染", "性能", "界面与调试", "字体").associateWith { "per-game KRKR page group (KrkrGameEditor)" }

    private val settingsFiles: List<Pair<String, String>> by lazy {
        val dir = listOf(File("src/main/java/com/kireibox/ui"), File("../app/src/main/java/com/kireibox/ui")).first { it.isDirectory }
        dir.listFiles { file -> file.isFile && file.name.startsWith("Settings") && file.extension == "kt" && !file.name.startsWith("SettingsSearch") }
            .orEmpty()
            .sortedBy { it.name }
            .map { it.name to it.readText() }
            .also { assertTrue("settings sources found", it.size >= 15) }
    }

    @Test
    fun entriesAreUniqueAndEveryPageHasOnePageEntry() {
        assertEquals("unique result keys", entries.size, entries.map { it.id }.toSet().size)
        entries.groupBy { it.page }.forEach { (page, onPage) ->
            assertEquals("$page: unique titles", onPage.size, onPage.map { it.title }.toSet().size)
        }
        KireiSettingsPage.entries.filter { it != KireiSettingsPage.ROOT }.forEach { page ->
            assertEquals("$page has one page entry", 1, entries.count { it.page == page && it.anchor == null })
        }
        entries.forEach { entry ->
            assertTrue("${entry.title}: no blank keyword", entry.keywords.none { it.isBlank() })
            assertTrue("${entry.title}: a summary is never blank", entry.subtitle?.isNotBlank() != false)
        }
    }

    @Test
    fun everyEntryOpensARoutablePageAndShowsItsPath() {
        val subPage = functionBody(UiSourceFiles.file("SettingsPages.kt"), "SettingsSubPage")
        entries.forEach { entry ->
            if (entry.page == KireiSettingsPage.ROOT) {
                assertEquals("only 快速设置 lives on the root", SettingsSearchIndex.QUICK_SETUP_ANCHOR, entry.anchor)
                return@forEach
            }
            val branch = Regex("""KireiSettingsPage\.${entry.page.name} -> (\w+)\(""").find(subPage)
            assertNotNull("${entry.page} renders a page", branch)
            assertNotNull("${entry.page} navigates back to the root", KireiBackPolicy.settingsParent(entry.page))
            val path = entry.location.breadcrumb.split(SettingsSearchIndex.SEPARATOR)
            if (entry.anchor != null) {
                assertEquals("${entry.title}: the path starts at its page", pagePath(entry.page).first(), path.first())
                assertTrue("${entry.title}: the path names its page: $path", settingsPageTitle(entry.page) in path)
            }
        }
    }

    @Test
    fun everyRowTitleOnASettingsPageIsSearchableOrExcludedWithAReason() {
        val indexed = entries.map { it.title }.toSet()
        val rows = settingsFiles.flatMap { (name, text) -> rowTitles(text).map { name to it } } + ("ImmersiveHomeStates.kt" to SettingsSearchIndex.HOME_STYLE_ANCHOR)
        assertTrue("row titles found", rows.size > 60)
        rows.forEach { (file, title) ->
            assertTrue("$file: row 「$title」 is not in SettingsSearchIndex (add it, or exclude it with a reason)", title in indexed || title in excludedRows)
        }
        // The exclusions are real rows, so the list cannot rot.
        val rendered = rows.map { it.second }.toSet()
        excludedRows.keys.forEach { assertTrue("excluded 「$it」 still exists", it in rendered) }
        assertTrue("the strict-matching switch is indexed by its constant", COVER_STRICT_MATCH_TITLE in indexed)
        assertTrue(UiSourceFiles.file("ImmersiveHomeStates.kt").contains("title = \"${SettingsSearchIndex.HOME_STYLE_ANCHOR}\""))
    }

    @Test
    fun everyGroupCaptionLeadsSomewhere() {
        val reachable = entries.flatMap { it.location.breadcrumb.split(SettingsSearchIndex.SEPARATOR) + it.title }.toSet()
        val captions = settingsFiles.flatMap { (name, text) -> groupCaptions(text).map { name to it } }
        assertTrue("captions found", captions.size > 25)
        captions.forEach { (file, caption) ->
            assertTrue("$file: group 「$caption」 is neither a result title nor on a result path", caption in reachable || caption in excludedCaptions)
        }
    }

    @Test
    fun everyAnchorIsRegisteredOnItsPage() {
        entries.filter { it.anchor != null && it.page != KireiSettingsPage.ROOT }.forEach { entry ->
            val anchors = registeredAnchors(pageSource(entry.page))
            assertTrue("${entry.page}: no row registers 「${entry.anchor}」 (SettingsAnchor or a titled settings row); found $anchors", entry.anchor in anchors)
        }
        // Immersive-only results fall back to 首页样式, which exists in both home styles (above the immersive gate).
        val interfaceSettings = functionBody(UiSourceFiles.file("SettingsLibraryPages.kt"), "InterfaceSettings")
        assertTrue(SettingsSearchIndex.HOME_STYLE_ANCHOR in registeredAnchors(functionBody(UiSourceFiles.file("ImmersiveHomeStates.kt"), "HomePresentationModeRow")))
        assertTrue(interfaceSettings.indexOf("HomePresentationModeRow(") in 0 until interfaceSettings.indexOf("visible = homeMode == HomePresentationMode.IMMERSIVE"))
        // Exactly the rows inside 「高级选项」 are marked IMMERSIVE_ADVANCED, so the panel opens before they are looked for.
        val visual = UiSourceFiles.file("SettingsImmersiveVisual.kt")
        val advanced =
            listOf("ImmersiveBackdropSettingsGroup", "ImmersiveCardSettingsGroup", "ImmersiveAmbientSettingsGroup", "ImmersiveRestoreDefaultsGroup")
                .joinToString("\n") { functionBody(visual, it) }
        val marked = entries.filter { it.location.scope == SettingsSearchScope.IMMERSIVE_ADVANCED && it.anchor != "高级选项" }.mapNotNull { it.anchor }.toSet()
        assertEquals(registeredAnchors(advanced), marked)
    }

    @Test
    fun resultTilesMatchTheRootCategoryRows() {
        val root = UiSourceFiles.file("SettingsRootPage.kt")
        val rows = Regex("""SettingsCategoryRow\(\s*KireiSettingsPage\.(\w+),\s*SettingsCategoryRowSpec\(\s*Icons\.Rounded\.(\w+),[\s\S]*?KireiTileColor\.(\w+)""").findAll(root).toList()
        assertEquals("nine root category rows", 9, rows.size)
        rows.forEach { row ->
            val (page, icon, tile) = row.destructured
            val (resultIcon, resultTile) = settingsSearchPageVisual(KireiSettingsPage.valueOf(page))
            assertEquals("$page result tile", tile, resultTile.name)
            assertTrue("$page result icon $icon", resultIcon.name.endsWith(icon))
        }
    }

    @Test
    fun theCapsuleLeadsTheRootAndBackClearsTheQueryFirst() {
        val root = functionBody(UiSourceFiles.file("SettingsRootPage.kt"), "SettingsRootPage")
        assertTrue(root.contains("val search = rememberSettingsSearchState()"))
        // Coming back from a result keeps the query, so results (not the category rows) show: focus
        // restoration must not ask an unattached category FocusRequester for focus (it throws).
        val restore = root.substring(root.indexOf("LaunchedEffect(Unit) {"), root.indexOf("?.requestFocus()"))
        assertTrue("category focus is only restored without a query", restore.contains("?.takeUnless { search.searching }"))
        assertTrue(root.indexOf("val search = rememberSettingsSearchState()") < root.indexOf("LaunchedEffect(Unit) {"))
        assertTrue("back clears the query before leaving settings", root.contains("SettingsSearchEffects(search, scroll)"))
        val effects = functionBody(UiSourceFiles.file("SettingsSearchState.kt"), "SettingsSearchEffects")
        assertTrue(effects.contains("SettingsSearchBackHandler(search)"))
        assertTrue("results wait for the IME to commit", effects.contains("SettingsSearchResultsSync(search)"))
        assertTrue("a drag puts the keyboard away", effects.contains("if (interaction is DragInteraction.Start && search.focused) focusManager.clearFocus()"))
        // A prompt over the root (e.g. 首页加载失败) must win back over the search handler registered after it.
        assertTrue(UiSourceFiles.file("KireiShell.kt").contains("key(activePrompt) {\n        BackHandler(enabled = activePrompt?.dismissLabel != null && !closing)"))
        assertTrue("the collapsed bar can bring the capsule back", root.contains("onSearch = rememberSettingsSearchShortcut(search, scroll)"))
        assertTrue("the capsule sits right under the large title", root.indexOf("SettingsSearchBar(search, colors)") in 0 until root.indexOf("SettingsOverviewCard("))
        val resultsAt = root.indexOf("SettingsSearchResults(search, context)")
        assertTrue("a query replaces the overview and groups", root.indexOf("if (search.searching) {") in 0 until resultsAt && resultsAt < root.indexOf("} else {"))
        assertTrue(root.contains("openSettingsSearchResult(entry, context) { quickSetupOpen = true }"))
        val ui = UiSourceFiles.file("SettingsSearchPage.kt")
        val back = functionBody(UiSourceFiles.file("SettingsSearchState.kt"), "SettingsSearchBackHandler")
        assertTrue(back.contains("BackHandler(enabled = search.active)"))
        assertTrue(back.indexOf("search.cancel()") < back.indexOf("focusManager.clearFocus(force = true)"))
        val bar = functionBody(ui, "SettingsSearchBar")
        assertTrue("取消 exits the search", bar.contains("\"取消\"") && bar.contains("visible = search.active"))
        assertTrue("the 搜索 key puts the keyboard away", bar.contains("onSearch = { focusManager.clearFocus() }"))
        val field = UiSourceFiles.designSystemFile("components/KireiSearchField.kt")
        assertTrue(field.contains("KeyboardOptions(imeAction = ImeAction.Search)"))
        assertTrue(field.contains("KeyboardActions(onSearch = { options.onSearch() })"))
        assertTrue("the field can be cleared in one tap", field.contains("SearchClearButton(visible = value.isNotEmpty()"))
        val empty = functionBody(ui, "SettingsSearchEmpty")
        assertEquals("没有找到相关设置", SETTINGS_SEARCH_EMPTY)
        assertTrue(empty.contains("SETTINGS_SEARCH_EMPTY,"))
        assertTrue("suggestions fill the query", empty.contains("SettingsSearchIndex.suggestions.forEach"))
        val results = functionBody(ui, "SettingsSearchResults")
        assertTrue("the last results scroll above the keyboard", results.contains("windowInsetsBottomHeight(WindowInsets.ime)"))
        assertTrue("a suggestion updates the field and the results at once", results.contains("search.fill(suggestion)"))
        // IME composition (pinyin 「shense」 before 「深色」) must not drive the results or the empty state.
        assertTrue(results.contains("remember(search.resultsQuery) { SettingsSearch.search(SettingsSearchIndex.entries, search.resultsQuery) }"))
        assertTrue(field.contains("options.onComposingChange(next.composition != null)"))
        assertTrue(functionBody(ui, "SettingsSearchBar").contains("onComposingChange = { search.composing = it }"))
        val sync = functionBody(UiSourceFiles.file("SettingsSearchState.kt"), "SettingsSearchResultsSync")
        assertTrue("committed text searches at once", sync.contains("if (!composing) {\n                search.resultsQuery = text"))
        assertTrue("composing text never shows the empty state", sync.contains("if (SettingsSearch.search(SettingsSearchIndex.entries, text).isNotEmpty()) search.resultsQuery = text"))
        // TalkBack: one persistent live-region node carries 「找到 N 项」 / 「没有找到相关设置」.
        assertTrue(results.contains("SettingsSearchAnnouncement(if (results.isEmpty()) SETTINGS_SEARCH_EMPTY else \"找到 ${'$'}{results.size} 项\", search.composing)"))
        val announcement = functionBody(ui, "SettingsSearchAnnouncement")
        assertTrue(announcement.contains("liveRegion = LiveRegionMode.Polite"))
        assertFalse("only the persistent node is a live region", empty.contains("liveRegion"))
        // The capsule keeps its height and its name once text is typed; the clear button says its name once.
        assertTrue(field.contains("private val SearchFieldHeight = 48.dp"))
        assertTrue(field.contains("Box(Modifier.semantics { text = AnnotatedString(options.placeholder) })"))
        assertFalse(field.contains("onClickLabel = label"))
    }

    @Test
    fun aResultOpensItsPageThenItsRowScrollsIntoViewAndPulses() {
        val ui = UiSourceFiles.file("SettingsSearchPage.kt")
        val open = functionBody(ui, "openSettingsSearchResult")
        assertTrue(open.indexOf("context.reveal.requestSearchTarget(target)") in 0 until open.indexOf("context.viewModel.openSettingsShortcut(target.page)"))
        // Back from a result's page (even 扫描目录 / 来源账号, one level deeper) returns to the root's results once.
        val viewModel = UiSourceFiles.file("KireiViewModel.kt")

        // View-model members are indented: cut each body at its own closing brace.
        fun member(name: String) = viewModel.substring(viewModel.indexOf("fun $name(")).substringBefore("\n        }\n")
        // The root's 来源账号 row and the cover picker use the same shortcut, so back lands on the root too.
        assertTrue(member("openSettingsShortcut").contains("settingsShortcutOrigin = page"))
        assertTrue(member("navigateUpSettings").contains("KireiBackPolicy.settingsBackTarget(mutableState.value.settingsPage, settingsShortcutOrigin)"))
        assertTrue("any other navigation forgets the origin", member("openSettingsPage").contains("settingsShortcutOrigin = null"))
        assertTrue(member("selectTab").contains("settingsShortcutOrigin = null"))
        assertTrue("快速设置 opens its sheet", open.contains("if (entry.anchor == SettingsSearchIndex.QUICK_SETUP_ANCHOR) {"))
        val scaffold = UiSourceFiles.file("SettingsScaffold.kt")
        assertTrue("each page hosts its anchors", scaffold.contains("CompositionLocalProvider(LocalSettingsAnchorHost provides host)"))
        assertTrue(UiSourceFiles.file("SettingsPages.kt").contains("onBack = viewModel::navigateUpSettings, page = page)"))
        val anchors = UiSourceFiles.file("SettingsSearchAnchors.kt")
        val anchor = functionBody(anchors, "SettingsAnchor")
        assertTrue(anchor.contains("val host = LocalSettingsAnchorHost.current"))
        assertTrue("rows tint behind themselves", anchor.contains("Modifier.drawBehind { drawRowHighlight(colors, highlight.value) }"))
        assertTrue(functionBody(anchors, "drawRowHighlight").contains("colors.accentContainer"))
        assertTrue(functionBody(anchors, "drawGroupHighlight").contains("colors.accentContainer"))
        // A group result tints its card only (not the caption above or the footer below).
        assertTrue(functionBody(anchors, "SettingsGroupAnchor").contains("Box(modifier.onPlaced { placement.coordinates = it }) { content(wash) }"))
        assertTrue(UiSourceFiles.file("SettingsCoverPages.kt").contains("cardModifier = cardHighlight"))
        assertTrue(UiSourceFiles.designSystemFile("components/KireiListGroup.kt").contains(".then(scope.cardModifier)"))
        val reveal = functionBody(anchors, "SettingsAnchorReveal")
        assertTrue("one shot: the row consumes the target", reveal.contains("host.reveal.consumeSearchTarget()"))
        assertTrue("the reveal runs in the row's own scope (no leak past the row)", reveal.contains("scope.launch { host.revealAnchor(placement, highlight, reducedMotion) }"))
        assertTrue(reveal.contains("it.page == host.page && it.anchor == key"))
        val run = functionBody(anchors, "revealAnchor")
        assertTrue(run.contains("SettingsRevealMath.scrollTarget(row, band, scroll.value, scroll.maxValue)"))
        assertTrue("reduced motion jumps", run.contains("if (reducedMotion) scroll.scrollTo(target) else scroll.animateScrollTo("))
        assertTrue(run.indexOf("pulseSettingsHighlight(highlight, reducedMotion)") > run.indexOf("scrollTarget("))
        val pulse = functionBody(anchors, "pulseSettingsHighlight")
        assertTrue("static under reduced motion", pulse.contains("highlight.snapTo(1f)") && pulse.contains("delay(SettingsAnchorMotion.STATIC_HOLD_MS)"))
        assertTrue(pulse.contains("highlight.animateTo(1f, tween(SettingsAnchorMotion.PULSE_IN_MS))"))
        assertTrue("stale targets are dropped", functionBody(anchors, "rememberSettingsAnchorHost").contains("context.reveal.consumeSearchTarget(target)"))
        // 高级选项 opens itself for a detail result, so the row exists to scroll to.
        val panel = functionBody(UiSourceFiles.file("SettingsImmersiveVisual.kt"), "ImmersiveVisualSettingsPanel")
        assertTrue(panel.contains("if (searchTarget?.page == KireiSettingsPage.INTERFACE && searchTarget.expandAdvanced) advancedOpen = true"))
    }

    @Test
    fun theSharedRowPrimitivesRegisterTheirTitles() {
        val components = UiSourceFiles.file("SettingsComponents.kt")
        assertTrue(functionBody(components, "SwitchRow").contains("SettingsAnchor(label)"))
        val segmented = UiSourceFiles.file("SettingsSegmentedRow.kt")
        assertTrue(functionBody(segmented, "SettingsSegmentedRow").contains("SettingsAnchor(spec.title)"))
        assertTrue(functionBody(segmented, "SettingsToggleRow").contains("SettingsAnchor(title)"))
        val choices = UiSourceFiles.file("SettingsChoiceRows.kt")
        assertTrue(functionBody(choices, "SettingsChoiceRow").contains("SettingsAnchor(spec.title)"))
        assertTrue(functionBody(choices, "SettingsSliderRow").contains("SettingsAnchor(spec.label)"))
        // The large-font sheet fallback anchors through SettingsChoiceRow only: never two anchors per row.
        val fallback = functionBody(segmented, "SettingsSegmentedRow")
        assertTrue(fallback.indexOf("SettingsChoiceRow(") < fallback.indexOf("return") && fallback.indexOf("return") < fallback.indexOf("SettingsAnchor(spec.title)"))
    }

    /** Pages that are also a row of the root (r5): their results show a path that starts at the page itself. */
    private val rootShortcuts = setOf(KireiSettingsPage.COVER_ACCOUNTS)

    /** Page titles from the root down to [page] (e.g. 游戏库 › 扫描目录). */
    private fun pagePath(page: KireiSettingsPage): List<String> =
        generateSequence(page) { KireiBackPolicy.settingsParent(it)?.takeIf { parent -> parent != KireiSettingsPage.ROOT && it !in rootShortcuts } }
            .map(::settingsPageTitle)
            .toList()
            .reversed()

    /** The source that renders [page]'s rows. */
    @Suppress("CyclomaticComplexMethod") // one branch per page
    private fun engineOrientationRow() = functionBody(UiSourceFiles.file("SettingsEnginePages.kt"), "EngineOrientationRow")

    private fun enginePageSource(page: KireiSettingsPage): String {
        val (file, function) =
            when (page) {
                KireiSettingsPage.KRKR -> "SettingsKrkrPages.kt" to "KrkrSettings"
                KireiSettingsPage.ONS -> "SettingsEnginePages.kt" to "OnsSettingsPage"
                KireiSettingsPage.ARTEMIS -> "SettingsEnginePages.kt" to "ArtemisSettingsPage"
                else -> "SettingsEnginePages.kt" to "TyranoSettingsPage"
            }
        return functionBody(UiSourceFiles.file(file), function) + engineOrientationRow()
    }

    private fun pageSource(page: KireiSettingsPage): String {
        val library = UiSourceFiles.file("SettingsLibraryPages.kt")
        return when (page) {
            KireiSettingsPage.APPEARANCE ->
                functionBody(UiSourceFiles.file("SettingsAppearancePage.kt"), "AppearanceSettings") +
                    functionBody(UiSourceFiles.file("SettingsComponents.kt"), "MotionModeRow") +
                    functionBody(UiSourceFiles.file("SettingsComponents.kt"), "OrientationModeRow")
            KireiSettingsPage.INTERFACE ->
                functionBody(library, "InterfaceSettings") + functionBody(UiSourceFiles.file("ImmersiveHomeStates.kt"), "HomePresentationModeRow") +
                    UiSourceFiles.file("SettingsHomePage.kt") + UiSourceFiles.file("SettingsImmersiveVisual.kt")
            KireiSettingsPage.LIBRARY_HUB -> functionBody(library, "LibraryHubSettings")
            KireiSettingsPage.LIBRARY -> functionBody(library, "LibrarySettings") + functionBody(library, "ScanRootGroup")
            KireiSettingsPage.PERMISSIONS -> functionBody(library, "PermissionSettings")
            KireiSettingsPage.COVERS -> UiSourceFiles.file("SettingsCoverPages.kt")
            KireiSettingsPage.COVER_ACCOUNTS, KireiSettingsPage.PLAYTIME_SYNC -> UiSourceFiles.file("SettingsCoverAccounts.kt") + UiSourceFiles.file("SettingsPlaytimeSync.kt")
            KireiSettingsPage.ENGINES -> functionBody(UiSourceFiles.file("SettingsEnginePages.kt"), "EngineHubSettings")
            KireiSettingsPage.KRKR, KireiSettingsPage.ONS, KireiSettingsPage.ARTEMIS, KireiSettingsPage.TYRANO -> enginePageSource(page)
            KireiSettingsPage.WINLATOR ->
                listOf("WinlatorSettingsPage", "WinlatorContainerGroup", "WinlatorGraphicsGroup", "WinlatorLocaleGroup", "WinlatorAdvancedGroup")
                    .joinToString("\n") { functionBody(UiSourceFiles.file("SettingsWinlatorPages.kt"), it) }
            KireiSettingsPage.DIAGNOSTICS -> functionBody(UiSourceFiles.file("SettingsAboutPage.kt"), "DiagnosticSettings")
            KireiSettingsPage.ABOUT -> functionBody(UiSourceFiles.file("SettingsAboutPage.kt"), "AboutSettings")
            KireiSettingsPage.ROOT -> ""
        }
    }

    /** Titles of the rows [source] renders: list rows, switches, segmented / choice rows, sliders and root categories. */
    private fun rowTitles(source: String): List<String> =
        listOf(
            """KireiListRowSpec\(\s*(?:title = )?"([^"$]+)"""",
            """SettingsCategoryRowSpec\(\s*Icons\.Rounded\.\w+,\s*"([^"$]+)"""",
        ).flatMap { pattern -> Regex(pattern).findAll(source).map { it.groupValues[1] } } + primitiveTitles(source)

    /** Titles registered by the anchored row primitives. */
    private fun primitiveTitles(source: String): List<String> {
        val literal =
            listOf(
                """(?<![\w.])SwitchRow\(\s*"([^"$]+)"""",
                """SettingsToggleRow\(\s*"([^"$]+)"""",
                """SettingsSegmentedRowSpec\(\s*(?:title = )?"([^"$]+)"""",
                """SettingsChoiceSpec\(\s*(?:title = )?"([^"$]+)"""",
                """KireiSliderSpec\(\s*label = "([^"$]+)"""",
            ).flatMap { pattern -> Regex(pattern).findAll(source).map { it.groupValues[1] } }
        val constant = if (source.contains("SettingsToggleRow(COVER_STRICT_MATCH_TITLE")) listOf(COVER_STRICT_MATCH_TITLE) else emptyList()
        return literal + constant
    }

    /** Every anchor key [source] registers: explicit SettingsAnchor keys plus the primitives' titles. */
    private fun registeredAnchors(source: String): Set<String> {
        val explicit = Regex("""SettingsAnchor\(\s*"([^"$]+)"""").findAll(source).map { it.groupValues[1] }.toList()
        val coverLists =
            if (source.contains("SettingsGroupAnchor(spec.title, modifier) { cardHighlight ->")) {
                Regex("""CoverGameGroupSpec\(\s*title = "([^"$]+)"""").findAll(source).map { it.groupValues[1] }.toList()
            } else {
                emptyList()
            }
        val accounts = if (source.contains("SettingsAnchor(source.label)")) CoverAccountSources.map { it.label } else emptyList()
        val native = if (source.contains("SettingsAnchor(SettingsSearchIndex.NATIVE_ACCESS_ANCHOR)")) listOf(SettingsSearchIndex.NATIVE_ACCESS_ANCHOR) else emptyList()
        return (explicit + coverLists + accounts + native + primitiveTitles(source)).toSet()
    }

    /** `title = "…"` captions of every KireiListGroup( call (arguments only, not the rows). */
    private fun groupCaptions(source: String): List<String> = callArguments(source, "KireiListGroup").mapNotNull { call -> Regex("""title = "([^"$]+)"""").find(call)?.groupValues?.get(1) }

    /** Source of the top-level function (or extension) [name]: from its `fun` keyword to the next top-level declaration. */
    private fun functionBody(
        source: String,
        name: String,
    ): String {
        val start = Regex("""fun (?:<[^>]+> )?(?:\w+\.)?$name\(""").find(source)?.range?.first ?: error("fun $name( not found")
        val end = Regex("""\n(?=[@A-Za-z/])""").find(source, start)?.range?.first ?: source.length
        return source.substring(start, end)
    }

    /** Argument lists of every `name(` call, paren-balanced and ignoring parens inside string literals. */
    private fun callArguments(
        source: String,
        name: String,
    ): List<String> =
        Regex("""(?<![\w.])$name\(""").findAll(source).map { match ->
            var depth = 0
            var inString = false
            var index = match.range.last
            while (index < source.length) {
                val char = source[index]
                if (char == '"' && source[index - 1] != '\\') inString = !inString
                if (!inString && char == '(') depth++
                if (!inString && char == ')') depth--
                if (depth == 0) break
                index++
            }
            source.substring(match.range.first, index + 1)
        }.toList()

    @Test
    fun noRowIsAnchoredTwiceOnItsPage() {
        // Two anchors with one key would both scroll and pulse.
        (KireiSettingsPage.entries - KireiSettingsPage.ROOT).forEach { page ->
            val source = pageSource(page)
            val explicit = Regex("""SettingsAnchor\(\s*"([^"$]+)"""").findAll(source).map { it.groupValues[1] }.toList()
            val auto = primitiveTitles(source)
            assertFalse("$page: an anchored primitive is wrapped again: ${explicit.intersect(auto.toSet())}", explicit.any { it in auto })
            assertEquals("$page: explicit anchors are unique", explicit.size, explicit.toSet().size)
        }
    }
}
