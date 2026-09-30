// Settings for the FoldShift Android project.
// Hosts the single :app module. Additional modules can be added later.
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
        // Shizuku / Rikka publish artifacts to JitPack.
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "fold-shift"
include(":app")