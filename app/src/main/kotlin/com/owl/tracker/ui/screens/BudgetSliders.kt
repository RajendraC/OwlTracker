package com.owl.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Money
import com.owl.tracker.core.TrendKind
import com.owl.tracker.core.Trends
import com.owl.tracker.data.Category
import com.owl.tracker.data.Schema
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.MonthHistory
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.components.FitText
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.InfoNote
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.components.SideOrStacked
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalStatusColors

/*
 * Budget sliders (Round 11, design 2a): one row per category with its glossy tile, its name, the trend in words, an
 * amount you can type and a slider whose track shows the last six months as dots (older ones fainter, the latest
 * ringed and named). The month budget is the total of the rows; ₹0 means no budget for that category. Used by the
 * first-time setup and by Budgets → Adjust budgets.
 */

/** Paise per category, kept across rotation as "id=paise;id=paise". */
private val PaiseMapSaver = Saver<Map<String, Long>, String>(
    save = { m -> m.entries.joinToString(";") { "${it.key}=${it.value}" } },
    restore = { s -> s.split(';').filter { '=' in it }.associate { it.substringBefore('=') to (it.substringAfter('=').toLongOrNull() ?: 0L) } },
)

/** 1234567 → 12,34,567 as you type, with the cursor in the right place. */
private object IndianGrouping : VisualTransformation {
    fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val pairs = head.reversed().chunked(2).joinToString(",").reversed()
        return "$pairs,${digits.takeLast(3)}"
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = group(raw)
        val o2t = IntArray(raw.length + 1)
        val t2o = IntArray(out.length + 1)
        var r = 0
        for (t in out.indices) {
            t2o[t] = r
            if (out[t] != ',') { o2t[r] = t; r++ }
        }
        o2t[raw.length] = out.length
        t2o[out.length] = raw.length
        return TransformedText(AnnotatedString(out), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int) = t2o[offset.coerceIn(0, out.length)]
        })
    }
}

/** One row: a category, its months, its usual month and (on Budgets) this month so far. */
class SliderCategory(val category: Category, val months: List<Long?>, val usual: Long, val now: Long?)

/** "↑ Rising · Aug ₹16.9k, Sep ₹17.5k, Oct ₹21.4k" and which way it went. */
fun trendWords(months: List<Long?>, labels: List<String>): Pair<String, TrendKind> {
    val kind = Trends.kind(months)
    val known = months.mapIndexedNotNull { i, v -> v?.let { labels.getOrElse(i) { "" } to it } }
    val last3 = known.takeLast(3).joinToString(", ") { (l, v) -> "$l ${Money.formatShort(v)}" }
    val text = when (kind) {
        TrendKind.RISING -> "↑ Rising · $last3"
        TrendKind.FALLING -> "↓ Falling · $last3"
        TrendKind.STEADY -> "→ Steady · about ${Money.formatShort(known.takeLast(3).map { it.second }.average().toLong())}"
        TrendKind.UP_AND_DOWN -> "↕ Up and down · ${Money.formatShort(known.minOf { it.second })} to ${Money.formatShort(known.maxOf { it.second })}"
        TrendKind.TOO_EARLY -> known.lastOrNull()?.let { (l, v) -> "Only $l so far · ${Money.formatShort(v)}" } ?: "Nothing yet"
        TrendKind.NONE -> if (known.isEmpty()) "No months to go by yet" else "Nothing in the last ${known.size} months"
    }
    return text to kind
}

