package com.owl.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import com.owl.tracker.ui.theme.clay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.owl.tracker.ui.screens.AdjustBudgetsScreen
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
import com.owl.tracker.ui.screens.RulesScreen
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
    androidx.compose.runtime.CompositionLocalProvider(
        // Everything drawn straight onto the background (lock screen included) gets the theme's text colour
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            RootContent(vm, platform)
        }
    }
}

@Composable
private fun RootContent(vm: AppViewModel, platform: Platform) {
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
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // Home's mango header runs up behind the status bar and draws its own room for it
        contentWindowInsets = if (current == Screen.HOME) {
            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            androidx.compose.material3.ScaffoldDefaults.contentWindowInsets
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (tab != null) MangoTabBar(vm, tab) },
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
                Screen.RULES -> RulesScreen(vm)
                Screen.ADJUST_BUDGETS -> AdjustBudgetsScreen(vm)
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
 * The bottom bar, in clay: one moulded pill floating above the bottom edge, the open tab pressed into it, and the
 * glossy mango Add button rising out of its middle.
 */
@Composable
private fun MangoTabBar(vm: AppViewModel, current: Tab) {
    val brand = com.owl.tracker.ui.theme.LocalBrand.current
    val icons = mapOf(Tab.HOME to Ic.home, Tab.ACTIVITY to Ic.list, Tab.BUDGETS to Ic.wallet, Tab.MORE to Ic.grid)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        com.owl.tracker.ui.theme.ClaySurface(RoundedCornerShape(30.dp), Modifier.fillMaxWidth(), color = brand.bar, elevation = 16.dp) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TABS.forEachIndexed { i, (t, label, target) ->
                    if (i == 2) androidx.compose.foundation.layout.Spacer(Modifier.width(78.dp))
                    val on = t == current
                    val tint by androidx.compose.animation.animateColorAsState(if (on) brand.barActive else brand.barIdle, label = "tab")
                    val shape = RoundedCornerShape(22.dp)
                    androidx.compose.foundation.layout.Column(
                        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp)
                            .then(if (on) Modifier.clay(shape, brand.clay, brand.clay.pressed, pressed = true) else Modifier.clip(shape))
                            .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) {
                                if (target == Screen.ACTIVITY) vm.showActivity() else vm.go(target)
                            }
                            .testTag("tab_${t.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(icons.getValue(t), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        // Big text: the labels grow a little, then get smaller to fit, never "Activi / y"
                        com.owl.tracker.ui.components.CapFontScale(1.2f) {
                            com.owl.tracker.ui.components.FitText(
                                label, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                                minScale = 0.7f,
                            )
                        }
                    }
                }
            }
        }
        AddButton(
            onClick = { vm.openSheet(Sheet.AddExpense) },
            ring = brand.bar,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-30).dp),
        )
    }
}

/** The Add button: a glossy mango ball with a highlight and its shadow, ringed in the bar's colour. */
@Composable
private fun AddButton(onClick: () -> Unit, ring: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val brand = com.owl.tracker.ui.theme.LocalBrand.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    Box(
        modifier.size(74.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(14.dp, androidx.compose.foundation.shape.CircleShape, ambientColor = brand.add[2], spotColor = brand.add[2])
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(ring)
            .padding(5.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(com.owl.tracker.ui.theme.SphereBrush)
            .drawBehind {
                // Shade underneath and a bright spot up top, so it reads as a ball
                drawCircle(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.55f to androidx.compose.ui.graphics.Color.Transparent,
                        1f to androidx.compose.ui.graphics.Color(0x66964600),
                    ),
                )
                drawOval(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.1f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.34f, size.height * 0.2f),
                )
            }
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Add expense") { onClick() }
            .semantics { contentDescription = "Add expense" }
            .testTag("add_expense"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ic.plus, contentDescription = null, tint = brand.onAdd, modifier = Modifier.size(32.dp))
    }
}
