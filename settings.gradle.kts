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
        // Só o Tesseract4Android (OCR dos talões) vem do JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.adaptech-cz.Tesseract4Android") }
        }
    }
}

rootProject.name = "VueFileManager"
include(":app")
