package com.kireibox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiListRowDefaults
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiSliderDefaults
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.yuki.yukihub.auth.CoverAccountStatus
import com.yuki.yukihub.metadata.model.CoverSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3 settings redesign (plan §5 P3, spec §4/§8): one list-group template on every page, a root
 * with unique category hues, the split 封面与元数据 page with its 来源账号 sub-page, inline
 * segmented choices for short option sets, and the KireiSlider used by the KRKR per-game page.
 */
class SettingsP3StructureTest {
    private val all: String by lazy { UiSourceFiles.all() }

    private val settingsFiles: List<Pair<String, String>> by lazy {
        val dir = listOf(File("src/main/java/com/kireibox/ui"), File("../app/src/main/java/com/kireibox/ui")).first { it.isDirectory }
        dir.listFiles { file -> file.isFile && file.name.startsWith("Settings") && file.extension == "kt" }
            .orEmpty()
            .sortedBy { it.name }
            .map { it.name to it.readText() }
            .also { assertTrue("settings sources found", it.size >= 10) }
    }

    @Test
    fun rootGroupsRunInOrderAndEveryCategoryTileHasItsOwnHue() {
        val root = UiSourceFiles.file("SettingsRootPage.kt")
        // r4 S1: 屏幕方向 moved from the root into 外观 → 显示; r5 added the 来源账号 shortcut, so the root
        // has ten category rows (个性化 外观 · 首页 / 游戏 ×4 / 系统 ×2 / 关于 ×2). Red is kept for warnings,
        // so the ten rows share nine hues: no two rows of one group share a hue (检查更新 and 来源账号
        // are INDIGO, in different groups).
        val tiles = Regex("""KireiTileColor\.([A-Z]+)""").findAll(root).map { it.groupValues[1] }.toList()
        assertEquals("10 category rows on the root: $tiles", 10, tiles.size)
        assertFalse("red stays reserved for warnings", tiles.contains("RED"))
        root.split("KireiListGroup(").drop(1).forEach { group ->
            val groupTiles = Regex("""KireiTileColor\.([A-Z]+)""").findAll(group.substringBefore("\n}\n")).map { it.groupValues[1] }.toList()
            assertEquals("no two rows of one root group share a tile hue: $groupTiles", groupTiles.size, groupTiles.toSet().size)
        }
        assertFalse("屏幕方向 is no longer a root row", root.contains("OrientationModeRow("))
        // Every category the root restores focus to has a row that opens it.
        listOf("APPEARANCE", "INTERFACE", "LIBRARY_HUB", "COVERS", "COVER_ACCOUNTS", "ENGINES", "PERMISSIONS", "DIAGNOSTICS", "ABOUT").forEach { page ->
            assertTrue("root row for $page", Regex("""SettingsCategoryRow\(\s*KireiSettingsPage\.$page,""").containsMatchIn(root))
        }
        assertTrue("the root also offers 检查更新", functionBody(root, "SettingsAboutGroup").contains("viewModel.checkForUpdates(manual = true)"))
        // r3 review: an available release always raises the modal update prompt and 稍后 ignores it,
        // so a 「新版本」 badge on this row could never be seen. The row is a plain command instead:
        // ACTION tone, the version on the root, the last check on 关于 (whose header shows the version).
        val updateRow = functionBody(root, "updateRowSpec")
        assertFalse("no badge the update prompt would always cover", updateRow.contains("KireiBadge("))
        assertTrue("检查更新 is a command row", updateRow.contains("tone = KireiRowTone.ACTION"))
        assertTrue("a manual check shows its progress", updateRow.contains("is UpdateUiState.Checking"))
        assertTrue(functionBody(root, "SettingsAboutGroup").contains("\"当前版本 \${BuildConfig.VERSION_NAME}\""))
        assertTrue(UiSourceFiles.file("SettingsAboutPage.kt").contains("lastUpdateCheckLabel(state.settings?.lastUpdateCheckAt)"))
        assertEquals("尚未检查过更新", lastUpdateCheckLabel(0L))
        assertTrue(lastUpdateCheckLabel(1_700_000_000_000L).startsWith("上次检查："))
        assertFalse("the 游戏 group has no footer about a badge that is usually hidden", functionBody(root, "SettingsGamesGroup").contains("footer ="))
        // Category rows keep the shared adaptive metrics (landscape compact rows clear the dock).
        assertTrue(functionBody(root, "SettingsCategoryRow").contains("withCategoryMetrics(resolveSettingsCategoryRowMetrics(context.orientation))"))
    }

