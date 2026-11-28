package com.owl.tracker.data

import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.core.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EventsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private var nextId = 1L
    private val cats = Schema.DEFAULT_CATEGORIES.mapIndexed { i, s -> Category(s.id, s.name, s.icon, s.color, s.kind, i, true, false, s.parent) }
    private val tree = CategoryTree(cats)

    private fun tx(day: String, rupees: Int, cat: String, dir: Direction = Direction.DEBIT, parts: List<TxnPart> = emptyList()) = Txn(
        id = nextId++, amountPaise = rupees * 100L, direction = dir, occurredAt = LocalDate.parse(day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        merchantName = "M", merchantKey = "m", merchantPackage = null, vpa = null, categoryId = cat,
        accountLabel = null, senderGroup = null, sender = null, refNo = null, source = Source.MANUAL, smsId = null,
        needsReview = false, excluded = false, note = null, kind = TxKind.NORMAL, smsBody = null, parts = parts,
    )

    private fun day(t: Txn) = java.time.Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate()

    private val diwali = EventBudget("e1", "diwali", "Diwali 2027", "🪔", LocalDate.parse("2027-10-22"), LocalDate.parse("2027-10-31"), 15_000_00, setOf(Cat.GIVING, Cat.FASHION))

    @Test fun categoriesAndGroupsInItsDatesCount() {
        val gift = tx("2027-10-25", 2000, Cat.GIFTS)         // in the Gifts & Giving group
        val clothes = tx("2027-10-26", 3000, Cat.FASHION)
        val groceries = tx("2027-10-26", 900, Cat.GROCERIES) // not chosen
        val before = tx("2027-10-20", 5000, Cat.FASHION)     // before its dates
        val refund = tx("2027-10-27", 400, Cat.FASHION, Direction.CREDIT)
        val s = Events.spend(diwali, listOf(gift, clothes, groceries, before, refund), tree, ::day)
        assertEquals(5000_00, s.totalPaise)
        assertEquals(listOf(clothes, gift), s.payments)
        assertEquals(listOf(Cat.SHOPPING to 3000_00L, Cat.GIVING to 2000_00L), s.byTop)
    }

    @Test fun movingPaymentsInAndOut() {
        val groceries = tx("2027-10-26", 900, Cat.GROCERIES)
        val clothes = tx("2027-10-26", 3000, Cat.FASHION)
        var e = Events.move(diwali, groceries, true, tree)
        e = Events.move(e, clothes, false, tree)
        assertTrue(Events.contains(e, groceries, tree, day(groceries)))
        assertFalse(Events.contains(e, clothes, tree, day(clothes)))
        assertEquals(900_00, Events.spend(e, listOf(groceries, clothes), tree, ::day).totalPaise)
        // back to what the categories say: nothing left over in the lists
        e = Events.move(Events.move(e, groceries, false, tree), clothes, true, tree)
        assertEquals(diwali, e)
    }

    @Test fun onlyTheMatchingPartOfASplitPaymentCounts() {
        val order = tx("2027-10-28", 5000, Cat.SHOPPING, parts = listOf(TxnPart(Cat.FASHION, 3500_00), TxnPart(Cat.GROCERIES, 1500_00)))
        val s = Events.spend(diwali, listOf(order), tree, ::day)
        assertEquals(3500_00, s.totalPaise)
        assertEquals(listOf(order), s.payments)
    }

    @Test fun jsonRoundTripAndRejectsBadInput() {
        val e = Events.move(diwali, tx("2027-10-26", 900, Cat.GROCERIES), true, tree)
        assertEquals(listOf(e), Events.fromJson(Events.toJson(listOf(e))))
        val bad = """[{"id":"x","name":"A","start":"2027-01-05","end":"2027-01-01","budget":100,"categories":["food"]},
            {"id":"y","name":"B","start":"2027-01-01","end":"2027-01-02","budget":0},
            {"id":"z","name":"C","template":"nope","emoji":"💣","start":"2027-01-01","end":"2027-01-02","budget":100,"added":["../etc"]}]"""
        val read = Events.fromJson(bad)
        assertEquals(listOf("z"), read.map { it.id })
        assertEquals("other", read[0].template)
        assertEquals("✨", read[0].emoji)
        assertTrue(read[0].added.isEmpty())
    }

    @Test fun suggestsTheNextDiwaliAndChristmas() {
        assertEquals(LocalDate.parse("2027-10-22") to LocalDate.parse("2027-10-31"), Events.suggestDates("diwali", LocalDate.parse("2026-11-28")))
        assertEquals(LocalDate.parse("2026-11-01") to LocalDate.parse("2026-11-10"), Events.suggestDates("diwali", LocalDate.parse("2026-11-01")))
        assertEquals(LocalDate.parse("2026-12-18") to LocalDate.parse("2026-12-26"), Events.suggestDates("christmas", LocalDate.parse("2026-11-28")))
        assertEquals(LocalDate.parse("2027-01-12") to LocalDate.parse("2027-01-16"), Events.suggestDates("harvest", LocalDate.parse("2026-11-28")))
        val t = LocalDate.parse("2026-11-28")
        assertEquals(t to t.plusDays(7), Events.suggestDates("trip", t))
    }

    @Test fun daysLeft() {
        assertEquals(10, diwali.daysLeft(LocalDate.parse("2027-10-01")))
        assertEquals(2, diwali.daysLeft(LocalDate.parse("2027-10-30")))
        assertEquals(0, diwali.daysLeft(LocalDate.parse("2027-11-01")))
    }
}
