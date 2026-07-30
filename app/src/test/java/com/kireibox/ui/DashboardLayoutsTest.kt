package com.kireibox.ui

import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.DashboardWidgetSize
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.DashboardStyles
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
        assertEquals(1, normalized.widgets.count { it.type == DashboardWidgetType.GAME_SPOTLIGHT && it.gameId == 5L })
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
}
