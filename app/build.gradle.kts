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
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":audio-bridge"))
    implementation(project(":realtime-client"))
}
