package com.owl.tracker.ui

import com.owl.tracker.core.BudgetMath
import com.owl.tracker.core.BudgetPeriod
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.core.TxKind
import com.owl.tracker.data.Category
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Source
import com.owl.tracker.data.Txn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Ways to save, on the JVM: what each tip finds, how much it says, and what it leaves alone. */
class SavingTipsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 18)   // day 18 of 31
    private val cats = Schema.DEFAULT_CATEGORIES.mapIndexed { i, s -> Category(s.id, s.name, s.icon, s.color, s.kind, i, true, false) }
    private var nextId = 1L

    private fun tx(day: LocalDate, rupees: Int, merchant: String, cat: String, dir: Direction = Direction.DEBIT, excluded: Boolean = false) = Txn(
        id = nextId++, amountPaise = rupees * 100L, direction = dir, occurredAt = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        merchantName = merchant, merchantKey = merchant.lowercase(), merchantPackage = null, vpa = null, categoryId = cat,
        accountLabel = null, senderGroup = null, sender = null, refNo = null, source = Source.MANUAL, smsId = null,
        needsReview = false, excluded = excluded, note = null, kind = TxKind.NORMAL, smsBody = null,
    )

    private val current: BudgetPeriod = BudgetMath.periodFor(today, 1)
    private val history: List<BudgetPeriod> = (3 downTo 1).map { back -> var p = current; repeat(back) { p = p.previous(1) }; p }

    private fun month(m: Int) = LocalDate.of(2026, m, 1)

    /** Jul–Sep: ₹5,000 food over 10 orders, ₹4,000 shopping over 5, ₹3,000 transport over 10, Jio, Netflix, Spotify. */
    private fun usualMonths(salary: Int): List<Txn> = (7..9).flatMap { m ->
        val d = month(m)
        List(10) { tx(d.plusDays(it.toLong()), 500, "Swiggy", Cat.FOOD) } +
            List(5) { tx(d.plusDays(it * 3L), 800, "Myntra", Cat.SHOPPING) } +
            List(10) { tx(d.plusDays(it * 2L), 300, "Uber", Cat.TRANSPORT) } +
            tx(d.plusDays(2), 299, "Jio", Cat.RECHARGE) +
            (if (m >= 8) listOf(tx(d.plusDays(4), 649, "Netflix", Cat.RECHARGE)) else emptyList()) +
            (if (m >= 9) listOf(tx(d.plusDays(5), 119, "Spotify", Cat.RECHARGE)) else emptyList()) +
            tx(d, salary, "Acme", Cat.INCOME, Direction.CREDIT)
    }

    private val october = List(6) { tx(month(10).plusDays(it.toLong()), 1_000, "Swiggy", Cat.FOOD) } +   // ₹6,000: over the usual ₹5,000
        List(10) { tx(month(10).plusDays(it.toLong()), 250, "Uber", Cat.TRANSPORT) } +                       // ₹2,500 by day 18: heading for ₹4,300
        tx(month(10).plusDays(3), 500, "Myntra", Cat.SHOPPING) +
        tx(month(10).plusDays(4), 649, "Netflix", Cat.RECHARGE) +
        tx(month(10).plusDays(5), 119, "Spotify", Cat.RECHARGE) +
        tx(month(10).plusDays(6), 299, "Hotstar", Cat.RECHARGE) +                                            // once only: not a subscription
        tx(month(10).plusDays(7), 5_000, "Swiggy", Cat.FOOD, Direction.CREDIT) +                             // a refund never lowers spending
        tx(month(10).plusDays(8), 10_000, "Myntra", Cat.SHOPPING, excluded = true)                           // left out by the user

    private fun plan(txns: List<Txn>) = SavingTips.build(txns, cats, history, current, today, zone)

    @Test fun findsEachKindOfSavingAndAddsThemUp() {
        val p = plan(usualMonths(salary = 50_000) + october)
        assertEquals(3, p.basedOnMonths)
        assertEquals(listOf("above:transport", "above:food", "trim:shopping", "subs"), p.tips.map { it.id })

        val transport = p.tips[0]
        assertEquals("Transport is heading ₹1,300 over your usual", transport.title)
        assertEquals(130_000L, transport.monthlyPaise)
        assertTrue(transport.detail.startsWith("₹2,500 so far, on course for about ₹4,300 against your usual ₹3,000"))

        val food = p.tips[1]
        assertEquals("Food & Dining is ₹1,000 over your usual month", food.title)
        assertEquals(100_000L, food.monthlyPaise)

        val shopping = p.tips[2]
        assertEquals("Shopping: one in five fewer saves ₹800 a month", shopping.title)
        assertTrue(shopping.detail.contains("over 5 payments") && shopping.detail.contains("₹9,600 a year"))

        val subs = p.tips[3]
        assertEquals("2 subscriptions cost ₹768 a month", subs.title)   // Jio is a phone plan; Hotstar was paid once
        assertTrue(subs.detail.startsWith("Netflix, Spotify. Dropping one you don't use saves at least ₹119 a month (₹1,428 a year)"))
        assertEquals(11_900L, subs.monthlyPaise)  // real prices aren't rounded

        assertEquals(130_000L + 100_000L + 80_000L + 11_900L, p.monthlyPaise)
        val keep = p.keep!!
        assertEquals("You save 74% of your income", keep.title)
        assertEquals(0L, keep.monthlyPaise)
    }

    @Test fun spendingMoreThanComesInSaysHowMuchLess() {
        val keep = plan(usualMonths(salary = 10_000) + october).keep!!
        assertEquals("You spend more than your income", keep.title)
        assertEquals(480_000L, keep.monthlyPaise)  // ₹12,771 a month out against ₹8,000 to save 20% of ₹10,000
        assertTrue(keep.detail.contains("Saving 20% means spending about ₹4,800 less"))
    }

    @Test fun oneOffsAndSmallCategoriesGetNoTip() {
        val p = plan(usualMonths(50_000) + (7..9).map { tx(month(it).plusDays(9), 3_000, "PVR", Cat.ENTERTAINMENT) })
        assertTrue("One cinema trip a month isn't everyday spending", p.tips.none { it.id == "trim:entertainment" })
        assertTrue("…nor a subscription", p.tips.none { "PVR" in it.title || "PVR" in it.detail })
        // Netflix in two months is the only subscription (Spotify was paid once by September)
        assertEquals("Netflix costs ₹649 a month", p.tips.single { it.id == "subs" }.title)
        assertTrue("Nothing this month, so nothing is over", p.tips.none { it.kind == TipKind.ABOVE_USUAL })
    }

    @Test fun withoutEarlierMonthsThereIsNothingToCompare() {
        val p = plan(october)
        assertTrue(p.isEmpty)
        assertEquals(0, p.basedOnMonths)
        assertNull(p.keep)
    }

    @Test fun amountsAreRounded() {
        assertEquals(120_000L, SavingTips.nice(123_456))  // ₹1,234.56 → ₹1,200
        assertEquals(44_000L, SavingTips.nice(43_700))    // ₹437 → ₹440
        assertEquals(9_900L, SavingTips.nice(9_900))      // ₹99 stays
    }
}
