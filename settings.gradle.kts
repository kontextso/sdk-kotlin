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

// Local-dev escape hatch. When `../kontextkit-android` exists on disk,
// Gradle substitutes the published `so.kontext.kit:kontext-kit-android`
// artifact with the local source tree. Useful when iterating across
// both repos without cutting a KontextKit release between every change.
// Gated on the directory existing so the same `settings.gradle.kts`
// works on contributor machines that haven't checked KontextKit out.
// Customers never see this block (their build resolves the dependency
// from Maven Central via the published .aar's POM); it's purely a
// build-time file for our repo.
val kontextKitLocal = file("${rootDir}/../kontextkit-android")
if (kontextKitLocal.exists()) {
    includeBuild(kontextKitLocal) {
        dependencySubstitution {
            substitute(module("so.kontext.kit:kontext-kit-android"))
                .using(project(":"))
            // Substitute the IAB OMID redistribution too, so a local
            // checkout of kontextkit-android can exercise the multi-
            // module wire (root + :omsdk-android) without round-
            // tripping through `publishToMavenLocal`.
            substitute(module("so.kontext.iab:omsdk-android"))
                .using(project(":omsdk-android"))
        }
    }
}
