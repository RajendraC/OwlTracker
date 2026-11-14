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
import androidx.compose.foundation.layout.size
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
import com.owl.tracker.core.Move
import com.owl.tracker.core.MoveKind
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
                            l, style = MaterialTheme.typography.labelSmall,
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

// ---------------- Home: What's changing ----------------

/** "↑ Rising · 27% more than in Aug", "↓ Falling · 24% less than in Aug", "New · ₹10.5k in Oct". */
fun movePhrase(m: Move, months: List<Long?>, labels: List<String>): String {
    val from = months[m.from] ?: 0L
    val pct = if (from > 0) kotlin.math.abs(m.change) * 100 / from else 0
    return when (m.kind) {
        MoveKind.NEW -> "New · ${Money.formatShort(m.change)} in ${labels.getOrElse(m.to) { "" }}"
        MoveKind.RISING -> "↑ Rising · $pct% more than in ${labels.getOrElse(m.from) { "" }}"
        MoveKind.FALLING -> "↓ Falling · $pct% less than in ${labels.getOrElse(m.from) { "" }}"
    }
}

/**
 * Home, under the budget: the categories that moved most over the last months (something new, a rise, a fall), each
 * with its months as small bars. Tap one for its months with amounts, its budget as a line, and its budget's slider.
 */
@Composable
fun WhatsChangingCard(h: MonthHistory, cats: List<Category>, budgets: Map<String, Long>, vm: AppViewModel, modifier: Modifier = Modifier) {
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val moves = remember(h, catMap) {
        Trends.biggestMoves(h.byCategory.filterKeys { id -> catMap[id]?.let { it.countsAsSpend && !it.archived } == true })
    }
    if (moves.isEmpty()) return
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val sc = LocalStatusColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labels = h.labels + MonthHistory.label(h.current)
    Panel(modifier.testTag("whats_changing"), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text("What's changing", style = MaterialTheme.typography.titleMedium)
                Text("Biggest moves over the last ${h.knownCount} months · tap one", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            TextButton(onClick = { vm.openCategoryTrends() }, modifier = Modifier.testTag("changes_all")) { Text("All ›") }
        }
        moves.forEachIndexed { i, (id, mv) ->
            val c = catMap[id] ?: return@forEachIndexed
            val months = h.of(id)
            val now = h.now[id] ?: 0L
            val budget = budgets[id]
            val isOpen = open == id
            if (i > 0) Divider()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = if (isOpen) null else id }
                    .padding(vertical = 8.dp, horizontal = 4.dp).testTag("change_$id"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryTile(c, 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                    Text(
                        movePhrase(mv, months, h.labels),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = when (mv.kind) { MoveKind.RISING -> sc.overFg; MoveKind.FALLING -> sc.okFg; MoveKind.NEW -> muted },
                    )
                }
                MonthBars(months, now, Color(c.color), Modifier.size(width = 76.dp, height = 32.dp))
            }
            if (isOpen) {
                Column(Modifier.fillMaxWidth().padding(start = 56.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BarsWithAmounts(months, now, labels, Color(c.color), budget, Modifier.fillMaxWidth().testTag("change_bars_$id"))
                    val known = Trends.known(months).size
                    val note = when {
                        mv.kind == MoveKind.NEW && budget == null -> "Nothing on it before ${labels.getOrElse(mv.to) { "" }}. No budget on it yet."
                        mv.kind == MoveKind.NEW -> "Nothing on it before ${labels.getOrElse(mv.to) { "" }}. Its budget: ${Money.formatInr(budget!!)}."
                        budget == null -> "Usual month now ${Money.formatInr(h.usual(id))}. No budget on it yet."
                        else -> {
                            val over = known - Trends.enoughIn(months, budget)
                            "Over its ${Money.formatInr(budget)} budget in $over of the last $known ${if (known == 1) "month" else "months"}. Usual month now ${Money.formatInr(h.usual(id))}."
                        }
                    }
                    Text(note, style = MaterialTheme.typography.bodySmall, color = muted)
                    androidx.compose.material3.Button(
                        onClick = { vm.openSheet(com.owl.tracker.ui.Sheet.BudgetEdit(id)) },
                        modifier = Modifier.testTag("change_budget_$id"),
                    ) { Text(if (budget != null) "Change the budget" else "Set a budget") }
                }
            }
        }
    }
}

/** A category's months with their amounts over the bars, month names under them, and its budget as a dashed line. */
@Composable
private fun BarsWithAmounts(months: List<Long?>, now: Long, labels: List<String>, color: Color, budget: Long?, modifier: Modifier) {
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
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
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