    @Test
    fun everySettingsPageUsesTheLargeTitleTemplateAndTheSharedListGroups() {
        assertFalse("the old SettingsSection group is gone", Regex("""\bSettingsSection\b""").containsMatchIn(all))
        assertFalse("no second row layout", all.contains("SettingsRowLayout"))
        settingsFiles.forEach { (name, text) ->
            listOf("material3.Card", "material3.Slider", "material3.Button", "material3.TextButton", "OutlinedTextField(").forEach { stock ->
                assertFalse("$name uses stock $stock", text.contains(stock))
            }
            assertFalse("$name: raw color literal", text.contains("Color(0x"))
            assertFalse("$name: literal corner radius", Regex("""RoundedCornerShape\(\s*\d""").containsMatchIn(text))
            callArguments(text, "Text").forEach { call -> assertTrue("$name: Text( without explicit style: $call", call.contains("style =")) }
        }
        // Explanations live in group footers: these pages contain no free-standing Text paragraphs.
        listOf("SettingsCoverPages.kt", "SettingsLibraryPages.kt", "SettingsKrkrPages.kt", "SettingsRootPage.kt").forEach { name ->
            assertTrue("$name has no paragraph between rows", callArguments(UiSourceFiles.file(name), "Text").isEmpty())
        }
        val scaffold = UiSourceFiles.file("SettingsScaffold.kt")
        assertTrue(functionBody(scaffold, "SettingsLargeTitleLayout").contains("KireiLargeTopBar("))
        val layout = functionBody(scaffold, "SettingsLargeTitleLayout")
        assertTrue(Regex("""KireiLargeTitle\(\s*spec\.header\.title,\s*colors,""").containsMatchIn(layout))
        assertTrue("the bar collapses with the page scroll", layout.contains("collapse = { KireiTopBarDefaults.collapseFraction(scroll, density) }"))
        // The large title fades out over the first half of the collapse and the compact title fades in
        // over the second (r3 review: 1 − f / f showed both at ~50% on short pages), so both titles
        // never show through the glass at once.
        assertTrue("the large title hands over to the bar", layout.contains("alpha = KireiTopBarDefaults.largeTitleAlpha(KireiTopBarDefaults.collapseFraction(scroll, density))"))
        assertTrue(UiSourceFiles.designSystemFile("components/KireiLargeTopBar.kt").contains("alpha = KireiTopBarDefaults.compactTitleAlpha(collapse())"))
        val pages = UiSourceFiles.file("SettingsPages.kt")
        assertTrue(
            "sub-pages get the back button",
            // r4 S2: the header also names its page (search anchors), so the call continues after onBack.
            functionBody(pages, "SettingsSubPage").contains("SettingsPageScaffold(SettingsHeaderSpec(settingsPageTitle(page), onBack = viewModel::navigateUpSettings")
        )
        assertTrue(functionBody(UiSourceFiles.file("SettingsRootPage.kt"), "SettingsRootPage").contains("SettingsPageScaffold("))
        assertTrue("the KRKR per-game page shares the template", functionBody(UiSourceFiles.file("SettingsKrkrPages.kt"), "KrkrGameSettingsPage").contains("SettingsLargeTitleLayout("))
        // Page transition, saveable state and the adaptive frame stay wired.
        assertTrue(pages.contains("stateHolder.SaveableStateProvider(page.name)"))
        assertTrue(pages.contains("KireiBackPolicy.settingsDepth(targetState) >= KireiBackPolicy.settingsDepth(initialState)"))
        assertTrue(scaffold.contains("maxContentWidth = adaptiveMetrics.settingsContentWidthDp.dp"))
    }

