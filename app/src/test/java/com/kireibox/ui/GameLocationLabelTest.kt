package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GameLocationLabelTest {
    @Test fun formatsPrimaryStorage() {
        assertEquals(
            "内部存储 / Download / gal / ATRI",
            GameLocationLabel.formatDocumentId("primary:Download/gal/ATRI")
        )
    }

    @Test fun formatsSdCardStorage() {
        assertEquals(
            "存储卡 1234-ABCD / Games / ATRI",
            GameLocationLabel.formatDocumentId("1234-ABCD:Games/ATRI")
        )
    }

    @Test fun formatsDocumentsHomeRoot() {
        assertEquals(
            "内部存储 / Documents / VisualNovels",
            GameLocationLabel.formatDocumentId("home:VisualNovels")
        )
    }
}
