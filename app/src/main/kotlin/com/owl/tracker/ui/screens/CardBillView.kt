package com.owl.tracker.ui.screens

import com.owl.tracker.i18n.tr
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.owl.tracker.ui.CardCredit
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
                Text(tr("Set this card's billing cycle"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("The bill date tells which bill each payment goes on. It's on your card statement as \"Statement date\", with the \"Payment due date\"."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { editing = true }, modifier = Modifier.testTag("bill_cycle_set")) { Text(tr("Set the bill date")) }
            }
        } else {
            val period by vm.periodTxns.collectAsStateWithLifecycle()
            val today = vm.today()
            val from = cycle.billBefore(cycle.lastBillOnOrBefore(today)).minusDays(1)
            val txns by produceState<List<Txn>?>(null, label, cycle, period, settings?.cardPayments) { value = vm.txnsSince(from) }
            var checking by remember { mutableStateOf(false) }
            val list = txns
            if (list == null) {
                Text(tr("Working it out…"), style = MaterialTheme.typography.bodySmall)
            } else {
                val manual = remember(settings?.cardPayments) { CardBills.paymentsFromJson(settings?.cardPayments)[label].orEmpty() }
                val kinds = remember(settings?.cardCreditKinds) { CardBills.creditKindsFromJson(settings?.cardCreditKinds) }
                val bill = remember(list, manual, cycle, today, kinds) { CardBills.compute(label, cycle, list, manual, today, vm.timeZone, kinds) }
                LastBill(vm, label, bill, today, onCheck = { checking = true })
                ThisBill(bill.current, bill.nextBill, bill.last.end, onCheck = { checking = true })
                if (bill.credits.isNotEmpty()) {
                    TextButton(onClick = { checking = true }, modifier = Modifier.testTag("card_credits_open")) {
                        Text(tr("Refunds and payments on this card ({0}) ›", bill.credits.size))
                    }
                }
                if (checking) CreditsDialog(vm, bill, onDismiss = { checking = false })
            }
            // The cycle, and a way to change it
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = tr("Change the billing cycle")) { editing = true }
                    .padding(vertical = 8.dp, horizontal = 4.dp).testTag("bill_cycle_change"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.calendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Billing cycle: bill on {0}", cycle.dayWords), style = MaterialTheme.typography.titleSmall)
                    Text(tr("Due {0} days after the bill", cycle.dueDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(tr("Change"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                tr("Worked out from your SMS and the payments you add here. Your card statement is the final word."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (editing) CycleDialog(label, cycle, vm.today(), onDismiss = { editing = false }) { c -> editing = false; vm.setCardCycle(label, c) }
}

@Composable
private fun AmountRow(
    label: String, paise: Long, minus: Boolean = false, bold: Boolean = false, tag: String? = null,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = tr("Check refunds and payments"), onClick = onClick) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
private fun LastBill(vm: AppViewModel, label: String, bill: CardBill, today: LocalDate, onCheck: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val overdue = !bill.paidInFull && today.isAfter(bill.dueDate)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LocalBrand.current.clay.pressed).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(tr("Still to pay"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            if (bill.paidInFull) tr("Paid in full ✓") else Money.formatInr(bill.stillToPayPaise),
            style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("still_to_pay_amount"),
        )
        Text(
            tr("Bill of {0} · ", d(bill.last.end)) + when {
                bill.paidInFull -> tr("nothing left to pay")
                overdue -> tr("was due {0}", bill.dueDate.format(DAY_W))
                else -> tr("pay by {0}", bill.dueDate.format(DAY_W))
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Divider()
        AmountRow(tr("Charged, {0} – {1}", d(bill.last.start), d(bill.last.end)), bill.last.chargedPaise)
        if (bill.last.refundsPaise > 0) AmountRow(tr("Refunds back to the card ›"), bill.last.refundsPaise, minus = true, tag = "bill_refunds", onClick = onCheck)
        AmountRow(tr("This bill"), bill.last.netPaise, bold = true, tag = "bill_amount")
        val onCardIds = bill.credits.map { it.txn.id }.toSet()
        bill.payments.forEach { p ->
            val on = vm.dateOf(p.atMs)
            val onCard = p.txnId != null && p.txnId in onCardIds
            AmountRow(
                tr("Paid {0} · ", d(on)) + when { p.txnId == null -> tr("added by you"); onCard -> tr("from the card's SMS ›"); else -> tr("from the bank's SMS") },
                p.paise, minus = true, tag = "bill_payment_${p.manualIndex ?: ("sms" + p.txnId)}",
                onClick = if (onCard) onCheck else null,
            ) {
                if (p.manualIndex != null) {
                    IconButton(onClick = { vm.removeCardPayment(label, p.manualIndex) }, modifier = Modifier.size(36.dp)) {
                        Icon(Ic.close, contentDescription = tr("Remove this payment"), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (bill.markedPaid) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Marked as paid in full by you"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.unmarkCardPaid(label, bill.last.end) }, modifier = Modifier.testTag("bill_unmark_paid")) { Text(tr("Undo")) }
            }
        }
        Divider()
        AmountRow(tr("Still to pay"), bill.stillToPayPaise, bold = true)
        if (adding) AmountEntry(bill.stillToPayPaise, onDismiss = { adding = false }) { paise ->
            adding = false
            vm.addCardPayment(label, bill.last.end, paise, full = false)
        }
        if (!bill.paidInFull && !adding) {
            FlowRow2 {
                Button(onClick = { vm.addCardPayment(label, bill.last.end, 0, full = true) }, modifier = Modifier.testTag("bill_mark_paid")) { Text(tr("Mark as fully paid")) }
                FilledTonalButton(onClick = { adding = true }, modifier = Modifier.testTag("bill_add_payment")) { Text(tr("Add a payment")) }
            }
        }
        if (bill.last.charges == 0 && bill.last.chargedPaise == 0L) {
            Text(tr("No payments on this card in that bill period."), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun ThisBill(p: BillPeriod, nextBill: LocalDate, lastBill: LocalDate, onCheck: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("this_bill"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("On the next bill so far"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        AmountRow(tr("Charged, {0} – today", d(p.start)), p.chargedPaise)
        if (p.refundsPaise > 0) AmountRow(tr("Refunds ›"), p.refundsPaise, minus = true, tag = "next_refunds", onClick = onCheck)
        if (p.extraPaidPaise > 0) AmountRow(tr("Paid extra on the {0} bill", d(lastBill)), p.extraPaidPaise, minus = true, tag = "next_extra_paid")
        AmountRow(tr("Bill on {0}, so far", nextBill.format(DAY_W)), p.netPaise, bold = true)
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
        title = { Text(tr("Billing cycle · {0}", label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Bill (statement) date, each month"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ((1..28).toList() + CardCycle.LAST_DAY).forEach { n ->
                        val on = day == n
                        Box(
                            Modifier.widthIn(min = 40.dp).size(height = 40.dp, width = if (n == CardCycle.LAST_DAY) 84.dp else 40.dp)
                                .clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else LocalBrand.current.clay.pressed)
                                .clickable(onClickLabel = if (n == CardCycle.LAST_DAY) tr("The last day of the month") else tr("The {0}", CardCycle.ordinal(n))) { day = n }
                                .testTag("bill_day_$n"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (n == CardCycle.LAST_DAY) tr("Last day") else "$n", textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Text(tr("Payment due"), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { due = (due - 1).coerceAtLeast(1) }, modifier = Modifier.testTag("bill_due_minus")) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(tr("{0} days after the bill", due), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).testTag("bill_due_days"))
                    IconButton(onClick = { due = (due + 1).coerceAtMost(60) }, modifier = Modifier.testTag("bill_due_plus")) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
                if (day != 0) {
                    val c = CardCycle(day, due)
                    val last = c.lastBillOnOrBefore(today)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalBrand.current.clay.pressed).padding(10.dp).testTag("bill_cycle_preview"),
                    ) {
                        Text(tr("Last bill: {0} – {1}, pay by {2}", d(c.billBefore(last).plusDays(1)), d(last), last.plusDays(due.toLong()).format(DAY_W)), style = MaterialTheme.typography.bodyMedium)
                        Text(tr("Next bill: {0}", c.nextBillAfter(last).format(DAY_W)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(tr("Pick the day your bill is made."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = day != 0, onClick = { onSave(CardCycle(day, due)) }, modifier = Modifier.testTag("bill_cycle_save")) { Text(tr("Save")) }
        },
        dismissButton = {
            Row {
                if (now != null) TextButton(onClick = { onSave(null) }, modifier = Modifier.testTag("bill_cycle_remove")) { Text(tr("Remove")) }
                TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
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
        Text(tr("How much did you pay towards this bill? {0} is left.", Money.formatInr(left)), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = text, onValueChange = { v -> text = v.filter(Char::isDigit).take(9) },
            prefix = { Text("₹") }, singleLine = true, placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("bill_payment_amount"),
        )
        FlowRow2 {
            Button(enabled = rupees > 0, onClick = { onSave(rupees * 100) }, modifier = Modifier.testTag("bill_payment_save")) { Text(tr("Add payment")) }
            TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
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
        listOf(tr("What it was used for") to false, tr("Still to pay") to true).forEach { (text, isBill) ->
            val on = bill == isBill
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(onClickLabel = tr("Show {0}", text)) { onBill(isBill) }
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

/** Every credit on the card since the last bill period began, each a refund or a bill payment, correctable. */
@Composable
private fun CreditsDialog(vm: AppViewModel, bill: CardBill, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("card_credits_dialog"),
        title = { Text(tr("Refunds and payments")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    tr("Money back on this card since {0}. A refund lowers the bill it lands on; a payment pays the bill before it.", d(bill.last.start)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                bill.credits.forEach { c ->
                    val t = c.txn
                    Column(Modifier.fillMaxWidth().testTag("credit_${t.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${d(vm.dateOf(t.occurredAt))} · ${t.merchantName}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(Money.formatInr(t.amountPaise), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            when (c.why) {
                                CardCredit.Why.YOU -> tr("Set by you")
                                CardCredit.Why.SMS_SAYS_PAYMENT -> tr("The SMS says it's a payment")
                                CardCredit.Why.MATCHES_ACCOUNT_PAYMENT -> tr("Same amount as a card bill payment from your bank account")
                                CardCredit.Why.LOOKS_LIKE_REFUND -> tr("Taken as a refund")
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalBrand.current.clay.pressed).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            listOf(false to tr("Refund"), true to tr("Payment")).forEach { (pay, text) ->
                                val on = c.payment == pay
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                                        .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                                        .clickable(onClickLabel = tr("Count as a {0}", text.lowercase())) { if (!on) vm.setCardCreditKind(t.id, pay) }
                                        .padding(vertical = 10.dp)
                                        .testTag(if (pay) "credit_payment_${t.id}" else "credit_refund_${t.id}"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        (if (on) "✓ " else "") + text, style = MaterialTheme.typography.labelLarge,
                                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (c.why == CardCredit.Why.YOU) {
                            TextButton(onClick = { vm.setCardCreditKind(t.id, null) }, modifier = Modifier.testTag("credit_auto_${t.id}")) {
                                Text(tr("Go back to what the SMS says"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("card_credits_done")) { Text(tr("Done")) } },
    )
}