    @Test
    fun coverPageSplitsSourcesMatchingAndManagementAndLinksTheAccountsSubPage() {
        val covers = UiSourceFiles.file("SettingsCoverPages.kt")
        var previous = -1
        listOf("title = \"来源与匹配\"", "title = \"自动匹配\"", "COVER_STRICT_MATCH_TITLE,", "title = \"成人内容\"", "title = \"待确认封面\"", "title = \"已设置封面\"").forEach { marker ->
            val at = covers.indexOf(marker, previous + 1)
            assertTrue("$marker follows the previous cover group", at > previous)
            previous = at
        }
        // r3 review: 「仅凭标题精确匹配」 read as "match using only the title"; the setting limits when
        // automatic binding applies (lexical evidence only, CoverMatchConfidence strict mode).
        assertEquals("仅标题一致时自动设置", COVER_STRICT_MATCH_TITLE)
        assertTrue("the plain-language switch still writes the stored strict setting", covers.contains("onCheckedChange = viewModel::setCoverStrictL2"))
        // r5: no footers and no subtitle that only repeats a title on the cover page; the one
        // subtitle left is the consequence that points at 默认来源.
        listOf("COVER_STRICT_MATCH_SUBTITLE", "COVER_MATCHING_FOOTER", "footer =").forEach { assertFalse("the cover page has no $it", covers.contains(it)) }
        assertTrue("同时查找多个来源 says what turning it off does", covers.contains("subtitle = \"关闭后只查默认来源\""))
        // One 自动匹配 group: the three switches, then the 清除无结果记录 command, all before 成人内容.
        val matching = functionBody(covers, "CoverMatchingGroups")
        val autoGroup = matching.substring(matching.indexOf("title = \"自动匹配\""), matching.indexOf("title = \"成人内容\""))
        listOf("\"同时查找多个来源\"", "\"仅 Wi-Fi 自动匹配\"", "COVER_STRICT_MATCH_TITLE,", "\"清除无结果记录\"").forEach { row ->
            assertTrue("$row sits in the 自动匹配 group", autoGroup.contains(row))
        }
        assertEquals("no single-row groups between 自动匹配 and 成人内容: only the 成人内容 group opens", 1, Regex("""KireiListGroup\(""").findAll(autoGroup).count())
        assertFalse("no engineering jargon left", all.contains("严格 L2"))
        assertTrue(covers.contains("viewModel.openSettingsPage(KireiSettingsPage.COVER_ACCOUNTS)"))
        assertTrue(all.contains("KireiSettingsPage.COVER_ACCOUNTS -> CoverAccountSettings(state, colors, viewModel)"))
        assertEquals("封面来源账号", settingsPageTitle(KireiSettingsPage.COVER_ACCOUNTS))
        assertEquals(KireiSettingsPage.COVERS, KireiBackPolicy.settingsParent(KireiSettingsPage.COVER_ACCOUNTS))
        // r5: 来源账号 is also a row of the root's 游戏 group (one tap, showing the sign-in summary) that
        // opens it as a shortcut: back returns to the root, while the row on this page keeps back here.
        val games = functionBody(UiSourceFiles.file("SettingsRootPage.kt"), "SettingsGamesGroup")
        assertTrue(Regex("""SettingsCategoryRow\(\s*KireiSettingsPage\.COVER_ACCOUNTS,""").containsMatchIn(games))
        assertTrue(games.contains("value = coverAccountSummary(state)"))
        assertTrue(UiSourceFiles.file("SettingsRootPage.kt").contains("viewModel.openSettingsShortcut(page)"))
        assertEquals(KireiSettingsPage.ROOT, KireiBackPolicy.settingsBackTarget(KireiSettingsPage.COVER_ACCOUNTS, KireiSettingsPage.COVER_ACCOUNTS))
        assertEquals(KireiSettingsPage.COVERS, KireiBackPolicy.settingsBackTarget(KireiSettingsPage.COVER_ACCOUNTS, null))
        assertTrue("default source is a sheet of described options", functionBody(covers, "CoverSourceGroup").contains("SettingsChoiceRow("))
        assertTrue("the overview card's shortcut scrolls to 待确认封面", covers.contains("reveal.consumePendingCovers()"))
        assertTrue(UiSourceFiles.file("SettingsRootPage.kt").contains("context.reveal.requestPendingCovers()"))
    }

    @Test
    fun coverListsRevealBoundedPagesSoALargeLibraryNeverDecodesEveryThumbnail() {
        // r3 review: 查看全部 composed every game (each decoding a cover thumbnail) in a non-lazy
        // column. Lists now show five rows and grow by pages of twenty.
        assertEquals(0, coverRowsShown(5, 0))
        assertEquals(3, coverRowsShown(5, 3))
        assertEquals(5, coverRowsShown(5, 300))
        assertEquals(25, coverRowsShown(25, 300))
        assertEquals("never past the list", 30, coverRowsShown(45, 30))
        assertEquals("a shrunken list never shows fewer than the preview", 5, coverRowsShown(0, 30))
        val covers = UiSourceFiles.file("SettingsCoverPages.kt")
        val group = functionBody(covers, "CoverGameGroup")
        assertTrue("only the shown rows are composed", group.contains("spec.games.take(spec.shown).forEach"))
        assertTrue(group.contains("\"显示更多（还有 \${spec.total - spec.shown} 个）\""))
        assertTrue(group.contains("onClick = if (more) spec.onShowMore else spec.onCollapse"))
        assertTrue(covers.contains("private const val COVER_PAGE_SIZE = 20"))
        assertFalse("no unbounded expansion", covers.contains("查看全部"))
    }

