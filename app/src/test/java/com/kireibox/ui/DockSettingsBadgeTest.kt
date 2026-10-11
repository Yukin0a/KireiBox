package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * r5 · B (user decision 「可以加小红点提示」): a one-time accent dot on the Dock's 设置 tab until 快速设置
 * is finished or dismissed. The rule is pure ([DockSettingsBadgePolicy]); the wiring pins that the
 * shell feeds it the same flag as the settings root suggestion card and that the Dock draws and
 * announces it.
 */
class DockSettingsBadgeTest {
    @Test
    fun theDotShowsOnlyWhileQuickSetupIsPending() {
        assertEquals(DockTabBadge.Suggestion, DockSettingsBadgePolicy.resolve(attentionCount = 0, quickSetupDone = false))
        assertEquals("finished, skipped, swiped away or dismissed", DockTabBadge.None, DockSettingsBadgePolicy.resolve(0, quickSetupDone = true))
        assertEquals("settings not loaded yet: no flash for users who already finished", DockTabBadge.None, DockSettingsBadgePolicy.resolve(0, quickSetupDone = null))
    }

    @Test
    fun attentionWinsOverTheDot() {
        assertEquals(DockTabBadge.Count(3), DockSettingsBadgePolicy.resolve(attentionCount = 3, quickSetupDone = false))
        assertEquals(DockTabBadge.Count(3), DockSettingsBadgePolicy.resolve(3, quickSetupDone = true))
        assertEquals(DockTabBadge.Count(128), DockSettingsBadgePolicy.resolve(128, quickSetupDone = null))
        assertEquals("a bogus negative count is no count", DockTabBadge.Suggestion, DockSettingsBadgePolicy.resolve(-1, quickSetupDone = false))
        // Once the covers are handled the dot comes back if 快速设置 is still pending.
        assertEquals(DockTabBadge.Suggestion, DockSettingsBadgePolicy.resolve(0, quickSetupDone = false))
    }

    @Test
    fun theDotFollowsTheSameFlagAsTheRootSuggestionCard() {
        // SettingsRootPage: `val quickSetupSuggested = state.settings?.quickSetupDone == false`.
        listOf(null, false, true).forEach { done ->
            val cardShown = done == false
            assertEquals("quickSetupDone = $done", cardShown, DockSettingsBadgePolicy.resolve(0, done) == DockTabBadge.Suggestion)
        }
        assertTrue(UiSourceFiles.file("SettingsRootPage.kt").contains("val quickSetupSuggested = state.settings?.quickSetupDone == false"))
    }

    @Test
    fun theTabNameMentionsTheSuggestionOnlyWhileTheDotShows() {
        assertEquals("设置，有新的设置建议", DockSettingsBadgePolicy.description("设置", DockTabBadge.Suggestion))
        assertNull("the count badge announces itself (「n 个封面待处理」)", DockSettingsBadgePolicy.description("设置", DockTabBadge.Count(2)))
        assertNull(DockSettingsBadgePolicy.description("首页", DockTabBadge.None))
    }

    @Test
    fun theShellFeedsTheDockAndTheDockDrawsAndAnnouncesTheDot() {
        val app = UiSourceFiles.file("KireiBoxApp.kt")
        assertTrue(app.contains("settingsBadge = DockSettingsBadgePolicy.resolve(state.coverAttentionCount, state.settings?.quickSetupDone)"))

        val common = UiSourceFiles.file("KireiCommonUi.kt")
        val dock = common.substring(common.indexOf("internal fun KireiMainDock("), common.indexOf("/** Dock tab order"))
        assertTrue("only the 设置 tab carries a badge", dock.contains("DockTabSpec.SETTINGS.copy(badge = spec.settingsBadge)"))
        assertTrue(dock.contains("DockButton(DockTabSpec.HOME, tab == KireiTab.HOME"))

        val button = common.substring(common.indexOf("internal fun DockButton("), common.indexOf("internal fun CountBadge("))
        assertTrue(button.contains("val description = DockSettingsBadgePolicy.description(tabSpec.label, tabSpec.badge)"))
        // The name lives on the focused, selectable tab node next to its selected state and role.
        val semantics = button.substring(button.indexOf("PressScaleBox("), button.indexOf("KireiShapes.full", button.indexOf("PressScaleBox(")))
        assertTrue(semantics.contains("this.selected = selected"))
        assertTrue(semantics.contains("description?.let { contentDescription = it }"))
        assertTrue("the dot rides on the icon", button.contains("if (tabSpec.badge == DockTabBadge.Suggestion) DockSuggestionDot(colors, Modifier.align(Alignment.TopEnd))"))
        assertTrue("both dock layouts draw the same icon", Regex("""\bglyph\(\)""").findAll(button).count() == 2)
        assertTrue("the count badge is unchanged", button.contains("count = (tabSpec.badge as? DockTabBadge.Count)?.count ?: 0"))

        val dot = UiSourceFiles.file("DockSettingsBadge.kt")
        assertTrue("component library dot, spec badge tokens", dot.contains("KireiDotBadge(colors, modifier.offset(x = half, y = -half), tone = KireiBadgeTone.ACCENT)"))
        assertTrue(dot.contains("val half = KireiBadgeDefaults.DotSize / 2"))
    }

    @Test
    fun everyWayOutOfQuickSetupClearsTheDotAtOnce() {
        val root = UiSourceFiles.file("SettingsRootPage.kt")
        assertTrue("closing the suggestion card", root.contains("onDismiss = { viewModel.setQuickSetupDone(true) }"))
        assertTrue(
            "完成 / 跳过 / swipe-away of the sheet (QuickSetupSheet's onClose)",
            Regex("""QuickSetupSheet\(context\) \{\s*quickSetupOpen = false\s*viewModel\.setQuickSetupDone\(true\)""").containsMatchIn(root),
        )
        assertTrue(UiSourceFiles.file("SettingsQuickSetup.kt").contains("KireiSheet(colors, onDismiss = onClose"))
        // The optimistic snapshot update recomposes the shell (and so the Dock) before the write lands.
        assertTrue(UiSourceFiles.file("KireiSettingsController.kt").contains("updateSettingsSnapshot { it.copy(quickSetupDone = done) }"))
    }
}
