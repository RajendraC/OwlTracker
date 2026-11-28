package com.owl.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Runs under Robolectric for Android's org.json. */
@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(application = com.owl.tracker.TestApp::class)
class HouseholdTest {
    private val today = LocalDate.parse("2026-11-28")
    private val days = mapOf(today to mapOf("food" to 500_00L, "groceries" to 1200_00L), today.minusDays(3) to mapOf("food" to 300_00L))
    private val pays = listOf(HousePayment(today, "Swiggy", 500_00, "food"), HousePayment(today, "DMart", 1200_00, "groceries"))

    private fun problem(block: () -> Unit): HouseholdException.Problem {
        try { block() } catch (e: HouseholdException) { return e.problem }
        fail("expected a HouseholdException"); throw IllegalStateException()
    }

    @Test fun inviteJoinAndUpdateRoundTrip() {
        val raj = Households.start("Our home", "Raj", shareAll = false, budgetPaise = 40_000_00, now = 1000)
        val invite = Households.peek(Households.invite(raj)) as HouseFile.Invite
        assertEquals("Raj", invite.fromName)
        val priya = Households.join(invite, "Priya", shareAll = true)
        assertEquals(raj.id, priya.id)
        assertEquals(40_000_00, priya.budgetPaise)

        // Priya's update reaches Raj: her days and (she shares all) her payments
        val fromPriya = Households.peek(Households.update(priya, 2000, days, emptyMap(), pays)) as HouseFile.Update
        val raj2 = Households.apply(raj, fromPriya, today)
        assertEquals(1, raj2.others.size)
        val m = raj2.others.single()
        assertEquals("Priya", m.name)
        assertEquals(2000_00L, Households.spentBetween(m, today.minusDays(5), today).values.sum())
        assertEquals(2, m.payments.size)

        // Raj shares totals only: Priya gets no payments from him
        val fromRaj = Households.peek(Households.update(raj2, 3000, days, mapOf("c_custom" to "Pets"), pays)) as HouseFile.Update
        val priya2 = Households.apply(priya, fromRaj, today)
        val r = priya2.others.single { it.name == "Raj" }
        assertTrue(r.payments.isEmpty())
        assertEquals(3000, r.updatedAt)
    }

    @Test fun olderUpdateChangesNothing() {
        val a = Households.start("Home", "A", false, 0, 1)
        val b = Households.join(Households.peek(Households.invite(a)) as HouseFile.Invite, "B", false)
        val newer = Households.peek(Households.update(b, 5000, days, emptyMap(), emptyList())) as HouseFile.Update
        val older = Households.peek(Households.update(b, 4000, emptyMap(), emptyMap(), emptyList())) as HouseFile.Update
        val a2 = Households.apply(a, newer, today)
        assertEquals(a2, Households.apply(a2, older, today))
    }

    @Test fun otherHouseholdTamperedOwnAndFullAreRefused() {
        val a = Households.start("Home", "A", false, 0, 1)
        val other = Households.start("Elsewhere", "X", false, 0, 1)
        val fromOther = Households.peek(Households.update(other, 10, days, emptyMap(), emptyList())) as HouseFile.Update
        assertEquals(HouseholdException.Problem.OTHER_HOUSEHOLD, problem { Households.apply(a, fromOther, today) })

        // Same id, wrong key (a forged update): it can't be opened
        val forged = other.copy(id = a.id)
        val fromForged = Households.peek(Households.update(forged, 10, days, emptyMap(), emptyList())) as HouseFile.Update
        assertEquals(HouseholdException.Problem.DAMAGED, problem { Households.apply(a, fromForged, today) })

        val own = Households.peek(Households.update(a, 10, days, emptyMap(), emptyList())) as HouseFile.Update
        assertEquals(HouseholdException.Problem.OWN, problem { Households.apply(a, own, today) })

        var full = a
        repeat(3) { i ->
            val m = Households.join(Households.peek(Households.invite(a)) as HouseFile.Invite, "M$i", false)
            full = Households.apply(full, Households.peek(Households.update(m, 10L + i, days, emptyMap(), emptyList())) as HouseFile.Update, today)
        }
        assertEquals(3, full.others.size)
        val fifth = Households.join(Households.peek(Households.invite(a)) as HouseFile.Invite, "Fifth", false)
        val fromFifth = Households.peek(Households.update(fifth, 99, days, emptyMap(), emptyList())) as HouseFile.Update
        assertEquals(HouseholdException.Problem.FULL, problem { Households.apply(full, fromFifth, today) })
    }

    @Test fun notAHouseholdFile() {
        assertEquals(HouseholdException.Problem.NOT_HOUSEHOLD, problem { Households.peek("{\"app\":\"owltracker\"}".toByteArray()) })
        assertEquals(HouseholdException.Problem.NOT_HOUSEHOLD, problem { Households.peek(byteArrayOf(1, 2, 3)) })
        assertEquals(HouseholdException.Problem.TOO_NEW, problem { Households.peek("{\"app\":\"owltracker-household\",\"v\":99}".toByteArray()) })
    }

    @Test fun newerBudgetIsTakenAndStoredHouseholdRoundTrips() {
        val a = Households.start("Home", "A", false, 30_000_00, 100)
        val b = Households.join(Households.peek(Households.invite(a)) as HouseFile.Invite, "B", true).copy(budgetPaise = 35_000_00, budgetAt = 200)
        val a2 = Households.apply(a, Households.peek(Households.update(b, 300, days, mapOf("c1" to "Pets"), pays)) as HouseFile.Update, today)
        assertEquals(35_000_00, a2.budgetPaise)
        val back = Households.fromJson(Households.toJson(a2))
        assertEquals(a2, back)
        assertNotEquals(a.key, Households.start("Home", "A", false, 0, 1).key)
    }

    @Test fun oldDaysAreDropped() {
        val a = Households.start("Home", "A", false, 0, 1)
        val b = Households.join(Households.peek(Households.invite(a)) as HouseFile.Invite, "B", false)
        val old = mapOf(today.minusDays(200) to mapOf("food" to 100L))
        val a2 = Households.apply(a, Households.peek(Households.update(b, 5, old + days, emptyMap(), emptyList())) as HouseFile.Update, today)
        assertTrue(a2.others.single().days.keys.none { it.isBefore(today.minusDays(Households.DAYS)) })
    }
}
