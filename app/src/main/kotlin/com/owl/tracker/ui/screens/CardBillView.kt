package com.owl.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.Money
import com.owl.tracker.data.Txn
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.BillPeriod
import com.owl.tracker.ui.CardBill
import com.owl.tracker.ui.CardBills
import com.owl.tracker.ui.CardCycle
import com.owl.tracker.ui.components.Divider
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.SideOrStacked
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalBrand
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * A credit card opened on Paid by, "Still to pay" tab (Round 23 B, with Round 24's billing cycle): the last bill, what's
 * been paid towards it (found in SMS, or added here), Mark as fully paid / Add a payment, and the bill building up now.
 */

private val DAY = DateTimeFormatter.ofPattern("d MMM")
private val DAY_W = DateTimeFormatter.ofPattern("EEE d MMM")
private fun d(x: LocalDate) = x.format(DAY)

@Composable
fun StillToPay(vm: AppViewModel, label: String) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cycle = remember(settings?.cardCycles) { CardBills.cyclesFromJson(settings?.cardCycles)[label] }
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("still_to_pay"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cycle == null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LocalBrand.current.clay.pressed).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Set this card's billing cycle", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The bill date tells which bill each payment goes on. It's on your card statement as \"Statement date\", with the \"Payment due date\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { editing = true }, modifier = Modifier.testTag("bill_cycle_set")) { Text("Set the bill date") }
            }
        } else {
            val period by vm.periodTxns.collectAsStateWithLifecycle()
            val today = vm.today()
            val from = cycle.billBefore(cycle.lastBillOnOrBefore(today)).minusDays(1)
            val txns by produceState<List<Txn>?>(null, label, cycle, period, settings?.cardPayments) { value = vm.txnsSince(from) }
            val list = txns
            if (list == null) {
                Text("Working it out…", style = MaterialTheme.typography.bodySmall)
            } else {
                val manual = remember(settings?.cardPayments) { CardBills.paymentsFromJson(settings?.cardPayments)[label].orEmpty() }
                val bill = remember(list, manual, cycle, today) { CardBills.compute(label, cycle, list, manual, today, vm.timeZone) }
                LastBill(vm, label, bill, today)
                ThisBill(bill.current, bill.nextBill)
            }
            // The cycle, and a way to change it
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Change the billing cycle") { editing = true }
                    .padding(vertical = 8.dp, horizontal = 4.dp).testTag("bill_cycle_change"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.calendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Billing cycle: bill on ${cycle.dayWords}", style = MaterialTheme.typography.titleSmall)
                    Text("Due ${cycle.dueDays} days after the bill", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Worked out from your SMS and the payments you add here. Your card statement is the final word.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (editing) CycleDialog(label, cycle, vm.today(), onDismiss = { editing = false }) { c -> editing = false; vm.setCardCycle(label, c) }
}

@Composable
private fun AmountRow(label: String, paise: Long, minus: Boolean = false, bold: Boolean = false, tag: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier), verticalAlignment = Alignment.CenterVertically) {
        SideOrStacked(
            Modifier.weight(1f),
            start = { Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium) },
            end = {
                Text(
                    (if (minus && paise > 0) "−" else "") + Money.formatInr(paise),
                    style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    color = if (minus && paise > 0) Color(0xFF2E7D4F) else MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        trailing?.invoke()
    }
}

@Composable
private fun LastBill(vm: AppViewModel, label: String, bill: CardBill, today: LocalDate) {
    var adding by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val overdue = !bill.paidInFull && today.isAfter(bill.dueDate)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LocalBrand.current.clay.pressed).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Still to pay", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            if (bill.paidInFull) "Paid in full ✓" else Money.formatInr(bill.stillToPayPaise),
            style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("still_to_pay_amount"),
        )
        Text(
            "Bill of ${d(bill.last.end)} · " + when {
                bill.paidInFull -> "nothing left to pay"
                overdue -> "was due ${bill.dueDate.format(DAY_W)}"
                else -> "pay by ${bill.dueDate.format(DAY_W)}"
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Divider()
        AmountRow("Charged, ${d(bill.last.start)} – ${d(bill.last.end)}", bill.last.chargedPaise)
        if (bill.last.refundsPaise > 0) AmountRow("Refunds back to the card", bill.last.refundsPaise, minus = true)
        AmountRow("This bill", bill.last.netPaise, bold = true, tag = "bill_amount")
        bill.payments.forEach { p ->
            val on = vm.dateOf(p.atMs)
            AmountRow(
                "Paid ${d(on)} · " + if (p.txnId != null) "from the bank's SMS" else "added by you",
                p.paise, minus = true, tag = "bill_payment_${p.manualIndex ?: ("sms" + p.txnId)}",
            ) {
                if (p.manualIndex != null) {
                    IconButton(onClick = { vm.removeCardPayment(label, p.manualIndex) }, modifier = Modifier.size(36.dp)) {
                        Icon(Ic.close, contentDescription = "Remove this payment", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (bill.markedPaid) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Marked as paid in full by you", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                val idx = CardBills.paymentsFromJson(vm.settings.value?.cardPayments)[label].orEmpty().indexOfLast { it.full && it.bill == bill.last.end }
                TextButton(onClick = { if (idx >= 0) vm.removeCardPayment(label, idx) }, modifier = Modifier.testTag("bill_unmark_paid")) { Text("Undo") }
            }
        }
        Divider()
        AmountRow("Still to pay", bill.stillToPayPaise, bold = true)
        if (adding) AmountEntry(bill.stillToPayPaise, onDismiss = { adding = false }) { paise ->
            adding = false
            vm.addCardPayment(label, bill.last.end, paise, full = false)
        }
        if (!bill.paidInFull && !adding) {
            FlowRow2 {
                Button(onClick = { vm.addCardPayment(label, bill.last.end, 0, full = true) }, modifier = Modifier.testTag("bill_mark_paid")) { Text("Mark as fully paid") }
                FilledTonalButton(onClick = { adding = true }, modifier = Modifier.testTag("bill_add_payment")) { Text("Add a payment") }
            }
        }
        if (bill.last.charges == 0 && bill.last.chargedPaise == 0L) {
            Text("No payments on this card in that bill period.", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun ThisBill(p: BillPeriod, nextBill: LocalDate) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("this_bill"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("On the next bill so far", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        AmountRow("Charged, ${d(p.start)} – today", p.chargedPaise)
        if (p.refundsPaise > 0) AmountRow("Refunds", p.refundsPaise, minus = true)
        AmountRow("Bill on ${nextBill.format(DAY_W)}, so far", p.netPaise, bold = true)
    }
}

/** Bill date (1–28 or the last day) and days until it's due, with what that makes this bill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleDialog(label: String, now: CardCycle?, today: LocalDate, onDismiss: () -> Unit, onSave: (CardCycle?) -> Unit) {
    var day by remember { mutableIntStateOf(now?.billDay ?: 0) }
    var due by remember { mutableIntStateOf(now?.dueDays ?: 20) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("bill_cycle_dialog"),
        title = { Text("Billing cycle · $label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bill (statement) date, each month", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ((1..28).toList() + CardCycle.LAST_DAY).forEach { n ->
                        val on = day == n
                        Box(
                            Modifier.widthIn(min = 40.dp).size(height = 40.dp, width = if (n == CardCycle.LAST_DAY) 84.dp else 40.dp)
                                .clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else LocalBrand.current.clay.pressed)
                                .clickable(onClickLabel = if (n == CardCycle.LAST_DAY) "The last day of the month" else "The ${CardCycle.ordinal(n)}") { day = n }
                                .testTag("bill_day_$n"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (n == CardCycle.LAST_DAY) "Last day" else "$n", textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Text("Payment due", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { due = (due - 1).coerceAtLeast(1) }, modifier = Modifier.testTag("bill_due_minus")) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text("$due days after the bill", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).testTag("bill_due_days"))
                    IconButton(onClick = { due = (due + 1).coerceAtMost(60) }, modifier = Modifier.testTag("bill_due_plus")) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
                if (day != 0) {
                    val c = CardCycle(day, due)
                    val last = c.lastBillOnOrBefore(today)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalBrand.current.clay.pressed).padding(10.dp).testTag("bill_cycle_preview"),
                    ) {
                        Text("Last bill: ${d(c.billBefore(last).plusDays(1))} – ${d(last)}, pay by ${last.plusDays(due.toLong()).format(DAY_W)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Next bill: ${c.nextBillAfter(last).format(DAY_W)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Pick the day your bill is made.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = day != 0, onClick = { onSave(CardCycle(day, due)) }, modifier = Modifier.testTag("bill_cycle_save")) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (now != null) TextButton(onClick = { onSave(null) }, modifier = Modifier.testTag("bill_cycle_remove")) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** A payment's amount in rupees, typed in the bill card; starts empty, with what's left above it. */
@Composable
private fun AmountEntry(left: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    val rupees = text.toLongOrNull() ?: 0
    Column(Modifier.fillMaxWidth().testTag("bill_payment_dialog"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("How much did you pay towards this bill? ${Money.formatInr(left)} is left.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = text, onValueChange = { v -> text = v.filter(Char::isDigit).take(9) },
            prefix = { Text("₹") }, singleLine = true, placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("bill_payment_amount"),
        )
        FlowRow2 {
            Button(enabled = rupees > 0, onClick = { onSave(rupees * 100) }, modifier = Modifier.testTag("bill_payment_save")) { Text("Add payment") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

/** What it was used for | Still to pay, for a credit card opened on Paid by. */
@Composable
fun CardTabs(bill: Boolean, onBill: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalBrand.current.clay.pressed).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf("What it was used for" to false, "Still to pay" to true).forEach { (text, isBill) ->
            val on = bill == isBill
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(onClickLabel = "Show $text") { onBill(isBill) }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag(if (isBill) "card_tab_bill" else "card_tab_used"),
                contentAlignment = Alignment.Center,
            ) {
                com.owl.tracker.ui.components.FitText(
                    text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, minScale = 0.75f,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
