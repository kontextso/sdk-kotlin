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

group = "so.kontext"
version = libs.versions.sdkkotlin.get()

android {
    namespace = "so.kontext.ads"
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
                artifactId = "ads"

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
