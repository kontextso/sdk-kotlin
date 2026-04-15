plugins {
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

val sdkVersion = providers.gradleProperty("sdkVersion")
    .orElse(libs.versions.sdkkotlin)

group = "so.kontext"
version = sdkVersion.get()

android {
    namespace = "so.kontext.ads"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-proguard-rules.pro")

        buildConfigField(
            type = "String",
            name = "SDK_VERSION",
            value = "\"$version\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform {
                // Robolectric is JUnit 4 — junit-vintage lets the JUnit Platform
                // run both engines side-by-side with JUnit 5.
                includeEngines("junit-jupiter", "junit-vintage")
            }
        }
        // Robolectric needs real Android resources + AndroidManifest.
        unitTests.isIncludeAndroidResources = true
        // Return default values from stubbed Android framework calls (e.g. Log.e)
        // in tests that run without Robolectric.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = group.toString(),
        artifactId = "ads",
        version = sdkVersion.get(),
    )

    pom {
        name.set("Kotlin ads sdk")
        description.set("Kotlin ads sdk")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.browser)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)

    implementation(libs.androidx.webkit)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.play.services.ads.identifier)
    implementation(libs.androidx.preference)

    implementation("iab:omsdk-android:1.6.4")

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    // Robolectric runs Android framework classes on the JVM.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit) // Robolectric uses JUnit 4
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:${libs.versions.junit.jupiter.get()}")
}
