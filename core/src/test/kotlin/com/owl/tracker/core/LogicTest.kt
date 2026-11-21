package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    @Test fun parsesIndianGrouping() {
        assertEquals(12345678L, Money.parseRupeesToPaise("1,23,456.78"))
        assertEquals(218000L, Money.parseRupeesToPaise("2180.0"))
        assertEquals(500L, Money.parseRupeesToPaise("5"))
        assertNull(Money.parseRupeesToPaise("abc"))
        assertNull(Money.parseRupeesToPaise("1.2.3"))
    }

    @Test fun formatsIndianGrouping() {
        assertEquals("₹1,23,457", Money.formatInr(12345678))
        assertEquals("₹1,23,456.78", Money.formatInr(12345678, showPaise = true))
        assertEquals("₹999", Money.formatInr(99900))
        assertEquals("₹75,000", Money.formatInr(7500000))
        assertEquals("₹12,00,000", Money.formatInr(120000000))
        assertEquals("-₹2,340", Money.formatInr(-234000))
        assertEquals("₹73.4k", Money.formatShort(7345000))
        assertEquals("₹1.2L", Money.formatShort(12000000))
    }
}

class SenderTest {
    @Test fun readsTraiSuffix() {
        assertEquals(HeaderType.SERVICE, SenderParser.parse("VM-HDFCBK-S")!!.type)
        assertEquals(HeaderType.TRANSACTIONAL, SenderParser.parse("JD-ICICIT-T")!!.type)
        assertEquals(HeaderType.PROMOTIONAL, SenderParser.parse("VM-HDFCBK-P")!!.type)
        assertEquals(HeaderType.UNMARKED, SenderParser.parse("AD-SBIUPI")!!.type)
        assertEquals("SBIUPI", SenderParser.parse("ad-sbiupi-s")!!.entity)
        assertNull(SenderParser.parse("+919876543210"))
        assertNull(SenderParser.parse("56161"))
    }

    @Test fun readsHeadersInEveryFormatPhonesUse() {
        assertEquals("HDFCBK", SenderParser.parse("HDFCBK")!!.entity)
        assertEquals("HDFCBK", SenderParser.parse("HDFCBK-S")!!.entity)
        assertEquals(HeaderType.SERVICE, SenderParser.parse("HDFCBK-S")!!.type)
        assertEquals("HDFCBK", SenderParser.parse("AXHDFCBK")!!.entity)
        assertEquals("HDFCBANK", SenderParser.parse("HDFCBANK")!!.entity)
        assertEquals("ICICIT", SenderParser.parse("JM-ICICIT-T")!!.entity)
        assertEquals("SBIINB", SenderParser.parse("SBIINB")!!.entity)
        assertNull(SenderParser.parse("9876543210"))
        assertNull(SenderParser.parse("+91 98765 43210"))
    }

    @Test fun mapsEntitiesToBanks() {
        assertEquals("hdfc", Institutions.forEntity("HDFCBK")!!.id)
        assertEquals("sbi", Institutions.forEntity("ATMSBI")!!.id)
        assertEquals("sbicard", Institutions.forEntity("SBICRD")!!.id)
        assertEquals("icici", Institutions.forEntity("ICICIT")!!.id)
        assertNull(Institutions.forEntity("XYZPAY"))
    }
}

class MerchantTest {
    @Test fun specificNamesWinOverBroadOnes() {
        assertEquals("swiggy_instamart", MerchantRegistry.match("SWIGGY INSTAMART", null)!!.key)
        assertEquals("swiggy", MerchantRegistry.match(null, "swiggy@icici")!!.key)
        assertEquals("primevideo", MerchantRegistry.match("AMAZON PRIME VIDEO", null)!!.key)
        assertEquals("amazon", MerchantRegistry.match("AMAZON PAY INDIA", null)!!.key)
        assertNull(MerchantRegistry.match("Credit card payment", null))
        assertNull(MerchantRegistry.match("Olam Traders", null))
    }

