package com.yuki.yukihub.settings

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

enum class MotionMode {
    AUTO, POLISHED, REDUCED;

    companion object {
        fun fromStored(value: String?): MotionMode = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: AUTO
    }
}

data class MotionDecisionInputs(
    val animatorsEnabled: Boolean,
    val lowRamDevice: Boolean,
    val memoryClassMb: Int,
    val powerSaveMode: Boolean
)

object MotionModePolicy {
    fun resolve(requested: MotionMode, inputs: MotionDecisionInputs): MotionMode {
        if (!inputs.animatorsEnabled) return MotionMode.REDUCED
        if (requested != MotionMode.AUTO) return requested
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
    FOCUS_POSTER("focus_poster", "9:16 专注海报（C）");

    companion object {
        fun fromStored(value: String?): LandscapeHomeStyle = entries.firstOrNull {
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
    PLAY_TREND(false)
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

    fun normalized(validGameIds: Set<Long>? = null): ImmersiveGallerySettings =
        ImmersiveGallerySettingsPolicy.normalize(this, validGameIds)

    companion object {
        const val MIN_COUNT = 1
        const val MAX_COUNT = 5
        const val DEFAULT_COUNT = MAX_COUNT
    }
}

enum class TrendMetric {
    PLAY_TIME,
    GAME_OPENS;

    companion object {
        fun fromStored(value: String?): TrendMetric = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: PLAY_TIME
    }
}

enum class TrendRange(val storedValue: String, val days: Int) {
    DAYS_7("7d", 7),
    DAYS_30("30d", 30);

    /** Aliases make the persisted contract explicit for callers without coupling them to enum names. */
    val persistedValue: String get() = storedValue
    val storageValue: String get() = storedValue

    companion object {
        fun fromStored(value: String?): TrendRange = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true) || it.storedValue.equals(value, ignoreCase = true)
        } ?: DAYS_7
    }
}

enum class TrendVisualStyle {
    LINE,
    AREA;

