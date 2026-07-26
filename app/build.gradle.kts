import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing is optional: CI injects a keystore through these properties (or the
// matching env vars). Without them the release build falls back to the debug key so the
// project still builds for anyone who just cloned it.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyName: String, envName: String): String? =
    keystoreProperties.getProperty(propertyName) ?: System.getenv(envName)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val hasReleaseSigning = releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

// The version comes from the release tag (CI passes -PappVersionName=v1.2.3). The in-app
// updater compares version codes, so this has to increase with every release - a hard-coded
// value would make every build look identical to the one already installed.
val appVersionName: String =
    (findProperty("appVersionName") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() }
        ?: "1.0.0"

// 1.2.3 -> 10203. Monotonic as long as minor and patch stay below 100.
val appVersionCode: Int = runCatching {
    val parts = appVersionName.substringBefore('-').split('.')
    parts[0].toInt() * 10_000 + parts[1].toInt() * 100 + parts[2].toInt()
}.getOrDefault(10_000)

android {
    namespace = "nl.ramon96.medicijntracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "nl.ramon96.medicijntracker"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Where the in-app updater looks for releases. In one place so a fork only has to
        // change this line.
        buildConfigField("String", "UPDATE_REPO", "\"Ramon96/medicine-tracker\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Barcode scanning. This is only the thin client: the scanner UI and the model live in a
    // Google Play services module that is downloaded on demand, which is also why the app needs
    // no CAMERA permission of its own.
    implementation(libs.play.services.code.scanner)

    // Play services drags in a fragment version older than 1.3.0, which makes lint reject the
    // ActivityResult API the notification permission already uses. Nothing here uses fragments;
    // this only raises the floor of what gets resolved.
    constraints {
        implementation(libs.androidx.fragment) {
            because("ActivityResult APIs require androidx.fragment 1.3.0 or newer")
        }
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // org.json ships inside android.jar, where every method throws under unit tests. The real
    // implementation on the test classpath is what lets the barcode lookup's response parsing be
    // tested without a device.
    testImplementation(libs.json)
}
