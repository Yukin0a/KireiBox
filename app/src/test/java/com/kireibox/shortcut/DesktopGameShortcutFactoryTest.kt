package com.kireibox.shortcut

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopGameShortcutFactoryTest {
    @Test fun shortcutIdIsStablePerDatabaseGame() {
        assertEquals("kireibox-game-42", DesktopGameShortcutFactory.shortcutId(42))
    }
}
