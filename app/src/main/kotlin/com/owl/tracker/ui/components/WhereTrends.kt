package com.owl.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owl.tracker.core.Money
import com.owl.tracker.core.TrendKind
import com.owl.tracker.core.Trends
import com.owl.tracker.data.Category
import com.owl.tracker.ui.MonthHistory
import com.owl.tracker.ui.screens.trendPhrase
import com.owl.tracker.ui.theme.LocalBrand
import com.owl.tracker.ui.theme.LocalStatusColors
import com.owl.tracker.ui.theme.clay

/*
 * Home's "Where it went", with the months behind it (Round 13, A + D): a This month | 6 months switch. This month is the
 * ring and its rows, each row with the category's months as small bars and which way it's going (tap the bars for the
 * amounts and its budget as a line). 6 months is a bar a month split by category, with the month's budget as a line,
 * and each category's trend; tap one to light up its part of every bar.
 */

/** What the card needs for its trends: the months, the budgets, and which tab is showing. */
class RingTrends(
    val history: MonthHistory,
    val budgets: Map<String, Long>,
    val monthBudget: Long?,
    val sixMonths: Boolean,
    val onSixMonths: (Boolean) -> Unit,
)

/** "↑ Rising · 50% more than in Jul", or "New in Nov" for a category with only this month, and which way. */
fun MonthHistory.trendOf(id: String): Pair<String, TrendKind> {
    val (said, kind) = trendPhrase(of(id), labels)
    return if (kind == TrendKind.NONE && (now[id] ?: 0L) > 0) "New in ${MonthHistory.label(current)}" to kind else said to kind
}

