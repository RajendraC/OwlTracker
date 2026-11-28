package com.owl.tracker.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A salary credit: when it came in and how much. */
data class SalarySeen(val date: LocalDate, val paise: Long)

/**
 * Salary day: the latest salary just in, the day of the month it usually comes, whether the budget month should
 * start then, and a round amount to set aside.
 */
object Salary {
    /** The latest salary that came in within [withinDays] days of [today] (0 = today only). */
    fun latest(seen: List<SalarySeen>, today: LocalDate, withinDays: Int = 3): SalarySeen? =
        seen.filter { !it.date.isAfter(today) && ChronoUnit.DAYS.between(it.date, today) <= withinDays }.maxByOrNull { it.date }

    /**
     * The day of the month salary usually comes: the last two (or three) monthly salaries landing within two days of
     * each other. Null with fewer, or when they wander.
     */
    fun usualDay(seen: List<SalarySeen>): Int? {
        // one per month: the credit nearest the latest salary's day (a bonus or a second credit counts once)
        val latestDay = seen.maxByOrNull { it.date }?.date?.dayOfMonth ?: return null
        val monthly = seen.groupBy { it.date.withDayOfMonth(1) }.values
            .map { m -> m.minWith(compareBy<SalarySeen> { kotlin.math.abs(it.date.dayOfMonth - latestDay) }.thenByDescending { it.paise }) }
            .sortedBy { it.date }.takeLast(3)
        if (monthly.size < 2) return null
        val last = monthly.takeLast(2)
        if (ChronoUnit.MONTHS.between(last[0].date.withDayOfMonth(1), last[1].date.withDayOfMonth(1)) != 1L) return null
        val days = monthly.map { it.date.dayOfMonth.coerceAtMost(28) }
        if (days.max() - days.min() > 2) return null
        return days.last()
    }

    /**
     * The start day to suggest for budget months: the usual salary day, when budget months start more than two days
     * away from it (a salary on the 31st or 1st already fits a month from the 1st).
     */
    fun suggestStartDay(seen: List<SalarySeen>, currentStart: Int): Int? {
        val day = usualDay(seen) ?: return null
        val near = { a: Int, b: Int -> minOf(Math.floorMod(a - b, 28), Math.floorMod(b - a, 28)) <= 2 }
        if (near(day, currentStart)) return null
        if (currentStart == 1 && day >= 27) return null
        return day
    }

    /** A tenth of the salary, rounded down to ₹500 (at least ₹500). */
    fun setAside(paise: Long): Long = maxOf(500_00L, paise / 10 / 500_00L * 500_00L)
}

/** When to show Play's rating prompt: only after a happy moment, to someone who has used the app a while, rarely. */
object ReviewAsk {
    const val MIN_DAYS = 7
    const val MIN_PAYMENTS = 30
    const val GAP_DAYS = 120

    fun shouldAsk(firstUseMs: Long, nowMs: Long, payments: Int, lastAskMs: Long?, sample: Boolean): Boolean {
        if (sample || payments < MIN_PAYMENTS) return false
        val day = 86_400_000L
        if (nowMs - firstUseMs < MIN_DAYS * day) return false
        return lastAskMs == null || nowMs - lastAskMs >= GAP_DAYS * day
    }
}
