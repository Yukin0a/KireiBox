package com.kireibox.ui

import com.kireibox.designsystem.components.KireiStatusTone
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.MetadataCover
import com.yuki.yukihub.metadata.model.VnMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * r5 · B (「横屏搜索封面完全用不了，全被挡住了，搜出来的选项根本展示不下」): the landscape cover picker is a
 * two-pane layout whose results keep the height the header, query and actions used to take; the
 * keyboard never covers them. Pure layout rules plus the source wiring that the emulator checks.
 */
class CoverPickerLayoutPolicyTest {
    private fun candidate(
        id: String,
        covers: Int = 1,
    ) = CoverCandidateUi(
        metadata =
            VnMetadata(id = id, chineseTitle = "作品 $id", coverUrl = "https://example.invalid/$id/0.jpg").apply {
                repeat(covers) { this.covers += MetadataCover("https://example.invalid/$id/$it.jpg", 600, 800, "VNDB", "safe") }
            },
        source = CoverSourceMode.VNDB,
        score = 80.0,
    )

    @Test
    fun landscapePhonesSplitIntoASearchPaneAndAResultsPane() {
        // Pixel 8 (2400×1080 @420), 1600×720 @320 and a 640×360dp phone, all landscape.
        val pixel = CoverPickerLayoutPolicy.resolve(914.3f, 411.4f)
        assertEquals(CoverPickerArrangement.TWO_PANE, pixel.arrangement)
        assertEquals(914.3f * .34f, pixel.sidePaneWidthDp, .01f)
        val short = CoverPickerLayoutPolicy.resolve(800f, 360f)
        assertEquals(CoverPickerArrangement.TWO_PANE, short.arrangement)
        assertEquals(272f, short.sidePaneWidthDp, .01f)
        assertEquals("clamped to the narrowest usable search pane", 248f, CoverPickerLayoutPolicy.resolve(640f, 360f).sidePaneWidthDp, .01f)
        assertEquals("never wider than 340dp", 340f, CoverPickerLayoutPolicy.resolve(1200f, 540f).sidePaneWidthDp, .01f)
    }

    @Test
    fun theResultsPaneOfALandscapePhoneHoldsTwoCardsPerRow() {
        // Results width = window − two 18dp gutters − the search pane − the 20dp pane gap.
        listOf(914.3f to 411.4f, 800f to 360f).forEach { (width, height) ->
            val frame = CoverPickerLayoutPolicy.resolve(width, height)
            val results = width - 2 * AdaptiveLayoutConstants.PAGE_GUTTER_DP - frame.sidePaneWidthDp - 20f
            assertEquals("$width×$height", 2, CoverPickerLayoutPolicy.columns(results))
        }
    }

    @Test
    fun portraitPhonesKeepOneColumnAndTabletsKeepTheCenteredCard() {
        assertEquals(CoverPickerArrangement.SINGLE, CoverPickerLayoutPolicy.resolve(411.4f, 914.3f).arrangement)
        assertEquals(CoverPickerArrangement.SINGLE, CoverPickerLayoutPolicy.resolve(360f, 640f).arrangement)
        assertEquals("a narrow split-screen window", CoverPickerArrangement.SINGLE, CoverPickerLayoutPolicy.resolve(500f, 420f).arrangement)
        assertEquals(1, CoverPickerLayoutPolicy.columns(411.4f - 2 * AdaptiveLayoutConstants.PAGE_GUTTER_DP))
        // ≥ 700dp wide and tall enough: the centered card, in portrait (as before) and landscape tablets.
        assertEquals(CoverPickerArrangement.CARD, CoverPickerLayoutPolicy.resolve(800f, 1280f).arrangement)
        assertEquals(CoverPickerArrangement.CARD, CoverPickerLayoutPolicy.resolve(1280f, 800f).arrangement)
        assertEquals("foldable inner screen (2208×1840 @420)", CoverPickerArrangement.CARD, CoverPickerLayoutPolicy.resolve(841f, 701f).arrangement)
        assertEquals(0f, CoverPickerLayoutPolicy.resolve(800f, 1280f).sidePaneWidthDp, 0f)
        // A 760dp card with 24dp padding: three cards per row.
        assertEquals(3, CoverPickerLayoutPolicy.columns(760f - 48f))
    }

