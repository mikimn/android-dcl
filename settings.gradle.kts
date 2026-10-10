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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "APK Loader"
include(":app")

// Fixture apps: tiny APKs built from source and packaged into the androidTest assets
// (see docs/TESTING.md). Add a new fixture by creating fixtures/<name> and listing it here and in
// app/build.gradle.kts.
include(":fixtures:fx-hello")
include(":fixtures:fx-resources")
include(":fixtures:fx-application")
include(":fixtures:fx-manifest")