    @Test
    fun emptyCoverListsShowARowInsteadOfAFloatingCaption() {
        val covers = UiSourceFiles.file("SettingsCoverPages.kt")
        val group = functionBody(covers, "CoverGameGroup")
        assertTrue("an empty list shows its empty-state row", group.contains("if (spec.total == 0) item(key = \"cover-list-empty\") { KireiListRow(spec.empty, colors) }"))
        assertFalse("the lists carry no footer", group.contains("footer"))
        assertTrue(covers.contains("KireiListRowSpec(\"没有待确认的封面\""))
        assertTrue(covers.contains("KireiListRowSpec(\"还没有已设置的封面\""))
        // Each source's one-line description lives in its 默认来源 sheet option, not in a footer.
        assertFalse(functionBody(covers, "CoverSourceGroup").contains("footer = \"\${KireiPublicCopy"))
    }

    @Test
    fun permissionPageLeadsWithTheAuthorizationActionWhileAccessIsMissing() {
        val page = functionBody(UiSourceFiles.file("SettingsLibraryPages.kt"), "PermissionSettings")
        val button = page.indexOf("KireiButton(")
        assertTrue("the primary button only shows while KRKR lacks access", page.lastIndexOf("if (!access.nativeGranted) {", button) in 0 until button)
        assertTrue(page.contains("KireiButtonOptions(style = KireiButtonStyle.PRIMARY)"))
        assertTrue("the button requests access instead of printing the report", page.indexOf("actions.requestNativeAccess") > button)
        assertTrue("the report stays reachable as a row", page.contains("KireiListRowSpec(title = \"检查访问权限\", tone = KireiRowTone.ACTION), colors, onClick = actions.checkPermissions"))
        assertTrue("each root shows its status and repairs on tap", page.contains("AccessChip(granted, colors)") && page.contains("onClick = { actions.repairPermission(root) }"))
        // r3 review: the button sits in the native-access card, right under the row it acts on.
        assertTrue("the button shares the native row's group item", page.indexOf("KireiListRow(StorageAccessPolicy.nativeAccessRow(") in 0 until button)
        assertFalse("the status chip says it once; no second line repeats the scan page's count", page.contains("subtitle = \"已导入"))
        assertTrue("no roots → an empty-state row, not a caption over a floating footer", page.contains("item(key = \"no-roots\")"))
        assertFalse("the 扫描目录 and KRKR groups carry no explanatory footers", Regex("""title = "(扫描目录|KRKR 原生引擎)", footer""").containsMatchIn(page))
        val library = UiSourceFiles.file("SettingsLibraryPages.kt")
        // The chip agrees with the 检查访问权限 report: a grant only counts when the tree is still readable.
        assertTrue(library.contains("DocumentFile.fromTreeUri(context, Uri.parse(root))?.canRead() == true"))
        assertTrue("the readable check runs off the main thread", library.contains("withContext(Dispatchers.IO) { StorageAccessPolicy.read(context, roots, verifyReadable = true) }"))
        assertTrue("never 「授权存储权限」", library.contains("\"开启所有文件访问\" else \"授予存储权限\""))
        assertFalse("the native row no longer uses a shield (it read as 'protected')", library.contains("Icons.Rounded.Shield"))
        val launcher =
            listOf(File("src/main/java/com/kireibox/app/launcher/LauncherContent.kt"), File("../app/src/main/java/com/kireibox/app/launcher/LauncherContent.kt"))
                .first { it.isFile }
                .readText()
        assertTrue("the launcher wires the real request", launcher.contains("requestNativeAccess = this::requestNativeFileAccess,"))
    }

