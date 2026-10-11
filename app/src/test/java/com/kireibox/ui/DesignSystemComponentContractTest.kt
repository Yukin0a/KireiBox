package com.kireibox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.components.KireiBadgeDefaults
import com.kireibox.designsystem.components.KireiDialogDefaults
import com.kireibox.designsystem.components.KireiListRowDefaults
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiSegmentedDefaults
import com.kireibox.designsystem.kireiColorsFromTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P2 component library + shell adoption (doc/2026-10-02-ui-alpha17-design-spec.md §3–§8): the
 * designsystem/components package exists and follows the token rules, every sheet/dialog/menu in
 * ui/ goes through it, and the dock gained its sliding capsule without touching its geometry.
 */
class DesignSystemComponentContractTest {
    private val components: String by lazy { UiSourceFiles.components() }
    private val all: String by lazy { UiSourceFiles.all() }

    @Test
    fun componentLibraryDeclaresEverySpecComponent() {
        listOf(
            "KireiListGroup",
            "KireiListRow",
            "KireiSwitch",
            "KireiSegmented",
            "KireiButton",
            "KireiChip",
            "KireiBadge",
            "KireiCountBadge",
            "KireiDotBadge",
            "KireiTextField",
            "KireiEmptyState",
            "KireiSkeleton",
            "KireiProgressBar",
            "KireiLargeTitle",
            "KireiLargeTopBar",
            "KireiSheet",
            "KireiDialog",
            "KireiMenu",
            "KireiMenuItem",
            // P3: the KRKR per-game page's slider and the account / permission status chips.
            "KireiSlider",
            "KireiStatusChip",
            // r4 · S2: the settings search capsule.
            "KireiSearchField",
        ).forEach { name ->
            assertTrue("components must declare fun $name(", Regex("""fun (?:<[^>]+> )?$name\(""").containsMatchIn(components))
        }
    }

    @Test
    fun componentsOnlyUseTokensAndExplicitTextStyles() {
        assertFalse("no raw color literals", components.contains("Color(0x"))
        assertFalse("radii come from KireiShapes", Regex("""RoundedCornerShape\(\s*\d""").containsMatchIn(components))
        assertFalse("tints use accentContainer", Regex("""accent\.copy\(\s*alpha""").containsMatchIn(components))
        val texts = callArguments(components, "Text")
        assertTrue(texts.isNotEmpty())
        texts.forEach { call -> assertTrue("Text( without explicit style: $call", call.contains("style =")) }
        assertTrue("reduced motion is read from the resolved motion mode", components.contains("LocalResolvedMotionMode.current == MotionMode.REDUCED"))
        assertTrue("skeleton is static under reduced motion", functionBody(components, "KireiSkeleton").contains("if (kireiReducedMotion())"))
    }

    @Test
    fun everySheetRendersThroughTheSharedKireiSheet() {
        val actionSheet = functionBody(all, "KireiActionSheet")
        assertTrue(actionSheet.contains("KireiSheet("))
        assertTrue("scrollableContent keeps its meaning", actionSheet.contains("scrollable = scrollableContent"))
        assertTrue(actionSheet.contains("ModalSystemBarVisibilityEffect(isDark = colors.isDark)"))
        val taskSheet = functionBody(all, "CoverTaskBottomSheet")
        assertTrue("the cover task sheet no longer uses the stock M3 handle/shape", taskSheet.contains("KireiSheet("))
        assertEquals("only KireiSheet builds a ModalBottomSheet", 1, Regex("""\bModalBottomSheet\(""").findAll(all).count())

        val sheet = UiSourceFiles.designSystemFile("components/KireiSheet.kt")
        listOf("KireiShapes.sheet", "colors.tertiary", "AppleTypography.headlineMedium", "scrimColor = colors.scrim", "colors.surfaceOverlay")
            .forEach { assertTrue("KireiSheet uses $it", sheet.contains(it)) }
    }

