package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyTest {

    @Test fun ordinaryMerchantNamesAreUntouched() {
        assertEquals("Swiggy", Privacy.merchant("Swiggy"))
        assertEquals("Domino's Pizza", Privacy.merchant("Domino's Pizza"))
        assertEquals("7-Eleven", Privacy.merchant("7-Eleven"))
        assertEquals("Shop 1234", Privacy.merchant("Shop 1234"))
    }

    @Test fun upiIdsKeepOnlyTheChosenName() {
        assertEquals("ramesh.k", Privacy.merchant("ramesh.k@okhdfcbank"))
        assertEquals("Paid to ramesh.k", Privacy.merchant("Paid to ramesh.k@okicici"))
    }

    @Test fun phoneNumbersAndAccountNumbersAreMasked() {
        assertEquals("•••••", Privacy.merchant("9876543210@ybl"))
        assertEquals("NEFT •••••", Privacy.merchant("NEFT 50100234567890"))
        assertEquals("Ref ••••• done", Privacy.maskDigits("Ref 629112345678 done"))
    }

    @Test fun nothingSensitiveSurvives() {
        listOf("9876543210@ybl", "UPI/629112345678/RAMESH", "a/c 50100234567890", "ramesh@okaxis").forEach {
            assertTrue(it, Privacy.looksSensitive(it))
            assertFalse(Privacy.merchant(it), Privacy.looksSensitive(Privacy.merchant(it)))
        }
    }

    @Test fun keysAreScrubbedToo() {
        assertEquals("swiggy", Privacy.merchantKey("swiggy"))
        assertEquals("xxxxx", Privacy.merchantKey("9876543210@ybl"))
        assertEquals("ramesh_k", Privacy.merchantKey("ramesh_k@okhdfcbank"))
    }

    @Test fun anEmptyResultFallsBackToAGenericName() {
        assertEquals("UPI payment", Privacy.merchant("   "))
    }
}
