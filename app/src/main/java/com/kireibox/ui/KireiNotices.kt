package com.kireibox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.yuki.yukihub.settings.MotionMode
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Transient e3 toasts: the top notice and the bottom undo-removal notice.

/** Kind of a transient notice; decides its leading glyph and token color. */
enum class NoticeTone { INFO, SUCCESS, WARNING, ERROR }

/**
 * A tone chosen by the code that raised a notice (failure branches show exception text, which has
 * no reliable wording). It belongs to one message: once another notice replaces it, it no longer
 * applies, so a later success can never inherit a stale error tone.
 */
data class NoticeToneTag(
    val message: String,
    val tone: NoticeTone,
)

/** Raises [message] with an explicit [tone]. */
internal fun KireiUiState.withNotice(
    message: String,
    tone: NoticeTone,
): KireiUiState = copy(notice = message, noticeTone = NoticeToneTag(message, tone))

/**
 * Tone of a notice: the tag set where it was raised when it matches [message], otherwise inferred
 * from the wording, conservatively: failures read as errors, "请…" requests as warnings, completed
 * actions ("已…" / "…已保存") as success, and everything else stays INFO.
 */
internal object NoticeTonePolicy {
    fun resolve(
        message: String,
        tag: NoticeToneTag?,
    ): NoticeTone = tag?.takeIf { it.message == message }?.tone ?: infer(message)

    private val errorWords = listOf("失败", "无法", "不支持", "错误")
    private val successWords = listOf("已保存", "已更新", "已连接", "已断开", "已开启", "已关闭", "已设为", "已添加", "已导入", "已恢复", "已清除")

    fun infer(message: String): NoticeTone {
        val text = message.trim()
        return when {
            errorWords.any(text::contains) -> NoticeTone.ERROR
            text.startsWith("请") -> NoticeTone.WARNING
            text.startsWith("已") || successWords.any(text::contains) -> NoticeTone.SUCCESS
            else -> NoticeTone.INFO
        }
    }
}

private fun NoticeTone.icon(): ImageVector =
    when (this) {
        NoticeTone.INFO -> Icons.Rounded.Info
        NoticeTone.SUCCESS -> Icons.Rounded.CheckCircle
        NoticeTone.WARNING -> Icons.Rounded.Warning
        NoticeTone.ERROR -> Icons.Rounded.Error
    }

private fun NoticeTone.color(colors: KireiColors): Color =
    when (this) {
        NoticeTone.INFO -> colors.accent
        NoticeTone.SUCCESS -> colors.success
        NoticeTone.WARNING -> colors.warning
        NoticeTone.ERROR -> colors.danger
    }

/** Spoken tone prefix; INFO stays silent so neutral notices read as just their message. */
private fun NoticeTone.spokenLabel(): String? =
    when (this) {
        NoticeTone.INFO -> null
        NoticeTone.SUCCESS -> "成功"
        NoticeTone.WARNING -> "注意"
        NoticeTone.ERROR -> "错误"
    }

/**
 * Accessibility of a transient notice (WCAG 4.1.3 / 2.2.1): errors interrupt (assertive), the rest
 * wait their turn (polite), and the visible time follows the user's accessibility timeout.
 */
internal object NoticeA11yPolicy {
    const val NOTICE_MILLIS = 2_200L
    const val UNDO_MILLIS = 5_000L

    fun liveRegion(tone: NoticeTone): LiveRegionMode = if (tone == NoticeTone.ERROR) LiveRegionMode.Assertive else LiveRegionMode.Polite
}

/** [base] stretched to the user's "time to take action" accessibility setting, when there is one. */
@Composable
private fun rememberNoticeTimeout(
    base: Long,
    containsControls: Boolean,
): Long {
    val manager = LocalAccessibilityManager.current
    return remember(manager, base, containsControls) {
        manager?.calculateRecommendedTimeoutMillis(base, containsIcons = true, containsText = true, containsControls = containsControls) ?: base
    }
}

/** Tone glyph on its own tinted well (the accentContainer alpha applied to the tone color). */
@Composable
private fun NoticeToneIcon(
    tone: NoticeTone,
    colors: KireiColors,
) {
    val color = tone.color(colors)
    Box(
        Modifier.size(NoticeIconWell).background(KireiTokens.accentContainer(color, colors.isDark), KireiShapes.full),
        contentAlignment = Alignment.Center,
    ) {
        Icon(tone.icon(), contentDescription = tone.spokenLabel(), tint = color, modifier = Modifier.size(NoticeIconSize))
    }
}

/** Spec §7: under reduced motion notices only fade; otherwise they also slide from their edge. */
private fun noticeEnter(
    reducedMotion: Boolean,
    fromTop: Boolean,
): EnterTransition =
    if (reducedMotion) {
        fadeIn(tween(AppleMotion.Duration.SHORT))
    } else {
        fadeIn(tween(180)) + slideInVertically(tween(220)) { if (fromTop) -it else it }
    }

private fun noticeExit(
    reducedMotion: Boolean,
    fromTop: Boolean,
): ExitTransition =
    if (reducedMotion) {
        fadeOut(tween(AppleMotion.Duration.SHORT))
    } else {
        fadeOut(tween(150)) + slideOutVertically(tween(150)) { if (fromTop) -it else it }
    }

