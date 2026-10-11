package com.kireibox.ui

import android.app.UiModeManager
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiThemeResolver
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.components.KireiSegmentedDefaults
import com.kireibox.designsystem.dangerSolid
import com.kireibox.designsystem.kireiColorsFromTokens
import com.yuki.yukihub.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Takeover review fixes that are about how the UI is announced, labelled and themed rather than
 * laid out: segmented controls, text-field labels, duplicate announcements, footers, the glass
 * icon buttons and the API 31+ splash night mode.
 */
class KireiA11yContractTest {
    private val components: String by lazy { UiSourceFiles.components() }
    private val all: String by lazy { UiSourceFiles.all() }

    @Test
    fun segmentsAreRadioButtonsNamedAfterTheirSetting() {
        assertEquals("首页模式，沉浸式", KireiSegmentedDefaults.segmentDescription("首页模式", "沉浸式"))
        assertEquals("沉浸式", KireiSegmentedDefaults.segmentDescription("", "沉浸式"))
        assertTrue("segments are 40dp; Compose widens the touch target to 48dp", KireiSegmentedDefaults.Height >= 40.dp)
        val controls = UiSourceFiles.designSystemFile("components/KireiSelectionControls.kt")
        val segmented = controls.substring(controls.indexOf("fun <T> KireiSegmented("))
        assertTrue(segmented.contains("role = Role.RadioButton"))
        assertFalse("a value choice is not navigation", segmented.contains("role = Role.Tab"))
        assertTrue(segmented.contains("clearAndSetSemantics { contentDescription = description }"))
        assertTrue("the row title names the setting", UiSourceFiles.file("SettingsSegmentedRow.kt").contains("KireiSegmentedSpec(label = spec.title"))
    }

    @Test
    fun textFieldsKeepAPersistentLabel() {
        assertTrue(components.contains("val label: String? = null"))
        // Placeholder ink is never tertiary (~2:1 on surface3); light fields use the deeper
        // PlaceholderLight because secondary is only 4.2:1 on the surface3 fill (r3 review).
        assertTrue(components.contains("Text(options.placeholder, color = placeholderInk(colors)"))
        assertTrue(components.contains("if (colors.isDark || colors.highContrast) colors.secondary else KireiTokens.PlaceholderLight"))
        // One term for Bangumi's credential everywhere: 令牌 (r3 review).
        assertTrue(UiSourceFiles.file("SettingsCoverPages.kt").contains("KireiTextFieldOptions(label = \"个人令牌\""))
        assertTrue(UiSourceFiles.file("SettingsCoverPages.kt").contains("KireiTextFieldOptions(label = \"镜像地址\""))
        assertTrue(UiSourceFiles.file("SheetsPages.kt").contains("KireiTextFieldOptions(label = \"游戏名称\""))
    }

    @Test
    fun nothingIsAnnouncedTwice() {
        val dock = all.substring(all.indexOf("internal fun DockButton("), all.indexOf("internal fun CountBadge("))
        assertTrue("dock icons are decorative next to their label", dock.contains("Icon(icon, contentDescription = null"))
        val choice = all.substring(all.indexOf("internal fun SheetChoiceRow("), all.indexOf("internal fun ColumnScope.SheetCancelButton("))
        assertTrue(choice.contains("role = Role.RadioButton"))
        assertTrue(components.contains("KireiRowTrailing.Check -> Icon(Icons.Rounded.Check, contentDescription = null"))
        val overview = UiSourceFiles.file("SettingsOverviewCard.kt")
        assertTrue("one stop per stat tile", overview.contains("clearAndSetSemantics { contentDescription = \"\$label \$value\" }"))
    }

    @Test
    fun footersUseSecondaryInkAndDialogsAnnounceTheirTitle() {
        val group = components.substring(components.indexOf("internal fun KireiListGroup("))
        val footer = group.substring(group.indexOf("if (footer != null)"))
        assertTrue(footer.contains("color = colors.secondary"))
        assertTrue(components.contains("style = AppleTypography.headlineMedium, modifier = Modifier.semantics { heading() }"))
        val shell = UiSourceFiles.file("KireiShell.kt")
        assertTrue("the prompt card is a pane", shell.contains("semantics { paneTitle = current.title }"))
        assertFalse("the scrim is no unlabeled clickable node", shell.contains(".clickable(indication = null"))
    }

