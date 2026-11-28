package com.owl.tracker.ui.screens

import com.owl.tracker.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.owl.tracker.ui.components.Gap
import com.owl.tracker.ui.components.IconTile
import com.owl.tracker.ui.components.Panel
import com.owl.tracker.ui.theme.Ic
import com.owl.tracker.update.UpdateUi
import com.owl.tracker.update.WhatsNew
import java.util.Locale

/** A new version on Google Play: Update or Later, the download, then Restart to finish (board P1, Round 25). */
@Composable
fun UpdateCard(
    state: UpdateUi,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlay: () -> Unit = {},
) {
    if (state == UpdateUi.None) return
    val (title, line) = when (state) {
        is UpdateUi.Ready -> tr("A new version is ready") to buildString {
            append(tr("Get it from Google Play"))
            if (state.bytes > 0) append(tr(" (about ")).append(sizeText(state.bytes)).append(")")
            append(tr(". Keep using the app while it downloads."))
        }
        is UpdateUi.Downloading -> tr("Downloading the update…") to tr("{0}% · you can keep using the app", state.percent)
        UpdateUi.Downloaded -> tr("Update downloaded") to tr("Restart Owl Tracker to finish. It takes a few seconds and nothing is lost.")
        UpdateUi.Installing -> tr("Installing the update…") to tr("Owl Tracker will close and open again in a moment.")
        UpdateUi.FinishInPlay -> tr("Finish the update in Google Play") to tr("It couldn't finish from here. Tap Update on Owl Tracker's page in the Play Store; nothing is lost.")
        UpdateUi.None -> return
    }
    Panel(modifier = modifier.testTag("update_card")) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ic.download, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state is UpdateUi.Downloading) {
            Gap(12.dp)
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).testTag("update_progress"),
            )
        }
        when (state) {
            is UpdateUi.Ready -> {
                Gap(10.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onUpdate, modifier = Modifier.testTag("update_now")) { Text(tr("Update")) }
                    TextButton(onClick = onLater, modifier = Modifier.testTag("update_later")) { Text(tr("Later")) }
                }
            }
            UpdateUi.Downloaded -> {
                Gap(10.dp)
                Button(onClick = onRestart, modifier = Modifier.testTag("update_restart")) { Text(tr("Restart to finish")) }
            }
            UpdateUi.FinishInPlay -> {
                Gap(10.dp)
                Button(onClick = onOpenPlay, modifier = Modifier.testTag("update_open_play")) { Text(tr("Open Google Play")) }
            }
            else -> Unit
        }
    }
}

private fun sizeText(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1) String.format(com.owl.tracker.i18n.I18n.locale, "%.0f MB", mb.coerceAtLeast(1.0)) else tr("under 1 MB")
}

/** What's new, once after updating (board P4, Round 25). */
@Composable
fun WhatsNewCard(versionName: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val notes = WhatsNew.notes(versionName)
    if (notes.isEmpty()) return
    Panel(modifier = modifier.testTag("whats_new")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("What's new in {0}", versionName), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onClose, modifier = Modifier.testTag("whats_new_close")) {
                Icon(Ic.close, contentDescription = tr("Close What's new"), modifier = Modifier.size(20.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            notes.forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall)
                        Text(n.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Gap(10.dp)
        Button(onClick = onClose, modifier = Modifier.testTag("whats_new_ok")) { Text(tr("Got it")) }
    }
}
