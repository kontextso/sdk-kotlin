// Top-level build file. Per-module config lives in `ads/build.gradle.kts`.
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
}
