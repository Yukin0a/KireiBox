package com.kireibox.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiStatusChip
import com.kireibox.designsystem.components.KireiStatusTone
import com.yuki.yukihub.metadata.model.CoverSourceMode

// 来源账号 (plan §5 P3; a row of 封面与元数据 and a shortcut on the settings root): one card per
// account-backed cover source with its avatar, status chip and the 登录 / 设置令牌 / 退出 buttons.
// Signing out always asks first.

/** Cover sources that have an account (Bangumi works anonymously; the others need a login). */
internal val CoverAccountSources = listOf(CoverSourceMode.BANGUMI, CoverSourceMode.NEXTMOE, CoverSourceMode.HIKARINAGI)

internal enum class CoverAccountState { SIGNED_IN, NEEDS_REAUTH, ANONYMOUS, SIGNED_OUT }

internal fun coverAccountState(
    source: CoverSourceMode,
    status: com.yuki.yukihub.auth.CoverAccountStatus?,
): CoverAccountState =
    when {
        status?.needsReauth == true -> CoverAccountState.NEEDS_REAUTH
        status?.connected == true -> CoverAccountState.SIGNED_IN
        source == CoverSourceMode.BANGUMI -> CoverAccountState.ANONYMOUS
        else -> CoverAccountState.SIGNED_OUT
    }

/** What one account button does; the card maps it to the login activity, the token dialog or the sign-out dialog. */
internal enum class CoverAccountAction { SIGN_IN, SIGN_OUT, CANCEL }

internal data class CoverAccountButton(
    val label: String,
    val style: KireiButtonStyle,
    val action: CoverAccountAction,
)

/**
 * Buttons of one account card. Only a broken account (需要重新授权) gets a filled primary button,
 * so the page never shows three competing calls to action; signing in or out is secondary.
 * Signed-in accounts can sign in again (switch or refresh the account) as well as 退出. Bangumi
 * signs in with a personal token, so its sign-in reads 设置令牌 / 更换令牌; while a token is being
 * verified the only action is 取消验证 (退出 then would race the verification).
 */
internal fun coverAccountButtons(
    source: CoverSourceMode,
    state: CoverAccountState,
    busy: Boolean,
): List<CoverAccountButton> {
    val bangumi = source == CoverSourceMode.BANGUMI
    if (bangumi && busy) return listOf(CoverAccountButton("取消验证", KireiButtonStyle.SECONDARY, CoverAccountAction.CANCEL))
    val signOut = CoverAccountButton("退出", KireiButtonStyle.SECONDARY, CoverAccountAction.SIGN_OUT)
    val signInLabel =
        when {
            bangumi && state == CoverAccountState.ANONYMOUS -> "设置令牌"
            bangumi -> "更换令牌"
            state == CoverAccountState.SIGNED_OUT -> "登录"
            else -> "重新登录"
        }
    return when (state) {
        CoverAccountState.SIGNED_IN -> listOf(CoverAccountButton(signInLabel, KireiButtonStyle.SECONDARY, CoverAccountAction.SIGN_IN), signOut)
        CoverAccountState.NEEDS_REAUTH -> listOf(CoverAccountButton(signInLabel, KireiButtonStyle.PRIMARY, CoverAccountAction.SIGN_IN), signOut)
        CoverAccountState.ANONYMOUS, CoverAccountState.SIGNED_OUT -> listOf(CoverAccountButton(signInLabel, KireiButtonStyle.SECONDARY, CoverAccountAction.SIGN_IN))
    }
}

@Composable
internal fun CoverAccountSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    CoverAccountSources.forEach { source -> CoverAccountCard(source, state, colors, viewModel) }
}

