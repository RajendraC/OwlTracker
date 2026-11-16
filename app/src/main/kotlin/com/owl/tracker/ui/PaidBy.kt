package com.owl.tracker.ui

import com.owl.tracker.data.Category
import com.owl.tracker.data.CategoryTree
import com.owl.tracker.data.Source
import com.owl.tracker.data.Txn

/** What a payment was made from, as far as the app can tell. */
enum class PaidKind { CARD, ACCOUNT, BY_HAND, UNKNOWN }

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
) {
    /** "Card · 38 payments" */
    val note: String
        get() = when (kind) {
            PaidKind.CARD -> "Card"
            PaidKind.ACCOUNT -> "Bank account"
            PaidKind.BY_HAND -> "No card or account"
            PaidKind.UNKNOWN -> "The SMS didn't say which"
        } + " · ${payments.size} ${if (payments.size == 1) "payment" else "payments"}"
}

/**
 * Home's "Paid by" tab: the spending of a period by the card or account it was paid from. Counts what every other
 * total counts (money out, in a category that counts, not left out; a split payment by its parts).
 */
object PaidBy {
    const val BY_HAND = "by_hand"
    const val UNKNOWN = "unknown"

    fun keyOf(t: Txn): String = t.accountLabel ?: if (t.source == Source.MANUAL) BY_HAND else UNKNOWN

    fun build(txns: List<Txn>, cats: List<Category>): List<PaidAccount> {
        val catMap = cats.associateBy { it.id }
        val tree = CategoryTree(cats)
        return txns.groupBy(::keyOf).mapNotNull { (key, list) ->
            val spends = list.flatMap { it.portions() }.filter { it.isSpending(catMap[it.categoryId]) }
            val total = spends.sumOf { it.amountPaise }
            if (total <= 0) return@mapNotNull null
            val ids = spends.map { it.id }.toSet()
            val kind = when {
                key == BY_HAND -> PaidKind.BY_HAND
                key == UNKNOWN -> PaidKind.UNKNOWN
                " card ••" in key -> PaidKind.CARD
                else -> PaidKind.ACCOUNT
            }
            PaidAccount(
                key = key,
                name = when (kind) {
                    PaidKind.BY_HAND -> "Cash and added by hand"
                    PaidKind.UNKNOWN -> "Card or account not known"
                    else -> key
                },
                kind = kind,
                senderGroup = list.firstNotNullOfOrNull { it.senderGroup },
                paise = total,
                payments = list.filter { it.id in ids }.sortedByDescending { it.occurredAt },
                tops = spends.groupBy { tree.top(it.categoryId) }
                    .mapNotNull { (id, l) -> catMap[id]?.let { CategorySpend(it, l.sumOf { p -> p.amountPaise }) } }
                    .sortedByDescending { it.paise },
            )
        }.sortedWith(compareBy<PaidAccount> { it.kind.ordinal >= PaidKind.BY_HAND.ordinal }.thenByDescending { it.paise })
    }

    /** The account's payments that went to a group (or a category on its own), newest first. */
    fun paymentsIn(account: PaidAccount, topId: String, cats: List<Category>): List<Txn> {
        val tree = CategoryTree(cats)
        val catMap = cats.associateBy { it.id }
        return account.payments.filter { t -> t.portions().any { it.isSpending(catMap[it.categoryId]) && tree.top(it.categoryId) == topId } }
    }
}