    @Test fun everyKnownMerchantHasAPackageOrCategory() {
        MerchantRegistry.all.forEach { assertTrue(it.category.isNotBlank()) }
        assertTrue(MerchantRegistry.packageNames.contains("in.swiggy.android"))
    }
}

class CategorizerTest {
    private fun tx(body: String, merchant: String?, dir: Direction = Direction.DEBIT, kind: TxKind = TxKind.NORMAL) =
        ParsedTransaction(1, "VM-HDFCBK-S", "hdfc", "HDFC Bank", 0, 100, dir, "4821", false, merchant, null, null, null, kind, 0.9, body)

    @Test fun userRuleBeatsDefaults() {
        val c = Categorizer(listOf(CategoryRule(RuleMatch.MERCHANT, "swiggy", Cat.GROCERIES)))
        val t = tx("", "Swiggy")
        assertEquals(Cat.GROCERIES, c.categorize(t, "swiggy", MerchantRegistry.match("Swiggy", null)))
    }

    @Test fun keywordDefaults() {
        val c = Categorizer()
        assertEquals(Cat.FUEL, c.categorize(tx("", "HP Petrol Pump"), "hp_petrol_pump", null))
        assertEquals(Cat.DINING, c.categorize(tx("", "Saravana Bhavan"), "saravana_bhavan", null))
        assertEquals(Cat.OTHERS, c.categorize(tx("", "R Kumar"), "r_kumar", null))
        assertEquals(Cat.INCOME, c.categorize(tx("", "R Kumar", Direction.CREDIT), "r_kumar", null))
        assertEquals(Cat.CASH, c.categorize(tx("", null, kind = TxKind.CASH_WITHDRAWAL), "atm", null))
    }

    @Test fun sportsAndFitness() {
        val c = Categorizer()
        fun cat(name: String) = c.categorize(tx("", name), MerchantRegistry.keyFor(null, name, null), MerchantRegistry.match(name, null))
        assertEquals(Cat.SPORTS, cat("PLAYO"))
        assertEquals(Cat.SPORTS, cat("Green Turf Arena"))
        assertEquals(Cat.SPORTS, cat("Smash Badminton Academy")) // not Education
        assertEquals(Cat.SPORTS, cat("Gold's Gym"))
        assertEquals(Cat.SPORTS, cat("Anytime Fitness"))
        assertEquals(Cat.SPORTS, cat("Decathlon Sports India")) // not Shopping
        assertEquals(Cat.SPORTS, cat("CUREFIT HEALTHCARE")) // cult.fit
        assertEquals(Cat.DINING, cat("Gymkhana Club Canteen")) // "gym" only as a word
        assertEquals(Cat.FASHION, cat("Lifestyle Stores"))
    }

    @Test fun holidayIsWhereYouStayAndTravelIsGettingThere() {
        val c = Categorizer()
        fun cat(name: String) = c.categorize(tx("", name), MerchantRegistry.keyFor(null, name, null), MerchantRegistry.match(name, null))
        assertEquals(Cat.HOLIDAY, cat("CLUB MAHINDRA HOLIDAYS"))
        assertEquals(Cat.HOLIDAY, cat("STERLING HOLIDAY RESORTS"))
        assertEquals(Cat.HOLIDAY, cat("AIRBNB * HMXY2Q"))
        assertEquals(Cat.HOLIDAY, cat("OYO ROOMS"))
        assertEquals(Cat.HOLIDAY, cat("ORAVEL STAYS PVT LTD"))
        assertEquals(Cat.HOLIDAY, cat("WWW.AGODA.COM"))
        assertEquals(Cat.HOLIDAY, cat("Coorg Hills Homestay"))
        assertEquals(Cat.HOLIDAY, cat("Hotel Sea View & Resort")) // a stay, not a restaurant
        assertEquals(Cat.HOLIDAY, cat("Holiday Inn Express"))
        assertEquals(Cat.TRAVEL, cat("INDIGO AIRLINES"))
        assertEquals(Cat.TRAVEL, cat("IRCTC"))
        assertEquals(Cat.TRAVEL, cat("SRS TRAVELS"))
        assertEquals(Cat.DINING, cat("Hotel Saravana Bhavan")) // in India a hotel is often a restaurant
        assertEquals(Cat.OTHERS, cat("TOYOTA KIRLOSKAR")) // "oyo" only as a word
    }

