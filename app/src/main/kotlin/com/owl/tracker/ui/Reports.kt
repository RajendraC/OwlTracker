package com.owl.tracker.ui

import com.owl.tracker.core.BudgetPeriod
import com.owl.tracker.core.Direction
import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import com.owl.tracker.data.CategoryKind
import com.owl.tracker.data.Txn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** How far back the Reports screen looks, in budget months. */
enum class ReportRange(val months: Int, val label: String) {
    ONE(1, "This month"), THREE(3, "3 months"), SIX(6, "6 months"), TWELVE(12, "12 months");

    /** How the previous period of the same length is described: "last month", "the previous 3 months". */
    val previousText: String get() = if (months == 1) "last month" else "the previous $months months"
}

/**
 * What the person tapped. Every section narrows to it, except the section that set it, which stays
 * whole so another choice can be made there (tap Food in the donut and the donut still shows all
 * categories, with Food picked out).
 */
data class ReportFilter(
    val bucket: Int? = null,
    val categoryId: String? = null,
    val merchantKey: String? = null,
    val weekday: DayOfWeek? = null,
    /** A day inside the month picked with [bucket], when the range is longer than a month (drilling down). */
    val day: Int? = null,
) {
    val isEmpty: Boolean get() = bucket == null && categoryId == null && merchantKey == null && weekday == null && day == null
}

enum class FilterKind { BUCKET, CATEGORY, MERCHANT, WEEKDAY, DAY }

/** A month of a longer range, drilled into: one bar per day, with every filter but the day applied. */
data class Drill(val title: String, val bars: List<TrendBar>, val budgetLine: Long)

data class TrendBar(
    val label: String,
    val title: String,
    val spent: Long,
    val received: Long,
    val hasData: Boolean,
    val isNow: Boolean,
)

data class CategorySlice(val category: Category, val paise: Long, val share: Float, val previous: Long)
data class MerchantRow(val key: String, val name: String, val pkg: String?, val categoryId: String, val paise: Long, val count: Int)
data class WeekdayBar(val day: DayOfWeek, val paise: Long, val count: Int)

/** One line of Income & expenses: a category's income, or its expenses. */
data class IeLine(val category: Category?, val paise: Long, val count: Int)

/** One budget month of Income & expenses. */
data class IeMonth(val label: String, val title: String, val moneyIn: Long, val spent: Long) {
    val net: Long get() = moneyIn - spent
}

/**
 * Income & expenses for the range (or the day or month picked on the trend): what came in, what was spent
 * and what's left. Income is salary and other money in, refunds and money back; expenses are money out in expense
 * categories. Savings, investments and transfers between your own accounts are neither, so they're
 * shown apart, as are payments the user left out.
 */
data class IncomeExpenses(
    val title: String,
    /** Income categories first, then refunds and money back by category; biggest first within each. */
    val income: List<IeLine>,
    val moneyIn: Long,
    val spending: List<IeLine>,
    val spent: Long,
    /** Share of money in that was kept; null with no money in. Negative when more went out. */
    val keptPct: Int?,
    val savingsOut: Long,
    val savingsIn: Long,
    val transfersOut: Long,
    val transfersIn: Long,
    val leftOut: Int,
    /** One row per budget month, oldest first, for ranges longer than a month. */
    val months: List<IeMonth>,
) {
    val net: Long get() = moneyIn - spent
}

data class Report(
    val range: ReportRange,
    val title: String,
    val filter: ReportFilter,
    /** One chip per active filter, to show and clear it. */
    val chips: List<Pair<FilterKind, String>>,
    val spent: Long,
    /** Money in: income, refunds and money back (see [Txn.isIncome]). */
    val received: Long,
    /** Spending in the previous period of the same length (same category/merchant/weekday filters); null when there's no data for it. */
    val previousSpent: Long?,
    val changePct: Int?,
    val perDay: Long,
    val payments: Int,
    /** One bar per day (This month) or per budget month. */
    val daily: Boolean,
    val trend: List<TrendBar>,
    /** The budget for one bar, when no category or merchant is picked. */
    val budgetLine: Long,
    val categories: List<CategorySlice>,
    val merchants: List<MerchantRow>,
    val weekdays: List<WeekdayBar>,
    val highlights: List<String>,
    /** The payments matching the filters, newest first (or the biggest payments when nothing is picked). */
    val matches: List<Txn>,
    val matchCount: Int,
    /** Null while a category, merchant or weekday is picked: Income & expenses is about everything. */
    val incomeExpenses: IncomeExpenses?,
    /** The month picked on a longer range's trend, day by day; null otherwise. */
    val drill: Drill? = null,
)

