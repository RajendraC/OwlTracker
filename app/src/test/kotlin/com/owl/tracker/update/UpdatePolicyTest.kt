package com.owl.tracker.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.owl.tracker.update.UpdatePolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** When the app shows the update card, Google Play's full-screen update, or nothing; and What's new once. */
@RunWith(RobolectricTestRunner::class)
class UpdatePolicyTest {

    private fun decide(
        availability: Int = UpdateAvailability.UPDATE_AVAILABLE,
        status: Int = InstallStatus.UNKNOWN,
        priority: Int = 2,
        flexible: Boolean = true,
        immediate: Boolean = true,
        code: Int = 142,
        putOff: Int = -1,
    ) = UpdatePolicy.decide(availability, status, priority, flexible, immediate, code, putOff)

    @Test
    fun aNormalUpdateIsACardThatLaterPutsOffUntilTheNextVersion() {
        assertEquals(Action.CARD, decide())
        assertEquals(Action.NOTHING, decide(putOff = 142))
        assertEquals(Action.CARD, decide(code = 143, putOff = 142))
        assertEquals(Action.NOTHING, decide(availability = UpdateAvailability.UPDATE_NOT_AVAILABLE))
        assertEquals(Action.NOTHING, decide(flexible = false))
    }

    @Test
    fun anUrgentUpdateIsGooglePlaysFullScreenAndIgnoresLater() {
        assertEquals(Action.FULL_SCREEN, decide(priority = 4))
        assertEquals(Action.FULL_SCREEN, decide(priority = 5, putOff = 142))
        // Play won't allow the full screen: the card, even after Later
        assertEquals(Action.CARD, decide(priority = 5, immediate = false, putOff = 142))
        // Left halfway: carry on
        assertEquals(Action.FULL_SCREEN, decide(availability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS))
    }

    @Test
    fun aDownloadInProgressOrFinishedComesFirst() {
        assertEquals(Action.DOWNLOADING, decide(status = InstallStatus.DOWNLOADING, putOff = 142))
        assertEquals(Action.DOWNLOADING, decide(status = InstallStatus.PENDING))
        assertEquals(Action.DOWNLOADED, decide(status = InstallStatus.DOWNLOADED, priority = 5))
        assertEquals(37, UpdatePolicy.percent(3_700, 10_000))
        assertEquals(0, UpdatePolicy.percent(5, 0))
    }

    @Test
    fun whatsNewShowsOnceAfterUpdatingNeverOnAFreshInstall() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        fun updates() = AppUpdates(null, ctx.getSharedPreferences("updates_test", Context.MODE_PRIVATE))
        ctx.getSharedPreferences("updates_test", Context.MODE_PRIVATE).edit().clear().commit()

        // A fresh install of 1.9.4: nothing to catch up on, and not later either
        updates().apply { startUp(37, "1.9.4", updatedFromEarlier = false); assertNull(whatsNew.value) }
        updates().apply { startUp(37, "1.9.4", updatedFromEarlier = true); assertNull(whatsNew.value) }

        // Coming from 1.9.3 (never seen any): shown until closed, then not again
        ctx.getSharedPreferences("updates_test", Context.MODE_PRIVATE).edit().clear().commit()
        updates().apply {
            startUp(37, "1.9.4", updatedFromEarlier = true)
            assertEquals("1.9.4", whatsNew.value)
            closeWhatsNew()
            assertNull(whatsNew.value)
        }
        updates().apply { startUp(37, "1.9.4", updatedFromEarlier = true); assertNull(whatsNew.value) }

        // A later version with no notes: nothing, and it counts as seen
        updates().apply { startUp(38, "1.9.5", updatedFromEarlier = true); assertNull(whatsNew.value) }
        assertEquals(3, WhatsNew.notes("1.9.4").size)
    }
}
