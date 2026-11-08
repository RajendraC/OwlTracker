package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The merchants whose app icons Owl Tracker can show, and that similar names don't get mixed up. */
class MerchantLogosTest {

    private fun key(raw: String) = MerchantRegistry.match(raw, null)?.key

    @Test fun recognisesTheNewApps() {
        assertEquals("nammayatri", key("NAMMA YATRI"))
        assertEquals("indigo", key("INTERGLOBE AVIATION LTD"))
        assertEquals("sonyliv", key("SONYLIV"))
        assertEquals("groww", key("NEXTBILLION TECHNOLOGY"))
        assertEquals("dmart", key("AVENUE SUPERMARTS LTD"))
        assertEquals("cultfit", key("CUREFIT HEALTHCARE"))
        assertEquals("eatsure", key("REBEL FOODS PVT LTD"))
    }

    @Test fun similarNamesStayApart() {
        assertEquals("googleplay", key("GOOGLE PLAY"))
        assertEquals("gpay", key("GOOGLE PAY"))
        assertEquals("googleone", key("GOOGLE ONE"))
        assertEquals("jiosaavn", key("JIOSAAVN"))
        assertEquals("jio", key("RELIANCE JIO"))
        assertEquals("kindle", key("AMAZON KINDLE"))
        assertEquals("amazon", key("AMAZON PAY INDIA"))
        assertEquals("youtubemusic", key("YOUTUBE MUSIC"))
        assertEquals("youtube", key("YOUTUBE PREMIUM"))
        assertEquals("shopsy", key("FLIPKART SHOPSY"))
    }

    @Test fun commonWordsDontMatchByAccident() {
        assertNull(key("BHIMA JEWELLERS"))
        assertNull(key("NAVI MUMBAI MUNICIPAL"))
        assertNull(key("INDIGO PAINTS"))
        assertNull(key("YATRI NIWAS"))
    }

    @Test fun recognisesPopularIndianApps() {
        // Streaming
        assertEquals("hotstar", key("JIOCINEMA"))
        assertEquals("hotstar", key("JIOSTAR INDIA PVT LTD"))
        assertEquals("sunnxt", key("SUN NXT"))
        assertEquals("altt", key("ALTT"))
        assertEquals("hoichoi", key("HOICHOI TECHNOLOGIES"))
        assertEquals("aha", key("ARHA MEDIA AND BROADCASTING"))
        assertEquals("tataplay", key("TATA SKY LTD"))
        assertEquals("airtelxstream", key("AIRTEL XSTREAM"))
        assertEquals("amazonmusic", key("AMAZON MUSIC"))
        assertEquals("applemusic", key("APPLE MUSIC"))
        assertEquals("pocketfm", key("POCKET FM"))
        assertEquals("kukufm", key("KUKU FM"))
        // Subscriptions
        assertEquals("claude", key("ANTHROPIC"))
        assertEquals("canva", key("CANVA"))
        assertEquals("notion", key("NOTION LABS INC"))
        assertEquals("zoom", key("ZOOM.US"))
        assertEquals("truecaller", key("TRUECALLER"))
        assertEquals("bumble", key("BUMBLE"))
        assertEquals("strava", key("STRAVA"))
        // UPI apps
        assertEquals("bhim", key("BHIM UPI"))
        assertEquals("navi", key("NAVI FINSERV LTD"))
        assertEquals("freecharge", key("FREECHARGE"))
        assertEquals("payzapp", key("PAYZAPP"))
        // 10-minute delivery
        assertEquals("bigbasket", key("BB NOW"))
        assertEquals("zepto", key("KIRANAKART TECHNOLOGIES"))
        assertEquals("blinkit", key("BLINKIT"))
        assertEquals("swiggy_instamart", key("SWIGGY INSTAMART"))
        // Broader names still win when nothing more specific is there
        assertEquals("amazon", key("AMAZON"))
        assertEquals("airtel", key("AIRTEL PREPAID"))
        assertEquals("jio", key("RELIANCE JIO"))
    }

