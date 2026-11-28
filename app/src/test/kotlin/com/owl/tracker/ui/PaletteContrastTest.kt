package com.owl.tracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.owl.tracker.ui.theme.Palette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every palette, light and dark, reads well: text 4.5:1 against what it sits on, large text, rings and buttons
 * 3:1 (WCAG 2.1 AA). Checked on the app's own colours, so a palette can't be changed into an unreadable one.
 */
class PaletteContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test
    fun everyPaletteReadsWell() {
        val problems = mutableListOf<String>()
        for (p in Palette.entries) for (dark in listOf(false, true)) {
            val c = p.scheme(dark); val b = p.brand(dark)
            val status = if (dark) Color(0xFF5FC77A) else Color(0xFF2E9E57)
            fun need(what: String, fg: Color, bg: Color, min: Double) {
                val r = ratio(fg, bg)
                if (r < min) problems += "${p.label} ${if (dark) "dark" else "light"}: $what ${"%.1f".format(r)}:1 (needs $min)"
            }
            need("text on the page", c.onSurface, c.background, 4.5)
            need("text on cards", c.onSurface, b.clay.surface, 4.5)
            need("grey text on cards", c.onSurfaceVariant, b.clay.surface, 4.5)
            need("grey text on the page", c.onSurfaceVariant, c.background, 4.5)
            need("button label", c.onPrimary, c.primary, 4.5)
            need("links on cards", c.primary, b.clay.surface, 4.5)
            need("header title", b.onHeader, b.header[1], 4.5)
            need("header greeting", b.onHeaderSoft, b.header[1], 3.0)
            // The header's pills (Reports button, month picker, Month/Week) are clay: card text colours on them
            need("month picker on its pill", c.onSurface, b.clay.surface, 4.5)
            need("Week, not picked, on the pressed pill", c.onSurfaceVariant, b.clay.pressed, 4.5)
            need("Month, picked", c.primary, b.clay.surface, 4.5)
            need("+ on the Add button", b.onAdd, b.add[1], 3.0)
            need("the green ring on cards", status, b.clay.surface, 3.0)
            need("the selected tab", b.barActive, b.bar, 3.0)
            need("idle tabs", b.barIdle, b.bar, 3.0)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
