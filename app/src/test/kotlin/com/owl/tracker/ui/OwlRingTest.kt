package com.owl.tracker.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.owl.tracker.ui.components.OwlMood
import com.owl.tracker.ui.components.RING_TILT
import com.owl.tracker.ui.components.Ring3DGeometry
import com.owl.tracker.ui.components.Ring3DSlice
import com.owl.tracker.ui.components.RingHit
import com.owl.tracker.ui.components.ringSpans
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The owl's moods, and the 3D ring finding the slice under a finger wherever it is drawn. */
class OwlRingTest {

    @Test
    fun theOwlsMoodFollowsHowMuchOfTheBudgetHasGone() {
        assertEquals(OwlMood.CURIOUS, OwlMood.of(50_000, null))
        assertEquals(OwlMood.CURIOUS, OwlMood.of(50_000, 0))
        assertEquals(OwlMood.CHEERING, OwlMood.of(0, 10_000))
        assertEquals(OwlMood.CHEERING, OwlMood.of(5_000, 10_000))
        assertEquals(OwlMood.HAPPY, OwlMood.of(5_001, 10_000))
        assertEquals(OwlMood.HAPPY, OwlMood.of(8_000, 10_000))
        assertEquals(OwlMood.WORRIED, OwlMood.of(8_001, 10_000))
        assertEquals(OwlMood.WORRIED, OwlMood.of(10_000, 10_000))
        assertEquals(OwlMood.SAD, OwlMood.of(10_001, 10_000))
        assertEquals(OwlMood.SAD, OwlMood.of(12_500, 10_000))
        assertEquals(OwlMood.UPSET, OwlMood.of(12_501, 10_000))
        assertEquals(OwlMood.UPSET, OwlMood.of(20_000, 10_000))
        assertEquals(OwlMood.CRYING, OwlMood.of(20_001, 10_000))
        // The tester's Transport: ₹21,726 against a ₹1,000 budget
        assertEquals(OwlMood.CRYING, OwlMood.of(2_172_600, 100_000))
        // and EMI & Loans: ₹40,328 against ₹22,000
        assertEquals(OwlMood.UPSET, OwlMood.of(4_032_800, 2_200_000))
    }

    private val slices = listOf(
        Ring3DSlice("emi", Color.Red, 0.488f), Ring3DSlice("transport", Color.Blue, 0.263f), Ring3DSlice("sports", Color.Green, 0.089f),
        Ring3DSlice("entertainment", Color.Magenta, 0.045f), Ring3DSlice("shopping", Color.Cyan, 0.039f), Ring3DSlice("recharge", Color.Yellow, 0.027f),
        Ring3DSlice("food", Color.Gray, 0.021f), Ring3DSlice("others", Color.DarkGray, 0.013f), Ring3DSlice("groceries", Color.LightGray, 0.011f),
        Ring3DSlice("health", Color.Black, 0.004f),
    )

    // A 393 dp phone at xxhdpi: the card leaves about 347 dp for the ring
    private val g = Ring3DGeometry(347f * 2.75f, 230f * 2.75f, 2.75f)

    @Test
    fun unprojectUndoesProject() {
        for (spin in listOf(0f, -43.2f, 133f)) for (tilt in listOf(10f, 35f, RING_TILT)) for (h in listOf(0f, g.liftUp)) {
            for ((u, v) in listOf(0f to 0f, 200f to -50f, -150f to 260f, 300f to 300f)) {
                val p = g.project(u, v, h, spin, tilt)
                val back = g.unproject(p.x, p.y, h, spin, tilt)
                assertEquals("u at spin $spin tilt $tilt", u, back.x, 0.05f)
                assertEquals("v at spin $spin tilt $tilt", v, back.y, 0.05f)
            }
        }
    }

    @Test
    fun aTapFindsTheSliceDrawnUnderIt() {
        for (spin in listOf(0f, -43.2f, 166f, -300f)) {
            for (s in slices) {
                val p = g.pointOf(slices, s.id, spin)!!
                assertEquals("${s.id} at spin $spin", RingHit.Slice(s.id), g.hit(p.x, p.y, slices, spin, RING_TILT))
            }
        }
        val middle = g.project(0f, 0f, 0f, 0f, RING_TILT)
        assertEquals(RingHit.Middle, g.hit(middle.x, middle.y, slices, 0f, RING_TILT))
        val beyond = g.project(0f, g.r1 * 1.5f, 0f, 0f, RING_TILT)
        assertEquals(RingHit.Outside, g.hit(beyond.x, beyond.y, slices, 0f, RING_TILT))
    }

    @Test
    fun aLiftedSliceIsFoundWhereItNowStands() {
        val spans = ringSpans(slices)
        val i = 1
        val (a0, a1) = spans[i]
        val mid = (a0 + a1) / 2f * PI.toFloat() / 180f
        val spin = 180f - (a0 + a1) / 2f   // turned to the front, as the ring does when it's tapped
        val r = (g.r0 + g.r1) / 2f
        // Its middle, moved out and lifted up the way it's drawn
        val out = Offset(sin(mid), -cos(mid)) * g.liftOut
        val p = g.project(r * sin(mid) + out.x, -r * cos(mid) + out.y, g.liftUp, spin, RING_TILT)
        assertEquals(RingHit.Slice(slices[i].id), g.hit(p.x, p.y, slices, spin, RING_TILT, picked = slices[i].id, lift = 1f))
    }
}
