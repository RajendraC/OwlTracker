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

    @Test
    fun whatChangedMostIsNewRisingOrFalling() {
        val transport = r(12400, 13800, 15200, 16900, 17500, 21400)
        assertEquals(Move(MoveKind.RISING, 3, 5, 4_500_00, 16_900_00), Trends.move(transport))
        assertEquals(Move(MoveKind.FALLING, 3, 5, -1_400_00, 5_900_00), Trends.move(r(7800, 7100, 6400, 5900, 5200, 4500)))
        // Nothing before October, then a trip: new
        assertEquals(Move(MoveKind.NEW, 5, 5, 10_500_00, 0), Trends.move(r(0, 0, 0, 0, 0, 10500)))
        // Up and down, but October far above (or below) the middle of the months before it
        assertEquals(Move(MoveKind.ABOVE, 5, 5, 4_350_00, 3_050_00), Trends.move(r(null, 3000, 3200, 2900, 3100, 7400)))
        assertEquals(Move(MoveKind.BELOW, 5, 5, -4_100_00, 6_100_00), Trends.move(r(6000, 6200, 5800, 6100, 6300, 2000)))
        // A trip in August and nothing since: with only two months before it, not enough to call September "down"
        assertEquals(null, Trends.move(r(null, null, null, 0, 12000, 0)))
        // Steady, up and down, too early, or too small: not news
        assertEquals(null, Trends.move(r(40328, 40328, 40328, 40328, 40328, 40328)))
        assertEquals(null, Trends.move(r(2100, 3900, 2600, 9800, 1900, 2100)))
        assertEquals(null, Trends.move(r(null, null, null, null, null, 5000)))
        assertEquals(null, Trends.move(r(null, null, null, 1000, 1100, 1400)))
        assertEquals(null, Trends.move(r(null, null, null, null, 0, 300)))

        val moves = Trends.biggestMoves(
            mapOf(
                "transport" to transport, "holiday" to r(0, 0, 0, 0, 0, 10500), "groceries" to r(7800, 7100, 6400, 5900, 5200, 4500),
                "food" to r(3200, 3600, 4100, 3700, 3900, 4100), "health" to r(null, null, null, 1000, 1100, 1400),
                "entertainment" to r(2400, 2900, 3100, 2800, 3300, 3500),
            ),
        )
        assertEquals(listOf("holiday", "transport", "groceries"), moves.map { it.first })
    }

    @Test
    fun theMonthsSliderGoesToFiveLakhAndBeyond() {
        assertEquals(5_00_000_00L, Trends.monthSliderMax(90_000_00))
        assertEquals(5_00_000_00L, Trends.monthSliderMax(1_50_000_00))
        // A usual month of ₹3,00,000: twice that
        assertEquals(6_00_000_00L, Trends.monthSliderMax(3_00_000_00))
        // ₹6,00,000 typed: 25% above it
        assertEquals(7_50_000_00L, Trends.monthSliderMax(90_000_00, 6_00_000_00))
    }
}
