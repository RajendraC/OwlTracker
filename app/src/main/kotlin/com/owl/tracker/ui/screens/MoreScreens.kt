package com.owl.tracker.ui.screens

import com.owl.tracker.i18n.tr
import com.owl.tracker.i18n.trn
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
    val lifetime by vm.lifetime.collectAsStateWithLifecycle()
    val sc = LocalStatusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(tr("More"))
        Panel(padding = 8.dp, modifier = Modifier.testTag("more_unlock")) {
            when (val a = access) {
                is com.owl.tracker.licence.Access.Trial ->
                    NavRow(Ic.unlock, tr("Unlock Owl Tracker"), tr("Free trial: {0} left · then {1} a year, or pay once", daysText(a.daysLeft), price)) { vm.openUnlock() }
                com.owl.tracker.licence.Access.TrialEnded ->
                    NavRow(Ic.lock, tr("Unlock Owl Tracker"), tr("Trial ended · read-only until you subscribe or pay once"), iconTint = sc.warnFg, iconBg = sc.warnBg) { vm.openUnlock() }
                com.owl.tracker.licence.Access.Lapsed ->
                    NavRow(Ic.lock, tr("Subscribe again"), tr("Subscription ended · read-only until you subscribe or pay once"), iconTint = sc.warnFg, iconBg = sc.warnBg) { vm.openUnlock() }
                com.owl.tracker.licence.Access.Unlocked -> if (lifetime) NavRow(Ic.check, tr("Lifetime unlock"), tr("Paid once · yours for good"), iconTint = sc.okFg, iconBg = sc.okBg) { vm.openUnlock() }
                else
                    NavRow(
                        Ic.check, tr("Subscribed"), if (store.renewing == false) tr("Cancelled · stops at the end of the paid year") else tr("{0} a year · manage or cancel", price),
                        iconTint = sc.okFg, iconBg = sc.okBg,
                    ) { vm.openUnlock() }
            }
        }
        InfoNote(
            tr("Owl Tracker has no internet permission, so your messages and spending never leave this phone."),
            icon = Ic.noNet, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Panel(padding = 8.dp) {
            NavRow(Ic.tag, tr("Categories"), tr("{0} categories · add, edit, archive", cats.count { !it.archived })) { vm.go(Screen.CATEGORIES) }
            Divider()
            val ruleCount by vm.ruleCount.collectAsStateWithLifecycle()
            NavRow(
                Ic.sliders, tr("Rules"), if (ruleCount == 0) tr("None yet · which category a merchant's payments go to") else tr("{0} · see and delete them", ruleCount),
                modifier = Modifier.testTag("open_rules"),
            ) { vm.go(Screen.RULES) }
            Divider()
            NavRow(Ic.chart, tr("Reports"), tr("Income & expenses, ways to save, trends and more")) { vm.go(Screen.INSIGHTS) }
            Divider()
            NavRow(Ic.jar, tr("Goals"), tr("Jars for what you're saving towards"), modifier = Modifier.testTag("open_goals")) { vm.go(Screen.GOALS) }
            Divider()
            NavRow(Ic.calendar, tr("Bills coming up"), tr("EMIs, rent and subscriptions, found from repeating payments"), modifier = Modifier.testTag("open_bills")) { vm.go(Screen.BILLS) }
            Divider()
            NavRow(Ic.wallet, tr("Can I afford it?"), tr("What a purchase does to the rest of your month"), modifier = Modifier.testTag("open_afford")) { vm.openSheet(com.owl.tracker.ui.Sheet.Afford) }
            Divider()
            NavRow(Ic.share, tr("Backup & restore"), tr("Save to a file or move to another phone")) { vm.openBackup() }
            Divider()
            NavRow(Ic.sliders, tr("Settings"), tr("App lock, SMS import, delete or reset")) { vm.go(Screen.SETTINGS) }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val version = androidx.compose.runtime.remember {
            @Suppress("DEPRECATION")
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        }
        Text(
            tr("Owl Tracker {0}\nFonts: Plus Jakarta Sans, Fraunces and Baloo 2, under the SIL Open Font License 1.1\nCategory icons: Fluent Emoji by Microsoft, under the MIT License\nCategory picture props: Noto Emoji by Google, under the Apache License 2.0", version),
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
    val made = DateTimeFormatter.ofPattern("d MMM", com.owl.tracker.i18n.I18n.locale)
    LazyColumn(
        Modifier.fillMaxSize().testTag("rules_list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(tr("Rules"), onBack = { vm.back() }) }
        item {
            Text(
                tr("When a payment comes in, these decide its category. You make one with “Use for every …” when you change a payment's category."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        if (rules.isEmpty()) item {
            Panel { Text(tr("No rules yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("rules_empty")) }
        }
        rules.forEach { r ->
            item(key = "rule_${r.id}") {
                val cat = cats.firstOrNull { it.id == r.categoryId }
                val mixed = r.match == com.owl.tracker.core.RuleMatch.MERCHANT && com.owl.tracker.core.MixedStores.isMixed(r.pattern)
                Panel(modifier = Modifier.testTag("rule_${r.pattern}")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryTile(cat)
                        Column(Modifier.weight(1f)) {
                            Text("${r.name} → ${cat?.name ?: tr("Others")}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    when (r.match) {
                                        com.owl.tracker.core.RuleMatch.MERCHANT -> null
                                        com.owl.tracker.core.RuleMatch.VPA -> tr("UPI ID")
                                        com.owl.tracker.core.RuleMatch.KEYWORD -> tr("Names containing it")
                                    },
                                    "${trn(r.payments, "{0} payment", "{0} payments")}",
                                    tr("made ") + java.time.Instant.ofEpochMilli(r.createdAt).atZone(java.time.ZoneId.systemDefault()).format(made),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (mixed) {
                        Gap(8.dp)
                        InfoNote(
                            tr("{0} sells many kinds of things; this rule puts all of it in {1}.", r.name, cat?.name ?: tr("one category")),
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
                        Text(tr("Delete rule"))
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
            title = { Text(tr("Delete “{0} → {1}”?", p.rule.name, catName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("New {0} payments will get their usual category again.", p.rule.name))
                    if (p.moves.isNotEmpty()) {
                        val n = p.moves.size
                        SwitchRow(
                            tr("Put {0} {1} back in {2}", n, if (n == 1) tr("payment") else tr("payments"), p.mainCategory ?: tr("their usual category")),
                            if (putBack) tr("You can still move any one of them, like a real grocery order") else tr("Off: they stay in {0}", catName),
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
                ) { Text(tr("Delete rule"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { plan = null }) { Text(tr("Keep it")) } },
        )
    }
}

@Composable
fun CategoriesScreen(vm: AppViewModel) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val rules by vm.ruleCount.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val kidsOn = settings?.kids != false
    val tree = remember(cats) { com.owl.tracker.data.CategoryTree(cats) }
    // This month: each group's total, and each category's inside it
    val spentBy = remember(d) {
        d?.byCategory?.associate { it.category.id to it.paise }.orEmpty() +
            d?.children?.values?.flatten()?.filter { cs -> d?.children?.containsKey(cs.category.id) != true }?.associate { it.category.id to it.paise }.orEmpty()
    }
    val groups = cats.filter { it.parent == null && (tree.isGroup(it.id) || it.id == com.owl.tracker.core.Cat.KIDS) && (!it.archived || it.id == com.owl.tracker.core.Cat.KIDS) }
    val alone = cats.filter { it.parent == null && !it.archived && !tree.isGroup(it.id) && it.id != com.owl.tracker.core.Cat.KIDS }

    @Composable
    fun CategoryLine(c: com.owl.tracker.data.Category, small: Boolean) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (small) 52.dp else 60.dp).padding(start = if (small) 8.dp else 0.dp, top = 2.dp, bottom = 2.dp).testTag("category_row_${c.id}"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryTile(c, if (small) 34.dp else 40.dp)
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall)
                val kind = when (c.kind) {
                    com.owl.tracker.data.CategoryKind.EXPENSE -> ""
                    com.owl.tracker.data.CategoryKind.INCOME -> tr("Income · ")
                    else -> tr("Not counted in spend · ")
                }
                Text(kind + (spentBy[c.id]?.let { tr("{0} this month", Money.formatInr(it)) } ?: tr("Nothing this month")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.openSheet(Sheet.CategoryEdit(c.id)) }) { Icon(Ic.edit, contentDescription = tr("Edit {0}", c.name)) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(tr("Categories"), onBack = { vm.back() }) {
                IconButton(onClick = { vm.openSheet(Sheet.CategoryEdit(null)) }, modifier = Modifier.testTag("add_category")) { Icon(Ic.plus, contentDescription = tr("Add category")) }
            }
        }
        item {
            Text(
                tr("Categories sit in groups: budgets and Home's ring are by group. Tap one to rename it, change its icon and colour ") +
                    tr("or move it to another group. When you recategorize a transaction, Owl Tracker can remember it as a rule") +
                    if (rules > 0) tr(" (you have {0}).", rules) else ".",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        items(groups, key = { it.id }) { g ->
            val kids = g.id == com.owl.tracker.core.Cat.KIDS
            Panel(padding = 8.dp, modifier = Modifier.testTag("category_group_${g.id}")) {
                if (kids) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryTile(g)
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (kidsOn) (spentBy[g.id]?.let { tr("{0} this month", Money.formatInr(it)) } ?: tr("On · nothing this month")) else tr("Off · hidden everywhere"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.Switch(checked = kidsOn, onCheckedChange = { vm.setKids(it) }, modifier = Modifier.testTag("kids_switch"))
                    }
                } else CategoryLine(g, small = false)
                if (kids && !kidsOn) {
                    Text(
                        tr("Tuition and classes go to Courses & Books or Sports & Fitness instead. Turn on if you pay for children's school, classes or activities."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(6.dp),
                    )
                } else {
                    tree.children(g.id).forEach { c -> Divider(); CategoryLine(c, small = true) }
                    Divider()
                    TextButton(onClick = { vm.openSheet(Sheet.CategoryEdit(null, g.id)) }, modifier = Modifier.testTag("add_to_${g.id}")) {
                        Icon(Ic.plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(tr("  Add a category to {0}", g.name))
                    }
                }
            }
        }
        item {
            Panel(padding = 8.dp, modifier = Modifier.testTag("category_group_alone")) {
                Text(tr("On their own"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(6.dp))
                alone.forEach { c -> Divider(); CategoryLine(c, small = false) }
            }
        }
        item {
            OutlinedButton(onClick = { vm.openSheet(Sheet.CategoryEdit(null)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(tr("Add category")) }
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
        ScreenHeader(tr("Settings"), onBack = { vm.back() })

        SectionLabel(tr("Appearance"))
        Panel(padding = 12.dp) {
            Text(tr("Language") + if (com.owl.tracker.i18n.I18n.lang != "en") " · Language" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            LanguageChoice(vm, Modifier.padding(horizontal = 8.dp))
            Divider()
            PaletteChoice(com.owl.tracker.ui.theme.Palette.from(settings.palette)) { vm.setPalette(it) }
            Divider()
            ThemeChoice(settings.theme) { vm.setTheme(it) }
            Divider()
            OwlReactsChoice(settings.owlReacts) { vm.setOwlReacts(it) }
        }

        SectionLabel(tr("Owl nudges"))
        NudgeSettings()

        SectionLabel(tr("Monthly report"))
        MonthlyReportSettings()

        SectionLabel(tr("Security"))
        Panel(padding = 12.dp) {
            SwitchRow(tr("App lock"), tr("Fingerprint, face or screen lock · locks a minute after you leave"), settings.appLock, { on ->
                if (on) platform.authenticate(tr("Turn on app lock"), tr("Confirm it's you"), onSuccess = { vm.setAppLock(true) }, onFail = { msg -> vm.messages.tryEmit(com.owl.tracker.ui.UiMessage(msg)) })
                else vm.setAppLock(false)
            }, Ic.lock)
            Divider()
            SwitchRow(tr("Screen privacy"), tr("Blocks screenshots and hides Owl Tracker in Recents"), settings.screenPrivacy, { vm.setScreenPrivacy(it) }, Ic.eye)
            if (settings.appLock) {
                Divider()
                NavRow(Ic.finger, tr("Lock now")) { vm.lockNow() }
            }
            Divider()
            NavRow(Ic.shield, tr("Encryption key"), tr("AES-256 \u00b7 key held in {0}", keyProtection), trailing = {}) { }
        }

        SectionLabel(tr("SMS import"))
        Panel(padding = 12.dp) {
            NavRow(
                Ic.message, tr("SMS access"),
                when {
                    settings.smsEnabled && granted -> tr("Allowed · last scan ") + if (settings.lastSyncAt > 0) java.time.Instant.ofEpochMilli(settings.lastSyncAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a", com.owl.tracker.i18n.I18n.locale)) else "never"
                    settings.smsEnabled -> tr("Permission was removed in Android settings")
                    else -> tr("Off · manual mode")
                },
            ) { if (settings.smsEnabled && granted) platform.openAppSettings() else vm.go(Screen.DISCLOSURE) }
            Divider()
            NavRow(
                Ic.filter, tr("What to import"),
                tr("{0} months · ", f.rangeMonths) + (if (f.disabledSenders.isEmpty()) tr("all senders") else (if (f.disabledSenders.size == 1) tr("1 sender left out") else tr("{0} senders left out", f.disabledSenders.size))) +
                    if (f.skipWords.isNotEmpty()) tr(" · {0} skip words", f.skipWords.size) else "",
            ) { if (settings.smsEnabled && granted) vm.startImport(ImportMode.SETTINGS) else vm.go(Screen.DISCLOSURE) }
            Divider()
            NavRow(Ic.refresh, tr("Rescan inbox"), tr("Also runs when you open the app and every 6 hours"), trailing = {}) { vm.rescan() }
            storage.bySenderGroup.entries.sortedByDescending { it.value }.forEach { (g, n) ->
                Divider()
                NavRow(Ic.bank, Institutions.nameFor(g), (if (g in f.disabledSenders) tr("Paused") else tr("Importing")) + tr(" · {0} stored", n), leading = { BankIcon(vm.appIcons, g, 36.dp) }, trailing = {}) { }
            }
        }

        SectionLabel(tr("Your data"))
        Panel(padding = 12.dp) {
            NavRow(
                Ic.database,
                if (storage.total > 0) tr("{0} transactions stored", storage.total) else tr("Nothing stored yet"),
                storage.oldestMs?.let { tr("Since ") + vm.dateOf(it).format(DateTimeFormatter.ofPattern("MMMM yyyy", com.owl.tracker.i18n.I18n.locale)) + tr(" · encrypted on this phone") } ?: tr("Import or add expenses to get started"),
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer, iconBg = MaterialTheme.colorScheme.primaryContainer, trailing = {},
            ) { }
            Divider()
            NavRow(Ic.trash, tr("Delete transactions"), tr("By age, by bank or card, or everything from SMS"), iconTint = sc.overFg, iconBg = sc.overBg) { vm.openDelete() }
            Divider()
            NavRow(Ic.reset, tr("Reset Owl Tracker"), tr("Erase everything on this phone and start again"), iconTint = sc.overFg, iconBg = sc.overBg, titleColor = sc.overFg) { vm.requestReset() }
        }

        SectionLabel(tr("About"))
        Panel(padding = 12.dp) {
            NavRow(Ic.doc, tr("Privacy policy"), tr("What Owl Tracker reads, keeps and never does")) { vm.go(Screen.PRIVACY) }
            Divider()
            val context = androidx.compose.ui.platform.LocalContext.current
            val version = androidx.compose.runtime.remember {
                @Suppress("DEPRECATION")
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            NavRow(Ic.info, tr("Version"), version, trailing = {}) { }
        }

        if (vm.canSimulateTrialEnd) {
            val ended by vm.debugTrialEnded.collectAsStateWithLifecycle()
            SectionLabel(tr("Testing (debug builds only)"))
            Panel(padding = 12.dp) {
                SwitchRow(tr("End the free trial now"), tr("See read-only mode and the unlock screen without waiting 7 days. Not in the Google Play version."), ended, { vm.setDebugTrialEnded(it) }, Ic.calendar)
            }
        }
    }
}

const val PRIVACY_URL = "https://rajendrac.github.io/OwlTracker/privacy.html"

@Composable
fun PrivacyScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(tr("Privacy policy"), onBack = { vm.back() })
        PrivacyPara(tr("What Owl Tracker reads"), tr("With your permission, Owl Tracker reads SMS messages in your inbox to find bank, card, wallet and UPI transactions. It reads each message's sender, text and time. It does not read contacts, call logs, photos, location or any other data."))
        PrivacyPara(tr("Merchant logos"), tr("To show a merchant's or bank's logo, Owl Tracker checks whether one of about 200 well-known apps (shopping, jewellery, clothing, delivery, streaming, books, travel, fuel, FASTag, insurance, UPI and banking apps such as Swiggy, Tanishq, FanCode, HP PAY, My FASTag, Policybazaar or YONO SBI) is installed, and if so shows that app's own icon. For a shop with no app, it picks a plain icon (jeweller, opticals, bakery, pharmacy…) from words in the shop's name, on your phone. When you choose a merchant's icon yourself, you can also pick any app on your phone that pays by UPI, or a picture through Android's photo picker, which gives Owl Tracker only the one picture you choose. A small copy of that picture (144 × 144 pixels) is kept in Owl Tracker's encrypted database and included in your backups. Owl Tracker never sees the full list of apps on your phone, and nothing about your apps or pictures leaves your phone."))
        PrivacyPara(tr("What it ignores"), tr("Messages from people (phone-number senders), OTPs and verification codes, promotional messages, reminders and failed payments are skipped in memory and never stored."))
        PrivacyPara(tr("What it keeps"), tr("The transactions you choose to import, with a copy of each original message so you can check it. They are stored in a database encrypted with AES-256. Its key is protected by Android Keystore and never leaves the phone."))
        PrivacyPara(tr("What it shares"), tr("Nothing. Owl Tracker has no internet permission, no analytics, no ads and no crash-reporting service. Your data is excluded from Android cloud backup and device-to-device transfer."))
        PrivacyPara(tr("Your choices"), tr("You choose which senders and transactions to import, and can change that any time. You can delete single transactions, delete by age or by bank, or reset the app, which erases everything and destroys the encryption key. You can also remove SMS access in Android settings; the app keeps working with manual entries."))
        PrivacyPara(tr("Backups"), tr("A backup file is made only when you ask for one (More \u2192 Backup & restore), and goes only where you send it: Owl Tracker never sends it anywhere itself. It holds amounts, dates, merchant names, the merchant names and icons you chose, categories, notes, budgets and category rules. It never holds SMS text, sender IDs, account or card numbers, UPI IDs or bank references, and long numbers inside merchant names are masked. With a password, the file is encrypted (AES-256-GCM) and can't be read without it."))
        PrivacyPara(tr("Payments"), tr("Owl Tracker is free for 7 days, then a ₹99-a-year subscription or a one-time ₹999 lifetime unlock through Google Play; the subscription can be cancelled there any time. Google Play takes the payment under its own terms and privacy policy. Owl Tracker never sees your card, UPI or bank details: the Play Store app on your phone only tells it whether you have paid (an active, renewing subscription or the lifetime unlock). The trial is counted on your phone. Nothing about your payment is sent anywhere by Owl Tracker."))
        PrivacyPara(tr("Sample data"), tr("If you choose Explore with sample data, Owl Tracker imports 21 built-in example messages instead of reading your inbox. Reset removes them."))
        PrivacyPara(tr("Contact"), tr("Questions or requests: sutra.apk@gmail.com. This policy is also published at {0}. Last updated 26 November 2026.", PRIVACY_URL))
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
/** How lively the owl is when you add a payment or reach a milestone: Lively, Calm (its face only) or Off. */
@Composable
private fun OwlReactsChoice(how: String, onPick: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val options = listOf("lively" to tr("Lively"), "calm" to tr("Calm"), "off" to tr("Off"))
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.owl.tracker.ui.components.MoodOwl(com.owl.tracker.ui.components.OwlMood.HAPPY, 36.dp, dark = false, still = true)
            Column(Modifier.weight(1f)) {
                Text(tr("How the owl reacts"), style = MaterialTheme.typography.titleSmall)
                Text(
                    when (how) {
                        "calm" -> tr("Its face changes when you add a payment; nothing moves")
                        "off" -> tr("Plain messages, and no milestone celebrations")
                        else -> tr("Hops, thinks or slumps when you add a payment, and celebrates milestones")
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("owl_reacts_choice")) {
            options.forEachIndexed { i, (key, label) ->
                SegmentedButton(
                    selected = key == how,
                    onClick = { onPick(key) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        inactiveContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveContentColor = c.onSurface,
                        activeContainerColor = c.primaryContainer,
                        activeContentColor = c.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("owl_reacts_$key"),
                    icon = { if (!com.owl.tracker.ui.components.textScaleAtLeast(1.3f)) SegmentedButtonDefaults.Icon(active = key == how) },
                    label = { com.owl.tracker.ui.components.FitText(label, minScale = 0.7f) },
                )
            }
        }
    }
}

@Composable
private fun ThemeChoice(mode: ThemeMode, onPick: (ThemeMode) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Ic.moon, c.onSurfaceVariant, c.surfaceVariant, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(tr("Theme"), style = MaterialTheme.typography.titleSmall)
                Text(
                    when (mode) {
                        ThemeMode.SYSTEM -> tr("Follows your phone's dark mode setting")
                        ThemeMode.LIGHT -> tr("Always light")
                        ThemeMode.DARK -> tr("Always dark")
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

/**
 * The owl's nudges, each with its own switch. On Android 13+ the first switch turned on (or the button) asks for
 * permission to notify; until then the switches are shown but nothing is sent.
 */
@Composable
private fun NudgeSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var allowed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.owl.tracker.nudge.Nudges.allowed(context)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { allowed = it }
    val on = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply { com.owl.tracker.nudge.Nudges.KINDS.forEach { put(it, com.owl.tracker.nudge.Nudges.isOn(context, it)) } } }
    Panel(padding = 12.dp, modifier = Modifier.testTag("nudge_settings")) {
        if (!allowed && android.os.Build.VERSION.SDK_INT >= 33) {
            Text(tr("Android needs your OK before the owl can nudge you. Everything is worked out on your phone; nothing is sent anywhere."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            androidx.compose.material3.Button(onClick = { ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("nudge_allow")) { Text(tr("Allow nudges")) }
            Divider()
        }
        val rows = listOf(
            Triple(com.owl.tracker.nudge.Nudges.DAILY, tr("Evening: today's spending"), tr("Around 9 pm, how today went against its share")),
            Triple(com.owl.tracker.nudge.Nudges.ALERTS, tr("Budget alerts"), tr("At 80% and 100% of the month, and when a category goes over")),
            Triple(com.owl.tracker.nudge.Nudges.WEEKLY, tr("Sunday: your week"), tr("The week's total, against the week before")),
            Triple(com.owl.tracker.nudge.Nudges.RECAP, tr("Month recap"), tr("On the 1st: your month in a few cards")),
            Triple(com.owl.tracker.nudge.Nudges.CHECK, tr("New payments: was it right?"), tr("Confirm or change a new payment's category from the nudge")),
            Triple(com.owl.tracker.nudge.Nudges.REPORT, tr("Monthly report"), tr("On the 2nd: last month's report, to look through on a computer")),
        )
        rows.forEachIndexed { i, (kind, title, sub) ->
            if (i > 0) Divider()
            SwitchRow(title, sub, on[kind] == true, { v ->
                on[kind] = v
                com.owl.tracker.nudge.Nudges.setOn(context, kind, v)
                if (v && !allowed && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }, if (i == 0) Ic.bell else null, Modifier.testTag("nudge_$kind"))
        }
    }
}

/** The monthly report's kind: an interactive page for any browser, or an Excel workbook. */
@Composable
private fun MonthlyReportSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var excel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.owl.tracker.nudge.Nudges.reportExcel(context)) }
    Panel(padding = 12.dp, modifier = Modifier.testTag("report_settings")) {
        Text(tr("On the 2nd of each month, the owl gets last month's report ready to look through on a bigger screen: charts, a calendar, ") +
            tr("filters and every payment. Send it to yourself (email, Drive…) and open it on your computer. Nothing is sent by itself."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        // Chips that wrap onto a second line with big text, rather than breaking words
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(false to tr("Web page (any browser)"), true to tr("Excel workbook")).forEach { (x, label) ->
                androidx.compose.material3.FilterChip(selected = excel == x, onClick = { excel = x; com.owl.tracker.nudge.Nudges.setReportExcel(context, x) },
                    label = { Text(label) }, modifier = Modifier.testTag(if (x) "report_kind_excel" else "report_kind_page"))
            }
        }
    }
}

/** The colours: a small picture of each palette (its light version), the picked one ringed with a tick. */
@Composable
private fun PaletteChoice(picked: com.owl.tracker.ui.theme.Palette, onPick: (com.owl.tracker.ui.theme.Palette) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(8.dp).testTag("palette_choice"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Colours"), style = MaterialTheme.typography.titleMedium)
        Text(tr("Pick the look you like. Each has a light and a dark version."), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.owl.tracker.ui.theme.Palette.entries.forEach { p ->
                val on = p == picked
                val (bg, head, button, add) = p.swatch
                Column(
                    Modifier.width(92.dp).clip(RoundedCornerShape(14.dp))
                        .clickable(onClickLabel = tr("Use {0}", p.label)) { onPick(p) }
                        .semantics { selected = on }
                        .testTag("palette_${p.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 76.dp, height = 104.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                            .then(if (on) Modifier.border(3.dp, c.onSurface, RoundedCornerShape(16.dp)) else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp))),
                    ) {
                        Box(Modifier.fillMaxWidth().height(32.dp).background(head))
                        Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 22.dp).fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.White))
                        Box(Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 26.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(button))
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(add))
                        if (on) Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.onSurface),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Ic.check, contentDescription = null, tint = c.surface, modifier = Modifier.size(13.dp)) }
                    }
                    // Two lines ("Indigo &" / "Saffron"), each shrinking to fit, so big text never splits a word
                    val words = p.label.split(" & ")
                    Column(Modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        words.forEachIndexed { i, w ->
                            com.owl.tracker.ui.components.FitText(
                                if (i < words.size - 1) "$w &" else w,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, minScale = 0.55f,
                            )
                        }
                    }
                }
            }
        }
    }
}
