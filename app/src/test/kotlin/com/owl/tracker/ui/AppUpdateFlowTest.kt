package com.owl.tracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.owl.tracker.TestApp
import com.owl.tracker.OwlTrackerApp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

/** A new version on Google Play, through the real Home with Google's fake update manager. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class AppUpdateFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val play = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() { TestApp.updatesOverride = play }
            override fun after() { TestApp.updatesOverride = null }
        })
        .around(compose)

    private fun setUpManually() = with(compose) {
        tapTag("get_started")
        tapTag("not_now")
        tapTag("save_budget")
    }

    private fun checkAgain() = compose.runOnUiThread { (compose.activity.application as OwlTrackerApp).container.updates.check() }

    private fun gone(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    @Test
    fun updateDownloadsWhileInUseThenRestartFinishes() {
        setUpManually()
        play.setUpdateAvailable(142)
        play.setTotalBytesToDownload(6_000_000)
        checkAgain()
        with(compose) {
            waitForTag("update_card")
            waitForText("A new version is ready")
            waitForText("Keep using the app while it downloads.")
            tapTag("update_now")
            waitUntil(5_000) { play.isConfirmationDialogVisible }
            play.userAcceptsUpdate()
            play.downloadStarts()
            play.setBytesDownloaded(3_000_000)
            waitForText("Downloading the update…")
            waitForTag("update_progress")
            play.downloadCompletes()
            waitForText("Update downloaded")
            tapTag("update_restart")
            waitUntil(5_000) { play.isInstallSplashScreenVisible }
        }
    }

    @Test
    fun aRestartThatFailsFinishesInGooglePlay() {
        setUpManually()
        play.setUpdateAvailable(152)
        checkAgain()
        with(compose) {
            waitForTag("update_card")
            tapTag("update_now")
            waitUntil(5_000) { play.isConfirmationDialogVisible }
            play.userAcceptsUpdate()
            play.downloadStarts()
            play.downloadCompletes()
            waitForText("Update downloaded")
            tapTag("update_restart")
            waitForText("Installing the update…")
            waitUntil(5_000) { play.isInstallSplashScreenVisible }
            play.installFails()
            waitForText("Finish the update in Google Play")
            waitForTag("update_open_play")
            // Opening the app again doesn't bring Restart back for this version
            checkAgain()
            waitForIdle()
            waitForText("Finish the update in Google Play")
            assertTrue(onAllNodesWithTag("update_restart").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun laterHidesItUntilTheNextVersion() {
        setUpManually()
        play.setUpdateAvailable(142)
        checkAgain()
        with(compose) {
            waitForTag("update_card")
            tapTag("update_later")
            gone("update_card")
            checkAgain()
            waitForIdle()
            assertTrue(onAllNodesWithTag("update_card").fetchSemanticsNodes().isEmpty())
            play.setUpdateAvailable(143)
            checkAgain()
            waitForTag("update_card")
        }
    }

    @Test
    fun anUrgentFixOpensGooglePlaysFullScreenUpdate() {
        setUpManually()
        play.setUpdateAvailable(142, com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE)
        play.setUpdatePriority(5)
        checkAgain()
        // Google Play was asked for its full-screen update (type IMMEDIATE = 1) and took it; no card on Home
        val updates = (compose.activity.application as OwlTrackerApp).container.updates
        compose.waitUntil(5_000) { updates.lastStart == "type 1 started: true" }
        assertTrue(compose.onAllNodesWithTag("update_card").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun whatsNewIsShownOnceAfterUpdating() {
        setUpManually()
        compose.runOnUiThread { (compose.activity.application as OwlTrackerApp).container.updates.startUp(9_999, "1.9.4", updatedFromEarlier = true) }
        with(compose) {
            waitForTag("whats_new")
            waitForText("What's new in 1.9.4")
            waitForText("Still to pay on credit cards")
            tapTag("whats_new_ok")
            gone("whats_new")
        }
    }
}
