package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Streaks and badges on Home and their page, and "Was this right?" for a payment. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class StreaksAndChecksFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

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
    fun theStreakCardOpensStreaksAndBadges() {
        setUpWithSms()
        with(compose) {
            waitForTag("streak_card")
            tapTag("streak_card")
            waitForTag("streaks_screen")
            waitForText("Owl badges", substring = true)
            waitForTag("badge_thrifty")
            waitForTag("badge_investor")
        }
    }

    @Test
    fun wasThisRightConfirmsOrMoves() {
        setUpWithSms()
        with(compose) {
            waitUntil(10_000) { vm.periodTxns.value.any { it.direction == com.owl.tracker.core.Direction.DEBIT } }
            val t = vm.periodTxns.value.first { it.direction == com.owl.tracker.core.Direction.DEBIT && it.parts.isEmpty() }
            compose.runOnUiThread { vm.openSheet(Sheet.QuickCheck(t.id)) }
            waitForTag("quick_check")
            waitForTag("quick_check_question")
            waitForTag("quick_check_more")
            // Its own category, confirmed
            tapTag("quick_check_yes")
            waitForText("Thanks:", substring = true)
            waitUntil(5_000) { onAllNodesWithTag("quick_check").fetchSemanticsNodes().isEmpty() }
            waitUntil(5_000) { vm.periodTxns.value.first { it.id == t.id }.needsReview.not() }
        }
    }
}
