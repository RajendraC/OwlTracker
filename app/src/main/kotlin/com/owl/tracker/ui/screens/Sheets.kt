package com.owl.tracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.data.Schema
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.DeleteOption
import com.owl.tracker.ui.Sheet
import com.owl.tracker.ui.UiMessage
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.components.Divider
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.InfoNote
import com.owl.tracker.ui.components.MerchantAvatar
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.components.PictureIcons
import com.owl.tracker.ui.components.PictureCropDialog
import com.owl.tracker.ui.components.SwitchRow
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalStatusColors
import com.owl.tracker.core.Cat
import com.owl.tracker.core.Direction
import com.owl.tracker.core.Institutions
import com.owl.tracker.core.Money
import com.owl.tracker.core.RejectReason
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(vm: AppViewModel) {
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val current = sheet ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { vm.closeSheet() }, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (current) {
                is Sheet.TxnDetail -> TxnDetailSheet(vm, current.id)
                is Sheet.CategoryPick -> CategoryPickSheet(vm, current.txId)
                is Sheet.QuickCheck -> QuickCheckSheet(vm, current.txId)
                Sheet.Afford -> AffordSheet(vm)
                is Sheet.GoalEdit -> GoalEditSheet(vm, current.id)
                is Sheet.GoalAdd -> GoalAddSheet(vm, current.id, current.suggest)
                is Sheet.Split -> SplitSheet(vm, current.txId)
                is Sheet.Rename -> RenameSheet(vm, current.txId)
                is Sheet.MerchantIcon -> MerchantIconSheet(vm, current.txId)
                is Sheet.AccountPick -> AccountPickSheet(vm, current.txId)
                Sheet.ShareReport -> ShareReportSheet(vm)
                is Sheet.BudgetEdit -> BudgetEditSheet(vm, current.key)
                is Sheet.CategoryEdit -> CategoryEditSheet(vm, current.id, current.inGroup)
                Sheet.AddExpense -> AddExpenseSheet(vm)
                Sheet.DeleteData -> DeleteSheet(vm)
                Sheet.Skipped -> SkippedSheet(vm)
                Sheet.OverBudget -> OverBudgetSheet(vm)
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- transaction ----------------

@Composable
private fun TxnDetailSheet(vm: AppViewModel, id: Long) {
    val txFlow = remember(id) { vm.txnFlow(id) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val cats by vm.categories.collectAsStateWithLifecycle()
    val tx = t ?: return
    val cat = cats.firstOrNull { it.id == tx.categoryId }
    val sc = LocalStatusColors.current
    var showSms by rememberSaveable(id) { mutableStateOf(false) }
    var note by rememberSaveable(id) { mutableStateOf(tx.note ?: "") }
    val credit = tx.direction == Direction.CREDIT
    // In a group but not sorted into one of its categories yet (an Amazon order)
    val waiting = cats.any { it.parent == tx.categoryId && !it.archived }
    val settings by vm.settings.collectAsStateWithLifecycle()

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MerchantAvatar(vm.appIcons, tx.merchantPackage, tx.merchantName, cat, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(tx.merchantName, style = MaterialTheme.typography.titleLarge)
            Text(
                if (tx.isSplit) "Split: " + tx.parts.joinToString(" + ") { vm.categoryName(it.categoryId) } else cat?.name ?: "Others",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.IconButton(
            onClick = { vm.openSheet(Sheet.Rename(tx.id)) },
            modifier = Modifier.testTag("rename_txn"),
        ) { Icon(Ic.edit, contentDescription = "Rename ${tx.merchantName}") }
    }
    Column {
        com.owl.tracker.ui.components.FitText(
            (if (credit) "+" else "−") + Money.formatInr(tx.amountPaise, showPaise = tx.amountPaise % 100 != 0L),
            style = MaterialTheme.typography.headlineLarge, minScale = 0.6f,
            color = if (credit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("detail_amount"),
        )
        val pickable = tx.source == com.owl.tracker.data.Source.SMS
        Row(
            Modifier.then(
                if (pickable) Modifier.clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Choose the account") { vm.openSheet(Sheet.AccountPick(tx.id)) }.testTag("choose_account")
                else Modifier,
            ),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The bank's own app icon beside "Paid from", when that app is on the phone
            val bank = remember(tx.senderGroup) { vm.appIcons.bankApp(tx.senderGroup) }
            if (tx.source == com.owl.tracker.data.Source.SMS && bank != null) {
                MerchantAvatar(vm.appIcons, bank, tx.accountLabel ?: "", null, 20.dp)
            }
            Text(
                when {
                    tx.source == com.owl.tracker.data.Source.BACKUP -> "Restored from a backup"
                    tx.source == com.owl.tracker.data.Source.MANUAL && tx.accountLabel == null -> "Added by you"
                    else -> (if (credit) "Credited to " else "Paid from ") + (tx.accountLabel ?: "an account")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false).testTag("paid_from"),
            )
            // The SMS didn't say which card or account: ask, plainly
            if (pickable) Text(
                if (tx.accountLabel == null) "Which one?" else "Change",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
        // A card or account named by the SMS: what it is, as on Paid by, and a way to correct it
        val label = tx.accountLabel
        if (label != null && tx.source == com.owl.tracker.data.Source.SMS && !credit) {
            val period by vm.periodTxns.collectAsStateWithLifecycle()
            val same = remember(period, tx) { (period.filter { it.accountLabel == label } + tx).distinctBy { it.id } }
            val guessed = remember(same) { com.owl.tracker.ui.PaidBy.kindOf(label, same, emptyMap()) }
            val current = com.owl.tracker.ui.PaidKind.fromKey(settings?.paidKinds?.get(label)) ?: guessed
            PaidKindChoice(vm, label, current, guessed)
        }
    }

    if (tx.needsReview) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(sc.warnBg).padding(14.dp)) {
            Text("We weren't sure about this one", style = MaterialTheme.typography.titleSmall, color = sc.warnFg)
            Text("The message didn't fully match a known format. Check the details and category, then confirm.", style = MaterialTheme.typography.bodySmall, color = sc.warnFg)
            Gap(8.dp)
            Button(onClick = { vm.confirmReview(tx.id) }, colors = ButtonDefaults.buttonColors(containerColor = sc.warnFg, contentColor = sc.warnBg)) { Text("Looks right") }
        }
    }

    Panel(padding = 12.dp) {
        DetailRow("Date", vm.dateOf(tx.occurredAt).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)) + ", " +
            java.time.Instant.ofEpochMilli(tx.occurredAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)))
        Divider()
        if (tx.isSplit) {
            Column(
                Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.Split(tx.id)) }.padding(vertical = 10.dp).testTag("split_parts"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Split", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                tx.parts.forEach { p ->
                    val pc = cats.firstOrNull { it.id == p.categoryId }
                    com.owl.tracker.ui.components.SideOrStacked(
                        Modifier.fillMaxWidth(),
                        start = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(pc, 28.dp)
                                Text(pc?.name ?: "Others", style = MaterialTheme.typography.titleSmall)
                            }
                        },
                        end = { Text(Money.formatInr(p.paise, showPaise = p.paise % 100 != 0L), style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
        } else {
            // Side by side when there's room; with big text the category goes under its label
            com.owl.tracker.ui.components.SideOrStacked(
                Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.CategoryPick(tx.id)) }.padding(vertical = 10.dp).testTag("change_category"),
                start = { Text("Category", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                end = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryTile(cat, 28.dp)
                        Text(
                            (cat?.name ?: "Others") + if (waiting) " · not sorted" else "",
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(if (waiting) "Sort" else "Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
            // An academy or classes could be for you or your children: one tap, remembered for the place
            val either = if (credit || settings?.kids == false) null else com.owl.tracker.core.CategoryGroups.forYouOrKids(tx.merchantName, tx.categoryId)
            if (either != null) ForYouOrKids(vm, tx, either.first, either.second, cats)
        }
        if (!credit && !tx.isSplit) {
            Divider()
            // One order, more than one kind of thing: split it across categories (a split one changes from its Split heading)
            Row(
                Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.Split(tx.id)) }.padding(vertical = 10.dp).testTag("split_open"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Split across categories", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Split", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Divider()
        com.owl.tracker.ui.components.SideOrStacked(
            Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.MerchantIcon(tx.id)) }.padding(vertical = 10.dp).testTag("change_icon"),
            start = { Text("Icon", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            end = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MerchantAvatar(vm.appIcons, tx.merchantPackage, tx.merchantName, cat, 28.dp)
                    Text(
                        when {
                            tx.merchantPackage != null && vm.appIcons.get(tx.merchantPackage) != null -> "App icon"
                            tx.merchantPackage != null -> "App not installed"
                            else -> "Category icon"
                        },
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            },
        )
        tx.refNo?.let { Divider(); DetailRow("Reference", it) }
        tx.sender?.let { Divider(); DetailRow("From", it) }
        Divider()
        if (credit) {
            // Money in is shown, never spent: it can count as income on Income & expenses, or be left out.
            val counts = cat == null || cat.kind == com.owl.tracker.data.CategoryKind.INCOME || cat.kind == com.owl.tracker.data.CategoryKind.EXPENSE
            // A card bill payment: money into the card from your own account, never income
            val cardBill = tx.kind == com.owl.tracker.core.TxKind.CARD_BILL_PAYMENT || com.owl.tracker.ui.CardBills.saysPayment(tx.smsBody)
            SwitchRow(
                "Count as income",
                when {
                    !counts && cardBill -> "A credit card bill payment, so never income. It's your own money paying the card."
                    !counts -> "${cat?.name ?: "Transfers"} aren't income. To count it, change the category to an income one."
                    tx.excluded -> "Left out of Income & expenses. Never counts as spending."
                    else -> "Counts as income in Reports. Never counts as spending."
                },
                tx.isIncome(cat),
                { on -> if (counts) vm.setExcluded(tx.id, !on) },
                modifier = Modifier.testTag("count_money_in"),
                enabled = counts,
            )
        } else {
            SwitchRow(
                "Count in spend",
                if (cat?.countsAsSpend == false) "Transfers and income never count" else if (tx.excluded) "Left out of your budget" else "Counts toward your budget",
                tx.isSpending(cat),
                { on -> if (cat?.countsAsSpend == false) Unit else vm.setExcluded(tx.id, !on) },
                enabled = cat?.countsAsSpend != false,
            )
        }
    }

    OutlinedTextField(
        value = note, onValueChange = { note = it },
        label = { Text("Note") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { if (note != (tx.note ?: "")) TextButton(onClick = { vm.setNote(tx.id, note) }) { Text("Save") } },
    )

    tx.smsBody?.let { body ->
        TextButton(onClick = { showSms = !showSms }) {
            Icon(Ic.message, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (showSms) "  Hide original SMS" else "  View original SMS")
        }
        if (showSms) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)) {
                Text("From ${tx.sender ?: "bank"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(4.dp)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    OutlinedButton(
        onClick = { vm.deleteTxn(tx.id) },
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("delete_txn"),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(Ic.trash, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Delete transaction")
    }
    Text(
        "It won't be imported again. Your SMS inbox isn't changed.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Who was this for?": [mine] if it was for you, [kids] if for your children. The answer goes for the place from now on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForYouOrKids(vm: AppViewModel, tx: com.owl.tracker.data.Txn, mine: String, kids: String, cats: List<com.owl.tracker.data.Category>) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(12.dp).testTag("for_you_or_kids"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Who was this for?", style = MaterialTheme.typography.titleSmall)
        Text(
            "${tx.merchantName} could be for you or your children. Your answer goes for every payment to it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(mine, "For me", "for_me"), Triple(kids, "For my kids", "for_kids")).forEach { (id, label, tag) ->
                FilterChip(
                    selected = tx.categoryId == id, onClick = { if (tx.categoryId != id) vm.setCategory(tx.id, id, true) },
                    label = { Text("$label · ${cats.firstOrNull { it.id == id }?.name ?: ""}") },
                    modifier = Modifier.testTag(tag),
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    // Side by side when they fit, else the value under its label (never "D / a / t / e")
    com.owl.tracker.ui.components.SideOrStacked(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        start = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        end = { Text(value, style = MaterialTheme.typography.titleSmall) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickSheet(vm: AppViewModel, txId: Long) {
    val txFlow = remember(txId) { vm.txnFlow(txId) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val cats by vm.categories.collectAsStateWithLifecycle()
    val countFlow = remember(txId) { vm.sameMerchantCount(txId) }
    val count by countFlow.collectAsStateWithLifecycle(initialValue = 1)
    // The category picked, waiting for "just this one or all?"
    var pending by rememberSaveable(txId) { mutableStateOf<String?>(null) }
    val tx = t ?: return
    // Stores that sell everything ("WWW AMAZON IN" says nothing about what was bought): just this one is usually right
    val mixed = com.owl.tracker.core.MixedStores.isMixed(tx.merchantKey)
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { vm.openSheet(Sheet.TxnDetail(txId)) }) { Icon(Ic.back, contentDescription = "Back to transaction") }
        SheetTitle("Choose category")
    }
    if (tx.isSplit) {
        InfoNote("This payment is split across ${tx.parts.size} categories. Picking one category joins it back together.", icon = Ic.info)
    }
    if (mixed) {
        Box(Modifier.testTag("mixed_store_note")) {
            InfoNote(
                "${tx.merchantName} sells all kinds of things, and its SMS doesn't say what was bought, so changing just this payment is usually right.",
                icon = Ic.info, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    pending?.let { id ->
        val name = cats.firstOrNull { it.id == id }?.name ?: ""
        if (count > 1) MoveDialog(vm, tx, id, count, cats, onDone = { pending = null })
        else OneOrAllDialog(
            title = "Move to $name",
            text = "Just this payment, or new payments from ${tx.merchantName} too?",
            allLabel = "This and new ones",
            onOne = { pending = null; vm.setCategory(txId, id, false) },
            onAll = { pending = null; vm.setCategory(txId, id, true) },
            onDismiss = { pending = null },
            tag = "apply",
        )
    }
    // Money in can go to Income (listed first); money out never can.
    val credit = tx.direction == com.owl.tracker.core.Direction.CREDIT
    val tree = remember(cats) { com.owl.tracker.data.CategoryTree(cats) }
    // In a group but not sorted into one of its categories yet (an Amazon order): that group's categories come first
    val waiting = tree.isGroup(tx.categoryId)
    val sections = remember(tree, credit, tx.categoryId) { pickSections(tree, credit, tx.categoryId) }
    val nameStyle = MaterialTheme.typography.labelMedium
    sections.forEach { (title, choices) ->
        if (title != null) {
            Text(
                title, style = MaterialTheme.typography.titleSmall,
                color = if (waiting && choices === sections.first().second) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp).semantics { heading() },
            )
        }
        com.owl.tracker.ui.components.CellGrid(choices.size, 100.dp, choices.map { it.name }, nameStyle, 8.dp, Modifier.fillMaxWidth()) { i, w ->
            run {
                val c = choices[i]
                val on = c.id == tx.categoryId
                Column(
                    Modifier.width(w).heightIn(min = 92.dp).clip(RoundedCornerShape(16.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) Color(c.color) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .selectable(selected = on, role = Role.RadioButton) { pending = c.id }
                        .padding(8.dp)
                        .testTag("pick_${c.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CategoryTile(c)
                    com.owl.tracker.ui.components.FitText(c.name, style = nameStyle, maxLines = 3, minScale = 0.85f, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/**
 * The categories a payment can be put in, a section a group: for a payment waiting in a group, that group's first
 * ("Shopping: what was it?"); then the others group by group, and last the ones on their own (Income first for money in).
 */
private fun pickSections(tree: com.owl.tracker.data.CategoryTree, credit: Boolean, current: String): List<Pair<String?, List<com.owl.tracker.data.Category>>> {
    val ok = tree.pickable().filter { credit || it.id != Cat.INCOME }.map { it.id }.toSet()
    val tops = tree.all.filter { it.parent == null && !it.archived }
    val waitingIn = current.takeIf { tree.isGroup(it) }
    return buildList {
        if (credit) tops.firstOrNull { it.id == Cat.INCOME }?.let { add(null to listOf(it)) }
        waitingIn?.let { g -> add("${tree.byId[g]?.name}: what was it?" to tree.children(g).filter { it.id in ok }) }
        tops.filter { tree.isGroup(it.id) && it.id != waitingIn }.forEach { g ->
            tree.children(g.id).filter { it.id in ok }.takeIf { it.isNotEmpty() }?.let { add(g.name to it) }
        }
        tops.filter { !tree.isGroup(it.id) && it.id in ok && !(credit && it.id == Cat.INCOME) }.takeIf { it.isNotEmpty() }?.let { add("On their own" to it) }
    }
}

/**
 * Splits one payment across categories: the first part takes whatever the others leave, so the parts always add up
 * to the payment. Each part counts in its own category in budgets, "Where it went" and Reports.
 */
@Composable
private fun SplitSheet(vm: AppViewModel, txId: Long) {
    val txFlow = remember(txId) { vm.txnFlow(txId) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val cats by vm.categories.collectAsStateWithLifecycle()
    val tx = t ?: return
    // Parts go into categories, not groups (a group itself is a payment not sorted yet)
    val choices = com.owl.tracker.data.CategoryTree(cats).pickable().filter { it.countsAsSpend }
    // Rows: category id and the amount typed (the first row's amount is the rest, worked out)
    val initial = remember(tx.id) {
        if (tx.isSplit) tx.parts.mapIndexed { i, p -> p.categoryId to if (i == 0) "" else rupees(p.paise) }
        else listOf(tx.categoryId to "", (choices.firstOrNull { it.id != tx.categoryId && it.id == Cat.GROCERIES } ?: choices.first { it.id != tx.categoryId }).id to "")
    }
    var rows by rememberSaveable(tx.id) { mutableStateOf(initial.joinToString("\u0002") { "${it.first}\u0001${it.second}" }) }
    val list = rows.split('\u0002').map { it.split('\u0001').let { p -> p[0] to p.getOrElse(1) { "" } } }
    fun save(next: List<Pair<String, String>>) { rows = next.joinToString("\u0002") { "${it.first}\u0001${it.second}" } }
    val amounts = list.drop(1).map { paiseOf(it.second) }
    val rest = tx.amountPaise - amounts.sumOf { it ?: 0L }
    val parts = listOf(com.owl.tracker.data.TxnPart(list[0].first, rest)) + list.drop(1).mapIndexed { i, r -> com.owl.tracker.data.TxnPart(r.first, amounts[i] ?: 0L) }
    val ok = amounts.all { (it ?: 0L) > 0 } && rest > 0 && com.owl.tracker.data.Parts.valid(parts, tx.amountPaise)

    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { vm.openSheet(Sheet.TxnDetail(txId)) }) { Icon(Ic.back, contentDescription = "Back to transaction") }
        SheetTitle("Split this payment", "One order, more than one kind of thing")
    }
    Panel(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MerchantAvatar(vm.appIcons, tx.merchantPackage, tx.merchantName, cats.firstOrNull { it.id == tx.categoryId }, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(tx.merchantName, style = MaterialTheme.typography.titleSmall)
                Text(vm.dateOf(tx.occurredAt).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Money.formatInr(tx.amountPaise, showPaise = tx.amountPaise % 100 != 0L), style = MaterialTheme.typography.titleMedium)
        }
    }
    list.forEachIndexed { i, (catId, text) ->
        val used = list.map { it.first }.toSet() - catId
        Panel(padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).clickable { open = true }.testTag("split_cat_$i"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryTile(cats.firstOrNull { it.id == catId }, 32.dp)
                        Column {
                            Text(vm.categoryName(catId), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (i == 0) "Gets the rest" else "Tap to change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        choices.filter { it.id !in used }.forEach { c ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(c.name) }, leadingIcon = { CategoryTile(c, 24.dp) },
                                onClick = { open = false; save(list.mapIndexed { j, r -> if (j == i) c.id to r.second else r }) },
                                modifier = Modifier.testTag("split_pick_${c.id}"),
                            )
                        }
                    }
                }
                if (i == 0) {
                    Text(
                        if (rest > 0) Money.formatInr(rest, showPaise = rest % 100 != 0L) else "₹0",
                        style = MaterialTheme.typography.titleMedium, color = if (rest > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp).testTag("split_rest"),
                    )
                } else {
                    OutlinedTextField(
                        value = text, onValueChange = { v -> save(list.mapIndexed { j, r -> if (j == i) r.first to v.filter { it.isDigit() || it == '.' }.take(10) else r }) },
                        prefix = { Text("₹") }, singleLine = true, placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(132.dp).testTag("split_amount_$i"),
                    )
                    if (list.size > 2) {
                        androidx.compose.material3.IconButton(onClick = { save(list.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Ic.close, contentDescription = "Remove this part")
                        }
                    }
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                amounts.any { it == null || it <= 0 } -> "Type an amount for each part"
                rest <= 0 -> "The parts come to more than ${Money.formatInr(tx.amountPaise, showPaise = tx.amountPaise % 100 != 0L)}"
                !ok -> "Use a different category for each part"
                else -> "Adds up to ${Money.formatInr(tx.amountPaise, showPaise = tx.amountPaise % 100 != 0L)}"
            },
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("split_check"),
            color = if (ok) LocalStatusColors.current.okFg else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (list.size < com.owl.tracker.backup.BackupCodec.MAX_PARTS && choices.any { c -> list.none { it.first == c.id } }) {
            TextButton(
                onClick = { save(list + ((choices.first { c -> list.none { it.first == c.id } }).id to "")) },
                modifier = Modifier.testTag("split_add"),
            ) { Text("+ Add a part") }
        }
    }
    Button(
        onClick = { vm.saveSplit(txId, parts) }, enabled = ok,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("split_save"),
    ) { Text("Save the split") }
    if (tx.isSplit) {
        OutlinedButton(onClick = { vm.joinSplit(txId) }, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("split_join")) {
            Text("Join back into one category")
        }
    }
    Text(
        "Each part counts in its own category in budgets, “Where it went” and Reports. The payment itself stays as one.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "1537.09" for 153709 paise, "1537" for 153700. */
private fun rupees(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else "%d.%02d".format(paise / 100, paise % 100)

/** Paise from what was typed: "1537.09", "1537.9", "1,537"; null when it isn't an amount. */
private fun paiseOf(text: String): Long? {
    val t = text.replace(",", "").trim()
    if (t.isEmpty()) return null
    val m = Regex("""^(\d{1,9})(?:\.(\d{0,2}))?$""").matchEntire(t) ?: return null
    val whole = m.groupValues[1].toLong()
    val frac = m.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
    return whole * 100 + frac
}

@Composable
private fun RenameSheet(vm: AppViewModel, txId: Long) {
    val txFlow = remember(txId) { vm.txnFlow(txId) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val countFlow = remember(txId) { vm.sameMerchantCount(txId) }
    val count by countFlow.collectAsStateWithLifecycle(initialValue = 1)
    val tx = t ?: return
    var name by rememberSaveable(txId) { mutableStateOf(tx.merchantName) }
    var asking by rememberSaveable(txId) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { vm.openSheet(Sheet.TxnDetail(txId)) }) { Icon(Ic.back, contentDescription = "Back to transaction") }
        SheetTitle("Rename", "Banks often send a company's legal name. Use one you'll recognise.")
    }
    OutlinedTextField(
        value = name, onValueChange = { name = it.take(com.owl.tracker.data.Repository.MAX_NAME) },
        label = { Text("Name") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("rename_field"),
    )
    if (asking) {
        OneOrAllDialog(
            title = "Rename to ${name.trim()}",
            text = if (count > 1) "Just this payment, or all $count payments from ${tx.merchantName}? All also names new ones from ${tx.merchantName} this way."
            else "Just this payment, or new payments from ${tx.merchantName} too?",
            allLabel = if (count > 1) "All $count" else "This and new ones",
            onOne = { asking = false; vm.renameTxn(txId, name, false) },
            onAll = { asking = false; vm.renameTxn(txId, name, true) },
            onDismiss = { asking = false },
            tag = "rename",
        )
    }
    Button(
        onClick = { asking = true },
        enabled = name.isNotBlank() && name.trim() != tx.merchantName,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("rename_save"),
    ) { Text("Save name") }
}

/**
 * Share a report with family, or open it on a computer: how much it covers, an interactive page or an Excel workbook,
 * and whether card and account numbers show (off: only the bank's name). Shared, or saved to a file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareReportSheet(vm: AppViewModel) {
    // From the monthly nudge: last month, in the kind chosen in Settings
    val context = androidx.compose.ui.platform.LocalContext.current
    val preset = remember { vm.reportPreset.also { vm.reportPreset = null } }
    var period by rememberSaveable { mutableStateOf(preset ?: com.owl.tracker.export.ReportPeriod.THIS_MONTH) }
    var format by rememberSaveable { mutableStateOf(if (com.owl.tracker.nudge.Nudges.reportExcel(context)) com.owl.tracker.export.ReportFormat.EXCEL else com.owl.tracker.export.ReportFormat.PAGE) }
    var accounts by rememberSaveable { mutableStateOf(false) }
    val working by vm.reportWorking.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val savePage = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/html")) { uri -> if (uri != null) vm.saveReport(uri) }
    val saveExcel = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(com.owl.tracker.export.ReportFormat.EXCEL.mime),
    ) { uri -> if (uri != null) vm.saveReport(uri) }

    SheetTitle("Share a report", "For your family, or to look through on a computer. It's made here on the phone and works without internet.")
    Text("How much", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.owl.tracker.export.ReportPeriod.entries.forEach { p ->
            FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) }, modifier = Modifier.testTag("report_period_${p.name}"))
        }
    }
    Text("As", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
    listOf(
        Triple(com.owl.tracker.export.ReportFormat.PAGE, "Interactive page", "Opens in any browser, on a phone or computer: charts, a calendar, filters by category, date and account, search, and every payment."),
        Triple(com.owl.tracker.export.ReportFormat.EXCEL, "Excel workbook", "Summary, every payment with filters, and spending by category and by month, for your own sums."),
    ).forEach { (f, title, sub) ->
        val on = format == f
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp))
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { format = f }.padding(12.dp).testTag("report_format_${f.name}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.RadioButton(selected = on, onClick = null)
            Gap(10.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    SwitchRow(
        "Show card and account numbers",
        if (accounts) "Like \"HDFC Bank card ••3643\"" else "Only the bank, like \"HDFC Bank card\"",
        accounts, { accounts = it }, modifier = Modifier.padding(top = 6.dp).testTag("report_accounts"),
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = {
                scope.launch {
                    val name = vm.prepareReport(period, format, accounts) ?: return@launch
                    if (format == com.owl.tracker.export.ReportFormat.PAGE) savePage.launch(name) else saveExcel.launch(name)
                }
            },
            enabled = !working, modifier = Modifier.weight(1f).height(52.dp).testTag("report_save"),
        ) { Text("Save to a file") }
        Button(
            onClick = { vm.shareReport(period, format, accounts) },
            enabled = !working, modifier = Modifier.weight(1f).height(52.dp).testTag("report_share"),
        ) { Text(if (working) "Making it…" else "Share") }
    }
}

/**
 * Which card or account a payment came from, for messages that don't say ("Paid from an account"): one already seen,
 * cash, or a new one by bank and last four digits. Can be used for the bank's other such messages, and new ones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountPickSheet(vm: AppViewModel, txId: Long) {
    val txFlow = remember(txId) { vm.txnFlow(txId) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val tx = t ?: return
    val bankId = tx.senderGroup?.takeIf { it != com.owl.tracker.core.Institutions.UNRECOGNISED }
    val bankName = bankId?.let { com.owl.tracker.core.Institutions.nameFor(it) }
    val known by androidx.compose.runtime.produceState<List<com.owl.tracker.data.KnownAccount>?>(null, txId) { value = vm.knownAccounts() }
    val missing by androidx.compose.runtime.produceState(0, bankId) { value = bankId?.let { vm.withoutAccount(it) } ?: 0 }
    // This bank's cards and accounts first, then the rest; each label once
    val choices = remember(known) {
        known.orEmpty().sortedByDescending { it.senderGroup == bankId && bankId != null }.map { it.label }.distinct()
            .filter { it != "Cash" } + "Cash"
    }
    var picked by rememberSaveable(txId) { mutableStateOf(tx.accountLabel) }
    var adding by rememberSaveable(txId) { mutableStateOf(false) }
    var newBank by rememberSaveable(txId) { mutableStateOf(bankName.orEmpty()) }
    var digits by rememberSaveable(txId) { mutableStateOf("") }
    var isCard by rememberSaveable(txId) { mutableStateOf(false) }
    var forBank by rememberSaveable(txId) { mutableStateOf(true) }
    val newLabel = if (newBank.isNotBlank() && digits.length == 4) (if (isCard) "${newBank.trim()} card ••$digits" else "${newBank.trim()} ••$digits") else null
    val label = if (adding) newLabel else picked

    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { vm.openSheet(Sheet.TxnDetail(txId)) }) { Icon(Ic.back, contentDescription = "Back to transaction") }
        SheetTitle(
            "Which account?",
            if (tx.accountLabel == null) "The ${bankName ?: "bank's"} message didn't say which card or account. Choose it and Owl Tracker remembers."
            else "Choose the card or account this came from.",
        )
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
        choices.forEachIndexed { i, choice ->
            val on = !adding && picked == choice
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { adding = false; picked = choice }.heightIn(min = 52.dp).padding(horizontal = 12.dp).testTag("account_pick_$i"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected = on, onClick = null)
                Gap(10.dp)
                Column(Modifier.weight(1f)) {
                    Text(choice, style = MaterialTheme.typography.titleSmall)
                    known?.filter { it.label == choice }?.sumOf { it.payments }?.takeIf { it > 0 }?.let {
                        Text(if (it == 1) "1 payment" else "$it payments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (adding) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable { adding = true }.heightIn(min = 52.dp).padding(horizontal = 12.dp).testTag("account_new"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.RadioButton(selected = adding, onClick = null)
            Gap(10.dp)
            Text("Another card or account", style = MaterialTheme.typography.titleSmall)
        }
        if (adding) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !isCard, onClick = { isCard = false }, label = { Text("Bank account") })
                FilterChip(selected = isCard, onClick = { isCard = true }, label = { Text("Card") })
            }
            OutlinedTextField(
                value = newBank, onValueChange = { newBank = it.take(30) }, label = { Text("Bank") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("account_new_bank"),
            )
            OutlinedTextField(
                value = digits, onValueChange = { v -> digits = v.filter { it.isDigit() }.take(4) },
                label = { Text("Last 4 digits") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("account_new_digits"),
            )
        }
    }
    if (bankId != null && label != null && label != "Cash") {
        val others = (missing - if (tx.accountLabel == null) 1 else 0).coerceAtLeast(0)
        SwitchRow(
            "Use it for $bankName messages that don't say",
            if (others > 0) "$others more payments now, and new ones" else "New ones from $bankName",
            forBank, { forBank = it }, modifier = Modifier.testTag("account_for_bank"),
        )
    }
    Button(
        onClick = { vm.setAccount(txId, label, forBank && label != "Cash") },
        enabled = label != null && label != tx.accountLabel,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("account_save"),
    ) { Text("Save") }
    if (tx.accountLabel != null) androidx.compose.material3.TextButton(
        onClick = { vm.setAccount(txId, null, false) }, modifier = Modifier.fillMaxWidth().testTag("account_clear"),
    ) { Text("Not sure, clear it") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantIconSheet(vm: AppViewModel, txId: Long) {
    val txFlow = remember(txId) { vm.txnFlow(txId) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val cats by vm.categories.collectAsStateWithLifecycle()
    val apps by androidx.compose.runtime.produceState<List<com.owl.tracker.KnownApp>?>(null) { value = vm.pickableApps() }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var picked by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Android's photo picker: no storage permission, and Owl Tracker only gets the one picture chosen
    val pickPicture = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PictureIcons.load(context, uri) }
            if (bitmap == null) vm.pictureNotOpened() else picked = bitmap
        }
    }
    val tx = t ?: return
    val cat = cats.firstOrNull { it.id == tx.categoryId }
    picked?.let { source ->
        PictureCropDialog(
            source,
            onCancel = { picked = null },
            onUse = { box ->
                scope.launch {
                    val png = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { PictureIcons.iconPng(source, box) }
                    picked = null
                    vm.setMerchantPicture(txId, png)
                }
            },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { vm.openSheet(Sheet.TxnDetail(txId)) }) { Icon(Ic.back, contentDescription = "Back to transaction") }
        SheetTitle("Icon for ${tx.merchantName}", "Used for all its payments, now and later")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.setMerchantIcon(txId, null) }, modifier = Modifier.weight(1f).testTag("icon_auto")) { Text("Automatic") }
        OutlinedButton(onClick = { vm.setMerchantIcon(txId, "") }, modifier = Modifier.weight(1f).testTag("icon_none")) {
            CategoryTile(cat, 20.dp)
            Text("  Category icon")
        }
    }
    val hasPicture = com.owl.tracker.core.MerchantPicture.isPicture(tx.merchantPackage)
    OutlinedButton(
        onClick = {
            pickPicture.launch(
                androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        modifier = Modifier.fillMaxWidth().testTag("icon_picture"),
        border = androidx.compose.foundation.BorderStroke(
            if (hasPicture) 2.dp else 1.dp,
            if (hasPicture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        if (hasPicture) MerchantAvatar(vm.appIcons, tx.merchantPackage, tx.merchantName, cat, 20.dp) else Icon(Ic.image, contentDescription = null)
        Text(if (hasPicture) "  Change picture" else "  Use a picture")
    }
    Text("Apps on this phone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val list = apps
    when {
        list == null -> Text("Looking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.isEmpty() -> Text(
            "None of the apps Owl Tracker recognises are installed. Use a picture instead.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> com.owl.tracker.ui.components.CellGrid(list.size, 76.dp, list.map { it.label }, MaterialTheme.typography.labelSmall, 4.dp, Modifier.fillMaxWidth()) { i, w ->
            run {
                val app = list[i]
                val on = app.packageName == tx.merchantPackage
                Column(
                    Modifier.width(w).clip(RoundedCornerShape(14.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .selectable(selected = on, role = Role.RadioButton) { vm.setMerchantIcon(txId, app.packageName) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .testTag("icon_${app.packageName}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MerchantAvatar(vm.appIcons, app.packageName, app.label, null, 36.dp)
                    Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    InfoNote(
        "Shows the apps Owl Tracker recognises and every app here that pays by UPI. For anything else, use a picture, " +
            "such as a screenshot of the app's icon. Owl Tracker never reads the full list of apps on your phone.",
    )
}

// ---------------- budgets & categories ----------------

@Composable
private fun BudgetEditSheet(vm: AppViewModel, key: String) {
    // The month's budget, or a category's: each on its slider, with a box to type any amount
    if (key == Schema.OVERALL) MonthBudgetSheet(vm) else CategoryBudgetSheet(vm, key)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditSheet(vm: AppViewModel, id: String?, inGroup: String? = null) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val existing = cats.firstOrNull { it.id == id }
    val tree = remember(cats) { com.owl.tracker.data.CategoryTree(cats) }
    val isGroup = id != null && tree.isGroup(id)
    var parent by rememberSaveable(id) { mutableStateOf(existing?.parent ?: inGroup) }
    var name by rememberSaveable(id) { mutableStateOf(existing?.name ?: "") }
    var icon by rememberSaveable(id) { mutableStateOf(existing?.icon ?: "tag") }
    var color by rememberSaveable(id) { mutableLongStateOf(existing?.color ?: Ic.colorChoices[3]) }
    var counts by rememberSaveable(id) { mutableStateOf(existing?.countsAsSpend ?: true) }

    SheetTitle(if (existing == null) "New category" else "Edit category")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(17.dp)).background(Color(color).copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(Ic.forCategory(icon), contentDescription = null, tint = Color(color), modifier = Modifier.size(28.dp))
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true,
            placeholder = { Text("e.g. Pets, Gifts, Kids") }, modifier = Modifier.weight(1f).testTag("category_name"),
        )
    }
    Text("Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Ic.categoryChoices.forEach { k ->
            val on = icon == k
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) Color(color) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .selectable(selected = on, role = Role.RadioButton) { icon = k },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.forCategory(k), contentDescription = "$k icon", tint = if (on) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    Text("Colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Ic.colorChoices.forEachIndexed { i, col ->
            val on = color == col
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .border(3.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .selectable(selected = on, role = Role.RadioButton) { color = col },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(30.dp).clip(CircleShape).background(Color(col))) }
        }
    }
    // Which group it sits in (a group itself stays where it is)
    if (!isGroup && existing?.kind != com.owl.tracker.data.CategoryKind.INCOME) {
        Text("Group", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("category_group")) {
            FilterChip(selected = parent == null, onClick = { parent = null }, label = { Text("On its own") })
            cats.filter { it.parent == null && !it.archived && tree.isGroup(it.id) }.forEach { g ->
                FilterChip(
                    selected = parent == g.id, onClick = { parent = g.id; counts = true }, label = { Text(g.name) },
                    modifier = Modifier.testTag("group_${g.id}"),
                )
            }
        }
    }
    if (existing?.kind != com.owl.tracker.data.CategoryKind.INCOME && parent == null) {
        SwitchRow("Count as spending", "Turn off for transfers and savings", counts, { counts = it })
    }
    Button(
        onClick = { vm.saveCategory(id, name, icon, color, counts, parent.takeIf { !isGroup }) },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_category"),
    ) { Text(if (existing == null) "Add category" else "Save changes") }
    if (existing != null && existing.id != Cat.OTHERS && existing.id != Cat.INCOME) {
        TextButton(onClick = { vm.archiveCategory(existing.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (isGroup) "Archive the group and its categories (their transactions move to Others)" else "Archive category (its transactions move to Others)",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ---------------- add expense ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseSheet(vm: AppViewModel) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    // From "Can I afford it?": the amount and category already filled in
    val prefill = remember { vm.addPrefill.also { vm.addPrefill = null } }
    var amount by rememberSaveable { mutableStateOf(prefill?.first?.toString() ?: "") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(prefill?.second ?: Cat.DINING) }
    var daysAgo by rememberSaveable { mutableIntStateOf(0) }
    val rupees = amount.toLongOrNull() ?: 0L

    SheetTitle("Add expense", "For cash, or anything an SMS missed")
    OutlinedTextField(
        value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() }.take(8) },
        label = { Text("Amount (₹)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("add_amount"),
        textStyle = MaterialTheme.typography.headlineSmall,
    )
    OutlinedTextField(
        value = merchant, onValueChange = { merchant = it.take(40) },
        label = { Text("Where") }, placeholder = { Text("e.g. Tea stall, Auto fare") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("add_merchant"),
    )
    Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.owl.tracker.data.CategoryTree(cats).pickable().filter { it.countsAsSpend }.forEach { c ->
            FilterChip(selected = cat == c.id, onClick = { cat = c.id }, label = { Text(c.name) },
                leadingIcon = { Icon(Ic.forCategory(c.icon), contentDescription = null, tint = Color(c.color), modifier = Modifier.size(18.dp)) })
        }
    }
    // Today and yesterday in one tap; any other day from the calendar (never a future one)
    var picking by rememberSaveable { mutableStateOf(false) }
    Text("When", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Today", 1 to "Yesterday").forEach { (d, label) ->
            FilterChip(selected = daysAgo == d, onClick = { daysAgo = d }, label = { Text(label) }, modifier = Modifier.testTag("add_day_$d"))
        }
        val other = daysAgo > 1
        FilterChip(
            selected = other, onClick = { picking = true },
            label = { Text(if (other) vm.today().minusDays(daysAgo.toLong()).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)) else "Pick a date") },
            leadingIcon = { Icon(Ic.calendar, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.testTag("add_pick_date"),
        )
    }
    if (picking) ExpenseDatePicker(
        today = vm.today(), current = vm.today().minusDays(daysAgo.toLong()),
        onPick = { d -> daysAgo = java.time.temporal.ChronoUnit.DAYS.between(d, vm.today()).toInt().coerceAtLeast(0); picking = false },
        onDismiss = { picking = false },
    )
    Button(
        onClick = { vm.addManual(rupees * 100, merchant.ifBlank { "Cash expense" }, cat, daysAgo) },
        enabled = rupees > 0,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_expense"),
    ) { Text("Save expense") }
}

/** A calendar for when an expense happened: any day up to today. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDatePicker(today: java.time.LocalDate, current: java.time.LocalDate, onPick: (java.time.LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker counts in UTC midnights
    fun utc(d: java.time.LocalDate) = d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val last = utc(today)
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = utc(current),
        yearRange = (today.year - 10)..today.year,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= last
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) } ?: onDismiss() },
                modifier = Modifier.testTag("add_date_ok"),
            ) { Text("OK") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        androidx.compose.material3.DatePicker(state = state, title = { Text("When was it?", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }, modifier = Modifier.testTag("add_date_picker"))
    }
}

// ---------------- delete ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeleteSheet(vm: AppViewModel) {
    val d by vm.deleteUi.collectAsStateWithLifecycle()
    SheetTitle("Delete transactions", "Messages in your SMS inbox are never touched")
    val opts = listOf(
        DeleteOption.OLDER_3 to "Older than 3 months",
        DeleteOption.OLDER_6 to "Older than 6 months",
        DeleteOption.ACCOUNT to "From one bank or card",
        DeleteOption.ALL_SMS to "Everything imported from SMS",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (o, label) ->
            val n = d.counts[o] ?: 0
            val on = d.option == o
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .selectable(selected = on, role = Role.RadioButton) { vm.setDeleteOption(o) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RadioButton(selected = on, onClick = null)
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (o) {
                            DeleteOption.ACCOUNT -> if (d.groups.isEmpty()) "No imported accounts yet" else "$n from ${Institutions.nameFor(d.group)}"
                            DeleteOption.ALL_SMS -> "$n transactions · keeps manual entries, categories, rules and budgets"
                            else -> if (n == 0) "Nothing that old is stored" else "$n transactions"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (d.option == DeleteOption.ACCOUNT && d.groups.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            d.groups.forEach { (g, n) -> FilterChip(selected = d.group == g, onClick = { vm.setDeleteGroup(g) }, label = { Text("${Institutions.nameFor(g)} · $n") }) }
        }
        SwitchRow("Also stop importing from it", "Turns this sender off in Import filters", d.stopImporting, { vm.setStopImporting(it) })
    }
    InfoNote("The next automatic scan won't bring these back. You can re-import them any time from Settings → What to import.")
    Button(
        onClick = { vm.requestDelete() },
        enabled = d.selectedCount > 0,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("delete_button"),
    ) { Text(if (d.selectedCount > 0) "Delete ${d.selectedCount} transactions" else "Nothing to delete") }
}


// ---------------- skipped messages ----------------

/**
 * Why messages were left out, so a bank format Owl Tracker doesn't know yet can be spotted and shared.
 * Nothing here is stored: the list lives in memory for this import only.
 */
@OptIn(ExperimentalLayoutApi::class)
/** Round 27 B: the budgets that are over, worst first, each with a bar to its budget and its two buttons. */
@Composable
private fun OverBudgetSheet(vm: AppViewModel) {
    val dash by vm.dashboard.collectAsStateWithLifecycle()
    val over = dash?.over.orEmpty().sortedByDescending { it.spent - it.limit }
    val sc = LocalStatusColors.current
    if (over.isEmpty()) {
        SheetTitle("No budget is over", "Nice. Every category is within its budget.")
        return
    }
    val total = over.sumOf { it.spent - it.limit }
    SheetTitle(
        if (over.size == 1) "1 budget is over" else "${over.size} budgets are over",
        "${Money.formatInr(total)} over in all · ${over.first().daysLeftText}",
    )
    Column(Modifier.testTag("over_sheet"), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        over.forEach { o ->
            Divider()
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("over_row_" + o.budgetKey), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${Money.formatInr(o.spent - o.limit)} over", style = MaterialTheme.typography.titleSmall, color = sc.overFg)
                }
                Text("${Money.formatInr(o.spent)} spent of ${Money.formatInr(o.limit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OverBar(o.spent, o.limit, sc.overFg, sc.overBg)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.closeSheet(); vm.showActivity(o.categoryId) },
                        modifier = Modifier.testTag("over_see_" + o.budgetKey),
                    ) { Text("See where it went") }
                    OutlinedButton(
                        onClick = { vm.openSheet(Sheet.BudgetEdit(o.budgetKey)) },
                        modifier = Modifier.testTag("over_adjust_" + o.budgetKey),
                    ) { Text("Adjust budget") }
                }
            }
        }
    }
}

/** Spending against a budget on a scale to twice the budget, with a mark at the budget. */
@Composable
private fun OverBar(spent: Long, limit: Long, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    val frac = if (limit > 0) (spent.toFloat() / (2f * limit)).coerceIn(0f, 1f) else 1f
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
        val w = maxWidth
        androidx.compose.foundation.layout.Box(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(bg),
        )
        androidx.compose.foundation.layout.Box(
            Modifier.align(Alignment.CenterStart).width(w * frac).height(8.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(fg),
        )
        androidx.compose.foundation.layout.Box(
            Modifier.align(Alignment.CenterStart).padding(start = w / 2 - 1.dp).width(2.dp).height(14.dp)
                .background(MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun SkippedSheet(vm: AppViewModel) {
    val ui by vm.importUi.collectAsStateWithLifecycle()
    val items = remember(ui.scan, ui.draft) { vm.skipped() }
    val context = LocalContext.current
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }
    val groups = remember(items) { items.groupBy { it.reason } }

    SheetTitle("Skipped messages", "${items.size} messages in this period weren't read as transactions. None of them is stored.")
    InfoNote(
        "If a real payment is listed here, tap Copy and send it to whoever maintains Owl Tracker. " +
            "Account, card, phone and reference numbers are hidden in the copy.",
    )
    Button(
        onClick = {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("Owl Tracker skipped messages", vm.skippedReport()))
            vm.messages.tryEmit(UiMessage("Copied, with account and card numbers hidden"))
        },
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("copy_skipped"),
    ) { Text("Copy list to share") }

    AppViewModel.SKIP_ORDER.filter { it in groups }.forEach { reason ->
        val list = groups.getValue(reason)
        Text("${AppViewModel.skipLabel(reason)} \u00b7 ${list.size}", style = MaterialTheme.typography.titleSmall)
        if (reason == RejectReason.PERSONAL_SENDER) {
            Text("Texts from phone numbers are skipped without being read further.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@forEach
        }
        Panel(padding = 12.dp) {
            list.take(30).forEachIndexed { i, sk ->
                if (i > 0) Divider()
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${sk.sender ?: "?"} \u00b7 ${vm.dateOf(sk.timestamp).format(fmt)}", style = MaterialTheme.typography.labelLarge)
                    sk.body?.let {
                        Text(
                            it.replace('\n', ' '), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (list.size > 30) Text("\u2026and ${list.size - 30} more", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * "Move to X": just this payment, or all from the same payee (and new ones), or, after "Review and choose", the
 * payments ticked in a list, each going to X or to a category picked for it alone. Tags as [OneOrAllDialog] with
 * `apply`, plus `apply_review`, `apply_row_<i>`, `apply_to_<i>`, `apply_new_ones`, `apply_move`.
 */
@Composable
private fun MoveDialog(vm: AppViewModel, tx: com.owl.tracker.data.Txn, target: String, count: Int, cats: List<com.owl.tracker.data.Category>, onDone: () -> Unit) {
    val byId = remember(cats) { cats.associateBy { it.id } }
    val name = byId[target]?.name ?: ""
    var review by rememberSaveable(tx.id, target) { mutableStateOf(false) }
    val others by androidx.compose.runtime.produceState<List<com.owl.tracker.data.Txn>?>(null, tx.id, review) {
        if (review && value == null) value = vm.sameMerchant(tx.id)
    }
    // Ticked: this one, and those in the same category as it; each goes to the target unless picked otherwise
    val ticked = androidx.compose.runtime.remember(tx.id, target) { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    val goesTo = androidx.compose.runtime.remember(tx.id, target) { androidx.compose.runtime.mutableStateMapOf<Long, String>() }
    var newOnes by rememberSaveable(tx.id, target) { mutableStateOf(true) }
    var choosingFor by remember(tx.id, target) { mutableStateOf<com.owl.tracker.data.Txn?>(null) }
    fun on(p: com.owl.tracker.data.Txn) = ticked[p.id] ?: (p.id == tx.id || p.categoryId == tx.categoryId)
    val list = others.orEmpty()
    val chosen = list.filter { on(it) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        modifier = Modifier.testTag("apply_dialog"),
        title = { Text("Move to $name") },
        text = {
            Column {
                if (!review) {
                    Text("Just this payment, or all $count payments from ${tx.merchantName}? All also sorts new ones from ${tx.merchantName} into $name.")
                    Row(
                        Modifier.padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).clickable { review = true }
                            .heightIn(min = 44.dp).padding(end = 8.dp).testTag("apply_review"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Review and choose", fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Icon(Ic.chevDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Text("Tick the payments from ${tx.merchantName} to move. Tap where one goes to send it somewhere else.", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.TextButton(onClick = { review = false }) { Text("Hide list") }
                        Spacer(Modifier.weight(1f))
                        val all = list.isNotEmpty() && chosen.size == list.size
                        androidx.compose.material3.TextButton(
                            onClick = { list.forEach { ticked[it.id] = !all } }, modifier = Modifier.testTag("apply_select_all"),
                        ) { Text(if (all) "Select none" else "Select all") }
                    }
                    if (others == null) Text("…", color = muted)
                    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                        list.forEachIndexed { i, p ->
                            val isOn = on(p)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(isOn, role = androidx.compose.ui.semantics.Role.Checkbox) { ticked[p.id] = it }
                                    .padding(vertical = 6.dp).testTag("apply_row_$i"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Checkbox(checked = isOn, onCheckedChange = null)
                                Gap(6.dp)
                                CategoryTile(byId[p.categoryId], 34.dp)
                                Gap(10.dp)
                                Column(Modifier.weight(1f)) {
                                    Row {
                                        Text(
                                            vm.dateOf(p.occurredAt).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)) + if (p.id == tx.id) " · this one" else "",
                                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                                        )
                                        Text(Money.formatInr(p.amountPaise), style = MaterialTheme.typography.titleSmall)
                                    }
                                    if (isOn) {
                                        val to = goesTo[p.id] ?: target
                                        Row(
                                            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50))
                                                .background(if (to != target) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable(onClickLabel = "Choose where this one goes") { choosingFor = p }
                                                .heightIn(min = 32.dp).padding(horizontal = 10.dp).testTag("apply_to_$i"),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("→ ${byId[to]?.name ?: ""}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                            Icon(Ic.chevDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    } else Text("Stays in ${byId[p.categoryId]?.name ?: ""}", style = MaterialTheme.typography.bodySmall, color = muted)
                                }
                            }
                        }
                    }
                    SwitchRow(
                        "Also new ones from ${tx.merchantName}", "Sorted into $name", newOnes, { newOnes = it },
                        modifier = Modifier.testTag("apply_new_ones"),
                    )
                }
            }
        },
        confirmButton = {
            if (!review) Button(onClick = { onDone(); vm.setCategory(tx.id, target, false) }, modifier = Modifier.testTag("apply_one")) { Text("Just this one") }
            else Button(
                onClick = { onDone(); vm.moveChosen(tx.id, chosen.associate { it.id to (goesTo[it.id] ?: target) }, target.takeIf { newOnes }) },
                enabled = chosen.isNotEmpty(), modifier = Modifier.testTag("apply_move"),
            ) { Text("Move ${chosen.size}") }
        },
        dismissButton = {
            if (!review) androidx.compose.material3.TextButton(onClick = { onDone(); vm.setCategory(tx.id, target, true) }, modifier = Modifier.testTag("apply_all")) { Text("All $count") }
            else androidx.compose.material3.TextButton(onClick = onDone) { Text("Cancel") }
        },
    )
    choosingFor?.let { p -> OneCategoryPicker(p, target, cats, vm, onPick = { goesTo[p.id] = it; choosingFor = null }, onDismiss = { choosingFor = null }) }
}

/** Where one payment in "Review and choose" goes: the move's category first ("For the rest"), then all others. */
@Composable
private fun OneCategoryPicker(p: com.owl.tracker.data.Txn, target: String, cats: List<com.owl.tracker.data.Category>, vm: AppViewModel, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val tree = remember(cats) { com.owl.tracker.data.CategoryTree(cats) }
    val credit = p.direction == com.owl.tracker.core.Direction.CREDIT
    val choices = remember(tree, credit, target) {
        val ok = tree.pickable().filter { credit || it.id != Cat.INCOME }
        ok.filter { it.id == target } + ok.filter { it.id == p.categoryId && it.id != target } + ok.filter { it.id != target && it.id != p.categoryId }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("apply_pick_dialog"),
        title = { Text("Where does ${vm.dateOf(p.occurredAt).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · ${Money.formatInr(p.amountPaise)} go?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Only this payment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                choices.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(c.id) }.heightIn(min = 48.dp).padding(horizontal = 6.dp)
                            .testTag("apply_pick_${c.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryTile(c, 32.dp)
                        Gap(10.dp)
                        Text(c.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        when (c.id) {
                            target -> Text("For the rest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            p.categoryId -> Text("Now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Just this payment, or all of them?" after renaming or re-categorising a payment. Just this one is the first choice;
 * all of them also covers new payments from the same payee. Tags: `<tag>_dialog`, `<tag>_one`, `<tag>_all`.
 */
@Composable
private fun OneOrAllDialog(title: String, text: String, allLabel: String, onOne: () -> Unit, onAll: () -> Unit, onDismiss: () -> Unit, tag: String) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("${tag}_dialog"),
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onOne, modifier = Modifier.testTag("${tag}_one")) { Text("Just this one") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onAll, modifier = Modifier.testTag("${tag}_all")) { Text(allLabel) } },
    )
}
