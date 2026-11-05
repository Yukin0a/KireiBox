package com.kireibox.ui

import androidx.lifecycle.viewModelScope
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.sync.NextmoePlaytimeSync
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
                if (wasRunning && !status.running && status.problem == null) {
                    reload()
                    showNotice("已同步：上传 ${status.uploaded} 款，拉取 ${status.pulled} 款，${status.summary.needsConfirm + status.summary.rejected} 款需要处理")
                }
                wasRunning = status.running
            }
        }
        viewModelScope.launch {
            host.state.map { state -> state.games.map { Triple(it.id, it.coverUri, it.title) } to state.coverQueue.toString() }.distinctUntilChanged().collect {
                if(connected()) sync.schedule(3_000L)
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
            val accountId = accounts.status.value[CoverSourceMode.NEXTMOE]?.id
            val games = withContext(Dispatchers.IO) { runCatching { sync.attention() }.getOrDefault(emptyList()) }
            val details = withContext(Dispatchers.IO) { Triple(sync.cloud(), sync.library(), sync.history()) }
            if(accountId != accounts.status.value[CoverSourceMode.NEXTMOE]?.id) return@launch
            mutableState.value = mutableState.value.copy(playtimeAttention = games, cloudPlaytimes = details.first, playtimeLibrary = details.second, playtimeHistory = details.third)
        }
    }

    fun confirm(
        gameId: Long,
        workId: String,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { sync.confirm(gameId, workId) } }
            showNotice(if (result.isSuccess) "作品绑定成功" else "绑定失败，请稍后重试")
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
        mutableState.value = mutableState.value.copy(cloudPlaytimes = emptyList(), playtimeLibrary = emptyList(), playtimeAttention = emptyList(), playtimeHistory = emptyList(), playRecordsPrivate = null)
        if (connected()) sync.syncNow() else sync.refreshSummary()
    }

    private fun connected(): Boolean = accounts.status.value[CoverSourceMode.NEXTMOE]?.let { it.connected && !it.needsReauth } == true
}
