import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** The default version (see version.properties). */
val appVersion = java.util.Properties().apply {
    load(java.io.StringReader(providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get()))
}

android {
    namespace = "com.owl.tracker"
    compileSdk = 36

    defaultConfig {
        // The app's permanent identity on Google Play. It can never change after the first upload.
        applicationId = "com.owl.tracker"
        minSdk = 26
        targetSdk = 36
        // The release workflow sets these; every Play upload needs a higher versionCode than the last.
        // Other builds take them from version.properties at the top of the project.
        versionCode = System.getenv("OWL_TRACKER_VERSION_CODE")?.toIntOrNull() ?: appVersion.getProperty("versionCode").toInt()
        versionName = System.getenv("OWL_TRACKER_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: appVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // A fixed debug key, committed on purpose: every GitHub build is signed the same way, so a new
        // app-debug.apk installs over the old one and keeps your data. It is not secret and is for testing only.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing comes from environment variables so no release key ever lives in the repository.
        create("release") {
            val storePath = System.getenv("OWL_TRACKER_KEYSTORE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("OWL_TRACKER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OWL_TRACKER_KEY_ALIAS")
                keyPassword = System.getenv("OWL_TRACKER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // For local testing only, a release build without a configured key is signed with the debug key.
            // Never upload a debug-signed build to Google Play.
            signingConfig = if (System.getenv("OWL_TRACKER_KEYSTORE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.systemProperty("robolectric.logging", "stderr")
            }
        }
        animationsDisabled = true
    }

    // UI test helpers shared by the Robolectric tests and the emulator tests.
    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").java.srcDir("src/sharedTest/kotlin")
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
        }
    }

    lint {
        abortOnError = true
        checkDependencies = false
        warningsAsErrors = false
        // App strings are English-only for now.
        disable += setOf("MissingTranslation", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "ObsoleteLintCustomCheck")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // The ₹50 unlock. Talks only to the Play Store app on the phone; its INTERNET permission is
    // stripped in AndroidManifest.xml, and CI fails if it ever comes back.
    implementation(libs.google.play.billing)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Encrypted database: SQLCipher behind the standard AndroidX SupportSQLite interfaces.
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    implementation("net.zetetic:sqlcipher-android:${libs.versions.sqlcipher.get()}@aar")

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
