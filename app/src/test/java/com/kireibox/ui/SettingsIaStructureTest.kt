package com.kireibox.ui

import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.ImmersiveCardSwitch
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ImmersiveGallerySettings
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * r4 S1 settings IA (user request 「外观和首页与沉浸式功能重复了」): 外观 owns everything about how the
 * whole app looks and behaves (theme, accent, 动画效果, 减少透明度, 增强对比度, 屏幕方向); 首页 owns
 * the home style, the shown games and the immersive effects. No setting is wired from two pages —
 * an effect that depends on a global switch links to 外观 instead. The 快速设置 guide is a shortcut
 * that writes through the very same setters, not a third settings page.
 */
class SettingsIaStructureTest {
    private val settingsPages: Map<String, String> by lazy {
        val dir = listOf(File("src/main/java/com/kireibox/ui"), File("../app/src/main/java/com/kireibox/ui")).first { it.isDirectory }
        dir.listFiles { file -> file.isFile && file.name.startsWith("Settings") && file.extension == "kt" }
            .orEmpty()
            .associate { it.name to it.readText() }
    }

    /** Every settings page source except the guide, which deliberately reuses the pages' setters. */
    private val pageSources: Map<String, String> by lazy { settingsPages - "SettingsQuickSetup.kt" }

    private val appearancePage: String by lazy { functionBody(UiSourceFiles.file("SettingsAppearancePage.kt"), "AppearanceSettings") }

    private val homePage: String by lazy {
        functionBody(UiSourceFiles.file("SettingsLibraryPages.kt"), "InterfaceSettings") +
            UiSourceFiles.file("SettingsHomePage.kt") +
            UiSourceFiles.file("SettingsImmersiveVisual.kt")
    }

    @Test
    fun everyAppWideSettingIsWiredFromExactlyOnePageAndThatPageIsAppearance() {
        listOf("setThemeMode", "setAccentChoice", "setMotionMode", "setReducedTransparency", "setContrast", "setOrientationMode").forEach { setter ->
            val sites = pageSources.flatMap { (name, text) -> List(occurrences(text, "viewModel::$setter")) { name } }
            assertEquals("viewModel::$setter is wired from exactly one settings page: $sites", listOf("SettingsAppearancePage.kt"), sites)
            assertTrue("$setter lives in AppearanceSettings", appearancePage.contains("viewModel::$setter"))
            assertFalse("首页 does not duplicate $setter", homePage.contains("viewModel::$setter"))
        }
    }

    @Test
    fun everyHomeSettingIsWiredFromTheHomePageOnly() {
        listOf(
            "setHomePresentationMode",
            "setImmersivePlayTimeVisible",
            "setImmersiveGalleryMode",
            "setImmersiveGalleryIds",
            "setImmersiveGallerySource",
            "setImmersiveGalleryStrategy",
            "setImmersiveGalleryCount"
        )
            .forEach { setter ->
                val sites = pageSources.filterValues { it.contains("viewModel::$setter") }.keys
                assertEquals("viewModel::$setter is wired once, on the 首页 page: $sites", 1, pageSources.values.sumOf { occurrences(it, "viewModel::$setter") })
                assertTrue("$setter lives on 首页", homePage.contains("viewModel::$setter"))
                assertFalse("外观 does not duplicate $setter", appearancePage.contains("viewModel::$setter"))
            }
        // The effects write one normalized snapshot, from the 首页 effects panel only.
        val effectWrites = pageSources.filterValues { it.contains("viewModel.setImmersiveVisual(") }.keys
        assertEquals(setOf("SettingsImmersiveVisual.kt"), effectWrites)
    }

