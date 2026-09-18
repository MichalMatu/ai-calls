plugins {
    id("com.android.library")
}

android {
    namespace = "pl.michalmatu.aicallbridge.realtime"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":audio-bridge"))
    implementation("com.google.code.gson:gson:2.14.0")
}
