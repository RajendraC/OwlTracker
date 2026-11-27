package com.owl.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours the user can pick in Settings, each with a light and a dark version. Green, amber and red keep their
 * meaning in all of them (StatusColors); the owl and the category pictures keep their own colours. Every pair of
 * text and background was checked for contrast (4.5:1 for text, 3:1 for large text, rings and buttons).
 */
enum class Palette(val key: String, val label: String) {
    CREAM("cream", "Cream & Mango"),
    TEAL("teal", "Mango & Teal"),
    INDIGO("indigo", "Indigo & Saffron"),
    MINT("mint", "Mint & Coral"),
    PLUM("plum", "Plum & Marigold");

    fun scheme(dark: Boolean): ColorScheme = when (this) {
        CREAM -> if (dark) DarkColors else LightColors
        TEAL -> if (dark) TealDark else TealLight
        INDIGO -> if (dark) IndigoDark else IndigoLight
        MINT -> if (dark) MintDark else MintLight
        PLUM -> if (dark) PlumDark else PlumLight
    }

    fun brand(dark: Boolean): BrandColors = when (this) {
        CREAM -> if (dark) DarkBrand else LightBrand
        TEAL -> if (dark) TealDarkBrand else TealLightBrand
        INDIGO -> if (dark) IndigoDarkBrand else IndigoLightBrand
        MINT -> if (dark) MintDarkBrand else MintLightBrand
        PLUM -> if (dark) PlumDarkBrand else PlumLightBrand
    }

    /** The empty part of rings and bars. */
    fun track(dark: Boolean): Color = when (this) {
        CREAM -> if (dark) Color(0xFF3A2A1E) else Color(0xFFF1E2CC)
        TEAL -> if (dark) Color(0xFF1F3B3E) else Color(0xFFE3EAE7)
        INDIGO -> if (dark) Color(0xFF2E3266) else Color(0xFFE1E1F4)
        MINT -> if (dark) Color(0xFF1F4038) else Color(0xFFDDEDE6)
        PLUM -> if (dark) Color(0xFF40213A) else Color(0xFFF0DCE4)
    }

    /** For the picker in Settings: background, header, button and Add colours of the light version. */
    val swatch: List<Color> get() = brand(false).let { b -> listOf(scheme(false).background, b.header[1], scheme(false).primary, b.add[1]) }

