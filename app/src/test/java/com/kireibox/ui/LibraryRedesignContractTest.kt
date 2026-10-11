package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the alpha.17 library redesign (plan P4): shared components, tokens, no stock Material widgets. */
class LibraryRedesignContractTest {
    private val files = listOf("LibraryPages.kt", "LibraryHeaders.kt", "LibraryCard.kt", "LibraryGrid.kt")
    private val all = files.joinToString("\n") { UiSourceFiles.file(it) }
    private val headers = UiSourceFiles.file("LibraryHeaders.kt")
    private val card = UiSourceFiles.file("LibraryCard.kt")
    private val page = UiSourceFiles.file("LibraryPages.kt")

    @Test
    fun libraryFilesUseTokensAndNoStockMaterialWidgets() {
        assertFalse("radii come from KireiShapes", Regex("""RoundedCornerShape\(\s*\d""").containsMatchIn(all))
        listOf("OutlinedTextField", "TextButton", "FilterChip", "AlertDialog", "ModalBottomSheet", "DropdownMenu", "Surface", "Card").forEach {
            assertFalse("$it left in the library files", Regex("""\b$it\(""").containsMatchIn(all))
        }
    }

    @Test
    fun headerIsTheSharedLargeTitleBarWithAPersistentSearchCapsule() {
        assertTrue(headers.contains("KireiLargeTitle("))
        assertTrue(headers.contains("KireiLargeTopBar("))
        assertTrue("the search capsule is the shared one", headers.contains("KireiSearchField("))
        assertFalse("no hand-rolled title", page.contains("AppleTypography.displayLarge"))
        assertFalse("search is persistent: the pull-to-reveal controller is gone", page.contains("SearchRevealController"))
        assertTrue("the count stays as a small secondary label", headers.contains("部游戏"))
    }

    @Test
    fun sortIsOneMenuInBothModesAndTheDoneButtonIsAKireiButton() {
        assertTrue(card.contains("KireiMenu("))
        assertTrue("the menu marks the choice with the shared check", card.contains("selected = selected == option"))
        assertTrue(headers.contains("LibraryEditSortMenu("))
        assertTrue("完成 is a primary KireiButton", headers.contains("KireiButton(\"完成\""))
    }

    @Test
    fun engineChipsCarryEngineIconsAndStayReadableWhileEditing() {
        assertTrue(headers.contains("libraryEngineIcon(engine)"))
        assertTrue("the chips are the design-system chip with its icon slot", headers.contains("KireiChip(engine.displayName, spec.selected == engine, colors, icon = libraryEngineIcon(engine))"))
        assertFalse("no hand-rolled engine chip", headers.contains("fun LibraryEngineChip("))
        val dim = Regex("""EDIT_DIM_ALPHA = ([\d.]+)f""").find(headers)?.groupValues?.get(1)?.toFloat() ?: 0f
        assertTrue("edit-mode dimming is mild (>= .6)", dim >= .6f)
    }

    @Test
    fun cardCarriesTheMetaLineBadgesAndSharedPressSpring() {
        assertTrue(card.contains("rememberPressProgress("))
        assertTrue(card.contains("KireiSkeleton("))
        assertTrue("selection numbers are KireiBadges", card.contains("KireiBadge(customOrderPosition.toString()"))
        assertTrue("NSFW-blurred covers carry a marker", card.contains("Icons.Rounded.VisibilityOff"))
        assertTrue("the favourite is a small glass badge", card.contains("LibraryCoverBadge(Icons.Rounded.Favorite"))
        assertTrue("the remove target is a full 48dp", card.contains("REMOVE_TOUCH_SIZE = 48.dp"))
        assertTrue("cover radius lg with the hairline frame", card.contains("libraryCoverFrame(KireiShapes.lg, colors)"))
    }

    @Test
    fun jiggleIsSmallAndReducedMotionKeepsCardsStill() {
        val degrees = Regex("""EDIT_JIGGLE_DEGREES = ([\d.]+)f""").find(card)?.groupValues?.get(1)?.toFloat() ?: 99f
        assertTrue("jiggle amplitude is at most half a degree", degrees <= .5f)
        assertTrue(card.contains("editMode && !reducedMotion"))
    }

    @Test
    fun emptyLibraryHidesChipsAndKeepsAOneLineHint() {
        assertTrue(headers.contains("if (!model.libraryEmpty)"))
        val model = libraryEmptyStateModel(LibraryEmptyKind.LIBRARY_EMPTY, onAddDirectory = {}, onClearFilters = {})!!
        assertEquals("添加游戏目录", model.actionLabel)
        assertTrue(model.showMascot)
        assertTrue("one short plain line", model.message.length <= 16 && !model.message.contains("。"))
    }
}