    @Test
    fun columnsFitWholeCardsAndStopAtThree() {
        assertEquals(1, CoverPickerLayoutPolicy.columns(100f))
        assertEquals(1, CoverPickerLayoutPolicy.columns(451f))
        assertEquals(2, CoverPickerLayoutPolicy.columns(452f))
        assertEquals(2, CoverPickerLayoutPolicy.columns(683f))
        assertEquals(3, CoverPickerLayoutPolicy.columns(684f))
        assertEquals(3, CoverPickerLayoutPolicy.columns(2000f))
        (300..1400 step 7).forEach { width ->
            val columns = CoverPickerLayoutPolicy.columns(width.toFloat())
            val card = (width - (columns - 1) * CoverPickerLayoutPolicy.CARD_GAP_DP) / columns
            assertTrue("$width dp: $columns columns of $card dp", columns == 1 || card >= CoverPickerLayoutPolicy.CARD_MIN_WIDTH_FOR_COLUMN_DP)
        }
    }

    @Test
    fun theSelectedWorksCoverVersionsFollowItsRow() {
        val candidates = (0 until 5).map { candidate("v$it", covers = if (it == 2) 3 else 1) }
        val rows = CoverPickerLayoutPolicy.rows(candidates, columns = 2, selectedKey = candidates[2].key)
        assertEquals(listOf("cards:${candidates[0].key}", "cards:${candidates[2].key}", "variants:${candidates[2].key}", "cards:${candidates[4].key}"), rows.map { it.key })
        assertEquals(listOf("v2", "v3"), (rows[1] as CoverResultRow.Cards).candidates.map { it.metadata.id })
        assertEquals("v2", (rows[2] as CoverResultRow.Variants).candidate.metadata.id)
        // The selected card is the second in its row: the strip still waits for the whole row.
        val second = CoverPickerLayoutPolicy.rows(candidates, columns = 2, selectedKey = candidates[3].key)
        assertTrue("one cover: no versions strip", second.none { it is CoverResultRow.Variants })
        val single = CoverPickerLayoutPolicy.rows(candidates, columns = 1, selectedKey = candidates[2].key)
        assertEquals("one column: right under the card", "variants:${candidates[2].key}", single[3].key)
        assertTrue(CoverPickerLayoutPolicy.rows(candidates, columns = 3, selectedKey = null).all { it is CoverResultRow.Cards })
        listOf(1, 2, 3).forEach { columns ->
            val keys = CoverPickerLayoutPolicy.rows(candidates, columns, candidates[2].key).map { it.key }
            assertEquals("lazy keys are unique", keys.size, keys.toSet().size)
            val shown = CoverPickerLayoutPolicy.rows(candidates, columns, candidates[2].key).filterIsInstance<CoverResultRow.Cards>().flatMap { it.candidates }
            assertEquals("every candidate once, in order", candidates, shown)
        }
    }

    @Test
    fun emptyStatesSayWhyAndPointToTheRealSetting() {
        val unreachable = CoverPickerLayoutPolicy.emptyState(allUnreachable = true, includeAdult = false)
        assertEquals("全部来源都连不上", unreachable.title)
        assertEquals("重新搜索", unreachable.actionLabel)
        val adult = CoverPickerLayoutPolicy.emptyState(allUnreachable = false, includeAdult = false)
        assertEquals("没有找到可用封面", adult.title)
        assertTrue(adult.message!!.contains("「搜索结果包含成人作品」"))
        assertTrue("the hint names the row as it is titled", UiSourceFiles.file("SettingsCoverPages.kt").contains("SwitchRow(\"搜索结果包含成人作品\""))
        assertFalse(CoverPickerLayoutPolicy.emptyState(allUnreachable = false, includeAdult = true).message!!.contains("成人"))

        val failed = CoverSearchUi(sourceHits = mapOf(CoverSourceMode.BANGUMI to 0, CoverSourceMode.VNDB to 0), sourceFailed = setOf(CoverSourceMode.BANGUMI, CoverSourceMode.VNDB))
        assertTrue(CoverPickerLayoutPolicy.allUnreachable(failed))
        assertFalse(CoverPickerLayoutPolicy.allUnreachable(failed.copy(sourceFailed = setOf(CoverSourceMode.BANGUMI))))
        assertFalse(CoverPickerLayoutPolicy.allUnreachable(CoverSearchUi()))
    }

