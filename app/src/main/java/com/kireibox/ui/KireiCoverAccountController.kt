package com.kireibox.ui

import androidx.lifecycle.viewModelScope
import com.yuki.yukihub.metadata.model.CoverSourceHealth
import com.yuki.yukihub.metadata.model.CoverSourceMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PROFILE_RETRY_MS = 10 * 60 * 1000L

internal class KireiCoverAccountController(host: KireiViewModel) : KireiViewModelController(host) {
    private var accountJob: Job? = null
    private val profileAttemptAt = mutableMapOf<CoverSourceMode, Long>()

    fun setMetadataSource(source: CoverSourceMode) {
        if (!source.isActive) return
        if (!accounts.canSearch(source)) {
            showNotice("请先连接自己的 ${source.label} 账号")
            return
        }
        core.settings.putString(com.yuki.yukihub.settings.YukiSettingsStore.KEY_METADATA_SOURCE, source.key)
        reload()
    }

    fun setNsfwBlurEnabled(value: Boolean) {
        core.settings.setNsfwBlurEnabled(value)
        reload()
    }

    /** Toggles the hybrid background auto-match path (off = legacy single-source flow). */
    fun setCoverHybridAutoMatch(value: Boolean) {
        core.settings.setCoverHybridAutoMatch(value)
        autoCoverGate.reset()
        reload()
    }

    /** Strict L2: only lexical evidence may auto-bind; the cross-source rule is disabled. */
    fun setCoverStrictL2(value: Boolean) {
        core.settings.setCoverStrictL2(value)
        autoCoverGate.reset()
        reload()
    }

    /** Gate the background automatic cover task on an unmetered/Wi-Fi network. */
    fun setCoverAutoMatchWifiOnly(value: Boolean) {
        core.settings.setCoverAutoMatchWifiOnly(value)
        autoCoverGate.reset()
        reload()
    }

    /** Forgets the persistent no-match dedup so the next pass re-queries every game. */
    fun clearCoverNoMatchStore() {
        coverNoMatchStore.clear()
        autoCoverGate.reset()
        reload()
        showNotice("已清除封面无结果记录")
    }

    fun setBangumiToken(token: String) {
        accountJob?.cancel()
        mutableState.value = mutableState.value.copy(accountBusy = true)
        accountJob =
            viewModelScope.launch {
                try {
                    com.yuki.yukihub.net.MetadataRequests.run(CoverSourceMode.BANGUMI) { accounts.connectBangumi(token) }
                    autoCoverGate.reset()
                    reload()
                    showNotice("Bangumi 账号已连接")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // Exception text ("Token 无效或已过期…") carries no failure wording: tag the tone.
                    mutableState.value = mutableState.value.withNotice(error.message ?: "账号验证失败", NoticeTone.ERROR)
                } finally {
                    mutableState.value = mutableState.value.copy(accountBusy = false)
                }
            }
    }

    fun cancelAccountConnection() {
        accountJob?.cancel()
    }

    /**
     * NextMoe's id_token carries no user name, so a fresh login (or an account saved before this fix)
     * shows a stand-in until the userinfo endpoint answers. Cosmetic: failures are silent and retried
     * at most once per [PROFILE_RETRY_MS]. [force] (right after a login) skips both checks so a renamed
     * account is picked up too.
     */
    fun refreshCoverProfile(
        source: CoverSourceMode,
        force: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        if (!force && (!accounts.needsProfile(source) || now - (profileAttemptAt[source] ?: 0L) < PROFILE_RETRY_MS)) return
        profileAttemptAt[source] = now
        viewModelScope.launch(Dispatchers.IO) { runCatching { accounts.refreshProfile(source) } }
    }

    fun disconnectCoverAccount(source: CoverSourceMode) {
        coverQueue.removeSource(source)
        if (mutableState.value.coverSearch?.source == source) {
            coverSearchJob?.cancel()
            coverSaveJob?.cancel()
            mutableState.value = mutableState.value.copy(coverSearch = mutableState.value.coverSearch?.copy(loading = false, saving = false, candidates = emptyList(), selectedKey = null))
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { accounts.disconnect(source) }
            withContext(Dispatchers.Main) {
                showNotice(if (result.isSuccess) "${source.label} 已断开" else "清除账号失败，请重试")
                host.onCoverAccountChanged(source)
            }
        }
    }

    fun completeCoverLogin(
        source: CoverSourceMode,
        success: Boolean,
        message: String,
        selectDefault: Boolean
    ) {
        // The login activity already knows the outcome; "授权已取消/已超时" must not read as success.
        mutableState.value = mutableState.value.withNotice(message, if (success) NoticeTone.SUCCESS else NoticeTone.WARNING)
        if (!success) return
        refreshCoverProfile(source, force = true)
        host.onCoverAccountChanged(source)
        autoCoverGate.reset()
        reload()
        if (selectDefault) setMetadataSource(source)
        // The picker is always hybrid, so a freshly connected source must re-run the whole search.
        if (mutableState.value.coverSearch != null) host.retryCoverSearch()
    }

    fun setIncludeAdult(value: Boolean) {
        accounts.setIncludesAdult(value)
        CoverSourceMode.active.forEach(coverQueue::removeSource)
        coverSearchJob?.cancel()
        mutableState.value =
            mutableState.value.copy(
                includeAdult = value,
                coverSearch = mutableState.value.coverSearch?.copy(loading = false, candidates = emptyList(), selectedKey = null)
            )
        autoCoverGate.reset()
        reload()
        // Re-run in place when the picker is open, so toggling it is enough to find adult-only works.
        if (mutableState.value.coverSearch != null) host.retryCoverSearch()
    }

    /** Persists a custom Bangumi API base (empty = automatic official → mirror fallback). */
    fun setBangumiApiBase(base: String) {
        core.settings.setBangumiApiBase(base)
        // A new mirror must be probed immediately instead of waiting out the old host's cooldown.
        CoverSourceHealth.reset()
        mutableState.value =
            mutableState.value.copy(
                settings = mutableState.value.settings?.copy(bangumiApiBase = base.trim()),
            )
        showNotice(if (base.isBlank()) "Bangumi 镜像地址已设为自动" else "Bangumi 镜像地址已保存")
    }
}

// ---------------------------------------------------------------------------------------------
// Cover picker (hybrid search)
// ---------------------------------------------------------------------------------------------
