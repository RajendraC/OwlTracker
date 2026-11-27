package com.owl.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.BudgetState
import com.owl.tracker.core.Direction
import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import com.owl.tracker.data.Schema
import com.owl.tracker.data.Txn
import com.owl.tracker.licence.Access
import com.owl.tracker.licence.UnlockStore
import com.owl.tracker.ui.ActivityFilter
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.Screen
import com.owl.tracker.ui.Sheet
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.components.DayFilterBar
import com.owl.tracker.ui.components.Divider
import com.owl.tracker.ui.components.LevelLegend
import com.owl.tracker.ui.components.MonthCalendarCard
import com.owl.tracker.ui.components.WeekDaysRow
import com.owl.tracker.ui.components.Donut
import com.owl.tracker.ui.components.DonutSlice
import com.owl.tracker.ui.components.Dot
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.IconTile
import com.owl.tracker.ui.components.MerchantAvatar
import com.owl.tracker.ui.components.NavRow
import com.owl.tracker.ui.components.PaceBar
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.components.SectionLabel
import com.owl.tracker.ui.components.StatusPill
import com.owl.tracker.ui.scene.CategoryScene
import com.owl.tracker.ui.scene.Scenes
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalBrand
import com.owl.tracker.ui.theme.clay
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.translate
import com.owl.tracker.ui.theme.LocalStatusColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

// ======================= HOME =======================

/** A quiet reminder while the free trial runs. */
@Composable
private fun TrialBanner(daysLeft: Int, price: String, lifePrice: String, onUnlock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).testTag("trial_banner"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Ic.calendar, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("Free trial: ${daysText(daysLeft)} left", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Then $price a year, or $lifePrice once", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        TextButton(onClick = onUnlock) { Text("Unlock") }
    }
}

