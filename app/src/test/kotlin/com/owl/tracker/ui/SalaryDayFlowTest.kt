package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.TestApp
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.core.TxKind
import com.owl.tracker.sms.ImportEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The salary card: put a tenth into a goal, and start budget months on the salary day. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [26, 36], qualifiers = "w393dp-h852dp-xxhdpi")
class SalaryDayFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]
    private val app get() = compose.activity.application as com.owl.tracker.OwlTrackerApp

    @After fun offersOff() { AppViewModel.homeOffersOn = false }

    private fun salaryOn(daysAgo: Long) = runBlocking {
        val at = vm.today().minusDays(daysAgo).atTime(10, 0).atZone(app.container.clock.zone).toInstant().toEpochMilli()
        app.container.repo.addManual(ImportEngine.manual(120_000_00, "ACME SOFTWARE", Cat.INCOME, at, Direction.CREDIT).copy(kind = TxKind.SALARY))
    }

    @Test
    fun salaryCardPutsATenthIntoTheGoalAndMovesTheMonth() {
        AppViewModel.homeOffersOn = true
        with(compose) {
            tapTag("get_started")
            tapTag("allow_sms")
            waitForTag("import_all")
            tapTag("import_all")
            tapTag("import_continue")
            setMonthlyTotal("50000")
            tapTag("save_budget")
            waitForTag("budget_hero")
            runOnUiThread { vm.saveGoal(null, "Goa trip", "🏖️", 50_000_00, null) }
            waitUntil(5_000) { vm.goals.value.isNotEmpty() }
            // salary today, and on the same day last month: a steady salary day
            salaryOn(0)
            salaryOn(java.time.temporal.ChronoUnit.DAYS.between(vm.today().minusMonths(1), vm.today()))
            runOnUiThread { vm.loadSalaries() }
            waitForTag("salary_card", 20_000)
            waitForText("Salary's in · ₹1,20,000")
            val day = vm.today().dayOfMonth.coerceAtMost(28)
            if (day != 1 && day < 27) {
                tapTag("salary_day_yes")
                waitUntil(5_000) { vm.settings.value?.budgetStartDay == day }
            }
            tapTag("salary_put_in")
            waitUntil(5_000) { vm.sheet.value is Sheet.GoalAdd }
            assertEquals(12_000_00L, (vm.sheet.value as Sheet.GoalAdd).suggest)
        }
    }
}