    companion object {
        fun from(key: String?): Palette = entries.firstOrNull { it.key == key } ?: CREAM
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.CREAM }

private fun clay(surface: Long, pressed: Long, shade: Color, shadow: Long, dark: Boolean) = ClayColors(
    surface = Color(surface), pressed = Color(pressed),
    light = Color.White.copy(alpha = if (dark) 0.08f else 0.95f), shade = shade, shadow = Color(shadow),
)

// ---------------- Mango & Teal ----------------

private val TealLight = LightColors.copy(
    primary = Color(0xFF0E4F55), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE9E7), onPrimaryContainer = Color(0xFF0B3337),
    secondary = Color(0xFF13707A), onSecondary = Color.White, secondaryContainer = Color(0xFFDDF0EE), onSecondaryContainer = Color(0xFF0B3337),
    background = Color(0xFFFBF6EE), onBackground = Color(0xFF13232A), surface = Color(0xFFFBF6EE), onSurface = Color(0xFF13232A),
    surfaceVariant = Color(0xFFEDF2F0), onSurfaceVariant = Color(0xFF5D6B70),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF6F8F6), surfaceContainerHighest = Color(0xFFEDF2F0),
    inverseSurface = Color(0xFF1C2E33), inverseOnSurface = Color(0xFFEAF3F2), inversePrimary = Color(0xFFFFC24D),
    outline = Color(0xFF8A9A9C), outlineVariant = Color(0xFFE2E7E4),
)
private val TealDark = DarkColors.copy(
    primary = Color(0xFFFFC24D), onPrimary = Color(0xFF13232A),
    primaryContainer = Color(0xFF1F4A4E), onPrimaryContainer = Color(0xFFCFF1EE),
    secondary = Color(0xFF7FD1CC), onSecondary = Color(0xFF0B3337), secondaryContainer = Color(0xFF1C3A3D), onSecondaryContainer = Color(0xFFCFF1EE),
    background = Color(0xFF0B1A1C), onBackground = Color(0xFFE8F3F2), surface = Color(0xFF0B1A1C), onSurface = Color(0xFFE8F3F2),
    surfaceVariant = Color(0xFF1C3A3D), onSurfaceVariant = Color(0xFF9DB7B6),
    surfaceContainerLowest = Color(0xFF071214), surfaceContainerLow = Color(0xFF0F2224), surfaceContainer = Color(0xFF13292C),
    surfaceContainerHigh = Color(0xFF183235), surfaceContainerHighest = Color(0xFF1D3A3D), surfaceBright = Color(0xFF1D3A3D),
    inverseSurface = Color(0xFFE8F3F2), inverseOnSurface = Color(0xFF13292C), inversePrimary = Color(0xFF0E4F55),
    outline = Color(0xFF6F8A8B), outlineVariant = Color(0xFF24464A),
)
private val TealLightBrand = LightBrand.copy(
    header = listOf(Color(0xFF0E4F55), Color(0xFF13707A), Color(0xFF1C8A8F)), onHeader = Color.White, onHeaderSoft = Color(0xFFFFD27A),
    headerButton = Color.White.copy(alpha = 0.18f), onAdd = Color(0xFF13232A),
    bar = Color.White, barIdle = Color(0xFF66767B), barActive = Color(0xFF0E4F55), spentBg = Color(0xFFEDF4F2), shadow = Color(0xFF0E4F55),
    clay = clay(0xFFFFFFFF, 0xFFE6EFEC, Color(0xFF0E4F55).copy(alpha = 0.12f), 0xFF0E4F55, false),
    hills = listOf(Color(0xFF2A9A9E), Color(0xFF1C8A8F), Color(0xFF13707A)), dish = Color(0xFFEDF2F0),
)
private val TealDarkBrand = DarkBrand.copy(
    header = listOf(Color(0xFF13585F), Color(0xFF0E3E43), Color(0xFF0B2427)), onHeader = Color.White, onHeaderSoft = Color(0xFFFFD27A),
    headerButton = Color.White.copy(alpha = 0.12f), onAdd = Color(0xFF13232A),
    bar = Color(0xFF102326), barIdle = Color(0xFF8FA9A8), barActive = Color(0xFFFFC24D), spentBg = Color(0xFF1C3A3D),
    clay = clay(0xFF13292C, 0xFF0E2022, Color.Black.copy(alpha = 0.4f), 0xFF000000, true),
    hills = listOf(Color(0xFF0F3438), Color(0xFF0D2C2F), Color(0xFF0B2427)), dish = Color(0xFF0E2022),
)

// ---------------- Indigo & Saffron ----------------

private val Saffron = listOf(Color(0xFFFFE0A0), Color(0xFFFFB547), Color(0xFFE08A10))

