package com.owl.tracker.data

import com.owl.tracker.core.Cat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A budget for an event with its own dates: a festival, a wedding, a trip. Payments in [categories] (a category, or a
 * whole group) between [start] and [end] count toward it, and the person can move any payment in those dates in
 * ([added]) or out ([removed]). It is a second look at the same payments: they still count in the month's budget too.
 *
 * Payments are named by [payKey] (time and amount), which a backup keeps, so moved payments stay moved after a restore.
 */
data class EventBudget(
    val id: String,
    val template: String,
    val name: String,
    val emoji: String,
    val start: LocalDate,
    val end: LocalDate,
    val budgetPaise: Long,
    val categories: Set<String>,
    val added: Set<String> = emptySet(),
    val removed: Set<String> = emptySet(),
) {
    fun covers(day: LocalDate): Boolean = !day.isBefore(start) && !day.isAfter(end)
    fun isOver(today: LocalDate): Boolean = today.isAfter(end)
    fun hasStarted(today: LocalDate): Boolean = !today.isBefore(start)
    /** Days left including today (0 once it's over, the whole length before it starts). */
    fun daysLeft(today: LocalDate): Int = when {
        today.isAfter(end) -> 0
        today.isBefore(start) -> (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        else -> (ChronoUnit.DAYS.between(today, end) + 1).toInt()
    }
}

/** A starting point for a new event: its picture, the categories that usually go with it, and when it usually falls. */
data class EventTemplate(val key: String, val emoji: String, val categories: List<String>)

/** What an event has gathered: its payments (portions of split ones), the total, and the total by group. */
data class EventSpend(val payments: List<Txn>, val totalPaise: Long, val byTop: List<Pair<String, Long>>)

object Events {
    const val MAX = 12

    val TEMPLATES = listOf(
        EventTemplate("diwali", "🪔", listOf(Cat.GIVING, Cat.FASHION, Cat.JEWELLERY, Cat.FURNITURE)),
        EventTemplate("wedding", "💍", listOf(Cat.GIVING, Cat.FASHION, Cat.JEWELLERY, Cat.BEAUTY, Cat.TRAVEL)),
        EventTemplate("trip", "✈️", listOf(Cat.FUN, Cat.DINING, Cat.CABS, Cat.FUEL)),
        EventTemplate("holi", "🎨", listOf(Cat.GIFTS, Cat.FASHION, Cat.DINING, Cat.ENTERTAINMENT)),
        EventTemplate("eid", "🌙", listOf(Cat.GIVING, Cat.FASHION, Cat.DINING, Cat.GROCERIES)),
        EventTemplate("harvest", "🌾", listOf(Cat.GIVING, Cat.FASHION, Cat.GROCERIES, Cat.DINING)),
        EventTemplate("christmas", "🎄", listOf(Cat.GIFTS, Cat.DINING, Cat.ENTERTAINMENT, Cat.FURNITURE)),
        EventTemplate("other", "✨", listOf(Cat.GIFTS, Cat.SHOPPING)),
    )
    val PICTURES: Set<String> = TEMPLATES.map { it.emoji }.toSet()

    fun template(key: String): EventTemplate = TEMPLATES.firstOrNull { it.key == key } ?: TEMPLATES.last()

    /** Diwali's date (Lakshmi Puja) in the years ahead; the window runs from a week before to two days after. */
    private val DIWALI = listOf("2026-11-08", "2027-10-29", "2028-10-17", "2029-11-05", "2030-10-26").map(LocalDate::parse)

    /**
     * Dates to suggest for a new event made on [today]: the next Diwali, Christmas or Pongal (when its window hasn't
     * ended), otherwise the coming week. The person can change both.
     */
    fun suggestDates(key: String, today: LocalDate): Pair<LocalDate, LocalDate> {
        fun yearly(month: Int, day: Int, before: Long, after: Long): Pair<LocalDate, LocalDate> {
            var d = LocalDate.of(today.year, month, day)
            if (d.plusDays(after).isBefore(today)) d = d.plusYears(1)
            return d.minusDays(before) to d.plusDays(after)
        }
        return when (key) {
            "diwali" -> DIWALI.firstOrNull { !it.plusDays(2).isBefore(today) }?.let { it.minusDays(7) to it.plusDays(2) } ?: (today to today.plusDays(7))
            "christmas" -> yearly(12, 25, 7, 1)
            "harvest" -> yearly(1, 14, 2, 2)
            else -> today to today.plusDays(7)
        }
    }

    /** How a payment is named in [EventBudget.added] and [EventBudget.removed]. */
    fun payKey(t: Txn): String = "${t.occurredAt}:${t.amountPaise}"

    /**
     * Whether [portion] (a whole payment, or one part of a split one) of the payment named [key] on [day] counts toward
     * [e]: in its dates, and either moved in by hand or in one of its categories and not moved out. Only spending counts.
     */
    fun counts(e: EventBudget, portion: Txn, key: String, day: LocalDate, tree: CategoryTree): Boolean {
        if (!e.covers(day) || !portion.isSpending(tree.byId[portion.categoryId])) return false
        if (key in e.added) return true
        if (key in e.removed) return false
        return portion.categoryId in e.categories || tree.top(portion.categoryId) in e.categories
    }

    /** Whether payment [t] (as stored) counts toward [e], whole or in part. */
    fun contains(e: EventBudget, t: Txn, tree: CategoryTree, day: LocalDate): Boolean =
        t.portions().any { counts(e, it, payKey(t), day, tree) }

    /** What [e] has gathered from [txns] (payments, as stored), with [dayOf] giving each one's date. */
    fun spend(e: EventBudget, txns: List<Txn>, tree: CategoryTree, dayOf: (Txn) -> LocalDate): EventSpend {
        val payments = mutableListOf<Txn>()
        val portions = mutableListOf<Txn>()
        txns.forEach { t ->
            val k = payKey(t)
            val day = dayOf(t)
            val mine = t.portions().filter { counts(e, it, k, day, tree) }
            if (mine.isNotEmpty()) { payments += t; portions += mine }
        }
        val byTop = portions.groupBy { tree.top(it.categoryId) }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
            .toList().sortedByDescending { it.second }
        return EventSpend(payments.sortedByDescending { it.occurredAt }, portions.sumOf { it.amountPaise }, byTop)
    }

    /** Moves payment [t] into [e] ([inIt]) or out of it, leaving the categories to decide when that's what they say. */
    fun move(e: EventBudget, t: Txn, inIt: Boolean, tree: CategoryTree): EventBudget {
        val k = payKey(t)
        val byCategory = t.portions().any { it.categoryId in e.categories || tree.top(it.categoryId) in e.categories }
        return when {
            inIt && byCategory -> e.copy(added = e.added - k, removed = e.removed - k)
            inIt -> e.copy(added = e.added + k, removed = e.removed - k)
            byCategory -> e.copy(added = e.added - k, removed = e.removed + k)
            else -> e.copy(added = e.added - k, removed = e.removed - k)
        }
    }

    fun fromJson(raw: String?): List<EventBudget> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until minOf(a.length(), MAX)).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").filter { it.isLetterOrDigit() || it == '-' }.take(40).ifEmpty { return@mapNotNull null }
            val name = o.optString("name").filter { !it.isISOControl() }.trim().take(40).ifEmpty { return@mapNotNull null }
            val template = o.optString("template").takeIf { k -> TEMPLATES.any { it.key == k } } ?: "other"
            val emoji = o.optString("emoji").takeIf { it in PICTURES } ?: template(template).emoji
            val start = runCatching { LocalDate.parse(o.optString("start")) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalDate.parse(o.optString("end")) }.getOrNull()?.takeIf { !it.isBefore(start) } ?: return@mapNotNull null
            val budget = o.optLong("budget", 0).coerceIn(0, 1_000_000_000_00L)
            fun set(key: String, max: Int, ok: (String) -> Boolean) = o.optJSONArray(key)?.let { arr ->
                (0 until minOf(arr.length(), max)).mapNotNull { j -> arr.optString(j).takeIf(ok) }.toSet()
            } ?: emptySet()
            val idOk = { s: String -> s.isNotEmpty() && s.length <= 60 && s.all { it.isLetterOrDigit() || it == '_' || it == '-' } }
            val keyOk = { s: String -> Regex("\\d{1,15}:\\d{1,13}").matches(s) }
            if (budget <= 0) null
            else EventBudget(id, template, name, emoji, start, end, budget, set("categories", 40, idOk), set("added", 500, keyOk), set("removed", 500, keyOk))
        }
    }.getOrDefault(emptyList())

    fun toJson(events: List<EventBudget>): String = JSONArray().apply {
        events.take(MAX).forEach { e ->
            put(
                JSONObject().put("id", e.id).put("template", e.template).put("name", e.name).put("emoji", e.emoji)
                    .put("start", e.start.toString()).put("end", e.end.toString()).put("budget", e.budgetPaise)
                    .put("categories", JSONArray(e.categories.toList())).put("added", JSONArray(e.added.toList())).put("removed", JSONArray(e.removed.toList())),
            )
        }
    }.toString()
}
