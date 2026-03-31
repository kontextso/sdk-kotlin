rootProject.name = "sdk-kotlin"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        maven { url = uri("${rootDir}/local-maven") }
        google()
        mavenCentral()
        mavenLocal()
    }
}

include(
    ":ads",
    ":example"
)
