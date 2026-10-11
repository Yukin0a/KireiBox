package com.yuki.yukihub.settings

/**
 * Pure gate for the background automatic cover task, kept in core so it is unit-testable without
 * Android connectivity APIs.
 *
 * The default ([wifiOnly] = `false`) never blocks, so the legacy behaviour is unchanged. When the
 * user opts into Wi-Fi-only matching, the caller passes whether the active network is currently
 * unmetered; the Android [android.net.ConnectivityManager] check itself stays in the app layer.
 */
object CoverAutoMatchNetworkPolicy {
    @JvmStatic
    fun shouldRun(
        nowUnmetered: Boolean,
        wifiOnly: Boolean,
    ): Boolean = !wifiOnly || nowUnmetered
}
