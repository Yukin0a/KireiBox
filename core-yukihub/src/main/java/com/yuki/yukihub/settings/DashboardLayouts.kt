package com.yuki.yukihub.settings

import java.util.UUID

object DashboardLayouts {
    const val VERSION = 6
    const val MAX_WIDGETS = 12
    const val MAX_SPOTLIGHT_PAGES = 5
    const val HEIGHT_STEP_DP = 8
    const val GAP_STEPS = 1
    const val MAX_VERTICAL_STEPS = 256

    /** Compatibility alias for older tests and callers. */
    const val MAX_ROWS = MAX_VERTICAL_STEPS

    internal val oneByOne = DashboardWidgetSize(1, 1)

    fun default(profile: DashboardProfile): DashboardLayout = DashboardLayoutEngine.default(profile)

    fun heightRange(type: DashboardWidgetType): IntRange =
        when (type) {
            DashboardWidgetType.GAME_SPOTLIGHT -> 17..52
            DashboardWidgetType.LIBRARY_OVERVIEW -> 13..28
            DashboardWidgetType.RECENT_GAMES -> 14..28
            DashboardWidgetType.FAVORITES -> 13..28
            DashboardWidgetType.LIBRARY_SHORTCUT -> 12..24
            DashboardWidgetType.PLAY_TREND -> 15..36
        }

    fun defaultHeightSteps(type: DashboardWidgetType): Int =
        when (type) {
            DashboardWidgetType.GAME_SPOTLIGHT -> 34
            DashboardWidgetType.LIBRARY_OVERVIEW -> 17
            DashboardWidgetType.RECENT_GAMES -> 17
            DashboardWidgetType.FAVORITES -> 17
            DashboardWidgetType.LIBRARY_SHORTCUT -> 15
            DashboardWidgetType.PLAY_TREND -> 17
        }

    fun heightDp(spec: DashboardWidgetSpec): Int = DashboardLayoutEngine.normalizeHeight(spec.type, spec.heightSteps) * HEIGHT_STEP_DP

    fun allowedWidths(
        type: DashboardWidgetType,
        profile: DashboardProfile,
    ): List<Int> = allowedSizes(type, profile).map { it.columns }.distinct()

    fun allowedSizes(
        type: DashboardWidgetType,
        profile: DashboardProfile,
    ): List<DashboardWidgetSize> =
        when (type) {
            DashboardWidgetType.GAME_SPOTLIGHT ->
                if (profile == DashboardProfile.COMPACT) {
                    listOf(DashboardWidgetSize(1, 2), DashboardWidgetSize(2, 1), DashboardWidgetSize(2, 2), DashboardWidgetSize(2, 3))
                } else {
                    listOf(
                        DashboardWidgetSize(1, 2),
                        DashboardWidgetSize(2, 1),
                        DashboardWidgetSize(2, 2),
                        DashboardWidgetSize(3, 2),
                        DashboardWidgetSize(4, 2),
                        DashboardWidgetSize(4, 3),
                    )
                }
            DashboardWidgetType.LIBRARY_OVERVIEW,
            DashboardWidgetType.FAVORITES,
            -> listOf(oneByOne, DashboardWidgetSize(2, 1))
            DashboardWidgetType.RECENT_GAMES ->
                if (profile == DashboardProfile.COMPACT) {
                    listOf(oneByOne, DashboardWidgetSize(2, 1))
                } else {
                    listOf(DashboardWidgetSize(2, 1), DashboardWidgetSize(4, 1))
                }
            DashboardWidgetType.LIBRARY_SHORTCUT -> listOf(oneByOne)
            DashboardWidgetType.PLAY_TREND ->
                if (profile == DashboardProfile.COMPACT) {
                    listOf(DashboardWidgetSize(1, 1), DashboardWidgetSize(2, 1))
                } else {
                    listOf(
                        DashboardWidgetSize(1, 1),
                        DashboardWidgetSize(2, 1),
                        DashboardWidgetSize(3, 1),
                        DashboardWidgetSize(4, 1),
                    )
                }
        }

    fun normalize(
        layout: DashboardLayout,
        profile: DashboardProfile,
    ): DashboardLayout {
        val base = DashboardLayoutEngine.normalizeBase(layout, profile)
        return if (base.autoFill) base else DashboardLayoutEngine.resolveManual(base, profile)
    }

    fun nextSize(
        spec: DashboardWidgetSpec,
        profile: DashboardProfile,
    ): DashboardWidgetSize {
        val allowed = allowedSizes(spec.type, profile)
        val index = allowed.indexOf(spec.size)
        return allowed[(if (index < 0) 0 else index + 1) % allowed.size]
    }

    fun resize(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        size: DashboardWidgetSize,
    ): DashboardLayout {
        val updated =
            DashboardLayoutEngine.normalizeBase(
                layout.copy(
                    widgets =
                        layout.widgets.map {
                            if (it.instanceId == instanceId) {
                                it.copy(size = size, heightSteps = DashboardLayoutEngine.legacyHeightSteps(size.rows))
                            } else {
                                it
                            }
                        },
                ),
                profile,
            )
        return if (updated.autoFill) updated else DashboardLayoutEngine.resolveManual(updated, profile, instanceId)
    }

    fun resizeWidth(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        columns: Int,
    ): DashboardLayout {
        val updated =
            DashboardLayoutEngine.normalizeBase(
                layout.copy(
                    widgets =
                        layout.widgets.map {
                            if (it.instanceId == instanceId) it.copy(size = it.size.copy(columns = columns)) else it
                        },
                ),
                profile,
            )
        return if (updated.autoFill) updated else DashboardLayoutEngine.resolveManual(updated, profile, instanceId)
    }

