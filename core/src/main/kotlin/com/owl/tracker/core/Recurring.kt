package com.owl.tracker.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A payment seen on one day, for finding the ones that repeat. */
data class Seen(val key: String, val name: String, val date: LocalDate, val paise: Long, val categoryId: String)

/**
 * A bill that repeats: who it goes to, about how much, how often, and when the next one is due. [steady] is false
 * when the amount moves around (an electricity bill), so it's shown as "about".
 */
data class RecurringBill(
    val key: String, val name: String, val categoryId: String, val paise: Long, val everyDays: Int, val last: LocalDate,
    val next: LocalDate, val times: Int, val steady: Boolean,
) {
    val cadence: String get() = when {
        everyDays in 6..8 -> "every week"
        everyDays in 25..35 -> "every month"
        everyDays in 55..65 -> "every 2 months"
        everyDays in 85..95 -> "every 3 months"
        else -> "every $everyDays days"
    }
}

/**
 * Finds repeating payments: the same merchant three or more times (twice for EMIs, rent and subscriptions) at a
 * steady gap of about a week, a month, two months or three, with amounts within 30% of their middle value. The
 * next one is due one gap after the last (on the same day of the month for monthly bills). Bills not seen for two
 * gaps have probably stopped and are left out.
 */
object Recurring {
    private val LIKELY = setOf(Cat.EMI, Cat.RENT, Cat.SUBSCRIPTIONS, Cat.BILLS, Cat.UTILITIES, Cat.INSURANCE, Cat.RECHARGE, Cat.HOME_HELP, Cat.EDUCATION)

    fun detect(payments: List<Seen>, today: LocalDate): List<RecurringBill> = payments
        .groupBy { it.key }
        .mapNotNull { (key, list) -> billFor(key, list.sortedBy { it.date }, today) }
        .sortedBy { it.next }

    private fun billFor(key: String, all: List<Seen>, today: LocalDate): RecurringBill? {
        if (key.isBlank()) return null
        // One payment a day at most (a split or a retry on the same day is the same bill)
        val list = all.groupBy { it.date }.map { (_, l) -> l.maxBy { it.paise } }.sortedBy { it.date }
        val likely = list.last().categoryId in LIKELY
        if (list.size < (if (likely) 2 else 3)) return null
        val gaps = list.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
        val gap = gaps.sorted()[gaps.size / 2]
        val band = listOf(6..8, 25..35, 55..65, 85..95).firstOrNull { gap in it } ?: return null
        // Most gaps must fit the same band (one missed or doubled month is fine)
        if (gaps.count { it in band } * 3 < gaps.size * 2) return null
        val amounts = list.map { it.paise }.sorted()
        val mid = amounts[amounts.size / 2]
        if (mid <= 0) return null
        val recent = list.takeLast(4)
        if (recent.any { abs(it.paise - mid) * 10 > mid * 3 }) return null
        val steady = recent.all { abs(it.paise - mid) * 20 <= mid }
        val last = list.last()
        val next = if (band == 25..35) last.date.plusMonths(1) else last.date.plusDays(gap.toLong())
        // Not seen for two gaps: it has probably stopped
        if (ChronoUnit.DAYS.between(last.date, today) > gap * 2L + 5) return null
        return RecurringBill(key, last.name, last.categoryId, if (steady) last.paise else mid, gap, last.date, next, list.size, steady)
    }

    /** Bills due from [from] (inclusive) up to [days] days ahead, each at its next date (rolled forward if it's passed). */
    fun upcoming(bills: List<RecurringBill>, from: LocalDate, days: Int): List<Pair<RecurringBill, LocalDate>> = bills.mapNotNull { b ->
        var due = b.next
        while (due.isBefore(from)) due = if (b.everyDays in 25..35) due.plusMonths(1) else due.plusDays(b.everyDays.toLong())
        if (ChronoUnit.DAYS.between(from, due) <= days) b to due else null
    }.sortedBy { it.second }
}
