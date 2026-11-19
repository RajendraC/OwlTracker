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

    /** The tester's card: bill on the 1st, ₹31,742 billed, ₹31,780 paid on 3 Nov, ₹27,439 spent since. */
    @Test
    fun aBillPaymentOnTheCardIsNeverARefundAndTheExtraPaidComesOffTheNextBill() {
        val txns = listOf(
            tx(31_780, "2026-10-20"), tx(38, "2026-10-25", Direction.CREDIT, TxKind.REFUND),
            // The account's SMS names the card; the card's own SMS came in as plain money back
            tx(31_780, "2026-11-03", kind = TxKind.CARD_BILL_PAYMENT, label = "HDFC Bank ••4821", body = "Rs 31780 debited for ICICI credit card XX1234"),
            tx(31_780, "2026-11-03", Direction.CREDIT, TxKind.NORMAL, body = "Dear Customer, Payment of INR 31,780.00 has been received on your ICICI Bank Credit Card XX1234"),
            tx(27_439, "2026-11-10"),
        )
        val today = LocalDate.parse("2026-11-19")
        val b = CardBills.compute(card, CardCycle(1), txns, emptyList(), today, zone)
        assertEquals(31_742_00L, b.last.netPaise)
        assertTrue(b.paidInFull)
        assertEquals(listOf(31_780_00L), b.payments.map { it.paise }) // counted once
        assertEquals(0L, b.current.refundsPaise) // not a refund on the next bill
        assertEquals(38_00L, b.current.extraPaidPaise)
        assertEquals(27_401_00L, b.current.netPaise)
        val credit = b.credits.single { it.txn.amountPaise == 31_780_00L }
        assertTrue(credit.payment)
        assertEquals(CardCredit.Why.SMS_SAYS_PAYMENT, credit.why)

        // No payment words, but the same money left a bank account for this card: still a payment
        val plain = txns.map { if (it.direction == Direction.CREDIT && it.amountPaise == 31_780_00L) it.copy(smsBody = "Rs 31780 credited to your card XX1234") else it }
        val b2 = CardBills.compute(card, CardCycle(1), plain, emptyList(), today, zone)
        assertEquals(CardCredit.Why.MATCHES_ACCOUNT_PAYMENT, b2.credits.single { it.txn.amountPaise == 31_780_00L }.why)
        assertEquals(27_401_00L, b2.current.netPaise)

        // The user says it was a refund after all: it comes off the next bill, and the bill stays paid by the account
        val id = credit.txn.id
        val b3 = CardBills.compute(card, CardCycle(1), txns, emptyList(), today, zone, mapOf(id to false))
        assertEquals(CardCredit.Why.YOU, b3.credits.single { it.txn.id == id }.why)
        assertEquals(31_780_00L, b3.current.refundsPaise)
        // And the ₹38 refund called a payment: it no longer lowers the 1 Nov bill
        val refundId = b.credits.single { it.txn.amountPaise == 38_00L }.txn.id
        val b4 = CardBills.compute(card, CardCycle(1), txns, emptyList(), today, zone, mapOf(refundId to true))
        assertEquals(31_780_00L, b4.last.netPaise)
        assertEquals(0L, b4.current.extraPaidPaise)
    }

    @Test
    fun paymentWordsAndTheSavedChoices() {
        assertTrue(CardBills.saysPayment("Thank you for your payment of Rs 5,000 towards your credit card"))
        assertTrue(CardBills.saysPayment("Payment received on your SBI Card ending 1234"))
        assertTrue(CardBills.saysPayment("We have received payment of Rs.2,000 for your HDFC Bank Credit Card"))
        assertFalse(CardBills.saysPayment("Refund of Rs 499 credited to your credit card XX1234"))
        assertFalse(CardBills.saysPayment("Rs 120 cashback credited to card XX1234"))
        val m = mapOf(5L to true, 9L to false)
        assertEquals(m, CardBills.creditKindsFromJson(CardBills.creditKindsToJson(m)))
        assertEquals(mapOf(5L to true), CardBills.creditKindsFromJson("""{"5":"payment","x":"refund","7":"other"}"""))
    }

}
