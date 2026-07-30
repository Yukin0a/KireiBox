package com.yuki.yukihub.launcherbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherKrkrBridgeTest {
    @Test
    fun startupDialogPolicyDefaultsToShowAll() {
        assertEquals(
            LauncherKrkrBridge.DIALOG_POLICY_SHOW_ALL,
            LauncherKrkrBridge.normalizeStartupDialogPolicy(null)
        )
        assertEquals(
            LauncherKrkrBridge.DIALOG_POLICY_SHOW_ALL,
            LauncherKrkrBridge.normalizeStartupDialogPolicy("unexpected")
        )
    }

    @Test
    fun startupDialogSkipPolicyRoundTrips() {
        assertEquals(
            LauncherKrkrBridge.DIALOG_POLICY_SKIP_SINGLE_BUTTON_STARTUP_INFO,
            LauncherKrkrBridge.normalizeStartupDialogPolicy("skip_single_button_startup_info")
        )
    }
}
