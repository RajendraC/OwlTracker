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
private fun TrialBanner(daysLeft: Int, price: String, onUnlock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).testTag("trial_banner"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Ic.calendar, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("Free trial: ${daysText(daysLeft)} left", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Then a one-time $price unlock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        TextButton(onClick = onUnlock) { Text("Unlock") }
    }
}

/** After the trial: read-only until the unlock is bought. */
@Composable
private fun TrialEndedBanner(price: String, onUnlock: () -> Unit) {
    val sc = LocalStatusColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(sc.warnBg).padding(16.dp).testTag("trial_ended_banner")) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Ic.lock, contentDescription = null, tint = sc.warnFg, modifier = Modifier.size(22.dp))
            Column {
                Text("Your free trial has ended", style = MaterialTheme.typography.titleMedium, color = sc.warnFg)
                Text(
                    "You can still see everything here. Unlock to import new SMS, add expenses and change budgets.",
                    style = MaterialTheme.typography.bodySmall, color = sc.warnFg,
                )
            }
        }
        Gap(10.dp)
        Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().testTag("trial_ended_unlock")) { Text("Unlock for $price") }
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

    // Drilling down on Home: a category, then one of its merchants (back up with the arrow or the phone's Back)
    var openCat by androidx.compose.runtime.saveable.rememberSaveable(ringKey) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var openMerchant by androidx.compose.runtime.saveable.rememberSaveable(ringKey) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = openCat != null) { if (openMerchant != null) openMerchant = null else openCat = null }
    androidx.activity.compose.BackHandler(enabled = openCat == null && picked != null) { pickedIso = null }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val showPictures = wholeMonth && dash.byCategory.isNotEmpty()
    // Where "Where it went" sits in the list, to scroll to it from the budget circle and the pictures
    val whereIndex = 1 + (if (calendar != null) 1 else 0) + (if (pickedTotal != null) 1 else 0) +
        (if (access is Access.Trial || access == Access.TrialEnded) 1 else 0) +
        (if (settings?.sampleData == true) 1 else 0) + (if (settings?.smsEnabled == false && settings?.sampleData != true) 1 else 0) +
        dash.over.size + (if (showPictures) 1 else 0)
    // The list runs up behind the status bar: once the header has gone, a band of the background colour keeps
    // the status bar readable, and a card scrolled to stops just below it
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
    var headerPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    fun openCategory(category: String?) {
        if (category != null) { openCat = category; openMerchant = null }
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
    // "Where it went" for a week or a day: worked out from its own payments
    val scopeCategories = remember(txns, catMap, wholeMonth, dash) {
        if (wholeMonth) dash.byCategory
        else spending.groupBy { it.categoryId }.mapNotNull { (id, l) -> catMap[id]?.let { com.owl.tracker.ui.CategorySpend(it, l.sumOf { t -> t.amountPaise }) } }
            .filter { it.paise > 0 }.sortedByDescending { it.paise }
    }
    val scopeSpent = scopeCategories.sumOf { it.paise }
    fun sceneFor(cat: Category, spent: Long) = Scenes.forCategory(
        cat, spent, budgetOf[cat.id]?.limit, dash.overall, dash.spent, merchantsOf[cat.id].orEmpty().map { it.name to it.paise },
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

            when (val a = access) {
                is Access.Trial -> item { Box(side) { TrialBanner(a.daysLeft, store.price ?: UnlockStore.FALLBACK_PRICE) { vm.openUnlock() } } }
                Access.TrialEnded -> item { Box(side) { TrialEndedBanner(store.price ?: UnlockStore.FALLBACK_PRICE) { vm.openUnlock() } } }
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

            items(dash.over, key = { "over_" + it.budgetKey }) { o ->
                Column(side.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(sc.overBg).padding(16.dp).testTag("over_banner")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Ic.alert, contentDescription = null, tint = sc.overFg, modifier = Modifier.size(22.dp))
                        Column {
                            Text(o.title, style = MaterialTheme.typography.titleMedium, color = sc.overFg)
                            Text(o.subtitle, style = MaterialTheme.typography.bodySmall, color = sc.overFg)
                        }
                    }
                    Gap(10.dp)
                    // The buttons go onto a second line when they don't fit side by side
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.showActivity(o.categoryId) }, colors = ButtonDefaults.buttonColors(containerColor = sc.overFg, contentColor = sc.overBg)) { Text("See where it went") }
                        OutlinedButton(onClick = { vm.openSheet(Sheet.BudgetEdit(o.budgetKey)) }) { Text("Adjust budget", color = sc.overFg) }
                    }
                }
            }

            // After the alerts, the month as a calendar, a row a week: tap a day for its payments, a week's total for the week
            if (calendar != null) item(key = "calendar") {
                MonthCalendarCard(calendar.title, calendar.rows, calendar.share, picked, today, ::pick, { start -> vm.openWeek(start) }, side)
            }

            // One day picked: everything below is about that day
            if (pickedTotal != null) item(key = "day_filter") {
                DayFilterBar(
                    pickedTotal, txns.size, (week?.share ?: calendar?.share)?.fromBudget == true, today, { pickedIso = null }, side,
                )
            }

            // A picture for each category the money went to; tapping one opens it in "Where it went" below (the whole month only)
            if (showPictures) item(key = "pictures") {
                val pictures = dash.byCategory.take(MAX_PICTURES).map { cs -> cs.category to sceneFor(cs.category, cs.paise) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (dash.isCurrent) "This month in pictures" else "That month in pictures",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
                    )
                    PicturesRow(pictures, onOpen = { id -> openCategory(id) })
                }
            }

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
                                )
                            },
                            pickedCategory = openCat,
                            pickedMerchant = openMerchant,
                            merchants = ringMerchants,
                            payments = ringPayments,
                            paymentCount = ringPayments.size,
                            sweepKey = ringKey,
                            appIcons = vm.appIcons,
                            catMap = catMap,
                            dayText = { t -> dayLabel(vm, vm.dateOf(t.occurredAt)) },
                            onCategory = { id -> openCat = if (openCat == id) null else id; openMerchant = null },
                            onMerchant = { k -> openMerchant = if (openMerchant == k) null else k },
                            onPayment = { t -> vm.openSheet(Sheet.TxnDetail(t.id)) },
                            headerAction = { TextButton(onClick = { vm.showActivity() }) { Text("See all") } },
                            moods = remember(dash, budgetOf, scopeCategories, picked, week) {
                                when {
                                    wholeMonth -> ringMoods(dash, budgetOf)
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
                            // The month's pictures say "of your month's budget", so they only show for the whole month
                            scene = if (!wholeMonth) null else { id ->
                                val cat = catMap[id]
                                val spent = dash.byCategory.firstOrNull { it.category.id == id }?.paise
                                if (cat != null && spent != null) {
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

            if (wholeMonth) item {
                Column(side) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() })
                        TextButton(onClick = { vm.showActivity() }) { Text("See all") }
                    }
                    if (dash.recent.isEmpty()) {
                        Panel { Text("No transactions yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    // Each payment its own little clay card
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        dash.recent.forEach { t ->
                            com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), elevation = 6.dp) {
                                Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) { TxnRow(vm, t, catMap[t.categoryId], showDay = true) }
                            }
                        }
                    }
                }
            } else {
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

private const val MAX_PICTURES = 10

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
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.owl.tracker.R.drawable.owl_mascot), contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
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

