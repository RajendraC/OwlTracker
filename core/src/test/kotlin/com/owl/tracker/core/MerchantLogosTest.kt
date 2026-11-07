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

    @Test fun everyKnownMerchantHasAValidPackageName() {
        val pkg = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")
        MerchantRegistry.all.forEach { m -> m.packageName?.let { assertTrue("${m.key}: $it", pkg.matches(it)) } }
        assertTrue("about 80 apps", MerchantRegistry.packageNames.size >= 75)
        assertEquals("keys are unique", MerchantRegistry.all.size, MerchantRegistry.all.map { it.key }.toSet().size)
    }
}
