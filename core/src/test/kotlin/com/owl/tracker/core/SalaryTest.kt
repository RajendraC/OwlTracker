package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SalaryTest {
    private fun s(d: String, rupees: Long = 120_000) = SalarySeen(LocalDate.parse(d), rupees * 100)

    @Test fun latestWithinThreeDays() {
        val seen = listOf(s("2026-10-25"), s("2026-11-25"))
        assertEquals(LocalDate.parse("2026-11-25"), Salary.latest(seen, LocalDate.parse("2026-11-27"))?.date)
        assertNull(Salary.latest(seen, LocalDate.parse("2026-11-29")))
    }

    @Test fun usualDayNeedsTwoMonthsInARowCloseTogether() {
        assertEquals(25, Salary.usualDay(listOf(s("2026-09-24"), s("2026-10-26"), s("2026-11-25"))))
        assertNull(Salary.usualDay(listOf(s("2026-11-25"))))
        assertNull(Salary.usualDay(listOf(s("2026-10-05"), s("2026-11-25"))))
        assertNull(Salary.usualDay(listOf(s("2026-08-25"), s("2026-11-25")))) // months apart
    }

    @Test fun aBonusInTheSameMonthCountsOnce() {
        assertEquals(25, Salary.usualDay(listOf(s("2026-10-25"), s("2026-10-10", 20_000), s("2026-11-25"))))
        // another salary-sized credit early in the month doesn't hide the usual day
        assertEquals(22, Salary.usualDay(listOf(s("2026-10-01"), s("2026-10-22"), s("2026-11-22"))))
    }

    @Test fun suggestsTheSalaryDayOnlyWhenTheMonthStartsElsewhere() {
        val seen = listOf(s("2026-10-25"), s("2026-11-25"))
        assertEquals(25, Salary.suggestStartDay(seen, 1))
        assertNull(Salary.suggestStartDay(seen, 25))
        assertNull(Salary.suggestStartDay(seen, 24))
        // paid on the last days of the month: a month from the 1st already fits
        assertNull(Salary.suggestStartDay(listOf(s("2026-10-30"), s("2026-11-30")), 1))
    }

    @Test fun aTenthRoundedDownTo500() {
        assertEquals(12_000_00L, Salary.setAside(120_000_00))
        assertEquals(4_500_00L, Salary.setAside(47_300_00))
        assertEquals(500_00L, Salary.setAside(2_000_00))
    }

    @Test fun reviewOnlyAfterAWeekThirtyPaymentsAndRarely() {
        val day = 86_400_000L
        val now = 1_000 * day
        assertTrue(ReviewAsk.shouldAsk(now - 10 * day, now, 40, null, sample = false))
        assertFalse(ReviewAsk.shouldAsk(now - 3 * day, now, 40, null, false))
        assertFalse(ReviewAsk.shouldAsk(now - 10 * day, now, 12, null, false))
        assertFalse(ReviewAsk.shouldAsk(now - 300 * day, now, 40, now - 30 * day, false))
        assertTrue(ReviewAsk.shouldAsk(now - 300 * day, now, 40, now - 130 * day, false))
        assertFalse(ReviewAsk.shouldAsk(now - 10 * day, now, 40, null, sample = true))
    }
}
