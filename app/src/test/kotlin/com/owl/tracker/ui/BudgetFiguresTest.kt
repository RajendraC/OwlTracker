package com.owl.tracker.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.owl.tracker.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The three figures under the budget each open what they're about. With a ₹10,000 budget the sample month is
 * ₹3,607 over, but no category has a budget of its own: the middle figure names the biggest category instead of
 * saying "Over: None" next to "Over budget by ₹3,607".
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class BudgetFiguresTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun eachFigureOpensWhatItIsAbout() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForTag("import_all")
            tapTag("import_all")
            tapTag("import_continue")
            setMonthlyTotal("10000")
            tapTag("save_budget")
            waitForTag("figures")

            // Over for the month, no category budget over: the biggest category, never "None"
            onNodeWithTag("figure_over").assertTextContains("Biggest")

            // Spent → this month's money out in Activity
            tapTag("figure_spent")
            waitForTag("search")
            back()
            waitForTag("figures")

            // The biggest category → opened right there in "Where it went"
            tapTag("figure_over")
            waitForTag("ring_up")

            // The budget's pencil edits it
            waitForTag("edit_budget")
            tapTag("edit_budget")
            waitForTag("budget_amount")
        }
    }
}
