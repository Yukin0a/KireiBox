package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the P3 settings behaviours that had no guard (takeover review): the overview card leads the
 * root page above the four groups, the immersive effects panel only shows in immersive mode and
 * writes one normalized snapshot, the cover lists are capped at five with review-first order, and
 * the effects controller cannot race. Markers are implementation-neutral (SettingsSection or
 * KireiListGroup), so the planned list-group migration keeps them meaningful.
 */
class SettingsP3WiringTest {
    private val all: String by lazy { UiSourceFiles.all() }

    @Test
    fun rootPageLeadsWithTheOverviewCardThenFourGroupsInOrder() {
        // P3: each root group is its own composable, called in order after the overview card and
        // declared in the same order (their titles follow each other through the file).
        val file = UiSourceFiles.file("SettingsRootPage.kt")
        val root = slice(file, "internal fun SettingsRootPage(", "\n@Composable")
        val overview = root.indexOf("SettingsOverviewCard(")
        assertTrue("overview card missing", overview >= 0)
        var previous = overview
        listOf("SettingsPersonalGroup(", "SettingsGamesGroup(", "SettingsSystemGroup(", "SettingsAboutGroup(").forEach { call ->
            val at = root.indexOf(call, previous)
            assertTrue("$call must follow the previous group", at > previous)
            previous = at
        }
        previous = file.indexOf("internal fun SettingsRootPage(")
        listOf("title = \"个性化\"", "title = \"游戏\"", "title = \"系统\"", "title = \"关于\"").forEach { title ->
            val at = file.indexOf(title, previous)
            assertTrue("group $title must follow the previous group", at > previous)
            previous = at
        }
    }

    @Test
    fun immersiveEffectsShowOnlyInImmersiveModeAndWriteOneNormalizedSnapshot() {
        val interfaceSettings = slice(all, "internal fun InterfaceSettings(", "\n@Composable")
        // r3: the AnimatedVisibility also takes reduced-motion transitions, so its arguments wrap.
        val gate = interfaceSettings.indexOf("visible = homeMode == HomePresentationMode.IMMERSIVE,")
        assertTrue("effects panel gated on immersive mode", gate >= 0)
        assertTrue(interfaceSettings.indexOf("ImmersiveVisualSettingsPanel(state, colors, viewModel)") > gate)
        assertTrue(UiSourceFiles.file("SettingsImmersiveVisual.kt").contains("viewModel.setImmersiveVisual(it.normalized())"))
    }

    @Test
    fun coverListsAreCappedAtFiveWithReviewFirst() {
        val covers = UiSourceFiles.file("SettingsCoverPages.kt")
        assertTrue(covers.contains("private const val COVER_PREVIEW_LIMIT = 5"))
        // r3: lists start at the five-row preview and grow in pages (coverRowsShown caps them);
        // the review-first order is what gets shown.
        assertTrue(covers.contains("var pendingRequested by rememberSaveable { mutableIntStateOf(COVER_PREVIEW_LIMIT) }"))
        assertTrue(covers.contains("shown = coverRowsShown(pendingRequested, pendingFirst.size)"))
        assertTrue(covers.contains("games = pendingFirst,"))
        assertEquals(5, coverRowsShown(5, 12))
        assertTrue(covers.contains("CoverSettingsPolicy.reviewFirst("))
    }

    @Test
    fun immersiveEffectsPersistWithALatestWinsReload() {
        val controller = UiSourceFiles.file("KireiSettingsController.kt")
        val setter = slice(controller, "fun setImmersiveVisual(", "\n    fun ")
        assertTrue(setter.contains("updateSettingsSnapshot { it.copy(immersiveVisual = normalized) }"))
        assertTrue("an older reload must not publish over the optimistic snapshot", setter.contains("reloadImmersiveSettingsLatest()"))
        assertTrue("no ungated full reload", !Regex("""\breload\(\)""").containsMatchIn(setter))
        listOf("fun setThemeMode(", "fun setAccentChoice(").forEach { marker ->
            assertTrue("$marker reloads latest-wins", slice(controller, marker, "\n    fun ").contains("reloadImmersiveSettingsLatest()"))
        }
    }

    @Test
    fun onlyALowRamDeviceGetsAPresetFooterAndPausedMotionIsNamedByItsRealSetting() {
        assertNull(immersivePresetFooter(lowRamDevice = false))
        assertEquals("设备内存较小，效果上限为「标准」。", immersivePresetFooter(lowRamDevice = true))
        // r6: 精简 / 完整 are real choices — the "动态效果已暂停" hint is gone, and the low-RAM cap
        // only caps the preset instead of silently pausing an explicit 完整.
        val visual = UiSourceFiles.file("SettingsImmersiveVisual.kt")
        assertFalse("the paused-motion hint was removed", visual.contains("动态效果已暂停"))
        assertTrue(visual.contains("LocalImmersiveFx.current.environment.lowRamDevice"))
    }

    private fun slice(
        source: String,
        start: String,
        end: String,
    ): String {
        val from = source.indexOf(start)
        assertTrue("$start missing", from >= 0)
        val to = source.indexOf(end, from + start.length)
        return if (to < 0) source.substring(from) else source.substring(from, to)
    }
}
