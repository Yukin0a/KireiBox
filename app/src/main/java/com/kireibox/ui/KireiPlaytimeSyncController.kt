package com.kireibox.ui

import androidx.lifecycle.viewModelScope
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.sync.NextmoePlaytimeSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NextMoe playtime sync (doc §5): mirrors the engine's status into the UI state, schedules a sync
 * whenever the launcher comes back (sessions settle there), and runs the user's confirmations.
 */
internal class KireiPlaytimeSyncController(host: KireiViewModel) : KireiViewModelController(host) {
    private val sync: NextmoePlaytimeSync get() = host.playtimeSync

    fun start() {
        viewModelScope.launch {
            var wasRunning = false
            sync.status.collect { status ->
                mutableState.value = mutableState.value.copy(playtimeSync = status)
                // A finished sync may have changed which games wait for the user.
                if (!status.running && mutableState.value.settingsPage == KireiSettingsPage.PLAYTIME_SYNC) loadAttention()
                // Time played on another device was brought in: show the new totals.
                if (wasRunning && !status.running && status.pulled > 0) {
                    reload()
                    showNotice("已从 NextMoe 同步 ${status.pulled} 款游戏的时长")
                }
                wasRunning = status.running
            }
        }
        sync.refreshSummary()
    }

    fun onAppForeground() {
        if (connected()) sync.schedule()
    }

    /** 同步数据 / 立即同步. Without a usable NextMoe account it opens the account page instead. */
    fun syncNow() {
        when {
            !connected() -> {
                host.openSettingsShortcut(KireiSettingsPage.COVER_ACCOUNTS)
                showNotice("登录 NextMoe 后即可同步游玩时长")
            }
            !sync.enabled() -> host.openSettingsShortcut(KireiSettingsPage.PLAYTIME_SYNC)
            else -> sync.syncNow()
        }
    }

    fun setEnabled(value: Boolean) = sync.setEnabled(value)

    fun loadAttention() {
        viewModelScope.launch {
            val games = withContext(Dispatchers.IO) { runCatching { sync.attention() }.getOrDefault(emptyList()) }
            mutableState.value = mutableState.value.copy(playtimeAttention = games)
        }
    }

    fun confirm(
        gameId: Long,
        workId: String,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { sync.confirm(gameId, workId) } }
            loadAttention()
        }
    }

    /** 选择作品: decide this game again after the user picks a cover (a NextMoe pick binds it). */
    fun pickWork(gameId: Long) {
        val game = mutableState.value.games.firstOrNull { it.id == gameId } ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { sync.forget(gameId) } }
            host.openCoverSearch(game, CoverPickerOrigin.GAME_MENU)
        }
    }

    /** After a NextMoe login the first sync runs at once; after a sign-out the status line is re-read. */
    fun onAccountChanged(source: CoverSourceMode) {
        if (source != CoverSourceMode.NEXTMOE) return
        if (connected()) sync.syncNow() else sync.refreshSummary()
    }

    private fun connected(): Boolean = accounts.status.value[CoverSourceMode.NEXTMOE]?.let { it.connected && !it.needsReauth } == true
}