private val IndigoLight = LightColors.copy(
    primary = Color(0xFF2A2F6B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E4FA), onPrimaryContainer = Color(0xFF1B1E45),
    secondary = Color(0xFF3A3F86), onSecondary = Color.White, secondaryContainer = Color(0xFFE8E8FB), onSecondaryContainer = Color(0xFF1B1E45),
    background = Color(0xFFF3F2FF), onBackground = Color(0xFF1B1E45), surface = Color(0xFFF3F2FF), onSurface = Color(0xFF1B1E45),
    surfaceVariant = Color(0xFFE8E8FB), onSurfaceVariant = Color(0xFF5E6190),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF7F7FF), surfaceContainerHighest = Color(0xFFEEEEFB),
    inverseSurface = Color(0xFF24285A), inverseOnSurface = Color(0xFFF3F1FF), inversePrimary = Color(0xFFFFB547),
    outline = Color(0xFF8A8CB8), outlineVariant = Color(0xFFDCDCF2),
)
private val IndigoDark = DarkColors.copy(
    primary = Color(0xFFFFB547), onPrimary = Color(0xFF1B1E45),
    primaryContainer = Color(0xFF2E3270), onPrimaryContainer = Color(0xFFE3E4FA),
    secondary = Color(0xFFB9BCF2), onSecondary = Color(0xFF1B1E45), secondaryContainer = Color(0xFF2A2E5E), onSecondaryContainer = Color(0xFFE3E4FA),
    background = Color(0xFF12142B), onBackground = Color(0xFFF3F1FF), surface = Color(0xFF12142B), onSurface = Color(0xFFF3F1FF),
    surfaceVariant = Color(0xFF2A2E5E), onSurfaceVariant = Color(0xFFA7A9D0),
    surfaceContainerLowest = Color(0xFF0C0E20), surfaceContainerLow = Color(0xFF171A38), surfaceContainer = Color(0xFF1E2147),
    surfaceContainerHigh = Color(0xFF24285A), surfaceContainerHighest = Color(0xFF2A2E5E), surfaceBright = Color(0xFF2A2E5E),
    inverseSurface = Color(0xFFF3F1FF), inverseOnSurface = Color(0xFF1E2147), inversePrimary = Color(0xFF2A2F6B),
    outline = Color(0xFF7F82B0), outlineVariant = Color(0xFF30346A),
)
private val IndigoLightBrand = LightBrand.copy(
    header = listOf(Color(0xFF1B1E45), Color(0xFF2A2F6B), Color(0xFF3A3F86)), onHeader = Color.White, onHeaderSoft = Color(0xFFFFB547),
    headerButton = Color.White.copy(alpha = 0.18f), add = Saffron, onAdd = Color(0xFF1B1E45),
    bar = Color.White, barIdle = Color(0xFF6E7099), barActive = Color(0xFF2A2F6B), spentBg = Color(0xFFEEEEFB), shadow = Color(0xFF1B1E45),
    clay = clay(0xFFFFFFFF, 0xFFE6E6F8, Color(0xFF2A2F6B).copy(alpha = 0.12f), 0xFF1B1E45, false),
    hills = listOf(Color(0xFF4A50A0), Color(0xFF3A3F86), Color(0xFF2A2F6B)), dish = Color(0xFFECECFA),
)
private val IndigoDarkBrand = DarkBrand.copy(
    header = listOf(Color(0xFF2A2F6B), Color(0xFF1E2150), Color(0xFF14173A)), onHeader = Color.White, onHeaderSoft = Color(0xFFFFB547),
    headerButton = Color.White.copy(alpha = 0.12f), add = Saffron, onAdd = Color(0xFF1B1E45),
    bar = Color(0xFF1B1E45), barIdle = Color(0xFF9C9FCB), barActive = Color(0xFFFFB547), spentBg = Color(0xFF2A2E5E),
    clay = clay(0xFF1E2147, 0xFF171A38, Color.Black.copy(alpha = 0.4f), 0xFF000000, true),
    hills = listOf(Color(0xFF232760), Color(0xFF1C1F50), Color(0xFF14173A)), dish = Color(0xFF171A38),
)

// ---------------- Mint & Coral ----------------

private val Coral = listOf(Color(0xFFFFC2B0), Color(0xFFFF7A59), Color(0xFFE0512F))

