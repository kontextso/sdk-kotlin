rootProject.name = "sdk-kotlin"

// ---------------------------------------------------------------------------
// pluginManagement — repositories Gradle searches for *plugins*
// (com.android.library, kotlin("android"), vanniktech maven-publish,
// spotless, detekt) applied via the `plugins { ... }` block in
// build.gradle.kts files. Plugin *versions* live in
// gradle/libs.versions.toml so this block only declares repositories.
// ---------------------------------------------------------------------------
pluginManagement {
    repositories {
        // AGP (Android Gradle Plugin) lives here. The content filter
        // says "only ever ask Google's repo for these groups" — Gradle
        // skips a redundant HTTP probe when resolving Kotlin / kotlinx
        // plugins (which live on Maven Central).
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Kotlin Gradle Plugin + most JetBrains-published plugins.
        mavenCentral()
        // Community plugins (vanniktech maven-publish, spotless, detekt).
        gradlePluginPortal()
    }
}

// ---------------------------------------------------------------------------
// dependencyResolutionManagement — repositories Gradle searches for
// *libraries* referenced by `dependencies { implementation("...") }`
// blocks in build.gradle.kts.
// ---------------------------------------------------------------------------
dependencyResolutionManagement {
    // Forbids individual modules from declaring their own
    // `repositories { ... }` blocks. Centralises the list here so a
    // future module can't quietly add a sketchy repo via a stray PR.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        // IAB OMID AAR — vendored. IAB ships OMID as a downloadable
        // zip, not a public Maven repo, so we host it here. The AAR
        // is loaded reflectively at runtime by KontextKit's OmManager.
        maven { url = uri("${rootDir}/local-maven") }
        // AndroidX, Material, Play Services.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

include(":ads", ":example")

// ---------------------------------------------------------------------------
// TEMPORARY composite build of KontextKit
// ---------------------------------------------------------------------------
// Wires `so.kontext.kit:kontext-kit-android` to the sibling kontextkit-android
// repo while we verify the OMID-reflective-paths fix end-to-end before
// publishing KontextKit 0.0.2 to Maven Central.
//
// To use:
//   git clone git@github.com:kontextso/kontextkit-android.git ../kontextkit-android
//   git -C ../kontextkit-android checkout fix/omid-reflective-paths
//
// Remove this block (and bump `kontext-kit` in gradle/libs.versions.toml to
// 0.0.2) once the fix is published.
// ---------------------------------------------------------------------------
val kontextkitLocal = file("../kontextkit-android")
if (kontextkitLocal.exists()) {
    // Gradle auto-substitutes the `so.kontext.kit:kontext-kit-android` dep
    // by matching the included build's `group` + `rootProject.name`.
    includeBuild(kontextkitLocal)
}
