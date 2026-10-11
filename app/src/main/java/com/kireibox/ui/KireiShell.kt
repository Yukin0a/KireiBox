package com.kireibox.ui
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogActions
import com.kireibox.designsystem.components.KireiDialogBody
import com.kireibox.designsystem.components.KireiDialogDefaults
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiProgressBar
import com.kireibox.update.UpdateUiState
import com.yuki.yukihub.BuildConfig
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Prompt hosts, update prompt and shared formatters.

@Composable
internal fun KireiPromptHost(
    prompt: KireiPrompt?,
    colors: KireiColors,
    haze: HazeState?,
    reducedMotion: Boolean,
    onPromptDismiss: () -> Unit
) {
    var displayedPrompt by remember { mutableStateOf(prompt) }
    var visible by remember { mutableStateOf(prompt != null) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val enterDuration = if (reducedMotion) 80 else 180
    val exitDuration = if (reducedMotion) 80 else 140
    val cardScale by animateFloatAsState(
        targetValue = if (visible || reducedMotion) 1f else .94f,
        animationSpec = tween(if (visible) enterDuration else exitDuration),
        label = "promptScale"
    )

    LaunchedEffect(prompt) {
        if (prompt != null) {
            displayedPrompt = prompt
            closing = false
            visible = true
        } else if (displayedPrompt != null && !closing) {
            visible = false
            delay(exitDuration.toLong())
            displayedPrompt = null
        }
    }

    fun close(confirm: Boolean) {
        if (closing) return
        val active = displayedPrompt ?: return
        closing = true
        visible = false
        scope.launch {
            delay(exitDuration.toLong())
            displayedPrompt = null
            closing = false
            onPromptDismiss()
            if (confirm) active.onConfirm() else active.onDismiss()
        }
    }

    val activePrompt = displayedPrompt
    // Keyed on the prompt so each prompt registers its handler anew, after every page handler (the
    // dispatcher favours the newest): back closes the prompt, not e.g. the settings search beneath it.
    key(activePrompt) {
        BackHandler(enabled = activePrompt?.dismissLabel != null && !closing) { close(false) }
    }
    AnimatedVisibility(
        visible = activePrompt != null && visible,
        enter = fadeIn(tween(enterDuration)),
        exit = fadeOut(tween(exitDuration))
    ) {
        Box(
            // The scrim swallows touches without becoming an unlabeled focusable node for TalkBack.
            Modifier.fillMaxSize()
                .background(colors.scrim)
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            activePrompt?.let { current ->
                GlassSurface(
                    colors = colors,
                    haze = haze,
                    modifier =
                        Modifier.semantics { paneTitle = current.title }
                            .padding(horizontal = KireiDialogDefaults.WindowMargin)
                            .widthIn(max = KireiDialogDefaults.MaxWidth)
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                            },
                    shape = KireiShapes.xl,
                    role = KireiGlassRole.ALERT
                ) {
                    // KireiDialog look (spec §8) on the e3 alert glass of the main window.
                    Column(Modifier.fillMaxWidth().padding(KireiDialogDefaults.ContentPadding)) {
                        KireiDialogBody(
                            KireiDialogSpec(
                                title = current.title,
                                message = current.message,
                                confirm = KireiDialogAction(current.confirmLabel, { close(true) }, enabled = !closing),
                                dismiss =
                                    current.dismissLabel?.let { label ->
                                        KireiDialogAction(label, { close(false) }, style = KireiButtonStyle.PLAIN, enabled = !closing)
                                    },
                            ),
                            colors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpdatePromptHost(
    state: UpdateUiState,
    colors: KireiColors,
    haze: HazeState?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onDiscard: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == UpdateUiState.Idle || state == UpdateUiState.Checking(manual = false)) return
    BackHandler {
        when (updatePromptBackAction(state)) {
            UpdatePromptBackAction.LATER -> onIgnore()
            UpdatePromptBackAction.CANCEL_DOWNLOAD -> onCancelDownload()
            UpdatePromptBackAction.DISCARD_READY -> onDiscard()
        }
    }
    Box(
        Modifier.fillMaxSize()
            .background(colors.scrim)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            colors = colors,
            haze = haze,
            modifier =
                Modifier.semantics { paneTitle = "更新" }
                    .padding(horizontal = KireiDialogDefaults.WindowMargin)
                    .widthIn(max = KireiDialogDefaults.MaxWidth)
                    .fillMaxWidth(),
            shape = KireiShapes.xl,
            role = KireiGlassRole.ALERT,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(KireiDialogDefaults.ContentPadding),
                verticalArrangement = Arrangement.spacedBy(KireiSpacing.sm),
            ) {
                when (state) {
                    is UpdateUiState.Checking -> {
                        UpdatePromptTitle("正在检查更新", colors)
                        KireiProgressBar(progress = null, colors = colors)
                    }
                    is UpdateUiState.Available -> {
                        val context = LocalContext.current
                        UpdatePromptTitle("发现新版本", colors)
                        Text(
                            "${BuildConfig.VERSION_NAME}  →  ${state.release.versionName} · ${formatUpdateBytes(state.release.apkSize)}",
                            color = colors.secondary,
                            style = AppleTypography.numericSmall,
                        )
                        if (state.release.notes.isNotBlank()) {
                            Text(
                                state.release.notes,
                                color = colors.secondary,
                                style = AppleTypography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                        // "稍后更新" also remembers the version, so it stops prompting until a
                        // genuinely newer release appears.
                        UpdatePromptButtons("稍后更新", onIgnore, "更新", onDownload, colors)
                        KireiButton(
                            "在 GitHub 上查看",
                            colors,
                            Modifier.align(Alignment.CenterHorizontally),
                            KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                        ) {
                            val url =
                                state.release.pageUrl.ifBlank {
                                    "https://github.com/Yukin0a/KireiBox/releases/latest"
                                }
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }.onFailure { }
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        val progress =
                            if (state.totalBytes > 0L) {
                                (state.bytesRead.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        UpdatePromptTitle("正在下载更新", colors)
                        KireiProgressBar(progress = progress, colors = colors)
                        Text(
                            "${formatUpdateBytes(state.bytesRead)} / ${formatUpdateBytes(state.totalBytes)} · ${formatUpdateBytes(state.bytesPerSecond)}/秒",
                            color = colors.secondary,
                            style = AppleTypography.numericSmall,
                        )
                        UpdateSinglePromptButton("取消下载", onCancelDownload, colors, emphasized = false)
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        UpdatePromptTitle("更新已校验", colors)
                        Text(
                            if (state.release.hasStrictManifest) {
                                "包名、版本、ABI、SDK、SHA-256 和签名证书均已通过校验。安装仍需由 Android 系统确认。"
                            } else {
                                "包名、版本、ABI、SDK 和已安装应用签名均已通过校验。安装仍需由 Android 系统确认。"
                            },
                            color = colors.secondary,
                            style = AppleTypography.bodyMedium,
                        )
                        UpdatePromptButtons("稍后安装", onDiscard, "安装更新", { onInstall(state.apkPath) }, colors)
                    }
                    is UpdateUiState.Error -> {
                        UpdatePromptTitle(if (state.release == null) "更新检查失败" else "更新失败", colors)
                        Text(state.message, color = colors.secondary, style = AppleTypography.bodyMedium)
                        if (state.release == null) {
                            UpdateSinglePromptButton("关闭", onDismiss, colors, emphasized = false)
                        } else {
                            UpdatePromptButtons("关闭", onDismiss, "重试下载", onDownload, colors)
                        }
                    }
                    UpdateUiState.Idle -> Unit
                }
            }
        }
    }
}

internal enum class UpdatePromptBackAction { LATER, CANCEL_DOWNLOAD, DISCARD_READY }

internal fun updatePromptBackAction(state: UpdateUiState): UpdatePromptBackAction =
    when (state) {
        is UpdateUiState.Downloading -> UpdatePromptBackAction.CANCEL_DOWNLOAD
        is UpdateUiState.ReadyToInstall -> UpdatePromptBackAction.DISCARD_READY
        else -> UpdatePromptBackAction.LATER
    }

@Composable
private fun UpdatePromptTitle(
    title: String,
    colors: KireiColors,
) {
    Text(title, color = colors.text, style = AppleTypography.headlineMedium)
}

/** KireiDialog actions: neutral [secondaryLabel] and the primary [primaryLabel], right-aligned. */
@Composable
internal fun UpdatePromptButtons(
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    colors: KireiColors,
) {
    KireiDialogActions(
        confirm = KireiDialogAction(primaryLabel, onPrimary),
        dismiss = KireiDialogAction(secondaryLabel, onSecondary, style = KireiButtonStyle.PLAIN),
        colors = colors,
        modifier = Modifier.padding(top = KireiSpacing.xs),
    )
}

@Composable
internal fun UpdateSinglePromptButton(
    label: String,
    onClick: () -> Unit,
    colors: KireiColors,
    emphasized: Boolean,
) {
    KireiDialogActions(
        confirm = KireiDialogAction(label, onClick, style = if (emphasized) KireiButtonStyle.PRIMARY else KireiButtonStyle.SECONDARY),
        dismiss = null,
        colors = colors,
        modifier = Modifier.padding(top = KireiSpacing.xs),
    )
}

internal fun formatUpdateBytes(bytes: Long): String =
    when {
        bytes <= 0L -> "未知大小"
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024L -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }

/**
 * Saturation only exists as a RenderEffect on API 31+; older API levels simply keep the
 * full-saturation artwork, matching the plan's "effect enabled on API 31+" rule.
 */
internal fun Modifier.saturationEffect(saturation: Float): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && saturation != 1f) {
        graphicsLayer {
            renderEffect =
                android.graphics.RenderEffect.createColorFilterEffect(
                    android.graphics.ColorMatrixColorFilter(
                        android.graphics.ColorMatrix().apply { setSaturation(saturation) }
                    )
                ).asComposeRenderEffect()
        }
    } else {
        this
    }

internal fun formatFileSize(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        else -> String.format("%.1f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
    }
