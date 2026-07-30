package com.kireibox.ui

import com.yuki.yukihub.model.Game
import java.util.Locale
import kotlin.math.max

enum class LibraryOverviewSort { RECENT, PLAY_TIME }

object LibraryOverviewPolicy {
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

    fun compactPlayTime(seconds: Long): String {
        if (seconds <= 0L) return "0m"
        if (seconds < 3_600L) return "${max(1L, seconds / 60L)}m"
        val hours = seconds / 3_600.0
        return if (hours % 1.0 == 0.0) {
            "${hours.toLong()}h"
        } else {
            String.format(Locale.ROOT, "%.1fh", hours)
        }
    }

    /** 66dp Dock + 28dp outer vertical padding + 12dp readable clearance. */
    fun dockContentPaddingDp(navigationBarInsetDp: Float): Float =
        navigationBarInsetDp.coerceAtLeast(0f) + 106f
}
