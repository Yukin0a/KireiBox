package com.yuki.yukihub.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PlayTrendAggregatorTest {
    private val utc = ZoneId.of("UTC")

    @Test fun fillsMissingDaysAndSumsSessionsByLocalDay() {
        val now = LocalDate.of(2026, 7, 30).atTime(12, 0).atZone(utc).toInstant().toEpochMilli()
        val day29 = LocalDate.of(2026, 7, 29).atTime(9, 0).atZone(utc).toInstant().toEpochMilli()
        val points =
            PlayTrendAggregator.aggregate(
                listOf(PlaySessionSample(day29, 10), PlaySessionSample(day29 + 1_000, 20)),
                days = 3,
                now = now,
                zoneId = utc
            )
        assertEquals(listOf(0L, 30L, 0L), points.map { it.duration })
    }

    @Test fun countsEachLaunchedGameOncePerLocalDay() {
        val now = LocalDate.of(2026, 7, 30).atTime(12, 0).atZone(utc).toInstant().toEpochMilli()
        val morning = LocalDate.of(2026, 7, 30).atTime(8, 0).atZone(utc).toInstant().toEpochMilli()
        val points =
            PlayTrendAggregator.aggregate(
                listOf(
                    PlaySessionSample(timestamp = morning, duration = 0, gameId = 7, launchedAt = morning),
                    PlaySessionSample(timestamp = morning + 1_000, duration = 0, gameId = 7, launchedAt = morning + 1_000),
                    PlaySessionSample(timestamp = morning + 2_000, duration = 0, gameId = 8, launchedAt = morning + 2_000)
                ),
                days = 1,
                now = now,
                zoneId = utc
            )
        assertEquals(2, points.single().distinctGamesLaunched)
    }

    @Test fun launchDayUsesRequestedTimeZone() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val now = LocalDate.of(2026, 7, 30).atTime(12, 0).atZone(shanghai).toInstant().toEpochMilli()
        val justAfterLocalMidnight =
            LocalDate.of(2026, 7, 30)
                .atTime(0, 15).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        val points =
            PlayTrendAggregator.aggregate(
                listOf(PlaySessionSample(justAfterLocalMidnight, 0, gameId = 3, launchedAt = justAfterLocalMidnight)),
                days = 2,
                now = now,
                zoneId = shanghai
            )
        assertEquals(listOf(0, 1), points.map { it.distinctGamesLaunched })
    }
}
