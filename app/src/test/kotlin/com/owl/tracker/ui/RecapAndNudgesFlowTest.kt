package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The month's recap story, and the owl's nudge switches in Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class RecapAndNudgesFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    private fun setUpWithSms() = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
        setMonthlyTotal("20000")
        tapTag("save_budget")
        waitForTag("budget_hero")
    }

    @Test
    fun theRecapIsAStoryOfCards() {
        setUpWithSms()
        with(compose) {
            compose.runOnUiThread { vm.openRecap(vm.period.value) }
            waitForTag("recap_intro")
            waitForText("Your October, in a few cards")
            tapTag("recap_next")
            // ₹23,607 against ₹20,000
            waitForTag("recap_budget")
            waitForText("You went over by")
            tapTag("recap_next")
            waitForTag("recap_biggest_day")
            tapTag("recap_next")
            waitForTag("recap_top_merchant")
            tapTag("recap_close")
            waitUntil(5_000) { onAllNodesWithTag("recap").fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test
    fun settingsHaveASwitchForEachNudge() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_more")
            tapText("Settings")
            waitForTag("nudge_settings")
            listOf("daily", "alerts", "weekly", "recap").forEach { waitForTag("nudge_$it") }
            waitForText("Budget alerts")
        }
    }

    @Test
    fun coloursArePickedInSettings() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_more")
            tapText("Settings")
            waitForTag("palette_choice")
            tapTag("palette_teal")
            waitUntil(5_000) { vm.settings.value?.palette == "teal" }
            tapTag("palette_plum")
            waitUntil(5_000) { vm.settings.value?.palette == "plum" }
        }
    }
}
