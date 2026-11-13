package com.owl.tracker.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owl.tracker.core.CalendarRow
import com.owl.tracker.core.DayLevel
import com.owl.tracker.core.DayTotal
import com.owl.tracker.core.Money
import com.owl.tracker.ui.DayShare
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalBrand
import com.owl.tracker.ui.theme.clay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The colours of a day's circle. Each step is lighter or darker as well as a different hue, so they can be told apart. */
private val LIGHT = Color(0xFF4CB86B)
private val WITHIN = Color(0xFFFFB21A)
private val HEAVY = Color(0xFFFF7A2E)
private val VERY_HEAVY = Color(0xFFD63B41)
/** EMI or rent paid that day: the EMI category's brown, lifted to show on both themes. */
val FIXED_DOT = Color(0xFF9C6B52)
private val MANGO = Color(0xFFFFB21A)
/** Wide enough for "₹19.2k" beside the seven days. */
private val WEEK_COL = 62.dp

fun levelFill(level: DayLevel): Color? = when (level) {
    DayLevel.LIGHT -> LIGHT
    DayLevel.WITHIN -> WITHIN
    DayLevel.HEAVY -> HEAVY
    DayLevel.VERY_HEAVY -> VERY_HEAVY
    else -> null
}

private fun levelInk(level: DayLevel): Color = when (level) {
    DayLevel.LIGHT -> Color(0xFF0E2614)
    DayLevel.WITHIN -> Color(0xFF2A1708)
    DayLevel.HEAVY -> Color(0xFF2A0F00)
    else -> Color.White
}

fun levelName(level: DayLevel, budgeted: Boolean): String = when (level) {
    DayLevel.LIGHT -> "Light"
    DayLevel.WITHIN -> if (budgeted) "Within budget" else "About usual"
    DayLevel.HEAVY -> "Heavy"
    DayLevel.VERY_HEAVY -> "Very heavy"
    DayLevel.NONE -> "Nothing spent"
    DayLevel.FUTURE -> "Still to come"
}

private val DAY_LONG = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
fun dayTitle(d: LocalDate): String = d.format(DAY_LONG)

/**
 * A day as a glossy circle, coloured by how its everyday spending went. A dashed outline for days still to come,
 * a ring for today and a bolder one for the day picked, and a small brown dot when an EMI or rent went out.
 */
@Composable
fun DayCircle(date: LocalDate, level: DayLevel, fixed: Boolean, picked: Boolean, today: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val none = brand.clay.pressed
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fill = levelFill(level)
    val surface = brand.clay.surface
    val diameter = size
    Box(
        modifier.size(diameter + 8.dp).drawBehind {
                val r = diameter.toPx() / 2f
                val c = center
                when {
                    picked -> drawCircle(MANGO, r + 3.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
                    today -> drawCircle(MANGO.copy(alpha = 0.85f), r + 2.5.dp.toPx(), c, style = Stroke(1.75.dp.toPx()))
                }
                when {
                    level == DayLevel.FUTURE -> drawCircle(
                        muted.copy(alpha = 0.55f), r - 1.dp.toPx(), c,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    )
                    fill == null -> drawCircle(none, r, c)
                    else -> {
                        // Glossy: a highlight up and to the left, darker towards the edge
                        drawCircle(Color.Black.copy(alpha = 0.18f), r, c + Offset(0f, 2.dp.toPx()))
                        drawCircle(
                            Brush.radialGradient(
                                0f to lerp(fill, Color.White, 0.45f), 0.55f to fill, 1f to lerp(fill, Color.Black, 0.22f),
                                center = c + Offset(-r * 0.32f, -r * 0.44f), radius = r * 1.6f,
                            ),
                            r, c,
                        )
                    }
                }
                if (fixed) {
                    val dc = c + Offset(r * 0.74f, -r * 0.74f)
                    drawCircle(surface, 6.dp.toPx(), dc)
                    drawCircle(FIXED_DOT, 4.dp.toPx(), dc)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The circle is a fixed size, so its number grows only a little with big text
        CapFontScale(1.2f) {
            FitText(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = if (diameter < 36.dp) 13.sp else 14.sp),
                color = when {
                    fill != null -> levelInk(level)
                    level == DayLevel.FUTURE -> muted.copy(alpha = 0.8f)
                    else -> muted
                },
                minScale = 0.7f,
            )
        }
    }
}

private fun dayDescription(d: DayTotal, budgeted: Boolean): String =
    dayTitle(d.date) + when (d.level) {
        DayLevel.FUTURE -> ", still to come"
        DayLevel.NONE -> ", nothing spent"
        else -> ", ${Money.formatInr(d.paise)}, ${levelName(d.level, budgeted).lowercase()}" +
            (if (d.fixedPaise > 0) ", with ${Money.formatInr(d.fixedPaise)} of EMI or rent" else "")
    }

/** The week's seven days as circles, with the day's name above and what was spent below. Tap a day to see only it. */
@Composable
fun WeekDaysRow(days: List<DayTotal>, picked: LocalDate?, today: LocalDate, budgeted: Boolean, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        days.forEach { d ->
            val on = d.date == picked
            val future = d.level == DayLevel.FUTURE
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .then(if (on) Modifier.clay(RoundedCornerShape(18.dp), brand.clay, brand.clay.pressed, elevation = 0.dp, pressed = true) else Modifier)
                    .then(if (future) Modifier else Modifier.clickable(onClickLabel = if (on) "Show the whole week" else "Show only this day") { onPick(d.date) })
                    .semantics {
                        contentDescription = dayDescription(d, budgeted)
                        selected = on
                        if (!future) role = Role.Button
                    }
                    .testTag("week_day_${d.date}")
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Seven columns: names and amounts grow only a little with big text, then get smaller to fit
                CapFontScale(1.25f) {
                    FitText(
                        d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (on || d.date == today) MaterialTheme.colorScheme.primary else muted, minScale = 0.7f,
                    )
                }
                DayCircle(d.date, d.level, d.fixedPaise > 0, on, d.date == today, 36.dp)
                CapFontScale(1.25f) {
                    FitText(
                        when (d.level) { DayLevel.FUTURE -> " "; else -> Money.formatShort(d.paise) },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (on) MaterialTheme.colorScheme.onSurface else muted, minScale = 0.6f,
                    )
                }
            }
        }
    }
}

