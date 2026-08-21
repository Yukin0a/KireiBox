package com.kireibox.ui

import com.yuki.yukihub.model.Game
import java.util.Locale
import kotlin.math.max

enum class LibraryOverviewSort { RECENT, PLAY_TIME }

object LibraryOverviewPolicy {
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_HOUR = 3_600_000L

    fun sorted(games: List<Game>, sort: LibraryOverviewSort): List<Game> = when (sort) {
        LibraryOverviewSort.RECENT -> games.sortedWith(
            compareByDescending<Game> { it.lastPlayedAt > 0L }
                .thenByDescending { it.lastPlayedAt }
                .thenBy { it.title.orEmpty() }
        )
        LibraryOverviewSort.PLAY_TIME -> games.sortedWith(
            compareByDescending<Game> { it.totalPlayTime }
                .thenBy { it.title.orEmpty() }
        )
    }

    fun compactPlayTime(milliseconds: Long): String {
        if (milliseconds <= 0L) return "0m"
        if (milliseconds < MILLIS_PER_HOUR) {
            return "${max(1L, milliseconds / MILLIS_PER_MINUTE)}m"
        }
        val hours = milliseconds / MILLIS_PER_HOUR.toDouble()
        return if (hours % 1.0 == 0.0) {
            "${hours.toLong()}h"
        } else {
            String.format(Locale.ROOT, "%.1fh", hours)
        }
    }

    fun summaryPlayTime(milliseconds: Long): String {
        val safeMilliseconds = milliseconds.coerceAtLeast(0L)
        return if (safeMilliseconds < MILLIS_PER_HOUR) {
            "${safeMilliseconds / MILLIS_PER_MINUTE} 分钟"
        } else {
            "${safeMilliseconds / MILLIS_PER_HOUR} 小时"
        }
    }

    /** 66dp Dock + 28dp outer vertical padding + 12dp readable clearance. */
    fun dockContentPaddingDp(navigationBarInsetDp: Float): Float =
        navigationBarInsetDp.coerceAtLeast(0f) + 106f
}