    companion object {
        fun fromStored(value: String?): TrendVisualStyle = entries.firstOrNull {
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
    val autoFill: Boolean = true
)

data class DashboardPlacement(
    val spec: DashboardWidgetSpec,
    val index: Int,
    val column: Int,
    val row: Int
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

    fun allowed(type: DashboardWidgetType): List<String> = when (type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> listOf(SPOTLIGHT_IMMERSIVE, SPOTLIGHT_POSTER, SPOTLIGHT_HORIZONTAL)
        DashboardWidgetType.LIBRARY_OVERVIEW -> listOf(
            OVERVIEW_MINIMAL, OVERVIEW_SPLIT, OVERVIEW_RING, OVERVIEW_TILES, OVERVIEW_FOCUS
        )
        DashboardWidgetType.RECENT_GAMES -> listOf(RECENT_STRIP, RECENT_GLASS, RECENT_COVERS)
        DashboardWidgetType.FAVORITES -> listOf(
            FAVORITES_STACK, FAVORITES_MOSAIC, FAVORITES_FEATURED, FAVORITES_COUNT
        )
        DashboardWidgetType.LIBRARY_SHORTCUT -> listOf(LIBRARY_ICON, LIBRARY_PROGRESS)
        DashboardWidgetType.PLAY_TREND -> listOf(
            TREND_7_DAY, TREND_30_DAY, TREND_LAUNCH_7_DAY, TREND_LAUNCH_30_DAY
        )
    }

    fun default(type: DashboardWidgetType): String = allowed(type).first()
    fun normalize(type: DashboardWidgetType, raw: String?): String =
        raw?.takeIf { it in allowed(type) } ?: default(type)
}

object DashboardLayouts {
    const val VERSION = 6
    const val MAX_WIDGETS = 12
    const val MAX_SPOTLIGHT_PAGES = 5
    const val HEIGHT_STEP_DP = 8
    const val GAP_STEPS = 1
    const val MAX_VERTICAL_STEPS = 256
    /** Compatibility alias for older tests and callers. */
    const val MAX_ROWS = MAX_VERTICAL_STEPS

    private val oneByOne = DashboardWidgetSize(1, 1)

    fun default(profile: DashboardProfile): DashboardLayout = when (profile) {
        DashboardProfile.COMPACT -> DashboardLayout(
            widgets = listOf(
                DashboardWidgetSpec(
                    "default-game-compact", DashboardWidgetType.GAME_SPOTLIGHT,
                    DashboardWidgetSize(2, 2), heightSteps = defaultHeightSteps(DashboardWidgetType.GAME_SPOTLIGHT)
                ),
                DashboardWidgetSpec(
                    "default-overview-compact", DashboardWidgetType.LIBRARY_OVERVIEW,
                    DashboardWidgetSize(2, 1), styleKey = DashboardStyles.OVERVIEW_SPLIT,
                    heightSteps = defaultHeightSteps(DashboardWidgetType.LIBRARY_OVERVIEW)
                ),
                DashboardWidgetSpec(
                    "default-recent-compact", DashboardWidgetType.RECENT_GAMES,
                    DashboardWidgetSize(2, 1), heightSteps = defaultHeightSteps(DashboardWidgetType.RECENT_GAMES)
                )
            )
        )
        DashboardProfile.WIDE -> DashboardLayout(
            widgets = listOf(
                DashboardWidgetSpec(
                    "default-game-wide", DashboardWidgetType.GAME_SPOTLIGHT,
                    DashboardWidgetSize(2, 2), heightSteps = defaultHeightSteps(DashboardWidgetType.GAME_SPOTLIGHT)
                ),
                DashboardWidgetSpec(
                    "default-overview-wide", DashboardWidgetType.LIBRARY_OVERVIEW,
                    DashboardWidgetSize(2, 1), styleKey = DashboardStyles.OVERVIEW_SPLIT,
                    heightSteps = defaultHeightSteps(DashboardWidgetType.LIBRARY_OVERVIEW)
                ),
                DashboardWidgetSpec(
                    "default-recent-wide", DashboardWidgetType.RECENT_GAMES,
                    DashboardWidgetSize(2, 1), heightSteps = defaultHeightSteps(DashboardWidgetType.RECENT_GAMES)
                )
            )
        )
    }

    fun heightRange(type: DashboardWidgetType): IntRange = when (type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> 17..52
        DashboardWidgetType.LIBRARY_OVERVIEW -> 13..28
        DashboardWidgetType.RECENT_GAMES -> 14..28
        DashboardWidgetType.FAVORITES -> 13..28
        DashboardWidgetType.LIBRARY_SHORTCUT -> 12..24
        DashboardWidgetType.PLAY_TREND -> 15..36
    }

    fun defaultHeightSteps(type: DashboardWidgetType): Int = when (type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> 34
        DashboardWidgetType.LIBRARY_OVERVIEW -> 17
        DashboardWidgetType.RECENT_GAMES -> 17
        DashboardWidgetType.FAVORITES -> 17
        DashboardWidgetType.LIBRARY_SHORTCUT -> 15
        DashboardWidgetType.PLAY_TREND -> 17
    }

    fun heightDp(spec: DashboardWidgetSpec): Int =
        normalizeHeight(spec.type, spec.heightSteps) * HEIGHT_STEP_DP

    fun allowedWidths(type: DashboardWidgetType, profile: DashboardProfile): List<Int> =
        allowedSizes(type, profile).map { it.columns }.distinct()

    fun allowedSizes(type: DashboardWidgetType, profile: DashboardProfile): List<DashboardWidgetSize> = when (type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> if (profile == DashboardProfile.COMPACT) {
            listOf(DashboardWidgetSize(1, 2), DashboardWidgetSize(2, 1), DashboardWidgetSize(2, 2), DashboardWidgetSize(2, 3))
        } else {
            listOf(
                DashboardWidgetSize(1, 2), DashboardWidgetSize(2, 1), DashboardWidgetSize(2, 2),
                DashboardWidgetSize(3, 2), DashboardWidgetSize(4, 2), DashboardWidgetSize(4, 3)
            )
        }
        DashboardWidgetType.LIBRARY_OVERVIEW,
        DashboardWidgetType.FAVORITES -> listOf(oneByOne, DashboardWidgetSize(2, 1))
        DashboardWidgetType.RECENT_GAMES -> if (profile == DashboardProfile.COMPACT) {
            listOf(oneByOne, DashboardWidgetSize(2, 1))
        } else {
            listOf(DashboardWidgetSize(2, 1), DashboardWidgetSize(4, 1))
        }
        DashboardWidgetType.LIBRARY_SHORTCUT -> listOf(oneByOne)
        DashboardWidgetType.PLAY_TREND -> if (profile == DashboardProfile.COMPACT) {
            listOf(DashboardWidgetSize(1, 1), DashboardWidgetSize(2, 1))
        } else {
            listOf(
                DashboardWidgetSize(1, 1), DashboardWidgetSize(2, 1),
                DashboardWidgetSize(3, 1), DashboardWidgetSize(4, 1)
            )
        }
    }

    fun normalize(layout: DashboardLayout, profile: DashboardProfile): DashboardLayout {
        val base = normalizeBase(layout, profile)
        return if (base.autoFill) base else resolveManual(base, profile)
    }

    fun nextSize(spec: DashboardWidgetSpec, profile: DashboardProfile): DashboardWidgetSize {
        val allowed = allowedSizes(spec.type, profile)
        val index = allowed.indexOf(spec.size)
        return allowed[(if (index < 0) 0 else index + 1) % allowed.size]
    }

    fun resize(layout: DashboardLayout, profile: DashboardProfile, instanceId: String, size: DashboardWidgetSize): DashboardLayout {
        val updated = normalizeBase(
            layout.copy(widgets = layout.widgets.map {
                if (it.instanceId == instanceId) {
                    it.copy(size = size, heightSteps = legacyHeightSteps(size.rows))
                } else it
            }),
            profile
        )
        return if (updated.autoFill) updated else resolveManual(updated, profile, instanceId)
    }

    fun resizeWidth(layout: DashboardLayout, profile: DashboardProfile, instanceId: String, columns: Int): DashboardLayout {
        val updated = normalizeBase(
            layout.copy(widgets = layout.widgets.map {
                if (it.instanceId == instanceId) it.copy(size = it.size.copy(columns = columns)) else it
            }),
            profile
        )
        return if (updated.autoFill) updated else resolveManual(updated, profile, instanceId)
    }

    fun resizeHeight(layout: DashboardLayout, profile: DashboardProfile, instanceId: String, heightSteps: Int): DashboardLayout {
        val updated = normalizeBase(
            layout.copy(widgets = layout.widgets.map {
                if (it.instanceId == instanceId) {
                    it.copy(heightSteps = heightSteps.coerceIn(heightRange(it.type)))
                } else it
            }),
            profile
        )
        return if (updated.autoFill) updated else resolveManual(updated, profile, instanceId)
    }

    fun setStyle(layout: DashboardLayout, profile: DashboardProfile, instanceId: String, styleKey: String): DashboardLayout =
        normalize(layout.copy(widgets = layout.widgets.map {
            if (it.instanceId == instanceId) it.copy(styleKey = DashboardStyles.normalize(it.type, styleKey)) else it
        }), profile)

    /**
     * Switches a spotlight widget to automatic selection with the given source, ordering
     * strategy and page count. Manual id lists are cleared so AUTOMATIC stays canonical.
     */
    fun setSpotlightAutomatic(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        source: SpotlightSource,
        strategy: SpotlightStrategy,
        maxPages: Int = MAX_SPOTLIGHT_PAGES,
    ): DashboardLayout = normalize(
        layout.copy(widgets = layout.widgets.map { spec ->
            if (spec.instanceId != instanceId || spec.type != DashboardWidgetType.GAME_SPOTLIGHT) spec
            else spec.copy(
                gameId = null,
                spotlightSource = source,
                spotlightStrategy = strategy,
                spotlightSelectionMode = SpotlightSelectionMode.AUTOMATIC,
                spotlightGameIds = emptyList(),
                spotlightMaxPages = maxPages.coerceIn(1, MAX_SPOTLIGHT_PAGES),
            )
        }),
        profile,
    )

    /** Replaces the spotlight with an explicit ordered manual list (1-5 games). */
    fun setSpotlightManual(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        gameIds: List<Long>,
        maxPages: Int = MAX_SPOTLIGHT_PAGES,
    ): DashboardLayout = normalize(
        layout.copy(widgets = layout.widgets.map { spec ->
            if (spec.instanceId != instanceId || spec.type != DashboardWidgetType.GAME_SPOTLIGHT) spec
            else spec.copy(
                gameId = null,
                spotlightSource = SpotlightSource.FIXED,
                spotlightStrategy = SpotlightStrategy.FIRST,
                spotlightSelectionMode = SpotlightSelectionMode.MANUAL,
                spotlightGameIds = gameIds,
                spotlightMaxPages = maxPages.coerceIn(1, MAX_SPOTLIGHT_PAGES),
            )
        }),
        profile,
    )

    fun setTrendConfig(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        metric: TrendMetric,
        range: TrendRange,
        visualStyle: TrendVisualStyle,
    ): DashboardLayout = normalize(
        layout.copy(widgets = layout.widgets.map { spec ->
            if (spec.instanceId != instanceId || spec.type != DashboardWidgetType.PLAY_TREND) spec
            else spec.copy(
                trendMetric = metric,
                trendRange = range,
                trendVisualStyle = visualStyle,
            )
        }),
        profile,
    )

    fun move(layout: DashboardLayout, profile: DashboardProfile, instanceId: String, targetIndex: Int): DashboardLayout {
        if (!layout.autoFill) return normalize(layout, profile)
        val mutable = layout.widgets.toMutableList()
        val from = mutable.indexOfFirst { it.instanceId == instanceId }
        if (from < 0) return normalize(layout, profile)
        val item = mutable.removeAt(from)
        mutable.add(targetIndex.coerceIn(0, mutable.size), item)
        return normalize(layout.copy(widgets = mutable), profile)
    }

    /** Manual placement gives the dragged widget priority and relocates collisions to the nearest free cells. */
    fun placeAt(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        column: Int,
        row: Int
    ): DashboardLayout {
        val frozen = freezePositions(layout, profile)
        val manual = frozen.copy(
            widgets = frozen.widgets.map {
                if (it.instanceId == instanceId) it.copy(column = column, row = row) else it
            },
            autoFill = false
        )
        return resolveManual(normalizeBase(manual, profile), profile, instanceId)
    }

    fun setAutoFill(layout: DashboardLayout, profile: DashboardProfile, enabled: Boolean): DashboardLayout {
        if (enabled) return normalizeBase(layout.copy(autoFill = true), profile)
        return freezePositions(layout, profile).copy(autoFill = false)
    }

    fun remove(layout: DashboardLayout, profile: DashboardProfile, instanceId: String): DashboardLayout =
        normalize(layout.copy(widgets = layout.widgets.filterNot { it.instanceId == instanceId }), profile)

    fun add(layout: DashboardLayout, profile: DashboardProfile, type: DashboardWidgetType, gameId: Long? = null): DashboardLayout {
        val spec = DashboardWidgetSpec(
            instanceId = "${type.name.lowercase()}-${UUID.randomUUID()}",
            type = type,
            size = allowedSizes(type, profile).first(),
            gameId = gameId,
            spotlightSource = if (gameId == null) SpotlightSource.RECENT else SpotlightSource.FIXED,
            spotlightSelectionMode = if (gameId == null) {
                SpotlightSelectionMode.AUTOMATIC
            } else {
                SpotlightSelectionMode.MANUAL
            },
            spotlightGameIds = if (gameId == null) emptyList() else listOfNotNull(gameId),
            styleKey = DashboardStyles.default(type),
            heightSteps = defaultHeightSteps(type)
        )
        return normalize(layout.copy(widgets = layout.widgets + spec), profile)
    }

    fun encode(layout: DashboardLayout, profile: DashboardProfile): String {
        val clean = normalize(layout, profile)
        val root = JSONObject().put("version", VERSION).put("autoFill", clean.autoFill)
        val widgets = JSONArray()
        clean.widgets.forEach { spec ->
            widgets.put(
                JSONObject()
                    .put("id", spec.instanceId)
                    .put("type", spec.type.name)
                    .put("columns", spec.size.columns)
                    .put("rows", spec.size.rows)
                    .put("heightSteps", spec.heightSteps)
                    .put("style", spec.styleKey)
                    .put("spotlightSource", spec.spotlightSource.name)
                    .put("spotlightStrategy", spec.spotlightStrategy.name)
                    .put("spotlightSelectionMode", spec.spotlightSelectionMode.name)
                    .put("spotlightMaxPages", spec.spotlightMaxPages)
                    .put("column", spec.column)
                    .put("row", spec.row)
                    .apply {
                        if (spec.type == DashboardWidgetType.GAME_SPOTLIGHT &&
                            spec.spotlightSelectionMode == SpotlightSelectionMode.MANUAL
                        ) {
                            put("spotlightGameIds", JSONArray(spec.spotlightGameIds))
                        }
                        if (spec.type == DashboardWidgetType.PLAY_TREND) {
                            put("trendMetric", spec.trendMetric.name)
                            put("trendRange", spec.trendRange.storedValue)
                            put("trendVisualStyle", spec.trendVisualStyle.name)
                        }
                    }
                    .apply { spec.gameId?.let { put("gameId", it) } }
            )
        }
        return root.put("widgets", widgets).toString()
    }

    fun decode(raw: String?, profile: DashboardProfile): DashboardLayout {
        if (raw.isNullOrBlank()) return default(profile)
        return runCatching {
            val root = JSONObject(raw)
            val storedVersion = root.optInt("version", 1)
            if (storedVersion > VERSION) return@runCatching default(profile)
            val array = root.optJSONArray("widgets") ?: return@runCatching default(profile)
            val widgets = ArrayList<DashboardWidgetSpec>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val type = DashboardWidgetType.entries.firstOrNull {
                    it.name.equals(item.optString("type"), ignoreCase = true)
                } ?: continue
                val gameId = item.optLong("gameId").takeIf { item.has("gameId") && it > 0L }
                val legacyTrend = legacyTrendConfig(item.optString("style"))
                val legacyFixed = gameId != null
                val selectionMode = if (storedVersion >= 6) {
                    SpotlightSelectionMode.entries.firstOrNull {
                        it.name.equals(item.optString("spotlightSelectionMode"), ignoreCase = true)
                    } ?: SpotlightSelectionMode.AUTOMATIC
                } else if (legacyFixed) {
                    SpotlightSelectionMode.MANUAL
                } else {
                    SpotlightSelectionMode.AUTOMATIC
                }
                widgets += DashboardWidgetSpec(
                    instanceId = item.optString("id"),
                    type = type,
                    size = DashboardWidgetSize(item.optInt("columns", 1), item.optInt("rows", 1)),
                    gameId = gameId,
                    spotlightSource = if (legacyFixed) {
                        SpotlightSource.FIXED
                    } else if (storedVersion >= 4) {
                        SpotlightSource.entries.firstOrNull {
                            it.name.equals(item.optString("spotlightSource"), ignoreCase = true)
                        } ?: SpotlightSource.RECENT
                    } else {
                        SpotlightSource.RECENT
                    },
                    spotlightStrategy = if (storedVersion >= 4) {
                        SpotlightStrategy.entries.firstOrNull {
                            it.name.equals(item.optString("spotlightStrategy"), ignoreCase = true)
                        } ?: SpotlightStrategy.FIRST
                    } else {
                        SpotlightStrategy.FIRST
                    },
                    spotlightSelectionMode = selectionMode,
                    spotlightGameIds = if (storedVersion >= 6) {
                        readSpotlightGameIds(item)
                    } else if (legacyFixed) {
                        listOf(gameId)
                    } else {
                        emptyList()
                    },
                    spotlightMaxPages = if (storedVersion >= 6) {
                        item.optInt("spotlightMaxPages", MAX_SPOTLIGHT_PAGES).coerceIn(1, MAX_SPOTLIGHT_PAGES)
                    } else {
                        MAX_SPOTLIGHT_PAGES
                    },
                    styleKey = if (storedVersion >= 2) item.optString("style") else DashboardStyles.default(type),
                    column = if (storedVersion >= 2) item.optInt("column", DashboardWidgetSpec.UNPLACED) else DashboardWidgetSpec.UNPLACED,
                    row = if (storedVersion >= 3) {
                        item.optInt("row", DashboardWidgetSpec.UNPLACED)
                    } else if (storedVersion >= 2) {
                        item.optInt("row", DashboardWidgetSpec.UNPLACED)
                            .takeIf { it >= 0 }?.times(18) ?: DashboardWidgetSpec.UNPLACED
                    } else {
                        DashboardWidgetSpec.UNPLACED
                    },
                    heightSteps = if (storedVersion >= 3) {
                        item.optInt("heightSteps", defaultHeightSteps(type))
                    } else {
                        legacyHeightSteps(item.optInt("rows", 1))
                    },
                    trendMetric = if (type != DashboardWidgetType.PLAY_TREND) {
                        TrendMetric.PLAY_TIME
                    } else if (storedVersion >= 5) {
                        TrendMetric.fromStored(item.optString("trendMetric"))
                    } else {
                        legacyTrend.metric
                    },
                    trendRange = if (type != DashboardWidgetType.PLAY_TREND) {
                        TrendRange.DAYS_7
                    } else if (storedVersion >= 5) {
                        TrendRange.fromStored(item.optString("trendRange"))
                    } else {
                        legacyTrend.range
                    },
                    trendVisualStyle = if (type != DashboardWidgetType.PLAY_TREND) {
                        TrendVisualStyle.LINE
                    } else if (storedVersion >= 5) {
                        TrendVisualStyle.fromStored(item.optString("trendVisualStyle"))
                    } else {
                        legacyTrend.visualStyle
                    },
                )
            }
            normalize(
                DashboardLayout(
                    version = VERSION,
                    widgets = widgets,
                    autoFill = if (storedVersion >= 2) root.optBoolean("autoFill", true) else true
                ),
                profile
            )
        }.getOrElse { default(profile) }
    }

    fun pack(layout: DashboardLayout, profile: DashboardProfile): List<DashboardPlacement> {
        val clean = normalize(layout, profile)
        if (!clean.autoFill) return clean.widgets.mapIndexed { index, spec ->
            DashboardPlacement(spec, index, spec.column, spec.row)
        }
        return firstFit(clean.widgets, profile)
    }

    private fun normalizeBase(layout: DashboardLayout, profile: DashboardProfile): DashboardLayout {
        val seenIds = HashSet<String>()
        val seenUnique = HashSet<DashboardWidgetType>()
        val seenGames = HashSet<Long>()
        var automaticGameSeen = false
        val normalized = ArrayList<DashboardWidgetSpec>()
        layout.widgets.take(MAX_WIDGETS * 2).forEachIndexed { index, raw ->
            if (normalized.size >= MAX_WIDGETS) return@forEachIndexed
            if (raw.type.unique && !seenUnique.add(raw.type)) return@forEachIndexed
            val selectionMode = when {
                raw.type != DashboardWidgetType.GAME_SPOTLIGHT -> SpotlightSelectionMode.AUTOMATIC
                raw.spotlightSelectionMode == SpotlightSelectionMode.MANUAL -> SpotlightSelectionMode.MANUAL
                raw.gameId != null -> SpotlightSelectionMode.MANUAL
                else -> SpotlightSelectionMode.AUTOMATIC
            }
            if (raw.type == DashboardWidgetType.GAME_SPOTLIGHT) {
                if (raw.gameId != null) {
                    if (!seenGames.add(raw.gameId)) return@forEachIndexed
                } else if (raw.spotlightSelectionMode != SpotlightSelectionMode.MANUAL) {
                    if (automaticGameSeen) return@forEachIndexed
                    automaticGameSeen = true
                }
            }
            val id = raw.instanceId.takeIf { it.isNotBlank() && seenIds.add(it) }
                ?: "${raw.type.name.lowercase()}-$index-${UUID.randomUUID()}"
            seenIds.add(id)
            val allowed = allowedSizes(raw.type, profile)
            val columns = allowedWidths(raw.type, profile)
                .minByOrNull { abs(it - raw.size.columns) } ?: 1
            val heightSteps = normalizeHeight(
                raw.type,
                if (raw.heightSteps > 0) raw.heightSteps else legacyHeightSteps(raw.size.rows)
            )
            val size = nearestAllowed(raw.size.copy(columns = columns), allowed)
            val spotlightSource = when {
                raw.type != DashboardWidgetType.GAME_SPOTLIGHT -> raw.spotlightSource
                selectionMode == SpotlightSelectionMode.MANUAL -> SpotlightSource.FIXED
                raw.spotlightSource == SpotlightSource.FIXED -> SpotlightSource.RECENT
                else -> raw.spotlightSource
            }
            normalized += raw.copy(
                instanceId = id,
                size = size,
                gameId = raw.gameId.takeIf {
                    raw.type != DashboardWidgetType.GAME_SPOTLIGHT || selectionMode != SpotlightSelectionMode.MANUAL
                },
                spotlightSource = spotlightSource,
                spotlightSelectionMode = selectionMode,
                spotlightGameIds = if (raw.type == DashboardWidgetType.GAME_SPOTLIGHT &&
                    selectionMode == SpotlightSelectionMode.MANUAL
                ) {
                    val seed = raw.spotlightGameIds.ifEmpty { listOfNotNull(raw.gameId) }
                    seed.filter { it > 0L }.distinct().take(MAX_SPOTLIGHT_PAGES)
                } else {
                    emptyList()
                },
                spotlightMaxPages = raw.spotlightMaxPages.coerceIn(1, MAX_SPOTLIGHT_PAGES),
                heightSteps = heightSteps,
                styleKey = DashboardStyles.normalize(raw.type, raw.styleKey),
                column = raw.column.coerceIn(DashboardWidgetSpec.UNPLACED, profile.columns - size.columns),
                row = raw.row.coerceIn(DashboardWidgetSpec.UNPLACED, MAX_VERTICAL_STEPS - occupiedHeight(raw.copy(heightSteps = heightSteps)))
            )
        }
        return DashboardLayout(VERSION, normalized, layout.autoFill)
    }

    private fun freezePositions(layout: DashboardLayout, profile: DashboardProfile): DashboardLayout {
        val base = normalizeBase(layout, profile)
        val placements = if (base.autoFill) firstFit(base.widgets, profile) else pack(base, profile)
        val byId = placements.associateBy { it.spec.instanceId }
        return base.copy(
            autoFill = false,
            widgets = base.widgets.map { spec ->
                val placement = byId[spec.instanceId]
                spec.copy(column = placement?.column ?: 0, row = placement?.row ?: 0)
            }
        )
    }

    private fun resolveManual(
        layout: DashboardLayout,
        profile: DashboardProfile,
        priorityId: String? = null
    ): DashboardLayout {
        val occupied = Array(MAX_ROWS) { BooleanArray(profile.columns) }
        val placed = HashMap<String, Pair<Int, Int>>()
        val ordered = layout.widgets.sortedBy { if (it.instanceId == priorityId) 0 else 1 }
        ordered.forEach { spec ->
            val preferredColumn = spec.column.coerceAtLeast(0).coerceAtMost(profile.columns - spec.size.columns)
            val preferredRow = spec.row.coerceAtLeast(0).coerceAtMost(MAX_VERTICAL_STEPS - occupiedHeight(spec))
            val occupiedSize = DashboardWidgetSize(spec.size.columns, occupiedHeight(spec))
            val destination = nearestFree(occupied, occupiedSize, preferredColumn, preferredRow, profile.columns)
                ?: return@forEach
            mark(occupied, destination.first, destination.second, occupiedSize)
            placed[spec.instanceId] = destination
        }
        return layout.copy(
            version = VERSION,
            autoFill = false,
            widgets = layout.widgets.mapNotNull { spec ->
                placed[spec.instanceId]?.let { (column, row) -> spec.copy(column = column, row = row) }
            }
        )
    }

    private fun nearestFree(
        occupied: Array<BooleanArray>,
        size: DashboardWidgetSize,
        preferredColumn: Int,
        preferredRow: Int,
        columns: Int
    ): Pair<Int, Int>? {
        val candidates = ArrayList<Pair<Int, Int>>()
        for (row in 0..(MAX_VERTICAL_STEPS - size.rows)) {
            for (column in 0..(columns - size.columns)) candidates += column to row
        }
        return candidates.sortedWith(
            compareBy<Pair<Int, Int>> { abs(it.first - preferredColumn) + abs(it.second - preferredRow) }
                .thenBy { it.second }.thenBy { it.first }
        ).firstOrNull { (column, row) -> isFree(occupied, column, row, size) }
    }

    private fun firstFit(widgets: List<DashboardWidgetSpec>, profile: DashboardProfile): List<DashboardPlacement> {
        val occupied = ArrayList<BooleanArray>()
        fun ensureRows(count: Int) { while (occupied.size < count) occupied += BooleanArray(profile.columns) }
        val placements = ArrayList<DashboardPlacement>()
        widgets.forEachIndexed { index, spec ->
            val width = spec.size.columns.coerceIn(1, profile.columns)
            val height = occupiedHeight(spec)
            var row = 0
            var placed = false
            while (!placed && row < MAX_VERTICAL_STEPS) {
                ensureRows(row + height)
                for (column in 0..(profile.columns - width)) {
                    var free = true
                    loop@ for (y in row until row + height) for (x in column until column + width) {
                        if (occupied[y][x]) { free = false; break@loop }
                    }
                    if (free) {
                        for (y in row until row + height) for (x in column until column + width) occupied[y][x] = true
                        placements += DashboardPlacement(spec, index, column, row)
                        placed = true
                        break
                    }
                }
                if (!placed) row++
            }
        }
        return placements
    }

    private fun isFree(occupied: Array<BooleanArray>, column: Int, row: Int, size: DashboardWidgetSize): Boolean {
        for (y in row until row + size.rows) for (x in column until column + size.columns) {
            if (y !in occupied.indices || x !in occupied[y].indices || occupied[y][x]) return false
        }
        return true
    }

    private fun mark(occupied: Array<BooleanArray>, column: Int, row: Int, size: DashboardWidgetSize) {
        for (y in row until row + size.rows) for (x in column until column + size.columns) occupied[y][x] = true
    }

    private fun nearestAllowed(size: DashboardWidgetSize, allowed: List<DashboardWidgetSize>): DashboardWidgetSize =
        allowed.minByOrNull { abs(it.columns - size.columns) + abs(it.rows - size.rows) } ?: oneByOne

    private fun normalizeHeight(type: DashboardWidgetType, raw: Int): Int =
        raw.coerceIn(heightRange(type).first, heightRange(type).last)

    private fun occupiedHeight(spec: DashboardWidgetSpec): Int =
        normalizeHeight(spec.type, spec.heightSteps) + GAP_STEPS

    private data class TrendConfig(
        val metric: TrendMetric,
        val range: TrendRange,
        val visualStyle: TrendVisualStyle,
    )

    private fun legacyTrendConfig(style: String): TrendConfig = when (style) {
        DashboardStyles.TREND_30_DAY -> TrendConfig(TrendMetric.PLAY_TIME, TrendRange.DAYS_30, TrendVisualStyle.AREA)
        DashboardStyles.TREND_LAUNCH_7_DAY -> TrendConfig(TrendMetric.GAME_OPENS, TrendRange.DAYS_7, TrendVisualStyle.LINE)
        DashboardStyles.TREND_LAUNCH_30_DAY -> TrendConfig(TrendMetric.GAME_OPENS, TrendRange.DAYS_30, TrendVisualStyle.AREA)
        else -> TrendConfig(TrendMetric.PLAY_TIME, TrendRange.DAYS_7, TrendVisualStyle.LINE)
    }

    private fun legacyHeightSteps(rows: Int): Int = when (rows.coerceAtLeast(1)) {
        1 -> 17
        2 -> 34
        3 -> 52
        else -> ((132 * rows + 10 * (rows - 1)) / HEIGHT_STEP_DP.toFloat()).toInt().coerceAtLeast(1)
    }

    private fun readSpotlightGameIds(item: JSONObject): List<Long> {
        val array = item.optJSONArray("spotlightGameIds") ?: return emptyList()
        val result = ArrayList<Long>()
        for (index in 0 until array.length()) {
            val id = array.optLong(index)
            if (id > 0L && id !in result) {
                result += id
                if (result.size >= MAX_SPOTLIGHT_PAGES) break
            }
        }
        return result
    }
}
