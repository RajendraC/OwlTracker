package com.owl.tracker.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.owl.tracker.R
import com.owl.tracker.core.BudgetState

/*
 * Mango & Cocoa: a warm cream ground, a ripe-mango header and Add button, and cocoa for the things you press;
 * surfaces in the clay look (Clay.kt).
 * The colours are fixed (not taken from the wallpaper) so the app looks the same on every phone, and budget
 * states always mean the same thing.
 */

/** Cocoa: buttons, the selected tab, links. */
val Cocoa = Color(0xFF4B2412)

/** Ripe mango: the Add button and the dot under the selected tab. */
val Mango = Color(0xFFFFB21A)

/**
 * True while the app draws dark. Use this, not isSystemInDarkTheme(): the user can pick light or dark
 * in Settings whatever the phone is set to.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = Cocoa,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE7AE),
    onPrimaryContainer = Color(0xFF3A1C0C),
    secondary = Color(0xFF9A4A0E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2C4),
    onSecondaryContainer = Color(0xFF3A1C0C),
    tertiary = Color(0xFF1E7A3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF3D2),
    onTertiaryContainer = Color(0xFF0E3A1C),
    background = Color(0xFFFFF1E0),
    onBackground = Color(0xFF2A1708),
    surface = Color(0xFFFFF1E0),
    onSurface = Color(0xFF2A1708),
    surfaceVariant = Color(0xFFF6EADB),
    onSurfaceVariant = Color(0xFF6A5040),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF7EC),
    surfaceContainer = Color(0xFFFFF7EC),
    surfaceContainerHigh = Color(0xFFFBF1E4),
    surfaceContainerHighest = Color(0xFFF6EADB),
    surfaceBright = Color.White,
    inverseSurface = Color(0xFF3A2A1E),
    inverseOnSurface = Color(0xFFFFF1E2),
    inversePrimary = Color(0xFFFFC24D),
    outline = Color(0xFF9C8676),
    outlineVariant = Color(0xFFEEDFCB),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC24D),
    onPrimary = Color(0xFF3A1C0C),
    primaryContainer = Color(0xFF5A2E14),
    onPrimaryContainer = Color(0xFFFFE3A3),
    secondary = Color(0xFFFFB27A),
    onSecondary = Color(0xFF4A1F06),
    secondaryContainer = Color(0xFF4A2A14),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF8FE0A8),
    onTertiary = Color(0xFF00391A),
    tertiaryContainer = Color(0xFF1E4A2C),
    onTertiaryContainer = Color(0xFFB7F2C6),
    background = Color(0xFF1A120C),
    onBackground = Color(0xFFF6EBDD),
    surface = Color(0xFF1A120C),
    onSurface = Color(0xFFF6EBDD),
    surfaceVariant = Color(0xFF3A2A1E),
    onSurfaceVariant = Color(0xFFD7C2AF),
    surfaceContainerLowest = Color(0xFF120C08),
    surfaceContainerLow = Color(0xFF22180F),
    surfaceContainer = Color(0xFF2A1D14),
    surfaceContainerHigh = Color(0xFF34251A),
    surfaceContainerHighest = Color(0xFF3E2D20),
    surfaceBright = Color(0xFF3E2D20),
    inverseSurface = Color(0xFFF6EBDD),
    inverseOnSurface = Color(0xFF2A1D14),
    inversePrimary = Cocoa,
    outline = Color(0xFFA08B7A),
    outlineVariant = Color(0xFF4A382A),
    error = Color(0xFFFFB4AB),
)

/** Budget state colours. Fixed, so their meaning never changes. Always paired with an icon and words. */
@Immutable
data class StatusColors(
    val okFg: Color, val okBg: Color,
    val warnFg: Color, val warnBg: Color, val warnRing: Color,
    val overFg: Color, val overBg: Color, val overRing: Color,
    val track: Color,
) {
    fun fg(s: BudgetState) = when (s) { BudgetState.ON_TRACK -> okFg; BudgetState.HEADS_UP -> warnFg; BudgetState.OVER -> overFg }
    fun bg(s: BudgetState) = when (s) { BudgetState.ON_TRACK -> okBg; BudgetState.HEADS_UP -> warnBg; BudgetState.OVER -> overBg }
    fun ring(s: BudgetState, ok: Color) = when (s) { BudgetState.ON_TRACK -> ok; BudgetState.HEADS_UP -> warnRing; BudgetState.OVER -> overRing }
}