    @Test
    fun theRootPersonalGroupHoldsAppearanceAndHomeOnly() {
        val root = UiSourceFiles.file("SettingsRootPage.kt")
        val personal = functionBody(root, "SettingsPersonalGroup")
        assertEquals(
            listOf("APPEARANCE", "INTERFACE"),
            Regex("""SettingsCategoryRow\(\s*KireiSettingsPage\.([A-Z_]+),""").findAll(personal).map { it.groupValues[1] }.toList(),
        )
        assertFalse("no inline setting on the root", Regex("""viewModel::set""").containsMatchIn(root))
        assertEquals(KireiSettingsPage.ROOT, KireiBackPolicy.settingsParent(KireiSettingsPage.INTERFACE))
        assertEquals(1, KireiBackPolicy.settingsDepth(KireiSettingsPage.INTERFACE))
        assertTrue(UiSourceFiles.all().contains("KireiSettingsPage.INTERFACE -> InterfaceSettings(state, colors, viewModel)"))
        listOf("SettingsGamesGroup(", "SettingsSystemGroup(", "SettingsAboutGroup(").forEach { assertTrue(it, root.contains(it)) }
    }

    @Test
    fun theImmersiveContentAndEffectsOnlyShowInImmersiveMode() {
        val page = functionBody(UiSourceFiles.file("SettingsLibraryPages.kt"), "InterfaceSettings")
        val gate = page.indexOf("visible = homeMode == HomePresentationMode.IMMERSIVE,")
        assertTrue(gate >= 0)
        assertTrue(page.indexOf("HomeContentSettingsGroup(state, colors, viewModel)") > gate)
        assertTrue(page.indexOf("ImmersiveVisualSettingsPanel(state, colors, viewModel)") > page.indexOf("HomeContentSettingsGroup("))
        // 展示内容 opens the immersive home's own editor sheet, so both entries edit the same thing.
        val content = functionBody(UiSourceFiles.file("SettingsHomePage.kt"), "HomeContentSettingsGroup")
        assertTrue(content.contains("ImmersiveGalleryConfigSheet("))
        assertTrue(content.contains("LibraryOverviewPolicy.sorted(state.games, LibraryOverviewSort.RECENT)"))
    }

    @Test
    fun presetCardsRecommendStandardAndTheAdvancedOptionsStartCollapsed() {
        assertEquals(ImmersiveFxPreset.STANDARD, RecommendedImmersivePreset)
        assertTrue(immersivePresetCardDescription(ImmersiveFxPreset.STANDARD).contains("推荐"))
        ImmersiveFxPreset.entries.filter { it != RecommendedImmersivePreset }.forEach { assertFalse(immersivePresetCardDescription(it).contains("推荐")) }
        ImmersiveFxPreset.entries.forEach { preset ->
            val line = immersivePresetDescription(preset)
            assertTrue("$preset has a one-line description", line.isNotBlank() && line.length <= 16 && !line.contains("\n"))
        }
        val panel = functionBody(UiSourceFiles.file("SettingsImmersiveVisual.kt"), "ImmersiveVisualSettingsPanel")
        assertTrue("高级选项 is collapsed by default", panel.contains("var advancedOpen by rememberSaveable { mutableStateOf(false) }"))
        assertTrue("the 自定义 card opens 高级选项", panel.contains("if (preset == ImmersiveFxPreset.CUSTOM) advancedOpen = true"))
        assertTrue(panel.contains("visible = advancedOpen,"))
        assertTrue(panel.contains("ImmersiveRestoreDefaultsGroup(visual, colors, write)"))
        val restore = functionBody(UiSourceFiles.file("SettingsImmersiveVisual.kt"), "ImmersiveRestoreDefaultsGroup")
        assertTrue(
            "恢复默认 always asks first",
            restore.contains("KireiDialog(") && restore.indexOf("write(visual.withPreset(RecommendedImmersivePreset))") > restore.indexOf("KireiDialogAction(\"恢复默认\"")
        )
    }

