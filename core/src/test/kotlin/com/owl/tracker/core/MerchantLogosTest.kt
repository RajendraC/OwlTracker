package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun recognisesLiveStreamingAndClubMahindra() {
        assertEquals("clubmahindra", key("CLUB MAHINDRA HOLIDAYS"))
        assertEquals("clubmahindra", key("MAHINDRA HOLIDAYS AND RESORTS"))
        assertEquals(Cat.TRAVEL, MerchantRegistry.match("CLUB MAHINDRA", null)?.category)
        assertEquals("fancode", key("FANCODE"))
        assertEquals("fancode", key("SPORTA TECHNOLOGIES PVT LTD"))
        assertEquals("cricbuzz", key("CRICBUZZ PLUS"))
        assertEquals("waves", key("PRASAR BHARATI WAVES"))
        assertEquals("shemaroome", key("SHEMAROO ENTERTAINMENT"))
        assertEquals("stage", key("STAGE TECHNOLOGIES PRIVATE LIMITED"))
        assertEquals("chaupal", key("CHAUPAL OTT"))
        assertEquals("manoramamax", key("MANORAMAMAX"))
        assertEquals("lionsgate", key("LIONSGATE PLAY"))
        assertEquals("ottplay", key("OTTPLAY PREMIUM"))
        // Already there: SonyLIV, ZEE5, JioHotstar
        assertEquals("sonyliv", key("SONYLIV"))
        assertEquals("zee5", key("ZEE5"))
        assertEquals("hotstar", key("JIOHOTSTAR"))
        // Everyday words stay apart
        assertNull(key("NEW WAVES SALON"))
        assertNull(key("GAON KI CHAUPAL DHABA"))
        assertNull(key("STAGE 3 CAFE"))
        assertNull(key("MAHINDRA FINANCE"))
    }

    @Test fun petrolPumpsAndTollsKeepTheirOwnNameWithTheBrandsIcon() {
        val pump = MerchantRegistry.match("SHREE GANESH HPCL FILLING STN", null)!!
        assertEquals("com.drivetrackplusrefuel", pump.packageName)
        assertEquals(Cat.FUEL, pump.category)
        assertTrue(pump.keepName)
        // Each pump stays its own merchant
        assertEquals("shree_ganesh_hpcl_filling_stn", MerchantRegistry.keyFor(pump, "SHREE GANESH HPCL FILLING STN", null))
        assertEquals("hpcl", MerchantRegistry.keyFor(pump, null, null))
        assertEquals("hpcl", key("HP PETROL PUMP ANNA NAGAR"))
        assertEquals("bpcl", key("BHARAT PETROLEUM CORP LTD"))
        assertEquals("indianoil", key("INDIAN OIL CORPORATION"))
        assertEquals("indianoil", key("IOCL COCO OUTLET"))
        assertEquals("shell", key("SHELL INDIA MARKETS PVT LTD"))
        assertEquals("jiobp", key("JIO-BP MOBILITY STATION"))
        assertNull(MerchantRegistry.match("JIO-BP MOBILITY STATION", null)!!.packageName)
        assertEquals(Cat.FUEL, MerchantRegistry.match("JIO BP", null)!!.category)
        assertEquals("jio", key("RELIANCE JIO INFOCOMM"))
        assertEquals("nayara", key("NAYARA ENERGY LTD"))
        // Cooking gas is a bill, with the same company's app
        assertEquals("indane", key("INDANE GAS AGENCY"))
        assertEquals(Cat.BILLS, MerchantRegistry.match("HP GAS", null)!!.category)
        assertEquals("bharatgas", key("BHARATGAS"))
        assertEquals("com.cgt.bharatgas", MerchantRegistry.match("BHARAT GAS", null)!!.packageName)
        // Tolls
        assertEquals("fastag", key("ATTIBELE TOLL PLAZA"))
        assertEquals("fastag", key("NETC FASTAG RECHARGE"))
        assertEquals("rajmargyatra", key("RAJMARGYATRA ANNUAL PASS"))
        assertEquals(Cat.TRANSPORT, MerchantRegistry.match("FASTAG", null)!!.category)
        // Look-alikes
        assertNull(key("SEA SHELL CAFE"))
        assertNull(key("NETCORE SOLUTIONS"))
        assertNull(key("HP WORLD LAPTOPS"))
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

    @Test fun recognisesJewellersClothingAndBooks() {
        assertEquals("tanishq", key("TANISHQ JEWELLERY"))
        assertEquals("caratlane", key("CARATLANE TRADING PVT LTD"))
        assertEquals("malabargold", key("MALABAR GOLD AND DIAMONDS LTD"))
        assertEquals("joyalukkas", key("JOYALUKKAS INDIA LTD"))
        assertEquals("bluestone", key("BLUESTONE JEWELLERY AND LIFESTYLE"))
        assertEquals("giva", key("GIVA"))
        assertEquals("senco", key("SENCO GOLD LIMITED"))
        assertEquals("titaneye", key("TITAN EYE PLUS"))
        assertEquals("westside", key("WESTSIDE"))
        assertEquals("maxfashion", key("MAX FASHION"))
        assertEquals("lifestyle", key("LIFESTYLE INTERNATIONAL PVT LTD"))
        assertEquals("pantaloons", key("PANTALOONS"))
        assertEquals("shoppersstop", key("SHOPPERS STOP LTD"))
        assertEquals("hm", key("H&M HENNES & MAURITZ RETAIL"))
        assertEquals("hm", key("H & M"))
        assertEquals("zara", key("INDITEX TRENT RETAIL INDIA"))
        assertEquals("uniqlo", key("UNIQLO INDIA"))
        assertEquals("fabindia", key("FABINDIA OVERSEAS"))
        assertEquals("souledstore", key("THE SOULED STORE"))
        assertEquals("snitch", key("SNITCH APPARELS"))
        assertEquals("bewakoof", key("BEWAKOOF BRANDS"))
        assertEquals("playbooks", key("GOOGLE PLAY BOOKS"))
        assertEquals("googleplay", key("GOOGLE PLAY"))
        assertEquals("storytel", key("STORYTEL"))
        assertEquals("pratilipi", key("NASADIYA TECHNOLOGIES"))
        assertEquals(Cat.SHOPPING, MerchantRegistry.match("TANISHQ", null)?.category)
        assertEquals(Cat.EDUCATION, MerchantRegistry.match("PLAY BOOKS", null)?.category)
        // Look-alikes and people's names stay apart
        assertNull(key("ZARA KHAN"))
        assertNull(key("MALABAR CAFE"))
        assertNull(key("GOLD PALACE JEWELLERS"))
        assertNull(key("ZUDIO TRENT LTD"))
        assertNull(key("GIVAN SINGH"))
        assertNull(key("SH&MS TRADERS"))
        assertNull(key("KALYAN SILKS"))
    }

    @Test fun moreBanksShowTheirApps() {
        assertEquals("com.snapwork.IDBI", Institutions.appsFor("idbi").first())
        assertEquals("com.iob.iobconnect", Institutions.appsFor(Institutions.forEntity("IOBCHN")?.id).first())
        assertEquals("com.lcode.ucomobilebanking", Institutions.appsFor("uco").first())
        assertEquals("com.centralbank.retail.omni", Institutions.appsFor("central").first())
        assertEquals("com.iexceed.ib.digitalbankingprod", Institutions.appsFor("indianbank").first())
        assertEquals("com.rblbank.mobank", Institutions.appsFor("rbl").first())
        assertEquals("com.bandhan.mBandhan", Institutions.appsFor("bandhan").first())
        assertEquals("air.app.scb.breeze.android.main.in.prod", Institutions.appsFor("scb").first())
        assertEquals("com.americanexpress.android.acctsvcs.in", Institutions.appsFor("amex").first())
        assertEquals("com.creditcard.onecard", Institutions.appsFor("onecard").first())
        assertEquals("American Express", KnownApps.nameFor("com.americanexpress.android.acctsvcs.in"))
        assertEquals("Tanishq", KnownApps.nameFor("com.titancompany.tanishqapp"))
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

    @Test
    fun storesThatSellEverythingAreKnownByTheirKey() {
        // The tester's SMS: "Spent Rs.14536.09 On HDFC Bank Card 3643 At WWW AMAZON IN"
        val amazon = MerchantRegistry.match("WWW AMAZON IN", null)
        assertEquals("amazon", amazon?.key)
        assertTrue(MixedStores.isMixed(MerchantRegistry.keyFor(amazon, "WWW AMAZON IN", null)))
        assertTrue(MixedStores.isMixed(MerchantRegistry.keyFor(MerchantRegistry.match("FLIPKART INTERNET", null), "FLIPKART INTERNET", null)))
        // A payment gateway's name stands in for any shop
        assertTrue(MixedStores.isMixed(MerchantRegistry.keyFor(MerchantRegistry.match("Payu", null), "Payu", null)))
        // Single-kind shops still make a rule for all their payments by default
        assertFalse(MixedStores.isMixed(MerchantRegistry.keyFor(MerchantRegistry.match("SWIGGY", null), "SWIGGY", null)))
        assertFalse(MixedStores.isMixed(MerchantRegistry.keyFor(MerchantRegistry.match("Amazon Music", null), "Amazon Music", null)))
        assertFalse(MixedStores.isMixed(MerchantRegistry.keyFor(MerchantRegistry.match("BLINKIT", null), "BLINKIT", null)))
    }
}