private val LightStatus = StatusColors(
    okFg = Color(0xFF1E6A2E), okBg = Color(0xFFE7F6DA),
    warnFg = Color(0xFF7A4A00), warnBg = Color(0xFFFFF0CF), warnRing = Color(0xFFE08A00),
    overFg = Color(0xFFB3261E), overBg = Color(0xFFFFE6E2), overRing = Color(0xFFD93B2B),
    track = Color(0xFFF1E2CC),
)
private val DarkStatus = StatusColors(
    okFg = Color(0xFF9EE6A8), okBg = Color(0xFF1F3A22),
    warnFg = Color(0xFFFFD08A), warnBg = Color(0xFF3D2C10), warnRing = Color(0xFFE59B2E),
    overFg = Color(0xFFFFB4AB), overBg = Color(0xFF4A1E1A), overRing = Color(0xFFF2675B),
    track = Color(0xFF3A2A1E),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** The brand's own colours, beyond Material's roles: the mango header and the Add button. */
@Immutable
data class BrandColors(
    /** The header's gradient, top to bottom. */
    val header: List<Color>,
    /** Text on the header. */
    val onHeader: Color,
    val onHeaderSoft: Color,
    /** The round buttons on the header. */
    val headerButton: Color,
    /** The Add button's glossy mango: light, middle and deep. */
    val add: List<Color>,
    val onAdd: Color,
    /** The bottom bar. */
    val bar: Color,
    val barIdle: Color,
    val barActive: Color,
    /** Soft fills for the small figures under the budget: spent, over, could save. */
    val spentBg: Color,
    val shadow: Color,
    /** The clay look's colours. */
    val clay: ClayColors,
    /** The three layers of hills at the foot of Home's header, back to front. */
    val hills: List<Color>,
    /** The dish the budget ring sits in. */
    val dish: Color,
)

private val LightBrand = BrandColors(
    header = listOf(Color(0xFFFFF1B3), Color(0xFFFFC85C), Color(0xFFFF9A3D)),
    onHeader = Color(0xFF2A1708), onHeaderSoft = Color(0xFF7A3B18),
    headerButton = Color.White.copy(alpha = 0.7f),
    add = listOf(Color(0xFFFFE08A), Mango, Color(0xFFE07A0A)), onAdd = Cocoa,
    bar = Color(0xFFFFF7EC), barIdle = Color(0xFF8A7466), barActive = Cocoa,
    spentBg = Color(0xFFFFF1D6),
    shadow = Color(0xFF7A3B18),
    clay = LightClay,
    hills = listOf(Color(0xFFFFB347), Color(0xFFFF9A3D), Color(0xFFF6862E)),
    dish = Color(0xFFFBEBD6),
)
private val DarkBrand = BrandColors(
    header = listOf(Color(0xFF4A2C12), Color(0xFF3A2210), Color(0xFF26170C)),
    onHeader = Color(0xFFFFF1DE), onHeaderSoft = Color(0xFFFFC98A),
    headerButton = Color.White.copy(alpha = 0.12f),
    add = listOf(Color(0xFFFFE08A), Mango, Color(0xFFE07A0A)), onAdd = Cocoa,
    bar = Color(0xFF2A1D14), barIdle = Color(0xFFB8A08C), barActive = Color(0xFFFFC24D),
    spentBg = Color(0xFF3A2A1E),
    shadow = Color.Black,
    clay = DarkClay,
    hills = listOf(Color(0xFF3A2410), Color(0xFF301D0D), Color(0xFF26170A)),
    dish = Color(0xFF1F150E),
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

/** The Add button's mango ball: a bright spot at the top left, deepening to burnt orange at the edge. */
val SphereBrush: androidx.compose.ui.graphics.Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader =
        androidx.compose.ui.graphics.RadialGradientShader(
            center = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.28f),
            radius = maxOf(size.width, size.height) * 0.82f,
            colors = listOf(Color(0xFFFFFBE6), Color(0xFFFFE08A), Mango, Color(0xFFE8840F), Color(0xFFB65A05)),
            colorStops = listOf(0f, 0.18f, 0.52f, 0.78f, 1f),
        )
}

/** A glossy ball of colour, like the pictures: [colors] light, middle and deep, lit from the top left. */
fun glossBrush(colors: List<Color>): androidx.compose.ui.graphics.Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader =
        androidx.compose.ui.graphics.RadialGradientShader(
            center = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.3f),
            radius = maxOf(size.width, size.height) * 0.78f,
            colors = colors,
            colorStops = listOf(0f, 0.55f, 1f).take(colors.size),
        )
}

/**
 * Plus Jakarta Sans (SIL Open Font License, see assets/fonts/OFL.txt), from the app's own files. If the phone
 * can't read it, the app falls back to the system font rather than failing.
 */
private fun appFont(context: Context): FontFamily {
    val ok = runCatching { context.resources.getFont(R.font.jakarta_extrabold) }.getOrNull() != null
    if (!ok) return FontFamily.Default
    return FontFamily(
        Font(R.font.jakarta_regular, FontWeight.Normal),
        Font(R.font.jakarta_medium, FontWeight.Medium),
        Font(R.font.jakarta_semibold, FontWeight.SemiBold),
        Font(R.font.jakarta_bold, FontWeight.Bold),
        Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    )
}

private fun appTypography(family: FontFamily): Typography {
    val t = Typography()
    fun TextStyle.f(w: FontWeight? = null, spacing: Float? = null) =
        copy(fontFamily = family, fontWeight = w ?: fontWeight, letterSpacing = spacing?.sp ?: letterSpacing)
    return t.copy(
        displayLarge = t.displayLarge.f(FontWeight.ExtraBold), displayMedium = t.displayMedium.f(FontWeight.ExtraBold),
        displaySmall = t.displaySmall.f(FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.f(FontWeight.ExtraBold, -0.5f),
        headlineMedium = t.headlineMedium.f(FontWeight.ExtraBold, -0.4f),
        headlineSmall = t.headlineSmall.f(FontWeight.ExtraBold, -0.2f),
        titleLarge = t.titleLarge.f(FontWeight.ExtraBold),
        titleMedium = t.titleMedium.f(FontWeight.Bold),
        titleSmall = t.titleSmall.f(FontWeight.Bold),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(FontWeight.Medium), bodySmall = t.bodySmall.f(FontWeight.Medium),
        labelLarge = t.labelLarge.f(FontWeight.Bold), labelMedium = t.labelMedium.f(FontWeight.Bold), labelSmall = t.labelSmall.f(FontWeight.SemiBold),
    )
}

val Numeric = TextStyle(fontFeatureSettings = "tnum")

/**
 * The app's theme. [dynamic] is no longer used (the colours are the app's own, not the wallpaper's); it stays so
 * callers keep compiling.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun OwlTrackerTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val typography = remember { appTypography(appFont(context)) }
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
        LocalBrand provides if (dark) DarkBrand else LightBrand,
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = typography, content = content)
    }
}
