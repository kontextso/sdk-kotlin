// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    kotlin("jvm") version "2.2.0"
}

group = "com.kontext.ads"
version = libs.versions.sdkkotlin

kotlin {
    jvmToolchain(21)
}
