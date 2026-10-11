package com.kireibox.ui

import com.kireibox.designsystem.components.KireiStatusTone
import com.yuki.yukihub.metadata.model.CoverSourceMode

/**
 * Per-source status copy for the hybrid cover picker. The picker no longer lets the user choose a
 * source, so this row is the only place that explains which source is still working, which one is
 * unreachable on this network, and which login is missing.
 */
internal object CoverHybridStatus {
    fun text(
        source: CoverSourceMode,
        connected: Boolean,
        loading: Boolean,
        attempted: Boolean,
        failed: Boolean,
        hits: Int,
    ): String =
        when {
            failed -> "不可达"
            attempted -> if (hits > 0) "$hits 条" else "无结果"
            loading -> "查询中…"
            !connected -> "未连接"
            else -> "待查询"
        }

    /** True when a login-required source is idle only because it was never connected. */
    fun needsLoginHint(
        sources: List<CoverSourceMode>,
        connected: (CoverSourceMode) -> Boolean
    ): Boolean = sources.any { it.requiresLogin && !connected(it) }

    /** Login-required sources that are not connected, named by the picker's login action. */
    fun missingLogins(
        sources: List<CoverSourceMode>,
        connected: (CoverSourceMode) -> Boolean
    ): List<CoverSourceMode> = sources.filter { it.requiresLogin && !connected(it) }

    /** The picker's login action, which opens 来源账号: 「登录 NextMoe / Hikarinagi」. */
    fun loginActionLabel(missing: List<CoverSourceMode>): String = "登录 ${missing.joinToString(" / ") { it.label }}"

    /**
     * Dot of the source's status chip (r5 · B, [KireiStatusTone]; the label always says the same in
     * words): results → positive, unreachable → warning, still asking → accent, otherwise neutral.
     */
    fun tone(
        loading: Boolean,
        attempted: Boolean,
        failed: Boolean,
        hits: Int,
    ): KireiStatusTone =
        when {
            failed -> KireiStatusTone.WARNING
            attempted -> if (hits > 0) KireiStatusTone.POSITIVE else KireiStatusTone.NEUTRAL
            loading -> KireiStatusTone.ACCENT
            else -> KireiStatusTone.NEUTRAL
        }
}
