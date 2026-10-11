package com.yuki.yukihub.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveVisualSettingsTest {
    @Test
    fun `presets follow the plan table`() {
        val minimal = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.MINIMAL)
        val standard = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD)
        val rich = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.RICH)

        // Backdrop: 氛围色 / 封面氛围 / 流光极光 (drawn over the blurred cover). r5 (user decision,
        // supersedes r4's 氛围色 default): the default (standard) preset is 封面氛围, the alpha.16
        // immersive background; 简约 keeps 氛围色 and 封面虚化 stays a 自定义 choice.
        assertEquals(ImmersiveBackdropStyle.TONE, minimal.backdrop)
        assertEquals(ImmersiveBackdropStyle.COVER_ZOOM, standard.backdrop)
        assertEquals(ImmersiveBackdropStyle.AURORA, rich.backdrop)
        // Intensity 低 / 中 / 高 (r4: 简约 is the calmest atmosphere) and card depth 平 / 标准 / 强.
        assertEquals(listOf(FxLevel.LOW, FxLevel.MEDIUM, FxLevel.HIGH), listOf(minimal, standard, rich).map { it.backdropIntensity })
        assertEquals(listOf(FxLevel.LOW, FxLevel.MEDIUM, FxLevel.HIGH), listOf(minimal, standard, rich).map { it.cardDepth })
        // — / ● / ● rows.
        listOf(ImmersiveVisualSettings::glow, ImmersiveVisualSettings::rimLight).forEach { flag ->
            assertEquals(listOf(false, true, true), listOf(minimal, standard, rich).map(flag))
        }
        // — / — / ● rows. The vignette is off by default: alpha.16's background had none and it would
        // darken the corners of the 封面氛围 look the standard preset reproduces.
        listOf(
            ImmersiveVisualSettings::vignette,
            ImmersiveVisualSettings::kenBurns,
            ImmersiveVisualSettings::grain,
            ImmersiveVisualSettings::sheen,
            ImmersiveVisualSettings::gyroParallax,
        ).forEach { flag ->
            assertEquals(listOf(false, false, true), listOf(minimal, standard, rich).map(flag))
        }
        // 动态取色 关 / 开 / 开, 氛围层 无 / 无 / 光尘.
        assertEquals(listOf(false, true, true), listOf(minimal, standard, rich).map { it.dynamicTint })
        assertEquals(
            listOf(ImmersiveAmbient.NONE, ImmersiveAmbient.NONE, ImmersiveAmbient.DUST),
            listOf(minimal, standard, rich).map { it.ambient },
        )
        // 切换方式 is not a preset detail: every preset starts on 缓和.
        assertEquals(List(3) { ImmersiveCardSwitch.SOFT }, listOf(minimal, standard, rich).map { it.cardSwitch })
        // The model default is the standard preset.
        assertEquals(standard, ImmersiveVisualSettings())
    }

    @Test
    fun `named presets stay named and any detail edit becomes custom`() {
        ImmersiveFxPreset.entries.filter { it != ImmersiveFxPreset.CUSTOM }.forEach { preset ->
            val canonical = ImmersiveVisualSettings.forPreset(preset)
            assertEquals(preset, canonical.normalized().preset)
            assertEquals(ImmersiveFxPreset.CUSTOM, canonical.copy(grain = !canonical.grain).normalized().preset)
        }
        val custom = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.CUSTOM)
        assertEquals(ImmersiveFxPreset.CUSTOM, custom.normalized().preset)
    }

    @Test
    fun `fresh prefs read the standard preset`() {
        assertEquals(ImmersiveVisualSettings(), ImmersiveVisualSettingsStore(StrictPrefs()).read())
    }

    @Test
    fun `a preset-only legacy entry materializes the canonical preset details`() {
        val prefs = StrictPrefs(mapOf(ImmersiveVisualSettingsStore.KEY_PRESET to "rich"))

        assertEquals(ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.RICH), ImmersiveVisualSettingsStore(prefs).read())
    }

    @Test
    fun `a named preset reads back canonically even when a detail key is missing or stale`() {
        // A later build may add a detail key or retune a preset: a stored RICH user must stay RICH
        // (with today's canonical details), not be relabelled CUSTOM and frozen on old values.
        val prefs = StrictPrefs()
        ImmersiveVisualSettingsStore(prefs).write(ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.RICH))
        prefs.edit().remove(ImmersiveVisualSettingsStore.KEY_GRAIN).putBoolean(ImmersiveVisualSettingsStore.KEY_SHEEN, false).apply()

        assertEquals(ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.RICH), ImmersiveVisualSettingsStore(prefs).read())
    }

    @Test
    fun `a custom state falls back per key to the standard values`() {
        val prefs =
            StrictPrefs(
                mapOf(
                    ImmersiveVisualSettingsStore.KEY_PRESET to "custom",
                    ImmersiveVisualSettingsStore.KEY_AMBIENT to "snow",
                ),
            )
        val standard = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD)

        assertEquals(standard.copy(preset = ImmersiveFxPreset.CUSTOM, ambient = ImmersiveAmbient.SNOW), ImmersiveVisualSettingsStore(prefs).read())
    }

    @Test
    fun `minimal and custom states round trip through the dedicated keys`() {
        val prefs = StrictPrefs()
        val store = ImmersiveVisualSettingsStore(prefs)

        val minimal = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.MINIMAL)
        assertEquals(minimal, store.write(minimal))
        assertEquals(minimal, ImmersiveVisualSettingsStore(prefs).read())
        assertEquals("tone", prefs.getString(ImmersiveVisualSettingsStore.KEY_BACKDROP, null))

        val custom = minimal.copy(ambient = ImmersiveAmbient.SNOW, cardDepth = FxLevel.HIGH, cardSwitch = ImmersiveCardSwitch.STAGGER)
        val written = store.write(custom)
        assertEquals(ImmersiveFxPreset.CUSTOM, written.preset)
        assertEquals(written, ImmersiveVisualSettingsStore(prefs).read())
        assertEquals(ImmersiveCardSwitch.STAGGER, ImmersiveVisualSettingsStore(prefs).read().cardSwitch)
        assertEquals("snow", prefs.getString(ImmersiveVisualSettingsStore.KEY_AMBIENT, null))
        assertTrue(prefs.all.keys.all { it.startsWith(ImmersiveVisualSettingsStore.KEY_PREFIX) })
    }

    @Test
    fun `malformed and type-mismatched values fall back to safe defaults`() {
        val prefs =
            StrictPrefs(
                mapOf(
                    ImmersiveVisualSettingsStore.KEY_PRESET to "custom",
                    ImmersiveVisualSettingsStore.KEY_BACKDROP to "hologram",
                    ImmersiveVisualSettingsStore.KEY_BACKDROP_INTENSITY to 3,
                    ImmersiveVisualSettingsStore.KEY_GLOW to "yes",
                    ImmersiveVisualSettingsStore.KEY_AMBIENT to " Sakura ",
                    ImmersiveVisualSettingsStore.KEY_GRAIN to true,
                ),
            )

        val read = ImmersiveVisualSettingsStore(prefs).read()

        assertEquals(ImmersiveFxPreset.CUSTOM, read.preset)
        // An unreadable backdrop falls back to the standard preset's backdrop (封面氛围 since r5).
        assertEquals(ImmersiveBackdropStyle.COVER_ZOOM, read.backdrop)
        assertEquals(FxLevel.MEDIUM, read.backdropIntensity)
        assertTrue("a non-boolean glow keeps the default", read.glow)
        assertEquals(ImmersiveAmbient.SAKURA, read.ambient)
        assertTrue(read.grain)
        assertFalse(read.sheen)
    }

    @Test
    fun `standard users move to the new default while custom choices are kept`() {
        // r5: a stored 标准 preset reads back canonically, so everyone who never customised the
        // effects gets 封面氛围; a 自定义 state that chose 氛围色 keeps it.
        val standardPrefs = StrictPrefs()
        val standardStore = ImmersiveVisualSettingsStore(standardPrefs)
        standardStore.write(ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD))
        standardPrefs.edit().putString(ImmersiveVisualSettingsStore.KEY_BACKDROP, "tone").apply()
        assertEquals(ImmersiveBackdropStyle.COVER_ZOOM, ImmersiveVisualSettingsStore(standardPrefs).read().backdrop)

        val customPrefs = StrictPrefs()
        ImmersiveVisualSettingsStore(customPrefs).write(ImmersiveVisualSettings().copy(backdrop = ImmersiveBackdropStyle.TONE))
        val custom = ImmersiveVisualSettingsStore(customPrefs).read()
        assertEquals(ImmersiveFxPreset.CUSTOM, custom.preset)
        assertEquals(ImmersiveBackdropStyle.TONE, custom.backdrop)
    }

    @Test
    fun `the card switch is kept across presets and never makes a preset custom`() {
        val prefs = StrictPrefs()
        val store = ImmersiveVisualSettingsStore(prefs)
        ImmersiveFxPreset.entries.filter { it != ImmersiveFxPreset.CUSTOM }.forEach { preset ->
            val staggered = ImmersiveVisualSettings.forPreset(preset).copy(cardSwitch = ImmersiveCardSwitch.STAGGER)
            assertEquals(preset, staggered.normalized().preset)
            assertEquals(staggered, store.write(staggered))
            assertEquals(staggered, ImmersiveVisualSettingsStore(prefs).read())
        }
        val rich = ImmersiveVisualSettings(cardSwitch = ImmersiveCardSwitch.STAGGER).withPreset(ImmersiveFxPreset.RICH)
        assertEquals(ImmersiveFxPreset.RICH, rich.preset)
        assertEquals(ImmersiveCardSwitch.STAGGER, rich.cardSwitch)
        assertEquals(ImmersiveCardSwitch.SOFT, ImmersiveCardSwitch.fromStored("bogus"))
        assertEquals(ImmersiveCardSwitch.STAGGER, ImmersiveCardSwitch.fromStored(" STAGGER "))
    }

    @Test
    fun `stored enum values accept their names and ignore case`() {
        assertEquals(ImmersiveFxPreset.RICH, ImmersiveFxPreset.fromStored("RICH"))
        assertEquals(ImmersiveFxPreset.STANDARD, ImmersiveFxPreset.fromStored(null))
        assertEquals(ImmersiveBackdropStyle.SOLID, ImmersiveBackdropStyle.fromStored("Solid"))
        assertEquals(ImmersiveBackdropStyle.BLUR_COVER, ImmersiveBackdropStyle.fromStored("blur_cover"))
        // r5: earlier stored values keep their meaning; the new style has its own stable value, and
        // an unknown value means the standard 封面氛围.
        assertEquals(ImmersiveBackdropStyle.TONE, ImmersiveBackdropStyle.fromStored("tone"))
        assertEquals(ImmersiveBackdropStyle.COVER_ZOOM, ImmersiveBackdropStyle.fromStored("cover_zoom"))
        assertEquals("cover_zoom", ImmersiveBackdropStyle.COVER_ZOOM.storedValue)
        assertEquals("封面氛围", ImmersiveBackdropStyle.COVER_ZOOM.displayName)
        assertEquals(ImmersiveBackdropStyle.COVER_ZOOM, ImmersiveBackdropStyle.fromStored("hologram"))
        assertEquals(FxLevel.HIGH, FxLevel.fromStored(" high "))
        assertEquals(ImmersiveAmbient.NONE, ImmersiveAmbient.fromStored("rain"))
    }
}

