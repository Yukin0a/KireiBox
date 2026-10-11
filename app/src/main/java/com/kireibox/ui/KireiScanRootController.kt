package com.kireibox.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class KireiScanRootController(host: KireiViewModel) : KireiViewModelController(host) {
    fun addScanRoot(uri: Uri) = addOrReplaceScanRoot(uri, null)

    fun replaceScanRoot(
        previousRoot: String?,
        uri: Uri
    ) = addOrReplaceScanRoot(uri, previousRoot)

    private fun addOrReplaceScanRoot(
        uri: Uri,
        previousRoot: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = mutableState.value.copy(busy = true)
            val change = core.settings.addOrReplaceScanRoot(uri.toString(), previousRoot)
            var foundGames = 0
            val result =
                runCatching {
                    check(change != null) { "目录路径无效" }
                    val report = core.scanUntilMatch(uri)
                    foundGames = report.results.size
                    // Adding or re-authorizing a directory is an explicit user action, so the games
                    // the scan finds are imported visible again even if they were hidden when the
                    // same directory was removed earlier. `keepRemovedGamesHiddenOnRefresh` only
                    // governs the background directory auto-refresh (KireiSettingsController).
                    core.importScan(report, keepRemovedHidden = false)
                }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(
                            busy = false,
                            notice =
                                if (change?.replaced == true) {
                                    "目录已更新：发现 $foundGames 个游戏"
                                } else {
                                    "扫描完成：发现 $foundGames 个游戏"
                                },
                        )
                    } else {
                        mutableState.value.copy(busy = false).withNotice(result.exceptionOrNull()?.message ?: "扫描目录失败", NoticeTone.ERROR)
                    }
            }
            reloadInternal()
        }
    }

    fun removeScanRoot(root: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = core.settings.snapshot()
            val affected = repository.gamesUnderRoot(root).map { it.id }
            val result =
                runCatching {
                    if (affected.isNotEmpty()) repository.setGamesHidden(affected, true)
                    check(core.settings.removeScanRoot(root) != null) { "扫描目录不存在" }
                    UndoScanRootRemoval(
                        root = root,
                        previousRoots = snapshot.scanRoots,
                        previousEnabled = snapshot.scanRootEnabled,
                        gameIds = affected,
                    )
                }.onFailure {
                    if (affected.isNotEmpty()) runCatching { repository.setGamesHidden(affected, false) }
                }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(
                            undoScanRootRemoval = result.getOrNull(),
                            notice = null,
                        )
                    } else {
                        mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "移除扫描目录失败", NoticeTone.ERROR)
                    }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun undoScanRootRemoval() {
        val undo = mutableState.value.undoScanRootRemoval ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    core.settings.replaceScanRoots(undo.previousRoots, undo.previousEnabled)
                    if (undo.gameIds.isNotEmpty()) repository.setGamesHidden(undo.gameIds, false)
                }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(undoScanRootRemoval = null, notice = "已恢复扫描目录")
                    } else {
                        mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "恢复扫描目录失败", NoticeTone.ERROR)
                    }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun dismissUndoScanRootRemoval() {
        mutableState.value = mutableState.value.copy(undoScanRootRemoval = null)
    }
}

// ---------------------------------------------------------------------------------------------
// Library mutations
// ---------------------------------------------------------------------------------------------
