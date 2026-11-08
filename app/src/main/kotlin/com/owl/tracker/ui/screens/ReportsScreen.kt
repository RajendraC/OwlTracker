package com.owl.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.FilterKind
import com.owl.tracker.ui.PlLine
import com.owl.tracker.ui.ProfitLoss
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.Report
import com.owl.tracker.ui.ReportBuilder
import com.owl.tracker.ui.ReportRange
import com.owl.tracker.ui.components.Divider
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.MerchantAvatar
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalStatusColors
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Reports: pick a range, then tap anything (a bar, a slice, a merchant, a weekday) to narrow every
 * other section to it. The payments that match are listed at the bottom.
 */
@Composable
fun ReportsScreen(vm: AppViewModel) {
    val report by vm.report.collectAsStateWithLifecycle()
    val range by vm.reportRange.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val catMap = remember(cats) { cats.associateBy { it.id } }

    LazyColumn(
        Modifier.fillMaxSize().testTag("reports"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Reports", subtitle = report?.title, onBack = { vm.back() }) }
        item { RangeChips(range) { vm.setReportRange(it) } }
        val r = report
        if (r == null) {
            item { Text("Loading…", modifier = Modifier.padding(16.dp)) }
            return@LazyColumn
        }
        if (r.chips.isNotEmpty()) item { ActiveFilters(r, vm) }
        item { SummaryCard(r) }
        r.profitLoss?.let { pl -> item { ProfitLossCard(pl) } }
        item { TrendCard(r, vm) }
        if (r.categories.isNotEmpty()) item { CategoryCard(r, vm) }
        if (r.merchants.isNotEmpty()) item { MerchantCard(r, catMap, vm) }
        if (r.weekdays.any { it.paise > 0 }) item { WeekdayCard(r, vm) }
        if (r.highlights.isNotEmpty()) item { HighlightsCard(r) }
        item { PaymentsCard(r, catMap, vm) }
    }
}

@Composable
private fun RangeChips(selected: ReportRange, onPick: (ReportRange) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportRange.values().forEach { r ->
            FilterChip(
                selected = r == selected, onClick = { onPick(r) }, label = { Text(r.label) },
                modifier = Modifier.testTag("range_${r.months}"),
            )
        }
    }
}

@Composable
private fun ActiveFilters(r: Report, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("report_filters"),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        r.chips.forEach { (kind, label) ->
            FilterChip(
                selected = true, onClick = { vm.clearReportFilter(kind) }, label = { Text(label, maxLines = 1) },
                trailingIcon = { Icon(Ic.close, contentDescription = "Remove $label", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("filter_${kind.name.lowercase()}"),
            )
        }
        if (r.chips.size > 1) TextButton(onClick = { vm.clearReportFilter() }) { Text("Clear all") }
    }
}

@Composable
private fun SummaryCard(r: Report) {
    val sc = LocalStatusColors.current
    Panel(modifier = Modifier.testTag("report_summary")) {
        Text(if (r.filter.isEmpty) "Spent" else "Spent on what you picked", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Money.formatInr(r.spent), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("report_spent"))
            r.changePct?.let { pct ->
                val up = pct > 0
                val (fg, bg) = if (up) sc.overFg to sc.overBg else sc.okFg to sc.okBg
                Text(
                    (if (up) "▲ " else if (pct < 0) "▼ " else "") + "${kotlin.math.abs(pct)}%",
                    style = MaterialTheme.typography.labelLarge, color = fg,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp).testTag("report_change"),
                )
            }
        }
        r.previousSpent?.takeIf { r.changePct != null }?.let {
            Text("vs ${Money.formatInr(it)} ${r.range.previousText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Gap(12.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Money in", Money.formatInr(r.received), Modifier.weight(1f))
            val net = r.received - r.spent
            Stat("Net", (if (net >= 0) "+" else "−") + Money.formatInr(kotlin.math.abs(net)), Modifier.weight(1f), if (net >= 0) MaterialTheme.colorScheme.primary else sc.overFg)
        }
        Gap(8.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Per day", Money.formatInr(r.perDay), Modifier.weight(1f))
            Stat("Payments", r.payments.toString(), Modifier.weight(1f))
        }
    }
}

/**
 * Profit & loss: money in (income, refunds, money back) against spending, and what's left. Money in
 * never lowers spending; savings, investments and transfers between your own accounts are neither.
 */
