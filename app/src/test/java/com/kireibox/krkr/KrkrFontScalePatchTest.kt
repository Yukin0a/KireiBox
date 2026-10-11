package com.kireibox.krkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KrkrFontScalePatchTest {
    @Test
    fun blockCarriesTheFactorLiteralTyranorWrites() {
        assertTrue(KrkrFontScalePatch.buildBlock(1.5f).contains("v * 1.5;"))
        assertTrue(KrkrFontScalePatch.buildBlock(2f).contains("v * 2;"))
        assertTrue(KrkrFontScalePatch.buildBlock(.25f).contains("v * 0.25;"))
        assertTrue(KrkrFontScalePatch.buildBlock(1.25f).contains("Plugins.link(\"kirikiroid2.dll\");"))
    }

    @Test
    fun mergeIsIdempotentAndKeepsTheRestOfThePatch() {
        val patch = "// translation\nSystem.exportMessage = true;\n"
        val once = KrkrFontScalePatch.merge(patch, 1.5f)
        assertTrue(once.startsWith(patch))
        assertEquals(once, KrkrFontScalePatch.merge(once, 1.5f))
        assertEquals(1, Regex("font-scale begin").findAll(once).count())
        val changed = KrkrFontScalePatch.merge(once, 2f)
        assertEquals(1, Regex("font-scale begin").findAll(changed).count())
        assertTrue(changed.contains("v * 2;"))
        assertFalse(changed.contains("v * 1.5;"))
    }

    @Test
    fun turningOffRestoresTheOriginalPatchOrEmptiesTheFile() {
        val patch = "// translation\nSystem.exportMessage = true;\n"
        assertEquals(patch, KrkrFontScalePatch.merge(KrkrFontScalePatch.merge(patch, 1.5f), null))
        assertEquals(patch, KrkrFontScalePatch.merge(KrkrFontScalePatch.merge(patch, 1.5f), KrkrFontScalePatch.OFF))
        assertEquals("", KrkrFontScalePatch.merge(KrkrFontScalePatch.merge(null, .5f), null))
        assertEquals("", KrkrFontScalePatch.merge(null, null))
        assertEquals(patch, KrkrFontScalePatch.merge(patch, null))
    }

    @Test
    fun crlfFilesStayCrlfAndTheBlockCanSitInTheMiddle() {
        val crlf = "A();\r\nB();\r\n"
        val merged = KrkrFontScalePatch.merge(crlf, 1.25f)
        assertFalse(merged.replace("\r\n", "").contains('\n'))
        assertEquals(crlf, KrkrFontScalePatch.merge(merged, null))
        val middle = KrkrFontScalePatch.buildBlock(1.5f).let { "A();\n$it\nB();\n" }
        assertEquals("A();\nB();\n", KrkrFontScalePatch.stripBlock(middle))
    }

    @Test
    fun snapAndLabelFollowTheSixSteps() {
        assertEquals(listOf(.25f, .5f, .75f, 1.25f, 1.5f, 2f), KrkrFontScalePatch.Presets)
        assertEquals(1.5f, KrkrFontScalePatch.snap(1.5f), 0f)
        assertEquals(KrkrFontScalePatch.OFF, KrkrFontScalePatch.snap(1.1f), 0f)
        assertEquals("关闭", KrkrFontScalePatch.label(1f))
        assertEquals("0.75 倍", KrkrFontScalePatch.label(.75f))
        assertEquals("2 倍", KrkrFontScalePatch.label(2f))
    }
}
