package com.owl.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owl.tracker.ui.screens.ActivityScreen
import com.owl.tracker.ui.screens.BudgetSetupScreen
import com.owl.tracker.ui.screens.BudgetsScreen
import com.owl.tracker.ui.screens.CategoriesScreen
import com.owl.tracker.ui.screens.DbErrorScreen
import com.owl.tracker.ui.screens.DisclosureScreen
import com.owl.tracker.ui.screens.HomeScreen
import com.owl.tracker.ui.screens.ImportFiltersScreen
import com.owl.tracker.ui.screens.ImportReviewScreen
import com.owl.tracker.ui.screens.ImportingScreen
import com.owl.tracker.ui.screens.ReportsScreen
import com.owl.tracker.ui.screens.LockScreen
import com.owl.tracker.ui.screens.MoreScreen
import com.owl.tracker.ui.screens.PrivacyScreen
import com.owl.tracker.ui.screens.SettingsScreen
import com.owl.tracker.ui.screens.SheetHost
import com.owl.tracker.ui.screens.UnlockScreen
import com.owl.tracker.ui.screens.BackupScreen
import com.owl.tracker.ui.screens.WelcomeScreen
import com.owl.tracker.ui.theme.Ic

@Composable
fun OwlTrackerRoot(vm: AppViewModel, platform: Platform) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val glass = settings?.glass ?: true
    androidx.compose.runtime.CompositionLocalProvider(
        com.owl.tracker.ui.theme.LocalGlass provides glass,
        // Everything drawn straight onto the background (lock screen included) gets the theme's text colour
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (glass) com.owl.tracker.ui.theme.GlassBackdrop()
            if (glass) {
                // Stronger secondary text everywhere while cards are see-through
                MaterialTheme(colorScheme = com.owl.tracker.ui.theme.glassColors(), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
                    RootContent(vm, platform, glass)
                }
            } else {
                RootContent(vm, platform, glass)
            }
        }
    }
}

