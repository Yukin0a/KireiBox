package com.kireibox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for "scan discovers games but they never show up".
 *
 * Removing a scan root hides the games under it. Re-adding (or re-authorizing) the same directory
 * must restore those cards: the scan finds them, so they have to be imported visible. Only the
 * background directory auto-refresh may keep removed cards hidden (`keepRemovedGamesHiddenOnRefresh`).
 */
class ScanRootAddRestoreWiringTest {
    private fun between(
        source: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = source.indexOf(startMarker)
        assertTrue("$startMarker missing", start >= 0)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("$endMarker missing after $startMarker", end >= 0)
        return source.substring(start + startMarker.length, end)
    }

    @Test
    fun `explicitly adding a scan root restores the games it finds`() {
        val add =
            between(
                UiSourceFiles.file("KireiScanRootController.kt"),
                "private fun addOrReplaceScanRoot(",
                "fun removeScanRoot(",
            )
        val importCall = between(add, "core.importScan(", ")")
        assertTrue(
            "an explicit add must import with keepRemovedHidden = false",
            importCall.contains("keepRemovedHidden = false"),
        )
        assertFalse(
            "the background keep-removed setting must not gate an explicit add",
            importCall.contains("keepRemovedGamesHiddenOnRefresh"),
        )
    }

    @Test
    fun `background directory auto-refresh still honours the keep-removed setting`() {
        val refresh =
            between(
                UiSourceFiles.file("KireiSettingsController.kt"),
                "fun onAppForeground()",
                "fun setKrEngineVersion(",
            )
        assertTrue(
            "auto-refresh keeps removed cards hidden per the setting",
            refresh.contains("keepRemovedGamesHiddenOnRefresh"),
        )
    }
}
