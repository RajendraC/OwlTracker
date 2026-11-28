package com.owl.tracker.ui.screens

import com.owl.tracker.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.Money
import com.owl.tracker.core.RecurringBill
import com.owl.tracker.data.Goal
import com.owl.tracker.data.Goals
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.Screen
import com.owl.tracker.ui.Sheet
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.components.categoryArt
import com.owl.tracker.ui.theme.ClaySurface
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalStatusColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Mango = Color(0xFFFFB21A)
private val dueFmt = DateTimeFormatter.ofPattern("d MMM", com.owl.tracker.i18n.I18n.locale)

private fun dueWords(due: LocalDate, today: LocalDate): String = when (val n = ChronoUnit.DAYS.between(today, due).toInt()) {
    0 -> "today"
    1 -> "tomorrow"
    in 2..6 -> tr("in {0} days", n)
    else -> due.format(dueFmt)
}

private fun billAmount(b: RecurringBill) = (if (b.steady) "" else tr("about ")) + Money.formatInr(b.paise)

// ---------------- Bills coming up ----------------

@Composable
private fun BillRow(b: RecurringBill, due: LocalDate, today: LocalDate, icon: String?, onClick: (() -> Unit)?) {
    val sc = LocalStatusColors.current
    val soon = ChronoUnit.DAYS.between(today, due) <= 4
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp).testTag("bill_${b.key}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            categoryArt(icon)?.let { Image(painterResource(it), null, Modifier.size(30.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(b.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(b.cadence.replaceFirstChar { it.uppercase() } + tr(" · {0} so far", b.times), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(billAmount(b), style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.owl.tracker.ui.theme.LocalDisplayFont.current, fontWeight = FontWeight.Bold))
            Text(
                dueWords(due, today), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
                color = if (soon) sc.fg(com.owl.tracker.core.BudgetState.HEADS_UP) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (soon) sc.bg(com.owl.tracker.core.BudgetState.HEADS_UP) else Color.Transparent).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Home: the next bills (30 days), up to three, and their total. Opens Bills. */
@Composable
fun BillsCard(vm: AppViewModel, bills: List<RecurringBill>, modifier: Modifier = Modifier) {
    val today = vm.today()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val next = com.owl.tracker.core.Recurring.upcoming(bills, today, 30)
    if (next.isEmpty()) return
    ClaySurface(RoundedCornerShape(30.dp), modifier.fillMaxWidth().testTag("bills_card"), elevation = 12.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Coming up"), style = MaterialTheme.typography.titleLarge)
                    Text(tr("{0} {1} in the next 30 days · {2}", next.size, if (next.size == 1) tr("bill") else tr("bills"), Money.formatInr(next.sumOf { it.first.paise })),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { vm.go(Screen.BILLS) }, modifier = Modifier.testTag("bills_all")) { Text(tr("All")) }
            }
            next.take(3).forEach { (b, due) -> BillRow(b, due, today, cats.firstOrNull { it.id == b.categoryId }?.icon) { vm.go(Screen.BILLS) } }
        }
    }
}