    @Test
    fun dialogsAndMenusUseTheKireiComponents() {
        assertFalse("stock AlertDialog left in ui/", all.contains("AlertDialog("))
        assertFalse("menus mark the selection with an icon, not a text prefix", all.contains("✓"))
        assertEquals("only KireiMenu builds a DropdownMenu", 1, Regex("""\bDropdownMenu\(""").findAll(all).count())
        assertTrue(UiSourceFiles.designSystemFile("components/KireiMenu.kt").contains("DropdownMenu("))

        val rename = functionBody(all, "RenameGameDialog")
        assertTrue(rename.contains("KireiDialog(") && rename.contains("KireiTextField("))
        listOf("LibraryPages.kt", "SettingsLibraryPages.kt", "SettingsCoverPages.kt").forEach { name ->
            assertTrue("$name uses KireiDialog", UiSourceFiles.file(name).contains("KireiDialog("))
        }
        assertTrue("the Bangumi token stays masked", UiSourceFiles.file("SettingsCoverPages.kt").contains("secret = true"))
        assertTrue("scan-root removal still requires an explicit choice", UiSourceFiles.file("SettingsLibraryPages.kt").contains("dismissible = false"))
        listOf("LibraryCard.kt", "LibraryHeaders.kt").forEach { name ->
            assertTrue("$name uses KireiMenu", UiSourceFiles.file(name).contains("KireiMenu("))
        }
        // Prompt hosts adopt the KireiDialog layout and buttons.
        val shell = UiSourceFiles.file("KireiShell.kt")
        assertTrue(shell.contains("KireiDialogBody("))
        assertTrue(shell.contains("KireiProgressBar("))
        assertFalse(shell.contains("LinearProgressIndicator("))
        assertFalse("prompt buttons are KireiButtons, not raw Surfaces", Regex("""\bSurface\(""").containsMatchIn(shell))
    }

    @Test
    fun sheetActionsShareTheListRowIconLanguage() {
        val sheetRow = functionBody(all, "SheetActionRow")
        assertTrue(sheetRow.contains("KireiListRow("))
        assertTrue(sheetRow.contains("KireiRowTone.DESTRUCTIVE"))
        assertFalse("no circular icon wells", sheetRow.contains("CircleShape"))
        val tile = functionBody(components, "KireiIconTile")
        assertTrue("icon tiles are sm-rounded", tile.contains("KireiShapes.sm"))
    }

    @Test
    fun listRowsAlignTextAndDividersToTheSpecMetrics() {
        val iconRow = KireiListRowSpec(title = "外观", icon = Icons.Rounded.Palette)
        val plainRow = KireiListRowSpec(title = "减少透明度")
        assertEquals("divider starts at the text, after the 30dp tile + 14dp gap", 60.dp, KireiListRowDefaults.textStart(iconRow))
        assertEquals(16.dp, KireiListRowDefaults.textStart(plainRow))
        assertEquals("reserved icon space keeps icon-less rows aligned", 60.dp, KireiListRowDefaults.textStart(plainRow.copy(reserveIconSpace = true)))
        assertEquals(52.dp, KireiListRowDefaults.minHeight(plainRow))
        assertEquals(64.dp, KireiListRowDefaults.minHeight(plainRow.copy(subtitle = "副标题")))
        val group = functionBody(components, "KireiListGroup")
        assertTrue("no divider after the last row", group.contains("LocalKireiListDivider provides (index < rows.lastIndex)"))
    }

    @Test
    fun badgesDialogActionsAndSegmentsFollowTheirPolicies() {
        assertEquals("", KireiBadgeDefaults.label(0))
        assertEquals("3", KireiBadgeDefaults.label(3))
        assertEquals("99+", KireiBadgeDefaults.label(128))
        assertFalse(KireiDialogDefaults.stackActions(listOf("取消", "保存")))
        assertFalse(KireiDialogDefaults.stackActions(listOf("继续编辑", "放弃更改")))
        assertTrue(KireiDialogDefaults.stackActions(listOf("取消", "移除目录与游戏")))
        assertTrue(KireiDialogDefaults.stackActions(listOf("使用系统 SAF", "前往授权")))
        val light = kireiColorsFromTokens(isDark = false)
        val dark = kireiColorsFromTokens(isDark = true)
        assertEquals("light selection block is the surface", light.surface, KireiSegmentedDefaults.indicatorColor(light))
        assertNotEquals("dark selection block lifts surface3", dark.surface3, KireiSegmentedDefaults.indicatorColor(dark))
        // r3 review: the spec's 8% lift was ~1.3:1 against the track; the block must read on its own.
        val lifted = KireiSegmentedDefaults.indicatorColor(dark).luminance()
        val track = dark.surface3.luminance()
        assertTrue("dark selection block ≥ 1.5:1 against the track", (lifted + .05f) / (track + .05f) >= 1.5f)
    }

