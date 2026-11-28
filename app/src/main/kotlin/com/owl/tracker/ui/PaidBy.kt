package com.owl.tracker.ui

import com.owl.tracker.i18n.tr
import com.owl.tracker.i18n.trn
import com.owl.tracker.data.Category
import com.owl.tracker.data.CategoryTree
import com.owl.tracker.data.Source
import com.owl.tracker.data.Txn

/** What a payment was made from, as far as the app can tell. In this order on Paid by. */
enum class PaidKind(val key: String, val label: String, val heading: String) {
    CREDIT_CARD("credit", tr("Credit card"), tr("Credit cards")),
    DEBIT_CARD("debit", tr("Debit card"), tr("Debit cards")),
    ACCOUNT("account", tr("Bank account"), tr("Bank accounts")),
    BY_HAND("by_hand", tr("No card or account"), tr("Cash and added by hand")),
    UNKNOWN("unknown", tr("The SMS didn't say which"), tr("Not known")),
    ;

    /** A card or account the user can say is one of the first three. */
    val named: Boolean get() = this == CREDIT_CARD || this == DEBIT_CARD || this == ACCOUNT

    companion object {
        /** The three a card or account can be set to. */
        val CHOICES = listOf(CREDIT_CARD, DEBIT_CARD, ACCOUNT)
        fun fromKey(key: String?): PaidKind? = CHOICES.firstOrNull { it.key == key }
    }
}

/**
 * A card or bank account, by the bank and the last 4 digits its SMS gives ("HDFC Bank card ••9110"), with what was
 * spent from it: the total, its payments (newest first) and the groups it went to, biggest first.
 */
data class PaidAccount(
    val key: String,
    val name: String,
    val kind: PaidKind,
    /** The bank's sender group, for its app's icon. */
    val senderGroup: String?,
    val paise: Long,
    val payments: List<Txn>,
    val tops: List<CategorySpend>,
    /** What the app worked out from the SMS; [kind] differs when the user changed it. */
    val guessed: PaidKind = kind,
) {
    val changed: Boolean get() = kind != guessed

    /** "Credit card · 38 payments" */
    val note: String
        get() = kind.label + " · ${trn(payments.size, "{0} payment", "{0} payments")}"
}

/**
 * Home's "Paid by" tab: the spending of a period by the card or account it was paid from. Counts what every other
 * total counts (money out, in a category that counts, not left out; a split payment by its parts).
 */
object PaidBy {
    const val BY_HAND = "by_hand"
    const val UNKNOWN = "unknown"

    /** Cash taken out at an ATM is stored with the label "Cash": it's spent as cash, so it goes with cash. */
    fun keyOf(t: Txn): String = t.accountLabel?.takeUnless { it == "Cash" } ?: if (t.source == Source.MANUAL || t.accountLabel == "Cash") BY_HAND else UNKNOWN

    private val CREDIT = Regex("""(?i)credit\s*card|\bcc\b""")
    private val DEBIT = Regex("""(?i)debit\s*card|\bdc\b|\bATM\s*card""")

    /**
     * What a card or account is, from its label and its SMS: an account unless the label says card; a card the SMS
     * calls a debit card more often than a credit card is a debit card, otherwise a credit card ("HDFC Bank Card 9110"
     * with neither word is usually a credit card alert).
     */
    fun guess(label: String, bodies: List<String?>): PaidKind {
        if (tr(" card ••") !in label) return PaidKind.ACCOUNT
        val texts = bodies.filterNotNull()
        val debit = texts.count { DEBIT.containsMatchIn(it) }
        val credit = texts.count { CREDIT.containsMatchIn(it) }
        return if (debit > credit) PaidKind.DEBIT_CARD else PaidKind.CREDIT_CARD
    }

    /** What the card or account [label] is: the user's choice, else [guess] from these payments' SMS. */
    fun kindOf(label: String, payments: List<Txn>, overrides: Map<String, String>): PaidKind =
        PaidKind.fromKey(overrides[label]) ?: guess(label, payments.filter { it.accountLabel == label }.map { it.smsBody })

    fun build(txns: List<Txn>, cats: List<Category>, overrides: Map<String, String> = emptyMap()): List<PaidAccount> {
        val catMap = cats.associateBy { it.id }
        val tree = CategoryTree(cats)
        return txns.groupBy(::keyOf).mapNotNull { (key, list) ->
            val spends = list.flatMap { it.portions() }.filter { it.isSpending(catMap[it.categoryId]) }
            val total = spends.sumOf { it.amountPaise }
            if (total <= 0) return@mapNotNull null
            val ids = spends.map { it.id }.toSet()
            val guessed = when (key) {
                BY_HAND -> PaidKind.BY_HAND
                UNKNOWN -> PaidKind.UNKNOWN
                else -> guess(key, list.map { it.smsBody })
            }
            val kind = if (guessed.named) PaidKind.fromKey(overrides[key]) ?: guessed else guessed
            PaidAccount(
                key = key,
                name = when (kind) {
                    PaidKind.BY_HAND -> tr("Cash and added by hand")
                    PaidKind.UNKNOWN -> tr("Card or account not known")
                    else -> key
                },
                kind = kind,
                guessed = guessed,
                senderGroup = list.firstNotNullOfOrNull { it.senderGroup },
                paise = total,
                payments = list.filter { it.id in ids }.sortedByDescending { it.occurredAt },
                tops = spends.groupBy { tree.top(it.categoryId) }
                    .mapNotNull { (id, l) -> catMap[id]?.let { CategorySpend(it, l.sumOf { p -> p.amountPaise }) } }
                    .sortedByDescending { it.paise },
            )
        }.sortedWith(compareBy<PaidAccount> { it.kind.ordinal }.thenByDescending { it.paise })
    }

    /** The account's payments that went to a group (or a category on its own), newest first. */
    fun paymentsIn(account: PaidAccount, topId: String, cats: List<Category>): List<Txn> {
        val tree = CategoryTree(cats)
        val catMap = cats.associateBy { it.id }
        return account.payments.filter { t -> t.portions().any { it.isSpending(catMap[it.categoryId]) && tree.top(it.categoryId) == topId } }
    }
}
