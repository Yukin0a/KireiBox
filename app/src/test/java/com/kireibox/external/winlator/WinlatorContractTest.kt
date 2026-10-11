package com.kireibox.external.winlator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinlatorContractTest {
    @Test
    fun anEmptySettingsObjectSendsNothing() {
        assertTrue(WinlatorContract.extras(WinlatorSettings()).isEmpty())
    }

    @Test
    fun onlyChosenValuesAreSentAndKeepTheirType() {
        val extras =
            WinlatorContract.extras(
                WinlatorSettings(
                    containerId = 2,
                    containerName = " 我的容器 ",
                    graphicsDriver = "turnip,zink",
                    dxwrapper = "dxvk",
                    screenSize = "1280x720",
                    lcAll = "ja_JP.UTF-8",
                    tz = "Asia/Tokyo",
                    execArgs = "-windowed",
                    save = true,
                ),
            )
        assertEquals(2, extras[WinlatorContract.EXTRA_CONTAINER_ID])
        assertEquals("我的容器", extras[WinlatorContract.EXTRA_CONTAINER_NAME])
        assertEquals("turnip,zink", extras[WinlatorContract.EXTRA_GRAPHICS_DRIVER])
        assertEquals("dxvk", extras[WinlatorContract.EXTRA_DXWRAPPER])
        assertEquals("1280x720", extras[WinlatorContract.EXTRA_SCREEN_SIZE])
        assertEquals("ja_JP.UTF-8", extras[WinlatorContract.EXTRA_LC_ALL])
        assertEquals("Asia/Tokyo", extras[WinlatorContract.EXTRA_TZ])
        assertEquals("-windowed", extras[WinlatorContract.EXTRA_EXEC_ARGS])
        assertEquals(true, extras[WinlatorContract.EXTRA_SAVE])
        assertFalse(extras.containsKey(WinlatorContract.EXTRA_OVERRIDES))
    }

    @Test
    fun boxPresetAndEnvVarsTravelInTheOverridesObject() {
        val extras = WinlatorContract.extras(WinlatorSettings(box64Preset = "performance", envVars = "WINEDLLOVERRIDES=d3d9=n   TZ=Asia/Tokyo"))
        assertEquals(
            mapOf(WinlatorContract.OVERRIDE_BOX64_PRESET to "PERFORMANCE", WinlatorContract.OVERRIDE_ENV_VARS to "WINEDLLOVERRIDES=d3d9=n TZ=Asia/Tokyo"),
            extras[WinlatorContract.EXTRA_OVERRIDES],
        )
    }

    @Test
    fun valuesWinlatorWouldRejectAreNeverSent() {
        // Winlator refuses a whole launch over one invalid override, so these are dropped instead.
        val extras =
            WinlatorContract.extras(
                WinlatorSettings(
                    graphicsDriver = "turnip",
                    dxwrapper = "dxvk-9",
                    screenSize = "100x100",
                    lcAll = "xx",
                    tz = "Mars/Base",
                    box64Preset = "TURBO",
                    envVars = "NOEQUALS",
                ),
            )
        assertTrue(extras.isEmpty())
    }

    @Test
    fun aCustomBoxPresetIsKept() {
        assertEquals("CUSTOM-3", WinlatorChoices.box64Preset("custom-3"))
    }

    @Test
    fun aGameOverridesTheGlobalDefaultsFieldByField() {
        val global = WinlatorSettings(containerName = "A", graphicsDriver = "turnip,zink", screenSize = "1280x720", save = false)
        val merged =
            WinlatorGameOverride(containerName = "B", graphicsDriver = "", execArgs = " -x ", save = true)
                .mergedOver(global)
        assertEquals("B", merged.containerName)
        assertEquals("a deliberate empty value sends nothing", "", merged.graphicsDriver)
        assertEquals("a field the game leaves alone follows the global one", "1280x720", merged.screenSize)
        assertEquals("-x", merged.execArgs)
        assertTrue(merged.save)
    }

    @Test
    fun anOverrideThatFollowsEverythingIsEmpty() {
        assertTrue(WinlatorGameOverride().isEmpty)
        assertFalse(WinlatorGameOverride(containerId = 0).isEmpty)
        assertEquals(WinlatorSettings(containerName = "A"), WinlatorGameOverride().mergedOver(WinlatorSettings(containerName = "A")))
    }
}
