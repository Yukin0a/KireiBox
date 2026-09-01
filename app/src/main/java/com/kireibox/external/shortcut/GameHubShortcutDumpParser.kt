package com.kireibox.external.shortcut

/** Parses only the exported local-game route used by GameHub's pinned desktop shortcuts. */
object GameHubShortcutDumpParser {
    private val SHORTCUT_ID = Regex("ShortcutInfo \\{id=([^,}]+)")
    private val ACTION = Regex("\\bact=([^ ]+)")
    private val COMPONENT = Regex("\\bcmp=([^ }]+)")
    private val NAV_TARGET = Regex("(?:^|[, {])app_nav_target=([^,}]+)")
    private val GAME_ID = Regex("(?:^|[, {])app_nav_game_id=([^,}]+)")

    fun parse(dump: String): Map<String, ExternalShortcutDirectLaunch> {
        val result = linkedMapOf<String, ExternalShortcutDirectLaunch>()
        var currentShortcutId: String? = null
        dump.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            SHORTCUT_ID.find(line)?.groupValues?.getOrNull(1)?.trim()?.let {
                currentShortcutId = it.takeIf(String::isNotEmpty)
            }
            if (!line.startsWith("intents=[Intent {")) return@forEach
            val shortcutId = currentShortcutId ?: return@forEach
            if (ACTION.find(line)?.groupValues?.getOrNull(1) != ExternalShortcutDirectLaunch.ACTION_VIEW) {
                return@forEach
            }
            val component = COMPONENT.find(line)?.groupValues?.getOrNull(1) ?: return@forEach
            if (!isExpectedComponent(component)) return@forEach
            if (NAV_TARGET.find(line)?.groupValues?.getOrNull(1)?.trim() !=
                ExternalShortcutDirectLaunch.NAV_TARGET_LOCAL_GAME
            ) return@forEach
            val recipe = ExternalShortcutDirectLaunch.create(
                GAME_ID.find(line)?.groupValues?.getOrNull(1)?.trim(),
            ) ?: return@forEach
            result[shortcutId] = recipe
        }
        return result
    }

    private fun isExpectedComponent(component: String): Boolean = component ==
        "${ExternalShortcutDirectLaunch.GAME_HUB_PACKAGE}/.DeepLinkActivity" || component ==
        "${ExternalShortcutDirectLaunch.GAME_HUB_PACKAGE}/${ExternalShortcutDirectLaunch.GAME_HUB_ACTIVITY}"
}
