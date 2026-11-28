package com.owl.tracker.ui.screens

import com.owl.tracker.i18n.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.core.Money
import com.owl.tracker.data.Category
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.Sheet
import com.owl.tracker.ui.components.categoryArt
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Was this right?" for a new payment: its category (tap to confirm) or one of three others, "More…" for the full
 * list, and a switch to always use the choice for that merchant.
 */
@Composable
fun QuickCheckSheet(vm: AppViewModel, txId: Long) {
    val txFlow = remember(txId) { vm.txnFlow(txId) }
    val t by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val choices by produceState(emptyList<Category>(), txId) { value = vm.checkChoices(txId) }
    var always by rememberSaveable(txId) { mutableStateOf(false) }
    val tx = t ?: return
    val current = choices.firstOrNull()
    Column(Modifier.testTag("quick_check"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("New payment"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tx.merchantName, style = MaterialTheme.typography.titleLarge)
                val time = Instant.ofEpochMilli(tx.occurredAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a", com.owl.tracker.i18n.I18n.locale))
                Text((tx.accountLabel?.let { "$it · " } ?: "") + time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Money.formatInr(tx.amountPaise), style = MaterialTheme.typography.headlineSmall)
        }
        Text(current?.let { tr("Was it {0}?", it.name) } ?: tr("What was it?"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("quick_check_question"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEachIndexed { i, c ->
                val first = i == 0
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.checkAnswer(txId, c.id, always) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                        .testTag(if (first) "quick_check_yes" else "quick_check_${c.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categoryArt(c.icon)?.let { Image(painterResource(it), null, Modifier.size(22.dp)) }
                    Text(
                        c.name + if (first) " ✓" else "", fontWeight = FontWeight.ExtraBold,
                        color = if (first) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                tr("More…"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.openSheet(Sheet.CategoryPick(txId)) }.padding(horizontal = 14.dp, vertical = 9.dp).testTag("quick_check_more"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(always, { always = it }, modifier = Modifier.testTag("quick_check_always"))
            Text(tr("Always use this for {0}", tx.merchantName), style = MaterialTheme.typography.bodyMedium)
        }
        Text(tr("With Always on, the owl sorts this merchant the same way from now on."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
