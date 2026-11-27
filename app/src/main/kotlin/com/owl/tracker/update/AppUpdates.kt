package com.owl.tracker.update

import com.owl.tracker.i18n.tr
import android.app.Activity
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What Home shows about a new version. */
sealed interface UpdateUi {
    data object None : UpdateUi

    /** A new version is on Google Play. [bytes] is its download size, 0 when Play didn't say. */
    data class Ready(val versionCode: Int, val bytes: Long) : UpdateUi

    data class Downloading(val percent: Int) : UpdateUi

    /** Downloaded; a restart installs it. */
    data object Downloaded : UpdateUi

    /** Restart was tapped: Google Play is installing (the app closes and reopens). */
    data object Installing : UpdateUi

    /**
     * Google Play couldn't finish the install from inside the app (it failed, or nothing happened): the update is
     * finished from Owl Tracker's page in the Play Store instead.
     */
    data object FinishInPlay : UpdateUi
}

/** What to do with Google Play's answer. Pure, so it can be tested without Play. */
object UpdatePolicy {
    /** Releases sent with this priority or more (`inAppUpdatePriority`, 0–5) get Google Play's full-screen update. */
    const val URGENT = 4

    enum class Action { NOTHING, CARD, FULL_SCREEN, DOWNLOADING, DOWNLOADED }

    fun decide(
        availability: Int,
        installStatus: Int,
        priority: Int,
        flexibleAllowed: Boolean,
        immediateAllowed: Boolean,
        versionCode: Int,
        putOffFor: Int,
    ): Action = when {
        installStatus == InstallStatus.DOWNLOADED -> Action.DOWNLOADED
        installStatus == InstallStatus.DOWNLOADING || installStatus == InstallStatus.PENDING -> Action.DOWNLOADING
        // An urgent update the person left halfway (the app was closed during it): carry on with it
        availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> Action.FULL_SCREEN
        availability != UpdateAvailability.UPDATE_AVAILABLE -> Action.NOTHING
        priority >= URGENT && immediateAllowed -> Action.FULL_SCREEN
        // An urgent one shows the card even after "Later"; a normal one waits for the next version
        flexibleAllowed && (priority >= URGENT || versionCode != putOffFor) -> Action.CARD
        else -> Action.NOTHING
    }

    fun percent(done: Long, total: Long): Int = if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 100)
}

/**
 * New versions from Google Play, and What's new after one is installed.
 *
 * Talks to the Play Store app on the phone (like the payments); Owl Tracker itself still has no internet
 * access. [manager] is null where there is no Play (tests, or a copy not installed from Play): then nothing
 * is ever offered. Remembers in [prefs] (not the encrypted database, and not in backups): the version the
 * person put off with Later, and the last version whose What's new was seen.
 */