private val MintLight = LightColors.copy(
    // A deeper coral for buttons and links: white on it, and it on white, both read (5.6:1)
    primary = Color(0xFFB83F25), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6DE), onPrimaryContainer = Color(0xFF4A1A0C),
    secondary = Color(0xFF127A55), onSecondary = Color.White, secondaryContainer = Color(0xFFD7F5E8), onSecondaryContainer = Color(0xFF0B3A28),
    background = Color(0xFFF2FAF6), onBackground = Color(0xFF10302A), surface = Color(0xFFF2FAF6), onSurface = Color(0xFF10302A),
    surfaceVariant = Color(0xFFE6F4EE), onSurfaceVariant = Color(0xFF4E6B63),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF5FBF8), surfaceContainerHighest = Color(0xFFE6F4EE),
    inverseSurface = Color(0xFF1A3A32), inverseOnSurface = Color(0xFFE6F7EF), inversePrimary = Color(0xFFFF8A6B),
    outline = Color(0xFF7F9A92), outlineVariant = Color(0xFFDCEBE4),
)
private val MintDark = DarkColors.copy(
    primary = Color(0xFFFF8A6B), onPrimary = Color(0xFF2A0F06),
    primaryContainer = Color(0xFF3A2620), onPrimaryContainer = Color(0xFFFFD9CE),
    secondary = Color(0xFF3DDC97), onSecondary = Color(0xFF06291B), secondaryContainer = Color(0xFF1D3D35), onSecondaryContainer = Color(0xFFC9F5E2),
    background = Color(0xFF0D1F1A), onBackground = Color(0xFFE6F7EF), surface = Color(0xFF0D1F1A), onSurface = Color(0xFFE6F7EF),
    surfaceVariant = Color(0xFF1D3D35), onSurfaceVariant = Color(0xFF9CC0B4),
    surfaceContainerLowest = Color(0xFF08140F), surfaceContainerLow = Color(0xFF112822), surfaceContainer = Color(0xFF15302A),
    surfaceContainerHigh = Color(0xFF1A3831), surfaceContainerHighest = Color(0xFF1F4038), surfaceBright = Color(0xFF1F4038),
    inverseSurface = Color(0xFFE6F7EF), inverseOnSurface = Color(0xFF15302A), inversePrimary = Color(0xFFB83F25),
    outline = Color(0xFF6F938A), outlineVariant = Color(0xFF24473F),
)
private val MintLightBrand = LightBrand.copy(
    header = listOf(Color(0xFFC9F0DE), Color(0xFFDDF5EA), Color(0xFFF2FAF6)), onHeader = Color(0xFF10302A), onHeaderSoft = Color(0xFF127A55),
    headerButton = Color.White.copy(alpha = 0.7f), add = Coral, onAdd = Color(0xFF2A0F06),
    bar = Color.White, barIdle = Color(0xFF66807A), barActive = Color(0xFFB83F25), spentBg = Color(0xFFE6F4EE), shadow = Color(0xFF10302A),
    clay = clay(0xFFFFFFFF, 0xFFE2F2EA, Color(0xFF10302A).copy(alpha = 0.10f), 0xFF10302A, false),
    hills = listOf(Color(0xFFB5E8D0), Color(0xFFA3E0C4), Color(0xFF8FD6B6)), dish = Color(0xFFE8F5EF),
)
private val MintDarkBrand = DarkBrand.copy(
    header = listOf(Color(0xFF164237), Color(0xFF12352C), Color(0xFF0D1F1A)), onHeader = Color(0xFFE6F7EF), onHeaderSoft = Color(0xFF3DDC97),
    headerButton = Color.White.copy(alpha = 0.12f), add = Coral, onAdd = Color(0xFF2A0F06),
    bar = Color(0xFF112822), barIdle = Color(0xFF8FB0A6), barActive = Color(0xFFFF8A6B), spentBg = Color(0xFF1D3D35),
    clay = clay(0xFF15302A, 0xFF112822, Color.Black.copy(alpha = 0.4f), 0xFF000000, true),
    hills = listOf(Color(0xFF143A31), Color(0xFF11312A), Color(0xFF0E2822)), dish = Color(0xFF112822),
)

// ---------------- Plum & Marigold ----------------

private val Marigold = listOf(Color(0xFFFFE09A), Color(0xFFFFA41B), Color(0xFFD97C06))

