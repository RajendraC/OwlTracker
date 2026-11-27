package com.owl.tracker.ui

import com.owl.tracker.i18n.tr
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
    val dayWords: String get() = if (billDay == LAST_DAY) tr("the last day") else tr("the ") + ordinal(billDay)

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
    /** Paid over the bill before, which the bank takes off this one. */
    val extraPaidPaise: Long = 0,
) {
    val netPaise: Long get() = (chargedPaise - refundsPaise - extraPaidPaise).coerceAtLeast(0)
}

/** Money back on the card (a credit): a refund, or a payment towards a bill. */
data class CardCredit(
    val txn: Txn,
    val payment: Boolean,
    /** Why: the user said so, or what the app saw. */
    val why: Why,
) {
    enum class Why { YOU, SMS_SAYS_PAYMENT, MATCHES_ACCOUNT_PAYMENT, LOOKS_LIKE_REFUND }
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
    /** Every credit on the card from the last bill period to today, with how it counts (to correct). */
    val credits: List<CardCredit> = emptyList(),
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
        /** The user's say on a card credit, by payment id: true = a bill payment, false = a refund. */
        creditKinds: Map<Long, Boolean> = emptyMap(),
    ): CardBill {
        fun dayOf(t: Txn) = java.time.Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate()
        val lastBill = cycle.lastBillOnOrBefore(today)
        val lastStart = cycle.billBefore(lastBill).plusDays(1)
        val nextBill = cycle.nextBillAfter(lastBill)
        val mine = txns.filter { it.accountLabel == label }
        val last4 = label.takeLast(4).takeIf { it.all(Char::isDigit) }
        val digits = last4?.let { Regex("(?<!\\d)$it(?!\\d)") }
        // Card bill payments from bank accounts, naming this card
        val accountPayments = txns.filter { t ->
            t.accountLabel != label && t.direction == Direction.DEBIT && t.kind == TxKind.CARD_BILL_PAYMENT &&
                digits != null && t.smsBody?.let { digits.containsMatchIn(it) } == true
        }
        fun sameMoney(a: Txn, b: Txn) = a.amountPaise == b.amountPaise && kotlin.math.abs(a.occurredAt - b.occurredAt) <= 4 * 86_400_000L

        // Each credit on the card: a bill payment or a refund
        val credits = mine.filter { it.direction == Direction.CREDIT }.map { t ->
            val said = creditKinds[t.id]
            when {
                said != null -> CardCredit(t, said, CardCredit.Why.YOU)
                t.kind == TxKind.CARD_BILL_PAYMENT || saysPayment(t.smsBody) -> CardCredit(t, true, CardCredit.Why.SMS_SAYS_PAYMENT)
                accountPayments.any { sameMoney(it, t) } -> CardCredit(t, true, CardCredit.Why.MATCHES_ACCOUNT_PAYMENT)
                else -> CardCredit(t, false, CardCredit.Why.LOOKS_LIKE_REFUND)
            }
        }
        val paymentIds = credits.filter { it.payment }.map { it.txn.id }.toSet()

        fun period(start: LocalDate, end: LocalDate, extra: Long = 0): BillPeriod {
            val inIt = mine.filter { val d = dayOf(it); !d.isBefore(start) && !d.isAfter(end) }
            val charges = inIt.filter { it.direction == Direction.DEBIT }
            val refunds = inIt.filter { it.direction == Direction.CREDIT && it.id !in paymentIds }
            return BillPeriod(start, end, charges.sumOf { it.amountPaise }, refunds.sumOf { it.amountPaise }, charges.size, extra)
        }

        // Bill payments after the last bill date
        val after = { t: Txn -> dayOf(t).isAfter(lastBill) }
        val onCard = mine.filter { it.id in paymentIds && after(it) }
        val fromAccounts = accountPayments.filter { after(it) }.filter { a ->
            // The same payment seen from both sides: the card's SMS is enough
            onCard.none { sameMoney(it, a) }
        }
        val found = (onCard + fromAccounts).sortedBy { it.occurredAt }.map { BillPayment(it.amountPaise, it.occurredAt, txnId = it.id) }
        val added = manual.withIndex().filter { it.value.bill == lastBill }
            .map { (i, p) -> BillPayment(p.paise, p.atMs, manualIndex = i, full = p.full) }

        val last = period(lastStart, lastBill)
        val payments = found + added.filter { !it.full }
        val markedPaid = added.any { it.full }
        // Paid more than the bill: the bank takes the extra off the next one
        val extra = if (markedPaid) 0 else (payments.sumOf { it.paise } - last.netPaise).coerceAtLeast(0)
        return CardBill(
            cycle = cycle,
            last = last,
            dueDate = lastBill.plusDays(cycle.dueDays.toLong()),
            payments = payments,
            markedPaid = markedPaid,
            current = period(lastBill.plusDays(1), today, extra),
            nextBill = nextBill,
            credits = credits.filter { !dayOf(it.txn).isBefore(lastStart) }.sortedBy { it.txn.occurredAt },
        )
    }

    /** A card's own SMS for a payment towards its bill ("Payment of Rs 31,780 received…", "Thank you for your payment"). */
    private val PAYMENT_WORDS = Regex(
        "payment\\s+(of\\s+(rs\\.?|inr|₹)\\s*[\\d,]+(\\.\\d+)?\\s+)?(has\\s+been\\s+|is\\s+|was\\s+)?(received|credited|successful|posted)" +
            "|thank\\s+you\\s+for\\s+(your|the)\\s+payment|received\\s+(a\\s+|your\\s+)?payment|payment\\s+received" +
            "|towards\\s+(your\\s+)?(\\w+\\s+){0,3}credit\\s+card(\\s+bill)?|card\\s+bill\\s+payment",
        RegexOption.IGNORE_CASE,
    )

    fun saysPayment(body: String?): Boolean = body != null && !body.contains("refund", ignoreCase = true) && PAYMENT_WORDS.containsMatchIn(body)

    fun creditKindsToJson(m: Map<Long, Boolean>): String =
        JSONObject().apply { m.forEach { (id, pay) -> put(id.toString(), if (pay) "payment" else "refund") } }.toString()

    fun creditKindsFromJson(raw: String?): Map<Long, Boolean> = runCatching {
        val o = JSONObject(raw ?: return emptyMap())
        o.keys().asSequence().mapNotNull { k ->
            val id = k.toLongOrNull() ?: return@mapNotNull null
            when (o.optString(k)) { "payment" -> id to true; "refund" -> id to false; else -> null }
        }.toMap()
    }.getOrDefault(emptyMap())

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