    @Test
    fun coverVersionCaptionsUsePlainWords() {
        assertEquals("VNDB · 1200×1600 · 全年龄", CoverVariantCaption.text(MetadataCover("u", 1200, 1600, "VNDB", "safe")))
        assertEquals("Hikarinagi · 轻微暗示", CoverVariantCaption.text(MetadataCover("u", 0, 0, "Hikarinagi", "suggestive")))
        assertEquals("800×1000", CoverVariantCaption.text(MetadataCover("u", 800, 1000, "", "unknown")))
        assertEquals("成人向", CoverVariantCaption.rating("explicit"))
        assertEquals("成人向", CoverVariantCaption.rating("2"))
        assertEquals("轻微暗示", CoverVariantCaption.rating("1.2"))
        assertEquals("全年龄", CoverVariantCaption.rating("0"))
        assertNull(CoverVariantCaption.rating("unknown"))
        assertEquals("", CoverVariantCaption.text(MetadataCover("u")))
    }

    @Test
    fun sourceChipsCarryTheirStateInTheDotAndTheWords() {
        assertEquals(KireiStatusTone.ACCENT, CoverHybridStatus.tone(loading = true, attempted = false, failed = false, hits = 0))
        assertEquals(KireiStatusTone.POSITIVE, CoverHybridStatus.tone(loading = false, attempted = true, failed = false, hits = 4))
        assertEquals(KireiStatusTone.NEUTRAL, CoverHybridStatus.tone(loading = false, attempted = true, failed = false, hits = 0))
        assertEquals(KireiStatusTone.WARNING, CoverHybridStatus.tone(loading = false, attempted = true, failed = true, hits = 0))
        assertEquals(KireiStatusTone.NEUTRAL, CoverHybridStatus.tone(loading = false, attempted = false, failed = false, hits = 0))
        val connected = { source: CoverSourceMode -> source == CoverSourceMode.VNDB || source == CoverSourceMode.NEXTMOE }
        assertEquals(listOf(CoverSourceMode.HIKARINAGI), CoverHybridStatus.missingLogins(CoverSourceMode.active, connected))
        assertEquals(CoverHybridStatus.needsLoginHint(CoverSourceMode.active, connected), CoverHybridStatus.missingLogins(CoverSourceMode.active, connected).isNotEmpty())
    }

