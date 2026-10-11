package com.yuki.yukihub.data.play

import com.yuki.yukihub.data.PlaySessionSample
import com.yuki.yukihub.data.PlayTrendAggregator
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.data.YukiDatabaseHelper
import java.util.ArrayList

/**
 * Play-statistics aggregation split out of `GameRepository` (the Home trend widget).
 *
 * GameRepository keeps the public API and delegates here.
 */
internal class PlayStatsRepository(
    private val helper: YukiDatabaseHelper,
) {
    /** Daily play duration including zero-value days; used by the Home trend widget. */
    @JvmOverloads
    fun dailyPlayTrend(
        days: Int,
        now: Long = System.currentTimeMillis(),
    ): List<PlayTrendPoint> {
        val start = PlayTrendAggregator.rangeStart(days, now)
        val samples = ArrayList<PlaySessionSample>()
        helper.readableDatabase.rawQuery(
            "SELECT COALESCE(end_time,start_time),duration,game_id,IFNULL(launched_at,0) FROM play_sessions " +
                "WHERE IFNULL(deleted,0)=0 AND " +
                "((duration>0 AND COALESCE(end_time,start_time)>=?) OR launched_at>=?)",
            arrayOf(start.toString(), start.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                samples +=
                    PlaySessionSample(
                        timestamp = cursor.getLong(0),
                        duration = cursor.getLong(1),
                        gameId = cursor.getLong(2),
                        launchedAt = cursor.getLong(3),
                    )
            }
        }
        return PlayTrendAggregator.aggregate(samples, days, now)
    }
}