    @Test
    fun dockSlidesASelectionCapsuleWithoutChangingItsGeometry() {
        val dock = functionBody(all, "KireiMainDock")
        assertTrue(dock.contains("DockSelectionCapsule("))
        assertTrue(dock.contains("resolveDockRenderRect(metrics.dockRect)"))
        assertTrue(dock.contains("forceVertical = metrics.orientation == HomeOrientation.PORTRAIT"))
        assertTrue(dock.contains("Arrangement.spacedBy(presentation.slotGapDp.dp)"))
        val capsule = functionBody(all, "DockSelectionCapsule")
        assertTrue(capsule.contains("colors.accentContainer"))
        assertTrue(capsule.contains("AppleMotion.Spring.Emphasized"))
        assertTrue("instant under reduced motion", capsule.contains("if (reducedMotion) snap()"))
        val button = functionBody(all, "DockButton")
        assertTrue("selected tab uses the filled icon", button.contains("if (selected) tabSpec.selectedIcon else tabSpec.icon"))
        assertTrue(button.contains("FontWeight.SemiBold"))
        listOf("Icons.Outlined.Home", "Icons.Outlined.Gamepad", "Icons.Outlined.Settings").forEach { assertTrue(all.contains(it)) }
        assertTrue("review badge renders through KireiBadge", functionBody(all, "CountBadge").contains("KireiBadge("))

        // Pure geometry on the 292.34 x 64 portrait dock (AdaptiveUiMetricsTest): one capsule per
        // button slot, centered on it, inset 6dp on every outer edge and never outside the dock.
        val width = 292.3443f
        val presentation = resolveDockPresentation(width, 64f, forceVertical = true)
        (0..2).forEach { slot ->
            val frame = DockSelectionCapsulePolicy.geometry(presentation, 64f, slot.toFloat())
            val buttonCenter = presentation.horizontalPaddingDp + slot * (presentation.buttonWidthDp + presentation.slotGapDp) + presentation.buttonWidthDp / 2f
            assertEquals(buttonCenter, frame.xDp + frame.widthDp / 2f, .001f)
            assertEquals(52f, frame.heightDp, .001f)
            assertTrue(frame.xDp >= 0f && frame.xDp + frame.widthDp <= width + .001f)
            assertTrue("capsule covers its button", frame.widthDp >= presentation.buttonWidthDp)
        }
        assertEquals(6f, DockSelectionCapsulePolicy.geometry(presentation, 64f, 0f).xDp, .001f)
        val landscape = resolveDockPresentation(320f, 48f)
        assertEquals(38f, DockSelectionCapsulePolicy.geometry(landscape, 48f, 1f).heightDp, .001f)
    }

    @Test
    fun overlaysResetTheListDividerFlagTheyWouldInheritFromAGroupedRow() {
        // Sheets, dialogs and menus inherit CompositionLocals from the row that opened them; a row
        // inside a KireiListGroup must not make every option in its sheet draw a divider.
        listOf("components/KireiSheet.kt", "components/KireiDialog.kt", "components/KireiMenu.kt").forEach { file ->
            assertTrue("$file resets the divider flag", UiSourceFiles.designSystemFile(file).contains("LocalKireiListDivider provides false"))
        }
        val segmentedRow = functionBody(all, "SettingsSegmentedRow")
        assertTrue("the divider sits under the control, not between title and control", segmentedRow.contains(".kireiListDivider(divider, colors"))
        assertTrue(segmentedRow.contains("LocalKireiListDivider provides false"))
    }