@Composable
private fun CoverAccountCard(
    source: CoverSourceMode,
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val context = LocalContext.current
    val status = state.coverAccounts[source]
    // A signed-in account that still shows a stand-in name asks for its real one (NextMoe's id_token has none).
    LaunchedEffect(source, status?.connected, status?.name) {
        if (status?.connected == true) viewModel.refreshCoverProfile(source)
    }
    var editToken by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val returnedSource = CoverSourceMode.selected(result.data?.getStringExtra("source"))
            viewModel.completeCoverLogin(returnedSource, result.resultCode == Activity.RESULT_OK, result.data?.getStringExtra("message") ?: "授权已取消", false)
        }
    val onAction: (CoverAccountAction) -> Unit = { action ->
        when (action) {
            CoverAccountAction.SIGN_IN ->
                if (source == CoverSourceMode.BANGUMI) {
                    editToken = true
                } else {
                    launcher.launch(Intent(context, com.kireibox.auth.CoverLoginActivity::class.java).putExtra("source", source.key))
                }
            CoverAccountAction.SIGN_OUT -> confirmSignOut = true
            CoverAccountAction.CANCEL -> viewModel.cancelAccountConnection()
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing) {
        item(key = source.key) {
            // Search anchor: the card is the source's whole entry (「Bangumi」, 「NextMoe」…).
            SettingsAnchor(source.label) {
                CoverAccountCardBody(
                    CoverAccountCardSpec(
                        source,
                        coverAccountState(source, status),
                        status?.name.orEmpty(),
                        status?.id.orEmpty(),
                        status?.avatarUrl.orEmpty(),
                        state.accountBusy,
                    ),
                    colors,
                    onAction,
                )
            }
        }
        // NextMoe also keeps the playtime (doc §5.6): its sync lives with the account it uses.
        if (source == CoverSourceMode.NEXTMOE && status?.connected == true) {
            item(key = "nextmoe-playtime-sync") {
                NextmoePlaytimeSyncRow(state, colors) { viewModel.openSettingsPage(KireiSettingsPage.PLAYTIME_SYNC) }
            }
        }
    }
    if (editToken) BangumiTokenDialog(colors, viewModel) { editToken = false }
    if (confirmSignOut) CoverSignOutDialog(source, colors, onConfirm = { viewModel.disconnectCoverAccount(source) }) { confirmSignOut = false }
}

private data class CoverAccountCardSpec(
    val source: CoverSourceMode,
    val state: CoverAccountState,
    val accountName: String,
    val accountId: String,
    val avatarUrl: String,
    val busy: Boolean,
)

private val AccountAvatarSize = 40.dp

