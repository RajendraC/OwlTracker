package com.owl.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.Money
import com.owl.tracker.data.CategoryTree
import com.owl.tracker.data.EventBudget
import com.owl.tracker.data.Events
import com.owl.tracker.data.Txn
import com.owl.tracker.i18n.tr
import com.owl.tracker.i18n.trn
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.Sheet
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.theme.ClaySurface
import com.owl.tracker.ui.theme.LocalStatusColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Diya = Color(0xFFF4A21B)
private fun dayFmt() = DateTimeFormatter.ofPattern("d MMM", com.owl.tracker.i18n.I18n.locale)

/** A template's name, in the app's language. */
fun templateName(key: String): String = when (key) {
    "diwali" -> tr("Diwali")
    "wedding" -> tr("Wedding")
    "trip" -> tr("Trip")
    "holi" -> tr("Holi")
    "eid" -> tr("Eid")
    "harvest" -> tr("Pongal · Onam")
    "christmas" -> tr("Christmas")
    else -> tr("Other")
}

private fun dates(e: EventBudget) = "${e.start.format(dayFmt())} – ${e.end.format(dayFmt())}"

/** "2 days left", "starts 12 Dec", or "ended". */
private fun whenWords(e: EventBudget, today: LocalDate): String = when {
    e.isOver(today) -> tr("ended {0}", e.end.format(dayFmt()))
    !e.hasStarted(today) -> tr("starts {0}", e.start.format(dayFmt()))
    else -> e.daysLeft(today).let { if (it == 1) tr("last day") else tr("{0} days left", it) }
}

@Composable
private fun SpendBar(fraction: Float, over: Boolean, modifier: Modifier = Modifier) {
    val sc = LocalStatusColors.current
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (over) sc.overFg else Diya))
    }
}

