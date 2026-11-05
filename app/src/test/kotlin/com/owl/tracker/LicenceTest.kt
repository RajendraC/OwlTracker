package com.owl.tracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.licence.Access
import com.owl.tracker.licence.FakeUnlockStore
import com.owl.tracker.licence.Licence
import com.owl.tracker.licence.PurchaseVerifier
import com.owl.tracker.licence.StoreState
import com.owl.tracker.licence.TrialClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

/** The 7-day trial, the unlock, and the tricks the trial clock has to survive. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class LicenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(TrialClock.PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val now = TEST_CLOCK.millis()

    private fun clockAt(ms: Long): Clock = Clock.fixed(Instant.ofEpochMilli(ms), ZoneId.of("Asia/Kolkata"))

    private fun licence(store: FakeUnlockStore = FakeUnlockStore(), clockMs: Long = now, installedAt: Long = now, debuggable: Boolean = false) =
        Licence(store, TrialClock(prefs, clockAt(clockMs), { installedAt }, debuggable), prefs, scope)

    @Test fun freshInstallHasSevenDays() {
        val a = licence().current()
        assertEquals(Access.Trial(7, now + 7 * DAY_MS), a)
        assertTrue(a.canEdit)
    }

    @Test fun countsDownThenTurnsReadOnly() {
        assertEquals(3, (licence(clockMs = now, installedAt = now - 4 * DAY_MS).current() as Access.Trial).daysLeft)
        val ended = licence(clockMs = now, installedAt = now - 7 * DAY_MS).current()
        assertEquals(Access.TrialEnded, ended)
        assertFalse(ended.canEdit)
    }

    @Test fun buyingUnlocksForGood() {
        val store = FakeUnlockStore()
        val l = licence(store, installedAt = now - 30 * DAY_MS)
        assertEquals(Access.TrialEnded, l.current())
        store.mutable.update { it.copy(owned = true) }
        assertEquals(Access.Unlocked, l.current())
        assertTrue(l.current().canEdit)
    }

    @Test fun staysUnlockedWhenGooglePlayCantBeReached() {
        val store = FakeUnlockStore()
        licence(store, installedAt = now - 30 * DAY_MS).also { store.mutable.update { s -> s.copy(owned = true) }; it.current() }
        // Next launch: Play Store unavailable, so Play gives no answer (owned = null)
        val offline = FakeUnlockStore(StoreState(status = StoreState.Status.UNAVAILABLE, owned = null))
        assertEquals(Access.Unlocked, licence(offline, installedAt = now - 30 * DAY_MS).current())
    }

    @Test fun googlePlaysLaterAnswerWins_forExampleAfterARefund() {
        val store = FakeUnlockStore()
        val l = licence(store, installedAt = now - 30 * DAY_MS)
        store.mutable.update { it.copy(owned = true) }
        assertEquals(Access.Unlocked, l.current())
        store.mutable.update { it.copy(owned = false) }
        assertEquals(Access.TrialEnded, l.current())
    }

    @Test fun pendingPaymentDoesNotUnlockUntilConfirmed() {
        val store = FakeUnlockStore().apply { nextBuyPending = true }
        val l = licence(store, installedAt = now - 30 * DAY_MS)
        store.mutable.update { it.copy(pending = true) }
        assertEquals(Access.TrialEnded, l.current())
        store.completePending()
        assertEquals(Access.Unlocked, l.current())
    }

    @Test fun settingTheClockBackDoesNotAddDays() {
        // Day 6 seen once...
        assertEquals(1, (licence(clockMs = now + 6 * DAY_MS).current() as Access.Trial).daysLeft)
        // ...then the phone's clock is put back 5 days: still 1 day left, not 6
        assertEquals(1, (licence(clockMs = now + DAY_MS).current() as Access.Trial).daysLeft)
    }

    @Test fun resetKeepsTheTrialStartAndThePurchase() = runBlocking {
        val store = FakeUnlockStore()
        val c = testContainer(context, store = store, installedAt = now - 3 * DAY_MS)
        store.mutable.update { it.copy(owned = true) }
        assertEquals(Access.Unlocked, c.licence.current())

        c.repo.open()
        c.eraseEverything()

        // After "Reset Owl Tracker", with Google Play unreachable and no install time to go on,
        // the purchase and the original trial start are both still remembered.
        val offline = FakeUnlockStore(StoreState(status = StoreState.Status.UNAVAILABLE, owned = null))
        val again = testContainer(context, store = offline, installedAt = 0L)
        assertEquals(Access.Unlocked, again.licence.current())
        assertEquals(now - 3 * DAY_MS, prefs.getLong("trial_start", 0L))
    }

    @Test fun anUnknownInstallTimeStartsTheTrialAtFirstLaunch() {
        val first = licence(clockMs = now, installedAt = 0L).current()
        assertEquals(7, (first as Access.Trial).daysLeft)
        // A later launch remembers the start
        assertEquals(4, (licence(clockMs = now + 3 * DAY_MS, installedAt = 0L).current() as Access.Trial).daysLeft)
    }

    @Test fun debugSwitchEndsTheTrialOnlyInDebugBuilds() {
        val release = licence(debuggable = false)
        release.setDebugTrialEnded(true)
        assertTrue(release.current() is Access.Trial)

        val debug = licence(debuggable = true)
        debug.setDebugTrialEnded(true)
        assertEquals(Access.TrialEnded, debug.current())
        debug.setDebugTrialEnded(false)
        assertTrue(debug.current() is Access.Trial)
    }

    // ---- Google Play's purchase signature ----

    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
    private val json = """{"orderId":"GPA.1234","packageName":"com.owl.tracker","productId":"owl_tracker_unlock","purchaseState":0}"""

    private fun sign(data: String): String = Signature.getInstance("SHA1withRSA").run {
        initSign(keys.private); update(data.toByteArray()); Base64.getEncoder().encodeToString(sign())
    }

    @Test fun acceptsAPurchaseSignedByGooglePlay() {
        assertTrue(PurchaseVerifier.verify(publicKey, json, sign(json)))
    }

    @Test fun rejectsATamperedOrForgedPurchase() {
        assertFalse(PurchaseVerifier.verify(publicKey, json.replace("GPA.1234", "GPA.9999"), sign(json)))
        assertFalse(PurchaseVerifier.verify(publicKey, json, "bm90IGEgc2lnbmF0dXJl"))
        assertFalse(PurchaseVerifier.verify(publicKey, json, "not base64 at all!"))
    }

    @Test fun withNoLicenceKeyConfiguredGooglePlaysAnswerIsTrusted() {
        assertTrue(PurchaseVerifier.verify("", json, "anything"))
        assertTrue(PurchaseVerifier.verify("  \n ", json, "anything"))
    }

    @Test fun theShippedLicenceKeyIsEmptyOrAValidRsaKey() {
        val key = context.getString(R.string.play_licence_key).filterNot { it.isWhitespace() }
        if (key.isNotEmpty()) {
            // A key pasted from Play Console must parse; a typo would reject every real purchase.
            val parsed = java.security.KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(key)))
            assertEquals("RSA", parsed.algorithm)
        }
    }
}