    @Test
    fun translucentIconButtonsCastNoShadowThroughTheirGlass() {
        assertTrue("light icon buttons are opaque", GlassFillPolicy.opaque(KireiGlassRole.ICON, opaqueFill = false, isDark = false))
        assertFalse(GlassFillPolicy.opaque(KireiGlassRole.ICON, opaqueFill = false, isDark = true))
        assertEquals(0.dp, GlassFillPolicy.elevation(KireiGlassRole.ICON, translucent = true))
        assertEquals(4.dp, GlassFillPolicy.elevation(KireiGlassRole.ICON, translucent = false))
        assertEquals(0.dp, GlassFillPolicy.elevation(KireiGlassRole.CONTENT, translucent = false))
        assertEquals(KireiElevation.e3Shadow, GlassFillPolicy.elevation(KireiGlassRole.NOTICE, translucent = true))
    }

    @Test
    fun keyboardFocusIsVisibleAndDisplayRowsAreOneStop() {
        // r3 review (WCAG 2.4.7): rows, segments and the slider show keyboard / D-pad focus.
        val row = UiSourceFiles.designSystemFile("components/KireiListRow.kt")
        assertTrue(row.contains("val focused by interaction.collectIsFocusedAsState()"))
        assertTrue(row.contains("if (focused) drawFocusRing(colors.accent)"))
        // Display-only rows (status rows, empty states) are one TalkBack stop.
        assertTrue(row.contains("else -> Modifier.semantics(mergeDescendants = true) {}"))
        val controls = UiSourceFiles.designSystemFile("components/KireiSelectionControls.kt")
        assertTrue(controls.contains("Modifier.border(KireiElevation.focusRingWidth, colors.accent, KireiSegmentedDefaults.IndicatorShape)"))
        assertTrue(controls.contains("interactionSource = interaction,"))
        assertEquals(2.dp, KireiElevation.focusRingWidth)
    }

    @Test
    fun destructiveInkUsesTheAaDangerPair() {
        // r3 review: white on the raw danger fails AA for button labels; titles in light too.
        assertTrue(components.contains("KireiButtonStyle.DESTRUCTIVE -> colors.dangerSolid.let { KireiButtonPalette(it.accent, it.accent, it.onAccent) }"))
        assertTrue(components.contains("KireiRowTone.DESTRUCTIVE -> colors.dangerSolid.accent"))
        assertTrue(components.contains("spec.destructive -> colors.dangerSolid.accent"))
        listOf(true, false).forEach { dark ->
            assertEquals(KireiTokens.badgeDanger(dark), kireiColorsFromTokens(isDark = dark).dangerSolid)
        }
    }

    @Test
    fun splashNightModeFollowsTheStoredThemeMode() {
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, KireiThemeResolver.applicationNightMode(ThemeMode.SYSTEM))
        assertEquals(UiModeManager.MODE_NIGHT_NO, KireiThemeResolver.applicationNightMode(ThemeMode.LIGHT))
        assertEquals(UiModeManager.MODE_NIGHT_YES, KireiThemeResolver.applicationNightMode(ThemeMode.DARK))
        val root = listOf(File("."), File("..")).first { File(it, "app/src/main").isDirectory }
        val launcher = File(root, "app/src/main/java/com/kireibox/app/launcher")
        assertTrue(File(launcher, "LauncherContent.kt").readText().contains("syncApplicationNightModeWhenHidden()"))
        val sync = File(launcher, "LauncherNightMode.kt").readText()
        assertTrue("API 31+ only", sync.contains("Build.VERSION.SDK_INT < Build.VERSION_CODES.S"))
        assertTrue(sync.contains("setApplicationNightMode(KireiThemeResolver.applicationNightMode(mode))"))
        assertTrue("never relaunch a running game task", sync.contains("hasOtherRunningTask(activity)"))
        assertTrue("applied only once the launcher is hidden, so in-app theme switches never relaunch", sync.contains("event == Lifecycle.Event.ON_STOP && !isChangingConfigurations"))
        assertFalse("never synced while resumed (that relaunches the visible page)", sync.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
        val splash = File(root, "app/src/main/res/values-v31/styles.xml").readText()
        assertTrue("the splash mark fits the circular mask", splash.contains("@drawable/ic_splash_mark"))
        assertTrue(File(root, "app/src/main/res/drawable/ic_splash_mark.xml").readText().contains("android:inset=\"27%\""))
    }
}