/** Home: an event that's on, or starting within two weeks. */
@Composable
fun EventHomeCard(vm: AppViewModel, st: AppViewModel.EventStatus, modifier: Modifier = Modifier) {
    val e = st.event
    val today = vm.today()
    val spent = st.spend.totalPaise
    val left = e.budgetPaise - spent
    val sc = LocalStatusColors.current
    ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth().testTag("event_card_${e.id}"), elevation = 10.dp) {
        Column(Modifier.clickable(onClickLabel = tr("Open {0}", e.name)) { vm.openEvent(e.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(e.emoji, fontSize = 30.sp)
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleMedium)
                    Text(dates(e) + " · " + whenWords(e, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e.hasStarted(today)) {
                    val (label, fg, bg) = when {
                        left < 0 -> Triple(tr("Over"), sc.overFg, sc.overBg)
                        spent > e.budgetPaise * 0.8 -> Triple(tr("Getting close…"), sc.warnFg, sc.warnBg)
                        else -> Triple(tr("On track"), sc.okFg, sc.okBg)
                    }
                    Text(label, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            if (e.hasStarted(today)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Money.formatInr(spent), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(tr("of {0}", Money.formatInr(e.budgetPaise)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SpendBar(spent.toFloat() / e.budgetPaise, left < 0)
                Text(
                    (if (left >= 0) tr("{0} left.", Money.formatInr(left)) else tr("{0} over.", Money.formatInr(-left))) + " " + tr("Also counted in your monthly budget."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(tr("{0} budget · payments in its categories count from {1}", Money.formatInr(e.budgetPaise), e.start.format(dayFmt())),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Home, in the week after an event: what it came to, against last time, and its leftover offered to a goal. */
@Composable
fun EventWrapCard(vm: AppViewModel, w: AppViewModel.EventWrap, modifier: Modifier = Modifier) {
    val e = w.status.event
    val spent = w.status.spend.totalPaise
    val left = e.budgetPaise - spent
    val top = w.status.spend.byTop.firstOrNull()?.first
    val cats by vm.categories.collectAsStateWithLifecycle()
    val topName = top?.let { id -> cats.firstOrNull { it.id == id }?.name }
    ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth().testTag("event_wrap"), elevation = 10.dp) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.owl.tracker.ui.components.OwlMark(64.dp)
                Text(e.emoji, fontSize = 36.sp)
            }
            Text(tr("{0}: done!", e.name), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(tr("{0} of {1}", Money.formatInr(spent), Money.formatInr(e.budgetPaise)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val lines = buildList {
                w.before?.let { b ->
                    if (spent < b) add(tr("{0} less than last time.", Money.formatInr(b - spent)))
                    else if (spent > b) add(tr("{0} more than last time.", Money.formatInr(spent - b)))
                }
                if (topName != null) add(tr("{0} was the biggest part.", topName))
                if (left < 0) add(tr("{0} over its budget.", Money.formatInr(-left)))
            }
            if (lines.isNotEmpty()) Text(lines.joinToString(" "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            val g = w.goal
            if (left > 0 && g != null) {
                val amount = minOf(left, g.targetPaise - g.savedPaise)
                Button(onClick = { vm.closeEventWrap(e.id); vm.openSheet(Sheet.GoalAdd(g.id, amount)) }, modifier = Modifier.fillMaxWidth().testTag("event_wrap_goal")) {
                    Text(tr("Put {0} in {1}", Money.formatInr(amount), g.name))
                }
            }
            TextButton(onClick = { vm.closeEventWrap(e.id) }, modifier = Modifier.testTag("event_wrap_close")) { Text(tr("Got it")) }
        }
    }
}

/** Home, three weeks before Diwali or Christmas: give it its own budget? */
@Composable
fun FestivalOfferCard(vm: AppViewModel, f: AppViewModel.FestivalOffer, modifier: Modifier = Modifier) {
    val t = Events.template(f.template)
    ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth().testTag("festival_offer"), elevation = 10.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t.emoji, fontSize = 30.sp)
                Column(Modifier.weight(1f)) {
                    Text(if (f.daysAway == 0) tr("{0} is today", templateName(f.template)) else tr("{0} in {1} days", templateName(f.template), f.daysAway), style = MaterialTheme.typography.titleMedium)
                    Text(tr("Give it its own budget, so you know what it cost when it's over."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startFestival(f) }, modifier = Modifier.testTag("festival_start")) { Text(tr("Set a budget")) }
                TextButton(onClick = { vm.closeFestival(f) }, modifier = Modifier.testTag("festival_close")) { Text(tr("Not now")) }
            }
        }
    }
}

/** One event in the Events tab: picture, name, dates, its bar and how much of its budget went. */
@Composable
private fun EventRow(vm: AppViewModel, e: EventBudget, spent: Long, today: LocalDate) {
    ClaySurface(RoundedCornerShape(22.dp), Modifier.fillMaxWidth().testTag("event_row_${e.id}"), elevation = 8.dp) {
        Row(Modifier.clickable { vm.openEvent(e.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(e.emoji, fontSize = 26.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium)
                Text(dates(e) + " · " + whenWords(e, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.hasStarted(today)) SpendBar(spent.toFloat() / e.budgetPaise, spent > e.budgetPaise)
            }
            Text(
                if (e.hasStarted(today)) tr("{0} of {1}", Money.formatShort(spent), Money.formatShort(e.budgetPaise)) else Money.formatShort(e.budgetPaise),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Budgets → Events (1.9.38, moved from More): what's on now (its Home card), what's coming, what's done, and a new one.
 * With none yet: what they're for, a template to start from, and the festival offer when one is near.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventsTab(vm: AppViewModel) {
    val map by vm.eventStatus.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val tick by vm.eventTick.collectAsStateWithLifecycle()
    val today = vm.today()
    Column(Modifier.fillMaxWidth().testTag("events_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val festival = tick.let { vm.festivalOffer(map) }
        if (events.isEmpty()) {
            ClaySurface(RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), elevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.owl.tracker.ui.components.OwlMark(72.dp)
                    Text(tr("Big spends get their own budget"), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(tr("Give a festival, a wedding or a trip its own money and dates. It still counts in your month."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            festival?.let { FestivalOfferCard(vm, it) }
            Text(tr("Start one"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
                Events.TEMPLATES.forEach { t ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { vm.eventTemplate = t.key; vm.openSheet(Sheet.EventEdit(null)) }
                            .padding(vertical = 10.dp, horizontal = 4.dp).testTag("events_start_${t.key}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(t.emoji, fontSize = 24.sp)
                        Text(templateName(t.key), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            return@Column
        }
        festival?.let { FestivalOfferCard(vm, it) }
        val now = events.filter { it.hasStarted(today) && !it.isOver(today) }.sortedBy { it.end }
        val coming = events.filter { !it.hasStarted(today) }.sortedBy { it.start }
        val done = events.filter { it.isOver(today) }.sortedByDescending { it.end }
        if (now.isNotEmpty()) {
            Text(tr("On now"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
            now.forEach { e -> map[e.id]?.let { EventHomeCard(vm, it) } ?: EventRow(vm, e, 0, today) }
        }
        if (coming.isNotEmpty()) {
            Text(tr("Coming up"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
            coming.forEach { e -> EventRow(vm, e, map[e.id]?.spend?.totalPaise ?: 0, today) }
        }
        if (done.isNotEmpty()) {
            Text(tr("Finished"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
            done.forEach { e -> EventRow(vm, e, map[e.id]?.spend?.totalPaise ?: 0, today) }
        }
        if (events.size < Events.MAX) Button(onClick = { vm.openSheet(Sheet.EventEdit(null)) }, modifier = Modifier.fillMaxWidth().testTag("event_new")) { Text(tr("+ New event budget")) }
        Text(tr("Event budgets are kept on your phone and in your backups."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** All event budgets, newest first, and starting a new one. */
@Composable
fun EventsScreen(vm: AppViewModel) {
    val map by vm.eventStatus.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val today = vm.today()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("events_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(tr("Event budgets"), tr("A festival, a wedding, a trip"), onBack = { vm.back() })
        if (events.isEmpty()) Text(tr("Give Diwali, a wedding or a trip its own budget and dates. Payments in its categories count toward it, and still count in your monthly budget."),
            style = MaterialTheme.typography.bodyMedium)
        events.sortedByDescending { it.start }.forEach { e ->
            val spent = map[e.id]?.spend?.totalPaise ?: 0
            ClaySurface(RoundedCornerShape(22.dp), Modifier.fillMaxWidth().testTag("event_row_${e.id}"), elevation = 8.dp) {
                Row(Modifier.clickable { vm.openEvent(e.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(e.emoji, fontSize = 26.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium)
                        Text(dates(e) + " · " + whenWords(e, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SpendBar(spent.toFloat() / e.budgetPaise, spent > e.budgetPaise)
                    }
                    Text(tr("{0} of {1}", Money.formatShort(spent), Money.formatShort(e.budgetPaise)), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (events.size < Events.MAX) Button(onClick = { vm.openSheet(Sheet.EventEdit(null)) }, modifier = Modifier.fillMaxWidth().testTag("event_new")) { Text(tr("+ New event budget")) }
        Text(tr("Event budgets are kept on your phone and in your backups."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One event: its ring, where the money went, and its payments. */
@Composable
fun EventScreen(vm: AppViewModel) {
    val id by vm.shownEvent.collectAsStateWithLifecycle()
    val map by vm.eventStatus.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val st = id?.let { map[it] }
    val today = vm.today()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("event_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (st == null) {
            ScreenHeader(tr("Event budgets"), null, onBack = { vm.back() })
            return@Column
        }
        val e = st.event
        val spent = st.spend.totalPaise
        val left = e.budgetPaise - spent
        val sc = LocalStatusColors.current
        ScreenHeader("${e.emoji} ${e.name}", dates(e) + " · " + whenWords(e, today), onBack = { vm.back() })
        ClaySurface(RoundedCornerShape(28.dp), Modifier.fillMaxWidth(), elevation = 10.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val fraction = spent.toFloat() / e.budgetPaise
                val track = MaterialTheme.colorScheme.surfaceVariant
                val ring = if (left < 0) sc.overFg else Diya
                Box(Modifier.size(150.dp).semantics { contentDescription = tr("{0} of {1}", Money.formatInr(spent), Money.formatInr(e.budgetPaise)) }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(150.dp)) {
                        val w = 16.dp.toPx()
                        val s = Size(size.width - w, size.height - w)
                        drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
                        drawArc(ring, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(w / 2, w / 2), s, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(tr("used"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(tr("{0} of {1}", Money.formatInr(spent), Money.formatInr(e.budgetPaise)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val days = e.daysLeft(today)
                Text(
                    when {
                        left < 0 -> tr("{0} over.", Money.formatInr(-left))
                        e.isOver(today) -> tr("{0} left.", Money.formatInr(left))
                        e.hasStarted(today) -> tr("{0} left · about {1} a day", Money.formatInr(left), Money.formatInr(left / days.coerceAtLeast(1)))
                        else -> tr("{0} left.", Money.formatInr(left))
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(tr("Also counted in your monthly budget."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (st.spend.byTop.isNotEmpty()) ClaySurface(RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), elevation = 8.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Where it went"), style = MaterialTheme.typography.titleMedium)
                val most = st.spend.byTop.first().second.coerceAtLeast(1)
                st.spend.byTop.forEach { (top, paise) ->
                    val c = cats.firstOrNull { it.id == top }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryTile(c, 36.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row { Text(c?.name ?: top, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(Money.formatInr(paise), style = MaterialTheme.typography.titleSmall) }
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxWidth(paise.toFloat() / most).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(c?.color ?: 0xFF7F8682)))
                            }
                        }
                    }
                }
            }
        }
        val catMap = cats.associateBy { it.id }
        Text(if (st.spend.payments.isEmpty()) tr("No payments yet. Payments in its categories during its dates will show here.")
            else trn(st.spend.payments.size, "{0} payment", "{0} payments"), style = MaterialTheme.typography.titleMedium)
        st.spend.payments.forEach { t -> TxnRow(vm, t, catMap[t.categoryId], showDay = true) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.openSheet(Sheet.EventEdit(e.id)) }, modifier = Modifier.weight(1f).testTag("event_edit")) { Text(tr("Change")) }
        }
    }
}

/** Starting an event budget from a template, or changing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditSheet(vm: AppViewModel, id: String?) {
    val existing = remember(id) { vm.events.value.firstOrNull { it.id == id } }
    val today = vm.today()
    val first = rememberSaveable(id) { existing?.template ?: vm.eventTemplate.also { vm.eventTemplate = null } ?: "diwali" }
    var template by rememberSaveable(id) { mutableStateOf(first) }
    fun defaultName(key: String) = if (key == "other") "" else "${templateName(key)} ${Events.suggestDates(key, today).second.year}"
    var name by rememberSaveable(id) { mutableStateOf(existing?.name ?: defaultName(first)) }
    var start by rememberSaveable(id) { mutableStateOf((existing?.start ?: Events.suggestDates(first, today).first).toString()) }
    var end by rememberSaveable(id) { mutableStateOf((existing?.end ?: Events.suggestDates(first, today).second).toString()) }
    var budget by rememberSaveable(id) { mutableStateOf(existing?.budgetPaise?.div(100)?.toString() ?: "") }
    var chosen by rememberSaveable(id) { mutableStateOf((existing?.categories ?: Events.template(first).categories.toSet()).joinToString(",")) }
    var picking by remember { mutableStateOf<String?>(null) }
    val cats by vm.categories.collectAsStateWithLifecycle()
    val tree = remember(cats) { CategoryTree(cats) }

    Text(if (id == null) tr("New event budget") else tr("Change {0}", existing?.name ?: ""), style = MaterialTheme.typography.titleLarge)
    if (id == null) Text(tr("A festival, a wedding, a trip: money with its own dates and limit."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
        Events.TEMPLATES.forEach { t ->
            val on = t.key == template
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (on) com.owl.tracker.ui.theme.LocalBrand.current.add[1] else MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable {
                        if (template != t.key) {
                            template = t.key
                            if (id == null) {
                                name = defaultName(t.key)
                                val (s, e) = Events.suggestDates(t.key, today)
                                start = s.toString(); end = e.toString()
                                chosen = t.categories.joinToString(",")
                            }
                        }
                    }
                    .padding(vertical = 8.dp, horizontal = 2.dp).testTag("event_template_${t.key}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t.emoji, fontSize = 24.sp)
                Text(templateName(t.key), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
    OutlinedTextField(name, { name = it.take(40) }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("event_name"))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(tr("From") to "start", tr("To") to "end").forEach { (label, which) ->
            val d = LocalDate.parse(if (which == "start") start else end)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable { picking = which }.padding(horizontal = 14.dp, vertical = 10.dp).testTag("event_$which"),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.format(DateTimeFormatter.ofPattern("d MMM yyyy", com.owl.tracker.i18n.I18n.locale)), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    OutlinedTextField(
        budget, { v -> budget = v.filter { it.isDigit() }.take(9) }, label = { Text(tr("Budget (₹)")) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("event_budget"),
        supportingText = vm.lastSpentOn(template)?.let { last -> { Text(tr("Last time you spent {0}", Money.formatInr(last))) } },
    )
    Text(tr("What counts towards it"), style = MaterialTheme.typography.labelLarge)
    val chosenSet = chosen.split(",").filter { it.isNotEmpty() }.toSet()
    var allCats by remember { mutableStateOf(false) }
    // Each group, and under it its categories when one of them is chosen (or when all are asked for); a group
    // chosen whole stands for all of its categories
    val options = remember(cats, chosen, allCats) {
        val tops = tree.spendTops.filter { it.id !in setOf(com.owl.tracker.core.Cat.TRANSFERS, com.owl.tracker.core.Cat.EMI, com.owl.tracker.core.Cat.INVESTMENTS) }
        tops.flatMap { top ->
            val kids = tree.children(top.id)
            listOf(top) + if (top.id in chosenSet || (!allCats && kids.none { it.id in chosenSet })) emptyList() else kids
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { c ->
            val on = c.id in chosenSet
            Text(
                c.name, style = MaterialTheme.typography.labelLarge, fontWeight = if (c.parent == null) FontWeight.Bold else FontWeight.Medium,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        val next = if (on) chosenSet - c.id else chosenSet + c.id - tree.children(c.id).map { it.id }.toSet()
                        chosen = next.joinToString(",")
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp).testTag("event_cat_${c.id}"),
            )
        }
    }
    if (!allCats) TextButton(onClick = { allCats = true }, modifier = Modifier.testTag("event_all_cats")) { Text(tr("More choices")) }
    Text(tr("Payments in these categories during its dates count toward it, and still count in your monthly budget. You can move any payment in or out from its details."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val b = (budget.toLongOrNull() ?: 0) * 100
    val s = LocalDate.parse(start)
    val e = LocalDate.parse(end)
    Button(
        onClick = { vm.saveEvent(id, template, name, s, e, b, chosenSet) },
        enabled = name.isNotBlank() && b > 0 && !e.isBefore(s) && chosenSet.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().testTag("event_save"),
    ) { Text(if (id == null) tr("Start {0} budget", name.ifBlank { templateName(template) }) else tr("Save")) }
    if (id != null) TextButton(onClick = { vm.deleteEvent(id) }, modifier = Modifier.fillMaxWidth().testTag("event_delete")) { Text(tr("Remove this event")) }

    picking?.let { which ->
        EventDatePicker(LocalDate.parse(if (which == "start") start else end), today, onPick = { d ->
            if (which == "start") { start = d.toString(); if (LocalDate.parse(end).isBefore(d)) end = d.toString() }
            else { end = d.toString(); if (LocalDate.parse(start).isAfter(d)) start = d.toString() }
            picking = null
        }, onDismiss = { picking = null })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EventDatePicker(current: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    fun utc(d: LocalDate) = d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = utc(current), yearRange = (today.year - 2)..(today.year + 3))
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() },
                modifier = Modifier.testTag("event_date_ok")) { Text(tr("OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    ) { androidx.compose.material3.DatePicker(state = state) }
}

/** In a payment's details: move it in or out of each event whose dates include it. */
@Composable
fun EventToggles(vm: AppViewModel, t: Txn) {
    vm.events.collectAsStateWithLifecycle().value // redraw when an event changes
    vm.eventsFor(t).forEach { e ->
        val inIt = vm.inEvent(e, t)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag("event_toggle_${e.id}")) {
            Text(e.emoji, fontSize = 24.sp)
            Column(Modifier.weight(1f)) {
                Text(tr("Part of {0}", e.name), style = MaterialTheme.typography.titleSmall)
                Text(tr("Counts in the event and in your month"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = inIt, onCheckedChange = { vm.setInEvent(e.id, t, it) })
        }
    }
}
