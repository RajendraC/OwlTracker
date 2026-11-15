package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.owl.tracker.OwlTrackerApp
import com.owl.tracker.TestApp
import com.owl.tracker.core.Cat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            // Food: Swiggy and Zomato (Food Delivery) and Zepto (Groceries)
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