    @Test
    fun thePickerIsWiredForTheKeyboardAndTheNewComponents() {
        val page = UiSourceFiles.file("CoverPickerPages.kt")
        val results = UiSourceFiles.file("CoverPickerResults.kt")
        val picker = UiSourceFiles.group("CoverPicker")
        // The arrangement comes from the whole window, so the keyboard never flips it.
        assertTrue(page.contains("val frame = CoverPickerLayoutPolicy.resolve(maxWidth.value, maxHeight.value)"))
        // safeDrawing includes the IME: every arrangement ends at the keyboard's top.
        assertTrue(page.contains("val safe = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)"))
        assertEquals("all three arrangements are padded by it", 3, Regex("""\bsafe[.,)]""").findAll(page).count())
        // Resize instead of pan while the picker is open, restored when it closes.
        assertTrue(page.contains("WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE"))
        assertTrue(page.contains("onDispose { if (window != null && previous != null) window.setSoftInputMode(previous) }"))
        // The keyboard's 搜索 action (and the 搜索 button) search again and close the keyboard.
        assertTrue(page.contains("KireiSearchFieldOptions(placeholder = \"输入游戏名称\", onSearch = runSearch)"))
        val runSearch = page.substring(page.indexOf("val runSearch = {"), page.indexOf("Row(", page.indexOf("val runSearch = {")))
        listOf("scope.viewModel.retryCoverSearch()", "keyboard?.hide()", "focus.clearFocus()").forEach { assertTrue(it, runSearch.contains(it)) }
        // While typing, the actions give their place to the results (both arrangements).
        assertEquals(2, Regex("""if \(!imeVisible\)""").findAll(page).count())
        assertTrue("the search pane scrolls on short windows", page.contains("Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)"))
        // Results: rows from the policy, the versions strip after the selected row, a reveal consumed once.
        assertTrue(results.contains("CoverPickerLayoutPolicy.rows(search.candidates, columns, search.selectedKey)"))
        assertTrue(results.contains("CoverPickerLayoutPolicy.columns(maxWidth.value)"))
        assertTrue(results.contains("requester.bringIntoView()\n            onRevealed()"))
        assertTrue(results.contains("role = Role.RadioButton"))
        // Component library only (spec §8/§9): no Material text field, buttons or literal shapes.
        listOf("KireiSearchField(", "KireiStatusChip(", "KireiButton(", "KireiEmptyState(", "KireiSkeleton(", "SelectionRing(", "CheckBadge(").forEach { assertTrue(it, picker.contains(it)) }
        listOf("OutlinedTextField", "TextButton(", "material3.Button", "RoundedCornerShape(", "CircularProgressIndicator", "accent.copy(alpha").forEach { assertFalse(it, picker.contains(it)) }
        // The album / file actions stay on the page (LocalCoverAutoRefreshWiringTest).
        listOf("\"从相册选择\"", "\"从手机文件选择\"", "actions.chooseCoverFromAlbum", "actions.chooseCoverFromFiles").forEach { assertTrue(it, page.contains(it)) }
    }

    @Test
    fun theLoginHintIsAnActionThatLeavesThePickerForTheAccountsPage() {
        val page = UiSourceFiles.file("CoverPickerPages.kt")
        val status = page.substring(page.indexOf("private fun CoverSourceStatus("), page.indexOf("private fun CoverPickerError("))
        // A button (48dp touch target, button role), not a caption, shown only while a login is missing.
        val action = status.substring(status.indexOf("if (missing.isNotEmpty()) {"))
        assertTrue(action.contains("CoverHybridStatus.loginActionLabel(missing)"))
        assertTrue(action.contains("scope.viewModel::openCoverAccountsFromPicker"))
        assertTrue("it cannot interrupt a save", action.contains("enabled = !search.saving"))
        assertFalse("no breadcrumb sentence any more", page.contains("可以找到更多封面"))
        // The view model drops the picker (stopping its search, not while saving) and opens 来源账号 as a shortcut.
        val viewModel = UiSourceFiles.file("KireiViewModel.kt")
        val open = viewModel.substring(viewModel.indexOf("fun openCoverAccountsFromPicker(")).substringBefore("\n        }\n")
        assertTrue(open.contains("if (mutableState.value.coverSearch?.saving == true) return"))
        assertTrue(open.contains("coverSearchJob?.cancel()"))
        assertTrue(open.contains("coverSearch = null, taskCenterOpen = false"))
        assertTrue(open.contains("openSettingsShortcut(KireiSettingsPage.COVER_ACCOUNTS)"))
    }

    @Test
    fun everyTextInThePickerHasAnExplicitStyle() {
        val picker = UiSourceFiles.group("CoverPicker")
        val calls = Regex("""(?<![A-Za-z])Text\(""").findAll(picker).map { match -> arguments(picker, match.range.last + 1) }.toList()
        assertTrue(calls.size >= 10)
        calls.forEach { args -> assertTrue("Text($args) has no style", args.contains("style = ")) }
    }

    /** The argument list of a call whose opening parenthesis ends at [start] − 1, paren-balanced. */
    private fun arguments(
        source: String,
        start: Int,
    ): String {
        var depth = 1
        var index = start
        while (depth > 0 && index < source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> depth--
            }
            index++
        }
        return source.substring(start, index - 1)
    }
}