/** Every repeating payment found, by next due date; a tap offers to stop tracking it. */
@Composable
fun BillsScreen(vm: AppViewModel) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val dash by vm.dashboard.collectAsStateWithLifecycle()
    val today = vm.today()
    var asking by remember { mutableStateOf<RecurringBill?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("bills_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(tr("Bills coming up"), tr("Found from payments that repeat"), onBack = { vm.back() })
        val all = com.owl.tracker.core.Recurring.upcoming(bills, today, 400)
        if (all.isEmpty()) {
            Text(tr("No repeating payments yet. Once an EMI, rent or subscription has been paid two or three times, it shows up here with its next date."), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val month = dash?.period
        val restOfMonth = month?.let { p -> all.filter { it.second.isBefore(p.endExclusive) }.sumOf { it.first.paise } } ?: 0
        if (restOfMonth > 0) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🦉", fontSize = 28.sp)
            Text(tr("Still to come this month: {0} in bills. Keep it in mind before spending what's left.", Money.formatInr(restOfMonth)), style = MaterialTheme.typography.bodyMedium)
        }
        ClaySurface(RoundedCornerShape(26.dp), Modifier.fillMaxWidth(), elevation = 10.dp) {
            Column(Modifier.padding(12.dp)) { all.forEach { (b, due) -> BillRow(b, due, today, cats.firstOrNull { it.id == b.categoryId }?.icon) { asking = b } } }
        }
        Text(tr("Tap a bill to stop tracking it. Amounts that change (like electricity) are shown as \"about\"."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    asking?.let { b ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(tr("Stop tracking {0}?", b.name)) },
            text = { Text(tr("It won't be shown as a bill any more. Its payments stay as they are.")) },
            confirmButton = { TextButton(onClick = { vm.stopTrackingBill(b.key, b.name); asking = null }, modifier = Modifier.testTag("bill_stop")) { Text(tr("Stop tracking")) } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(tr("Keep")) } },
        )
    }
}

// ---------------- Can I afford it? ----------------

@Composable
private fun MiniRing(used: Double, label: String) {
    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        val track = MaterialTheme.colorScheme.surfaceVariant
        val green = LocalStatusColors.current.ring
        Canvas(Modifier.fillMaxSize()) {
            val w = 13.dp.toPx(); val r = size.minDimension / 2 - w
            val tl = Offset(center.x - r, center.y - r); val s = Size(2 * r, 2 * r)
            drawArc(track, 0f, 360f, false, tl, s, style = Stroke(w))
            drawArc(if (used < 0.8) green else if (used <= 1) Mango else Color(0xFFD63B41), -90f, (used.coerceAtMost(1.0) * 360).toFloat().coerceAtMost(359.5f), false, tl, s, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (used > 1) String.format(Locale.ROOT, "%.1f×", used) else "${(used * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Type an amount: what's left after it, the new amount a day, and the owl's verdict. */
@Composable
fun AffordSheet(vm: AppViewModel) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val dash by vm.dashboard.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    val paise = (amount.toLongOrNull() ?: 0L) * 100
    Text(tr("Can I afford it?"), style = MaterialTheme.typography.titleLarge)
    Text(tr("See what a purchase does to the rest of your month."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() }.take(8) },
        label = { Text(tr("How much? (₹)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("afford_amount"), textStyle = MaterialTheme.typography.headlineSmall,
    )
    // Categories with a budget, so the answer can say what it does to that one too
    val budgeted = dash?.categoryRows.orEmpty().map { it.category }
    if (budgeted.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        budgeted.take(8).forEach { c ->
            val on = cat == c.id
            Text(
                c.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { cat = if (on) null else c.id }.padding(horizontal = 12.dp, vertical = 7.dp).testTag("afford_cat_${c.id}"),
            )
        }
    }
    val a = if (paise > 0) vm.afford(paise, cat) else null
    if (a == null) return
    if (a.budget <= 0) {
        Text(tr("Set a monthly budget to see what this does to it."), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MiniRing((a.spent + paise).toDouble() / a.budget, tr("after"))
        Column {
            Text(tr("Left for {0} {1}", a.daysLeft, if (a.daysLeft == 1) tr("day") else tr("days")), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.formatInr(a.left), style = MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (a.leftAfter >= 0) Money.formatInr(a.leftAfter) else tr("{0} over", Money.formatInr(-a.leftAfter)), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("afford_left"))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(tr("A day, now") to a.perDay, tr("A day, after") to a.perDayAfter).forEach { (l, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                Text(l, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.formatInr(v), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    val catLine = if (a.category != null && a.categoryLeftAfter != null) {
        if (a.categoryLeftAfter >= 0) tr(" {0} would still have {1} of its budget.", a.category, Money.formatInr(a.categoryLeftAfter))
        else tr(" {0} would go {1} over its own budget, though.", a.category, Money.formatInr(-a.categoryLeftAfter))
    } else ""
    val verdict = when {
        a.leftAfter < 0 -> tr("Not this month: it would take you {0} past your budget.", Money.formatInr(-a.leftAfter))
        a.perDayAfter * 2 < a.perDay -> tr("You can, but it's tight: it would leave {0} a day for the rest of the month.", Money.formatInr(a.perDayAfter))
        else -> tr("Yes, you can. You'd still have {0} for the rest of the month.", Money.formatInr(a.leftAfter))
    } + catLine
    val sc = LocalStatusColors.current
    val state = when { a.leftAfter < 0 -> com.owl.tracker.core.BudgetState.OVER; a.perDayAfter * 2 < a.perDay || (a.categoryLeftAfter ?: 0) < 0 -> com.owl.tracker.core.BudgetState.HEADS_UP; else -> com.owl.tracker.core.BudgetState.ON_TRACK }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(sc.bg(state)).padding(14.dp).testTag("afford_verdict"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🦉", fontSize = 28.sp)
        Text(verdict, color = sc.fg(state), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { vm.addFromAfford(paise, cat) }, modifier = Modifier.testTag("afford_add")) { Text(tr("Add it as an expense")) }
        TextButton(onClick = { vm.closeSheet() }) { Text(tr("Just checking")) }
    }
}

// ---------------- Savings goals ----------------

/** A jar filling up to [fraction], with the percentage on it. */
@Composable
private fun Jar(fraction: Float, size: androidx.compose.ui.unit.Dp) {
    val glass = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val fill = com.owl.tracker.ui.theme.LocalBrand.current.add[1]
    Box(Modifier.size(width = size * 0.68f, height = size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width; val h = this.size.height
            val lid = h * 0.1f
            drawRoundRect(Color(0xFFC98C46), Offset(w * 0.22f, 0f), Size(w * 0.56f, lid), androidx.compose.ui.geometry.CornerRadius(lid / 2))
            val body = androidx.compose.ui.geometry.RoundRect(0f, lid * 1.3f, w, h, androidx.compose.ui.geometry.CornerRadius(w * 0.22f))
            val path = androidx.compose.ui.graphics.Path().apply { addRoundRect(body) }
            drawPath(path, glass)
            clipPath(path) {
                val top = h - (h - lid * 1.3f) * fraction
                drawRect(fill, Offset(0f, top), Size(w, h - top))
            }
            drawPath(path, Color(0x33C98C46), style = Stroke(2.dp.toPx()))
        }
        Text("${(fraction * 100).toInt()}%", color = ink, style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.owl.tracker.ui.theme.LocalDisplayFont.current, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun GoalCard(vm: AppViewModel, g: Goal, today: LocalDate) {
    val sc = LocalStatusColors.current
    ClaySurface(RoundedCornerShape(28.dp), Modifier.fillMaxWidth().testTag("goal_${g.id}"), elevation = 10.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Jar(g.fraction, 110.dp)
                Column(Modifier.weight(1f)) {
                    Text(g.emoji, fontSize = 26.sp)
                    Text(g.name, style = MaterialTheme.typography.titleLarge)
                    Text(tr("{0} of {1}", Money.formatInr(g.savedPaise), Money.formatInr(g.targetPaise)) + (g.by?.let { tr("\nby {0}", it.format(DateTimeFormatter.ofPattern("d MMM yyyy", com.owl.tracker.i18n.I18n.locale))) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val per = g.perMonth(today)
                    when {
                        g.done -> Text(tr("🎉 Fully saved"), color = sc.okFg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        per != null -> Text(tr("{0} a month to get there", Money.formatInr(per)), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
                IconButton(onClick = { vm.openSheet(Sheet.GoalEdit(g.id)) }) { Icon(Ic.edit, contentDescription = tr("Change {0}", g.name)) }
            }
            if (!g.done) Button(onClick = { vm.openSheet(Sheet.GoalAdd(g.id)) }, modifier = Modifier.fillMaxWidth().testTag("goal_add_${g.id}")) { Text(tr("Put money in")) }
        }
    }
}

/** Home: the goals, one line each, opening Goals. Only when there are some. */
@Composable
fun GoalsRow(vm: AppViewModel, goals: List<Goal>, modifier: Modifier = Modifier) {
    if (goals.isEmpty()) return
    ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth(), elevation = 10.dp) {
        Column(Modifier.clickable(onClickLabel = tr("See your goals")) { vm.go(Screen.GOALS) }.padding(14.dp).testTag("goals_row"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Goals"), style = MaterialTheme.typography.titleLarge)
            goals.take(3).forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(g.emoji, fontSize = 22.sp)
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth(g.fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(com.owl.tracker.ui.theme.LocalBrand.current.add[1]))
                        }
                    }
                    Text("${(g.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Home, in the new month's first week: last month's leftover, offered to a goal. */
@Composable
fun LeftoverCard(month: String, left: Long, goal: Goal, onAdd: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth().testTag("leftover_card"), elevation = 10.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(goal.emoji, fontSize = 30.sp)
                Text(tr("{0} left {1} of its budget. Put it in {2}?", month, Money.formatInr(left), goal.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAdd, modifier = Modifier.testTag("leftover_add")) { Text(tr("Add {0}", Money.formatInr(minOf(left, goal.targetPaise - goal.savedPaise)))) }
                TextButton(onClick = onClose) { Text(tr("Not now")) }
            }
        }
    }
}

/**
 * Salary day on Home: the salary just in, an offer to put a tenth of it into the first goal (or to start one), and,
 * when salary comes on a steady day that isn't the budget month's first, an offer to start budget months then.
 */
@Composable
fun SalaryCard(vm: AppViewModel, offer: AppViewModel.SalaryOffer, startDay: Int, modifier: Modifier = Modifier) {
    val d = offer.salary.date
    ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth().testTag("salary_card"), elevation = 10.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("💰", fontSize = 30.sp)
                Column(Modifier.weight(1f)) {
                    Text(tr("Salary's in · {0}", Money.formatInr(offer.salary.paise)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (offer.goal != null) tr("Put {0} into {1} before the month takes it?", Money.formatInr(minOf(offer.setAside, offer.goal.targetPaise - offer.goal.savedPaise)), offer.goal.name)
                        else tr("A good moment to set some aside. Start a goal for it?"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val g = offer.goal
                if (g != null) {
                    val amount = minOf(offer.setAside, g.targetPaise - g.savedPaise)
                    Button(onClick = { vm.dismissSalary(d); vm.openSheet(com.owl.tracker.ui.Sheet.GoalAdd(g.id, amount)) }, modifier = Modifier.testTag("salary_put_in")) {
                        Text(tr("Put in {0}", Money.formatInr(amount)))
                    }
                } else {
                    Button(onClick = { vm.dismissSalary(d); vm.go(com.owl.tracker.ui.Screen.GOALS) }, modifier = Modifier.testTag("salary_new_goal")) { Text(tr("Start a goal")) }
                }
                TextButton(onClick = { vm.dismissSalary(d) }, modifier = Modifier.testTag("salary_close")) { Text(tr("Not now")) }
            }
            offer.suggestDay?.let { day ->
                androidx.compose.material3.HorizontalDivider()
                Text(tr("Your salary usually comes on the {0}. Start budget months on the {0}?", ordinal(day)), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { vm.answerSalaryDay(day) }, modifier = Modifier.testTag("salary_day_yes")) { Text(tr("Yes, from the {0}", ordinal(day))) }
                    TextButton(onClick = { vm.answerSalaryDay(null) }, modifier = Modifier.testTag("salary_day_no")) { Text(tr("Keep the {0}", ordinal(startDay))) }
                }
            }
        }
    }
}

/** All goals, and adding a new one. */
@Composable
fun GoalsScreen(vm: AppViewModel) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val today = vm.today()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("goals_screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(tr("Goals"), tr("Set money aside for something"), onBack = { vm.back() })
        if (goals.isEmpty()) Text(tr("A trip, a phone, a deposit: give it a jar and fill it as you set money aside. At the start of each month the owl offers what's left of last month's budget."),
            style = MaterialTheme.typography.bodyMedium)
        goals.forEach { GoalCard(vm, it, today) }
        if (goals.size < Goals.MAX) Button(onClick = { vm.openSheet(Sheet.GoalEdit(null)) }, modifier = Modifier.fillMaxWidth().testTag("goal_new")) { Text(tr("+ New goal")) }
        Text(tr("Goals are kept on your phone (and in your backups). The money stays in your bank; the jar tracks what you set aside."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GoalEditSheet(vm: AppViewModel, id: String?) {
    val existing = remember(id) { vm.goals.value.firstOrNull { it.id == id } }
    var name by rememberSaveable(id) { mutableStateOf(existing?.name ?: "") }
    var emoji by rememberSaveable(id) { mutableStateOf(existing?.emoji ?: Goals.PICTURES.first()) }
    var target by rememberSaveable(id) { mutableStateOf(existing?.targetPaise?.div(100)?.toString() ?: "") }
    var months by rememberSaveable(id) { mutableStateOf(existing?.by?.let { ChronoUnit.MONTHS.between(vm.today().withDayOfMonth(1), it.withDayOfMonth(1)).toInt() } ?: 0) }
    Text(if (id == null) tr("New goal") else tr("Change goal"), style = MaterialTheme.typography.titleLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Goals.PICTURES.forEach { e ->
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (e == emoji) com.owl.tracker.ui.theme.LocalBrand.current.add[1] else MaterialTheme.colorScheme.surfaceVariant).clickable { emoji = e },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 22.sp) }
        }
    }
    OutlinedTextField(name, { name = it.take(40) }, label = { Text(tr("What for?")) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("goal_name"))
    OutlinedTextField(
        target, { v -> target = v.filter { it.isDigit() }.take(9) }, label = { Text(tr("How much? (₹)")) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("goal_target"),
    )
    Text(tr("By when?"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to tr("No date"), 3 to tr("3 months"), 6 to tr("6 months"), 12 to tr("1 year"), 24 to tr("2 years")).forEach { (m, l) ->
            Text(
                l, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (months == m) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (months == m) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { months = m }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
    val t = (target.toLongOrNull() ?: 0) * 100
    val by = if (months > 0) vm.today().plusMonths(months.toLong()).withDayOfMonth(1) else null
    Button(onClick = { vm.saveGoal(id, name, emoji, t, by) }, enabled = name.isNotBlank() && t > 0, modifier = Modifier.fillMaxWidth().testTag("goal_save")) {
        Text(if (id == null) tr("Add goal") else tr("Save"))
    }
    if (id != null) TextButton(onClick = { vm.deleteGoal(id) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Remove this goal")) }
}

@Composable
fun GoalAddSheet(vm: AppViewModel, id: String, suggest: Long) {
    val g = remember(id) { vm.goals.value.firstOrNull { it.id == id } } ?: return
    var amount by rememberSaveable(id) { mutableStateOf(if (suggest > 0) (suggest / 100).toString() else "") }
    Text(tr("Put money in {0}", g.name), style = MaterialTheme.typography.titleLarge)
    Text(tr("{0} of {1} so far. Note what you've set aside (in a savings account, an FD, anywhere).", Money.formatInr(g.savedPaise), Money.formatInr(g.targetPaise)),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        amount, { v -> amount = v.filter { it.isDigit() }.take(9) }, label = { Text(tr("Amount (₹)")) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("goal_add_amount"),
        textStyle = MaterialTheme.typography.headlineSmall,
    )
    val p = (amount.toLongOrNull() ?: 0) * 100
    Button(onClick = { vm.addToGoal(id, p) }, enabled = p > 0, modifier = Modifier.fillMaxWidth().testTag("goal_add_save")) { Text(tr("Add to the jar")) }
}
