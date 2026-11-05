package com.owl.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.owl.tracker.DAY_MS
import com.owl.tracker.TEST_CLOCK
import com.owl.tracker.TestApp
import com.owl.tracker.licence.FakeUnlockStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * The 7-day free trial and the ₹50 unlock, through the real UI with a fake Google Play.
 */

/** Starts the app as if installed [installedDaysAgo] days ago, with its own fake Google Play. */
abstract class LicenceUiTest(private val installedDaysAgo: Long) {
    protected val compose = createAndroidComposeRule<MainActivity>()
    protected val store = FakeUnlockStore()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() {
                TestApp.installedAtOverride = TEST_CLOCK.millis() - installedDaysAgo * DAY_MS
                TestApp.storeOverride = store
            }
            override fun after() {
                TestApp.installedAtOverride = null
                TestApp.storeOverride = null
            }
        })
        .around(compose)

    /** Manual-mode setup with the suggested budget. Finishing setup is allowed even after the trial. */
    protected fun setUpManually() = with(compose) {
        tapTag("get_started")
        tapTag("not_now")
        tapTag("save_budget")
    }

    protected fun goBack() = with(compose) {
        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class TrialRunningTest : LicenceUiTest(installedDaysAgo = 0) {

    @Test
    fun trialBannerCountsDown_thenUnlockFromMore() {
        setUpManually()
        with(compose) {
            waitForTag("trial_banner")
            waitForText("Free trial: 7 days left")
            waitForText("Then a one-time ₹50.00 unlock")

            tapTag("tab_more")
            tapText("Unlock Owl Tracker")
            waitForTag("unlock_trial")
            waitForText("One payment of ₹50.00")
            tapTag("unlock_buy")
            waitForTag("unlock_done")
            assertEquals(1, store.buys)

            goBack()
            waitForText("Owl Tracker is unlocked")
            tapTag("tab_home")
            waitForTag("home")
            onAllNodesWithTag("trial_banner").assertCountEquals(0)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 33, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class TrialEndedTest : LicenceUiTest(installedDaysAgo = 8) {

    @Test
    fun readOnlyAfterTheTrial_unlockThenAddExpense() {
        setUpManually()
        with(compose) {
            waitForTag("trial_ended_banner")

            // Adding an expense is blocked: + opens the unlock screen instead
            tapTag("add_expense")
            waitForTag("unlock_ended")
            waitForText("Your free trial has ended")
            tapTag("unlock_buy")
            waitForTag("unlock_done")

            goBack()
            waitForTag("home")
            onAllNodesWithTag("trial_ended_banner").assertCountEquals(0)

            // Unlocked: adding works again
            tapTag("add_expense")
            typeInto("add_amount", "1500")
            typeInto("add_merchant", "Tea stall")
            tapTag("save_expense")
            waitForText("Tea stall")
        }
    }

    @Test
    fun pendingUpiPayment_unlocksWhenGooglePlayConfirmsIt() {
        store.nextBuyPending = true
        setUpManually()
        with(compose) {
            tapTag("trial_ended_unlock")
            tapTag("unlock_buy")
            waitForText("Payment pending")
            onAllNodesWithTag("unlock_done").assertCountEquals(0)

            store.completePending()
            waitForTag("unlock_done")
        }
    }

    @Test
    fun smsImportIsBlockedAfterTheTrial() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForTag("unlock_ended")
        }
    }

    @Test
    fun restorePurchaseAsksGooglePlayAgain() {
        setUpManually()
        with(compose) {
            tapTag("trial_ended_unlock")
            val before = store.refreshes
            tapTag("unlock_restore")
            waitForText("Checking Google Play for your purchase")
            assertTrue("Restore should ask Google Play again", store.refreshes > before)
        }
    }
}
