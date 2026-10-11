package com.kireibox.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.storage.StorageNodes
import com.yuki.yukihub.storage.StorageRootIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Drives the 添加游戏 pages: collects folders, detects engines, then imports what the user confirms. */
internal class KireiAddGameController(host: KireiViewModel) : KireiViewModelController(host) {
    private var detectJob: Job? = null

    fun openSingle() {
        mutableState.value = mutableState.value.copy(addGame = AddGameUi(AddGameStage.SINGLE))
    }

    fun openBatch() {
        mutableState.value = mutableState.value.copy(addGame = AddGameUi(AddGameStage.BATCH))
    }

    fun close() {
        detectJob?.cancel()
        detectJob = null
        val ui = mutableState.value.addGame ?: return
        mutableState.value =
            mutableState.value.copy(addGame = null, notice = if (ui.addedCount > 0) AddGamePolicy.summary(ui) else mutableState.value.notice)
        if (ui.addedCount > 0) viewModelScope.launch(Dispatchers.IO) { reloadInternal() }
    }

    /** Back from detection or review returns to the page the flow started from. */
    fun backToStart() {
        val ui = mutableState.value.addGame ?: return
        if (ui.stage == AddGameStage.DETECTING || ui.stage == AddGameStage.REVIEW) {
            detectJob?.cancel()
            if (ui.addedCount > 0 || ui.folders.isEmpty()) close() else update { it.copy(stage = AddGameStage.BATCH, queue = emptyList(), selected = emptySet(), editing = null, error = null) }
        } else {
            close()
        }
    }

    // --- Batch ------------------------------------------------------------------------------

    fun addBatchFolder(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = StorageRootIdentity.key(uri.toString())
            val name = folderName(uri)
            withContext(Dispatchers.Main) {
                update { ui ->
                    if (ui.folders.any { StorageRootIdentity.key(it.uri) == key }) ui else ui.copy(folders = ui.folders + AddGameFolder(uri.toString(), name), error = null)
                }
            }
        }
    }

    fun removeBatchFolder(uri: String) = update { ui -> ui.copy(folders = ui.folders.filterNot { it.uri == uri }) }

    fun startBatchDetect() {
        val folders = mutableState.value.addGame?.folders.orEmpty()
        if (folders.isEmpty()) return
        update { it.copy(stage = AddGameStage.DETECTING, error = null) }
        detectJob?.cancel()
        detectJob =
            viewModelScope.launch(Dispatchers.IO) {
                val found = ArrayList<AddGameCandidate>()
                var failure: String? = null
                for (folder in folders) {
                    val report = runCatching { core.scanUntilMatch(Uri.parse(folder.uri)) }
                    report.getOrNull()?.results?.mapTo(found) { AddGamePolicy.candidateFor(it).withLibraryState() }
                    report.exceptionOrNull()?.let { failure = it.message ?: "识别失败" }
                }
                val (queue, present) = AddGamePolicy.buildQueue(found)
                finishDetection(queue, present, emptyMessage = failure ?: if (present > 0) "发现的游戏都已在游戏库中" else "没有发现游戏", backTo = AddGameStage.BATCH)
            }
    }

    // --- Single -----------------------------------------------------------------------------

    /** [engine] is what the user picked beforehand; null detects it from the folder. */
    fun addSingleFolder(
        uri: Uri,
        engine: EngineType?,
    ) {
        update { it.copy(stage = AddGameStage.DETECTING, error = null) }
        detectJob?.cancel()
        detectJob =
            viewModelScope.launch(Dispatchers.IO) {
                val root = runCatching { core.detectDirectory(uri) }.getOrNull()
                if (root == null) {
                    finishDetection(emptyList(), 0, emptyMessage = "无法读取所选目录", backTo = AddGameStage.SINGLE)
                    return@launch
                }
                val queue =
                    if (engine != null || root.engine != EngineType.UNKNOWN) {
                        listOf(AddGamePolicy.candidateFor(root, engine).withLibraryState())
                    } else {
                        // The folder itself is no game: games inside it are offered one by one, else the user picks the engine.
                        val inside = runCatching { core.scanUntilMatch(uri).results }.getOrDefault(emptyList())
                        val found = inside.map { AddGamePolicy.candidateFor(it).withLibraryState() }
                        if (found.isEmpty()) listOf(AddGamePolicy.candidateFor(root).withLibraryState()) else AddGamePolicy.buildQueue(found).first
                    }
                finishDetection(queue, 0, emptyMessage = "没有发现游戏", backTo = AddGameStage.SINGLE)
            }
    }

    // --- Review -----------------------------------------------------------------------------

    /** Applies one of the [AddGameReview] edits to the flow state. */
    fun review(edit: (AddGameUi) -> AddGameUi) = update(edit)

    /** Imports every selected, ready candidate at once and closes the flow with the summary. */
    fun confirm() {
        val ui = mutableState.value.addGame
        val items = ui?.importable.orEmpty()
        if (ui == null || !ui.canConfirm) return
        update { it.copy(saving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { core.importResults(items.map(AddGameCandidate::toScanResult), keepRemovedHidden = false) }
            withContext(Dispatchers.Main) {
                val summary = result.getOrNull()
                if (summary == null) {
                    update { it.copy(saving = false, error = result.exceptionOrNull()?.message ?: "添加失败") }
                } else {
                    val added = (items.size - summary.skippedCount).coerceAtLeast(0)
                    update { it.copy(addedCount = added, skippedCount = it.queue.size - added, saving = false, error = null) }
                    close()
                    if (items.any { it.engine == EngineType.WINDOWS }) host.winlatorController.onGameAdded(EngineType.WINDOWS)
                }
            }
        }
    }

    // --- Helpers ----------------------------------------------------------------------------

    private suspend fun finishDetection(
        queue: List<AddGameCandidate>,
        alreadyInLibrary: Int,
        emptyMessage: String,
        backTo: AddGameStage,
    ) {
        withContext(Dispatchers.Main) {
            // The user left the flow (or restarted detection) while this ran: drop the stale result.
            if (mutableState.value.addGame?.stage != AddGameStage.DETECTING) return@withContext
            update {
                if (queue.isEmpty()) {
                    it.copy(stage = backTo, error = emptyMessage, alreadyInLibrary = alreadyInLibrary)
                } else {
                    AddGameReview.reviewing(it, queue).copy(alreadyInLibrary = alreadyInLibrary)
                }
            }
        }
    }

    private fun AddGameCandidate.withLibraryState(): AddGameCandidate = copy(inLibrary = repository.findByIdentity(rootUri, "")?.hidden == false)

    private fun folderName(uri: Uri): String =
        runCatching { StorageNodes.resolve(hostApplication, uri)?.name }.getOrNull()?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty().ifBlank { "文件夹" }

    /** No-op once the flow was closed, so a late result never reopens it. */
    private fun update(transform: (AddGameUi) -> AddGameUi) {
        val current = mutableState.value.addGame ?: return
        mutableState.value = mutableState.value.copy(addGame = transform(current))
    }
}