/** What the colours mean, and what they're measured against. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LevelLegend(share: DayShare, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Two lines of swatches so they never squeeze
        val items = listOf(LIGHT to levelName(DayLevel.LIGHT, share.fromBudget), WITHIN to levelName(DayLevel.WITHIN, share.fromBudget),
            HEAVY to "Heavy", VERY_HEAVY to "Very heavy", FIXED_DOT to "EMI or rent")
        // As many to a line as fit (three on most phones), the rest on the next
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (c, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(if (c == FIXED_DOT) 9.dp else 12.dp).clip(CircleShape).background(c))
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = muted)
                }
            }
        }
        Text(
            when {
                share.paise <= 0 -> "Set a monthly budget and each day is coloured against its share of it"
                share.fromBudget -> "Each day's spending, EMIs and rent left out, against its ${Money.formatInr(share.paise)} share of the budget"
                else -> "Each day's spending, EMIs and rent left out, against a usual ${Money.formatInr(share.paise)} day"
            },
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
    }
}

/**
 * The budget month as a calendar: a row a week, each day a coloured circle, each week's total at the end of its row.
 * Tap a day to see only its payments; tap a week's total to open that week.
 */
@Composable
fun MonthCalendarCard(
    title: String, rows: List<CalendarRow>, share: DayShare, picked: LocalDate?, today: LocalDate,
    onPick: (LocalDate) -> Unit, onWeek: (LocalDate) -> Unit, modifier: Modifier = Modifier,
) {
    val brand = LocalBrand.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val thisWeek = com.owl.tracker.core.Weeks.startOf(today)
    Panel(modifier = modifier.testTag("month_calendar"), padding = 12.dp) {
        Text("$title, week by week", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
        Text(
            "Tap a day for its payments · tap a week's total to open the week", style = MaterialTheme.typography.bodySmall, color = muted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Gap(10.dp)
        // A grid of fixed circles: with big text its numbers and totals grow only a little, then get smaller to fit
        CapFontScale(1.2f) { Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = muted)
            }
            FitText("Week", Modifier.width(WEEK_COL).padding(end = 6.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = muted)
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                row.days.forEachIndexed { i, d ->
                    val date = row.weekStart.plusDays(i.toLong())
                    val future = d == null || d.level == DayLevel.FUTURE
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                            .then(
                                if (d == null) Modifier
                                else (if (future) Modifier else Modifier.clickable(onClickLabel = if (date == picked) "Show the whole month" else "Show only this day") { onPick(date) })
                                    .semantics {
                                        contentDescription = dayDescription(d, share.fromBudget)
                                        selected = date == picked
                                        if (!future) role = Role.Button
                                    }
                                    .testTag("cal_day_$date"),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d != null) DayCircle(date, d.level, d.fixedPaise > 0, date == picked, date == today, 32.dp)
                    }
                }
                val open = !row.weekStart.isAfter(thisWeek)
                Box(
                    Modifier.width(WEEK_COL).heightIn(min = 46.dp).padding(start = 2.dp)
                        .then(
                            if (open) Modifier.clay(RoundedCornerShape(14.dp), brand.clay, brand.clay.pressed, elevation = 0.dp, pressed = true)
                                .clickable(onClickLabel = "Open this week") { onWeek(row.weekStart) }
                                .semantics { contentDescription = "Week of ${dayTitle(row.weekStart)}, ${Money.formatInr(row.paise)} this month"; role = Role.Button }
                            else Modifier,
                        )
                        .testTag("cal_week_${row.weekStart}")
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (open) {
                        Column(horizontalAlignment = Alignment.End) {
                            FitText(Money.formatShort(row.paise), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp), textAlign = TextAlign.End, minScale = 0.6f)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FitText("Week", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary, minScale = 0.7f)
                                Icon(Ic.chevR, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            }
                        }
                    } else {
                        FitText("to come", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = muted.copy(alpha = 0.7f), textAlign = TextAlign.End, minScale = 0.6f)
                    }
                }
            }
        }
        } }
        Gap(8.dp)
        LevelLegend(share, Modifier.padding(horizontal = 4.dp))
    }
}

/** Shown while one day is picked: the day, what went out, and the way back to every day. */
@Composable
fun DayFilterBar(day: DayTotal, payments: Int, budgeted: Boolean, today: LocalDate, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(24.dp), modifier.fillMaxWidth().testTag("day_filter"), elevation = 8.dp) {
        Row(Modifier.padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            DayCircle(day.date, day.level, day.fixedPaise > 0, picked = false, today = day.date == today, size = 36.dp)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                FitText(
                    "${dayTitle(day.date)} · ${Money.formatInr(day.paise)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 2, minScale = 0.8f,
                )
                Text(
                    listOfNotNull(
                        "$payments ${if (payments == 1) "payment" else "payments"}",
                        levelName(day.level, budgeted).takeIf { day.level != DayLevel.NONE },
                        day.fixedPaise.takeIf { it > 0 }?.let { "EMI or rent ${Money.formatInr(it)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2,
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.testTag("day_filter_clear")) {
                Icon(Ic.close, contentDescription = "Show every day again", modifier = Modifier.size(20.dp))
            }
        }
    }
}
