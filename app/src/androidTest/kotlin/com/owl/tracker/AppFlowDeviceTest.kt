package com.owl.tracker

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.ui.MainActivity
import com.owl.tracker.ui.tapTag
import com.owl.tracker.ui.tapText
import com.owl.tracker.ui.typeInto
import com.owl.tracker.ui.waitForTag
import com.owl.tracker.ui.waitForText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * The production app on a real Android build: SQLCipher database, Keystore key, real activity.
 * Starts from a clean slate, uses manual mode (no SMS dialog), and ends with a full reset.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowDeviceTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() {
                runBlocking { ApplicationProvider.getApplicationContext<OwlTrackerApp>().container.eraseEverything() }
            }
        })
        .around(compose)

    @Test
    fun manualExpenseOverBudgetThenReset() {
        with(compose) {
            tapTag("get_started")
            tapTag("not_now")
            typeInto("budget_input", "1000")
            tapTag("save_budget")
            waitForText("SMS import is off", timeoutMs = 20_000)

            tapTag("add_expense")
            typeInto("add_amount", "1500")
            typeInto("add_merchant", "Tea stall")
            tapTag("save_expense")
            waitForText("over budget with", timeoutMs = 20_000)
            waitForText("Tea stall", timeoutMs = 20_000)

            tapTag("tab_more")
            tapText("Settings")
            waitForText("Encryption key", timeoutMs = 20_000)
            tapText("Reset Owl Tracker")
            tapTag("confirm_reset")
            waitForTag("get_started", timeoutMs = 20_000)
        }
    }

    /**
     * A fresh install is in its 7-day trial. The emulator images have no Play Store, so the real
     * Google Play Billing code reports Play as unavailable: the unlock screen must explain that
     * instead of hanging or crashing.
     */
    @Test
    fun freeTrialAndUnlockScreenWithoutGooglePlay() {
        with(compose) {
            tapTag("get_started")
            tapTag("not_now")
            tapTag("save_budget")
            waitForTag("trial_banner", timeoutMs = 20_000)
            waitForText("Free trial: 7 days left", timeoutMs = 20_000)

            tapTag("tab_more")
            tapText("Unlock Owl Tracker")
            waitForTag("unlock_trial", timeoutMs = 20_000)

            // Google Play must answer, or time out, with something to tell the person
            val store = ApplicationProvider.getApplicationContext<OwlTrackerApp>().container.licence.store
            val end = System.currentTimeMillis() + 30_000
            while (store.state.value.message == null && System.currentTimeMillis() < end) Thread.sleep(250)
            assertNotNull("No message from Google Play; its state is ${store.state.value}", store.state.value.message)
            waitForTag("unlock_message", timeoutMs = 10_000)
            waitForTag("unlock_buy")
        }
    }
}
