package com.owl.tracker

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.owl.tracker.core.KnownApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Guards the privacy promises in the merged manifest, including anything libraries add. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ManifestSecurityTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Suppress("DEPRECATION")
    private val info get() = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES)

    @Test
    fun noInternetPermission() {
        val perms = info.requestedPermissions.orEmpty().toList()
        assertFalse("INTERNET must never be requested: $perms", "android.permission.INTERNET" in perms)
    }

    @Test
    fun onlyOneSensitivePermission() {
        val perms = info.requestedPermissions.orEmpty().toSet()
        assertTrue("android.permission.READ_SMS" in perms)
        val forbidden = setOf(
            "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS", "android.permission.READ_CONTACTS",
            "android.permission.READ_CALL_LOG", "android.permission.ACCESS_FINE_LOCATION", "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES",
        )
        assertEquals(emptySet<String>(), perms intersect forbidden)
    }

    /** The ₹50 unlock talks to the Play Store app only; the billing library's INTERNET permission stays stripped. */
    @Test
    fun playBillingWithoutNetworkAccess() {
        val perms = info.requestedPermissions.orEmpty().toList()
        assertTrue("Play Billing permission missing: $perms", "com.android.vending.BILLING" in perms)
        assertFalse("android.permission.INTERNET" in perms)
    }

    /** Play Console reads the billing library version from this entry; if manifest merging drops it, Play flags the app. */
    @Test
    fun billingLibraryVersionIsDeclaredForPlay() {
        @Suppress("DEPRECATION")
        val meta = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        val version = meta?.get("com.google.android.play.billingclient.version")?.toString()
        assertTrue("Billing library version not in the merged manifest: $version", version != null && version.startsWith("9."))
    }

    /** The backup share link: no other app can open it unless Owl Tracker grants one file for one share. */
    @Test
    fun noContentProviderIsExported() {
        @Suppress("DEPRECATION")
        val providers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertTrue("No providers found", providers.isNotEmpty())
        assertEquals(emptyList<String>(), providers.filter { it.exported }.map { it.name })
        val backups = providers.single { it.authority == "${context.packageName}.backups" }
        assertTrue(backups.grantUriPermissions)
    }

    @Test
    fun backupIsOff() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun onlyTheLauncherActivityIsExported() {
        val exported = info.activities.orEmpty().filter { it.exported }.map { it.name }
        assertEquals(listOf("com.owl.tracker.ui.MainActivity"), exported.filter { it.startsWith("com.owl.tracker") })
    }

    /** Android 11+ hides other apps unless they are listed in <queries>; a missing entry means no icon. */
    @Test
    fun everyMerchantAppIsDeclaredForIconLookup() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val missing = KnownApps.packageNames.filter { "<package android:name=\"$it\" />" !in manifest }
        assertEquals("Add these to <queries> in AndroidManifest.xml", emptyList<String>(), missing)
        // and nothing else: the app may see only the apps it shows icons for
        val declared = Regex("""<package android:name="([^"]+)" />""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals("Only recognised apps may be queried", emptyList<String>(), declared - KnownApps.packageNames.toSet())
    }

    /**
     * Google Play restricts broad app visibility. The only intent query allowed is "apps that pay by UPI";
     * a MAIN/LAUNCHER query (which would reveal nearly every app) must never be added.
     */
    @Test
    fun theOnlyIntentQueryIsUpiApps() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val queries = manifest.substringAfter("<queries>").substringBefore("</queries>")
        val intents = Regex("""<intent>(.*?)</intent>""", RegexOption.DOT_MATCHES_ALL).findAll(queries).map { it.groupValues[1] }.toList()
        assertEquals(1, intents.size)
        assertTrue(intents[0].contains("""android.intent.action.VIEW""") && intents[0].contains("""android:scheme="upi""""))
        assertFalse(queries.contains("android.intent.category.LAUNCHER"))
        assertFalse(queries.contains("android.intent.action.MAIN"))
        assertFalse(queries.contains("<provider"))
    }
}
