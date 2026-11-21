plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.soundrepeater"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.soundrepeater"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