    @Test
    fun settingsGroupsAllSpaceThemselvesFromBelowAndNeverRepeatThePageTitle() {
        // One rhythm on every page: each group carries the 24dp gap below it (SettingsGroupSpacing),
        // so the first group starts the same distance under every large title.
        settingsFiles.forEach { (name, text) ->
            assertFalse("$name: top-spaced group", text.contains("padding(top = KireiSpacing.groupGap)"))
        }
        // r4 S1: the 首页 page's first group has no caption (the page is titled 首页); it still spaces
        // itself from below like every other group.
        assertTrue(functionBody(all, "InterfaceSettings").contains("KireiListGroup(colors, SettingsGroupSpacing, footer = homeStyleFooter(homeMode))"))
        assertTrue(functionBody(all, "HomeContentSettingsGroup").contains("KireiListGroup(colors, SettingsGroupSpacing, title = \"展示内容\""))
        assertTrue(functionBody(all, "ImmersiveVisualSettingsPanel").contains("val groupSpacing = SettingsGroupSpacing"))
        // The 扫描目录 page names its list instead of repeating the page title as a caption.
        val library = UiSourceFiles.file("SettingsLibraryPages.kt")
        assertTrue(functionBody(library, "ScanRootGroup").contains("title = \"已添加的目录\""))
        assertFalse(functionBody(library, "ScanRootGroup").contains("title = \"${settingsPageTitle(KireiSettingsPage.LIBRARY)}\""))
    }

    @Test
    fun accountCardsOfferTheRightButtonsAndSignOutAlwaysConfirms() {
        val bangumi = CoverSourceMode.BANGUMI
        val nextMoe = CoverSourceMode.NEXTMOE
        assertEquals(CoverAccountState.ANONYMOUS, coverAccountState(bangumi, null))
        assertEquals(CoverAccountState.SIGNED_OUT, coverAccountState(nextMoe, CoverAccountStatus()))
        assertEquals(CoverAccountState.SIGNED_IN, coverAccountState(nextMoe, CoverAccountStatus(connected = true, name = "yuki")))
        assertEquals(CoverAccountState.NEEDS_REAUTH, coverAccountState(nextMoe, CoverAccountStatus(connected = true, needsReauth = true)))

        fun labels(
            source: CoverSourceMode,
            state: CoverAccountState,
            busy: Boolean = false,
        ) = coverAccountButtons(source, state, busy).map { it.label to it.style }
        // r3 review: only a broken account (需要重新授权) gets a filled primary button, so the page
        // never shows three competing calls to action; signed-in NextMoe / Hikarinagi accounts can
        // sign in again (switch or refresh the account) as the old 重新授权 allowed.
        assertEquals(listOf("设置令牌" to KireiButtonStyle.SECONDARY), labels(bangumi, CoverAccountState.ANONYMOUS))
        assertEquals("退出 would race the token check", listOf("取消验证" to KireiButtonStyle.SECONDARY), labels(bangumi, CoverAccountState.ANONYMOUS, busy = true))
        assertEquals(listOf("更换令牌" to KireiButtonStyle.SECONDARY, "退出" to KireiButtonStyle.SECONDARY), labels(bangumi, CoverAccountState.SIGNED_IN))
        assertEquals(listOf("登录" to KireiButtonStyle.SECONDARY), labels(nextMoe, CoverAccountState.SIGNED_OUT))
        assertEquals(listOf("重新登录" to KireiButtonStyle.SECONDARY, "退出" to KireiButtonStyle.SECONDARY), labels(nextMoe, CoverAccountState.SIGNED_IN))
        assertEquals(listOf("重新登录" to KireiButtonStyle.PRIMARY, "退出" to KireiButtonStyle.SECONDARY), labels(nextMoe, CoverAccountState.NEEDS_REAUTH))

        val accounts = UiSourceFiles.file("SettingsCoverAccounts.kt")
        // Each card is one heading, and every button names its account ("登录 NextMoe").
        val card = functionBody(accounts, "CoverAccountCardBody")
        assertTrue(card.contains("Modifier.semantics(mergeDescendants = true) { heading() }"))
        assertTrue(card.contains("contentDescription = \"\${button.label} \${spec.source.label}\""))
        // Bangumi has no login: its card speaks of the token in every state.
        assertTrue(functionBody(accounts, "coverAccountLine").contains("\"令牌已失效，请更换令牌\""))
        assertFalse("account footers are their own sentences, not the sheet's · fragments", accounts.contains("KireiPublicCopy.coverSourceDescription"))
        // r5: the cards carry no per-source footer; the chip says the state and the line only what needs doing.
        assertFalse("no account footers", accounts.contains("footer"))
        assertFalse("a signed-out card has no filler line", accounts.contains("登录后自动匹配也会使用这个来源"))
        assertEquals("disconnect is only reachable through the confirmation", 1, Regex("""viewModel\.disconnectCoverAccount\(""").findAll(accounts).count())
        assertTrue(accounts.contains("CoverSignOutDialog(source, colors, onConfirm = { viewModel.disconnectCoverAccount(source) })"))
        val dialog = functionBody(accounts, "CoverSignOutDialog")
        assertTrue(dialog.contains("KireiDialog(") && dialog.contains("KireiButtonStyle.DESTRUCTIVE"))
        assertTrue("one card per account source", CoverAccountSources == listOf(bangumi, nextMoe, CoverSourceMode.HIKARINAGI))

        val signedIn = KireiUiState(coverAccounts = mapOf(nextMoe to CoverAccountStatus(connected = true, name = "yuki")))
        assertEquals("1 个已登录", coverAccountSummary(signedIn))
        assertEquals("未登录", coverAccountSummary(KireiUiState()))
        assertEquals("需要重新授权", coverAccountSummary(KireiUiState(coverAccounts = mapOf(bangumi to CoverAccountStatus(needsReauth = true)))))
    }

