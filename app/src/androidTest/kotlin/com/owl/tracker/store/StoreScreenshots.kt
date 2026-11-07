package com.owl.tracker.store

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                if (description.methodName == "capture") freshAppWithDemoInbox()
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
            typeInto("budget_input", (budgets.getValue(Schema.OVERALL) / 100).toString())
            shot("06_budget_setup")
            tapTag("save_budget")
            runBlocking { budgets.filterKeys { it != Schema.OVERALL }.forEach { (k, v) -> app.container.repo.setBudget(k, v) } }

            waitForTag("budget_hero", 30_000)
            shot("10_home")
            runCatching { scrollTo(hasText("Recent")) }
            shot("11_home_lower")

            section("budgets") {
                tapTag("tab_budgets")
                Thread.sleep(800)
                shot("20_budgets")
            }
            section("activity") {
                tapTag("tab_activity")
                waitForText("Swiggy", timeoutMs = 20_000)
                shot("30_activity")
                tapText("Swiggy")
                Thread.sleep(1200)
                shot("31_txn_detail")
            }
            section("reports") {
                nav(Screen.MORE)
                shot("40_more")
                nav(Screen.INSIGHTS)
                waitForTag("report_summary", 20_000)
                runCatching { tapTag("range_6") }
                shot("50_reports_top")
                scrollTo(hasTestTag("report_categories"))
                shot("51_reports_categories")
                runCatching { tapTag("cat_${Cat.SHOPPING}") }
                Thread.sleep(800)
                shot("52_reports_filtered")
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
                onAllNodes(hasContentDescription("Edit Food"))[0].performClick()
                Thread.sleep(1000)
                shot("61_category_edit")
            }
            section("backup") {
                withVm { it.openBackup() }
                waitForTag("backup_screen", 20_000)
                shot("70_backup")
            }
            nav(Screen.HOME)
        }
        val files = out.listFiles()?.map { it.name }?.sorted().orEmpty()
        Log.i(TAG, "Saved ${files.size} screenshots in $out: $files")
        assertTrue("Only ${files.size} screenshots; failed sections: $failures", files.size >= 10)
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
            .filter { !it.excluded && cats[it.categoryId]?.countsAsSpend == true }
            .groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.signedPaise } / 100.0 }
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

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(900)
        val bmp: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot for $name")
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "Saved $name (${bmp.width}x${bmp.height})")
    }

    /** Scrolls the screen's main list until the node matching [m] sits near the top. */
    private fun scrollTo(m: SemanticsMatcher) {
        compose.waitForNode(m, 15_000)
        runCatching { compose.onAllNodes(m)[0].performScrollTo() }
        compose.waitForIdle()
        runCatching {
            val target = compose.onAllNodes(m)[0].fetchSemanticsNode().boundsInRoot
            val lists = compose.onAllNodes(hasScrollAction())
            val nodes = lists.fetchSemanticsNodes()
            val i = nodes.indices.maxBy { nodes[it].boundsInRoot.height }
            val dy = target.top - nodes[i].boundsInRoot.top - 12 * compose.density.density
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
