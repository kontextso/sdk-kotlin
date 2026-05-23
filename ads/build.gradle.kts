plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// SDK version — single source of truth for both the published Maven
// artifact version AND the runtime `BuildConfig.SDK_VERSION` field that
// gets sent in `/init` and `/preload` request bodies. Defaults to the
// catalog value; CI patch releases override via `-PsdkVersion=X.Y.Z`
// (see .github/workflows/publish_sdk.yml).
val sdkVersion: String = providers.gradleProperty("sdkVersion")
    .orElse(libs.versions.sdk.kotlin)
    .get()

group = "so.kontext"
version = sdkVersion

android {
    // Java/Kotlin package prefix used to generate BuildConfig + R.
    // Must match the Kotlin source package of the public API
    // (`so.kontext.ads.*`).
    namespace = "so.kontext.ads"

    // SDK we compile against — Android 16 (Baklava). Doesn't affect
    // runtime behaviour on older devices, only compile-time visibility
    // of new framework symbols.
    compileSdk = 36

    defaultConfig {
        // Minimum runtime API. Android 7.0 (Nougat).
        minSdk = 24

        // ProGuard rules packaged into the .aar; auto-applied during
        // the consuming app's R8 pass.
        consumerProguardFiles("consumer-proguard-rules.pro")

        // SDK version baked in at build time. Read via
        // `BuildConfig.SDK_VERSION` from `SDKInfo.kt` so the version
        // sent to the ad server can never drift from the published
        // Maven artifact version.
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
    }

    buildFeatures {
        // Enables the `androidx.compose.*` source sets and Compose
        // compiler integration. Required for `ui/InlineAd.kt`'s
        // public `@Composable` API.
        compose = true
        // Generates `BuildConfig.java` so `buildConfigField` values are
        // reachable from Kotlin source.
        buildConfig = true
    }

    // Compose compiler is configured by the `org.jetbrains.kotlin.plugin.compose`
    // plugin (applied above) — the old `composeOptions { kotlinCompilerExtensionVersion }`
    // block is unsupported on Kotlin 2.x.

    buildTypes {
        release {
            // SDK doesn't minify itself — the consuming app's R8 pass
            // does that with full visibility into what's used.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.all {
            // JUnit 5 (Jupiter).
            it.useJUnitPlatform()
        }
        // Stubs Android framework methods called from unit tests
        // (e.g. `android.util.Log.d(...)`) to return defaults instead
        // of throwing `RuntimeException("Method ... not mocked")`.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    // Requires every top-level / member declaration to specify
    // `public` / `internal` / `private` explicitly. Catches the
    // case where someone forgets a visibility modifier and a class
    // accidentally leaks to consumers.
    explicitApi()

    // All Kotlin compilation + tests run against JDK 17 regardless of
    // the developer's locally installed JDK.
    jvmToolchain(17)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = group.toString(),
        artifactId = "ads",
        version = sdkVersion,
    )

    pom {
        name.set("Kontext Kotlin SDK")
        description.set("Android SDK for integrating Kontext's AI-powered contextual ads into chat apps.")
        url.set("https://www.kontext.so/advertisers")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("kontext")
                name.set("kontext")
            }
        }

        scm {
            url.set("https://github.com/kontextso/sdk-kotlin")
            connection.set("scm:git:https://github.com/kontextso/sdk-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/kontextso/sdk-kotlin.git")
        }
    }
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()

    // ktlint default style enforces stricter rules than the v4
    // codebase (imported from the sdk-v4 monorepo) was formatted under.
    // These overrides match the existing style so we don't have to
    // reformat 100+ files as part of the v4 migration:
    //   - no-wildcard-imports: kotlinx.coroutines.* / Assertions.* are idiomatic
    //   - import-ordering: source uses IntelliJ default ordering, not ktlint's
    //     strict lexicographic order
    val ktlintConfig = mapOf(
        // Package-concept filenames are intentional (e.g. DebugEvent.kt
        // holds DebugEventHandler typealias; AdEvent.kt holds AdEventHandler).
        // ktlint's strict file=class match is too narrow for that style.
        "ktlint_standard_filename" to "disabled",
    )

    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(ktlintVersion).editorConfigOverride(ktlintConfig)
    }

    kotlinGradle {
        target("*.gradle.kts")
        targetExclude("**/build/**/*.kts")
        ktlint(ktlintVersion).editorConfigOverride(ktlintConfig)
    }

    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**/*.xml")
    }
}

detekt {
    // Layer our config on top of detekt's bundled defaults.
    buildUponDefaultConfig = true
    config.from("${rootDir.absolutePath}/extras/detekt.yml")
    parallel = true
}

dependencies {
    // ---- Kotlin runtime libraries ----
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // ---- AndroidX ----
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)

    // ---- Jetpack Compose ----
    // The BOM (Bill of Materials) pins a version-aligned set of
    // Compose libraries; the individual `androidx.compose.*` deps
    // below are listed without versions and inherit from the BOM.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)

    // ---- KontextKit (shared device-info / privacy / UI / OMID) ----
    // Brings in androidx.browser, androidx.preference,
    // play-services-ads-identifier transitively, so they don't need
    // to be declared here. Since kontextkit-android 0.0.6 the IAB
    // OMID AAR also flows in transitively as
    // `so.kontext.iab:omsdk-android:1.6.4` (KontextKit publishes a
    // thin redistribution of the IAB-Tech-Lab-distributed AAR under
    // its own coordinate so it's resolvable from Maven Central
    // without vendoring).
    implementation(libs.kontext.kit)

    // ---- Testing ----
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.json)
}
