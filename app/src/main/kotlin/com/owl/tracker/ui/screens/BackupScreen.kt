package com.owl.tracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.backup.BackupContent
import com.owl.tracker.ui.AppViewModel
import com.owl.tracker.ui.BackupUi
import com.owl.tracker.ui.Platform
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.InfoNote
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.components.ScreenHeader
import com.owl.tracker.ui.components.SwitchRow
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.ui.theme.LocalStatusColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INCLUDED = listOf("Amounts and dates", "Merchant names, icons and categories", "Your notes", "Budgets and category rules")
private val NEVER = listOf("SMS text and sender IDs", "Account and card numbers", "UPI IDs and bank references", "SMS import settings")

@Composable
fun BackupScreen(vm: AppViewModel, platform: Platform) {
    val ui by vm.backupUi.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val newPhone = settings?.onboarded != true
    val sc = LocalStatusColors.current

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickBackup(uri)
    }
    LaunchedEffect(Unit) { vm.shareRequests.collect { platform.shareBackup(it) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp).testTag("backup_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(if (newPhone) "Restore from a backup" else "Backup & restore", onBack = { vm.back() })

        ui.working?.let { msg ->
            Column(Modifier.fillMaxWidth().testTag("backup_working")) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Gap(6.dp)
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ui.error?.let { Column(Modifier.testTag("backup_error")) { InfoNote(it, icon = Ic.alert, container = sc.overBg, content = sc.overFg) } }

        if (!newPhone) BackUpPanel(vm, ui, onSave = { save.launch(vm.backupFileName()) })

        RestorePanel(vm, ui, newPhone, onPick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) })

        InfoNote(
            "Owl Tracker never sends your backup anywhere. You decide where the file goes: a folder, Google Drive, WhatsApp, " +
                "or Quick Share straight to your other phone.",
            icon = Ic.noNet, container = MaterialTheme.colorScheme.surfaceVariant, content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackUpPanel(vm: AppViewModel, ui: BackupUi, onSave: () -> Unit) {
    Panel {
        Text("Back up", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save your spending history to a file you can keep or move to another phone.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CheckList("Included", INCLUDED, Ic.check, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            CheckList("Never included", NEVER, Ic.close, LocalStatusColors.current.overFg, Modifier.weight(1f).testTag("backup_never"))
        }
        Gap(8.dp)
        SwitchRow(
            "Protect with a password",
            if (ui.protect) "Recommended. The file is encrypted (AES-256) and useless without the password."
            else "Anyone who gets the file can read your spending history.",
            ui.protect, { vm.setBackupProtect(it) }, Ic.lock,
        )
        if (ui.protect) {
            OutlinedTextField(
                value = ui.password, onValueChange = vm::setBackupPassword, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("backup_password"),
            )
            Gap(8.dp)
            OutlinedTextField(
                value = ui.confirm, onValueChange = vm::setBackupConfirm, label = { Text("Type it again") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = ui.confirm.isNotEmpty() && ui.passwordProblem != null,
                supportingText = { Text(ui.passwordProblem ?: "Keep it safe: without it the backup can't be opened, not even by us.") },
                modifier = Modifier.fillMaxWidth().testTag("backup_confirm"),
            )
        }
        Gap(8.dp)
        val ready = ui.passwordProblem == null && ui.working == null
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSave, enabled = ready, modifier = Modifier.weight(1f).height(48.dp).testTag("backup_save")) {
                Icon(Ic.doc, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Save to a file")
            }
            OutlinedButton(onClick = { vm.shareBackup() }, enabled = ready, modifier = Modifier.weight(1f).height(48.dp).testTag("backup_share")) {
                Icon(Ic.share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Share")
            }
        }
        ui.exported?.let {
            Gap(8.dp)
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("backup_done"))
        }
    }
}

@Composable
private fun RestorePanel(vm: AppViewModel, ui: BackupUi, newPhone: Boolean, onPick: () -> Unit) {
    Panel {
        Text("Restore", style = MaterialTheme.typography.titleMedium)
        Text(
            if (newPhone) "Bring your spending history over from your other phone. Make a backup there first (More → Backup & restore)."
            else "Bring in a backup made on this phone or another one.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(12.dp)
        OutlinedButton(onClick = onPick, enabled = ui.working == null, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("restore_pick")) {
            Icon(Ic.doc, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (ui.header == null) "  Choose a backup file" else "  Choose a different file")
        }

        val header = ui.header
        if (header != null) {
            Gap(12.dp)
            Text(ui.fileName ?: "Backup file", style = MaterialTheme.typography.titleSmall)
            val made = if (header.createdAt > 0) "Made " + vm.dateOf(header.createdAt).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) else "Backup"
            Text(
                made + if (header.encrypted) " · password protected" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (header.encrypted && ui.content == null) {
                Gap(8.dp)
                OutlinedTextField(
                    value = ui.restorePassword, onValueChange = vm::setRestorePassword, label = { Text("Backup password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("restore_password"),
                )
                Gap(8.dp)
                Button(
                    onClick = { vm.unlockBackup() }, enabled = ui.restorePassword.isNotEmpty() && ui.working == null,
                    modifier = Modifier.fillMaxWidth().testTag("restore_unlock"),
                ) { Text("Unlock") }
            }
        }

        ui.content?.let { content ->
            Gap(12.dp)
            Summary(vm, content)
            if (!newPhone) {
                Gap(8.dp)
                Choice("Add to what's here", "Payments already on this phone are skipped, and your categories and budgets are kept.", !ui.replace, "restore_add") { vm.setRestoreReplace(false) }
                Choice("Replace everything here", "This phone's payments, categories, budgets and rules are replaced by the backup's.", ui.replace, "restore_replace") { vm.setRestoreReplace(true) }
            }
            Gap(8.dp)
            Button(
                onClick = { vm.restoreBackup() }, enabled = ui.working == null,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("restore_go"),
            ) { Text(if (ui.replace && !newPhone) "Replace with this backup" else "Restore") }
        }

        ui.restored?.let { r ->
            Gap(10.dp)
            Text(
                "Restored ${r.added} ${if (r.added == 1) "payment" else "payments"}" +
                    (if (r.skipped > 0) ", skipped ${r.skipped} already here" else "") +
                    (if (r.categoriesAdded > 0) ", added ${r.categoriesAdded} ${if (r.categoriesAdded == 1) "category" else "categories"}" else "") + ".",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("restore_done"),
            )
        }
    }
}

@Composable
private fun Summary(vm: AppViewModel, c: BackupContent) {
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    val span = if (c.firstAt != null && c.lastAt != null) "${vm.dateOf(c.firstAt!!).format(fmt)} – ${vm.dateOf(c.lastAt!!).format(fmt)}" else "no payments"
    Column(Modifier.testTag("restore_summary")) {
        Text("${c.transactions.size} ${if (c.transactions.size == 1) "payment" else "payments"} · $span", style = MaterialTheme.typography.titleSmall)
        Text(
            "${c.categories.size} categories · ${c.budgets.size} budgets · ${c.rules.size} rules",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Choice(title: String, body: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CheckList(title: String, items: List<String>, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Gap(4.dp)
        items.forEach { line ->
            Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
