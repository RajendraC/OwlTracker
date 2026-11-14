package com.owl.tracker.core

/** Which way a category's spending has gone over the last months. */
enum class TrendKind { RISING, FALLING, STEADY, UP_AND_DOWN, TOO_EARLY, NONE }

/**
 * A change worth a line on Home: something new, a rise or fall over the last three months, or (for a category that
 * goes up and down) the latest month well above or below its usual.
 */
enum class MoveKind { NEW, RISING, FALLING, ABOVE, BELOW }

/** [from] and [to] index the months; [change] is the latest month less [base] (the month or usual it's compared with), in paise. */
data class Move(val kind: MoveKind, val from: Int, val to: Int, val change: Long, val base: Long)

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

    /** Smaller changes aren't news. */
    const val MIN_MOVE = 500_00L

    /**
     * How a category moved, for "What's changing": new (nothing in the earlier months, something in the latest),
     * rising or falling (as [kind], the latest month against the first of the last three), by at least [MIN_MOVE].
     * Null when it held steady, went up and down, or there isn't enough to go by.
     */
    fun move(months: List<Long?>): Move? {
        val idx = months.indices.filter { months[it] != null }
        if (idx.size < 2) return null
        val last = idx.last()
        val latest = months[last]!!
        if (latest > 0 && idx.dropLast(1).all { months[it] == 0L }) {
            return if (latest >= MIN_MOVE) Move(MoveKind.NEW, last, last, latest, 0) else null
        }
        val k = kind(months)
        if (k == TrendKind.RISING || k == TrendKind.FALLING) {
            val from = idx.takeLast(USUAL_OF).first()
            val change = latest - months[from]!!
            if (kotlin.math.abs(change) < MIN_MOVE) return null
            return Move(if (change > 0) MoveKind.RISING else MoveKind.FALLING, from, last, change, months[from]!!)
        }
        // Up and down, or steady: the latest month against the middle of the months before it (three at least), so
        // one big month earlier doesn't make an ordinary one look like a fall
        val earlier = idx.dropLast(1).map { months[it]!! }.sorted()
        if (earlier.size < USUAL_OF) return null
        val usual = if (earlier.size % 2 == 1) earlier[earlier.size / 2] else (earlier[earlier.size / 2 - 1] + earlier[earlier.size / 2]) / 2
        val change = latest - usual
        if (kotlin.math.abs(change) < MIN_MOVE || kotlin.math.abs(change) * 10 < usual * 3) return null
        return Move(if (change > 0) MoveKind.ABOVE else MoveKind.BELOW, last, last, change, usual)
    }

    /** The biggest moves first, at most [count]. */
    fun <K> biggestMoves(byKey: Map<K, List<Long?>>, count: Int = 3): List<Pair<K, Move>> =
        byKey.mapNotNull { (k, m) -> move(m)?.let { k to it } }.sortedByDescending { kotlin.math.abs(it.second.change) }.take(count)

    /** Where a slider for the month's whole budget ends: at least ₹5,00,000, or twice the usual month, stretched for a typed amount. */
    fun monthSliderMax(usual: Long, value: Long = 0): Long = maxOf(5_00_000_00L, sliderMax(usual, value))
}
