package com.kireibox.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.yuki.yukihub.data.ScanImportSummary
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.ImmersiveGalleryAutoSource
import com.yuki.yukihub.settings.ImmersiveGallerySettings
import com.yuki.yukihub.settings.ImmersiveGallerySettingsPolicy
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.OrientationMode
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import com.yuki.yukihub.settings.ThemeMode
import com.yuki.yukihub.settings.ThemeModePolicy
import com.yuki.yukihub.settings.YukiSettingsStore
import com.yuki.yukihub.util.DevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class KireiSettingsController(host: KireiViewModel) : KireiViewModelController(host) {
    private val foregroundRefreshGate = ForegroundRefreshGate()

    fun setSortMode(mode: String) {
        core.settings.putString(com.yuki.yukihub.settings.YukiSettingsStore.KEY_SORT_MODE, mode)
        reload()
    }

    fun setUseBuiltinFileChooser(value: Boolean) {
        core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_USE_BUILTIN_FILE_CHOOSER, value)
        reload()
    }

    fun setAutoScanOnStartup(value: Boolean) {
        core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_AUTO_SCAN_ON_STARTUP, value)
        reload()
    }

    fun setKeepRemovedGamesHiddenOnRefresh(value: Boolean) {
        core.settings.putBoolean(
            com.yuki.yukihub.settings.YukiSettingsStore.KEY_KEEP_REMOVED_GAMES_HIDDEN_ON_REFRESH,
            value,
        )
        reload()
    }

    fun onAppForeground() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = core.settings.snapshot()
            if (!snapshot.autoScanOnStartup) return@launch
            if (!foregroundRefreshGate.tryStart()) return@launch
            try {
                val roots = DirectoryAutoRefreshPolicy.enabledRoots(snapshot.scanRoots, snapshot.scanRootEnabled)
                if (roots.isEmpty()) return@launch
                var failed = 0
                var pruned = 0
                var total = ScanImportSummary()
                roots.forEach { root ->
                    val parsed = runCatching { Uri.parse(root) }.getOrNull()
                    if (parsed == null) {
                        failed += 1
                        return@forEach
                    }
                    val report =
                        runCatching { core.scanUntilMatch(parsed) }.getOrElse {
                            failed += 1
                            return@forEach
                        }
                    total += core.importScan(report, snapshot.keepRemovedGamesHiddenOnRefresh)
                    pruned += core.pruneMissingGames(parsed, report)
                }
                val message = DirectoryAutoRefreshPolicy.notice(total, failed, pruned)
                val tone = DirectoryAutoRefreshPolicy.noticeTone(total, failed, pruned)
                withContext(Dispatchers.Main) {
                    if (message != null) mutableState.value = mutableState.value.withNotice(message, tone)
                }
                reloadInternal()
            } finally {
                foregroundRefreshGate.finish()
            }
        }
    }

    fun setKrEngineVersion(value: String) {
        core.settings.putString(com.yuki.yukihub.settings.YukiSettingsStore.KEY_KR_ENGINE_VERSION, value)
        reload()
    }

    fun setKrCompat(value: Boolean) {
        core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_KR_COMPAT_MODE, value)
        reload()
    }

    fun setArtemisScopedSaveDir(value: Boolean) {
        core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_ARTEMIS_SCOPED_SAVE_DIR, value)
        reload()
    }

    fun setTyranoScopedSaveDir(value: Boolean) {
        core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_TYRANO_SCOPED_SAVE_DIR, value)
        reload()
    }

    fun setKrSkipStartupInfoDialog(value: Boolean) {
        core.settings.putString(
            com.yuki.yukihub.settings.YukiSettingsStore.KEY_KR_STARTUP_DIALOG_POLICY,
            if (value) {
                com.yuki.yukihub.settings.YukiSettingsStore.DIALOG_POLICY_SKIP_SINGLE_BUTTON_STARTUP_INFO
            } else {
                com.yuki.yukihub.settings.YukiSettingsStore.DIALOG_POLICY_SHOW_ALL
            }
        )
        reload()
    }

    /**
     * Persists the theme mode and applies it to the in-memory snapshot first, so Compose switches
     * immediately (no activity recreate). The follow-up reload is latest-wins: an older reload that
     * read the preferences before this write can no longer publish its stale snapshot over it.
     */
    fun setThemeMode(mode: ThemeMode) {
        core.settings.setThemeMode(mode)
        updateSettingsSnapshot { it.copy(themeMode = mode, darkMode = ThemeModePolicy.legacyDarkFor(mode) ?: it.darkMode) }
        reloadImmersiveSettingsLatest()
    }

    fun setAccentChoice(choice: AccentChoice) {
        core.settings.setAccentChoice(choice)
        updateSettingsSnapshot { it.copy(accentChoice = choice) }
        reloadImmersiveSettingsLatest()
    }

    /**
     * 快速设置 finished, skipped or its suggestion dismissed: the settings root stops suggesting it.
     * Latest-wins like the theme setters: the guide's own steps start reloads, and one that read the
     * preferences before this write must not publish `quickSetupDone = false` over the optimistic update.
     */
    fun setQuickSetupDone(done: Boolean) {
        core.settings.setQuickSetupDone(done)
        updateSettingsSnapshot { it.copy(quickSetupDone = done) }
        reloadImmersiveSettingsLatest()
    }

    /** Compatibility shim for the home theme toggle: a boolean maps to an explicit DARK/LIGHT mode. */
    fun setDarkMode(value: Boolean) = setThemeMode(if (value) ThemeMode.DARK else ThemeMode.LIGHT)

    private fun updateSettingsSnapshot(transform: (YukiSettingsStore.Snapshot) -> YukiSettingsStore.Snapshot) {
        val current = mutableState.value
        current.settings?.let { mutableState.value = current.copy(settings = transform(it)) }
    }

    fun setMotionMode(value: MotionMode) {
        core.settings.setMotionMode(value)
        reload()
    }

    fun setHomePresentationMode(mode: HomePresentationMode) {
        if (testHomePresentationWriter != null && testReloadAction != null) {
            testHomePresentationWriter!!(mode)
            testReloadAction!!()
        } else {
            core.settings.setHomePresentationMode(mode)
            reload()
        }
    }

    fun setImmersivePlayTimeVisible(visible: Boolean) {
        core.settings.setImmersivePlayTimeVisible(visible)
        reload()
    }

    /**
     * Persists immersive visual effects as one complete snapshot and publishes it optimistically.
     * The reload is latest-wins (like the gallery setters): two quick taps cannot let the first
     * tap's reload, which read the preferences before the second write, flip the second switch
     * back, nor let a control then rebuild its copy(...) from that reverted snapshot.
     */
    fun setImmersiveVisual(next: ImmersiveVisualSettings) {
        val normalized = next.normalized()
        if (testImmersiveVisualWriter != null && testReloadAction != null) {
            testImmersiveVisualWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveVisual(normalized)
            updateSettingsSnapshot { it.copy(immersiveVisual = normalized) }
            reloadImmersiveSettingsLatest()
        }
    }

    fun setOrientationMode(mode: OrientationMode) {
        core.settings.setOrientationMode(mode)
        reload()
    }

    fun setImmersiveGalleryMode(mode: SpotlightSelectionMode) {
        if (testImmersiveGalleryModeWriter != null && testReloadAction != null) {
            testImmersiveGalleryModeWriter!!(mode)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryMode(mode)
            reloadImmersiveSettingsLatest()
        }
    }

    fun setImmersiveGalleryIds(ids: List<Long>) {
        val normalized = ImmersiveGallerySettingsPolicy.normalizeIds(ids)
        if (testImmersiveGalleryIdsWriter != null && testReloadAction != null) {
            testImmersiveGalleryIdsWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryIds(ids)
            reloadImmersiveSettingsLatest()
        }
    }

    fun setImmersiveGallerySelectionMode(mode: SpotlightSelectionMode) = setImmersiveGalleryMode(mode)

    fun setImmersiveGalleryManualIds(ids: List<Long>) = setImmersiveGalleryIds(ids)

    fun setImmersiveGalleryAutoSource(source: ImmersiveGalleryAutoSource) {
        if (testImmersiveGalleryAutoWriter != null && testReloadAction != null) {
            testImmersiveGalleryAutoWriter!!(source)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryAutoSource(source)
            reloadImmersiveSettingsLatest()
        }
    }

    fun setImmersiveGallerySource(source: SpotlightSource) {
        val normalized = ImmersiveGallerySettingsPolicy.normalizeSource(source)
        if (testImmersiveGallerySourceWriter != null && testReloadAction != null) {
            testImmersiveGallerySourceWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGallerySource(normalized)
            reloadImmersiveSettingsLatest()
        }
    }

    fun setImmersiveGalleryStrategy(strategy: SpotlightStrategy) {
        val normalized = strategy
        if (testImmersiveGalleryStrategyWriter != null && testReloadAction != null) {
            testImmersiveGalleryStrategyWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryStrategy(normalized)
            reloadImmersiveSettingsLatest()
        }
    }

    fun setImmersiveGalleryCount(count: Int) {
        val normalized = ImmersiveGallerySettingsPolicy.normalizeCount(count)
        if (testImmersiveGalleryCountWriter != null && testReloadAction != null) {
            testImmersiveGalleryCountWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryCount(normalized)
            reloadImmersiveSettingsLatest()
        }
    }

    fun setImmersiveGalleryMaxCount(count: Int) = setImmersiveGalleryCount(count)

    fun setImmersiveGallerySettings(settings: ImmersiveGallerySettings) {
        val normalized = settings.normalized()
        if (testImmersiveGallerySettingsWriter != null && testReloadAction != null) {
            testImmersiveGallerySettingsWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGallerySettings(normalized)
            reloadImmersiveSettingsLatest()
        }
    }

    fun setReducedMotion(value: Boolean) {
        setMotionMode(if (value) MotionMode.REDUCED else MotionMode.AUTO)
    }

    fun setReducedTransparency(value: Boolean) {
        core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_REDUCED_TRANSPARENCY, value)
        updateSettingsSnapshot { it.copy(reducedTransparency = value) }
        reload()
    }

    fun setContrast(value: Boolean) {
        core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_INCREASED_CONTRAST, value)
        updateSettingsSnapshot { it.copy(increasedContrast = value) }
        reload()
    }

    fun setDiagnosticLogging(value: Boolean) {
        DevLogger.setEnabled(hostApplication, value)
        mutableState.value =
            mutableState.value.copy(
                diagnosticLoggingEnabled = DevLogger.isEnabled(),
                notice = if (value) "诊断日志已开启" else "诊断日志已关闭",
            )
    }

    fun saveDashboardLayout(
        profile: DashboardProfile,
        layout: DashboardLayout
    ) {
        core.settings.saveDashboardLayout(profile, layout)
        val normalized = DashboardLayouts.normalize(layout, profile)
        mutableState.value =
            if (profile == DashboardProfile.COMPACT) {
                mutableState.value.copy(dashboardCompact = normalized, notice = "手机首页布局已保存")
            } else {
                mutableState.value.copy(dashboardWide = normalized, notice = "宽屏首页布局已保存")
            }
        reload()
    }
}
