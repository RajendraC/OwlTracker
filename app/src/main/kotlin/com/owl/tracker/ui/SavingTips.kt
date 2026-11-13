package com.owl.tracker.ui

import com.owl.tracker.core.BudgetPeriod
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import com.owl.tracker.data.Txn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class TipKind { ABOVE_USUAL, ONE_OFF, TRIM, SUBSCRIPTIONS, KEEP }

/** One way to save, with roughly how much it could save in a month. */
data class SavingTip(
    val id: String,
    val kind: TipKind,
    val title: String,
    val detail: String,
    val monthlyPaise: Long,
    val categoryId: String? = null,
)

/**
 * Ways to save, worked out on the phone from the last few budget months and this one. The [tips] are
 * about different spending, so their savings add up to [monthlyPaise]. [keep] is the overall picture
 * (the share of income saved, against a 20% goal); it overlaps the tips, so it isn't added in.
 */
data class SavingPlan(val tips: List<SavingTip>, val keep: SavingTip?, val basedOnMonths: Int) {
    val monthlyPaise: Long get() = tips.sumOf { it.monthlyPaise }
    val isEmpty: Boolean get() = tips.isEmpty() && keep == null
}

/** Pure, so it's tested on the JVM. Nothing leaves the phone. */
object SavingTips {
    const val HISTORY_MONTHS = 3
    const val MAX_TIPS = 4
    const val TRIM_PCT = 20
    const val KEEP_PCT = 20

    /** A category under ₹500 a month isn't worth a tip. */
    private const val MIN_USUAL = 50_000L
    /** Trimming needs at least ₹1,000 a month over 4+ payments: everyday spending, not one-offs. */
    private const val MIN_TRIM = 100_000L
    private const val MIN_TRIM_COUNT = 4.0

    /** Commitments and needs: an EMI, rent, school fees or a hospital bill aren't "ways to save". */
    private val FIXED = setOf(Cat.EMI, Cat.RENT, Cat.EDUCATION, Cat.HEALTH, Cat.INVESTMENTS)

    /** Everyday spending that is easy to cut back a little. */
    private val DISCRETIONARY = listOf(Cat.FOOD, Cat.SHOPPING, Cat.ENTERTAINMENT)
    /** Streaming and app subscriptions are filed here; Entertainment is cinema tickets and the like. */
    private val SUBSCRIPTION_CATS = setOf(Cat.RECHARGE)
    /** Phone plans repeat every month but aren't something to cancel. */
    private val PHONE_PLANS = setOf("jio", "airtel", "vi", "bsnl")

