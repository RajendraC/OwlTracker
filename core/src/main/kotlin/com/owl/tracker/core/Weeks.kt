package com.owl.tracker.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One payment that counts as spending, reduced to what the week view and the calendar need. */
data class DaySpend(val date: LocalDate, val paise: Long, val fixed: Boolean)

/**
 * How a day's everyday spending compares with the day's share of the month's budget: up to half of it is light, up to
 * all of it is within budget, up to twice it is heavy, more is very heavy. Big fixed payments (EMIs, rent) are left
 * out, so the day an EMI goes out isn't painted red every month.
 */
enum class DayLevel { FUTURE, NONE, LIGHT, WITHIN, HEAVY, VERY_HEAVY }

data class DayTotal(val date: LocalDate, val paise: Long, val fixedPaise: Long, val level: DayLevel) {
    val everydayPaise: Long get() = paise - fixedPaise
}

/** One week of a budget month in the calendar: Sunday to Saturday, null for days outside the month. */
data class CalendarRow(val weekStart: LocalDate, val days: List<DayTotal?>, val paise: Long)

/**
 * A week's everyday spending against the week before. While a week is still going, only as many days of the week
 * before are counted as have passed (Sunday to Friday against Sunday to Friday).
 */
data class WeekCompare(val nowPaise: Long, val beforePaise: Long, val days: Int) {
    /** Percent more (positive) or less (negative) than before; null when nothing was spent before. */
    val percent: Int? get() = if (beforePaise <= 0) null else Math.round((nowPaise - beforePaise) * 100.0 / beforePaise).toInt()
}

object Weeks {
    /** Weeks run Sunday to Saturday, like the calendar on most Indian phones. */
    val FIRST_DAY: DayOfWeek = DayOfWeek.SUNDAY

    fun startOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(FIRST_DAY))

    fun daysOf(start: LocalDate): List<LocalDate> = (0L until 7L).map { start.plusDays(it) }

    /** The day's share of a month's budget: the budget spread evenly over the budget month's days. */
    fun dailyShare(budgetPaise: Long, periodDays: Int): Long = if (budgetPaise <= 0 || periodDays <= 0) 0L else budgetPaise / periodDays

    fun level(everydayPaise: Long, sharePaise: Long): DayLevel = when {
        everydayPaise <= 0 -> DayLevel.NONE
        sharePaise <= 0 -> DayLevel.WITHIN
        everydayPaise * 2 <= sharePaise -> DayLevel.LIGHT
        everydayPaise <= sharePaise -> DayLevel.WITHIN
        everydayPaise <= sharePaise * 2 -> DayLevel.HEAVY
        else -> DayLevel.VERY_HEAVY
    }

    /** Each day's spending and level. Days after [today] are [DayLevel.FUTURE]. [share] is a day's share of the budget. */
    fun totals(days: List<LocalDate>, spends: List<DaySpend>, today: LocalDate, share: (LocalDate) -> Long): List<DayTotal> {
        val byDay = spends.groupBy { it.date }
        return days.map { d ->
            val list = byDay[d].orEmpty()
            val paise = list.sumOf { it.paise }
            val fixed = list.filter { it.fixed }.sumOf { it.paise }
            DayTotal(d, paise, fixed, if (d.isAfter(today)) DayLevel.FUTURE else level(paise - fixed, share(d)))
        }
    }

    /** The week starting [weekStart] against the week before it, everyday spending only. Null for a week yet to come. */
    fun compare(weekStart: LocalDate, spends: List<DaySpend>, today: LocalDate): WeekCompare? {
        if (weekStart.isAfter(today)) return null
        val passed = (ChronoUnit.DAYS.between(weekStart, today) + 1).coerceIn(1, 7)
        fun everyday(from: LocalDate) = spends.filter { !it.fixed && !it.date.isBefore(from) && it.date.isBefore(from.plusDays(passed)) }.sumOf { it.paise }
        return WeekCompare(everyday(weekStart), everyday(weekStart.minusDays(7)), passed.toInt())
    }

    /**
     * A budget month laid out in weeks, Sunday to Saturday. Days outside [start, endExclusive) are null, and each
     * row's total is what was spent inside the month that week, so the rows add up to the month.
     */
    fun calendar(start: LocalDate, endExclusive: LocalDate, spends: List<DaySpend>, today: LocalDate, share: (LocalDate) -> Long): List<CalendarRow> {
        val rows = mutableListOf<CalendarRow>()
        var weekStart = startOf(start)
        while (weekStart.isBefore(endExclusive)) {
            val days = daysOf(weekStart)
            val inside = days.filter { !it.isBefore(start) && it.isBefore(endExclusive) }
            val totals = totals(inside, spends, today, share).associateBy { it.date }
            rows += CalendarRow(weekStart, days.map { totals[it] }, totals.values.sumOf { it.paise })
            weekStart = weekStart.plusDays(7)
        }
        return rows
    }

    /**
     * With no budget set: what a usual day costs, from the everyday spending between [from] and [untilExclusive].
     * Zero when nothing was spent.
     */
    fun usualDay(spends: List<DaySpend>, from: LocalDate, untilExclusive: LocalDate): Long {
        val days = ChronoUnit.DAYS.between(from, untilExclusive)
        if (days <= 0) return 0
        return spends.filter { !it.fixed && !it.date.isBefore(from) && it.date.isBefore(untilExclusive) }.sumOf { it.paise } / days
    }

    /** "8–14 Nov", "27 Sep – 3 Oct", "29 Dec 2026 – 4 Jan 2027". */
    fun label(start: LocalDate): String {
        val end = start.plusDays(6)
        val mon = { d: LocalDate -> d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) }
        return when {
            start.year != end.year -> "${start.dayOfMonth} ${mon(start)} ${start.year} – ${end.dayOfMonth} ${mon(end)} ${end.year}"
            start.month != end.month -> "${start.dayOfMonth} ${mon(start)} – ${end.dayOfMonth} ${mon(end)}"
            else -> "${start.dayOfMonth}–${end.dayOfMonth} ${mon(end)}"
        }
    }
}
