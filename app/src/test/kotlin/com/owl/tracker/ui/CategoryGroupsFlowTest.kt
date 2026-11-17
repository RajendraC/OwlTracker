package com.owl.tracker.ui

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
            assertTrue(runBlocking { app.container.repo.settings() }.wherePaidBy)
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
            assertFalse(runBlocking { app.container.repo.settings() }.wherePaidBy)
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
