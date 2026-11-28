package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import com.owl.tracker.i18n.I18n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Picking Hindi on the first screen or in Settings, and built-in category names following the language. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class LanguageFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    // I18n is shared by every test in this run, so English comes back after each one
    @After fun english() {
        compose.activity.getSharedPreferences("look", android.content.Context.MODE_PRIVATE).edit().remove("language").commit()
        I18n.load(compose.activity, "en")
    }

    @Test
    fun hindiFromTheFirstScreen() {
        with(compose) {
            waitForTag("language_choice")
            tapTag("language_hi")
            waitForText("शुरू करें")
            waitForText("इंटरनेट की अनुमति नहीं")
            assertEquals("hi", I18n.lang)
            tapTag("language_en")
            waitForText("Get started")
        }
    }

    @Test
    fun hindiFromSettingsAndCategoryNames() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForTag("import_all")
            tapTag("import_all")
            tapTag("import_continue")
            setMonthlyTotal("20000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            tapTag("tab_more")
            tapText("Settings")
            waitForTag("language_choice")
            tapTag("language_hi")
            waitForText("सेटिंग")
            // built-in categories show in Hindi; saving one unchanged keeps its English name underneath
            waitUntil(5_000) { vm.categories.value.any { it.name == "किराना" } }
            val groceries = vm.categories.value.first { it.name == "किराना" }
            runOnUiThread { vm.saveCategory(groceries.id, groceries.name, groceries.icon, groceries.color, groceries.countsAsSpend, groceries.parent) }
            waitForText("कैटेगरी अपडेट हुई")
            runOnUiThread { vm.setLanguage("en") }
            waitUntil(5_000) { vm.categories.value.any { it.id == groceries.id && it.name == "Groceries" } }
        }
    }
}
