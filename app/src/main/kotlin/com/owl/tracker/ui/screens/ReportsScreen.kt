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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import com.owl.tracker.ui.IeLine
import com.owl.tracker.ui.IncomeExpenses
import com.owl.tracker.ui.SavingPlan
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.Report
import com.owl.tracker.ui.ReportBuilder
import com.owl.tracker.ui.ReportRange
import com.owl.tracker.ui.components.Divider
import com.owl.tracker.ui.components.Reveal
import com.owl.tracker.ui.components.SettleEasing
import com.owl.tracker.ui.components.animatedPaise
import com.owl.tracker.ui.components.growIn
import com.owl.tracker.ui.components.stagger
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
    val plan by vm.savingPlan.collectAsStateWithLifecycle()
    // Cards rise into place once, the first time they appear
    val seen = remember { mutableSetOf<Any>() }
    val openedAt = remember { System.currentTimeMillis() }

    LazyColumn(
        Modifier.fillMaxSize().testTag("reports"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        var order = 0
        fun card(key: String, content: @Composable () -> Unit) {
            val o = order++
            item(key = key) { Reveal(key, seen, o, openedAt, content) }
        }
        item { ScreenHeader("Reports", subtitle = report?.title, onBack = { vm.back() }) }
        item { RangeChips(range) { vm.setReportRange(it) } }
        val r = report
        if (r == null) {
            item { Text("Loading…", modifier = Modifier.padding(16.dp)) }
            return@LazyColumn
        }
        if (r.chips.isNotEmpty()) item { ActiveFilters(r, vm) }
        card("summary") { SummaryCard(r) }
        r.incomeExpenses?.let { pl -> card("income_expenses") { IncomeExpensesCard(pl) } }
        plan?.takeIf { r.filter.isEmpty && !it.isEmpty }?.let { p -> card("ways") { WaysToSaveCard(p, catMap, vm) } }
        card("trend") { TrendCard(r, vm) }
        if (r.categories.isNotEmpty()) card("categories") { CategoryCard(r, vm) }
        if (r.merchants.isNotEmpty()) card("merchants") { MerchantCard(r, catMap, vm) }
        if (r.weekdays.any { it.paise > 0 }) card("weekdays") { WeekdayCard(r, vm) }
        if (r.highlights.isNotEmpty()) card("highlights") { HighlightsCard(r) }
        card("payments") { PaymentsCard(r, catMap, vm) }
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
            Text(Money.formatInr(animatedPaise(r.spent)), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("report_spent"))
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
            Stat("Income", Money.formatInr(animatedPaise(r.received)), Modifier.weight(1f))
            val net = r.received - r.spent
            Stat("Net", (if (net >= 0) "+" else "−") + Money.formatInr(kotlin.math.abs(animatedPaise(net))), Modifier.weight(1f), if (net >= 0) MaterialTheme.colorScheme.primary else sc.overFg)
        }
        Gap(8.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Per day", Money.formatInr(animatedPaise(r.perDay)), Modifier.weight(1f))
            Stat("Payments", r.payments.toString(), Modifier.weight(1f))
        }
    }
}

/**
 * Income & expenses: income (salary, other money in, refunds, money back) against expenses, and what's left. Money in
 * never lowers spending; savings, investments and transfers between your own accounts are neither.
 */
