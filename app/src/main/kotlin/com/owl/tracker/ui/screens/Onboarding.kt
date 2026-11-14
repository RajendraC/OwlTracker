package com.owl.tracker.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.Platform
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.IconTile
import com.owl.tracker.ui.components.InfoNote
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.core.Money

@Composable
fun WelcomeScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        com.owl.tracker.ui.components.OwlBadge(size = 72.dp)
        Text("Your spending, sorted from your bank SMS", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(
            "Owl Tracker turns bank, card and UPI alerts into a monthly dashboard, without sending anything anywhere.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Promise(Ic.message, "Reads only bank, card and UPI SMS", "OTPs and personal messages are ignored")
        Promise(Ic.noNet, "No internet permission", "Your data cannot leave this phone")
        Promise(Ic.lock, "Encrypted and lockable", "Stored with a key held in your phone's secure hardware")
        Gap(8.dp)
        Button(onClick = { vm.go(com.owl.tracker.ui.Screen.DISCLOSURE) }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("get_started")) {
            Text("Get started")
        }
        androidx.compose.material3.TextButton(onClick = { vm.openBackup() }, modifier = Modifier.fillMaxWidth().testTag("welcome_restore")) {
            Text("Moving from another phone? Restore from a backup")
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Prominent disclosure required by Google Play before the system SMS prompt. Declining is a first-class
 * choice: the app works in manual mode.
 */
@Composable
fun DisclosureScreen(vm: AppViewModel, platform: Platform) {
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.onSmsPermission(true) else denied = true
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconTile(Ic.message, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer, 56.dp)
        Text("Read your bank SMS to track spending?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(
            "Owl Tracker reads messages from banks, cards and UPI apps to find your transactions. " +
                "Everything is processed on this phone and never leaves it. The app has no internet access.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Panel {
            Fact("WHAT WE READ", "Debit and credit alerts from bank, card and wallet senders such as VM-HDFCBK-S")
            Fact("WHAT WE IGNORE", "OTPs, offers, reminders, and every message from a person")
            Fact("WHAT WE KEEP", "Only the transactions you choose, encrypted on this phone")
        }
        if (denied) {
            Panel {
                Text("SMS access wasn't allowed", style = MaterialTheme.typography.titleSmall)
                Text(
                    "If Android no longer shows the prompt, allow SMS in the app's settings: Permissions → SMS → Allow. " +
                        "On some phones you first need ⋮ → Allow restricted settings.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(8.dp)
                OutlinedButton(onClick = { platform.openAppSettings() }) { Text("Open app settings") }
            }
        }
        Gap(8.dp)
        Button(
            onClick = {
                if (vm.smsPermissionGranted()) vm.onSmsPermission(true)
                else { launcher.launch(Manifest.permission.READ_SMS) }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("allow_sms"),
        ) { Text("Allow SMS access") }
        TextButton(onClick = { vm.chooseManualMode() }, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("not_now")) {
            Text("Not now, add expenses manually")
        }
        TextButton(onClick = { vm.startSampleImport() }, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("try_sample")) {
            Text("Explore with sample data first")
        }
    }
}

@Composable
private fun Fact(label: String, text: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BudgetSetupScreen(vm: AppViewModel) {
    val hist by vm.monthHistory.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var oneTotal by rememberSaveable { mutableStateOf(false) }
    val h = hist
    when {
        // A moment while the months are added up
        h == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        h.hasData && !oneTotal -> BudgetSlidersSetup(vm, h, onOneTotal = { oneTotal = true })
        else -> OneTotalSetup(vm, settings?.smsEnabled == false, h.takeIf { it.hasData }, onByCategory = { oneTotal = false })
    }
}

/** The single monthly amount: the only choice before there's a month to go by, or when you'd rather not split it up. */
@Composable
private fun OneTotalSetup(vm: AppViewModel, manual: Boolean, h: com.owl.tracker.ui.MonthHistory?, onByCategory: () -> Unit) {
    // With history, start from the usual month, rounded up to ₹1,000; else ₹75,000. Any amount, on a slider or typed
    val usual = h?.let { com.owl.tracker.core.Trends.usualMonth(it.totals) } ?: 0L
    var paise by rememberSaveable { mutableStateOf(if (usual > 0) (usual + 999_99) / 1_000_00 * 1_000_00 else 75_000_00L) }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Set a monthly budget", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(
            "Owl Tracker warns you early when you're spending faster than your budget allows. Change it any time.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthBudgetPicker(h, h?.now?.values?.sum() ?: 0L, paise, "budget_input") { paise = it }
            }
        }
        if (manual) {
            InfoNote("You're in manual mode: add expenses with + on the home screen. You can turn on SMS import later in Settings.")
        } else if (h == null) {
            SlidersComingNote()
        }
        Gap(8.dp)
        Button(
            onClick = { if (paise > 0) vm.finishOnboarding(paise) },
            enabled = paise > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("save_budget"),
        ) { com.owl.tracker.ui.components.FitText("Save budget · ${Money.formatInr(paise)} a month", minScale = 0.8f, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        if (h != null) {
            androidx.compose.material3.TextButton(onClick = onByCategory, modifier = Modifier.fillMaxWidth().testTag("by_category")) {
                Text("Set a budget for each category instead")
            }
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit, canUseDeviceLock: Boolean, onFallbackUnlock: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { if (canUseDeviceLock) onUnlock() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        com.owl.tracker.ui.components.OwlBadge(size = 80.dp)
        Gap(16.dp)
        Text("Owl Tracker is locked", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
        Text("Use your fingerprint, face or screen lock to open", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
        Gap(32.dp)
        Button(onClick = { if (canUseDeviceLock) onUnlock() else onFallbackUnlock() }, modifier = Modifier.testTag("unlock")) {
            Icon(Ic.finger, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Unlock")
        }
    }
}

@Composable
fun DbErrorScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your data can't be unlocked", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The key that protects Owl Tracker's encrypted data isn't available on this phone any more. " +
                "This can happen after restoring the phone or clearing its secure storage. Your SMS inbox is untouched, " +
                "so you can reset Owl Tracker and import again.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = { vm.performReset() }) { Text("Reset and start again") }
    }
}
