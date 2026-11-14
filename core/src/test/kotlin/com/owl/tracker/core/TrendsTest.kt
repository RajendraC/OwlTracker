package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendsTest {

    private fun r(vararg rupees: Long?): List<Long?> = rupees.map { it?.times(100) }

    @Test
    fun theUsualMonthIsTheLastThreeMonthsRoundedUpToAHundredRupees() {
        assertEquals(18_600_00L, Trends.usualMonth(r(12400, 13800, 15200, 16900, 17500, 21400)))
        // An EMI of ₹40,328 every month: ₹40,400, so a budget at the usual month covers it
        assertEquals(40_400_00L, Trends.usualMonth(r(40328, 40328, 40328, 40328, 40328, 40328)))
        // Months before the records begin don't count as months with nothing spent
        assertEquals(5_000_00L, Trends.usualMonth(r(null, null, null, null, 4000, 6000)))
        assertEquals(0L, Trends.usualMonth(r(null, null, null, null, null, null)))
        assertEquals(0L, Trends.usualMonth(r(null, 0, 0, 0, 0, 0)))
    }

    @Test
    fun trendsInWords() {
        assertEquals(TrendKind.RISING, Trends.kind(r(12400, 13800, 15200, 16900, 17500, 21400)))
        assertEquals(TrendKind.FALLING, Trends.kind(r(7800, 7100, 6400, 5900, 5200, 4500)))
        assertEquals(TrendKind.STEADY, Trends.kind(r(40328, 40328, 40328, 40328, 40328, 40328)))
        assertEquals(TrendKind.STEADY, Trends.kind(r(3200, 3600, 4100, 3700, 3900, 4100)))
        // A sale month, and a trip: up and down
        assertEquals(TrendKind.UP_AND_DOWN, Trends.kind(r(2100, 3900, 2600, 9800, 1900, 2100)))
        assertEquals(TrendKind.UP_AND_DOWN, Trends.kind(r(0, 0, 0, 0, 0, 10500)))
        assertEquals(TrendKind.TOO_EARLY, Trends.kind(r(null, null, null, null, null, 5000)))
        assertEquals(TrendKind.NONE, Trends.kind(r(null, null, 0, 0, 0, 0)))
    }

    @Test
    fun howOftenABudgetWouldHaveBeenEnough() {
        val transport = r(12400, 13800, 15200, 16900, 17500, 21400)
        assertEquals(5, Trends.enoughIn(transport, 18_600_00))
        assertEquals(2, Trends.enoughIn(transport, 15_000_00))
        assertEquals(6, Trends.enoughIn(r(40328, 40328, 40328, 40328, 40328, 40328), 40_400_00))
        // Only months with data are counted
        assertEquals(2, Trends.enoughIn(r(null, null, null, null, 4000, 6000), 6_000_00))
    }

    @Test
    fun theSliderEndsAtTwiceTheUsualMonthAndStretchesForATypedAmount() {
        assertEquals(38_000_00L, Trends.sliderMax(18_600_00))
        assertEquals(81_000_00L, Trends.sliderMax(40_400_00))
        assertEquals(5_000_00L, Trends.sliderMax(900_00))
        assertEquals(5_000_00L, Trends.sliderMax(0))
        // ₹12,500 typed for a ₹900 category: 25% above it, to the next ₹1,000
        assertEquals(16_000_00L, Trends.sliderMax(900_00, 12_500_00))
        assertEquals(5_000_00L, Trends.sliderMax(900_00, 4_000_00))
    }
}
