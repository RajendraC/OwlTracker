package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.data.HouseFile
import com.owl.tracker.data.HousePayment
import com.owl.tracker.data.Households
import com.owl.tracker.sms.ImportEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Shared household: start one, take in a partner's update, see both on Home, leave. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class HouseholdFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]
    private val app get() = compose.activity.application as com.owl.tracker.OwlTrackerApp

    /**
     * Sharing a file fills FileProvider's static cache with this test's temporary folders; later tests in the same
     * JVM get new ones, so the cache is cleared after each test here.
     */
    @org.junit.After
    fun forgetFileProviderRoots() {
        runCatching {
            val f = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            f.isAccessible = true
            (f.get(null) as MutableMap<*, *>).clear()
        }
    }

    private fun spend(rupees: Long, merchant: String, category: String): Long = runBlocking {
        val at = vm.today().atTime(12, 0).atZone(app.container.clock.zone).toInstant().toEpochMilli()
        app.container.repo.addManual(ImportEngine.manual(rupees * 100, merchant, category, at, Direction.DEBIT))
    }

    @Test
    fun startTakeInAnUpdateAndLeave() {
        with(compose) {
            tapTag("get_started")
            tapTag("not_now")
            setMonthlyTotal("40000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            spend(700, "Big Basket", Cat.GROCERIES)

            tapTag("tab_more")
            onNodeWithTag("open_household").performScrollTo()
            tapTag("open_household")
            waitForTag("house_screen")
            tapTag("house_start")
            waitForTag("house_me")
            onNodeWithTag("house_me").performTextInput("Raj")
            onNodeWithTag("house_setup_budget").performScrollTo().performTextInput("50000")
            onNodeWithTag("house_setup_go").performScrollTo()
            tapTag("house_setup_go")
            waitUntil(5_000) { vm.household.value != null }
            val h = vm.household.value!!
            assertEquals("Raj", h.meName)
            assertEquals(50_000_00L, h.budgetPaise)
            assertEquals(false, h.shareAll)
            waitUntil(5_000) { vm.houseView.value?.people?.firstOrNull()?.totalPaise == 700_00L }

            // Priya joins from the invite on her phone and sends her update
            val invite = Households.peek(Households.invite(h)) as HouseFile.Invite
            val priya = Households.join(invite, "Priya", shareAll = true)
            val today = vm.today()
            val bytes = Households.update(priya, app.container.clock.millis(), mapOf(today to mapOf(Cat.GROCERIES to 1300_00L)), emptyMap(),
                listOf(HousePayment(today, "DMart", 1300_00, Cat.GROCERIES)))
            // Amma's update opened straight after: both stick
            val amma = Households.join(invite, "Amma", shareAll = false)
            val fromAmma = Households.update(amma, app.container.clock.millis(), mapOf(today to mapOf(Cat.GROCERIES to 500_00L)), emptyMap(), emptyList())
            runOnUiThread { vm.openHouseBytes(bytes); vm.openHouseBytes(fromAmma) }
            waitUntil(5_000) { vm.houseView.value?.people?.size == 3 }
            assertEquals(2500_00L, vm.houseView.value!!.totalPaise)
            waitForTag("house_person_1")
            tapTag("house_person_1")
            waitForTag("house_payments_1")
            tapTag("house_payments_1")
            waitForText("DMart")

            // Opening the same update again changes nothing
            runOnUiThread { vm.openHouseBytes(bytes) }
            waitForText("You already have this update.")

            // On Home
            runOnUiThread { vm.go(Screen.HOME) }
            waitForTag("house_card", 10_000)

            // Leaving removes everyone's spending from this phone
            runOnUiThread { vm.go(Screen.HOUSEHOLD) }
            waitForTag("house_leave")
            onNodeWithTag("house_leave").performScrollTo()
            tapTag("house_leave")
            tapTag("house_leave_confirm")
            waitUntil(5_000) { vm.household.value == null }
            waitUntil(5_000) { vm.houseView.value == null }
        }
    }

    @Test
    fun anInviteOpensTheJoinSheet() {
        with(compose) {
            tapTag("get_started")
            tapTag("not_now")
            setMonthlyTotal("40000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            val other = Households.start("Sharma home", "Asha", false, 0, 1)
            runOnUiThread { vm.openHouseBytes(Households.invite(other)) }
            waitForTag("house_me")
            waitForText("Join Sharma home")
            onNodeWithTag("house_me").performTextClearance()
            onNodeWithTag("house_me").performTextInput("Vik")
            onNodeWithTag("house_setup_go").performScrollTo()
            tapTag("house_setup_go")
            waitUntil(5_000) { vm.household.value?.id == other.id }
            assertEquals(listOf("Asha"), vm.household.value!!.others.map { it.name })
            // Joining sends the first update straight away
            waitUntil(5_000) { (vm.household.value?.lastSentAt ?: 0) > 0 }
        }
    }
}