/** The budget card: the circle split by category, what's left, how it's going, and three small figures. */
@Composable
private fun BudgetCard(vm: AppViewModel, dash: com.owl.tracker.ui.Dashboard, plan: com.owl.tracker.ui.SavingPlan?, onOpen: (String?) -> Unit, modifier: Modifier = Modifier) {
    val sc = LocalStatusColors.current
    val brand = LocalBrand.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val status = dash.status
    val daysLeft = dash.period.days - dash.dayNumber
    com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(36.dp), modifier.fillMaxWidth().testTag("budget_hero"), elevation = 18.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (status == null) {
                Column {
                    Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = muted)
                    Text(Money.formatInr(dash.spent), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("spent_total"))
                    Text(if (dash.isCurrent) "Day ${dash.dayNumber} of ${dash.period.days}" else dash.label, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Button(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }, modifier = Modifier.fillMaxWidth()) { Text("Set a monthly budget") }
                return@Column
            }
            // The ring and what's left, side by side; with big text (or a narrow phone) one under the other
            val left = dash.overall - dash.spent
            val heading = when { left < 0 -> "Over budget by"; dash.isCurrent -> "Left this month"; else -> "Left over" }
            val amount = Money.formatInr(kotlin.math.abs(left))
            val ofText = "of ${Money.formatInr(dash.overall)}"
            val info: @Composable (Modifier, Alignment.Horizontal) -> Unit = { m, align ->
                Column(m, horizontalAlignment = align) {
                    Text(heading, style = MaterialTheme.typography.labelLarge, color = muted)
                    com.owl.tracker.ui.components.FitText(
                        amount, style = MaterialTheme.typography.headlineMedium, minScale = 0.6f,
                        color = if (left < 0) sc.overFg else MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("left_total"),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ofText, style = MaterialTheme.typography.bodySmall, color = muted)
                        IconButton(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }, modifier = Modifier.size(40.dp).testTag("edit_budget")) {
                            Icon(Ic.edit, contentDescription = "Edit the monthly budget", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    StatusPill(status.state)
                }
            }
            val need = maxOf(
                com.owl.tracker.ui.components.rememberTextWidth(heading, MaterialTheme.typography.labelLarge),
                com.owl.tracker.ui.components.rememberTextWidth(amount, MaterialTheme.typography.headlineMedium) * 0.7f,
                com.owl.tracker.ui.components.rememberTextWidth(ofText, MaterialTheme.typography.bodySmall) + 40.dp,
            )
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth - 140.dp - 16.dp >= need) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BudgetDrillRing(status.fraction, dash.spent, dash.overall, dash.byCategory, dash.period.start, onOpen)
                        info(Modifier.weight(1f), Alignment.Start)
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BudgetDrillRing(status.fraction, dash.spent, dash.overall, dash.byCategory, dash.period.start, onOpen)
                        info(Modifier.fillMaxWidth(), Alignment.CenterHorizontally)
                    }
                }
            }
            val line = when {
                !dash.isCurrent && status.state == BudgetState.OVER -> "Finished ${Money.formatInr(dash.spent - dash.overall)} over budget"
                !dash.isCurrent -> "Finished ${Money.formatInr(dash.overall - dash.spent)} under budget"
                status.state == BudgetState.OVER -> "${Money.formatInr(dash.spent - dash.overall)} over budget with $daysLeft days left"
                status.state == BudgetState.HEADS_UP && status.projectedPaise > dash.overall -> "At this pace you'll be ${Money.formatInr(status.projectedPaise - dash.overall)} over by month end"
                status.state == BudgetState.HEADS_UP -> "You've used ${(status.fraction * 100).toInt()}% with $daysLeft days left"
                else -> "${Money.formatInr(status.safePerDayPaise)} a day is safe to spend for the next $daysLeft days"
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(sc.bg(status.state)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(if (status.state == BudgetState.ON_TRACK) Ic.check else Ic.alert, contentDescription = null, tint = sc.fg(status.state), modifier = Modifier.size(18.dp))
                Text(line, color = sc.fg(status.state), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("budget_line"))
            }
            // Three small figures, each opening what it's about: spent → this month's payments; a budget that's over →
            // that category (or Budgets); the biggest category when the month is over but no category budget is;
            // what could be saved → Ways to save in Reports
            val overRows = dash.categoryRows.filter { it.status.state == BudgetState.OVER }
            val biggest = dash.byCategory.firstOrNull()
            val monthOver = status.state == BudgetState.OVER
            val figures = buildList {
                add(Figure("Spent", Money.formatInr(dash.spent), brand.spentBg, MaterialTheme.colorScheme.onSurface, com.owl.tracker.R.drawable.glossy_coins,
                    "figure_spent", "See this month's payments") { vm.showActivity(filter = com.owl.tracker.ui.ActivityFilter.SPENT) })
                when {
                    overRows.size == 1 -> add(Figure("Over budget", overRows[0].category.name, sc.overBg, sc.overFg,
                        com.owl.tracker.ui.components.glossyArt(overRows[0].category.icon), "figure_over", "Open ${overRows[0].category.name}") { onOpen(overRows[0].category.id) })
                    overRows.size > 1 -> add(Figure("Over budget", "${overRows.size} categories", sc.overBg, sc.overFg,
                        com.owl.tracker.ui.components.glossyArt(overRows[0].category.icon), "figure_over", "See your budgets") { vm.go(Screen.BUDGETS) })
                    monthOver && biggest != null -> add(Figure("Biggest", biggest.category.name, brand.spentBg, MaterialTheme.colorScheme.onSurface,
                        com.owl.tracker.ui.components.glossyArt(biggest.category.icon), "figure_over", "Open ${biggest.category.name}") { onOpen(biggest.category.id) })
                    else -> add(Figure("Over budget", "None", sc.okBg, sc.okFg, null, "figure_over", "See your budgets") { vm.go(Screen.BUDGETS) })
                }
                val save = plan?.monthlyPaise?.takeIf { it > 0 && dash.isCurrent }
                if (save != null) {
                    add(Figure("Could save", Money.formatInr(save), sc.okBg, sc.okFg, com.owl.tracker.R.drawable.glossy_piggy, "figure_save", "See the ways to save") { vm.openWaysToSave() })
                } else {
                    add(Figure(if (dash.isCurrent) "Days left" else "Days", "${if (dash.isCurrent) daysLeft else dash.period.days}", brand.spentBg,
                        MaterialTheme.colorScheme.onSurface, com.owl.tracker.R.drawable.glossy_emi, "figure_days", null, null))
                }
            }
            Figures(figures)
        }
    }
}

