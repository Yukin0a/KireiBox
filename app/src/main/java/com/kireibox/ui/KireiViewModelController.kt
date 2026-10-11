package com.kireibox.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.kireibox.cover.CoverQueueUiState
import com.kireibox.cover.CoverTaskStatus
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.Job

internal val ACTIVE_COVER_QUEUE_STATUSES =
    setOf(
        CoverTaskStatus.QUEUED,
        CoverTaskStatus.SEARCHING,
        CoverTaskStatus.DOWNLOADING,
    )

/** Number of games that still need an explicit cover decision; shared by reload and queue projections. */
internal fun pendingCoverReviewCount(
    games: List<Game>,
    queue: CoverQueueUiState
): Int =
    buildSet {
        games.filter(CoverSettingsPolicy::needsUserReview).forEach { add(it.id) }
        queue.tasks.filter { task ->
            task.status == CoverTaskStatus.NEEDS_REVIEW &&
                games.firstOrNull { it.id == task.gameId }?.coverUri.isNullOrBlank()
        }.forEach { add(it.gameId) }
    }.size

/**
 * Games whose cover still needs the user's attention: awaiting review **or** a failed automatic
 * match. This is what the 设置 tab's red dot / the 封面与元数据 row badge counts, so a batch that
 * failed (the common offline / blocked-network case) is not silently invisible in settings.
 */
internal fun coverAttentionCount(
    games: List<Game>,
    queue: CoverQueueUiState
): Int =
    buildSet {
        games.filter(CoverSettingsPolicy::needsAttention).forEach { add(it.id) }
        queue.tasks.filter { task ->
            task.status in COVER_ATTENTION_TASK_STATUSES &&
                games.firstOrNull { it.id == task.gameId }?.coverUri.isNullOrBlank()
        }.forEach { add(it.gameId) }
    }.size

internal val COVER_ATTENTION_TASK_STATUSES =
    setOf(
        CoverTaskStatus.NEEDS_REVIEW,
        CoverTaskStatus.FAILED,
    )

/** Games whose queued cover task is waiting for the user's choice. */
internal fun reviewTaskGameIds(queue: CoverQueueUiState): Set<Long> = queue.tasks.filter { it.status == CoverTaskStatus.NEEDS_REVIEW }.mapTo(HashSet()) { it.gameId }

internal const val BINDING_REFRESH_DEBOUNCE_MS = 200L

/**
 * Shared base for the [KireiViewModel] controllers. It forwards to the facade so the extracted
 * method bodies can stay verbatim while the public API keeps living on [KireiViewModel].
 */
internal abstract class KireiViewModelController(protected val host: KireiViewModel) {
    protected val mutableState get() = host.mutableState
    protected val viewModelScope get() = host.viewModelScope
    protected val isTestMode get() = host.isTestMode
    protected val core get() = host.core
    protected val repository get() = host.repository
    protected val metadata get() = host.metadata
    protected val metadataController get() = host.metadataController
    protected val coverStore get() = host.coverStore
    protected val krkrStore get() = host.krkrStore
    protected val shortcutGateway get() = host.shortcutGateway
    protected val accounts get() = host.accounts
    protected val coverQueue get() = host.coverQueue
    protected val coverNoMatchStore get() = host.coverNoMatchStore
    protected val autoCoverGate get() = host.autoCoverGate
    protected val coverSearchSemaphore get() = host.coverSearchSemaphore

    protected val updateRepository get() = host.updateRepository
    protected val updateDownloader get() = host.updateDownloader
    protected val updateInstaller get() = host.updateInstaller

    protected val testHomePresentationWriter get() = host.testHomePresentationWriter
    protected val testReloadAction get() = host.testReloadAction
    protected val testPayloadLoader get() = host.testPayloadLoader
    protected val testImmersiveGalleryModeWriter get() = host.testImmersiveGalleryModeWriter
    protected val testImmersiveGalleryIdsWriter get() = host.testImmersiveGalleryIdsWriter
    protected val testImmersiveGalleryAutoWriter get() = host.testImmersiveGalleryAutoWriter
    protected val testImmersiveGallerySourceWriter get() = host.testImmersiveGallerySourceWriter
    protected val testImmersiveGalleryStrategyWriter get() = host.testImmersiveGalleryStrategyWriter
    protected val testImmersiveGalleryCountWriter get() = host.testImmersiveGalleryCountWriter
    protected val testImmersiveGallerySettingsWriter get() = host.testImmersiveGallerySettingsWriter
    protected val testImmersiveVisualWriter get() = host.testImmersiveVisualWriter
    protected val testUpdateCheck get() = host.testUpdateCheck
    protected val testUpdatePreferenceWriter get() = host.testUpdatePreferenceWriter
    protected val testUpdateDownload get() = host.testUpdateDownload
    protected val testUpdateValidate get() = host.testUpdateValidate

    protected var coverSearchJob: Job?
        get() = host.coverSearchJob
        set(value) {
            host.coverSearchJob = value
        }
    protected var coverSaveJob: Job?
        get() = host.coverSaveJob
        set(value) {
            host.coverSaveJob = value
        }

    protected val hostApplication: Application get() = host.getApplication()

    protected open fun reload() = host.reload()

    protected open suspend fun reloadInternal(generation: Long? = null) = host.reloadInternal(generation)

    protected open fun reloadImmersiveSettingsLatest() = host.reloadImmersiveSettingsLatest()

    protected fun showNotice(message: String) = host.showNotice(message)
}

// ---------------------------------------------------------------------------------------------
// Updates
// ---------------------------------------------------------------------------------------------