    /**
     * [history]: the budget months before [current], oldest first (months without any payment are
     * ignored). [txns] must cover them and [current].
     */
    fun build(
        payments: List<Txn>,
        cats: List<Category>,
        history: List<BudgetPeriod>,
        current: BudgetPeriod,
        today: LocalDate,
        zone: ZoneId,
    ): SavingPlan {
        // A split payment counts as its parts, each in its own category
        val txns = payments.flatMap { it.portions() }
        val catMap = cats.associateBy { it.id }
        val dates = HashMap<Long, LocalDate>()
        fun dateOf(t: Txn): LocalDate = dates.getOrPut(t.id) { Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate() }
        fun spending(list: List<Txn>) = list.filter { it.isSpending(catMap[it.categoryId]) }
        fun name(id: String) = catMap[id]?.name ?: "Others"

        val active = history.map { p -> txns.filter { p.contains(dateOf(it)) } }.filter { it.isNotEmpty() }
        val n = active.size
        if (n == 0) return SavingPlan(emptyList(), null, 0)

        val past = active.flatMap { spending(it) }
        val usual = past.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountPaise } / n }
        val usualCount = past.groupBy { it.categoryId }.mapValues { (_, l) -> l.size.toDouble() / n }
        val now = spending(txns.filter { current.contains(dateOf(it)) }).groupBy { it.categoryId }
        val elapsed = if (current.contains(today)) current.dayNumber(today).toDouble() / current.days else 1.0
        val tips = ArrayList<SavingTip>()

        // 1. A category already over, or heading over, its usual month
        for ((id, list) in now) {
            if (id in FIXED) continue
            val u = usual[id] ?: continue
            if (u < MIN_USUAL) continue
            val soFar = list.sumOf { it.amountPaise }
            val biggest = list.maxBy { it.amountPaise }
            if (soFar > u && soFar - u >= maxOf(30_000L, u / 10) && biggest.amountPaise * 10 >= soFar * 6 && biggest.amountPaise >= 2 * u) {
                // Mostly one big payment (a flight, a repair): said, but not counted as something to cut back every month
                tips += SavingTip(
                    "oneoff:$id", TipKind.ONE_OFF,
                    "${name(id)}: one big payment this month",
                    "${fmt(biggest.amountPaise)} to ${biggest.merchantName} on ${dateOf(biggest).format(DAY)} made ${name(id)} ${fmt(nice(soFar - u))} more than usual. " +
                        "If it was a one-off, there's nothing to cut back.",
                    0, id,
                )
            } else if (soFar > u) {
                val over = nice(soFar - u)
                if (soFar - u >= maxOf(30_000L, u / 10)) tips += SavingTip(
                    "above:$id", TipKind.ABOVE_USUAL,
                    "${name(id)} is ${fmt(over)} over your usual month",
                    "You usually spend ${fmt(nice(u))} a month here; this month it's ${fmt(soFar)} so far. Getting back to your usual saves ${fmt(over)}.",
                    over, id,
                )
            } else if (elapsed in 0.33..0.9 && list.size >= 4) {
                val projected = (soFar / elapsed).toLong()
                if (projected - u >= maxOf(50_000L, u / 5)) {
                    val over = nice(projected - u)
                    tips += SavingTip(
                        "above:$id", TipKind.ABOVE_USUAL,
                        "${name(id)} is heading ${fmt(over)} over your usual",
                        "${fmt(soFar)} so far, on course for about ${fmt(nice(projected))} against your usual ${fmt(nice(u))}. Slowing down now saves about ${fmt(over)}.",
                        over, id,
                    )
                }
            }
        }

        // 2. Everyday spending: one in five fewer
        for (id in DISCRETIONARY) {
            if (id in FIXED) continue
            if (tips.any { it.categoryId == id }) continue
            val u = usual[id] ?: continue
            val count = usualCount[id] ?: 0.0
            if (u < MIN_TRIM || count < MIN_TRIM_COUNT) continue
            val save = nice(u * TRIM_PCT / 100)
            tips += SavingTip(
                "trim:$id", TipKind.TRIM,
                "${name(id)}: one in five fewer saves ${fmt(save)} a month",
                "You spend about ${fmt(nice(u))} a month here over ${count.roundToInt()} payments. Skipping one in five is ${fmt(save * 12)} a year.",
                save, id,
            )
        }

        // 3. Subscriptions: the same merchant in two or more months
        val months = history + current
        val subs = txns.asSequence()
            .filter { it.isSpending(catMap[it.categoryId]) && it.categoryId in SUBSCRIPTION_CATS && it.merchantKey !in PHONE_PLANS }
            .groupBy { it.merchantKey }
            .mapNotNull { (_, l) ->
                val seen = l.map { t -> months.indexOfFirst { it.contains(dateOf(t)) } }.filter { it >= 0 }.toSet()
                if (seen.size < 2) null else {
                    val amounts = l.map { it.amountPaise }.sorted()
                    l.maxBy { it.occurredAt }.merchantName to amounts[amounts.size / 2]
                }
            }
            .sortedByDescending { it.second }
        if (subs.size >= 2) {
            val total = subs.sumOf { it.second }
            val cheapest = subs.minOf { it.second }
            val names = subs.take(4).joinToString(", ") { it.first } + if (subs.size > 4) " and ${subs.size - 4} more" else ""
            tips += SavingTip(
                "subs", TipKind.SUBSCRIPTIONS,
                "${subs.size} subscriptions cost ${fmt(total)} a month",
                "$names. Dropping one you don't use saves at least ${fmt(cheapest)} a month (${fmt(cheapest * 12)} a year).",
                cheapest,
            )
        } else if (subs.size == 1 && subs[0].second >= 19_900) {
            val (who, cost) = subs[0]
            tips += SavingTip("subs", TipKind.SUBSCRIPTIONS, "$who costs ${fmt(cost)} a month", "Still using it? Cancelling saves ${fmt(cost * 12)} a year.", cost)
        }

        // 4. The share of income saved, against a 20% goal
        val income = active.flatMap { l -> l.filter { it.isIncome(catMap[it.categoryId]) } }.sumOf { it.amountPaise } / n
        val spent = past.sumOf { it.amountPaise } / n
        val keep = if (income <= 0) null else {
            val pct = ((income - spent) * 100.0 / income).roundToInt()
            if (pct >= KEEP_PCT) {
                SavingTip("keep", TipKind.KEEP, "You save $pct% of your income", "That's above a $KEEP_PCT% goal. Keep it up.", 0)
            } else {
                val cut = nice(spent - income * (100 - KEEP_PCT) / 100)
                SavingTip(
                    "keep", TipKind.KEEP,
                    if (pct >= 0) "You save $pct% of your income" else "You spend more than your income",
                    "Usually ${fmt(nice(income))} comes in and ${fmt(nice(spent))} goes out a month. Saving $KEEP_PCT% means spending about ${fmt(cut)} less.",
                    cut,
                )
            }
        }
        return SavingPlan(tips.sortedByDescending { it.monthlyPaise }.take(MAX_TIPS), keep, n)
    }

    private val DAY = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)

    /** ₹1,234 → ₹1,200 and ₹437 → ₹440: tips are estimates, so they don't pretend to the rupee. */
    internal fun nice(paise: Long): Long = when {
        paise >= 100_000 -> (paise + 5_000) / 10_000 * 10_000
        paise >= 10_000 -> (paise + 500) / 1_000 * 1_000
        else -> paise
    }

    private fun fmt(paise: Long) = Money.formatInr(paise)
}
