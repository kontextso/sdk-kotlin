plugins {
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "com.kontext.ads"
version = libs.versions.sdkkotlin.get()

android {
    namespace = "com.kontext.ads"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-proguard-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false // TODO revert to true once the proguard files are setup correctly
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                artifactId = "sdk"
            }
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
    implementation(kotlin("stdlib"))
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.webkit)

    implementation(libs.ktorfit.lib)
    ksp(libs.ktorfit.compiler)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
}
