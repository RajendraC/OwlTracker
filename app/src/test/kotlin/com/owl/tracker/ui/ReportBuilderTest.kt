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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** The Reports maths, on the JVM: totals, comparisons, every chart and how tapping narrows them. */
class ReportBuilderTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 18)
    private val cats = Schema.DEFAULT_CATEGORIES.mapIndexed { i, s -> Category(s.id, s.name, s.icon, s.color, s.kind, i, true, false) }
    private var nextId = 1L

    private fun tx(day: LocalDate, paise: Long, merchant: String, cat: String, dir: Direction = Direction.DEBIT, excluded: Boolean = false) = Txn(
        id = nextId++, amountPaise = paise, direction = dir, occurredAt = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        merchantName = merchant, merchantKey = merchant.lowercase(), merchantPackage = null, vpa = null, categoryId = cat,
        accountLabel = null, senderGroup = null, sender = null, refNo = null, source = Source.MANUAL, smsId = null,
        needsReview = false, excluded = excluded, note = null, kind = TxKind.NORMAL, smsBody = null,
    )

    private fun oct(d: Int) = LocalDate.of(2026, 10, d)
    private fun sep(d: Int) = LocalDate.of(2026, 9, d)

    private val txns = listOf(
        tx(oct(3), 50_000, "Swiggy", Cat.FOOD),               // Saturday
        tx(oct(4), 30_000, "Swiggy", Cat.FOOD),               // Sunday
        tx(oct(10), 200_000, "Amazon", Cat.SHOPPING),         // Saturday
        tx(oct(12), 5_000_000, "Employer", Cat.INCOME, Direction.CREDIT),
        tx(oct(13), 1_000_000, "To savings", Cat.TRANSFERS),  // a transfer: never spending
        tx(oct(14), 50_000, "Amazon", Cat.SHOPPING, Direction.CREDIT), // refund
        tx(oct(15), 20_000, "Uber", Cat.TRANSPORT, excluded = true),
        tx(sep(5), 100_000, "Swiggy", Cat.FOOD),
        tx(sep(20), 100_000, "Amazon", Cat.SHOPPING),
    )

    private fun period(back: Int): BudgetPeriod {
        var p = BudgetMath.periodFor(today, 1)
        repeat(back) { p = p.previous(1) }
        return p
    }

    private fun build(range: ReportRange = ReportRange.ONE, filter: ReportFilter = ReportFilter(), budget: Long = 0L): Report {
        val periods = (range.months - 1 downTo 0).map { period(it) }
        val previous = (2 * range.months - 1 downTo range.months).map { period(it) }
        return ReportBuilder.build(txns, cats, periods, previous, budget, range, filter, today, zone)
    }

    @Test fun onlyMoneyOutIsSpendingAndTransfersAndExcludedAreLeftOut() {
        val r = build()
        assertEquals(280_000L, r.spent)          // 500 + 300 + 2000: the ₹500 refund is money in, not less spending
        assertEquals(5_050_000L, r.received)     // salary + refund
        assertEquals(200_000L, r.previousSpent)  // September
        assertEquals(40, r.changePct)
        assertEquals(280_000L / 18, r.perDay)    // 18 days so far
        assertEquals(3, r.payments)
        assertEquals("October 2026", r.title)
    }

    @Test fun thisMonthIsDayByDayAndFutureDaysAreEmpty() {
        val r = build()
        assertTrue(r.daily)
        assertEquals(31, r.trend.size)
        assertEquals(200_000L, r.trend[9].spent)
        assertTrue(r.trend[17].isNow)
        assertFalse(r.trend[20].hasData)
    }

    @Test fun categoriesAreRankedWithSharesAndLastPeriod() {
        val r = build()
        assertEquals(listOf(Cat.SHOPPING, Cat.FOOD), r.categories.map { it.category.id })
        assertEquals(200_000L, r.categories[0].paise)
        assertEquals(200f / 280f, r.categories[0].share, 0.001f)
        assertEquals(100_000L, r.categories[0].previous)
        assertTrue(r.categories.none { it.category.id == Cat.TRANSFERS || it.category.id == Cat.INCOME || it.category.id == Cat.TRANSPORT })
    }

    @Test fun merchantsAndWeekdaysCountPayments() {
        val r = build()
        assertEquals(listOf("amazon", "swiggy"), r.merchants.map { it.key })
        assertEquals(2, r.merchants[1].count)
        val sat = r.weekdays.first { it.day == DayOfWeek.SATURDAY }
        assertEquals(250_000L, sat.paise)
        assertEquals(2, sat.count)
    }

    @Test fun tappingACategoryNarrowsEverythingElse() {
        val r = build(filter = ReportFilter(categoryId = Cat.FOOD))
        assertEquals(80_000L, r.spent)
        assertEquals(-20, r.changePct)                    // Food: 800 vs 1,000 last month
        assertEquals(listOf("swiggy"), r.merchants.map { it.key })
        assertEquals(2, r.categories.size)                // the donut stays whole, with Food picked
        assertEquals(2, r.matchCount)
        assertTrue(r.matches.first().occurredAt > r.matches.last().occurredAt)
        assertEquals(listOf(FilterKind.CATEGORY to "Food & Dining"), r.chips)
        assertEquals(0L, r.budgetLine)
    }

    @Test fun tappingADayShowsThatDay() {
        val r = build(filter = ReportFilter(bucket = 9), budget = 3_100_000)
        assertEquals(200_000L, r.spent)
        assertEquals(200_000L, r.perDay)
        assertNull(r.changePct)
        assertEquals(listOf(Cat.SHOPPING), r.categories.map { it.category.id })
        assertEquals("Amazon", r.matches.single().merchantName)
        assertEquals(FilterKind.BUCKET to "Sat 10 Oct", r.chips.single())
        assertEquals(100_000L, r.budgetLine)              // ₹31,000 a month = ₹1,000 a day
    }

    @Test fun tappingAMerchantOrWeekdayAlsoNarrows() {
        val byMerchant = build(filter = ReportFilter(merchantKey = "swiggy"))
        assertEquals(listOf(Cat.FOOD), byMerchant.categories.map { it.category.id })
        assertEquals(2, byMerchant.merchants.size)       // the merchant list stays whole
        val bySaturday = build(filter = ReportFilter(weekday = DayOfWeek.SATURDAY))
        assertEquals(250_000L, bySaturday.spent)
        assertEquals(FilterKind.WEEKDAY to "Saturdays", bySaturday.chips.single())
    }

    @Test fun severalMonthsShowMonthlyBarsAndBudgetRecord() {
        val r = build(ReportRange.THREE, budget = 300_000)
        assertFalse(r.daily)
        assertEquals(listOf("Aug", "Sep", "Oct"), r.trend.map { it.label })
        assertFalse(r.trend[0].hasData)
        assertEquals(200_000L, r.trend[1].spent)
        assertEquals(480_000L, r.spent)
        assertNull("Nothing before August to compare with", r.previousSpent)
        assertEquals(300_000L, r.budgetLine)
        assertTrue(r.highlights.any { it.startsWith("1 of 1 finished month") })
    }

    @Test fun highlightsSummariseTheMonth() {
        val r = build(budget = 3_100_000)
        assertTrue(r.highlights.any { it.startsWith("Shopping was your biggest spend: 71%") })
        assertTrue(r.highlights.any { it.startsWith("You spent 40% more than last month") })
        assertTrue(r.highlights.any { it.startsWith("You spend most on Saturdays") })
        assertTrue(r.highlights.any { it.startsWith("Biggest payment: ₹2,000 to Amazon on 10 Oct") })
        assertTrue(r.highlights.size <= 5)
        assertTrue("Highlights are for the whole range only", build(filter = ReportFilter(categoryId = Cat.FOOD)).highlights.isEmpty())
    }

    @Test fun incomeAndExpensesForTheMonth() {
        val pl = build().incomeExpenses!!
        assertEquals("October 2026", pl.title)
        assertEquals(5_050_000L, pl.moneyIn)
        assertEquals(listOf(Cat.INCOME to 5_000_000L, Cat.SHOPPING to 50_000L), pl.income.map { it.category?.id to it.paise })
        assertEquals(280_000L, pl.spent)
        assertEquals(listOf(Cat.SHOPPING to 200_000L, Cat.FOOD to 80_000L), pl.spending.map { it.category?.id to it.paise })
        assertEquals(listOf(1, 2), pl.spending.map { it.count })
        assertEquals(4_770_000L, pl.net)
        assertEquals(94, pl.keptPct)
        assertEquals(1_000_000L, pl.transfersOut)  // "To savings" is a transfer: neither money in nor spending
        assertEquals(0L, pl.transfersIn)
        assertEquals(1, pl.leftOut)                // the excluded Uber ride
        assertTrue(pl.months.isEmpty())
    }

    @Test fun incomeAndExpensesMonthByMonth() {
        val pl = build(ReportRange.THREE).incomeExpenses!!
        assertEquals("Aug 2026 – Oct 2026", pl.title)
        assertEquals(listOf("Aug", "Sep", "Oct"), pl.months.map { it.label })
        assertEquals(listOf(0L, 0L, 5_050_000L), pl.months.map { it.moneyIn })
        assertEquals(listOf(0L, 200_000L, 280_000L), pl.months.map { it.spent })
        assertEquals(-200_000L, pl.months[1].net)
        assertEquals(480_000L, pl.spent)
        assertEquals(4_570_000L, pl.net)
    }

    @Test fun incomeAndExpensesFollowsAPickedDayButNotACategory() {
        val day = build(filter = ReportFilter(bucket = 13)).incomeExpenses!!  // Wed 14 Oct: only the refund
        assertEquals("Wed 14 Oct", day.title)
        assertEquals(50_000L, day.moneyIn)
        assertEquals(0L, day.spent)
        assertNull("Nothing came in…", build(filter = ReportFilter(bucket = 2)).incomeExpenses!!.keptPct)
        assertNull(build(filter = ReportFilter(categoryId = Cat.FOOD)).incomeExpenses)
        assertNull(build(filter = ReportFilter(weekday = DayOfWeek.MONDAY)).incomeExpenses)
    }

    @Test fun moneyInLeftOutByTheUserIsNotIncome() {
        val extra = txns + tx(oct(16), 70_000, "Ramesh", Cat.OTHERS, Direction.CREDIT, excluded = true)
        val r = ReportBuilder.build(extra, cats, listOf(period(0)), listOf(period(1)), 0L, ReportRange.ONE, ReportFilter(), today, zone)
        assertEquals(5_050_000L, r.received)
        assertEquals(280_000L, r.spent)
        assertEquals(2, r.incomeExpenses!!.leftOut)
    }

    @Test fun withNothingPickedTheBiggestPaymentsAreListed() {
        val r = build()
        assertEquals(listOf(200_000L, 50_000L, 30_000L), r.matches.map { it.amountPaise })
    }

    @Test fun aStaleBarIndexIsIgnored() {
        val r = build(ReportRange.THREE, ReportFilter(bucket = 25))
        assertNull(r.filter.bucket)
        assertTrue(r.chips.isEmpty())
    }

    @Test fun lettersForMerchantsWithoutAnApp() {
        assertEquals("RK", com.owl.tracker.ui.components.initials("Ramesh Kumar"))
        assertEquals("Z", com.owl.tracker.ui.components.initials("zomato"))
        assertEquals("7E", com.owl.tracker.ui.components.initials("7-Eleven"))
        assertEquals("?", com.owl.tracker.ui.components.initials("  "))
    }
}