class AppUpdates(private val manager: AppUpdateManager?, private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow<UpdateUi>(UpdateUi.None)
    val state: StateFlow<UpdateUi> = _state.asStateFlow()

    /** The version name whose What's new is on Home, or null. */
    private val _whatsNew = MutableStateFlow<String?>(null)
    val whatsNew: StateFlow<String?> = _whatsNew.asStateFlow()

    private var info: AppUpdateInfo? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null

    private val listener = InstallStateUpdatedListener { s ->
        val st = s.installStatus()
        if (_state.value == UpdateUi.Installing && (st == InstallStatus.FAILED || st == InstallStatus.CANCELED)) {
            finishInPlay(tr("install {0}", s.installErrorCode()))
            return@InstallStateUpdatedListener
        }
        _state.value = when (st) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
                UpdateUi.Downloading(UpdatePolicy.percent(s.bytesDownloaded(), s.totalBytesToDownload()))
            InstallStatus.DOWNLOADED -> UpdateUi.Downloaded
            InstallStatus.INSTALLING -> UpdateUi.Installing
            InstallStatus.INSTALLED -> UpdateUi.None
            // Failed or cancelled: offer it again
            else -> (info?.let { UpdateUi.Ready(it.availableVersionCode(), it.totalBytesToDownload()) }) ?: UpdateUi.None
        }
    }

    /** Opens Owl Tracker's page in the Play Store (set by the activity). */
    private var openStore: (() -> Unit)? = null

    /** From the activity's onCreate: [launcher] opens Google Play's update screens, [openStore] the Play Store page. */
    fun attach(launcher: ActivityResultLauncher<IntentSenderRequest>, openStore: () -> Unit = {}) {
        this.launcher = launcher
        this.openStore = openStore
        runCatching { manager?.registerListener(listener) }
    }

    fun detach() {
        runCatching { manager?.unregisterListener(listener) }
        launcher = null
        openStore = null
    }

    /** Asks Google Play whether there's a newer version. Each time the app comes to the front. */
    fun check() {
        val m = manager ?: return
        runCatching { m.appUpdateInfo.addOnSuccessListener { apply(it) } }
    }

    private fun apply(i: AppUpdateInfo) {
        info = i
        val action = UpdatePolicy.decide(
            availability = i.updateAvailability(),
            installStatus = i.installStatus(),
            priority = i.updatePriority(),
            flexibleAllowed = i.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            immediateAllowed = i.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            versionCode = i.availableVersionCode(),
            putOffFor = prefs.getInt(PUT_OFF, -1),
        )
        // A restart that didn't take for this version: don't offer the in-app steps again, finish in the Play Store
        val failedHere = prefs.getInt(RESTART_FAILED, -1) == i.availableVersionCode()
        _state.value = when (action) {
            UpdatePolicy.Action.DOWNLOADED, UpdatePolicy.Action.CARD -> if (failedHere) UpdateUi.FinishInPlay
                else if (action == UpdatePolicy.Action.DOWNLOADED) UpdateUi.Downloaded
                else UpdateUi.Ready(i.availableVersionCode(), i.totalBytesToDownload())
            UpdatePolicy.Action.DOWNLOADING -> UpdateUi.Downloading(UpdatePolicy.percent(i.bytesDownloaded(), i.totalBytesToDownload()))
            UpdatePolicy.Action.FULL_SCREEN -> { start(AppUpdateType.IMMEDIATE); UpdateUi.None }
            UpdatePolicy.Action.NOTHING -> UpdateUi.None
        }
    }

    /** Update on the card: Google Play asks once, then downloads while the app stays in use. */
    fun update() = start(AppUpdateType.FLEXIBLE)

    private fun start(type: Int) {
        val i = info ?: run { lastStart = tr("no update info"); return }
        val l = launcher ?: run { lastStart = tr("no screen to start from"); return }
        lastStart = runCatching { manager?.startUpdateFlowForResult(i, l, AppUpdateOptions.defaultOptions(type)) }
            .fold({ tr("type {0} started: {1}", type, it) }, { tr("type {0} failed: {1}", type, it) })
        android.util.Log.i("OwlUpdate", lastStart.orEmpty())
    }

    /** What Google Play said to the last request to open its update screen (for tests and the log). */
    @Volatile internal var lastStart: String? = null
        private set

    /** The answer from Google Play's screen. Said no: the card stays (an urgent update asks again next time). */
    fun onResult(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) check()
    }

    /** Later on the card: hidden until the next version. */
    fun later() {
        (_state.value as? UpdateUi.Ready)?.let { prefs.edit().putInt(PUT_OFF, it.versionCode).apply() }
        _state.value = UpdateUi.None
    }

    /**
     * Restart to finish: Google Play installs the download and opens the app again. If Play says no, or nothing has
     * happened after [RESTART_WAIT_MS] (the app is still here), the update is finished in the Play Store instead,
     * so the card never just sits there. (22 Nov 2026: a download from an older closed-testing build did nothing.)
     */
    fun restart() {
        val m = manager ?: return
        _state.value = UpdateUi.Installing
        val task = runCatching { m.completeUpdate() }.getOrElse { finishInPlay(tr("completeUpdate threw: {0}", it)); return }
        task.addOnFailureListener { finishInPlay(tr("completeUpdate failed: {0}", it)) }
        main.postDelayed({ if (_state.value == UpdateUi.Installing) finishInPlay(tr("nothing happened")) }, RESTART_WAIT_MS)
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun finishInPlay(why: String) {
        android.util.Log.w("OwlUpdate", tr("Restart didn't finish the update ({0}); finishing in the Play Store", why))
        info?.let { prefs.edit().putInt(RESTART_FAILED, it.availableVersionCode()).apply() }
        _state.value = UpdateUi.FinishInPlay
        runCatching { openStore?.invoke() }
    }

    /** Open Google Play on the Finish in Google Play card. */
    fun openPlay() {
        runCatching { openStore?.invoke() }
    }

    /**
     * At start-up: shows What's new once for this version if it has notes and the person came from an
     * earlier one. A fresh install starts with nothing to catch up on.
     */
    fun startUp(versionCode: Int, versionName: String, updatedFromEarlier: Boolean) {
        val seen = prefs.getInt(SEEN, -1)
        val newHere = if (seen < 0) updatedFromEarlier else seen < versionCode
        if (newHere && WhatsNew.notes(versionName).isNotEmpty()) _whatsNew.value = versionName
        else markSeen(versionCode)
        this.versionCode = versionCode
    }

    private var versionCode = 0

    fun closeWhatsNew() {
        _whatsNew.value = null
        markSeen(versionCode)
    }

    private fun markSeen(code: Int) {
        if (prefs.getInt(SEEN, -1) != code) prefs.edit().putInt(SEEN, code).apply()
    }

    companion object {
        const val PREFS = "updates"
        private const val PUT_OFF = "put_off_version"
        private const val RESTART_FAILED = "restart_failed_version"
        const val RESTART_WAIT_MS = 8_000L
        private const val SEEN = "whats_new_seen"
    }
}

