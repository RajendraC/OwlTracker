package com.owl.tracker.nudge

import com.owl.tracker.core.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** What the owl says, and when: the words of each nudge from a month's figures. */
class NudgesTest {
    private val nov = BudgetPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))
    private fun rs(r: Long) = r * 100

    private fun snap(
        spent: Long, overall: Long = rs(99500), today: Long = rs(1240), share: Long = rs(2317), at: LocalDate = LocalDate.of(2026, 11, 27),
        over: List<Triple<String, Long, Long>> = emptyList(), week: Long = 0, lastWeek: Long = 0,
    ) = MonthSnapshot(nov, at, spent, overall, today, share, week, lastWeek, over, "Shopping", appLock = false)

    @Test
    fun theEveningLineSaysHowTodayWent() {
        val n = Nudges.due(snap(rs(85240)), LocalDateTime.of(2026, 11, 27, 21, 0), null)
        val day = n.single { it.kind == Nudges.DAILY }
        assertEquals("Today: ₹1,240 👍", day.title)
        assertTrue(day.text, day.text.startsWith("Within today's ₹2,317 share."))
        // Before 7 pm there's no evening line yet
        assertTrue(Nudges.due(snap(rs(85240)), LocalDateTime.of(2026, 11, 27, 15, 0), null).none { it.kind == Nudges.DAILY })
        // Over the share
        val over = Nudges.due(snap(rs(85240), today = rs(3000)), LocalDateTime.of(2026, 11, 27, 21, 0), null).single { it.kind == Nudges.DAILY }
        assertEquals("₹683 over today's ₹2,317 share.", over.text)
        // A ₹0 day
        assertEquals(
            "Nothing went out today. A no-spend day!",
            Nudges.due(snap(rs(85240), today = 0), LocalDateTime.of(2026, 11, 27, 21, 0), null).single { it.kind == Nudges.DAILY }.text,
        )
    }

    @Test
    fun budgetAlertsAt80AndPastTheBudget() {
        val at80 = Nudges.due(snap(rs(80000)), LocalDateTime.of(2026, 11, 20, 10, 0), null).single { it.kind == Nudges.ALERTS }
        assertEquals("80% of November's budget used", at80.title)
        assertEquals("80_2026-11-01", at80.key)
        val past = Nudges.due(snap(rs(105000)), LocalDateTime.of(2026, 11, 20, 10, 0), null).single { it.kind == Nudges.ALERTS }
        assertEquals("You've gone past November's budget", past.title)
        assertTrue(past.text, past.text.startsWith("₹5,500 over"))
        // Under 80%: nothing
        assertTrue(Nudges.due(snap(rs(50000)), LocalDateTime.of(2026, 11, 20, 10, 0), null).none { it.kind == Nudges.ALERTS })
        // A category going over
        val cat = Nudges.due(snap(rs(50000), over = listOf(Triple("Shopping", rs(25645), rs(20000)))), LocalDateTime.of(2026, 11, 20, 10, 0), null).single()
        assertEquals("Shopping went over its budget", cat.title)
    }

    @Test
    fun sundayBringsTheWeekInsteadOfTheDay() {
        val sunday = LocalDateTime.of(2026, 11, 29, 21, 0)
        val n = Nudges.due(snap(rs(50000), week = rs(14420), lastWeek = rs(16520), at = sunday.toLocalDate()), sunday, null)
        assertTrue(n.none { it.kind == Nudges.DAILY })
        val w = n.single { it.kind == Nudges.WEEKLY }
        assertEquals("Your week: ₹14,420", w.title)
        assertTrue(w.text, w.text.startsWith("₹2,100 less than last week."))
    }

    @Test
    fun theRecapComesInTheFirstDaysOfTheNewMonth() {
        val dec = BudgetPeriod(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1))
        val now = MonthSnapshot(dec, LocalDate.of(2026, 12, 1), 0, rs(99500), 0, rs(3000), 0, 0, emptyList(), null, false)
        val last = snap(rs(87560), at = LocalDate.of(2026, 11, 30))
        val r = Nudges.due(now, LocalDateTime.of(2026, 12, 1, 9, 0), last).single { it.kind == Nudges.RECAP }
        assertEquals("Your November recap is ready", r.title)
        assertEquals("You came in ₹11,940 under budget. See the highlights.", r.text)
        // Day 4: no more
        val later = now.copy(today = LocalDate.of(2026, 12, 4))
        assertTrue(Nudges.due(later, LocalDateTime.of(2026, 12, 4, 9, 0), last).none { it.kind == Nudges.RECAP })
    }

    @Test
    fun aBillDueSoonGetsANudge() {
        val emi = com.owl.tracker.core.RecurringBill("hdfc emi", "HDFC EMI", "emi", rs(18500), 30, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), 4, true)
        val n = Nudges.due(snap(rs(50000)), LocalDateTime.of(2026, 11, 29, 10, 0), null, listOf(emi to LocalDate.of(2026, 12, 1)))
        val b = n.single { it.key.startsWith("bill_") }
        assertEquals("HDFC EMI: ₹18,500 due in 2 days", b.title)
        // Three days out: not yet
        assertTrue(Nudges.due(snap(rs(50000)), LocalDateTime.of(2026, 11, 28, 10, 0), null, listOf(emi to LocalDate.of(2026, 12, 1))).none { it.key.startsWith("bill_") })
    }

    @Test
    fun theMonthlyReportComesOnTheSecond() {
        val dec = BudgetPeriod(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1))
        val last = snap(rs(87560), at = LocalDate.of(2026, 11, 30))
        fun on(day: Int, hour: Int) = Nudges.due(
            MonthSnapshot(dec, LocalDate.of(2026, 12, day), 0, rs(99500), 0, rs(3000), 0, 0, emptyList(), null, false),
            LocalDateTime.of(2026, 12, day, hour, 0), last,
        ).filter { it.kind == Nudges.REPORT }
        assertTrue(on(1, 10).isEmpty())   // the 1st is the recap's
        val r = on(2, 10).single()
        assertEquals("Your November report is ready 📊", r.title)
        assertEquals("Spent ₹87,560 · saved ₹11,940. Dig into it on a computer: an interactive page that opens in any browser.", r.text)
        assertTrue(on(2, 7).isEmpty())    // not before 9 am
        assertTrue(on(5, 10).isEmpty())
    }
}
