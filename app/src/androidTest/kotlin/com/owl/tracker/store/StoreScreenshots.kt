package com.owl.tracker.store

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.owl.tracker.OwlTrackerApp
import com.owl.tracker.core.Cat
import com.owl.tracker.data.Schema
import com.owl.tracker.licence.TrialClock
import com.owl.tracker.sms.AndroidSmsSource
import androidx.lifecycle.ViewModelProvider
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.MainActivity
import com.owl.tracker.ui.Screen
import com.owl.tracker.ui.tapTag
import com.owl.tracker.ui.tapText
import com.owl.tracker.ui.typeInto
import com.owl.tracker.ui.waitForNode
import com.owl.tracker.ui.waitForTag
import com.owl.tracker.ui.waitForText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Captures the Google Play screenshots and the promo-video tour on an emulator. Not part of the
 * normal test run: it only runs with `-e storeShots 1` (see .github/workflows/store-assets.yml),
 * and only on an emulator, because it replaces the SMS inbox and erases the app's data.
 *
 * [capture] fills the emulator inbox with [DemoInbox], goes through onboarding like a new user, sets
 * a few budgets and saves a PNG of each screen. [tour] then walks through the app slowly while the
 * workflow records the screen.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshots {

    private val app get() = ApplicationProvider.getApplicationContext<OwlTrackerApp>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val out: File by lazy { File(app.getExternalFilesDir(null), "store-screens").apply { mkdirs() } }

    private val compose = createAndroidComposeRule<MainActivity>()

    /** Runs before the activity starts: [capture] needs a fresh app with a full inbox. */
    private val prepare = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                assumeTrue("Store screenshots run only with -e storeShots 1", InstrumentationRegistry.getArguments().getString("storeShots") == "1")
                assumeTrue("Store screenshots replace the SMS inbox, so they run only on an emulator", isEmulator())
                when (description.methodName) {
                    "capture", "seedInbox" -> freshAppWithDemoInbox()
                    "smsDemo" -> prepareSmsDemo()
                }
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(prepare).around(compose)

    @Test
    fun capture() {
        with(compose) {
            waitForTag("get_started", 30_000)
            shot("01_welcome")

            tapTag("get_started")
            waitForTag("allow_sms")
            shot("02_disclosure")

            tapTag("allow_sms") // READ_SMS is already granted, so no system dialog
            waitForText("transactions from", timeoutMs = 60_000)
            shot("03_import_filters")

            tapTag("review_button")
            waitForTag("import_selected", 30_000)
            shot("04_import_review")

            tapTag("import_selected")
            waitForTag("import_continue", 60_000)
            shot("05_import_done")
            tapTag("import_continue")

            val budgets = budgetsFromSpending()
            // With months of history the setup opens on a slider per category
            compose.waitForNode(hasTestTag("save_budgets") or hasTestTag("budget_input"), 30_000)
            if (exists(hasTestTag("save_budgets"))) {
                Thread.sleep(800)
                shot("06_budget_setup")
                runCatching { tapTag("more_categories") }
                runCatching { scrollTo(hasTestTag("slider_row_${Cat.HOLIDAY}")) }
                shot("06b_budget_setup_lower")
                tapTag("one_total")
            }
            typeInto("budget_input", (budgets.getValue(Schema.OVERALL) / 100).toString())
            shot("06c_budget_one_total")
            tapTag("save_budget")
            runBlocking { budgets.filterKeys { it != Schema.OVERALL }.forEach { (k, v) -> app.container.repo.setBudget(k, v) } }

            waitForTag("budget_hero", 30_000)
            Thread.sleep(6000) // the pictures come in a moment later, then play (the first WebView takes longest)
            shot("10_home")
            // What's changing, with its first row opened (then closed again for the shots after it)
            runCatching {
                scrollTo(hasTestTag("whats_changing"), below = 40f)
                val row = SemanticsMatcher("a change") { n ->
                    n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
                        ?.let { t -> t.startsWith("change_") && !t.startsWith("change_bars_") && !t.startsWith("change_budget_") } == true
                }
                compose.onAllNodes(row)[0].performClick()
                Thread.sleep(900)
                shot("10c_home_changes")
                compose.onAllNodes(row)[0].performClick()
                Thread.sleep(500)
            }.onFailure { Log.w(TAG, "What's changing: not shown", it) }
            // Home runs up behind the status bar: keep the heading clear of it
            runCatching { scrollTo(hasText("This month in pictures"), below = 40f) }
            Thread.sleep(4500)
            shot("10b_home_pictures")
            runCatching { scrollTo(hasText("Recent")) }
            shot("11_home_lower")
            runCatching { scrollTo(hasTestTag("saving_nudge")) }
            shot("12_saving_nudge")
            // Home drill-down: tap the budget circle's biggest part, then come back up
            runCatching {
                scrollTo(hasTestTag("budget_ring"))
                onAllNodes(hasTestTag("budget_ring"))[0].performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX + 4f, 11.dp.toPx())) }
            }
            Thread.sleep(1500)
            shot("13_home_drill")
            runCatching { activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
            Thread.sleep(800)
            // The 3D ring with the owl: the month, then the slice at the front turned towards you
            runCatching {
                scrollTo(hasTestTag("owl_bubble"), below = 70f)
                Thread.sleep(3800)
                shot("14_home_ring")
                val ring = compose.onAllNodes(hasTestTag("ring3d"))[0]
                val node = ring.fetchSemanticsNode()
                val g = com.owl.tracker.ui.components.Ring3DGeometry(node.size.width.toFloat(), node.size.height.toFloat(), compose.density.density)
                val r = (g.r0 + g.r1) / 2f
                ring.performTouchInput { click(g.project(0f, r, 0f, 0f, com.owl.tracker.ui.components.RING_TILT)) }
                Thread.sleep(2600)
                shot("15_home_ring_pick")
                // and the one at the back, which then comes round to the front
                ring.performTouchInput { click(g.project(0f, -r, 0f, 0f, com.owl.tracker.ui.components.RING_TILT)) }
                Thread.sleep(2600)
                shot("15b_home_ring_pick")
            }

            // Where it went with the months behind it: a row's bars opened, then the 6 months tab (and back)
            section("where_trends") {
                runCatching { scrollTo(hasTestTag("where_bars_${Cat.SHOPPING}"), below = 220f) }
                Thread.sleep(600)
                shot("15c_home_where_bars")
                tapTag("where_bars_${Cat.SHOPPING}")
                Thread.sleep(900)
                shot("15d_home_where_bars_open")
                tapTag("where_bars_${Cat.SHOPPING}")
                scrollTo(hasTestTag("where_six_months"), below = 20f)
                tapTag("where_six_months")
                Thread.sleep(1000)
                scrollTo(hasTestTag("where_six_months"), below = 20f)
                shot("15e_home_six_months")
                runCatching { tapTag("six_${Cat.SHOPPING}") }
                Thread.sleep(700)
                shot("15f_home_six_months_pick")
                runCatching { tapTag("six_${Cat.SHOPPING}") }
                tapTag("where_this_month")
                Thread.sleep(600)
            }

            // The month as a calendar, a day picked in it, then a week at a time and a day in it
            section("week") {
                val day = java.time.LocalDate.now().minusDays(1)
                scrollTo(hasTestTag("month_calendar"), below = 40f)
                Thread.sleep(800)
                shot("16_home_calendar")
                tapTag("cal_day_$day")
                scrollTo(hasTestTag("day_filter"), below = 40f)
                Thread.sleep(2500)
                shot("17_home_day")
                tapTag("day_filter_clear")
                // The screenshots' clock is a Sunday morning: last week is the full one, with yesterday in it
                withVm { it.showWeek(); it.previousWeek() }
                runCatching { onAllNodes(hasTestTag("home"))[0].performScrollToIndex(0) }
                Thread.sleep(1500)
                shot("18_home_week")
                tapTag("week_day_$day")
                scrollTo(hasTestTag("day_filter"), below = 40f)
                Thread.sleep(2500)
                shot("19_home_week_day")
                withVm { it.showMonth() }
            }

            section("budgets") {
                tapTag("tab_budgets")
                Thread.sleep(800)
                shot("20_budgets")
                tapTag("adjust_budgets")
                waitForTag("slider_summary", 20_000)
                Thread.sleep(800)
                shot("21_adjust_budgets")
                runCatching { scrollTo(hasTestTag("slider_row_${Cat.SHOPPING}")) }
                shot("22_adjust_budgets_shopping")
                withVm { it.back() }
                Thread.sleep(600)
                // A category's own budget: the slider sheet
                tapTag("budget_row_${Cat.SHOPPING}")
                waitForTag("save_budget_sheet", 20_000)
                Thread.sleep(900)
                shot("23_category_budget")
                withVm { it.closeSheet() }
                Thread.sleep(600)
                // The month's budget: on a slider too
                tapTag("edit_month_budget")
                waitForTag("month_slider_end", 20_000)
                Thread.sleep(900)
                shot("24_month_budget")
                withVm { it.closeSheet() }
                Thread.sleep(600)
            }
            section("activity") {
                tapTag("tab_activity")
                waitForText("Swiggy", timeoutMs = 20_000)
                shot("30_activity")
                tapText("Swiggy")
                Thread.sleep(1200)
                shot("31_txn_detail")
                withVm { it.closeSheet() }
                Thread.sleep(600)
                runCatching { tapText("Received") }
                Thread.sleep(800)
                shot("32_money_in")
                runCatching { onAllNodes(hasText("+₹", substring = true))[0].performClick() }
                Thread.sleep(1200)
                shot("33_money_in_detail")
                withVm { it.closeSheet() }
                Thread.sleep(600)
                runCatching { tapText("All") }
            }

            // Amazon: the picker that moves one payment at a time, a split, then a rule on the Rules screen
            section("split") {
                tapTag("tab_activity")
                waitForText("Amazon", timeoutMs = 20_000)
                // An everyday rule for Swiggy, to sit next to the Amazon one made below (this opens Swiggy's detail: close it)
                withVm { vm -> vm.periodTxns.value.firstOrNull { it.merchantKey == "swiggy" }?.let { vm.setCategory(it.id, it.categoryId, true) } }
                Thread.sleep(800)
                withVm { it.closeSheet() }
                Thread.sleep(400)
                tapText("Amazon")
                Thread.sleep(800)
                tapTag("change_category")
                Thread.sleep(1200)
                shot("34_amazon_picker")
                // A rule made on purpose: the switch turned on
                tapTag("remember_rule")
                Thread.sleep(400)
                tapText("Groceries")
                Thread.sleep(1200)
                withVm { it.closeSheet(); it.go(com.owl.tracker.ui.Screen.RULES) }
                waitForTag("rule_amazon")
                // Let the "Rule saved" messages pass, so they don't cover the list
                runCatching { compose.waitUntil(15_000) { compose.onAllNodes(hasText("Rule saved: Amazon", substring = true)).fetchSemanticsNodes().isNotEmpty() } }
                runCatching { compose.waitUntil(15_000) { compose.onAllNodes(hasText("Rule saved", substring = true)).fetchSemanticsNodes().isEmpty() } }
                shot("37_rules")
                tapTag("rule_delete_amazon")
                Thread.sleep(1500)
                shot("38_rule_delete")
                // Delete it with its payments put back in Shopping, then split one order
                tapTag("rule_delete_confirm")
                Thread.sleep(800)
                withVm { it.back() }
                Thread.sleep(600)
                tapTag("tab_activity")
                tapText("Amazon")
                Thread.sleep(800)
                tapTag("split_open")
                waitForTag("split_amount_1")
                typeInto("split_amount_1", "499")
                Thread.sleep(1200)
                shot("35_split")
                tapTag("split_save")
                Thread.sleep(1500)
                shot("36_split_saved")
                withVm { it.closeSheet() }
                Thread.sleep(600)
            }
            section("reports") {
                nav(Screen.MORE)
                shot("40_more")
                nav(Screen.INSIGHTS)
                waitForTag("report_summary", 20_000)
                runCatching { tapTag("range_6") }
                shot("50_reports_top")
                runCatching { scrollTo(hasTestTag("income_expenses")) }
                shot("50a_income_expenses")
                runCatching { scrollTo(hasTestTag("pl_spending")) }
                shot("50b_income_expenses_list")
                runCatching { scrollTo(hasText("Each month")) }
                shot("50c_income_expenses_months")
                runCatching { scrollTo(hasTestTag("ways_to_save")) }
                shot("50d_ways_to_save")
                runCatching { scrollTo(hasTestTag("report_trend")) }
                runCatching { tapTag("bar_4") }   // open October, day by day
                Thread.sleep(1500)
                shot("50e_trend_drill")
                runCatching { tapTag("drill_up") }
                Thread.sleep(800)
                scrollTo(hasTestTag("report_categories"))
                shot("51_reports_categories")
                // A card just below the screen can be composed but not placed yet: bring it in by its key first
                runCatching { compose.onNodeWithTag("reports").performScrollToKey("category_trends") }
                    .onFailure { Log.w(TAG, "Trend by category: no card", it) }
                compose.waitForIdle()
                runCatching { scrollTo(hasTestTag("report_category_trends")) }
                Thread.sleep(1200)
                shot("51b_category_trends")
                runCatching { tapTag("trends_all") }
                Thread.sleep(600)
                shot("51c_category_trends_all")
                runCatching { compose.onNodeWithTag("reports").performScrollToKey("categories") }
                runCatching { scrollTo(hasTestTag("report_categories")) }
                runCatching { tapTag("cat_${Cat.SHOPPING}") }
                Thread.sleep(800)
                shot("52_reports_filtered")
                runCatching { tapTag("ring_merchant_amazon") }   // the third level: one merchant's payments
                Thread.sleep(1200)
                shot("52b_ring_merchant")
                withVm { it.reportBack() }
                Thread.sleep(600)
                runCatching { scrollTo(hasTestTag("report_filters")) }
                shot("53_reports_filtered_top")
                runCatching { tapTag("filter_category") } // clear the filter
                scrollTo(hasTestTag("report_merchants"))
                shot("54_reports_merchants")
                scrollTo(hasTestTag("report_weekdays"))
                shot("55_reports_weekdays")
                runCatching { scrollTo(hasTestTag("report_payments")) }
                shot("56_reports_payments")
            }
            section("categories") {
                nav(Screen.CATEGORIES)
                shot("60_categories")
                onAllNodes(hasContentDescription("Edit Food", substring = true))[0].performClick() // "Food & Dining"
                Thread.sleep(1000)
                shot("61_category_edit")
            }
            section("backup") {
                withVm { it.openBackup() }
                waitForTag("backup_screen", 20_000)
                shot("70_backup")
            }
            section("settings") {
                nav(Screen.SETTINGS)
                waitForTag("theme_choice", 20_000)
                shot("75_settings")
                // The opposite of the phone's own mode, then back to following the phone
                val phoneDark = (app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val flip = if (phoneDark) "theme_light" else "theme_dark"
                onNode(hasTestTag(flip)).performClick()
                Thread.sleep(1200)
                shot("76_settings_theme_flipped")
                onNode(hasTestTag("theme_system")).performClick()
                Thread.sleep(800)
            }
            nav(Screen.HOME)
        }
        val files = out.listFiles()?.map { it.name }?.sorted().orEmpty()
        Log.i(TAG, "Saved ${files.size} screenshots in $out: $files")
        assertTrue("Only ${files.size} screenshots; failed sections: $failures", files.size >= 10)
    }

    /** Fills the SMS inbox for [smsDemo] ([prepare] does the work). The workflow then takes READ_SMS away again. */
    @Test
    fun seedInbox() {
        Log.i(TAG, "Demo inbox ready")
    }

    /**
     * The walkthrough for Google Play's SMS Permissions Declaration video: a new user goes from the
     * welcome screen through the disclosure, Android's own permission prompt and the import, then opens
     * a payment's original SMS. Saves a PNG at each step in sms-demo/, and in taps.txt where each tap
     * lands ("<shot> <x> <y>" in screen pixels), for tools/make_sms_demo_video.py.
     */
    @Test
    fun smsDemo() {
        assertTrue(
            "READ_SMS must not be granted yet, so Android's prompt shows",
            app.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED,
        )
        with(compose) {
            waitForTag("get_started", 30_000)
            demoShot("d01_welcome", hasTestTag("get_started"))
            tapTag("get_started")

            waitForTag("allow_sms")
            demoShot("d02_disclosure", hasTestTag("allow_sms"))
            onNode(hasTestTag("allow_sms")).performClick()

            // Android's own permission prompt, tapped the way a person would
            val allow = waitForAllowButton(30_000)
            Thread.sleep(1000)
            val r = Rect().also { allow.getBoundsInScreen(it) }
            demoShot("d03_android_prompt", tap = r.centerX() to r.centerY())
            if (!allow.performAction(AccessibilityNodeInfo.ACTION_CLICK)) shell("input tap ${r.centerX()} ${r.centerY()}")

            waitForText("transactions from", timeoutMs = 90_000)
            demoShot("d04_choose_senders", hasTestTag("review_button"))
            tapTag("review_button")

            waitForTag("import_selected", 30_000)
            demoShot("d05_review", hasTestTag("import_selected"))
            tapTag("import_selected")

            waitForTag("import_continue", 90_000)
            demoShot("d06_saved", hasTestTag("import_continue"))
            tapTag("import_continue")

            val budgets = budgetsFromSpending()
            compose.waitForNode(hasTestTag("save_budgets") or hasTestTag("budget_input"), 30_000)
            if (exists(hasTestTag("save_budgets"))) {
                Thread.sleep(800)
                demoShot("d07_budget", hasTestTag("save_budgets"))
                tapTag("save_budgets")
            } else {
                typeInto("budget_input", (budgets.getValue(Schema.OVERALL) / 100).toString())
                demoShot("d07_budget", hasTestTag("save_budget"))
                tapTag("save_budget")
            }
            useBudgets(budgets)

            waitForTag("budget_hero", 30_000)
            demoShot("d08_home")

            tapTag("tab_activity")
            waitForText("Swiggy", timeoutMs = 20_000)
            demoShot("d09_activity", hasText("Swiggy"))
            tapText("Swiggy")
            Thread.sleep(1200)

            val viewSms = hasText("View original SMS", substring = true)
            demoShot("d10_payment", viewSms)
            onAllNodes(viewSms)[0].performClick()
            Thread.sleep(900)
            runCatching { onNode(hasTestTag("delete_txn")).performScrollTo() }
            demoShot("d11_original_sms")
            withVm { it.closeSheet() }
            Thread.sleep(600)

            nav(Screen.MORE)
            demoShot("d12_no_internet")
        }
    }

    /** Slow walk through the app for the promo video; the workflow records the screen meanwhile. */
    @Test
    fun tour() {
        with(compose) {
            waitForTag("budget_hero", 30_000)
            Thread.sleep(3500)
            swipeList(up = true); Thread.sleep(1800)
            swipeList(up = false); Thread.sleep(1200)

            tapTag("tab_budgets"); Thread.sleep(2800)

            tapTag("tab_activity"); Thread.sleep(1800)
            swipeList(up = true); Thread.sleep(1600)

            tapTag("tab_more"); Thread.sleep(1500)
            tapText("Reports")
            waitForTag("report_summary", 20_000)
            runCatching { tapTag("range_6") }
            Thread.sleep(2600)
            swipeList(up = true); Thread.sleep(1800)
            runCatching { tapTag("cat_${Cat.SHOPPING}") }; Thread.sleep(2400)
            runCatching { tapTag("filter_category") }; Thread.sleep(1200)
            swipeList(up = true); Thread.sleep(1800)
            swipeList(up = true); Thread.sleep(2200)

            nav(Screen.MORE); Thread.sleep(900)
            tapText("Backup & restore"); Thread.sleep(2800)
            nav(Screen.HOME); Thread.sleep(3000)
        }
    }

    // ---- set-up ----

    /** For [smsDemo]: the app as [seedInbox] left it (not set up yet, demo inbox filled), unlocked, screenshots allowed. */
    private fun prepareSmsDemo() {
        val ctx = app
        ctx.getSharedPreferences(TrialClock.PREFS, Context.MODE_PRIVATE).edit().putBoolean("unlock_owned", true).commit()
        shell("settings put global sysui_demo_allowed 1")
        shell("am broadcast -a com.android.systemui.demo -e command enter")
        shell("am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1030")
        shell("am broadcast -a com.android.systemui.demo -e command battery -e level 92 -e plugged false")
        shell("am broadcast -a com.android.systemui.demo -e command network -e wifi hide -e mobile show -e datatype none -e level 4")
        shell("am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
        runBlocking { ctx.container.repo.updateSettings { it.copy(screenPrivacy = false) } }
        demoOut.listFiles()?.forEach { it.delete() }
    }

    private fun freshAppWithDemoInbox() {
        val ctx = app
        // Unlocked, so no trial banner on the screenshots. Written before the app's container (and its
        // licence) is first built. Never set outside this emulator-only test.
        ctx.getSharedPreferences(TrialClock.PREFS, Context.MODE_PRIVATE).edit().putBoolean("unlock_owned", true).commit()
        shell("settings put global sysui_demo_allowed 1")
        shell("am broadcast -a com.android.systemui.demo -e command enter")
        shell("am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1030")
        shell("am broadcast -a com.android.systemui.demo -e command battery -e level 92 -e plugged false")
        shell("am broadcast -a com.android.systemui.demo -e command network -e wifi hide -e mobile show -e datatype none -e level 4")
        shell("am broadcast -a com.android.systemui.demo -e command notifications -e visible false")

        runBlocking { ctx.container.eraseEverything() }
        // FLAG_SECURE (the default) makes screenshots black.
        runBlocking { ctx.container.repo.updateSettings { it.copy(screenPrivacy = false) } }

        instrumentation.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.READ_SMS)
        // Lets this app write to the SMS provider, as the default SMS app could. Emulator only.
        shell("appops set ${ctx.packageName} WRITE_SMS allow")
        val sms = Uri.parse("content://sms")
        val removed = runCatching { ctx.contentResolver.delete(sms, null, null) }.getOrDefault(-1)
        val messages = DemoInbox.messages(System.currentTimeMillis())
        var inserted = 0
        for (m in messages) {
            val v = ContentValues().apply {
                put("address", m.address); put("body", m.body); put("date", m.timestamp)
                put("date_sent", m.timestamp); put("read", 1); put("seen", 1); put("type", 1)
            }
            if (runCatching { ctx.contentResolver.insert(Uri.parse("content://sms/inbox"), v) }.getOrNull() != null) inserted++
        }
        val inInbox = runBlocking { AndroidSmsSource(ctx).read(0).size }
        Log.i(TAG, "Inbox: removed $removed, inserted $inserted of ${messages.size}, now holds $inInbox")
        assertTrue("The SMS provider ignored the demo messages ($inInbox in the inbox)", inInbox >= messages.size * 9 / 10)
        out.listFiles()?.forEach { it.delete() }
    }

    /**
     * Budgets that tell the same story whatever day the emulator thinks it is: overall on track,
     * Shopping over (festive season), Food close to its limit, the rest comfortable.
     */
    private fun budgetsFromSpending(): Map<String, Long> = runBlocking {
        val repo = app.container.repo
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val cats = repo.categories().associateBy { it.id }
        val spent = repo.transactions(start, System.currentTimeMillis())
            .filter { it.isSpending(cats[it.categoryId]) }
            .groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountPaise } / 100.0 }
        val total = spent.values.sum()
        val elapsed = today.dayOfMonth.toDouble() / YearMonth.from(today).lengthOfMonth()
        fun up(x: Double, step: Int) = (ceil(x / step) * step).toLong().coerceAtLeast(step.toLong()) * 100
        fun down(x: Double, step: Int) = (floor(x / step) * step).toLong().coerceAtLeast(step.toLong()) * 100
        val b = linkedMapOf(Schema.OVERALL to up(total / elapsed * 1.12, 5000))
        spent[Cat.SHOPPING]?.let { b[Cat.SHOPPING] = down(it * 0.8, 500) }
        spent[Cat.FOOD]?.let { b[Cat.FOOD] = up(it / 0.82, 500) }
        spent[Cat.GROCERIES]?.let { b[Cat.GROCERIES] = up(it / 0.55, 500) }
        spent[Cat.TRANSPORT]?.let { b[Cat.TRANSPORT] = up(it / 0.5, 500) }
        Log.i(TAG, "Month so far ${spent.mapValues { it.value.toLong() }}; budgets ${b.mapValues { it.value / 100 }}")
        b
    }

    // ---- helpers ----

    /** One group of screenshots. A failure is logged and the next group still runs. */
    private fun section(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.e(TAG, "Section $name failed", it); failures += name }
    }

    private val failures = ArrayList<String>()

    private fun withVm(action: (AppViewModel) -> Unit) {
        compose.runOnUiThread { action(ViewModelProvider(compose.activity)[AppViewModel::class.java]) }
        compose.waitForIdle()
        Thread.sleep(500)
    }

    /** Straight to a screen, closing any sheet, the way the app's own navigation does. */
    private fun nav(s: Screen) = withVm { it.closeSheet(); it.dismissConfirm(); it.go(s) }

    /** The capture's budgets, whatever the setup saved: the same story on every run. */
    private fun useBudgets(b: Map<String, Long>) = runBlocking {
        val repo = app.container.repo
        repo.observeBudgets().first().keys.filter { it !in b }.forEach { repo.setBudget(it, null) }
        b.forEach { (k, v) -> repo.setBudget(k, v) }
    }

    private fun exists(m: SemanticsMatcher) = compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun shot(name: String, dir: File = out) {
        compose.waitForIdle()
        Thread.sleep(900)
        val bmp: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot for $name")
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "Saved $name (${bmp.width}x${bmp.height})")
    }

    private val demoOut: File by lazy { File(app.getExternalFilesDir(null), "sms-demo").apply { mkdirs() } }

    /** A [smsDemo] screenshot, noting where the next tap lands: on [tapOn]'s centre, or at [tap]. */
    private fun demoShot(name: String, tapOn: SemanticsMatcher? = null, tap: Pair<Int, Int>? = null) {
        if (tapOn != null) runCatching { compose.onAllNodes(tapOn)[0].performScrollTo() }
        shot(name, demoOut)
        val at = tap ?: tapOn?.let { m ->
            runCatching {
                val n = compose.onAllNodes(m)[0].fetchSemanticsNode()
                val p = n.positionOnScreen
                (p.x + n.size.width / 2f).toInt() to (p.y + n.size.height / 2f).toInt()
            }.onFailure { Log.w(TAG, "No tap spot for $name", it) }.getOrNull()
        }
        if (at != null) File(demoOut, "taps.txt").appendText("$name ${at.first} ${at.second}\n")
    }

    /** The Allow button of Android's permission prompt, found through accessibility. */
    private fun waitForAllowButton(timeoutMs: Long): AccessibilityNodeInfo {
        val ua = instrumentation.uiAutomation
        ua.serviceInfo?.let { info ->
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            ua.serviceInfo = info
        }
        val ids = listOf("com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller")
            .map { "$it:id/permission_allow_button" }
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val roots = runCatching { ua.windows.mapNotNull { it.root } }.getOrDefault(emptyList()) + listOfNotNull(ua.rootInActiveWindow)
            for (root in roots) {
                for (id in ids) root.findAccessibilityNodeInfosByViewId(id).firstOrNull()?.let { return it }
                root.findAccessibilityNodeInfosByText("Allow")
                    .firstOrNull { it.isClickable && it.text?.toString().equals("Allow", ignoreCase = true) }
                    ?.let { return it }
            }
            Thread.sleep(300)
        }
        error("Android's permission prompt didn't appear")
    }

    /** Scrolls the screen's main list until the node matching [m] sits near the top. */
    /** Scrolls the longest list so the node sits [below] dp under the list's top edge. */
    private fun scrollTo(m: SemanticsMatcher, below: Float = 12f) {
        compose.waitForNode(m, 15_000)
        runCatching { compose.onAllNodes(m)[0].performScrollTo() }
        compose.waitForIdle()
        runCatching {
            val target = compose.onAllNodes(m)[0].fetchSemanticsNode().boundsInRoot
            val lists = compose.onAllNodes(hasScrollAction())
            val nodes = lists.fetchSemanticsNodes()
            val i = nodes.indices.maxBy { nodes[it].boundsInRoot.height }
            val dy = target.top - nodes[i].boundsInRoot.top - below * compose.density.density
            if (dy > 1f) lists[i].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
        }
        compose.waitForIdle()
    }

    private fun swipeList(up: Boolean) {
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        val list = if (runCatching { lists.fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)) lists[0]
        else compose.onAllNodes(hasScrollAction())[0]
        runCatching {
            list.performTouchInput { if (up) swipeUp(startY = bottom * 0.8f, endY = bottom * 0.35f, durationMillis = 700) else swipeDown(startY = bottom * 0.3f, endY = bottom * 0.85f, durationMillis = 700) }
        }
        compose.waitForIdle()
    }

    private fun shell(cmd: String): String {
        val pfd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }.also {
            Log.i(TAG, "$ $cmd -> ${it.trim().take(200)}")
        }
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("sdk_gphone") || Build.MODEL.contains("Emulator") || Build.PRODUCT.contains("sdk")

    companion object {
        private const val TAG = "OwlStoreShots"
    }
}
