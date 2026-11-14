package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.owl.tracker.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renaming a payment, choosing its icon, and the Sports & Fitness category, through the real UI. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class MerchantEditFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun renameChooseIconAndPickSports() {
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForTag("import_all")
            tapTag("import_all")
            tapTag("import_continue")
            setMonthlyTotal("20000")
            tapTag("save_budget")
            waitForTag("budget_hero")

            tapTag("tab_activity")
            waitForText("Swiggy")
            tapText("Swiggy")

            // Rename: every Swiggy payment, and new ones too
            tapTag("rename_txn")
            waitForTag("rename_field")
            typeInto("rename_field", "Food delivery")
            tapTag("rename_save")
            waitForText("Renamed") // "Renamed to …" or "Renamed N payments to …"
            waitForTag("delete_txn") // back on the payment
            waitForText("Food delivery")

            // Icon: no recognised apps on this test phone, so choose the category icon
            tapTag("change_icon")
            waitForText("None of the apps Owl Tracker recognises are installed")
            waitForText("never reads the full list of apps")
            waitForTag("icon_picture") // any picture can be the icon too
            tapTag("icon_none")
            waitForText("App icon removed")
            waitForText("Category icon")

            // Sports & Fitness is a built-in category
            tapTag("change_category")
            waitForText("Sports & Fitness")
            tapText("Sports & Fitness")
            waitForText("Sports & Fitness")
        }
    }
}
