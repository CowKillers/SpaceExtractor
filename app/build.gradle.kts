plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) keystoreProperties.load(keystorePropertiesFile.inputStream())

android { namespace = "com.manus.spaceextractor"; compileSdk = 35
    defaultConfig { applicationId = "com.manus.spaceextractor"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2" }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    signingConfigs { create("release") {
        if (keystorePropertiesFile.exists()) {
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    } }
    buildTypes { getByName("release") { isMinifyEnabled = false; if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("release") } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
