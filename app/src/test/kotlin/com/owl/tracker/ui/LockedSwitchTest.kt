package com.owl.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.owl.tracker.ui.components.SwitchRow
import com.owl.tracker.ui.theme.OwlTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A switch that can't change (money into a card, a transfer) looks locked and a tap does nothing. */
@RunWith(RobolectricTestRunner::class)
class LockedSwitchTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aLockedSwitchIsShownDisabledAndATapChangesNothing() {
        var taps by mutableIntStateOf(0)
        compose.setContent {
            OwlTrackerTheme(dark = false) {
                SwitchRow("Count as income", "A credit card bill payment, so never income.", false, { taps++ },
                    modifier = Modifier.testTag("count_money_in"), enabled = false)
            }
        }
        compose.onNodeWithTag("count_money_in").assertIsNotEnabled().performClick()
        compose.waitForIdle()
        assertEquals(0, taps)
    }

    @Test
    fun theTestersHdfcCardCreditReadsAsABillPayment() {
        assertTrue(CardBills.saysPayment("Payment of Rs 7803 received towards your HDFC Bank Credit Card ending 3643"))
    }
}
