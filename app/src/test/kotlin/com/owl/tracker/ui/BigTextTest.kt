package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.owl.tracker.TestApp
import com.owl.tracker.core.SmsMessage
import com.owl.tracker.sampleInbox
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * Big text, as many people set it on their phones: through every main screen, no word or amount may be broken across
 * lines ("Groce / ries", "₹6,00,7 / 99") and no amount or short label cut off ("₹1,14…", "Novem…"). A narrow phone
 * with large text (many Realme and Redmi phones), the largest text Android 14+ allows on a Pixel-size phone, and
 * normal text with lakh-size spending (a tester's ₹1,14,931 month). Three months of history first, so the budget sliders
 * and Reports' trend by category are on the way. Real text measurement (native graphics).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35])
class BigTextTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    private fun at(month: Int, day: Int) = LocalDate.of(2026, month, day).atTime(12, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    // A ₹98,000 purchase on top of the sample month: spending in lakhs, far over a ₹20,000 budget
    private val big = listOf(
        SmsMessage(7101, "VM-HDFCBK-S", "Sent Rs.98000.00 From HDFC Bank A/C *4821 To CROMA RETAIL On 12/10/26 Ref 629999997101", at(10, 12)),
    )

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() { TestApp.inboxOverride = sampleInbox(big + historyInbox()) }
            override fun after() { TestApp.inboxOverride = null }
        })
        .around(compose)

    private val found = mutableListOf<String>()

    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    /** Goes somewhere, then notes every text on screen that doesn't fit. A step that fails is noted too. */
    private fun screen(name: String, go: () -> Unit) {
        runCatching(go).onFailure { found += "$name: couldn't get there: ${it.message?.lineSequence()?.firstOrNull()}" }
        compose.waitForIdle()
        found += compose.textProblems().map { "$name: $it" }
    }

    private fun journey() = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
        screen("Budget setup, sliders") { waitForTag("save_budgets"); waitForTag("slider_row_food") }
        screen("Budget setup, more categories") { tapTag("more_categories"); waitForTag("slider_row_education") }
        screen("Budget setup, one total") { tapTag("one_total"); waitForTag("budget_input") }
        tapTag("by_category")
        setMonthlyTotal("20000")
        tapTag("save_budget")
        waitForTag("budget_hero")

        screen("Home") { waitForTag("figures") }
        screen("Home, over budget") { waitForTag("budget_line") }
        screen("Home, what's changing") { waitForTag("whats_changing"); tapTag("change_food"); waitForTag("change_bars_food") }
        screen("Home, calendar") { waitForTag("month_calendar") }
        screen("Home, where it went") { waitForText("Where it went") }
        screen("Home, a week") { tapTag("mode_week"); waitForTag("week_card") }
        screen("Home, a day") { waitForTag("week_card"); tapTag("week_day_2026-10-18"); waitForTag("day_filter") }
        screen("Activity") { tapTag("tab_activity"); waitForTag("search") }
        screen("A payment") { tapText("Amazon"); waitForTag("delete_txn") }
        screen("Category picker") { tapTag("change_category"); waitForTag("remember_rule") }
        back()
        screen("Split") { tapText("Amazon"); waitForTag("split_open"); tapTag("split_open"); waitForTag("split_save") }
        back()
        screen("Budgets") { tapTag("tab_budgets"); waitForText("Monthly budget") }
        screen("Adjust budgets") { tapTag("adjust_budgets"); waitForTag("slider_summary") }
        screen("Adjust budgets, a row") { waitForTag("slider_row_food") }
        back()
        screen("A category's budget") { tapTag("set_budget_food"); waitForTag("save_budget_sheet") }
        // Saved, then opened again: with Remove
        screen("A category's budget, with Remove") { tapTag("save_budget_sheet"); tapTag("budget_row_food"); waitForTag("remove_budget") }
        back()
        screen("The month's budget") { tapTag("edit_month_budget"); waitForTag("month_slider_end") }
        back()
        screen("More") { tapTag("tab_more"); waitForTag("open_rules") }
        screen("Settings") { tapText("Settings"); waitForTag("theme_choice") }
        back()
        screen("Reports") { waitForText("Reports"); tapText("Reports"); waitForTag("reports") }
        screen("Reports, income & expenses") { waitForTag("income_expenses") }
        screen("Reports, trend by category") { waitForTag("report_category_trends"); waitForTag("trend_food") }
        screen("Reports, by day of the week") { waitForText("By day of the week") }

        assertTrue("Text that doesn't fit:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f)
    fun narrowPhoneWithLargeText() = journey()

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun largestTextOnAPixelSizePhone() = journey()

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun normalTextWithLakhSizeSpending() = journey()
}
