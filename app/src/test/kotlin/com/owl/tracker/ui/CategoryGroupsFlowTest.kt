package com.owl.tracker.ui

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithTag
import com.owl.tracker.OwlTrackerApp
import com.owl.tracker.TestApp
import com.owl.tracker.core.Cat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Categories in groups through the real UI: Home's ring opens a group, Amazon gets sorted, Kids can be turned off. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class CategoryGroupsFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OwlTrackerApp

    private fun setUpWithSms() = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
        // Asked while setting up; yes until answered
        waitForTag("kids_question")
        setMonthlyTotal("20000")
        tapTag("save_budget")
        waitForTag("budget_hero")
    }

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun aGroupOpensIntoItsCategoriesThenTheirMerchants() {
        setUpWithSms()
        with(compose) {
            // Food: Swiggy and Zomato (Food Delivery) and Zepto (Groceries), named on its row
            waitForTag("ring_inside_${Cat.FOOD}")
            waitForText("Groceries ₹1.2k · Food Delivery ₹1.2k")
            tapTag("cat_food")
            waitForTag("ring_up")
            waitForTag("group_cat_food_delivery")
            waitForTag("group_cat_groceries")
            waitForText("Zomato, Swiggy")
            tapTag("group_cat_food_delivery")
            waitForTag("ring_merchant_swiggy")
            waitForTag("ring_merchant_zomato")
            // Back: to Food's categories, then to every group
            back()
            waitForTag("group_cat_groceries")
            back()
            waitUntil(5_000) { onAllNodesWithTag("ring_up").fetchSemanticsNodes().isEmpty() }
            waitForTag("cat_food")
        }
    }

    /** Recent lists every payment, so it isn't shown under an opened group or category; Ways to save and Reports stay. */
    @Test
    fun recentIsOnlyShownWithNothingOpened() {
        setUpWithSms()
        with(compose) {
            fun noRecent() = assertTrue("Recent is still shown", runCatching { waitForTag("recent", 1_500) }.isFailure)
            waitForTag("recent")
            // A group opened: no Recent below it
            tapTag("cat_food")
            waitForTag("ring_up")
            noRecent()
            waitForText("Income & expenses, ways to save")
            // One of its categories opened: still none
            tapTag("group_cat_food_delivery")
            waitForTag("ring_merchant_swiggy")
            noRecent()
            // Back to the group: none; back to all the groups: Recent again
            back()
            waitForTag("group_cat_groceries")
            noRecent()
            back()
            waitUntil(5_000) { onAllNodesWithTag("ring_up").fetchSemanticsNodes().isEmpty() }
            waitForTag("recent")
            // A category on its own (Cash isn't in a group) hides it too
            tapTag("cat_${Cat.CASH}")
            waitForTag("ring_up")
            noRecent()
        }
    }

    /** Where it went's third tab: the month by card and account; one opens into its groups, a group into its payments. */
    @Test
    fun paidByShowsTheMonthByCardAndAccount() {
        setUpWithSms()
        with(compose) {
            fun noRecent() = assertTrue("Recent is still shown", runCatching { waitForTag("recent", 1_500) }.isFailure)
            tapTag("where_paid_by")
            waitForTag("paid_by")
            waitForTag("paid_row_0")
            waitForText("••")
            // Not kept for the next time the app opens: a new start shows This month
            assertEquals("month", AppViewModel(app.container).whereView.value)
            // The groups' ring and rows are the other tab's
            assertTrue(onAllNodesWithTag("cat_food").fetchSemanticsNodes().isEmpty())
            waitForTag("recent")
            // One opened: what it was used for, by group; no tabs and no Recent while it's open
            tapTag("paid_row_0")
            waitForTag("paid_up")
            assertTrue(onAllNodesWithTag("where_paid_by").fetchSemanticsNodes().isEmpty())
            noRecent()
            val top = onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a group's row") { n ->
                n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("paid_top_") == true
            }).fetchSemanticsNodes().first().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
            tapTag(top)
            waitForTag(top.replace("paid_top_", "paid_payments_"))
            // Correct what it is: it moves to that section, and says it was set by you
            tapTag("paid_kind_change")
            waitForTag("paid_kind_dialog")
            tapTag("paid_kind_account")
            tapTag("paid_kind_save")
            waitUntil(5_000) { runBlocking { app.container.repo.settings() }.paidKinds.values.contains("account") }
            waitForText("Bank account (set by you)", substring = true)
            // Back: to every card and account, in sections by kind, where the tabs are again; This month is as before
            back()
            waitForTag("paid_row_0")
            waitForTag("paid_section_account")
            waitForTag("paid_kinds_bar")
            tapTag("where_this_month")
            waitForTag("cat_food")
        }
    }

    /** A credit card's Still to pay: set its billing cycle, add a part payment, mark it paid, undo. */
    @Test
    fun aCreditCardsBillWithItsCycleAndPayments() {
        setUpWithSms()
        with(compose) {
            tapTag("where_paid_by")
            waitForTag("paid_section_credit")
            // ICICI card ••1234: ₹3,499 on 15 Oct, the biggest credit card
            tapTag("paid_row_0")
            waitForText("ICICI Bank card ••1234")
            tapTag("card_tab_bill")
            tapTag("bill_cycle_set")
            waitForTag("bill_cycle_dialog")
            tapTag("bill_day_16")
            waitForText("17 Sep – 16 Oct")
            waitForText("pay by Thu 5 Nov")
            tapTag("bill_cycle_save")
            waitUntil(5_000) { runBlocking { app.container.repo.settings() }.cardCycles.contains("ICICI Bank card ••1234") }
            waitForTag("still_to_pay_amount")
            onNodeWithTag("still_to_pay_amount").assertTextEquals("₹3,499")
            // A part payment
            tapTag("bill_add_payment")
            waitForTag("bill_payment_dialog")
            onNodeWithTag("bill_payment_amount").performTextInput("1000")
            tapTag("bill_payment_save")
            waitUntil(5_000) { runCatching { onNodeWithTag("still_to_pay_amount").assertTextEquals("₹2,499") }.isSuccess }
            waitForTag("bill_payment_0")
            // Paid in full, then undone
            tapTag("bill_mark_paid")
            waitUntil(5_000) { runCatching { onNodeWithTag("still_to_pay_amount").assertTextEquals("Paid in full ✓") }.isSuccess }
            tapTag("bill_unmark_paid")
            waitUntil(5_000) { runCatching { onNodeWithTag("still_to_pay_amount").assertTextEquals("₹2,499") }.isSuccess }
            // The part payment taken back
            onNodeWithContentDescription("Remove this payment").performClick()
            waitUntil(5_000) { runCatching { onNodeWithTag("still_to_pay_amount").assertTextEquals("₹3,499") }.isSuccess }
            // What it was used for is still there
            tapTag("card_tab_used")
            waitUntil(5_000) { onAllNodesWithTag("still_to_pay").fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test
    fun anAmazonOrderWaitsInShoppingUntilItIsSorted() {
        setUpWithSms()
        with(compose) {
            tapTag("cat_shopping")
            waitForTag("group_unsorted_shopping")
            waitForText("Not sorted yet")
            tapTag("sort_shopping")
            waitForText("Shopping: what was it?")
            tapTag("pick_${Cat.ELECTRONICS}")
            waitForText("Moved to Phones & Electronics")
            val amazon = runBlocking { app.container.repo.transactions(0, Long.MAX_VALUE) }.first { it.merchantName == "Amazon" && !it.isMoneyIn }
            assertEquals(Cat.ELECTRONICS, amazon.categoryId)
            // Amazon sells everything: no rule was made, so the next Amazon order waits too
            assertEquals(0, runBlocking { app.container.repo.rules() }.count { it.pattern == "amazon" })
        }
    }

    @Test
    fun budgetsAreByGroupWithWhatIsInside() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_budgets")
            waitForTag("set_budget_${Cat.FOOD}")
            // No budget on a category inside a group, only on the group
            assertEquals(0, onAllNodesWithTag("set_budget_${Cat.GROCERIES}").fetchSemanticsNodes().size)
            waitForTag("inside_${Cat.FOOD}")
            waitForText("Groceries ₹1,240")
        }
    }

    @Test
    fun kidsCanBeTurnedOffInCategories() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_more")
            tapText("Categories")
            waitForTag("category_group_${Cat.KIDS}")
            waitForTag("category_row_${Cat.KIDS_EDUCATION}")
            tapTag("kids_switch")
            waitForText("Kids categories are off")
            waitForText("Off · hidden everywhere")
            assertFalse(runBlocking { app.container.repo.settings() }.kids)
            assertEquals(0, onAllNodesWithTag("category_row_${Cat.KIDS_EDUCATION}").fetchSemanticsNodes().size)
        }
    }
}