private val PlumLight = LightColors.copy(
    primary = Color(0xFF6E2560), onPrimary = Color.White,
    primaryContainer = Color(0xFFF8E0EC), onPrimaryContainer = Color(0xFF3A1234),
    secondary = Color(0xFF8E3472), onSecondary = Color.White, secondaryContainer = Color(0xFFF8E6EF), onSecondaryContainer = Color(0xFF3A1234),
    background = Color(0xFFFFF4F0), onBackground = Color(0xFF2E1229), surface = Color(0xFFFFF4F0), onSurface = Color(0xFF2E1229),
    surfaceVariant = Color(0xFFF8E6EF), onSurfaceVariant = Color(0xFF7A5A72),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFFFF9F7), surfaceContainerHighest = Color(0xFFFBEDF1),
    inverseSurface = Color(0xFF3A1A34), inverseOnSurface = Color(0xFFF8E6EF), inversePrimary = Color(0xFFFFA41B),
    outline = Color(0xFFA08497), outlineVariant = Color(0xFFF0DCE4),
)
private val PlumDark = DarkColors.copy(
    primary = Color(0xFFFFB547), onPrimary = Color(0xFF2E1229),
    primaryContainer = Color(0xFF4A1E42), onPrimaryContainer = Color(0xFFF8E0EC),
    secondary = Color(0xFFE7A6D2), onSecondary = Color(0xFF3A1234), secondaryContainer = Color(0xFF3E1F38), onSecondaryContainer = Color(0xFFF8E0EC),
    background = Color(0xFF1C0E1A), onBackground = Color(0xFFF8E6EF), surface = Color(0xFF1C0E1A), onSurface = Color(0xFFF8E6EF),
    surfaceVariant = Color(0xFF3E1F38), onSurfaceVariant = Color(0xFFCFA9C2),
    surfaceContainerLowest = Color(0xFF12080F), surfaceContainerLow = Color(0xFF241020), surfaceContainer = Color(0xFF2C1528),
    surfaceContainerHigh = Color(0xFF351A30), surfaceContainerHighest = Color(0xFF3E1F38), surfaceBright = Color(0xFF3E1F38),
    inverseSurface = Color(0xFFF8E6EF), inverseOnSurface = Color(0xFF2C1528), inversePrimary = Color(0xFF6E2560),
    outline = Color(0xFF9A7890), outlineVariant = Color(0xFF4A2A44),
)
private val PlumLightBrand = LightBrand.copy(
    header = listOf(Color(0xFF4A1942), Color(0xFF6E2560), Color(0xFF8E3472)), onHeader = Color.White, onHeaderSoft = Color(0xFFFFC861),
    headerButton = Color.White.copy(alpha = 0.18f), add = Marigold, onAdd = Color(0xFF2E1229),
    bar = Color.White, barIdle = Color(0xFF8A6A80), barActive = Color(0xFF6E2560), spentBg = Color(0xFFFBEAF1), shadow = Color(0xFF4A1942),
    clay = clay(0xFFFFFFFF, 0xFFF6E2EA, Color(0xFF6E2560).copy(alpha = 0.12f), 0xFF4A1942, false),
    hills = listOf(Color(0xFFA0447F), Color(0xFF8E3472), Color(0xFF6E2560)), dish = Color(0xFFFBEAF1),
)
private val PlumDarkBrand = DarkBrand.copy(
    header = listOf(Color(0xFF6A2460), Color(0xFF521A4A), Color(0xFF2A1226)), onHeader = Color.White, onHeaderSoft = Color(0xFFFFC861),
    headerButton = Color.White.copy(alpha = 0.12f), add = Marigold, onAdd = Color(0xFF2E1229),
    bar = Color(0xFF24101F), barIdle = Color(0xFFC09AB4), barActive = Color(0xFFFFB547), spentBg = Color(0xFF3E1F38),
    clay = clay(0xFF2C1528, 0xFF241020, Color.Black.copy(alpha = 0.4f), 0xFF000000, true),
    hills = listOf(Color(0xFF4A1A42), Color(0xFF3A1434), Color(0xFF2A1226)), dish = Color(0xFF241020),
)
