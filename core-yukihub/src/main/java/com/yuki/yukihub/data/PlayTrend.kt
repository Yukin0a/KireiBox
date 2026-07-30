package com.yuki.yukihub.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PlayTrendPoint(
    val dayStart: Long,
    val duration: Long,
    val distinctGamesLaunched: Int = 0
)

data class PlaySessionSample(
    val timestamp: Long,
    val duration: Long,
    val gameId: Long = 0L,
    val launchedAt: Long = 0L
)

object PlayTrendAggregator {
    fun aggregate(
        sessions: List<PlaySessionSample>,
        days: Int,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<PlayTrendPoint> {
        val count = days.coerceIn(1, 31)
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val first = today.minusDays((count - 1).toLong())
        val totals = sessions.groupBy { sample ->
            Instant.ofEpochMilli(sample.timestamp).atZone(zoneId).toLocalDate()
        }.mapValues { (_, values) -> values.sumOf { it.duration.coerceAtLeast(0L) } }
        val launches = sessions.asSequence()
            .filter { it.gameId > 0L && it.launchedAt > 0L }
            .groupBy { sample ->
                Instant.ofEpochMilli(sample.launchedAt).atZone(zoneId).toLocalDate()
            }
            .mapValues { (_, values) -> values.map { it.gameId }.distinct().size }
        return (0 until count).map { offset ->
            val date = first.plusDays(offset.toLong())
            PlayTrendPoint(
                dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                duration = totals[date] ?: 0L,
                distinctGamesLaunched = launches[date] ?: 0
            )
        }
    }

    fun rangeStart(days: Int, now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val count = days.coerceIn(1, 31)
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return today.minusDays((count - 1).toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