private val NoticeIconWell = 30.dp
private val NoticeIconSize = 18.dp
private val NoticeMaxWidth = 520.dp

@Composable
internal fun Notice(
    message: String,
    tone: NoticeTone,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
) {
    var visible by remember(message) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val close: () -> Unit = {
        visible = false
        scope.launch {
            delay(150)
            onDismiss()
        }
    }
    val timeout = rememberNoticeTimeout(NoticeA11yPolicy.NOTICE_MILLIS, containsControls = false)
    LaunchedEffect(message) {
        visible = true
        delay(timeout)
        close()
    }
    AnimatedVisibility(
        visible = visible,
        enter = noticeEnter(reducedMotion, fromTop = true),
        exit = noticeExit(reducedMotion, fromTop = true),
    ) {
        // Page gutter and xxs below the status bar: the notice spans the top bar's whole slot row
        // (back button to actions) instead of half-covering the buttons at its rounded ends.
        Box(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = AdaptiveLayoutConstants.PAGE_GUTTER_DP.dp, vertical = KireiSpacing.xxs),
            contentAlignment = Alignment.TopCenter,
        ) {
            GlassSurface(
                colors,
                haze,
                // TalkBack announces the notice when it appears (it is a live region).
                Modifier.fillMaxWidth().widthIn(max = NoticeMaxWidth).semantics { liveRegion = NoticeA11yPolicy.liveRegion(tone) },
                KireiShapes.lg,
                KireiGlassRole.NOTICE,
                close
            ) {
                Row(
                    Modifier.padding(start = KireiSpacing.sm, end = KireiSpacing.md, top = KireiSpacing.sm, bottom = KireiSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoticeToneIcon(tone, colors)
                    Text(
                        message,
                        color = colors.text,
                        style = AppleTypography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(start = KireiSpacing.sm, end = KireiSpacing.xs),
                    )
                    Icon(Icons.Rounded.Close, "关闭", tint = colors.tertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun UndoRemovalNotice(
    title: String,
    colors: KireiColors,
    haze: HazeState?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    var visible by remember(title) { mutableStateOf(false) }
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val timeout = rememberNoticeTimeout(NoticeA11yPolicy.UNDO_MILLIS, containsControls = true)
    LaunchedEffect(title) {
        visible = true
        delay(timeout)
        visible = false
        delay(150)
        onDismiss()
    }
    AnimatedVisibility(
        visible = visible,
        enter = noticeEnter(reducedMotion, fromTop = false),
        exit = noticeExit(reducedMotion, fromTop = false),
    ) {
        Box(
            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 92.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            GlassSurface(
                colors,
                haze,
                Modifier.fillMaxWidth().widthIn(max = NoticeMaxWidth).semantics { liveRegion = LiveRegionMode.Polite },
                KireiShapes.lg,
                KireiGlassRole.NOTICE,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = KireiSpacing.sm, end = KireiSpacing.xxs, top = KireiSpacing.xxs, bottom = KireiSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoticeToneIcon(NoticeTone.INFO, colors)
                    Spacer(Modifier.width(KireiSpacing.sm))
                    Text(
                        "已移出 $title",
                        color = colors.text,
                        style = AppleTypography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    KireiButton(
                        "撤销",
                        colors,
                        options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                    ) {
                        visible = false
                        onUndo()
                    }
                }
            }
        }
    }
}

internal data class ActionNoticeSpec(
    val message: String,
    val actionLabel: String,
    val tone: NoticeTone,
    val topOffset: Dp,
)

/**
 * A top notice that stays until the user acts on it or closes it (the first-use Winlator guide):
 * the same glass strip as [Notice], with a text action. [topOffset] keeps it clear of a transient
 * notice that is showing in the same slot.
 */
@Composable
internal fun ActionNotice(
    spec: ActionNoticeSpec,
    colors: KireiColors,
    haze: HazeState?,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = spec.message
    val tone = spec.tone
    var visible by remember(message) { mutableStateOf(false) }
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    LaunchedEffect(message) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = noticeEnter(reducedMotion, fromTop = true),
        exit = noticeExit(reducedMotion, fromTop = true),
    ) {
        Box(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = AdaptiveLayoutConstants.PAGE_GUTTER_DP.dp).padding(top = KireiSpacing.xxs + spec.topOffset),
            contentAlignment = Alignment.TopCenter,
        ) {
            GlassSurface(
                colors,
                haze,
                Modifier.fillMaxWidth().widthIn(max = NoticeMaxWidth).semantics { liveRegion = NoticeA11yPolicy.liveRegion(tone) },
                KireiShapes.lg,
                KireiGlassRole.NOTICE,
                onAction,
            ) {
                Row(
                    Modifier.padding(start = KireiSpacing.sm, end = KireiSpacing.xxs, top = KireiSpacing.xxs, bottom = KireiSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoticeToneIcon(tone, colors)
                    Text(
                        message,
                        color = colors.text,
                        style = AppleTypography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = KireiSpacing.sm, vertical = KireiSpacing.xs),
                    )
                    KireiButton(
                        spec.actionLabel,
                        colors,
                        options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                        onClick = onAction,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "关闭提示", tint = colors.tertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
