pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (Apache 2.0) is only published on JitPack; limit JitPack to it.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.adaptech-cz.*") } }
    }
}

rootProject.name = "ScanFire"

include(":app")
