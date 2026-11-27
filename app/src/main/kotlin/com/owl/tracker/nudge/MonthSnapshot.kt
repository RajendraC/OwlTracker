package com.owl.tracker.nudge

import com.owl.tracker.core.BudgetMath
import com.owl.tracker.core.BudgetPeriod
import com.owl.tracker.core.Weeks
import com.owl.tracker.data.Category
import com.owl.tracker.data.Repository
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Txn
import com.owl.tracker.ui.FIXED_CATEGORIES
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The month as the owl's nudges and the home-screen widget see it, worked out on the phone from the database: what
 * went out, against the budget and today's share, and which category budgets are over. The same rules as Home
 * (splits count as their parts; EMIs and rent are left out of the daily share).
 */
data class MonthSnapshot(
    val period: BudgetPeriod,
    val today: LocalDate,
    val spent: Long,
    val overall: Long,
    val todaySpent: Long,
    /** Today's share of the budget after EMIs and rent (0 with no budget). */
    val dayShare: Long,
    val weekSpent: Long,
    val lastWeekSpent: Long,
    /** Category budgets that are over: name, spent, limit. */
    val overCategories: List<Triple<String, Long, Long>>,
    val biggestCategory: String?,
    val appLock: Boolean,
) {
    val left: Long get() = overall - spent
    val used: Double get() = if (overall > 0) spent.toDouble() / overall else 0.0
    val dayNumber: Int get() = period.dayNumber(today)
    val daysLeft: Int get() = period.days - dayNumber

    /** What's safe a day for the rest of the month, today included. */
    val perDayLeft: Long get() = if (left > 0) left / (daysLeft + 1).coerceAtLeast(1) else 0

    companion object {
        suspend fun load(repo: Repository, clock: Clock, at: LocalDate = LocalDate.now(clock)): MonthSnapshot {
            val zone = clock.zone
            val settings = repo.settings()
            val period = BudgetMath.periodFor(at, settings.budgetStartDay)
            val weekStart = at.minusDays(((at.dayOfWeek.value - 1) % 7).toLong())   // Monday
            val from = minOf(period.start, weekStart.minusDays(7))
            val txns = repo.transactions(ms(from, zone), ms(maxOf(period.endExclusive, at.plusDays(1)), zone))
            val cats = repo.categories()
            val budgets = repo.budgets()
            return compute(txns, cats, budgets, period, at, zone, settings.appLock)
        }

        fun compute(
            txns: List<Txn>, cats: List<Category>, budgets: Map<String, Long>, period: BudgetPeriod, at: LocalDate, zone: ZoneId, appLock: Boolean,
        ): MonthSnapshot {
            val catMap = cats.associateBy { it.id }
            val counted = txns.flatMap { it.portions() }.filter { it.isSpending(catMap[it.categoryId]) }
            fun day(t: Txn) = Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate()
            val inMonth = counted.filter { val d = day(it); !d.isBefore(period.start) && d.isBefore(period.endExclusive) }
            val spent = inMonth.sumOf { it.amountPaise }
            val overall = budgets[Schema.OVERALL] ?: 0L
            val fixedSpent = inMonth.filter { it.categoryId in FIXED_CATEGORIES }.sumOf { it.amountPaise }
            val everyday = Weeks.everydayBudget(overall, FIXED_CATEGORIES.sumOf { budgets[it] ?: 0L }, fixedSpent)
            val share = Weeks.dailyShare(everyday, period.days)
            val todaySpent = counted.filter { day(it) == at && it.categoryId !in FIXED_CATEGORIES }.sumOf { it.amountPaise }
            val weekStart = at.minusDays((at.dayOfWeek.value - 1).toLong())
            val week = counted.filter { val d = day(it); !d.isBefore(weekStart) && !d.isAfter(at) }.sumOf { it.amountPaise }
            val lastWeek = counted.filter { val d = day(it); !d.isBefore(weekStart.minusDays(7)) && d.isBefore(weekStart) }.sumOf { it.amountPaise }
            // By top-level category, the way budgets are set
            val tree = com.owl.tracker.data.CategoryTree(cats)
            val byTop = inMonth.groupBy { tree.top(it.categoryId) }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
            val over = budgets.filterKeys { it != Schema.OVERALL }.mapNotNull { (id, limit) ->
                val sp = byTop[id] ?: 0L
                val name = catMap[id]?.name ?: return@mapNotNull null
                if (limit > 0 && sp > limit) Triple(name, sp, limit) else null
            }.sortedByDescending { it.second - it.third }
            val biggest = byTop.maxByOrNull { it.value }?.key?.let { catMap[it]?.name }
            return MonthSnapshot(period, at, spent, overall, todaySpent, share, week, lastWeek, over, biggest, appLock)
        }

        private fun ms(d: LocalDate, zone: ZoneId) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
