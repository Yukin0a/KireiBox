package com.kireibox.external.winlator

/**
 * What a Winlator launch asks for. An empty value (0 for [containerId]) means "do not send": the
 * container's own configuration, and Winlator's container fallback (last used, only, first), decide.
 * [execArgs] only exists per game; the global defaults never set it.
 */
data class WinlatorSettings(
    val containerId: Int = 0,
    val containerName: String = "",
    val graphicsDriver: String = "",
    val dxwrapper: String = "",
    val screenSize: String = "",
    val lcAll: String = "",
    val tz: String = "",
    val box64Preset: String = "",
    val envVars: String = "",
    val execArgs: String = "",
    /** Writes the sent values into the container (Winlator asks the user to confirm every time). */
    val save: Boolean = false,
)

/**
 * One game's own choices. A null field follows the global default; an empty string is a deliberate
 * "do not send this", so a game can opt out of something the global default sets.
 */
data class WinlatorGameOverride(
    val containerId: Int? = null,
    val containerName: String? = null,
    val graphicsDriver: String? = null,
    val dxwrapper: String? = null,
    val screenSize: String? = null,
    val lcAll: String? = null,
    val tz: String? = null,
    val box64Preset: String? = null,
    val envVars: String? = null,
    val execArgs: String? = null,
    val save: Boolean? = null,
) {
    val isEmpty: Boolean get() = this == WinlatorGameOverride()

    /** The global defaults with this game's choices on top; values are normalized to what Winlator accepts. */
    fun mergedOver(global: WinlatorSettings): WinlatorSettings =
        WinlatorSettings(
            containerId = containerId?.coerceAtLeast(0) ?: global.containerId,
            containerName = containerName?.trim() ?: global.containerName,
            graphicsDriver = graphicsDriver?.let(WinlatorChoices::graphicsDriver) ?: global.graphicsDriver,
            dxwrapper = dxwrapper?.let(WinlatorChoices::dxwrapper) ?: global.dxwrapper,
            screenSize = screenSize?.let(WinlatorChoices::screenSize) ?: global.screenSize,
            lcAll = lcAll?.let(WinlatorChoices::lcAll) ?: global.lcAll,
            tz = tz?.let(WinlatorChoices::timezone) ?: global.tz,
            box64Preset = box64Preset?.let(WinlatorChoices::box64Preset) ?: global.box64Preset,
            envVars = envVars?.trim() ?: global.envVars,
            execArgs = execArgs?.trim() ?: global.execArgs,
            save = save ?: global.save,
        )
}

/**
 * The values Winlator-CN accepts. Anything outside a list becomes empty ("do not send"), because
 * Winlator rejects a whole launch over one invalid override instead of ignoring it.
 */
object WinlatorChoices {
    /** Vulkan driver (Turnip / Vortek) x OpenGL driver (Zink / VirGL / Gladio), sent as `vulkan,opengl`. */
    val graphicsDrivers = listOf("turnip,zink", "turnip,virgl", "turnip,gladio", "vortek,zink", "vortek,virgl", "vortek,gladio")
    val dxwrappers = listOf("dxvk", "wined3d")
    val box64Presets = listOf("STABILITY", "CONSERVATIVE", "INTERMEDIATE", "PERFORMANCE")
    val screenSizes =
        listOf(
            "640x360",
            "640x480",
            "800x600",
            "854x480",
            "960x544",
            "1024x768",
            "1280x720",
            "1280x800",
            "1280x1024",
            "1366x768",
            "1440x900",
            "1600x900",
            "1920x1080",
        )
    val locales = listOf("ja_JP.UTF-8", "zh_CN.utf8", "zh_TW.utf8", "en_US.UTF-8", "ko_KR.UTF-8")
    val timezones = listOf("Asia/Tokyo", "Asia/Shanghai", "Asia/Taipei", "Asia/Seoul", "UTC", "Europe/London", "America/New_York")

    private val customBox64Preset = Regex("^CUSTOM-\\d+$")

    fun graphicsDriver(value: String?): String = value?.trim()?.lowercase()?.replace(" ", "")?.takeIf { it in graphicsDrivers }.orEmpty()

    fun dxwrapper(value: String?): String = value?.trim()?.lowercase()?.takeIf { it in dxwrappers }.orEmpty()

    fun box64Preset(value: String?): String = value?.trim()?.uppercase()?.takeIf { it in box64Presets || customBox64Preset.matches(it) }.orEmpty()

    fun screenSize(value: String?): String = value?.trim()?.takeIf { it in screenSizes }.orEmpty()

    fun lcAll(value: String?): String = value?.trim()?.takeIf { it in locales }.orEmpty()

    fun timezone(value: String?): String = value?.trim()?.takeIf { it in timezones }.orEmpty()

    /** Space-separated `NAME=value` pairs, as Winlator parses them; anything else is dropped whole. */
    fun envVars(value: String?): String {
        val tokens = value?.trim().orEmpty().split(Regex("\\s+")).filter(String::isNotEmpty)
        return if (tokens.all { token -> token.indexOf('=') > 0 }) tokens.joinToString(" ") else ""
    }
}
