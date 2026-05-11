import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Dev-only secrets (publisher token, user id) live in
// `local.properties` — gitignored by default. A fresh clone falls back
// to the placeholders below, so the example still compiles without
// any local config. See local.properties.example for the keys.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val devPublisherToken: String = localProps.getProperty("publisherToken") ?: "YOUR_PUBLISHER_TOKEN"
val devUserId: String = localProps.getProperty("userId") ?: "user-1"

android {
    namespace = "so.kontext.ads.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "so.kontext.ads.example"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "PUBLISHER_TOKEN", "\"$devPublisherToken\"")
        buildConfigField("String", "USER_ID", "\"$devUserId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":ads"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
