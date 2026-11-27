package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringTest {
    private val today = LocalDate.of(2026, 11, 27)
    private fun rs(r: Long) = r * 100
    private fun seen(key: String, y: Int, m: Int, d: Int, r: Long, cat: String = Cat.FOOD) = Seen(key, key.uppercase(), LocalDate.of(y, m, d), rs(r), cat)

    @Test
    fun monthlyBillsAreFoundWithTheirNextDate() {
        val netflix = (6..11).map { seen("netflix", 2026, it, 5, 649, Cat.SUBSCRIPTIONS) }
        val emi = (8..11).map { seen("hdfc emi", 2026, it, 1, 18500, Cat.EMI) }
        // Swiggy: often, but not on a steady gap or amount
        val swiggy = listOf(seen("swiggy", 2026, 11, 2, 389), seen("swiggy", 2026, 11, 3, 1200), seen("swiggy", 2026, 11, 9, 450), seen("swiggy", 2026, 11, 20, 610))
        val bills = Recurring.detect(netflix + emi + swiggy, today)
        assertEquals(listOf("hdfc emi", "netflix"), bills.map { it.key })
        val n = bills.single { it.key == "netflix" }
        assertEquals(LocalDate.of(2026, 12, 5), n.next)
        assertEquals("every month", n.cadence)
        assertTrue(n.steady)
        assertEquals(rs(649), n.paise)
        assertEquals(LocalDate.of(2026, 12, 1), bills.first().next)
    }

    @Test
    fun billsThatMoveAroundAreAboutAndStoppedOnesAreLeftOut() {
        // Electricity every two months, ₹2,100 to ₹2,600
        val power = listOf(seen("bescom", 2026, 5, 14, 2100, Cat.UTILITIES), seen("bescom", 2026, 7, 14, 2480, Cat.UTILITIES), seen("bescom", 2026, 9, 13, 2600, Cat.UTILITIES), seen("bescom", 2026, 11, 14, 2300, Cat.UTILITIES))
        // A gym that stopped in July
        val gym = (3..7).map { seen("gym", 2026, it, 10, 1500, Cat.SPORTS) }
        val bills = Recurring.detect(power + gym, today)
        val p = bills.single()
        assertEquals("bescom", p.key)
        assertFalse(p.steady)
        assertEquals("every 2 months", p.cadence)
        // Upcoming within 30 days from the 27th: nothing (next is mid-January)
        assertTrue(Recurring.upcoming(bills, today, 30).isEmpty())
        assertEquals(1, Recurring.upcoming(bills, today, 60).size)
    }
}
