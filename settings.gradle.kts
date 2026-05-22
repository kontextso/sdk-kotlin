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

// Local-dev escape hatch — leave commented out. When uncommented and
// `../kontextkit-android` exists on disk, Gradle substitutes the published
// `so.kontext.kit:kontext-kit-android` artifact with the local source
// tree. Useful when iterating across both repos without cutting a
// KontextKit release between every change. Customers never see this
// block (their build resolves the dependency from Maven Central via
// the published .aar's POM); it's purely a build-time file for our repo.
//
// val kontextKitLocal = file("${rootDir}/../kontextkit-android")
// if (kontextKitLocal.exists()) {
//     includeBuild(kontextKitLocal) {
//         dependencySubstitution {
//             substitute(module("so.kontext.kit:kontext-kit-android"))
//                 .using(project(":"))
//         }
//     }
// }
