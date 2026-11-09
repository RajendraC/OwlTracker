package com.owl.tracker.ui

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.WindowCompat
import com.owl.tracker.OwlTrackerApp
import com.owl.tracker.TestApp
import com.owl.tracker.data.AppSettings
import com.owl.tracker.data.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Appearance → Theme: System, Light or Dark. It applies at once (screen and system bars),
 * is saved with the other settings, and survives the screen being rebuilt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi-notnight")
class ThemeSettingTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as OwlTrackerApp

    /** Dark as the activity draws it: the theme, and light (white) status bar icons to go with it. */
    private fun drawsDark(): Boolean {
        var dark = false
        var lightIcons = false
        compose.activityRule.scenario.onActivity { a ->
            dark = a.drawsDark
            lightIcons = !WindowCompat.getInsetsController(a.window, a.window.decorView).isAppearanceLightStatusBars
        }
        assertEquals("the status bar icons should suit the theme", dark, lightIcons)
        return dark
    }

    private fun savedTheme(): ThemeMode = runBlocking { app.container.repo.settings().theme }

    private fun mirrored(): String? =
        app.getSharedPreferences("look", Context.MODE_PRIVATE).getString("theme", null)

    private fun openSettings() = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
        typeInto("budget_input", "20000")
        tapTag("save_budget")
        waitForTag("budget_hero")
        tapTag("tab_more")
        tapText("Settings")
        waitForTag("theme_choice")
    }

    @Test
    fun lightPhone_pickDarkThenLightThenSystem() {
        with(compose) {
            openSettings()
            onNodeWithTag("theme_system").assertIsSelected()
            waitForText("Follows your phone's dark mode setting")
            assertFalse(drawsDark())

            tapTag("theme_dark")
            waitForText("Always dark")
            waitUntil(5_000) { drawsDark() }
            onNodeWithTag("theme_dark").assertIsSelected()
            onNodeWithTag("theme_system").assertIsNotSelected()
            assertEquals(ThemeMode.DARK, savedTheme())
            waitUntil(5_000) { mirrored() == "dark" }

            // Still dark after the screen is rebuilt (as on rotation)
            activityRule.scenario.recreate()
            waitForTag("theme_choice")
            onNodeWithTag("theme_dark").assertIsSelected()
            assertTrue(drawsDark())

            tapTag("theme_light")
            waitForText("Always light")
            waitUntil(5_000) { !drawsDark() }
            assertEquals(ThemeMode.LIGHT, savedTheme())

            tapTag("theme_system")
            waitForText("Follows your phone's dark mode setting")
            waitUntil(5_000) { savedTheme() == ThemeMode.SYSTEM }
            assertFalse(drawsDark())
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkPhone_followsItUntilLightIsPicked() {
        with(compose) {
            openSettings()
            assertTrue(drawsDark())

            tapTag("theme_light")
            waitUntil(5_000) { !drawsDark() }
            assertEquals(ThemeMode.LIGHT, savedTheme())

            tapTag("theme_system")
            waitUntil(5_000) { drawsDark() }
        }
    }

    @Test
    fun choiceIsStoredWithTheOtherSettings() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, AppSettings.fromMap(AppSettings(theme = mode).toMap()).theme)
        }
        // Older backups and installs have no theme: follow the phone
        assertEquals(ThemeMode.SYSTEM, AppSettings.fromMap(emptyMap()).theme)
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("purple"))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
    }
}
