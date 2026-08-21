package com.kireibox.ui

import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.settings.TrendMetric
import com.yuki.yukihub.settings.TrendRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendChartPolicyTest {
    @Test fun playTimeUsesMinutesBelowTwoHoursAndHoursAtOrAboveIt() {
        val minuteModel = TrendChartPolicy.chart(
            points = listOf(point(duration = 90 * MINUTE)),
            metric = TrendMetric.PLAY_TIME,
            range = TrendRange.DAYS_7,
            compact = true,
        )
        val hourModel = TrendChartPolicy.chart(
            points = listOf(point(duration = 2 * HOUR)),
            metric = TrendMetric.PLAY_TIME,
            range = TrendRange.DAYS_7,
            compact = false,
        )

        assertEquals("分钟", minuteModel.unit)
        assertEquals("小时", hourModel.unit)
        assertEquals("游玩时长", minuteModel.title)
        assertEquals("最近 7 天 · 结果", minuteModel.summary)
    }

    @Test fun niceAxisStaysFiniteForZeroSingleEqualAndExtremeValues() {
        listOf(
            emptyList(),
            listOf(point(duration = 0)),
            listOf(point(duration = HOUR), point(duration = HOUR)),
            listOf(point(duration = Long.MAX_VALUE)),
        ).forEach { points ->
            val model = TrendChartPolicy.chart(points, TrendMetric.PLAY_TIME, TrendRange.DAYS_30, compact = false)
            assertTrue(model.axis.maximum.isFinite())
            assertTrue(model.axis.maximum > 0f)
            assertTrue(model.axis.ticks.all { it.isFinite() })
        }
    }

    @Test fun compactModelHasTwoYAxisTicksAndNoDatesWhileExpandedShowsThreeAndDates() {
        val points = List(7) { index -> point(day = index.toLong(), duration = (index + 1) * MINUTE) }

        val compact = TrendChartPolicy.chart(points, TrendMetric.PLAY_TIME, TrendRange.DAYS_7, compact = true)
        val expanded = TrendChartPolicy.chart(points, TrendMetric.PLAY_TIME, TrendRange.DAYS_7, compact = false)

        assertEquals(2, compact.axis.ticks.size)
        assertTrue(0f in compact.axis.ticks)
        assertTrue(compact.axis.maximum in compact.axis.ticks)
        assertTrue(compact.dateTicks.isEmpty())
        assertTrue(expanded.axis.ticks.size >= 3)
        assertEquals(3, expanded.dateTicks.size)
    }

    @Test fun gameOpensUsesWholeGameUnitAndNearestIndexClampsSafely() {
        val model = TrendChartPolicy.chart(
            listOf(point(opens = 1), point(opens = 3)),
            TrendMetric.GAME_OPENS,
            TrendRange.DAYS_7,
            compact = false,
        )

        assertEquals("款", model.unit)
        assertTrue(model.axis.ticks.all { it % 1f == 0f })
        assertEquals(0, TrendChartPolicy.nearestIndex(-4f, 100f, 3))
        assertEquals(2, TrendChartPolicy.nearestIndex(500f, 100f, 3))
        assertEquals(0, TrendChartPolicy.nearestIndex(10f, 0f, 3))
        assertEquals(-1, TrendChartPolicy.nearestIndex(10f, 100f, 0))
        assertEquals(1, TrendChartPolicy.nearestIndex(52f, 100f, 3))
        assertFalse(model.values.any { it.isNaN() })
    }

    @Test fun hapticIsReservedForChangesBetweenAlreadySnappedPoints() {
        assertFalse(TrendChartPolicy.shouldHaptic(previousIndex = null, nextIndex = 0))
        assertFalse(TrendChartPolicy.shouldHaptic(previousIndex = 2, nextIndex = 2))
        assertTrue(TrendChartPolicy.shouldHaptic(previousIndex = 1, nextIndex = 2))
    }

    private fun point(day: Long = 0, duration: Long = 0, opens: Int = 0) =
        PlayTrendPoint(dayStart = day * 86_400_000L, duration = duration, distinctGamesLaunched = opens)

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