    private fun cat(name: String, kids: Boolean = true) =
        Categorizer(kids = kids).categorize(tx("", name), MerchantRegistry.keyFor(null, name, null), MerchantRegistry.match(name, null))

    @Test fun shoppingIsSortedByWhatTheShopSells() {
        assertEquals(Cat.FASHION, cat("MYNTRA DESIGNS"))
        assertEquals(Cat.FASHION, cat("Sri Krishna Silks"))
        assertEquals(Cat.FASHION, cat("Ramesh Tailors"))
        assertEquals(Cat.FASHION, cat("Vision Opticals"))
        assertEquals(Cat.ELECTRONICS, cat("CROMA INFINITI RETAIL"))
        assertEquals(Cat.ELECTRONICS, cat("Sri Ram Mobiles"))
        assertEquals(Cat.FURNITURE, cat("PEPPERFRY"))
        assertEquals(Cat.FURNITURE, cat("Balaji Hardwares"))
        assertEquals(Cat.BEAUTY, cat("NYKAA E RETAIL"))
        assertEquals(Cat.BEAUTY, cat("Diamond Hair Salon"))
        assertEquals(Cat.JEWELLERY, cat("TANISHQ"))
        assertEquals(Cat.JEWELLERY, cat("Sri Lakshmi Jewellers"))
        assertEquals(Cat.EDUCATION, cat("Higginbothams Book House"))
        // Stores that sell everything: their SMS never says what, so they wait in Shopping to be sorted
        assertEquals(Cat.SHOPPING, cat("WWW AMAZON IN"))
        assertEquals(Cat.SHOPPING, cat("FLIPKART INTERNET"))
        assertEquals(Cat.SHOPPING, cat("Sri Ganesh Stores"))
    }

    @Test fun gettingAroundFoodBillsAndHealthAreSplitToo() {
        assertEquals(Cat.COMMUTE, cat("UBER INDIA"))
        assertEquals(Cat.COMMUTE, cat("BMRCL Metro"))
        assertEquals(Cat.CAR_BIKE, cat("FASTAG RECHARGE NETC"))
        assertEquals(Cat.CAR_BIKE, cat("Forum Mall Parking"))
        assertEquals(Cat.FUEL, cat("Murugan Service Station"))
        assertEquals(Cat.FOOD_DELIVERY, cat("SWIGGY"))
        assertEquals(Cat.FOOD_DELIVERY, cat("ZOMATO LTD"))
        assertEquals(Cat.DINING, cat("Meghana Biryani"))
        assertEquals(Cat.GROCERIES, cat("BLINKIT"))
        assertEquals(Cat.UTILITIES, cat("BESCOM"))
        assertEquals(Cat.UTILITIES, cat("INDANE GAS"))
        assertEquals(Cat.RECHARGE, cat("AIRTEL PREPAID"))
        assertEquals(Cat.RECHARGE, cat("ACT FIBERNET"))
        assertEquals(Cat.SUBSCRIPTIONS, cat("NETFLIX.COM"))
        assertEquals(Cat.SUBSCRIPTIONS, cat("APPLE MEDIA SERVICES"))
        assertEquals(Cat.INSURANCE, cat("LIC OF INDIA"))
        assertEquals(Cat.INSURANCE, cat("STAR HEALTH"))
        assertEquals(Cat.INSURANCE, cat("New India Assurance"))
        assertEquals(Cat.HOME_HELP, cat("URBAN COMPANY"))
        assertEquals(Cat.HOME_HELP, cat("Quick Dry Cleaners"))
        assertEquals(Cat.MEDICINES, cat("PHARMEASY"))
        assertEquals(Cat.MEDICINES, cat("Sri Sai Medicals"))
        assertEquals(Cat.DOCTOR, cat("Manipal Hospital"))
        assertEquals(Cat.DOCTOR, cat("PRACTO"))
        assertEquals(Cat.ENTERTAINMENT, cat("PVR CINEMAS"))
        assertEquals(Cat.TEMPLE, cat("Sri Venkateswara Temple"))
        assertEquals(Cat.TEMPLE, cat("TIRUMALA TIRUPATI DEVASTHANAMS"))
        assertEquals(Cat.PETS, cat("SUPERTAILS"))
        assertEquals(Cat.GIFTS, cat("FERNS N PETALS"))
        assertEquals(Cat.EMI, cat("Muthoot Gold Loan")) // a loan, not a jeweller
    }

