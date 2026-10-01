plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// google-services.json liegt bewusst im Repository (kein Geheimnis im Firebase-Sinn, siehe README und Plan,
// Risiko 2c). Fehlt die Datei (z. B. bei einem eigenen Firebase-Projekt), baut das Projekt trotzdem; die App
// zeigt dann einen klaren Hinweis statt abzustürzen.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Fester Signaturschlüssel, damit neue Versionen ohne Deinstallieren als Update installiert werden können.
// Der Schlüssel liegt nie im Repository: GitHub Actions legt ihn aus den Secrets OBELIX_KEYSTORE_BASE64 und
// OBELIX_KEYSTORE_PASSWORD als Datei ab und setzt diese Umgebungsvariablen (siehe .github/workflows/build.yml).
// Fehlen sie (z. B. lokal oder bei Pull Requests), gilt der übliche zufällige Debug-Schlüssel.
val obelixKeystore: File? = System.getenv("OBELIX_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
val obelixKeystorePassword: String? = System.getenv("OBELIX_KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }

android {
    namespace = "de.hagi089.obelix"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.hagi089.obelix"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "23" // Bei jedem Deployment erhöhen (versionCode +1, versionName zweistellig: 11, 12, ...)
    }

    signingConfigs {
        if (obelixKeystore != null && obelixKeystorePassword != null) {
            create("obelix") {
                storeFile = obelixKeystore
                storePassword = obelixKeystorePassword
                keyAlias = "obelix"
                keyPassword = obelixKeystorePassword
            }
        }
    }

    buildTypes {
        val fixedSigning = signingConfigs.findByName("obelix")
        getByName("debug") {
            if (fixedSigning != null) signingConfig = fixedSigning
        }
        release {
            // Verteilt wird die Release-APK (nicht debuggable). R8 (Verkleinern/Verschleiern) bleibt aus: Es wurde nie
            // getestet und kann Firebase, Serialisierung und osmdroid zur Laufzeit brechen (Plan, Entscheidung 48).
            isMinifyEnabled = false
            isShrinkResources = false
            if (fixedSigning != null) signingConfig = fixedSigning
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Phase 9: Standort (Google Play Services) und Karte (OpenStreetMap, ohne API-Schlüssel)
    implementation(libs.play.services.location)
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
}
