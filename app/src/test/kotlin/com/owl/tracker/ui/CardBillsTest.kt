package com.owl.tracker.ui

import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.core.TxKind
import com.owl.tracker.data.Source
import com.owl.tracker.data.Txn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/** Still to pay on a credit card: bill periods from the billing cycle, payments found and added. (Robolectric for org.json.) */
@RunWith(RobolectricTestRunner::class)
class CardBillsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val card = "ICICI Bank card ••1234"
    private var nextId = 1L

    private fun tx(rupees: Long, on: String, dir: Direction = Direction.DEBIT, kind: TxKind = TxKind.NORMAL, label: String? = card, body: String? = null) = Txn(
        id = nextId++, amountPaise = rupees * 100, direction = dir,
        occurredAt = LocalDate.parse(on).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        merchantName = "M", merchantKey = "m", merchantPackage = null, vpa = null, categoryId = Cat.SHOPPING,
        accountLabel = label, senderGroup = null, sender = null, refNo = null, source = Source.SMS, smsId = null,
        needsReview = false, excluded = false, note = null, kind = kind, smsBody = body,
    )

    @Test
    fun billDatesFollowTheCycleIncludingShortMonths() {
        val c = CardCycle(20, 18)
        assertEquals(LocalDate.parse("2026-11-20"), c.lastBillOnOrBefore(LocalDate.parse("2026-11-20")))
        assertEquals(LocalDate.parse("2026-10-20"), c.lastBillOnOrBefore(LocalDate.parse("2026-11-17")))
        assertEquals(LocalDate.parse("2026-12-20"), c.nextBillAfter(LocalDate.parse("2026-11-20")))
        val last = CardCycle(CardCycle.LAST_DAY)
        assertEquals(LocalDate.parse("2026-10-31"), last.lastBillOnOrBefore(LocalDate.parse("2026-11-17")))
        assertEquals(LocalDate.parse("2027-02-28"), last.nextBillAfter(LocalDate.parse("2027-01-31")))
        assertEquals(LocalDate.parse("2026-09-30"), last.billBefore(LocalDate.parse("2026-10-31")))
        assertEquals("the 20th", c.dayWords)
        assertEquals("the 1st", CardCycle(1).dayWords)
        assertEquals("the 22nd", CardCycle(22).dayWords)
        assertEquals("the 13th", CardCycle(13).dayWords)
        assertEquals("the last day", last.dayWords)
    }

    @Test
    fun stillToPayIsTheLastBillLessWhatWasPaidSince() {
        val txns = listOf(
            tx(20_000, "2026-10-05"), tx(9_000, "2026-10-20"), // on the bill of 20 Oct (21 Sep – 20 Oct)
            tx(1_000, "2026-10-12", Direction.CREDIT, TxKind.REFUND), // a refund on it
            tx(3_000, "2026-09-20"), // the bill before
            tx(4_000, "2026-11-02"), // after the bill: the next one
            // Paid on 7 Nov: the card's own SMS, and the account's SMS for the same payment (counted once)
            tx(10_000, "2026-11-07", Direction.CREDIT, TxKind.CARD_BILL_PAYMENT),
            tx(10_000, "2026-11-06", kind = TxKind.CARD_BILL_PAYMENT, label = "HDFC Bank ••4821", body = "Rs 10000 paid to ICICI Credit Card XX1234 from A/c 4821"),
            // Paid from an account only, naming the card: counted
            tx(2_000, "2026-11-10", kind = TxKind.CARD_BILL_PAYMENT, label = "SBI ••3307", body = "Rs 2000 debited for credit card bill 1234"),
            // Another card's bill: not this one
            tx(5_000, "2026-11-10", kind = TxKind.CARD_BILL_PAYMENT, label = "SBI ••3307", body = "Rs 5000 debited for credit card bill 9012"),
        )
        val today = LocalDate.parse("2026-11-17")
        val b = CardBills.compute(card, CardCycle(20, 18), txns, emptyList(), today, zone)
        assertEquals(LocalDate.parse("2026-09-21"), b.last.start)
        assertEquals(LocalDate.parse("2026-10-20"), b.last.end)
        assertEquals(29_000_00L, b.last.chargedPaise)
        assertEquals(1_000_00L, b.last.refundsPaise)
        assertEquals(28_000_00L, b.last.netPaise)
        assertEquals(listOf(10_000_00L, 2_000_00L), b.payments.map { it.paise })
        assertEquals(16_000_00L, b.stillToPayPaise)
        assertEquals(LocalDate.parse("2026-11-07"), b.dueDate)
        assertFalse(b.paidInFull)
        assertEquals(4_000_00L, b.current.netPaise)
        assertEquals(LocalDate.parse("2026-11-20"), b.nextBill)

        // Added by the user: a part payment, then the whole bill
        val part = CardPayment(LocalDate.parse("2026-10-20"), 6_000_00, 0)
        val b2 = CardBills.compute(card, CardCycle(20, 18), txns, listOf(part), today, zone)
        assertEquals(10_000_00L, b2.stillToPayPaise)
        assertEquals(0, b2.payments.last().manualIndex)
        val old = CardPayment(LocalDate.parse("2026-09-20"), 50_000_00, 0) // for another bill: ignored
        val full = CardPayment(LocalDate.parse("2026-10-20"), 0, 0, full = true)
        val b3 = CardBills.compute(card, CardCycle(20, 18), txns, listOf(old, part, full), today, zone)
        assertTrue(b3.markedPaid)
        assertEquals(0L, b3.stillToPayPaise)
        assertTrue(b3.paidInFull)
    }

    @Test
    fun savedCyclesAndPaymentsReadBackAndBadOnesAreDropped() {
        val cycles = mapOf(card to CardCycle(20, 18), "HDFC Bank card ••9012" to CardCycle(CardCycle.LAST_DAY, 22))
        assertEquals(cycles, CardBills.cyclesFromJson(CardBills.cyclesToJson(cycles)))
        assertEquals(mapOf(card to CardCycle(5)), CardBills.cyclesFromJson("""{"$card":{"bill":5},"x":{"bill":40}}"""))
        assertEquals(emptyMap<String, CardCycle>(), CardBills.cyclesFromJson("not json"))
        val pays = mapOf(card to listOf(CardPayment(LocalDate.parse("2026-10-20"), 500_00, 123L), CardPayment(LocalDate.parse("2026-10-20"), 0, 456L, true)))
        assertEquals(pays, CardBills.paymentsFromJson(CardBills.paymentsToJson(pays)))
    }
}
