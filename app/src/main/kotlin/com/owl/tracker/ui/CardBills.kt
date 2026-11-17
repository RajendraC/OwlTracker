package com.owl.tracker.ui

import com.owl.tracker.core.Direction
import com.owl.tracker.core.TxKind
import com.owl.tracker.data.Txn
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * A credit card's billing cycle, set by the user: the bill (statement) date each month, [billDay] 1–28 or [LAST_DAY],
 * and the payment due [dueDays] days after it.
 */
data class CardCycle(val billDay: Int, val dueDays: Int = 20) {
    init {
        require(billDay in 1..28 || billDay == LAST_DAY)
        require(dueDays in 1..60)
    }

    /** This cycle's bill date in [year]/[month] (1–12): the last day for [LAST_DAY]. */
    fun billIn(year: Int, month: Int): LocalDate {
        val first = LocalDate.of(year, month, 1)
        return if (billDay == LAST_DAY) first.withDayOfMonth(first.lengthOfMonth()) else first.withDayOfMonth(billDay)
    }

    /** The last bill date on or before [day]. */
    fun lastBillOnOrBefore(day: LocalDate): LocalDate {
        val here = billIn(day.year, day.monthValue)
        return if (!here.isAfter(day)) here else day.minusMonths(1).let { billIn(it.year, it.monthValue) }
    }

    fun nextBillAfter(bill: LocalDate): LocalDate = bill.plusMonths(1).let { billIn(it.year, it.monthValue) }
    fun billBefore(bill: LocalDate): LocalDate = bill.minusMonths(1).let { billIn(it.year, it.monthValue) }

    /** "the 20th", "the last day" */
    val dayWords: String get() = if (billDay == LAST_DAY) "the last day" else "the " + ordinal(billDay)

    companion object {
        const val LAST_DAY = 31

        fun ordinal(n: Int): String = n.toString() + when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}

/**
 * A payment the user recorded against a card's bill dated [bill]: [paise] paid on [atMs], or the whole bill ([full]).
 */
data class CardPayment(val bill: LocalDate, val paise: Long, val atMs: Long, val full: Boolean = false)

/** A payment towards a bill: found in an SMS ([txnId]) or added by the user ([manualIndex] in the card's list). */
data class BillPayment(val paise: Long, val atMs: Long, val txnId: Long? = null, val manualIndex: Int? = null, val full: Boolean = false)

/**
 * One bill period, [start] to [end] (both days counted): what was charged to the card in it and what came back
 * (refunds). For the last bill, also what has been paid towards it since.
 */
data class BillPeriod(
    val start: LocalDate,
    val end: LocalDate,
    val chargedPaise: Long,
    val refundsPaise: Long,
    val charges: Int,
) {
    val netPaise: Long get() = (chargedPaise - refundsPaise).coerceAtLeast(0)
}

/** A credit card's last bill and the bill building up now. */
data class CardBill(
    val cycle: CardCycle,
    /** The bill whose date has passed: what's owed on it. */
    val last: BillPeriod,
    val dueDate: LocalDate,
    val payments: List<BillPayment>,
    val markedPaid: Boolean,
    /** The bill period running now, up to today. */
    val current: BillPeriod,
    val nextBill: LocalDate,
) {
    val paidPaise: Long get() = payments.sumOf { it.paise }
    /** Still to pay on the last bill: nothing once it's marked paid or the payments cover it. */
    val stillToPayPaise: Long get() = if (markedPaid) 0 else (last.netPaise - paidPaise).coerceAtLeast(0)
    val paidInFull: Boolean get() = stillToPayPaise == 0L
}

/**
 * Still to pay on a credit card, from its billing cycle, the card's payments in the app and the payments to it.
 *
 * - Charged: every payment out of the card (the bank bills them all, whatever their category).
 * - Refunds: money back on the card that isn't a bill payment.
 * - Paid: bill payments since the last bill date, found two ways: the card's own SMS ("Payment received towards your
 *   Credit Card ending 9012"), and a bank account's SMS for a card bill payment naming the card's last 4 digits (the
 *   same payment is counted once when both came); and payments the user added for that bill.
 */
object CardBills {

