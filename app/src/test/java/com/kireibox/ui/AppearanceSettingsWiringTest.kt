package com.kireibox.ui

import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings → 外观 (P1b): routing, root entry, theme/accent writes and the moved display options. */
class AppearanceSettingsWiringTest {
    private val all: String by lazy { UiSourceFiles.all() }
    private val page: String by lazy { UiSourceFiles.file("SettingsAppearancePage.kt") }

    @Test
    fun appearancePageIsRoutedToAppearanceSettingsAndTitled() {
        assertTrue(all.contains("KireiSettingsPage.APPEARANCE -> AppearanceSettings(state, colors, viewModel)"))
        assertEquals("外观", settingsPageTitle(KireiSettingsPage.APPEARANCE))
        // r4 S1: the INTERFACE page id is kept (back stack, saved state) but the page is now 「首页」.
        assertEquals("首页", settingsPageTitle(KireiSettingsPage.INTERFACE))
    }

    @Test
    fun settingsRootLeadsWithTheAppearanceRowShowingTheCurrentThemeMode() {
        // P3 split the root into SettingsRootPage + one composable per group in SettingsRootPage.kt,
        // so the whole file is the root page; rows navigate through SettingsCategoryRow(page, …).
        val root = UiSourceFiles.file("SettingsRootPage.kt")
        val appearanceRow = root.indexOf("\"外观\"")
        assertTrue("root has the 外观 row", appearanceRow >= 0)
        // r4 S1: 屏幕方向 left the root for 外观, so 外观 now leads the 首页 row (and everything else).
        assertTrue("外观 is the first row of the root list", appearanceRow < root.indexOf("\"首页\""))
        assertTrue(appearanceRow < root.indexOf("\"游戏库\""))
        assertTrue("the 外观 row opens the APPEARANCE page", Regex("""SettingsCategoryRow\(\s*KireiSettingsPage\.APPEARANCE,""").containsMatchIn(root))
        assertTrue("category rows open their page", functionBody(root, "SettingsCategoryRow").contains("onClick = { context.open(page) }"))
        // context.open must be openCategory (not viewModel::openSettingsPage): openCategory records
        // the row for focus restore before navigating, which the old openCategory(APPEARANCE) pin covered.
        assertTrue("rows navigate through openCategory", root.contains("SettingsCategoryRowContext(colors, categoryFocusRequesters, adaptiveMetrics.orientation, openCategory)"))
        val openCategory = root.substring(root.indexOf("val openCategory: (KireiSettingsPage) -> Unit = { page ->"), root.indexOf("val rows = SettingsCategoryRowContext("))
        assertTrue("openCategory remembers the row for focus restore", openCategory.contains("lastFocusedCategory = page.name"))
        assertTrue("…and then opens the page", openCategory.contains("viewModel.openSettingsPage(page)"))
        assertTrue(root.contains("openCategory(KireiSettingsPage.COVERS)"))
        assertTrue("pink category tile from the tokens", Regex("""Icons\.Rounded\.Palette, "外观", null, KireiTileColor\.PINK""").containsMatchIn(root))
        assertTrue("trailing value is the current theme mode", root.contains("value = state.settings?.themeMode?.displayName"))
        assertTrue("focus restore covers the new row", Regex("""listOf\(\s*KireiSettingsPage\.APPEARANCE,""").containsMatchIn(root))
        assertTrue(Regex("""SettingsCategoryRow\(\s*KireiSettingsPage\.INTERFACE,\s*SettingsCategoryRowSpec\(Icons\.Rounded\.Home, "首页",""").containsMatchIn(root))
        assertFalse(root.contains("\"界面与辅助\""))
        assertFalse(root.contains("\"首页与沉浸式\""))
        assertEquals("首页 does not reuse the 外观 palette icon", 1, Regex("""Icons\.Rounded\.Palette\b""").findAll(root).count())
    }

    @Test
    fun themeCardsAndAccentSwatchesWriteThroughTheViewModel() {
        val appearance = functionBody(page, "AppearanceSettings")
        assertTrue(appearance.contains("ThemeModePicker("))
        assertTrue(appearance.contains("onSelect = viewModel::setThemeMode"))
        assertTrue(appearance.contains("AccentSwatchPicker("))
        assertTrue(appearance.contains("onSelect = viewModel::setAccentChoice"))

        val themePicker = functionBody(page, "ThemeModePicker")
        assertTrue(themePicker.contains("AppearanceThemeModeOrder.forEach"))
        assertTrue(themePicker.contains("ThemeModeCard("))
        assertTrue(themePicker.contains("onSelect(mode)"))
        assertEquals(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM), AppearanceThemeModeOrder)