    @Test
    fun accountCardsPreferBundledBrandLogosOverInitials() {
        assertEquals("ic_cover_source_bangumi", coverSourceBrandDrawableName(CoverSourceMode.BANGUMI))
        assertEquals("ic_cover_source_nextmoe", coverSourceBrandDrawableName(CoverSourceMode.NEXTMOE))
        assertEquals("ic_cover_source_hikarinagi", coverSourceBrandDrawableName(CoverSourceMode.HIKARINAGI))
        val accounts = UiSourceFiles.file("SettingsCoverAccounts.kt")
        val logo = functionBody(accounts, "CoverSourceLogo")
        assertTrue("a bundled brand logo is preferred over the placeholder", logo.contains("painterResource(brandRes)"))
        assertTrue(logo.contains("coverSourceBrandDrawable(context, source)"))
        assertFalse("the initial-letter tile is gone", logo.contains("source.label.take(1)"))
        // The source logo never turns into the account's avatar; the avatar is a small circle before the name.
        assertFalse(logo.contains("avatarUrl"))
        val card = functionBody(accounts, "CoverAccountCardBody")
        assertTrue(card.contains("CoverSourceLogo(spec.source, colors.isDark)"))
        assertTrue(card.contains("CoverAccountAvatar(spec.source, spec.avatarUrl, colors)"))
    }

    @Test
    fun shortOptionSetsAreInlineSegmentsThatFitAPhoneAndLongerOnesUseASheet() {
        // ≤ 4 short options → segmented control; at 360dp each of four segments is ~70dp, room for
        // four CJK characters at 13sp, so every label of a four-option control stays within that.
        listOf(LibrarySortOptions.map(::choiceLabel), KrkrEngineVersions.map(::choiceLabel), KrkrMemoryLevels.map(::krkrOptionLabel), KrkrRenderers.map(::krkrOptionLabel))
            .forEach { labels ->
                assertTrue("$labels: at most four options", labels.size <= 4)
                assertTrue("$labels: short labels", labels.all { it.length <= 6 })
            }
        assertEquals(listOf("最近游玩", "添加时间", "名称", "自定义"), LibrarySortOptions.map(::choiceLabel))
        assertTrue(functionBody(all, "LibraryHubSettings").contains("SettingsSegmentedRow("))
        assertTrue(functionBody(all, "LibraryHubSettings").contains("viewModel::setSortMode"))
        val krkr = UiSourceFiles.file("SettingsKrkrPages.kt")
        assertTrue(functionBody(krkr, "KrkrSettings").contains("SettingsSegmentedRow("))
        assertTrue(functionBody(krkr, "KrkrSettings").contains("viewModel::setKrEngineVersion"))
        assertTrue("seven thread counts use the sheet", KrkrRenderThreads.size > 4 && functionBody(krkr, "KrkrGameEditor").contains("SettingsChoiceRow("))
        // A segmented row under an icon row starts at the row text, like the divider below it.
        val segmented = functionBody(all, "SettingsSegmentedRow")
        assertTrue(segmented.contains("val textStart = KireiListRowDefaults.textStart(header)"))
        assertTrue(segmented.contains("Modifier.padding(start = textStart"))
        val metrics = resolveSettingsCategoryRowMetrics(HomeOrientation.PORTRAIT)
        assertEquals(
            16.dp + metrics.iconSizeDp.dp + 14.dp,
            KireiListRowDefaults.textStart(KireiListRowSpec("屏幕方向", icon = Icons.Rounded.ScreenRotation).withCategoryMetrics(metrics))
        )
    }

