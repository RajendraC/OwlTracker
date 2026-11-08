package com.owl.tracker

import android.content.Context
import android.graphics.Bitmap
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.owl.tracker.backup.BackupManager
import com.owl.tracker.core.Institutions
import com.owl.tracker.core.KnownApps
import com.owl.tracker.core.MerchantPicture
import android.net.Uri
import android.graphics.BitmapFactory
import android.content.Intent
import com.owl.tracker.data.Db
import com.owl.tracker.data.Repository
import com.owl.tracker.licence.Licence
import com.owl.tracker.licence.NoStore
import com.owl.tracker.licence.PlayBillingStore
import com.owl.tracker.licence.TrialClock
import com.owl.tracker.licence.UnlockStore
import com.owl.tracker.security.KeyManager
import com.owl.tracker.sms.AndroidSmsSource
import com.owl.tracker.sms.ImportEngine
import com.owl.tracker.sms.SmsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Wires the app together by hand: one place to swap in fakes for tests. */
class AppContainer(
    val context: Context,
    dbFactory: () -> SupportSQLiteOpenHelper.Factory,
    dbName: String?,
    val sms: SmsSource,
    val clock: Clock,
    val keyManager: KeyManager?,
    unlockStore: UnlockStore = NoStore(),
    installedAt: () -> Long = { TrialClock.installTime(context) },
) {
    val db = Db(context, dbFactory, dbName)
    val appIcons: AppIcons = AppIcons(context) { id -> repo.pictureBytes(id) }
    val repo: Repository = Repository(db, clock, appIcons::bankApp)
    val importEngine = ImportEngine(sms, repo, clock, bankApp = appIcons::bankApp)
    val backups = BackupManager(context, repo, clock)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Kept apart from the encrypted database on purpose: "Reset Owl Tracker" erases the database
    // but must not restart the free trial or forget a purchase.
    private val licencePrefs = context.getSharedPreferences(TrialClock.PREFS, Context.MODE_PRIVATE)

    /** The 7-day trial and the ₹50 unlock. */
    val licence = Licence(
        store = unlockStore,
        trial = TrialClock(licencePrefs, clock, installedAt, TrialClock.isDebuggable(context)),
        prefs = licencePrefs,
        scope = scope,
    )

    /**
     * Erases everything: the Keystore key that protected the database, then the database file.
     * Runs on the database thread so no query can reopen the old file halfway through; the next
     * read creates a fresh database under a new key.
     */
    suspend fun eraseEverything() = repo.exclusive {
        keyManager?.destroy()
        db.destroy()
    }

    fun keyProtection(): String = keyManager?.protectionLevel() ?: "Test database (not encrypted)"

    companion object {
        // Permanent on-device file name, kept from the app's working name (SpendLens). Renaming it would
        // orphan the encrypted data in copies already installed, and it is never shown to anyone.
        const val DB_NAME = "spendlens.db"

        fun production(context: Context): AppContainer {
            val app = context.applicationContext
            val keys = KeyManager(app)
            return AppContainer(
                context = app,
                dbFactory = {
                    System.loadLibrary("sqlcipher")
                    SupportOpenHelperFactory(keys.sqlcipherPassphrase())
                },
                dbName = DB_NAME,
                sms = AndroidSmsSource(app),
                clock = Clock.systemDefaultZone(),
                keyManager = keys,
                unlockStore = runCatching { PlayBillingStore(app, app.getString(R.string.play_licence_key)) as UnlockStore }
                    .getOrElse { NoStore() },
            )
        }
    }
}

/**
 * Icons for merchants: an installed app's own icon by package name (Swiggy, Uber...), or a picture the
 * user chose ("pic:…", read through [pictures]). Android draws app icons for us; we never ship anyone's
 * logo. Only apps the manifest's <queries> block names (or that pay by UPI) are visible on Android 11+.
 */
class AppIcons(private val context: Context, private val pictures: (String) -> ByteArray? = { null }) {
    private val cache = ConcurrentHashMap<String, Any>()
    private object Missing

    fun get(packageName: String?): Bitmap? {
        if (packageName == null) return null
        val v = cache.getOrPut(packageName) {
            try {
                if (MerchantPicture.isPicture(packageName)) {
                    pictures(packageName)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: Missing
                } else {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Missing
            } catch (e: Exception) {
                Missing
            }
        }
        return v as? Bitmap
    }

    /** The first installed app of a bank, card or wallet (by its sender id), for showing its icon. */
    fun bankApp(institutionId: String?): String? = Institutions.appsFor(institutionId).firstOrNull { get(it) != null }

    /**
     * Apps a merchant's icon can be picked from: the recognised apps that are installed, and every app on
     * the phone that pays by UPI (the manifest's narrow upi:// query, which covers bank and wallet apps).
     * The phone's full list of apps is never read. Call off the main thread.
     */
    fun pickableApps(): List<KnownApp> {
        val pm = context.packageManager
        val upi = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")), 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return (KnownApps.packageNames + upi).distinct().filter { it != context.packageName }.mapNotNull { pkg ->
            if (get(pkg) == null) return@mapNotNull null
            val label = runCatching {
                @Suppress("DEPRECATION")
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: KnownApps.nameFor(pkg)
            KnownApp(pkg, label)
        }.sortedBy { it.label.lowercase() }
    }
}

data class KnownApp(val packageName: String, val label: String)