@Composable
private fun ProfitLossCard(pl: ProfitLoss) {
    val sc = LocalStatusColors.current
    val gain = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Panel(modifier = Modifier.testTag("profit_loss")) {
        Text("Profit & loss", style = MaterialTheme.typography.titleMedium)
        Text(pl.title, style = MaterialTheme.typography.bodySmall, color = muted)
        Gap(12.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Money in", "+" + Money.formatInr(pl.moneyIn), Modifier.weight(1f).testTag("pl_in"), gain)
            Stat("Spent", "−" + Money.formatInr(pl.spent), Modifier.weight(1f).testTag("pl_spent"))
            Stat(
                if (pl.net >= 0) "Saved" else "Overspent", Money.formatInr(kotlin.math.abs(pl.net)),
                Modifier.weight(1f).testTag("pl_net"), if (pl.net >= 0) gain else sc.overFg,
            )
        }
        Gap(8.dp)
        Text(
            when {
                pl.moneyIn == 0L && pl.spent == 0L -> "Nothing came in or went out yet."
                pl.keptPct == null -> "Nothing came in, so everything spent came from what you already had."
                pl.net >= 0 -> "You kept ${pl.keptPct}% of what came in."
                else -> "You spent ${Money.formatInr(-pl.net)} more than came in."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (pl.net < 0) sc.overFg else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("pl_verdict"),
        )
        if (pl.moneyIn > 0 || pl.spent > 0) {
            // How much of what came in was spent, as one bar
            val total = maxOf(pl.moneyIn, pl.spent).coerceAtLeast(1)
            Gap(8.dp)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(pl.moneyIn.toFloat() / total).height(10.dp).background(gain.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth(pl.spent.toFloat() / total).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (pl.net < 0) sc.overFg else gain))
            }
        }

        Gap(14.dp)
        PlSection("Money in", "+" + Money.formatInr(pl.moneyIn), gain, "pl_income")
        if (pl.income.isEmpty()) Text("Nothing came in.", style = MaterialTheme.typography.bodySmall, color = muted)
        pl.income.forEach { PlRow(it, true) }
        Gap(10.dp)
        PlSection("Spending", "−" + Money.formatInr(pl.spent), MaterialTheme.colorScheme.onSurface, "pl_spending")
        if (pl.spending.isEmpty()) Text("Nothing spent.", style = MaterialTheme.typography.bodySmall, color = muted)
        pl.spending.forEach { PlRow(it, false) }
        Divider()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (pl.net >= 0) "Saved" else "Overspent", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                (if (pl.net >= 0) "+" else "−") + Money.formatInr(kotlin.math.abs(pl.net)),
                style = MaterialTheme.typography.titleMedium, color = if (pl.net >= 0) gain else sc.overFg,
            )
        }

        if (pl.months.size > 1) {
            Gap(6.dp)
            Text("Each month", style = MaterialTheme.typography.titleSmall)
            Gap(4.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Month", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(0.8f))
                listOf("In", "Spent", "Net").forEach {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
            pl.months.forEach { m ->
                Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}.testTag("pl_month_${m.label}")) {
                    Text(m.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.8f))
                    Text(Money.formatShort(m.moneyIn), style = MaterialTheme.typography.bodyMedium, color = gain, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(Money.formatShort(m.spent), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(
                        (if (m.net >= 0) "+" else "−") + Money.formatShort(kotlin.math.abs(m.net)),
                        style = MaterialTheme.typography.bodyMedium, color = if (m.net >= 0) gain else sc.overFg,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        val apart = buildList {
            if (pl.savingsOut > 0 || pl.savingsIn > 0) add(
                "Savings & investments: " + listOfNotNull(
                    pl.savingsOut.takeIf { it > 0 }?.let { "${Money.formatInr(it)} put away" },
                    pl.savingsIn.takeIf { it > 0 }?.let { "${Money.formatInr(it)} back" },
                ).joinToString(" · "),
            )
            if (pl.transfersOut > 0 || pl.transfersIn > 0) add(
                "Transfers and card bills: " + listOfNotNull(
                    pl.transfersOut.takeIf { it > 0 }?.let { "${Money.formatInr(it)} out" },
                    pl.transfersIn.takeIf { it > 0 }?.let { "${Money.formatInr(it)} in" },
                ).joinToString(" · "),
            )
            if (pl.leftOut > 0) add("${pl.leftOut} ${if (pl.leftOut == 1) "payment" else "payments"} you left out")
        }
        if (apart.isNotEmpty()) {
            Gap(10.dp)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp).testTag("pl_apart"),
            ) {
                Text("Not in profit & loss", style = MaterialTheme.typography.labelLarge)
                apart.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            }
        }
    }
}

@Composable
private fun PlSection(title: String, total: String, color: Color, tag: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(total, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun PlRow(line: PlLine, moneyIn: Boolean) {
    val c = line.category
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryTile(c, 28.dp)
        Column(Modifier.weight(1f)) {
            Text(c?.name ?: "Others", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val what = if (moneyIn && c?.kind != com.owl.tracker.data.CategoryKind.INCOME) "refunds and money back" else null
            Text(
                listOfNotNull(what, "${line.count} ${if (line.count == 1) "payment" else "payments"}").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (moneyIn) "+" else "") + Money.formatInr(line.paise),
            style = MaterialTheme.typography.bodyMedium,
            color = if (moneyIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun TrendCard(r: Report, vm: AppViewModel) {
    val sc = LocalStatusColors.current
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurface
    val selected = r.filter.bucket
    Panel(modifier = Modifier.testTag("report_trend")) {
        Text(if (r.daily) "Day by day" else "Month by month", style = MaterialTheme.typography.titleMedium)
        val sel = selected?.let { r.trend.getOrNull(it) }
        Text(
            if (sel != null) "${sel.title}: ${Money.formatInr(sel.spent)} spent" + (if (sel.received > 0) " · ${Money.formatInr(sel.received)} received" else "")
            else "Tap a bar to see that ${if (r.daily) "day" else "month"}",
            style = MaterialTheme.typography.bodySmall, color = if (sel != null) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("trend_caption"),
        )
        Gap(12.dp)
        val maxV = (r.trend.maxOfOrNull { it.spent } ?: 0L).coerceAtLeast((r.budgetLine * 11) / 10).coerceAtLeast(1L)
        val desc = r.trend.filter { it.hasData }.joinToString { "${it.title}: ${Money.formatInr(it.spent)}" }
        Canvas(
            Modifier.fillMaxWidth().height(150.dp)
                .pointerInput(r.trend.size) {
                    detectTapGestures { pos ->
                        val n = r.trend.size
                        if (n > 0) vm.tapReportBucket((pos.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1))
                    }
                }
                .semantics { contentDescription = "Spending chart. $desc" }
                .testTag("trend_chart"),
        ) {
            val n = r.trend.size.coerceAtLeast(1)
            val slot = size.width / n
            val w = slot * if (r.daily) 0.62f else 0.7f
            r.trend.forEachIndexed { i, b ->
                if (!b.hasData) return@forEachIndexed
                val h = (b.spent.toFloat() / maxV * size.height).coerceAtLeast(if (b.spent > 0) 2.dp.toPx() else 0f)
                val over = r.budgetLine > 0 && b.spent > r.budgetLine
                val base = if (over) sc.overRing else primary
                val color = when {
                    selected == null -> if (b.isNow) base else base.copy(alpha = 0.6f)
                    selected == i -> base
                    else -> base.copy(alpha = 0.22f)
                }
                drawRoundRect(color, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 4, w / 4))
            }
            if (r.budgetLine > 0) {
                val y = size.height - r.budgetLine.toFloat() / maxV * size.height
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Gap(4.dp)
        // Labels double as large, accessible tap targets for each bar.
        Row(Modifier.fillMaxWidth()) {
            r.trend.forEachIndexed { i, b ->
                val show = !r.daily || i == 0 || (i + 1) % 7 == 0 || i == r.trend.lastIndex
                Box(
                    Modifier.weight(1f).heightIn(min = 28.dp)
                        .clickable { vm.tapReportBucket(i) }
                        .semantics { contentDescription = "${b.title}: ${Money.formatInr(b.spent)}" }
                        .testTag("bar_$i"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (show) Text(
                        b.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                        color = if (selected == i) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (r.budgetLine > 0) {
            Text(
                "Dashed line: your budget for ${if (r.daily) "a day (${Money.formatInr(r.budgetLine)})" else "a month (${Money.formatInr(r.budgetLine)})"}. Red bars went over.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryCard(r: Report, vm: AppViewModel) {
    val sc = LocalStatusColors.current
    val selected = r.filter.categoryId
    val track = MaterialTheme.colorScheme.surfaceVariant
    Panel(modifier = Modifier.testTag("report_categories")) {
        Text("Where it went", style = MaterialTheme.typography.titleMedium)
        Text("Tap a slice or a row to focus on that category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(12.dp)
        val pick = selected?.let { id -> r.categories.firstOrNull { it.category.id == id } }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(190.dp)
                    .pointerInput(r.categories) {
                        detectTapGestures { pos ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            if (hypot(pos.x - cx, pos.y - cy) > size.width / 2f) return@detectTapGestures
                            val angle = ((Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())) + 90 + 360) % 360).toFloat()
                            var acc = 0f
                            for (s in r.categories) {
                                acc += s.share * 360f
                                if (angle <= acc) { vm.tapReportCategory(s.category.id); break }
                            }
                        }
                    }
                    .semantics { contentDescription = "Spending by category: " + r.categories.joinToString { "${it.category.name} ${(it.share * 100).toInt()}%" } }
                    .testTag("category_donut"),
            ) {
                val stroke = 26.dp.toPx()
                val inset = stroke / 2 + 4.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                var start = -90f
                r.categories.forEach { s ->
                    val sweep = s.share * 360f
                    val gap = if (r.categories.size > 1) 1.2f else 0f
                    val dim = selected != null && selected != s.category.id
                    val width = if (selected == s.category.id) stroke * 1.25f else stroke
                    drawArc(
                        Color(s.category.color).copy(alpha = if (dim) 0.25f else 1f), start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                        Offset(inset, inset), arcSize, style = Stroke(width),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                Text(pick?.category?.name ?: "Total", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Money.formatInr(pick?.paise ?: r.categories.sumOf { it.paise }), style = MaterialTheme.typography.titleMedium)
                pick?.let { Text("${(it.share * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Gap(8.dp)
        r.categories.forEachIndexed { i, s ->
            if (i > 0) Divider()
            val isPicked = s.category.id == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (isPicked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { vm.tapReportCategory(s.category.id) }
                    .padding(vertical = 10.dp, horizontal = 6.dp)
                    .testTag("cat_${s.category.id}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(s.category.color)))
                Column(Modifier.weight(1f)) {
                    Text(s.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val change = changeText(s.paise, s.previous)
                    Text(
                        "${(s.share * 100).toInt()}%" + (change?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            s.previous > 0 && s.paise > s.previous * 11 / 10 -> sc.overFg
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(Money.formatInr(s.paise), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private fun changeText(now: Long, before: Long): String? = when {
    before <= 0L -> if (now > 0) "new" else null
    else -> {
        val pct = ((now - before) * 100 / before).toInt()
        when {
            pct > 0 -> "▲ $pct%"
            pct < 0 -> "▼ ${-pct}%"
            else -> "same"
        }
    }
}

@Composable
private fun MerchantCard(r: Report, catMap: Map<String, Category>, vm: AppViewModel) {
    val selected = r.filter.merchantKey
    val top = r.merchants.maxOfOrNull { it.paise }?.coerceAtLeast(1L) ?: 1L
    Panel(padding = 12.dp, modifier = Modifier.testTag("report_merchants")) {
        Text("Top merchants", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp))
        r.merchants.forEachIndexed { i, m ->
            if (i > 0) Divider()
            val isPicked = m.key == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (isPicked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { vm.tapReportMerchant(m.key) }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .testTag("merchant_${m.key}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MerchantAvatar(vm.appIcons, m.pkg, m.name, catMap[m.categoryId], 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${m.count} ${if (m.count == 1) "payment" else "payments"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(4.dp)
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth((m.paise.toFloat() / top).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                }
                Text(Money.formatInr(m.paise), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun WeekdayCard(r: Report, vm: AppViewModel) {
    val selected = r.filter.weekday
    val maxV = r.weekdays.maxOf { it.paise }.coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    Panel(modifier = Modifier.testTag("report_weekdays")) {
        Text("By day of the week", style = MaterialTheme.typography.titleMedium)
        Gap(12.dp)
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            r.weekdays.forEach { w ->
                val isPicked = w.day == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { vm.tapReportWeekday(w.day) }
                        .semantics { contentDescription = "${ReportBuilder.plural(w.day)}: ${Money.formatInr(w.paise)}" }
                        .testTag("weekday_${w.day.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
                ) {
                    if (isPicked) Text(Money.formatShort(w.paise), style = MaterialTheme.typography.labelSmall, color = primary, maxLines = 1)
                    val frac = (w.paise.toFloat() / maxV).coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.75f * frac + 0.01f).clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    selected == null -> primary.copy(alpha = 0.35f + 0.65f * frac)
                                    isPicked -> primary
                                    else -> primary.copy(alpha = 0.18f)
                                },
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        w.day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3), style = MaterialTheme.typography.labelSmall,
                        color = if (isPicked) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightsCard(r: Report) {
    Panel(modifier = Modifier.testTag("report_highlights")) {
        Text("Highlights", style = MaterialTheme.typography.titleMedium)
        Gap(6.dp)
        r.highlights.forEach { line ->
            Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Ic.chart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PaymentsCard(r: Report, catMap: Map<String, Category>, vm: AppViewModel) {
    Panel(padding = 12.dp, modifier = Modifier.testTag("report_payments")) {
        Text(
            if (r.filter.isEmpty) "Biggest payments" else "${r.matchCount} matching ${if (r.matchCount == 1) "payment" else "payments"}",
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp).testTag("payments_title"),
        )
        if (r.matches.isEmpty()) {
            Text("Nothing here yet.", modifier = Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        r.matches.forEachIndexed { i, t ->
            if (i > 0) Divider()
            TxnRow(vm, t, catMap[t.categoryId], showDay = true)
        }
        if (r.matchCount > r.matches.size) {
            Text(
                "Showing the latest ${r.matches.size}. Pick a shorter range or another filter to see the rest.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp),
            )
        }
    }
}