    @Test
    fun krkrGamePageUsesTheKireiSliderTextFieldAndButton() {
        val krkr = UiSourceFiles.file("SettingsKrkrPages.kt")
        listOf(
            "SettingsSliderRow(",
            "KireiSliderSpec(label = \"FPS 限制\"",
            // r3 review: menu_handler_opa is the button's opacity, so 「透明度」 inverted its meaning.
            "KireiSliderSpec(label = \"菜单按钮不透明度\"",
            "KireiSliderSpec(label = \"虚拟光标大小\"",
            "SwitchRow(\"输出引擎日志\"",
            // Discrete 5-unit stops, so TalkBack and arrow keys land on round values.
            "valueRange = 15f..240f, steps = 44",
            "valueRange = .05f..1f, steps = 18",
            "valueRange = .25f..2f, steps = 34",
            "KireiTextFieldOptions(label = \"默认字体文件名\"",
            "KireiButton(",
            "viewModel.saveKrkrSettings(game, settings)",
            "BackHandler(onBack = viewModel::closeKrkrEditor)"
        )
            .forEach { assertTrue("KRKR page uses $it", krkr.contains(it)) }
        assertTrue(functionBody(UiSourceFiles.file("SettingsChoiceRows.kt"), "SettingsSliderRow").contains("KireiSlider("))
        listOf("菜单透明度", "\"虚拟光标\"", "打印日志", "原生模拟器").forEach { assertFalse("old wording $it is gone", all.contains(it)) }
        assertTrue("the one note says when the page applies", functionBody(krkr, "KrkrGameSettingsPage").contains("footer = \"下次启动时生效。\""))
        assertTrue("reduced motion swaps the font field in place", functionBody(krkr, "KrkrFontGroup").contains("if (reducedMotion) EnterTransition.None"))
        assertTrue(functionBody(all, "InterfaceSettings").contains("if (reducedMotion) EnterTransition.None"))
    }

    @Test
    fun sliderMapsPositionsToValuesAndExposesRangeSemantics() {
        val range = 15f..240f
        assertEquals(0f, KireiSliderDefaults.fraction(15f, range), .0001f)
        assertEquals(1f, KireiSliderDefaults.fraction(300f, range), .0001f)
        assertEquals(.5f, KireiSliderDefaults.fraction(127.5f, range), .0001f)
        assertEquals(127.5f, KireiSliderDefaults.valueAt(.5f, range), .0001f)
        assertEquals("clamped below", 15f, KireiSliderDefaults.valueAt(-.3f, range), .0001f)
        // Three stops between the ends snap to quarters.
        assertEquals(.5f, KireiSliderDefaults.valueAt(.4f, 0f..1f, steps = 3), .0001f)
        assertEquals(.25f, KireiSliderDefaults.increment(0f..1f, steps = 3), .0001f)
        assertEquals("continuous sliders step by 1/20", 11.25f, KireiSliderDefaults.increment(range, steps = 0), .0001f)
        assertEquals(240f, KireiSliderDefaults.stepped(235f, 1, range, steps = 0), .0001f)
        assertEquals(15f, KireiSliderDefaults.stepped(20f, -1, range, steps = 0), .0001f)
        // The KRKR FPS slider (44 stops) moves by exactly 5 FPS.
        assertEquals(5f, KireiSliderDefaults.increment(range, steps = 44), .0001f)
        assertEquals(65f, KireiSliderDefaults.stepped(60f, 1, range, steps = 44), .0001f)
        assertTrue("the touch band meets the 48dp minimum", KireiSliderDefaults.TouchHeight >= 48.dp)

        val slider = UiSourceFiles.designSystemFile("components/KireiSlider.kt")
        listOf(
            "contentDescription = spec.label",
            "stateDescription = spec.valueText",
            "progressBarRangeInfo = ProgressBarRangeInfo(",
            "setProgress { target ->",
            "detectHorizontalDragGestures(",
            "colors.accent",
            "colors.surface3",
            "KireiTokens.OnSolid",
            "kireiE2Shadow(",
            "AppleTypography.numericMedium",
            // Light thumb outline ≥ 3:1 on the white group (1.4.11) and a keyboard focus ring (2.4.7).
            "Modifier.border(KireiSwitchDefaults.ThumbStroke, KireiTokens.ControlBorderLight, KireiShapes.full)",
            ".onFocusChanged { focused = it.isFocused }",
            "Modifier.border(KireiSliderDefaults.FocusRing, colors.accent, KireiShapes.full)",
        ).forEach { assertTrue("KireiSlider: $it", slider.contains(it)) }
    }

