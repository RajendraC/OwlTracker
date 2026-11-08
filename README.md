# Owl Tracker

Owl Tracker (package `com.owl.tracker`) is an Android expense manager that reads bank, card and UPI SMS on your phone and turns them into a monthly dashboard with budgets. No internet permission: nothing ever leaves the phone.

## What's in the box

| Area | What it does |
| --- | --- |
| SMS import | Reads only the SMS inbox (READ_SMS). Keeps bank/card/UPI transactions; drops OTPs, promotions, reminders, failed payments and every message from a person. |
| Import choices | Pick senders, how far back (3/6/12 months), money in/out, a minimum amount and skip words, then untick anything on a review list. Change it later and re-import. |
| Dashboard | Budget ring with pace, over-budget banners, category donut, recent transactions with real app icons for about 170 Indian apps (Swiggy, FanCode, PhonePe, HP PAY, My FASTag, Policybazaar, YONO SBI…) when installed, including the bank that sent each SMS; any UPI app or a cropped picture can be chosen by hand, and coloured initials otherwise. Icons come from Android for the apps listed in the manifest's `<queries>`: no permission, and no list of installed apps is ever read. |
| Budgets | Overall and per-category monthly budgets, pace marker, budget month that can start on your salary day. |
| Categories | 18 defaults; add, rename, recolour, change icon, archive. "Always use" rules learn from your corrections. |
| Reports | Range of 1, 3, 6 or 12 months; spent, income and net against the previous period; **Income & expenses** (income by category, expenses by category, saved or overspent, each month side by side, with savings, transfers and left-out payments shown apart); **Ways to save** (see below); tappable day or month trend, category donut, top merchants with app icons, weekday chart and highlights. Tapping anything narrows every section and lists the matching payments. Motion in the spirit of Apple Health: amounts count up, bars rise one after another, the category ring sweeps round and cards rise into place (all under a second, and off when Android's "Remove animations" is on). |
| Ways to save | From the last 3 budget months and this one, worked out on the phone: categories over (or heading over) their usual month, one in five fewer everyday payments (food, shopping, entertainment), subscriptions paid in two or more months, and the share of income saved against a 20% goal. Home shows the total a month and the biggest way, hidden with × until next month; Reports lists them all. |
| Money in | Shown with a + and never counted as spending, not even a refund: it counts as income on Income & expenses, can be left out there, or deleted. Messages like "sent to your A/c" or "paid to you by" are money in. |
| Backup and restore | A `.json` backup, password-encrypted by default (AES-256-GCM, PBKDF2 600k), saved to a file or shared through the share sheet; restored by adding (duplicates skipped) or replacing, also from the welcome screen of a new phone. Never contains SMS text, senders, account or card numbers, UPI IDs or references. |
| Delete and reset | Delete one transaction (with undo, and it never comes back), by age, by bank or card, all SMS imports, or reset everything. |
| Free trial and unlock | 7 days free from install, then a one-time ₹50 unlock through Google Play Billing (product `owl_tracker_unlock`). After the trial, the app is read-only until unlocked: everything stays visible and deletable, but SMS imports, new expenses and budget or category changes pause. Pending UPI payments unlock when Google Play confirms them. |
| Security | SQLCipher AES-256 database; key wrapped by Android Keystore (StrongBox when available); optional biometric app lock; screenshot blocking; no backups; no INTERNET. |

## Free trial and the ₹50 unlock

- The trial starts at Android's install time (`licence/Licence.kt`), so clearing storage or resetting the app doesn't restart it; uninstalling and reinstalling does, since there is no server to remember it. Putting the phone's clock back doesn't add days.
- Payment goes through Google Play Billing 9 (`licence/PlayBillingStore.kt`), which talks only to the Play Store app on the phone. The billing library's INTERNET permission is removed in the manifest, and CI fails if it comes back.
- Once Google Play confirms the purchase it is remembered on the phone, so the app stays unlocked offline. Purchases are acknowledged (Google refunds unacknowledged ones after 3 days) and restored on a new phone with **Restore purchase**.
- Optional: paste the app's licence key from Play Console (Monetize with Play → Monetization setup → Licensing) into `app/src/main/res/values/licence.xml` to check each purchase's signature.
- Debug builds have **Settings → End the free trial now** to see read-only mode without waiting 7 days.

## Releasing to Google Play

1. Create an upload key on your PC and add four repository secrets: `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`.
2. Actions → **Release bundle for Google Play** → Run workflow, with the version name (e.g. `1.0.0`). The version code is set automatically (100 + run number), so every release is higher than the last.
3. Download the `owl-tracker-aab-<version>` artifact and upload the `.aab` in Play Console. The run summary shows the upload key's SHA-256 fingerprint to compare with Play Console.

Before building, `ci/CheckUploadKey.java` opens the key file and checks the alias and passwords. It ignores stray spaces, line breaks or quotes in the secrets and uses the file's only key if `UPLOAD_KEY_ALIAS` doesn't match its name; each of these shows as a warning on the run. Any other problem stops the run with a message naming the secret to fix.

The privacy policy is published from `docs/` with GitHub Pages: https://rajendrac.github.io/OwlTracker/privacy.html. Choosing **Explore with sample data** on the SMS screen imports 21 built-in example messages. Use it for store screenshots and Play review.

## Project layout

- `core/` – pure Kotlin: SMS parser, sender/bank registry, merchant registry, categorizer, de-duplication, import filters, budget maths. Fast JVM tests with 21 sample bank messages.
- `app/` – Android app: Jetpack Compose UI, encrypted database, Keystore, SMS reader, background sync.
  - `src/test` – Robolectric tests: full user journeys on Android 8.0, 9, 11, 13, 14, 15 and 16, six screen sizes, manifest security checks, import pipeline.
  - `src/androidTest` – emulator tests: Keystore + SQLCipher encryption, real SMS inbox reading, the production app end to end.
- `.github/workflows/android.yml` – builds the APKs and runs everything above, plus emulators for API 26, 29, 31, 33, 34 (tablet), 35 and 36.

## Build and test

Requirements: Android Studio (latest stable), which bundles a JDK. On the command line use JDK 21.

```
./gradlew :core:test                 # parser and budget logic
./gradlew :app:testDebugUnitTest     # Robolectric: every Android version, screen sizes
./gradlew :app:assembleDebug         # app-debug.apk
./gradlew :app:connectedDebugAndroidTest   # with a phone or emulator connected
```

Every push to `main` runs `.github/workflows/android.yml` on GitHub Actions:

| Stage | What runs |
| --- | --- |
| Build | Parser and budget tests; debug and release APKs; Robolectric journeys on Android 8, 9, 11, 13, 14, 15 and 16 plus small phone, large phone, tablet, foldable, dark mode and 1.5× font; lint; no INTERNET permission; 16 KB page-size alignment |
| Emulators | Pixel 2 (Android 8), Pixel 3a (10), Pixel 5 (12), Pixel 6 (13, 15, 16), Pixel C tablet (14): encrypted database, Keystore key, real SMS inbox, full app journey and reset |

The APKs are attached to each run as the `owl-tracker-apks` artifact. Android 17 emulator images don't boot reliably on GitHub's standard runners, so Android 17 is tested on a real phone.

Installing on your phone: see the step-by-step guide shared with this project.

## Versions

AGP 8.13.2 · Gradle 8.14.3 · Kotlin 2.2.20 · Compose BOM 2025.12.00 (Material 3) · compileSdk/targetSdk 36 · minSdk 26 · SQLCipher for Android 4.10.0 · Robolectric 4.16.1.

## Before publishing to Google Play

1. Change `applicationId` in `app/build.gradle.kts` (it can never change after the first upload).
2. Put a real contact email in the in-app privacy policy (`PrivacyScreen`) and publish the same text at a public URL.
3. Create an upload key and set `OWL_TRACKER_KEYSTORE`, `OWL_TRACKER_KEYSTORE_PASSWORD`, `OWL_TRACKER_KEY_ALIAS`, `OWL_TRACKER_KEY_PASSWORD`, then `./gradlew :app:bundleRelease`.
4. Fill in the SMS Permissions Declaration (SMS-based money management) in Play Console.
