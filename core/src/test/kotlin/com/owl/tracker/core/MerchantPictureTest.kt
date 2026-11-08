package com.owl.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPictureTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(100) { it.toByte() }

    @Test fun picturesHaveStableIds() {
        val id = MerchantPicture.idFor(png)
        assertTrue(id, Regex("pic:[0-9a-f]{16}").matches(id))
        assertEquals(id, MerchantPicture.idFor(png.copyOf()))
        assertNotEquals(id, MerchantPicture.idFor(png + 1))
        assertTrue(MerchantPicture.isPicture(id))
        assertFalse(MerchantPicture.isPicture("pic:../../etc"))
        assertFalse(MerchantPicture.isPicture("in.swiggy.android"))
        assertFalse(MerchantPicture.isPicture(null))
    }

    @Test fun onlySmallPngsAreAccepted() {
        assertTrue(MerchantPicture.isPng(png))
        assertFalse(MerchantPicture.isPng("GIF89a and more bytes to pass the length".toByteArray()))
        assertFalse(MerchantPicture.isPng(png.copyOf(20)))
        assertFalse(MerchantPicture.isPng(png + ByteArray(MerchantPicture.MAX_BYTES)))
        assertFalse(MerchantPicture.isPng(null))
    }

    @Test fun packageNamesAreCheckedForShape() {
        assertTrue(KnownApps.isPackageName("com.idbibank.abhay_card"))
        assertTrue(KnownApps.isPackageName("com.Version1"))
        assertFalse(KnownApps.isPackageName("swiggy"))
        assertFalse(KnownApps.isPackageName("com..evil"))
        assertFalse(KnownApps.isPackageName("com.x/../y"))
        assertFalse(KnownApps.isPackageName("pic:0123456789abcdef"))
        assertFalse(KnownApps.isPackageName(""))
        assertFalse(KnownApps.isPackageName(null))
    }

    @Test fun cropStartsCentredAndStaysInside() {
        val tall = CropBox.centred(1080, 2400)
        assertEquals(Triple(0, 660, 1080), tall.pixels())
        assertEquals(0f, tall.zoom, 0.001f)

        // Zoom all the way in: the smallest square, still centred
        val close = tall.withZoom(1f)
        assertEquals(24, close.pixels().third)
        assertEquals(1f, close.zoom, 0.001f)
        assertEquals(540f, close.left + close.side / 2, 0.5f)
        assertEquals(1200f, close.top + close.side / 2, 0.5f)

        // Pinching beyond the limits is held at them
        assertEquals(24, tall.zoomedBy(1000f).pixels().third)
        assertEquals(1080, close.zoomedBy(0.0001f).pixels().third)
        assertEquals(tall, tall.zoomedBy(0f))

        // Moving past an edge stops at the edge
        val icon = tall.withZoom(0.5f)
        val corner = icon.movedBy(-5000f, -5000f)
        assertEquals(0, corner.pixels().first)
        assertEquals(0, corner.pixels().second)
        val far = icon.movedBy(5000f, 5000f)
        val (x, y, s) = far.pixels()
        assertEquals(1080 - s, x)
        assertEquals(2400 - s, y)

        // Slow drags add up even when each step is under a pixel
        var slow = icon
        repeat(100) { slow = slow.movedBy(0.3f, 0f) }
        assertEquals(icon.left + 30f, slow.left, 0.01f)
    }

    @Test fun zoomSliderRoundTrips() {
        val box = CropBox.centred(1200, 900)
        listOf(0f, 0.25f, 0.5f, 0.9f, 1f).forEach { z -> assertEquals(z, box.withZoom(z).zoom, 0.01f) }
        // Tiny pictures can't zoom
        val tiny = CropBox.centred(20, 30)
        assertEquals(0f, tiny.zoom, 0f)
        assertEquals(Triple(0, 5, 20), tiny.withZoom(1f).pixels())
    }
}