    fun compute(
        label: String,
        cycle: CardCycle,
        txns: List<Txn>,
        manual: List<CardPayment>,
        today: LocalDate,
        zone: ZoneId,
    ): CardBill {
        fun dayOf(t: Txn) = java.time.Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate()
        val lastBill = cycle.lastBillOnOrBefore(today)
        val lastStart = cycle.billBefore(lastBill).plusDays(1)
        val nextBill = cycle.nextBillAfter(lastBill)
        val mine = txns.filter { it.accountLabel == label }

        fun period(start: LocalDate, end: LocalDate): BillPeriod {
            val inIt = mine.filter { val d = dayOf(it); !d.isBefore(start) && !d.isAfter(end) }
            val charges = inIt.filter { it.direction == Direction.DEBIT }
            val refunds = inIt.filter { it.direction == Direction.CREDIT && it.kind != TxKind.CARD_BILL_PAYMENT }
            return BillPeriod(start, end, charges.sumOf { it.amountPaise }, refunds.sumOf { it.amountPaise }, charges.size)
        }

        // Bill payments after the last bill date
        val after = { t: Txn -> dayOf(t).isAfter(lastBill) }
        val onCard = mine.filter { it.direction == Direction.CREDIT && it.kind == TxKind.CARD_BILL_PAYMENT && after(it) }
        val last4 = label.takeLast(4).takeIf { it.all(Char::isDigit) }
        val digits = last4?.let { Regex("(?<!\\d)$it(?!\\d)") }
        val fromAccounts = txns.filter { t ->
            t.accountLabel != label && t.direction == Direction.DEBIT && t.kind == TxKind.CARD_BILL_PAYMENT && after(t) &&
                digits != null && t.smsBody?.let { digits.containsMatchIn(it) } == true
        }.filter { a ->
            // The same payment seen from both sides: the card's SMS is enough
            onCard.none { it.amountPaise == a.amountPaise && kotlin.math.abs(it.occurredAt - a.occurredAt) <= 4 * 86_400_000L }
        }
        val found = (onCard + fromAccounts).sortedBy { it.occurredAt }.map { BillPayment(it.amountPaise, it.occurredAt, txnId = it.id) }
        val added = manual.withIndex().filter { it.value.bill == lastBill }
            .map { (i, p) -> BillPayment(p.paise, p.atMs, manualIndex = i, full = p.full) }

        return CardBill(
            cycle = cycle,
            last = period(lastStart, lastBill),
            dueDate = lastBill.plusDays(cycle.dueDays.toLong()),
            payments = found + added.filter { !it.full },
            markedPaid = added.any { it.full },
            current = period(lastBill.plusDays(1), today),
            nextBill = nextBill,
        )
    }

    // ---- stored in settings (never in backups: the keys hold card digits)

    fun cyclesToJson(m: Map<String, CardCycle>): String =
        JSONObject().apply { m.forEach { (k, c) -> put(k, JSONObject().put("bill", c.billDay).put("due", c.dueDays)) } }.toString()

    fun cyclesFromJson(raw: String?): Map<String, CardCycle> = runCatching {
        val o = JSONObject(raw ?: return emptyMap())
        o.keys().asSequence().mapNotNull { k ->
            val c = o.getJSONObject(k)
            runCatching { k to CardCycle(c.getInt("bill"), c.optInt("due", 20)) }.getOrNull()
        }.toMap()
    }.getOrDefault(emptyMap())

    fun paymentsToJson(m: Map<String, List<CardPayment>>): String = JSONObject().apply {
        m.forEach { (k, list) ->
            put(k, JSONArray().apply {
                list.forEach { p -> put(JSONObject().put("bill", p.bill.toString()).put("paise", p.paise).put("at", p.atMs).put("full", p.full)) }
            })
        }
    }.toString()

    fun paymentsFromJson(raw: String?): Map<String, List<CardPayment>> = runCatching {
        val o = JSONObject(raw ?: return emptyMap())
        o.keys().asSequence().associateWith { k ->
            val a = o.getJSONArray(k)
            (0 until a.length()).mapNotNull { i ->
                val p = a.getJSONObject(i)
                runCatching {
                    CardPayment(LocalDate.parse(p.getString("bill")), p.getLong("paise").coerceAtLeast(0), p.optLong("at"), p.optBoolean("full"))
                }.getOrNull()
            }
        }
    }.getOrDefault(emptyMap())
}
