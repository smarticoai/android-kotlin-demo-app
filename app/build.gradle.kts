plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    // @Serializable for the social-login request/response bodies
    kotlin("plugin.serialization")
}

// The Firebase config is not committed (shared demo project) and the Google
// Services plugin hard-fails when the file is absent, which would make a clean
// clone unbuildable. Applied only when the file is actually there: without it
// the app builds and runs, and push token registration logs and skips.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "ai.smartico.fakecasino"
    compileSdk = 35

    defaultConfig {
        // Reuses the RN demo's app id on purpose: Google Sign-In, Facebook and
        // FCM are all registered against THIS id + our debug signing key, so the
        // demo gets working auth and pushes without a new registration round.
        applicationId = "ai.smartico.rnexpo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // The SAME debug key the RN demo uses: Google Sign-In / Facebook are
        // registered against its SHA-1 (5E:8F:16:…) plus the app id above, so
        // signing with any other debug key silently breaks social login.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // demo app — the debug key keeps installs frictionless
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }

    buildFeatures { compose = true }
}

dependencies {
    // The SDK, resolved from Maven Central like any other consumer would.
    // Releases are immutable, so this number is what pins the demo to a
    // published SDK — bump it to move to a newer one.
    implementation("ai.smartico:kotlin-public-api:0.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // animated AVIF/WebP support for the slot sprites
    implementation("io.coil-kt:coil-gif:2.7.0")
    // the demo backend (SL_SERVER) is plain HTTP, unrelated to the SDK
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // social login
    // Credential Manager is the current Google sign-in API; the old
    // play-services-auth intent flow is deprecated and its picker no longer
    // renders on recent Play Services builds.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Push notifications. The SDK carries the Smartico half (cid 1003 token
    // registration and the lifecycle reports); FCM is what actually delivers
    // the message to the device, and that is Google's, not ours.
    // NotificationCompat/NotificationManagerCompat: channels and the Android 13
    // permission check behind one API that works back to minSdk 24.
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