@Composable
private fun trendColor(kind: TrendKind): Color {
    val sc = LocalStatusColors.current
    return when (kind) {
        TrendKind.RISING -> sc.overFg
        TrendKind.FALLING -> sc.okFg
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * The slider: a track filled in the category's colour up to the budget, the months as dots on it (older ones fainter,
 * the latest ringed and named above it), and a clay thumb. Moves in steps of ₹100.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthsSlider(
    id: String, name: String, paise: Long, maxPaise: Long, months: List<Long?>, latestLabel: String, color: Color, onChange: (Long) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val trackBg = LocalStatusColors.current.track
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, color = ink)
    val maxRupees = (maxPaise / 100).coerceAtLeast(100)
    Slider(
        value = (paise / 100).coerceIn(0, maxRupees).toFloat(),
        onValueChange = { v -> onChange(Math.round(v / 100f).toLong() * 100 * 100) },
        valueRange = 0f..maxRupees.toFloat(),
        modifier = Modifier.fillMaxWidth().padding(top = if (months.any { (it ?: 0L) > 0 }) 18.dp else 0.dp).testTag("budget_slider_$id")
            .semantics { contentDescription = "$name budget"; stateDescription = Money.formatInr(paise) },
        thumb = {
            Box(Modifier.size(24.dp).shadow(3.dp, CircleShape).background(surface, CircleShape).border(3.dp, ink, CircleShape))
        },
        track = { st ->
            val range = st.valueRange.endInclusive - st.valueRange.start
            val fraction = if (range <= 0f) 0f else ((st.value - st.valueRange.start) / range).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val h = 8.dp.toPx()
                val y = size.height / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(trackBg, Offset(0f, y - h / 2), Size(size.width, h), r)
                drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * fraction, h), r)
                // Nothing spent in any month: no dots to show
                if (months.none { (it ?: 0L) > 0 }) return@Canvas
                val last = months.indexOfLast { it != null }
                months.forEachIndexed { i, m ->
                    if (m == null) return@forEachIndexed
                    val x = size.width * (m.toFloat() / maxPaise).coerceIn(0f, 1f)
                    if (i == last) {
                        drawCircle(surface, 6.dp.toPx(), Offset(x, y))
                        drawCircle(ink, 6.dp.toPx(), Offset(x, y), style = Stroke(2.5.dp.toPx()))
                        val t = measurer.measure(latestLabel, labelStyle)
                        // Above the thumb, which often sits on the latest month
                        drawText(t, topLeft = Offset((x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), y - 13.dp.toPx() - t.size.height))
                    } else {
                        drawCircle(ink.copy(alpha = 0.18f + i * 0.12f), 4.5.dp.toPx(), Offset(x, y))
                        drawCircle(surface, 4.5.dp.toPx(), Offset(x, y), style = Stroke(1.2.dp.toPx()))
                    }
                }
            }
        },
    )
}