/** SharedPreferences fake that, like the platform, throws when a key holds another type. */
private class StrictPrefs(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    private val store = initial.toMutableMap()

    private inline fun <reified T> typed(
        key: String?,
        default: T,
    ): T {
        if (!store.containsKey(key)) return default
        return store[key] as? T ?: throw ClassCastException("$key is not ${T::class.java.simpleName}")
    }

    override fun getAll(): Map<String, *> = store.toMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = if (store.containsKey(key)) store[key] as? String ?: throw ClassCastException("$key is not a String") else defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = store[key] as? MutableSet<String> ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = typed(key, defValue)

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = typed(key, defValue)

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = typed(key, defValue)

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = typed(key, defValue)

    override fun contains(key: String?): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val puts = mutableMapOf<String, Any?>()

        private fun put(
            key: String?,
            value: Any?,
        ): SharedPreferences.Editor {
            puts[key.orEmpty()] = value
            return this
        }

        override fun putString(
            key: String?,
            value: String?,
        ) = put(key, value)

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ) = put(key, values)

        override fun putInt(
            key: String?,
            value: Int,
        ) = put(key, value)

        override fun putLong(
            key: String?,
            value: Long,
        ) = put(key, value)

        override fun putFloat(
            key: String?,
            value: Float,
        ) = put(key, value)

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ) = put(key, value)

        override fun remove(key: String?) = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            store.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            puts.forEach { (key, value) -> if (value == null) store.remove(key) else store[key] = value }
            puts.clear()
        }
    }
}
