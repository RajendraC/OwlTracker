package com.owl.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.owl.tracker.TestApp
import com.owl.tracker.core.SmsMessage
import com.owl.tracker.sampleInbox
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

/**
 * Saving nudges through the real UI: the sample inbox plus Netflix and Spotify in August and September,
 * so the two repeat and Home suggests dropping one. Hidden with × until next month; always in Reports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class SavingNudgeTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    private fun at(month: Int, day: Int) = LocalDate.of(2026, month, day).atTime(12, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    private val history = listOf(
        SmsMessage(7001, "VM-HDFCBK-S", "Sent Rs.119.00 From HDFC Bank A/C *4821 To SPOTIFY On 20/08/26 Ref 629999997001", at(8, 20)),
        SmsMessage(7002, "VM-HDFCBK-S", "Sent Rs.119.00 From HDFC Bank A/C *4821 To SPOTIFY On 20/09/26 Ref 629999997002", at(9, 20)),
        SmsMessage(7003, "VM-HDFCBK-S", "Sent Rs.649.00 From HDFC Bank A/C *4821 To NETFLIX On 17/09/26 Ref 629999997003", at(9, 17)),
    )

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() { TestApp.inboxOverride = sampleInbox(history) }
            override fun after() { TestApp.inboxOverride = null }
        })
        .around(compose)

    @Test
    fun homeNudgesTowardsSavingAndReportsListsEveryWay() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForTag("import_all")
            tapTag("import_all")
            tapTag("import_continue")
            typeInto("budget_input", "20000")
            tapTag("save_budget")
            waitForTag("budget_hero")

            // Netflix (₹649) and Spotify (₹119) each in two months: dropping the cheaper saves ₹119 a month
            waitForTag("saving_nudge")
            waitForText("Save about ₹119 a month")
            waitForText("2 subscriptions cost ₹768 a month")

            tapTag("saving_more")
            waitForTag("ways_to_save")
            waitForText("You could save about ₹119 a month")
            waitForText("That's ₹1,428 a year.")
            onNodeWithContentDescription("Back").performClick()

            // Hidden until next month
            tapTag("saving_hide")
            waitForText("Saving tips hidden until next month")
            waitForTag("budget_hero")
            onAllNodesWithTag("saving_nudge").assertCountEquals(0)
        }
    }
}