    @Test fun recognisesInsuranceApps() {
        assertEquals("policybazaar", key("PB FINTECH LIMITED"))
        assertEquals("policybazaar", key("POLICYBAZAAR INSURANCE BROKERS"))
        assertEquals("policyx", key("POLICYX.COM"))
        assertEquals("digit", key("GO DIGIT GENERAL INSURANCE LTD"))
        assertEquals("acko", key("ACKO GENERAL INSURANCE"))
        assertEquals("lic", key("LIC OF INDIA"))
        assertEquals("lic", key("LIFE INSURANCE CORPORATION OF INDIA"))
        assertEquals("hdfcergo", key("HDFC ERGO GENERAL INSURANCE"))
        assertEquals("icicilombard", key("ICICI LOMBARD GIC LTD"))
        assertEquals("tataaig", key("TATA AIG GENERAL INSURANCE"))
        assertEquals("tataaia", key("TATA AIA LIFE INSURANCE"))
        assertEquals("bajajgeneral", key("BAJAJ ALLIANZ GENERAL INSURANCE"))
        assertEquals("hdfclife", key("HDFC LIFE INSURANCE CO"))
        assertEquals("sbilife", key("SBI LIFE INSURANCE"))
        assertEquals("icicipru", key("ICICI PRUDENTIAL LIFE INSURANCE"))
        assertEquals("axismaxlife", key("AXIS MAX LIFE INSURANCE"))
        assertEquals("starhealth", key("STAR HEALTH AND ALLIED INSURANCE"))
        assertEquals("nivabupa", key("NIVA BUPA HEALTH INSURANCE"))
        assertEquals("carehealth", key("CARE HEALTH INSURANCE LTD"))
        assertEquals(Cat.HEALTH, MerchantRegistry.match("STAR HEALTH", null)?.category)
        assertEquals(Cat.BILLS, MerchantRegistry.match("ACKO", null)?.category)
        // Look-alikes stay apart
        assertNull(key("DIGITAL STORE"))
        assertNull(key("LICIOUS")?.takeIf { it != "licious" })
        assertNull(key("MAX LIFESTYLE"))
        assertNull(key("ICICI PRUDENTIAL MUTUAL FUND"))
        assertNull(key("CARE HEALTH PHARMACY"))
    }

    @Test fun wholeWordNamesNeedTheWholeWord() {
        assertNull(key("CANVAS PRINTS"))
        assertNull(key("ZOOMCAR INDIA"))
        assertNull(key("BUMBLEBEE CAFE"))
        assertNull(key("NOTIONAL CHARGES"))
        assertNull(key("AHAAR RESTAURANT"))
        assertNull(key("JEAN CLAUDE BAKERY"))
    }

    @Test fun theUpiHandleIsNotTheMerchant() {
        // @freecharge, @paytm and @upi name the app the person uses, not who was paid
        assertNull(MerchantRegistry.match("RAMESH KUMAR", "ramesh@freecharge"))
        assertNull(MerchantRegistry.match(null, "9876543210@paytm"))
        assertNull(MerchantRegistry.match(null, "ramesh.k@upi"))
        assertEquals("rapido", MerchantRegistry.match(null, "rapido@ybl")?.key)
        assertEquals("swiggy", MerchantRegistry.match("SWIGGY", "swiggy@icici")?.key)
    }

    @Test fun banksShowTheirOwnApps() {
        assertEquals("com.hdfcbank.android.now", Institutions.appsFor("hdfc").first())
        assertEquals("com.sbi.lotusintouch", Institutions.forEntity("SBIUPI")?.apps?.first())
        assertEquals(listOf("com.csam.icici.bank.imobile"), Institutions.appsFor(Institutions.forEntity("ICICIT")?.id))
        assertTrue(Institutions.appsFor(Institutions.UNRECOGNISED).isEmpty())
        assertTrue(Institutions.appsFor(null).isEmpty())
        assertEquals("ICICI Bank", KnownApps.nameFor("com.csam.icici.bank.imobile"))
        assertEquals("Canva", KnownApps.nameFor("com.canva.editor"))
        listOf("sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "canara", "union", "idfc", "yes", "indusind").forEach {
            assertTrue("$it has an app", Institutions.appsFor(it).isNotEmpty())
        }
    }

    @Test fun everyKnownMerchantHasAValidPackageName() {
        val pkg = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")
        MerchantRegistry.all.forEach { m -> m.packageName?.let { assertTrue("${m.key}: $it", pkg.matches(it)) } }
        Institutions.packageNames.forEach { assertTrue(it, pkg.matches(it)) }
        assertTrue("well over 100 apps", KnownApps.packageNames.size >= 120)
        assertEquals(KnownApps.packageNames.size, KnownApps.packageNames.toSet().size)
        assertTrue(KnownApps.packageNames.containsAll(MerchantRegistry.packageNames + Institutions.packageNames))
        assertEquals("keys are unique", MerchantRegistry.all.size, MerchantRegistry.all.map { it.key }.toSet().size)
    }
}
