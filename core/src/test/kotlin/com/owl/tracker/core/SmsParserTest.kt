package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SmsParserTest {

    private val now = 1_792_300_000_000L

    @Test
    fun everySampleParsesOrIsSkippedAsExpected() {
        val failures = ArrayList<String>()
        SampleSms.all.forEachIndexed { i, s ->
            val sms = SmsMessage(i.toLong(), s.address, s.body, now - s.minutesAgo * 60_000)
            when (val r = SmsParser.parse(sms)) {
                is ParseResult.Rejected -> {
                    if (s.expectReject == null) failures += "#$i ${s.address}: rejected as ${r.reason}, expected a transaction"
                    else if (s.expectReject != r.reason) failures += "#$i ${s.address}: rejected as ${r.reason}, expected ${s.expectReject}"
                }
                is ParseResult.Accepted -> {
                    val t = r.tx
                    if (s.expectReject != null) { failures += "#$i ${s.address}: accepted, expected reject ${s.expectReject}"; return@forEachIndexed }
                    if (s.expectAmountPaise != null && t.amountPaise != s.expectAmountPaise) failures += "#$i amount ${t.amountPaise} != ${s.expectAmountPaise}"
                    if (s.expectDirection != null && t.direction != s.expectDirection) failures += "#$i direction ${t.direction} != ${s.expectDirection}"
                    if (s.expectLast4 != null && t.accountLast4 != s.expectLast4) failures += "#$i last4 ${t.accountLast4} != ${s.expectLast4}"
                    if (s.expectMerchant != null) {
                        val known = MerchantRegistry.match(t.merchantRaw, t.vpa)
                        val name = known?.displayName ?: t.merchantRaw
                        if (name != s.expectMerchant) failures += "#$i merchant '$name' (raw '${t.merchantRaw}') != '${s.expectMerchant}'"
                    }
                    if (s.expectCategory != null) {
                        val known = MerchantRegistry.match(t.merchantRaw, t.vpa)
                        val key = MerchantRegistry.keyFor(known, t.merchantRaw, t.vpa)
                        val cat = Categorizer().categorize(t, key, known)
                        if (cat != s.expectCategory) failures += "#$i category $cat != ${s.expectCategory} (merchant '${t.merchantRaw}', kind ${t.kind})"
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun hdfcUpiFieldsAreExtracted() {
        val t = accepted("VM-HDFCBK-S", SampleSms.all[0].body)
        assertEquals("hdfc", t.senderGroup)
        assertEquals("629112345678", t.reference)
        assertFalse(t.isCard)
        assertFalse(t.needsReview)
    }

    @Test
    fun cardSpendIsMarkedAsCardAndBalanceIsNotTheAmount() {
        val t = accepted("VM-HDFCBK-T", SampleSms.all[1].body)
        assertTrue(t.isCard)
        assertEquals(31200L, t.amountPaise)
        assertEquals(14231000L, t.balancePaise)
    }

    @Test
    fun earliestVerbWinsWhenBothDebitAndCreditAppear() {
        val t = accepted("JD-ICICIT-S", SampleSms.all[2].body)
        assertEquals(Direction.DEBIT, t.direction)
    }

    @Test
    fun genuineDebitWithOtpWarningIsKept() {
        val t = accepted("VM-HDFCBK-S", "Rs.250.00 debited from A/c XX4821 to CHAI POINT on 18-10-26. Never share your OTP or PIN with anyone. -HDFC Bank")
        assertEquals(25000L, t.amountPaise)
    }

    @Test
    fun failedPaymentRefundCreditIsKept() {
        val t = accepted("VM-ICICIT-S", "Rs 350.00 has been reversed to your ICICI Bank Acct XX710 for the failed transaction to xyz@ybl on 18-Oct-26.")
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals(TxKind.REFUND, t.kind)
    }

    @Test
    fun unmarkedOldHeaderStillParses() {
        val t = accepted("VM-HDFCBK", "Rs.99.00 debited from A/c XX4821 to SPOTIFY on 10-10-26.")
        assertEquals("hdfc", t.senderGroup)
    }

    @Test
    fun unknownServiceSenderIsAcceptedButFlaggedForReview() {
        val t = accepted("VM-XYZPAY-S", "Rs.120 debited from your wallet for order 5512 on 10-10-26.")
        assertEquals(Institutions.UNRECOGNISED, t.senderGroup)
        assertTrue(t.needsReview)
    }

    @Test
    fun personalMessagesAreNeverRead() {
        val r = SmsParser.parse(SmsMessage(1, "9876543210", "Rs 500 debited from my account lol", now))
        assertTrue(r is ParseResult.Rejected && r.reason == RejectReason.PERSONAL_SENDER)
    }

    @Test
    fun moneySentToYourAccountIsMoneyIn() {
        // Testers saw these counted as spending: "sent", "paid" and "transferred to" read as money out.
        val cases = listOf(
            "VM-HDFCBK" to "Rs.5000 transferred to your A/c XX1234 from RAMESH KUMAR via IMPS on 07-11-25. Ref 530912345678",
            "VM-ICICIB" to "INR 500.00 sent to your A/c XX123 by RAMESH on 07-Nov-25 via UPI. Ref 530912345678",
            "VM-PAYTMB" to "Rs 500 paid to you by Ramesh Kumar on Paytm. UPI Ref 530912345678",
            "VM-KOTAKB" to "Rs.5000 is credited to your Kotak Bank a/c XX1234 by IMPS from RAMESH KUMAR on 07/11/25. Ref 530912345678",
            "VM-SBIINB" to "Rs.2,000 trf to ur a/c XX1234 by NEFT from ACME LTD on 07-11-25.",
        )
        cases.forEach { (from, body) -> assertEquals(body, Direction.CREDIT, accepted(from, body).direction) }
        assertEquals("Ramesh Kumar", accepted(cases[0].first, cases[0].second).merchantRaw)
        assertEquals("Ramesh Kumar", accepted(cases[2].first, cases[2].second).merchantRaw)
    }

    @Test
    fun moneySentByYouIsStillMoneyOut() {
        val cases = listOf(
            "VM-HDFCBK" to "Amt Sent Rs.500.00 From HDFC Bank A/C *1234 To Ramesh On 07/11/25 Ref 530912345678 Not You? Call 18002586161",
            "VM-SBIINB" to "Rs.2,000 transferred to RAMESH KUMAR from your A/c XX1234 on 07-11-25.",
            "VM-ICICIB" to "Rs 500 paid to your friend Ramesh from A/c XX123 on 07-Nov-25 via UPI.",
            "VM-HDFCBK" to "Rs.5,000 paid to your credit card XX9012 from HDFC Bank A/c XX1234 on 07-11-25.",
            "VM-AXISBK" to "Rs 999 debited from A/c XX1234; amount sent to your biller AIRTEL on 07-11-25.",
        )
        cases.forEach { (from, body) -> assertEquals(body, Direction.DEBIT, accepted(from, body).direction) }
    }

    @Test
    fun amountsWithAColonAreRead() {
        val t = accepted("VM-UNIONB", "A/c *1234 Credited for Rs:500.00 on 07-11-2025 10:00:00 by Mob Bk ref no 530912345678 Avl Bal Rs:10000.00.Never Share OTP/PIN/CVV-Union Bank of India")
        assertEquals(50_000L, t.amountPaise)
        assertEquals(1_000_000L, t.balancePaise)
        assertEquals(Direction.CREDIT, t.direction)
    }

    @Test
    fun anAmountIsNeverTakenForWhoPaid() {
        assertNull(accepted("VM-SBIUPI", "Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 07Nov25 by (Ref no 530912345678)").merchantRaw)
        assertNull(accepted("VM-SBIINB", "Dear Customer, your a/c no. XXXXXXXX1234 is credited by Rs.75,000.00 on 07-11-25 by a/c linked to mobile 9XXXXXX000-ACME PAYROLL (IMPS Ref no 530912345678).").merchantRaw)
    }

    @Test
    fun cleanMerchantTidiesNames() {
        assertEquals("Uber", SmsParser.cleanMerchant("UBER INDIA SYSTEMS PVT LTD"))
        assertEquals("Swiggy", SmsParser.cleanMerchant("VPA swiggy@icici"))
        assertNull(SmsParser.cleanMerchant("12345"))
    }

    private fun accepted(address: String, body: String): ParsedTransaction {
        val r = SmsParser.parse(SmsMessage(1, address, body, now))
        assertTrue("Expected a transaction but got $r", r is ParseResult.Accepted)
        return (r as ParseResult.Accepted).tx.also { assertNotNull(it) }
    }
}
