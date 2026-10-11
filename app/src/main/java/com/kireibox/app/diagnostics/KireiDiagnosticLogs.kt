package com.kireibox.app.diagnostics

import android.content.Context
import android.os.Build
import com.yuki.yukihub.util.DevLogger
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KireiDiagnosticLogs {
    private const val MAX_FILE_TAIL_BYTES = 512 * 1024

    fun buildText(context: Context): String {
        val app = context.applicationContext
        val packageInfo =
            runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0)
            }.getOrNull()

        @Suppress("DEPRECATION")
        val versionCode =
            packageInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
            } ?: 0L
        val active = DevLogger.getLogFile()
        val old = active?.parentFile?.resolve("logcat_old.txt")
        return redact(
            buildString {
                appendLine("KireiBox diagnostic log")
                appendLine("generatedAt=${System.currentTimeMillis()}")
                appendLine("version=${packageInfo?.versionName.orEmpty()} ($versionCode)")
                appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("abis=${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("captureEnabled=${DevLogger.isEnabled()}")
                appendLine()
                appendLogFile("previous", old)
                appendLogFile("current", active)
                appendEngineLaunchLogs(app)
                appendCommandSection("crash buffer", "logcat", "-d", "-b", "crash", "-v", "threadtime", "-t", "200")
                appendCommandSection("logcat dump", "logcat", "-d", "-v", "threadtime", "-t", "1500")
            },
        )
    }

    fun writeExport(
        context: Context,
        text: String
    ): File {
        val directory = File(context.cacheDir, "log-exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(directory, "KireiBox-log-$stamp.txt").apply {
            writeText(text, Charsets.UTF_8)
        }
    }

    internal fun redact(text: String): String =
        text
            .replace(
                Regex("""(?i)(authorization\s*[:=]\s*bearer\s+)([^\s"',;]+)"""),
            ) { "${it.groupValues[1]}<redacted>" }
            .replace(
                Regex("""(?i)(["']?(?:access_?token|refresh_?token|token)["']?\s*[:=]\s*["']?)([^\s"',;}]+)"""),
            ) { "${it.groupValues[1]}<redacted>" }

    private fun StringBuilder.appendLogFile(
        label: String,
        file: File?
    ) {
        appendLine("=== $label log ===")
        if (file?.isFile != true) {
            appendLine("(not available)")
            return
        }
        appendLine(readTail(file, MAX_FILE_TAIL_BYTES))
    }

    private fun StringBuilder.appendEngineLaunchLogs(context: Context) {
        appendLine("=== engine launch logs (kr) ===")
        val directory = File(context.applicationContext.filesDir, "launch_logs/kr")
        val files =
            runCatching {
                directory.listFiles { file -> file.isFile && file.name.endsWith(".log") }
                    ?.sortedByDescending(File::lastModified)
                    ?.take(2)
                    .orEmpty()
            }.getOrDefault(emptyList())
        if (files.isEmpty()) {
            appendLine("(not available)")
            return
        }
        files.forEach { file ->
            appendLine("--- ${file.name} ---")
            appendLine(readTail(file, MAX_FILE_TAIL_BYTES))
        }
    }

    private fun StringBuilder.appendCommandSection(
        label: String,
        vararg command: String
    ) {
        appendLine("=== $label ===")
        val output = runCommandTimeout(command.toList())
        if (output.isNullOrBlank()) appendLine("(not available)") else appendLine(output)
    }

    private fun runCommandTimeout(command: List<String>): String? {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = StringBuilder()
            val readerThread =
                Thread({
                    try {
                        process.inputStream.bufferedReader().useLines { lines ->
                            for (line in lines) {
                                if (output.length >= 1_500_000) break
                                output.append(line).append('\n')
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }, "diag-log-reader")
            readerThread.isDaemon = true
            readerThread.start()
            readerThread.join(2500L)
            process.destroy()
            output.toString().takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readTail(
        file: File,
        maxBytes: Int
    ): String =
        runCatching {
            RandomAccessFile(file, "r").use { source ->
                val count = minOf(source.length(), maxBytes.toLong()).toInt()
                source.seek((source.length() - count).coerceAtLeast(0L))
                val bytes = ByteArray(count)
                source.readFully(bytes)
                bytes.toString(Charsets.UTF_8)
            }
        }.getOrElse { "(failed to read ${file.name}: ${it.javaClass.simpleName})" }
}