@Composable
private fun RootContent(vm: AppViewModel, platform: Platform, glass: Boolean) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val confirm by vm.confirm.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.text, m.actionLabel, withDismissAction = false, duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val atRoot = screen == null || screen == Screen.HOME || screen == Screen.WELCOME || screen == Screen.DB_ERROR
    // At the root screens the system handles Back (and closes the app), which keeps predictive back working.
    BackHandler(enabled = !locked && (sheet != null || confirm != null || !atRoot)) {
        vm.back()
    }

    if (locked) {
        LockScreen(onUnlock = {
            platform.authenticate("Unlock Owl Tracker", "Use your fingerprint, face or screen lock", onSuccess = { vm.unlocked() }, onFail = { })
        }, canUseDeviceLock = platform.canUseDeviceLock(), onFallbackUnlock = { vm.unlocked() })
        return
    }

    val current = screen
    val tab = current?.tab
    Scaffold(
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
        // Transparent has no matching text colour: without this, text outside cards is black in the dark theme
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tab != null && glass) {
                GlassTabBar(vm, tab)
            } else if (tab != null) {
                NavigationBar {
                    TabItem(vm, Tab.HOME, tab, "Home", Ic.home, Screen.HOME)
                    TabItem(vm, Tab.ACTIVITY, tab, "Activity", Ic.list, Screen.ACTIVITY)
                    TabItem(vm, Tab.BUDGETS, tab, "Budgets", Ic.wallet, Screen.BUDGETS)
                    TabItem(vm, Tab.MORE, tab, "More", Ic.grid, Screen.MORE)
                }
            }
        },
        floatingActionButton = {
            if (current == Screen.HOME || current == Screen.ACTIVITY) {
                FloatingActionButton(onClick = { vm.openSheet(Sheet.AddExpense) }, modifier = Modifier.testTag("add_expense")) {
                    Icon(Ic.plus, contentDescription = "Add expense")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
                Screen.WELCOME -> WelcomeScreen(vm)
                Screen.DISCLOSURE -> DisclosureScreen(vm, platform)
                Screen.IMPORT_FILTERS -> ImportFiltersScreen(vm)
                Screen.IMPORT_REVIEW -> ImportReviewScreen(vm)
                Screen.IMPORTING -> ImportingScreen(vm)
                Screen.BUDGET_SETUP -> BudgetSetupScreen(vm)
                Screen.HOME -> HomeScreen(vm)
                Screen.ACTIVITY -> ActivityScreen(vm)
                Screen.BUDGETS -> BudgetsScreen(vm)
                Screen.MORE -> MoreScreen(vm)
                Screen.CATEGORIES -> CategoriesScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm, platform)
                Screen.INSIGHTS -> ReportsScreen(vm)
                Screen.PRIVACY -> PrivacyScreen(vm)
                Screen.DB_ERROR -> DbErrorScreen(vm)
                Screen.UNLOCK -> UnlockScreen(vm, platform)
                Screen.BACKUP -> BackupScreen(vm, platform)
            }
        }
        SheetHost(vm)
    }

    when (confirm) {
        ConfirmKind.DELETE -> {
            val d by vm.deleteUi.collectAsStateWithLifecycle()
            val n = d.selectedCount
            val what = when (d.option) {
                DeleteOption.OLDER_3 -> "Transactions older than 3 months"
                DeleteOption.OLDER_6 -> "Transactions older than 6 months"
                DeleteOption.ACCOUNT -> "All transactions from ${vm.senderName(d.group)}"
                DeleteOption.ALL_SMS -> "Every transaction imported from SMS"
            }
            AlertDialog(
                onDismissRequest = { vm.dismissConfirm() },
                icon = { Icon(Ic.trash, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete $n ${if (n == 1) "transaction" else "transactions"}?") },
                text = {
                    Text(
                        "$what, with their saved SMS copies, will be erased from Owl Tracker. This can't be undone.\n\n" +
                            "Stays: manual entries, categories, rules and budgets. Your SMS inbox isn't touched."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.confirmDelete() }, modifier = Modifier.testTag("confirm_delete")) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                    }
                },
                dismissButton = { TextButton(onClick = { vm.dismissConfirm() }) { Text("Cancel") } },
            )
        }
        ConfirmKind.RESET -> {
            val secure = platform.canUseDeviceLock()
            AlertDialog(
                onDismissRequest = { vm.dismissConfirm() },
                icon = { Icon(Ic.reset, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Reset Owl Tracker?") },
                text = {
                    Text(
                        "This erases every transaction, saved SMS copy, category, rule, budget and setting, and destroys the encryption key. " +
                            "It can't be undone.\n\nYour SMS inbox isn't touched. You can set Owl Tracker up again straight away."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (secure) platform.authenticate("Confirm reset", "Confirm it's you to erase Owl Tracker", onSuccess = { vm.performReset() }, onFail = { })
                            else vm.performReset()
                        },
                        modifier = Modifier.testTag("confirm_reset"),
                    ) {
                        Text(if (secure) "Confirm it's you" else "Erase everything", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                    }
                },
                dismissButton = { TextButton(onClick = { vm.dismissConfirm() }) { Text("Cancel") } },
            )
        }
        null -> Unit
    }
}

private val TABS = listOf(
    Triple(Tab.HOME, "Home", Screen.HOME),
    Triple(Tab.ACTIVITY, "Activity", Screen.ACTIVITY),
    Triple(Tab.BUDGETS, "Budgets", Screen.BUDGETS),
    Triple(Tab.MORE, "More", Screen.MORE),
)

/**
 * The glass tab bar: a floating capsule with a lens that glides to the tab you pick, stretching a
 * little on the way like a drop of liquid, then settling with a spring.
 */
@Composable
private fun GlassTabBar(vm: AppViewModel, current: Tab) {
    val icons = mapOf(Tab.HOME to Ic.home, Tab.ACTIVITY to Ic.list, Tab.BUDGETS to Ic.wallet, Tab.MORE to Ic.grid)
    val index = TABS.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val pos by androidx.compose.animation.core.animateFloatAsState(
        index.toFloat(), androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 380f), label = "lens",
    )
    val primary = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(32.dp),
            color = com.owl.tracker.ui.theme.glassFillStrong(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = com.owl.tracker.ui.theme.glassEdge(),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
                val slot = maxWidth / TABS.size
                // Stretches most halfway between two tabs
                val frac = kotlin.math.abs(pos - kotlin.math.round(pos))
                val stretch = 1f + 0.35f * frac * 2f
                androidx.compose.foundation.layout.Box(
                    Modifier.offset(x = slot * pos).width(slot).fillMaxHeight()
                        .graphicsLayer { scaleX = stretch; scaleY = 1f - 0.08f * frac * 2f }
                        .clip(RoundedCornerShape(28.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(primary.copy(alpha = 0.20f), primary.copy(alpha = 0.12f)))),
                )
                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                    TABS.forEach { (t, label, target) ->
                        val on = t == current
                        val tint by androidx.compose.animation.animateColorAsState(
                            if (on) primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tab",
                        )
                        androidx.compose.foundation.layout.Column(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                                .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) {
                                    if (target == Screen.ACTIVITY) vm.showActivity() else vm.go(target)
                                }
                                .testTag("tab_${t.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            Icon(icons.getValue(t), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(vm: AppViewModel, tab: Tab, current: Tab, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, target: Screen) {
    NavigationBarItem(
        selected = tab == current,
        onClick = { if (target == Screen.ACTIVITY) vm.showActivity() else vm.go(target) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
    )
}
