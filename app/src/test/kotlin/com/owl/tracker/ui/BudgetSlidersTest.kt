package com.owl.tracker.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.owl.tracker.OwlTrackerApp
import com.owl.tracker.TestApp
import com.owl.tracker.core.Cat
import com.owl.tracker.core.SmsMessage
import com.owl.tracker.data.Schema
import com.owl.tracker.sampleInbox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Three months of SMS before October, so the first budget is set with a slider per category. */
fun historyInbox(): List<SmsMessage> {
    fun at(m: Int, d: Int) = LocalDate.of(2026, m, d).atTime(12, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
    var ref = 8100L
    fun sent(m: Int, d: Int, rupees: Int, to: String) =
        SmsMessage(ref, "VM-HDFCBK-S", "Sent Rs.$rupees.00 From HDFC Bank A/C *4821 To $to On %02d/%02d/26 Ref 62999998%04d".format(d, m, ref), at(m, d)).also { ref++ }
    fun salary(m: Int) =
        SmsMessage(ref, "VM-HDFCBK-S", "Rs.120000.00 credited to HDFC Bank A/c XX4821 on 01-%02d-26 by NEFT from ACME SOFTWARE (salary). Avl Bal Rs.1,46,250.00".format(m), at(m, 1)).also { ref++ }
    return listOf(
        salary(7), salary(8), salary(9),
        // Food rising: ₹4,000, ₹5,000, ₹6,000
        sent(7, 2, 4000, "SWIGGY"), sent(8, 9, 5000, "SWIGGY"), sent(9, 9, 6000, "SWIGGY"),
        // Shopping steady at ₹3,000
        sent(7, 12, 3000, "AMAZON"), sent(8, 12, 3000, "AMAZON"), sent(9, 12, 3000, "AMAZON"),
        // One stay in August
        sent(8, 14, 12000, "OYO ROOMS"),
    )
}

/** Budgets set with sliders, first time and from Budgets, and Reports' trend by category, through the real UI. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class BudgetSlidersTest {

    private val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OwlTrackerApp

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() { TestApp.inboxOverride = sampleInbox(historyInbox()) }
            override fun after() { TestApp.inboxOverride = null }
        })
        .around(compose)

    private fun budgets() = runBlocking { app.container.repo.observeBudgets().first() }

    private fun importAll() = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
    }

    private fun sliderValue(tag: String) = compose.run {
        waitForTag(tag)
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
    }

    /** The list scrolls, so find the node first. */
    private fun hasTextAt(tag: String, text: String) = compose.run {
        waitForTag(tag)
        onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(text)
    }

    @Test
    fun firstBudgetWithASliderPerCategoryThenAdjustedFromBudgets() {
        importAll()
        with(compose) {
            // Each category starts at its usual month: Food ₹5,000, Holiday ₹4,000, Shopping ₹3,000
            waitForTag("save_budgets")
            hasTextAt("slider_total", "₹12,000")
            waitForText("₹1,08,000") // left to save from ₹1,20,000 coming in
            waitForText("↑ Rising · Jul ₹4k, Aug ₹5k, Sep ₹6k")
            hasTextAt("enough_${Cat.FOOD}", "Enough in 2 of the last 3 months")
            hasTextAt("enough_${Cat.SHOPPING}", "Enough in 3 of the last 3 months")
            waitForText("↕ Up and down · ₹0 to ₹12k")
            waitForTag("more_categories")

            // Type an amount; the slider follows
            typeInto("budget_amount_${Cat.FOOD}", "9000")
            hasTextAt("slider_total", "₹16,000")
            assertEquals(9000f, sliderValue("budget_slider_${Cat.FOOD}").current)

            // Typed past the slider's end (twice the usual ₹3,000), the slider stretches: ₹25,000 → up to ₹32,000
            assertEquals(6_000f, sliderValue("budget_slider_${Cat.SHOPPING}").range.endInclusive)
            typeInto("budget_amount_${Cat.SHOPPING}", "25000")
            assertEquals(32_000f, sliderValue("budget_slider_${Cat.SHOPPING}").range.endInclusive)
            assertEquals(25_000f, sliderValue("budget_slider_${Cat.SHOPPING}").current)

            // Drag, in steps of ₹100
            waitForTag("budget_slider_${Cat.FOOD}")
            onNodeWithTag("budget_slider_${Cat.FOOD}").performSemanticsAction(SemanticsActions.SetProgress) { it(7_040f) }
            waitForIdle()
            hasTextAt("slider_total", "₹36,000")
            hasTextAt("enough_${Cat.FOOD}", "Enough in 3 of the last 3 months")

            tapTag("save_budgets")
            waitForTag("budget_hero")
            budgets().let { b ->
                assertEquals(7_000_00L, b[Cat.FOOD])
                assertEquals(25_000_00L, b[Cat.SHOPPING])
                assertEquals(4_000_00L, b[Cat.HOLIDAY])
                assertEquals(36_000_00L, b[Schema.OVERALL])
            }

            // Budgets → Adjust: starts from what was saved, with this month so far
            tapTag("tab_budgets")
            tapTag("adjust_budgets")
            waitForTag("slider_summary")
            hasTextAt("slider_total", "₹36,000")
            waitForText("This month: ₹1,228 spent · ₹5,772 left") // Swiggy and Zomato in October
            typeInto("budget_amount_${Cat.HOLIDAY}", "")
            waitForText("No budget for this category")
            hasTextAt("slider_total", "₹32,000")
            tapTag("save_budgets")
            waitForText("Budgets saved · ₹32,000 a month")
            waitForText("Monthly budget")
            budgets().let { b ->
                assertNull(b[Cat.HOLIDAY])
                assertEquals(32_000_00L, b[Schema.OVERALL])
            }
        }
    }

    @Test
    fun oneMonthlyTotalStillThereAndStartsFromTheUsualMonth() {
        importAll()
        with(compose) {
            tapTag("one_total")
            waitForText("₹12,000 a month")
            tapTag("by_category")
            waitForTag("save_budgets")
            setMonthlyTotal("15000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            assertEquals(15_000_00L, budgets()[Schema.OVERALL])
            assertNull(budgets()[Cat.FOOD])
        }
    }

    @Test
    fun aCategoryOnBudgetsGetsTheSliderToo() {
        importAll()
        with(compose) {
            setMonthlyTotal("20000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            tapTag("tab_budgets")

            // No budget on Food yet: the row opens the slider, starting at the usual month
            tapTag("set_budget_${Cat.FOOD}")
            waitForTag("budget_slider_${Cat.FOOD}")
            waitForText("Save · ₹5,000 a month")
            waitForText("↑ Rising · Jul ₹4k, Aug ₹5k, Sep ₹6k")
            hasTextAt("budget_sheet_now", "This month: ₹1,228 spent · ₹3,772 left")
            hasTextAt("budget_sheet_enough", "Enough in 2 of the last 3 months")
            // ₹100 a step: up to twice the usual month, ₹10,000, is 99 stops between the ends
            assertEquals(99, sliderValue("budget_slider_${Cat.FOOD}").steps)

            onNodeWithTag("budget_slider_${Cat.FOOD}").performSemanticsAction(SemanticsActions.SetProgress) { it(7_040f) }
            waitForText("Save · ₹7,000 a month")
            hasTextAt("budget_sheet_enough", "Enough in 3 of the last 3 months")

            // Any amount can be typed; the slider stretches to fit
            typeInto("budget_amount_${Cat.FOOD}", "25000")
            hasTextAt("budget_slider_end", "₹32,000")
            waitForText("Save · ₹25,000 a month")

            // One tap back to the usual month, then save
            tapTag("budget_quick_usual")
            waitForText("Save · ₹5,000 a month")
            tapTag("save_budget_sheet")
            waitForText("Food & Dining budget: ₹5,000 a month")
            assertEquals(5_000_00L, budgets()[Cat.FOOD])
            assertEquals(20_000_00L, budgets()[Schema.OVERALL]) // the month's budget is left as it was

            // Now under "By category": the same sheet, starting from the budget, with Remove
            tapTag("budget_row_${Cat.FOOD}")
            waitForText("Save · ₹5,000 a month")
            tapTag("remove_budget")
            waitForText("Food & Dining budget removed")
            assertNull(budgets()[Cat.FOOD])

            // The month's own budget: a slider too, at least ₹2,00,000 long, and any amount typed
            tapTag("edit_month_budget")
            waitForTag("budget_amount")
            hasTextAt("month_slider_end", "₹2,00,000")
            waitForText("Save · ₹20,000 a month")
            typeInto("budget_amount", "250000")
            waitForText("Save · ₹2,50,000 a month")
            hasTextAt("month_slider_end", "₹3,13,000")
            tapTag("save_budget_sheet")
            waitForText("Budget set to ₹2,50,000")
            assertEquals(2_50_000_00L, budgets()[Schema.OVERALL])
            // … and offers every category's slider
            tapTag("edit_month_budget")
            waitForText("Save · ₹2,50,000 a month")
            tapTag("open_adjust")
            waitForTag("slider_summary")
        }
    }

    @Test
    fun homeShowsWhatsChanging() {
        importAll()
        with(compose) {
            setMonthlyTotal("20000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            // Food rose from ₹4,000 to ₹6,000; Shopping held steady and the stay was a one-off, so only Food
            waitForTag("whats_changing")
            waitForTag("change_${Cat.FOOD}")
            waitForText("↑ Rising · 50% more than in Jul")
            assertEquals(0, onAllNodes(hasTestTag("change_${Cat.SHOPPING}")).fetchSemanticsNodes().size)
            assertEquals(0, onAllNodes(hasTestTag("change_${Cat.HOLIDAY}")).fetchSemanticsNodes().size)
            // Tap it: its months with amounts, a note, and its budget's slider
            tapTag("change_${Cat.FOOD}")
            waitForTag("change_bars_${Cat.FOOD}")
            waitForText("Usual month now ₹5,000. No budget on it yet.")
            tapTag("change_budget_${Cat.FOOD}")
            waitForTag("budget_slider_${Cat.FOOD}")
            waitForText("Save · ₹5,000 a month")
            tapTag("save_budget_sheet")
            waitForText("Food & Dining budget: ₹5,000 a month")
            waitForText("Over its ₹5,000 budget in 1 of the last 3 months. Usual month now ₹5,000.")
            // All › opens Reports at the trend by category
            tapTag("changes_all")
            waitForTag("report_category_trends")
        }
    }

    @Test
    fun reportsShowEachCategorysTrend() {
        importAll()
        with(compose) {
            setMonthlyTotal("20000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            tapTag("tab_more")
            tapText("Reports")
            waitForTag("report_category_trends")
            waitForTag("trend_${Cat.FOOD}")
            waitForText("↑ Rising · 50% more than in Jul")
            waitForText("↕ Up and down · ₹0 to ₹12k")
            hasTextAt("trend_usual_${Cat.FOOD}", "₹5k a month")
            // Tapping one narrows Reports to it
            tapTag("trend_${Cat.FOOD}")
            waitForTag("filter_category")
        }
    }
}

/** Right after a first import with no month behind it: a category's budget still has its slider, without dots. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class CategoryBudgetWithoutHistoryTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as OwlTrackerApp

    @Test
    fun theSliderStartsFromThisMonthSoFar() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForTag("import_all")
            tapTag("import_all")
            tapTag("import_continue")
            // Nothing before October: the single amount, and a word about the sliders
            waitForTag("budget_input")
            waitForText("In Budgets, tap any category")
            setMonthlyTotal("20000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            tapTag("tab_budgets")

            // Food: ₹1,228 so far in October, rounded up to ₹1,300
            tapTag("set_budget_${Cat.FOOD}")
            waitForTag("budget_slider_${Cat.FOOD}")
            waitForText("This month so far: ₹1,228")
            waitForText("Save · ₹1,300 a month")
            tapTag("save_budget_sheet")
            waitForText("Food & Dining budget: ₹1,300 a month")
            assertEquals(1_300_00L, runBlocking { app.container.repo.observeBudgets().first() }[Cat.FOOD])
        }
    }
}
