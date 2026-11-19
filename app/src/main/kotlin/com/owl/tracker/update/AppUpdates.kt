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
        _state.value = when (s.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
                UpdateUi.Downloading(UpdatePolicy.percent(s.bytesDownloaded(), s.totalBytesToDownload()))
            InstallStatus.DOWNLOADED -> UpdateUi.Downloaded
            InstallStatus.INSTALLED -> UpdateUi.None
            // Failed or cancelled: offer it again
            else -> (info?.let { UpdateUi.Ready(it.availableVersionCode(), it.totalBytesToDownload()) }) ?: UpdateUi.None
        }
    }

    /** From the activity's onCreate: [launcher] opens Google Play's update screens. */
    fun attach(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        this.launcher = launcher
        runCatching { manager?.registerListener(listener) }
    }

    fun detach() {
        runCatching { manager?.unregisterListener(listener) }
        launcher = null
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
        _state.value = when (action) {
            UpdatePolicy.Action.DOWNLOADED -> UpdateUi.Downloaded
            UpdatePolicy.Action.DOWNLOADING -> UpdateUi.Downloading(UpdatePolicy.percent(i.bytesDownloaded(), i.totalBytesToDownload()))
            UpdatePolicy.Action.CARD -> UpdateUi.Ready(i.availableVersionCode(), i.totalBytesToDownload())
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

    /** Restart to finish: Google Play installs the download and opens the app again. */
    fun restart() {
        runCatching { manager?.completeUpdate() }
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
        private const val SEEN = "whats_new_seen"
    }
}

/** A few lines on what changed, shown once on Home after updating. Add an entry for each release worth telling. */
object WhatsNew {
    data class Item(val title: String, val line: String)

    private val NOTES: Map<String, List<Item>> = mapOf(
        "1.9.5" to listOf(
            Item("Card bills add up right", "A payment to your credit card no longer shows up as a refund on the next bill, and anything paid extra comes off it."),
            Item("Refund or payment? You choose", "In Still to pay, tap Refunds and payments on this card to correct any that were read wrong."),
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
