package com.kireibox.ui

import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.settings.TrendMetric
import com.yuki.yukihub.settings.TrendRange
import kotlin.math.ceil
import kotlin.math.pow

internal data class TrendAxis(val maximum: Float, val ticks: List<Float>)

internal data class TrendChartModel(
    val title: String,
    val summary: String,
    val unit: String,
    val values: List<Float>,
    val axis: TrendAxis,
    val dateTicks: List<Long>,
)

/** Presentation-only rules: source play time remains milliseconds in [PlayTrendPoint]. */
internal object TrendChartPolicy {
    fun chart(
        points: List<PlayTrendPoint>,
        metric: TrendMetric,
        range: TrendRange,
        compact: Boolean,
    ): TrendChartModel {
        val source = points.takeLast(range.days)
        val rawMaximum = source.maxOfOrNull { point ->
            when (metric) {
                TrendMetric.PLAY_TIME -> point.duration.coerceAtLeast(0L)
                TrendMetric.GAME_OPENS -> point.distinctGamesLaunched.coerceAtLeast(0).toLong()
            }
        } ?: 0L
        val divisor = when {
            metric == TrendMetric.GAME_OPENS -> 1L
            rawMaximum < TWO_HOURS_MS -> MINUTE_MS
            else -> HOUR_MS
        }
        val values = source.map { point ->
            val raw = when (metric) {
                TrendMetric.PLAY_TIME -> point.duration.coerceAtLeast(0L)
                TrendMetric.GAME_OPENS -> point.distinctGamesLaunched.coerceAtLeast(0).toLong()
            }
            (raw.toDouble() / divisor).toFloat().takeIf { it.isFinite() } ?: Float.MAX_VALUE
        }
        val maximum = niceMaximum(values.maxOrNull() ?: 0f, metric == TrendMetric.GAME_OPENS)
        val middle = if (metric == TrendMetric.GAME_OPENS) ceil(maximum / 2f).toFloat() else maximum / 2f
        val ticks = if (compact) listOf(0f, maximum) else listOf(0f, middle, maximum)
        return TrendChartModel(
            title = if (metric == TrendMetric.PLAY_TIME) "游玩时长" else "打开游戏数",
            summary = "最近 ${range.days} 天 · 结果",
            unit = when (metric) {
                TrendMetric.PLAY_TIME -> if (divisor == MINUTE_MS) "分钟" else "小时"
                TrendMetric.GAME_OPENS -> "款"
            },
            values = values,
            axis = TrendAxis(maximum, ticks),
            dateTicks = if (compact || source.isEmpty()) emptyList() else listOf(
                source.first().dayStart,
                source[source.lastIndex / 2].dayStart,
                source.last().dayStart,
            ),
        )
    }

    fun nearestIndex(x: Float, width: Float, count: Int): Int {
        if (count <= 0) return -1
        if (width <= 0f || count == 1) return 0
        return (x / width * (count - 1)).toInt().coerceIn(0, count - 1).let { floor ->
            val fraction = x / width * (count - 1) - floor
            if (fraction >= .5f) (floor + 1).coerceAtMost(count - 1) else floor
        }
    }

    fun shouldHaptic(previousIndex: Int?, nextIndex: Int): Boolean =
        previousIndex != null && nextIndex >= 0 && previousIndex != nextIndex

    private fun niceMaximum(value: Float, wholeNumbers: Boolean): Float {
        if (!value.isFinite() || value <= 0f) return 1f
        if (wholeNumbers && value <= 2f) return 2f
        val exponent = kotlin.math.floor(kotlin.math.log10(value.toDouble())).toInt()
        val base = 10.0.pow(exponent.toDouble())
        val fraction = value / base
        val multiplier = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        val result = (ceil(fraction / multiplier) * multiplier * base).toFloat()
        return result.takeIf { it.isFinite() && it > 0f } ?: Float.MAX_VALUE
    }

    private const val MINUTE_MS = 60_000L
    private const val TWO_HOURS_MS = 2 * 60 * MINUTE_MS
    private const val HOUR_MS = 60 * MINUTE_MS
}
