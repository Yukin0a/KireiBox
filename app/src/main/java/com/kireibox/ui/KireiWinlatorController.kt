package com.kireibox.ui

import com.kireibox.external.winlator.WinlatorAvailability
import com.kireibox.external.winlator.WinlatorGameOverride
import com.kireibox.external.winlator.WinlatorLauncher
import com.kireibox.external.winlator.WinlatorSettingsStore
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the first-use Winlator guide is: nothing, the one-time notice, or the tutorial sheet. */
enum class WinlatorGuideStage { NONE, NOTICE, SHEET }

/**
 * When the first-use guide speaks up. Winlator keeps its containers in private data and its launch
 * entry returns nothing, so KireiBox cannot tell whether a container exists: it says so once, right
 * after the first exe game is added, and the user confirms when it is done.
 */
internal object WinlatorGuidePolicy {
    fun shouldRaiseNotice(
        added: EngineType,
        guideDone: Boolean,
        noticeShown: Boolean,
    ): Boolean = added == EngineType.WINDOWS && !guideDone && !noticeShown

    fun noticeMessage(availability: WinlatorAvailability): String =
        when (availability) {
            WinlatorAvailability.READY -> "首次使用 Winlator 前，需要先在其中创建一个默认容器"
            WinlatorAvailability.NOT_INSTALLED -> "未安装 Winlator，安装并创建容器后才能运行 PC 游戏"
            WinlatorAvailability.UNSUPPORTED -> "Winlator 版本过旧，不支持外置启动，请更新"
        }
}

/** Per-game Winlator choices, the first-use guide, and the two states they live in. */
internal class KireiWinlatorController(host: KireiViewModel) : KireiViewModelController(host) {
    private val store by lazy { WinlatorSettingsStore(hostApplication) }

    fun openEditor(game: Game) {
        mutableState.value = mutableState.value.copy(selectedGame = null, winlatorEditorGame = game, winlatorOverride = store.loadGame(game.id))
    }

    fun closeEditor() {
        mutableState.value = KireiBackPolicy.closeWinlatorEditor(mutableState.value)
    }

    fun saveOverride(
        game: Game,
        override: WinlatorGameOverride,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            store.saveGame(game.id, override)
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(winlatorOverride = override, winlatorEditorGame = null, notice = "Winlator 设置已保存")
            }
        }
    }

    /** Called when a game was added; raises the notice once, for the first exe game. */
    fun onGameAdded(engine: EngineType) {
        if (!WinlatorGuidePolicy.shouldRaiseNotice(engine, store.guideDone, store.guideNoticeShown)) return
        store.guideNoticeShown = true
        mutableState.value = mutableState.value.copy(winlatorGuide = WinlatorGuideStage.NOTICE)
    }

    fun openGuide() {
        mutableState.value = mutableState.value.copy(winlatorGuide = WinlatorGuideStage.SHEET)
    }

    fun dismissGuide() {
        mutableState.value = mutableState.value.copy(winlatorGuide = WinlatorGuideStage.NONE)
    }

    fun completeGuide() {
        store.guideDone = true
        mutableState.value = mutableState.value.copy(winlatorGuide = WinlatorGuideStage.NONE)
    }

    fun noticeMessage(): String = WinlatorGuidePolicy.noticeMessage(WinlatorLauncher.status(hostApplication).availability)
}
