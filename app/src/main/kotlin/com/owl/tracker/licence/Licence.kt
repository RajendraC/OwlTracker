package com.owl.tracker.licence

import com.owl.tracker.i18n.tr
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

    /** Read-only: everything stays visible, nothing new comes in until a subscription starts. */
    data object TrialEnded : Access {
        override val canEdit = false
    }

    /** Read-only again: the yearly subscription ended (cancelled, or a renewal couldn't be paid). */
    data object Lapsed : Access {
        override val canEdit = false
    }
}

/** Google Play's side of the unlock, as last reported by the Play Store app on this phone. */
data class StoreState(
    val status: Status = Status.CONNECTING,
    /** The yearly price as Google Play shows it, e.g. "₹99.00". Null until Play has answered. */
    val price: String? = null,
    /** The one-time lifetime price as Google Play shows it, e.g. "₹999.00". Null until Play has answered. */
    val lifetimePrice: String? = null,
    /**
     * Play's answer to "is Owl Tracker paid for on this Google account?": an active subscription or the lifetime
     * purchase. Null when Play hasn't answered yet.
     */
    val owned: Boolean? = null,
    /** Play's answer to "did this account buy the lifetime unlock?". Null when Play hasn't answered yet. */
    val lifetime: Boolean? = null,
    /** The subscription renews by itself (false: cancelled, and it stops at the end of the paid year). Null when unknown. */
    val renewing: Boolean? = null,
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

    /** Opens Google Play's payment sheet for the yearly subscription, or for the one-time lifetime unlock. */
    fun buy(activity: Activity, plan: Plan = Plan.YEARLY)

    enum class Plan { YEARLY, LIFETIME }

    companion object {
        /**
         * The subscription in Play Console (Monetize with Play → Products → Subscriptions), with one auto-renewing
         * base plan [BASE_PLAN], ₹99 a year from 1.9.22 (₹50 before; existing subscribers keep the price they joined
         * at unless moved in Play Console). (Until 1.9.5 this was the one-time product `owl_tracker_unlock`, bought
         * only by licence testers in Closed testing; it is no longer sold or honoured.)
         */
        const val PRODUCT_ID = "owl_tracker_yearly"
        const val BASE_PLAN = "yearly"

        /**
         * The one-time "pay once, keep it for life" unlock, ₹999 (Play Console → Monetize with Play → Products →
         * One-time products). Not consumed: once bought it stays with the Google account.
         */
        const val LIFETIME_ID = "owl_tracker_lifetime"

        /** Shown until Google Play reports the real prices. */
        const val FALLBACK_PRICE = "₹99"
        const val FALLBACK_LIFETIME_PRICE = "₹999"

        /** Where a subscriber manages or cancels it: the Play Store app's subscription page. */
        fun manageUrl(packageName: String) =
            "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=$packageName"
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
 * Once Play has confirmed the subscription it is remembered on the phone for [OFFLINE_DAYS] days, so Owl Tracker
 * stays unlocked while the Play Store can't be reached. Play's later answer always wins: when it no longer lists
 * the subscription (cancelled and run out, or a renewal not paid) the app is read-only again.
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

    fun buy(activity: Activity, plan: UnlockStore.Plan = UnlockStore.Plan.YEARLY) = store.buy(activity, plan)

    /** The lifetime unlock is bought (Play said so, now or before). */
    val lifetime: StateFlow<Boolean> = combine(store.state, tick) { s, _ -> compute(s); prefs.getBoolean(KEY_LIFETIME, false) }
        .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean(KEY_LIFETIME, false))

    /** Debug builds only. */
    fun setDebugTrialEnded(ended: Boolean) {
        trial.debugEnded = ended
        tick.update { it + 1 }
    }

    private fun compute(s: StoreState): Access {
        val now = trial.now()
        // Lifetime is for good: once Play confirms it, it's remembered with no time limit. Only Play saying it's gone
        // (a refund) takes it away.
        when (s.lifetime) {
            true -> if (!prefs.getBoolean(KEY_LIFETIME, false)) prefs.edit().putBoolean(KEY_LIFETIME, true).putBoolean(KEY_EVER, true).apply()
            false -> if (prefs.getBoolean(KEY_LIFETIME, false)) prefs.edit().putBoolean(KEY_LIFETIME, false).apply()
            null -> Unit
        }
        if (prefs.getBoolean(KEY_LIFETIME, false)) return Access.Unlocked
        when (s.owned) {
            true -> prefs.edit().putLong(KEY_CONFIRMED_AT, now).putBoolean(KEY_EVER, true).apply()
            false -> if (prefs.getLong(KEY_CONFIRMED_AT, 0L) != 0L) prefs.edit().putLong(KEY_CONFIRMED_AT, 0L).apply()
            null -> Unit
        }
        // Play hasn't answered (yet): the last confirmation counts for a while
        val confirmedAt = prefs.getLong(KEY_CONFIRMED_AT, 0L)
        val remembered = confirmedAt > 0 && now - confirmedAt <= OFFLINE_DAYS * 86_400_000L
        if (s.owned ?: remembered) return Access.Unlocked
        val start = trial.start()
        if (trial.debugEnded || TrialMath.isOver(start, now)) return if (prefs.getBoolean(KEY_EVER, false)) Access.Lapsed else Access.TrialEnded
        return Access.Trial(TrialMath.daysLeft(start, now), TrialMath.endsAt(start))
    }

    companion object {
        /** How long a confirmed subscription is trusted while Google Play can't be asked. */
        const val OFFLINE_DAYS = 30
        private const val KEY_CONFIRMED_AT = "subscription_confirmed_at"
        private const val KEY_EVER = "ever_subscribed"
        private const val KEY_LIFETIME = "lifetime_unlocked"
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
class NoStore(message: String = tr("Google Play isn't available here")) : UnlockStore {
    override val state: StateFlow<StoreState> = MutableStateFlow(StoreState(status = StoreState.Status.UNAVAILABLE, message = message))
    override fun refresh() = Unit
    override fun buy(activity: Activity, plan: UnlockStore.Plan) = Unit
}