    fun resizeHeight(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        heightSteps: Int,
    ): DashboardLayout {
        val updated =
            DashboardLayoutEngine.normalizeBase(
                layout.copy(
                    widgets =
                        layout.widgets.map {
                            if (it.instanceId == instanceId) {
                                it.copy(heightSteps = heightSteps.coerceIn(heightRange(it.type)))
                            } else {
                                it
                            }
                        },
                ),
                profile,
            )
        return if (updated.autoFill) updated else DashboardLayoutEngine.resolveManual(updated, profile, instanceId)
    }

    fun setStyle(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        styleKey: String,
    ): DashboardLayout =
        normalize(
            layout.copy(
                widgets =
                    layout.widgets.map {
                        if (it.instanceId == instanceId) it.copy(styleKey = DashboardStyles.normalize(it.type, styleKey)) else it
                    },
            ),
            profile,
        )

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
    ): DashboardLayout =
        normalize(
            layout.copy(
                widgets =
                    layout.widgets.map { spec ->
                        if (spec.instanceId != instanceId || spec.type != DashboardWidgetType.GAME_SPOTLIGHT) {
                            spec
                        } else {
                            spec.copy(
                                gameId = null,
                                spotlightSource = source,
                                spotlightStrategy = strategy,
                                spotlightSelectionMode = SpotlightSelectionMode.AUTOMATIC,
                                spotlightGameIds = emptyList(),
                                spotlightMaxPages = maxPages.coerceIn(1, MAX_SPOTLIGHT_PAGES),
                            )
                        }
                    },
            ),
            profile,
        )

    /** Replaces the spotlight with an explicit ordered manual list (1-5 games). */
    fun setSpotlightManual(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        gameIds: List<Long>,
        maxPages: Int = MAX_SPOTLIGHT_PAGES,
    ): DashboardLayout =
        normalize(
            layout.copy(
                widgets =
                    layout.widgets.map { spec ->
                        if (spec.instanceId != instanceId || spec.type != DashboardWidgetType.GAME_SPOTLIGHT) {
                            spec
                        } else {
                            spec.copy(
                                gameId = null,
                                spotlightSource = SpotlightSource.FIXED,
                                spotlightStrategy = SpotlightStrategy.FIRST,
                                spotlightSelectionMode = SpotlightSelectionMode.MANUAL,
                                spotlightGameIds = gameIds,
                                spotlightMaxPages = maxPages.coerceIn(1, MAX_SPOTLIGHT_PAGES),
                            )
                        }
                    },
            ),
            profile,
        )

    fun setTrendConfig(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        metric: TrendMetric,
        range: TrendRange,
        visualStyle: TrendVisualStyle,
    ): DashboardLayout =
        normalize(
            layout.copy(
                widgets =
                    layout.widgets.map { spec ->
                        if (spec.instanceId != instanceId || spec.type != DashboardWidgetType.PLAY_TREND) {
                            spec
                        } else {
                            spec.copy(
                                trendMetric = metric,
                                trendRange = range,
                                trendVisualStyle = visualStyle,
                            )
                        }
                    },
            ),
            profile,
        )

    fun move(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        targetIndex: Int,
    ): DashboardLayout {
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
        row: Int,
    ): DashboardLayout {
        val frozen = DashboardLayoutEngine.freezePositions(layout, profile)
        val manual =
            frozen.copy(
                widgets =
                    frozen.widgets.map {
                        if (it.instanceId == instanceId) it.copy(column = column, row = row) else it
                    },
                autoFill = false,
            )
        return DashboardLayoutEngine.resolveManual(DashboardLayoutEngine.normalizeBase(manual, profile), profile, instanceId)
    }

    fun setAutoFill(
        layout: DashboardLayout,
        profile: DashboardProfile,
        enabled: Boolean,
    ): DashboardLayout {
        if (enabled) return DashboardLayoutEngine.normalizeBase(layout.copy(autoFill = true), profile)
        return DashboardLayoutEngine.freezePositions(layout, profile).copy(autoFill = false)
    }

    fun remove(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
    ): DashboardLayout = normalize(layout.copy(widgets = layout.widgets.filterNot { it.instanceId == instanceId }), profile)

    fun add(
        layout: DashboardLayout,
        profile: DashboardProfile,
        type: DashboardWidgetType,
        gameId: Long? = null,
    ): DashboardLayout {
        val spec =
            DashboardWidgetSpec(
                instanceId = "${type.name.lowercase()}-${UUID.randomUUID()}",
                type = type,
                size = allowedSizes(type, profile).first(),
                gameId = gameId,
                spotlightSource = if (gameId == null) SpotlightSource.RECENT else SpotlightSource.FIXED,
                spotlightSelectionMode =
                    if (gameId == null) {
                        SpotlightSelectionMode.AUTOMATIC
                    } else {
                        SpotlightSelectionMode.MANUAL
                    },
                spotlightGameIds = if (gameId == null) emptyList() else listOfNotNull(gameId),
                styleKey = DashboardStyles.default(type),
                heightSteps = defaultHeightSteps(type),
            )
        return normalize(layout.copy(widgets = layout.widgets + spec), profile)
    }

    fun encode(
        layout: DashboardLayout,
        profile: DashboardProfile,
    ): String = DashboardLayoutCodec.encode(layout, profile)

    fun decode(
        raw: String?,
        profile: DashboardProfile,
    ): DashboardLayout = DashboardLayoutCodec.decode(raw, profile)

    fun pack(
        layout: DashboardLayout,
        profile: DashboardProfile,
    ): List<DashboardPlacement> = DashboardLayoutEngine.pack(layout, profile)
}