/** Builds the Reports screen from the transactions of the range and the period before it. Pure, so it's tested on the JVM. */
object ReportBuilder {

    const val MAX_MERCHANTS = 8
    const val MAX_MATCHES = 60

    fun build(
        txns: List<Txn>,
        cats: List<Category>,
        periods: List<BudgetPeriod>,
        previous: List<BudgetPeriod>,
        monthlyBudget: Long,
        range: ReportRange,
        filter: ReportFilter,
        today: LocalDate,
        zone: ZoneId,
    ): Report {
        val catMap = cats.associateBy { it.id }
        val dates = HashMap<Long, LocalDate>()
        fun dateOf(t: Txn): LocalDate = dates.getOrPut(t.id) { Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate() }
        // Only money out counts as spending; money in (even a refund) never lowers it.
        fun counts(t: Txn) = t.isSpending(catMap[t.categoryId])
        fun spendOf(list: List<Txn>) = list.filter { counts(it) }.sumOf { it.amountPaise }
        fun receivedOf(list: List<Txn>) = list.filter { it.isIncome(catMap[it.categoryId]) }.sumOf { it.amountPaise }

        val from = periods.first().start
        val to = periods.last().endExclusive
        val inRange = txns.filter { val d = dateOf(it); !d.isBefore(from) && d.isBefore(to) }
        val prevFrom = previous.firstOrNull()?.start
        val prevTo = previous.lastOrNull()?.endExclusive
        val inPrevious = if (prevFrom == null || prevTo == null) emptyList() else txns.filter { val d = dateOf(it); !d.isBefore(prevFrom) && d.isBefore(prevTo) }

        // Buckets: days of the month for "This month", otherwise budget months.
        val daily = range == ReportRange.ONE
        data class Bucket(val start: LocalDate, val end: LocalDate, val label: String, val title: String)
        val dayTitle = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
        val buckets: List<Bucket> = if (daily) {
            val p = periods.first()
            (0 until p.days).map { i ->
                val d = p.start.plusDays(i.toLong())
                Bucket(d, d.plusDays(1), d.dayOfMonth.toString(), d.format(dayTitle))
            }
        } else {
            periods.map { p -> Bucket(p.start, p.endExclusive, p.start.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)), periodTitle(p)) }
        }
        val bucket = filter.bucket?.takeIf { it in buckets.indices }
        // Drilling down: a month of a longer range splits into its days, and one day can be picked
        val drillDays: List<Bucket> = if (daily || bucket == null) emptyList() else {
            val m = buckets[bucket]
            generateSequence(m.start) { it.plusDays(1) }.takeWhile { it.isBefore(m.end) }
                .map { d -> Bucket(d, d.plusDays(1), d.dayOfMonth.toString(), d.format(dayTitle)) }.toList()
        }
        val day = filter.day?.takeIf { it in drillDays.indices }
        val f = filter.copy(bucket = bucket, day = day)

        fun inBucket(t: Txn, b: Int?) = b == null || dateOf(t).let { d -> !d.isBefore(buckets[b].start) && d.isBefore(buckets[b].end) }
        fun inDay(t: Txn, i: Int) = dateOf(t).let { d -> !d.isBefore(drillDays[i].start) && d.isBefore(drillDays[i].end) }
        /** The picked month (or day, or nothing): what every section but the trend is about. */
        fun inScope(t: Txn) = if (day != null) inDay(t, day) else inBucket(t, bucket)
        fun byCategory(t: Txn) = f.categoryId == null || t.categoryId == f.categoryId
        fun byMerchant(t: Txn) = f.merchantKey == null || t.merchantKey == f.merchantKey
        fun byWeekday(t: Txn) = f.weekday == null || dateOf(t).dayOfWeek == f.weekday

        val matching = inRange.filter { inScope(it) && byCategory(it) && byMerchant(it) && byWeekday(it) }
        val spent = spendOf(matching)
        val received = receivedOf(matching)

        val prevMatching = inPrevious.filter { byCategory(it) && byMerchant(it) && byWeekday(it) }
        val previousSpent = if (inPrevious.isEmpty()) null else spendOf(prevMatching)
        val changePct = if (bucket == null && previousSpent != null && previousSpent > 0) {
            (((spent - previousSpent) * 100.0) / previousSpent).let { kotlin.math.round(it).toInt() }
        } else null

        val days = if (day != null) 1 else if (bucket != null) {
            java.time.temporal.ChronoUnit.DAYS.between(buckets[bucket].start, buckets[bucket].end).toInt()
        } else {
            val end = if (today.isBefore(to)) today.plusDays(1) else to
            java.time.temporal.ChronoUnit.DAYS.between(from, end).toInt()
        }.coerceAtLeast(1)

        // Trend: every bucket, with all filters except the bucket itself.
        val forTrend = inRange.filter { byCategory(it) && byMerchant(it) && byWeekday(it) }
        val trend = buckets.mapIndexed { i, b ->
            val inB = forTrend.filter { inBucket(it, i) }
            val isNow = !today.isBefore(b.start) && today.isBefore(b.end)
            TrendBar(
                label = b.label,
                title = b.title,
                spent = spendOf(inB),
                received = receivedOf(inB),
                hasData = (inB.isNotEmpty() || isNow) && !b.start.isAfter(today),
                isNow = isNow,
            )
        }
        val drill = if (drillDays.isEmpty()) null else Drill(
            title = buckets[bucket!!].title,
            bars = drillDays.mapIndexed { i, d ->
                val inD = forTrend.filter { inDay(it, i) }
                TrendBar(d.label, d.title, spendOf(inD), receivedOf(inD), hasData = !d.start.isAfter(today), isNow = d.start == today)
            },
            budgetLine = if (monthlyBudget <= 0 || f.categoryId != null || f.merchantKey != null || f.weekday != null) 0L else monthlyBudget / drillDays.size,
        )
        val budgetLine = when {
            monthlyBudget <= 0 || f.categoryId != null || f.merchantKey != null || f.weekday != null -> 0L
            daily -> monthlyBudget / periods.first().days.coerceAtLeast(1)
            else -> monthlyBudget
        }

        // Categories: all filters except the category.
        val forCats = inRange.filter { inScope(it) && byMerchant(it) && byWeekday(it) && counts(it) }
        val prevForCats = inPrevious.filter { byMerchant(it) && byWeekday(it) && counts(it) }
        val prevByCat = prevForCats.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        val catTotals = forCats.groupBy { it.categoryId }.mapNotNull { (id, list) ->
            val cat = catMap[id] ?: return@mapNotNull null
            val paise = list.sumOf { it.amountPaise }
            if (paise <= 0) null else cat to paise
        }
        val catSum = catTotals.sumOf { it.second }.coerceAtLeast(1)
        val categories = catTotals.sortedByDescending { it.second }.map { (cat, paise) ->
            CategorySlice(cat, paise, paise.toFloat() / catSum, prevByCat[cat.id] ?: 0L)
        }

        // Merchants: all filters except the merchant; spending only.
        val forMerchants = inRange.filter { inScope(it) && byCategory(it) && byWeekday(it) && counts(it) && it.direction == Direction.DEBIT }
        val merchants = forMerchants.groupBy { it.merchantKey }.map { (key, list) ->
            val latest = list.maxBy { it.occurredAt }
            MerchantRow(key, latest.merchantName, list.firstNotNullOfOrNull { it.merchantPackage }, latest.categoryId, list.sumOf { it.amountPaise }, list.size)
        }.sortedByDescending { it.paise }
        val topMerchants = merchants.take(MAX_MERCHANTS).let { top ->
            // A picked merchant stays visible even if it isn't in the top few.
            val picked = f.merchantKey?.let { k -> merchants.firstOrNull { it.key == k } }
            if (picked != null && top.none { it.key == picked.key }) top + picked else top
        }

        // Weekdays: all filters except the weekday; spending only.
        val forWeek = inRange.filter { inScope(it) && byCategory(it) && byMerchant(it) && counts(it) && it.direction == Direction.DEBIT }
        val byDay = forWeek.groupBy { dateOf(it).dayOfWeek }
        val weekdays = DayOfWeek.values().map { d -> WeekdayBar(d, byDay[d].orEmpty().sumOf { it.amountPaise }, byDay[d].orEmpty().size) }

        val matches = if (f.isEmpty) {
            inRange.filter { counts(it) && it.direction == Direction.DEBIT }.sortedByDescending { it.amountPaise }.take(5)
        } else {
            matching.sortedWith(compareByDescending<Txn> { it.occurredAt }.thenByDescending { it.id }).take(MAX_MATCHES)
        }

        val chips = buildList {
            bucket?.let { add(FilterKind.BUCKET to buckets[it].title) }
            day?.let { add(FilterKind.DAY to drillDays[it].title) }
            f.categoryId?.let { add(FilterKind.CATEGORY to (catMap[it]?.name ?: "Category")) }
            f.merchantKey?.let { k -> add(FilterKind.MERCHANT to (merchants.firstOrNull { it.key == k }?.name ?: inRange.firstOrNull { it.merchantKey == k }?.merchantName ?: "Merchant")) }
            f.weekday?.let { add(FilterKind.WEEKDAY to plural(it)) }
        }

        val highlights = if (f.isEmpty) highlights(range, spent, previousSpent, changePct, categories, merchants, weekdays, trend, daily, monthlyBudget, inRange.filter { counts(it) && it.direction == Direction.DEBIT }, ::dateOf, days, periods.first().days) else emptyList()

        return Report(
            range = range,
            title = if (daily) periodTitle(periods.first()) else "${periods.first().start.format(MONTH_YEAR)} – ${periods.last().start.format(MONTH_YEAR)}",
            filter = f,
            chips = chips,
            spent = spent,
            received = received,
            previousSpent = previousSpent,
            changePct = changePct,
            perDay = spent / days,
            payments = matching.count { counts(it) && it.direction == Direction.DEBIT },
            daily = daily,
            trend = trend,
            budgetLine = budgetLine,
            categories = categories,
            merchants = topMerchants,
            weekdays = weekdays,
            highlights = highlights,
            matches = matches,
            matchCount = if (f.isEmpty) matches.size else matching.size,
            drill = drill,
            incomeExpenses = if (f.categoryId != null || f.merchantKey != null || f.weekday != null) null else incomeExpenses(
                title = day?.let { drillDays[it].title } ?: bucket?.let { buckets[it].title } ?: if (daily) periodTitle(periods.first()) else
                    "${periods.first().start.format(MONTH_YEAR)} – ${periods.last().start.format(MONTH_YEAR)}",
                txns = inRange.filter { inScope(it) },
                catMap = catMap,
                months = if (daily || bucket != null) emptyList() else buckets.map { b ->
                    val inB = inRange.filter { val d = dateOf(it); !d.isBefore(b.start) && d.isBefore(b.end) }
                    IeMonth(b.label, b.title, receivedOf(inB), spendOf(inB))
                },
            ),
        )
    }

    /** See [IncomeExpenses]. */
    fun incomeExpenses(title: String, txns: List<Txn>, catMap: Map<String, Category>, months: List<IeMonth>): IncomeExpenses {
        fun lines(list: List<Txn>) = list.groupBy { it.categoryId }.map { (id, l) -> IeLine(catMap[id], l.sumOf { it.amountPaise }, l.size) }
        val incoming = txns.filter { it.isIncome(catMap[it.categoryId]) }
        val spending = txns.filter { it.isSpending(catMap[it.categoryId]) }
        val moneyIn = incoming.sumOf { it.amountPaise }
        val spent = spending.sumOf { it.amountPaise }
        fun kindSum(kind: CategoryKind, dir: Direction) =
            txns.filter { !it.excluded && it.direction == dir && catMap[it.categoryId]?.kind == kind }.sumOf { it.amountPaise }
        return IncomeExpenses(
            title = title,
            income = lines(incoming).sortedWith(compareBy<IeLine> { if (it.category?.kind == CategoryKind.INCOME) 0 else 1 }.thenByDescending { it.paise }),
            moneyIn = moneyIn,
            spending = lines(spending).sortedByDescending { it.paise },
            spent = spent,
            keptPct = if (moneyIn > 0) kotlin.math.round((moneyIn - spent) * 100.0 / moneyIn).toInt() else null,
            savingsOut = kindSum(CategoryKind.SAVING, Direction.DEBIT),
            savingsIn = kindSum(CategoryKind.SAVING, Direction.CREDIT),
            transfersOut = kindSum(CategoryKind.TRANSFER, Direction.DEBIT),
            transfersIn = kindSum(CategoryKind.TRANSFER, Direction.CREDIT),
            leftOut = txns.count { it.excluded },
            months = months,
        )
    }

    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    fun periodTitle(p: BudgetPeriod): String =
        if (p.start.dayOfMonth == 1) p.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        else p.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " – " +
            p.endExclusive.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))

    fun plural(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s"

    private fun highlights(
        range: ReportRange,
        spent: Long,
        previousSpent: Long?,
        changePct: Int?,
        categories: List<CategorySlice>,
        merchants: List<MerchantRow>,
        weekdays: List<WeekdayBar>,
        trend: List<TrendBar>,
        daily: Boolean,
        monthlyBudget: Long,
        spending: List<Txn>,
        dateOf: (Txn) -> LocalDate,
        daysSoFar: Int,
        daysInMonth: Int,
    ): List<String> = buildList {
        if (spent <= 0) return@buildList
        categories.firstOrNull()?.let {
            add("${it.category.name} was your biggest spend: ${(it.share * 100).toInt()}% (${Money.formatInr(it.paise)}).")
        }
        if (changePct != null && previousSpent != null) {
            add(
                when {
                    changePct > 2 -> "You spent $changePct% more than ${range.previousText} (${Money.formatInr(previousSpent)})."
                    changePct < -2 -> "You spent ${-changePct}% less than ${range.previousText} (${Money.formatInr(previousSpent)})."
                    else -> "You spent about the same as ${range.previousText}."
                },
            )
        }
        weekdays.maxByOrNull { it.paise }?.takeIf { it.paise > 0 && weekdays.count { w -> w.paise > 0 } > 1 }?.let {
            add("You spend most on ${plural(it.day)}: ${Money.formatInr(it.paise)} across ${it.count} ${if (it.count == 1) "payment" else "payments"}.")
        }
        spending.maxByOrNull { it.amountPaise }?.let {
            add("Biggest payment: ${Money.formatInr(it.amountPaise)} to ${it.merchantName} on ${dateOf(it).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}.")
        }
        merchants.maxByOrNull { it.count }?.takeIf { it.count >= 3 }?.let {
            add("Most frequent: ${it.name}, ${it.count} payments.")
        }
        if (monthlyBudget > 0) {
            if (daily) {
                val now = trend.lastOrNull { it.isNow }
                if (now != null) {
                    add("So far ${Money.formatInr(spent / daysSoFar.coerceAtLeast(1))} a day; your budget allows ${Money.formatInr(monthlyBudget / daysInMonth.coerceAtLeast(1))} a day.")
                }
            } else {
                val done = trend.filter { it.hasData && !it.isNow }
                if (done.isNotEmpty()) {
                    val within = done.count { it.spent <= monthlyBudget }
                    add("$within of ${done.size} finished ${if (done.size == 1) "month" else "months"} stayed within your ${Money.formatInr(monthlyBudget)} budget.")
                }
            }
        }
    }.take(5)
}
