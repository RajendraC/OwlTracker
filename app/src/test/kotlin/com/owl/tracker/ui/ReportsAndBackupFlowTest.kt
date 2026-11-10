package com.owl.tracker.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.owl.tracker.TestApp
import com.owl.tracker.backup.BackupCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Reports you can tap through, and backups, through the real UI. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class ReportsAndBackupFlowTest {

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
    fun reportsNarrowToWhateverIsTapped() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_more")
            tapText("Reports")
            waitForTag("report_summary")
            // Income & expenses: the ₹1,20,000 salary and ₹799 refund are income, and expenses never net the refund off
            waitForTag("income_expenses")
            waitForText("of your income")
            waitForText("refunds and money back")
            tapTag("range_3")
            waitForText("Month by month")
            waitForText("Each month")
            waitForText("Biggest payments")

            // A category: a chip appears and the payments list narrows to it; Income & expenses is about everything, so it goes
            tapTag("cat_food")
            waitForTag("filter_category")
            // The ring opens into Food's merchants, with a way back up
            waitForTag("ring_up")
            waitForTag("ring_merchant_swiggy")
            onAllNodesWithTag("income_expenses").assertCountEquals(0)
            waitForText("matching payment")
            // Tapping the chip lets go again
            tapTag("filter_category")
            waitForText("Biggest payments")
            onAllNodesWithTag("filter_category").assertCountEquals(0)

            // A merchant, a month and a weekday can be combined
            tapTag("merchant_swiggy")
            waitForTag("filter_merchant")
            tapTag("bar_2")
            waitForTag("filter_bucket")
            // The month opens day by day; a day can be picked, and the arrow goes back up
            waitForTag("drill_up")
            tapTag("day_2")
            waitForTag("filter_day")
            waitForText("Clear all")
            tapText("Clear all")
            waitForText("Biggest payments")

            tapTag("weekday_saturday")
            waitForTag("filter_weekday")
        }
    }

    /** Home: tapping the budget circle opens that category right below, with its picture; the ring opens a merchant's payments; Back goes up. */
    @Test
    fun theBudgetCircleOnHomeDrillsDownRightThere() {
        setUpWithSms()
        with(compose) {
            waitForTag("budget_ring")
            // The top of the circle is the biggest category's part
            onNodeWithTag("budget_ring").performTouchInput { click(Offset(centerX + 4f, 11.dp.toPx())) }
            waitForTag("ring_up")
            // The opened category's picture sits above its merchants (Home only)
            waitForTag("category_scene")
            waitForText("tap the picture to play it again", substring = true)
            waitForTag("category_donut")
            onNodeWithTag("category_donut").performTouchInput { click(Offset(centerX + 6f, 23.dp.toPx())) }
            waitForTag("ring_payments")

            fun back() = activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            back()
            waitUntil(5_000) { onAllNodesWithTag("ring_payments").fetchSemanticsNodes().isEmpty() }
            waitForTag("ring_up")
            back()
            waitUntil(5_000) { onAllNodesWithTag("ring_up").fetchSemanticsNodes().isEmpty() }
            waitForTag("budget_hero")
            onAllNodesWithTag("ring_up").assertCountEquals(0)
        }
    }

    /** Touching the ring opens a category, then a merchant's payments; Back goes up one level at a time, then leaves. */
    @Test
    fun theRingOpensByTouchAndBackGoesUpOneLevelAtATime() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_more")
            tapText("Reports")
            waitForTag("report_summary")
            waitForTag("category_donut")
            // The biggest slice starts at the top of the ring
            fun touchTopOfRing() = onNodeWithTag("category_donut").performTouchInput { click(Offset(centerX + 6f, 23.dp.toPx())) }
            touchTopOfRing()
            waitForTag("ring_up")
            touchTopOfRing()
            waitForTag("ring_payments")
            waitForText("payment")

            fun back() = activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            back()
            waitUntil(5_000) { onAllNodesWithTag("ring_payments").fetchSemanticsNodes().isEmpty() }
            waitForTag("ring_up")
            back()
            waitUntil(5_000) { onAllNodesWithTag("ring_up").fetchSemanticsNodes().isEmpty() }
            waitForTag("report_summary")
            back()
            waitForText("Backup & restore")
        }
    }

    @Test
    fun moneyInShowsWithAPlusAndCanBeLeftOutOrDeleted() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_activity")
            tapText("Received")
            waitForText("+₹799")
            tapText("+₹799")
            waitForTag("count_money_in")
            waitForText("Never counts as spending")
            // Left out of Income & expenses, then deleted
            tapTag("count_money_in")
            waitForText("Left out of Income & expenses")
            tapTag("delete_txn")
            waitForText("Transaction deleted")
        }
    }

    @Test
    fun aNewPhoneCanRestoreFromTheWelcomeScreen() {
        with(compose) {
            tapTag("welcome_restore")
            waitForTag("backup_screen")
            waitForText("Restore from a backup")
            waitForTag("restore_pick")
            onAllNodesWithTag("backup_save").assertCountEquals(0)
        }
    }

    @Test
    fun backupNeedsMatchingPasswords_thenOpensTheShareSheetWithAnEncryptedFile() {
        setUpWithSms()
        with(compose) {
            tapTag("tab_more")
            tapText("Backup & restore")
            waitForTag("backup_never")
            onNodeWithTag("backup_save").assertIsNotEnabled()

            typeInto("backup_password", "secret-one")
            typeInto("backup_confirm", "secret-two")
            waitForText("The two passwords don't match")
            onNodeWithTag("backup_share").assertIsNotEnabled()

            typeInto("backup_confirm", "secret-one")
            onNodeWithTag("backup_save").assertIsEnabled()
            tapTag("backup_share")
            waitForTag("backup_done", timeoutMs = 30_000)

            // The share sheet was asked to send a content:// link to the backup, readable once
            val shadow = shadowOf(activity)
            waitUntil(10_000) { shadow.peekNextStartedActivity() != null }
            val chooser = shadow.nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(send)
            assertEquals("application/json", send!!.type)
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            assertEquals("content", uri!!.scheme)
            assertEquals("${activity.packageName}.backups", uri.authority)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

            // ...and the file behind it is encrypted
            val file = File(activity.cacheDir, "backups").listFiles()!!.single()
            assertTrue(file.name.startsWith("owl-tracker-backup-"))
            assertTrue(BackupCodec.peek(file.readBytes()).encrypted)
            assertTrue(!file.readText().contains("Swiggy"))
        }
    }
}
