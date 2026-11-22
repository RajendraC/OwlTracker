package com.owl.tracker.update

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
 * Talks to the Play Store app on the phone (like the ₹50 unlock); Owl Tracker itself still has no internet
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
            finishInPlay("install ${s.installErrorCode()}")
            return@InstallStateUpdatedListener
        }
        _state.value = when (st) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
                UpdateUi.Downloading(UpdatePolicy.percent(s.bytesDownloaded(), s.totalBytesToDownload()))
            InstallStatus.DOWNLOADED -> UpdateUi.Downloaded
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
        val i = info ?: run { lastStart = "no update info"; return }
        val l = launcher ?: run { lastStart = "no screen to start from"; return }
        lastStart = runCatching { manager?.startUpdateFlowForResult(i, l, AppUpdateOptions.defaultOptions(type)) }
            .fold({ "type $type started: $it" }, { "type $type failed: $it" })
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
        val task = runCatching { m.completeUpdate() }.getOrElse { finishInPlay("completeUpdate threw: $it"); return }
        task.addOnFailureListener { finishInPlay("completeUpdate failed: $it") }
        main.postDelayed({ if (_state.value == UpdateUi.Installing) finishInPlay("nothing happened") }, RESTART_WAIT_MS)
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun finishInPlay(why: String) {
        android.util.Log.w("OwlUpdate", "Restart didn't finish the update ($why); finishing in the Play Store")
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
        "1.9.14" to listOf(
            Item("Updates that always finish", "If Restart to finish can't install an update, Owl Tracker now says so and takes you to its Play Store page to finish there, instead of doing nothing."),
        ),
        "1.9.13" to listOf(
            Item("New category pictures", "Every category has a new, softer 3D picture: an auto rickshaw for getting around, a barber pole for the salon, a cricket bat for sports and more."),
            Item("Metro", "Metro rides and metro card top-ups now have their own category in Getting around. Delhi, Namma, Mumbai, Hyderabad, Chennai, Kochi, Pune, Noida, Lucknow, Gujarat and Kolkata metros are sorted there by themselves."),
        ),
        "1.9.12" to listOf(
            Item("More for families with babies", "Kids now has Baby Essentials, Daycare & Nanny, Kids' Health & Vaccines and Toys, and Kids' Shopping & Care is now Kids' Clothes. FirstCry, baby shops, creches and children's hospitals are sorted there by themselves."),
        ),
        "1.9.11" to listOf(
            Item("The owl in every category", "Each category's picture now has the owl acting it out: eating noodles, pushing a trolley, driving, filling up, swinging a bat and more. Its face shows how the budget is going."),
            Item("Hello after unlocking", "With the app lock on, the owl now says hello after you unlock. It also greets you when you come back after half an hour or more."),
        ),
        "1.9.10" to listOf(
            Item("The owl says hello", "When you open Owl Tracker, the owl greets you in one of five ways: a blink, waking up, a look around, a wink at its coin or a wave."),
        ),
        "1.9.9" to listOf(
            Item("A shorter Home", "The Recent list has moved off Home. Every payment is in Activity, one tap away."),
        ),
        "1.9.8" to listOf(
            Item("A new owl", "The owl in Where it went is new: it hops and waves when you're on track, thinks when you're close, worries when spending runs fast and sheds a tear when a budget goes over."),
            Item("Every category at a glance", "Beside the ring you now see a picture for each category. Tap one to see how it's doing."),
        ),
        "1.9.7" to listOf(
            Item("Where it went, side by side", "The owl's ring now sits beside a card. Tap a slice and the card shows that category's picture, how much went there and how it's doing."),
            Item("No more picture row", "This month in pictures is gone: the same pictures now show in Where it went, so Home is shorter."),
        ),
        "1.9.6" to listOf(
            Item("Now ₹50 a year", "After the 7-day free trial, Owl Tracker is a ₹50-a-year subscription through Google Play. Manage or cancel it any time from More."),
            Item("A calmer Home", "Budgets that are over now share one line on Home. Tap it, or the Over budget figure, to see them all with their buttons."),
        ),
        "1.9.5" to listOf(
            Item("Card bills add up right", "A payment to your credit card no longer shows up as a refund on the next bill, and anything paid extra comes off it."),
            Item("Refund or payment? You choose", "In Still to pay, tap Refunds and payments on this card to correct any that were read wrong."),
            Item("Just this one, or all?", "Renaming a payment or changing its category now asks whether to change just that one. UPI payments with no shop name are no longer lumped together under the bank."),
            Item("Why a day is green", "Tap a day in the calendar: a bar shows its everyday spending against the day's share, with EMIs and rent apart."),
        ),
        "1.9.4" to listOf(
            Item("Still to pay on credit cards", "Open a credit card in Paid by: set its bill date, then mark a bill paid or add a part payment."),
            Item("Opens on This month", "Where it went always starts on This month when you open the app."),
            Item("Updates, right here", "When a new version is out, a card like this one lets you update without leaving the app."),
        ),
    )

    fun notes(versionName: String): List<Item> = NOTES[versionName].orEmpty()
}
