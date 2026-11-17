package com.owl.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.PaidAccount
import com.owl.tracker.ui.PaidBy
import com.owl.tracker.ui.PaidKind
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.components.Divider
import com.owl.tracker.ui.components.FitText
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.IconTile
import com.owl.tracker.ui.components.MerchantAvatar
import com.owl.tracker.ui.components.SideOrStacked
import com.owl.tracker.ui.components.animatedPaise
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalBrand

/*
 * Home's "Where it went", third tab: Paid by. The month by the card or bank account it was paid from (the bank and
 * the last 4 digits its SMS gives). A ring with a slice each, a row each; one opened shows what it was used for, by
 * group, and a group's row opens its payments from that card. Round 22 on the design canvas.
 */

private val PAID_COLORS = listOf(0xFF2F5FB3, 0xFF1E8E7E, 0xFFC2571A, 0xFF7A4FC0, 0xFFB5892A, 0xFFC23F73, 0xFF4F8A9E).map { Color(it) }
private val BY_HAND_COLOR = Color(0xFF8A7A66)
private val UNKNOWN_COLOR = Color(0xFFB9A58F)

private fun colorOf(a: PaidAccount, index: Int): Color = when (a.kind) {
    PaidKind.BY_HAND -> BY_HAND_COLOR
    PaidKind.UNKNOWN -> UNKNOWN_COLOR
    else -> PAID_COLORS[index % PAID_COLORS.size]
}

