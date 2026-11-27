package com.owl.tracker.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.owl.tracker.OwlTrackerApp
import com.owl.tracker.data.ThemeMode
import com.owl.tracker.ui.theme.OwlTrackerTheme
import kotlinx.coroutines.launch

/**
 * The only activity. FragmentActivity (not just ComponentActivity) because BiometricPrompt needs it.
 */
class MainActivity : FragmentActivity() {

    private lateinit var vm: AppViewModel

    /** Google Play's update screens; the answer goes back to the app's update check. */
    private val updateLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) {
        (application as OwlTrackerApp).container.updates.onResult(it.resultCode)
    }

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = vm.onAppForeground()
        override fun onStop(owner: LifecycleOwner) {
            vm.onAppBackground()
            // The home-screen widgets catch up with whatever changed in the app
            com.owl.tracker.nudge.OwlWidgets.refreshAsync(this@MainActivity)
        }
    }

    /** Opened from a nudge or a widget: go where it points. */
    private fun handleOpen(intent: android.content.Intent?) {
        val what = intent?.getStringExtra(com.owl.tracker.nudge.Nudges.EXTRA_OPEN) ?: return
        intent.removeExtra(com.owl.tracker.nudge.Nudges.EXTRA_OPEN)
        vm.openFromOutside(what)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpen(intent)
    }

    /** Whether the app is drawn dark right now (for tests). */
    internal var drawsDark = false
        private set

    /**
     * A copy of the theme choice outside the encrypted database, so the first frame is already the
     * right colour (the settings take a moment to load). The choice itself lives in the settings.
     */
    private val look by lazy { getSharedPreferences("look", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val startTheme = ThemeMode.from(look.getString("theme", null))
        val startPalette = com.owl.tracker.ui.theme.Palette.from(look.getString("palette", null))
        applySystemBars(startTheme.isDark(systemDark()))
        super.onCreate(savedInstanceState)
        val container = (application as OwlTrackerApp).container
        vm = ViewModelProvider(this, viewModelFactory { initializer { AppViewModel(container) } })[AppViewModel::class.java]
        if (savedInstanceState == null) handleOpen(intent)

        lifecycleScope.launch {
            vm.settings.collect { s ->
                applyScreenPrivacy(s?.screenPrivacy ?: true)
                if (s != null && look.getString("theme", null) != s.theme.key) look.edit().putString("theme", s.theme.key).apply()
                // The colours too: the first frame and the home-screen widgets use them before the settings load
                if (s != null && look.getString("palette", null) != s.palette) {
                    look.edit().putString("palette", s.palette).apply()
                    com.owl.tracker.nudge.OwlWidgets.refreshAsync(this@MainActivity)
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        container.updates.attach(updateLauncher) {
            // Owl Tracker's page in the Play Store app (or the web page if there's no Play Store)
            val page = android.net.Uri.parse("market://details?id=$packageName")
            runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, page).setPackage("com.android.vending")) }
                .recoverCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))) }
        }
        if (savedInstanceState == null) runCatching {
            @Suppress("DEPRECATION")
            val p = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode.toInt() else p.versionCode
            container.updates.startUp(code, p.versionName.orEmpty(), updatedFromEarlier = p.lastUpdateTime > p.firstInstallTime)
        }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = (settings?.theme ?: startTheme).isDark(isSystemInDarkTheme())
            // Status and navigation bar icons follow the app's theme, not the phone's
            DisposableEffect(dark) {
                applySystemBars(dark)
                onDispose {}
            }
            val palette = settings?.let { com.owl.tracker.ui.theme.Palette.from(it.palette) } ?: startPalette
            OwlTrackerTheme(dark = dark, palette = palette) {
                // The owl says hello (see AppViewModel.greeting), once the settings are in and not behind the app lock
                val greeting by vm.greeting.collectAsStateWithLifecycle()
                val locked by vm.locked.collectAsStateWithLifecycle()
                androidx.compose.foundation.layout.Box {
                    OwlTrackerRoot(vm, platform)
                    val act = greeting
                    if (act != null && settings != null && !locked) LaunchGreeting(act) { vm.greeted() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as OwlTrackerApp).container.updates.check()
    }

    override fun onDestroy() {
        (application as OwlTrackerApp).container.updates.detach()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }

    private fun systemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Edge to edge, with light or dark bar icons to match the theme, and a matching window colour behind. */
    private fun applySystemBars(dark: Boolean) {
        drawsDark = dark
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        // Seen for a moment at start-up and behind the keyboard; the same colours as @color/window_background
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF1A120C.toInt() else 0xFFFFF1E0.toInt()))
    }

    /** Blocks screenshots, screen recording and the Recents thumbnail while on. */
    private fun applyScreenPrivacy(on: Boolean) {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!on)
    }

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val platform = object : Platform {
        override fun canUseDeviceLock(): Boolean =
            BiometricManager.from(this@MainActivity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        override fun authenticate(title: String, subtitle: String, onSuccess: () -> Unit, onFail: (String) -> Unit) {
            if (!canUseDeviceLock()) { onFail("Set a screen lock on your phone first"); return }
            val prompt = BiometricPrompt(this@MainActivity, ContextCompat.getMainExecutor(this@MainActivity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(info)
        }

        override fun openAppSettings() {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }

        override fun buyUnlock(plan: com.owl.tracker.licence.UnlockStore.Plan) {
            (application as OwlTrackerApp).container.licence.buy(this@MainActivity, plan)
        }

        override fun shareFile(file: java.io.File, mime: String, subject: String, chooser: String) {
            val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.backups", file)
            val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, subject)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = android.content.ClipData.newRawUri(file.name, uri)
            startActivity(Intent.createChooser(send, chooser))
        }

        override fun shareBackup(file: java.io.File) {
            val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.backups", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Owl Tracker backup")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = android.content.ClipData.newRawUri(file.name, uri)
            startActivity(Intent.createChooser(send, "Share your backup"))
        }
    }
}

// The scrims enableEdgeToEdge() uses by default behind 3-button navigation
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/** What the UI needs from the activity, behind an interface so tests can run without a fingerprint. */
interface Platform {
    fun canUseDeviceLock(): Boolean
    fun authenticate(title: String, subtitle: String, onSuccess: () -> Unit, onFail: (String) -> Unit)
    fun openAppSettings()

    /** Opens Google Play's payment sheet for the yearly subscription. */
    fun buyUnlock(plan: com.owl.tracker.licence.UnlockStore.Plan = com.owl.tracker.licence.UnlockStore.Plan.YEARLY)

    /** Opens the share sheet for a backup file (WhatsApp, Drive, Quick Share...). */
    fun shareBackup(file: java.io.File)
    /** Hands [file] (in the shared cache folder) to an app the person picks. */
    fun shareFile(file: java.io.File, mime: String, subject: String, chooser: String)
}