/** A few lines on what changed, shown once on Home after updating. Add an entry for each release worth telling. */
object WhatsNew {
    data class Item(val title: String, val line: String)

    private val NOTES: Map<String, List<Item>> = mapOf(
        "1.9.32" to listOf(
            Item(tr("The owl reacts"), tr("Add a payment and the owl hops when you're on track, thinks it over after a big one, and tells you kindly when today goes past its share. Streaks, badges and goals get a little celebration. Choose Lively, Calm or Off in Settings → Appearance.")),
            Item(tr("Share your month, not your money"), tr("Share my month on the recap, or tap a badge you've earned, to make a picture of your wins: no-spend days, days within your share, badges. No amounts or percentages, ever.")),
        ),
        "1.9.31" to listOf(
            Item(tr("Pick your colours"), tr("Settings → Appearance now has five looks: Cream & Mango, Mango & Teal, Indigo & Saffron, Mint & Coral and Plum & Marigold, each in light and dark. Your widgets follow along.")),
        ),
        "1.9.30" to listOf(
            Item(tr("Your month, on a bigger screen"), tr("On the 2nd, the owl gets last month's report ready to dig into on a computer: an interactive web page for any browser, or an Excel workbook. Send it to yourself in one tap. Pick the kind in Settings → Monthly report.")),
        ),
        "1.9.29" to listOf(
            Item(tr("Bills coming up"), tr("EMIs, rent and subscriptions are found from payments that repeat, with their next date. Home shows the next few, and the owl reminds you two days before.")),
            Item(tr("Can I afford it?"), tr("Type an amount to see what it leaves for the rest of the month, the new amount a day, and the owl's verdict. On Home under Spent, and in More.")),
            Item(tr("Savings goals"), tr("Give what you're saving for a jar and fill it as you set money aside. At the start of a month, the owl offers what was left of the last one.")),
        ),
        "1.9.28" to listOf(
            Item(tr("Streaks and owl badges"), tr("See how many days in a row you've stayed within your daily share, right under the calendar. Tap it for your weeks, no-spend days and nine owl badges to earn.")),
            Item(tr("No-spend days"), tr("A day when nothing went out gets a star on the calendar, and the owl celebrates it the next morning.")),
            Item(tr("Was this right?"), tr("When a new payment comes in by SMS, confirm its category straight from the notification, or change it in one tap.")),
        ),
        "1.9.27" to listOf(
            Item(tr("The owl nudges you"), tr("A line each evening about today, a heads-up at 80% and 100% of your budget, and your week on Sundays. Turn each on or off in Settings → Owl nudges. All worked out on your phone.")),
            Item(tr("Widgets"), tr("Add Owl Tracker to your home screen: this month's ring and what's left, or today with a button to add cash. Amounts stay hidden while app lock is on.")),
            Item(tr("Your month, in a few cards"), tr("On the 1st, a recap of the month just ended: saved or over, your biggest day, top merchant and more. Share any card as a picture.")),
        ),
        "1.9.26" to listOf(
            Item(tr("Spent, clear as day"), tr("Home's budget card now shows what you spent as the big number. The ring goes round once for your budget; if you go past it, a red ring laps inside, and the middle says how many times over (like 1.3×) instead of a percentage.")),
            Item(tr("A warmer look"), tr("Headings and amounts use a new, softer typeface, so the numbers that matter stand out.")),
        ),
        "1.9.25" to listOf(
            Item(tr("A day, right on Home"), tr("Tap a day in the calendar and Where it went shows just that day: the orbit, the owl's mood and the list. Tap the day again, or ✕, for the whole month.")),
        ),
        "1.9.24" to listOf(
            Item(tr("Days that fill up"), tr("Each day in Home's calendar now fills like a glass with what you spent against your daily share, with the amount underneath (red if you went over). Tap a day to see its payments. The daily share now leaves out what EMIs and rent take.")),
            Item(tr("Investments count, in purple"), tr("Money you put into investments (SIPs, Groww, Zerodha and the like) now goes out of your budget like spending, marked in purple so you can see what's still yours.")),
        ),
        "1.9.23" to listOf(
            Item(tr("Add expenses for any day"), tr("Adding an expense by hand? Tap Pick a date and choose any day from the calendar, not just today or yesterday.")),
        ),
        "1.9.22" to listOf(
            Item(tr("Pay once, keep it for life"), tr("Owl Tracker is now ₹99 a year, or ₹999 once for a lifetime unlock with nothing more to pay. Already subscribed? You keep your price, and can switch to lifetime from More.")),
        ),
        "1.9.21" to listOf(
            Item(tr("Share a report"), tr("In Reports, tap Share to send your spending to family or open it on a computer: an interactive page with charts, a calendar and filters, or an Excel workbook. Card and account numbers are left out unless you choose.")),
        ),
        "1.9.20" to listOf(
            Item(tr("Review and choose"), tr("Moving a payment to another category? Tap Review and choose to see every payment from the same payee, tick the ones to move, and send any of them somewhere else.")),
        ),
        "1.9.19" to listOf(
            Item(tr("Choose the account"), tr("When a bank's message doesn't say which card or account paid, tap \"Paid from an account\" and pick it. Owl Tracker can use it for that bank's other such messages, and new ones.")),
        ),
        "1.9.18" to listOf(
            Item(tr("The orbit is your budget"), tr("Where it went is now one piece: how much went and what's left across the top, and the ring the bubbles ride on fills up as you spend your budget. Tap a bubble and the top shows that category, with Open.")),
        ),
        "1.9.17" to listOf(
            Item(tr("Your categories circle the owl"), tr("Where it went on Home is now an orbit: each category is a bubble circling the owl, bigger the more went there, with a ring showing its budget used. Over-budget ones glow red. Swipe to spin, tap one to see how it's doing, tap again to open it.")),
            Item(tr("Clearer locked switches"), tr("On a card bill payment or a transfer, Count as income is greyed out and says why, instead of looking like it should switch.")),
        ),
        "1.9.16" to listOf(
            Item(tr("A livelier owl"), tr("The owl beside the greeting on Home now blinks, looks around and waves now and then. Tap it and it waves back.")),
        ),
        "1.9.15" to listOf(
            Item(tr("Cabs & Bike Taxis"), tr("Uber, Ola, Rapido, Namma Yatri, BluSmart, inDrive and taxis now have their own category in Getting around. Daily Commute keeps buses, local trains and autos.")),
        ),
        "1.9.14" to listOf(
            Item(tr("Updates that always finish"), tr("If Restart to finish can't install an update, Owl Tracker now says so and takes you to its Play Store page to finish there, instead of doing nothing.")),
        ),
        "1.9.13" to listOf(
            Item(tr("New category pictures"), tr("Every category has a new, softer 3D picture: an auto rickshaw for getting around, a barber pole for the salon, a cricket bat for sports and more.")),
            Item(tr("Metro"), tr("Metro rides and metro card top-ups now have their own category in Getting around. Delhi, Namma, Mumbai, Hyderabad, Chennai, Kochi, Pune, Noida, Lucknow, Gujarat and Kolkata metros are sorted there by themselves.")),
        ),
        "1.9.12" to listOf(
            Item(tr("More for families with babies"), tr("Kids now has Baby Essentials, Daycare & Nanny, Kids' Health & Vaccines and Toys, and Kids' Shopping & Care is now Kids' Clothes. FirstCry, baby shops, creches and children's hospitals are sorted there by themselves.")),
        ),
        "1.9.11" to listOf(
            Item(tr("The owl in every category"), tr("Each category's picture now has the owl acting it out: eating noodles, pushing a trolley, driving, filling up, swinging a bat and more. Its face shows how the budget is going.")),
            Item(tr("Hello after unlocking"), tr("With the app lock on, the owl now says hello after you unlock. It also greets you when you come back after half an hour or more.")),
        ),
        "1.9.10" to listOf(
            Item(tr("The owl says hello"), tr("When you open Owl Tracker, the owl greets you in one of five ways: a blink, waking up, a look around, a wink at its coin or a wave.")),
        ),
        "1.9.9" to listOf(
            Item(tr("A shorter Home"), tr("The Recent list has moved off Home. Every payment is in Activity, one tap away.")),
        ),
        "1.9.8" to listOf(
            Item(tr("A new owl"), tr("The owl in Where it went is new: it hops and waves when you're on track, thinks when you're close, worries when spending runs fast and sheds a tear when a budget goes over.")),
            Item(tr("Every category at a glance"), tr("Beside the ring you now see a picture for each category. Tap one to see how it's doing.")),
        ),
        "1.9.7" to listOf(
            Item(tr("Where it went, side by side"), tr("The owl's ring now sits beside a card. Tap a slice and the card shows that category's picture, how much went there and how it's doing.")),
            Item(tr("No more picture row"), tr("This month in pictures is gone: the same pictures now show in Where it went, so Home is shorter.")),
        ),
        "1.9.6" to listOf(
            Item(tr("Now ₹50 a year"), tr("After the 7-day free trial, Owl Tracker is a ₹50-a-year subscription through Google Play. Manage or cancel it any time from More.")),
            Item(tr("A calmer Home"), tr("Budgets that are over now share one line on Home. Tap it, or the Over budget figure, to see them all with their buttons.")),
        ),
        "1.9.5" to listOf(
            Item(tr("Card bills add up right"), tr("A payment to your credit card no longer shows up as a refund on the next bill, and anything paid extra comes off it.")),
            Item(tr("Refund or payment? You choose"), tr("In Still to pay, tap Refunds and payments on this card to correct any that were read wrong.")),
            Item(tr("Just this one, or all?"), tr("Renaming a payment or changing its category now asks whether to change just that one. UPI payments with no shop name are no longer lumped together under the bank.")),
            Item(tr("Why a day is green"), tr("Tap a day in the calendar: a bar shows its everyday spending against the day's share, with EMIs and rent apart.")),
        ),
        "1.9.4" to listOf(
            Item(tr("Still to pay on credit cards"), tr("Open a credit card in Paid by: set its bill date, then mark a bill paid or add a part payment.")),
            Item(tr("Opens on This month"), tr("Where it went always starts on This month when you open the app.")),
            Item(tr("Updates, right here"), tr("When a new version is out, a card like this one lets you update without leaving the app.")),
        ),
    )

    fun notes(versionName: String): List<Item> = NOTES[versionName].orEmpty()
}
