package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.sms.ImportEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Event budgets: start one from a template, see a payment count, move it out, see it on Home. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class EventsFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]
    private val app get() = compose.activity.application as com.owl.tracker.OwlTrackerApp

    private fun spend(rupees: Long, merchant: String, category: String): Long = runBlocking {
        val at = vm.today().atTime(12, 0).atZone(app.container.clock.zone).toInstant().toEpochMilli()
        app.container.repo.addManual(ImportEngine.manual(rupees * 100, merchant, category, at, Direction.DEBIT))
    }

    @Test
    fun aTripBudgetCountsItsPaymentsAndOneCanBeMovedOut() {
        with(compose) {
            tapTag("get_started")
            tapTag("not_now")
            setMonthlyTotal("40000")
            tapTag("save_budget")
            waitForTag("budget_hero")

            // Budgets → Events; with none yet, a template starts one
            tapTag("tab_budgets")
            tapTag("budgets_events")
            waitForTag("events_screen")
            tapTag("events_start_trip")
            waitForTag("event_template_trip")
            tapTag("event_template_trip")
            onNodeWithTag("event_name").performTextInput(" to Goa")
            onNodeWithTag("event_budget").performScrollTo().performTextInput("10000")
            onNodeWithTag("event_save").performScrollTo()
            tapTag("event_save")
            waitUntil(5_000) { vm.events.value.size == 1 }
            val e = vm.events.value.single()
            assertEquals("trip", e.template)
            assertEquals(vm.today(), e.start)
            assertEquals(10_000_00L, e.budgetPaise)

            // Eating Out is one of a trip's categories; groceries isn't
            val dinner = spend(1500, "Fisherman's Wharf", Cat.DINING)
            spend(800, "Local Mart", Cat.GROCERIES)
            waitUntil(5_000) { vm.eventStatus.value[e.id]?.spend?.totalPaise == 1500_00L }

            // Shown on Home while it's on
            runOnUiThread { vm.go(Screen.HOME) }
            waitForTag("event_card_${e.id}", 10_000)

            // Moved out from its details, it no longer counts
            val t = runBlocking { app.container.repo.transaction(dinner)!! }
            runOnUiThread { vm.setInEvent(e.id, t, false) }
            waitUntil(5_000) { vm.eventStatus.value[e.id]?.spend?.totalPaise == 0L }
            runOnUiThread { vm.setInEvent(e.id, t, true) }
            waitUntil(5_000) { vm.eventStatus.value[e.id]?.spend?.totalPaise == 1500_00L }

            // Its own page
            // Budgets → Events lists it under On now, with the badge on the tab
            runOnUiThread { vm.openEvents() }
            waitForTag("events_on_badge")
            waitForTag("event_card_${e.id}")
            runOnUiThread { vm.openEvent(e.id) }
            waitForTag("event_screen")
            waitForText("Fisherman's Wharf")
        }
    }
}
