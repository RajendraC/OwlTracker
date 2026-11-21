package com.owl.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.owl.tracker.TestApp
import com.owl.tracker.ui.components.LAUNCH_SECONDS
import com.owl.tracker.ui.components.LaunchAct
import com.owl.tracker.ui.components.LaunchBadge
import com.owl.tracker.ui.theme.OwlTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The owl's hello when the app opens: all five acts draw, it goes away by itself, and it never blocks a tap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class LaunchGreetingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyActDrawsTheOwlFromStartToEnd() {
        var act by mutableStateOf(LaunchAct.BLINK)
        var t by mutableStateOf(0f)
        compose.setContent { OwlTrackerTheme(dark = false) { LaunchBadge(act, t, 120.dp, Modifier.testTag("badge")) } }
        for (a in LaunchAct.entries) for (step in 0..6) {
            act = a; t = LAUNCH_SECONDS * step / 6f
            compose.waitForIdle()
            val px = compose.onNodeWithTag("badge").captureToImage().toPixelMap()
            // the owl's dark brown is in the middle of the mango circle
            var brown = 0
            for (x in 30 until 90 step 3) for (y in 20 until 80 step 3) { val c = px[x, y]; if (c.red < 0.45f && c.green < 0.3f && c.alpha > 0.9f) brown++ }
            assertTrue("$a at $t", brown > 20)
        }
    }

    @Test
    fun itGoesAwayByItselfAndLetsTapsThrough() {
        var done by mutableStateOf(false)
        var taps by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OwlTrackerTheme(dark = false) {
                Box {
                    Button(onClick = { taps++ }, Modifier.testTag("under")) { Text("Under") }
                    if (!done) LaunchGreeting(LaunchAct.WAKE_UP) { done = true }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        assertFalse(done)
        compose.onNodeWithTag("under").performClick()
        assertEquals(1, taps)
        compose.mainClock.advanceTimeBy((LAUNCH_SECONDS * 1000).toLong() + 600)
        compose.waitForIdle()
        assertTrue(done)
    }
}