@Composable
fun trendTint(kind: TrendKind): Color {
    val sc = LocalStatusColors.current
    return when (kind) {
        TrendKind.RISING -> sc.overFg
        TrendKind.FALLING -> sc.okFg
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** This month | 6 months: two segments pressed into the card, the picked one raised. */
@Composable
fun WhereSwitch(six: Boolean, onSix: (Boolean) -> Unit) {
    val brand = LocalBrand.current
    Row(
        Modifier.fillMaxWidth().clay(RoundedCornerShape(22.dp), brand.clay, brand.clay.pressed, elevation = 0.dp, pressed = true).padding(4.dp)
            .semantics { contentDescription = "Show this month or six months" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("This month" to false, "6 months" to true).forEach { (label, isSix) ->
            val on = six == isSix
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .then(if (on) Modifier.clay(RoundedCornerShape(18.dp), brand.clay, elevation = 3.dp) else Modifier.clip(RoundedCornerShape(18.dp)))
                    .clickable(onClickLabel = "Show $label") { onSix(isSix) }
                    .semantics { selected = on; role = androidx.compose.ui.semantics.Role.Tab }
                    .testTag(if (isSix) "where_six_months" else "where_this_month")
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FitText(
                    label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, minScale = 0.8f,
                )
            }
        }
    }
}

/** Under a row's name: its months as small bars and which way it's going. Tapping it opens [RowTrendOpen]. */
@Composable
fun RowTrendStrip(h: MonthHistory, id: String, name: String, color: Color, open: Boolean, onToggle: () -> Unit) {
    val (phrase, kind) = h.trendOf(id)
    Row(
        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = if (open) "Hide the months" else "Show the months") { onToggle() }
            .padding(vertical = 3.dp).padding(end = 6.dp)
            .semantics { contentDescription = "$name: $phrase"; stateDescription = if (open) "Months shown" else "Months hidden" }
            .testTag("where_bars_$id"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MonthBars(h.of(id), h.now[id] ?: 0L, color, Modifier.size(width = 64.dp, height = 22.dp))
        Text(phrase, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = trendTint(kind))
    }
}

/** The row's months with their amounts, its budget as a dashed line, and a word on what's what. */
@Composable
fun RowTrendOpen(h: MonthHistory, id: String, color: Color, budget: Long?) {
    val labels = h.labels + MonthHistory.label(h.current)
    Column(Modifier.fillMaxWidth().padding(start = 58.dp, end = 6.dp, bottom = 10.dp).testTag("where_bars_open_$id"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BarsWithAmounts(h.of(id), h.now[id] ?: 0L, labels, color, budget, Modifier.fillMaxWidth())
        Text(
            (if (budget != null) "Its ${Money.formatInr(budget)} budget is the dashed line. " else "No budget on it. ") +
                "${labels.last()} so far is the lighter bar.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 6 months: a bar a month split by category (this month so far lighter), the month's budget as a dashed line, then each
 * category's trend, usual month and small bars. Tapping a category lights up its part of every bar.
 */
@Composable
fun SixMonthsView(h: MonthHistory, order: List<Category>, monthBudget: Long?, picked: String?, onPick: (String?) -> Unit) {
    val sc = LocalStatusColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val labels = h.labels + MonthHistory.label(h.current)
    val cats = remember(h, order) { order.filter { c -> (h.now[c.id] ?: 0L) > 0 || Trends.known(h.of(c.id)).any { it > 0 } } }
    // Each month's parts, bottom up in the list's order; months before the records are empty
    val columns = remember(h, cats) {
        (0..h.months.size).map { i ->
            cats.map { c -> c to (if (i == h.months.size) h.now[c.id] ?: 0L else h.of(c.id).getOrNull(i) ?: 0L) }
        }
    }
    val totals = columns.map { col -> col.sumOf { it.second } }
    val top = (maxOf(totals.maxOrNull() ?: 0L, monthBudget ?: 0L).coerceAtLeast(1L) * 108 / 100)
    val barH = 150.dp
    val known = h.totals.mapNotNull { it }
    val over = monthBudget?.let { b -> known.count { it > b } } ?: 0

    Column(Modifier.fillMaxWidth().testTag("six_months_bars"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CapFontScale(1.3f) {
            Box(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    columns.forEachIndexed { i, col ->
                        val isNow = i == columns.lastIndex
                        val empty = i < h.firstKnown
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            FitText(if (empty) "–" else Money.formatShort(totals[i]), style = MaterialTheme.typography.labelSmall, color = muted, minScale = 0.6f)
                            Box(Modifier.height(barH), contentAlignment = Alignment.BottomCenter) {
                                Column(Modifier.fillMaxWidth(0.66f).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))) {
                                    // Biggest at the bottom
                                    col.asReversed().forEach { (c, v) ->
                                        if (v > 0) {
                                            val lit = picked == null || picked == c.id
                                            Box(
                                                Modifier.fillMaxWidth().height(barH * (v.toFloat() / top))
                                                    .background(Color(c.color).copy(alpha = (if (lit) 1f else 0.2f) * (if (isNow) 0.55f else 1f))),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // The month's budget, across the bars
                if (monthBudget != null && monthBudget > 0) {
                    Canvas(Modifier.matchParentSize()) {
                        val y = size.height - barH.toPx() * (monthBudget.toFloat() / top).coerceIn(0f, 1f)
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
        val pick = cats.firstOrNull { it.id == picked }
        Text(
            when {
                pick != null -> "${pick.name}: ${h.trendOf(pick.id).first}. Its part of each bar is lit."
                monthBudget == null -> "A bar a month, by category. ${labels.last()} so far is lighter."
                over == 0 -> "Within the ${Money.formatInr(monthBudget)} budget, the dashed line, in all ${known.size} months. Tap a category to light it up."
                else -> "Over the ${Money.formatInr(monthBudget)} budget, the dashed line, in $over of the last ${known.size} months. Tap a category to light it up."
            },
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = when {
                pick != null -> trendTint(h.trendOf(pick.id).second)
                over > 0 -> sc.overFg
                else -> muted
            },
            modifier = Modifier.padding(top = 4.dp).testTag("six_months_caption"),
        )
    }
    Gap(6.dp)
    cats.forEachIndexed { i, c ->
        if (i > 0) Divider()
        val (phrase, kind) = h.trendOf(c.id)
        val on = picked == c.id
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .then(if (on) Modifier.background(LocalBrand.current.clay.pressed) else Modifier)
                .clickable(onClickLabel = if (on) "Show every category" else "Light up ${c.name}") { onPick(if (on) null else c.id) }
                .semantics { selected = on }
                .padding(vertical = 8.dp, horizontal = 6.dp)
                .testTag("six_${c.id}"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryTile(c, 40.dp)
            SideOrStacked(
                Modifier.weight(1f),
                start = {
                    Column {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text(phrase, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = trendTint(kind))
                    }
                },
                end = {
                    Column(horizontalAlignment = Alignment.End) {
                        MonthBars(h.of(c.id), h.now[c.id] ?: 0L, Color(c.color), Modifier.size(width = 60.dp, height = 20.dp))
                        Text(
                            h.usual(c.id).takeIf { it > 0 }?.let { "usual ${Money.formatShort(it)}" } ?: "new",
                            style = MaterialTheme.typography.labelSmall, color = muted,
                        )
                    }
                },
            )
        }
    }
}