@Composable
private fun IncomeExpensesCard(pl: IncomeExpenses) {
    val sc = LocalStatusColors.current
    val gain = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Panel(modifier = Modifier.testTag("income_expenses")) {
        Text("Income & expenses", style = MaterialTheme.typography.titleMedium)
        Text(pl.title, style = MaterialTheme.typography.bodySmall, color = muted)
        Gap(12.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Income", "+" + Money.formatInr(animatedPaise(pl.moneyIn)), Modifier.weight(1f).testTag("pl_in"), gain)
            Stat("Expenses", "−" + Money.formatInr(animatedPaise(pl.spent)), Modifier.weight(1f).testTag("pl_spent"))
            Stat(
                if (pl.net >= 0) "Saved" else "Overspent", Money.formatInr(kotlin.math.abs(animatedPaise(pl.net))),
                Modifier.weight(1f).testTag("pl_net"), if (pl.net >= 0) gain else sc.overFg,
            )
        }
        Gap(8.dp)
        Text(
            when {
                pl.moneyIn == 0L && pl.spent == 0L -> "No income or expenses yet."
                pl.keptPct == null -> "No income yet, so everything spent came from what you already had."
                pl.net >= 0 -> "You saved ${pl.keptPct}% of your income."
                else -> "You spent ${Money.formatInr(-pl.net)} more than your income."
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
                // Income fills first, then expenses grow over it
                val fill = growIn(pl.title, 1100)
                val inFrac by animateFloatAsState(pl.moneyIn.toFloat() / total, tween(600, easing = SettleEasing), label = "in")
                val outFrac by animateFloatAsState(pl.spent.toFloat() / total, tween(600, easing = SettleEasing), label = "out")
                Box(Modifier.fillMaxWidth((inFrac * stagger(fill, 0, 2)).coerceIn(0f, 1f)).height(10.dp).background(gain.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth((outFrac * stagger(fill, 1, 2)).coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (pl.net < 0) sc.overFg else gain))
            }
        }

        Gap(14.dp)
        IeSection("Income", "+" + Money.formatInr(pl.moneyIn), gain, "pl_income")
        if (pl.income.isEmpty()) Text("No income.", style = MaterialTheme.typography.bodySmall, color = muted)
        pl.income.forEach { IeRow(it, true) }
        Gap(10.dp)
        IeSection("Expenses", "−" + Money.formatInr(pl.spent), MaterialTheme.colorScheme.onSurface, "pl_spending")
        if (pl.spending.isEmpty()) Text("No expenses.", style = MaterialTheme.typography.bodySmall, color = muted)
        pl.spending.forEach { IeRow(it, false) }
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
                listOf("Income", "Expenses", "Net").forEach {
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
                Text("Not counted as income or expenses", style = MaterialTheme.typography.labelLarge)
                apart.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            }
        }
    }
}

