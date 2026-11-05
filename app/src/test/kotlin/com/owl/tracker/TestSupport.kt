package com.owl.tracker

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.owl.tracker.licence.FakeUnlockStore
import com.owl.tracker.licence.UnlockStore
import com.owl.tracker.sms.FakeSmsSource
import com.owl.tracker.core.SampleSms
import com.owl.tracker.core.SmsMessage
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Saturday 18 Oct 2026, 1:14 PM in India: mid-month, so every sample lands in the current budget month. */
val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-18T07:44:00Z"), ZoneId.of("Asia/Kolkata"))

fun sampleInbox(extra: List<SmsMessage> = emptyList()): MutableList<SmsMessage> =
    (SampleSms.messages(TEST_CLOCK.millis()) + extra).toMutableList()

const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * In-memory database, sample inbox, fixed clock, a fake Google Play. No Keystore (Robolectric has none).
 * By default the app was installed just now, so the 7-day trial is running.
 */
fun testContainer(
    context: Context,
    inbox: MutableList<SmsMessage> = sampleInbox(),
    granted: Boolean = true,
    store: UnlockStore = FakeUnlockStore(),
    installedAt: Long = TEST_CLOCK.millis(),
) = AppContainer(
    context = context,
    dbFactory = { FrameworkSQLiteOpenHelperFactory() },
    dbName = null,
    sms = FakeSmsSource({ inbox.toList() }, granted),
    clock = TEST_CLOCK,
    keyManager = null,
    unlockStore = store,
    installedAt = { installedAt },
)

/** Application used by Robolectric UI tests: same app, test container, no background scheduling. */
class TestApp : OwlTrackerApp() {
    override fun createContainer(): AppContainer = testContainer(
        this,
        inboxOverride ?: sampleInbox(),
        store = storeOverride ?: FakeUnlockStore(),
        installedAt = installedAtOverride ?: TEST_CLOCK.millis(),
    )
    override val scheduleBackgroundSync: Boolean = false

    companion object {
        /** Set before the activity starts (from an outer rule) to give one test its own inbox. */
        var inboxOverride: MutableList<SmsMessage>? = null

        /** When the app was "installed": 8 days ago means the free trial has ended. */
        var installedAtOverride: Long? = null

        /** The fake Google Play for one test, to buy through or inspect afterwards. */
        var storeOverride: FakeUnlockStore? = null
    }
}
