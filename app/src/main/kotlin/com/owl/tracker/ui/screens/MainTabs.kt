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
import androidx.compose.foundation.layout.fillMaxSize
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
import com.owl.tracker.ui.components.Divider
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

@Composable
fun HomeScreen(vm: AppViewModel) {
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val canForward by vm.canGoForward.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val store by vm.store.collectAsStateWithLifecycle()
    val plan by vm.savingPlan.collectAsStateWithLifecycle()
    val txns by vm.periodTxns.collectAsStateWithLifecycle()
    val dash = d ?: return
    val catMap = cats.associateBy { it.id }
    val sc = LocalStatusColors.current
    val side = Modifier.padding(horizontal = 16.dp)

    // Drilling down on Home: a category, then one of its merchants (back up with the arrow or the phone's Back)
    var openCat by androidx.compose.runtime.saveable.rememberSaveable(dash.period.start.toString()) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var openMerchant by androidx.compose.runtime.saveable.rememberSaveable(dash.period.start.toString()) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = openCat != null) { if (openMerchant != null) openMerchant = null else openCat = null }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val showPictures = dash.byCategory.isNotEmpty()
    // Where "Where it went" sits in the list, to scroll to it from the budget circle and the pictures
    val whereIndex = 1 + (if (access is Access.Trial || access == Access.TrialEnded) 1 else 0) +
        (if (settings?.sampleData == true) 1 else 0) + (if (settings?.smsEnabled == false && settings?.sampleData != true) 1 else 0) +
        dash.over.size + (if (showPictures) 1 else 0)
    fun openCategory(category: String?) {
        if (category != null) { openCat = category; openMerchant = null }
        scope.launch { listState.animateScrollToItem(whereIndex) }
    }
    val spending = remember(txns, catMap) { txns.filter { vm.counts(it, catMap) } }
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
        txns.filter { it.categoryId == openCat && it.merchantKey == openMerchant }.sortedByDescending { it.occurredAt }
    val budgetOf = dash.categoryRows.associateBy { it.category.id }
    fun sceneFor(cat: Category, spent: Long) = Scenes.forCategory(
        cat, spent, budgetOf[cat.id]?.limit, dash.overall, dash.spent, merchantsOf[cat.id].orEmpty().map { it.name to it.paise },
    )

    LazyColumn(Modifier.fillMaxSize().testTag("home"), state = listState, contentPadding = PaddingValues(bottom = 44.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The mango header, with the budget card resting on its lower edge
        item(key = "top") {
            Column {
                HomeHeader(vm, dash, canForward)
                BudgetCard(vm, dash, plan, ::openCategory, Modifier.padding(horizontal = 16.dp).pullUp(60.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.showActivity(o.categoryId) }, colors = ButtonDefaults.buttonColors(containerColor = sc.overFg, contentColor = sc.overBg)) { Text("See where it went") }
                    OutlinedButton(onClick = { vm.openSheet(Sheet.BudgetEdit(o.budgetKey)) }) { Text("Adjust budget", color = sc.overFg) }
                }
            }
        }

        // A picture for each category the money went to; tapping one opens it in "Where it went" below
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
                if (dash.byCategory.isEmpty()) {
                    Panel {
                        Text("Where it went", style = MaterialTheme.typography.titleMedium)
                        Text("Nothing spent yet this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    com.owl.tracker.ui.components.DrillRingCard(
                        categories = dash.byCategory.map { cs ->
                            val row = budgetOf[cs.category.id]
                            val pct = (cs.paise * 100 / dash.spent.coerceAtLeast(1)).toInt()
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
                        sweepKey = dash.period.start,
                        appIcons = vm.appIcons,
                        catMap = catMap,
                        dayText = { t -> dayLabel(vm, vm.dateOf(t.occurredAt)) },
                        onCategory = { id -> openCat = if (openCat == id) null else id; openMerchant = null },
                        onMerchant = { k -> openMerchant = if (openMerchant == k) null else k },
                        onPayment = { t -> vm.openSheet(Sheet.TxnDetail(t.id)) },
                        headerAction = { TextButton(onClick = { vm.showActivity() }) { Text("See all") } },
                        scene = { id ->
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

        plan?.takeIf { dash.isCurrent && it.monthlyPaise > 0 && !vm.tipsHidden(settings) }?.let { p -> item(key = "saving_nudge") { Box(side) { SavingNudge(vm, p) } } }

        item {
            Column(side) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() })
                    TextButton(onClick = { vm.showActivity() }) { Text("See all") }
                }
                Panel(padding = 8.dp) {
                    if (dash.recent.isEmpty()) Text("No transactions yet. Tap + to add one.", modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    dash.recent.forEachIndexed { i, t ->
                        if (i > 0) Divider()
                        TxnRow(vm, t, catMap[t.categoryId], showDay = true)
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
private fun HomeHeader(vm: AppViewModel, dash: com.owl.tracker.ui.Dashboard, canForward: Boolean) {
    val brand = LocalBrand.current
    val dark = com.owl.tracker.ui.theme.LocalDarkTheme.current
    val monthName = dash.period.start.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
    val title = when {
        !dash.isCurrent -> "Looking back"
        dash.period.start.dayOfMonth == 1 -> "$monthName so far"
        else -> "This month so far"
    }
    Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(brand.header))) {
        // A soft sun in the corner
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val c = androidx.compose.ui.geometry.Offset(size.width - 24.dp.toPx(), 10.dp.toPx())
            val r = 150.dp.toPx()
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.75f), Color.White.copy(alpha = 0f)), c, r),
                r, c,
            )
        }
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 76.dp)) {
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
                    Text(
                        title, style = MaterialTheme.typography.headlineSmall, color = brand.onHeader, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(brand.headerButton).clickable(onClickLabel = "Open Reports") { vm.go(Screen.INSIGHTS) }
                        .semantics { contentDescription = "Reports" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.chart, contentDescription = null, tint = brand.onHeader, modifier = Modifier.size(22.dp))
                }
            }
            Gap(12.dp)
            // The month: back to earlier ones, and forward again
            Row(Modifier.clip(RoundedCornerShape(24.dp)).background(brand.headerButton), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.previousMonth() }, modifier = Modifier.size(40.dp)) {
                    Icon(Ic.chevL, contentDescription = "Previous month", tint = brand.onHeader, modifier = Modifier.size(20.dp))
                }
                Text(
                    if (dash.isCurrent) "${dash.label} · day ${dash.dayNumber} of ${dash.period.days}" else dash.label,
                    style = MaterialTheme.typography.labelLarge, color = brand.onHeader,
                )
                IconButton(onClick = { vm.nextMonth() }, enabled = canForward, modifier = Modifier.size(40.dp)) {
                    Icon(Ic.chevR, contentDescription = "Next month", tint = brand.onHeader.copy(alpha = if (canForward) 1f else 0.3f), modifier = Modifier.size(20.dp))
                }
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
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth().testTag("budget_hero"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (status == null) {
                Column {
                    Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = muted)
                    Text(Money.formatInr(dash.spent), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("spent_total"))
                    Text(if (dash.isCurrent) "Day ${dash.dayNumber} of ${dash.period.days}" else dash.label, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Button(onClick = { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }, modifier = Modifier.fillMaxWidth()) { Text("Set a monthly budget") }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BudgetDrillRing(status.fraction, dash.spent, dash.overall, dash.byCategory, dash.period.start, onOpen)
                Column(Modifier.weight(1f)) {
                    val left = dash.overall - dash.spent
                    Text(
                        when { left < 0 -> "Over budget by"; dash.isCurrent -> "Left this month"; else -> "Left over" },
                        style = MaterialTheme.typography.labelLarge, color = muted,
                    )
                    val amount = Money.formatInr(kotlin.math.abs(left))
                    Text(
                        amount, style = if (amount.length > 8) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        color = if (left < 0) sc.overFg else MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("left_total"),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("of ${Money.formatInr(dash.overall)}", style = MaterialTheme.typography.bodySmall, color = muted)
                        Text(
                            "Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(10.dp))
                                .clickable(onClickLabel = "Edit the monthly budget") { vm.openSheet(Sheet.BudgetEdit(Schema.OVERALL)) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                    }
                    StatusPill(status.state, Modifier.padding(top = 4.dp))
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
            // Three small figures: spent, which budget is over, and what could be saved
            val overRows = dash.categoryRows.filter { it.status.state == BudgetState.OVER }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Spent", Money.formatInr(dash.spent), brand.spentBg, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), valueTag = "spent_total")
                StatPill(
                    "Over budget",
                    when (overRows.size) { 0 -> "None"; 1 -> overRows[0].category.name; else -> "${overRows.size} budgets" },
                    if (overRows.isEmpty()) sc.okBg else sc.overBg, if (overRows.isEmpty()) sc.okFg else sc.overFg, Modifier.weight(1f),
                    onClick = overRows.firstOrNull()?.let { r -> { onOpen(r.category.id) } },
                )
                val save = plan?.monthlyPaise?.takeIf { it > 0 && dash.isCurrent }
                if (save != null) {
                    StatPill("Could save", Money.formatInr(save), sc.okBg, sc.okFg, Modifier.weight(1f), onClick = { vm.go(Screen.INSIGHTS) })
                } else {
                    StatPill(if (dash.isCurrent) "Days left" else "Days", "${if (dash.isCurrent) daysLeft else dash.period.days}", brand.spentBg, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, bg: Color, fg: Color, modifier: Modifier = Modifier, valueTag: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier,
        )
    }
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(rowState),
        modifier = Modifier.fillMaxWidth().testTag("pictures"),
    ) {
        items(pictures, key = { it.first.id }) { (cat, spec) ->
            val shape = RoundedCornerShape(22.dp)
            Box(Modifier.width(280.dp).shadow(6.dp, shape, ambientColor = brand.shadow, spotColor = brand.shadow)) {
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
            val stroke = size.minDimension * 0.095f
            val pop = 5.dp.toPx()
            val inset = stroke / 2 + pop
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(84.dp), verticalArrangement = Arrangement.Center) {
            if (focus == null) {
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall)
                Text("spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(focus.category.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(Money.formatShort(focus.paise), style = MaterialTheme.typography.titleMedium)
                Text("${(focus.paise * 100 / budget.coerceAtLeast(1))}% of budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
        TextButton(onClick = { vm.go(Screen.INSIGHTS) }, modifier = Modifier.testTag("saving_more")) {
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
            Text(
                "${category?.name ?: "Others"} · $whenText" + when {
                    t.excluded -> " · not counted"
                    credit -> " · money in"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CategoryTile(r.category, 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(r.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${Money.formatInr(r.spent)} of ${Money.formatInr(r.limit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(r.status.state)
                            }
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
                            Text(cs.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
