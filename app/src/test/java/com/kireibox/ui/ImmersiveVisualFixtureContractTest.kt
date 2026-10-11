package com.kireibox.ui

import com.yuki.yukihub.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImmersiveVisualFixtureContractTest {
    private fun workspaceRoot(): File {
        val candidates = listOf(File("."), File(".."))
        return candidates.firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("workspace root not found")
    }

    private fun readDebugSource(relative: String): String {
        val file = File(workspaceRoot(), relative)
        assertTrue("debug source missing: " + relative, file.isFile)
        return file.readText()
    }

    @Test
    fun debugFixtureDeclaresFiveStableGamesAndTestIntentExtra() {
        val source =
            File(
                workspaceRoot(),
                "app/src/debug/java/com/kireibox/ui/ImmersiveVisualFixture.kt",
            )
        assertTrue("debug fixture source missing", source.isFile)
        val text = source.readText()
        assertTrue(text.contains("EXTRA_IMMERSIVE_FIXTURE"))
        assertTrue(text.contains("EXTRA_NSFW_COVERS"))
        assertTrue(text.contains("EXTRA_NSFW_GLOBAL_ENABLED"))
        assertTrue(text.contains("List(5)"))
        assertTrue(text.contains("9nine新章"))
        assertTrue(text.contains("nsfw = nsfwCovers"))
    }

    @Test
    fun fixtureIsAbsentFromMainAndReleaseSourceSets() {
        val root = workspaceRoot()
        val forbidden =
            listOf(
                File(root, "app/src/main/java/com/kireibox/ui/ImmersiveVisualFixture.kt"),
                File(root, "app/src/release/java/com/kireibox/ui/ImmersiveVisualFixture.kt"),
                File(root, "app/src/main/java/com/kireibox/ui/ImmersiveFixtureActivity.kt"),
                File(root, "app/src/release/java/com/kireibox/ui/ImmersiveFixtureActivity.kt"),
                File(root, "app/src/main/res/drawable/fixture_cover_1.xml"),
                File(root, "app/src/release/res/drawable/fixture_cover_1.xml"),
            )
        assertFalse(forbidden.joinToString { it.path }, forbidden.any(File::exists))
    }

    @Test
    fun progressExtraIsDeclaredInDebugFixtureAndGatedBehindBuildConfigAndEnabled() {
        val text = readDebugSource("app/src/debug/java/com/kireibox/ui/ImmersiveVisualFixture.kt")
        assertTrue("missing progress extra constant", text.contains("EXTRA_IMMERSIVE_PROGRESS"))
        assertTrue("missing canonical extra name", text.contains("com.kireibox.debug.IMMERSIVE_PROGRESS"))
        assertTrue(
            "progress must only be read when DEBUG and fixture enabled",
            text.contains("BuildConfig.DEBUG && enabled(intent)"),
        )
    }

    @Test
    fun progressParserAcceptsExactFrozenFramesAndRejectsEverythingElse() {
        if (BuildConfig.DEBUG) {
            val cls = Class.forName("com.kireibox.ui.ImmersiveVisualFixture")
            val instance = cls.getField("INSTANCE").get(null)
            val method =
                cls.declaredMethods.firstOrNull {
                    it.name == "immersiveProgress" || it.name.startsWith("immersiveProgress$")
                } ?: error("immersiveProgress not found in ImmersiveVisualFixture")
            method.isAccessible = true

            val accepted = mapOf(0 to 0f, 25 to 0.25f, 50 to 0.5f, 75 to 0.75f, 100 to 1f)
            for ((percent, expected) in accepted) {
                val actual = method.invoke(instance, Integer.valueOf(percent)) as Float
                assertEquals("percent=" + percent, expected, actual, 0.0001f)
            }
            for (invalid in listOf(1, 13, 24, 26, 99, 101, 200, -1, -5, Int.MIN_VALUE, Int.MAX_VALUE)) {
                assertNull(
                    "percent=" + invalid + " must be rejected, never clamped",
                    method.invoke(instance, Integer.valueOf(invalid)),
                )
            }
            assertNull(
                "missing extra (null percent) must map to null",
                method.invoke(instance, *arrayOf<Any?>(null)),
            )
        } else {
            try {
                Class.forName("com.kireibox.ui.ImmersiveVisualFixture")
                throw AssertionError("fixture class must be absent from release classpath")
            } catch (expected: ClassNotFoundException) {
                // Expected: the fixture lives exclusively in the debug source set.
            }
        }
    }

    @Test
    fun fixtureActivityWiresFrozenProgressToImmersiveHomeAndKeepsLegacyExtras() {
        val text = readDebugSource("app/src/debug/java/com/kireibox/ui/ImmersiveFixtureActivity.kt")
        assertTrue(
            "activity must pass debugFrozenTrackProgress to ImmersiveHome",
            text.contains("debugFrozenTrackProgress"),
        )
        assertTrue("fixture must always expose its five games", text.contains("source = SpotlightSource.ALL"))
        assertTrue(
            "progress must come from the gated fixture reader",
            text.contains("ImmersiveVisualFixture.frozenTrackProgress(intent)"),
        )
        assertTrue("exact_canvas must be preserved", text.contains("\"exact_canvas\""))
        // The legacy extras moved into the fixture object, where they are one switch with the
        // namespaced IMMERSIVE_REDUCED_* extras; the activity must read them through it.
        val fixture = readDebugSource("app/src/debug/java/com/kireibox/ui/ImmersiveVisualFixture.kt")
        assertTrue("reduced_motion must be preserved", fixture.contains("EXTRA_LEGACY_REDUCED_MOTION = \"reduced_motion\""))
        assertTrue("reduced_transparency must be preserved", fixture.contains("EXTRA_LEGACY_REDUCED_TRANSPARENCY = \"reduced_transparency\""))
        assertTrue(text.contains("reducedMotion = ImmersiveVisualFixture.reducedMotion(intent)"))
        assertTrue(text.contains("reducedTransparency = ImmersiveVisualFixture.reducedTransparency(intent)"))
        assertTrue(text.contains("LocalNsfwBlurEnabled provides"))
    }

    @Test
    fun fixtureRendersOneResolvedEffectSetFromItsExtras() {
        val fixture = readDebugSource("app/src/debug/java/com/kireibox/ui/ImmersiveVisualFixture.kt")
        listOf(
            "com.kireibox.debug.IMMERSIVE_PRESET",
            "com.kireibox.debug.IMMERSIVE_BACKDROP",
            "com.kireibox.debug.IMMERSIVE_AMBIENT",
            "com.kireibox.debug.IMMERSIVE_REDUCED_MOTION",
            "com.kireibox.debug.IMMERSIVE_REDUCED_TRANSPARENCY",
            "com.kireibox.debug.IMMERSIVE_INCREASED_CONTRAST",
            "com.kireibox.debug.IMMERSIVE_FREEZE_MOTION",
        ).forEach { extra -> assertTrue("missing $extra", fixture.contains(extra)) }
        assertTrue("legacy and namespaced reduced-motion extras are one switch", fixture.contains("intent.flag(EXTRA_LEGACY_REDUCED_MOTION) || intent.flag(EXTRA_IMMERSIVE_REDUCED_MOTION)"))
        assertTrue("the fixture degrades exactly like the app", fixture.contains("resolveImmersiveFx("))
        val activity = readDebugSource("app/src/debug/java/com/kireibox/ui/ImmersiveFixtureActivity.kt")
        assertTrue(activity.contains("val fixtureFx = ImmersiveVisualFixture.resolvedFx(intent)"))
        // r5: 1–5 of the five games, so the small-gallery track can be checked on the fixture.
        assertTrue(fixture.contains("EXTRA_IMMERSIVE_GAME_COUNT = \"com.kireibox.debug.IMMERSIVE_GAME_COUNT\""))
        assertTrue(activity.contains("count = ImmersiveVisualFixture.gameCount(intent)"))
        assertTrue(activity.contains("LocalImmersiveFx provides fixtureFx"))
    }

    @Test
    fun fixtureActivityIsRegisteredOnlyInDebugManifest() {
        val root = workspaceRoot()
        val debugManifest = File(root, "app/src/debug/AndroidManifest.xml")
        val mainManifest = File(root, "app/src/main/AndroidManifest.xml")
        assertTrue("debug manifest missing", debugManifest.isFile)
        assertTrue(
            "debug manifest must register the fixture entry",
            debugManifest.readText().contains("ImmersiveFixtureActivity"),
        )
        assertFalse(
            "main manifest must not register the debug fixture",
            mainManifest.readText().contains("ImmersiveFixtureActivity"),
        )
        val releaseManifest = File(root, "app/src/release/AndroidManifest.xml")
        assertFalse(
            "release must not register the debug fixture",
            releaseManifest.isFile && releaseManifest.readText().contains("ImmersiveFixtureActivity"),
        )
    }
}
