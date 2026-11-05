package com.owl.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.licence.Access
import com.owl.tracker.licence.StoreState
import com.owl.tracker.licence.UnlockStore
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.Platform
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.InfoNote
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalStatusColors
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the unlock includes, shown on the unlock screen. */
private val INCLUDED = listOf(
    "Automatic import of bank, card and UPI SMS",
    "Monthly budgets with over-spend warnings",
    "Your own categories and “always use” rules",
    "Every future update",
)

@Composable
fun UnlockScreen(vm: AppViewModel, platform: Platform) {
    val access by vm.access.collectAsStateWithLifecycle()
    val store by vm.store.collectAsStateWithLifecycle()
    val price = store.price ?: UnlockStore.FALLBACK_PRICE
    val sc = LocalStatusColors.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("unlock_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Unlock Owl Tracker", onBack = { vm.back() })

        when (val a = access) {
            Access.Unlocked -> StatusCard(
                Ic.check, sc.okFg, sc.okBg, "Owl Tracker is unlocked",
                "Thank you! It's yours for good, including on a new phone signed in to the same Google account.",
                Modifier.testTag("unlock_done"),
            )
            is Access.Trial -> StatusCard(
                Ic.calendar, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer,
                "Free trial: ${daysText(a.daysLeft)} left",
                "Ends on ${vm.dateOf(a.endsAt).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))}. After that Owl Tracker turns read-only: " +
                    "everything stays visible, but nothing new comes in until you unlock it.",
                Modifier.testTag("unlock_trial"),
            )
            Access.TrialEnded -> StatusCard(
                Ic.lock, sc.warnFg, sc.warnBg, "Your free trial has ended",
                "Everything you've imported is still here to see. Unlock to import new SMS again, add expenses and change budgets.",
                Modifier.testTag("unlock_ended"),
            )
        }

        if (access != Access.Unlocked) {
            Panel {
                Text("One payment of $price", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("unlock_price"))
                Text("No subscription, no ads, no account.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(12.dp)
                INCLUDED.forEach { line ->
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (store.pending) {
                InfoNote(
                    "Payment pending. Google Play will confirm it, which can take a few minutes with UPI. Owl Tracker unlocks by itself as soon as it does.",
                    icon = Ic.info, container = sc.warnBg, content = sc.warnFg,
                )
            }
            if (store.status == StoreState.Status.CONNECTING && store.message == null) {
                Text(
                    "Connecting to Google Play\u2026", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("unlock_connecting"),
                )
            }
            store.message?.let { msg ->
                Column(Modifier.testTag("unlock_message")) { InfoNote(msg, icon = Ic.alert, container = sc.overBg, content = sc.overFg) }
            }

            val available = store.status != StoreState.Status.UNAVAILABLE
            Button(
                onClick = { if (available) platform.buyUnlock() else vm.restorePurchase() },
                enabled = !store.busy && !store.pending,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("unlock_buy"),
            ) {
                Text(if (available) "Unlock for $price" else "Try Google Play again", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { vm.restorePurchase() }, modifier = Modifier.fillMaxWidth().testTag("unlock_restore")) {
                Text("Already paid? Restore purchase")
            }
        }

        InfoNote(
            "Google Play takes the payment. Owl Tracker never sees your card or UPI details, and it still has no internet access: " +
                "the Play Store app on your phone confirms the purchase to it.",
            icon = Ic.noNet, container = MaterialTheme.colorScheme.surfaceVariant, content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(icon: ImageVector, fg: Color, bg: Color, title: String, body: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
            Gap(2.dp)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = fg)
        }
    }
}

fun daysText(n: Int): String = if (n == 1) "1 day" else "$n days"
