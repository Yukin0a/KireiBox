package com.kireibox.ui

import com.kireibox.external.shortcut.ExternalShortcutDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalShortcutPickerPolicyTest {
    private val available = entry("com.game", "totsulover_chs", "徒然喜欢你", false)
    private val imported = entry("com.other", "already", "已导入", true)

    @Test fun searchesAllVisibleFields() {
        val entries = listOf(available, imported)
        assertEquals(listOf(available), ExternalShortcutPickerPolicy.visible(entries, "徒然"))
        assertEquals(listOf(available), ExternalShortcutPickerPolicy.visible(entries, "com.game"))
        assertEquals(listOf(imported), ExternalShortcutPickerPolicy.visible(entries, "already"))
    }

    @Test fun importedCannotBeSelectedAndToggleAllUsesAvailableOnly() {
        assertTrue(ExternalShortcutPickerPolicy.toggle(listOf(imported), emptySet(), imported.stableKey).isEmpty())
        assertEquals(setOf(available.stableKey), ExternalShortcutPickerPolicy.toggleAll(listOf(available, imported), emptySet()))
    }

    private fun entry(
        pkg: String,
        id: String,
        title: String,
        imported: Boolean
    ) = ExternalShortcutPickerEntry(
        ExternalShortcutDescriptor(0, pkg, pkg, id, title, null, pinned = true, dynamic = false),
        imported,
    )
}
