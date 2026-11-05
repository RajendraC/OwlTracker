package com.owl.tracker.licence

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.owl.tracker.core.Trial as TrialMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.util.Base64

/** What the person can do right now. */
sealed interface Access {
    /** Adding or changing data (imports, expenses, budgets, categories). Viewing and deleting are always allowed. */
    val canEdit: Boolean

    data class Trial(val daysLeft: Int, val endsAt: Long) : Access {
        override val canEdit = true
    }

    data object Unlocked : Access {
        override val canEdit = true
    }

    /** Read-only: everything stays visible, nothing new comes in until the unlock is bought. */
    data object TrialEnded : Access {
        override val canEdit = false
    }
}

/** Google Play's side of the unlock, as last reported by the Play Store app on this phone. */
data class StoreState(
    val status: Status = Status.CONNECTING,
    /** Price as Google Play shows it, e.g. "₹50.00". Null until Play has answered. */
    val price: String? = null,
    /** Play's answer to "has this Google account bought the unlock?". Null when Play hasn't answered yet. */
    val owned: Boolean? = null,
    /** A purchase is waiting for payment (UPI and some cards can take a while). */
    val pending: Boolean = false,
    /** Google Play's payment sheet is open. */
    val busy: Boolean = false,
    /** The last problem worth telling the person about, in plain words. */
    val message: String? = null,
) {
    enum class Status { CONNECTING, READY, UNAVAILABLE }
}

/** Google Play Billing behind an interface, so tests and emulators without Play can stand in for it. */
interface UnlockStore {
    val state: StateFlow<StoreState>

    /** Connects if needed, then re-reads the price and the purchase. Safe to call often. */
    fun refresh()

    /** Opens Google Play's payment sheet for the one-time unlock. */
    fun buy(activity: Activity)

    companion object {
        /** The one-time product to create in Play Console (Monetize with Play → Products → One-time products). */
        const val PRODUCT_ID = "owl_tracker_unlock"

        /** Shown until Google Play reports the real price. */
        const val FALLBACK_PRICE = "₹50"
    }
}

/**
 * When the trial started and what time it is, hardened a little against the easy tricks:
 * - The start is Android's install time, so "Clear storage" and the in-app reset don't restart it.
 *   (Uninstalling and reinstalling does: with no internet there is nowhere else to remember it.)
 * - "Now" never goes backwards, so setting the phone's clock back doesn't add days.
 */
class TrialClock(
    private val prefs: SharedPreferences,
    private val clock: Clock,
    private val installedAt: () -> Long,
    private val debuggable: Boolean = false,
) {
    fun start(): Long {
        val now = now()
        val stored = prefs.getLong(KEY_START, 0L).takeIf { it > 0 }
        val installed = runCatching { installedAt() }.getOrDefault(0L).takeIf { it in 1..now }
        val start = listOfNotNull(stored, installed).minOrNull() ?: now
        if (stored != start) prefs.edit().putLong(KEY_START, start).apply()
        return start
    }

    fun now(): Long {
        val real = clock.millis()
        val seen = prefs.getLong(KEY_SEEN, 0L)
        if (real > seen) prefs.edit().putLong(KEY_SEEN, real).apply()
        return maxOf(real, seen)
    }

    /** Debug builds only: lets you see the read-only mode and the unlock screen without waiting 7 days. */
    var debugEnded: Boolean
        get() = debuggable && prefs.getBoolean(KEY_DEBUG_ENDED, false)
        set(value) { if (debuggable) prefs.edit().putBoolean(KEY_DEBUG_ENDED, value).apply() }

    val isDebuggable: Boolean get() = debuggable

    companion object {
        const val PREFS = "licence"
        private const val KEY_START = "trial_start"
        private const val KEY_SEEN = "latest_time_seen"
        private const val KEY_DEBUG_ENDED = "debug_trial_ended"

        fun installTime(context: Context): Long = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(0L)

        fun isDebuggable(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}

/**
 * Combines the trial and Google Play into one answer: [access].
 * Once Play has confirmed the unlock it is remembered on the phone, so Owl Tracker stays unlocked
 * when the Play Store can't be reached. Play's later answer still wins (for example after a refund).
 */
class Licence(
    val store: UnlockStore,
    val trial: TrialClock,
    private val prefs: SharedPreferences,
    scope: CoroutineScope,
) {
    private val tick = MutableStateFlow(0)

    val access: StateFlow<Access> = combine(store.state, tick) { s, _ -> compute(s) }
        .stateIn(scope, SharingStarted.Eagerly, compute(store.state.value))

    /** The answer right now, for code that can't wait on a flow (the background SMS worker). */
    fun current(): Access = compute(store.state.value)

    /** Re-reads the clock and asks Google Play again. Call when the app comes to the front. */
    fun refresh() {
        tick.update { it + 1 }
        runCatching { store.refresh() }
    }

    fun buy(activity: Activity) = store.buy(activity)

    /** Debug builds only. */
    fun setDebugTrialEnded(ended: Boolean) {
        trial.debugEnded = ended
        tick.update { it + 1 }
    }

    private fun compute(s: StoreState): Access {
        s.owned?.let { if (it != prefs.getBoolean(KEY_OWNED, false)) prefs.edit().putBoolean(KEY_OWNED, it).apply() }
        if (s.owned ?: prefs.getBoolean(KEY_OWNED, false)) return Access.Unlocked
        val start = trial.start()
        val now = trial.now()
        if (trial.debugEnded || TrialMath.isOver(start, now)) return Access.TrialEnded
        return Access.Trial(TrialMath.daysLeft(start, now), TrialMath.endsAt(start))
    }

    companion object {
        private const val KEY_OWNED = "unlock_owned"
    }
}

/**
 * Checks Google Play's signature on a purchase with the app's licence key (Play Console → Monetize
 * with Play → Monetization setup → Licensing). The key is public, so it lives in res/values/licence.xml.
 * With no key configured, Google Play's answer is trusted as it is.
 */
object PurchaseVerifier {
    fun verify(base64PublicKey: String, signedData: String, signature: String): Boolean {
        val key = base64PublicKey.filterNot { it.isWhitespace() }
        if (key.isEmpty()) return true
        return try {
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key)))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(publicKey)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signature))
            }
        } catch (e: Exception) {
            false
        }
    }
}

/** Used where Google Play Billing can't run at all. Reports Play as unavailable; the trial still works. */
class NoStore(message: String = "Google Play isn't available here") : UnlockStore {
    override val state: StateFlow<StoreState> = MutableStateFlow(StoreState(status = StoreState.Status.UNAVAILABLE, message = message))
    override fun refresh() = Unit
    override fun buy(activity: Activity) = Unit
}
