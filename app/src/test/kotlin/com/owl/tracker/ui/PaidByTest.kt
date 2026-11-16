package com.owl.tracker.ui

import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.core.TxKind
import com.owl.tracker.data.Category
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Source
import com.owl.tracker.data.Txn
import com.owl.tracker.data.TxnPart
import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's Paid by tab: the month by the card or account it was paid from. */
class PaidByTest {

    private val cats = Schema.DEFAULT_CATEGORIES.mapIndexed { i, s -> Category(s.id, s.name, s.icon, s.color, s.kind, i, true, false, s.parent) }
    private var nextId = 1L

    private fun tx(
        rupees: Long, cat: String, account: String?, source: Source = Source.SMS, dir: Direction = Direction.DEBIT, excluded: Boolean = false,
        parts: List<TxnPart> = emptyList(),
    ) = Txn(
        id = nextId, amountPaise = rupees * 100, direction = dir, occurredAt = 1_000L * nextId++,
        merchantName = cat, merchantKey = cat, merchantPackage = null, vpa = null, categoryId = cat,
        accountLabel = account, senderGroup = account?.substringBefore(" "), sender = null, refNo = null, source = source, smsId = null,
        needsReview = false, excluded = excluded, note = null, kind = TxKind.NORMAL, smsBody = null, parts = parts,
    )

    private val card = "HDFC Bank card ••9110"
    private val account = "SBI ••8823"

    @Test
    fun spendingIsSplitByCardAndAccountBiggestFirstWithTheUnnamedOnesLast() {
        val list = PaidBy.build(listOf(
            tx(500, Cat.FOOD_DELIVERY, card), tx(1_500, Cat.FASHION, card), tx(300, Cat.GROCERIES, card),
            tx(4_000, Cat.COMMUTE, account),
            tx(9_000, Cat.GROCERIES, null, Source.MANUAL),
            tx(200, Cat.OTHERS, null), tx(100, Cat.OTHERS, null, Source.BACKUP),
        ), cats)
        assertEquals(listOf(account, card, PaidBy.BY_HAND, PaidBy.UNKNOWN), list.map { it.key })
        assertEquals(listOf(PaidKind.ACCOUNT, PaidKind.CARD, PaidKind.BY_HAND, PaidKind.UNKNOWN), list.map { it.kind })
        assertEquals(listOf(4_000_00L, 2_300_00L, 9_000_00L, 300_00L), list.map { it.paise })
        val c = list[1]
        assertEquals("Card · 3 payments", c.note)
        assertEquals("HDFC", c.senderGroup)
        // What it was used for, by group: Shopping, then Food (delivery and groceries together)
        assertEquals(listOf(Cat.SHOPPING to 1_500_00L, Cat.FOOD to 800_00L), c.tops.map { it.category.id to it.paise })
        assertEquals(2, PaidBy.paymentsIn(c, Cat.FOOD, cats).size)
        assertEquals("Bank account · 1 payment", list[0].note)
        assertEquals("Cash and added by hand", list[2].name)
        assertEquals("Card or account not known", list[3].name)
    }

    @Test
    fun onlyWhatCountsAsSpendingIsCounted() {
        val split = tx(1_000, Cat.SHOPPING, card, parts = listOf(TxnPart(Cat.FASHION, 600_00), TxnPart(Cat.INVESTMENTS, 400_00)))
        val list = PaidBy.build(listOf(
            split,
            tx(5_000, Cat.INCOME, card, dir = Direction.CREDIT), // money in
            tx(7_000, Cat.TRANSFERS, account), // between own accounts
            tx(800, Cat.DINING, account, excluded = true), // left out by the user
        ), cats)
        // The split counts for its spending part only; the account has nothing that counts, so it isn't listed
        assertEquals(listOf(card), list.map { it.key })
        assertEquals(600_00L, list[0].paise)
        assertEquals(listOf(split.id), list[0].payments.map { it.id })
        assertEquals(emptyList<PaidAccount>(), PaidBy.build(emptyList(), cats))
    }
}
