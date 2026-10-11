package com.yuki.yukihub.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence codec for [DashboardLayouts]. Split out of the public facade (planbook A8) so each
 * file stays within the source-size budget; [DashboardLayouts.encode]/[DashboardLayouts.decode]
 * forward here and behaviour is unchanged.
 */
internal object DashboardLayoutCodec {
    fun encode(
        layout: DashboardLayout,
        profile: DashboardProfile,
    ): String {
        val clean = DashboardLayouts.normalize(layout, profile)
        val root = JSONObject().put("version", DashboardLayouts.VERSION).put("autoFill", clean.autoFill)
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
                    .apply { spec.gameId?.let { put("gameId", it) } },
            )
        }
        return root.put("widgets", widgets).toString()
    }

    fun decode(
        raw: String?,
        profile: DashboardProfile,
    ): DashboardLayout {
        if (raw.isNullOrBlank()) return DashboardLayouts.default(profile)
        return runCatching {
            val root = JSONObject(raw)
            val storedVersion = root.optInt("version", 1)
            if (storedVersion > DashboardLayouts.VERSION) return@runCatching DashboardLayouts.default(profile)
            val array = root.optJSONArray("widgets") ?: return@runCatching DashboardLayouts.default(profile)
            val widgets = ArrayList<DashboardWidgetSpec>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val type =
                    DashboardWidgetType.entries.firstOrNull {
                        it.name.equals(item.optString("type"), ignoreCase = true)
                    } ?: continue
                val gameId = item.optLong("gameId").takeIf { item.has("gameId") && it > 0L }
                val legacyTrend = legacyTrendConfig(item.optString("style"))
                val legacyFixed = gameId != null
                val selectionMode =
                    if (storedVersion >= 6) {
                        SpotlightSelectionMode.entries.firstOrNull {
                            it.name.equals(item.optString("spotlightSelectionMode"), ignoreCase = true)
                        } ?: SpotlightSelectionMode.AUTOMATIC
                    } else if (legacyFixed) {
                        SpotlightSelectionMode.MANUAL
                    } else {
                        SpotlightSelectionMode.AUTOMATIC
                    }
                widgets +=
                    DashboardWidgetSpec(
                        instanceId = item.optString("id"),
                        type = type,
                        size = DashboardWidgetSize(item.optInt("columns", 1), item.optInt("rows", 1)),
                        gameId = gameId,
                        spotlightSource =
                            if (legacyFixed) {
                                SpotlightSource.FIXED
                            } else if (storedVersion >= 4) {
                                SpotlightSource.entries.firstOrNull {
                                    it.name.equals(item.optString("spotlightSource"), ignoreCase = true)
                                } ?: SpotlightSource.RECENT
                            } else {
                                SpotlightSource.RECENT
                            },
                        spotlightStrategy =
                            if (storedVersion >= 4) {
                                SpotlightStrategy.entries.firstOrNull {
                                    it.name.equals(item.optString("spotlightStrategy"), ignoreCase = true)
                                } ?: SpotlightStrategy.FIRST
                            } else {
                                SpotlightStrategy.FIRST
                            },
                        spotlightSelectionMode = selectionMode,
                        spotlightGameIds =
                            if (storedVersion >= 6) {
                                readSpotlightGameIds(item)
                            } else if (legacyFixed) {
                                listOf(gameId)
                            } else {
                                emptyList()
                            },
                        spotlightMaxPages =
                            if (storedVersion >= 6) {
                                item.optInt("spotlightMaxPages", DashboardLayouts.MAX_SPOTLIGHT_PAGES)
                                    .coerceIn(1, DashboardLayouts.MAX_SPOTLIGHT_PAGES)
                            } else {
                                DashboardLayouts.MAX_SPOTLIGHT_PAGES
                            },
                        styleKey = if (storedVersion >= 2) item.optString("style") else DashboardStyles.default(type),
                        column = if (storedVersion >= 2) item.optInt("column", DashboardWidgetSpec.UNPLACED) else DashboardWidgetSpec.UNPLACED,
                        row =
                            if (storedVersion >= 3) {
                                item.optInt("row", DashboardWidgetSpec.UNPLACED)
                            } else if (storedVersion >= 2) {
                                item.optInt("row", DashboardWidgetSpec.UNPLACED)
                                    .takeIf { it >= 0 }?.times(18) ?: DashboardWidgetSpec.UNPLACED
                            } else {
                                DashboardWidgetSpec.UNPLACED
                            },
                        heightSteps =
                            if (storedVersion >= 3) {
                                item.optInt("heightSteps", DashboardLayouts.defaultHeightSteps(type))
                            } else {
                                DashboardLayoutEngine.legacyHeightSteps(item.optInt("rows", 1))
                            },
                        trendMetric =
                            if (type != DashboardWidgetType.PLAY_TREND) {
                                TrendMetric.PLAY_TIME
                            } else if (storedVersion >= 5) {
                                TrendMetric.fromStored(item.optString("trendMetric"))
                            } else {
                                legacyTrend.metric
                            },
                        trendRange =
                            if (type != DashboardWidgetType.PLAY_TREND) {
                                TrendRange.DAYS_7
                            } else if (storedVersion >= 5) {
                                TrendRange.fromStored(item.optString("trendRange"))
                            } else {
                                legacyTrend.range
                            },
                        trendVisualStyle =
                            if (type != DashboardWidgetType.PLAY_TREND) {
                                TrendVisualStyle.LINE
                            } else if (storedVersion >= 5) {
                                TrendVisualStyle.fromStored(item.optString("trendVisualStyle"))
                            } else {
                                legacyTrend.visualStyle
                            },
                    )
            }
            DashboardLayouts.normalize(
                DashboardLayout(
                    version = DashboardLayouts.VERSION,
                    widgets = widgets,
                    autoFill = if (storedVersion >= 2) root.optBoolean("autoFill", true) else true,
                ),
                profile,
            )
        }.getOrElse { DashboardLayouts.default(profile) }
    }

    private data class TrendConfig(
        val metric: TrendMetric,
        val range: TrendRange,
        val visualStyle: TrendVisualStyle,
    )

    private fun legacyTrendConfig(style: String): TrendConfig =
        when (style) {
            DashboardStyles.TREND_30_DAY -> TrendConfig(TrendMetric.PLAY_TIME, TrendRange.DAYS_30, TrendVisualStyle.AREA)
            DashboardStyles.TREND_LAUNCH_7_DAY -> TrendConfig(TrendMetric.GAME_OPENS, TrendRange.DAYS_7, TrendVisualStyle.LINE)
            DashboardStyles.TREND_LAUNCH_30_DAY -> TrendConfig(TrendMetric.GAME_OPENS, TrendRange.DAYS_30, TrendVisualStyle.AREA)
            else -> TrendConfig(TrendMetric.PLAY_TIME, TrendRange.DAYS_7, TrendVisualStyle.LINE)
        }

    private fun readSpotlightGameIds(item: JSONObject): List<Long> {
        val array = item.optJSONArray("spotlightGameIds") ?: return emptyList()
        val result = ArrayList<Long>()
        for (index in 0 until array.length()) {
            val id = array.optLong(index)
            if (id > 0L && id !in result) {
                result += id
                if (result.size >= DashboardLayouts.MAX_SPOTLIGHT_PAGES) break
            }
        }
        return result
    }
}
