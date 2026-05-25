import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Dev-only overrides (publisher token + optional ad-server URL) live in
// `local.properties` — gitignored by default. A fresh clone falls back
// to safe defaults below, so the example still compiles without any
// local config. See local.properties.example for the keys.
//
// `adServerUrl` is intentionally an empty-string sentinel when unset:
// `BuildConfig.String` fields can't be null, so MainActivity reads the
// empty string as "use the SDK default" and omits the override from
// `SessionOptions`.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val devPublisherToken: String = localProps.getProperty("publisherToken") ?: "YOUR_PUBLISHER_TOKEN"
val devAdServerUrl: String = localProps.getProperty("adServerUrl").orEmpty()

android {
    namespace = "so.kontext.ads.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "so.kontext.ads.example"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "PUBLISHER_TOKEN", "\"$devPublisherToken\"")
        buildConfigField("String", "AD_SERVER_URL", "\"$devAdServerUrl\"")
    }

    buildFeatures {
        buildConfig = true
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

    // Traditional-View demo (RecyclerView + InlineAdView). No Compose — the
    // example mirrors a publisher integrating from an XML / RecyclerView UI:
    // ComponentActivity for the screen, lifecycle-runtime-ktx for
    // lifecycleScope, recyclerview for the chat list.
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
