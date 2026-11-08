package com.owl.tracker.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/*
 * The glass look, after Apple's translucent design: see-through cards with a bright edge, floating
 * over a soft wash of colour. The colours come from the theme (so they follow the wallpaper on
 * Android 12+), and the look can be turned off in Settings.
 */

/** True when the glass look is on (Settings → Appearance). */
val LocalGlass = staticCompositionLocalOf { false }

/** The soft colour wash behind everything: three large, blurred pools of colour. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val base = c.background
    // Saturated enough to show through the cards, soft enough to keep text easy to read
    val a = if (dark) 0.30f else 0.34f
    val pools = listOf(
        Triple(Offset(0.0f, 0.06f), 0.80f, lerp(c.primary, c.primaryContainer, 0.35f).copy(alpha = a)),
        Triple(Offset(1.0f, 0.40f), 0.72f, lerp(c.tertiary, c.tertiaryContainer, 0.35f).copy(alpha = a * 0.85f)),
        Triple(Offset(0.12f, 0.95f), 0.85f, lerp(c.primary, Color(0xFFFF9F6B), 0.7f).copy(alpha = a * 0.75f)),
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        pools.forEach { (at, size, colour) ->
            val centre = Offset(at.x * this.size.width, at.y * this.size.height)
            val radius = size * maxOf(this.size.width, this.size.height * 0.6f)
            drawCircle(Brush.radialGradient(listOf(colour, colour.copy(alpha = 0f)), centre, radius), radius, centre)
        }
    }
}

/** A card's see-through fill. */
@Composable
@ReadOnlyComposable
fun glassFill(): Color {
    val dark = isSystemInDarkTheme()
    return MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.44f else 0.52f)
}

/** A thicker fill for things that float over content, like the tab bar (there's no blur behind them). */
@Composable
@ReadOnlyComposable
fun glassFillStrong(): Color {
    val dark = isSystemInDarkTheme()
    return MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.80f else 0.86f)
}

/** The bright rim: light catching the top-left edge and fading out round the card. */
@Composable
@ReadOnlyComposable
fun glassEdge(): BorderStroke {
    val dark = isSystemInDarkTheme()
    val top = Color.White.copy(alpha = if (dark) 0.28f else 0.95f)
    val bottom = Color.White.copy(alpha = if (dark) 0.05f else 0.30f)
    return BorderStroke(1.dp, Brush.linearGradient(listOf(top, bottom, top.copy(alpha = top.alpha * 0.4f))))
}
