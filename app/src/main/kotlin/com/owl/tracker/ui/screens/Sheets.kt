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
                is Sheet.Rename -> RenameSheet(vm, current.txId)
                is Sheet.MerchantIcon -> MerchantIconSheet(vm, current.txId)
                is Sheet.BudgetEdit -> BudgetEditSheet(vm, current.key)
                is Sheet.CategoryEdit -> CategoryEditSheet(vm, current.id)
                Sheet.AddExpense -> AddExpenseSheet(vm)
                Sheet.DeleteData -> DeleteSheet(vm)
                Sheet.Skipped -> SkippedSheet(vm)
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

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MerchantAvatar(vm.appIcons, tx.merchantPackage, tx.merchantName, cat, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(tx.merchantName, style = MaterialTheme.typography.titleLarge)
            Text(cat?.name ?: "Others", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.IconButton(
            onClick = { vm.openSheet(Sheet.Rename(tx.id)) },
            modifier = Modifier.testTag("rename_txn"),
        ) { Icon(Ic.edit, contentDescription = "Rename ${tx.merchantName}") }
    }
    Column {
        Text(
            (if (credit) "+" else "−") + Money.formatInr(tx.amountPaise, showPaise = tx.amountPaise % 100 != 0L),
            style = MaterialTheme.typography.headlineLarge,
            color = if (credit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("detail_amount"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                modifier = Modifier.testTag("paid_from"),
            )
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
        Row(
            Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.CategoryPick(tx.id)) }.padding(vertical = 10.dp).testTag("change_category"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Category", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            CategoryTile(cat, 28.dp)
            Text(cat?.name ?: "Others", style = MaterialTheme.typography.titleSmall)
            Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Divider()
        Row(
            Modifier.fillMaxWidth().clickable { vm.openSheet(Sheet.MerchantIcon(tx.id)) }.padding(vertical = 10.dp).testTag("change_icon"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Icon", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            MerchantAvatar(vm.appIcons, tx.merchantPackage, tx.merchantName, cat, 28.dp)
            Text(
                when {
                    tx.merchantPackage != null && vm.appIcons.get(tx.merchantPackage) != null -> "App icon"
                    tx.merchantPackage != null -> "App not installed"
                    else -> "Category icon"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        tx.refNo?.let { Divider(); DetailRow("Reference", it) }
        tx.sender?.let { Divider(); DetailRow("From", it) }
        Divider()
        if (credit) {
            // Money in is shown, never spent: it can count as income on Income & expenses, or be left out.
            val counts = cat == null || cat.kind == com.owl.tracker.data.CategoryKind.INCOME || cat.kind == com.owl.tracker.data.CategoryKind.EXPENSE
            SwitchRow(
                "Count as income",
                when {
                    !counts -> "Transfers and savings coming back aren't income"
                    tx.excluded -> "Left out of Income & expenses. Never counts as spending."
                    else -> "Counts as income in Reports. Never counts as spending."
                },
                tx.isIncome(cat),
                { on -> if (counts) vm.setExcluded(tx.id, !on) },
                modifier = Modifier.testTag("count_money_in"),
            )
        } else {
            SwitchRow(
                "Count in spend",
                if (cat?.countsAsSpend == false) "Transfers, savings and income never count" else if (tx.excluded) "Left out of your budget" else "Counts toward your budget",
                tx.isSpending(cat),
                { on -> if (cat?.countsAsSpend == false) Unit else vm.setExcluded(tx.id, !on) },
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

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickSheet(vm: AppViewModel, txId: Long) {
    val txFlow = remember(txId) { vm.txnFlow(txId) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val cats by vm.categories.collectAsStateWithLifecycle()
    var rememberRule by rememberSaveable { mutableStateOf(true) }
    val tx = t ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { vm.openSheet(Sheet.TxnDetail(txId)) }) { Icon(Ic.back, contentDescription = "Back to transaction") }
        SheetTitle("Choose category")
    }
    SwitchRow("Always use for ${tx.merchantName}", "Creates a rule for past and future payments", rememberRule, { rememberRule = it })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Money in can go to Income (listed first); money out never can.
        val credit = tx.direction == com.owl.tracker.core.Direction.CREDIT
        cats.filter { !it.archived && (credit || it.id != Cat.INCOME) }.sortedBy { if (credit && it.id == Cat.INCOME) 0 else 1 }.forEach { c ->
            val on = c.id == tx.categoryId
            Column(
                Modifier.width(100.dp).heightIn(min = 92.dp).clip(RoundedCornerShape(16.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) Color(c.color) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .selectable(selected = on, role = Role.RadioButton) { vm.setCategory(txId, c.id, rememberRule) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CategoryTile(c)
                Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RenameSheet(vm: AppViewModel, txId: Long) {
    val txFlow = remember(txId) { vm.txnFlow(txId) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val countFlow = remember(txId) { vm.sameMerchantCount(txId) }
    val count by countFlow.collectAsStateWithLifecycle(initialValue = 1)
    val tx = t ?: return
    var name by rememberSaveable(txId) { mutableStateOf(tx.merchantName) }
    var all by rememberSaveable(txId) { mutableStateOf(true) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { vm.openSheet(Sheet.TxnDetail(txId)) }) { Icon(Ic.back, contentDescription = "Back to transaction") }
        SheetTitle("Rename", "Banks often send a company's legal name. Use one you'll recognise.")
    }
    OutlinedTextField(
        value = name, onValueChange = { name = it.take(com.owl.tracker.data.Repository.MAX_NAME) },
        label = { Text("Name") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("rename_field"),
    )
    Box(Modifier.testTag("rename_all")) {
        SwitchRow(
            if (count > 1) "Rename all $count payments" else "Use for this merchant",
            "New payments from ${tx.merchantName} will get this name too",
            all, { all = it },
        )
    }
    Button(
        onClick = { vm.renameTxn(txId, name, all) },
        enabled = name.isNotBlank() && name.trim() != tx.merchantName,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("rename_save"),
    ) { Text("Save name") }
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
        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { app ->
                val on = app.packageName == tx.merchantPackage
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(14.dp))
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
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val overall = key == Schema.OVERALL
    val name = if (overall) "Monthly budget" else (cats.firstOrNull { it.id == key }?.name ?: "Category") + " budget"
    val current = budgets[key]
    var text by rememberSaveable(key) { mutableStateOf(current?.let { (it / 100).toString() } ?: "") }
    val rupees = text.toLongOrNull() ?: 0L
    val spent = if (overall) d?.spent ?: 0L else d?.byCategory?.firstOrNull { it.category.id == key }?.paise ?: 0L
    val daysLeft = d?.let { (it.period.days - it.dayNumber).coerceAtLeast(1) } ?: 1

    SheetTitle(name, "${Money.formatInr(spent)} spent so far this month")
    OutlinedTextField(
        value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(8) },
        label = { Text("Monthly limit (₹)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("budget_amount"),
        textStyle = MaterialTheme.typography.headlineSmall,
    )
    Text(
        if (rupees > 0) "${Money.formatInr(rupees * 100)} a month · ${Money.formatInr(((rupees * 100 - spent).coerceAtLeast(0)) / daysLeft)} a day left to spend" else "Enter an amount",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (if (overall) listOf(50_000L, 75_000L, 1_00_000L) else listOf(2_000L, 5_000L, 8_000L)).forEach { v ->
            FilterChip(selected = rupees == v, onClick = { text = v.toString() }, label = { Text(Money.formatInr(v * 100)) })
        }
    }
    Text("Repeats every month until you change it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = { vm.saveBudget(key, rupees * 100) }, enabled = rupees > 0, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_budget_sheet")) { Text("Save budget") }
    if (!overall && current != null) {
        TextButton(onClick = { vm.saveBudget(key, null) }, modifier = Modifier.fillMaxWidth()) { Text("Remove this budget", color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditSheet(vm: AppViewModel, id: String?) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val existing = cats.firstOrNull { it.id == id }
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
    if (existing?.kind != com.owl.tracker.data.CategoryKind.INCOME) {
        SwitchRow("Count as spending", "Turn off for transfers and savings", counts, { counts = it })
    }
    Button(
        onClick = { vm.saveCategory(id, name, icon, color, counts) },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_category"),
    ) { Text(if (existing == null) "Add category" else "Save changes") }
    if (existing != null && existing.id != Cat.OTHERS && existing.id != Cat.INCOME) {
        TextButton(onClick = { vm.archiveCategory(existing.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("Archive category (its transactions move to Others)", color = MaterialTheme.colorScheme.error)
        }
    }
}

// ---------------- add expense ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseSheet(vm: AppViewModel) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(Cat.FOOD) }
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
        cats.filter { !it.archived && it.countsAsSpend }.forEach { c ->
            FilterChip(selected = cat == c.id, onClick = { cat = c.id }, label = { Text(c.name) },
                leadingIcon = { Icon(Ic.forCategory(c.icon), contentDescription = null, tint = Color(c.color), modifier = Modifier.size(18.dp)) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Today", 1 to "Yesterday", 2 to "2 days ago").forEach { (d, label) ->
            FilterChip(selected = daysAgo == d, onClick = { daysAgo = d }, label = { Text(label) })
        }
    }
    Button(
        onClick = { vm.addManual(rupees * 100, merchant.ifBlank { "Cash expense" }, cat, daysAgo) },
        enabled = rupees > 0,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_expense"),
    ) { Text("Save expense") }
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
