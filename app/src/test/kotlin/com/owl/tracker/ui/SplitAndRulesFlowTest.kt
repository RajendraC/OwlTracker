package com.owl.tracker.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.owl.tracker.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Amazon's SMS ("… At WWW AMAZON IN") never says what was bought. Through the real UI: changing an Amazon payment's
 * category moves only that payment unless asked; a rule can be seen and deleted with its payments put back; and
 * one payment can be split across categories. The sample inbox has a ₹3,499 Amazon order (Thu 15 Oct) and a ₹799
 * Amazon refund.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class SplitAndRulesFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun setUpWithSms() = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
        typeInto("budget_input", "20000")
        tapTag("save_budget")
        waitForTag("budget_hero")
    }

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun amazonChangesOneAtATimeAndARuleCanBeDeletedWithItsPaymentsPutBack() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_activity")
            tapText("Amazon")
            waitForTag("delete_txn")

            // A store that sells everything: the picker says so, and "Use for every Amazon payment" starts off
            tapTag("change_category")
            waitForTag("mixed_store_note")
            onNodeWithTag("remember_rule").assertIsOff()
            waitForText("Off: only this payment changes")
            tapText("Groceries")
            waitForText("Moved to Groceries")

            // Making a rule on purpose: switch it on, and it says what it would change
            tapTag("change_category")
            tapTag("remember_rule")
            waitForText("This and 1 other Amazon payment, and every new one")
            tapText("Health")
            waitForText("Rule saved: Amazon → Health")

            // More → Rules shows it, with the warning, and deleting it puts both payments back in Shopping
            back()
            tapTag("tab_more")
            tapTag("open_rules")
            waitForText("Amazon → Health")
            waitForText("sells many kinds of things")
            tapTag("rule_delete_amazon")
            waitForTag("rule_delete_dialog")
            waitForText("Put 2 payments back in Shopping")
            tapTag("rule_delete_confirm")
            waitForText("Rule deleted · 2 payments put back")
            waitForTag("rules_empty")
        }
    }

    @Test
    fun onePaymentSplitAcrossTwoCategoriesAndJoinedBack() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_activity")
            tapText("Amazon")
            tapTag("split_open")
            // Shopping takes whatever the other parts leave
            waitForTag("split_amount_1")
            typeInto("split_amount_1", "499")
            waitForText("Adds up to ₹3,499")
            onNodeWithTag("split_rest").assertTextEquals("₹3,000")
            tapTag("split_save")
            waitForText("Split: Shopping and Groceries")
            waitForTag("split_parts")
            waitForText("Split: Shopping + Groceries")

            // Too much in one part can't be saved (a split payment opens its split from the Split heading)
            tapTag("split_parts")
            typeInto("split_amount_1", "5000")
            waitForText("The parts come to more than ₹3,499")
            typeInto("split_amount_1", "499")
            waitForText("Adds up to ₹3,499")

            // And it joins back into one category
            tapTag("split_join")
            waitForText("Joined back into Shopping")
            waitForTag("change_category")
        }
    }
}
