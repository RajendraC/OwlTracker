package com.owl.tracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.data.ThemeMode
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.ImportMode
import com.owl.tracker.ui.Platform
import com.owl.tracker.ui.Screen
import com.owl.tracker.ui.Sheet
import com.owl.tracker.ui.components.BankIcon
import com.owl.tracker.ui.components.CategoryTile
import com.owl.tracker.ui.components.Divider
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.IconTile
import com.owl.tracker.ui.components.InfoNote
import com.owl.tracker.ui.components.MerchantAvatar
import com.owl.tracker.ui.components.NavRow
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.components.SectionLabel
import com.owl.tracker.ui.components.SwitchRow
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalStatusColors
import com.owl.tracker.core.Institutions
import com.owl.tracker.core.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MoreScreen(vm: AppViewModel) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val store by vm.store.collectAsStateWithLifecycle()
    val price = store.price ?: com.owl.tracker.licence.UnlockStore.FALLBACK_PRICE
    val sc = LocalStatusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("More")
        Panel(padding = 8.dp, modifier = Modifier.testTag("more_unlock")) {
            when (val a = access) {
                is com.owl.tracker.licence.Access.Trial ->
                    NavRow(Ic.unlock, "Unlock Owl Tracker", "Free trial: ${daysText(a.daysLeft)} left · then $price once") { vm.openUnlock() }
                com.owl.tracker.licence.Access.TrialEnded ->
                    NavRow(Ic.lock, "Unlock Owl Tracker", "Trial ended · read-only until you unlock", iconTint = sc.warnFg, iconBg = sc.warnBg) { vm.openUnlock() }
                com.owl.tracker.licence.Access.Unlocked ->
                    NavRow(Ic.check, "Owl Tracker is unlocked", "Thank you for buying it", iconTint = sc.okFg, iconBg = sc.okBg) { vm.openUnlock() }
            }
        }
        InfoNote(
            "Owl Tracker has no internet permission, so your messages and spending never leave this phone.",
            icon = Ic.noNet, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Panel(padding = 8.dp) {
            NavRow(Ic.tag, "Categories", "${cats.count { !it.archived }} categories · add, edit, archive") { vm.go(Screen.CATEGORIES) }
            Divider()
            val ruleCount by vm.ruleCount.collectAsStateWithLifecycle()
            NavRow(
                Ic.sliders, "Rules", if (ruleCount == 0) "None yet · which category a merchant's payments go to" else "$ruleCount · see and delete them",
                modifier = Modifier.testTag("open_rules"),
            ) { vm.go(Screen.RULES) }
            Divider()
            NavRow(Ic.chart, "Reports", "Income & expenses, ways to save, trends and more") { vm.go(Screen.INSIGHTS) }
            Divider()
            NavRow(Ic.share, "Backup & restore", "Save to a file or move to another phone") { vm.openBackup() }
            Divider()
            NavRow(Ic.sliders, "Settings", "App lock, SMS import, delete or reset") { vm.go(Screen.SETTINGS) }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val version = androidx.compose.runtime.remember {
            @Suppress("DEPRECATION")
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        }
        Text(
            "Owl Tracker $version\nFont: Plus Jakarta Sans, under the SIL Open Font License 1.1",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        // Room for the raised Add button
        Gap(36.dp)
    }
}

/**
 * Every rule, newest first: who it's for, where it puts their payments, how many it covers and when it was made.
 * A rule for a store that sells everything is flagged. Deleting one can put its payments back where they'd usually go.
 */
@Composable
fun RulesScreen(vm: AppViewModel) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val sc = LocalStatusColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var plan by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<AppViewModel.RuleDeletePlan?>(null) }
    var putBack by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val made = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
    LazyColumn(
        Modifier.fillMaxSize().testTag("rules_list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Rules", onBack = { vm.back() }) }
        item {
            Text(
                "When a payment comes in, these decide its category. You make one with “Use for every …” when you change a payment's category.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        if (rules.isEmpty()) item {
            Panel { Text("No rules yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("rules_empty")) }
        }
        rules.forEach { r ->
            item(key = "rule_${r.id}") {
                val cat = cats.firstOrNull { it.id == r.categoryId }
                val mixed = r.match == com.owl.tracker.core.RuleMatch.MERCHANT && com.owl.tracker.core.MixedStores.isMixed(r.pattern)
                Panel(modifier = Modifier.testTag("rule_${r.pattern}")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryTile(cat)
                        Column(Modifier.weight(1f)) {
                            Text("${r.name} → ${cat?.name ?: "Others"}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    when (r.match) {
                                        com.owl.tracker.core.RuleMatch.MERCHANT -> null
                                        com.owl.tracker.core.RuleMatch.VPA -> "UPI ID"
                                        com.owl.tracker.core.RuleMatch.KEYWORD -> "Names containing it"
                                    },
                                    "${r.payments} ${if (r.payments == 1) "payment" else "payments"}",
                                    "made " + java.time.Instant.ofEpochMilli(r.createdAt).atZone(java.time.ZoneId.systemDefault()).format(made),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (mixed) {
                        Gap(8.dp)
                        InfoNote(
                            "${r.name} sells many kinds of things; this rule puts all of it in ${cat?.name ?: "one category"}.",
                            icon = Ic.info, container = sc.warnBg, content = sc.warnFg,
                        )
                    }
                    Gap(8.dp)
                    OutlinedButton(
                        onClick = { scope.launch { putBack = true; plan = vm.planRuleDelete(r) } },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("rule_delete_${r.pattern}"),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Ic.trash, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Delete rule")
                    }
                }
            }
        }
    }
    plan?.let { p ->
        val catName = vm.categoryName(p.rule.categoryId)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { plan = null },
            modifier = Modifier.testTag("rule_delete_dialog"),
            title = { Text("Delete “${p.rule.name} → $catName”?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("New ${p.rule.name} payments will get their usual category again.")
                    if (p.moves.isNotEmpty()) {
                        val n = p.moves.size
                        SwitchRow(
                            "Put $n ${if (n == 1) "payment" else "payments"} back in ${p.mainCategory ?: "their usual category"}",
                            if (putBack) "You can still move any one of them, like a real grocery order" else "Off: they stay in $catName",
                            putBack, { putBack = it },
                            modifier = Modifier.testTag("rule_put_back"),
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { vm.deleteRule(p, putBack); plan = null },
                    modifier = Modifier.testTag("rule_delete_confirm"),
                ) { Text("Delete rule", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { plan = null }) { Text("Keep it") } },
        )
    }
}

@Composable
fun CategoriesScreen(vm: AppViewModel) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val rules by vm.ruleCount.collectAsStateWithLifecycle()
    val spentBy = d?.byCategory?.associate { it.category.id to it.paise }.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Categories", onBack = { vm.back() }) {
                IconButton(onClick = { vm.openSheet(Sheet.CategoryEdit(null)) }, modifier = Modifier.testTag("add_category")) { Icon(Ic.plus, contentDescription = "Add category") }
            }
        }
        item {
            Text(
                "Tap a category to rename it or change its icon and colour. When you recategorize a transaction, Owl Tracker can remember it as a rule" +
                    if (rules > 0) " (you have $rules)." else ".",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        item {
            Panel(padding = 8.dp) {
                cats.filter { !it.archived }.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryTile(c)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            val kind = when (c.kind) {
                                com.owl.tracker.data.CategoryKind.EXPENSE -> ""
                                com.owl.tracker.data.CategoryKind.INCOME -> "Income · "
                                else -> "Not counted in spend · "
                            }
                            Text(kind + (spentBy[c.id]?.let { "${Money.formatInr(it)} this month" } ?: "Nothing this month"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.openSheet(Sheet.CategoryEdit(c.id)) }) { Icon(Ic.edit, contentDescription = "Edit ${c.name}") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { vm.openSheet(Sheet.CategoryEdit(null)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Add category") }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, platform: Platform) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val keyProtection by vm.keyProtection.collectAsStateWithLifecycle()
    val settings = s ?: return
    val f = settings.filters
    val sc = LocalStatusColors.current
    val granted = vm.smsPermissionGranted()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Settings", onBack = { vm.back() })

        SectionLabel("Appearance")
        Panel(padding = 12.dp) {
            ThemeChoice(settings.theme) { vm.setTheme(it) }
        }

        SectionLabel("Security")
        Panel(padding = 12.dp) {
            SwitchRow("App lock", "Fingerprint, face or screen lock · locks a minute after you leave", settings.appLock, { on ->
                if (on) platform.authenticate("Turn on app lock", "Confirm it's you", onSuccess = { vm.setAppLock(true) }, onFail = { msg -> vm.messages.tryEmit(com.owl.tracker.ui.UiMessage(msg)) })
                else vm.setAppLock(false)
            }, Ic.lock)
            Divider()
            SwitchRow("Screen privacy", "Blocks screenshots and hides Owl Tracker in Recents", settings.screenPrivacy, { vm.setScreenPrivacy(it) }, Ic.eye)
            if (settings.appLock) {
                Divider()
                NavRow(Ic.finger, "Lock now") { vm.lockNow() }
            }
            Divider()
            NavRow(Ic.shield, "Encryption key", "AES-256 \u00b7 key held in $keyProtection", trailing = {}) { }
        }

        SectionLabel("SMS import")
        Panel(padding = 12.dp) {
            NavRow(
                Ic.message, "SMS access",
                when {
                    settings.smsEnabled && granted -> "Allowed · last scan " + if (settings.lastSyncAt > 0) java.time.Instant.ofEpochMilli(settings.lastSyncAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)) else "never"
                    settings.smsEnabled -> "Permission was removed in Android settings"
                    else -> "Off · manual mode"
                },
            ) { if (settings.smsEnabled && granted) platform.openAppSettings() else vm.go(Screen.DISCLOSURE) }
            Divider()
            NavRow(
                Ic.filter, "What to import",
                "${f.rangeMonths} months · " + (if (f.disabledSenders.isEmpty()) "all senders" else "${f.disabledSenders.size} sender${if (f.disabledSenders.size == 1) "" else "s"} left out") +
                    if (f.skipWords.isNotEmpty()) " · ${f.skipWords.size} skip words" else "",
            ) { if (settings.smsEnabled && granted) vm.startImport(ImportMode.SETTINGS) else vm.go(Screen.DISCLOSURE) }
            Divider()
            NavRow(Ic.refresh, "Rescan inbox", "Also runs when you open the app and every 6 hours", trailing = {}) { vm.rescan() }
            storage.bySenderGroup.entries.sortedByDescending { it.value }.forEach { (g, n) ->
                Divider()
                NavRow(Ic.bank, Institutions.nameFor(g), (if (g in f.disabledSenders) "Paused" else "Importing") + " · $n stored", leading = { BankIcon(vm.appIcons, g, 36.dp) }, trailing = {}) { }
            }
        }

        SectionLabel("Your data")
        Panel(padding = 12.dp) {
            NavRow(
                Ic.database,
                if (storage.total > 0) "${storage.total} transactions stored" else "Nothing stored yet",
                storage.oldestMs?.let { "Since " + vm.dateOf(it).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) + " · encrypted on this phone" } ?: "Import or add expenses to get started",
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer, iconBg = MaterialTheme.colorScheme.primaryContainer, trailing = {},
            ) { }
            Divider()
            NavRow(Ic.trash, "Delete transactions", "By age, by bank or card, or everything from SMS", iconTint = sc.overFg, iconBg = sc.overBg) { vm.openDelete() }
            Divider()
            NavRow(Ic.reset, "Reset Owl Tracker", "Erase everything on this phone and start again", iconTint = sc.overFg, iconBg = sc.overBg, titleColor = sc.overFg) { vm.requestReset() }
        }

        SectionLabel("About")
        Panel(padding = 12.dp) {
            NavRow(Ic.doc, "Privacy policy", "What Owl Tracker reads, keeps and never does") { vm.go(Screen.PRIVACY) }
            Divider()
            val context = androidx.compose.ui.platform.LocalContext.current
            val version = androidx.compose.runtime.remember {
                @Suppress("DEPRECATION")
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            NavRow(Ic.info, "Version", version, trailing = {}) { }
        }

        if (vm.canSimulateTrialEnd) {
            val ended by vm.debugTrialEnded.collectAsStateWithLifecycle()
            SectionLabel("Testing (debug builds only)")
            Panel(padding = 12.dp) {
                SwitchRow("End the free trial now", "See read-only mode and the unlock screen without waiting 7 days. Not in the Google Play version.", ended, { vm.setDebugTrialEnded(it) }, Ic.calendar)
            }
        }
    }
}

const val PRIVACY_URL = "https://rajendrac.github.io/OwlTracker/privacy.html"

@Composable
fun PrivacyScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Privacy policy", onBack = { vm.back() })
        PrivacyPara("What Owl Tracker reads", "With your permission, Owl Tracker reads SMS messages in your inbox to find bank, card, wallet and UPI transactions. It reads each message's sender, text and time. It does not read contacts, call logs, photos, location or any other data.")
        PrivacyPara("Merchant logos", "To show a merchant's or bank's logo, Owl Tracker checks whether one of about 200 well-known apps (shopping, jewellery, clothing, delivery, streaming, books, travel, fuel, FASTag, insurance, UPI and banking apps such as Swiggy, Tanishq, FanCode, HP PAY, My FASTag, Policybazaar or YONO SBI) is installed, and if so shows that app's own icon. For a shop with no app, it picks a plain icon (jeweller, opticals, bakery, pharmacy…) from words in the shop's name, on your phone. When you choose a merchant's icon yourself, you can also pick any app on your phone that pays by UPI, or a picture through Android's photo picker, which gives Owl Tracker only the one picture you choose. A small copy of that picture (144 × 144 pixels) is kept in Owl Tracker's encrypted database and included in your backups. Owl Tracker never sees the full list of apps on your phone, and nothing about your apps or pictures leaves your phone.")
        PrivacyPara("What it ignores", "Messages from people (phone-number senders), OTPs and verification codes, promotional messages, reminders and failed payments are skipped in memory and never stored.")
        PrivacyPara("What it keeps", "The transactions you choose to import, with a copy of each original message so you can check it. They are stored in a database encrypted with AES-256. Its key is protected by Android Keystore and never leaves the phone.")
        PrivacyPara("What it shares", "Nothing. Owl Tracker has no internet permission, no analytics, no ads and no crash-reporting service. Your data is excluded from Android cloud backup and device-to-device transfer.")
        PrivacyPara("Your choices", "You choose which senders and transactions to import, and can change that any time. You can delete single transactions, delete by age or by bank, or reset the app, which erases everything and destroys the encryption key. You can also remove SMS access in Android settings; the app keeps working with manual entries.")
        PrivacyPara("Backups", "A backup file is made only when you ask for one (More \u2192 Backup & restore), and goes only where you send it: Owl Tracker never sends it anywhere itself. It holds amounts, dates, merchant names, the merchant names and icons you chose, categories, notes, budgets and category rules. It never holds SMS text, sender IDs, account or card numbers, UPI IDs or bank references, and long numbers inside merchant names are masked. With a password, the file is encrypted (AES-256-GCM) and can't be read without it.")
        PrivacyPara("Payments", "Owl Tracker is free for 7 days, then a one-time unlock bought through Google Play. Google Play takes the payment under its own terms and privacy policy. Owl Tracker never sees your card, UPI or bank details: the Play Store app on your phone only tells it whether the unlock has been bought. The trial is counted on your phone. Nothing about the purchase is sent anywhere by Owl Tracker.")
        PrivacyPara("Sample data", "If you choose Explore with sample data, Owl Tracker imports 21 built-in example messages instead of reading your inbox. Reset removes them.")
        PrivacyPara("Contact", "Questions or requests: rajendra.chandrasekhar@gmail.com. This policy is also published at $PRIVACY_URL. Last updated 7 November 2026.")
    }
}

@Composable
private fun PrivacyPara(title: String, body: String) {
    Panel {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Gap(4.dp)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Light, dark or follow the phone; applies straight away. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChoice(mode: ThemeMode, onPick: (ThemeMode) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Ic.moon, c.onSurfaceVariant, c.surfaceVariant, 36.dp)
            Column(Modifier.weight(1f)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (mode) {
                        ThemeMode.SYSTEM -> "Follows your phone's dark mode setting"
                        ThemeMode.LIGHT -> "Always light"
                        ThemeMode.DARK -> "Always dark"
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("theme_choice")) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == mode,
                    onClick = { onPick(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    // The picked one uses a container colour with its own text colour
                    colors = SegmentedButtonDefaults.colors(
                        inactiveContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveContentColor = c.onSurface,
                        activeContainerColor = c.primaryContainer,
                        activeContentColor = c.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("theme_${m.key}"),
                    // With big text the tick makes room and the word gets smaller, never "Syste / m"
                    icon = { if (!com.owl.tracker.ui.components.textScaleAtLeast(1.3f)) SegmentedButtonDefaults.Icon(active = m == mode) },
                    label = { com.owl.tracker.ui.components.FitText(m.label, minScale = 0.7f) },
                )
            }
        }
    }
}