    @Test fun kidsCategoriesOnlyForThoseWhoPayForChildren() {
        assertEquals(Cat.KIDS_EDUCATION, cat("Delhi Public School"))
        assertEquals(Cat.KIDS_EDUCATION, cat("Kendriya Vidyalaya"))
        assertEquals(Cat.KIDS_EDUCATION, cat("BYJUS THINK AND LEARN"))
        assertEquals(Cat.KIDS_ACTIVITIES, cat("UCMAS Abacus"))
        assertEquals(Cat.BABY, cat("FIRSTCRY BRAINBEES"))
        assertEquals(Cat.KIDS_TOYS, cat("Hamleys"))
        // The Kids categories added in 1.9.12
        assertEquals(Cat.BABY, cat("Baby Care Store"))
        assertEquals(Cat.DAYCARE, cat("Little Steps Daycare"))
        assertEquals(Cat.DAYCARE, cat("Sunshine Creche"))
        assertEquals(Cat.KIDS_HEALTH, cat("Rainbow Children's Hospital"))
        assertEquals(Cat.KIDS_HEALTH, cat("Dr Rao Paediatric Clinic"))
        assertEquals(Cat.KIDS_TOYS, cat("Toy World"))
        // Academies and classes could be anyone's
        assertEquals(Cat.SPORTS, cat("Aqua Swim Academy"))
        assertEquals(Cat.EDUCATION, cat("Vidya Coaching Academy"))
        // Without kids
        assertEquals(Cat.EDUCATION, cat("Delhi Public School", kids = false))
        assertEquals(Cat.SPORTS, cat("UCMAS Abacus", kids = false))
        assertEquals(Cat.SHOPPING, cat("FIRSTCRY BRAINBEES", kids = false))
        assertEquals(Cat.HOME_HELP, cat("Little Steps Daycare", kids = false))
        assertEquals(Cat.DOCTOR, cat("Rainbow Children's Hospital", kids = false))
    }

    @Test fun academiesAndClassesAskWhoTheyWereFor() {
        assertEquals(Cat.SPORTS to Cat.KIDS_ACTIVITIES, CategoryGroups.forYouOrKids("Aqua Swim Academy", Cat.SPORTS))
        assertEquals(Cat.SPORTS to Cat.KIDS_ACTIVITIES, CategoryGroups.forYouOrKids("Nritya Dance Classes", Cat.KIDS_ACTIVITIES))
        assertEquals(Cat.EDUCATION to Cat.KIDS_EDUCATION, CategoryGroups.forYouOrKids("Vidya Coaching Academy", Cat.EDUCATION))
        assertNull(CategoryGroups.forYouOrKids("Playo", Cat.SPORTS))            // a court you book: yours
        assertNull(CategoryGroups.forYouOrKids("Aqua Swim Academy", Cat.OTHERS)) // put somewhere else by the user
    }

    @Test fun everyBuiltInCategoryHasOneGroupAtMost() {
        val all = CategoryGroups.CHILDREN.values.flatten()
        assertEquals(all.size, all.toSet().size)
        assertTrue(CategoryGroups.GROUPS.none { it in all })
        assertEquals(Cat.SHOPPING, CategoryGroups.top(Cat.FASHION))
        assertEquals(Cat.TRANSPORT, CategoryGroups.top(Cat.FUEL))
        assertEquals(Cat.EMI, CategoryGroups.top(Cat.EMI))
        // Every category the old ones were split into is in a group or on its own, never another old one's group
        CategoryGroups.SPLIT_FROM.values.flatten().forEach { assertTrue(it !in CategoryGroups.GROUPS) }
    }
}

