package com.owl.tracker.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.owl.tracker.core.CropBox
import com.owl.tracker.core.MerchantPicture
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Turning a picture the user picked into a merchant icon. Everything happens on the phone. */
object PictureIcons {
    /** Pictures are scaled down to this on opening: plenty for cutting out a 144 px icon. */
    private const val MAX_SIDE = 2048

    /** Opens a picture from Android's photo picker, scaled down if large. Null if it can't be read. Off the main thread. */
    fun load(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val f = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * f).roundToInt().coerceAtLeast(1), (info.size.height * f).roundToInt().coerceAtLeast(1))
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()?.takeIf { it.width > 0 && it.height > 0 }

    /** The square [box] of [source], as a small PNG ([MerchantPicture.SIZE_PX] square). */
    fun iconPng(source: Bitmap, box: CropBox): ByteArray {
        val (x, y, side) = box.pixels()
        val square = Bitmap.createBitmap(source, x, y, side, side)
        val icon = Bitmap.createScaledBitmap(square, MerchantPicture.SIZE_PX, MerchantPicture.SIZE_PX, true)
        return ByteArrayOutputStream().use { out ->
            icon.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}

/** Draws the part of [image] inside [box], filling this canvas. */
private fun DrawScope.drawCrop(image: ImageBitmap, box: CropBox) {
    val k = size.width / box.side
    withTransform({
        translate(-box.left * k, -box.top * k)
        scale(k, k, pivot = Offset.Zero)
    }) { drawImage(image, filterQuality = FilterQuality.Medium) }
}

/**
 * Full screen: move and zoom the picture until the icon fills the square, then "Use picture".
 * [onUse] gets the chosen square, in the picture's own pixels.
 */
@Composable
fun PictureCropDialog(source: Bitmap, onCancel: () -> Unit, onUse: (CropBox) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    var box by remember(source) { mutableStateOf(CropBox.centred(source.width, source.height)) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).testTag("crop_dialog"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Fit the icon in the square", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Drag to move the picture. Pinch, or use the slider, to zoom in on the icon.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
                        .semantics { contentDescription = "Picture to crop" }
                        .testTag("crop_area")
                        .pointerInput(source) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val perPx = box.side / size.width
                                box = box.zoomedBy(zoom).movedBy(-pan.x * perPx, -pan.y * perPx)
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) { drawCrop(image, box) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Zoom", style = MaterialTheme.typography.labelLarge)
                    Slider(value = box.zoom, onValueChange = { box = box.withZoom(it) }, modifier = Modifier.weight(1f).testTag("crop_zoom"))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // How it will look in the lists
                    Canvas(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).testTag("crop_preview")) { drawCrop(image, box) }
                    Text("Preview", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).testTag("crop_cancel")) { Text("Cancel") }
                    Button(onClick = { onUse(box) }, modifier = Modifier.weight(1f).testTag("crop_use")) { Text("Use picture") }
                }
            }
        }
    }
}
