package com.owl.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.owl.tracker.core.Money

/*
 * A category's months as bars: small ones beside a row (MonthBars) and labelled ones with its budget as a dashed line
 * (BarsWithAmounts). Used by Home's Where it went and What's changing, and by Reports' trend by category.
 */

/** One bar a month, scaled to the row's own biggest; a month before the records is a dashed stub, this one lighter. */
@Composable
fun MonthBars(months: List<Long?>, now: Long, color: Color, modifier: Modifier) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700)) }
    val muted = MaterialTheme.colorScheme.outlineVariant
    val values = months + now
    val top = values.maxOf { it ?: 0L }.coerceAtLeast(1L)
    Canvas(modifier) {
        val slot = size.width / values.size
        val w = slot * 0.58f
        val base = size.height
        values.forEachIndexed { i, v ->
            val x = slot * i + (slot - w) / 2
            if (v == null) {
                drawLine(muted, Offset(x, base - 1.dp.toPx()), Offset(x + w, base - 1.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                return@forEachIndexed
            }
            val hgt = if (v <= 0) 2.dp.toPx() else (size.height * v / top * grow.value).coerceAtLeast(3.dp.toPx())
            val isNow = i == values.lastIndex
            drawRoundRect(
                if (isNow) color.copy(alpha = 0.38f) else color.copy(alpha = 0.55f + 0.45f * i / (values.size - 1)),
                Offset(x, base - hgt), Size(w, hgt), CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
}

/** A category's months with their amounts over the bars, month names under them, and its budget as a dashed line. */
@Composable
fun BarsWithAmounts(months: List<Long?>, now: Long, labels: List<String>, color: Color, budget: Long?, modifier: Modifier) {
    val values = months + now
    val top = (values.maxOf { it ?: 0L }.coerceAtLeast(budget ?: 0L).coerceAtLeast(1L) * 11 / 10)
    val barH = 64.dp
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    CapFontScale(1.3f) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    values.forEachIndexed { i, v ->
                        Column(Modifier.weight(1f).padding(horizontal = 1.5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            FitText(if (v == null) "–" else Money.formatShort(v), style = MaterialTheme.typography.labelSmall, color = muted, minScale = 0.6f)
                            val f = ((v ?: 0L).toFloat() / top).coerceIn(0f, 1f)
                            Box(Modifier.height(barH), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    Modifier.fillMaxWidth(0.62f).height(if (v == null) 2.dp else barH * f.coerceAtLeast(0.03f))
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                        .background(if (v == null) MaterialTheme.colorScheme.outlineVariant else color.copy(alpha = if (i == values.lastIndex) 0.38f else 0.55f + 0.45f * i / values.lastIndex)),
                                )
                            }
                        }
                    }
                }
                // The budget, across the bars
                if (budget != null && budget > 0) {
                    Canvas(Modifier.matchParentSize()) {
                        val y = size.height - barH.toPx() * (budget.toFloat() / top).coerceIn(0f, 1f)
                        drawLine(ink, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, l ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FitText(l, style = MaterialTheme.typography.labelSmall, color = if (i == labels.lastIndex) MaterialTheme.colorScheme.primary else muted, minScale = 0.6f)
                    }
                }
            }
        }
    }
}