/** Every way to save, with the total a month and a year, then the share of income saved. */
@Composable
private fun WaysToSaveCard(plan: SavingPlan, catMap: Map<String, Category>, vm: AppViewModel) {
    val gain = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Panel(modifier = Modifier.testTag("ways_to_save")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.owl.tracker.ui.components.IconTile(Ic.bulb, gain, MaterialTheme.colorScheme.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text("Ways to save", style = MaterialTheme.typography.titleMedium)
                Text(
                    "From your last ${plan.basedOnMonths} ${if (plan.basedOnMonths == 1) "month" else "months"} and this one",
                    style = MaterialTheme.typography.bodySmall, color = muted,
                )
            }
        }
        if (plan.tips.isNotEmpty()) {
            Gap(12.dp)
            Text("You could save about ${Money.formatInr(plan.monthlyPaise)} a month", style = MaterialTheme.typography.titleMedium, color = gain, modifier = Modifier.testTag("ways_total"))
            Text("That's ${Money.formatInr(plan.monthlyPaise * 12)} a year.", style = MaterialTheme.typography.bodySmall, color = muted)
            Gap(6.dp)
            plan.tips.forEachIndexed { i, tip ->
                if (i > 0) Divider()
                val cat = tip.categoryId?.let { catMap[it] }
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (cat != null) Modifier.clickable { vm.tapReportCategory(cat.id) } else Modifier)
                        .padding(vertical = 10.dp)
                        .testTag("tip_${tip.id}"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (cat != null) CategoryTile(cat, 28.dp) else com.owl.tracker.ui.components.IconTile(Ic.calendar, muted, MaterialTheme.colorScheme.surfaceVariant, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(tip.title, style = MaterialTheme.typography.titleSmall)
                        Text(tip.detail, style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    Text(
                        Money.formatShort(tip.monthlyPaise) + "/mo",
                        style = MaterialTheme.typography.labelLarge, color = gain,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        plan.keep?.let { k ->
            Gap(10.dp)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp).testTag("tip_keep"),
            ) {
                Text(k.title, style = MaterialTheme.typography.titleSmall)
                Text(k.detail, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

@Composable
private fun IeSection(title: String, total: String, color: Color, tag: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(total, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun IeRow(line: IeLine, moneyIn: Boolean) {
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val drill = r.drill
    // The chart shows the range's bars, or the days of the month drilled into
    val bars = drill?.bars ?: r.trend
    val days = drill != null || r.daily
    val selected = if (drill != null) r.filter.day else r.filter.bucket
    val budget = drill?.budgetLine ?: r.budgetLine
    val haptics = LocalHapticFeedback.current
    val view = drill?.title ?: r.range.name
    // A finger sliding along the bars: shows each one's amount without changing the report
    var scrub by remember(view) { mutableStateOf<Int?>(null) }
    val focus = scrub ?: selected?.takeIf { bars.getOrNull(it)?.hasData == true }
    fun tap(i: Int) = if (drill != null) vm.tapReportDay(i) else vm.tapReportBucket(i)

    Panel(modifier = Modifier.testTag("report_trend")) {
        AnimatedContent(
            targetState = drill?.title,
            transitionSpec = {
                val down = targetState != null
                (fadeIn(tween(300)) + slideInHorizontally(tween(380, easing = SettleEasing)) { if (down) it / 3 else -it / 3 }) togetherWith fadeOut(tween(160))
            },
            label = "trend_title",
        ) { title ->
            if (title == null) {
                Text(if (r.daily) "Day by day" else "Month by month", style = MaterialTheme.typography.titleMedium)
            } else {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.clearReportFilter(FilterKind.BUCKET) }.padding(end = 8.dp).testTag("drill_up"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.back, contentDescription = "Back to ${r.range.label.lowercase()}", tint = primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text("Day by day · back to ${r.range.label.lowercase()}", style = MaterialTheme.typography.labelSmall, color = primary)
                    }
                }
            }
        }
        val f = focus?.let { bars.getOrNull(it) }
        Text(
            when {
                f != null -> "${f.title}: ${Money.formatInr(f.spent)} spent" + (if (f.received > 0) " · ${Money.formatInr(f.received)} income" else "")
                !days -> "Tap a month to see its days · slide along the bars"
                else -> "Tap a day to see its payments · slide along the bars"
            },
            style = MaterialTheme.typography.bodySmall, color = if (f != null) primary else muted,
            modifier = Modifier.testTag("trend_caption"),
        )
        Gap(10.dp)

        val withData = bars.filter { it.hasData }
        val average = if (withData.isEmpty()) 0L else withData.sumOf { it.spent } / withData.size
        val maxTarget = (bars.maxOfOrNull { it.spent } ?: 0L).coerceAtLeast((budget * 11) / 10).coerceAtLeast(1L)
        val desc = withData.joinToString { "${it.title}: ${Money.formatInr(it.spent)}" }
        // Bars rise one after another when the range changes or a month opens, and spring to new heights when something is tapped
        val grow = growIn(view)
        val zoom = growIn(drill?.title, 650)
        val maxV by animateFloatAsState(maxTarget.toFloat(), spring(dampingRatio = 0.8f, stiffness = 220f), label = "max")
        val heights = key(view, bars.size) {
            bars.map { b -> animateFloatAsState(b.spent.toFloat(), spring(dampingRatio = 0.62f, stiffness = 260f), label = "bar").value }
        }
        val avgY by animateFloatAsState(average.toFloat(), spring(dampingRatio = 0.8f, stiffness = 200f), label = "avg")
        val measurer = rememberTextMeasurer()
        val avgStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
        val zoomFrom = if (drill != null) r.filter.bucket ?: 0 else 0
        val zoomSlots = r.trend.size.coerceAtLeast(1)

        BoxWithConstraints(Modifier.fillMaxWidth().height(180.dp)) {
            val widthPx = constraints.maxWidth.toFloat()
            fun indexAt(x: Float) = (x / (widthPx / bars.size.coerceAtLeast(1))).toInt().coerceIn(0, (bars.size - 1).coerceAtLeast(0))
            Canvas(
                Modifier.fillMaxSize().padding(top = 34.dp)
                    .pointerInput(view) { detectTapGestures { pos -> if (bars.isNotEmpty()) tap(indexAt(pos.x)) } }
                    .pointerInput(view) {
                        detectHorizontalDragGestures(
                            onDragStart = { pos -> scrub = indexAt(pos.x) },
                            onHorizontalDrag = { change, _ ->
                                val i = indexAt(change.position.x)
                                if (i != scrub) { scrub = i; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            },
                            onDragEnd = { scrub = null },
                            onDragCancel = { scrub = null },
                        )
                    }
                    .semantics { contentDescription = "Spending chart. $desc" }
                    .testTag("trend_chart"),
            ) {
                val n = bars.size.coerceAtLeast(1)
                val slot = size.width / n
                val w = slot * if (days) 0.62f else 0.7f
                // Opening a month: its bar widens into the whole chart like a lens, then fades as the days rise
                if (drill != null && zoom < 1f) {
                    val fromSlot = size.width / zoomSlots
                    val left = zoomFrom * fromSlot * (1f - zoom)
                    val right = left + fromSlot + (size.width - fromSlot) * zoom
                    drawRoundRect(primary.copy(alpha = 0.22f * (1f - zoom)), Offset(left, 0f), Size(right - left, size.height), CornerRadius(18f, 18f))
                }
                bars.forEachIndexed { i, b ->
                    if (!b.hasData) return@forEachIndexed
                    val g = stagger(grow, i, n)
                    if (g <= 0f) return@forEachIndexed
                    val v = heights.getOrElse(i) { b.spent.toFloat() }.coerceAtLeast(0f)
                    val lifted = if (focus == i) 1.04f else 1f
                    val h = (v / maxV * size.height * g * lifted).coerceIn(if (b.spent > 0) 2.dp.toPx() * g else 0f, size.height)
                    val over = budget > 0 && b.spent > budget
                    val base = if (over) sc.overRing else primary
                    val color = when {
                        focus == null -> if (b.isNow) base else base.copy(alpha = 0.6f)
                        focus == i -> base
                        else -> base.copy(alpha = 0.22f)
                    }
                    val bw = if (focus == i) w * 1.08f else w
                    drawRoundRect(color, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 4, bw / 4))
                }
                if (budget > 0) {
                    val y = size.height - budget.toFloat() / maxV * size.height
                    drawLine(line.copy(alpha = 0.7f * grow), Offset(0f, y), Offset(size.width * grow, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
                // The average draws itself across, left to right
                if (average > 0 && withData.size > 1) {
                    val y = size.height - avgY / maxV * size.height
                    val reach = size.width * grow
                    drawLine(muted.copy(alpha = 0.8f), Offset(0f, y), Offset(reach, y), strokeWidth = 1.2.dp.toPx())
                    if (grow > 0.95f) {
                        val label = measurer.measure("avg ${Money.formatShort(average)}", avgStyle)
                        drawText(label, topLeft = Offset(size.width - label.size.width, (y - label.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
                    }
                }
            }
            // The value bubble: pops up over the bar under the finger (or the one picked) and glides between bars
            val shown = focus != null && f != null
            val pop by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "pop")
            val centre by animateFloatAsState(
                ((focus ?: 0) + 0.5f) * (widthPx / bars.size.coerceAtLeast(1)), spring(dampingRatio = 0.78f, stiffness = 500f), label = "bubble_x",
            )
            // Keeps the last amount while the bubble shrinks away
            val last = remember { arrayOf("") }
            if (f != null) last[0] = Money.formatInr(f.spent)
            if (pop > 0.01f) {
                Box(
                    Modifier.fillMaxWidth().height(30.dp)
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0))
                            layout(constraints.maxWidth, p.height) {
                                p.place((centre - p.width / 2f).toInt().coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0)), 0)
                            }
                        }
                        .graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f); transformOrigin = TransformOrigin(0.5f, 1f) }
                        .clip(RoundedCornerShape(15.dp))
                        .background(primary)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("trend_bubble"),
                ) {
                    Text(last[0], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                }
            }
        }
        Gap(4.dp)
        // Labels double as large, accessible tap targets for each bar.
        Row(Modifier.fillMaxWidth()) {
            bars.forEachIndexed { i, b ->
                val show = !days || i == 0 || (i + 1) % 7 == 0 || i == bars.lastIndex
                Box(
                    Modifier.weight(1f).heightIn(min = 28.dp)
                        .clickable { tap(i) }
                        .semantics { contentDescription = "${b.title}: ${Money.formatInr(b.spent)}" }
                        .testTag(if (drill != null) "day_$i" else "bar_$i"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (show) Text(
                        b.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                        color = if (selected == i) primary else muted,
                    )
                }
            }
        }
        if (budget > 0) {
            Text(
                "Dashed line: your budget for ${if (days) "a day (${Money.formatInr(budget)})" else "a month (${Money.formatInr(budget)})"}. Red bars went over.",
                style = MaterialTheme.typography.bodySmall, color = muted,
            )
        }
    }
}

@Composable
private fun CategoryCard(r: Report, vm: AppViewModel) {
    val sc = LocalStatusColors.current
    val selected = r.filter.categoryId
    val track = MaterialTheme.colorScheme.surfaceVariant
    val pick = selected?.let { id -> r.categories.firstOrNull { it.category.id == id } }
    // The whole ring sweeps in when the range changes or you come back up from a category
    val ring = growIn(r.range to (pick == null), 1000)
    // Drilling into a category: its slice opens out into the whole ring, which then splits into its merchants
    val open = growIn(pick?.category?.id, 1000)
    val pickColor = pick?.let { Color(it.category.color) } ?: MaterialTheme.colorScheme.primary
    val parts = remember(pick, r.merchants) {
        if (pick == null) emptyList() else {
            val total = pick.paise.coerceAtLeast(1)
            val top = r.merchants.filter { it.categoryId == pick.category.id }.take(6)
            val rest = (pick.paise - top.sumOf { it.paise }).coerceAtLeast(0)
            top.map { Triple(it.key, it.name, it.paise) }.let { if (rest > 0) it + Triple("", "Others", rest) else it }
                .map { (k, name, p) -> Triple(k, name, p) to p.toFloat() / total }
        }
    }
    fun shade(i: Int, n: Int) = lerp(pickColor, Color.White, 0.55f * i / n.coerceAtLeast(1))
    val pickStart = remember(pick, r.categories) {
        var acc = 0f
        for (s in r.categories) { if (s.category.id == pick?.category?.id) break; acc += s.share * 360f }
        -90f + acc
    }

    Panel(modifier = Modifier.testTag("report_categories")) {
        AnimatedContent(targetState = pick?.category?.name, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) }, label = "cat_title") { name ->
            if (name == null) {
                Column {
                    Text("Where it went", style = MaterialTheme.typography.titleMedium)
                    Text("Tap a slice or a row to open that category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.tapReportCategory(selected!!) }.padding(end = 8.dp).testTag("ring_up"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ic.back, contentDescription = "Back to all categories", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text("By merchant · back to all categories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Gap(12.dp)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(200.dp)
                    .pointerInput(r.categories, pick) {
                        detectTapGestures { pos ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            if (hypot(pos.x - cx, pos.y - cy) > size.width / 2f) return@detectTapGestures
                            val angle = ((Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())) + 90 + 360) % 360).toFloat()
                            var acc = 0f
                            if (pick == null) {
                                for (s in r.categories) {
                                    acc += s.share * 360f
                                    if (angle <= acc) { vm.tapReportCategory(s.category.id); break }
                                }
                            } else {
                                for ((part, share) in parts) {
                                    acc += share * 360f
                                    if (angle <= acc) { if (part.first.isNotEmpty()) vm.tapReportMerchant(part.first); break }
                                }
                            }
                        }
                    }
                    .semantics {
                        contentDescription = if (pick == null) "Spending by category: " + r.categories.joinToString { "${it.category.name} ${(it.share * 100).toInt()}%" }
                        else "${pick.category.name} by merchant: " + parts.joinToString { "${it.first.second} ${(it.second * 100).toInt()}%" }
                    }
                    .testTag("category_donut"),
            ) {
                val stroke = 26.dp.toPx()
                val inset = stroke / 2 + 6.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val tl = Offset(inset, inset)
                drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
                if (pick == null) {
                    // The ring sweeps round clockwise, slice after slice, like a fitness ring closing
                    val shown = 360f * ring
                    var start = -90f
                    r.categories.forEach { s ->
                        val sweep = s.share * 360f
                        val visible = (shown - (start + 90f)).coerceIn(0f, sweep)
                        val gap = if (r.categories.size > 1) 1.2f else 0f
                        if (visible > gap) drawArc(Color(s.category.color), start + gap / 2, (visible - gap).coerceAtLeast(0.5f), false, tl, arcSize, style = Stroke(stroke))
                        start += sweep
                    }
                } else {
                    // 1: the picked slice swings round and opens out into the whole ring while the rest fade
                    val e = SettleEasing.transform((open / 0.45f).coerceIn(0f, 1f))
                    var start = -90f
                    r.categories.forEach { s ->
                        val sweep = s.share * 360f
                        if (s.category.id != pick.category.id && e < 1f) {
                            drawArc(Color(s.category.color).copy(alpha = 0.9f * (1f - e)), start + 0.6f, (sweep - 1.2f).coerceAtLeast(0.5f), false, tl, arcSize, style = Stroke(stroke))
                        }
                        start += sweep
                    }
                    val from = pickStart
                    val pickSweep = pick.share * 360f
                    drawArc(pickColor.copy(alpha = if (open < 0.45f) 1f else 0.35f), from + (-90f - from) * e, pickSweep + (360f - pickSweep) * e, false, tl, arcSize, style = Stroke(stroke * (1f + 0.25f * e)))
                    // 2: then its merchants sweep in, each a lighter shade of the category's colour
                    if (open > 0.45f) {
                        val shown = 360f * SettleEasing.transform(((open - 0.45f) / 0.55f).coerceIn(0f, 1f))
                        var at = -90f
                        parts.forEachIndexed { i, (_, share) ->
                            val sweep = share * 360f
                            val visible = (shown - (at + 90f)).coerceIn(0f, sweep)
                            if (visible > 1.2f) drawArc(shade(i, parts.size), at + 0.6f, visible - 1.2f, false, tl, arcSize, style = Stroke(stroke * 1.25f))
                            at += sweep
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
                Text(pick?.category?.name ?: "Total", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Money.formatInr(animatedPaise(pick?.paise ?: r.categories.sumOf { it.paise })), style = MaterialTheme.typography.titleMedium)
                pick?.let { Text("${(it.share * 100).toInt()}% of spending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Gap(8.dp)
        if (pick == null) {
            r.categories.forEachIndexed { i, s ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
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
                    Icon(Ic.chevR, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            // The category's merchants, matching the ring's shades; tap one to narrow to it
            parts.forEachIndexed { i, (part, share) ->
                if (i > 0) Divider()
                val (key, name, paise) = part
                val picked = key.isNotEmpty() && key == r.filter.merchantKey
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (picked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .then(if (key.isNotEmpty()) Modifier.clickable { vm.tapReportMerchant(key) } else Modifier)
                        .padding(vertical = 10.dp, horizontal = 6.dp)
                        .testTag("ring_merchant_${key.ifEmpty { "others" }}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(shade(i, parts.size)))
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.formatInr(paise), style = MaterialTheme.typography.titleSmall)
                }
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
    val grow = growIn(r.range, 900)
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
                        val frac by animateFloatAsState((m.paise.toFloat() / top).coerceIn(0.02f, 1f), tween(500, easing = SettleEasing), label = "merchant")
                        Box(Modifier.fillMaxWidth((frac * stagger(grow, i, r.merchants.size)).coerceIn(0.001f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
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
    val grow = growIn(r.range)
    Panel(modifier = Modifier.testTag("report_weekdays")) {
        Text("By day of the week", style = MaterialTheme.typography.titleMedium)
        Gap(12.dp)
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            r.weekdays.forEachIndexed { idx, w ->
                val isPicked = w.day == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { vm.tapReportWeekday(w.day) }
                        .semantics { contentDescription = "${ReportBuilder.plural(w.day)}: ${Money.formatInr(w.paise)}" }
                        .testTag("weekday_${w.day.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
                ) {
                    if (isPicked) Text(Money.formatShort(w.paise), style = MaterialTheme.typography.labelSmall, color = primary, maxLines = 1)
                    val frac = (w.paise.toFloat() / maxV).coerceIn(0f, 1f)
                    val shown by animateFloatAsState(frac, tween(500, easing = SettleEasing), label = "weekday")
                    Box(
                        Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.75f * shown * stagger(grow, idx, 7) + 0.01f).clip(RoundedCornerShape(6.dp))
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
