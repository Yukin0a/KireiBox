package com.yuki.yukihub.settings

import java.util.UUID
import kotlin.math.abs

/**
 * Normalization and grid-placement engine behind [DashboardLayouts]. Split out of the public
 * facade (planbook A8) so each file stays within the source-size budget; behaviour is unchanged.
 */
internal object DashboardLayoutEngine {
    fun default(profile: DashboardProfile): DashboardLayout =
        when (profile) {
            DashboardProfile.COMPACT ->
                DashboardLayout(
                    widgets =
                        listOf(
                            DashboardWidgetSpec(
                                "default-game-compact",
                                DashboardWidgetType.GAME_SPOTLIGHT,
                                DashboardWidgetSize(2, 2),
                                heightSteps = DashboardLayouts.defaultHeightSteps(DashboardWidgetType.GAME_SPOTLIGHT),
                            ),
                            DashboardWidgetSpec(
                                "default-overview-compact",
                                DashboardWidgetType.LIBRARY_OVERVIEW,
                                DashboardWidgetSize(2, 1),
                                styleKey = DashboardStyles.OVERVIEW_SPLIT,
                                heightSteps = DashboardLayouts.defaultHeightSteps(DashboardWidgetType.LIBRARY_OVERVIEW),
                            ),
                            DashboardWidgetSpec(
                                "default-recent-compact",
                                DashboardWidgetType.RECENT_GAMES,
                                DashboardWidgetSize(2, 1),
                                heightSteps = DashboardLayouts.defaultHeightSteps(DashboardWidgetType.RECENT_GAMES),
                            ),
                        ),
                )
            DashboardProfile.WIDE ->
                DashboardLayout(
                    widgets =
                        listOf(
                            DashboardWidgetSpec(
                                "default-game-wide",
                                DashboardWidgetType.GAME_SPOTLIGHT,
                                DashboardWidgetSize(2, 2),
                                heightSteps = DashboardLayouts.defaultHeightSteps(DashboardWidgetType.GAME_SPOTLIGHT),
                            ),
                            DashboardWidgetSpec(
                                "default-overview-wide",
                                DashboardWidgetType.LIBRARY_OVERVIEW,
                                DashboardWidgetSize(2, 1),
                                styleKey = DashboardStyles.OVERVIEW_SPLIT,
                                heightSteps = DashboardLayouts.defaultHeightSteps(DashboardWidgetType.LIBRARY_OVERVIEW),
                            ),
                            DashboardWidgetSpec(
                                "default-recent-wide",
                                DashboardWidgetType.RECENT_GAMES,
                                DashboardWidgetSize(2, 1),
                                heightSteps = DashboardLayouts.defaultHeightSteps(DashboardWidgetType.RECENT_GAMES),
                            ),
                        ),
                )
        }

    fun pack(
        layout: DashboardLayout,
        profile: DashboardProfile,
    ): List<DashboardPlacement> {
        val clean = DashboardLayouts.normalize(layout, profile)
        if (!clean.autoFill) {
            return clean.widgets.mapIndexed { index, spec ->
                DashboardPlacement(spec, index, spec.column, spec.row)
            }
        }
        return firstFit(clean.widgets, profile)
    }

