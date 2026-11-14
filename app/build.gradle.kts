plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.taeppir.chagokchagok"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.taeppir.chagokchagok"
        minSdk = 34
        targetSdk = 34
        versionCode = 5
        versionName = "0.3.2"
    }

    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
}
