plugins {
    id("com.android.library")
}

android {
    namespace = "pl.michalmatu.aicallbridge.audio"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