/** The amount as a box you can type in: the number keypad, any amount up to ₹99,99,999; empty is ₹0. */
@Composable
private fun AmountBox(id: String, name: String, paise: Long, onTyped: (Long) -> Unit) {
    var text by remember(paise) { mutableStateOf(if (paise > 0) (paise / 100).toString() else "") }
    val focus = remember { MutableInteractionSource() }
    val focused by focus.collectIsFocusedAsState()
    val style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
    BasicTextField(
        value = text,
        onValueChange = { v ->
            val d = v.filter { it.isDigit() }.trimStart('0').take(7)
            text = d
            onTyped((d.toLongOrNull() ?: 0L) * 100)
        },
        singleLine = true,
        textStyle = style,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        visualTransformation = IndianGrouping,
        interactionSource = focus,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.testTag("budget_amount_$id").semantics { contentDescription = "$name budget in rupees" },
        decorationBox = { inner ->
            Row(
                Modifier.widthIn(min = 100.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(if (focused) 2.5.dp else 1.5.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("₹", style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Spacer(Modifier.size(4.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (text.isEmpty()) Text("0", style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                    inner()
                }
            }
        },
    )
}

@Composable
private fun SliderRow(r: SliderCategory, labels: List<String>, paise: Long, maxPaise: Long, onValue: (Long, Boolean) -> Unit) {
    val c = r.category
    val sc = LocalStatusColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (trend, kind) = trendWords(r.months, labels)
    val known = Trends.known(r.months).size
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("slider_row_${c.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile(c, 40.dp)
            // The amount beside the name when there's room, else under it (big text)
            SideOrStacked(
                Modifier.weight(1f), stackedGap = 6.dp,
                start = {
                    Column {
                        Text(c.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                        Text(trend, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = trendColor(kind))
                    }
                },
                end = { AmountBox(c.id, c.name, paise) { onValue(it, true) } },
            )
        }
        Column(Modifier.padding(start = 52.dp)) {
            MonthsSlider(c.id, c.name, paise, maxPaise, r.months, labels.getOrElse(r.months.indexOfLast { it != null }) { "" }, Color(c.color)) { onValue(it, false) }
            // On Budgets: this month so far against the amount being set
            r.now?.let { now ->
                val over = now - paise
                val line = when {
                    paise <= 0 -> "This month: ${Money.formatInr(now)} spent"
                    over > 0 -> "This month: ${Money.formatInr(now)} spent · ${Money.formatInr(over)} over"
                    else -> "This month: ${Money.formatInr(now)} spent · ${Money.formatInr(-over)} left"
                }
                Text(line, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = if (paise > 0 && over > 0) sc.overFg else muted)
            }
            if (known > 0) {
                val ok = Trends.enoughIn(r.months, paise)
                val (text, col) = when {
                    paise <= 0 -> "No budget for this category" to muted
                    ok * 10 >= known * 8 -> "Enough in $ok of the last $known months" to sc.okFg
                    ok * 2 >= known -> "Enough in $ok of the last $known months" to sc.warnFg
                    else -> "Enough in $ok of the last $known months" to sc.overFg
                }
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = col, modifier = Modifier.testTag("enough_${c.id}"))
            }
        }
    }
}

/** The total that moves with the sliders, the share of each category, and money in against it. */
@Composable
private fun SliderSummary(rows: List<SliderCategory>, values: Map<String, Long>, moneyIn: Long, now: Long?, daysLeft: Int?, before: Long?) {
    val sc = LocalStatusColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val total = values.values.filter { it > 0 }.sum()
    Panel(Modifier.testTag("slider_summary")) {
        SideOrStacked(
            Modifier.fillMaxWidth(),
            start = {
                Column {
                    Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = muted)
                    FitText(Money.formatInr(total), style = MaterialTheme.typography.headlineMedium, minScale = 0.7f, modifier = Modifier.testTag("slider_total"))
                }
            },
            end = {
                when {
                    now != null -> Column(horizontalAlignment = Alignment.End) {
                        Text("Spent so far", style = MaterialTheme.typography.labelLarge, color = muted)
                        Text(Money.formatInr(now), style = MaterialTheme.typography.titleMedium, color = if (now > total) sc.overFg else MaterialTheme.colorScheme.onSurface)
                    }
                    moneyIn > 0 -> Column(horizontalAlignment = Alignment.End) {
                        val left = moneyIn - total
                        Text(if (left >= 0) "Left to save" else "More than comes in", style = MaterialTheme.typography.labelLarge, color = muted)
                        Text(Money.formatInr(kotlin.math.abs(left)), style = MaterialTheme.typography.titleMedium, color = if (left >= 0) sc.okFg else sc.overFg)
                    }
                    else -> Unit
                }
            },
        )
        Gap(10.dp)
        // Each category's share of the budget, in its colour
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(sc.track)) {
            if (total > 0) rows.forEach { r ->
                val v = values[r.category.id] ?: 0L
                if (v > 0) Box(Modifier.weight(v.toFloat()).height(12.dp).background(Color(r.category.color)))
            }
        }
        Gap(8.dp)
        when {
            now != null && daysLeft != null && daysLeft > 0 -> {
                val left = total - now
                Text(
                    if (left >= 0) "${Money.formatInr(left / daysLeft)} a day is safe for the next $daysLeft days" else "${Money.formatInr(-left)} over this budget with $daysLeft days left",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = if (left >= 0) sc.okFg else sc.overFg,
                )
            }
            moneyIn > 0 -> Text("Money in, usual month: ${Money.formatInr(moneyIn)}", style = MaterialTheme.typography.bodySmall, color = muted)
            else -> Unit
        }
        if (before != null && before > 0 && before != total) {
            Gap(4.dp)
            Text("Your monthly budget now: ${Money.formatInr(before)}. Saving makes it the total of these.", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

/** The categories to show: expense categories, by their starting amount; those with nothing at all wait behind "More". */
private fun sliderRows(h: MonthHistory, cats: List<Category>, start: Map<String, Long>, withNow: Boolean): Pair<List<SliderCategory>, List<SliderCategory>> {
    val all = cats.filter { it.countsAsSpend && !it.archived && it.id != Cat.TRANSFERS }
        .map { SliderCategory(it, h.of(it.id), h.usual(it.id), if (withNow) h.now[it.id] ?: 0L else null) }
        .sortedByDescending { start[it.category.id] ?: 0L }
    return all.partition { (start[it.category.id] ?: 0L) > 0 || Trends.known(it.months).any { m -> m > 0 } }
}

/** The list both screens share: summary, rows, then "More categories". Returns nothing; [values] and [maxes] are the state. */
@Composable
private fun SliderList(
    h: MonthHistory, shown: List<SliderCategory>, more: List<SliderCategory>, values: Map<String, Long>, maxes: Map<String, Long>,
    onValue: (String, Long, Boolean) -> Unit, now: Long?, daysLeft: Int?, before: Long?, note: String, modifier: Modifier,
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.testTag("slider_list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "summary") { SliderSummary(shown + more, values, h.usualMoneyIn, now, daysLeft, before) }
        item(key = "note") {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        }
        items(shown, key = { it.category.id }) { r ->
            SliderRow(r, h.labels, values[r.category.id] ?: 0L, maxes[r.category.id] ?: Trends.sliderMax(r.usual)) { v, typed -> onValue(r.category.id, v, typed) }
        }
        if (more.isNotEmpty()) {
            item(key = "more") {
                TextButton(onClick = { showMore = !showMore }, modifier = Modifier.testTag("more_categories")) {
                    Text(if (showMore) "Fewer categories" else "More categories (${more.size})")
                }
            }
            if (showMore) items(more, key = { it.category.id }) { r ->
                SliderRow(r, h.labels, values[r.category.id] ?: 0L, maxes[r.category.id] ?: Trends.sliderMax(r.usual)) { v, typed -> onValue(r.category.id, v, typed) }
            }
        }
    }
}

private fun note(h: MonthHistory): String =
    "Dots: what you spent ${h.labels.getOrElse(h.firstKnown) { "" }} to ${h.labels.lastOrNull() ?: ""}, older ones fainter. " +
        "Drag, or type any amount. ₹0 means no budget."

/**
 * First-time setup with the sliders, once the SMS history has at least one month: every category starts at its usual
 * month. "Just one monthly total" goes back to the single amount.
 */
@Composable
fun BudgetSlidersSetup(vm: AppViewModel, h: MonthHistory, onOneTotal: () -> Unit) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    var values by rememberSaveable(stateSaver = PaiseMapSaver) { mutableStateOf(emptyMap<String, Long>()) }
    var maxes by rememberSaveable(stateSaver = PaiseMapSaver) { mutableStateOf(emptyMap<String, Long>()) }
    val start = remember(h, cats) { cats.associate { it.id to h.usual(it.id) } }
    LaunchedEffect(start) { if (values.isEmpty()) values = start.filterValues { it > 0 } }
    val (shown, more) = remember(h, cats, start) { sliderRows(h, cats, start, withNow = false) }
    val total = values.values.filter { it > 0 }.sum()
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 8.dp)) {
            Text("Set your budgets", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(
                "From your last ${h.knownCount} ${if (h.knownCount == 1) "month" else "months"} of SMS. Change any of them later in Budgets.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SliderList(
            h, shown, more, values, maxes,
            onValue = { id, v, typed ->
                values = values + (id to v)
                val usual = (shown + more).firstOrNull { it.category.id == id }?.usual ?: 0L
                if (typed && v > (maxes[id] ?: Trends.sliderMax(usual))) maxes = maxes + (id to Trends.sliderMax(usual, v))
            },
            now = null, daysLeft = null, before = null, note = note(h), modifier = Modifier.weight(1f),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Button(
                onClick = { vm.finishOnboardingWithBudgets((shown + more).associate { it.category.id to (values[it.category.id] ?: 0L) }) },
                enabled = total > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("save_budgets"),
            ) { FitText("Save budgets · ${Money.formatInr(total)} a month", minScale = 0.8f, maxLines = 2, textAlign = TextAlign.Center) }
            TextButton(onClick = onOneTotal, modifier = Modifier.fillMaxWidth().testTag("one_total")) { Text("Just one monthly total instead") }
        }
    }
}

/** Budgets → Adjust budgets: the same sliders, starting from the budgets set (or the usual month), with this month so far. */
@Composable
fun AdjustBudgetsScreen(vm: AppViewModel) {
    val hist by vm.monthHistory.collectAsStateWithLifecycle()
    val h = hist
    val days = h?.current?.let { it.days - it.dayNumber(vm.today()) }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            "Adjust budgets",
            h?.let { "${MonthHistory.label(it.current)} · $days ${if (days == 1) "day" else "days"} left" },
            onBack = { vm.back() },
        )
        if (h == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            AdjustBudgetsBody(vm, h, days)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AdjustBudgetsBody(vm: AppViewModel, h: MonthHistory, days: Int?) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    run {
        // (one block, so the state below lives and dies with this body)
        var values by rememberSaveable(stateSaver = PaiseMapSaver) { mutableStateOf(emptyMap<String, Long>()) }
        var maxes by rememberSaveable(stateSaver = PaiseMapSaver) { mutableStateOf(emptyMap<String, Long>()) }
        val start = remember(h, cats, budgets) { cats.associate { it.id to (budgets[it.id] ?: h.usual(it.id)) } }
        LaunchedEffect(start) {
            if (values.isEmpty()) {
                values = start.filterValues { it > 0 }
                maxes = cats.associate { it.id to Trends.sliderMax(maxOf(h.usual(it.id), h.now[it.id] ?: 0L), start[it.id] ?: 0L) }
            }
        }
        val (shown, more) = remember(h, cats, start) { sliderRows(h, cats, start, withNow = true) }
        val total = values.values.filter { it > 0 }.sum()
        SliderList(
            h, shown, more, values, maxes,
            onValue = { id, v, typed ->
                values = values + (id to v)
                val cap = maxes[id] ?: 0L
                if (typed && v > cap) maxes = maxes + (id to Trends.sliderMax((shown + more).firstOrNull { it.category.id == id }?.usual ?: 0L, v))
            },
            now = h.now.values.sum(), daysLeft = days, before = budgets[Schema.OVERALL], note = note(h), modifier = Modifier.weight(1f),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Button(
                onClick = { vm.saveBudgets((shown + more).associate { it.category.id to (values[it.category.id] ?: 0L) }) },
                enabled = total > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("save_budgets"),
            ) { FitText("Save · ${Money.formatInr(total)} a month", minScale = 0.8f, maxLines = 2, textAlign = TextAlign.Center) }
        }
    }
}

/** Shown when there's no month to go by yet: a note under the single amount. */
@Composable
fun SlidersComingNote() {
    InfoNote("Once there's a month of payments, Budgets → Adjust budgets sets a budget for each category with sliders.", Ic.info)
}
