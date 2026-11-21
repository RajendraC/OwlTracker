package com.owl.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
        val steps = 7
        compose.setContent {
            OwlTrackerTheme(dark = false) {
                Column(Modifier.testTag("grid")) {
                    for (a in LaunchAct.entries) Row {
                        for (step in 0 until steps) LaunchBadge(a, LAUNCH_SECONDS * step / (steps - 1), 50.dp)
                    }
                }
            }
        }
        compose.waitForIdle()
        val px = compose.onNodeWithTag("grid").captureToImage().toPixelMap()
        val cell = px.width / steps
        for ((row, a) in LaunchAct.entries.withIndex()) for (step in 0 until steps) {
            // the owl's dark brown fills the middle of each mango circle
            var brown = 0; var seen = 0
            for (fx in 35..65 step 3) for (fy in 30..60 step 3) {
                val c = px[step * cell + cell * fx / 100, row * cell + cell * fy / 100]
                seen++
                if (c.red < 0.45f && c.green < 0.3f && c.alpha > 0.9f) brown++
            }
            assertTrue("$a, step $step: $brown of $seen", brown > seen / 5)
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
