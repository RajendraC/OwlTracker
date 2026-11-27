package com.owl.tracker.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How a finished day went, for the streak dots. */
enum class DayMark { WITHIN, OVER, NO_SPEND, TODAY, BEFORE }

/**
 * Streaks, worked out from what went out each day (everyday spending: EMIs and rent left out, investments in):
 * days in a row within the day's share, weeks in a row within seven days' share, and no-spend days. Days before
 * the first recorded payment don't count either way; today counts once it's over, so the streak ends yesterday.
 */
data class StreakInfo(
    /** Days in a row within the share, up to yesterday. */
    val days: Int,
    val bestDays: Int,
    /** Monday-to-Sunday weeks in a row within the week's share, up to last week. */
    val weeks: Int,
    val bestWeeks: Int,
    /** The last seven days, oldest first, today last. */
    val lastSeven: List<Pair<LocalDate, DayMark>>,
    /** Whether today so far is within its share (the streak grows tonight if it stays so). */
    val todayWithin: Boolean,
    val noSpendThisMonth: Int,
    /** The most no-spend days in any one budget month. */
    val bestNoSpendMonth: Int,
    val share: Long,
)

object Streaks {
    /**
     * [everyday]: each day's everyday spending; [share]: a day's share (0: no budget, nothing to measure against);
     * [first]: the first day with a recorded payment; [month]: the current budget month (for no-spend days).
     * [months]: earlier budget months to look through for the best no-spend month.
     */
    fun compute(everyday: Map<LocalDate, Long>, share: Long, first: LocalDate?, today: LocalDate, month: BudgetPeriod, months: List<BudgetPeriod> = emptyList()): StreakInfo {
        fun spent(d: LocalDate) = everyday[d] ?: 0L
        val start = first ?: today
        fun counts(d: LocalDate) = !d.isBefore(start) && d.isBefore(today)
        fun within(d: LocalDate) = share > 0 && spent(d) <= share
        // Days in a row, back from yesterday
        var days = 0
        var d = today.minusDays(1)
        while (counts(d) && within(d)) { days++; d = d.minusDays(1) }
        var best = 0; var run = 0
        var x = start
        while (x.isBefore(today)) { if (within(x)) { run++; best = maxOf(best, run) } else run = 0; x = x.plusDays(1) }
        // Weeks: whole Monday-to-Sunday weeks that have finished and started on or after the first day
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun weekOk(monday: LocalDate): Boolean = share > 0 && (0L..6L).sumOf { spent(monday.plusDays(it)) } <= share * 7
        fun weekCounts(monday: LocalDate) = !monday.isBefore(start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) && monday.isBefore(thisMonday)
        var weeks = 0
        var m = thisMonday.minusWeeks(1)
        while (weekCounts(m) && !m.isBefore(start) && weekOk(m)) { weeks++; m = m.minusWeeks(1) }
        var bestWeeks = 0; var wr = 0
        var wm = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        while (wm.isBefore(thisMonday)) { if (weekOk(wm)) { wr++; bestWeeks = maxOf(bestWeeks, wr) } else wr = 0; wm = wm.plusWeeks(1) }
        val seven = (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            day to when {
                day == today -> DayMark.TODAY
                day.isBefore(start) -> DayMark.BEFORE
                spent(day) == 0L -> DayMark.NO_SPEND
                within(day) -> DayMark.WITHIN
                else -> DayMark.OVER
            }
        }
        fun noSpendIn(p: BudgetPeriod): Int = generateSequence(maxOf(p.start, start)) { it.plusDays(1) }
            .takeWhile { it.isBefore(p.endExclusive) && it.isBefore(today) }.count { spent(it) == 0L }
        val thisMonth = noSpendIn(month)
        return StreakInfo(
            days = days, bestDays = maxOf(best, days), weeks = weeks, bestWeeks = maxOf(bestWeeks, weeks),
            lastSeven = seven, todayWithin = within(today), noSpendThisMonth = thisMonth,
            bestNoSpendMonth = (months.map { noSpendIn(it) } + thisMonth).maxOrNull() ?: 0, share = share,
        )
    }

    /** Whether [d] was a no-spend day: past, on or after the first recorded payment, and nothing went out. */
    fun isNoSpend(d: LocalDate, everydayPaise: Long, first: LocalDate?, today: LocalDate): Boolean =
        first != null && !d.isBefore(first) && d.isBefore(today) && everydayPaise == 0L
}
