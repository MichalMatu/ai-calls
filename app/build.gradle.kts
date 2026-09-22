plugins {
    id("com.android.application")
}

android {
    namespace = "pl.michalmatu.aicallbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.michalmatu.aicallbridge"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
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
    implementation(project(":audio-bridge"))
    implementation(project(":privileged-helper"))
    implementation(project(":realtime-client"))
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}