@Composable
private fun CoverAccountCardBody(
    spec: CoverAccountCardSpec,
    colors: KireiColors,
    onAction: (CoverAccountAction) -> Unit,
) {
    val (chipLabel, chipTone) = coverAccountChip(spec.state)
    Column(Modifier.fillMaxWidth().padding(KireiSpacing.md)) {
        // One heading per card ("NextMoe，未登录"): TalkBack can jump card to card.
        Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
            CoverSourceLogo(spec.source, colors.isDark)
            Column(Modifier.weight(1f).padding(start = KireiSpacing.sm, end = KireiSpacing.xs)) {
                Text(spec.source.label, color = colors.text, style = AppleTypography.bodyLarge, maxLines = 1)
                coverAccountLine(spec.source, spec.state, spec.accountName, spec.accountId)?.let { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (spec.state == CoverAccountState.SIGNED_IN) {
                            CoverAccountAvatar(spec.source, spec.avatarUrl, colors)
                        }
                        Text(line, color = colors.secondary, style = AppleTypography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            KireiStatusChip(chipLabel, chipTone, colors)
        }
        Row(
            Modifier.padding(start = AccountAvatarSize + KireiSpacing.sm, top = KireiSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
        ) {
            coverAccountButtons(spec.source, spec.state, spec.busy).forEach { button ->
                KireiButton(
                    button.label,
                    colors,
                    // Names the account: "登录 NextMoe", never three identical "登录" buttons.
                    Modifier.semantics { contentDescription = "${button.label} ${spec.source.label}" },
                    options =
                        KireiButtonOptions(
                            style = button.style,
                            size = KireiButtonSize.MEDIUM,
                            enabled = button.action != CoverAccountAction.SIGN_IN || !spec.busy,
                        ),
                ) { onAction(button.action) }
            }
        }
    }
}

/**
 * The source's bundled brand logo, always — the account's own avatar sits beside its name instead.
 * Drop `ic_cover_source_bangumi` / `ic_cover_source_nextmoe` / `ic_cover_source_hikarinagi`
 * (square PNG/SVG) into `app/src/main/res/drawable-nodpi/` and it is picked up automatically. With
 * no bundled logo, a neutral source glyph stands in on the source's category hue — never the
 * source's initial.
 */
@Composable
private fun CoverSourceLogo(
    source: CoverSourceMode,
    isDark: Boolean,
) {
    val context = LocalContext.current
    val tile = coverSourceTile(source)
    val brandRes = remember(source) { coverSourceBrandDrawable(context, source) }
    Box(
        Modifier.size(AccountAvatarSize)
            .clip(KireiShapes.full)
            .background(tile.container(isDark))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (brandRes != 0) {
            Image(painterResource(brandRes), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = tile.glyph(isDark), modifier = Modifier.size(AccountAvatarGlyphSize))
        }
    }
}

/** The signed-in account's avatar (fetched from the source, cached like covers) in a small circle before its name. */
@Composable
private fun CoverAccountAvatar(
    source: CoverSourceMode,
    avatarUrl: String,
    colors: KireiColors,
) {
    val tile = coverSourceTile(source)
    val avatar = rememberCover(avatarUrl.takeIf { it.isNotBlank() }, sourceKey = source.key)
    Box(
        Modifier.padding(end = KireiSpacing.xs)
            .size(AccountMiniAvatarSize)
            .clip(KireiShapes.full)
            .background(tile.container(colors.isDark))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = tile.glyph(colors.isDark), modifier = Modifier.size(AccountMiniAvatarGlyphSize))
        }
    }
}

private fun coverSourceTile(source: CoverSourceMode): KireiTileColor =
    when (source) {
        CoverSourceMode.NEXTMOE -> KireiTileColor.BLUE
        CoverSourceMode.HIKARINAGI -> KireiTileColor.TEAL
        else -> KireiTileColor.PINK
    }

private val AccountMiniAvatarSize = 20.dp
private val AccountMiniAvatarGlyphSize = 14.dp
private val AccountAvatarGlyphSize = 20.dp

/** Resource name a source's bundled brand logo must use, e.g. `ic_cover_source_bangumi`. */
internal fun coverSourceBrandDrawableName(source: CoverSourceMode): String = "ic_cover_source_${source.key}"

/** Resolves the bundled brand logo for [source], or 0 when the app does not ship one. */
private fun coverSourceBrandDrawable(
    context: android.content.Context,
    source: CoverSourceMode,
): Int = context.resources.getIdentifier(coverSourceBrandDrawableName(source), "drawable", context.packageName)

private fun coverAccountChip(state: CoverAccountState): Pair<String, KireiStatusTone> =
    when (state) {
        CoverAccountState.SIGNED_IN -> "已登录" to KireiStatusTone.POSITIVE
        CoverAccountState.NEEDS_REAUTH -> "需要重新授权" to KireiStatusTone.WARNING
        CoverAccountState.ANONYMOUS -> "匿名可用" to KireiStatusTone.ACCENT
        CoverAccountState.SIGNED_OUT -> "未登录" to KireiStatusTone.NEUTRAL
    }

/**
 * The account name and, once we know it, the account id, or what a broken account needs. The chip
 * already says 未登录 / 匿名可用, so those have no line.
 */
internal fun coverAccountLine(
    source: CoverSourceMode,
    state: CoverAccountState,
    accountName: String,
    accountId: String,
): String? =
    when (state) {
        CoverAccountState.SIGNED_IN ->
            buildList {
                add(accountName.ifBlank { "账号已连接" })
                // Only Bangumi's id is a short number worth showing; NextMoe/Hikarinagi ids are opaque.
                accountId.takeIf { source == CoverSourceMode.BANGUMI && it.isNotBlank() }?.let { add("ID $it") }
            }.joinToString(" · ")
        // Bangumi has no login, only its personal token.
        CoverAccountState.NEEDS_REAUTH -> if (source == CoverSourceMode.BANGUMI) "令牌已失效，请更换令牌" else "授权已失效，请重新登录"
        CoverAccountState.ANONYMOUS, CoverAccountState.SIGNED_OUT -> null
    }

@Composable
private fun CoverSignOutDialog(
    source: CoverSourceMode,
    colors: KireiColors,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    KireiDialog(
        spec =
            KireiDialogSpec(
                title = "退出 ${source.label}？",
                message = "将清除本机保存的 ${source.label} 授权，自动匹配和封面搜索不再使用这个账号。",
                confirm =
                    KireiDialogAction("退出", {
                        onClose()
                        onConfirm()
                    }, style = KireiButtonStyle.DESTRUCTIVE),
                dismiss = KireiDialogAction("取消", onClose, style = KireiButtonStyle.PLAIN),
            ),
        colors = colors,
        onDismissRequest = onClose,
    )
}
