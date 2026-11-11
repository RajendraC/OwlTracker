package com.owl.tracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The clay look: surfaces that seem moulded rather than printed. Light falls from the top left, so each surface
 * is brighter along its top and left edges and darker along its bottom and right, with a thin rim that catches
 * the light, and it casts a soft warm shadow. A pressed surface (the open tab) is lit the other way, as if pushed in.
 * Drawn with gradients, so it looks the same on every Android version.
 */

/** The colours of the clay look, for the light or dark theme. */
@Immutable
data class ClayColors(
    /** Cards, the tab bar, rows. */
    val surface: Color,
    /** A pressed-in surface (the open tab). */
    val pressed: Color,
    /** The light on the top and left edges. */
    val light: Color,
    /** The shade on the bottom and right edges. */
    val shade: Color,
    /** The shadow cast on the ground. */
    val shadow: Color,
)

val LightClay = ClayColors(
    surface = Color(0xFFFFF7EC),
    pressed = Color(0xFFFFEBD0),
    light = Color.White.copy(alpha = 0.95f),
    shade = Color(0xFFC98C46).copy(alpha = 0.22f),
    shadow = Color(0xFF7A3B18),
)

val DarkClay = ClayColors(
    surface = Color(0xFF2A1D14),
    pressed = Color(0xFF1F150E),
    light = Color.White.copy(alpha = 0.10f),
    shade = Color.Black.copy(alpha = 0.40f),
    shadow = Color.Black,
)

/**
 * Draws this element as clay: [color] filled into [shape], lit from the top left, with a warm shadow [elevation] deep.
 * [pressed] lights it the other way, pushed in, with no shadow.
 */
fun Modifier.clay(shape: Shape, clay: ClayColors, color: Color = clay.surface, elevation: Dp = 10.dp, pressed: Boolean = false): Modifier =
    (if (elevation > 0.dp && !pressed) shadow(elevation, shape, clip = false, ambientColor = clay.shadow, spotColor = clay.shadow) else this)
        .clip(shape)
        .background(color)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val lit = if (pressed) clay.shade else clay.light
            val dim = if (pressed) clay.light.copy(alpha = clay.light.alpha * 0.6f) else clay.shade
            val band = minOf(14.dp.toPx(), h * 0.42f, w * 0.42f)
            val bandDark = minOf(18.dp.toPx(), h * 0.45f, w * 0.45f)
            val top = Brush.verticalGradient(listOf(lit, Color.Transparent), 0f, band)
            val left = Brush.horizontalGradient(listOf(lit.copy(alpha = lit.alpha * 0.7f), Color.Transparent), 0f, band)
            val bottom = Brush.verticalGradient(listOf(Color.Transparent, dim), h - bandDark, h)
            val right = Brush.horizontalGradient(listOf(Color.Transparent, dim.copy(alpha = dim.alpha * 0.7f)), w - bandDark, w)
            val rim = Brush.linearGradient(listOf(lit, Color.Transparent, dim), Offset.Zero, Offset(w, h))
            val outline = shape.createOutline(size, layoutDirection, this)
            val rimStroke = Stroke(1.5.dp.toPx())
            onDrawBehind {
                drawRect(top, size = Size(w, band))
                drawRect(left, size = Size(band, h))
                drawRect(bottom, topLeft = Offset(0f, h - bandDark), size = Size(w, bandDark))
                drawRect(right, topLeft = Offset(w - bandDark, 0f), size = Size(bandDark, h))
                drawOutline(outline, rim, style = rimStroke)
            }
        }

/** A clay surface holding [content], in the theme's clay colours. */
@Composable
fun ClaySurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = LocalBrand.current.clay.surface,
    elevation: Dp = 10.dp,
    pressed: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier.clay(shape, LocalBrand.current.clay, color, elevation, pressed), content = content)
    }
}