/** One figure under the budget: its label, value, colours, picture, and what a tap opens (null: nothing). */
private class Figure(
    val label: String, val value: String, val bg: Color, val fg: Color, val art: Int?, val tag: String,
    val clickLabel: String?, val onClick: (() -> Unit)?,
)

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
            if (f.onClick != null) Icon(Ic.chevR, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
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
                        com.owl.tracker.R.drawable.glossy_coins, "figure_week_average", null, null),
                    Figure(
                        "Biggest day",
                        if (biggest == null || biggest.paise == 0L) "None yet"
                        else biggest.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + Money.formatShort(biggest.paise),
                        brand.spentBg, MaterialTheme.colorScheme.onSurface, com.owl.tracker.R.drawable.glossy_wallet, "figure_week_biggest", "Show that day",
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
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.owl.tracker.R.drawable.glossy_piggy), contentDescription = null, modifier = Modifier.size(34.dp))
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
    )
}

/**
 * "This month in pictures": a row of category pictures to swipe through, biggest spend first. The pictures (each a
 * small web page) are put in a moment after Home first shows, so Home itself appears without waiting for them.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PicturesRow(pictures: List<Pair<Category, com.owl.tracker.ui.scene.SceneSpec>>, onOpen: (String) -> Unit) {
    val brand = LocalBrand.current
    var live by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); live = true }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.foundation.lazy.LazyRow(
        state = rowState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(rowState),
        modifier = Modifier.fillMaxWidth().testTag("pictures"),
    ) {
        items(pictures, key = { it.first.id }) { (cat, spec) ->
            val shape = RoundedCornerShape(22.dp)
            Box(
                Modifier.width(280.dp)
                    // Leaning back a little, like a card stood up on the table
                    .graphicsLayer {
                        rotationX = 4f
                        cameraDistance = 12f * density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .shadow(12.dp, shape, ambientColor = brand.shadow, spotColor = brand.shadow),
            ) {
                if (live) {
                    CategoryScene(spec, cat.name, caption = false, tag = "picture_${cat.id}", onClick = { onOpen(cat.id) })
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(320f / 180f).clip(shape).background(androidx.compose.ui.graphics.lerp(Color(cat.color), Color.White, 0.86f)))
                }
            }
        }
    }
}

/**
 * The budget circle, split into the categories the money went to (in their colours). Slide a finger
 * round it to see each one in the middle; tap one to open it in "Where it went" just below, or tap
 * the middle for the whole breakdown. Over budget, a red inner ring shows by how much.
 */