class DeduperTest {
    @Test fun sameReferenceIsOnePayment() {
        val d = Deduper()
        assertTrue(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 0, "4821", "629112345678")))
        assertFalse(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 90_000, null, "629112345678")))
    }

    @Test fun bankAndAppAlertWithinFiveMinutesAreOnePayment() {
        val d = Deduper()
        assertTrue(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 0, "4821", null)))
        assertFalse(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 120_000, null, null)))
        assertTrue(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 10 * 60_000, "4821", null)))
        assertTrue(d.offer(Deduper.DedupeEntry(48600, Direction.DEBIT, 10 * 60_000 + 1000, "9012", null)))
    }

    @Test fun differentReferencesAreDifferentPayments() {
        val d = Deduper()
        assertTrue(d.offer(Deduper.DedupeEntry(2000, Direction.DEBIT, 0, "4821", "111111")))
        assertTrue(d.offer(Deduper.DedupeEntry(2000, Direction.DEBIT, 1000, "4821", "222222")))
    }
}

class FiltersTest {
    private fun tx(amount: Long, dir: Direction, group: String = "hdfc", merchant: String = "Shop", body: String = "") =
        ParsedTransaction(1, "x", group, null, 0, amount, dir, null, false, merchant, null, null, null, TxKind.NORMAL, 0.9, body)

    @Test fun defaultsSkipUnknownSendersOnly() {
        val f = ImportFilters()
        assertTrue(f.accepts(tx(100, Direction.DEBIT)))
        assertFalse(f.accepts(tx(100, Direction.DEBIT, group = Institutions.UNRECOGNISED)))
    }

    @Test fun moneyInOutMinAmountAndWords() {
        val f = ImportFilters(includeIn = false, minAmountPaise = 5000, skipWords = listOf("NEFT"))
        assertFalse(f.accepts(tx(100000, Direction.CREDIT)))
        assertFalse(f.accepts(tx(4999, Direction.DEBIT)))
        assertTrue(f.accepts(tx(5000, Direction.DEBIT)))
        assertFalse(f.accepts(tx(5000, Direction.DEBIT, body = "by neft to R Kumar")))
    }
}

class BudgetTest {
    @Test fun statesFollowPaceAndLimit() {
        assertEquals(BudgetState.ON_TRACK, BudgetMath.status(30_000_00, 75_000_00, 18, 31).state)
        // 75% used -> heads-up even if pace is fine
        assertEquals(BudgetState.HEADS_UP, BudgetMath.status(57_000_00, 75_000_00, 28, 31).state)
        // projected over -> heads-up
        assertEquals(BudgetState.HEADS_UP, BudgetMath.status(50_000_00, 75_000_00, 18, 31).state)
        assertEquals(BudgetState.OVER, BudgetMath.status(77_340_00, 75_000_00, 18, 31).state)
    }

    @Test fun safePerDayUsesDaysLeft() {
        val s = BudgetMath.status(20_000_00, 75_000_00, 18, 31)
        assertEquals(13, s.daysLeft)
        assertEquals((55_000_00L) / 13, s.safePerDayPaise)
    }

    @Test fun periodCanStartOnSalaryDay() {
        val p = BudgetMath.periodFor(LocalDate.of(2026, 10, 18), 25)
        assertEquals(LocalDate.of(2026, 9, 25), p.start)
        assertEquals(LocalDate.of(2026, 10, 25), p.endExclusive)
        assertEquals(30, p.days)
        assertEquals(24, p.dayNumber(LocalDate.of(2026, 10, 18)))
        val q = BudgetMath.periodFor(LocalDate.of(2026, 10, 18), 1)
        assertEquals(LocalDate.of(2026, 10, 1), q.start)
        assertEquals(31, q.days)
        assertEquals(LocalDate.of(2026, 9, 1), q.previous(1).start)
    }
}