    fun normalizeBase(
        layout: DashboardLayout,
        profile: DashboardProfile,
    ): DashboardLayout {
        val seenIds = HashSet<String>()
        val seenUnique = HashSet<DashboardWidgetType>()
        val seenGames = HashSet<Long>()
        var automaticGameSeen = false
        val normalized = ArrayList<DashboardWidgetSpec>()
        layout.widgets.take(DashboardLayouts.MAX_WIDGETS * 2).forEachIndexed { index, raw ->
            if (normalized.size >= DashboardLayouts.MAX_WIDGETS) return@forEachIndexed
            if (raw.type.unique && !seenUnique.add(raw.type)) return@forEachIndexed
            val selectionMode =
                when {
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
            val id =
                raw.instanceId.takeIf { it.isNotBlank() && seenIds.add(it) }
                    ?: "${raw.type.name.lowercase()}-$index-${UUID.randomUUID()}"
            seenIds.add(id)
            val allowed = DashboardLayouts.allowedSizes(raw.type, profile)
            val columns =
                DashboardLayouts.allowedWidths(raw.type, profile)
                    .minByOrNull { abs(it - raw.size.columns) } ?: 1
            val heightSteps =
                normalizeHeight(
                    raw.type,
                    if (raw.heightSteps > 0) raw.heightSteps else legacyHeightSteps(raw.size.rows),
                )
            val size = nearestAllowed(raw.size.copy(columns = columns), allowed)
            val spotlightSource =
                when {
                    raw.type != DashboardWidgetType.GAME_SPOTLIGHT -> raw.spotlightSource
                    selectionMode == SpotlightSelectionMode.MANUAL -> SpotlightSource.FIXED
                    raw.spotlightSource == SpotlightSource.FIXED -> SpotlightSource.RECENT
                    else -> raw.spotlightSource
                }
            normalized +=
                raw.copy(
                    instanceId = id,
                    size = size,
                    gameId =
                        raw.gameId.takeIf {
                            raw.type != DashboardWidgetType.GAME_SPOTLIGHT || selectionMode != SpotlightSelectionMode.MANUAL
                        },
                    spotlightSource = spotlightSource,
                    spotlightSelectionMode = selectionMode,
                    spotlightGameIds =
                        if (raw.type == DashboardWidgetType.GAME_SPOTLIGHT &&
                            selectionMode == SpotlightSelectionMode.MANUAL
                        ) {
                            val seed = raw.spotlightGameIds.ifEmpty { listOfNotNull(raw.gameId) }
                            seed.filter { it > 0L }.distinct().take(DashboardLayouts.MAX_SPOTLIGHT_PAGES)
                        } else {
                            emptyList()
                        },
                    spotlightMaxPages = raw.spotlightMaxPages.coerceIn(1, DashboardLayouts.MAX_SPOTLIGHT_PAGES),
                    heightSteps = heightSteps,
                    styleKey = DashboardStyles.normalize(raw.type, raw.styleKey),
                    column = raw.column.coerceIn(DashboardWidgetSpec.UNPLACED, profile.columns - size.columns),
                    row = raw.row.coerceIn(DashboardWidgetSpec.UNPLACED, DashboardLayouts.MAX_VERTICAL_STEPS - occupiedHeight(raw.copy(heightSteps = heightSteps))),
                )
        }
        return DashboardLayout(DashboardLayouts.VERSION, normalized, layout.autoFill)
    }

    fun freezePositions(
        layout: DashboardLayout,
        profile: DashboardProfile,
    ): DashboardLayout {
        val base = normalizeBase(layout, profile)
        val placements = if (base.autoFill) firstFit(base.widgets, profile) else pack(base, profile)
        val byId = placements.associateBy { it.spec.instanceId }
        return base.copy(
            autoFill = false,
            widgets =
                base.widgets.map { spec ->
                    val placement = byId[spec.instanceId]
                    spec.copy(column = placement?.column ?: 0, row = placement?.row ?: 0)
                },
        )
    }

    fun resolveManual(
        layout: DashboardLayout,
        profile: DashboardProfile,
        priorityId: String? = null,
    ): DashboardLayout {
        val occupied = Array(DashboardLayouts.MAX_ROWS) { BooleanArray(profile.columns) }
        val placed = HashMap<String, Pair<Int, Int>>()
        val ordered = layout.widgets.sortedBy { if (it.instanceId == priorityId) 0 else 1 }
        ordered.forEach { spec ->
            val preferredColumn = spec.column.coerceAtLeast(0).coerceAtMost(profile.columns - spec.size.columns)
            val preferredRow = spec.row.coerceAtLeast(0).coerceAtMost(DashboardLayouts.MAX_VERTICAL_STEPS - occupiedHeight(spec))
            val occupiedSize = DashboardWidgetSize(spec.size.columns, occupiedHeight(spec))
            val destination =
                nearestFree(occupied, occupiedSize, preferredColumn, preferredRow, profile.columns)
                    ?: return@forEach
            mark(occupied, destination.first, destination.second, occupiedSize)
            placed[spec.instanceId] = destination
        }
        return layout.copy(
            version = DashboardLayouts.VERSION,
            autoFill = false,
            widgets =
                layout.widgets.mapNotNull { spec ->
                    placed[spec.instanceId]?.let { (column, row) -> spec.copy(column = column, row = row) }
                },
        )
    }

    fun nearestFree(
        occupied: Array<BooleanArray>,
        size: DashboardWidgetSize,
        preferredColumn: Int,
        preferredRow: Int,
        columns: Int,
    ): Pair<Int, Int>? {
        val candidates = ArrayList<Pair<Int, Int>>()
        for (row in 0..(DashboardLayouts.MAX_VERTICAL_STEPS - size.rows)) {
            for (column in 0..(columns - size.columns)) candidates += column to row
        }
        return candidates.sortedWith(
            compareBy<Pair<Int, Int>> { abs(it.first - preferredColumn) + abs(it.second - preferredRow) }
                .thenBy { it.second }.thenBy { it.first },
        ).firstOrNull { (column, row) -> isFree(occupied, column, row, size) }
    }

    fun firstFit(
        widgets: List<DashboardWidgetSpec>,
        profile: DashboardProfile,
    ): List<DashboardPlacement> {
        val occupied = ArrayList<BooleanArray>()

        fun ensureRows(count: Int) {
            while (occupied.size < count) occupied += BooleanArray(profile.columns)
        }
        val placements = ArrayList<DashboardPlacement>()
        widgets.forEachIndexed { index, spec ->
            val width = spec.size.columns.coerceIn(1, profile.columns)
            val height = occupiedHeight(spec)
            var row = 0
            var placed = false
            while (!placed && row < DashboardLayouts.MAX_VERTICAL_STEPS) {
                ensureRows(row + height)
                for (column in 0..(profile.columns - width)) {
                    var free = true
                    loop@ for (y in row until row + height) for (x in column until column + width) {
                        if (occupied[y][x]) {
                            free = false
                            break@loop
                        }
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

    fun isFree(
        occupied: Array<BooleanArray>,
        column: Int,
        row: Int,
        size: DashboardWidgetSize,
    ): Boolean {
        for (y in row until row + size.rows) for (x in column until column + size.columns) {
            if (y !in occupied.indices || x !in occupied[y].indices || occupied[y][x]) return false
        }
        return true
    }

    fun mark(
        occupied: Array<BooleanArray>,
        column: Int,
        row: Int,
        size: DashboardWidgetSize,
    ) {
        for (y in row until row + size.rows) for (x in column until column + size.columns) occupied[y][x] = true
    }

    fun nearestAllowed(
        size: DashboardWidgetSize,
        allowed: List<DashboardWidgetSize>,
    ): DashboardWidgetSize = allowed.minByOrNull { abs(it.columns - size.columns) + abs(it.rows - size.rows) } ?: DashboardLayouts.oneByOne

    fun normalizeHeight(
        type: DashboardWidgetType,
        raw: Int,
    ): Int = raw.coerceIn(DashboardLayouts.heightRange(type).first, DashboardLayouts.heightRange(type).last)

    fun occupiedHeight(spec: DashboardWidgetSpec): Int = normalizeHeight(spec.type, spec.heightSteps) + DashboardLayouts.GAP_STEPS

    fun legacyHeightSteps(rows: Int): Int =
        when (rows.coerceAtLeast(1)) {
            1 -> 17
            2 -> 34
            3 -> 52
            else -> ((132 * rows + 10 * (rows - 1)) / DashboardLayouts.HEIGHT_STEP_DP.toFloat()).toInt().coerceAtLeast(1)
        }
}
