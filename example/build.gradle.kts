import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile = rootProject.file("example/keystore/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.isFile) {
    FileInputStream(keystorePropertiesFile).use {
        keystoreProperties.load(it)
    }
}

android {
    namespace = "so.kontext.ads.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "so.kontext.ads.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.isFile) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storePassword = keystoreProperties["storePassword"] as String
                storeFile = rootProject.file("example/keystore/keystore")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs["release"]
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()

    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(ktlintVersion)
    }

    kotlinGradle {
        target("*.gradle.kts")
        targetExclude("**/build/**/*.kts")
        ktlint(ktlintVersion)
    }

    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**/*.xml")
    }
}

detekt {
    val configPath = "${rootDir.absolutePath}/extras/detekt.yml"

    buildUponDefaultConfig = true
    config.from(configPath)
    parallel = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(project(":ads"))
}