    @Test
    fun designSystemNeverDependsOnTheUiPackage() {
        UiSourceFiles.designSystemSources().forEach { (name, text) ->
            assertFalse("$name imports from com.kireibox.ui", text.contains("import com.kireibox.ui."))
        }
        assertTrue(UiSourceFiles.designSystemFile("KireiMotionLocal.kt").contains("val LocalResolvedMotionMode"))
    }

    @Test
    fun selectionStateIsExposedOnTheFocusedNode() {
        val dockButton = functionBody(all, "DockButton")
        val pressBox = dockButton.indexOf("PressScaleBox(")
        assertTrue(pressBox >= 0)
        val pressModifier = dockButton.substring(pressBox, dockButton.indexOf("KireiShapes.full", pressBox))
        assertTrue("selected lives on the clickable PressScaleBox node", pressModifier.contains("this.selected = selected"))
        assertTrue(pressModifier.contains("role = Role.Tab"))
        assertTrue("chips announce their selected state", functionBody(components, "KireiChip").contains(".selectable(selected = selected"))
        val segmented = functionBody(components, "KireiSegmented")
        assertTrue("an unmatched value (a custom mix) shows no indicator", segmented.contains("if (selectedIndex >= 0)"))
    }

    @Test
    fun noticeToneIsInferredConservatively() {
        assertEquals(NoticeTone.SUCCESS, NoticeTonePolicy.infer("封面已保存"))
        assertEquals(NoticeTone.SUCCESS, NoticeTonePolicy.infer("已添加 千恋＊万花"))
        assertEquals(NoticeTone.SUCCESS, NoticeTonePolicy.infer("Bangumi 账号已连接"))
        assertEquals(NoticeTone.ERROR, NoticeTonePolicy.infer("清除账号失败，请重试"))
        assertEquals(NoticeTone.ERROR, NoticeTonePolicy.infer("无法打开浏览器"))
        assertEquals(NoticeTone.WARNING, NoticeTonePolicy.infer("请先连接自己的 Bangumi 账号"))
        assertEquals(NoticeTone.INFO, NoticeTonePolicy.infer("所选快捷方式已在游戏库中"))
        assertEquals(NoticeTone.INFO, NoticeTonePolicy.infer("GitHub 暂无公开版本"))
    }

    @Test
    fun appearancePageUsesTheSharedListComponents() {
        val page = UiSourceFiles.file("SettingsAppearancePage.kt")
        assertTrue(page.contains("KireiListGroup("))
        assertTrue(page.contains("KireiRowTrailing.Toggle("))
        assertFalse("the private group style is gone", page.contains("AppearanceGroup("))
        assertFalse(page.contains("HorizontalDivider("))
        assertTrue("switch rows use the Kirei switch", functionBody(all, "SwitchRow").contains("KireiRowTrailing.Toggle("))
    }

    @Test
    fun showcaseIsDebugOnlyAndTakesThemeExtras() {
        val root = listOf(File("."), File("..")).first { File(it, "app/src/main").isDirectory }
        val showcase = File(root, "app/src/debug/java/com/kireibox/ui/UiShowcaseActivity.kt")
        assertTrue("debug showcase missing", showcase.isFile)
        val text = showcase.readText()
        listOf("\"dark\"", "\"accent\"", "\"contrast\"", "AccentChoice.fromStored(", "AppleDesignTheme(darkTheme = dark, accent = accent, highContrast = contrast)")
            .forEach { assertTrue("showcase reads $it", text.contains(it)) }
        listOf("main", "release").forEach { set ->
            assertFalse("showcase must not exist in $set", File(root, "app/src/$set/java/com/kireibox/ui/UiShowcaseActivity.kt").exists())
        }
        assertTrue(File(root, "app/src/debug/AndroidManifest.xml").readText().contains("com.kireibox.ui.UiShowcaseActivity"))
        assertFalse(File(root, "app/src/main/AndroidManifest.xml").readText().contains("UiShowcaseActivity"))
        val fixture = File(root, "app/src/debug/java/com/kireibox/ui/ImmersiveFixtureActivity.kt").readText()
        assertTrue("immersive fixture can render the light theme", fixture.contains("AppleDesignTheme(darkTheme = dark)"))
        assertTrue(fixture.contains("getBooleanExtra(EXTRA_DARK, true)"))
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
