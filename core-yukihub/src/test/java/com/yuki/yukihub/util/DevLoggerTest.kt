package com.yuki.yukihub.util

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class DevLoggerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun captureStartsAndWritesOnlyForExplicitOptIn() {
        val logDirectory = temporaryFolder.newFolder("logs")
        val starts = AtomicInteger(0)
        val processFactory = {
            starts.incrementAndGet()
            FakeProcess("captured line\n")
        }

        try {
            DevLogger.initializeCapture(
                preferences = BooleanPreferences(null),
                logDirectory = logDirectory,
                processFactory = processFactory,
            )
            assertFalse(DevLogger.isEnabled())
            assertEquals(0, starts.get())
            DevLogger.event("disabled event")
            assertFalse(logDirectory.resolve("logcat.txt").exists())

            DevLogger.initializeCapture(
                preferences = BooleanPreferences(false),
                logDirectory = logDirectory,
                processFactory = processFactory,
            )
            assertFalse(DevLogger.isEnabled())
            assertEquals(0, starts.get())

            DevLogger.initializeCapture(
                preferences = BooleanPreferences(true),
                logDirectory = logDirectory,
                processFactory = processFactory,
            )
            await {
                starts.get() == 1 &&
                    logDirectory.resolve("logcat.txt").takeIf { it.isFile }
                        ?.readText()?.contains("captured line") == true
            }
            assertTrue(DevLogger.isEnabled())
            assertEquals(1, starts.get())
        } finally {
            DevLogger.initializeCapture(
                preferences = BooleanPreferences(false),
                logDirectory = logDirectory,
                processFactory = processFactory,
            )
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(10L)
        }
        error("Timed out waiting for log capture")
    }

    private class FakeProcess(text: String) : Process() {
        private val input = ByteArrayInputStream(text.toByteArray())

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = input

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun exitValue(): Int = 0

        override fun destroy() = Unit
    }

    private class BooleanPreferences(private val enabled: Boolean?) : SharedPreferences {
        override fun getBoolean(
            key: String?,
            defaultValue: Boolean,
        ): Boolean = enabled ?: defaultValue

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()

        override fun getString(
            key: String?,
            defaultValue: String?,
        ): String? = defaultValue

        override fun getStringSet(
            key: String?,
            defaultValues: MutableSet<String>?,
        ): MutableSet<String>? = defaultValues

        override fun getInt(
            key: String?,
            defaultValue: Int,
        ): Int = defaultValue

        override fun getLong(
            key: String?,
            defaultValue: Long,
        ): Long = defaultValue

        override fun getFloat(
            key: String?,
            defaultValue: Float,
        ): Float = defaultValue

        override fun contains(key: String?): Boolean = enabled != null

        override fun edit(): SharedPreferences.Editor = NoOpEditor

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private object NoOpEditor : SharedPreferences.Editor {
        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor = this

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = this

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor = this

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor = this

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor = this

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = this

        override fun remove(key: String?): SharedPreferences.Editor = this

        override fun clear(): SharedPreferences.Editor = this

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
