package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverAccountLineTest {
    private fun line(
        source: CoverSourceMode,
        name: String,
        id: String
    ) = coverAccountLine(source, CoverAccountState.SIGNED_IN, name, id)

    @Test
    fun nextmoeShowsTheUserNameWithoutTheOpaqueId() {
        assertEquals("Yukin0a123", line(CoverSourceMode.NEXTMOE, "Yukin0a123", "72acc252-7578-484f-8052-a66f9c059fe1"))
    }

    @Test
    fun aMissingNameNeverFallsBackToTheId() {
        assertEquals("账号已连接", line(CoverSourceMode.NEXTMOE, "", "72acc252-7578-484f-8052-a66f9c059fe1"))
        assertEquals("账号已连接", line(CoverSourceMode.HIKARINAGI, "", "42"))
    }

    @Test
    fun bangumiKeepsItsShortNumericId() {
        assertEquals("yuki · ID 297264", line(CoverSourceMode.BANGUMI, "yuki", "297264"))
    }
}