@Composable
private fun BudgetDrillRing(fraction: Double, spent: Long, budget: Long, byCategory: List<com.owl.tracker.ui.CategorySpend>, period: LocalDate, onOpen: (String?) -> Unit) {
    val sc = LocalStatusColors.current
    val brand = LocalBrand.current
    val track = sc.track
    val over = sc.overFg
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val parts = byCategory.filter { it.paise > 0 }
    val sum = parts.sumOf { it.paise }.coerceAtLeast(1)
    val filled = fraction.coerceIn(0.0, 1.0).toFloat() * 360f
    // Each category's arc, in order round the circle: start and sweep in degrees from the top
    val arcs = remember(parts, filled) {
        var at = 0f
        parts.map { p -> val sw = filled * p.paise / sum; (at to sw).also { at += sw } }
    }
    var hover by androidx.compose.runtime.remember(period) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val grow = com.owl.tracker.ui.components.growIn(period, 1100)
    val pops = androidx.compose.runtime.key(parts.size, period) {
        parts.indices.map { i ->
            androidx.compose.animation.core.animateFloatAsState(
                if (hover == i) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 520f), label = "seg",
            ).value
        }
    }
    val focus = hover?.let { parts.getOrNull(it) }
    Box(
        Modifier.size(140.dp)
            .pointerInput(arcs) {
                val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val outer = size.width / 2f
                fun partAt(p: androidx.compose.ui.geometry.Offset): Int? {
                    val angle = ((Math.toDegrees(kotlin.math.atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())) + 90 + 360) % 360).toFloat()
                    return arcs.indexOfFirst { (start, sw) -> angle >= start && angle < start + sw }.takeIf { it >= 0 }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val d = kotlin.math.hypot(down.position.x - c.x, down.position.y - c.y)
                    if (d > outer) return@awaitEachGesture
                    val inRing = d > outer * 0.62f
                    hover = if (inRing) partAt(down.position) else null
                    if (hover != null) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            ch.consume()
                            val i = partAt(ch.position)
                            if (i != hover) { hover = i; if (i != null) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
                        }
                    }
                    val last = hover
                    hover = null
                    if (!moved) onOpen(if (inRing) last?.let { parts[it].category.id } else null)
                }
            }
            .semantics {
                contentDescription = "${(fraction * 100).toInt()}% of budget. " + parts.joinToString { "${it.category.name} ${Money.formatInr(it.paise)}" } +
                    ". Tap a part to see where it went."
            }
            .testTag("budget_ring"),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.12f
            val pop = 5.dp.toPx()
            val inset = stroke / 2 + pop
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            val ringStroke = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
            // The dish the ring sits in: shaded at the top, where its rim hides the light
            val dishR = arcSize.width / 2 - stroke / 2
            drawCircle(brand.dish, dishR, center)
            drawCircle(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(brand.clay.shade, Color.Transparent), center.y - dishR, center.y,
                ),
                dishR, center,
            )
            // The ring's shadow on the card, then the ring as a tube: the track, the categories, light on top, shade below
            for (k in 1..3) {
                drawArc(
                    brand.clay.shadow.copy(alpha = 0.06f), 0f, 360f, false, tl + androidx.compose.ui.geometry.Offset(0f, k * 1.5f.dp.toPx()), arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke + k * 2.dp.toPx()),
                )
            }
            drawArc(track, 0f, 360f, false, tl, arcSize, style = ringStroke)
            // The spent part sweeps round, category after category
            val shown = filled * grow
            arcs.forEachIndexed { i, (start, sw) ->
                val visible = (shown - start).coerceIn(0f, sw)
                if (visible <= 0.4f) return@forEachIndexed
                val p = pops.getOrElse(i) { 0f }
                val mid = Math.toRadians((start - 90f + visible / 2).toDouble())
                val shift = androidx.compose.ui.geometry.Offset((kotlin.math.cos(mid) * pop * p).toFloat(), (kotlin.math.sin(mid) * pop * p).toFloat())
                val gap = if (arcs.size > 1) 1.5f else 0f
                drawArc(
                    Color(parts[i].category.color).copy(alpha = if (hover != null && hover != i) 0.35f else 1f),
                    start - 90f + gap / 2, (visible - gap).coerceAtLeast(0.4f), false, tl + shift, arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * (1f + 0.35f * p)),
                )
            }
            drawArc(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.5f), 0.45f to Color.White.copy(alpha = 0.04f), 0.6f to Color.Transparent, 1f to Color(0x473A1C0C),
                    startY = tl.y - stroke / 2, endY = tl.y + arcSize.height + stroke / 2,
                ),
                0f, 360f, false, tl, arcSize, style = ringStroke,
            )
            val rimOuter = androidx.compose.ui.geometry.Size(arcSize.width + stroke, arcSize.height + stroke)
            val rimInner = androidx.compose.ui.geometry.Size(arcSize.width - stroke, arcSize.height - stroke)
            drawArc(Color(0x1F3A1C0C), 0f, 360f, false, tl - androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), rimOuter, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawArc(Color(0x1A3A1C0C), 0f, 360f, false, tl + androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), rimInner, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            // A soft highlight on the upper left, so the ring looks glossy like the pictures
            if (shown > 1f) drawArc(
                Color.White.copy(alpha = 0.35f), -165f, 60f, false, tl + androidx.compose.ui.geometry.Offset(stroke * 0.12f, stroke * 0.12f),
                androidx.compose.ui.geometry.Size(arcSize.width - stroke * 0.24f, arcSize.height - stroke * 0.24f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 0.3f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            if (fraction > 1.0) {
                val s2 = stroke * 0.55f
                val inner = inset + stroke / 2 + s2
                val innerSize = androidx.compose.ui.geometry.Size(size.width - inner * 2, size.height - inner * 2)
                val innerSweep = ((fraction - 1.0).coerceIn(0.0, 1.0) * 360 * grow).toFloat()
                drawArc(over, -90f, innerSweep, false, androidx.compose.ui.geometry.Offset(inner, inner), innerSize, style = androidx.compose.ui.graphics.drawscope.Stroke(s2, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        // The hole is a fixed size: with big text its words grow a little, then get smaller to fit
        com.owl.tracker.ui.components.CapFontScale(1.25f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(84.dp), verticalArrangement = Arrangement.Center) {
                val center = androidx.compose.ui.text.style.TextAlign.Center
                if (focus == null) {
                    com.owl.tracker.ui.components.FitText("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, textAlign = center, minScale = 0.6f)
                    com.owl.tracker.ui.components.FitText("spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = center)
                } else {
                    com.owl.tracker.ui.components.FitText(focus.category.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = center, minScale = 0.75f)
                    com.owl.tracker.ui.components.FitText(Money.formatShort(focus.paise), style = MaterialTheme.typography.titleMedium, textAlign = center, minScale = 0.6f)
                    com.owl.tracker.ui.components.FitText(
                        "${(focus.paise * 100 / budget.coerceAtLeast(1))}% of budget", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = center, maxLines = 2, minScale = 0.75f,
                    )
                }
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

@Composable
fun TxnRow(vm: AppViewModel, t: Txn, category: Category?, showDay: Boolean) {
    val counted = t.isSpending(category)
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
            style = MaterialTheme.typography.titleSmall,
            color = when {
                credit -> MaterialTheme.colorScheme.primary
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
                        if (total > 0) Text("${Money.formatInr(total)} spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    OutlinedButton(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }) { Text(if (dash.overall > 0) "Edit" else "Set") }
                }
                dash.status?.let { s ->
                    Gap(12.dp)
                    PaceBar(s.fraction, pace, sc.ring(s.state, MaterialTheme.colorScheme.primary))
                    Gap(8.dp)
                    StatusPill(s.state)
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
            item { SectionLabel("By category") }
            item {
                Panel(padding = 12.dp) {
                    dash.categoryRows.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        Column(Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.BudgetEdit(r.category.id)) }.padding(vertical = 10.dp)) {
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
                        }
                    }
                }
            }
        }
        item { SectionLabel("No budget yet") }
        item {
            Panel(padding = 12.dp) {
                dash.unbudgeted.forEachIndexed { i, cs ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryTile(cs.category, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(cs.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (cs.paise > 0) "${Money.formatInr(cs.paise)} spent this month" else "Nothing spent yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    return com.owl.tracker.ui.components.RingMoods(
        com.owl.tracker.ui.components.OwlSay(com.owl.tracker.ui.components.OwlMood.of(dash.spent, dash.overall.takeIf { it > 0 }), month, monthLine), cats,
    )
}
