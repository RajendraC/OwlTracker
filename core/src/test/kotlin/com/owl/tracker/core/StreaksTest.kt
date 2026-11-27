package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 11, 27)   // a Friday
    private val nov = BudgetPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun rs(r: Long) = r * 100

    @Test
    fun daysInARowEndYesterday() {
        // Within ₹2,000 every day from the 21st to the 26th; the 20th went over
        val spend = (1..26).associate { d(it) to if (it == 20) rs(5000) else rs(1500) } + (d(27) to rs(300))
        val s = Streaks.compute(spend, rs(2000), d(1), today, nov)
        assertEquals(6, s.days)
        assertEquals(19, s.bestDays)   // the 1st to the 19th
        assertTrue(s.todayWithin)
        assertEquals(DayMark.TODAY, s.lastSeven.last().second)
        assertEquals(DayMark.WITHIN, s.lastSeven.first { it.first == d(21) }.second)
        assertEquals(d(21), s.lastSeven.first().first)
    }

    @Test
    fun noSpendDaysAreCountedFromTheFirstPayment() {
        // Payments start on the 10th; the 12th and 15th had nothing
        val spend = (10..26).filter { it != 12 && it != 15 }.associate { d(it) to rs(800) }
        val s = Streaks.compute(spend, rs(2000), d(10), today, nov)
        assertEquals(2, s.noSpendThisMonth)
        assertEquals(DayMark.WITHIN, s.lastSeven.first { it.first == d(21) }.second)
        assertTrue(Streaks.isNoSpend(d(12), 0, d(10), today))
        assertFalse(Streaks.isNoSpend(d(5), 0, d(10), today))    // before any payment
        assertFalse(Streaks.isNoSpend(today, 0, d(10), today))   // today isn't over
        // No-spend days count as within the share
        assertEquals(17, s.days)
    }

    @Test
    fun weeksInARowWithinSevenDaysShare() {
        // Mon 2 Nov onwards: ₹1,000 a day except one ₹9,000 day in the week of the 9th
        val spend = (2..26).associate { d(it) to if (it == 11) rs(9000) else rs(1000) }
        val s = Streaks.compute(spend, rs(2000), d(2), today, nov)
        // The week of the 16th was ₹7,000 of ₹14,000; the week of the 9th went over (₹15,000), which ends the run
        assertEquals(1, s.weeks)
        assertEquals(1, s.bestWeeks)
        // With no budget there's nothing to measure against
        val none = Streaks.compute(spend, 0, d(2), today, nov)
        assertEquals(0, none.days)
        assertEquals(0, none.weeks)
    }
}