        val swatchPicker = functionBody(page, "AccentSwatchPicker")
        assertTrue(swatchPicker.contains("AccentChoice.entries.forEach"))
        assertTrue(swatchPicker.contains("AccentSwatch("))
        assertTrue(swatchPicker.contains("onSelect(choice)"))
        assertTrue("swatches show each accent for the current theme", functionBody(page, "AccentSwatch").contains("KireiTokens.accent(choice, colors.isDark)"))
        // r4: purple is the default, listed first, and its caption / spoken name say 默认.
        assertEquals(AccentChoice.DEFAULT, AccentChoice.entries.first())
        assertEquals("紫色（默认）", accentChoiceLabel(AccentChoice.CLASSIC))
        assertEquals("樱粉", accentChoiceLabel(AccentChoice.SAKURA))
        assertTrue(swatchPicker.contains("accentChoiceLabel(selected)"))
        assertTrue(functionBody(page, "AccentSwatch").contains("强调色：${'$'}{accentChoiceLabel(choice)}"))
    }

    @Test
    fun themeCardsAndSwatchesAreAccessibleRadioChoicesWithReducedMotionSupport() {
        listOf("ThemeModeCard", "AccentSwatch").forEach { name ->
            val body = functionBody(page, name)
            assertTrue("$name is selectable", body.contains(".selectable("))
            assertTrue("$name has radio semantics", body.contains("role = Role.RadioButton"))
            assertTrue("$name has a content description", body.contains("contentDescription = "))
            assertTrue("$name animates selection with the shared spec", body.contains("selectionSpec(reducedMotion)"))
        }
        assertTrue(page.contains("if (reducedMotion) snap() else AppleMotion.Spring.Emphasized"))
        assertTrue(functionBody(page, "ThemeModePicker").contains(".selectableGroup()"))
        assertTrue(functionBody(page, "AccentSwatchPicker").contains(".selectableGroup()"))
    }

    @Test
    fun displayOptionsMovedFromInterfaceSettingsToTheAppearancePage() {
        val appearance = functionBody(page, "AppearanceSettings")
        // r4 S1: 屏幕方向 joined the other display options on 外观 (it was a root row before).
        listOf(
            "MotionModeRow(",
            "viewModel::setMotionMode",
            "viewModel::setReducedTransparency",
            "viewModel::setContrast",
            "\"减少透明度\"",
            "\"增强对比度\"",
            "OrientationModeRow(",
            "viewModel::setOrientationMode",
        ).forEach { assertTrue("外观 contains $it", appearance.contains(it)) }

        val library = UiSourceFiles.file("SettingsLibraryPages.kt")
        // The 首页 page: InterfaceSettings plus its 展示内容 group, which lives in SettingsHomePage.kt.
        val interfacePage = library.substringAfter("fun InterfaceSettings(").substringBefore("fun LibrarySettings(") + UiSourceFiles.file("SettingsHomePage.kt")
        listOf("MotionModeRow(", "viewModel::setReducedTransparency", "viewModel::setContrast", "OrientationModeRow(", "viewModel::setOrientationMode")
            .forEach { assertFalse("首页 no longer contains $it", interfacePage.contains(it)) }
        assertTrue(interfacePage.contains("HomePresentationModeRow("))
        assertTrue(interfacePage.contains("viewModel::setImmersivePlayTimeVisible"))

        // Alpha15V23ContractTest slices between these functions; their order must stay put.
        val order = listOf("fun LibraryHubSettings(", "fun InterfaceSettings(", "fun LibrarySettings(", "fun PermissionSettings(").map(library::indexOf)
        assertTrue(order.all { it >= 0 })
        assertEquals(order.sorted(), order)
    }

    @Test
    fun homeMenuToggleFlipsTheResolvedThemeIntoAnExplicitMode() {
        assertTrue(UiSourceFiles.file("HomePages.kt").contains("actions.setDarkMode(!colors.isDark)"))
        assertTrue(UiSourceFiles.file("ImmersiveHome.kt").contains("actions.setDarkMode(!colors.isDark)"))
        assertTrue(all.contains("fun setDarkMode(value: Boolean) = setThemeMode(if (value) ThemeMode.DARK else ThemeMode.LIGHT)"))
    }

    @Test
    fun appearancePageUsesTokensAndExplicitTextStylesOnly() {
        assertFalse("no raw color literals", page.contains("Color(0x"))
        assertFalse("shapes come from KireiShapes", Regex("""RoundedCornerShape\(\s*\d""").containsMatchIn(page))
        assertFalse("selection tint uses accentContainer, not accent alpha", Regex("""accent\.copy\(\s*alpha""").containsMatchIn(page))
        val textCalls = callArguments(page, "Text")
        assertTrue(textCalls.isNotEmpty())
        textCalls.forEach { call -> assertTrue("Text( without explicit style: $call", call.contains("style =")) }
    }

    /** Source of the top-level function (or extension) [name]: from its `fun` keyword to the next top-level declaration. */
    private fun functionBody(
        source: String,
        name: String,
    ): String {
        val start = Regex("""fun (?:\w+\.)?$name\(""").find(source)?.range?.first ?: error("fun $name( not found")
        val end = Regex("""\n(?=[@A-Za-z/])""").find(source, start)?.range?.first ?: source.length
        return source.substring(start, end)
    }

    /** Argument lists of every `name(` call, paren-balanced and ignoring parens inside string literals. */
    private fun callArguments(
        source: String,
        name: String,
    ): List<String> =
        Regex("""\b$name\(""").findAll(source).map { match ->
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
