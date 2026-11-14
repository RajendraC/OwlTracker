package com.owl.tracker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owl.tracker.core.Money
import com.owl.tracker.core.TrendKind
import com.owl.tracker.core.Trends
import com.owl.tracker.data.Category
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.MonthHistory
import com.owl.tracker.ui.components.CapFontScale
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.components.Divider
import com.owl.tracker.ui.components.FitText
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.components.SideOrStacked
import com.owl.tracker.ui.theme.LocalStatusColors

/*
 * Reports → Trend by category: for each category, the last six budget months as bars and this one so far (lighter),
 * which way it's going in words, and its usual month. Tapping one narrows Reports to it, like the ring does.
 */

/** "↑ Rising · 27% more than in Aug": short, because the bars show the amounts. */
fun trendPhrase(months: List<Long?>, labels: List<String>): Pair<String, TrendKind> {
    val kind = Trends.kind(months)
    val known = months.mapIndexedNotNull { i, v -> v?.let { labels.getOrElse(i) { "" } to it } }
    val last3 = known.takeLast(Trends.USUAL_OF)
    fun pct(a: Long, b: Long) = if (b <= 0) 0 else (Math.abs(a - b) * 100 / b).toInt()
    val text = when (kind) {
        TrendKind.RISING -> "↑ Rising · ${pct(last3.last().second, last3.first().second)}% more than in ${last3.first().first}"
        TrendKind.FALLING -> "↓ Falling · ${pct(last3.last().second, last3.first().second)}% less than in ${last3.first().first}"
        TrendKind.STEADY -> "→ Steady"
        TrendKind.UP_AND_DOWN -> "↕ Up and down · ${Money.formatShort(known.minOf { it.second })} to ${Money.formatShort(known.maxOf { it.second })}"
        TrendKind.TOO_EARLY -> known.lastOrNull()?.let { "Only ${it.first} so far" } ?: "Nothing yet"
        TrendKind.NONE -> "Nothing in these months"
    }
    return text to kind
}

private class TrendRow(val category: Category, val months: List<Long?>, val now: Long, val usual: Long)

private const val SHOWN = 6

@Composable
fun CategoryTrendsCard(h: MonthHistory, cats: List<Category>, picked: String?, vm: AppViewModel) {
    val rows = remember(h, cats) {
        cats.filter { it.countsAsSpend && !it.archived }
            .map { TrendRow(it, h.of(it.id), h.now[it.id] ?: 0L, h.usual(it.id)) }
            .filter { r -> r.now > 0 || Trends.known(r.months).any { it > 0 } }
            .sortedWith(compareByDescending<TrendRow> { it.usual }.thenByDescending { it.now })
    }
    if (rows.isEmpty()) return
    var all by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labels = h.labels + MonthHistory.label(h.current)
    Panel(padding = 12.dp, modifier = Modifier.testTag("report_category_trends")) {
        Text("Trend by category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            "The last ${h.months.size} months, and ${labels.last()} so far (lighter). Tap one to see its payments.",
            style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(horizontal = 4.dp),
        )
        Gap(8.dp)
        // Month names once, over the bars' columns
        CapFontScale(1.3f) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp + 36.dp + 12.dp, end = 4.dp)) {
                labels.forEachIndexed { i, l ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FitText(
                            if (i == labels.lastIndex) "$l…" else l, style = MaterialTheme.typography.labelSmall,
                            color = if (i == labels.lastIndex) MaterialTheme.colorScheme.primary else muted, minScale = 0.6f,
                        )
                    }
                }
            }
        }
        val shown = if (all) rows else rows.take(SHOWN)
        shown.forEachIndexed { i, r ->
            if (i > 0) Divider()
            TrendRowView(r, h, labels, r.category.id == picked) { vm.tapReportCategory(r.category.id) }
        }
        if (rows.size > SHOWN) {
            TextButton(onClick = { all = !all }, modifier = Modifier.testTag("trends_all")) {
                Text(if (all) "Fewer categories" else "All ${rows.size} categories")
            }
        }
    }
}

@Composable
private fun TrendRowView(r: TrendRow, h: MonthHistory, labels: List<String>, isPicked: Boolean, onTap: () -> Unit) {
    val c = r.category
    val sc = LocalStatusColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (said, kind) = trendPhrase(r.months, h.labels)
    // Spent on only this month: say so rather than "nothing"
    val phrase = if (kind == TrendKind.NONE && r.now > 0) "New in ${labels.last()}" else said
    val color = when (kind) {
        TrendKind.RISING -> sc.overFg
        TrendKind.FALLING -> sc.okFg
        else -> muted
    }
    val spoken = buildString {
        append("${c.name}. $phrase. ")
        r.months.forEachIndexed { i, v -> if (v != null) append("${labels[i]} ${Money.formatInr(v)}, ") }
        append("${labels.last()} so far ${Money.formatInr(r.now)}.")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (isPicked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onTap)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("trend_${c.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(c, 36.dp)
        Column(Modifier.weight(1f)) {
            SideOrStacked(
                Modifier.fillMaxWidth(),
                start = { Text(c.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)) },
                end = {
                    Text(
                        if (r.usual > 0) "${Money.formatShort(r.usual)} a month" else "—",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("trend_usual_${c.id}"),
                    )
                },
            )
            Text(phrase, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = color)
            Gap(6.dp)
            MonthBars(r.months, r.now, Color(c.color), Modifier.fillMaxWidth().height(38.dp).semantics { contentDescription = spoken })
        }
    }
}

/** One bar a month, scaled to the row's own biggest; a month before the records is a dashed stub, this one lighter. */
@Composable
private fun MonthBars(months: List<Long?>, now: Long, color: Color, modifier: Modifier) {
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
