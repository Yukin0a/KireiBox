package com.kireibox.ui

import android.content.res.Configuration
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemBarVisibilityPolicyTest {
    private fun resolve(
        widthDp: Float,
        heightDp: Float,
        smallestScreenWidthDp: Int,
        screenLayoutSize: Int,
        orientation: Int,
    ) = SystemBarVisibilityPolicy.resolve(
        widthDp = widthDp,
        heightDp = heightDp,
        smallestScreenWidthDp = smallestScreenWidthDp,
        screenLayoutSize = screenLayoutSize,
        orientation = orientation,
    )

    @Test
    fun pixel8PortraitShowsStatusBar() {
        assertFalse(resolve(411f, 914f, 411, Configuration.SCREENLAYOUT_SIZE_NORMAL, Configuration.ORIENTATION_PORTRAIT))
    }

    @Test
    fun pixel8LandscapeHidesStatusBar() {
        assertTrue(resolve(914f, 411f, 411, Configuration.SCREENLAYOUT_SIZE_NORMAL, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun padLandscapeKeepsStatusBarVisible() {
        assertFalse(resolve(1280f, 800f, 800, Configuration.SCREENLAYOUT_SIZE_LARGE, Configuration.ORIENTATION_LANDSCAPE))
        assertFalse(resolve(1280f, 800f, 800, Configuration.SCREENLAYOUT_SIZE_XLARGE, Configuration.ORIENTATION_LANDSCAPE))
        assertFalse(resolve(800f, 1280f, 800, Configuration.SCREENLAYOUT_SIZE_LARGE, Configuration.ORIENTATION_PORTRAIT))
    }

    @Test
    fun padSplitScreenLandscapeKeepsStatusBarVisible() {
        assertFalse(resolve(700f, 400f, 600, Configuration.SCREENLAYOUT_SIZE_LARGE, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun unfoldedPadLandscapeKeepsStatusBarVisible() {
        assertFalse(resolve(840f, 700f, 600, Configuration.SCREENLAYOUT_SIZE_LARGE, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun nonPadLandscapeHidesStatusBar() {
        assertTrue(resolve(1000f, 500f, 500, Configuration.SCREENLAYOUT_SIZE_NORMAL, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun foldedPhoneHidesStatusBarInLandscapeOnly() {
        assertTrue(resolve(800f, 400f, 400, Configuration.SCREENLAYOUT_SIZE_NORMAL, Configuration.ORIENTATION_LANDSCAPE))
        assertFalse(resolve(400f, 800f, 400, Configuration.SCREENLAYOUT_SIZE_NORMAL, Configuration.ORIENTATION_PORTRAIT))
    }

    @Test
    fun statusBarFollowsTheFourWayWindowClass() {
        assertFalse(SystemBarVisibilityPolicy.shouldHideStatusBar(ResponsiveWindowClass.PHONE_PORTRAIT))
        assertTrue(SystemBarVisibilityPolicy.shouldHideStatusBar(ResponsiveWindowClass.PHONE_LANDSCAPE))
        assertFalse(SystemBarVisibilityPolicy.shouldHideStatusBar(ResponsiveWindowClass.PAD_LANDSCAPE))
        assertFalse(SystemBarVisibilityPolicy.shouldHideStatusBar(ResponsiveWindowClass.PAD_PORTRAIT))
    }

    @Test
    fun navigationBarsAreNeverHidden() {
        assertFalse(SystemBarVisibilityPolicy.shouldHideNavigationBars())
    }

    @Test
    fun cutoutPolicyUsesShortEdges() {
        assertEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
            SystemBarVisibilityPolicy.cutoutMode(),
        )
    }

    @Test
    fun statusBarIconsFollowTheActualLightOrDarkBackgroundInEveryHomeMode() {
        // Light theme, normal pages: dark icons on light backgrounds.
        assertFalse(SystemBarVisibilityPolicy.statusBarIconsLight(darkTheme = false, immersiveHome = false))
        // Light immersive palette is also a light background: keep dark icons.
        assertFalse(SystemBarVisibilityPolicy.statusBarIconsLight(darkTheme = false, immersiveHome = true))
        // Dark theme: light icons everywhere.
        assertTrue(SystemBarVisibilityPolicy.statusBarIconsLight(darkTheme = true, immersiveHome = false))
        assertTrue(SystemBarVisibilityPolicy.statusBarIconsLight(darkTheme = true, immersiveHome = true))
    }

    @Test
    fun modalCallSiteDerivesIconToneFromItsSheetBackground() {
        val source = UiSourceFiles.all()
        assertTrue(source.contains("val lightIcons = SystemBarVisibilityPolicy.statusBarIconsLight(isDark)"))
    }

    @Test
    fun explicitPhoneAndPadOrientationMatrixIsStable() {
        val cases =
            listOf(
                ResponsiveWindowClass.PHONE_PORTRAIT to HomeOrientation.PORTRAIT,
                ResponsiveWindowClass.PHONE_LANDSCAPE to HomeOrientation.LANDSCAPE,
                ResponsiveWindowClass.PAD_PORTRAIT to HomeOrientation.PORTRAIT,
                ResponsiveWindowClass.PAD_LANDSCAPE to HomeOrientation.LANDSCAPE,
            )

        cases.forEach { (windowClass, orientation) ->
            val spec = SystemBarVisibilityPolicy.resolve(windowClass, orientation)
            assertEquals(windowClass, spec.windowClass)
            assertEquals(orientation, spec.orientation)
            assertEquals(windowClass == ResponsiveWindowClass.PHONE_LANDSCAPE, spec.hideStatusBar)
            assertFalse(spec.hideNavigationBar)
            assertEquals(SystemBarVisibilityPolicy.cutoutMode(), spec.cutoutMode)
        }
    }

    @Test
    fun configurationPolicyUsesSafeDimensionsAndSizeClass() {
        val phone =
            SystemBarVisibilityPolicy.visibilitySpec(
                widthDp = 640f,
                heightDp = 360f,
                smallestScreenWidthDp = 360,
                screenLayoutSize = Configuration.SCREENLAYOUT_SIZE_NORMAL,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
            )
        val pad =
            SystemBarVisibilityPolicy.visibilitySpec(
                widthDp = 600f,
                heightDp = 1024f,
                smallestScreenWidthDp = 600,
                screenLayoutSize = Configuration.SCREENLAYOUT_SIZE_NORMAL,
                orientation = Configuration.ORIENTATION_PORTRAIT,
            )

        assertEquals(ResponsiveWindowClass.PHONE_LANDSCAPE, phone.windowClass)
        assertTrue(phone.hideStatusBar)
        assertEquals(ResponsiveWindowClass.PAD_PORTRAIT, pad.windowClass)
        assertFalse(pad.hideStatusBar)
    }

    @Test
    fun modalTransitionsUseTheFourWayClassWithoutLeakingLandscapeState() {
        val transitions =
            listOf(
                ResponsiveWindowClass.PHONE_PORTRAIT,
                ResponsiveWindowClass.PAD_LANDSCAPE,
                ResponsiveWindowClass.PHONE_LANDSCAPE,
                ResponsiveWindowClass.PAD_PORTRAIT,
            )

        assertEquals(
            listOf(false, false, true, false),
            transitions.map(SystemBarVisibilityPolicy::shouldHideStatusBar),
        )
    }

    @Test
    fun launcherActivityWiresLightIconToneWithoutInversion() {
        val source = appSource()
        assertTrue("must wire the composed tone request", source.contains("onSystemBarIconsLight = this::applyStatusBarTone"))
        assertTrue("light icons must clear the light-status-bar flag", source.contains("isAppearanceLightStatusBars = !lightIcons"))
        assertTrue("navigation icons follow the same tone", source.contains("isAppearanceLightNavigationBars = !lightIcons"))
    }

    /** Concatenation of the app shell sources; the launcher was split out of LauncherActivity.kt. */
    private fun appSource(): String {
        val dir =
            listOf(
                File("src/main/java/com/kireibox/app"),
                File("../app/src/main/java/com/kireibox/app"),
            ).firstOrNull(File::isDirectory)
                ?: error("app source dir not found")
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .joinToString("\n") { it.readText() }
    }
}
