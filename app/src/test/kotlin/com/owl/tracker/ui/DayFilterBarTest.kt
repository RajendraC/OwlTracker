package com.owl.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.owl.tracker.core.DayLevel
import com.owl.tracker.core.DayTotal
import com.owl.tracker.ui.components.DayFilterBar
import com.owl.tracker.ui.theme.OwlTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The day bar says what the colour is about: everyday spending against the day's share, EMIs and rent apart. */
@RunWith(RobolectricTestRunner::class)
class DayFilterBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun anEmiDayIsGreenForItsEverydaySpendingAndShowsTheEmiApart() {
        // The tester's Mon 2 Nov: ₹20,300 in all, ₹18,861 of it EMI or rent, against a ₹3,317 share
        val day = DayTotal(LocalDate.parse("2026-11-02"), 2_030_000, 1_886_100, DayLevel.LIGHT)
        compose.setContent {
            OwlTrackerTheme(dark = false) { DayFilterBar(day, 9, DayShare(331_700, fromBudget = true), LocalDate.parse("2026-11-19"), {}) }
        }
        with(compose) {
            waitForText("Mon 2 Nov · ₹20,300")
            waitForText("₹1,439 everyday · Light")
            waitForTag("day_filter_bar")
            waitForText("day's share ₹3,317")
            waitForTag("day_filter_fixed")
            waitForText("+ EMI or rent · not in the colour")
            waitForText("₹18,861")
            waitForText("9 payments")
        }
    }

    @Test
    fun withNoBudgetItComparesWithAUsualDay() {
        val day = DayTotal(LocalDate.parse("2026-11-05"), 985_000, 0, DayLevel.VERY_HEAVY)
        compose.setContent {
            OwlTrackerTheme(dark = false) { DayFilterBar(day, 1, DayShare(300_000, fromBudget = false), LocalDate.parse("2026-11-19"), {}) }
        }
        with(compose) {
            waitForText("₹9,850 everyday · Very heavy")
            waitForText("usual day ₹3,000")
            waitForText("1 payment")
            onAllNodesWithTag("day_filter_fixed").assertCountEquals(0)
        }
    }
}
