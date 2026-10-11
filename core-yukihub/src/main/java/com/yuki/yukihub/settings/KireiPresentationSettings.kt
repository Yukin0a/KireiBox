package com.yuki.yukihub.settings

enum class MotionMode {
    AUTO,
    POLISHED,
    REDUCED,
    ;

    companion object {
        fun fromStored(value: String?): MotionMode =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: AUTO
    }
}

data class MotionDecisionInputs(
    val animatorsEnabled: Boolean,
    val lowRamDevice: Boolean,
    val memoryClassMb: Int,
    val powerSaveMode: Boolean,
)

object MotionModePolicy {
    /**
     * An explicit choice always wins: a user who picked 完整 (POLISHED) gets the full experience even
     * on a low-RAM device, in power-save mode or with the system/developer animation scale off. Only
     * 自动 (AUTO) consults the device, so a constrained device defaults to 精简 (REDUCED) until the
     * user overrides it.
     */
    fun resolve(
        requested: MotionMode,
        inputs: MotionDecisionInputs,
    ): MotionMode {
        if (requested != MotionMode.AUTO) return requested
        if (!inputs.animatorsEnabled) return MotionMode.REDUCED
        return if (inputs.lowRamDevice || inputs.memoryClassMb <= 192 || inputs.powerSaveMode) {
            MotionMode.REDUCED
        } else {
            MotionMode.POLISHED
        }
    }
}

enum class DashboardProfile(val columns: Int) { COMPACT(2), WIDE(4) }

enum class LandscapeHomeStyle(val storedValue: String, val label: String) {
    /** D: default 16:9 arc Cover Flow for compact landscape phones. */
    IMMERSIVE_COVER("immersive_cover", "16:9 弧线画廊（D）"),

    /** C: optional stacked-focus 9:16 portrait layout with side cards tucked behind center. */
    FOCUS_POSTER("focus_poster", "9:16 专注海报（C）"),
    ;

    companion object {
        fun fromStored(value: String?): LandscapeHomeStyle =
            entries.firstOrNull {
                val normalized = value?.trim()
                it.storedValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: IMMERSIVE_COVER
    }
}

enum class DashboardWidgetType(val unique: Boolean) {
    GAME_SPOTLIGHT(false),
    LIBRARY_OVERVIEW(true),
    RECENT_GAMES(true),
    FAVORITES(true),
    LIBRARY_SHORTCUT(true),
    PLAY_TREND(false),
}

enum class SpotlightSource { FIXED, RECENT, FAVORITES, ALL }

enum class SpotlightStrategy { FIRST, RANDOM_EACH_LAUNCH, DAILY_RANDOM }

/**
 * Dashboard v6 spotlight selection contract. MANUAL keeps an explicit ordered 1-5 game id
 * list; AUTOMATIC keeps the legacy source/strategy pair (recent/favorites/all plus a
 * deterministic ordering). Legacy v5 FIXED+gameId widgets migrate to MANUAL.
 */
enum class SpotlightSelectionMode { MANUAL, AUTOMATIC }

/**
 * Immersive gallery settings are kept separate from dashboard drafts. The type aliases keep
 * the first-wave API compatible with the existing spotlight names used by the dashboard editor.
 */
typealias ImmersiveGallerySource = SpotlightSource
typealias ImmersiveGalleryStrategy = SpotlightStrategy
typealias ImmersiveGallerySelectionMode = SpotlightSelectionMode

data class ImmersiveGallerySettings(
    val selectionMode: ImmersiveGallerySelectionMode = SpotlightSelectionMode.AUTOMATIC,
    val manualIds: List<Long> = emptyList(),
    val source: ImmersiveGallerySource = SpotlightSource.RECENT,
    val strategy: ImmersiveGalleryStrategy = SpotlightStrategy.FIRST,
    val count: Int = DEFAULT_COUNT,
) {
    val orderedManualIds: List<Long> get() = manualIds
    val manualGameIds: List<Long> get() = manualIds
    val maxItems: Int get() = count

    fun normalized(validGameIds: Set<Long>? = null): ImmersiveGallerySettings = ImmersiveGallerySettingsPolicy.normalize(this, validGameIds)

    companion object {
        const val MIN_COUNT = 1
        const val MAX_COUNT = 5
        const val DEFAULT_COUNT = MAX_COUNT
    }
}

enum class TrendMetric {
    PLAY_TIME,
    GAME_OPENS,
    ;

    companion object {
        fun fromStored(value: String?): TrendMetric =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: PLAY_TIME
    }
}

enum class TrendRange(val storedValue: String, val days: Int) {
    DAYS_7("7d", 7),
    DAYS_30("30d", 30),
    ;

    /** Aliases make the persisted contract explicit for callers without coupling them to enum names. */
    val persistedValue: String get() = storedValue
    val storageValue: String get() = storedValue

    companion object {
        fun fromStored(value: String?): TrendRange =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.storedValue.equals(value, ignoreCase = true)
            } ?: DAYS_7
    }
}

