package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeeksTest {
    private fun d(day: Int, month: Int = 11) = LocalDate.of(2026, month, day)
    private fun rs(r: Long) = r * 100

    /** The tester's November up to Friday the 13th, day by day (₹), with the EMIs marked. */
    private val spends = listOf(
        DaySpend(d(1), rs(2199), false),
        DaySpend(d(2), rs(8951), false),
        DaySpend(d(3), rs(8328), true), DaySpend(d(3), rs(1770), false),
        DaySpend(d(4), rs(2792), false),
        DaySpend(d(5), rs(28500), true), DaySpend(d(5), rs(360), false),
        DaySpend(d(6), rs(2237), false),
        DaySpend(d(7), rs(3600), false),
        DaySpend(d(8), rs(3964), false),
        DaySpend(d(9), rs(4290), false),
        DaySpend(d(10), rs(3500), true), DaySpend(d(10), rs(2464), false),
        DaySpend(d(11), rs(4748), false),
        DaySpend(d(12), rs(3129), false),
        DaySpend(d(13), rs(1740), false),
    )
    private val today = d(13)
    private val share = { _: LocalDate -> rs(3000) }

    @Test
    fun weeksRunSundayToSaturday() {
        assertEquals(d(8), Weeks.startOf(d(13)))
        assertEquals(d(8), Weeks.startOf(d(8)))
        assertEquals(d(8), Weeks.startOf(d(14)))
        assertEquals(LocalDate.of(2026, 9, 27), Weeks.startOf(d(3, 10)))
        assertEquals(listOf(d(8), d(14)), Weeks.daysOf(d(8)).let { listOf(it.first(), it.last()) })
        assertEquals("8–14 Nov", Weeks.label(d(8)))
        assertEquals("27 Sep – 3 Oct", Weeks.label(LocalDate.of(2026, 9, 27)))
        assertEquals("27 Dec 2026 – 2 Jan 2027", Weeks.label(LocalDate.of(2026, 12, 27)))
    }

    @Test
    fun aDayIsLightWithinHeavyOrVeryHeavyAgainstItsShare() {
        val s = rs(3000)
        assertEquals(DayLevel.NONE, Weeks.level(0, s))
        assertEquals(DayLevel.LIGHT, Weeks.level(rs(1500), s))
        assertEquals(DayLevel.WITHIN, Weeks.level(rs(1501), s))
        assertEquals(DayLevel.WITHIN, Weeks.level(rs(3000), s))
        assertEquals(DayLevel.HEAVY, Weeks.level(rs(3001), s))
        assertEquals(DayLevel.HEAVY, Weeks.level(rs(6000), s))
        assertEquals(DayLevel.VERY_HEAVY, Weeks.level(rs(6001), s))
        // No budget and no usual day to go by: any spending is just "spent"
        assertEquals(DayLevel.WITHIN, Weeks.level(rs(50000), 0))
        assertEquals(rs(3000), Weeks.dailyShare(rs(90000), 30))
        assertEquals(0L, Weeks.dailyShare(0, 30))
    }

    @Test
    fun theEmiDayIsColouredByEverydaySpendingAndTheFutureIsLeftOpen() {
        val week = Weeks.totals(Weeks.daysOf(d(1)), spends, today, share)
        val emiDay = week.first { it.date == d(5) }
        assertEquals(rs(28860), emiDay.paise)
        assertEquals(rs(28500), emiDay.fixedPaise)
        assertEquals("₹28,500 EMI, ₹360 everyday: a light day", DayLevel.LIGHT, emiDay.level)
        assertEquals(DayLevel.VERY_HEAVY, week.first { it.date == d(2) }.level)
        assertEquals(DayLevel.HEAVY, week.first { it.date == d(7) }.level)
        assertEquals(DayLevel.WITHIN, week.first { it.date == d(1) }.level)

        val thisWeek = Weeks.totals(Weeks.daysOf(d(8)), spends, today, share)
        assertEquals(DayLevel.FUTURE, thisWeek.last().level)
        assertEquals(0L, thisWeek.last().paise)
        assertEquals(DayLevel.WITHIN, thisWeek.first { it.date == d(13) }.level)
        assertEquals(DayLevel.HEAVY, thisWeek.first { it.date == d(11) }.level)
    }

    @Test
    fun aWeekStillGoingIsComparedWithTheSameDaysOfTheWeekBefore() {
        val c = Weeks.compare(d(8), spends, today)!!
        assertEquals(6, c.days)
        // Sun–Fri this week, EMIs left out, against Sun–Fri last week
        assertEquals(rs(3964 + 4290 + 2464 + 4748 + 3129 + 1740), c.nowPaise)
        assertEquals(rs(2199 + 8951 + 1770 + 2792 + 360 + 2237), c.beforePaise)
        assertEquals(11, c.percent)
        // A finished week: all seven days
        val last = Weeks.compare(d(1), spends, today)!!
        assertEquals(7, last.days)
        assertEquals(rs(21909), last.nowPaise)
        assertNull("Nothing before November in this list", last.percent)
        assertNull(Weeks.compare(d(15), spends, today))
    }

    @Test
    fun aBudgetMonthIsLaidOutInWeeksThatAddUpToTheMonth() {
        val rows = Weeks.calendar(d(1), d(1, 12), spends, today, share)
        assertEquals(listOf(d(1), d(8), d(15), d(22), d(29)), rows.map { it.weekStart })
        assertEquals(rs(58737), rows[0].paise)
        assertEquals(rs(23835), rows[1].paise)
        assertEquals(spends.sumOf { it.paise }, rows.sumOf { it.paise })
        // 29 and 30 Nov, then December's days are left blank
        assertEquals(listOf(true, true, false, false, false, false, false), rows[4].days.map { it != null })
        assertEquals(DayLevel.FUTURE, rows[4].days[0]!!.level)

        // A budget month from the 25th: the first row starts on the Sunday before
        val custom = Weeks.calendar(d(25, 10), d(25), spends, today, share)
        assertEquals(LocalDate.of(2026, 10, 25), custom.first().weekStart)
        assertEquals(LocalDate.of(2026, 11, 22), custom.last().weekStart)
        assertEquals(listOf(true, true, true, false, false, false, false), custom.last().days.map { it != null })
    }

    @Test
    fun withNoBudgetAUsualDayComesFromEverydaySpending() {
        assertEquals(rs(21909) / 7, Weeks.usualDay(spends, d(1), d(8)))
        assertEquals(0L, Weeks.usualDay(spends, d(15), d(22)))
        assertEquals(0L, Weeks.usualDay(spends, d(8), d(8)))
    }
}
