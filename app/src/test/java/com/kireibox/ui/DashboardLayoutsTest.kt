package com.kireibox.ui

import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.DashboardWidgetSize
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.DashboardStyles
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import com.yuki.yukihub.settings.TrendMetric
import com.yuki.yukihub.settings.TrendRange
import com.yuki.yukihub.settings.TrendVisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutsTest {
    @Test fun jsonRoundTripPreservesNormalizedCompactLayout() {
        val original = DashboardLayout(widgets = listOf(
            DashboardWidgetSpec("game-7", DashboardWidgetType.GAME_SPOTLIGHT, DashboardWidgetSize(1, 2), 7),
            DashboardWidgetSpec("overview", DashboardWidgetType.LIBRARY_OVERVIEW, DashboardWidgetSize(2, 1))
        ))
        assertEquals(
            DashboardLayouts.normalize(original, DashboardProfile.COMPACT),
            DashboardLayouts.decode(DashboardLayouts.encode(original, DashboardProfile.COMPACT), DashboardProfile.COMPACT)
        )
    }

    @Test fun addingWidgetKeepsExistingLegalCompactSizes() {
        val original = DashboardLayouts.default(DashboardProfile.COMPACT)
        val added = DashboardLayouts.add(
            original,
            DashboardProfile.COMPACT,
            DashboardWidgetType.FAVORITES
        )
        assertEquals(original.widgets.map { it.size }, added.widgets.take(original.widgets.size).map { it.size })
        assertEquals(DashboardWidgetSize(2, 1), added.widgets[1].size)
        assertEquals(DashboardWidgetSize(2, 1), added.widgets[2].size)
    }

    @Test fun corruptJsonFallsBackToProfileDefault() {
        assertEquals(DashboardLayouts.default(DashboardProfile.WIDE), DashboardLayouts.decode("{broken", DashboardProfile.WIDE))
    }

    @Test fun normalizationDropsDuplicateUniqueAndGameWidgetsAndCapsCount() {
        val many = buildList {
            add(DashboardWidgetSpec("overview-1", DashboardWidgetType.LIBRARY_OVERVIEW, DashboardWidgetSize(2, 1)))
            add(DashboardWidgetSpec("overview-2", DashboardWidgetType.LIBRARY_OVERVIEW, DashboardWidgetSize(2, 1)))
            add(DashboardWidgetSpec("game-1", DashboardWidgetType.GAME_SPOTLIGHT, DashboardWidgetSize(2, 2), 5))
            add(DashboardWidgetSpec("game-2", DashboardWidgetType.GAME_SPOTLIGHT, DashboardWidgetSize(2, 2), 5))
            repeat(20) { add(DashboardWidgetSpec("library-$it", DashboardWidgetType.LIBRARY_SHORTCUT, DashboardWidgetSize(1, 1))) }
        }
        val normalized = DashboardLayouts.normalize(DashboardLayout(widgets = many), DashboardProfile.COMPACT)
        assertEquals(1, normalized.widgets.count { it.type == DashboardWidgetType.LIBRARY_OVERVIEW })
        assertEquals(
            1,
            normalized.widgets.count {
                it.type == DashboardWidgetType.GAME_SPOTLIGHT &&
                    it.spotlightSelectionMode == SpotlightSelectionMode.MANUAL &&
                    it.spotlightGameIds == listOf(5L)
            },
        )
        assertTrue(normalized.widgets.size <= DashboardLayouts.MAX_WIDGETS)
    }

    @Test fun resizeSnapsToAllowedSizeAndMoveChangesOrder() {
        val layout = DashboardLayouts.default(DashboardProfile.COMPACT)
        val id = layout.widgets.first().instanceId
        val resized = DashboardLayouts.resizeWidth(layout, DashboardProfile.COMPACT, id, 9)
        assertTrue(resized.widgets.first().size in DashboardLayouts.allowedSizes(DashboardWidgetType.GAME_SPOTLIGHT, DashboardProfile.COMPACT))
        val moved = DashboardLayouts.move(resized, DashboardProfile.COMPACT, id, resized.widgets.lastIndex)
        assertNotEquals(id, moved.widgets.first().instanceId)
        assertEquals(id, moved.widgets.last().instanceId)
    }

    @Test fun packingHasNoOverlapForCompactAndWideDefaults() {
        DashboardProfile.entries.forEach { profile ->
            val placements = DashboardLayouts.pack(DashboardLayouts.default(profile), profile)
            val cells = HashSet<Pair<Int, Int>>()
            placements.forEach { placement ->
                for (row in placement.row until placement.row + placement.spec.heightSteps + DashboardLayouts.GAP_STEPS) {
                    for (column in placement.column until placement.column + placement.spec.size.columns) {
                        assertFalse("overlap at $column,$row", !cells.add(column to row))
                        assertTrue(column < profile.columns)
                    }
                }
            }
        }
    }

    @Test fun versionOneJsonMigratesToAutoFillVersionThree() {
        val raw = """{"version":1,"widgets":[{"id":"recent","type":"RECENT_GAMES","columns":2,"rows":1}]}"""
        val migrated = DashboardLayouts.decode(raw, DashboardProfile.COMPACT)
        assertEquals(DashboardLayouts.VERSION, migrated.version)
        assertTrue(migrated.autoFill)
        assertEquals(DashboardStyles.RECENT_STRIP, migrated.widgets.single().styleKey)
        assertEquals(17, migrated.widgets.single().heightSteps)
    }

    @Test fun versionTwoRowsAndManualPositionMigrateToEightDpGrid() {
        val raw = """{"version":2,"autoFill":false,"widgets":[{"id":"recent","type":"RECENT_GAMES","columns":2,"rows":1,"column":0,"row":3}]}"""
        val migrated = DashboardLayouts.decode(raw, DashboardProfile.COMPACT)
        val widget = migrated.widgets.single()
        assertEquals(17, widget.heightSteps)
        assertEquals(54, widget.row)
    }

    @Test fun everyWidgetHeightClampsToItsDocumentedRange() {
        DashboardWidgetType.entries.forEach { type ->
            var layout = DashboardLayout(widgets = listOf(
                DashboardWidgetSpec(type.name, type, DashboardLayouts.allowedSizes(type, DashboardProfile.COMPACT).first())
            ))
            layout = DashboardLayouts.resizeHeight(layout, DashboardProfile.COMPACT, type.name, -20)
            assertEquals(DashboardLayouts.heightRange(type).first, layout.widgets.single().heightSteps)
            layout = DashboardLayouts.resizeHeight(layout, DashboardProfile.COMPACT, type.name, 999)
            assertEquals(DashboardLayouts.heightRange(type).last, layout.widgets.single().heightSteps)
            assertEquals(layout.widgets.single().heightSteps * 8, DashboardLayouts.heightDp(layout.widgets.single()))
        }
    }

    @Test fun manualPlacementPreservesHolesAfterRemoval() {
        val initial = DashboardLayouts.setAutoFill(
            DashboardLayouts.default(DashboardProfile.COMPACT), DashboardProfile.COMPACT, false
        )
        val overview = initial.widgets.first { it.type == DashboardWidgetType.LIBRARY_OVERVIEW }
        val moved = DashboardLayouts.placeAt(initial, DashboardProfile.COMPACT, overview.instanceId, 0, 6)
        val withoutGame = DashboardLayouts.remove(
            moved, DashboardProfile.COMPACT,
            moved.widgets.first { it.type == DashboardWidgetType.GAME_SPOTLIGHT }.instanceId
        )
        assertFalse(withoutGame.autoFill)
        assertEquals(6, withoutGame.widgets.first { it.instanceId == overview.instanceId }.row)
    }

    @Test fun draggedWidgetWinsManualCollisionAndLayoutRemainsNonOverlapping() {
        var layout = DashboardLayouts.setAutoFill(
            DashboardLayouts.default(DashboardProfile.COMPACT), DashboardProfile.COMPACT, false
        )
        val recent = layout.widgets.first { it.type == DashboardWidgetType.RECENT_GAMES }
        layout = DashboardLayouts.placeAt(layout, DashboardProfile.COMPACT, recent.instanceId, 0, 0)
        val placements = DashboardLayouts.pack(layout, DashboardProfile.COMPACT)
        assertEquals(0, placements.first { it.spec.instanceId == recent.instanceId }.row)
        val cells = HashSet<Pair<Int, Int>>()
        placements.forEach { placement ->
            repeat(placement.spec.heightSteps + DashboardLayouts.GAP_STEPS) { dy -> repeat(placement.spec.size.columns) { dx ->
                assertTrue(cells.add((placement.column + dx) to (placement.row + dy)))
            } }
        }
    }

    @Test fun multipleTrendInstancesRoundTripWithIndependentConfigurations() {
        val original = DashboardLayout(widgets = listOf(
            trend("time", TrendMetric.PLAY_TIME, TrendRange.DAYS_7, TrendVisualStyle.LINE),
            trend("opens", TrendMetric.GAME_OPENS, TrendRange.DAYS_30, TrendVisualStyle.AREA),
        ))

        val restored = DashboardLayouts.decode(
            DashboardLayouts.encode(original, DashboardProfile.COMPACT),
            DashboardProfile.COMPACT,
        )

        assertEquals(2, restored.widgets.count { it.type == DashboardWidgetType.PLAY_TREND })
        assertEquals(TrendMetric.PLAY_TIME, restored.widgets.first { it.instanceId == "time" }.trendMetric)
        assertEquals(TrendRange.DAYS_30, restored.widgets.first { it.instanceId == "opens" }.trendRange)
        assertEquals(TrendVisualStyle.AREA, restored.widgets.first { it.instanceId == "opens" }.trendVisualStyle)
    }

    @Test fun versionFourTrendStylesMigrateToTheirV5Configurations() {
        val cases = listOf(
            DashboardStyles.TREND_7_DAY to Triple(TrendMetric.PLAY_TIME, TrendRange.DAYS_7, TrendVisualStyle.LINE),
            DashboardStyles.TREND_30_DAY to Triple(TrendMetric.PLAY_TIME, TrendRange.DAYS_30, TrendVisualStyle.AREA),
            DashboardStyles.TREND_LAUNCH_7_DAY to Triple(TrendMetric.GAME_OPENS, TrendRange.DAYS_7, TrendVisualStyle.LINE),
            DashboardStyles.TREND_LAUNCH_30_DAY to Triple(TrendMetric.GAME_OPENS, TrendRange.DAYS_30, TrendVisualStyle.AREA),
        )

        cases.forEachIndexed { index, (style, expected) ->
            val restored = DashboardLayouts.decode(
                """{"version":4,"widgets":[{"id":"trend-$index","type":"PLAY_TREND","columns":2,"rows":1,"heightSteps":17,"style":"$style"}]}""",
                DashboardProfile.WIDE,
            ).widgets.single()

            assertEquals(expected.first, restored.trendMetric)
            assertEquals(expected.second, restored.trendRange)
            assertEquals(expected.third, restored.trendVisualStyle)
        }
    }

    @Test fun unknownV5TrendFieldsFallBackWithoutResettingPlacementHeightAutoFillOrStyle() {
        val restored = DashboardLayouts.decode(
            """{"version":5,"autoFill":false,"widgets":[{"id":"trend","type":"PLAY_TREND","columns":3,"rows":1,"heightSteps":31,"style":"trend_30_day","column":1,"row":9,"trendMetric":"unknown","trendRange":"unknown","trendVisualStyle":"unknown"}]}""",
            DashboardProfile.WIDE,
        )
        val trend = restored.widgets.single()

        assertFalse(restored.autoFill)
        assertEquals(1, trend.column)
        assertEquals(9, trend.row)
        assertEquals(31, trend.heightSteps)
        assertEquals(DashboardStyles.TREND_30_DAY, trend.styleKey)
        assertEquals(TrendMetric.PLAY_TIME, trend.trendMetric)
        assertEquals(TrendRange.DAYS_7, trend.trendRange)
        assertEquals(TrendVisualStyle.LINE, trend.trendVisualStyle)
    }

    @Test fun trendUsesDocumentedWidthsAndV5HeightRange() {
        assertEquals(listOf(1, 2), DashboardLayouts.allowedWidths(DashboardWidgetType.PLAY_TREND, DashboardProfile.COMPACT))
        assertEquals(listOf(1, 2, 3, 4), DashboardLayouts.allowedWidths(DashboardWidgetType.PLAY_TREND, DashboardProfile.WIDE))
        assertEquals(15..36, DashboardLayouts.heightRange(DashboardWidgetType.PLAY_TREND))
    }

    @Test fun setTrendConfigOnlyUpdatesTheTargetTrendInstance() {
        val original = DashboardLayout(widgets = listOf(
            trend("first", TrendMetric.PLAY_TIME, TrendRange.DAYS_7, TrendVisualStyle.LINE),
            trend("second", TrendMetric.PLAY_TIME, TrendRange.DAYS_7, TrendVisualStyle.LINE),
        ))

        val updated = DashboardLayouts.setTrendConfig(
            original,
            DashboardProfile.COMPACT,
            "second",
            TrendMetric.GAME_OPENS,
            TrendRange.DAYS_30,
            TrendVisualStyle.AREA,
        )

        assertEquals(TrendMetric.PLAY_TIME, updated.widgets.first { it.instanceId == "first" }.trendMetric)
        assertEquals(TrendRange.DAYS_7, updated.widgets.first { it.instanceId == "first" }.trendRange)
        assertEquals(TrendMetric.GAME_OPENS, updated.widgets.first { it.instanceId == "second" }.trendMetric)
        assertEquals(TrendRange.DAYS_30, updated.widgets.first { it.instanceId == "second" }.trendRange)
        assertEquals(TrendVisualStyle.AREA, updated.widgets.first { it.instanceId == "second" }.trendVisualStyle)
    }

    @Test fun multipleTrendsPackAndManualPlacementWithoutOverlap() {
        val automatic = DashboardLayout(widgets = listOf(
            trend("first", TrendMetric.PLAY_TIME, TrendRange.DAYS_7, TrendVisualStyle.LINE),
            trend("second", TrendMetric.GAME_OPENS, TrendRange.DAYS_30, TrendVisualStyle.AREA),
        ))
        val manual = DashboardLayouts.placeAt(
            DashboardLayouts.placeAt(automatic, DashboardProfile.COMPACT, "first", 0, 0),
            DashboardProfile.COMPACT,
            "second",
            0,
            0,
        )

        assertEquals(2, DashboardLayouts.pack(automatic, DashboardProfile.COMPACT).size)
        val cells = HashSet<Pair<Int, Int>>()
        DashboardLayouts.pack(manual, DashboardProfile.COMPACT).forEach { placement ->
            repeat(placement.spec.heightSteps + DashboardLayouts.GAP_STEPS) { dy -> repeat(placement.spec.size.columns) { dx ->
                assertTrue(cells.add((placement.column + dx) to (placement.row + dy)))
            } }
        }
    }

    @Test
    fun versionFiveFixedGameMigratesToManualList() {
        val raw = """{"version":5,"widgets":[{"id":"hero","type":"GAME_SPOTLIGHT","columns":2,"rows":2,"heightSteps":34,"spotlightSource":"FIXED","spotlightStrategy":"DAILY_RANDOM","gameId":7}]}"""
        val migrated = DashboardLayouts.decode(raw, DashboardProfile.WIDE).widgets.single()

        assertEquals(SpotlightSelectionMode.MANUAL, migrated.spotlightSelectionMode)
        assertEquals(listOf(7L), migrated.spotlightGameIds)
        assertEquals(null, migrated.gameId)
        assertEquals(SpotlightSource.FIXED, migrated.spotlightSource)
    }

    @Test
    fun versionFiveAutomaticSourcesMigrateToAutomaticModePreservingStrategy() {
        val raw = """{"version":5,"widgets":[{"id":"hero","type":"GAME_SPOTLIGHT","columns":2,"rows":2,"heightSteps":34,"spotlightSource":"FAVORITES","spotlightStrategy":"DAILY_RANDOM"}]}"""
        val migrated = DashboardLayouts.decode(raw, DashboardProfile.WIDE).widgets.single()

        assertEquals(SpotlightSelectionMode.AUTOMATIC, migrated.spotlightSelectionMode)
        assertEquals(SpotlightSource.FAVORITES, migrated.spotlightSource)
        assertEquals(SpotlightStrategy.DAILY_RANDOM, migrated.spotlightStrategy)
        assertTrue(migrated.spotlightGameIds.isEmpty())
        assertEquals(DashboardLayouts.MAX_SPOTLIGHT_PAGES, migrated.spotlightMaxPages)
    }

    @Test
    fun v6ManualRoundTripPreservesOrderMaxPagesAndMode() {
        val original = DashboardLayout(widgets = listOf(
            DashboardWidgetSpec(
                "hero",
                DashboardWidgetType.GAME_SPOTLIGHT,
                DashboardWidgetSize(2, 2),
                spotlightSelectionMode = SpotlightSelectionMode.MANUAL,
                spotlightGameIds = listOf(3L, 1L, 2L),
                spotlightMaxPages = 3,
            ),
        ))

        val restored = DashboardLayouts.decode(
            DashboardLayouts.encode(original, DashboardProfile.WIDE),
            DashboardProfile.WIDE,
        ).widgets.single()

        assertEquals(SpotlightSelectionMode.MANUAL, restored.spotlightSelectionMode)
        assertEquals(listOf(3L, 1L, 2L), restored.spotlightGameIds)
        assertEquals(3, restored.spotlightMaxPages)
        assertEquals(null, restored.gameId)
    }

    @Test
    fun manualListIsDeduplicatedClampedToFiveAndMaxPagesCoerced() {
        val normalized = DashboardLayouts.normalize(
            DashboardLayout(widgets = listOf(
                DashboardWidgetSpec(
                    "hero",
                    DashboardWidgetType.GAME_SPOTLIGHT,
                    DashboardWidgetSize(2, 2),
                    spotlightSelectionMode = SpotlightSelectionMode.MANUAL,
                    spotlightGameIds = listOf(1L, 1L, 2L, 3L, 4L, 5L, 6L),
                    spotlightMaxPages = 9,
                ),
            )),
            DashboardProfile.WIDE,
        ).widgets.single()

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), normalized.spotlightGameIds)
        assertEquals(DashboardLayouts.MAX_SPOTLIGHT_PAGES, normalized.spotlightMaxPages)
    }

    @Test
    fun setSpotlightManualAndAutomaticKeepCanonicalInvariant() {
        val layout = DashboardLayout(widgets = listOf(
            DashboardWidgetSpec("hero", DashboardWidgetType.GAME_SPOTLIGHT, DashboardWidgetSize(2, 2)),
        ))
        val manual = DashboardLayouts.setSpotlightManual(layout, DashboardProfile.WIDE, "hero", listOf(4L, 2L), 2)
        assertEquals(SpotlightSelectionMode.MANUAL, manual.widgets.single().spotlightSelectionMode)
        assertEquals(listOf(4L, 2L), manual.widgets.single().spotlightGameIds)

        val automatic = DashboardLayouts.setSpotlightAutomatic(manual, DashboardProfile.WIDE, "hero", SpotlightSource.RECENT, SpotlightStrategy.DAILY_RANDOM, 3)
        assertEquals(SpotlightSelectionMode.AUTOMATIC, automatic.widgets.single().spotlightSelectionMode)
        assertTrue(automatic.widgets.single().spotlightGameIds.isEmpty())
        assertEquals(null, automatic.widgets.single().gameId)
        assertEquals(SpotlightSource.RECENT, automatic.widgets.single().spotlightSource)
        assertEquals(3, automatic.widgets.single().spotlightMaxPages)
    }

    private fun trend(
        id: String,
        metric: TrendMetric,
        range: TrendRange,
        visualStyle: TrendVisualStyle,
    ) = DashboardWidgetSpec(
        instanceId = id,
        type = DashboardWidgetType.PLAY_TREND,
        size = DashboardWidgetSize(1, 1),
        trendMetric = metric,
        trendRange = range,
        trendVisualStyle = visualStyle,
    )
}