/** A flat ring: a slice for each share, with what it's about in the middle. */
@Composable
private fun PaidRing(slices: List<Pair<Color, Long>>, description: String, top: String, amount: Long, bottom: String) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp).semantics { contentDescription = description }) {
            val stroke = size.minDimension * 0.13f
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            val gap = if (slices.size > 1) 2f else 0f
            var at = -90f
            slices.forEach { (color, paise) ->
                val sweep = paise.toFloat() / total * 360f
                if (sweep > gap) drawArc(color, at + gap / 2f, sweep - gap, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
                at += sweep
            }
        }
        Column(Modifier.size(128.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            FitText(top, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minScale = 0.7f)
            FitText(Money.formatInr(animatedPaise(amount, 450)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, minScale = 0.6f)
            FitText(bottom, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, minScale = 0.7f)
        }
    }
}

@Composable
private fun AccountTile(vm: AppViewModel, a: PaidAccount, color: Color, size: androidx.compose.ui.unit.Dp) {
    val bank = remember(a.senderGroup) { vm.appIcons.bankApp(a.senderGroup) }
    when {
        a.kind == PaidKind.BY_HAND -> IconTile(Ic.wallet, color, color.copy(alpha = 0.18f), size)
        a.kind == PaidKind.UNKNOWN -> IconTile(Ic.info, color, color.copy(alpha = 0.18f), size)
        bank != null -> MerchantAvatar(vm.appIcons, bank, a.name, null, size)
        else -> IconTile(if (a.kind == PaidKind.ACCOUNT) Ic.bank else Ic.card, color, color.copy(alpha = 0.18f), size)
    }
}

/**
 * [opened]: the key of the card or account opened, if any. [onOpen] opens one, or goes back with null.
 */
@Composable
fun PaidByView(vm: AppViewModel, accounts: List<PaidAccount>, opened: String?, cats: List<Category>, onOpen: (String?) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val total = accounts.sumOf { it.paise }.coerceAtLeast(1)
    val index = accounts.indexOfFirst { it.key == opened }
    val open = accounts.getOrNull(index)
    Column(Modifier.fillMaxWidth().testTag("paid_by")) {
        if (accounts.isEmpty()) {
            Text("Nothing spent yet. When there is, it's listed here by the card or account it was paid from.", style = MaterialTheme.typography.bodyMedium, color = muted)
            return@Column
        }
        if (open == null) {
            val named = accounts.count { it.kind == PaidKind.CARD || it.kind == PaidKind.ACCOUNT }
            PaidRing(
                accounts.mapIndexed { i, a -> colorOf(a, i) to a.paise },
                "Spending by card and account: " + accounts.joinToString { "${it.name} ${it.paise * 100 / total}%" },
                "Paid by", accounts.sumOf { it.paise },
                if (named == 0) "no card or account named" else "$named ${if (named == 1) "card or account" else "cards and accounts"}",
            )
            Gap(8.dp)
            // The kinds' shares, then a section each: credit cards, debit cards, bank accounts, then the rest
            val byKind = accounts.withIndex().groupBy { it.value.kind }
            KindShareBar(byKind.mapValues { (_, l) -> l.sumOf { it.value.paise } }, total)
            var shut by rememberSaveable { mutableStateOf(setOf<String>()) }
            PaidKind.entries.forEach { kind ->
                val list = byKind[kind] ?: return@forEach
                val isShut = kind.key in shut
                Gap(10.dp)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = if (isShut) "Show ${kind.heading}" else "Hide ${kind.heading}") { shut = if (isShut) shut - kind.key else shut + kind.key }
                        .padding(vertical = 8.dp, horizontal = 6.dp).testTag("paid_section_${kind.key}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(KIND_COLORS.getValue(kind)))
                    Column(Modifier.weight(1f)) {
                        Text(kind.heading.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(KIND_NOTES.getValue(kind), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    Text(Money.formatInr(list.sumOf { it.value.paise }), style = MaterialTheme.typography.titleSmall)
                    Icon(Ic.chevR, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp).rotate(if (isShut) 0f else 90f))
                }
                if (!isShut) list.forEach { (i, a) ->
                    Divider()
                    AccountRow(vm, a, i, total, onOpen)
                }
            }
            Gap(8.dp)
            Text(
                "The digits are the last 4 in the bank's SMS. Money moved between your own accounts isn't counted.",
                style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(horizontal = 6.dp),
            )
        } else {
            val color = colorOf(open, index)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Back to all cards and accounts") { onOpen(null) }
                    .padding(vertical = 4.dp).testTag("paid_up"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.back, contentDescription = "Back to all cards and accounts", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                AccountTile(vm, open, color, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(open.name, style = MaterialTheme.typography.titleMedium)
                    Text("${open.payments.size} ${if (open.payments.size == 1) "payment" else "payments"} · what it was used for", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            // What it is (credit card, debit card or bank account), and a way to correct it
            if (open.kind.named) PaidKindChoice(vm, open.key, open.kind, open.guessed, Modifier.padding(start = 30.dp))
            Gap(12.dp)
            PaidRing(
                open.tops.map { Color(it.category.color) to it.paise },
                "${open.name} by group: " + open.tops.joinToString { "${it.category.name} ${it.paise * 100 / open.paise.coerceAtLeast(1)}%" },
                open.name, open.paise, "${open.paise * 100 / total}% of spending",
            )
            Gap(8.dp)
            var openTop by rememberSaveable(open.key) { mutableStateOf<String?>(null) }
            open.tops.forEachIndexed { i, cs ->
                if (i > 0) Divider()
                val shown = openTop == cs.category.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .then(if (shown) Modifier.background(LocalBrand.current.clay.pressed) else Modifier)
                        .clickable(onClickLabel = if (shown) "Hide its payments" else "Show its payments") { openTop = if (shown) null else cs.category.id }
                        .padding(vertical = 8.dp, horizontal = 6.dp).testTag("paid_top_${cs.category.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CategoryTile(cs.category, 36.dp)
                    SideOrStacked(
                        Modifier.weight(1f),
                        start = {
                            Column {
                                Text(cs.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${cs.paise * 100 / open.paise.coerceAtLeast(1)}% of this ${if (open.kind == PaidKind.CREDIT_CARD || open.kind == PaidKind.DEBIT_CARD) "card" else "one"}", style = MaterialTheme.typography.bodySmall, color = muted)
                            }
                        },
                        end = { Text(Money.formatInr(cs.paise), style = MaterialTheme.typography.titleSmall) },
                    )
                    Icon(Ic.chevR, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp).rotate(if (shown) 270f else 90f))
                }
                if (shown) {
                    val catMap = remember(cats) { cats.associateBy { it.id } }
                    val list = remember(open, cs.category.id, cats) { PaidBy.paymentsIn(open, cs.category.id, cats) }
                    Column(Modifier.padding(start = 8.dp).testTag("paid_payments_${cs.category.id}")) {
                        list.forEach { t -> TxnRow(vm, t, catMap[t.categoryId], showDay = true) }
                    }
                }
            }
        }
    }
}

private val KIND_COLORS = mapOf(
    PaidKind.CREDIT_CARD to Color(0xFF2F5FB3), PaidKind.DEBIT_CARD to Color(0xFFB5892A), PaidKind.ACCOUNT to Color(0xFF1E8E7E),
    PaidKind.BY_HAND to BY_HAND_COLOR, PaidKind.UNKNOWN to UNKNOWN_COLOR,
)
private val KIND_NOTES = mapOf(
    PaidKind.CREDIT_CARD to "Paid by the bank now, you pay it back on the bill",
    PaidKind.DEBIT_CARD to "Straight from your bank account",
    PaidKind.ACCOUNT to "UPI, net banking and transfers",
    PaidKind.BY_HAND to "Added in the app, with no card or account",
    PaidKind.UNKNOWN to "The SMS didn't say which card or account",
)

/** One bar split by kind, with its legend. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun KindShareBar(byKind: Map<PaidKind, Long>, total: Long) {
    val kinds = PaidKind.entries.filter { (byKind[it] ?: 0) > 0 }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp).testTag("paid_kinds_bar")) {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
            kinds.forEach { k -> Box(Modifier.weight((byKind.getValue(k).toFloat() / total).coerceAtLeast(0.01f)).fillMaxSize().background(KIND_COLORS.getValue(k))) }
        }
        Gap(6.dp)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            kinds.forEach { k ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(KIND_COLORS.getValue(k)))
                    Text("${k.heading} ${Money.formatInr(byKind.getValue(k))}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AccountRow(vm: AppViewModel, a: PaidAccount, i: Int, total: Long, onOpen: (String?) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "See what ${a.name} was used for") { onOpen(a.key) }
            .padding(vertical = 8.dp, horizontal = 6.dp).testTag("paid_row_$i"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountTile(vm, a, colorOf(a, i), 40.dp)
        SideOrStacked(
            Modifier.weight(1f),
            start = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(colorOf(a, i)))
                        // Never cut: the digits at the end are what tells one card from another
                        Text(a.name, style = MaterialTheme.typography.titleSmall)
                    }
                    Text("${a.paise * 100 / total}% · ${a.note}" + if (a.changed) " · set by you" else "", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            },
            end = { Text(Money.formatInr(a.paise), style = MaterialTheme.typography.titleSmall) },
        )
        Icon(Ic.chevR, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
    }
}

/**
 * "Credit card · Change": what a card or account is, and a choice to correct it. Applies to all of [label]'s payments.
 * [guessed] is what the app works out from the SMS; [current] what it is now.
 */
@Composable
fun PaidKindChoice(vm: AppViewModel, label: String, current: PaidKind, guessed: PaidKind, modifier: Modifier = Modifier) {
    var asking by remember { mutableStateOf(false) }
    androidx.compose.material3.TextButton(onClick = { asking = true }, modifier = modifier.testTag("paid_kind_change")) {
        Text("${current.label}${if (current != guessed) " (set by you)" else ""} · Change", style = MaterialTheme.typography.labelLarge)
    }
    if (asking) {
        var picked by remember { mutableStateOf(current) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            modifier = Modifier.testTag("paid_kind_dialog"),
            title = { Text("What is $label?") },
            text = {
                Column {
                    Text("This applies to all its payments, past and new.", style = MaterialTheme.typography.bodyMedium)
                    Gap(8.dp)
                    PaidKind.CHOICES.forEach { k ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable(onClickLabel = "Choose ${k.label}") { picked = k }
                                .padding(vertical = 4.dp).testTag("paid_kind_${k.key}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = picked == k, onClick = { picked = k })
                            Column(Modifier.weight(1f)) {
                                Text(k.label + if (k == guessed) " (what the SMS suggests)" else "", style = MaterialTheme.typography.bodyLarge)
                                Text(KIND_NOTES.getValue(k), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { asking = false; if (picked != current) vm.setPaidKind(label, if (picked == guessed) null else picked) },
                    modifier = Modifier.testTag("paid_kind_save"),
                ) { Text("Save") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
}
