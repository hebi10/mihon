pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}
dependencyResolutionManagement {
    versionCatalogs {
        create("mihonx") {
            from(files("gradle/mihon.versions.toml"))
        }
    }
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "HebiComicReader"

// Legacy Mihon sources are retained for attribution/reference but NOT included in the APK.
include(":reader")
