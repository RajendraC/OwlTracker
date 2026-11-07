package com.owl.tracker.store

import com.owl.tracker.core.SmsMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * Six months of made-up bank, card and UPI alerts for one person in Chennai, used only to fill the
 * emulator's inbox for store screenshots and the promo video. Account numbers, references and amounts
 * are invented; the message formats are the ones in SampleSms, which the parser tests cover.
 *
 * Pure Kotlin (no Android imports) so it can be checked against the parser on any JVM.
 * The same seed always gives the same messages relative to [now].
 */
object DemoInbox {

    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun messages(nowMillis: Long, days: Int = 172, seed: Int = 337): List<SmsMessage> {
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZONE)
        val g = Gen(Random(seed), now)
        val first = now.toLocalDate().minusDays(days.toLong())
        var d = first
        while (!d.isAfter(now.toLocalDate())) {
            g.day(d)
            d = d.plusDays(1)
        }
        return g.out.filter { it.timestamp <= nowMillis - 20 * 60_000L }.sortedBy { it.timestamp }
            .mapIndexed { i, m -> m.copy(id = 9000L + i) }
    }

    private class Gen(val r: Random, val now: ZonedDateTime) {
        val out = ArrayList<SmsMessage>()
        private var ref = 629_100_000_000L + r.nextLong(0, 99_999_999)

        private fun nextRef(): String { ref += r.nextLong(1_000, 90_000); return ref.toString() }
        private fun at(d: LocalDate, h1: Int, h2: Int): ZonedDateTime =
            d.atStartOfDay(ZONE).plusMinutes(r.nextLong(h1 * 60L, h2 * 60L))
        private fun amt(lo: Int, hi: Int, step: Int = 1): Int = (r.nextInt(lo, hi + 1) / step) * step
        private fun chance(p: Double) = r.nextDouble() < p

        private fun rs(a: Int) = "$a.00"
        private fun inr(a: Int): String = indian(a) + ".00"
        private fun indian(a: Int): String {
            val s = a.toString()
            if (s.length <= 3) return s
            val last3 = s.takeLast(3)
            val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            return "$rest,$last3"
        }

        private fun f(t: ZonedDateTime, p: String) = t.format(DateTimeFormatter.ofPattern(p, Locale.ENGLISH))
        private fun add(sender: String, t: ZonedDateTime, body: String) {
            out += SmsMessage(0, sender, body, t.toInstant().toEpochMilli())
        }

        // HDFC savings account 4821 (salary account, most UPI)
        fun hdfcUpi(t: ZonedDateTime, a: Int, to: String) = add(
            "VM-HDFCBK-S",
            t,
            "Sent Rs.${rs(a)}\nFrom HDFC Bank A/C *4821\nTo $to\nOn ${f(t, "dd/MM/yy")}\nRef ${nextRef()}\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
        )

        // HDFC credit card 9012
        fun hdfcCard(t: ZonedDateTime, a: Int, at: String) = add(
            "VM-HDFCBK-T",
            t,
            "Spent Rs.$a On HDFC Bank Card 9012 At $at On ${f(t, "yyyy-MM-dd:HH:mm:ss")} Bal Rs.${150000 - a} Not You? Call 18002586161/SMS BLOCK DC 9012 to 7308080808",
        )

        // ICICI savings account 710
        fun iciciUpi(t: ZonedDateTime, a: Int, to: String) = add(
            "JD-ICICIT-S",
            t,
            "ICICI Bank Acct XX710 debited for Rs ${inr(a)} on ${f(t, "dd-MMM-yy")}; $to credited. UPI:${nextRef()}. Call 18002662 for dispute. SMS BLOCK 710 to 9215676766.",
        )

        // ICICI credit card 1234
        fun iciciCard(t: ZonedDateTime, a: Int, on: String) = add(
            "VM-ICICIT-T",
            t,
            "INR ${inr(a)} spent using ICICI Bank Card XX1234 on ${f(t, "dd-MMM-yy")} on $on. Avl Limit: INR 1,42,310.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766",
        )

        // SBI savings account 3307 (bills)
        fun sbiUpi(t: ZonedDateTime, a: Int, to: String) = add(
            "AD-SBIUPI-S",
            t,
            "Dear UPI user A/C X3307 debited by $a.0 on date ${f(t, "ddMMMyy")} trf to $to Refno ${nextRef()}. If not u? call 1800111109. -SBI",
        )

        // Axis savings account 5521 (subscriptions)
        fun axisUpi(t: ZonedDateTime, a: Int, to: String) = add(
            "AX-AXISBK-S",
            t,
            "INR ${inr(a)} debited\nA/c no. XX5521\n${f(t, "dd-MM-yy, HH:mm:ss")}\nUPI/P2M/${nextRef()}/$to\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
        )

        // Kotak savings account 8899 (rides, paid to a UPI ID)
        fun kotakUpi(t: ZonedDateTime, a: Int, vpa: String) = add(
            "VK-KOTAKB-S",
            t,
            "Sent Rs.${rs(a)} from Kotak Bank AC X8899 to $vpa on ${f(t, "dd-MM-yy")}.UPI Ref ${nextRef()}. Not you, call 18602662666",
        )

        fun day(d: LocalDate) {
            val dom = d.dayOfMonth
            val festive = d.monthValue == 11 && dom in 1..10 // Diwali shopping, 8 Nov 2026

            // Monthly
            if (dom == 1) add("VM-HDFCBK-S", at(d, 9, 11), "Rs.120000.00 credited to HDFC Bank A/c XX4821 on ${f(at(d, 9, 10), "dd-MM-yy")} by NEFT from ACME SOFTWARE (salary). Avl Bal Rs.1,46,250.00")
            if (dom == 5) add("VM-HDFCBK-S", at(d, 6, 8), "Rs.10000.00 debited from A/c XX4821 on ${f(at(d, 6, 7), "dd-MM-yy")} towards SIP Groww Mutual Fund. Avl Bal Rs.1,36,250.00")
            if (dom == 3) sbiUpi(at(d, 19, 22), 1179, "ACT FIBERNET")
            if (dom == 12) axisUpi(at(d, 6, 9), 649, "NETFLIX")
            if (dom == 18) axisUpi(at(d, 6, 9), 139, "SPOTIFY")
            if (dom == 2) axisUpi(at(d, 7, 9), 1499, "CULT.FIT")
            if (dom == 16) hdfcUpi(at(d, 10, 12), 349, "AIRTEL")
            if (dom == 9 && d.monthValue % 2 == 1) sbiUpi(at(d, 18, 21), amt(1480, 2640, 10), "ELECTRICITY BOARD")
            if (dom == 20) add("VM-HDFCBK-T", at(d, 11, 13), "Payment of Rs.${inr(amt(14000, 24000, 100))} received towards your HDFC Bank Credit Card ending 9012 on ${f(at(d, 11, 12), "dd-MM-yy")}. Thank you.")
            if (dom == 7 || dom == 23) add("VM-HDFCBK-S", at(d, 18, 20), "Rs.3000.00 withdrawn at ATM ANNA NAGAR CHENNAI from A/c XX4821 on ${f(at(d, 18, 19), "dd-MM-yy")}. Avl Bal Rs.41,250.00. Not you? Call 18002586161")

            // Food: about 18 orders a month
            if (chance(0.34)) hdfcUpi(at(d, 19, 22), amt(220, 640), "SWIGGY")
            if (chance(0.20)) hdfcUpi(at(d, 12, 14), amt(240, 780), "zomato@hdfcbank")
            if (chance(0.07)) iciciUpi(at(d, 20, 22), amt(499, 899), "DOMINOS PIZZA")
            if (chance(0.10)) hdfcUpi(at(d, 8, 10), amt(90, 260), "MEENAKSHI BHAVAN")
            if (chance(0.06)) hdfcUpi(at(d, 16, 18), amt(140, 320), "KAAPI CORNER CAFE")

            // Groceries
            if (chance(0.16)) hdfcUpi(at(d, 8, 11), amt(260, 1450), "BLINKIT")
            if (chance(0.13)) iciciUpi(at(d, 17, 20), amt(220, 1240), "ZEPTO")
            if (chance(0.05)) iciciUpi(at(d, 10, 12), amt(1800, 3200), "BIGBASKET")
            if (chance(0.07)) hdfcUpi(at(d, 18, 20), amt(180, 640), "GREEN LEAF FRESH MART")

            // Shopping
            if (chance(if (festive) 0.45 else 0.10)) iciciCard(at(d, 20, 23), amt(349, if (festive) 4299 else 2499), "AMAZON")
            if (chance(if (festive) 0.25 else 0.035)) hdfcCard(at(d, 21, 23), amt(1299, 2899), "MYNTRA")
            if (chance(0.03)) iciciCard(at(d, 13, 16), amt(599, 2199), "FLIPKART")

            // Getting around
            if (chance(0.15)) hdfcCard(at(d, 8, 10), amt(180, 520), "UBER INDIA")
            if (chance(0.26)) kotakUpi(at(d, 18, 21), amt(48, 160), "rapido@ybl")
            if (chance(0.08)) kotakUpi(at(d, 9, 11), amt(90, 210), "nammayatri@ybl")
            if (dom == 6 || dom == 21) hdfcCard(at(d, 18, 21), amt(1200, 1900, 10), "HP PETROL PUMP ANNA NAGAR")

            // Health, fun, travel
            if (chance(0.04)) iciciUpi(at(d, 18, 21), amt(380, 1250), "APOLLO PHARMACY")
            if (chance(0.04)) iciciCard(at(d, 13, 18), amt(420, 1180), "BOOKMYSHOW")
            if (d.monthValue == 9 && dom == 14) hdfcUpi(at(d, 21, 23), 1845, "IRCTC")
            if (d.monthValue == 10 && dom == 4) iciciCard(at(d, 22, 23), 6240, "INDIGO AIRLINES")
            if (d.monthValue == 11 && dom == 2) hdfcUpi(at(d, 21, 23), 2140, "IRCTC")
            if (d.monthValue == 10 && dom == 26) add("VM-HDFCBK-T", at(d, 12, 14), "Refund of Rs.799.00 from AMAZON credited to your HDFC Bank Credit Card ending 9012 on ${f(at(d, 12, 13), "dd-MM-yy")}")
        }
    }
}
