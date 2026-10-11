package com.kireibox.ui

import com.kireibox.external.shortcut.ExternalShortcutDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the r6 redesign of the four pickers and the cover task center: they are built from the
 * Kirei components (no Material default widgets, no literal shapes), every `Text` carries a style,
 * and the pickers share one frame with a width cap for landscape.
 */
class PickerPagesStructureTest {
    private val files = listOf("PickerPages.kt", "PickerScaffold.kt", "AndroidAppIcons.kt", "CoverTaskCenter.kt")

    @Test
    fun pickersAndTaskCenterUseNoMaterialDefaultWidgetsOrLiteralShapes() {
        val forbidden =
            listOf(
                "RoundedCornerShape(",
                "OutlinedTextField",
                "TextButton",
                "material3.Button",
                "ButtonDefaults",
                " IconButton(",
                "FilterChip",
                "CircularProgressIndicator",
                "tonalElevation",
                "material3.Surface",
                "accent.copy(alpha",
            )
        files.forEach { name ->
            val source = UiSourceFiles.file(name)
            forbidden.forEach { assertFalse("$name must not contain $it", source.contains(it)) }
        }
    }

    @Test
    fun everyTextInThePickerFilesHasAnExplicitStyle() {
        files.forEach { name ->
            val source = UiSourceFiles.file(name)
            Regex("""(?<![A-Za-z])Text\(""").findAll(source).forEach { match ->
                assertTrue("$name: Text( at ${match.range.first} has no style", callOf(source, match.range.last).contains("style ="))
            }
        }
    }

    @Test
    fun pickersShareOneFrameWithTheLargeTitleBarAndAPrimaryBottomAction() {
        val scaffold = UiSourceFiles.file("PickerScaffold.kt")
        listOf("KireiLargeTitle(", "KireiLargeTopBar(", "KireiButton(", "LocalKireiListDivider", "KireiTopBarDefaults.collapseFraction(listState").forEach {
            assertTrue("PickerScaffold.kt must use $it", scaffold.contains(it))
        }
        val pages = UiSourceFiles.file("PickerPages.kt")
        assertEquals("every picker page opens the shared frame", 4, Regex("""(?<![A-Za-z0-9])PickerPage\(""").findAll(pages).count())
        listOf("KireiListRow(", "KireiSearchField(", "KireiStatusChip(", "KireiChip(", "KireiTileColor.", "PickerAction(").forEach {
            assertTrue("PickerPages.kt must use $it", pages.contains(it))
        }
        assertTrue("landscape and tablets keep a centered column", PickerContentMaxWidth.value in 480f..680f)
    }

    @Test
    fun taskCenterIsBuiltFromTheSheetListRowAndProgressComponents() {
        val center = UiSourceFiles.file("CoverTaskCenter.kt")
        listOf("KireiSheet(", "KireiStatusChip(", "KireiProgressBar(", "KireiButton(", "kireiListDivider(", "downloadBytesLabel(", "chipLabel(").forEach {
            assertTrue("CoverTaskCenter.kt must use $it", center.contains(it))
        }
        // The toolbar ring must stay readable on dark glass: accent, not the danger red.
        assertTrue(center.contains("val progressColor = colors.accent"))
    }

    @Test
    fun shortcutRowsShowTheTitleAndNeverTheRawIdBesideIt() {
        fun shortcut(
            title: String,
            app: String = "GameHub",
            direct: String? = null,
        ) = ExternalShortcutDescriptor(0, "com.xiaoji.gamehub", app, "game--992212052", title, null, pinned = true, dynamic = false, directLaunchTarget = direct)

        val titled = shortcut("千恋＊万花")
        assertEquals("千恋＊万花", shortcutRowTitle(titled))
        assertEquals("GameHub · 桌面固定", shortcutRowDetail(titled))
        assertEquals("GameHub · 桌面固定 · 导入后免 Shizuku", shortcutRowDetail(shortcut("千恋＊万花", direct = "recipe")))
        assertFalse(shortcutRowDetail(titled).contains("game--"))
        // No title: the app name stands in, and only then is the id the title.
        assertEquals("GameHub", shortcutRowTitle(shortcut("")))
        assertEquals("game--992212052", shortcutRowTitle(shortcut("", app = "")))
        assertEquals("桌面固定", shortcutRowDetail(shortcut("GameHub")))
    }

    /** The text of the call whose opening parenthesis is at [open]. */
    private fun callOf(
        source: String,
        open: Int,
    ): String {
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return source.substring(open, i + 1)
            }
        }
        return source.substring(open)
    }
}
