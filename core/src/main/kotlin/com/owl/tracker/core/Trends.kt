package com.owl.tracker.core

/** Which way a category's spending has gone over the last months. */
enum class TrendKind { RISING, FALLING, STEADY, UP_AND_DOWN, TOO_EARLY, NONE }

/**
 * One category's spending month by month, for the budget sliders and the trend report. [months] are complete budget
 * months, oldest first, in paise; null for a month before the records begin (the SMS history doesn't reach it), so
 * "no data" is never read as "spent nothing".
 */
object Trends {
    /** Months looked back over. */
    const val MONTHS = 6

    /** Months the usual month is the average of. */
    const val USUAL_OF = 3

    private const val HUNDRED_RUPEES = 100_00L

    /** The months that have data, oldest first. */
    fun known(months: List<Long?>): List<Long> = months.filterNotNull()

    /**
     * The usual month: the average of the last [USUAL_OF] months with data, rounded UP to the next ₹100, so a budget
     * set to it covers a month like those (an EMI of ₹40,328 gives ₹40,400). 0 when there is no data.
     */
    fun usualMonth(months: List<Long?>): Long {
        val last = known(months).takeLast(USUAL_OF)
        if (last.isEmpty()) return 0
        val avg = last.sum().toDouble() / last.size
        return kotlin.math.ceil(avg / HUNDRED_RUPEES).toLong() * HUNDRED_RUPEES
    }

    /**
     * Rising or falling: the latest month against the one two months before it, beyond 15% either way. Up and down:
     * a month with nothing next to months with something, or the biggest month more than 2.5 times the smallest.
     * Too early: fewer than two months with data. None: nothing spent at all.
     */
    fun kind(months: List<Long?>): TrendKind {
        val m = known(months)
        if (m.isEmpty() || m.all { it == 0L }) return TrendKind.NONE
        if (m.size < 2) return TrendKind.TOO_EARLY
        val hi = m.max()
        val lo = m.min()
        if (lo == 0L || hi > lo * 5 / 2) return TrendKind.UP_AND_DOWN
        val last3 = m.takeLast(USUAL_OF)
        val first = last3.first()
        val latest = last3.last()
        return when {
            latest * 100 > first * 115 -> TrendKind.RISING
            latest * 100 < first * 85 -> TrendKind.FALLING
            else -> TrendKind.STEADY
        }
    }

    /** In how many of the months with data [budget] would have been enough (spent no more than it). */
    fun enoughIn(months: List<Long?>, budget: Long): Int = known(months).count { it <= budget }

    /**
     * Where a slider for this category should end: twice the usual month rounded up to ₹1,000, at least ₹5,000, and
     * never short of [value] (then 25% above it, rounded up to ₹1,000).
     */
    fun sliderMax(usual: Long, value: Long = 0): Long {
        val thousand = 1_000_00L
        fun up(p: Long) = (p + thousand - 1) / thousand * thousand
        val base = maxOf(5_000_00L, up(usual * 2))
        return if (value > base) up(value * 5 / 4) else base
    }
}
