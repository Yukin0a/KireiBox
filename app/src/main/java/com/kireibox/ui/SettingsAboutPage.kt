package com.kireibox.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.kireiE2Shadow
import com.yuki.yukihub.BuildConfig

// 日志与诊断 and 关于 (plan §5 P3): the same list-group anatomy as every other settings page.

// Public project links: the repository and its THIRD_PARTY_NOTICES.
internal const val KIREI_GITHUB_URL = "https://github.com/Yukin0a/KireiBox"
internal const val KIREI_THIRD_PARTY_NOTICES_URL = "$KIREI_GITHUB_URL/blob/main/THIRD_PARTY_NOTICES.md"

/** Opens [url] in the browser (or the app that handles it); [onFailure] when nothing can. */
internal fun openKireiLink(
    context: Context,
    url: String,
    onFailure: () -> Unit,
) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { onFailure() }
}

@Composable
internal fun DiagnosticSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    actions: KireiActions,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "记录") {
        item { SwitchRow("记录诊断日志", state.diagnosticLoggingEnabled, colors, viewModel::setDiagnosticLogging) }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "分享") {
        item {
            SettingsAnchor("导出日志") {
                KireiListRow(
                    KireiListRowSpec("导出日志", icon = Icons.Rounded.Description, tile = KireiTileColor.BLUE, trailing = KireiRowTrailing.Chevron),
                    colors,
                    onClick = actions.exportLogs,
                )
            }
        }
        item {
            SettingsAnchor("复制日志") {
                KireiListRow(
                    // A command (copies at once): ACTION tone, like every other immediate command row.
                    KireiListRowSpec("复制日志", icon = Icons.Rounded.ContentCopy, tile = KireiTileColor.SLATE, tone = KireiRowTone.ACTION),
                    colors,
                    onClick = actions.copyLogs,
                )
            }
        }
    }
}

@Composable
internal fun AboutSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val context = LocalContext.current
    val open: (String) -> Unit = { url -> openKireiLink(context, url) { viewModel.showNotice("无法打开浏览器") } }
    AboutBrandHeader(colors)
    KireiListGroup(colors, SettingsGroupSpacing, title = "版本") {
        item {
            SettingsAnchor("检查更新") {
                KireiListRow(
                    updateRowSpec(state.updateState, KireiTileColor.INDIGO, lastUpdateCheckLabel(state.settings?.lastUpdateCheckAt)),
                    colors,
                    onClick = { viewModel.checkForUpdates(manual = true) },
                )
            }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "社区与许可") {
        item {
            SettingsAnchor("GitHub") {
                KireiListRow(
                    KireiListRowSpec("GitHub", "github.com/Yukin0a/KireiBox", icon = Icons.Rounded.Code, tile = KireiTileColor.SLATE, trailing = KireiRowTrailing.Chevron),
                    colors,
                    onClick = { open(KIREI_GITHUB_URL) },
                )
            }
        }
        item {
            SettingsAnchor("第三方声明") {
                KireiListRow(
                    KireiListRowSpec("第三方声明", icon = Icons.Rounded.Gavel, tile = KireiTileColor.GRAY, trailing = KireiRowTrailing.Chevron),
                    colors,
                    onClick = { open(KIREI_THIRD_PARTY_NOTICES_URL) },
                )
            }
        }
    }
}

private val AboutMascotSize = 96.dp

/** Brand header: the mascot tile, the text wordmark and the version in numeric style. */
@Composable
private fun AboutBrandHeader(colors: KireiColors) {
    Column(
        Modifier.fillMaxWidth().padding(top = KireiSpacing.xs, bottom = KireiSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KireiMascot(
            Modifier.size(AboutMascotSize).kireiE2Shadow(KireiShapes.lg, colors.isDark).clip(KireiShapes.lg),
            contentDescription = null,
        )
        Text(
            "KireiBox",
            color = colors.text,
            style = AppleTypography.displayMedium,
            modifier = Modifier.padding(top = KireiSpacing.md).semantics { heading() },
        )
        Text(BuildConfig.VERSION_NAME, color = colors.secondary, style = AppleTypography.numericSmall, modifier = Modifier.padding(top = KireiSpacing.xxs))
    }
}
