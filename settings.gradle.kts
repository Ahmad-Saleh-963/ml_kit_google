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
        maven { url = java.net.URI("https://dl.google.com/dl/android/maven2/") }
        // إضافة مستودع JitPack
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "ml_kit_google"
include(":app")
