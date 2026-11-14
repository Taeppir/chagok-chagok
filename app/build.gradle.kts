import java.util.Properties

val communityConfig = Properties().apply {
    rootProject.file("community.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun clientSetting(name: String) = "\"" + communityConfig.getProperty(name, "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.taeppir.chagokchagok"
    compileSdk = 34

    buildFeatures { buildConfig = true }

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", clientSetting("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", clientSetting("SUPABASE_PUBLISHABLE_KEY"))
        applicationId = "com.taeppir.chagokchagok"
        testInstrumentationRunner = "com.taeppir.chagokchagok.AudioRecipeTest"
        minSdk = 34
        targetSdk = 34
        versionCode = 8
        versionName = "0.5.0"
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
