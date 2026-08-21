package com.yuki.yukihub.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

/**
 * 开发者日志收集 - 直接捕获 logcat 系统日志。
 * 日志路径：Android/data/com.yuki.yukihub/files/logs/logcat.txt
 */
object DevLogger {
    private const val TAG = "DevLogger"
    private const val PREFS = "yukihub_prefs"
    private const val KEY = "dev_log_enabled"
    private const val MAX_LOG_SIZE = 10L * 1024L * 1024L

    @Volatile
    private var enabled = false
    private var logcatFile: File? = null
    private var process: Process? = null
    private var captureThread: Thread? = null
    @Volatile
    private var captureGeneration = 0L
    private val fileAppendLock = Any()

    @JvmStatic
    fun init(ctx: Context?) {
        if (ctx == null) return
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "logs")
        initializeCapture(
            preferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            logDirectory = dir,
        )
    }

    /** Shared production path for app initialization and deterministic JVM verification. */
    internal fun initializeCapture(
        preferences: SharedPreferences,
        logDirectory: File,
        processFactory: () -> Process = { Runtime.getRuntime().exec("logcat -v threadtime") },
    ) {
        enabled = preferences.getBoolean(KEY, false)
        if (!logDirectory.exists()) logDirectory.mkdirs()
        logcatFile = File(logDirectory, "logcat.txt")
        if (enabled) startCapture(processFactory) else stopCapture()
    }

    @JvmStatic
    fun isEnabled(): Boolean = enabled

    @JvmStatic
    fun setEnabled(ctx: Context?, on: Boolean) {
        if (ctx == null) return
        enabled = on
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        if (on) startCapture() else stopCapture()
    }

    private fun startCapture(
        processFactory: () -> Process = { Runtime.getRuntime().exec("logcat -v threadtime") },
    ) {
        val previous: Thread?
        val next: Thread
        synchronized(DevLogger::class.java) {
            previous = stopCaptureLocked()
            if (!enabled || logcatFile == null) return
            val generation = ++captureGeneration
            next = Thread({
                var localProcess: Process? = null
                var reader: BufferedReader? = null
                var writer: FileWriter? = null
                try {
                    localProcess = processFactory()
                    synchronized(DevLogger::class.java) {
                        if (!enabled || generation != captureGeneration) {
                            localProcess.destroy()
                            return@Thread
                        }
                        process = localProcess
                    }
                    reader = BufferedReader(InputStreamReader(localProcess.inputStream))
                    writer = FileWriter(logcatFile!!, true)
                    writer!!.write("\n=== Logcat capture started ===\n")
                    writer!!.flush()
                    var pendingLines = 0
                    var line = reader.readLine()
                    while (enabled && generation == captureGeneration && line != null) {
                        writer!!.write(line)
                        writer!!.write("\n")
                        pendingLines++
                        if (pendingLines >= 32) {
                            writer!!.flush()
                            pendingLines = 0
                            // Check rotation in batches to avoid a filesystem stat per line.
                            if (logcatFile!!.length() > MAX_LOG_SIZE) {
                                writer!!.close()
                                writer = null
                                val bak = File(logcatFile!!.parent, "logcat_old.txt")
                                if (bak.exists()) bak.delete()
                                logcatFile!!.renameTo(bak)
                                writer = FileWriter(logcatFile!!)
                            }
                        }
                        line = reader.readLine()
                    }
                } catch (e: Exception) {
                    if (enabled && generation == captureGeneration) {
                        runCatching { Log.e(TAG, "Capture failed", e) }
                    }
                } finally {
                    reader?.let { runCatching { it.close() } }
                    writer?.let { runCatching { it.close() } }
                    localProcess?.destroy()
                    synchronized(DevLogger::class.java) {
                        if (process === localProcess) process = null
                        if (captureThread === Thread.currentThread()) captureThread = null
                    }
                }
            }, "LogcatCapture")
            captureThread = next
        }
        awaitCaptureStop(previous)
        synchronized(DevLogger::class.java) {
            if (captureThread !== next || !enabled) return
            next.start()
        }
        runCatching { Log.i(TAG, "Logcat capture started") }
    }

    private fun stopCapture() {
        val previous: Thread?
        synchronized(DevLogger::class.java) {
            previous = stopCaptureLocked()
        }
        awaitCaptureStop(previous)
    }

    private fun stopCaptureLocked(): Thread? {
        captureGeneration++
        val previous = captureThread
        process?.let {
            it.destroy()
            process = null
        }
        captureThread?.let {
            it.interrupt()
            captureThread = null
        }
        return previous
    }

    private fun awaitCaptureStop(thread: Thread?) {
        if (thread == null || thread === Thread.currentThread()) return
        try {
            thread.join(1500L)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @JvmStatic
    fun getLogFile(): File? = logcatFile

    @JvmStatic
    fun getLogPath(): String? = logcatFile?.absolutePath

    @JvmStatic
    fun getLogSize(): Long = logcatFile?.let { if (it.exists()) it.length() else 0L } ?: 0L

    @JvmStatic
    fun clearLog(): Boolean {
        return try {
            stopCapture()
            FileWriter(logcatFile!!, false).close()
            if (enabled) startCapture()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Records an app-level event directly into the log file. Streaming logcat
     * can be unavailable on some OEM builds (Huawei/HarmonyOS), so key launch
     * steps are written straight to disk to keep diagnostics useful.
     */
    @JvmStatic
    fun event(message: String) {
        if (!enabled) return
        val target = logcatFile ?: return
        val line = "[app] ${android.os.SystemClock.elapsedRealtime()}ms $message\n"
        synchronized(fileAppendLock) {
            runCatching {
                FileWriter(target, true).use { it.write(line) }
                if (target.length() > MAX_LOG_SIZE) FileWriter(target, false).use { }
            }
        }
    }

    @JvmStatic
    fun formatSize(b: Long): String {
        if (b < 1024) return "$b B"
        if (b < 1048576) return String.format(java.util.Locale.getDefault(), "%.1f KB", b / 1024.0)
        return String.format(java.util.Locale.getDefault(), "%.2f MB", b / 1048576.0)
    }
}