    @Test
    fun largeTitleCollapsesFullyOnShortPagesAndNeverShowsBothTitles() {
        val density = androidx.compose.ui.unit.Density(1f)
        // A page with only 30px of scroll still reaches the compact title at its end.
        assertEquals(1f, KireiTopBarDefaults.collapseFraction(30f, 30f, density), .0001f)
        assertEquals(.5f, KireiTopBarDefaults.collapseFraction(15f, 30f, density), .0001f)
        // Long pages keep the 52dp collapse distance; a page that cannot scroll never collapses.
        assertEquals(.5f, KireiTopBarDefaults.collapseFraction(26f, 1000f, density), .0001f)
        assertEquals(0f, KireiTopBarDefaults.collapseFraction(0f, 0f, density), .0001f)
        (0..20).map { it / 20f }.forEach { fraction ->
            val large = KireiTopBarDefaults.largeTitleAlpha(fraction)
            val compact = KireiTopBarDefaults.compactTitleAlpha(fraction)
            assertTrue("at $fraction at most one title shows", large == 0f || compact == 0f)
        }
        assertEquals(1f, KireiTopBarDefaults.largeTitleAlpha(0f), 0f)
        assertEquals(1f, KireiTopBarDefaults.compactTitleAlpha(1f), 0f)
    }

    @Test
    fun fourOptionSegmentsBecomeASheetAtLargeFontScales() {
        // r3 review (WCAG 1.4.4): four 4-character labels need ~73dp each at 1.4× but a phone
        // segment is ~72dp, so a crowded control falls back to a value row with a sheet.
        assertFalse(SettingsSegmentedLayoutPolicy.useSheet(4, 1f))
        assertTrue(SettingsSegmentedLayoutPolicy.useSheet(4, 1.3f))
        assertTrue(SettingsSegmentedLayoutPolicy.useSheet(4, 2f))
        assertFalse("two or three short options still fit", SettingsSegmentedLayoutPolicy.useSheet(3, 2f))
        // r5: five options (背景样式 gained 封面氛围) never fit a phone segment, at any text size.
        assertTrue(SettingsSegmentedLayoutPolicy.useSheet(5, 1f))
        val segmented = functionBody(all, "SettingsSegmentedRow")
        assertTrue(segmented.contains("SettingsChoiceRow(SettingsChoiceSpec(spec.title"))
        assertTrue("wide windows cap the control", segmented.contains(".widthIn(max = SettingsSegmentedLayoutPolicy.MaxControlWidth)"))
    }

    @Test
    fun immersiveEffectsPageSaysWhenReducedMotionPausesThem() {
        // r4 S1: the global-setting hints (减少透明度) live in ImmersiveGlobalHints, which the effects
        // panel shows first; the row links to 外观 instead of duplicating a switch. The old
        // 「动态效果已暂停」 hint is gone: 精简 is now a real choice and 完整 is never silently paused.
        assertTrue(functionBody(all, "ImmersiveVisualSettingsPanel").contains("ImmersiveGlobalHints(state, visual, colors, viewModel)"))
        val hints = functionBody(all, "ImmersiveGlobalHints")
        assertFalse("the 动态效果已暂停 hint was removed", hints.contains("动态效果已暂停"))
        assertTrue("减少透明度 swapping the backdrop is said up front", hints.contains("title = \"背景已改用氛围色\""))
        assertTrue(hints.contains("viewModel.openSettingsPage(KireiSettingsPage.APPEARANCE)"))
        assertTrue(hints.contains("state.settings?.reducedTransparency == true && visual.backdrop in TranslucentBackdrops"))
    }

    @Test
    fun overviewStatTilesShareOneHeightAndBaseline() {
        val card = UiSourceFiles.file("SettingsOverviewCard.kt")
        assertTrue(card.contains("Modifier.fillMaxWidth().height(IntrinsicSize.Min)"))
        assertEquals("every tile stretches to the row", 3, Regex("""Modifier\.weight\(1f\)\.fillMaxHeight\(\)""").findAll(card).count())
        val stat = functionBody(card, "OverviewStat")
        assertTrue("the unit is split off the number", stat.contains("value.substringAfter(' ', missingDelimiterValue = \"\")"))
        assertTrue(stat.contains("Modifier.alignByBaseline()"))
        assertFalse("10sp labels are too small for CJK", stat.contains("labelSmall"))
    }

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
}