    @Test
    fun presetSelectionKeepsDetailsForCustomAndResetsThemForNamedPresets() {
        val custom = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.RICH).copy(grain = false).normalized()
        assertEquals(ImmersiveFxPreset.CUSTOM, custom.preset)
        val standard = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD)
        val toCustom = immersivePresetSelection(ImmersiveFxPreset.CUSTOM, standard)
        assertEquals(ImmersiveFxPreset.CUSTOM, toCustom.preset)
        assertTrue("自定义 starts from today's details", toCustom.sameDetailsAs(standard))
        assertEquals("…and stays custom once normalized", ImmersiveFxPreset.CUSTOM, toCustom.normalized().preset)
        assertEquals(ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.MINIMAL), immersivePresetSelection(ImmersiveFxPreset.MINIMAL, custom))
        // 切换方式 is a general choice: picking any preset keeps it.
        val staggered = custom.copy(cardSwitch = ImmersiveCardSwitch.STAGGER)
        ImmersiveFxPreset.entries.forEach { assertEquals(ImmersiveCardSwitch.STAGGER, immersivePresetSelection(it, staggered).cardSwitch) }
    }

    @Test
    fun presetGridShowsThreeAcrossWhenTheyFitAndTwoOtherwise() {
        assertEquals("quick setup on a 411dp phone", 3, ImmersivePresetGridPolicy.columns(3, 379f, 1f))
        assertEquals("four presets on a phone page", 2, ImmersivePresetGridPolicy.columns(4, 351f, 1f))
        assertEquals("four presets on a wide column", 4, ImmersivePresetGridPolicy.columns(4, 600f, 1f))
        assertEquals("large text drops to two", 2, ImmersivePresetGridPolicy.columns(3, 328f, 1.3f))
        assertTrue("never zero columns", ImmersivePresetGridPolicy.columns(4, 90f, 1f) >= 1)
        // Two columns only when two minimum-width cards fit: a 360dp phone at 2× text gets one card per row.
        assertEquals("very large text: one per row", 1, ImmersivePresetGridPolicy.columns(4, 328f, 2f))
    }

    @Test
    fun homeContentSummaryReadsAsOnePlainSentence() {
        fun summary(
            mode: SpotlightSelectionMode = SpotlightSelectionMode.AUTOMATIC,
            ids: List<Long> = emptyList(),
            source: SpotlightSource = SpotlightSource.RECENT,
            strategy: SpotlightStrategy = SpotlightStrategy.FIRST,
            count: Int = 5,
        ) = homeContentSummary(ImmersiveGallerySettings(mode, ids, source, strategy, count))
        assertEquals("最近打开的 5 个游戏", summary())
        assertEquals("收藏中的前 3 个游戏", summary(source = SpotlightSource.FAVORITES, count = 3))
        assertEquals("游戏库中的前 5 个游戏", summary(source = SpotlightSource.ALL))
        assertEquals("从收藏中随机 3 个，每天更换", summary(source = SpotlightSource.FAVORITES, strategy = SpotlightStrategy.DAILY_RANDOM, count = 3))
        assertEquals("从全部游戏中随机 5 个，每次启动更换", summary(source = SpotlightSource.ALL, strategy = SpotlightStrategy.RANDOM_EACH_LAUNCH))
        assertEquals("a legacy fixed source reads as 最近打开", "最近打开的 2 个游戏", summary(source = SpotlightSource.FIXED, count = 2))
        assertEquals("counts are clamped to the editor's 1–5", "最近打开的 5 个游戏", summary(count = 9))
        assertEquals("手动挑选的 2 个游戏", summary(mode = SpotlightSelectionMode.MANUAL, ids = listOf(3L, 7L, 3L)))
        assertEquals("手动挑选，还没有选择游戏", summary(mode = SpotlightSelectionMode.MANUAL))
    }

    @Test
    fun quickSetupNamesItselfAnnouncesEachStepAndOffersSkipOnlyWhileUseful() {
        assertTrue(QuickSetupFlow.canSkip(QuickSetupStep.THEME))
        assertFalse("跳过 beside 完成 would do the same thing", QuickSetupFlow.canSkip(QuickSetupStep.HOME))
        assertEquals("the step names the control's own word", "选择首页风格", QuickSetupStep.HOME.title)
        assertEquals("在首页菜单的「编辑首页」调整布局。", homeStyleFooter(HomePresentationMode.COMPONENTS))
        assertNull("only components mode says where its layout is edited", homeStyleFooter(HomePresentationMode.IMMERSIVE))
        val guide = UiSourceFiles.file("SettingsQuickSetup.kt")
        val header = functionBody(guide, "QuickSetupHeader")
        assertTrue("TalkBack hears 「快速设置」 when the sheet opens", header.contains("semantics { paneTitle = QUICK_SETUP_TITLE }"))
        assertTrue("…and each new step title", header.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(header.contains("if (QuickSetupFlow.canSkip(step)) {"))
        val indicator = functionBody(guide, "QuickSetupStepIndicator")
        assertTrue("the flow is named beside the progress", indicator.contains("\"\$QUICK_SETUP_TITLE · \${step.ordinal + 1} / \${QuickSetupFlow.steps.size}\""))
        assertTrue("steps ahead are visible on the sheet", indicator.contains("if (colors.isDark) colors.tertiary else KireiTokens.ControlBorderLight"))
        assertFalse(indicator.contains("colors.surface3"))
        // No explanation lines: the steps carry only their heading, and the suggestion only its title and 开始.
        assertFalse("no 接下来 line under the steps", guide.contains("upNext"))
        assertFalse("no explanation under the step heading", functionBody(guide, "QuickSetupHeader").contains("step.subtitle"))
        assertFalse("no body line on the suggestion card", functionBody(guide, "QuickSetupSuggestion").contains("AppleTypography.bodySmall"))
    }

    @Test
    fun keyboardFocusIsVisibleOnEveryCardAndSwatchOfTheGuide() {
        // WCAG 2.4.7: the preset cards, theme cards and accent swatches use indication = null, so focus has its own ring.
        val appearance = UiSourceFiles.file("SettingsAppearancePage.kt")
        val presets = UiSourceFiles.file("SettingsImmersivePresets.kt")
        assertTrue(functionBody(appearance, "FocusRing").contains("border(SelectionRingWidth, colors.text, shape)"))
        assertTrue(functionBody(presets, "ImmersivePresetCard").contains("FocusRing(focused, colors, PresetRingShape, Modifier.matchParentSize())"))
        assertTrue(functionBody(appearance, "ThemeModeCard").contains("FocusRing(focused, colors, ThemeCardRingShape, Modifier.matchParentSize())"))
        assertTrue(functionBody(appearance, "AccentSwatch").contains("FocusRing(focused, colors, CircleShape, Modifier.size(SwatchRingSize))"))
        listOf(functionBody(presets, "ImmersivePresetCard"), functionBody(appearance, "ThemeModeCard"), functionBody(appearance, "AccentSwatch")).forEach { card ->
            assertTrue(card.contains("val focused by interaction.collectIsFocusedAsState()"))
        }
    }

    @Test
    fun presetDescriptionsBreakOnlyBetweenPhrases() {
        ImmersiveFxPreset.entries.forEach { preset ->
            val line = immersivePresetDisplayLine(immersivePresetDescription(preset))
            // Every line break opportunity left is right after 「，」 or 「、」.
            line.forEachIndexed { index, char ->
                if (index > 0 && char != '\u2060' && line[index - 1] != '\u2060') {
                    assertTrue("$preset: unjoined break inside a phrase at $index", line[index - 1] in "，、")
                }
            }
            assertEquals("the visible text is unchanged", immersivePresetDescription(preset), line.replace("\u2060", ""))
        }
        val spec = ImmersivePresetCardsSpec(ImmersiveVisualSettings(), customDescription = QUICK_SETUP_KEEP_CUSTOM)
        assertEquals(QUICK_SETUP_KEEP_CUSTOM, spec.description(ImmersiveFxPreset.CUSTOM))
        assertEquals(immersivePresetDescription(ImmersiveFxPreset.MINIMAL), spec.description(ImmersiveFxPreset.MINIMAL))
        assertTrue(immersivePresetCardDescription(ImmersiveFxPreset.CUSTOM, QUICK_SETUP_KEEP_CUSTOM).endsWith(QUICK_SETUP_KEEP_CUSTOM))
    }

    @Test
    fun theOverviewMovesItsSyncButtonUnderTheNameWhenTheVersionWouldClip() {
        assertFalse("a 412dp phone at 1×", overviewStacksAction(348f, 1f))
        assertFalse("a 360dp phone at 1× stays on the title row", overviewStacksAction(292f, 1f))
        assertFalse("a 360dp phone at 1.3× stays on the title row", overviewStacksAction(292f, 1.3f))
        assertFalse("a 320dp phone at 1× stays on the title row", overviewStacksAction(252f, 1f))
        assertTrue("a genuinely tiny card stacks", overviewStacksAction(220f, 1f))
        assertTrue("very large text stacks", overviewStacksAction(348f, 1.6f))
        val card = UiSourceFiles.file("SettingsOverviewCard.kt")
        assertTrue(card.contains("Text(BuildConfig.VERSION_NAME, color = colors.secondary, style = AppleTypography.numericSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)"))
    }

    @Test
    fun quickSetupStepsRunThemeAccentHomeWithPlainNavigation() {
        assertEquals(listOf(QuickSetupStep.THEME, QuickSetupStep.ACCENT, QuickSetupStep.HOME), QuickSetupFlow.steps)
        assertNull(QuickSetupFlow.previous(QuickSetupStep.THEME))
        assertEquals(QuickSetupStep.ACCENT, QuickSetupFlow.next(QuickSetupStep.THEME))
        assertEquals(QuickSetupStep.ACCENT, QuickSetupFlow.previous(QuickSetupStep.HOME))
        assertNull(QuickSetupFlow.next(QuickSetupStep.HOME))
        assertEquals("下一步", QuickSetupFlow.primaryLabel(QuickSetupStep.THEME))
        assertEquals("完成", QuickSetupFlow.primaryLabel(QuickSetupStep.HOME))
        assertEquals("第 2 步，共 3 步", QuickSetupFlow.progressLabel(QuickSetupStep.ACCENT))
        assertEquals(listOf(ImmersiveFxPreset.MINIMAL, ImmersiveFxPreset.STANDARD, ImmersiveFxPreset.RICH), ImmersivePresetSegments)
    }

    @Test
    fun quickSetupWritesThroughThePagesOwnSettersAndRecolorsLive() {
        val guide = UiSourceFiles.file("SettingsQuickSetup.kt")
        val content = functionBody(guide, "QuickSetupStepContent")
        assertTrue("step 1 is the 外观 theme picker", content.contains("ThemeModePicker(settings?.themeMode ?: ThemeMode.SYSTEM, accent, colors, viewModel::setThemeMode)"))
        assertTrue("step 2 is the 外观 accent picker", content.contains("AccentSwatchPicker(accent, colors, viewModel::setAccentChoice)"))
        assertTrue("step 3 is the home style + presets", content.contains("QuickSetupHomeStep(context)"))
        val home = functionBody(guide, "QuickSetupHomeStep")
        assertTrue(home.contains("context.viewModel::setHomePresentationMode"))
        // A named preset still writes its canonical look (immersivePresetSelection); the 自定义 card, shown only
        // while the mix is custom, keeps it — the guide never silently overwrites a custom mix (r4 review).
        assertTrue(home.contains("context.viewModel.setImmersiveVisual(immersivePresetSelection(preset, visual))"))
        assertTrue(home.contains("if (visual.preset == ImmersiveFxPreset.CUSTOM) ImmersivePresetSegments + ImmersiveFxPreset.CUSTOM else ImmersivePresetSegments"))
        assertTrue(home.contains("customDescription = QUICK_SETUP_KEEP_CUSTOM"))
        assertTrue("only immersive shows the presets", home.contains("if (mode == HomePresentationMode.IMMERSIVE) {"))
        val sheet = functionBody(guide, "QuickSetupSheet")
        assertTrue("one sheet, opened at full height", sheet.contains("KireiSheet(colors, onDismiss = onClose, spec = KireiSheetSpec(scrollable = false, skipPartiallyExpanded = true))"))
        assertTrue("跳过 closes the guide", sheet.contains("QuickSetupHeader(step, colors, onSkip = onClose)"))
        assertTrue("完成 closes it on the last step", sheet.contains("if (QuickSetupFlow.next(step) == null) onClose() else stepIndex += 1"))
        assertTrue("上一步 goes back", sheet.contains("onBack = { stepIndex -= 1 }"))
        assertTrue("system back steps back too; only the first step closes the sheet", sheet.contains("BackHandler(enabled = stepIndex > 0) { stepIndex -= 1 }"))
        assertTrue("the step survives rotation", sheet.contains("var stepIndex by rememberSaveable { mutableIntStateOf(0) }"))
        // The guide takes the live page context, so a theme or accent choice recolors it immediately.
        assertTrue(sheet.contains("val colors = context.colors"))
    }

    @Test
    fun theRootSuggestsQuickSetupOnceAndTheOverviewButtonSyncs() {
        val root = functionBody(UiSourceFiles.file("SettingsRootPage.kt"), "SettingsRootPage")
        assertTrue(root.contains("val quickSetupSuggested = state.settings?.quickSetupDone == false"))
        assertTrue("the banner shows only while suggested", root.contains("if (quickSetupSuggested) {"))
        assertTrue("closing the banner marks it done", root.contains("onDismiss = { viewModel.setQuickSetupDone(true) }"))
        // Quick setup is needed once: afterwards it is a search result, and the overview button syncs data.
        assertTrue(root.contains("onSync = viewModel::syncPlaytimeNow"))
        assertTrue("游戏时长 opens the play-time ranking", root.contains("onOpenPlayTime = context.openPlayTimeOverview"))
        assertTrue(root.contains("onOpenSync = { viewModel.openSettingsShortcut(KireiSettingsPage.PLAYTIME_SYNC) }"))
        val close = root.substring(root.indexOf("QuickSetupSheet(context) {"))
        assertTrue("finishing, skipping or dismissing the guide marks it done", close.contains("viewModel.setQuickSetupDone(true)"))
        assertTrue(root.indexOf("SettingsOverviewCard(") < root.indexOf("QuickSetupSuggestion("))
        assertTrue(root.indexOf("QuickSetupSuggestion(") < root.indexOf("SettingsPersonalGroup("))
        val card = UiSourceFiles.file("SettingsOverviewCard.kt")
        assertFalse("no permanent quick-setup entry on the card", card.contains("QUICK_SETUP_TITLE"))
        assertTrue("the sync button shows its state in color", card.contains("PlaytimeSyncButton(sync, state.nextmoeConnected(), colors"))
        assertTrue(card.contains("OverviewStat(\"游戏时长\", LibraryOverviewPolicy.summaryPlayTime(totalPlayTime), colors, Modifier.weight(1f).fillMaxHeight(), actions.onOpenPlayTime)"))
        assertTrue(card.contains("OverviewStat(\"游戏数\", visibleGames.toString(), colors, Modifier.weight(1f).fillMaxHeight())"))
        assertTrue(card.contains("OverviewStat(\"上次同步\", lastSyncLabel(sync.lastSyncAt, System.currentTimeMillis()), colors, Modifier.weight(1f).fillMaxHeight(), actions.onOpenSync)"))
        assertTrue("快速设置 stays reachable from search", UiSourceFiles.file("SettingsSearchIndex.kt").contains("快速设置"))
        assertEquals("快速设置", QUICK_SETUP_TITLE)
        val banner = functionBody(UiSourceFiles.file("SettingsQuickSetup.kt"), "QuickSetupSuggestion")
        assertTrue("the close button says what it does", banner.contains("contentDescription = \"不再提示快速设置\""))
        val controller = UiSourceFiles.file("KireiSettingsController.kt")
        val setter = controller.substring(controller.indexOf("fun setQuickSetupDone("))
        assertTrue(setter.contains("core.settings.setQuickSetupDone(done)"))
        assertTrue("the root updates at once", setter.contains("updateSettingsSnapshot { it.copy(quickSetupDone = done) }"))
        assertTrue(
            "an older in-flight reload cannot publish quickSetupDone = false over the optimistic update",
            setter.substringBefore("\n    }").contains("reloadImmersiveSettingsLatest()"),
        )
        assertTrue(UiSourceFiles.all().contains("fun setQuickSetupDone(done: Boolean) = settingsController.setQuickSetupDone(done)"))
    }

    private fun occurrences(
        text: String,
        needle: String,
    ): Int = Regex(Regex.escape(needle) + """\b""").findAll(text).count()

    /** Source of the top-level function (or extension) [name]: from its `fun` keyword to the next top-level declaration. */
    private fun functionBody(
        source: String,
        name: String,
    ): String {
        val start = Regex("""fun (?:<[^>]+> )?(?:\w+\.)?$name\(""").find(source)?.range?.first ?: error("fun $name( not found")
        val end = Regex("""\n(?=[@A-Za-z/])""").find(source, start)?.range?.first ?: source.length
        return source.substring(start, end)
    }
}
