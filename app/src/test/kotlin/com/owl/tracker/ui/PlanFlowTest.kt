package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Savings goals, "Can I afford it?" and Bills coming up. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class PlanFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    private fun setUpWithSms() = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
        setMonthlyTotal("40000")
        tapTag("save_budget")
        waitForTag("budget_hero")
    }

    @Test
    fun aGoalIsMadeAndFilled() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_more")
            tapTag("open_goals")
            waitForTag("goals_screen")
            tapTag("goal_new")
            waitForTag("goal_name")
            onNodeWithTag("goal_name").performTextInput("Goa trip")
            onNodeWithTag("goal_target").performTextInput("25000")
            tapTag("goal_save")
            waitForText("Goa trip")
            waitUntil(5_000) { vm.goals.value.size == 1 }
            val id = vm.goals.value.single().id
            tapTag("goal_add_$id")
            waitForTag("goal_add_amount")
            onNodeWithTag("goal_add_amount").performTextInput("12000")
            tapTag("goal_add_save")
            waitUntil(5_000) { vm.goals.value.single().savedPaise == 12_000_00L }
            waitForText("48%")
        }
    }

    @Test
    fun canIAffordItSaysWhatsLeft() {
        setUpWithSms()
        with(compose) {
            tapTag("afford_open")
            waitForTag("afford_amount")
            onNodeWithTag("afford_amount").performTextInput("8000")
            waitForTag("afford_verdict")
            // What's left of the ₹40,000 budget, less the ₹8,000
            val a = vm.afford(8_000_00L, null)!!
            assertEquals(a.left - 8_000_00L, a.leftAfter)
            waitForText(com.owl.tracker.core.Money.formatInr(a.leftAfter))
        }
    }

    @Test
    fun billsComingUpOpensFromMore() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_more")
            tapTag("open_bills")
            waitForTag("bills_screen")
            waitForText("Bills coming up")
        }
    }
}
