package com.owl.tracker.core

import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A picture the user chose as a merchant's icon, for merchants whose app Owl Tracker can't show.
 * It is a small square PNG kept in the encrypted database; transactions refer to it as "pic:<id>",
 * where the id is a hash of the PNG, so the same picture always has the same id.
 */
object MerchantPicture {
    const val PREFIX = "pic:"
    const val SIZE_PX = 144
    const val MAX_BYTES = 200_000

    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val ID = Regex("""pic:[0-9a-f]{16}""")

    /** A PNG of a sensible size: anything else is refused (from the picker or from a backup). */
    fun isPng(bytes: ByteArray?): Boolean =
        bytes != null && bytes.size in 33..MAX_BYTES && bytes.copyOfRange(0, PNG.size).contentEquals(PNG)

    fun idFor(png: ByteArray): String =
        PREFIX + MessageDigest.getInstance("SHA-256").digest(png).take(8).joinToString("") { "%02x".format(it) }

    /** Whether a stored icon value names a picture rather than an app. */
    fun isPicture(logo: String?): Boolean = logo != null && ID.matches(logo)
}

/**
 * The square of a picture that becomes the icon, in the picture's own pixels. Positions are kept as
 * decimals so slow drags while zoomed in still move it. Always inside the picture.
 */
data class CropBox(val imageWidth: Int, val imageHeight: Int, val left: Float, val top: Float, val side: Float) {
    private val maxSide: Float get() = min(imageWidth, imageHeight).toFloat()
    private val minSide: Float get() = min(maxSide, MIN_SIDE)

    /** 0 = as much of the picture as fits, 1 = closest. Even steps on a log scale, for a slider. */
    val zoom: Float
        get() = if (maxSide <= minSide) 0f else (ln(maxSide / side) / ln(maxSide / minSide)).coerceIn(0f, 1f)

    fun withZoom(z: Float): CropBox = sized(maxSide * (minSide / maxSide).pow(z.coerceIn(0f, 1f)))

    /** Pinch: [factor] above 1 zooms in. */
    fun zoomedBy(factor: Float): CropBox = if (factor <= 0f) this else sized(side / factor)

    /** Moves the square by [dx], [dy] picture pixels. */
    fun movedBy(dx: Float, dy: Float): CropBox = copy(left = left + dx, top = top + dy).clamped()

    /** Whole pixels for cutting the picture: x, y, side. */
    fun pixels(): Triple<Int, Int, Int> {
        val s = side.roundToInt().coerceIn(1, min(imageWidth, imageHeight))
        return Triple(left.roundToInt().coerceIn(0, imageWidth - s), top.roundToInt().coerceIn(0, imageHeight - s), s)
    }

    private fun sized(newSide: Float): CropBox {
        val s = newSide.coerceIn(minSide, maxSide)
        val cx = left + side / 2
        val cy = top + side / 2
        return copy(left = cx - s / 2, top = cy - s / 2, side = s).clamped()
    }

    private fun clamped(): CropBox =
        copy(left = left.coerceIn(0f, imageWidth - side), top = top.coerceIn(0f, imageHeight - side))

    companion object {
        /** The smallest square, in picture pixels: about an app icon in a phone screenshot is 120-200. */
        const val MIN_SIDE = 24f

        /** The largest centred square. */
        fun centred(width: Int, height: Int): CropBox {
            require(width > 0 && height > 0)
            val s = min(width, height).toFloat()
            return CropBox(width, height, (width - s) / 2, (height - s) / 2, s)
        }
    }
}
