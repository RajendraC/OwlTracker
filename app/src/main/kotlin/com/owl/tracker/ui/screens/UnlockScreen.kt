package com.owl.tracker.ui.screens

import com.owl.tracker.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.setValue
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
import androidx.compose.material3.OutlinedButton
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

/** What the subscription includes, shown on the unlock screen. */
private val INCLUDED = listOf(
    tr("Automatic import of bank, card and UPI SMS"),
    tr("Monthly budgets with over-spend warnings"),
    tr("Your own categories and “always use” rules"),
    tr("Every future update"),
)

@Composable
fun UnlockScreen(vm: AppViewModel, platform: Platform) {
    val access by vm.access.collectAsStateWithLifecycle()
    val store by vm.store.collectAsStateWithLifecycle()
    val price = store.price ?: UnlockStore.FALLBACK_PRICE
    val lifePrice = store.lifetimePrice ?: UnlockStore.FALLBACK_LIFETIME_PRICE
    val lifetime by vm.lifetime.collectAsStateWithLifecycle()
    var plan by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(UnlockStore.Plan.YEARLY) }
    val sc = LocalStatusColors.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("unlock_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(tr("Unlock Owl Tracker"), onBack = { vm.back() })
        val context = androidx.compose.ui.platform.LocalContext.current
        val manage = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(UnlockStore.manageUrl(context.packageName)))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Unit
        }

        when (val a = access) {
            Access.Unlocked -> if (lifetime) {
                StatusCard(
                    Ic.check, sc.okFg, sc.okBg, tr("Lifetime · paid once"),
                    tr("Thank you! Owl Tracker is yours for good on this Google account, including on a new phone. Nothing more to pay."),
                    Modifier.testTag("unlock_lifetime_done"),
                )
                // Bought lifetime while subscribed: the subscription would keep charging until cancelled
                if (store.renewing == true) {
                    InfoNote(
                        tr("You also have the yearly subscription. Cancel it in Google Play so it doesn't renew; you don't need it any more."),
                        icon = Ic.info, container = sc.warnBg, content = sc.warnFg,
                    )
                    OutlinedButton(onClick = manage, modifier = Modifier.fillMaxWidth().testTag("unlock_manage")) {
                        Text(tr("Cancel the subscription in Google Play"))
                    }
                }
            } else {
                StatusCard(
                    Ic.check, sc.okFg, sc.okBg,
                    if (store.renewing == false) tr("Subscribed · won't renew") else tr("Subscribed · {0} a year", price),
                    if (store.renewing == false) tr("You cancelled it, so it stops at the end of the year you paid for. Subscribe again in Google Play any time before then to keep it going.")
                    else tr("Thank you! It renews every year until you cancel, and works on a new phone signed in to the same Google account."),
                    Modifier.testTag("unlock_done"),
                )
                OutlinedButton(onClick = manage, modifier = Modifier.fillMaxWidth().testTag("unlock_manage")) {
                    Text(tr("Manage or cancel in Google Play"))
                }
                // A subscriber can move to paying once
                Panel {
                    Text(tr("Rather pay once?"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("{0} once and Owl Tracker is yours for good. After buying it, cancel the yearly subscription in Google Play.", lifePrice),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Gap(10.dp)
                    OutlinedButton(
                        onClick = { platform.buyUnlock(UnlockStore.Plan.LIFETIME) },
                        enabled = !store.busy && !store.pending && store.status != StoreState.Status.UNAVAILABLE,
                        modifier = Modifier.fillMaxWidth().testTag("unlock_switch_lifetime"),
                    ) { Text(tr("Pay {0} once", lifePrice)) }
                }
            }
            is Access.Trial -> StatusCard(
                Ic.calendar, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer,
                tr("Free trial: {0} left", daysText(a.daysLeft)),
                tr("Ends on {0}. After that Owl Tracker turns read-only: ", vm.dateOf(a.endsAt).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))) +
                    tr("everything stays visible, but nothing new comes in until you unlock it."),
                Modifier.testTag("unlock_trial"),
            )
            Access.TrialEnded -> StatusCard(
                Ic.lock, sc.warnFg, sc.warnBg, tr("Your free trial has ended"),
                tr("Everything you've imported is still here to see. Subscribe, or pay once, to import new SMS again, add expenses and change budgets."),
                Modifier.testTag("unlock_ended"),
            )
            Access.Lapsed -> StatusCard(
                Ic.lock, sc.warnFg, sc.warnBg, tr("Your subscription has ended"),
                tr("Everything is still here to see. Subscribe again, or pay once for good, to import new SMS, add expenses and change budgets."),
                Modifier.testTag("unlock_lapsed"),
            )
        }

        if (access != Access.Unlocked) {
            PlanCard(
                plan == UnlockStore.Plan.YEARLY, tr("{0} a year", price), tr("Yearly subscription"),
                tr("Renews every year. Cancel anytime in Google Play."), null,
                Modifier.testTag("unlock_plan_yearly").testTag("unlock_price"),
            ) { plan = UnlockStore.Plan.YEARLY }
            PlanCard(
                plan == UnlockStore.Plan.LIFETIME, tr("{0} once", lifePrice), tr("Lifetime"),
                tr("Pay once, yours for good. No renewals, nothing more to pay."), tr("Pay once"),
                Modifier.testTag("unlock_plan_lifetime"),
            ) { plan = UnlockStore.Plan.LIFETIME }
            Panel {
                Text(tr("Both include"), style = MaterialTheme.typography.titleSmall)
                Text(tr("No ads, no account."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(8.dp)
                INCLUDED.forEach { line ->
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (store.pending) {
                InfoNote(
                    tr("Payment pending. Google Play will confirm it, which can take a few minutes with UPI. Owl Tracker unlocks by itself as soon as it does."),
                    icon = Ic.info, container = sc.warnBg, content = sc.warnFg,
                )
            }
            if (store.status == StoreState.Status.CONNECTING && store.message == null) {
                Text(
                    tr("Connecting to Google Play\u2026"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("unlock_connecting"),
                )
            }
            store.message?.let { msg ->
                Column(Modifier.testTag("unlock_message")) { InfoNote(msg, icon = Ic.alert, container = sc.overBg, content = sc.overFg) }
            }

            val available = store.status != StoreState.Status.UNAVAILABLE
            Button(
                onClick = { if (available) platform.buyUnlock(plan) else vm.restorePurchase() },
                enabled = !store.busy && !store.pending,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("unlock_buy"),
            ) {
                Text(
                    when {
                        !available -> tr("Try Google Play again")
                        plan == UnlockStore.Plan.LIFETIME -> tr("Pay {0} once", lifePrice)
                        else -> tr("Subscribe for {0} a year", price)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                if (plan == UnlockStore.Plan.LIFETIME) tr("One payment of {0} through Google Play. It stays with your Google account, so it works on a new phone too.", lifePrice)
                else tr("Renews automatically every year at {0} until you cancel in Google Play (Play Store › Payments & subscriptions). ", price) +
                    tr("If you cancel, you keep it until the end of the year you paid for."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("unlock_terms"),
            )
            TextButton(onClick = { vm.restorePurchase() }, modifier = Modifier.fillMaxWidth().testTag("unlock_restore")) {
                Text(tr("Already paid? Restore"))
            }
        }

        InfoNote(
            tr("Google Play takes the payment. Owl Tracker never sees your card or UPI details, and it still has no internet access: ") +
                tr("the Play Store app on your phone confirms the purchase to it."),
            icon = Ic.noNet, container = MaterialTheme.colorScheme.surfaceVariant, content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One way to pay: a big price, a name, a line, and a badge; picked like a radio button. */
@Composable
private fun PlanCard(on: Boolean, price: String, name: String, line: String, badge: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)
            .selectable(selected = on, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = on, onClick = null)
        Gap(12.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (badge != null) Text(
                    badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(price, style = MaterialTheme.typography.titleLarge)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

fun daysText(n: Int): String = if (n == 1) tr("1 day") else tr("{0} days", n)
