package com.owl.tracker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.owl.tracker.TestApp
import com.owl.tracker.core.CropBox
import com.owl.tracker.core.MerchantPicture
import com.owl.tracker.ui.components.PictureCropDialog
import com.owl.tracker.ui.components.PictureIcons
import com.owl.tracker.ui.theme.OwlTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cutting a merchant icon out of a screenshot: the crop screen, and the PNG it makes. Real pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class PictureCropTest {

    @get:Rule
    val compose = createComposeRule()

    /** A white 1080×1920 "screenshot" with a red 160 px square "app icon" at (700, 900). */
    private fun screenshot(): Bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply {
            drawColor(Color.WHITE)
            drawRect(700f, 900f, 860f, 1060f, Paint().apply { color = Color.RED })
        }
    }

    @Test
    fun theChosenSquareBecomesASmallPngIcon() {
        val png = PictureIcons.iconPng(screenshot(), CropBox(1080, 1920, 700f, 900f, 160f))
        assertTrue("a PNG small enough to keep", MerchantPicture.isPng(png))
        val icon = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertEquals(MerchantPicture.SIZE_PX, icon.width)
        assertEquals(MerchantPicture.SIZE_PX, icon.height)
        assertEquals(Color.RED, icon.getPixel(72, 72))
        assertEquals(Color.RED, icon.getPixel(4, 4))

        // Half on the icon, half off: the left half is white, the right half red
        val halfPng = PictureIcons.iconPng(screenshot(), CropBox(1080, 1920, 620f, 900f, 160f))
        val half = BitmapFactory.decodeByteArray(halfPng, 0, halfPng.size)
        assertEquals(Color.WHITE, half.getPixel(10, 72))
        assertEquals(Color.RED, half.getPixel(134, 72))
    }

    @Test
    fun dragAndZoomChooseTheSquare() {
        var chosen: CropBox? = null
        compose.setContent {
            OwlTrackerTheme(dark = false, dynamic = false) {
                PictureCropDialog(screenshot(), onCancel = {}, onUse = { chosen = it })
            }
        }
        compose.onNodeWithTag("crop_area").performTouchInput { swipeUp() }
        compose.onNodeWithTag("crop_zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithTag("crop_use").performClick()
        compose.waitForIdle()

        assertNotNull("Use picture gives the square", chosen)
        val box = chosen!!
        // Zoomed to about a sixth of the width: sqrt(1080 × 24) ≈ 161 px
        assertEquals(161f, box.side, 2f)
        // Dragging up moved the picture up, so the square sits lower in it than the centre (960)
        assertTrue("square centre ${box.top + box.side / 2}", box.top + box.side / 2 > 960f)
        // And it stays inside the picture
        val (x, y, s) = box.pixels()
        assertTrue(x >= 0 && y >= 0 && x + s <= 1080 && y + s <= 1920)
    }
}
