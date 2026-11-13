package com.owl.tracker.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/*
 * Big text. Many people set their phone's text size (and display size) larger. Android then makes every sp bigger
 * while the screen stays the same width, so a layout drawn for normal text squeezes: words break letter by letter,
 * amounts are cut to "₹1,14…". These pieces let a layout give way instead:
 *  - FitText: one line (or a few) that gets smaller, down to a limit, instead of being cut or broken mid-word.
 *  - FirstThatFits: the first of several wordings that fits ("November 2026", "Nov 2026", "Nov").
 *  - SideOrStacked: a label and its value side by side when they fit, else the value under the label.
 *  - CapFontScale: for crowded chrome only (tab labels, calendar numbers), text grows less than the phone asks.
 * Never cap the whole app: people who set big text need it.
 */

/** True when the phone's text is at least [scale] times the normal size. */
@Composable
fun textScaleAtLeast(scale: Float): Boolean = LocalDensity.current.fontScale >= scale

/** Inside [content], text grows at most [max] times; the rest of the layout is unchanged. */
@Composable
fun CapFontScale(max: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale <= max) content() else CompositionLocalProvider(LocalDensity provides Density(d.density, max)) { content() }
}

/** A line break between two letters or digits: a word (or an amount) broken in two. */
internal fun TextLayoutResult.breaksAWord(text: CharSequence): Boolean {
    for (i in 0 until lineCount - 1) {
        val end = getLineEnd(i)
        if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) return true
    }
    return false
}

private fun TextLayoutResult.fits(text: CharSequence): Boolean =
    !hasVisualOverflow && (lineCount == 0 || !isLineEllipsized(lineCount - 1)) && !breaksAWord(text)

/** From [max] down to [min]: the biggest size at which the text fits, with no word broken in two. */
private data class WholeWordsAutoSize(val max: TextUnit, val min: TextUnit, val step: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > min.value) {
            if (performLayout(constraints, text, size.sp).fits(text)) return size.sp
            size -= step.value
        }
        // The size returned is the one laid out last
        performLayout(constraints, text, min)
        return min
    }
}

/**
 * Text that gets smaller, down to [minScale] of its size, until it fits in [maxLines] lines without breaking a word.
 * Below that it is cut with "…" as a last resort.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    minScale: Float = 0.65f,
) {
    val size = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val merged = style.merge(
        TextStyle(color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }, fontWeight = fontWeight, textAlign = textAlign ?: TextAlign.Unspecified),
    )
    val autoSize = remember(size, minScale) { WholeWordsAutoSize(size, (size.value * minScale).sp, 0.5.sp) }
    BasicText(
        text, modifier, merged, overflow = TextOverflow.Ellipsis, softWrap = maxLines > 1, maxLines = maxLines, autoSize = autoSize,
    )
}

/**
 * Shows the first of [options] that fits on one line at full size; if none does, the last one, made smaller to fit.
 * Screen readers (and tests) get the first, full one.
 */
@Composable
fun FirstThatFits(
    options: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    minScale: Float = 0.65f,
) {
    val measurer = rememberTextMeasurer()
    val full = options.first()
    BoxWithConstraints(modifier.clearAndSetSemantics { text = AnnotatedString(full) }) {
        val width = constraints.maxWidth
        val pick = options.firstOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width <= width } ?: options.last()
        FitText(pick, style = style, color = color, textAlign = textAlign, minScale = minScale)
    }
}

/** How wide [text] is on one line at [style], in dp. */
@Composable
fun rememberTextWidth(text: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current
    return remember(text, style, d) { with(d) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() } }
}

/**
 * [start] and [end] side by side when they fit (each may wrap by whole words), else [end] under [start]. For a label and
 * its value ("Date" … "Wed, 18 Nov 2026, 7:35 PM"), a name and an amount, a title and a button.
 */
@Composable
fun SideOrStacked(
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    stackedGap: Dp = 2.dp,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(contents = listOf(start, end), modifier = modifier) { (s, e), constraints ->
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val sm = s.firstOrNull()
        val em = e.firstOrNull()
        if (sm == null || em == null) {
            val only = (sm ?: em)?.measure(constraints.copy(minWidth = 0))
            return@Layout layout(only?.width ?: 0, only?.height ?: 0) { only?.place(0, 0) }
        }
        val g = gap.roundToPx()
        val sMax = sm.maxIntrinsicWidth(Constraints.Infinity)
        val sMin = sm.minIntrinsicWidth(Constraints.Infinity)
        val eMax = em.maxIntrinsicWidth(Constraints.Infinity)
        val eMin = em.minIntrinsicWidth(Constraints.Infinity)
        val endWidth = when {
            sMax + g + eMax <= w -> eMax                 // both on one line
            sMin + g + eMax <= w -> eMax                 // the value on one line, the label wraps by words
            sMax + g + eMin <= w -> w - g - sMax         // the label on one line, the value wraps by words
            else -> -1                                   // no room: one under the other
        }
        if (endWidth >= 0) {
            val ep = em.measure(Constraints(minWidth = 0, maxWidth = endWidth))
            val stp = sm.measure(Constraints(minWidth = 0, maxWidth = (w - g - ep.width).coerceAtLeast(0)))
            val h = maxOf(stp.height, ep.height)
            val width = if (constraints.hasBoundedWidth) w else stp.width + g + ep.width
            layout(width, h) {
                stp.place(0, (h - stp.height) / 2)
                ep.place(width - ep.width, (h - ep.height) / 2)
            }
        } else {
            val stp = sm.measure(Constraints(minWidth = 0, maxWidth = w))
            val ep = em.measure(Constraints(minWidth = 0, maxWidth = w))
            val sg = stackedGap.roundToPx()
            layout(w, stp.height + sg + ep.height) {
                stp.place(0, 0)
                ep.place(0, stp.height + sg)
            }
        }
    }
}

/**
 * [count] equal cells, as many to a line as fit, each at least [minCell] wide and wide enough for the longest word in
 * [words] (plus [cellPadding] each side), so a name like "Groceries" is never broken in two: with big text there are
 * fewer, wider cells.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CellGrid(
    count: Int,
    minCell: Dp,
    words: List<String>,
    wordStyle: TextStyle,
    cellPadding: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    cell: @Composable (index: Int, width: Dp) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current
    val widest = remember(words, wordStyle, d) {
        with(d) {
            words.flatMap { it.split(' ', '/', '-') }.filter { it.isNotBlank() }
                .maxOfOrNull { measurer.measure(it, wordStyle, maxLines = 1, softWrap = false).size.width }?.toDp() ?: 0.dp
        }
    }
    BoxWithConstraints(modifier) {
        val need = maxOf(minCell, widest + cellPadding * 2 + 4.dp)
        val cols = ((maxWidth + gap) / (need + gap)).toInt().coerceIn(1, count.coerceAtLeast(1))
        val width = (maxWidth - gap * (cols - 1)) / cols
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap),
            maxItemsInEachRow = cols,
        ) {
            repeat(count) { cell(it, width) }
        }
    }
}
