package com.kireibox.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiSheet
import com.kireibox.designsystem.components.KireiSheetSpec
import com.kireibox.external.winlator.WinlatorAvailability
import com.kireibox.external.winlator.WinlatorLauncher
import com.kireibox.external.winlator.WinlatorStatus
import dev.chrisbanes.haze.HazeState

// First-use Winlator guide: a notice after the first exe game is added, and the tutorial sheet it
// (and 设置 › Windows 引擎设置) opens. KireiBox cannot see Winlator's containers, so the sheet is
// the whole check: the user confirms with 「我已创建容器」.

internal const val WINLATOR_GUIDE_TITLE = "创建 Winlator 容器"

/** The steps the sheet lists, in order. The first one also shows whether Winlator is installed. */
internal val WinlatorGuideSteps =
    listOf(
        "安装 winlator-cn（v0.4.2.11.2 或更新版本）",
        "打开 Winlator，等它完成首次初始化",
        "新建一个容器，名称和选项保持默认即可",
        "回到 KireiBox，点击游戏启动",
    )

internal fun winlatorInstallLabel(availability: WinlatorAvailability): String =
    when (availability) {
        WinlatorAvailability.READY -> "已安装"
        WinlatorAvailability.NOT_INSTALLED -> "未安装"
        WinlatorAvailability.UNSUPPORTED -> "版本过旧，需要更新"
    }

/** The installed Winlator's status, read again whenever the user comes back (after installing or updating it). */
@Composable
internal fun rememberWinlatorStatus(): WinlatorStatus {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(resumes) { WinlatorLauncher.status(context) }
}

/** Opens Winlator's own launcher screen; false when it is not installed or the system refuses. */
internal fun openWinlatorApp(
    context: Context,
    status: WinlatorStatus,
): Boolean {
    val intent = status.packageName?.let { WinlatorLauncher.openAppIntent(context, it) }
    return intent != null && runCatching { context.startActivity(intent) }.isSuccess
}

@Composable
internal fun WinlatorGuideNotice(
    colors: KireiColors,
    haze: HazeState?,
    stacked: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val availability = rememberWinlatorStatus().availability
    val spec =
        ActionNoticeSpec(
            message = WinlatorGuidePolicy.noticeMessage(availability),
            actionLabel = "查看教程",
            tone = if (availability == WinlatorAvailability.READY) NoticeTone.INFO else NoticeTone.WARNING,
            topOffset = if (stacked) NoticeStackOffset else 0.dp,
        )
    ActionNotice(spec, colors, haze, onOpen, onDismiss)
}

private val NoticeStackOffset = 64.dp

@Composable
internal fun WinlatorGuideSheet(
    colors: KireiColors,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val status = rememberWinlatorStatus()
    KireiSheet(colors, onDismiss, KireiSheetSpec(title = WINLATOR_GUIDE_TITLE, expanded = true, scrollable = false)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KireiSpacing.lg, vertical = KireiSpacing.sm)) {
            WinlatorGuideSteps.forEachIndexed { index, step ->
                WinlatorGuideStep(index + 1, step, if (index == 0) winlatorInstallLabel(status.availability) else null, colors)
            }
            WinlatorGuideImageSlot(colors)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = KireiSpacing.lg, vertical = KireiSpacing.sm), verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
            if (status.availability == WinlatorAvailability.READY) {
                KireiButton("打开 Winlator", colors, Modifier.fillMaxWidth()) { openWinlatorApp(context, status) }
            } else {
                KireiButton("下载 Winlator-CN", colors, Modifier.fillMaxWidth()) { openKireiLink(context, WINLATOR_CN_RELEASES_URL) {} }
            }
            KireiButton(
                "我已创建容器",
                colors,
                Modifier.fillMaxWidth(),
                KireiButtonOptions(style = KireiButtonStyle.SECONDARY),
                onDone,
            )
        }
    }
}

@Composable
private fun WinlatorGuideStep(
    number: Int,
    text: String,
    note: String?,
    colors: KireiColors,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = KireiSpacing.xs).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Box(Modifier.size(StepBadgeSize).background(colors.accentContainer, KireiShapes.full), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = colors.accent, style = AppleTypography.numericSmall)
        }
        Column(Modifier.weight(1f).padding(start = KireiSpacing.sm)) {
            Text(text, color = colors.text, style = AppleTypography.bodyLarge)
            note?.let { Text(it, color = colors.secondary, style = AppleTypography.bodySmall) }
        }
    }
}

/** Where the illustrated walkthrough goes; empty until the tutorial images exist. */
@Composable
private fun WinlatorGuideImageSlot(colors: KireiColors) {
    Box(
        Modifier.fillMaxWidth()
            .padding(top = KireiSpacing.md)
            .aspectRatio(IMAGE_SLOT_ASPECT)
            .background(colors.surface3, KireiShapes.lg)
            .border(KireiElevation.strokeWidth, colors.strokeStrong, KireiShapes.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text("图文教程", color = colors.secondary, style = AppleTypography.bodyMedium, textAlign = TextAlign.Center)
    }
}

private val StepBadgeSize = 28.dp
private const val IMAGE_SLOT_ASPECT = 16f / 9f
