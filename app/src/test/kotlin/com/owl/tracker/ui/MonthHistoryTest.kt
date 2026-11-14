package com.owl.tracker.ui

import com.owl.tracker.core.BudgetMath
import com.owl.tracker.core.BudgetPeriod
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.core.TrendKind
import com.owl.tracker.core.TxKind
import com.owl.tracker.data.Category
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Source
import com.owl.tracker.data.Txn
import com.owl.tracker.ui.screens.trendPhrase
import com.owl.tracker.ui.screens.trendWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The months the budget sliders and Reports' trend by category are drawn from. */
class MonthHistoryTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 18)
    private val cats = Schema.DEFAULT_CATEGORIES.mapIndexed { i, s -> Category(s.id, s.name, s.icon, s.color, s.kind, i, true, false) }
    private var nextId = 1L

    private fun tx(day: LocalDate, rupees: Long, cat: String, dir: Direction = Direction.DEBIT, excluded: Boolean = false) = Txn(
        id = nextId++, amountPaise = rupees * 100, direction = dir, occurredAt = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        merchantName = cat, merchantKey = cat, merchantPackage = null, vpa = null, categoryId = cat,
        accountLabel = null, senderGroup = null, sender = null, refNo = null, source = Source.MANUAL, smsId = null,
        needsReview = false, excluded = excluded, note = null, kind = TxKind.NORMAL, smsBody = null,
    )

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    private fun build(txns: List<Txn>, startDay: Int = 1): MonthHistory {
        val current = BudgetMath.periodFor(today, startDay)
        val months = ArrayList<BudgetPeriod>()
        var p = current
        repeat(6) { p = p.previous(startDay); months.add(0, p) }
        return MonthHistory.build(txns, cats, months, current, zone)
    }

    @Test
    fun monthsBeforeTheRecordsAreUnknownNotZero() {
        val h = build(listOf(
            tx(d(7, 2), 4_000, Cat.FOOD), tx(d(8, 10), 5_000, Cat.FOOD), tx(d(9, 10), 6_000, Cat.FOOD),
            tx(d(8, 14), 12_000, Cat.HOLIDAY),
            tx(d(10, 5), 1_500, Cat.FOOD),
        ))
        assertEquals(listOf("Apr", "May", "Jun", "Jul", "Aug", "Sep"), h.labels)
        assertEquals(3, h.firstKnown)
        assertEquals(listOf(null, null, null, 4_000_00L, 5_000_00L, 6_000_00L), h.of(Cat.FOOD))
        assertEquals(listOf(null, null, null, 0L, 12_000_00L, 0L), h.of(Cat.HOLIDAY))
        // A category with nothing is ₹0 in the months that have data
        assertEquals(listOf(null, null, null, 0L, 0L, 0L), h.of(Cat.SHOPPING))
        assertEquals(1_500_00L, h.now[Cat.FOOD])
        assertEquals(5_000_00L, h.usual(Cat.FOOD))
        assertEquals(4_000_00L, h.usual(Cat.HOLIDAY))
        assertEquals(3, h.knownCount)
        assertTrue(h.hasData)
    }

    @Test
    fun aMonthCountsOnlyWhenTheRecordsReachItsStart() {
        // The first payment on 20 Aug: August is only partly known, so it doesn't count
        val h = build(listOf(tx(d(8, 20), 119, Cat.ENTERTAINMENT), tx(d(9, 20), 119, Cat.ENTERTAINMENT)))
        assertEquals(5, h.firstKnown)
        assertEquals(listOf(null, null, null, null, null, 119_00L), h.of(Cat.ENTERTAINMENT))
    }

    @Test
    fun onlyThisMonthMeansNothingToGoByYet() {
        val h = build(listOf(tx(d(10, 3), 500, Cat.FOOD)))
        assertFalse(h.hasData)
        assertEquals(500_00L, h.now[Cat.FOOD])
    }

    @Test
    fun moneyInTransfersAndLeftOutPaymentsAreNotSpending() {
        val h = build(listOf(
            tx(d(9, 1), 1_20_000, Cat.INCOME, Direction.CREDIT),
            tx(d(9, 2), 10_000, Cat.TRANSFERS),
            tx(d(9, 3), 700, Cat.TRANSPORT, excluded = true),
            tx(d(9, 4), 900, Cat.GROCERIES),
        ))
        assertNull(h.byCategory[Cat.TRANSFERS])
        assertEquals(listOf(null, null, null, null, null, 0L), h.of(Cat.TRANSPORT))
        assertEquals(1_20_000_00L, h.usualMoneyIn)
        assertEquals(900_00L, h.usual(Cat.GROCERIES))
    }

    @Test
    fun budgetMonthsFollowTheStartDay() {
        // Months that start on the 25th: 2 Aug falls in July's budget month (25 Jul – 24 Aug)
        val h = build(listOf(tx(d(7, 26), 1_000, Cat.FOOD), tx(d(8, 2), 2_000, Cat.FOOD)), startDay = 25)
        assertEquals(3_000_00L, h.of(Cat.FOOD)[h.months.indexOfFirst { it.start == d(7, 25) }])
    }

    @Test
    fun trendsInWords() {
        val labels = listOf("Apr", "May", "Jun", "Jul", "Aug", "Sep")
        val rising = listOf(null, null, null, 4_000_00L, 5_000_00L, 6_000_00L)
        assertEquals("↑ Rising · Jul ₹4k, Aug ₹5k, Sep ₹6k" to TrendKind.RISING, trendWords(rising, labels))
        assertEquals("↑ Rising · 50% more than in Jul" to TrendKind.RISING, trendPhrase(rising, labels))
        val falling = listOf(null, null, null, 8_000_00L, 7_000_00L, 6_000_00L)
        assertEquals("↓ Falling · 25% less than in Jul" to TrendKind.FALLING, trendPhrase(falling, labels))
        assertEquals(TrendKind.STEADY, trendPhrase(listOf(null, null, null, 3_000_00L, 3_000_00L, 3_000_00L), labels).second)
        assertEquals("↕ Up and down · ₹0 to ₹12k" to TrendKind.UP_AND_DOWN, trendPhrase(listOf(null, null, null, 0L, 12_000_00L, 0L), labels))
        assertEquals("Only Sep so far" to TrendKind.TOO_EARLY, trendPhrase(listOf(null, null, null, null, null, 119_00L), labels))
    }

    @Test
    fun movesInWords() {
        val labels = listOf("May", "Jun", "Jul", "Aug", "Sep", "Oct")
        fun phrase(vararg r: Long?) = r.map { it?.times(100) }.let { m -> com.owl.tracker.ui.screens.movePhrase(com.owl.tracker.core.Trends.move(m)!!, m, labels) }
        assertEquals("↑ Rising · 27% more than in Aug", phrase(12400, 13800, 15200, 16900, 17500, 21400))
        assertEquals("↓ Falling · 24% less than in Aug", phrase(7800, 7100, 6400, 5900, 5200, 4500))
        assertEquals("New · ₹10.5k in Oct", phrase(0, 0, 0, 0, 0, 10500))
        assertEquals("↑ Up · ₹6.2k in Oct, usually nothing", phrase(null, 0, 0, 0, 1800, 6200))
        assertEquals("↓ Down · nothing in Oct, usually ₹1.2k", phrase(400, 1200, 2800, 1200, 1100, 0))
    }
}
