package com.owl.tracker.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import com.owl.tracker.ui.components.OwlMood
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The owl reacting to a payment, a milestone celebrated, and pictures to share that carry no amounts. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class OwlMomentsFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    @After fun tidy() { AppViewModel.celebrationsOn = false }

    private fun setUp(budget: String = "20000") = with(compose) {
        tapTag("get_started")
        tapTag("allow_sms")
        waitForTag("import_all")
        tapTag("import_all")
        tapTag("import_continue")
        setMonthlyTotal(budget)
        tapTag("save_budget")
        waitForTag("budget_hero")
    }

    /** No text in the picture (or anything else in its dialog) shows an amount or a percentage. */
    private fun assertPictureHasNoAmounts() {
        listOf("₹", "%").forEach { mark ->
            val found = compose.onAllNodes(hasAnyAncestor(hasTestTag("share_picture")) and hasText(mark, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("The picture shows \"$mark\"", found.isEmpty())
        }
    }

    @Test
    fun theOwlReactsInPlaceOfThePlainMessage() {
        setUp()
        with(compose) {
            runOnUiThread { vm.messages.tryEmit(UiMessage("Saved ₹240 · Food", "Undo", {}, OwlMood.CHEERING, "Nice! ₹1,077 left of today's ₹2,317")) }
            // after any message still showing from the setup
            waitForText("Saved ₹240 · Food", timeoutMs = 30_000)
            waitForTag("owl_reaction")
            waitForText("Nice! ₹1,077 left")
            waitForTag("owl_reaction_undo")
        }
    }

    @Test
    fun aSmallPaymentWithinTodaysShareGetsAHop() {
        // A budget big enough that today is well within its share
        setUp(budget = "2000000")
        with(compose) {
            runOnUiThread { vm.addManual(100, "Tea", com.owl.tracker.core.Cat.FOOD_DELIVERY, 0) }
            waitForText("Saved ₹1 ·", timeoutMs = 30_000)
            waitForTag("owl_reaction")
            waitForText("left of today's")
        }
    }

    @Test
    fun withTheOwlOffMessagesArePlain() {
        setUp()
        with(compose) {
            runOnUiThread { vm.setOwlReacts("off") }
            waitUntil(5_000) { vm.settings.value?.owlReacts == "off" }
            runOnUiThread { vm.addManual(100, "Tea", com.owl.tracker.core.Cat.FOOD_DELIVERY, 0) }
            waitForText("Added ₹1 to", timeoutMs = 30_000)
            assertTrue(onAllNodesWithTag("owl_reaction").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun aGoalReachedIsCelebratedAndSharedWithoutAmounts() {
        setUp()
        with(compose) {
            runOnUiThread { vm.saveGoal(null, "Goa trip", "🏖️", 1_000_00, null) }
            waitUntil(5_000) { vm.goals.value.isNotEmpty() }
            val id = vm.goals.value.first().id
            runOnUiThread { vm.addToGoal(id, 1_000_00) }
            waitForTag("celebration")
            waitForText("Goa trip: done!")
            tapTag("celebration_share")
            waitForTag("share_picture")
            waitForText("GOAL REACHED")
            assertPictureHasNoAmounts()
            tapTag("share_picture_close")
            waitUntil(5_000) { onAllNodesWithTag("share_picture").fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test
    fun theMonthIsSharedAsWinsOnly() {
        setUp()
        with(compose) {
            runOnUiThread { vm.openRecap(vm.period.value) }
            waitForTag("recap_intro")
            tapTag("recap_share")
            waitForTag("share_picture")
            waitForText("MY MONTH")
            assertPictureHasNoAmounts()
        }
    }

    @Test
    fun theOwlsLivelinessIsPickedInSettings() {
        setUp()
        with(compose) {
            tapTag("tab_more")
            tapText("Settings")
            waitForTag("owl_reacts_choice")
            tapTag("owl_reacts_calm")
            waitUntil(5_000) { vm.settings.value?.owlReacts == "calm" }
        }
    }
}
