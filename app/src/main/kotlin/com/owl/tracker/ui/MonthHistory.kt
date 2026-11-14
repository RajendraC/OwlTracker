package com.owl.tracker.ui

import com.owl.tracker.core.BudgetPeriod
import com.owl.tracker.core.Trends
import com.owl.tracker.data.Category
import com.owl.tracker.data.Txn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Spending by category over the last [Trends.MONTHS] complete budget months, and this one so far: what the budget
 * sliders start from and show as dots, and what Reports' trend by category draws.
 *
 * A month before the records begin (the SMS history doesn't reach it) is null, never ₹0: "no data" is not "spent
 * nothing". A month counts once the records begin no later than its fourth day.
 */
data class MonthHistory(
    /** Complete budget months, oldest first. */
    val months: List<BudgetPeriod>,
    /** "May", "Jun"…: the month most of each budget month falls in. */
    val labels: List<String>,
    val current: BudgetPeriod,
    /** Per category, one amount per month (null before the records begin). Categories with nothing are left out. */
    val byCategory: Map<String, List<Long?>>,
    /** This budget month so far, per category. */
    val now: Map<String, Long>,
    /** Money in (salary, refunds…) per month, the same way. */
    val moneyIn: List<Long?>,
    /** Index of the first month with data, or [months].size when none has any. */
    val firstKnown: Int,
) {
    val hasData: Boolean get() = byCategory.values.any { m -> m.any { (it ?: 0L) > 0 } }

    /** A category's months; one with nothing spent is ₹0 in every month that has data. */
    fun of(categoryId: String): List<Long?> = byCategory[categoryId] ?: months.indices.map { if (it >= firstKnown) 0L else null }

    fun usual(categoryId: String): Long = Trends.usualMonth(of(categoryId))

    /** Money in, usual month; 0 when none came in. */
    val usualMoneyIn: Long get() = Trends.usualMonth(moneyIn)

    /** Months with data. */
    val knownCount: Int get() = months.size - firstKnown

    /** All spending, month by month (null before the records begin): what the month budget's slider shows as dots. */
    val totals: List<Long?> get() = months.indices.map { i -> if (i < firstKnown) null else byCategory.values.sumOf { it[i] ?: 0L } }

    companion object {
        val EMPTY = MonthHistory(emptyList(), emptyList(), BudgetPeriod(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 2, 1)), emptyMap(), emptyMap(), emptyList(), 0)

        fun label(p: BudgetPeriod): String = p.start.plusDays(14).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

        fun build(txns: List<Txn>, cats: List<Category>, months: List<BudgetPeriod>, current: BudgetPeriod, zone: ZoneId): MonthHistory {
            val catMap = cats.associateBy { it.id }
            fun day(t: Txn) = Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate()
            val first = txns.minOfOrNull { it.occurredAt }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val firstKnown = if (first == null) months.size else months.indexOfFirst { !first.isAfter(it.start.plusDays(3)) }.let { if (it < 0) months.size else it }

            val spent = HashMap<String, LongArray>()
            val now = HashMap<String, Long>()
            val moneyIn = LongArray(months.size)
            for (t in txns) {
                val d = day(t)
                val i = months.indexOfFirst { it.contains(d) }
                val isNow = current.contains(d)
                if (i < 0 && !isNow) continue
                if (t.isIncome(catMap[t.categoryId]) && i >= 0) moneyIn[i] += t.amountPaise
                for (p in t.portions()) {
                    if (!p.isSpending(catMap[p.categoryId])) continue
                    if (isNow) now[p.categoryId] = (now[p.categoryId] ?: 0L) + p.amountPaise
                    else spent.getOrPut(p.categoryId) { LongArray(months.size) }[i] += p.amountPaise
                }
            }
            fun known(a: LongArray): List<Long?> = a.mapIndexed { i, v -> if (i >= firstKnown) v else null }
            return MonthHistory(
                months = months, labels = months.map(::label), current = current,
                byCategory = spent.mapValues { known(it.value) }, now = now, moneyIn = known(moneyIn), firstKnown = firstKnown,
            )
        }
    }
}
