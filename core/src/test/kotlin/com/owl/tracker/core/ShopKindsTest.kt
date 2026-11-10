package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Icons for shops with no app, picked from words in the shop's name. */
class ShopKindsTest {

    private fun kind(name: String?) = ShopKinds.of(name)

    @Test fun readsTheKindOfShopFromItsName() {
        assertEquals(ShopKind.JEWELLERY, kind("SRI LAKSHMI JEWELLERS"))
        assertEquals(ShopKind.JEWELLERY, kind("BHIMA JEWELLERS"))
        assertEquals(ShopKind.JEWELLERY, kind("GRT THANGA MALIGAI"))
        assertEquals(ShopKind.JEWELLERY, kind("Ganesh Gold Palace"))
        assertEquals(ShopKind.CLOTHING, kind("KUMAR SILKS & SAREES"))
        assertEquals(ShopKind.CLOTHING, kind("Kalyan Silks"))
        assertEquals(ShopKind.CLOTHING, kind("NEW STYLE GARMENTS"))
        assertEquals(ShopKind.CLOTHING, kind("Royal Tailors"))
        assertEquals(ShopKind.BOOKS, kind("CROSSWORD BOOKSTORES"))
        assertEquals(ShopKind.BOOKS, kind("SAPNA BOOK HOUSE"))
        assertEquals(ShopKind.BOOKS, kind("Higginbothams Books"))
        assertEquals(ShopKind.BOOKS, kind("Vijay Stationers"))
        assertEquals(ShopKind.PHARMACY, kind("SRI SAI MEDICALS"))
        assertEquals(ShopKind.PHARMACY, kind("WELLNESS FOREVER PHARMACY"))
        assertEquals(ShopKind.PHARMACY, kind("Jan Aushadhi Kendra"))
        assertEquals(ShopKind.OPTICALS, kind("VISION OPTICALS"))
        assertEquals(ShopKind.OPTICALS, kind("Lawrence & Mayo Opticians"))
        assertEquals(ShopKind.BAKERY, kind("IYENGARS BAKERY"))
        assertEquals(ShopKind.BAKERY, kind("ANAND SWEETS"))
        assertEquals(ShopKind.BAKERY, kind("Haldiram Sweet Mart"))
        assertEquals(ShopKind.SALON, kind("NATURALS SALON"))
        assertEquals(ShopKind.SALON, kind("Green Trends Unisex Hair & Style"))
        assertEquals(ShopKind.SALON, kind("Lakme Beauty Parlour"))
        assertEquals(ShopKind.MOBILES, kind("SANGEETHA MOBILES"))
        assertEquals(ShopKind.MOBILES, kind("Poorvika Mobiles Pvt Ltd"))
        assertEquals(ShopKind.MOBILES, kind("VIJAY SALES ELECTRONICS"))
        assertEquals(ShopKind.HARDWARE, kind("BALAJI HARDWARE & PAINTS"))
        assertEquals(ShopKind.HARDWARE, kind("Sri Ram Electricals"))
        assertEquals(ShopKind.CLINIC, kind("CITY DENTAL CLINIC"))
        assertEquals(ShopKind.CLINIC, kind("Thyrocare Labs"))
        assertEquals(ShopKind.CLINIC, kind("Manipal Hospital"))
    }

    @Test fun theLastWordThatNamesAKindWins() {
        assertEquals(ShopKind.SALON, kind("DIAMOND HAIR SALON"))
        assertEquals(ShopKind.BAKERY, kind("SRI KRISHNA SWEETS & BAKERY"))
        assertEquals(ShopKind.JEWELLERY, kind("FASHION JEWELLERY"))
        assertEquals(ShopKind.PHARMACY, kind("CITY HOSPITAL PHARMACY"))
    }

    @Test fun placesThatAreNoneOfTheseGetNoShopIcon() {
        assertNull(kind("GOLDS GYM"))
        assertNull(kind("MUTHOOT GOLD LOAN"))
        assertNull(kind("BOOK CAFE"))
        assertNull(kind("Silk Route Restaurant"))
        assertNull(kind("Fashion Institute of Technology"))
        assertNull(kind("St. Mary's Medical College"))
    }

    @Test fun peopleAndOrdinaryWordsGetNoShopIcon() {
        assertNull(kind("RAMESH KUMAR"))
        assertNull(kind("SWEETY SHARMA"))
        assertNull(kind("RAJESH TAILOR"))
        assertNull(kind("SWARNA LATHA"))
        assertNull(kind("GOLDIE SINGH"))
        assertNull(kind("Golden Harvest Foods"))
        assertNull(kind("FACEBOOK ADS"))
        assertNull(kind("BOOKING HOLDINGS"))
        assertNull(kind("HONDA AUTOMOBILES"))
        assertNull(kind("SPAR HYPERMARKET"))
        assertNull(kind("KAPIL DAWAR"))
        assertNull(kind(""))
        assertNull(kind(null))
    }

    @Test fun knownMerchantsKeepTheirOwnIconOrCategory() {
        // BookMyShow is not a book shop, and a known jeweller has its own app
        assertNull(kind("BOOKMYSHOW"))
        assertNull(kind("TANISHQ JEWELLERY"))
        assertNull(kind("APOLLO PHARMACY"))
        assertNull(kind("LENSKART OPTICALS"))
    }
}
