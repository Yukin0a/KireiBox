package com.kireibox.krkr

import org.junit.Assert.assertFalse
import org.junit.Test

class KrkrGameSettingsTest {
    @Test
    fun startupDialogIsNotSkippedUnlessChosen() {
        assertFalse(KrkrGameSettings().skipStartupDialog)
    }
}
