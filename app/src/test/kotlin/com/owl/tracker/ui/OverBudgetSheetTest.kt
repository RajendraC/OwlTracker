package com.owl.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 27 B: budgets that are over no longer stack up as a card each on Home. One line names them and, with the
 * Over budget figure, opens a sheet listing every one with See where it went and Adjust budget.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class OverBudgetSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    @Test
    fun oneLineOnHomeAndASheetWithThemAll() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForTag("import_all")
            tapTag("import_all")
            tapTag("import_continue")
            setMonthlyTotal("10000")
            tapTag("save_budget")
            waitForTag("figures")

            // A ₹1 budget on the two biggest categories: both are over
            val top = waitUntilValue { vm.dashboard.value?.byCategory?.take(2)?.takeIf { it.size == 2 } }
            runOnUiThread { top.forEach { vm.saveBudget(it.category.id, 100L) } }
            waitForTag("over_line")
            onAllNodesWithTag("over_banner").assertCountEquals(0)
            onNodeWithTag("over_line").assertTextContains("${top[0].category.name} and ${top[1].category.name} are over")
            onNodeWithTag("figure_over").assertTextContains("2 categories")
            waitForTag("figure_badge")

            // The line opens the sheet; See where it went goes to that category's payments
            tapTag("over_line")
            waitForTag("over_sheet")
            waitForTag("over_row_" + top[0].category.id)
            waitForTag("over_row_" + top[1].category.id)
            tapTag("over_see_" + top[0].category.id)
            waitForTag("search")
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            // The figure opens it too; Adjust budget opens that budget
            waitForTag("figures")
            tapTag("figure_over")
            waitForTag("over_sheet")
            tapTag("over_adjust_" + top[1].category.id)
            waitForTag("budget_amount")
        }
    }

    private fun <T : Any> waitUntilValue(get: () -> T?): T {
        var v: T? = null
        compose.waitUntil(10_000) { v = get(); v != null }
        return v!!
    }
}
