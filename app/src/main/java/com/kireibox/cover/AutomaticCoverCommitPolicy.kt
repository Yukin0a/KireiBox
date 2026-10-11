package com.kireibox.cover

import com.yuki.yukihub.model.Game

/** Prevents an older automatic task from replacing a newer user choice. */
internal object AutomaticCoverCommitPolicy {
    private val eligibleBindingModes = setOf("", "NONE", "RETRY", "REVIEW")

    fun canCommit(game: Game): Boolean = game.coverUri.isNullOrBlank() && game.vndbBindingMode in eligibleBindingModes
}
