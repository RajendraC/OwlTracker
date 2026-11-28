package com.owl.tracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.Money
import com.owl.tracker.data.CategoryTree
import com.owl.tracker.data.Households
import com.owl.tracker.i18n.tr
import com.owl.tracker.i18n.trn
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.Sheet
import com.owl.tracker.ui.components.OwlMark
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.theme.ClaySurface
import com.owl.tracker.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Each person's colour in the household's bars, in the order they appear (me first). */
val HouseColors = listOf(Color(0xFFF4A21B), Color(0xFF3F8F7F), Color(0xFF7B6CD9), Color(0xFFD45B8A))

private fun monthFmt() = DateTimeFormatter.ofPattern("MMMM", com.owl.tracker.i18n.I18n.locale)
private fun dayFmt() = DateTimeFormatter.ofPattern("d MMM", com.owl.tracker.i18n.I18n.locale)

/** "today", "yesterday", "3 days ago" for an update made at [ms] (0: none yet). */
fun houseWhen(ms: Long, now: Long): String {
    if (ms <= 0) return tr("No update yet")
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate()).toInt()
    return when {
        days <= 0 -> tr("Updated today")
        days == 1 -> tr("Updated yesterday")
        else -> trn(days, "Updated {0} day ago", "Updated {0} days ago")
    }
}

/** A category group's name and its icon key and colour: from this phone, or as the person who spent it named it. */
private data class GroupLabel(val name: String, val icon: String, val color: Long)

private fun groupLabel(tree: CategoryTree, id: String, names: Map<String, String>): GroupLabel =
    tree.byId[id]?.let { GroupLabel(it.name, it.icon, it.color) } ?: GroupLabel(names[id] ?: tr("Other"), "others", 0xFF8A8A8A)

@Composable
private fun GroupIcon(g: GroupLabel) =
    androidx.compose.material3.Icon(com.owl.tracker.ui.theme.Ic.forCategory(g.icon), contentDescription = null, tint = Color(g.color), modifier = Modifier.size(20.dp))

/** One bar split by person, each in their colour. */
@Composable
private fun SplitBar(parts: List<Long>, modifier: Modifier = Modifier, height: Int = 12) {
    val total = parts.sum()
    Row(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (total > 0) parts.forEachIndexed { i, p ->
            if (p > 0) Box(Modifier.weight(p.toFloat()).fillMaxHeight().background(HouseColors[i % HouseColors.size]))
        }
    }
}

@Composable
private fun Dot(i: Int) = Box(Modifier.size(10.dp).clip(CircleShape).background(HouseColors[i % HouseColors.size]))

