package com.kireibox.app.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.kireibox.app.LauncherActivity
import com.kireibox.app.diagnostics.KireiDiagnosticLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LauncherActivity.copyDiagnosticLogs() {
    lifecycleScope.launch {
        val result =
            runCatching {
                withContext(Dispatchers.IO) { KireiDiagnosticLogs.buildText(this@copyDiagnosticLogs) }
            }
        val text = result.getOrNull()
        if (text == null) {
            showMessage("复制失败", result.exceptionOrNull()?.message ?: "无法读取诊断日志")
            return@launch
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KireiBox 日志", text))
        showMessage("日志已复制", "设备信息与最近日志已复制，常见 Token 已自动遮盖。")
    }
}

internal fun LauncherActivity.exportDiagnosticLogs() {
    lifecycleScope.launch {
        val result =
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = KireiDiagnosticLogs.buildText(this@exportDiagnosticLogs)
                    KireiDiagnosticLogs.writeExport(this@exportDiagnosticLogs, text)
                }
            }
        val file = result.getOrNull()
        if (file == null) {
            showMessage("导出失败", result.exceptionOrNull()?.message ?: "无法生成诊断日志")
            return@launch
        }
        val uri =
            FileProvider.getUriForFile(
                this@exportDiagnosticLogs,
                "$packageName.fileprovider",
                file,
            )
        val share =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "KireiBox 诊断日志")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        runCatching { startActivity(Intent.createChooser(share, "导出日志")) }
            .onFailure { showMessage("导出失败", it.message ?: "没有可用的分享应用") }
    }
}
