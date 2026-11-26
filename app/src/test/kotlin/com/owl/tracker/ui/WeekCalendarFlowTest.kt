package com.owl.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.owl.tracker.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home by the week and the month's calendar, through the real UI. The test clock is Sunday 18 Oct 2026, so this
 * week is 18–24 Oct and last week 11–17 Oct; the budget is ₹20,000, so a day's share is ₹645.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class WeekCalendarFlowTest {

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

    @Test
    fun tappingADayInTheCalendarOpensItsPayments() {
        setUpWithSms()
        with(compose) {
            waitForTag("month_calendar")
            waitForText("October, week by week")
            // Thursday the 15th: one ₹3,499 Amazon order, well over the day's share, so its amount is shown (in red)
            waitForTag("cal_amount_2026-10-15")
            onNodeWithTag("cal_amount_2026-10-15", useUnmergedTree = true).assertTextEquals("₹3.5k")
            waitForText("the budget after EMIs and rent", substring = true)
            // No card under the calendar: the day opens in Activity with only its payments
            tapTag("cal_day_2026-10-15")
            waitForTag("activity_day")
            waitForText("Thu 15 Oct")
            waitForText("Amazon")
            onNodeWithTag("search").assertExists()
            onAllNodesWithTag("day_filter").assertCountEquals(0)
            // Clearing the day shows every day again
            tapTag("activity_day")
            onAllNodesWithTag("activity_day").assertCountEquals(0)
        }
    }

    @Test
    fun aWeekAtATimeFromTheCalendarAndBack() {
        setUpWithSms()
        with(compose) {
            // A week's total in the calendar opens that week
            tapTag("cal_week_2026-10-11")
            waitForTag("week_card")
            onNodeWithTag("period_label").assertTextEquals("11–17 Oct")
            waitForText("Last week")
            waitForText("Day by day")
            // A day in the week: Wednesday's Zomato order
            tapTag("week_day_2026-10-14")
            waitForTag("day_filter")
            waitForText("Payments on Wed 14 Oct")
            waitForText("Zomato")
            tapTag("week_day_2026-10-14")
            onAllNodesWithTag("day_filter").assertCountEquals(0)
            // Forward to this week; there's no week after it yet
            tapTag("period_next")
            waitForText("This week so far")
            onNodeWithTag("period_label").assertTextEquals("18–24 Oct")
            onNodeWithTag("period_next").assertIsNotEnabled()
            waitForTag("week_compare")
            // Back to the month: the calendar returns, the week card goes
            tapTag("mode_month")
            waitForTag("month_calendar")
            onAllNodesWithTag("week_card").assertCountEquals(0)
            onNodeWithTag("period_label").assertTextEquals("October 2026")
            // And the Week switch goes straight to this week
            tapTag("mode_week")
            waitForTag("week_card")
            onNodeWithTag("period_label").assertTextEquals("18–24 Oct")
            tapTag("period_prev")
            onNodeWithTag("period_label").assertTextEquals("11–17 Oct")
        }
    }
}
