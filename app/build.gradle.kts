plugins {
    id("com.android.application")
}

android {
    namespace = "pl.michalmatu.aicallbridge"
    // Kadb Android AAR metadata requires API 37; targetSdk stays on tested Android 16.
    compileSdk = 37

    defaultConfig {
        applicationId = "pl.michalmatu.aicallbridge"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // S8 lab APK installs side-by-side with the stable dialer on Samsung.
    // Never use the production package ID for an experimental debug build.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".s8lab"
            versionNameSuffix = "-s8lab"
            // Only the owner's 64-bit Samsung S22+ target. Do not ship
            // x86/x86_64/32-bit native LLM binaries in the test installer.
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    // BouncyCastle modules ship an identical META-INF/LICENSE.md path.
    // Retain one copy in the APK rather than silently dropping license notices.
    packaging {
        resources {
            pickFirsts += "META-INF/LICENSE.md"
        }
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":conversation-core"))
    implementation(project(":conversation-tools"))
    implementation(project(":audio-bridge"))
    implementation(project(":privileged-helper"))
    implementation(project(":realtime-client"))
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.0")
    implementation("com.google.mediapipe:tasks-audio:1.2.0")
    implementation("com.google.code.gson:gson:2.14.0")
    // Existing cached Activity artifact supplies lifecycle-aware predictive Back handling.
    implementation("androidx.activity:activity:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    // Explicit, user-approved local ADB pairing for the isolated S8 lab only.
    implementation("com.flyfishxu:kadb:2.1.5")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
