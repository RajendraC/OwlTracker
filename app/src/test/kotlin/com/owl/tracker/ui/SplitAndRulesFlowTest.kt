package com.owl.tracker.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
        setMonthlyTotal("20000")
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

            // A store that sells everything: the picker says so, and asks just this one or all
            tapTag("change_category")
            waitForTag("mixed_store_note")
            tapText("Groceries")
            waitForTag("apply_dialog")
            waitForText("Just this payment, or all 2 payments from Amazon?")
            tapTag("apply_one")
            waitForText("Moved to Groceries")

            // Making a rule on purpose: all of them
            tapTag("change_category")
            tapText("Medicines")
            tapTag("apply_all")
            waitForText("Rule saved: Amazon → Medicines")

            // More → Rules shows it, with the warning, and deleting it puts both payments back in Shopping
            back()
            tapTag("tab_more")
            tapTag("open_rules")
            waitForText("Amazon → Medicines")
            waitForText("sells many kinds of things")
            tapTag("rule_delete_amazon")
            waitForTag("rule_delete_dialog")
            waitForText("Put 2 payments back in Shopping")
            tapTag("rule_delete_confirm")
            waitForText("Rule deleted · 2 payments put back")
            waitForTag("rules_empty")
        }
    }

    /** "Review and choose": the payments from the same payee listed, one sent somewhere else, only the ticked ones moved. */
    @Test
    fun reviewAndChooseMovesTheTickedPaymentsEachWhereItGoes() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_activity")
            tapText("Amazon")
            waitForTag("delete_txn")
            tapTag("change_category")
            tapText("Groceries")
            waitForTag("apply_dialog")
            tapTag("apply_review")
            waitForTag("apply_row_1")
            waitForText("this one", substring = true)
            // This one goes to Medicines instead
            val mine = (0..1).first { i ->
                onAllNodes(
                    androidx.compose.ui.test.hasTestTag("apply_row_$i") and
                        androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText("this one", substring = true)),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
            }
            tapTag("apply_to_$mine")
            waitForTag("apply_pick_dialog")
            tapTag("apply_pick_${com.owl.tracker.core.Cat.MEDICINES}")
            waitForText("→ Medicines")
            tapTag("apply_new_ones")  // not for new ones
            tapTag("apply_move")
            waitForText("Moved", substring = true)
            waitForText("Medicines")
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
