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
 * Home by the week and the month's calendar, through the real UI. The test clock is Saturday 18 Oct 2026, so this
 * week is 12–18 Oct; the budget is ₹20,000, so a day's share is ₹645.
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
        typeInto("budget_input", "20000")
        tapTag("save_budget")
        waitForTag("budget_hero")
    }

    @Test
    fun tappingADayInTheCalendarShowsOnlyThatDay() {
        setUpWithSms()
        with(compose) {
            waitForTag("month_calendar")
            waitForText("October, week by week")
            // Thursday the 15th: one ₹3,499 Amazon order, more than twice the day's ₹645 share
            tapTag("cal_day_2026-10-15")
            waitForTag("day_filter")
            waitForText("Thu 15 Oct · ₹3,499")
            waitForText("1 payment · Very heavy")
            waitForText("Where it went on Thu 15 Oct")
            waitForText("Payments on Thu 15 Oct")
            waitForText("Amazon")
            // The month's pictures and Recent are about the whole month, so they step aside
            onAllNodesWithTag("pictures").assertCountEquals(0)
            // Tapping the day again, or ✕, shows every day again
            tapTag("day_filter_clear")
            onAllNodesWithTag("day_filter").assertCountEquals(0)
            waitForText("Recent")
            // A day with nothing spent can be picked too
            tapTag("cal_day_2026-10-13")
            waitForText("Nothing on Tue 13 Oct")
            tapTag("cal_day_2026-10-13")
            onAllNodesWithTag("day_filter").assertCountEquals(0)
        }
    }

    @Test
    fun aWeekAtATimeFromTheCalendarAndBack() {
        setUpWithSms()
        with(compose) {
            // A week's total in the calendar opens that week
            tapTag("cal_week_2026-10-05")
            waitForTag("week_card")
            onNodeWithTag("period_label").assertTextEquals("5–11 Oct")
            waitForText("Last week")
            // Forward to this week; there's no week after it yet
            tapTag("period_next")
            waitForText("This week so far")
            onNodeWithTag("period_label").assertTextEquals("12–18 Oct")
            onNodeWithTag("period_next").assertIsNotEnabled()
            waitForTag("week_compare")
            waitForText("Day by day")
            // A day in the week: Wednesday's Zomato order
            tapTag("week_day_2026-10-14")
            waitForTag("day_filter")
            waitForText("Payments on Wed 14 Oct")
            waitForText("Zomato")
            tapTag("week_day_2026-10-14")
            onAllNodesWithTag("day_filter").assertCountEquals(0)
            // Back to the month: the calendar returns, the week card goes
            tapTag("mode_month")
            waitForTag("month_calendar")
            onAllNodesWithTag("week_card").assertCountEquals(0)
            onNodeWithTag("period_label").assertTextEquals("October 2026")
            // And the Week switch goes straight to this week
            tapTag("mode_week")
            waitForTag("week_card")
            onNodeWithTag("period_label").assertTextEquals("12–18 Oct")
            tapTag("period_prev")
            onNodeWithTag("period_label").assertTextEquals("5–11 Oct")
        }
    }
}