/** After the trial, or when the subscription ended: read-only until a subscription starts or lifetime is bought. */
@Composable
private fun TrialEndedBanner(price: String, lifePrice: String, lapsed: Boolean, onUnlock: () -> Unit) {
    val sc = LocalStatusColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(sc.warnBg).padding(16.dp).testTag("trial_ended_banner")) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Ic.lock, contentDescription = null, tint = sc.warnFg, modifier = Modifier.size(22.dp))
            Column {
                Text(if (lapsed) "Your subscription has ended" else "Your free trial has ended", style = MaterialTheme.typography.titleMedium, color = sc.warnFg)
                Text(
                    "You can still see everything here. Subscribe for $price a year, or pay $lifePrice once, to import new SMS, add expenses and change budgets.",
                    style = MaterialTheme.typography.bodySmall, color = sc.warnFg,
                )
            }
        }
        Gap(10.dp)
        Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().testTag("trial_ended_unlock")) { Text("See ways to unlock") }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val canForward by vm.canGoForward.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val store by vm.store.collectAsStateWithLifecycle()
    val plan by vm.savingPlan.collectAsStateWithLifecycle()
    val monthTxns by vm.periodTxns.collectAsStateWithLifecycle()
    val mode by vm.homeMode.collectAsStateWithLifecycle()
    val weekView by vm.weekView.collectAsStateWithLifecycle()
    val canForwardWeek by vm.canGoForwardWeek.collectAsStateWithLifecycle()
    val hist by vm.monthHistory.collectAsStateWithLifecycle()
    val budgetMap by vm.budgets.collectAsStateWithLifecycle()
    val dash = d ?: return
    val catMap = cats.associateBy { it.id }
    val sc = LocalStatusColors.current
    val side = Modifier.padding(horizontal = 16.dp)
    val today = vm.today()

    // A week at a time, or the month with its calendar; either can be narrowed to one day
    val week = weekView.takeIf { mode == com.owl.tracker.ui.HomeMode.WEEK }
    val weekMode = mode == com.owl.tracker.ui.HomeMode.WEEK
    val scopeKey = if (weekMode) "w" + (week?.start ?: "") else "m" + dash.period.start
    var pickedIso by androidx.compose.runtime.saveable.rememberSaveable(scopeKey) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val picked = pickedIso?.let { LocalDate.parse(it) }
    fun pick(day: LocalDate) { pickedIso = if (pickedIso == day.toString()) null else day.toString() }
    val calendar = remember(dash, monthTxns, cats, weekMode) { if (weekMode) null else vm.monthCalendar(dash, monthTxns, cats) }
    val pickedTotal = picked?.let { p -> (week?.days ?: calendar?.rows?.flatMap { it.days }?.filterNotNull()).orEmpty().firstOrNull { it.date == p } }
    val baseTxns = if (weekMode) week?.txns.orEmpty() else monthTxns
    val txns = remember(baseTxns, picked) { if (picked == null) baseTxns else baseTxns.filter { vm.dateOf(it.occurredAt) == picked } }
    val wholeMonth = !weekMode && picked == null
    val ringKey = scopeKey + (pickedIso ?: "")

    // Drilling down on Home: a group, then one of its categories (or a category on its own), then one of its merchants
    // (back up with the arrow or the phone's Back)
    val tree = remember(cats) { com.owl.tracker.data.CategoryTree(cats) }
    var openGroup by androidx.compose.runtime.saveable.rememberSaveable(ringKey) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var openCat by androidx.compose.runtime.saveable.rememberSaveable(ringKey) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var openMerchant by androidx.compose.runtime.saveable.rememberSaveable(ringKey) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    fun ringUp() { if (openMerchant != null) openMerchant = null else if (openCat != null) openCat = null else openGroup = null }
    androidx.activity.compose.BackHandler(enabled = openCat != null || openGroup != null) { ringUp() }
    // The Paid by tab: the card or account opened in it
    var openPaid by androidx.compose.runtime.saveable.rememberSaveable(ringKey) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val whereView by vm.whereView.collectAsStateWithLifecycle()
    val appUpdate by vm.appUpdate.collectAsStateWithLifecycle()
    val whatsNew by vm.whatsNew.collectAsStateWithLifecycle()
    var recapTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val recapOffer = recapTick.let { vm.recapOffer(settings) }
    val streak by vm.streaks.collectAsStateWithLifecycle()
    var noSpendTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val noSpendYesterday = noSpendTick.let { vm.noSpendYesterday(streak) }
    val showStreak = streak != null && calendar != null && dash.isCurrent
    val bills by vm.bills.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val showBills = calendar != null && dash.isCurrent && com.owl.tracker.core.Recurring.upcoming(bills, today, 30).isNotEmpty()
    val showGoals = calendar != null && dash.isCurrent && goals.isNotEmpty()
    androidx.compose.runtime.LaunchedEffect(goals.size, settings?.budgetStartDay) { if (goals.isNotEmpty()) vm.loadLeftover() }
    var leftoverTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var reportTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val reportOffer = reportTick.let { vm.reportOffer(settings) }
    val leftover = leftoverTick.let { vm.leftoverOffer(settings, goals) }
    var askNudges by remember(settings?.onboarded) { androidx.compose.runtime.mutableStateOf(vm.askForNudges(settings)) }
    val paidTab = wholeMonth && whereView == "paid" && openGroup == null && openCat == null
    androidx.activity.compose.BackHandler(enabled = paidTab && openPaid != null) { openPaid = null }
    androidx.activity.compose.BackHandler(enabled = openCat == null && openGroup == null && picked != null) { pickedIso = null }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // What's changing: this month on Home, when the months behind it show something moving
    val changes = hist?.takeIf { wholeMonth && dash.isCurrent && it.hasData }
    val showChanges = remember(changes, cats) {
        val ok = cats.filter { it.countsAsSpend && !it.archived && it.parent == null }.map { it.id }.toSet()
        changes != null && com.owl.tracker.core.Trends.biggestMoves(changes.byCategory.filterKeys { it in ok }).isNotEmpty()
    }
    // Where "Where it went" sits in the list, to scroll to it from the budget circle
    val whereIndex = 1 + (if (calendar != null) 1 else 0) + (if (pickedTotal != null && weekMode) 1 else 0) +
        (if (access is Access.Trial || !access.canEdit) 1 else 0) +
        (if (settings?.sampleData == true) 1 else 0) + (if (settings?.smsEnabled == false && settings?.sampleData != true) 1 else 0) +
        (if (dash.over.isNotEmpty()) 1 else 0) + (if (showChanges) 1 else 0) + (if (recapOffer != null) 1 else 0) + (if (askNudges) 1 else 0) +
        (if (noSpendYesterday != null) 1 else 0) + (if (showStreak) 1 else 0) + (if (showBills) 1 else 0) + (if (showGoals) 1 else 0) + (if (leftover != null) 1 else 0) + (if (reportOffer != null) 1 else 0)
    // The list runs up behind the status bar: once the header has gone, a band of the background colour keeps
    // the status bar readable, and a card scrolled to stops just below it
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
    var headerPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    fun openCategory(category: String?) {
        if (category != null) {
            val top = tree.top(category)
            when {
                top != category -> { openGroup = top; openCat = category }
                tree.isGroup(category) -> { openGroup = category; openCat = null }
                else -> { openGroup = null; openCat = category }
            }
            openMerchant = null
        }
        scope.launch { listState.animateScrollToItem(whereIndex, -(topInset + with(density) { 8.dp.roundToPx() })) }
    }
    // A split payment counts as its parts, each in its own category
    val spending = remember(txns, catMap) { txns.flatMap { it.portions() }.filter { vm.counts(it, catMap) } }
    // Each category's merchants and their totals, biggest first
    val merchantsOf = remember(spending) {
        spending.groupBy { it.categoryId }.mapValues { (_, list) ->
            list.groupBy { it.merchantKey }
                .map { (k, l) -> com.owl.tracker.ui.components.RingMerchant(k, l.maxBy { it.occurredAt }.merchantName, l.sumOf { it.amountPaise }) }
                .sortedByDescending { it.paise }
        }
    }
    val ringMerchants = openCat?.let { merchantsOf[it] }.orEmpty()
    val ringPayments = if (openCat == null || openMerchant == null) emptyList() else
        txns.flatMap { it.portions() }.filter { it.categoryId == openCat && it.merchantKey == openMerchant }.sortedByDescending { it.occurredAt }
    val budgetOf = dash.categoryRows.associateBy { it.category.id }
    // "Where it went" for a week or a day: worked out from its own payments. By group, with what's inside each.
    val scopeRolled = remember(txns, cats, wholeMonth, dash) {
        if (wholeMonth) dash.byCategory to dash.children else com.owl.tracker.ui.rollUp(spending, cats)
    }
    val scopeCategories = scopeRolled.first
    val scopeChildren = scopeRolled.second
    val scopeSpent = scopeCategories.sumOf { it.paise }
    // A group's picture is drawn from the merchants of all its categories
    val merchantsOfGroup = remember(spending, tree) {
        spending.groupBy { tree.top(it.categoryId) }.mapValues { (_, list) ->
            list.groupBy { it.merchantKey }.map { (_, l) -> l.maxBy { it.occurredAt }.merchantName to l.sumOf { it.amountPaise } }.sortedByDescending { it.second }
        }
    }
    fun sceneFor(cat: Category, spent: Long) = Scenes.forCategory(
        cat, spent, budgetOf[cat.id]?.limit, dash.overall, dash.spent,
        if (tree.isGroup(cat.id)) merchantsOfGroup[cat.id].orEmpty() else merchantsOf[cat.id].orEmpty().map { it.name to it.paise },
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("home"), state = listState, contentPadding = PaddingValues(bottom = 44.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // The mango header, with the budget card resting on its lower edge
            item(key = "top") {
                Column {
                    HomeHeader(vm, dash, canForward, weekMode, week, canForwardWeek, Modifier.onSizeChanged { headerPx = it.height })
                    when {
                        !weekMode -> BudgetCard(vm, dash, plan, ::openCategory, Modifier.padding(horizontal = 14.dp).pullUp(76.dp))
                        week != null -> WeekCard(vm, week, picked, ::pick, Modifier.padding(horizontal = 14.dp).pullUp(76.dp))
                        // The week's payments are still being read
                        else -> com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(36.dp), Modifier.padding(horizontal = 14.dp).pullUp(76.dp).fillMaxWidth().heightIn(min = 300.dp), elevation = 18.dp) {}
                    }
                }
            }

            // Under the budget card: a new version from Google Play, or What's new after updating
            if (appUpdate != com.owl.tracker.update.UpdateUi.None) item(key = "update") {
                UpdateCard(appUpdate, vm::updateNow, vm::updateLater, vm::restartToUpdate, side, onOpenPlay = vm::finishUpdateInPlay)
            }
            whatsNew?.let { v -> item(key = "whats_new") { WhatsNewCard(v, vm::closeWhatsNew, side) } }
            // The month just ended, in a few cards (its first week only)
            recapOffer?.let { p -> item(key = "recap_offer") { RecapOfferCard(com.owl.tracker.nudge.Nudges.monthName(p), { vm.openRecap(p); recapTick++ }, { vm.dismissRecapOffer(p); recapTick++ }, side) } }
            // From the 2nd: last month's report, ready to share with the family
            reportOffer?.let { month -> item(key = "report_offer") { ReportOfferCard(vm, month, { vm.shareMonthly(); reportTick++ }, { vm.reportOfferDone(); reportTick++ }, side) } }
            // Last month's leftover, offered to the first goal not yet full (once)
            leftover?.let { (p, left) ->
                val goal = goals.first { !it.done }
                item(key = "leftover") {
                    LeftoverCard(com.owl.tracker.nudge.Nudges.monthName(p), left, goal, {
                        vm.dismissLeftover(p); leftoverTick++
                        vm.openSheet(Sheet.GoalAdd(goal.id, minOf(left, goal.targetPaise - goal.savedPaise)))
                    }, { vm.dismissLeftover(p); leftoverTick++ }, side)
                }
            }
            // Android 13+: ask once whether the owl may nudge
            if (askNudges) item(key = "nudge_ask") { NudgeAskCard({ vm.nudgeAskDone(); askNudges = false }, side) }

            when (val a = access) {
                is Access.Trial -> item { Box(side) { TrialBanner(a.daysLeft, store.price ?: UnlockStore.FALLBACK_PRICE, store.lifetimePrice ?: UnlockStore.FALLBACK_LIFETIME_PRICE) { vm.openUnlock() } } }
                Access.TrialEnded, Access.Lapsed -> item { Box(side) { TrialEndedBanner(store.price ?: UnlockStore.FALLBACK_PRICE, store.lifetimePrice ?: UnlockStore.FALLBACK_LIFETIME_PRICE, a == Access.Lapsed) { vm.openUnlock() } } }
                Access.Unlocked -> Unit
            }

            if (settings?.sampleData == true) item {
                Panel(modifier = side.testTag("sample_banner")) {
                    Text("You're looking at sample data", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "These transactions are built-in examples. Reset Owl Tracker to start with your own SMS.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Gap(10.dp)
                    OutlinedButton(onClick = { vm.requestReset() }, modifier = Modifier.fillMaxWidth()) { Text("Reset and use my SMS") }
                }
            }

            if (settings?.smsEnabled == false && settings?.sampleData != true) item {
                Panel(modifier = side) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(Ic.message, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text("SMS import is off", style = MaterialTheme.typography.titleSmall)
                            Text("You're in manual mode. Add expenses with +, or turn on SMS import.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Gap(10.dp)
                    OutlinedButton(onClick = { vm.go(Screen.DISCLOSURE) }, modifier = Modifier.fillMaxWidth()) { Text("Turn on SMS import") }
                }
            }

            // Round 27 B: one slim line, not a card per budget; the line and the Over budget figure open the sheet with them all
            if (dash.over.isNotEmpty()) item(key = "over_line") {
                val names = dash.over.sortedByDescending { it.spent - it.limit }.map { it.name }
                val text = when (names.size) {
                    1 -> "${names[0]} is over its budget"
                    2 -> "${names[0]} and ${names[1]} are over"
                    else -> "${names[0]}, ${names[1]} and ${names.size - 2} more are over"
                }
                Row(
                    side.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(sc.overBg)
                        .clickable(onClickLabel = "See the budgets that are over") { vm.openSheet(Sheet.OverBudget) }
                        .padding(horizontal = 14.dp, vertical = 10.dp).testTag("over_line"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Ic.alert, contentDescription = null, tint = sc.overFg, modifier = Modifier.size(20.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = sc.overFg, modifier = Modifier.weight(1f))
                    Icon(Ic.chevR, contentDescription = null, tint = sc.overFg, modifier = Modifier.size(16.dp))
                }
            }

            // Nothing went out yesterday: a little celebration (once)
            noSpendYesterday?.let { d -> item(key = "no_spend_yesterday") { NoSpendCard(streak?.info?.noSpendThisMonth ?: 1, { vm.dismissNoSpend(d); noSpendTick++ }, side) } }

            // Under the budget and its alerts: the categories that moved most over the last months
            if (showChanges && changes != null) item(key = "changes") {
                WhatsChangingCard(changes, cats, budgetMap, vm, side)
            }

            // After the alerts, the month as a calendar, a row a week: tap a day for its payments, a week's total for the week
            if (calendar != null) item(key = "calendar") {
                // A tap on a day narrows "Where it went" (owl, orbit and list) to that day; tap it again for the month
                MonthCalendarCard(calendar.title, calendar.rows, calendar.share, picked, today, ::pick, { start -> vm.openWeek(start) }, side, calendar.firstDay)
            }
            // Under the calendar: days in a row within the share, and the badges
            streak?.takeIf { showStreak }?.let { st -> item(key = "streak") { StreakCard(st, { vm.go(Screen.STREAKS) }, side) } }
            // Bills due in the next 30 days, and the goals
            if (showBills) item(key = "bills") { BillsCard(vm, bills, side) }
            if (showGoals) item(key = "goals") { GoalsRow(vm, goals, side) }

            // One day picked: everything below is about that day
            if (pickedTotal != null && weekMode) item(key = "day_filter") {
                DayFilterBar(
                    pickedTotal, txns.size, week?.share ?: calendar?.share, today, { pickedIso = null }, side,
                )
            }

            // Round 28 B6: no row of pictures; the picture shows in the card beside the ring when a slice is tapped

            // "Where it went": touch the ring, open a category, then a merchant's payments
            item(key = "where") {
                Box(side) {
                    if (scopeCategories.isEmpty()) {
                        Panel {
                            Text("Where it went", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    picked != null -> "Nothing spent on ${com.owl.tracker.ui.components.dayTitle(picked)}."
                                    weekMode -> "Nothing spent this week."
                                    else -> "Nothing spent yet this month."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        com.owl.tracker.ui.components.DrillRingCard(
                            title = when {
                                picked != null && !weekMode -> "Where it went"
                                picked != null -> "Where it went on ${com.owl.tracker.ui.components.dayTitle(picked)}"
                                weekMode -> "Where it went this week".takeIf { week?.isCurrent == true } ?: "Where it went that week"
                                else -> "Where it went"
                            },
                            categories = scopeCategories.map { cs ->
                                val row = budgetOf[cs.category.id].takeIf { wholeMonth }
                                val pct = (cs.paise * 100 / scopeSpent.coerceAtLeast(1)).toInt()
                                com.owl.tracker.ui.components.RingCategory(
                                    cs.category.id, cs.category.name, Color(cs.category.color), cs.paise,
                                    note = "$pct%" + when {
                                        row == null -> ""
                                        row.spent > row.limit -> " · ${Money.formatInr(row.spent - row.limit)} over its budget"
                                        else -> " · ${Money.formatInr(row.limit - row.spent)} left of ${Money.formatInr(row.limit)}"
                                    },
                                    warn = row != null && row.spent > row.limit,
                                    // A group: what it's made of, biggest first
                                    inside = scopeChildren[cs.category.id]?.takeIf { it.isNotEmpty() }?.let { list ->
                                        list.take(3).joinToString(" · ") { ch ->
                                            (if (ch.category.id == cs.category.id) "Not sorted" else ch.category.name) + " " + Money.formatShort(ch.paise)
                                        } + if (list.size > 3) " · +${list.size - 3} more" else ""
                                    },
                                )
                            },
                            pickedCategory = openCat,
                            pickedMerchant = openMerchant,
                            pickedGroup = openGroup,
                            groups = scopeChildren.mapValues { (groupId, list) ->
                                list.map { cs ->
                                    val names = merchantsOf[cs.category.id].orEmpty().take(3).joinToString { it.name }
                                    if (cs.category.id == groupId) com.owl.tracker.ui.components.RingCategory(
                                        groupId, "Not sorted yet", Color(0xFFB9A58F), cs.paise,
                                        note = names.ifEmpty { "Pick what these were" },
                                    ) else com.owl.tracker.ui.components.RingCategory(cs.category.id, cs.category.name, Color(cs.category.color), cs.paise, note = names.ifEmpty { null })
                                }
                            },
                            onUp = ::ringUp,
                            onSort = { groupId ->
                                txns.filter { it.categoryId == groupId && !it.isMoneyIn }.maxByOrNull { it.occurredAt }
                                    ?.let { vm.openSheet(Sheet.CategoryPick(it.id)) }
                            },
                            merchants = ringMerchants,
                            payments = ringPayments,
                            paymentCount = ringPayments.size,
                            sweepKey = ringKey,
                            appIcons = vm.appIcons,
                            catMap = catMap,
                            dayText = { t -> dayLabel(vm, vm.dateOf(t.occurredAt)) },
                            onCategory = { id ->
                                when {
                                    // A group: open it into its categories
                                    openGroup == null && openCat == null && scopeChildren.containsKey(id) -> openGroup = id
                                    // In an open group: one of its categories (or the payments not sorted yet)
                                    openGroup != null && openCat == null -> openCat = id
                                    else -> openCat = if (openCat == id) null else id
                                }
                                openMerchant = null
                            },
                            onMerchant = { k -> openMerchant = if (openMerchant == k) null else k },
                            onPayment = { t -> vm.openSheet(Sheet.TxnDetail(t.id)) },
                            headerAction = {
                                // One day picked from the calendar: a chip says which, and ✕ goes back to the month
                                if (picked != null && !weekMode) androidx.compose.material3.InputChip(
                                    selected = true, onClick = { pickedIso = null },
                                    label = { Text(com.owl.tracker.ui.components.dayTitle(picked), fontWeight = FontWeight.ExtraBold) },
                                    trailingIcon = { Icon(Ic.close, contentDescription = "Show the whole month", modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.testTag("where_day"),
                                ) else TextButton(onClick = { vm.showActivity() }) { Text("See all") }
                            },
                            moods = remember(dash, budgetOf, scopeCategories, picked, week, calendar, pickedTotal) {
                                when {
                                    wholeMonth -> ringMoods(dash, budgetOf)
                                    // A day from the month's calendar: the owl weighs it like its glass, everyday spending
                                    // (EMIs and rent left out) against the day's share after EMIs and rent
                                    picked != null && calendar != null -> vm.dayShares(picked).let { (_, cats) ->
                                        val everyday = pickedTotal?.everydayPaise ?: scopeSpent
                                        scopeMoods(com.owl.tracker.ui.components.dayTitle(picked), everyday, scopeCategories, calendar.share.paise, cats, "the day")
                                    }
                                    picked != null -> vm.dayShares(picked).let { (share, cats) ->
                                        scopeMoods(com.owl.tracker.ui.components.dayTitle(picked), scopeSpent, scopeCategories, share, cats, "the day")
                                    }
                                    else -> scopeMoods(
                                        if (week?.isCurrent == true) "This week" else week?.label ?: "", scopeSpent, scopeCategories,
                                        week?.weekShare ?: 0L, week?.categoryShares.orEmpty(), "the week",
                                    )
                                }
                            },
                            tiles = true,
                            sideBySide = true,
                            // This month, with months behind it: small bars on every row and a 6 months tab
                            trends = hist?.takeIf { wholeMonth && dash.isCurrent && it.hasData }?.let { h ->
                                com.owl.tracker.ui.components.RingTrends(h, budgetMap, budgetMap[Schema.OVERALL], whereView == "six") { vm.setWhereSixMonths(it) }
                            },
                            // The whole month: a third tab, by the card or account it was paid from
                            paidBy = if (!wholeMonth) null else com.owl.tracker.ui.components.PaidByTab(
                                showing = whereView == "paid", opened = openPaid != null,
                                onShow = { on -> openPaid = null; vm.setWherePaidBy(on) },
                            ) {
                                val kinds = settings?.paidKinds.orEmpty()
                                val accounts = remember(txns, cats, kinds) { com.owl.tracker.ui.PaidBy.build(txns, cats, kinds) }
                                PaidByView(vm, accounts, openPaid, cats) { openPaid = it }
                            },
                            // The month's pictures say "of your month's budget", so they only show for the whole month
                            scene = if (!wholeMonth) null else { id ->
                                val cat = catMap[id]
                                // A group, a category on its own, or a category inside a group (not the payments not sorted yet)
                                val spent = dash.byCategory.firstOrNull { it.category.id == id }?.paise
                                    ?: dash.children.values.flatten().firstOrNull { it.category.id == id && id != openGroup }?.paise
                                if (cat != null && spent != null && !(openCat != null && openCat == openGroup)) {
                                    val spec = remember(cat, spent, budgetOf[id]?.limit, dash.overall, dash.spent, ringMerchants) { sceneFor(cat, spent) }
                                    CategoryScene(spec, cat.name)
                                }
                            },
                        )
                    }
                }
            }

            if (dash.reviewCount > 0) item {
                Panel(modifier = side, padding = 4.dp) {
                    NavRow(
                        Ic.info, "${dash.reviewCount} ${if (dash.reviewCount == 1) "transaction needs" else "transactions need"} a quick look",
                        "Unsure matches wait here until you confirm", iconTint = sc.warnFg, iconBg = sc.warnBg,
                    ) { vm.showActivity(filter = ActivityFilter.REVIEW) }
                }
            }

            if (wholeMonth) plan?.takeIf { dash.isCurrent && it.monthlyPaise > 0 && !vm.tipsHidden(settings) }?.let { p -> item(key = "saving_nudge") { Box(side) { SavingNudge(vm, p) } } }

            // No Recent list on Home any more (the user, 20 Nov): every payment is in Activity. A week or a day still
            // lists its payments, as that view is about them.
            if (!wholeMonth) {
                // A day: every payment that day. A week: day by day, newest first.
                item(key = "payments_head") {
                    Column(side.testTag("day_payments")) {
                        Text(
                            when {
                                picked != null && txns.isEmpty() -> "Nothing on ${com.owl.tracker.ui.components.dayTitle(picked)}"
                                picked != null -> "Payments on ${com.owl.tracker.ui.components.dayTitle(picked)}"
                                else -> "Day by day"
                            },
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp).semantics { heading() },
                        )
                        if (picked == null && txns.isEmpty()) {
                            Gap(8.dp)
                            Panel { Text("No transactions this week.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                val byDay = txns.groupBy { vm.dateOf(it.occurredAt) }.toSortedMap(compareByDescending<LocalDate> { it })
                byDay.forEach { (day, list) ->
                    if (picked == null) item(key = "day_head_$day") {
                        val spent = list.filter { vm.counts(it, catMap) }.sumOf { it.amountPaise }
                        Row(side.padding(start = 6.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(dayLabel(vm, day), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Text(Money.formatInr(spent), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(list, key = { "pay_" + it.id }) { t ->
                        com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(24.dp), side.fillMaxWidth(), elevation = 6.dp) {
                            Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) { TxnRow(vm, t, catMap[t.categoryId], showDay = false) }
                        }
                    }
                }
            }

            item {
                Panel(modifier = side, padding = 4.dp) {
                    NavRow(Ic.chart, "Reports", "Income & expenses, ways to save, trends and more", iconTint = MaterialTheme.colorScheme.onPrimaryContainer, iconBg = MaterialTheme.colorScheme.primaryContainer) { vm.go(Screen.INSIGHTS) }
                }
            }
        }
        val covered by remember(headerPx, topInset) {
            androidx.compose.runtime.derivedStateOf {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > (headerPx - topInset).coerceAtLeast(0)
            }
        }
        val scrim by androidx.compose.animation.core.animateFloatAsState(if (covered) 1f else 0f, label = "scrim")
        Box(
            Modifier.fillMaxWidth().windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .graphicsLayer { alpha = scrim }.background(MaterialTheme.colorScheme.background),
        )
    }
}


/** Lays the content out [by] higher than it would sit, and that much shorter, so it overlaps what's above. */
private fun Modifier.pullUp(by: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val px = by.roundToPx()
    layout(p.width, (p.height - px).coerceAtLeast(0)) { p.place(0, -px) }
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}

/** Home's mango header: the owl, a greeting, the month, and the way to Reports. It runs up behind the status bar. */
@Composable
private fun HomeHeader(
    vm: AppViewModel, dash: com.owl.tracker.ui.Dashboard, canForward: Boolean, weekMode: Boolean, week: com.owl.tracker.ui.WeekView?,
    canForwardWeek: Boolean, modifier: Modifier = Modifier,
) {
    val brand = LocalBrand.current
    val dark = com.owl.tracker.ui.theme.LocalDarkTheme.current
    val monthName = dash.period.start.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
    val title = when {
        weekMode -> week?.title ?: "Week by week"
        !dash.isCurrent -> "Looking back"
        dash.period.start.dayOfMonth == 1 -> "$monthName so far"
        else -> "This month so far"
    }
    Box(modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(brand.header))) {
        // A soft sun in the corner
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val c = androidx.compose.ui.geometry.Offset(size.width - 24.dp.toPx(), 10.dp.toPx())
            val r = 150.dp.toPx()
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.75f), Color.White.copy(alpha = 0f)), c, r),
                r, c,
            )
            // Three layers of mango hills at the foot, each casting a soft shadow up onto the one behind
            val w = size.width
            val h = size.height
            val band = 92.dp.toPx()
            fun hill(top: Float, a: Float, b: Float, c2: Float) = androidx.compose.ui.graphics.Path().apply {
                val y0 = h - band
                moveTo(0f, y0 + band * top)
                cubicTo(w * 0.2f, y0 + band * (top - a), w * 0.38f, y0 + band * (top + b), w * 0.56f, y0 + band * (top - c2))
                cubicTo(w * 0.74f, y0 + band * (top - c2 - 0.08f), w * 0.88f, y0 + band * (top + 0.06f), w, y0 + band * (top - 0.04f))
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            listOf(hill(0.42f, 0.3f, 0.1f, 0.2f), hill(0.62f, 0.22f, 0.1f, 0.14f), hill(0.8f, 0.16f, 0.06f, 0.1f)).forEachIndexed { i, path ->
                for (k in 4 downTo 1) {
                    translate(top = -k * 1.2f.dp.toPx()) { drawPath(path, brand.clay.shadow.copy(alpha = 0.05f)) }
                }
                drawPath(path, brand.hills[i])
            }
        }
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 96.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    com.owl.tracker.ui.components.IdleOwl(48.dp, wander = com.owl.tracker.ui.AppViewModel.greetingsOn)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(greeting(), style = MaterialTheme.typography.labelLarge, color = brand.onHeaderSoft)
                    com.owl.tracker.ui.components.FitText(
                        title, style = MaterialTheme.typography.headlineSmall, color = brand.onHeader, maxLines = 2, minScale = 0.75f,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Box(
                    Modifier.size(48.dp).clay(CircleShape, brand.clay, elevation = 6.dp).clickable(onClickLabel = "Open Reports") { vm.go(Screen.INSIGHTS) }
                        .semantics { contentDescription = "Reports" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.chart, contentDescription = null, tint = brand.onHeader, modifier = Modifier.size(22.dp))
                }
            }
            Gap(12.dp)
            // A month or a week, and back to earlier ones and forward again. With big text the two go one under the other.
            val forward = if (weekMode) canForwardWeek else canForward
            val unit = if (weekMode) "week" else "month"
            val periodText = if (weekMode) week?.label ?: "" else dash.label
            val options = shorterLabels(periodText)
            val segStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
            val switchWidth = com.owl.tracker.ui.components.rememberTextWidth("Month", segStyle) +
                com.owl.tracker.ui.components.rememberTextWidth("Week", segStyle) + 74.dp
            val periodMin = com.owl.tracker.ui.components.rememberTextWidth(options.last(), MaterialTheme.typography.labelLarge) + 96.dp
            val periodPill: @Composable (Modifier) -> Unit = { m ->
                Row(m.clay(RoundedCornerShape(24.dp), brand.clay, elevation = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (weekMode) vm.previousWeek() else vm.previousMonth() }, modifier = Modifier.size(44.dp).testTag("period_prev")) {
                        Icon(Ic.chevL, contentDescription = "Previous $unit", tint = brand.onHeader, modifier = Modifier.size(20.dp))
                    }
                    com.owl.tracker.ui.components.FirstThatFits(
                        options, style = MaterialTheme.typography.labelLarge, color = brand.onHeader,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).testTag("period_label"),
                    )
                    IconButton(onClick = { if (weekMode) vm.nextWeek() else vm.nextMonth() }, enabled = forward, modifier = Modifier.size(44.dp).testTag("period_next")) {
                        Icon(Ic.chevR, contentDescription = "Next $unit", tint = brand.onHeader.copy(alpha = if (forward) 1f else 0.3f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (switchWidth + 10.dp + periodMin <= maxWidth) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PeriodSwitch(weekMode, onMonth = { vm.showMonth() }, onWeek = { vm.showWeek() })
                        periodPill(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeriodSwitch(weekMode, onMonth = { vm.showMonth() }, onWeek = { vm.showWeek() })
                        periodPill(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** "November 2026" → also "Nov 2026" and "Nov", for when the full name doesn't fit. Other labels as they are. */
internal fun shorterLabels(label: String): List<String> {
    val m = Regex("""^([A-Za-z]{4,}) (\d{4})$""").find(label) ?: return listOf(label)
    val (month, year) = m.destructured
    return listOf(label, "${month.take(3)} $year", month.take(3))
}

/** Month or Week: two segments pressed into the header, the picked one raised. */
@Composable
private fun PeriodSwitch(isWeek: Boolean, onMonth: () -> Unit, onWeek: () -> Unit) {
    val brand = LocalBrand.current
    Row(
        Modifier.clay(RoundedCornerShape(24.dp), brand.clay, brand.clay.pressed, elevation = 0.dp, pressed = true).padding(4.dp)
            .semantics { contentDescription = "Show a month or a week" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("Month" to !isWeek, "Week" to isWeek).forEach { (label, on) ->
            Box(
                Modifier.heightIn(min = 40.dp)
                    .then(if (on) Modifier.clay(RoundedCornerShape(20.dp), brand.clay, elevation = 3.dp) else Modifier.clip(RoundedCornerShape(20.dp)))
                    .clickable(onClickLabel = "Show a $label".lowercase().replaceFirstChar { it.uppercase() }) { if (label == "Week") onWeek() else onMonth() }
                    .semantics { selected = on; role = androidx.compose.ui.semantics.Role.Tab }
                    .testTag("mode_${label.lowercase()}")
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (on) MaterialTheme.colorScheme.primary else brand.onHeaderSoft,
                )
            }
        }
    }
}

/**
 * The budget card. Spent is always the big number; the ring goes round once for the budget (green, mango from 80%)
 * and, past it, laps on the inside in red, so it never shows more than 100%. The middle says how much is used, or how
 * many times the budget went out ("2.8×"). A small tick marks where an even pace would be by today.
 */
@Composable
private fun BudgetCard(vm: AppViewModel, dash: com.owl.tracker.ui.Dashboard, plan: com.owl.tracker.ui.SavingPlan?, onOpen: (String?) -> Unit, modifier: Modifier = Modifier) {
    val sc = LocalStatusColors.current
    val brand = LocalBrand.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val status = dash.status
    val daysLeft = dash.period.days - dash.dayNumber
    fun days(n: Int) = if (n == 1) "1 day" else "$n days"
    com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(36.dp), modifier.fillMaxWidth().testTag("budget_hero"), elevation = 18.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (status == null) {
                Column {
                    Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = muted)
                    Text(Money.formatInr(dash.spent), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("spent_total"))
                    Text(if (dash.isCurrent) "Day ${dash.dayNumber} of ${dash.period.days}" else dash.label, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Button(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }, modifier = Modifier.fillMaxWidth()) { Text("Set a monthly budget") }
                return@Column
            }
            val used = if (dash.overall > 0) dash.spent.toDouble() / dash.overall else 0.0
            val over = dash.spent - dash.overall
            val left = -over
            val pace = if (dash.isCurrent && used < 1) dash.dayNumber.toFloat() / dash.period.days else null
            val centre = if (used > 1) String.format(java.util.Locale.ROOT, "%.1f×", used) else "${(used * 100).toInt()}%"
            val centreSub = if (used > 1) "the budget" else "used"
            val chips: List<Pair<String, String>> = when {   // text to kind (ok, warn, over, plain)
                over > 0 && dash.isCurrent -> listOf("Over by ${Money.formatInr(over)}" to "over")
                over > 0 -> listOf("Went over by ${Money.formatInr(over)}" to "over")
                !dash.isCurrent -> listOf("${Money.formatInr(left)} saved" to "ok")
                else -> listOfNotNull(
                    "${Money.formatInr(left)} left" to if (status.state == BudgetState.ON_TRACK) "ok" else "warn",
                    status.safePerDayPaise.takeIf { it > 0 && daysLeft > 0 }?.let { "${Money.formatInr(it)} a day for ${days(daysLeft)}" to "plain" },
                )
            }
            val info: @Composable (Modifier, Alignment.Horizontal) -> Unit = { m, align ->
                Column(m, horizontalAlignment = align) {
                    Text("Spent", style = MaterialTheme.typography.labelLarge, color = muted)
                    // The biggest number is always what went out; a tap shows those payments
                    com.owl.tracker.ui.components.FitText(
                        Money.formatInr(dash.spent), style = MaterialTheme.typography.headlineMedium, minScale = 0.6f,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "See this month's payments") { vm.showActivity(filter = com.owl.tracker.ui.ActivityFilter.SPENT) }
                            .testTag("figure_spent"),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("budget ${Money.formatInr(dash.overall)}", style = MaterialTheme.typography.bodyMedium, color = muted)
                        IconButton(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }, modifier = Modifier.size(40.dp).testTag("edit_budget")) {
                            Icon(Ic.edit, contentDescription = "Edit the monthly budget", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (dash.isCurrent) Text(
                        "Can I afford it? ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "See what a purchase does to the month") { vm.openSheet(Sheet.Afford) }
                            .padding(vertical = 2.dp).testTag("afford_open"),
                    )
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEachIndexed { i, (text, kind) ->
                            val (bg, fg) = when (kind) {
                                "ok" -> sc.okBg to sc.okFg
                                "warn" -> sc.bg(BudgetState.HEADS_UP) to sc.fg(BudgetState.HEADS_UP)
                                "over" -> sc.overBg to sc.overFg
                                else -> brand.spentBg to MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text, color = fg, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp)
                                    .then(if (i == 0) Modifier.testTag("left_total") else Modifier),
                            )
                        }
                    }
                }
            }
            val need = maxOf(
                com.owl.tracker.ui.components.rememberTextWidth(Money.formatInr(dash.spent), MaterialTheme.typography.headlineMedium) * 0.7f,
                com.owl.tracker.ui.components.rememberTextWidth("budget ${Money.formatInr(dash.overall)}", MaterialTheme.typography.bodyMedium) + 40.dp,
                com.owl.tracker.ui.components.rememberTextWidth(chips.first().first, MaterialTheme.typography.labelLarge) + 24.dp,
            )
            val ring: @Composable () -> Unit = {
                LapRing(used, pace, !dash.isCurrent, centre, centreSub, dash.period.start, Modifier.clickable(onClickLabel = "See where it went") { onOpen(null) })
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth - 132.dp - 14.dp >= need) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ring()
                        info(Modifier.weight(1f), Alignment.Start)
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ring()
                        info(Modifier.fillMaxWidth(), Alignment.CenterHorizontally)
                    }
                }
            }
            // What the ring's parts mean, quietly
            Text(
                if (used > 1) "Outer ring: the budget, once round · inner: what went past it"
                else if (pace != null) "The small mark is where an even pace would be by today" else "The ring is the budget, once round",
                style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
            val overRows = dash.categoryRows.filter { it.status.state == BudgetState.OVER }
            val overNames = when (overRows.size) {
                0 -> null
                1 -> "${overRows[0].category.name} went over"
                2 -> "${overRows[0].category.name} and ${overRows[1].category.name} went over"
                else -> "${overRows[0].category.name}, ${overRows[1].category.name} and ${overRows.size - 2} more went over"
            }
            val top2 = dash.byCategory.take(2).map { it.category.name }
            val line: Pair<String, BudgetState>? = when {
                over > 0 && dash.isCurrent -> "${days(daysLeft)} left · ${overNames ?: "no room left in the budget this month"}" to BudgetState.OVER
                over > 0 -> "${dash.label} went over" + (if (top2.isNotEmpty()) ". ${top2.joinToString(" and ")} were most of it" else "") to BudgetState.OVER
                !dash.isCurrent -> "${dash.label} came in ${Money.formatInr(left)} under budget" to BudgetState.ON_TRACK
                status.state == BudgetState.HEADS_UP && status.projectedPaise > dash.overall && status.safePerDayPaise > 0 ->
                    "Faster than usual: keep to ${Money.formatInr(status.safePerDayPaise)} a day to finish within budget" to BudgetState.HEADS_UP
                status.state == BudgetState.HEADS_UP -> "Nearly there: ${Money.formatInr(left)} left for the last ${days(daysLeft)}" to BudgetState.HEADS_UP
                else -> null
            }
            if (line != null) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(sc.bg(line.second))
                    .then(if (overRows.isNotEmpty() && line.second == BudgetState.OVER) Modifier.clickable(onClickLabel = "See the budgets that are over") { vm.openSheet(Sheet.OverBudget) } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(if (line.second == BudgetState.ON_TRACK) Ic.check else Ic.alert, contentDescription = null, tint = sc.fg(line.second), modifier = Modifier.size(18.dp))
                Text(line.first, color = sc.fg(line.second), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f).testTag("budget_line"))
            }
            // Two small figures (Spent is the big number now): a budget that's over, or the biggest category; and
            // what could be saved, or the days left
            val biggest = dash.byCategory.firstOrNull()
            val monthOver = over > 0
            val figures = buildList {
                when {
                    overRows.size == 1 -> add(Figure("Over budget", overRows[0].category.name, sc.overBg, sc.overFg,
                        com.owl.tracker.ui.components.categoryArt(overRows[0].category.icon), "figure_over", "Open ${overRows[0].category.name}") { onOpen(overRows[0].category.id) })
                    overRows.size > 1 -> add(Figure("Over budget", "${overRows.size} categories", sc.overBg, sc.overFg,
                        com.owl.tracker.ui.components.categoryArt(overRows[0].category.icon), "figure_over", "See the budgets that are over",
                    ) { vm.openSheet(Sheet.OverBudget) }.apply { badge = overRows.size })
                    monthOver && biggest != null -> add(Figure("Biggest", biggest.category.name, brand.spentBg, MaterialTheme.colorScheme.onSurface,
                        com.owl.tracker.ui.components.categoryArt(biggest.category.icon), "figure_over", "Open ${biggest.category.name}") { onOpen(biggest.category.id) })
                    else -> add(Figure("Over budget", "None", sc.okBg, sc.okFg, null, "figure_over", "See your budgets") { vm.go(Screen.BUDGETS) })
                }
                val save = plan?.monthlyPaise?.takeIf { it > 0 && dash.isCurrent }
                if (save != null) {
                    add(Figure("Could save", Money.formatInr(save), sc.okBg, sc.okFg, com.owl.tracker.R.drawable.soft3d_piggy, "figure_save", "See the ways to save") { vm.openWaysToSave() })
                } else {
                    add(Figure(if (dash.isCurrent) "Days left" else "Days", "${if (dash.isCurrent) daysLeft else dash.period.days}", brand.spentBg,
                        MaterialTheme.colorScheme.onSurface, com.owl.tracker.R.drawable.soft3d_calendar, "figure_days", null, null))
                }
            }
            Figures(figures)
        }
    }
}

/**
 * The budget as a ring that laps: the outer ring fills once for the budget (green, then mango from 80%; green for a
 * finished month that stayed within it); past the budget, an inner red ring laps once more, and again brighter past
 * twice. [pace] puts a tick where an even pace would be by today.
 */
@Composable
private fun LapRing(used: Double, pace: Float?, finished: Boolean, centre: String, centreSub: String, period: LocalDate, modifier: Modifier = Modifier) {
    val sc = LocalStatusColors.current
    val track = sc.track
    val green = com.owl.tracker.ui.components.RING_GREEN
    val mango = com.owl.tracker.ui.components.RING_MANGO
    val red = com.owl.tracker.ui.components.RING_RED
    val redLight = Color(0xFFFF6B6B)
    val tick = MaterialTheme.colorScheme.onSurface
    val grow = com.owl.tracker.ui.components.growIn(period, 1100)
    val outerColor = if (used < 0.8 || (finished && used <= 1)) green else mango
    Box(
        modifier.size(132.dp).clip(CircleShape).testTag("budget_ring").semantics {
            contentDescription = if (used > 1) "Spent $centre the budget" else "$centre of the budget used"
        }.drawBehind {
            val ow = 15.dp.toPx()
            val r = size.minDimension / 2f - ow / 2f - 2.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val sz = androidx.compose.ui.geometry.Size(2 * r, 2 * r)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(ow))
            val outer = (used.coerceIn(0.0, 1.0) * 360 * grow).toFloat()
            if (outer > 0f) drawArc(outerColor, -90f, outer, false, tl, sz, style = Stroke(ow, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            if (used > 1) {
                val iw = 11.dp.toPx()
                val ir = r - ow / 2f - iw / 2f - 3.dp.toPx()
                val itl = Offset(center.x - ir, center.y - ir)
                val isz = androidx.compose.ui.geometry.Size(2 * ir, 2 * ir)
                drawArc(track, 0f, 360f, false, itl, isz, style = Stroke(iw))
                val past = (used - 1) * grow
                if (past > 1) drawArc(red, 0f, 360f, false, itl, isz, style = Stroke(iw))
                val lap = (if (past > 1) (past - 1).coerceAtMost(1.0) else past) * 360
                drawArc(redLight, -90f, lap.toFloat().coerceAtMost(359.5f), false, itl, isz, style = Stroke(iw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            if (pace != null) {
                val a = Math.toRadians((pace * 360 - 90).toDouble())
                val r0 = r - ow / 2f - 3.dp.toPx(); val r1 = r + ow / 2f + 3.dp.toPx()
                drawLine(tick, Offset(center.x + r0 * kotlin.math.cos(a).toFloat(), center.y + r0 * kotlin.math.sin(a).toFloat()),
                    Offset(center.x + r1 * kotlin.math.cos(a).toFloat(), center.y + r1 * kotlin.math.sin(a).toFloat()), 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(if (used > 1) 66.dp else 84.dp)) {
            com.owl.tracker.ui.components.FitText(centre, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, minScale = 0.6f, modifier = Modifier.testTag("ring_centre_value"))
            // Big text: "the budget" wraps onto two lines rather than being cut
            com.owl.tracker.ui.components.CapFontScale(1.3f) {
                Text(centreSub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, lineHeight = MaterialTheme.typography.labelSmall.fontSize)
            }
        }
    }
}

/** One figure under the budget: its label, value, colours, picture, and what a tap opens (null: nothing). */
private class Figure(
    val label: String, val value: String, val bg: Color, val fg: Color, val art: Int?, val tag: String,
    val clickLabel: String?, val onClick: (() -> Unit)?,
) {
    /** A count shown in place of the chevron (how many budgets are over). */
    var badge: Int? = null
}

/**
 * The figures as three small tiles in a row when their words fit, else as full-width rows one under the other (big
 * text, narrow phones, lakh-size amounts). The tiles never cut a label or an amount.
 */
@Composable
private fun Figures(figures: List<Figure>) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val valueStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().testTag("figures")) {
        val tileText = (maxWidth - 8.dp * (figures.size - 1)) / figures.size - 22.dp
        fun width(t: String, st: androidx.compose.ui.text.TextStyle) = with(d) { measurer.measure(t, st, maxLines = 1, softWrap = false).size.width.toDp() }
        val tiles = figures.all { f ->
            width(f.label, labelStyle) * 0.85f <= tileText &&
                f.value.split(' ').all { w -> width(w, valueStyle) * 0.72f <= tileText }
        }
        if (tiles) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                figures.forEach { FigureTile(it, labelStyle, valueStyle, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { figures.forEach { FigureRow(it, labelStyle, valueStyle) } }
        }
    }
}

@Composable
private fun Modifier.figureSurface(f: Figure, shape: RoundedCornerShape): Modifier {
    val colors = LocalBrand.current.clay
    return this.clay(shape, colors, f.bg.compositeOver(colors.surface), elevation = 4.dp)
        .then(if (f.onClick != null) Modifier.clip(shape).clickable(onClickLabel = f.clickLabel, onClick = f.onClick) else Modifier)
        .testTag(f.tag)
}

@Composable
private fun FigureTile(f: Figure, labelStyle: androidx.compose.ui.text.TextStyle, valueStyle: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    Column(modifier.figureSurface(f, RoundedCornerShape(20.dp)).padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (f.art != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(f.art), contentDescription = null, modifier = Modifier.size(24.dp))
            else Spacer(Modifier.height(24.dp))
            Spacer(Modifier.weight(1f))
            if (f.badge != null) {
                androidx.compose.foundation.layout.Box(
                    Modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp).clip(RoundedCornerShape(11.dp)).background(f.fg).padding(horizontal = 6.dp).testTag("figure_badge"),
                    contentAlignment = Alignment.Center,
                ) { Text("${f.badge}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = f.bg) }
            } else if (f.onClick != null) Icon(Ic.chevR, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
        Gap(2.dp)
        com.owl.tracker.ui.components.FitText(f.label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, minScale = 0.85f)
        com.owl.tracker.ui.components.FitText(
            f.value, style = valueStyle, color = f.fg, minScale = 0.72f, maxLines = if (' ' in f.value) 2 else 1,
            modifier = if (f.tag == "figure_spent") Modifier.testTag("spent_total") else Modifier,
        )
    }
}

@Composable
private fun FigureRow(f: Figure, labelStyle: androidx.compose.ui.text.TextStyle, valueStyle: androidx.compose.ui.text.TextStyle) {
    Row(
        Modifier.fillMaxWidth().figureSurface(f, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (f.art != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(f.art), contentDescription = null, modifier = Modifier.size(28.dp))
        com.owl.tracker.ui.components.SideOrStacked(
            Modifier.weight(1f),
            start = { Text(f.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            end = {
                Text(f.value, style = valueStyle, color = f.fg, modifier = if (f.tag == "figure_spent") Modifier.testTag("spent_total") else Modifier)
            },
        )
        if (f.onClick != null) Icon(Ic.chevR, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/**
 * The week card: what went out this week, against the same days of the week before (EMIs and rent left out), the
 * seven days as coloured circles to tap, and what's left of the month.
 */
@Composable
private fun WeekCard(vm: AppViewModel, week: com.owl.tracker.ui.WeekView, picked: LocalDate?, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val sc = LocalStatusColors.current
    val brand = LocalBrand.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val today = vm.today()
    com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(36.dp), modifier.fillMaxWidth().testTag("week_card"), elevation = 18.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val c = week.compare
            val pct = c?.percent
            val cmpColor = when {
                pct == null -> muted
                pct < -1 -> sc.okFg
                pct > 1 -> sc.overFg
                else -> muted
            }
            Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (week.isCurrent) "Spent this week" else "Spent that week", style = MaterialTheme.typography.labelLarge, color = muted)
                    Text(Money.formatInr(week.spent), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("week_total"))
                    val count = week.txns.count { !it.isMoneyIn }
                    Text(
                        if (week.fixed > 0) "Everyday ${Money.formatInr(week.spent - week.fixed)} · EMIs and rent ${Money.formatInr(week.fixed)}"
                        else "$count ${if (count == 1) "payment" else "payments"}",
                        style = MaterialTheme.typography.bodySmall, color = muted,
                    )
                    if (c != null) {
                        val than = if (c.days < 7) {
                            val from = week.start.minusDays(7)
                            val short = { d: LocalDate -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) }
                            if (c.days == 1) "last ${short(from)}" else "${short(from)}–${short(from.plusDays(c.days - 1L))} last week"
                        } else "the week before"
                        val lead = if (week.fixed > 0) "Leaving out EMIs and rent, " else ""
                        Text(
                            when {
                                pct == null -> "Nothing everyday spent $than"
                                pct < -1 -> "$lead${-pct}% less than $than (${Money.formatInr(c.beforePaise)})"
                                pct > 1 -> "$lead$pct% more than $than (${Money.formatInr(c.beforePaise)})"
                                else -> "${lead}about the same as $than"
                            }.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = cmpColor,
                            modifier = Modifier.testTag("week_compare"),
                        )
                    }
                }
                if (pct != null) {
                    Text(
                        when { pct < -1 -> "↓ ${-pct}%"; pct > 1 -> "↑ $pct%"; else -> "≈ same" },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = cmpColor,
                        modifier = Modifier.clay(RoundedCornerShape(14.dp), brand.clay, brand.clay.pressed, elevation = 0.dp, pressed = true)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            WeekDaysRow(week.days, picked, today, week.share.fromBudget, onPick)
            LevelLegend(week.share, Modifier.padding(horizontal = 2.dp))
            val passed = week.days.filter { !it.date.isAfter(today) }
            val biggest = passed.maxByOrNull { it.paise }
            Figures(
                listOf(
                    Figure("Daily average", Money.formatInr(week.spent / passed.size.coerceAtLeast(1)), brand.spentBg, MaterialTheme.colorScheme.onSurface,
                        com.owl.tracker.R.drawable.soft3d_coins, "figure_week_average", null, null),
                    Figure(
                        "Biggest day",
                        if (biggest == null || biggest.paise == 0L) "None yet"
                        else biggest.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + Money.formatShort(biggest.paise),
                        brand.spentBg, MaterialTheme.colorScheme.onSurface, com.owl.tracker.R.drawable.soft3d_wallet, "figure_week_biggest", "Show that day",
                        biggest?.takeIf { it.paise > 0 }?.let { b -> { onPick(b.date) } },
                    ),
                ),
            )
            week.monthLeft?.let { left ->
                Row(
                    Modifier.fillMaxWidth().clay(RoundedCornerShape(20.dp), brand.clay, brand.clay.pressed, elevation = 0.dp, pressed = true)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.owl.tracker.R.drawable.soft3d_piggy), contentDescription = null, modifier = Modifier.size(34.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (left >= 0) "${Money.formatInr(left)} left in ${week.monthName}" else "${Money.formatInr(-left)} over budget in ${week.monthName}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (left >= 0) MaterialTheme.colorScheme.onSurface else sc.overFg,
                            modifier = Modifier.testTag("week_month_left"),
                        )
                        Text(
                            if (left > 0 && week.daysLeft > 0) "About ${Money.formatInr(week.safePerDay)} a day for the next ${week.daysLeft} ${if (week.daysLeft == 1) "day" else "days"}"
                            else if (left > 0) "The budget month ends today" else "Every rupee from here adds to it",
                            style = MaterialTheme.typography.bodySmall, color = muted,
                        )
                    }
                }
            }
        }
    }
}

/** What the owl says about a week or a day: against its share of the month's budget, and each category against its share. */
private fun scopeMoods(
    title: String, spent: Long, byCategory: List<com.owl.tracker.ui.CategorySpend>, share: Long, categoryShares: Map<String, Long>, span: String,
): com.owl.tracker.ui.components.RingMoods {
    val inr = { p: Long -> Money.formatInr(p) }
    val line = when {
        share <= 0 -> "Set a month budget and I'll keep watch"
        spent <= share -> "${inr(share - spent)} left of $span's ${inr(share)} share"
        else -> "${inr(spent - share)} over $span's ${inr(share)} share"
    }
    val cats = byCategory.associate { cs ->
        val lim = categoryShares[cs.category.id]?.takeIf { it > 0 }
        val l = when {
            lim == null -> "Give it a budget in Budgets and I'll keep watch"
            cs.paise <= lim -> "${inr(lim - cs.paise)} left of its ${inr(lim)} for $span"
            else -> "${inr(cs.paise - lim)} over its ${inr(lim)} for $span"
        }
        cs.category.id to com.owl.tracker.ui.components.OwlSay(com.owl.tracker.ui.components.OwlMood.of(cs.paise, lim), "${cs.category.name} · ${inr(cs.paise)}", l)
    }
    return com.owl.tracker.ui.components.RingMoods(
        com.owl.tracker.ui.components.OwlSay(com.owl.tracker.ui.components.OwlMood.of(spent, share.takeIf { it > 0 }), "$title · ${inr(spent)}", line), cats,
        use = byCategory.mapNotNull { cs -> categoryShares[cs.category.id]?.takeIf { it > 0 }?.let { cs.category.id to cs.paise.toFloat() / it } }.toMap(),
        spent = spent, budget = share.coerceAtLeast(0),
    )
}


/**
 * The budget circle, split into the categories the money went to (in their colours). Slide a finger
 * round it to see each one in the middle; tap one to open it in "Where it went" just below, or tap
 * the middle for the whole breakdown. Over budget, a red inner ring shows by how much.
 */
/** Last month's report, ready to share (2nd to 7th). */
@Composable
private fun ReportOfferCard(vm: AppViewModel, month: String, onShare: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val to = remember { com.owl.tracker.nudge.Nudges.reportTo(context) }
    val excel = remember { com.owl.tracker.nudge.Nudges.reportExcel(context) }
    com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth().testTag("report_offer"), elevation = 10.dp) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📊", fontSize = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Your $month report is ready", style = MaterialTheme.typography.titleLarge)
                    Text(
                        (if (excel) "An Excel workbook" else "An interactive page that opens in any browser") + ", made on your phone. Share it with ${to ?: "your family"}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("report_offer_close")) { Icon(Ic.close, contentDescription = "Not now") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onShare, modifier = Modifier.testTag("report_offer_share")) { Text(if (to != null) "Share with $to" else "Share") }
                TextButton(onClick = { vm.reportPreset = com.owl.tracker.export.ReportPeriod.LAST_MONTH; vm.openSheet(Sheet.ShareReport) }) { Text("More choices") }
            }
        }
    }
}

/** The month just ended, in a few cards: shown on Home in the new month's first week. */
@Composable
private fun RecapOfferCard(month: String, onOpen: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF3A1C0C), Color(0xFF8A3E12), Color(0xFFE08A12))))
            .clickable(onClickLabel = "Open the recap", onClick = onOpen).padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 14.dp).testTag("recap_offer"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Your $month recap is ready", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Your month in a few cards: saved, biggest day, top merchant", color = Color(0xFFFFE3B0), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onClose, modifier = Modifier.testTag("recap_offer_close")) { Icon(Ic.close, contentDescription = "Not now", tint = Color.White) }
    }
}

/** Android 13+: ask once whether the owl may nudge. Not now puts it away for good (Settings has the switches). */
@Composable
private fun NudgeAskCard(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { onDone() }
    com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth().testTag("nudge_ask"), elevation = 10.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.owl.tracker.ui.components.MoodOwl(com.owl.tracker.ui.components.OwlMood.HAPPY, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text("Let the owl nudge you", style = MaterialTheme.typography.titleLarge)
                    Text("A line each evening, a heads-up at 80% of your budget, your week on Sundays. Worked out on your phone; nothing is sent.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.testTag("nudge_ask_allow")) { Text("Allow nudges") }
                TextButton(onClick = onDone, modifier = Modifier.testTag("nudge_ask_later")) { Text("Not now") }
            }
        }
    }
}

/** Home's nudge: how much could be saved a month, and the biggest way to do it. Hidden until next month with ×. */
@Composable
private fun SavingNudge(vm: AppViewModel, plan: com.owl.tracker.ui.SavingPlan) {
    val tip = plan.tips.first()
    Panel(modifier = Modifier.testTag("saving_nudge")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Ic.bulb, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text("Ways to save", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Save about ${Money.formatInr(plan.monthlyPaise)} a month",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("saving_total"),
                )
            }
            IconButton(onClick = { vm.hideSavingTips() }, modifier = Modifier.testTag("saving_hide")) {
                Icon(Ic.close, contentDescription = "Hide saving tips until next month", modifier = Modifier.size(18.dp))
            }
        }
        Gap(10.dp)
        Text(tip.title, style = MaterialTheme.typography.titleSmall)
        Text(tip.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(4.dp)
        TextButton(onClick = { vm.openWaysToSave() }, modifier = Modifier.testTag("saving_more")) {
            Text(if (plan.tips.size > 1) "See all ${plan.tips.size} ways to save" else "See it in Reports")
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun dayLabel(vm: AppViewModel, d: LocalDate): String = when (d) {
    vm.today() -> "Today"
    vm.today().minusDays(1) -> "Yesterday"
    else -> d.format(dayFmt)
}

/** A small purple tag on money put into investments: it went out of the budget, and it's still yours. */
@Composable
private fun InvestmentTag() {
    Text(
        "Investment",
        Modifier.clip(RoundedCornerShape(50)).background(com.owl.tracker.ui.components.INVESTED.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp).testTag("investment_tag"),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = com.owl.tracker.ui.components.INVESTED, maxLines = 1,
    )
}

@Composable
fun TxnRow(vm: AppViewModel, t: Txn, category: Category?, showDay: Boolean) {
    val counted = t.isSpending(category)
    val invested = t.isInvested(category)
    val credit = t.direction == Direction.CREDIT
    val whenText = if (showDay) dayLabel(vm, vm.dateOf(t.occurredAt))
    else java.time.Instant.ofEpochMilli(t.occurredAt).atZone(java.time.ZoneId.systemDefault()).format(timeFmt)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { vm.openSheet(Sheet.TxnDetail(t.id)) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MerchantAvatar(vm.appIcons, t.merchantPackage, t.merchantName, category)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.merchantName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (t.needsReview) UnsureBadge()
                if (invested) InvestmentTag()
            }
            // A split payment names its parts; one part of it (in a category's list) says what it's part of
            val what = when {
                t.isSplit && t.isPortion -> "${category?.name ?: "Others"} · part of ${Money.formatInr(t.parts.sumOf { it.paise })}"
                t.isSplit -> "Split: " + t.parts.joinToString(" + ") { vm.categoryName(it.categoryId) }
                else -> category?.name ?: "Others"
            }
            Text(
                "$what · $whenText" + when {
                    t.excluded -> " · not counted"
                    credit -> " · money in"
                    else -> ""
                },
                // Two lines at most, so big text still shows the day or time
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (com.owl.tracker.ui.components.textScaleAtLeast(1.3f)) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (credit) "+" else "−") + Money.formatInr(t.amountPaise),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.owl.tracker.ui.theme.LocalDisplayFont.current, fontWeight = FontWeight.Bold),
            color = when {
                credit -> MaterialTheme.colorScheme.primary
                invested -> com.owl.tracker.ui.components.INVESTED
                counted -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

// ======================= ACTIVITY =======================

@Composable
fun ActivityScreen(vm: AppViewModel) {
    val list by vm.activity.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val filter by vm.activityFilter.collectAsStateWithLifecycle()
    val catFilter by vm.categoryFilter.collectAsStateWithLifecycle()
    val dayFilter by vm.dayFilter.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val catMap = cats.associateBy { it.id }
    val groups = list.groupBy { vm.dateOf(it.occurredAt) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Activity", "${d?.label ?: ""} · ${list.size} ${if (list.size == 1) "transaction" else "transactions"}")
        OutlinedTextField(
            value = q, onValueChange = { vm.query.value = it },
            placeholder = { Text("Search merchant, category or note") },
            leadingIcon = { Icon(Ic.search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search"),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dayFilter?.let { day ->
                InputChip(selected = true, onClick = { vm.dayFilter.value = null }, label = { Text(com.owl.tracker.ui.components.dayTitle(day)) },
                    trailingIcon = { Icon(Ic.close, contentDescription = "Show every day", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("activity_day"))
            }
            catFilter?.let { id ->
                InputChip(selected = true, onClick = { vm.categoryFilter.value = null }, label = { Text(catMap[id]?.name ?: "Category") },
                    trailingIcon = { Icon(Ic.close, contentDescription = "Remove category filter", modifier = Modifier.size(16.dp)) })
            }
            FilterChip(filter == ActivityFilter.ALL, { vm.activityFilter.value = ActivityFilter.ALL }, { Text("All") })
            FilterChip(filter == ActivityFilter.SPENT, { vm.activityFilter.value = ActivityFilter.SPENT }, { Text("Spent") })
            FilterChip(filter == ActivityFilter.RECEIVED, { vm.activityFilter.value = ActivityFilter.RECEIVED }, { Text("Received") })
            FilterChip(filter == ActivityFilter.REVIEW, { vm.activityFilter.value = ActivityFilter.REVIEW }, { Text("Needs review") })
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 44.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    Gap(8.dp)
                    Text("No transactions match", style = MaterialTheme.typography.titleMedium)
                    Text("Try another search or filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            groups.forEach { (day, txns) ->
                item(key = "d$day") {
                    val total = txns.filter { vm.counts(it, catMap) }.sumOf { it.amountPaise }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                        Text(dayLabel(vm, day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        val invested = txns.filter { it.isInvested(catMap[it.categoryId]) }.sumOf { it.amountPaise }
                        if (total > 0) Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                append(if (invested > 0) "${Money.formatInr(total)} out" else "${Money.formatInr(total)} spent")
                                if (invested > 0) { pushStyle(androidx.compose.ui.text.SpanStyle(color = com.owl.tracker.ui.components.INVESTED)); append(" · ${Money.formatInr(invested)} invested"); pop() }
                            },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "g$day") {
                    Panel(padding = 8.dp) {
                        txns.forEachIndexed { i, t ->
                            if (i > 0) Divider()
                            TxnRow(vm, t, catMap[t.categoryId], showDay = false)
                        }
                    }
                }
            }
        }
    }
}

// ======================= BUDGETS =======================

@Composable
fun BudgetsScreen(vm: AppViewModel) {
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dash = d ?: return
    val sc = LocalStatusColors.current
    val pace = dash.dayNumber.toDouble() / dash.period.days
    val startDay = settings?.budgetStartDay ?: 1

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 44.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Budgets", "${dash.label} · " + if (dash.isCurrent) "${dash.period.days - dash.dayNumber} days left" else "ended") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (dash.overall > 0) "${Money.formatInr(dash.spent)} of ${Money.formatInr(dash.overall)}" else "Not set",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    OutlinedButton(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }, modifier = Modifier.testTag("edit_month_budget")) {
                        Text(if (dash.overall > 0) "Edit" else "Set")
                    }
                }
                dash.status?.let { s ->
                    Gap(12.dp)
                    PaceBar(s.fraction, pace, sc.ring(s.state, MaterialTheme.colorScheme.primary))
                    Gap(8.dp)
                    StatusPill(s.state)
                }
                Gap(12.dp)
                // Every category's budget at once, with the last six months on each slider
                androidx.compose.material3.FilledTonalButton(
                    onClick = { vm.go(Screen.ADJUST_BUDGETS) },
                    modifier = Modifier.fillMaxWidth().testTag("adjust_budgets"),
                ) {
                    Icon(Ic.sliders, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    com.owl.tracker.ui.components.FitText("Adjust budgets with sliders", minScale = 0.85f, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(width = 2.dp, height = 14.dp).background(MaterialTheme.colorScheme.onSurface))
                Text("The marker shows where an even pace would put you today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (dash.categoryRows.isNotEmpty()) {
            item { SectionLabel("By group") }
            item {
                Panel(padding = 12.dp) {
                    dash.categoryRows.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        Column(Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.BudgetEdit(r.category.id)) }.padding(vertical = 10.dp).testTag("budget_row_${r.category.id}")) {
                            // The status beside the name when there's room, else under it
                            com.owl.tracker.ui.components.SideOrStacked(
                                Modifier.fillMaxWidth(), stackedGap = 6.dp,
                                start = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CategoryTile(r.category, 36.dp)
                                        Column {
                                            Text(r.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${Money.formatInr(r.spent)} of ${Money.formatInr(r.limit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                end = { StatusPill(r.status.state) },
                            )
                            Gap(8.dp)
                            PaceBar(r.status.fraction, pace, sc.ring(r.status.state, Color(r.category.color)), height = 8.dp)
                            insideLine(dash, r.category.id)?.let {
                                Gap(6.dp)
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inside_${r.category.id}"))
                            }
                        }
                    }
                }
            }
        }
        item { SectionLabel("No budget yet") }
        item {
            Text(
                "Budgets are for a whole group: Shopping covers Fashion & Clothing, Phones & Electronics and the rest, so a phone bought once a year doesn't need one of its own.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        item {
            Panel(padding = 12.dp) {
                dash.unbudgeted.forEachIndexed { i, cs ->
                    if (i > 0) Divider()
                    // The whole row opens the budget's slider, as "Set" does
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.BudgetEdit(cs.category.id)) }.padding(vertical = 8.dp).testTag("set_budget_${cs.category.id}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryTile(cs.category, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(cs.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (cs.paise > 0) "${Money.formatInr(cs.paise)} spent this month" else "Nothing spent yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            insideLine(dash, cs.category.id)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inside_${cs.category.id}"))
                            }
                        }
                        TextButton(onClick = { vm.openSheet(Sheet.BudgetEdit(cs.category.id)) }) { Text("Set") }
                    }
                }
            }
        }
        item {
            Panel(padding = 4.dp) {
                NavRow(Ic.calendar, "Budget month starts on the ${ordinal(startDay)}", "Tap to match it to your salary day") {
                    val next = listOf(1, 5, 10, 25).let { opts -> opts[(opts.indexOf(startDay) + 1).mod(opts.size)] }
                    vm.setStartDay(next)
                }
            }
        }
    }
}

/** What a group's spending is made of this month: "Daily Commute ₹38,906 · Car & Bike ₹2,600", biggest first. */
private fun insideLine(dash: com.owl.tracker.ui.Dashboard, groupId: String): String? {
    val list = dash.children[groupId]?.takeIf { it.isNotEmpty() } ?: return null
    return list.take(3).joinToString(" · ") { cs ->
        (if (cs.category.id == groupId) "Not sorted" else cs.category.name) + " " + Money.formatInr(cs.paise)
    } + if (list.size > 3) " · +${list.size - 3} more" else ""
}

fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}


/** What the owl in "Where it went" says: about the month against its budget, and each category against its own. */
private fun ringMoods(dash: com.owl.tracker.ui.Dashboard, budgetOf: Map<String, com.owl.tracker.ui.CategoryBudgetRow>): com.owl.tracker.ui.components.RingMoods {
    val inr = { p: Long -> Money.formatInr(p) }
    val month = (if (dash.isCurrent) "This month" else dash.label) + " · " + inr(dash.spent)
    val monthLine = when {
        dash.overall <= 0 -> "Set a month budget and I'll keep watch"
        dash.spent <= dash.overall -> "${inr(dash.overall - dash.spent)} left of ${inr(dash.overall)}"
        else -> "${inr(dash.spent - dash.overall)} over the ${inr(dash.overall)} budget"
    }
    val cats = dash.byCategory.associate { cs ->
        val row = budgetOf[cs.category.id]
        val line = when {
            row == null || row.limit <= 0 -> "Give it a budget in Budgets and I'll keep watch"
            row.spent <= row.limit -> "${inr(row.limit - row.spent)} left of ${inr(row.limit)}"
            else -> "${inr(row.spent - row.limit)} over its ${inr(row.limit)} budget"
        }
        cs.category.id to com.owl.tracker.ui.components.OwlSay(
            com.owl.tracker.ui.components.OwlMood.of(row?.spent ?: cs.paise, row?.limit), "${cs.category.name} · ${inr(cs.paise)}", line,
        )
    }
    // How much of each category's own budget is used, for the ring round its bubble
    val use = dash.byCategory.mapNotNull { cs ->
        budgetOf[cs.category.id]?.takeIf { it.limit > 0 }?.let { cs.category.id to it.spent.toFloat() / it.limit }
    }.toMap()
    return com.owl.tracker.ui.components.RingMoods(
        com.owl.tracker.ui.components.OwlSay(com.owl.tracker.ui.components.OwlMood.of(dash.spent, dash.overall.takeIf { it > 0 }), month, monthLine), cats, use,
        spent = dash.spent, budget = dash.overall.coerceAtLeast(0),
    )
}