/** Home: the household's month so far, split by person. */
@Composable
fun HouseHomeCard(vm: AppViewModel, v: AppViewModel.HouseView, modifier: Modifier = Modifier) {
    val sc = LocalStatusColors.current
    val budget = v.h.budgetPaise
    ClaySurface(RoundedCornerShape(26.dp), modifier.fillMaxWidth().testTag("house_card"), elevation = 10.dp) {
        Column(Modifier.clickable(onClickLabel = tr("Open {0}", v.h.name)) { vm.go(com.owl.tracker.ui.Screen.HOUSEHOLD) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🏠", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f)) {
                    Text(v.h.name, style = MaterialTheme.typography.titleMedium)
                    Text(trn(v.people.size, "{0} person · {1}", "{0} people · {1}").replace("{1}", v.period.start.format(monthFmt())),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (budget > 0) {
                    val (label, fg, bg) = when {
                        v.totalPaise > budget -> Triple(tr("Over"), sc.overFg, sc.overBg)
                        v.totalPaise > budget * 0.8 -> Triple(tr("Getting close…"), sc.warnFg, sc.warnBg)
                        else -> Triple(tr("On track"), sc.okFg, sc.okBg)
                    }
                    Text(label, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Money.formatInr(v.totalPaise), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (budget > 0) Text(tr("of {0}", Money.formatInr(budget)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SplitBar(v.people.map { it.totalPaise })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                v.people.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(i)
                        Text("${if (p.me) tr("You") else p.name} ${Money.formatShort(p.totalPaise)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (vm.houseUpdateDue(v.h)) {
                OutlinedButton(onClick = { vm.sendHouseUpdate() }, modifier = Modifier.fillMaxWidth().testTag("house_card_send")) { Text(tr("Send this week's update")) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HouseholdScreen(vm: AppViewModel) {
    val v by vm.houseView.collectAsStateWithLifecycle()
    val h by vm.household.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val tree = remember(cats) { CategoryTree(cats) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.openHouseFile(uri) }
    var leaving by remember { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    var payments by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("house_screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val view = v
        if (h == null || view == null) {
            ScreenHeader(tr("Shared household"), tr("Up to four people, each on their own phone"), onBack = { vm.back() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwlMark(56.dp)
                Text(tr("See what your home spends together. Each of you keeps using Owl Tracker on your own phone, and you swap a small update file on WhatsApp, Quick Share or any app. No account, no internet, no server."),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Button(onClick = { vm.openSheet(Sheet.HouseSetup) }, modifier = Modifier.fillMaxWidth().testTag("house_start")) { Text(tr("Start a household")) }
            OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().testTag("house_open_invite")) { Text(tr("Open an invite I was sent")) }
            Text(tr("Only totals by category are shared unless you choose to share every payment. Card and account numbers, messages and UPI IDs never leave your phone."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val house = view.h
        ScreenHeader("🏠 " + house.name, trn(view.people.size, "{0} person · {1}", "{0} people · {1}").replace("{1}", view.period.start.format(monthFmt())), onBack = { vm.back() })

        // The month so far
        ClaySurface(RoundedCornerShape(26.dp), Modifier.fillMaxWidth(), elevation = 10.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Spent together since {0}", view.period.start.format(dayFmt())), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Money.formatInr(view.totalPaise), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (house.budgetPaise > 0) Text(tr("of {0}", Money.formatInr(house.budgetPaise)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SplitBar(view.people.map { it.totalPaise }, height = 14)
                if (house.budgetPaise > 0) {
                    val left = house.budgetPaise - view.totalPaise
                    Text(if (left >= 0) tr("{0} left.", Money.formatInr(left)) else tr("{0} over.", Money.formatInr(-left)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Each person
        Text(tr("Who spent what"), style = MaterialTheme.typography.titleMedium)
        view.people.forEachIndexed { i, p ->
            ClaySurface(RoundedCornerShape(20.dp), Modifier.fillMaxWidth(), elevation = 6.dp) {
                Column(Modifier.testTag("house_person_$i").clickable { shown = if (shown == p.id) null else p.id; payments = false }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Dot(i)
                        Column(Modifier.weight(1f)) {
                            Text(if (p.me) tr("{0} (you)", p.name) else p.name, style = MaterialTheme.typography.titleSmall)
                            Text(if (p.me) (if (house.lastSentAt > 0) tr("Last sent: {0}", Instant.ofEpochMilli(house.lastSentAt).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFmt())) else tr("You haven't sent an update yet"))
                                else houseWhen(p.updatedAt, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.formatInr(p.totalPaise), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (shown == p.id) {
                        p.byGroup.toList().sortedByDescending { it.second }.forEach { (g, paise) ->
                            val label = groupLabel(tree, g, p.names)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GroupIcon(label)
                                Text(label.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(Money.formatInr(paise), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (p.byGroup.isEmpty()) Text(tr("Nothing spent this month yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.sharesAll) {
                            TextButton(onClick = { payments = !payments }, modifier = Modifier.testTag("house_payments_$i")) { Text(if (payments) tr("Hide payments") else tr("See every payment")) }
                            if (payments) vm.housePayments(p.id).take(60).forEach { pay ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(pay.date.format(dayFmt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(pay.merchant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(Money.formatInr(pay.paise), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else if (!p.me) Text(tr("{0} shares totals by category only.", p.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!p.me) TextButton(onClick = { vm.removeHouseMember(p.id) }, modifier = Modifier.testTag("house_remove_$i")) { Text(tr("Remove {0} from this phone", p.name)) }
                    }
                }
            }
        }

        // By category, split by person
        if (view.byGroup.isNotEmpty()) {
            Text(tr("Where it went"), style = MaterialTheme.typography.titleMedium)
            ClaySurface(RoundedCornerShape(20.dp), Modifier.fillMaxWidth(), elevation = 6.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    view.byGroup.take(12).forEach { (g, paise) ->
                        val names = view.people.fold(emptyMap<String, String>()) { a, p -> a + p.names }
                        val label = groupLabel(tree, g, names)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GroupIcon(label)
                                Text(label.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(Money.formatInr(paise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            SplitBar(view.people.map { it.byGroup[g] ?: 0 }, height = 8)
                        }
                    }
                }
            }
        }

        // Send and receive
        ClaySurface(RoundedCornerShape(20.dp), Modifier.fillMaxWidth(), elevation = 6.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Keep each other up to date"), style = MaterialTheme.typography.titleSmall)
                Text(tr("Send your update once a week, or whenever you like. When someone sends you theirs, open the file and pick Owl Tracker, or tap Open an update."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { vm.sendHouseUpdate() }, modifier = Modifier.fillMaxWidth().testTag("house_send")) { Text(tr("Send my update")) }
                OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().testTag("house_open")) { Text(tr("Open an update")) }
                if (view.people.size < Households.MAX_PEOPLE) TextButton(onClick = { vm.shareInvite() }, modifier = Modifier.testTag("house_invite")) {
                    Text(tr("Invite someone ({0} of {1})", view.people.size.toString(), Households.MAX_PEOPLE.toString()))
                }
            }
        }

        // Settings
        ClaySurface(RoundedCornerShape(20.dp), Modifier.fillMaxWidth(), elevation = 6.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("What the others see"), style = MaterialTheme.typography.titleSmall)
                ShareChoice(house.shareAll) { vm.setHouseShareAll(it) }
                var budget by rememberSaveable(house.budgetPaise) { mutableStateOf(if (house.budgetPaise > 0) (house.budgetPaise / 100).toString() else "") }
                Text(tr("Household budget"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(budget, { t -> budget = t.filter(Char::isDigit).take(9) }, label = { Text(tr("Monthly, for everyone (₹)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).testTag("house_budget"))
                    val paise = (budget.toLongOrNull() ?: 0) * 100
                    TextButton(onClick = { vm.setHouseBudget(paise) }, enabled = paise != house.budgetPaise, modifier = Modifier.testTag("house_budget_save")) { Text(tr("Save")) }
                }
            }
        }

        Text(tr("Mark money you send each other as a Transfer, so it isn't counted twice."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr("The household's spending is kept only on this phone, not in your backups. Update files are locked with your household's own key."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { leaving = true }, modifier = Modifier.testTag("house_leave")) { Text(tr("Leave {0}", house.name), color = MaterialTheme.colorScheme.error) }
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text(tr("Leave {0}?", h?.name ?: "")) },
        text = { Text(tr("Everyone's spending from this household is removed from this phone. Your own payments stay. To join again, you'll need a new invite.")) },
        confirmButton = { TextButton(onClick = { leaving = false; vm.leaveHousehold() }, modifier = Modifier.testTag("house_leave_confirm")) { Text(tr("Leave")) } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text(tr("Cancel")) } },
    )
}

/** Totals by category (the default) or every payment. */
@Composable
private fun ShareChoice(all: Boolean, onPick: (Boolean) -> Unit) {
    listOf(false to (tr("Totals by category") to tr("Food ₹8,200, Groceries ₹6,100… No shops or dates.")), true to (tr("Every payment") to tr("Shop, amount, date and category of each payment.")))
        .forEach { (value, words) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(all == value, role = Role.RadioButton) { onPick(value) }.padding(vertical = 4.dp)
                    .testTag(if (value) "house_share_all" else "house_share_totals"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(all == value, onClick = null, modifier = Modifier.padding(end = 8.dp))
                Column {
                    Text(words.first, style = MaterialTheme.typography.bodyMedium)
                    Text(words.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
}

/** Starting a household, or joining the one an opened invite is for. */
@Composable
fun HouseSetupSheet(vm: AppViewModel) {
    val invite by vm.pendingInvite.collectAsStateWithLifecycle()
    var me by rememberSaveable { mutableStateOf("") }
    var home by rememberSaveable { mutableStateOf(tr("Our home")) }
    var all by rememberSaveable { mutableStateOf(false) }
    var budget by rememberSaveable { mutableStateOf("") }
    val inv = invite
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OwlMark(48.dp)
        Text(if (inv != null) tr("Join {0}", inv.name) else tr("Start a household"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
    Text(
        if (inv != null) tr("{0} invited you. Your spending stays on your phone; you'll send them an update file when you choose.", inv.fromName)
        else tr("You'll send an invite file to the others (up to three more people). They open it in Owl Tracker on their phone."),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(me, { me = it.take(20) }, label = { Text(tr("Your name, as the others see it")) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("house_me"))
    if (inv == null) {
        OutlinedTextField(home, { home = it.take(30) }, label = { Text(tr("Household name")) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("house_name"))
        OutlinedTextField(budget, { t -> budget = t.filter(Char::isDigit).take(9) }, label = { Text(tr("Monthly budget for everyone (₹, optional)")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("house_setup_budget"))
    }
    Text(tr("What the others see"), style = MaterialTheme.typography.titleSmall)
    ShareChoice(all) { all = it }
    Button(
        onClick = {
            if (inv != null) vm.joinHousehold(me, all) else vm.startHousehold(home, me, all, (budget.toLongOrNull() ?: 0) * 100)
        },
        enabled = me.isNotBlank() && (inv != null || home.isNotBlank()),
        modifier = Modifier.fillMaxWidth().testTag("house_setup_go"),
    ) { Text(if (inv != null) tr("Join and send my first update") else tr("Start and send the invite")) }
    Text(tr("Card and account numbers, messages and UPI IDs never leave your phone."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