enum class TrendVisualStyle {
    LINE,
    AREA,
    ;

    companion object {
        fun fromStored(value: String?): TrendVisualStyle =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: LINE
    }
}

data class DashboardWidgetSize(val columns: Int, val rows: Int)

data class DashboardWidgetSpec(
    val instanceId: String,
    val type: DashboardWidgetType,
    val size: DashboardWidgetSize,
    val gameId: Long? = null,
    val spotlightSource: SpotlightSource = SpotlightSource.RECENT,
    val spotlightStrategy: SpotlightStrategy = SpotlightStrategy.FIRST,
    val spotlightSelectionMode: SpotlightSelectionMode = SpotlightSelectionMode.AUTOMATIC,
    val spotlightGameIds: List<Long> = emptyList(),
    val spotlightMaxPages: Int = DEFAULT_SPOTLIGHT_MAX_PAGES,
    val styleKey: String = "",
    val column: Int = UNPLACED,
    val row: Int = UNPLACED,
    /** Height is stored independently from the horizontal span in 8dp steps. */
    val heightSteps: Int = UNSPECIFIED_HEIGHT,
    val trendMetric: TrendMetric = TrendMetric.PLAY_TIME,
    val trendRange: TrendRange = TrendRange.DAYS_7,
    val trendVisualStyle: TrendVisualStyle = TrendVisualStyle.LINE,
) {
    companion object {
        const val UNPLACED = -1
        const val UNSPECIFIED_HEIGHT = 0
        const val DEFAULT_SPOTLIGHT_MAX_PAGES = 5
    }
}

data class DashboardLayout(
    val version: Int = DashboardLayouts.VERSION,
    val widgets: List<DashboardWidgetSpec>,
    val autoFill: Boolean = true,
)

data class DashboardPlacement(
    val spec: DashboardWidgetSpec,
    val index: Int,
    val column: Int,
    val row: Int,
)

/** Type-specific styles. Unknown values are normalized without discarding the layout. */
object DashboardStyles {
    const val SPOTLIGHT_IMMERSIVE = "spotlight_immersive"
    const val SPOTLIGHT_POSTER = "spotlight_poster"
    const val SPOTLIGHT_HORIZONTAL = "spotlight_horizontal"
    const val OVERVIEW_MINIMAL = "overview_minimal"
    const val OVERVIEW_SPLIT = "overview_split"
    const val OVERVIEW_RING = "overview_ring"
    const val OVERVIEW_TILES = "overview_tiles"
    const val OVERVIEW_FOCUS = "overview_focus"
    const val RECENT_STRIP = "recent_strip"
    const val RECENT_GLASS = "recent_glass"
    const val RECENT_COVERS = "recent_covers"
    const val FAVORITES_STACK = "favorites_stack"
    const val FAVORITES_MOSAIC = "favorites_mosaic"
    const val FAVORITES_FEATURED = "favorites_featured"
    const val FAVORITES_COUNT = "favorites_count"
    const val LIBRARY_ICON = "library_icon"
    const val LIBRARY_PROGRESS = "library_progress"
    const val TREND_7_DAY = "trend_7_day"
    const val TREND_30_DAY = "trend_30_day"
    const val TREND_LAUNCH_7_DAY = "trend_launch_7_day"
    const val TREND_LAUNCH_30_DAY = "trend_launch_30_day"

    fun allowed(type: DashboardWidgetType): List<String> =
        when (type) {
            DashboardWidgetType.GAME_SPOTLIGHT -> listOf(SPOTLIGHT_IMMERSIVE, SPOTLIGHT_POSTER, SPOTLIGHT_HORIZONTAL)
            DashboardWidgetType.LIBRARY_OVERVIEW ->
                listOf(
                    OVERVIEW_MINIMAL,
                    OVERVIEW_SPLIT,
                    OVERVIEW_RING,
                    OVERVIEW_TILES,
                    OVERVIEW_FOCUS,
                )
            DashboardWidgetType.RECENT_GAMES -> listOf(RECENT_STRIP, RECENT_GLASS, RECENT_COVERS)
            DashboardWidgetType.FAVORITES ->
                listOf(
                    FAVORITES_STACK,
                    FAVORITES_MOSAIC,
                    FAVORITES_FEATURED,
                    FAVORITES_COUNT,
                )
            DashboardWidgetType.LIBRARY_SHORTCUT -> listOf(LIBRARY_ICON, LIBRARY_PROGRESS)
            DashboardWidgetType.PLAY_TREND ->
                listOf(
                    TREND_7_DAY,
                    TREND_30_DAY,
                    TREND_LAUNCH_7_DAY,
                    TREND_LAUNCH_30_DAY,
                )
        }

    fun default(type: DashboardWidgetType): String = allowed(type).first()

    fun normalize(
        type: DashboardWidgetType,
        raw: String?,
    ): String = raw?.takeIf { it in allowed(type) } ?: default(type)
}
